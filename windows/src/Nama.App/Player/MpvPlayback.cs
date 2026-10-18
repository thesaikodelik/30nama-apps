using System.Collections.Concurrent;
using System.Diagnostics;
using System.Globalization;
using System.Runtime.InteropServices;
using Microsoft.UI.Dispatching;

namespace Nama.App.Player;

/// <summary>
/// Plays through libmpv, drawing into a swap chain the app shows in a <c>SwapChainPanel</c>.
///
/// mpv is never asked anything from the UI thread: a synchronous call waits for mpv's own loop,
/// and that loop can be waiting on the thread that asked. Commands go out with
/// <c>mpv_command_async</c>; what mpv reports arrives as property changes on a thread of its own
/// and is kept here for the UI to read.
/// </summary>
public sealed class MpvPlayback : IDisposable
{
    private IntPtr _handle;
    private readonly DispatcherQueue _ui;
    private readonly ConcurrentDictionary<string, double> _numbers = new();
    private readonly Thread _events;
    private volatile bool _stopping;

    public event Action<IntPtr>? SwapChainChanged;
    public event Action? Ended;
    public event Action? Failed;
    public event Action? Loaded;

    public bool IsAvailable => _handle != IntPtr.Zero;

    public MpvPlayback(DispatcherQueue ui, int bufferSeconds = 0)
    {
        _ui = ui;
        try { _handle = Mpv.Create(); }
        catch (DllNotFoundException) { _handle = IntPtr.Zero; }
        _events = new Thread(Drain) { IsBackground = true, Name = "mpv events" };
        if (_handle == IntPtr.Zero) return;

        foreach (var (name, value) in new[]
        {
            // No window of mpv's own: it draws into a swap chain for composition, which the app hands to its panel.
            ("vo", "gpu-next"), ("gpu-context", "d3d11"), ("d3d11-output-mode", "composition"), ("d3d11-composition-size", "1280x720"),
            ("hwdec", "auto-safe"),
            // The app draws its own controls and its own subtitles, and takes the keys itself.
            ("osc", "no"), ("osd-level", "0"), ("input-default-bindings", "no"), ("input-vo-keyboard", "no"), ("input-cursor", "no"),
            ("sid", "no"), ("sub-auto", "no"), ("ytdl", "no"),
            // Nothing looked for beside the file: a cover or a second sound track meant listing its
            // whole folder, and a Downloads folder with thousands of files kept the file waiting.
            ("cover-art-auto", "no"), ("audio-file-auto", "no"),
            // Stay alive between files, and say so when one ends.
            ("idle", "yes"), ("keep-open", "no"),
        })
        {
            Mpv.SetOptionString(_handle, name, value);
        }
        if (bufferSeconds > 0)
        {
            Mpv.SetOptionString(_handle, "cache-secs", bufferSeconds.ToString(CultureInfo.InvariantCulture));
            Mpv.SetOptionString(_handle, "demuxer-readahead-secs", bufferSeconds.ToString(CultureInfo.InvariantCulture));
        }
        Mpv.RequestLogMessages(_handle, Debugger.IsAttached || Environment.GetEnvironmentVariable("NAMA_MPV_LOG") is not null ? "warn" : "no");
        if (Mpv.Initialize(_handle) < 0)
        {
            Mpv.TerminateDestroy(_handle);
            _handle = IntPtr.Zero;
            return;
        }
        foreach (var name in new[] { "time-pos", "duration", "video-params/aspect" }) Mpv.ObserveProperty(_handle, 0, name, Mpv.FormatDouble);
        foreach (var name in new[] { "paused-for-cache", "seeking", "pause" }) Mpv.ObserveProperty(_handle, 0, name, Mpv.FormatFlag);
        Mpv.ObserveProperty(_handle, 0, "display-swapchain", Mpv.FormatInt64);
        _events.Start();
    }

    /// <summary>Reads what mpv has to report: where it is, its swap chain, and when a file ends. Runs off the UI thread.</summary>
    private void Drain()
    {
        var handle = _handle;
        while (!_stopping)
        {
            var e = Marshal.PtrToStructure<Mpv.Event>(Mpv.WaitEvent(handle, -1));
            switch (e.Id)
            {
                case Mpv.EventId.Shutdown:
                    return;
                case Mpv.EventId.LogMessage when e.Data != IntPtr.Zero:
                    var log = Marshal.PtrToStructure<Mpv.EventLogMessage>(e.Data);
                    var line = $"mpv [{Mpv.Utf8(log.Prefix)}] {Mpv.Utf8(log.Text)}";
                    Debug.Write(line);
                    if (LogFile is { } file) File.AppendAllText(file, line);
                    break;
                case Mpv.EventId.PropertyChange when e.Data != IntPtr.Zero:
                    var property = Marshal.PtrToStructure<Mpv.EventProperty>(e.Data);
                    var name = Mpv.Utf8(property.Name) ?? "";
                    if (property.Data == IntPtr.Zero) { _numbers.TryRemove(name, out _); break; }
                    switch (property.Format)
                    {
                        case Mpv.FormatDouble:
                            _numbers[name] = Marshal.PtrToStructure<double>(property.Data);
                            break;
                        case Mpv.FormatFlag:
                            _numbers[name] = Marshal.ReadInt32(property.Data) != 0 ? 1 : 0;
                            break;
                        case Mpv.FormatInt64 when name == "display-swapchain":
                            var swapChain = (IntPtr)Marshal.ReadInt64(property.Data);
                            _ui.TryEnqueue(() => SwapChainChanged?.Invoke(swapChain));
                            break;
                    }
                    break;
                case Mpv.EventId.FileLoaded:
                    _ui.TryEnqueue(() => Loaded?.Invoke());
                    break;
                case Mpv.EventId.EndFile when e.Data != IntPtr.Zero:
                    var reason = Marshal.PtrToStructure<Mpv.EventEndFile>(e.Data).Reason;
                    if (reason == Mpv.EndFileReason.Eof) _ui.TryEnqueue(() => Ended?.Invoke());
                    if (reason == Mpv.EndFileReason.Error) _ui.TryEnqueue(() => Failed?.Invoke());
                    break;
            }
        }
    }

    /// <summary>Where mpv's warnings are written when <c>NAMA_MPV_LOG</c> names a file.</summary>
    private static readonly string? LogFile = Environment.GetEnvironmentVariable("NAMA_MPV_LOG") is { Length: > 1 } path ? path : null;

    private double Number(string name) => _numbers.TryGetValue(name, out var v) ? v : 0;

    public double Position => Number("time-pos");
    public double Duration => Number("duration");
    public bool Waiting => Number("paused-for-cache") > 0 || Number("seeking") > 0;
    public bool Paused => Number("pause") > 0;
    public double Aspect => Number("video-params/aspect");

    private void Set(string name, string value) => Command("set", name, value);

    private void Command(params string[] words)
    {
        if (_handle != IntPtr.Zero && !_stopping) Mpv.Command(_handle, words);
    }

    /// <summary>The size, in pixels, of the swap chain mpv draws into.</summary>
    public void SetSurfaceSize(int width, int height)
    {
        if (width > 0 && height > 0) Set("d3d11-composition-size", $"{width}x{height}");
    }

    public void Load(string url, double? position = null)
    {
        if (_handle == IntPtr.Zero)
        {
            Failed?.Invoke();
            return;
        }
        _numbers.Clear();
        var words = new List<string> { "loadfile", url, "replace", "-1" };
        if (position is > 0) words.Add($"start={(int)position}");
        Command([.. words]);
    }

    public void Play() => Set("pause", "no");
    public void Pause() => Set("pause", "yes");
    public void Seek(double seconds) => Command("seek", seconds.ToString(CultureInfo.InvariantCulture), "absolute");
    public void SetSpeed(double speed) => Set("speed", speed.ToString(CultureInfo.InvariantCulture));
    public void SetVolume(double volume) => Set("volume", ((int)(volume * 100)).ToString(CultureInfo.InvariantCulture));
    public void SetMuted(bool muted) => Set("mute", muted ? "yes" : "no");

    public void Dispose()
    {
        if (_handle == IntPtr.Zero || _stopping) return;
        _stopping = true;
        var handle = _handle;
        // Shutting mpv down waits for its threads; that wait is not for the UI thread.
        new Thread(() =>
        {
            Mpv.Wakeup(handle);
            if (_events.IsAlive) _events.Join();
            Mpv.TerminateDestroy(handle);
        }) { IsBackground = true, Name = "mpv shutdown" }.Start();
        _handle = IntPtr.Zero;
    }
}
