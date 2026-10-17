namespace Nama.Core;

/// <summary>
/// The hours of the day downloads may run in, for a line that is cheaper or freer at night. From
/// 30nama's own desktop app. <c>From</c> to <c>To</c> in whole hours of local time, and across
/// midnight when <c>From</c> is the later one: 2 to 8 is the early morning, 23 to 7 the night.
/// </summary>
public static class DownloadHours
{
    /// <summary>Whether downloads may run at <paramref name="local"/>. No hours set, or the same hour twice, is any time.</summary>
    public static bool Open(int? from, int? to, DateTime local)
    {
        if (from is not { } start || to is not { } end || start == end) return true;
        var hour = local.Hour;
        return start < end ? hour >= start && hour < end : hour >= start || hour < end;
    }
}
