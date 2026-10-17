using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class EpisodeAlertsTests
{
    private static Post Series(int id, string? next)
    {
        var json = new Dictionary<string, object?> { ["id"] = id, ["title"] = new Dictionary<string, object?> { ["english"] = $"Show {id}" } };
        if (next is not null) json["air"] = new Dictionary<string, object?> { ["next_episode_date"] = new Dictionary<string, object?> { ["g"] = next } };
        return Post.List(Json.From(new Dictionary<string, object?> { ["posts"] = new[] { json } }))[0];
    }

    private static DateTime Day(string text) => DateTime.Parse(text + "T00:00:00Z", null, System.Globalization.DateTimeStyles.AdjustToUniversal);

    [Fact]
    public void TellsOfAnEpisodeOnceItsDayHasCome()
    {
        var alerts = new EpisodeAlerts();
        Assert.Empty(alerts.Update([Series(1, "2026-10-20")], Day("2026-10-16")));
        Assert.Empty(alerts.Update([Series(1, "2026-10-20")], Day("2026-10-19"))); // not yet
        Assert.Equal([1], alerts.Update([Series(1, "2026-10-27")], Day("2026-10-21")).Select(p => p.Id)); // the day passed, and the server has moved on
        Assert.Empty(alerts.Update([Series(1, "2026-10-27")], Day("2026-10-22"))); // said once
        Assert.Equal(Day("2026-10-27"), alerts.Expected[1]); // and the next one is awaited
    }

    [Fact]
    public void SaysNothingOfASeriesNoLongerFollowed()
    {
        var alerts = new EpisodeAlerts();
        alerts.Update([Series(1, "2026-10-20")], Day("2026-10-16"));
        Assert.Empty(alerts.Update([], Day("2026-10-21")));
        Assert.Empty(alerts.Expected);
    }

    [Fact]
    public void SurvivesBeingStored()
    {
        var alerts = new EpisodeAlerts();
        alerts.Update([Series(4, "2026-11-02")], Day("2026-10-16"));
        Assert.Equal(alerts.Expected, new EpisodeAlerts(alerts.Stored).Expected);
    }
}
