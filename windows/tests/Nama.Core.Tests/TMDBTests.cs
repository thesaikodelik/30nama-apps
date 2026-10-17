using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class TMDBTests
{
    [Fact]
    public void KeysOfBothKinds()
    {
        using var bearer = TMDB.FindRequest("tt1", "eyJhbGciOi");
        Assert.Equal("Bearer eyJhbGciOi", bearer.Headers.Authorization?.ToString());
        Assert.DoesNotContain("api_key", bearer.RequestUri!.AbsoluteUri);
        using var plain = TMDB.FindRequest("tt1", "abc123");
        Assert.Null(plain.Headers.Authorization);
        Assert.Contains("api_key=abc123", plain.RequestUri!.AbsoluteUri);
    }

    [Fact]
    public void FindsTheTitle()
    {
        var found = Json.From(new Dictionary<string, object?>
        {
            ["movie_results"] = new[] { new Dictionary<string, object?> { ["id"] = 7 } },
            ["tv_results"] = new[] { new Dictionary<string, object?> { ["id"] = 9 } },
        });
        Assert.Equal(7, TMDB.IdInFind(found, false));
        Assert.Equal(9, TMDB.IdInFind(found, true));
    }

    private static readonly Json Film = Json.Parse("""
        {
          "images": {"logos": [
            {"file_path": "/de.png", "iso_639_1": "de", "vote_average": 9},
            {"file_path": "/en-low.png", "iso_639_1": "en", "vote_average": 2},
            {"file_path": "/en.svg", "iso_639_1": "en", "vote_average": 5}
          ]},
          "videos": {"results": [
            {"key": "teaser", "site": "YouTube", "type": "Teaser", "official": true},
            {"key": "fan", "site": "YouTube", "type": "Trailer", "official": false},
            {"key": "real", "site": "YouTube", "type": "Trailer", "official": true}
          ]},
          "credits": {"crew": [
            {"name": "Sam Grip", "job": "Gaffer"},
            {"name": "Ann Write", "job": "Screenplay", "profile_path": "/a.jpg"},
            {"name": "Dee Rect", "job": "Director"},
            {"name": "Dee Rect", "job": "Screenplay"}
          ]},
          "reviews": {"results": [{"author": "critic", "content": "Fine.", "author_details": {"rating": 7}}, {"author": "empty", "content": ""}]}
        }
        """);

    [Fact]
    public void ReadsWhatAPageShows()
    {
        var info = TMDB.InfoIn(Film, Lang.En);
        Assert.Equal("https://image.tmdb.org/t/p/w500/en.png", info.Logo?.AbsoluteUri); // English, the best voted, as PNG
        Assert.Equal("real", info.Trailer);
        Assert.Equal(["Dee Rect", "Ann Write"], info.Crew.Select(c => c.Name)); // director first, each person once, no gaffer
        Assert.Equal("https://image.tmdb.org/t/p/w185/a.jpg", info.Crew[^1].Photo?.AbsoluteUri);
        Assert.Equal([new TMDB.Review("critic", "Fine.", 7, null)], info.Reviews);
    }

    [Fact]
    public void SeriesCrewAndCreators()
    {
        var series = Json.Parse("""
            {"created_by": [{"name": "Cree Ator"}],
             "aggregate_credits": {"crew": [{"name": "Com Poser", "jobs": [{"job": "Original Music Composer"}]}]}}
            """);
        Assert.Equal(["Creator", "Original Music Composer"], TMDB.InfoIn(series, Lang.Fa).Crew.Select(c => c.Job));
    }

    [Fact]
    public void ReadsTheLogoFromThePublicPage()
    {
        const string html = """<img src="https://media.themoviedb.org/t/p/w500/aEbOsxbcx4.png"><img src="/t/p/w500/second.png">""";
        Assert.Equal("https://image.tmdb.org/t/p/w500/aEbOsxbcx4.png", TMDB.LogoInPage(html)?.AbsoluteUri);
        Assert.Null(TMDB.LogoInPage("<html>no logos</html>"));
        var page = new Uri("https://www.themoviedb.org/movie/475557-joker");
        Assert.Equal("https://www.themoviedb.org/movie/475557-joker/images/logos?image_language=en", TMDB.LogosPage(page, "en")?.AbsoluteUri);
        Assert.Null(TMDB.LogosPage(new Uri("https://www.themoviedb.org/search"), "en")); // not a title page
    }

    [Fact]
    public async Task ComesBackWithTheCookieTheSiteAsksFor()
    {
        var cookies = new List<string?>();
        Transport site = (request, _) =>
        {
            var cookie = request.Headers.TryGetValues("Cookie", out var v) ? v.Single() : null;
            cookies.Add(cookie);
            var answer = cookie == "tmdb_ok=abc.123"
                ? new HttpResponseMessage(System.Net.HttpStatusCode.OK) { Content = new StringContent("<p>the page</p>") }
                : new HttpResponseMessage(System.Net.HttpStatusCode.Forbidden) { Content = new StringContent("""<body data-cookie="tmdb_ok" data-token="abc.123">Just a moment...</body>""") };
            answer.RequestMessage = new HttpRequestMessage(HttpMethod.Get, "http://www.themoviedb.org:443/movie/1-a");
            return Task.FromResult(answer);
        };
        var tmdb = new TmdbSite(site);
        var page = await tmdb.GetAsync(new Uri("https://www.themoviedb.org/redirect?x=1"));
        Assert.Equal("<p>the page</p>", page?.Html);
        Assert.Equal("https://www.themoviedb.org/movie/1-a", page?.Address.AbsoluteUri);
        await tmdb.GetAsync(new Uri("https://www.themoviedb.org/other"));
        Assert.Equal([null, "tmdb_ok=abc.123", "tmdb_ok=abc.123"], cookies); // the cookie is kept for the next page
    }

    [Fact]
    public void ReadsTheTrailerFromThePublicPage()
    {
        Assert.Equal("t433PEQGErc", TMDB.TrailerInPage("""<a data-site="YouTube" data-id="t433PEQGErc" href="#">"""));
        Assert.Null(TMDB.TrailerInPage("<p>none</p>"));
        Assert.Equal("https://www.themoviedb.org/tv/196322-dark-matter/videos?active_nav_item=Trailers&language=en-US",
            TMDB.TrailersPage(new Uri("https://www.themoviedb.org/tv/196322-dark-matter"))?.AbsoluteUri);
    }
}
