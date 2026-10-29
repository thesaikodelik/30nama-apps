using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

/// <summary>As the Android app's DirectTest; libmpv plays a TrueHD file, so none is passed over here.</summary>
public class DirectTests
{
    private static Dictionary<string, object?> File(string name, string quality, string size) =>
        new() { ["dl"] = $"https://example.com/{name}", ["quality"] = quality, ["size"] = size };

    private static DownloadOptions Film(params Dictionary<string, object?>[] files) =>
        new(Json.From(new Dictionary<string, object?> { ["download"] = files }));

    [Fact]
    public void AFilmTakesTheSharpestThenLargestFile()
    {
        var options = Film(
            File("a.1080p.mkv", "1080p BluRay", "2.1 GB"),
            File("a.2160p.TrueHD.mkv", "2160p REMUX", "40 GB"),
            File("a.2160p.mkv", "2160p WEB-DL HDR", "12 GB"),
            File("a.720p.mkv", "720p", "900 MB"));
        Assert.Equal("a.2160p.TrueHD.mkv", Direct.Pick(options, isSeries: false, null, null)?.Url.LastPathComponent());
    }

    [Fact]
    public void AnEpisodeTakesItsOwnFile()
    {
        static Dictionary<string, object?> Pack(int season, params (string Name, string Episode)[] links) => new()
        {
            ["season"] = season, ["quality"] = "1080p", ["size"] = "1 GB",
            ["link"] = links.Select(l => new Dictionary<string, object?> { ["dl"] = $"https://example.com/{l.Name}", ["episode"] = l.Episode }).ToArray(),
        };
        var options = new DownloadOptions(Json.From(new Dictionary<string, object?>
        {
            ["download"] = new Dictionary<string, object?> { ["s"] = new[] { Pack(1, ("s1e1.mkv", "1"), ("s1e2.mkv", "2")), Pack(2, ("s2e2.mkv", "2")) } },
        }));
        Assert.Equal("s1e2.mkv", Direct.Pick(options, isSeries: true, 1, "02")?.Url.LastPathComponent());
        Assert.Null(Direct.Pick(options, isSeries: true, 3, "1"));
    }

    [Fact]
    public void SizesAndLabels()
    {
        Assert.Equal(2457.6, Direct.Megabytes("2.4 GB"), 2);
        Assert.Equal(781, Direct.Megabytes("781 MB"), 2);
        var options = Film(File("b.mkv", "2160p HDR x265", "10 GB"));
        var file = Direct.Pick(options, false, null, null)!;
        Assert.Equal("2160p · HDR · x265", Direct.Label(file));
        Assert.Equal(2160, Direct.AsQuality(file).Height);
    }
}
