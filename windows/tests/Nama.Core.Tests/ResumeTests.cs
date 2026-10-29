using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

/// <summary>As the Android app's ResumeTest.</summary>
public class ResumeTests
{
    private static readonly List<StreamItem> Items = Enumerable.Range(1, 4)
        .Select(i => new StreamItem { FileId = $"f{i}", Season = 1, EpisodeRaw = $"{i}", DurationMinutes = 40 }).ToList();

    private static ContinueWatching Server(string file, double time) => new() { FileId = file, Time = time, Duration = 2400 };

    [Fact]
    public void TheFurtherPointWins()
    {
        // The server rounds down to thirty seconds; this computer knows the exact second.
        var p = Resume.Start(Items, 1, Server("f2", 600), new Resume.Kept("f2", 623), pickedByUser: false);
        Assert.Equal(1, p.Index);
        Assert.Equal(623, p.Seconds);
    }

    [Fact]
    public void ALaterEpisodeHereWins()
    {
        var p = Resume.Start(Items, 1, Server("f2", 1800), new Resume.Kept("f3", 120), pickedByUser: false);
        Assert.Equal(2, p.Index);
        Assert.Equal(120, p.Seconds);
    }

    [Fact]
    public void TheServerWinsWhenItIsFurther()
    {
        // Watched on another device since.
        var p = Resume.Start(Items, 0, Server("f4", 300), new Resume.Kept("f2", 900), pickedByUser: false);
        Assert.Equal(3, p.Index);
    }

    [Fact]
    public void TheCreditsGoOnToTheNext()
    {
        var p = Resume.Start(Items, 0, null, new Resume.Kept("f1", 2390), pickedByUser: false);
        Assert.Equal(1, p.Index);
        Assert.Null(p.Seconds);
    }

    [Fact]
    public void TheLastOnesCreditsStartOver()
    {
        var p = Resume.Start(Items, 0, null, new Resume.Kept("f4", 2390), pickedByUser: false);
        Assert.Equal(3, p.Index);
        Assert.Null(p.Seconds);
    }

    [Fact]
    public void AChosenEpisodeStartsThere()
    {
        var p = Resume.Start(Items, 3, Server("f2", 600), new Resume.Kept("f4", 400), pickedByUser: true);
        Assert.Equal(3, p.Index);
        Assert.Equal(400, p.Seconds);
    }

    [Fact]
    public void NothingSavedStartsAtTheBeginning()
    {
        var p = Resume.Start(Items, 0, null, null, pickedByUser: false);
        Assert.Equal(0, p.Index);
        Assert.Null(p.Seconds);
    }
}
