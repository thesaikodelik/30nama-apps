using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class SiteLinkTests
{
    private static Post Title(int id, string english, int? year, bool series = false) =>
        Post.Parse(Json.Parse($$$"""{"id":{{{id}}},"title":{"english":"{{{english}}}"},"info":{"year":{{{(year?.ToString() ?? "null")}}}},"options":{"is_series":{{{(series ? "true" : "false")}}}}}"""))!;

    [Fact]
    public void AsTheSitesOwnLinks()
    {
        // Links as they are in the site's own articles (30 October 2026).
        Assert.Equal("https://30nama.com/movie/4705/X-Men-2000", SiteLink.For(Title(4705, "X-Men", 2000)).AbsoluteUri);
        Assert.Equal("https://30nama.com/movie/582455/The-Batman-Part-II-2028", SiteLink.For(Title(582455, "The Batman: Part II", 2028)).AbsoluteUri);
        Assert.Equal("https://30nama.com/movie/128728/The-Death-of-Stalin-2017", SiteLink.For(Title(128728, "The Death of Stalin", 2017)).AbsoluteUri);
    }

    [Fact]
    public void ASeriesAndTheEdges()
    {
        Assert.Equal("https://30nama.com/series/1/Friends-1994", SiteLink.For(Title(1, "Friends", 1994, series: true)).AbsoluteUri);
        // A year already at the end of the name is not written twice.
        Assert.Equal("https://30nama.com/movie/2/Up-2009", SiteLink.For(Title(2, "Up 2009", 2009)).AbsoluteUri);
        // Punctuation and spaces together are one hyphen.
        Assert.Equal("https://30nama.com/movie/3/Spider-Man-Across-the-Spider-Verse-2023", SiteLink.For(Title(3, "Spider-Man: Across the Spider-Verse", 2023)).AbsoluteUri);
        Assert.Equal("https://30nama.com/movie/4", SiteLink.For(Title(4, "", null)).AbsoluteUri);
    }
}
