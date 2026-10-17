using System.Runtime.InteropServices;

namespace Nama.App.Shell;

/// <summary>
/// The app's icon in the notification area, beside the clock, while its window is closed but it
/// goes on (downloads, as 30nama's own desktop app does). A click brings the window back; the
/// menu also quits. WinUI has no such icon of its own, so this is the Win32 one, with a hidden
/// window of its own to hear the clicks.
/// </summary>
public sealed partial class Tray : IDisposable
{
    private const int CallbackMessage = 0x8000 + 1; // WM_APP + 1
    private const int OpenItem = 1, QuitItem = 2;

    private readonly WndProc _proc;
    private readonly IntPtr _window;
    private readonly IntPtr _icon;
    private bool _shown;

    public Func<(string Open, string Quit)> Labels { get; init; } = () => ("Open", "Quit");
    public event Action? OpenRequested;
    public event Action? QuitRequested;

    public Tray(string iconPath)
    {
        _proc = Handle;
        var className = "NamaTray" + Environment.ProcessId;
        var windowClass = new WindowClass { Size = (uint)Marshal.SizeOf<WindowClass>(), Proc = Marshal.GetFunctionPointerForDelegate(_proc), Instance = GetModuleHandle(null), ClassName = className };
        RegisterClassEx(ref windowClass);
        // A message-only window: it is never shown and never in the taskbar.
        _window = CreateWindowEx(0, className, "", 0, 0, 0, 0, 0, new IntPtr(-3), IntPtr.Zero, windowClass.Instance, IntPtr.Zero);
        _icon = LoadImage(IntPtr.Zero, iconPath, 1 /* icon */, 16, 16, 0x10 /* from file */);
    }

    public void Show(string tip)
    {
        var data = Data(tip);
        if (!_shown) Shell_NotifyIcon(0 /* add */, ref data);
        else Shell_NotifyIcon(1 /* modify */, ref data);
        _shown = true;
    }

    public void Hide()
    {
        if (!_shown) return;
        var data = Data("");
        Shell_NotifyIcon(2 /* delete */, ref data);
        _shown = false;
    }

    private NotifyIconData Data(string tip) => new()
    {
        Size = (uint)Marshal.SizeOf<NotifyIconData>(), Window = _window, Id = 1,
        Flags = 0x1 | 0x2 | 0x4, // message, icon, tip
        CallbackMessage = CallbackMessage, Icon = _icon, Tip = tip.Length > 127 ? tip[..127] : tip,
    };

    private IntPtr Handle(IntPtr window, uint message, IntPtr w, IntPtr l)
    {
        if (message == CallbackMessage)
        {
            var mouse = (uint)l.ToInt64() & 0xFFFF;
            if (mouse is 0x0202 /* left up */ or 0x0203 /* double click */) OpenRequested?.Invoke();
            else if (mouse == 0x0205 /* right up */) ShowMenu();
            return IntPtr.Zero;
        }
        return DefWindowProc(window, message, w, l);
    }

    private void ShowMenu()
    {
        var (open, quit) = Labels();
        var menu = CreatePopupMenu();
        AppendMenu(menu, 0, OpenItem, open);
        AppendMenu(menu, 0x800 /* separator */, 0, null);
        AppendMenu(menu, 0, QuitItem, quit);
        GetCursorPos(out var point);
        // The menu goes away when clicked off only if its window is in front.
        SetForegroundWindow(_window);
        var chosen = TrackPopupMenu(menu, 0x100 /* return the choice */ | 0x80 /* no notify */, point.X, point.Y, 0, _window, IntPtr.Zero);
        DestroyMenu(menu);
        if (chosen == OpenItem) OpenRequested?.Invoke();
        if (chosen == QuitItem) QuitRequested?.Invoke();
    }

    public void Dispose()
    {
        Hide();
        if (_icon != IntPtr.Zero) DestroyIcon(_icon);
        DestroyWindow(_window);
    }

    // MARK: Win32

    private delegate IntPtr WndProc(IntPtr window, uint message, IntPtr w, IntPtr l);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct WindowClass
    {
        public uint Size, Style;
        public IntPtr Proc;
        public int ClassExtra, WindowExtra;
        public IntPtr Instance, Icon, Cursor, Background;
        public string? MenuName;
        public string ClassName;
        public IntPtr SmallIcon;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NotifyIconData
    {
        public uint Size;
        public IntPtr Window;
        public uint Id, Flags, CallbackMessage;
        public IntPtr Icon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Tip;
        public uint State, StateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string Info;
        public uint TimeoutOrVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)] public string InfoTitle;
        public uint InfoFlags;
        public Guid Item;
        public IntPtr BalloonIcon;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct Point { public int X, Y; }

    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern ushort RegisterClassEx(ref WindowClass windowClass);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern IntPtr CreateWindowEx(uint exStyle, string className, string name, uint style, int x, int y, int w, int h, IntPtr parent, IntPtr menu, IntPtr instance, IntPtr param);
    [DllImport("user32.dll")] private static extern IntPtr DefWindowProc(IntPtr window, uint message, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] private static extern bool DestroyWindow(IntPtr window);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] private static extern IntPtr GetModuleHandle(string? name);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern IntPtr LoadImage(IntPtr instance, string name, uint type, int w, int h, uint flags);
    [DllImport("user32.dll")] private static extern bool DestroyIcon(IntPtr icon);
    [DllImport("shell32.dll", CharSet = CharSet.Unicode)] private static extern bool Shell_NotifyIcon(uint message, ref NotifyIconData data);
    [DllImport("user32.dll")] private static extern IntPtr CreatePopupMenu();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern bool AppendMenu(IntPtr menu, uint flags, int id, string? text);
    [DllImport("user32.dll")] private static extern int TrackPopupMenu(IntPtr menu, uint flags, int x, int y, int reserved, IntPtr window, IntPtr rect);
    [DllImport("user32.dll")] private static extern bool DestroyMenu(IntPtr menu);
    [DllImport("user32.dll")] private static extern bool GetCursorPos(out Point point);
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr window);
}
