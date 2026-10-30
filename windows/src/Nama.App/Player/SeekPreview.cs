using Microsoft.UI.Dispatching;

namespace Nama.App.Player;

/// <summary>A picture from the film, small, as its pixels (BGRA, every one opaque).</summary>
public sealed record PreviewFrame(int Width, int Height, byte[] Pixels);

/// <summary>
/// The picture at the point under the pointer on the timeline (the Android app's). 30nama has no
/// thumbnail strips, so frames come from a second mpv of its own (<see cref="FrameReader"/>)
/// reading the stream's lowest quality, on a thread of its own. Learnt from 30namaTV, by way of the
/// Android app:
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

    private FrameReader? _reader;

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
                    _reader ??= new FrameReader(() => { lock (_lock) return _disposed; });
                    var frame = _reader.Take(url, point);
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
            _reader?.Dispose();
            _signal.Dispose();
        }
    }
}
