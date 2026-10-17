using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

// After the tests of Tachyon's segment.rs.
public class SegmentsTests
{
    private const long MB = 1024 * 1024;

    private static void Contiguous(List<SegmentState> states, long size)
    {
        Assert.Equal(0, states[0].Start);
        for (var i = 1; i < states.Count; i++) Assert.Equal(states[i - 1].End, states[i].Start); // no gap, no overlap
        Assert.Equal(size, states[^1].End);
    }

    [Fact]
    public void SplitsTheLargestInHalf()
    {
        var map = SegmentMap.Whole(100 * MB);
        var first = map.TakeUnowned()!;
        var second = map.SplitLargest()!;
        Assert.Equal(first.End, second.Start);
        Assert.Equal(50 * MB, second.Start);
        Assert.Equal(25 * MB, map.SplitLargest()!.Start); // both have 50 MB left; the earlier one is split
        Contiguous(map.Snapshot(), 100 * MB);
    }

    [Fact]
    public void ASplitCountsWhatHasArrived()
    {
        var map = SegmentMap.Whole(100 * MB);
        var segment = map.TakeUnowned()!;
        using var file = new MemoryStream(new byte[100 * MB]);
        var chunk = new byte[60 * MB];
        Assert.Equal(chunk.Length, segment.Write(file, chunk));
        Assert.Equal(80 * MB, map.SplitLargest()!.Start); // 40 MB were left after 60; the split lands halfway
        Assert.Equal(20 * MB, segment.Write(file, chunk)); // writing stops at the new boundary
        Assert.True(segment.IsComplete);
        Contiguous(map.Snapshot(), 100 * MB);
    }

    [Fact]
    public void RefusesTinySplits()
    {
        var map = SegmentMap.Whole(MB + MB / 2);
        map.TakeUnowned();
        Assert.Null(map.SplitLargest());
        Assert.Null(new SegmentMap([new SegmentState(0, 0, 100 * MB)], splittable: false).SplitLargest());
    }

    [Fact]
    public void LeftoversAreTakenOnce()
    {
        var map = new SegmentMap([new SegmentState(0, 10, 10), new SegmentState(10, 15, 20), new SegmentState(20, 22, 30)], splittable: true);
        Assert.Equal(10, map.TakeUnowned()!.Start);
        Assert.Equal(20, map.TakeUnowned()!.Start);
        Assert.Null(map.TakeUnowned());
        Assert.Equal(10 + 5 + 2, map.Downloaded);
    }

    [Fact]
    public void AReleasedSegmentIsTakenAgain()
    {
        var map = SegmentMap.Whole(10 * MB, have: 3 * MB);
        var segment = map.TakeUnowned()!;
        Assert.Equal(3 * MB, segment.Pos); // a single file from an older run goes on where it ended
        Assert.Null(map.TakeUnowned());
        segment.Release();
        Assert.Same(segment, map.TakeUnowned());
    }

    [Fact]
    public void ManyWritersFillTheFileExactly()
    {
        const long size = 8 * MB + 12345;
        var map = SegmentMap.Whole(size);
        var file = new MemoryStream(new byte[size]);
        var written = 0L;
        Parallel.For(0, 4, _ =>
        {
            var chunk = new byte[64 * 1024];
            Array.Fill(chunk, (byte)1);
            while (map.NextWork() is { } segment)
            {
                int n;
                lock (file)
                {
                    while ((n = segment.Write(file, chunk)) > 0) Interlocked.Add(ref written, n);
                }
            }
        });
        Assert.True(map.IsComplete);
        Assert.Equal(size, written);
        Assert.All(file.ToArray(), b => Assert.Equal(1, b));
        Contiguous(map.Snapshot(), size);
    }
}
