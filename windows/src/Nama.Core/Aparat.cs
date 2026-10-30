using System.Text;
using System.Text.RegularExpressions;

namespace Nama.Core;

/// <summary>
/// A title's trailer from Aparat, the Iranian video site: open in Iran, where TMDB and YouTube are
/// filtered, searchable without a key, and its videos are plain files the app can play itself.
/// What is there is uploaded by anyone, so a video is taken only when its title says plainly that
/// it is this title's trailer. As Aparat.swift.
/// </summary>
public static class Aparat
{
    public sealed record Video(string Uid, string Title, int Duration, int Visits);

    public static HttpRequestMessage SearchRequest(string title)
    {
        var query = "تریلر " + BareTitle(title);
        return new HttpRequestMessage(HttpMethod.Get, $"https://www.aparat.com/etc/api/videoBySearch/text/{Uri.EscapeDataString(query)}/perpage/20");
    }

    public static HttpRequestMessage VideoRequest(string uid) =>
        new(HttpMethod.Get, $"https://www.aparat.com/etc/api/video/videohash/{Uri.EscapeDataString(uid)}");

    public static List<Video> VideosInSearch(Json answer) =>
        answer["videobysearch"].Array
            .Select(v => v["uid"].Text is { } uid && v["title"].Text is { } title ? new Video(uid, title, v["duration"].Int ?? 0, v["visit_cnt"].Int ?? 0) : null)
            .OfType<Video>().ToList();

    /// <summary>
    /// The trailer for a title among what a search found: the most watched of those that name
    /// the title as a whole, call themselves a trailer of a film, a series or an animation, and are
    /// neither a review nor a reaction nor a game's.
    /// </summary>
    public static Video? Trailer(IEnumerable<Video> videos, string title, int? year)
    {
        var name = Words(BareTitle(title));
        if (name.Length == 0) return null;
        // A name this short is in many other titles; it must come with its year.
        var isShort = name.Length < 4;
        return videos.Where(video =>
        {
            var text = Words(video.Title);
            if (video.Duration is < 30 or > 300
                || !$" {text} ".Contains($" {name} ", StringComparison.Ordinal)
                || !TrailerWords.Any(w => text.Contains(w, StringComparison.Ordinal))
                || !KindWords.Any(w => text.Contains(w, StringComparison.Ordinal))
                || OtherWords.Any(w => text.Contains(w, StringComparison.Ordinal))) return false;
            var years = Years(text);
            if (year is { } y && years.Count > 0 && !years.Any(found => Math.Abs(found - y) <= 1)) return false;
            if (isShort) return year is { } wanted && years.Contains(wanted);
            return true;
        }).MaxBy(v => v.Visits);
    }

    /// <summary>The video's file to play: 720p where there is one, else the nearest to it.</summary>
    public static Uri? FileInVideo(Json answer)
    {
        var files = answer["video"]["file_link_all"].Array;
        foreach (var profile in new[] { "720p", "1080p", "480p", "360p", "240p", "144p" })
        {
            if (files.FirstOrDefault(f => f["profile"].Text == profile) is { } file && file["urls"].Array.FirstOrDefault()?.Url is { } url) return url;
        }
        return answer["video"]["file_link"].Url;
    }

    private static readonly string[] TrailerWords = ["تریلر", "تیزر", "trailer", "teaser"];
    private static readonly string[] KindWords = ["فیلم", "سریال", "انیمیشن", "انیمه", "کارتون", "movie", "film", "series", "anime"];
    private static readonly string[] OtherWords =
    [
        "نقد", "بررسی", "واکنش", "ری اکشن", "ریاکشن", "تحلیل", "خلاصه", "مقایسه", "پشت صحنه",
        // Not "بازی" alone: "با بازی ..." is "starring".
        "تریلر بازی", "بازی ویدیویی", "گیمر", "هوش مصنوعی", "هواداری",
        "reaction", "review", "game", "fan made", "fanmade",
    ];

    /// <summary>The title without a year at its end, as 30nama's English titles sometimes have.</summary>
    private static string BareTitle(string title) => Regex.Replace(title, @"\s*\(?(19|20)\d\d\)?\s*$", "");

    /// <summary>Lowercase words, digits in Latin, everything else a single space.</summary>
    public static string Words(string text)
    {
        var latin = new StringBuilder(text.Length);
        foreach (var c in text.ToLowerInvariant())
        {
            if (c is >= '۰' and <= '۹') latin.Append((char)('0' + (c - '۰')));
            else latin.Append(char.IsLetterOrDigit(c) ? c : ' ');
        }
        return string.Join(' ', latin.ToString().Split(' ', StringSplitOptions.RemoveEmptyEntries));
    }

    private static List<int> Years(string text) =>
        text.Split(' ').Where(w => w.Length == 4 && int.TryParse(w, out var y) && y is >= 1900 and <= 2100).Select(int.Parse).ToList();
}

/// <summary>
/// The TMDB logos of 30nama's titles, by IMDb id, as a daily workflow writes them to the
/// repository's <c>data</c> branch (Tools/logos/build_logos.py). For where TMDB is filtered: GitHub
/// is not, and the pictures themselves come through an image proxy that is not either. As LogoIndex.swift.
/// </summary>
public sealed class LogoIndex
{
    public static readonly Uri Address = new("https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/logos.json");

    /// <summary>English (or with no text), then Persian; an empty name where there is none.</summary>
    private readonly Dictionary<string, string[]> _logos;

    private LogoIndex(Dictionary<string, string[]> logos) => _logos = logos;

    public static LogoIndex? From(Json answer)
    {
        if (answer["version"].Int != 1) return null;
        var logos = new Dictionary<string, string[]>();
        foreach (var (imdb, names) in answer["logos"].Object ?? new Dictionary<string, Json>())
            logos[imdb] = names.Array.Select(n => n.Text ?? "").ToArray();
        return new LogoIndex(logos);
    }

    public int Count => _logos.Count;

    /// <summary>The title's logo in the interface's language if there is one, else English, through the proxy.</summary>
    public Uri? Logo(string? imdb, Lang lang)
    {
        if (imdb is null || !_logos.TryGetValue(imdb, out var names)) return null;
        var english = names.Length > 0 ? names[0] : "";
        var persian = names.Length > 1 ? names[1] : "";
        var name = lang == Lang.Fa && persian.Length > 0 ? persian : english;
        return name.Length == 0 ? null : Proxied(name);
    }

    /// <summary>A TMDB picture by its file name, through wsrv.nl, which fetches it from outside.</summary>
    public static Uri Proxied(string name) => new("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/" + name);
}
