using Microsoft.UI.Dispatching;
using Nama.App.Model;
using Nama.Core;
using Windows.Graphics.Imaging;
using Windows.Storage.Streams;

namespace Nama.App.Player;

/// <summary>
/// A still for each episode's tile, taken from the episode itself: 30nama has none. From the lowest
/// quality, with a second mpv of its own (<see cref="FrameReader"/>), one episode at a time on a
/// thread of its own, at the point <see cref="EpisodeStill"/> picks. Kept on the disk as small
/// JPEGs, so each episode costs this once. Only for episodes that stream: a file there is nothing to
/// read. As EpisodeStills.kt in the Android app; learnt from 30namaTV.
/// </summary>
public sealed class EpisodeStills
{
    public static EpisodeStills Shared { get; } = new(DispatcherQueue.GetForCurrentThread());

    private const long MaxBytes = 20L * 1024 * 1024;

    private readonly DispatcherQueue _ui;
    private readonly object _lock = new();
    private readonly AutoResetEvent _signal = new(false);
    private readonly HashSet<string> _failed = [];
    private List<(string Id, Uri Url, double? Credits)> _wanted = [];
    private Thread? _thread;

    /// <summary>A still has been saved for a file id, on the UI thread.</summary>
    public event Action<string>? Arrived;

    private EpisodeStills(DispatcherQueue ui) => _ui = ui;

    private static string Folder => Directory.CreateDirectory(Path.Combine(Storage.Folder, "frames")).FullName;

    /// <summary>The saved still for a file id, if there is one.</summary>
    public static string? File(string fileId)
    {
        if (fileId.Length == 0) return null;
        var path = Path.Combine(Folder, fileId + ".jpg");
        return System.IO.File.Exists(path) ? path : null;
    }

    /// <summary>
    /// The episodes now in sight: those without a still are taken in this order, replacing what was
    /// waiting (another season, another title). Nothing for an episode that does not stream.
    /// </summary>
    public void Want(IEnumerable<StreamItem> items)
    {
        var wanted = new List<(string, Uri, double?)>();
        foreach (var item in items)
        {
            if (item.FileId.Length == 0 || File(item.FileId) is not null) continue;
            var lowest = item.Qualities.Where(q => !q.IsAuto).OrderBy(q => q.Height).FirstOrDefault() ?? item.Qualities.FirstOrDefault();
            if (lowest is null) continue;
            wanted.Add((item.FileId, lowest.UrlFor(AudioMix.Stereo), item.CreditsStart));
        }
        lock (_lock)
        {
            _wanted = wanted.Where(w => !_failed.Contains(w.Item1)).ToList();
            if (_wanted.Count == 0) return;
            _thread ??= StartThread();
        }
        _signal.Set();
    }

    private Thread StartThread()
    {
        var thread = new Thread(Run) { IsBackground = true, Name = "episode stills", Priority = ThreadPriority.Lowest };
        thread.Start();
        return thread;
    }

    // MARK: Its own thread

    private void Run()
    {
        using var reader = new FrameReader(() => false);
        while (true)
        {
            _signal.WaitOne();
            while (true)
            {
                (string Id, Uri Url, double? Credits) next;
                lock (_lock)
                {
                    if (_wanted.Count == 0) break;
                    next = _wanted[0];
                    _wanted.RemoveAt(0);
                }
                if (File(next.Id) is not null) continue;
                var began = DateTime.UtcNow;
                var still = Take(reader, next.Url, next.Credits);
                Log($"{next.Id} {(still is null ? "failed" : "taken")} in {(DateTime.UtcNow - began).TotalSeconds:0.0}s from {next.Url}");
                if (still is null || !Save(next.Id, still))
                {
                    lock (_lock) _failed.Add(next.Id);
                    continue;
                }
                var id = next.Id;
                _ui.TryEnqueue(() => Arrived?.Invoke(id));
            }
        }
    }

    [System.Diagnostics.Conditional("DEBUG")]
    private static void Log(string line)
    {
        try { System.IO.File.AppendAllText(Path.Combine(Storage.Folder, "stills.log"), $"{DateTime.Now:HH:mm:ss} {line}{Environment.NewLine}"); } catch (Exception) { }
    }

    /// <summary>The frame at the first point, or at the second when the first is nearly black and the second is not darker.</summary>
    private static PreviewFrame? Take(FrameReader reader, Uri url, double? credits)
    {
        var (first, second) = EpisodeStill.Points(credits);
        var a = reader.Take(url, first);
        if (a is null) return null;
        var light = EpisodeStill.Luma(a.Pixels);
        if (light >= EpisodeStill.Dark) return a;
        var b = reader.Take(url, second);
        return b is not null && EpisodeStill.Luma(b.Pixels) > light ? b : a;
    }

    private static bool Save(string id, PreviewFrame frame)
    {
        try
        {
            var path = Path.Combine(Folder, id + ".jpg");
            var part = path + ".part";
            using (var memory = new InMemoryRandomAccessStream())
            {
                var encoder = BitmapEncoder.CreateAsync(BitmapEncoder.JpegEncoderId, memory).AsTask().GetAwaiter().GetResult();
                encoder.SetPixelData(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Ignore, (uint)frame.Width, (uint)frame.Height, 96, 96, frame.Pixels);
                encoder.FlushAsync().AsTask().GetAwaiter().GetResult();
                memory.Seek(0);
                using var file = System.IO.File.Create(part);
                memory.AsStreamForRead().CopyTo(file);
            }
            System.IO.File.Move(part, path, overwrite: true);
            Trim();
            return true;
        }
        catch (Exception) { return false; }
    }

    /// <summary>Twenty megabytes of stills at most; the oldest go first.</summary>
    private static void Trim()
    {
        var files = new DirectoryInfo(Folder).GetFiles("*.jpg").OrderBy(f => f.LastWriteTimeUtc).ToList();
        var total = files.Sum(f => f.Length);
        foreach (var f in files)
        {
            if (total <= MaxBytes) break;
            total -= f.Length;
            try { f.Delete(); } catch (Exception) { }
        }
    }
}
