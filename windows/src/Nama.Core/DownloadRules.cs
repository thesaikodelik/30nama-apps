using System.Diagnostics;

namespace Nama.Core;

/// <summary>
/// A cap on how fast downloads come in, shared by every connection: a bucket that fills at the
/// allowed rate and holds at most a second's worth. Each piece that arrives is taken from it, and
/// a connection that has overdrawn it waits until it is back to zero: the whole debt, not only
/// what this piece added, or the average runs over the cap. As RateLimit in DownloadRules.swift.
/// </summary>
public sealed class RateLimit
{
    private readonly object _lock = new();
    private double _allowance;
    private double? _last;
    private double _rate;

    /// <summary>Bytes a second; zero or less is no limit.</summary>
    public double BytesPerSecond
    {
        get { lock (_lock) return _rate; }
        set { lock (_lock) { _rate = value; _allowance = Math.Min(_allowance, Math.Max(0, value)); } }
    }

    private static double Now => Stopwatch.GetTimestamp() / (double)Stopwatch.Frequency;

    /// <summary>Takes <paramref name="bytes"/> from the budget at <paramref name="now"/> (seconds) and says how long to wait before reading more.</summary>
    public TimeSpan Take(long bytes, double? now = null)
    {
        lock (_lock)
        {
            if (_rate <= 0) return TimeSpan.Zero;
            var at = now ?? Now;
            if (_last is { } last) _allowance = Math.Min(_rate, _allowance + (at - last) * _rate);
            _last = at;
            _allowance -= bytes;
            return _allowance >= 0 ? TimeSpan.Zero : TimeSpan.FromSeconds(-_allowance / _rate);
        }
    }
}

/// <summary>How a file fetched over several connections answers a server that takes fewer.</summary>
public static class Connections
{
    /// <summary>
    /// A connection that ended this way gives its stretch back and the file goes on with fewer
    /// connections. Some servers take only so many from one address and leave the rest unanswered
    /// or say there are too many (429, 503); that is only a sign of it while others are working.
    /// </summary>
    /// <param name="status">The HTTP status that ended it; null for no answer or a dropped connection.</param>
    /// <param name="received">Bytes this connection brought in on this try.</param>
    /// <param name="othersWorking">Other connections bringing in bytes now.</param>
    public static bool GivesWay(int? status, long received, int othersWorking) =>
        othersWorking > 0 && (status is { } s ? s is 429 or 503 : received == 0);
}
