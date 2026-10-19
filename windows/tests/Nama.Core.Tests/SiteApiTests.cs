using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class SiteApiTests
{
    [Fact]
    public void SitePostsAreReadInTheAppShape()
    {
        var page = SitePosts.Page(Fixtures.Load("site_advanced_search")["result"]);
        Assert.Equal(1, page.Page);
        Assert.Equal(4, page.Pages);
        Assert.Equal(2, page.Posts.Count);

        var film = page.Posts[0];
        Assert.Equal(572188, film.Id);
        Assert.Equal(Post.Kinds.Movie, film.Kind);
        Assert.False(film.IsSeries);
        Assert.True(film.HasStream);
        Assert.Equal("The Exorcist: Believer 2023", film.TitleEnglish);
        Assert.Equal(2023, film.Year);
        Assert.Equal(4.8, film.ImdbScore);
        Assert.Equal(40478, film.ImdbVotes);
        Assert.Equal("tt12921446", film.ImdbId);
        Assert.Equal(5.8, film.SiteScore);
        Assert.Equal(["horror"], film.Genres.Select(g => g.Slug));
        Assert.Equal("https://example.com/poster/30572188-m_30NAMA.webp?", film.Poster.Medium?.OriginalString);
        Assert.Equal("https://example.com/poster/30572188-l_30NAMA.jpg?", film.Poster.Jpeg?.OriginalString);
        Assert.Equal("https://example.com/cover/30572188_30NAMA.webp?", film.Cover?.OriginalString);

        var series = page.Posts[1];
        Assert.True(series.IsSeries);
        Assert.Equal(Post.Kinds.Series, series.Kind);
        Assert.Equal(2022, series.Year);
    }

    [Fact]
    public void TheYearIsOnlyTakenFromTheEndOfTheTitle()
    {
        Assert.Equal(2017, SitePosts.Parse(Json.Parse("""{"id":1,"title":"Blade Runner 2049 2017"}"""))?.Year);
        Assert.Null(SitePosts.Parse(Json.Parse("""{"id":2,"title":"Untitled Tomb Raider Project"}"""))?.Year);
        Assert.Null(SitePosts.Parse(Json.Parse("""{"title":"No id 2020"}""")));
    }

    [Fact]
    public void FilmographyAsksTheSiteByImdbIdNewestFirst()
    {
        var (path, form) = SiteQuery.Filmography("nm0000995", 2);
        Assert.Equal("advanced_search/page/2/orderby/year/order/desc", path);
        Assert.Equal("""{"cast":"nm0000995"}""", form["parameters"]);
        Assert.Single(form);
    }

    [Fact]
    public async Task SiteCallsGoToTheSiteHostWithItsKeyAndAForm()
    {
        var net = new FakeNetwork((_, _) => (200, """{"success":true,"msg":null,"result":{"page":1,"pages":1,"posts":[]}}""", []));
        var api = new ApiClient(new ApiConfig { RequestSpacing = TimeSpan.Zero, SiteKey = "site-key" }, net.Transport);
        Assert.True(api.HasSite);
        var (path, form) = SiteQuery.Filmography("nm1", 1);
        await api.SiteAsync(path, form);
        var last = net.Last!;
        Assert.Equal("interface.30nama.com", last.Host);
        Assert.Equal("desc", last.Action);
        Assert.Equal("site-key", last.Headers["c-api-key"]);
        Assert.Equal("Website", last.Headers["c-platform"]);
        Assert.Equal("parameters=%7B%22cast%22%3A%22nm1%22%7D", last.Body);
    }

    [Fact]
    public async Task WithoutAKeyTheSiteIsNotAsked()
    {
        var net = new FakeNetwork((_, _) => (200, "{}", []));
        var api = new ApiClient(new ApiConfig { SiteKey = null }, net.Transport);
        Assert.False(api.HasSite);
        var e = await Assert.ThrowsAsync<ApiException>(() => api.SiteAsync("schedule/type/series"));
        Assert.Equal(ApiException.Kinds.Unreachable, e.Kind);
        Assert.Empty(net.Calls);
    }

    [Fact]
    public void CastMembersKeepTheirImdbId() =>
        Assert.Equal("nm0000255", Post.Parse(Fixtures.Load("single_movie")["result"])?.Cast[0].Imdb);
}
