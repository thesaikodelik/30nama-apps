namespace Nama.Core;

/// <summary>
/// When a download that failed is worth trying again, and how long to wait first. A dropped
/// connection, a busy server or a time-out are passing; a refusal (an expired link, a missing
/// file) is not. Waits double from two seconds, with a little jitter, and a server's own
/// Retry-After is honoured. The schedule is after the Tachyon download manager's (MIT).
/// </summary>
public static class RetryPolicy
{
    public const int Attempts = 6;
    private static readonly TimeSpan Longest = TimeSpan.FromMinutes(2);

    /// <summary>Whether an answer with this status may come right on a later try.</summary>
    public static bool Passing(int? status) => status is null or 408 or 429 or >= 500;

    /// <summary>The wait before try number <paramref name="attempt"/> + 1, or null to give up.</summary>
    /// <param name="attempt">Tries made so far, from 1.</param>
    /// <param name="status">The HTTP status that ended the last try; null for a dropped connection or a time-out.</param>
    public static TimeSpan? Next(int attempt, int? status, TimeSpan? retryAfter = null, double jitter = 0.5)
    {
        if (attempt >= Attempts || !Passing(status)) return null;
        if (retryAfter is { } asked && asked > TimeSpan.Zero) return asked < Longest ? asked : Longest;
        var seconds = Math.Min(Longest.TotalSeconds, 2 * Math.Pow(2, attempt - 1));
        return TimeSpan.FromSeconds(seconds * (0.75 + 0.5 * Math.Clamp(jitter, 0, 1)));
    }
}
