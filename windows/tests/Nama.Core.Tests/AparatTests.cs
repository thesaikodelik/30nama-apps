using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

/// <summary>As AparatTests.swift.</summary>
public class AparatTests
{
    [Fact]
    public void TheMostWatchedTrueTrailerIsTaken()
    {
        var videos = Aparat.VideosInSearch(Fixtures.Load("aparat_search_exorcist"));
        Assert.Equal(10, videos.Count);
        // 30nama's English title carries its year; the reactions to the trailer are passed over.
        var found = Aparat.Trailer(videos, "The Exorcist: Believer 2023", 2023);
        Assert.Equal("تریلر فیلم جن گیر: مومن The Exorcist: Believer", found?.Title);
    }

    [Fact]
    public void AShortNameNeedsItsYear()
    {
        var videos = Aparat.VideosInSearch(Fixtures.Load("aparat_search_up"));
        // "step up 6", a game's "Team Up" and "Up and Away 2018" are not the 2009 film.
        Assert.Equal("تریلر انیمیشن Up 2009", Aparat.Trailer(videos, "Up", 2009)?.Title);
        Assert.Null(Aparat.Trailer(videos, "Up", null));
    }

    [Fact]
    public void OnlyTrailersOfAFilmAndOfAFittingLength()
    {
        static Aparat.Video Video(string title, int duration = 120, int visits = 1) => new(title, title, duration, visits);
        var videos = new[]
        {
            Video("نقد و بررسی تریلر فیلم Inception", 140, 900),
            Video("تریلر Inception", 140, 800),
            Video("تریلر فیلم Inception ساخت هوش مصنوعی", 140, 700),
            Video("تریلر فیلم Inception", 1200, 600),
            Video("تریلر فیلم Inception 2024", 140, 500),
            Video("تریلر فیلم Inception با بازی لئوناردو دی کاپریو (زیرنویس فارسی)", 143, 50),
            Video("تریلر فیلم Inception ۲۰۱۰", 143, 40),
        };
        Assert.Equal(50, Aparat.Trailer(videos, "Inception", 2010)?.Visits);
        Assert.Equal("تریلر فیلم inception 2010 تلقین", Aparat.Words("تریلر فیلم Inception ۲۰۱۰ (تلقین)"));
    }

    [Fact]
    public void TheFileIs720pWhereThereIsOne()
    {
        Assert.Equal("https://example.com/aparat-video/abc-720p.mp4?wmsAuthSign=x", Aparat.FileInVideo(Fixtures.Load("aparat_video"))?.AbsoluteUri);
        Assert.Null(Aparat.FileInVideo(Json.Parse("""{"video":{}}""")));
    }

    [Fact]
    public void TheSearchAsksForTheTrailerByName()
    {
        using var request = Aparat.SearchRequest("Dune: Part Two 2024");
        Assert.Equal("www.aparat.com", request.RequestUri!.Host);
        Assert.Contains("videoBySearch/text/", request.RequestUri.AbsoluteUri);
        Assert.Contains("تریلر Dune: Part Two/perpage", Uri.UnescapeDataString(request.RequestUri.AbsoluteUri));
    }

    [Fact]
    public void ALogoInTheLanguageOfTheInterfaceThroughTheProxy()
    {
        var index = LogoIndex.From(Json.Parse("""{"version":1,"updated":"2026-10-25","logos":{"tt1":["en.png","fa.png"],"tt2":["only.png",""]}}"""));
        Assert.NotNull(index);
        Assert.Equal(2, index.Count);
        Assert.Equal("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/fa.png", index.Logo("tt1", Lang.Fa)?.OriginalString);
        Assert.Equal("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/en.png", index.Logo("tt1", Lang.En)?.OriginalString);
        Assert.Equal("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/only.png", index.Logo("tt2", Lang.Fa)?.OriginalString);
        Assert.Null(index.Logo("tt3", Lang.En));
        Assert.Null(index.Logo(null, Lang.En));
        Assert.Null(LogoIndex.From(Json.Parse("""{"version":2}""")));
    }

    [Fact]
    public void TheFilteredHostsAreKnownByTheirSuffix()
    {
        Assert.True(FilteredHosts.Contains("api.themoviedb.org"));
        Assert.True(FilteredHosts.Contains("interface.30nama.com"));
        Assert.True(FilteredHosts.Contains("www.YouTube.com"));
        Assert.False(FilteredHosts.Contains("www.aparat.com"));
        Assert.False(FilteredHosts.Contains("raw.githubusercontent.com"));
        Assert.False(FilteredHosts.Contains("not30nama.com"));
        Assert.False(FilteredHosts.Contains(null));
    }
}
