using System.Runtime.InteropServices;
using Microsoft.UI.Dispatching;

namespace Nama.App.Player;

/// <summary>A picture from the film, small, as its pixels (BGRA, every one opaque).</summary>
public sealed record PreviewFrame(int Width, int Height, byte[] Pixels);

/// <summary>
/// The picture at the point under the pointer on the timeline (the Android app's). 30nama has no
/// thumbnail strips, so frames come from a second mpv of its own, with no picture or sound out,
/// reading the stream's lowest quality: it seeks to the keyframe nearest each point (no decoding up
/// to the exact frame) and hands back a 320-pixel copy of it. Its own thread asks it everything, and
/// waits for each answer there; nothing of it is asked from the UI thread (AGENTS.md). Learnt from
/// 30namaTV, by way of the Android app:
///  - points in steps of five seconds, one request at a time, the newest point replacing any waiting;
///  - a file that fails is opened again on the next point; three misses in a row and previews are off for that file.
/// </summary>
public sealed class SeekPreview : IDisposable
{
    public const double Step = 5;
    private const int Kept = 40;

    private readonly DispatcherQueue _ui;
    private readonly object _lock = new();
    private readonly AutoResetEvent _signal = new(false);
    private readonly Thread _thread;
    private readonly Dictionary<double, PreviewFrame> _frames = [];
    private readonly LinkedList<double> _order = new();
    private Uri? _url;
    private int _generation;
    private double? _wanted;
    private int _misses;
    private bool _disposed;

    /// <summary>A frame has come for a point, on the UI thread.</summary>
    public event Action<double, PreviewFrame>? Arrived;

    public SeekPreview(DispatcherQueue ui)
    {
        _ui = ui;
        _thread = new Thread(Run) { IsBackground = true, Name = "seek preview", Priority = ThreadPriority.BelowNormal };
        _thread.Start();
    }

    /// <summary>The point a time falls on: frames are taken every <see cref="Step"/> seconds.</summary>
    public static double PointOf(double seconds) => Math.Max(0, Math.Round(seconds / Step) * Step);

    /// <summary>The file the frames are taken from; null for none. What was taken from the one before is let go.</summary>
    public void Open(Uri? url)
    {
        lock (_lock)
        {
            if (url == _url) return;
            _url = url;
            _generation++;
            _wanted = null;
            _misses = 0;
            _frames.Clear();
            _order.Clear();
        }
    }

    /// <summary>The frame for <paramref name="seconds"/> if it has been taken; otherwise asked for, and <see cref="Arrived"/> says when it comes.</summary>
    public PreviewFrame? Ask(double seconds)
    {
        var point = PointOf(seconds);
        lock (_lock)
        {
            if (_frames.TryGetValue(point, out var frame)) return frame;
            if (_disposed || _url is null || _misses >= 3) return null;
            _wanted = point;
        }
        _signal.Set();
        return null;
    }

    public void Dispose()
    {
        lock (_lock) _disposed = true;
        _signal.Set();
    }

    // MARK: Its own thread

    private IntPtr _handle;
    private Uri? _loaded;

    private void Run()
    {
        try
        {
            while (true)
            {
                _signal.WaitOne();
                while (true)
                {
                    Uri? url;
                    int generation;
                    double point;
                    lock (_lock)
                    {
                        if (_disposed) return;
                        if (_wanted is not { } wanted || _url is null) break;
                        url = _url;
                        generation = _generation;
                        point = wanted;
                    }
                    var frame = Take(url, point);
                    lock (_lock)
                    {
                        if (generation != _generation) continue;
                        if (_wanted == point) _wanted = null;
                        if (frame is null)
                        {
                            _misses++;
                            continue;
                        }
                        _misses = 0;
                        _frames[point] = frame;
                        _order.AddLast(point);
                        while (_order.Count > Kept)
                        {
                            _frames.Remove(_order.First!.Value);
                            _order.RemoveFirst();
                        }
                    }
                    _ui.TryEnqueue(() =>
                    {
                        lock (_lock) if (generation != _generation) return;
                        Arrived?.Invoke(point, frame);
                    });
                }
            }
        }
        finally
        {
            if (_handle != IntPtr.Zero) Mpv.TerminateDestroy(_handle);
            _signal.Dispose();
        }
    }

    private PreviewFrame? Take(Uri url, double seconds)
    {
        try
        {
            if (!Ready() || !Load(url)) return null;
            // What is left from before (the restart after loading, a seek given up on) is not this seek's answer.
            Drain();
            if (!Run("seek", seconds.ToString(System.Globalization.CultureInfo.InvariantCulture), "absolute+keyframes") || !WaitFor(Mpv.EventId.PlaybackRestart, 8))
            {
                // Perhaps the connection: the file is opened again for the next point.
                _loaded = null;
                return null;
            }
            return Picture();
        }
        catch (Exception)
        {
            _loaded = null;
            return null;
        }
    }

    private bool Ready()
    {
        if (_handle != IntPtr.Zero) return true;
        _handle = Mpv.Create();
        if (_handle == IntPtr.Zero) return false;
        foreach (var (name, value) in new[]
        {
            // No window, no sound: it only reads, and is asked for its picture.
            ("vo", "null"), ("ao", "null"), ("audio", "no"), ("sid", "no"), ("sub-auto", "no"),
            ("ytdl", "no"), ("cover-art-auto", "no"), ("audio-file-auto", "no"),
            ("input-default-bindings", "no"), ("terminal", "no"), ("idle", "yes"), ("pause", "yes"),
            // In software, and light: the film's own decoder is left alone, and a frame need not be exact.
            ("hwdec", "no"), ("vd-lavc-threads", "2"), ("vd-lavc-skiploopfilter", "all"), ("hr-seek", "no"),
            ("vf", "scale=w=320:h=-2"), ("hls-bitrate", "min"),
            ("demuxer-max-bytes", "8MiB"), ("demuxer-readahead-secs", "1"),
        })
        {
            Mpv.SetOptionString(_handle, name, value);
        }
        if (Mpv.Initialize(_handle) >= 0) return true;
        Mpv.TerminateDestroy(_handle);
        _handle = IntPtr.Zero;
        return false;
    }

    private bool Load(Uri url)
    {
        if (_loaded == url) return true;
        _loaded = null;
        // Loaded, and its first picture decoded: that first restart, come late, would otherwise be
        // taken for the answer to the first seek, and give the opening frame for any point.
        if (!Run("loadfile", url.IsFile ? url.LocalPath : url.AbsoluteUri) || !WaitFor(Mpv.EventId.FileLoaded, 15) || !WaitFor(Mpv.EventId.PlaybackRestart, 15)) return false;
        _loaded = url;
        return true;
    }

    private void Drain()
    {
        while (Marshal.PtrToStructure<Mpv.Event>(Mpv.WaitEvent(_handle, 0)).Id != Mpv.EventId.None) { }
    }

    private bool Run(params string[] words)
    {
        var args = Mpv.Words(words);
        try { return Mpv.CommandWait(_handle, args) >= 0; }
        finally { Mpv.FreeWords(args); }
    }

    /// <summary>Waits on this thread for <paramref name="wanted"/>; false at an end of file or after <paramref name="seconds"/>.</summary>
    private bool WaitFor(Mpv.EventId wanted, double seconds)
    {
        var until = DateTime.UtcNow.AddSeconds(seconds);
        while (DateTime.UtcNow < until)
        {
            var e = Marshal.PtrToStructure<Mpv.Event>(Mpv.WaitEvent(_handle, 0.5));
            if (e.Id == wanted) return true;
            if (e.Id is Mpv.EventId.EndFile or Mpv.EventId.Shutdown) return false;
            lock (_lock) if (_disposed) return false;
        }
        return false;
    }

    private PreviewFrame? Picture()
    {
        var args = Mpv.Words("screenshot-raw", "video");
        try
        {
            if (Mpv.CommandRet(_handle, args, out var result) < 0) return null;
            try
            {
                if (result.Format != Mpv.FormatNodeMap) return null;
                var list = Marshal.PtrToStructure<Mpv.NodeList>(result.U);
                int width = 0, height = 0, stride = 0;
                Mpv.ByteArray? data = null;
                var size = Marshal.SizeOf<Mpv.Node>();
                for (var i = 0; i < list.Count; i++)
                {
                    var key = Mpv.Utf8(Marshal.ReadIntPtr(list.Keys, i * IntPtr.Size));
                    var node = Marshal.PtrToStructure<Mpv.Node>(list.Values + i * size);
                    switch (key)
                    {
                        case "w": width = (int)node.U; break;
                        case "h": height = (int)node.U; break;
                        case "stride": stride = (int)node.U; break;
                        case "data" when node.Format == Mpv.FormatByteArray: data = Marshal.PtrToStructure<Mpv.ByteArray>(node.U); break;
                    }
                }
                if (data is not { } bytes || width <= 0 || height <= 0 || stride < width * 4 || (long)bytes.Size < (long)stride * height) return null;
                // bgr0 to BGRA: the fourth byte is not alpha, so every pixel is made opaque.
                var pixels = new byte[width * height * 4];
                for (var y = 0; y < height; y++) Marshal.Copy(bytes.Data + y * stride, pixels, y * width * 4, width * 4);
                for (var i = 3; i < pixels.Length; i += 4) pixels[i] = 255;
                return new PreviewFrame(width, height, pixels);
            }
            finally { Mpv.FreeNodeContents(ref result); }
        }
        finally { Mpv.FreeWords(args); }
    }
}
