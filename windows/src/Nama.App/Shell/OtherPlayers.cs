using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;
using Nama.Core;

namespace Nama.App.Shell;

/// <summary>
/// The video players on this computer, from Windows' own "Open with" list: beside a finished
/// download, by their own icons (as the Mac app does), and in Settings as the player for online
/// playback. Only programs ExternalPlayer knows as video players are offered.
/// </summary>
public static partial class OtherPlayers
{
    /// <summary>A player, its program (a path, or a Store app's name), and its icon as a file or as pixels.</summary>
    public sealed record Player(string Name, string Program, ExternalPlayer.Kind Kind, string? IconFile, byte[]? IconPixels, int IconSize);

    private static readonly Dictionary<string, IReadOnlyList<Player>> ByType = new(StringComparer.OrdinalIgnoreCase);
    private static readonly object Lock = new();

    /// <summary>The players for files ending in <paramref name="extension"/> (".mkv"), if they have been looked up already.</summary>
    public static IReadOnlyList<Player>? Known(string extension)
    {
        lock (Lock) return ByType.TryGetValue(extension, out var found) ? found : null;
    }

    /// <summary>Looks up the players for <paramref name="extension"/>, once, off the window's thread: the shell takes a moment.</summary>
    public static Task<IReadOnlyList<Player>> ForAsync(string extension)
    {
        if (Known(extension) is { } known) return Task.FromResult(known);
        return OnShellThread(() =>
        {
            var found = Find(extension);
            lock (Lock) ByType[extension] = found;
            return found;
        });
    }

    /// <summary>The players that take a stream's address, for the online player choice in Settings.</summary>
    public static async Task<IReadOnlyList<Player>> ForStreamsAsync()
    {
        var all = (await ForAsync(".mp4")).Concat(await ForAsync(".mkv"));
        return all.Where(p => ExternalPlayer.TakesStreams(p.Kind) && File.Exists(p.Program)).DistinctBy(p => p.Program.ToLowerInvariant()).ToList();
    }

    /// <summary>Opens <paramref name="file"/> in <paramref name="player"/>, as Explorer's "Open with" would.</summary>
    public static Task<bool> OpenAsync(string file, Player player) => OnShellThread(() =>
    {
        try
        {
            // A program of its own: started with the file, which every player takes.
            if (File.Exists(player.Program))
            {
                Process.Start(new ProcessStartInfo(player.Program) { ArgumentList = { file }, UseShellExecute = false });
                return true;
            }
            // A Store app: only the shell can start it with a file.
            foreach (var handler in Handlers(Path.GetExtension(file)))
            {
                if (Name(handler) != player.Program) continue;
                var item = (IShellItem)ShellItem(file, typeof(IShellItem).GUID);
                item.BindToHandler(IntPtr.Zero, BindToDataObject, typeof(IDataObject).GUID, out var data);
                return handler.Invoke((IDataObject)data) >= 0;
            }
        }
        catch (Exception) { }
        return false;
    });

    /// <summary>Plays <paramref name="address"/> in the program at <paramref name="program"/>, from <paramref name="start"/> seconds.</summary>
    public static bool Stream(string program, string address, double? start, string? title)
    {
        if (ExternalPlayer.Of(program) is not { } kind || !File.Exists(program)) return false;
        try
        {
            var info = new ProcessStartInfo(program) { UseShellExecute = false };
            foreach (var argument in ExternalPlayer.Arguments(kind, address, start, title)) info.ArgumentList.Add(argument);
            Process.Start(info);
            return true;
        }
        catch (Exception) { return false; }
    }

    /// <summary>The player's own name for a program path, as Windows shows it, for a choice kept in Settings.</summary>
    public static string NameOf(string program) =>
        Known(".mp4")?.Concat(Known(".mkv") ?? []).FirstOrDefault(p => string.Equals(p.Program, program, StringComparison.OrdinalIgnoreCase))?.Name
        ?? FileVersionInfo(program) ?? Path.GetFileNameWithoutExtension(program);

    private static string? FileVersionInfo(string program)
    {
        try { return File.Exists(program) ? System.Diagnostics.FileVersionInfo.GetVersionInfo(program).FileDescription : null; }
        catch (Exception) { return null; }
    }

    // MARK: Finding them

    private static IReadOnlyList<Player> Find(string extension)
    {
        var self = Environment.ProcessPath ?? "";
        var players = new List<Player>();
        try
        {
            foreach (var handler in Handlers(extension))
            {
                var program = Name(handler);
                if (program is null || string.Equals(program, self, StringComparison.OrdinalIgnoreCase)) continue;
                // A Store app gives its shown name, in the system's language: known by its package instead.
                var known = File.Exists(program) ? program : ExternalPlayer.Package(handler.GetIconLocation(out var reference, out _) >= 0 ? reference : null);
                if (known is null || ExternalPlayer.Of(known) is not { } kind || players.Any(p => p.Program == program)) continue;
                var name = handler.GetUIName(out var ui) >= 0 && !string.IsNullOrWhiteSpace(ui) ? ui : Path.GetFileNameWithoutExtension(program);
                var (file, pixels, size) = Icon(handler, program);
                players.Add(new Player(name, program, kind, file, pixels, size));
            }
        }
        catch (Exception) { }
        // Four at most, as on the Mac: a row of icons, not a menu. The players made for any file
        // first, then other programs, and Windows' own last (the old Media Player the very last).
        return players
            .OrderBy(p => ExternalPlayer.TakesStreams(p.Kind) ? 0 : !File.Exists(p.Program) ? 2 : p.Program.EndsWith("wmplayer.exe", StringComparison.OrdinalIgnoreCase) ? 3 : 1)
            .Take(4).ToList();
    }

    private static IEnumerable<IAssocHandler> Handlers(string extension)
    {
        if (SHAssocEnumHandlers(extension, 1 /* recommended */, out var list) < 0 || list is null) yield break;
        var one = new IAssocHandler[1];
        while (list.Next(1, one, out var fetched) == 0 && fetched == 1) yield return one[0];
    }

    private static string? Name(IAssocHandler handler) => handler.GetName(out var name) >= 0 ? name : null;

    /// <summary>A Store app's icon is a picture in its package; a program's, the one Explorer shows for it.</summary>
    private static (string? File, byte[]? Pixels, int Size) Icon(IAssocHandler handler, string program)
    {
        const int size = 64;
        try
        {
            if (handler.GetIconLocation(out var location, out _) >= 0 && location?.StartsWith('@') == true)
            {
                var buffer = new char[1024];
                if (SHLoadIndirectString(location, buffer, buffer.Length, IntPtr.Zero) >= 0)
                {
                    var path = new string(buffer).TrimEnd('\0');
                    if (File.Exists(path)) return (path, null, 0);
                }
            }
            var source = location is { Length: > 0 } && File.Exists(location) ? location : program;
            if (!File.Exists(source)) return (null, null, 0);
            var factory = (IShellItemImageFactory)ShellItem(source, typeof(IShellItemImageFactory).GUID);
            if (factory.GetImage(new NativeSize { Width = size, Height = size }, 0x4 /* icon only */, out var bitmap) < 0) return (null, null, 0);
            try { return (null, Pixels(bitmap, size), size); }
            finally { DeleteObject(bitmap); }
        }
        catch (Exception) { return (null, null, 0); }
    }

    /// <summary>The bitmap's pixels, top row first, blue-green-red-alpha.</summary>
    private static byte[]? Pixels(IntPtr bitmap, int size)
    {
        var header = new BitmapInfoHeader { Size = Marshal.SizeOf<BitmapInfoHeader>(), Width = size, Height = -size, Planes = 1, BitCount = 32 };
        var pixels = new byte[size * size * 4];
        var screen = GetDC(IntPtr.Zero);
        try { return GetDIBits(screen, bitmap, 0, (uint)size, pixels, ref header, 0) == size ? pixels : null; }
        finally { ReleaseDC(IntPtr.Zero, screen); }
    }

    /// <summary>The shell's objects want a single-threaded apartment of their own.</summary>
    private static Task<T> OnShellThread<T>(Func<T> work)
    {
        var done = new TaskCompletionSource<T>();
        var thread = new Thread(() =>
        {
            try { done.SetResult(work()); }
            catch (Exception e) { done.SetException(e); }
        }) { IsBackground = true };
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        return done.Task;
    }

    // MARK: Windows

    private static readonly Guid BindToDataObject = new("B8C0BD9F-ED24-455C-83E6-D5390C4FE8C4");

    [ComImport, Guid("973810ae-9599-4b88-9e4d-6ee98c9552da"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IEnumAssocHandlers
    {
        [PreserveSig] int Next(uint count, [Out, MarshalAs(UnmanagedType.LPArray, SizeParamIndex = 0)] IAssocHandler[] handlers, out uint fetched);
    }

    [ComImport, Guid("F04061AC-1659-4a3f-A954-775AA57FC083"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAssocHandler
    {
        [PreserveSig] int GetName([MarshalAs(UnmanagedType.LPWStr)] out string name);
        [PreserveSig] int GetUIName([MarshalAs(UnmanagedType.LPWStr)] out string name);
        [PreserveSig] int GetIconLocation([MarshalAs(UnmanagedType.LPWStr)] out string path, out int index);
        [PreserveSig] int IsRecommended();
        [PreserveSig] int MakeDefault([MarshalAs(UnmanagedType.LPWStr)] string description);
        [PreserveSig] int Invoke(IDataObject data);
    }

    [ComImport, Guid("43826d1e-e718-42ee-bc55-a1e261c37bfe"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IShellItem
    {
        void BindToHandler(IntPtr context, [MarshalAs(UnmanagedType.LPStruct)] Guid handler, [MarshalAs(UnmanagedType.LPStruct)] Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object result);
    }

    [ComImport, Guid("bcc18b79-ba16-442f-80c4-8a59c30c463b"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IShellItemImageFactory
    {
        [PreserveSig] int GetImage(NativeSize size, int flags, out IntPtr bitmap);
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeSize { public int Width, Height; }

    [StructLayout(LayoutKind.Sequential)]
    private struct BitmapInfoHeader
    {
        public int Size, Width, Height;
        public short Planes, BitCount;
        public int Compression, SizeImage, XPelsPerMeter, YPelsPerMeter, ClrUsed, ClrImportant;
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern int SHAssocEnumHandlers(string extension, int filter, out IEnumAssocHandlers list);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
    private static extern void SHCreateItemFromParsingName(string path, IntPtr context, [MarshalAs(UnmanagedType.LPStruct)] Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object item);

    private static object ShellItem(string path, Guid iid)
    {
        SHCreateItemFromParsingName(path, IntPtr.Zero, iid, out var found);
        return found;
    }

    [DllImport("shlwapi.dll", CharSet = CharSet.Unicode)]
    private static extern int SHLoadIndirectString(string source, char[] output, int size, IntPtr reserved);

    [DllImport("gdi32.dll")] private static extern bool DeleteObject(IntPtr handle);
    [DllImport("gdi32.dll")] private static extern int GetDIBits(IntPtr dc, IntPtr bitmap, uint start, uint lines, byte[] bits, ref BitmapInfoHeader info, uint usage);
    [DllImport("user32.dll")] private static extern IntPtr GetDC(IntPtr window);
    [DllImport("user32.dll")] private static extern int ReleaseDC(IntPtr window, IntPtr dc);
}
