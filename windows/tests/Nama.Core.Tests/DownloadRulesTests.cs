using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class DownloadRulesTests
{
    [Fact]
    public void RateLimitKeepsToTheRate()
    {
        var limit = new RateLimit();
        Assert.Equal(TimeSpan.Zero, limit.Take(10_000_000, now: 0)); // no limit set
        limit.BytesPerSecond = 1000;
        Assert.Equal(0.5, limit.Take(500, now: 10).TotalSeconds, 3); // an empty bucket: half a second for half a second's worth
        Assert.Equal(0, limit.Take(500, now: 11).TotalSeconds, 3); // a second later the debt is paid, and this piece with it
        Assert.Equal(2, limit.Take(3000, now: 100).TotalSeconds, 3); // a long rest saves at most a second's worth
    }

    [Fact]
    public void FourConnectionsTogetherKeepToTheRate()
    {
        // Each connection reads a piece whenever it is free to, and then waits what it is told.
        var limit = new RateLimit { BytesPerSecond = 5_000_000 };
        var free = new double[4];
        long taken = 0;
        while (true)
        {
            var next = Array.IndexOf(free, free.Min());
            var now = free[next];
            if (now >= 60) break;
            taken += 65_536;
            free[next] = now + 0.002 + limit.Take(65_536, now).TotalSeconds;
        }
        // Sixty seconds' worth, and at most the one second's worth the bucket starts able to hold.
        Assert.InRange(taken / 60.0, 4_900_000, 5_000_000 * 61 / 60.0 + 4 * 65_536);
    }

    [Fact]
    public void AConnectionGivesWayOnlyWhileOthersWork()
    {
        Assert.True(Connections.GivesWay(null, 0, othersWorking: 2)); // unanswered
        Assert.True(Connections.GivesWay(429, 0, othersWorking: 1));
        Assert.True(Connections.GivesWay(503, 5_000_000, othersWorking: 1));
        Assert.False(Connections.GivesWay(null, 4096, othersWorking: 2)); // it was working, then dropped: tried again
        Assert.False(Connections.GivesWay(null, 0, othersWorking: 0)); // the only one: tried again
        Assert.False(Connections.GivesWay(404, 0, othersWorking: 3)); // a refusal is for all of them
    }
}
