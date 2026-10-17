using System.Text.RegularExpressions;

namespace Nama.Core;

/// <summary>
/// What TMDB adds to a title page, with a key the user made on TMDB themselves: the title's own
/// logo, its trailer, the people who made it, and reviews. The app keeps no key of its own.
/// </summary>
public static class TMDB
{
    public const string Api = "https://api.themoviedb.org/3";
    public const string Images = "https://image.tmdb.org/t/p/";

    /// <summary>A v4 read access token is sent as a bearer token; a v3 key goes in the address.</summary>
    public static HttpRequestMessage Request(string path, IEnumerable<(string Name, string Value)>? query, string key)
    {
        var items = (query ?? []).ToList();
        var bearer = key.StartsWith("eyJ", StringComparison.Ordinal);
        if (!bearer) items.Add(("api_key", key));
        var url = $"{Api}/{path}" + (items.Count > 0 ? "?" + string.Join('&', items.Select(i => $"{Uri.EscapeDataString(i.Name)}={Uri.EscapeDataString(i.Value)}")) : "");
        var request = new HttpRequestMessage(HttpMethod.Get, url);
        if (bearer) request.Headers.TryAddWithoutValidation("Authorization", "Bearer " + key);
        request.Headers.TryAddWithoutValidation("Accept", "application/json");
        return request;
    }

    /// <summary>Finding the title: by its IMDb id when 30nama gives one, else by name and year.</summary>
    public static HttpRequestMessage FindRequest(string imdbId, string key) => Request($"find/{imdbId}", [("external_source", "imdb_id")], key);

    public static HttpRequestMessage SearchRequest(string title, int? year, bool isSeries, string key)
    {
        var query = new List<(string, string)> { ("query", title) };
        if (year is { } y) query.Add((isSeries ? "first_air_date_year" : "year", y.ToString()));
        return Request($"search/{(isSeries ? "tv" : "movie")}", query, key);
    }

    public static int? IdInFind(Json answer, bool isSeries) => answer[isSeries ? "tv_results" : "movie_results"][0]["id"].Int;

    public static int? IdInSearch(Json answer) => answer["results"][0]["id"].Int;

    /// <summary>Everything a title page shows, in one request.</summary>
    public static HttpRequestMessage DetailsRequest(int id, bool isSeries, string key) =>
        Request($"{(isSeries ? "tv" : "movie")}/{id}",
            [("append_to_response", $"images,videos,{(isSeries ? "aggregate_credits" : "credits")},reviews"), ("include_image_language", "fa,en,null")], key);

    // MARK: Without a key: the public site

    public static readonly Uri Site = new("https://www.themoviedb.org");

    /// <summary>The site sends this to the title's own page.</summary>
    public static Uri PublicPage(string imdbId) => new($"https://www.themoviedb.org/redirect?external_source=imdb_id&external_id={imdbId}");

    private static bool IsTitlePage(Uri page) => page.AbsolutePath.StartsWith("/movie/") || page.AbsolutePath.StartsWith("/tv/");

    /// <summary>The page that lists a title's logos in one language, given the title's own page.</summary>
    public static Uri? LogosPage(Uri page, string language) =>
        IsTitlePage(page) ? new Uri(page.AbsoluteUri.Split('?')[0] + "/images/logos?image_language=" + language) : null;

    /// <summary>The page that lists a title's trailers, given the title's own page.</summary>
    public static Uri? TrailersPage(Uri page) =>
        IsTitlePage(page) ? new Uri(page.AbsoluteUri.Split('?')[0] + "/videos?active_nav_item=Trailers&language=en-US") : null;

    /// <summary>The YouTube id of the first trailer the page lists.</summary>
    public static string? TrailerInPage(string html) =>
        Regex.Match(html, @"data-site=""YouTube"" data-id=""([A-Za-z0-9_-]{11})""") is { Success: true } m ? m.Groups[1].Value : null;

    /// <summary>The first logo the page lists, which the site puts best voted first.</summary>
    public static Uri? LogoInPage(string html)
    {
        if (Regex.Match(html, @"/t/p/[a-z0-9_]+/([A-Za-z0-9]+\.(png|svg))") is not { Success: true } m) return null;
        return new Uri(Images + "w500/" + m.Groups[1].Value.Replace(".svg", ".png"));
    }

    public const string Agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

    public sealed record Person(string Name, string Job, Uri? Photo);

    public sealed record Review(string Author, string Text, double? Rating, Uri? Link);

    public sealed record Info
    {
        /// <summary>The title written as its own artwork, on a clear ground.</summary>
        public Uri? Logo { get; init; }
        /// <summary>The YouTube id of the official trailer.</summary>
        public string? Trailer { get; init; }
        public IReadOnlyList<Person> Crew { get; init; } = [];
        public IReadOnlyList<Review> Reviews { get; init; } = [];
        public Uri? TrailerPage => Trailer is null ? null : new Uri($"https://www.youtube.com/watch?v={Trailer}");
    }

    /// <summary>The jobs worth naming on a title page, in the order they are shown.</summary>
    public static readonly string[] Jobs = ["Director", "Creator", "Screenplay", "Writer", "Novel", "Original Music Composer", "Director of Photography"];

    public static Info InfoIn(Json details, Lang lang)
    {
        // A logo in the interface's language if there is one, else English, else one with no text.
        Uri? logo = null;
        var logos = details["images"]["logos"].Array;
        foreach (var code in (lang == Lang.Fa ? new[] { "fa", "en" } : ["en"]).Append(""))
        {
            var best = logos.Where(l => (l["iso_639_1"].Text ?? "") == code).MaxBy(l => l["vote_average"].Double ?? 0);
            if (best?["file_path"].Text is { } path)
            {
                // PNG at a size that stays sharp on a large screen; TMDB keeps SVGs as PNGs too.
                logo = new Uri(Images + "w500" + path.Replace(".svg", ".png"));
                break;
            }
        }

        var videos = details["videos"]["results"].Array.Where(v => v["site"].Text == "YouTube" && v["type"].Text == "Trailer").ToList();
        var trailer = (videos.FirstOrDefault(v => v["official"].Bool) ?? videos.FirstOrDefault())?["key"].Text;

        // Films list one entry per job; series list each person once, with all their jobs.
        var people = new List<Person>();
        foreach (var entry in details["credits"]["crew"].Array.Concat(details["aggregate_credits"]["crew"].Array))
        {
            if (entry["name"].Text is not { } name) continue;
            var jobs = entry["job"].Text is { } job ? [job] : entry["jobs"].Array.Select(j => j["job"].Text).OfType<string>().ToList();
            var photo = entry["profile_path"].Text is { } p ? new Uri(Images + "w185" + p) : null;
            foreach (var j in jobs.Where(Jobs.Contains)) people.Add(new Person(name, j, photo));
        }
        foreach (var creator in details["created_by"].Array)
        {
            if (creator["name"].Text is not { } name) continue;
            people.Add(new Person(name, "Creator", creator["profile_path"].Text is { } p ? new Uri(Images + "w185" + p) : null));
        }
        var seen = new HashSet<string>();
        var crew = people.OrderBy(p => Array.IndexOf(Jobs, p.Job) is var i and >= 0 ? i : 99).Where(p => seen.Add(p.Name)).Take(8).ToList();

        var reviews = details["reviews"]["results"].Array.Take(6)
            .Select(r => r["author"].Text is { } author && r["content"].Text is { Length: > 0 } text
                ? new Review(author, text, r["author_details"]["rating"].Double, r["url"].Url) : null)
            .OfType<Review>().ToList();
        return new Info { Logo = logo, Trailer = trailer, Crew = crew, Reviews = reviews };
    }
}

/// <summary>
/// TMDB's public site. A visitor it does not know yet is sent a page that names a cookie and asks
/// to be loaded again with it; that is done here, and the cookie kept for the pages after.
/// </summary>
public sealed class TmdbSite(Transport transport)
{
    private readonly object _lock = new();
    private string? _cookie;

    public sealed record Page(string Html, Uri Address);

    /// <summary>The page at <paramref name="url"/> and where it ended up after redirects; null when it could not be had.</summary>
    public async Task<Page?> GetAsync(Uri url)
    {
        for (var attempt = 0; attempt < 2; attempt++)
        {
            string? cookie;
            lock (_lock) cookie = _cookie;
            try
            {
                using var request = new HttpRequestMessage(HttpMethod.Get, url);
                request.Headers.TryAddWithoutValidation("User-Agent", TMDB.Agent);
                if (cookie is not null) request.Headers.TryAddWithoutValidation("Cookie", cookie);
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
                using var response = await transport(request, timeout.Token);
                var html = System.Text.Encoding.UTF8.GetString(await response.Content.ReadAsByteArrayAsync(timeout.Token));
                if (Challenge(html) is { } asked)
                {
                    lock (_lock) _cookie = asked;
                    continue;
                }
                if ((int)response.StatusCode != 200) return null;
                return new Page(html, Secure(response.RequestMessage?.RequestUri ?? url));
            }
            catch (Exception) { return null; }
        }
        return null;
    }

    /// <summary>The cookie a challenge page asks for, as a Cookie header.</summary>
    public static string? Challenge(string html)
    {
        var name = Regex.Match(html, @"data-cookie=""([A-Za-z0-9_\-]+)""");
        var token = Regex.Match(html, @"data-token=""([^""]+)""");
        return name.Success && token.Success ? $"{name.Groups[1].Value}={token.Groups[1].Value}" : null;
    }

    /// <summary>The transport sends https as http to port 443 (see <see cref="ApiClient.HttpTransport"/>); the address is given back as it was.</summary>
    private static Uri Secure(Uri url) =>
        url is { Scheme: "http", Port: 443 } ? new UriBuilder(url) { Scheme = "https", Port = -1 }.Uri : url;
}
