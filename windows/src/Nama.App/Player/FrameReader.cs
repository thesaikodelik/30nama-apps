using System.Runtime.InteropServices;

namespace Nama.App.Player;

/// <summary>
/// A second mpv of its own, with no picture or sound out, that hands back small pictures from a
/// file: it seeks to the keyframe nearest a point (no decoding up to the exact frame) and gives a
/// 320-pixel copy of it. Software decoding only, so the film's own decoder is left alone. Not
/// thread-safe: one thread asks it everything and waits for each answer there; nothing of it is
/// asked from the UI thread (AGENTS.md). Used by <see cref="SeekPreview"/> and <see cref="EpisodeStills"/>.
/// </summary>
internal sealed class FrameReader : IDisposable
{
    private readonly Func<bool> _stopped;
    private IntPtr _handle;
    private Uri? _loaded;

    /// <param name="stopped">Asked while waiting: true lets a wait end early, as the owner is going away.</param>
    public FrameReader(Func<bool> stopped) => _stopped = stopped;

    /// <summary>The picture nearest <paramref name="seconds"/> into <paramref name="url"/>; null when it could not be had.</summary>
    public PreviewFrame? Take(Uri url, double seconds)
    {
        try
        {
            if (!Ready() || !Load(url)) return null;
            // What is left from before (the restart after loading, a seek given up on) is not this seek's answer.
            Drain();
            if (!Run("seek", seconds.ToString(System.Globalization.CultureInfo.InvariantCulture), "absolute+keyframes") || !WaitFor(Mpv.EventId.PlaybackRestart, 8))
            {
                // Perhaps the connection: the file is opened again next time.
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

    public void Dispose()
    {
        if (_handle != IntPtr.Zero) Mpv.TerminateDestroy(_handle);
        _handle = IntPtr.Zero;
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
        // taken for the answer to the first seek, and give the opening frame for any point. The
        // file before it ends first; that end is not this one's, so it is waited past to the start.
        if (!Run("loadfile", url.IsFile ? url.LocalPath : url.AbsoluteUri) || !WaitFor(Mpv.EventId.StartFile, 15, endsWait: false)
            || !WaitFor(Mpv.EventId.FileLoaded, 15) || !WaitFor(Mpv.EventId.PlaybackRestart, 15)) return false;
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

    /// <summary>Waits on this thread for <paramref name="wanted"/>; false at an end of file (when <paramref name="endsWait"/>) or after <paramref name="seconds"/>.</summary>
    private bool WaitFor(Mpv.EventId wanted, double seconds, bool endsWait = true)
    {
        var until = DateTime.UtcNow.AddSeconds(seconds);
        while (DateTime.UtcNow < until)
        {
            var e = Marshal.PtrToStructure<Mpv.Event>(Mpv.WaitEvent(_handle, 0.5));
            if (e.Id == wanted) return true;
            if (e.Id == Mpv.EventId.Shutdown || (e.Id == Mpv.EventId.EndFile && endsWait)) return false;
            if (_stopped()) return false;
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
