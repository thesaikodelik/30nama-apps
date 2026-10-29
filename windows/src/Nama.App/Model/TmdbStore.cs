using Nama.Core;

namespace Nama.App.Model;

/// <summary>
/// Fetches and keeps what TMDB says about titles. With the user's own TMDB key, through the API:
/// logo, trailer, crew and reviews. Without one, the logo and the trailer, read from the site's
/// public pages. As TMDBStore.swift. Used from the UI thread only.
/// </summary>
public sealed class TmdbStore
{
    // Before the shared store, which is made with it.
    private static readonly Transport Net = ApiClient.HttpTransport();

    public static TmdbStore Shared { get; } = new();

    private readonly TmdbSite _site = new(Net);

    public static string? Key => AppModel.Shared.Settings.TmdbKey?.Trim() is { Length: > 0 } key ? key : null;

    /// <summary>What is known, by 30nama post id. Kept for the session; titles do not change their logo.</summary>
    private readonly Dictionary<int, TMDB.Info> _known = [];
    /// <summary>Looks under way, so that two parts of a page asking at once share one.</summary>
    private readonly Dictionary<int, Task<TMDB.Info?>> _running = [];
    /// <summary>Titles whose trailer has been looked for, found or not.</summary>
    private readonly HashSet<int> _trailerSettled = [];
    private string? _usedKey;

    /// <summary>A trailer that was found after the info was handed out, by post id.</summary>
    public event Action<int>? TrailerFound;

    public TMDB.Info? Known(int post) => _known.GetValueOrDefault(post);

    /// <summary>
    /// What TMDB has for <paramref name="post"/>, fetched the first time it is asked for. Without a
    /// key it returns once the logo is known; the trailer follows a moment later (<see cref="TrailerFound"/>).
    /// </summary>
    public async Task<TMDB.Info?> InfoAsync(Post post, Lang lang)
    {
        // A new key, or none: what was looked up with the old one may not hold.
        if (Key != _usedKey)
        {
            _usedKey = Key;
            _known.Clear();
            _running.Clear();
            _trailerSettled.Clear();
        }
        if (_known.TryGetValue(post.Id, out var info)) return info;
        if (!_running.TryGetValue(post.Id, out var task)) _running[post.Id] = task = LookAsync(post, lang);
        try { return await task; }
        finally { _running.Remove(post.Id); }
    }

    private async Task<TMDB.Info?> LookAsync(Post post, Lang lang)
    {
#if DEBUG
        // NAMA_TMDB_SAMPLE=1: sample crew and reviews on top of what the public pages give, to see
        // those parts of a page without a key.
        if (Environment.GetEnvironmentVariable("NAMA_TMDB_SAMPLE") == "1")
        {
            var sample = (await PublicLookAsync(post, lang) ?? new TMDB.Info()) with
            {
                Crew = [new("Sample Director", "Director", null), new("Sample Writer", "Screenplay", null),
                        new("Sample Composer", "Original Music Composer", null), new("Sample DoP", "Director of Photography", null)],
                Reviews = [new("sample critic", string.Concat(Enumerable.Repeat("A sample review that runs on for a while, to see how a long text is cut short in its card. ", 4)), 8, null),
                           new("another", "Short and to the point.", null, null)],
            };
            _known[post.Id] = sample with { Trailer = _known.GetValueOrDefault(post.Id)?.Trailer };
            return _known[post.Id];
        }
#endif
        if (Key is not { } key) return await PublicLookAsync(post, lang);
        int? id = null;
        if (post.ImdbId is { } imdb && await JsonAsync(TMDB.FindRequest(imdb, key)) is { } found) id = TMDB.IdInFind(found, post.IsSeries);
        // By name: the result that is this title, and again without the year, which TMDB and 30nama
        // do not always agree on.
        var name = post.TitleEnglish ?? post.Title(Lang.En);
        if (id is null && await JsonAsync(TMDB.SearchRequest(name, post.Year, post.IsSeries, key)) is { } searched)
            id = TMDB.BestInSearch(searched, name, post.Year);
        if (id is null && post.Year is not null && await JsonAsync(TMDB.SearchRequest(name, null, post.IsSeries, key)) is { } again)
            id = TMDB.BestInSearch(again, name, post.Year);
        // A failed look is tried again next time the title is opened.
        if (id is not { } tmdbId || await JsonAsync(TMDB.DetailsRequest(tmdbId, post.IsSeries, key)) is not { } details) return null;
        var info = TMDB.InfoIn(details, lang);
        _known[post.Id] = info;
        _trailerSettled.Add(post.Id);
        return info;
    }

    /// <summary>Without a key, the logo and the trailer, read from the site's public pages; the rest needs the API.</summary>
    private async Task<TMDB.Info?> PublicLookAsync(Post post, Lang lang)
    {
        if (post.ImdbId is not { } imdb) return null;
        // The redirect lands on the title's own page; that address is all that is wanted of it.
        if (await _site.GetAsync(TMDB.PublicPage(imdb)) is not { } page) return null;
        var info = new TMDB.Info();
        foreach (var language in lang == Lang.Fa ? new[] { "fa", "en" } : ["en"])
        {
            if (TMDB.LogosPage(page.Address, language) is not { } list || await _site.GetAsync(list) is not { } html) break;
            if (TMDB.LogoInPage(html.Html) is { } logo)
            {
                info = info with { Logo = logo };
                break;
            }
        }
        // The logo is returned at once; the trailer follows without holding it up.
        _known[post.Id] = info;
        _ = FindTrailerAsync(post.Id, page.Address);
        return info;
    }

    private async Task FindTrailerAsync(int post, Uri page)
    {
        if (TMDB.TrailersPage(page) is { } list && await _site.GetAsync(list) is { } html && TMDB.TrailerInPage(html.Html) is { } trailer
            && _known.TryGetValue(post, out var info))
        {
            _known[post] = info with { Trailer = trailer };
            TrailerFound?.Invoke(post);
        }
        _trailerSettled.Add(post);
    }

    /// <summary>The trailer for <paramref name="post"/>, waiting a little for a look that is still under way.</summary>
    public async Task<string?> TrailerAsync(Post post, Lang lang, TimeSpan? patience = null)
    {
        await InfoAsync(post, lang);
        var until = DateTime.UtcNow + (patience ?? TimeSpan.FromSeconds(4));
        while (DateTime.UtcNow < until)
        {
            if (_known.GetValueOrDefault(post.Id)?.Trailer is { } trailer) return trailer;
            if (_trailerSettled.Contains(post.Id)) return null;
            await Task.Delay(250);
        }
        return _known.GetValueOrDefault(post.Id)?.Trailer;
    }


    private static async Task<Json?> JsonAsync(HttpRequestMessage request)
    {
        try
        {
            using (request)
            {
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
                using var response = await Net(request, timeout.Token);
                if ((int)response.StatusCode != 200) return null;
                return Json.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token));
            }
        }
        catch (Exception) { return null; }
    }
}
