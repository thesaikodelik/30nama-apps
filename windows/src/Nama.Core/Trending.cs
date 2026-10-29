using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

namespace Nama.Core;

// TMDB's trending lists, turned into titles 30nama carries. With the user's own TMDB key the
// documented API is asked; without one, the week's trending list is read from TMDB's public page,
// and the rows that need the API stay empty. As Trending.swift, itself from the Tizen client.

/// <summary>A title as TMDB lists it, cut down to what matching needs.</summary>
public sealed record TMDBItem
{
    public enum Kinds { Movie, Tv }

    public required int Id { get; init; }
    public required Kinds Kind { get; init; }
    public required string Title { get; init; }
    public string? OriginalTitle { get; init; }
    public int? Year { get; init; }
    public bool HasPoster { get; init; }
    public double Popularity { get; init; }
    public int VoteCount { get; init; }

    public string Key => (Kind == Kinds.Tv ? "tv" : "movie") + Id;

    /// <summary>The fields of a list entry worth caching.</summary>
    public static readonly string[] KeptKeys =
        ["id", "media_type", "title", "name", "original_title", "original_name", "release_date", "first_air_date", "poster_path", "popularity", "vote_count"];

    /// <param name="defaultKind">For lists that hold one kind and so do not say which.</param>
    public static TMDBItem? Parse(Json j, Kinds? defaultKind = null)
    {
        if (j["id"].Int is not { } id || j["media_type"].Text == "person") return null;
        var kind = j["media_type"].Text switch
        {
            "tv" => Kinds.Tv,
            "movie" => Kinds.Movie,
            _ => defaultKind ?? (j["first_air_date"].IsNull ? Kinds.Movie : Kinds.Tv),
        };
        var date = j["release_date"].Text ?? j["first_air_date"].Text;
        return new TMDBItem
        {
            Id = id, Kind = kind, Title = j["title"].Text ?? j["name"].Text ?? "",
            OriginalTitle = j["original_title"].Text ?? j["original_name"].Text,
            Year = date is { Length: >= 4 } && int.TryParse(date[..4], out var y) ? y : null,
            HasPoster = j["poster_path"].Text is not null,
            Popularity = j["popularity"].Double ?? 0,
            VoteCount = j["vote_count"].Int ?? 0,
        };
    }

    public static List<TMDBItem> List(Json results, Kinds? defaultKind = null) =>
        results.Array.Select(r => Parse(r, defaultKind)).OfType<TMDBItem>().ToList();

    /// <summary>TMDB's public "trending this week" panel, written as the list entries the API would give.</summary>
    public static readonly Uri PublicTrending = new("https://www.themoviedb.org/remote/panel?panel=trending_scroller&group=this-week&language=en-US");

    private static readonly Regex PanelEntry = new(
        @"href=""/(movie|tv)/(\d+)[^""]*""><h2[^>]*><span>([^<]+)</span></h2></a></div><span class=""release_date[^""]*"">([^<]*)</span>", RegexOptions.Compiled);

    public static Json PublicList(string html)
    {
        var seen = new HashSet<string>();
        var entries = new List<Json>();
        foreach (Match m in PanelEntry.Matches(html))
        {
            var kind = m.Groups[1].Value;
            if (!int.TryParse(m.Groups[2].Value, out var id) || !seen.Add(kind + id)) continue;
            var entry = new Dictionary<string, object?>
            {
                ["id"] = id, ["media_type"] = kind,
                [kind == "tv" ? "name" : "title"] = System.Net.WebUtility.HtmlDecode(m.Groups[3].Value),
                ["poster_path"] = "/public",
            };
            if (Regex.Match(m.Groups[4].Value, @"\d{4}") is { Success: true } year) entry[kind == "tv" ? "first_air_date" : "release_date"] = year.Value + "-01-01";
            entries.Add(Json.From(entry));
        }
        return new Json(entries);
    }
}

/// <summary>The rows TMDB adds to Home.</summary>
public enum TrendingRow
{
    /// <summary>Trending this week, movies and series together.</summary>
    Trend,
    /// <summary>New and returning series.</summary>
    Fresh,
    /// <summary>Movies in cinemas and trending today.</summary>
    Pop,
}

public sealed record TrendingSource(string Path, IReadOnlyDictionary<string, string> Params, TMDBItem.Kinds? Kind);

public static class TrendingRows
{
    /// <summary>Most titles a row shows.</summary>
    public static int Cap(this TrendingRow row) => row == TrendingRow.Trend ? 16 : 14;

    /// <summary>A row with fewer titles than this is not worth showing.</summary>
    public const int Minimum = 5;

    public static IReadOnlyList<TrendingSource> Sources(this TrendingRow row, DateTime? now = null) => row switch
    {
        TrendingRow.Trend => [new TrendingSource("/trending/all/week", new Dictionary<string, string> { ["language"] = "en-US" }, null)],
        TrendingRow.Fresh => Enumerable.Range(1, 3).Select(page => new TrendingSource("/discover/tv", new Dictionary<string, string>
        {
            ["language"] = "en-US", ["air_date.gte"] = (now ?? DateTime.UtcNow).AddDays(-30).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            ["sort_by"] = "popularity.desc", ["vote_count.gte"] = "200",
            // No talk shows, news, reality or soaps.
            ["without_genres"] = "10767,10763,10764,10766", ["page"] = page.ToString(),
        }, TMDBItem.Kinds.Tv)).ToList(),
        _ =>
        [
            new TrendingSource("/movie/now_playing", new Dictionary<string, string> { ["language"] = "en-US", ["region"] = "US" }, TMDBItem.Kinds.Movie),
            new TrendingSource("/trending/movie/day", new Dictionary<string, string> { ["language"] = "en-US" }, TMDBItem.Kinds.Movie),
        ],
    };

    /// <summary>The titles to look for on 30nama, in the order the row should show them.</summary>
    public static List<TMDBItem> Candidates(this TrendingRow row, IEnumerable<IEnumerable<TMDBItem>> lists)
    {
        var seen = new HashSet<string>();
        var output = lists.SelectMany(l => l).Where(i => i.Title.Length > 0 && i.HasPoster && seen.Add(i.Key)).ToList();
        if (row == TrendingRow.Fresh)
        {
            // Best-known series first, obscure ones dropped.
            static double Weight(TMDBItem i) => i.Popularity + Math.Sqrt(i.VoteCount) / 5;
            output = output.Where(i => i.VoteCount >= 200).Select((item, index) => (item, index))
                .OrderByDescending(x => Weight(x.item)).ThenBy(x => x.index).Select(x => x.item).ToList();
        }
        return output;
    }
}

/// <summary>Decides whether a 30nama title and a TMDB title are the same one.</summary>
public static class TitleMatch
{
    /// <summary>Lowercase, without accents or punctuation: "Amélie &amp; Co." -> "amelie and co".</summary>
    public static string Normalize(string s)
    {
        var decomposed = s.ToLowerInvariant().Normalize(NormalizationForm.FormD);
        var bare = new string(decomposed.Where(c => c is < '̀' or > 'ͯ').ToArray());
        return Regex.Replace(bare.Replace("&", " and "), "[^a-z0-9؀-ۿ]+", " ").Trim();
    }

    /// <summary>30nama appends the release year to its titles ("Animals 2026").</summary>
    public static string StripYear(string s)
    {
        var stripped = Regex.Replace(s, @"\s*[\(\[]?\b(19|20)\d\d[\)\]]?\s*$", "").Trim();
        return stripped.Length == 0 ? s : stripped;
    }

    /// <summary>0...1: how many words two titles share. A title that only adds a subtitle to the other still counts as close.</summary>
    public static double Similarity(string a, string b)
    {
        a = Normalize(a);
        b = Normalize(b);
        if (a.Length == 0 || b.Length == 0) return 0;
        if (a == b) return 1;
        var wordsA = a.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        var wordsB = b.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        var setB = wordsB.ToHashSet();
        var shared = wordsA.Count(setB.Contains);
        var dice = 2.0 * shared / (wordsA.Length + wordsB.Length);
        return a.StartsWith(b + " ") || b.StartsWith(a + " ") ? Math.Max(dice, 0.82) : dice;
    }

    /// <summary>
    /// What to search 30nama for: the title, then the original one when it differs and is in Latin
    /// script, then the title with its year, the way 30nama itself writes it.
    /// </summary>
    public static List<string> Queries(TMDBItem item)
    {
        var output = new List<string> { item.Title };
        if (item.OriginalTitle is { } original && Normalize(original) != Normalize(item.Title) && Regex.IsMatch(original, "[a-zA-Z]")) output.Add(original);
        if (item.Year is { } year) output.Add($"{item.Title} {year}");
        return output;
    }

    /// <summary>
    /// The search result that is this TMDB title: same kind, a close enough name, the same year
    /// give or take one. Titles that can be streamed win over ones that cannot; upcoming ones are skipped.
    /// </summary>
    public static Post? BestPost(IEnumerable<Post> posts, TMDBItem item)
    {
        Post? best = null;
        var bestScore = 0.0;
        foreach (var post in posts.Where(p => p.IsSeries == (item.Kind == TMDBItem.Kinds.Tv) && !p.ComingSoon))
        {
            var raw = post.TitleEnglish ?? "";
            var name = new[] { item.Title, item.OriginalTitle ?? "" }.Where(t => t.Length > 0)
                .Select(t => Math.Max(Similarity(StripYear(raw), t), Similarity(raw, t))).DefaultIfEmpty(0).Max();
            if (name < 0.8) continue;
            var score = name;
            if (item.Year is { } wanted && post.Year is { } year)
            {
                switch (Math.Abs(wanted - year))
                {
                    case 0: score += 0.25; break;
                    case 1: score += 0.1; break;
                    default: continue;
                }
            }
            if (post.HasStream) score += 0.1;
            if (score > bestScore) { best = post; bestScore = score; }
        }
        return best;
    }
}

/// <summary>
/// Builds the trending rows: asks TMDB what is popular, then finds each title on 30nama. Both
/// halves are cached on disk, because finding one title costs a 30nama search and 30nama is rate
/// limited: TMDB's lists for six hours, a title that was found for a week, one that was not for a day.
/// </summary>
public sealed class Trending
{
    public delegate Task<Json> Search(string query);

    private sealed record Entry(DateTime At, Json Value);

    /// <summary>The user's own TMDB key; null for the public page.</summary>
    private readonly string? _key;
    private readonly Transport _transport;
    private readonly TmdbSite _site;
    private readonly Search _search;
    private readonly string? _cacheFile;
    private readonly Func<DateTime> _now;
    private readonly object _lock = new();
    private readonly Dictionary<string, Entry> _cache = [];
    private bool _cacheLoaded;
    private readonly Dictionary<TrendingRow, Task<List<Post>>> _running = [];

    /// <summary>The fields of a 30nama post that lists and the billboard draw from.</summary>
    private static readonly string[] KeptPostKeys = ["id", "post_type", "options", "info", "title", "plot", "genre", "image", "score"];

    /// <param name="key">The user's TMDB key; null or blank reads the public page instead.</param>
    /// <param name="search">Runs a 30nama title search and returns its result.</param>
    public Trending(string? key, Search search, string? cacheFile = null, Transport? transport = null, Func<DateTime>? now = null)
    {
        _key = string.IsNullOrWhiteSpace(key) ? null : key.Trim();
        _search = search;
        _cacheFile = cacheFile;
        _transport = transport ?? ApiClient.HttpTransport();
        _site = new TmdbSite(_transport);
        _now = now ?? (() => DateTime.UtcNow);
    }

    /// <summary>The titles of a row, in TMDB's order. Empty when too few of them are on 30nama, or TMDB could not be reached.</summary>
    public async Task<List<Post>> RowAsync(TrendingRow row)
    {
        Task<List<Post>> task;
        lock (_lock)
        {
            if (!_running.TryGetValue(row, out task!)) _running[row] = task = BuildAsync(row);
        }
        try { return await task; }
        finally { lock (_lock) _running.Remove(row); }
    }

    private async Task<List<Post>> BuildAsync(TrendingRow row)
    {
        LoadCache();
        var lists = new List<List<TMDBItem>>();
        foreach (var source in row.Sources(_now())) lists.Add(await ListAsync(source));
        var candidates = row.Candidates(lists);

        // Up to four lookups at a time, and no new ones once the row is full.
        var found = new List<(int Index, Post Post)>();
        var active = new Dictionary<Task<Post?>, int>();
        var next = 0;
        while (true)
        {
            while (active.Count < 4 && next < candidates.Count && found.Count < row.Cap())
            {
                active[ResolveAsync(candidates[next])] = next;
                next++;
            }
            if (active.Count == 0) break;
            var done = await Task.WhenAny(active.Keys);
            var index = active[done];
            active.Remove(done);
            if (await done is { } post) found.Add((index, post));
        }
        SaveCache();
        var seen = new HashSet<int>();
        var posts = found.OrderBy(f => f.Index).Select(f => f.Post).Where(p => seen.Add(p.Id)).Take(row.Cap()).ToList();
        return posts.Count >= TrendingRows.Minimum ? posts : [];
    }

    private async Task<List<TMDBItem>> ListAsync(TrendingSource source)
    {
        var query = string.Join('&', source.Params.OrderBy(p => p.Key, StringComparer.Ordinal).Select(p => $"{p.Key}={p.Value}"));
        var key = $"l:{source.Path}?{query}";
        if (Cached(key) is { } hit) return TMDBItem.List(hit, source.Kind);
        if (_key is null)
        {
            // Without a key, the lists written for everyone each day on the repository's data branch
            // (Tools/trending/build_trending.py), as the Android app reads them; TMDB's public page
            // only where GitHub does not answer.
            var shared = (await FetchSharedAsync())?["lists"][$"{source.Path}?{query}"];
            if (shared is { } list && list.Array.Count > 0)
            {
                Store(key, list);
                return TMDBItem.List(list, source.Kind);
            }
            if (source.Path != "/trending/all/week" || await FetchPublicAsync() is not { } html) return [];
            var scraped = TMDBItem.PublicList(html);
            if (scraped.Array.Count == 0) return [];
            Store(key, scraped);
            return TMDBItem.List(scraped);
        }
        if (await FetchAsync(source) is not { } fresh) return [];
        var slim = new Json(fresh["results"].Array.Select(e => new Json((e.Object ?? new Dictionary<string, Json>())
            .Where(kv => TMDBItem.KeptKeys.Contains(kv.Key)).ToDictionary(kv => kv.Key, kv => kv.Value))).ToList());
        Store(key, slim);
        return TMDBItem.List(slim, source.Kind);
    }

    private async Task<Json?> FetchAsync(TrendingSource source)
    {
        try
        {
            using var request = TMDB.Request(source.Path[1..], source.Params.OrderBy(p => p.Key, StringComparer.Ordinal).Select(p => (p.Key, p.Value)), _key!);
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(20));
            using var response = await _transport(request, timeout.Token);
            if ((int)response.StatusCode != 200) return null;
            return Json.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token));
        }
        catch (Exception) { return null; }
    }

    private async Task<string?> FetchPublicAsync() => (await _site.GetAsync(TMDBItem.PublicTrending))?.Html;

    /// <summary>The trending lists written each day for viewers without a key.</summary>
    public static readonly Uri SharedTrending = new("https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/trending.json");

    private async Task<Json?> FetchSharedAsync()
    {
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, SharedTrending);
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
            using var response = await _transport(request, timeout.Token);
            if ((int)response.StatusCode != 200) return null;
            return Json.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token));
        }
        catch (Exception) { return null; }
    }

    /// <summary>The 30nama title for a TMDB one, or null when 30nama does not have it.</summary>
    private async Task<Post?> ResolveAsync(TMDBItem item)
    {
        var key = "p:" + item.Key;
        if (Cached(key) is { } hit) return Post.Parse(hit);
        var match = Json.Null;
        try
        {
            foreach (var query in TitleMatch.Queries(item))
            {
                var entries = Post.RawList(await _search(query)).Select(e => e["post"].IsNull ? e : e["post"]).ToList();
                if (TitleMatch.BestPost(entries.Select(Post.Parse).OfType<Post>(), item) is { } post
                    && entries.FirstOrDefault(e => e["id"].Int == post.Id) is { } raw)
                {
                    match = new Json((raw.Object ?? new Dictionary<string, Json>()).Where(kv => KeptPostKeys.Contains(kv.Key)).ToDictionary(kv => kv.Key, kv => kv.Value));
                    break;
                }
            }
        }
        catch (Exception)
        {
            // The search itself failed, which says nothing about the title: ask again next time.
            return null;
        }
        Store(key, match);
        return Post.Parse(match);
    }

    // MARK: Cache

    private static TimeSpan Life(string key, Json value) =>
        key.StartsWith("l:") ? TimeSpan.FromHours(6) : value.IsNull ? TimeSpan.FromDays(1) : TimeSpan.FromDays(7);

    private Json? Cached(string key)
    {
        lock (_lock) return _cache.TryGetValue(key, out var e) && _now() - e.At < Life(key, e.Value) ? e.Value : null;
    }

    private void Store(string key, Json value)
    {
        lock (_lock) _cache[key] = new Entry(_now(), value);
    }

    private void LoadCache()
    {
        lock (_lock)
        {
            if (_cacheLoaded) return;
            _cacheLoaded = true;
        }
        if (_cacheFile is null || !File.Exists(_cacheFile)) return;
        try
        {
            var stored = Json.Parse(File.ReadAllText(_cacheFile));
            lock (_lock)
            {
                foreach (var (key, entry) in stored.Object ?? new Dictionary<string, Json>())
                {
                    if (entry["t"].Double is { } at) _cache[key] = new Entry(DateTime.UnixEpoch.AddSeconds(at), entry["v"]);
                }
            }
        }
        catch (Exception) { }
    }

    private void SaveCache()
    {
        if (_cacheFile is null) return;
        Dictionary<string, Json> stored;
        lock (_lock)
        {
            var time = _now();
            foreach (var stale in _cache.Where(kv => time - kv.Value.At >= Life(kv.Key, kv.Value.Value)).Select(kv => kv.Key).ToList()) _cache.Remove(stale);
            stored = _cache.ToDictionary(kv => kv.Key, kv => new Json(new Dictionary<string, Json>
            {
                ["t"] = new Json((kv.Value.At - DateTime.UnixEpoch).TotalSeconds), ["v"] = kv.Value.Value,
            }));
        }
        try { File.WriteAllText(_cacheFile, new Json(stored).Serialize()); } catch (Exception) { }
    }
}
