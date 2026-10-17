using System.IO.Compression;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Nama.Core;

/// <summary>
/// Finding a Persian subtitle on SubSource for a title 30nama has none for.
///
/// Two ways in. With the user's own SubSource key, its documented API. Without one, the requests
/// the site's own pages make, which need no key but may change when the site does. The app keeps
/// no key of its own: anything shipped inside it could be taken out of it.
/// </summary>
public static class SubSource
{
    public static readonly Uri Site = new("https://subsource.net");
    public const string Api = "https://api.subsource.net/api/v1";
    public const string Persian = "farsi_persian";

    /// <summary>What is being looked for.</summary>
    public sealed record Query(string Title, int? Year, string? ImdbId, bool IsSeries, int? Season);

    /// <summary>A subtitle that may fit: how to fetch it, and the releases it was timed for.</summary>
    public sealed record Candidate(Uri? Page, int? ApiId, IReadOnlyList<string> Releases)
    {
        public string Id => Page is not null ? Page.AbsoluteUri : $"api:{ApiId}";
        /// <summary>The release it is named after, for a menu.</summary>
        public string Name => Releases.FirstOrDefault() ?? "SubSource";
        public bool Equals(Candidate? other) => other is not null && other.Id == Id && other.Releases.SequenceEqual(Releases);
        public override int GetHashCode() => Id.GetHashCode();
    }

    // MARK: Without a key: the site's own requests

    /// <summary>The body of the site's search request.</summary>
    public static string SearchBody(Query query) =>
        JsonSerializer.Serialize(new Dictionary<string, object> { ["query"] = query.Title, ["includeSeasons"] = query.IsSeries, ["limit"] = 15 });

    /// <summary>
    /// The listing of Persian subtitles for the result that is this title: the same kind (film or
    /// series), the same year when both say one, and the name closest to it.
    /// </summary>
    public static Uri? Listing(Json answer, Query query)
    {
        var wanted = Simple(query.Title);
        Json? best = null;
        var bestScore = int.MinValue;
        foreach (var r in answer["results"].Array)
        {
            if ((r["type"].Text ?? "").Contains("series") != query.IsSeries) continue;
            var name = Simple(r["title"].Text ?? "");
            int score;
            if (name == wanted) score = 10;
            else if (name.Contains(wanted) || wanted.Contains(name)) score = 3;
            else continue;
            if (query.Year is { } year && r["releaseYear"].Int is { } theirs)
            {
                if (theirs == year) score += 10;
                else if (Math.Abs(theirs - year) > 1) continue;
            }
            if (score > bestScore) { best = r; bestScore = score; }
        }
        if (best is null) return null;
        var path = query.IsSeries
            ? best["seasons"].Array.FirstOrDefault(s => s["season"].Int == (query.Season ?? 1))?["link"].Text
            : best["link"].Text;
        return path is null ? null : new Uri(Site, Current(path) + "/" + Persian);
    }

    /// <summary>A listing address in the form the site uses now: "season-2", where it once had "season=2".</summary>
    public static string Current(string path) => path.Replace("season=", "season-");

    /// <summary>The same, for a whole address, such as one 30nama links to.</summary>
    public static Uri Current(Uri url) => Uri.TryCreate(Current(url.OriginalString), UriKind.Absolute, out var u) ? u : url;

    /// <summary>Simplified for comparing names: lower case, letters and digits only.</summary>
    public static string Simple(string name) => new(name.ToLowerInvariant().Where(char.IsLetterOrDigit).ToArray());

    // MARK: With the user's key: the documented API

    public static Uri SearchUrl(Query query)
    {
        var items = new List<string>();
        if (query.ImdbId is { } imdb) items.AddRange(["searchType=imdb", $"imdb={Uri.EscapeDataString(imdb)}"]);
        else
        {
            items.AddRange(["searchType=text", $"q={Uri.EscapeDataString(query.Title)}", $"type={(query.IsSeries ? "series" : "movie")}"]);
            if (query.Year is { } year) items.Add($"year={year}");
        }
        if (query.IsSeries && query.Season is { } season) items.Add($"season={season}");
        return new Uri($"{Api}/movies/search?{string.Join('&', items)}");
    }

    /// <summary>The id of the title the search found: for a series, the entry for the season wanted.</summary>
    public static int? MovieId(Json answer, Query query)
    {
        var found = answer["data"].Array;
        if (query.IsSeries && query.Season is { } season && found.FirstOrDefault(e => e["season"].Int == season) is { } entry) return entry["movieId"].Int;
        return found.Count > 0 ? found[0]["movieId"].Int : null;
    }

    public static Uri SubtitlesUrl(int movieId) => new($"{Api}/subtitles?movieId={movieId}&language={Persian}&sort=rating");

    public static List<Candidate> CandidatesInApi(Json answer) =>
        answer["data"].Array.Select(s => s["subtitleId"].Int is { } id ? new Candidate(null, id, s["releaseInfo"].Array.Select(x => x.Text).OfType<string>().ToList()) : null)
            .OfType<Candidate>().ToList();

    public static Uri DownloadUrl(int id) => new($"{Api}/subtitles/{id}/download");

    // MARK: Choosing

    /// <summary>
    /// The candidates in the order they are likely to be in time with what is playing: for an
    /// episode, only those for it or for its whole season; then those that share the copy's marks.
    /// </summary>
    public static List<Candidate> Ranked(IReadOnlyList<Candidate> candidates, string release, int? season, int? episode)
    {
        var left = candidates.Select(c => new SubtitleSite.Entry(c.Id, c.Releases)).ToList();
        var order = new List<SubtitleSite.Entry>();
        while (SubtitleSite.Best(release, season, episode, left) is { } next)
        {
            // Best passes over subtitles for other episodes only when something better is there.
            if (season is { } s && episode is { } e && !Fits(next.Releases, s, e)) break;
            order.Add(next);
            left.Remove(next);
        }
        return order.Select(entry => candidates.First(c => c.Id == entry.Path)).ToList();
    }

    private static bool Fits(IReadOnlyList<string> releases, int season, int episode)
    {
        var tag = $"s{season:00}e{episode:00}";
        var anyEpisode = releases.Any(r => SubtitleSite.Words(r).Any(w => Regex.IsMatch(w, @"^s\d{1,2}e\d{1,3}$")));
        return !anyEpisode || releases.Any(r => SubtitleSite.Words(r).Contains(tag));
    }

    /// <summary>The subtitle text in what was downloaded: an archive, or the file itself.</summary>
    public static string? TextFrom(byte[] data, int? season, int? episode)
    {
        if (data.Length < 2 || data[0] != 0x50 || data[1] != 0x4B)
            return SubtitleSite.Text(data) is { } plain && plain.Contains("-->") ? plain : null;
        var files = Zip.Files(data);
        if (SubtitleSite.Pick(files.Select(f => f.Name), season, episode) is not { } name) return null;
        var file = files.First(f => f.Name == name);
        return SubtitleSite.Text(file.Data) is { } text && text.Contains("-->") ? text : null;
    }
}

/// <summary>Reads the files out of a zip archive held in memory, as subtitle sites hand them out.</summary>
public static class Zip
{
    /// <summary>
    /// Larger than any subtitle file; an entry that says it is bigger is not unpacked. The size is
    /// what the archive claims, and an archive from a stranger may claim anything.
    /// </summary>
    public const int Largest = 20 * 1024 * 1024;

    /// <summary>
    /// Name and contents of every file. Folders are left out, and so is anything that cannot be
    /// read; names keep only their last part, so nothing points outside where it is put.
    /// </summary>
    public static List<(string Name, byte[] Data)> Files(byte[] data)
    {
        var files = new List<(string, byte[])>();
        try
        {
            using var archive = new ZipArchive(new MemoryStream(data), ZipArchiveMode.Read);
            foreach (var entry in archive.Entries)
            {
                if (entry.FullName.EndsWith('/') || entry.Length > Largest) continue;
                try
                {
                    using var stream = entry.Open();
                    using var copy = new MemoryStream();
                    // Read no further than the limit, whatever the entry claims.
                    var buffer = new byte[81920];
                    int n;
                    while ((n = stream.Read(buffer, 0, buffer.Length)) > 0)
                    {
                        if (copy.Length + n > Largest) throw new InvalidDataException("too large");
                        copy.Write(buffer, 0, n);
                    }
                    files.Add((Path.GetFileName(entry.FullName.Replace('\\', '/')), copy.ToArray()));
                }
                catch (Exception) { }
            }
        }
        catch (Exception) { }
        return files;
    }
}

/// <summary>
/// Looks on SubSource for Persian subtitles to a title 30nama gives none for, and fetches them.
/// With a key the user made on SubSource it goes through the site's documented API; without one,
/// or when that fails, through the requests the site's own pages make.
/// </summary>
public sealed class SubtitleFinder(Transport transport, Func<string?> key)
{
    private const string Agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";

    /// <summary>What was found for a title and season, so the next episode does not search again.</summary>
    private readonly Dictionary<string, List<SubSource.Candidate>> _found = [];
    private readonly Dictionary<string, string> _texts = [];

    /// <summary>The subtitles that may fit, best first; empty when there are none.</summary>
    public async Task<List<SubSource.Candidate>> CandidatesAsync(SubSource.Query query, string release, int? episode)
    {
        var cacheKey = $"{SubSource.Simple(query.Title)}|{query.Year ?? 0}|{query.Season ?? 0}";
        if (!_found.TryGetValue(cacheKey, out var all))
        {
            all = [];
            if (key() is { Length: > 0 } k) all = await ViaApiAsync(query, k);
            if (all.Count == 0) all = await ViaPagesAsync(query);
            // Nothing found may be the network; asked again for the next episode.
            if (all.Count > 0) _found[cacheKey] = all;
        }
        return SubSource.Ranked(all, release, query.IsSeries ? query.Season : null, episode);
    }

    /// <summary>The text of one subtitle, taken out of its archive and made readable.</summary>
    public async Task<string?> TextAsync(SubSource.Candidate candidate, int? season, int? episode)
    {
        var cacheKey = $"{candidate.Id}|{season ?? 0}|{episode ?? 0}";
        if (_texts.TryGetValue(cacheKey, out var known)) return known;
        byte[]? data;
        if (candidate.ApiId is { } id)
        {
            if (key() is not { Length: > 0 } k) return null;
            data = await FetchAsync(SubSource.DownloadUrl(id), k);
        }
        else
        {
            if (candidate.Page is not { } page || await FetchAsync(page) is not { } html || SubtitleSite.Download(Encoding.UTF8.GetString(html)) is not { } address) return null;
            data = await FetchAsync(address);
        }
        if (data is null) return null;
        var text = SubSource.TextFrom(data, season, episode);
        if (text is not null) _texts[cacheKey] = text;
        return text;
    }

    /// <summary>
    /// A subtitle in time with <paramref name="fileName"/> from the subtitle site's listing 30nama
    /// links to, for saving beside a downloaded film.
    /// </summary>
    public async Task<string?> FromListingAsync(Uri listing, string fileName, int? season, int? episode)
    {
        var page = SubSource.Current(listing);
        if (await FetchAsync(page) is not { } html) return null;
        if (SubtitleSite.Best(fileName, season, episode, SubtitleSite.Entries(Encoding.UTF8.GetString(html))) is not { } entry) return null;
        if (await FetchAsync(new Uri(page, entry.Path)) is not { } detail) return null;
        if (SubtitleSite.Download(Encoding.UTF8.GetString(detail)) is not { } address) return null;
        return await FetchAsync(address) is { } archive ? SubSource.TextFrom(archive, season, episode) : null;
    }

    private async Task<List<SubSource.Candidate>> ViaApiAsync(SubSource.Query query, string k)
    {
        if (await JsonAsync(SubSource.SearchUrl(query), k) is not { } answer || SubSource.MovieId(answer, query) is not { } movie) return [];
        return await JsonAsync(SubSource.SubtitlesUrl(movie), k) is { } list ? SubSource.CandidatesInApi(list) : [];
    }

    private async Task<List<SubSource.Candidate>> ViaPagesAsync(SubSource.Query query)
    {
        using var search = new HttpRequestMessage(HttpMethod.Post, "https://api.subsource.net/v1/movie/search")
        {
            Content = new StringContent(SubSource.SearchBody(query), Encoding.UTF8, "application/json"),
        };
        search.Headers.TryAddWithoutValidation("Origin", "https://subsource.net");
        if (await SendAsync(search) is not { } body) return [];
        Json answer;
        try { answer = Json.Parse(body); } catch (Exception) { return []; }
        if (SubSource.Listing(answer, query) is not { } listing || await FetchAsync(listing) is not { } html) return [];
        return SubtitleSite.Entries(Encoding.UTF8.GetString(html))
            .Select(e => new SubSource.Candidate(new Uri(SubSource.Site, e.Path), null, e.Releases)).ToList();
    }

    private async Task<Json?> JsonAsync(Uri url, string? k = null)
    {
        if (await FetchAsync(url, k) is not { } data) return null;
        try { return Json.Parse(data); } catch (Exception) { return null; }
    }

    private async Task<byte[]?> FetchAsync(Uri url, string? k = null)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        if (k is not null) request.Headers.TryAddWithoutValidation("X-API-Key", k);
        return await SendAsync(request);
    }

    private async Task<byte[]?> SendAsync(HttpRequestMessage request)
    {
        request.Headers.TryAddWithoutValidation("User-Agent", Agent);
        try
        {
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(25));
            using var response = await transport(request, timeout.Token);
            if ((int)response.StatusCode >= 400) return null;
            return await response.Content.ReadAsByteArrayAsync(timeout.Token);
        }
        catch (Exception) { return null; }
    }
}
