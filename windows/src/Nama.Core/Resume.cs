namespace Nama.Core;

/// <summary>
/// Where to start a title. 30nama's own record lags behind: it keeps positions in steps of thirty
/// seconds, and an episode left part-way can come back as nowhere at all. So this computer keeps its
/// own point for each title too, and playback starts from whichever of the two is further on (later
/// in the list of files, then later in the file). As the Android app's Resume, learnt from 30namaTV.
/// </summary>
public static class Resume
{
    public sealed record Point(int Index, double? Seconds);

    /// <summary>A point this computer kept: the file, and the second in it.</summary>
    public sealed record Kept(string FileId, double Seconds);

    /// <summary>
    /// Where to start <paramref name="items"/>: <paramref name="chosen"/> is the file the user picked
    /// (or the server's), <paramref name="server"/> what 30nama remembers and <paramref name="kept"/>
    /// what this computer does. A position inside the last 3% is the credits: that file starts over.
    /// A position in the first five seconds is no position at all.
    /// </summary>
    public static Point Start(IReadOnlyList<StreamItem> items, int chosen, ContinueWatching? server, Kept? kept, bool pickedByUser)
    {
        static double? Position(double seconds, double duration) => seconds > 5 && (duration <= 0 || seconds < duration * 0.97) ? seconds : null;
        static double DurationOf(StreamItem item) => (item.DurationMinutes ?? 0) * 60.0;
        int? IndexOf(string? fileId) => fileId is null ? null : items.ToList().FindIndex(i => i.FileId == fileId) is var i and >= 0 ? i : null;

        // An episode chosen from the list starts there; only its own saved point can apply.
        if (pickedByUser)
        {
            if (chosen < 0 || chosen >= items.Count) return new Point(chosen, null);
            var fileId = items[chosen].FileId;
            var fromServer = server?.FileId == fileId ? Position(server.Time, server.Duration) : null;
            var fromHere = kept?.FileId == fileId ? Position(kept.Seconds, DurationOf(items[chosen])) : null;
            return new Point(chosen, new[] { fromServer, fromHere }.Max());
        }
        (int Index, double Seconds, double Duration)? best = null;
        if (IndexOf(server?.FileId) is { } s) best = (s, server!.Time, server.Duration);
        if (IndexOf(kept?.FileId) is { } k && (best is not { } b || k > b.Index || (k == b.Index && kept!.Seconds > b.Seconds)))
            best = (k, kept!.Seconds, DurationOf(items[k]));
        if (best is not { } found) return new Point(chosen, null);
        // Left in its credits, and another follows: that one is next, not this one again from the start.
        if (found.Duration > 0 && found.Seconds >= found.Duration * 0.97 && found.Index < items.Count - 1) return new Point(found.Index + 1, null);
        return new Point(found.Index, Position(found.Seconds, found.Duration));
    }
}
