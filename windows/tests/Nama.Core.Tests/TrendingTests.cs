using System.Net;
using System.Text;
using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

/// <summary>Scripted TMDB and 30nama search: answers from tables and counts what was asked.</summary>
internal sealed class FakeCatalogue
{
    private readonly object _lock = new();
    private readonly List<HttpRequestMessage> _tmdbLog = [];
    private readonly List<string> _searchLog = [];

    /// <summary>TMDB list entries by request path.</summary>
    public Dictionary<string, List<Dictionary<string, object?>>> Lists { get; } = [];
    /// <summary>30nama search results by query.</summary>
    public Dictionary<string, List<Dictionary<string, object?>>> Shelves { get; set; } = [];
    public int TmdbStatus { get; set; } = 200;
    public bool SearchFails { get; set; }

    public List<HttpRequestMessage> TmdbRequests { get { lock (_lock) return [.. _tmdbLog]; } }
    public List<string> Searches { get { lock (_lock) return [.. _searchLog]; } }

    public Transport Transport => (request, _) =>
    {
        lock (_lock) _tmdbLog.Add(request);
        var url = request.RequestUri!;
        var path = url.AbsolutePath.StartsWith("/3") ? url.AbsolutePath[2..] : url.AbsolutePath;
        var page = System.Web.HttpUtility.ParseQueryString(url.Query)["page"] ?? "1";
        var results = page == "1" && Lists.TryGetValue(path, out var list) ? list : [];
        var body = Json.From(new Dictionary<string, object?> { ["results"] = results }).Serialize();
        return Task.FromResult(new HttpResponseMessage((HttpStatusCode)TmdbStatus) { Content = new StringContent(body, Encoding.UTF8) });
    };

    public Trending.Search Search => query =>
    {
        lock (_lock) _searchLog.Add(query);
        if (SearchFails) throw new ApiException(ApiException.Kinds.Transient, "search");
        return Task.FromResult(Json.From(new Dictionary<string, object?> { ["posts"] = Shelves.TryGetValue(query, out var s) ? s : [] }));
    };
}

public class TrendingTests
{
    private static Dictionary<string, object?> Tmdb(int id, string title, int year = 2024, bool tv = false, string? type = null,
        string? original = null, bool poster = true, double popularity = 10, int votes = 500)
    {
        var j = new Dictionary<string, object?> { ["id"] = id, ["popularity"] = popularity, ["vote_count"] = votes };
        j[tv ? "name" : "title"] = title;
        j[tv ? "first_air_date" : "release_date"] = $"{year}-05-01";
        if (original is not null) j[tv ? "original_name" : "original_title"] = original;
        if (type is not null) j["media_type"] = type;
        if (poster) j["poster_path"] = $"/p{id}.jpg";
        return j;
    }

    private static Dictionary<string, object?> PostJson(int id, string title, int year = 2024, bool series = false, bool stream = true, bool soon = false) => new()
    {
        ["id"] = id,
        ["title"] = new Dictionary<string, object?> { ["english"] = $"{title} {year}", ["local"] = $"عنوان {id}" },
        ["info"] = new Dictionary<string, object?> { ["year"] = year },
        ["options"] = new Dictionary<string, object?> { ["is_series"] = series, ["stream"] = stream, ["coming_soon"] = soon },
        ["image"] = new Dictionary<string, object?> { ["poster"] = new Dictionary<string, object?> { ["webp"] = new Dictionary<string, object?> { ["large"] = $"https://example.com/{id}-l.webp" } } },
        ["cast"] = new[] { new Dictionary<string, object?> { ["name"] = "Someone" } },
    };

    private static TMDBItem Item(Dictionary<string, object?> json, TMDBItem.Kinds? kind = null) => TMDBItem.Parse(Json.From(json), kind)!;

    private static List<Post> Posts(params Dictionary<string, object?>[] list) => list.Select(p => Post.Parse(Json.From(p))).OfType<Post>().ToList();

    private static readonly string[] Words = Enumerable.Range(0, 41).Select(i => $"N{i}x").ToArray();

    /// <summary>Movies trending on TMDB, all of them on 30nama under the same names.</summary>
    private static FakeCatalogue Stocked(int count = 6)
    {
        var net = new FakeCatalogue();
        net.Lists["/trending/all/week"] = Enumerable.Range(1, count).Select(i => Tmdb(i, $"Film {Words[i]}", type: "movie")).ToList();
        for (var i = 1; i <= count; i++) net.Shelves[$"Film {Words[i]}"] = [PostJson(100 + i, $"Film {Words[i]}")];
        return net;
    }

    // Matching

    [Fact]
    public void NormalizeAndSimilarity()
    {
        Assert.Equal("amelie and co the return", TitleMatch.Normalize("Amélie & Co.: The  Return!"));
        Assert.Equal("بازی مرکب", TitleMatch.Normalize("  بازی مرکب  "));
        Assert.Equal(1, TitleMatch.Similarity("Spider-Man", "spider man"));
        Assert.Equal(0, TitleMatch.Similarity("", "anything"));
        Assert.Equal(0.5, TitleMatch.Similarity("The Bear", "The Boys"));
        Assert.Equal(0.82, TitleMatch.Similarity("Dune", "Dune Part Two")); // a title that only adds a subtitle counts as close
        Assert.True(TitleMatch.Similarity("Dune", "Dunes of Mars") < 0.8);
    }

    [Fact]
    public void StripYear()
    {
        Assert.Equal("Animals", TitleMatch.StripYear("Animals 2026"));
        Assert.Equal("Animals", TitleMatch.StripYear("Animals (2026)"));
        Assert.Equal("Blade Runner", TitleMatch.StripYear("Blade Runner 2049"));
        Assert.Equal("2012", TitleMatch.StripYear("2012")); // a title that is only a year stays
        Assert.Equal("Severance", TitleMatch.StripYear("Severance"));
    }

    [Fact]
    public void BestPostWantsTheSameKindAndYear()
    {
        var dune = Item(Tmdb(1, "Dune", year: 2021), TMDBItem.Kinds.Movie);
        Assert.Equal(2, TitleMatch.BestPost(Posts(PostJson(1, "Dune", year: 1984), PostJson(2, "Dune", year: 2021)), dune)?.Id);
        Assert.Equal(3, TitleMatch.BestPost(Posts(PostJson(3, "Dune", year: 2022)), dune)?.Id); // one year off is still the same title
        Assert.Null(TitleMatch.BestPost(Posts(PostJson(4, "Dune", year: 1984)), dune));
        Assert.Null(TitleMatch.BestPost(Posts(PostJson(5, "Dune", year: 2021, series: true)), dune)); // a series is not a movie
        Assert.Null(TitleMatch.BestPost(Posts(PostJson(6, "Dune", year: 2021, soon: true)), dune)); // upcoming titles are skipped
        Assert.Null(TitleMatch.BestPost(Posts(PostJson(7, "Dunkirk", year: 2021)), dune));
    }

    [Fact]
    public async Task ATitleBuriedUnderItsNamesakesIsFoundByItsYear()
    {
        var net = Stocked();
        net.Shelves["Film N3x"] = [PostJson(900, "Film N3x", year: 2001), PostJson(901, "Film N3x", year: 1990)];
        net.Shelves["Film N3x 2024"] = [PostJson(103, "Film N3x")];
        var row = await new Trending("t", net.Search, transport: net.Transport).RowAsync(TrendingRow.Trend);
        Assert.Equal([101, 102, 103, 104, 105, 106], row.Select(p => p.Id));
    }

    [Fact]
    public void BestPostPrefersWhatCanBeStreamed()
    {
        var dune = Item(Tmdb(1, "Dune", year: 2021), TMDBItem.Kinds.Movie);
        Assert.Equal(2, TitleMatch.BestPost(Posts(PostJson(1, "Dune", year: 2021, stream: false), PostJson(2, "Dune", year: 2021)), dune)?.Id);
    }

    [Fact]
    public void BestPostMatchesTheOriginalTitle()
    {
        var heist = Item(Tmdb(1, "Money Heist", year: 2017, tv: true, original: "La casa de papel"), TMDBItem.Kinds.Tv);
        Assert.Equal(9, TitleMatch.BestPost(Posts(PostJson(9, "La Casa de Papel", year: 2017, series: true)), heist)?.Id);
        Assert.Equal(["Money Heist", "La casa de papel", "Money Heist 2017"], TitleMatch.Queries(heist));
        var squid = Item(Tmdb(2, "Squid Game", tv: true, original: "오징어 게임"), TMDBItem.Kinds.Tv);
        Assert.Equal(["Squid Game", "Squid Game 2024"], TitleMatch.Queries(squid)); // an original title 30nama cannot be searched by is left out
    }

    [Fact]
    public void BestPostAgainstALiveSearchResponse()
    {
        var results = Post.List(Fixtures.Load("search")["result"]);
        var series = Item(Tmdb(1396, "Breaking Bad", year: 2008, tv: true), TMDBItem.Kinds.Tv);
        var film = Item(Tmdb(559969, "El Camino: A Breaking Bad Movie", year: 2019), TMDBItem.Kinds.Movie);
        Assert.Equal(12382, TitleMatch.BestPost(results, series)?.Id);
        Assert.Equal(228418, TitleMatch.BestPost(results, film)?.Id);
    }

    // Lists

    [Fact]
    public void ItemsKnowTheirKind()
    {
        Assert.Equal(TMDBItem.Kinds.Tv, Item(Tmdb(1, "A", tv: true, type: "tv")).Kind);
        Assert.Equal(TMDBItem.Kinds.Tv, Item(Tmdb(1, "A", tv: true)).Kind); // without a type, an air date means a series
        Assert.Equal(TMDBItem.Kinds.Movie, Item(Tmdb(1, "A")).Kind);
        Assert.Equal(2019, Item(Tmdb(1, "A", year: 2019)).Year);
        Assert.Null(TMDBItem.Parse(Json.Parse("""{"id": 7, "media_type": "person", "name": "Someone"}"""))); // people are not titles
    }

    [Fact]
    public void CandidatesDropDuplicatesAndTitlesWithoutAPoster()
    {
        TMDBItem a = Item(Tmdb(1, "A")), b = Item(Tmdb(2, "B")), bare = Item(Tmdb(3, "C", poster: false));
        Assert.Equal([1, 2], TrendingRow.Pop.Candidates([[a, bare, b], [b, a]]).Select(i => i.Id));
    }

    [Fact]
    public void FreshSeriesPutTheBestKnownFirst()
    {
        var quiet = Item(Tmdb(1, "Quiet", tv: true, popularity: 20, votes: 250), TMDBItem.Kinds.Tv);
        var obscure = Item(Tmdb(2, "Obscure", tv: true, popularity: 900, votes: 12), TMDBItem.Kinds.Tv);
        var hit = Item(Tmdb(3, "Hit", tv: true, popularity: 80, votes: 9000), TMDBItem.Kinds.Tv);
        Assert.Equal([3, 1], TrendingRow.Fresh.Candidates([[quiet, obscure, hit]]).Select(i => i.Id));
        Assert.Equal([1, 2, 3], TrendingRow.Trend.Candidates([[quiet, obscure, hit]]).Select(i => i.Id)); // other rows keep TMDB's order
    }

    [Fact]
    public void FreshSeriesAreAskedForTheLastThirtyDays()
    {
        var sources = TrendingRow.Fresh.Sources(DateTime.UnixEpoch.AddSeconds(1_780_000_000)); // 2026-05-28 UTC
        Assert.Equal(["1", "2", "3"], sources.Select(s => s.Params["page"]));
        Assert.Equal("2026-04-28", sources[0].Params["air_date.gte"]);
    }

    // Rows

    [Fact]
    public async Task ReadsThePublicPanelWithoutAKey()
    {
        const string html = """<a data-media-type="tv" href="/tv/1-show-one?language=en-US"><h2 class="x"><span>Film N1x</span></h2></a></div><span class="release_date w-full">May 1, 2024</span>"""
            + """<a data-media-type="movie" href="/movie/2-two?language=en-US"><h2 class="x"><span>Film N2x &amp; Co</span></h2></a></div><span class="release_date w-full">May 3, 2024</span>""";
        var items = TMDBItem.List(TMDBItem.PublicList(html));
        Assert.Equal(["Film N1x", "Film N2x & Co"], items.Select(i => i.Title));
        Assert.Equal([TMDBItem.Kinds.Tv, TMDBItem.Kinds.Movie], items.Select(i => i.Kind));
        Assert.Equal([2024, 2024], items.Select(i => i.Year));
        Assert.All(items, i => Assert.True(i.HasPoster));

        var net = new FakeCatalogue();
        Transport page = (request, _) =>
        {
            // The shared file first; GitHub not answering, the public page.
            if (request.RequestUri == Trending.SharedTrending) return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound));
            Assert.Equal(TMDBItem.PublicTrending, request.RequestUri);
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(html, Encoding.UTF8) });
        };
        var trending = new Trending("  \n", net.Search, transport: page);
        await trending.RowAsync(TrendingRow.Trend);
        Assert.Equal(["Film N1x"], net.Searches.Take(1)); // the public list is looked for on 30nama
        Assert.Empty(await trending.RowAsync(TrendingRow.Fresh)); // rows that need the API stay empty without a key
    }

    // As the Android app: without a key, the lists written each day on the data branch.
    [Fact]
    public async Task ReadsTheSharedListsWithoutAKey()
    {
        var net = Stocked();
        var shared = Json.From(new Dictionary<string, object?>
        {
            ["lists"] = new Dictionary<string, object?> { ["/trending/all/week?language=en-US"] = net.Lists["/trending/all/week"] },
        }).Serialize();
        var asked = new List<Uri>();
        Transport github = (request, _) =>
        {
            lock (asked) asked.Add(request.RequestUri!);
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(shared, Encoding.UTF8) });
        };
        var row = await new Trending(null, net.Search, transport: github).RowAsync(TrendingRow.Trend);
        Assert.Equal([101, 102, 103, 104, 105, 106], row.Select(p => p.Id));
        Assert.Equal([Trending.SharedTrending], asked.Distinct());
    }

    [Fact]
    public async Task RowFindsTrendingTitlesOn30nama()
    {
        var net = Stocked();
        var row = await new Trending(" eyJsecret\n", net.Search, transport: net.Transport).RowAsync(TrendingRow.Trend);
        Assert.Equal([101, 102, 103, 104, 105, 106], row.Select(p => p.Id)); // in TMDB's order
        Assert.All(row, p => Assert.Empty(p.Cast)); // only the fields a list draws are kept
        Assert.Equal("Bearer eyJsecret", net.TmdbRequests[0].Headers.Authorization?.ToString());
        Assert.Equal("https://api.themoviedb.org/3/trending/all/week?language=en-US", net.TmdbRequests[0].RequestUri?.AbsoluteUri);
    }

    [Fact]
    public async Task RowIsCappedAndStopsSearchingOnceFull()
    {
        var net = Stocked(30);
        var row = await new Trending("t", net.Search, transport: net.Transport).RowAsync(TrendingRow.Trend);
        Assert.Equal(Enumerable.Range(101, 16), row.Select(p => p.Id));
        Assert.True(net.Searches.Count <= 16 + 3); // at most the lookups already under way overshoot
    }

    [Fact]
    public async Task RowWithTooFewTitlesIsLeftOut()
    {
        var net = Stocked(6);
        net.Shelves = net.Shelves.Where(kv => kv.Key is "Film N1x" or "Film N2x" or "Film N3x" or "Film N4x").ToDictionary();
        Assert.Empty(await new Trending("t", net.Search, transport: net.Transport).RowAsync(TrendingRow.Trend));
    }

    [Fact]
    public async Task TwoTMDBTitlesThatAreOne30namaTitleShowOnce()
    {
        var net = Stocked();
        net.Lists["/trending/all/week"][1]["title"] = "Film N1x";
        net.Lists["/trending/all/week"][1]["id"] = 77;
        var row = await new Trending("t", net.Search, transport: net.Transport).RowAsync(TrendingRow.Trend);
        Assert.Equal([101, 103, 104, 105, 106], row.Select(p => p.Id));
    }

    [Fact]
    public async Task AnswersAreCachedAcrossLaunches()
    {
        var file = Path.Combine(Path.GetTempPath(), $"trending-{Guid.NewGuid()}.json");
        try
        {
            var net = Stocked(7);
            net.Shelves.Remove("Film N7x"); // one title 30nama does not have
            var clock = DateTime.UnixEpoch.AddSeconds(1_780_000_000);
            Trending Launch() => new("t", net.Search, file, net.Transport, () => clock);

            var first = await Launch().RowAsync(TrendingRow.Trend);
            Assert.Equal(6, first.Count);
            Assert.Single(net.TmdbRequests);
            Assert.Equal(8, net.Searches.Count); // the missing title is also tried with its year

            clock = clock.AddHours(5);
            var second = await Launch().RowAsync(TrendingRow.Trend);
            Assert.Equal(first.Select(p => p.Id), second.Select(p => p.Id));
            Assert.Single(net.TmdbRequests); // the list is good for six hours
            Assert.Equal(8, net.Searches.Count); // found and missing titles are both remembered

            clock = clock.AddHours(20);
            await Launch().RowAsync(TrendingRow.Trend);
            Assert.Equal(2, net.TmdbRequests.Count); // the list is asked for again
            Assert.Equal(10, net.Searches.Count);
            Assert.Equal("Film N7x 2024", net.Searches[^1]); // a missing title is looked for again after a day
        }
        finally { File.Delete(file); }
    }

    [Fact]
    public async Task AFailedSearchIsNotRememberedAsMissing()
    {
        var net = Stocked();
        net.SearchFails = true;
        var trending = new Trending("t", net.Search, transport: net.Transport);
        Assert.Empty(await trending.RowAsync(TrendingRow.Trend));
        net.SearchFails = false;
        Assert.Equal(6, (await trending.RowAsync(TrendingRow.Trend)).Count);
    }

    [Fact]
    public async Task RowIsEmptyWhenTMDBRefuses()
    {
        var net = Stocked();
        net.TmdbStatus = 401;
        var trending = new Trending("wrong", net.Search, transport: net.Transport);
        Assert.Empty(await trending.RowAsync(TrendingRow.Trend));
        Assert.Empty(net.Searches);
        net.TmdbStatus = 200;
        Assert.Equal(6, (await trending.RowAsync(TrendingRow.Trend)).Count); // a refusal is not cached
    }
}
