using System.Globalization;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Nama.Core;

/// <summary>
/// The actions of 30nama's website API: some of the site is only there (a person's titles, the
/// weekly schedule, the top lists, the newest titles). Same account system and same
/// <c>{success, msg, result}</c> envelope as the app API, but its own host and key, the action's
/// parameters in the path, and posts that are flat where the app API's are nested. As SiteAPI.swift.
/// </summary>
public static class SiteQuery
{
    /// <summary>
    /// A person's titles, newest first: what the site's own "explore?cast=nm…" asks. Only the site
    /// API acts on <c>cast</c>, only with the IMDb id, and only in a form field: sent as JSON or in
    /// the address, the filter is dropped without a word and the whole site comes back.
    /// </summary>
    public static (string Path, Dictionary<string, string> Form) Filmography(string imdb, int page) =>
        ($"advanced_search/page/{page}/orderby/year/order/desc",
         new Dictionary<string, string> { ["parameters"] = JsonSerializer.Serialize(new SortedDictionary<string, string> { ["cast"] = imdb }) });

    /// <summary>
    /// A person as the site's biography page has them, with their titles newest first, a page at a
    /// time: by 30nama's own id for the person (<c>cast[].id</c> of <c>single</c>), not the IMDb one.
    /// </summary>
    public static string Person(int id, int page) => page > 1 ? $"single/id/{id}/page/{page}" : $"single/id/{id}";

    /// <summary>
    /// The titles most recently added to 30nama, of one kind ("movie", "series", "anime"): what the
    /// site's own "newest" is, by post id. The app API has no such order.
    /// </summary>
    public static string Newest(string kind, string? genre, bool streamOnly, int page) =>
        $"list/type/category/value/{kind}/page/{page}/orderby/id/order/desc/streamonly/{(streamOnly ? "yes" : "no")}/genre/{genre ?? "all"}";

    public static string Schedule(Schedule.Kinds kind) => $"schedule/type/{(kind == Core.Schedule.Kinds.Anime ? "anime" : "series")}";

    public static string TopList(TopLists list) => $"toplist/list/{TopListEntry.Slug(list)}";

    /// <summary>The site's poster sizes, flat (<c>medium</c>, <c>medium_webp</c>…), in the app API's nested shape.</summary>
    public static Json PosterShape(Json v)
    {
        var webp = new Dictionary<string, object?>();
        var jpg = new Dictionary<string, object?>();
        foreach (var size in new[] { "small", "medium", "large", "big" })
        {
            webp[size] = v[size + "_webp"];
            jpg[size] = v[size];
        }
        return Json.From(new Dictionary<string, object?> { ["webp"] = webp, ["jpg"] = jpg });
    }
}

public static partial class SitePosts
{
    [GeneratedRegex(@"(?<=\s)\d{4}$")]
    private static partial Regex YearAtEnd();

    /// <summary>A post as the site API lists it, put in the app API's shape so it is read by the one parser.</summary>
    public static Post? Parse(Json j)
    {
        if (j["id"].Int is null) return null;
        var title = j["title"].Text ?? "";
        // The site has no year of its own on a list entry: it is the one at the end of the title.
        var year = YearAtEnd().Match(title) is { Success: true } m ? m.Value : null;
        var imdb = j["imdb_id"].Text is { } id ? $"https://www.imdb.com/title/{id}/" : null;
        return Post.Parse(Json.From(new Dictionary<string, object?>
        {
            ["id"] = j["id"],
            ["options"] = new Dictionary<string, object?>
            {
                ["title_type"] = j["title_type"], ["is_series"] = j["is_series"], ["stream"] = j["stream"],
                ["coming_soon"] = j["coming_soon"], ["free_stream"] = j["free_stream"], ["persian_subtitle"] = j["persian_subtitle"],
            },
            ["title"] = new Dictionary<string, object?> { ["english"] = j["title"], ["local"] = j["persian_title"] },
            ["plot"] = new Dictionary<string, object?> { ["english"] = j["english_plot"], ["local"] = j["persian_plot"] },
            ["info"] = new Dictionary<string, object?> { ["year"] = year, ["budget"] = j["budget"] },
            ["genre"] = j["genre"],
            ["image"] = new Dictionary<string, object?>
            {
                ["poster"] = SiteQuery.PosterShape(j["image"]["poster"]),
                ["cover"] = new Dictionary<string, object?> { ["webp"] = j["image"]["cover_webp"], ["jpg"] = j["image"]["cover"] },
            },
            ["score"] = new Dictionary<string, object?>
            {
                ["imdb"] = new Dictionary<string, object?> { ["score"] = j["imdb_score"], ["votes"] = j["imdb_votes"], ["link"] = imdb, ["top_250"] = j["imdb_250"] },
                ["30nama"] = new Dictionary<string, object?> { ["score"] = j["30nama_score"] },
                ["myanimelist"] = new Dictionary<string, object?> { ["score"] = j["myanimelist_score"] },
                ["mydramalist"] = new Dictionary<string, object?> { ["score"] = j["mydramalist_score"] },
            },
        }));
    }

    /// <summary>A page of the site API's lists: <c>{page, pages, posts}</c>, with no total.</summary>
    public static PostPage Page(Json j)
    {
        var posts = j["posts"].Array.Select(Parse).OfType<Post>().ToList();
        return new PostPage(posts, j["page"].Int ?? 1, j["pages"].Int ?? 1, posts.Count);
    }
}

/// <summary>
/// What the site knows of a person: the facts beside their picture, the biography 30nama wrote, and
/// the first page of their titles. Many have no biography; then only the titles are there.
/// </summary>
public sealed class PersonDetails
{
    public string? Name { get; private init; }
    public string? FullName { get; private init; }
    public string? PersianName { get; private init; }
    public string? Profession { get; private init; }
    /// <summary>As the site writes them, in Persian ("۲۴ مرداد ۱۳۵۱").</summary>
    public string? Birthdate { get; private init; }
    public string? Deathdate { get; private init; }
    public string? Age { get; private init; }
    public Uri? Imdb { get; private init; }
    public Poster? Picture { get; private init; }
    /// <summary>The biography, a paragraph each, as plain text.</summary>
    public IReadOnlyList<string> Biography { get; private init; } = [];
    public PostPage Titles { get; private init; } = new([], 1, 1, 0);

    public static PersonDetails? Parse(Json j)
    {
        if (j["id"].Int is null) return null;
        var picture = new Poster(SiteQuery.PosterShape(j["image"]["poster"]));
        return new PersonDetails
        {
            Name = j["name"].Text,
            FullName = j["full_name"].Text,
            PersianName = j["persian_name"].Text,
            Profession = j["profession"].Text,
            Birthdate = j["birthdate"].Text,
            Deathdate = j["deathdate"].Text,
            Age = j["age"].Text,
            Imdb = j["imdb"].Url,
            Picture = picture.IsEmpty ? null : picture,
            Biography = SiteText.Paragraphs(j["content"].Text ?? j["summary"].Text ?? "").Select(SiteText.TidyPersian).ToList(),
            Titles = SitePosts.Page(j["posts"]),
        };
    }
}

/// <summary>The site's texts are HTML written in its editor: paragraphs, bold, entities.</summary>
public static partial class SiteText
{
    public static string LatinDigits(string s) => string.Concat(s.Select(c =>
        c is >= '۰' and <= '۹' ? (char)('0' + (c - '۰')) : c is >= '٠' and <= '٩' ? (char)('0' + (c - '٠')) : c));

    [GeneratedRegex("\"\\s*([^\"\\n]+?)\\s*\"")] private static partial Regex StraightQuotes();
    [GeneratedRegex(@"\s+([،؛:.!؟])")] private static partial Regex SpaceBeforeStop();
    [GeneratedRegex(@"،(?=\S)")] private static partial Regex CommaWithoutSpace();
    [GeneratedRegex(@"(^|\s)(ن?می) (?=[آ-ی])")] private static partial Regex MiWithSpace();
    [GeneratedRegex(@"ه ی(?=\s|$)")] private static partial Regex EzafeWithSpace();
    [GeneratedRegex("<[^>]+>")] private static partial Regex Tag();
    [GeneratedRegex("[ \\t\\u00A0]+")] private static partial Regex Spaces();
    [GeneratedRegex("&#([xX]?)([0-9A-Fa-f]+);")] private static partial Regex Numbered();

    /// <summary>
    /// The site's Persian is typed loosely: Latin digits, a space before every comma, straight quotes
    /// that the right-to-left layout puts on the wrong side of what they quote, and the half-space
    /// left out. Put right for reading, not for meaning.
    /// </summary>
    public static string TidyPersian(string text)
    {
        // A straight quote is neither left nor right, so in Persian it lands beside the wrong word;
        // Persian quotes have a side.
        var s = StraightQuotes().Replace(text, "«$1»");
        // No space before a comma, a semicolon or a full stop; one after a comma.
        s = SpaceBeforeStop().Replace(s, "$1");
        s = CommaWithoutSpace().Replace(s, "، ");
        // The half-space after "می" and "نمی", and before the "ی" of "خانه ی".
        s = MiWithSpace().Replace(s, "$1$2\u200C");
        s = EzafeWithSpace().Replace(s, "ه\u200Cی");
        return Fmt.PersianDigits(s);
    }

    /// <summary>A heading 30nama put in a biography: a short line that is not a sentence.</summary>
    public static bool IsHeading(string paragraph) => paragraph.Length < 48 && !paragraph.Any(c => ".،؛!؟:".Contains(c));

    /// <summary>A biography this long is shown folded, with "More".</summary>
    public static bool IsLong(IReadOnlyList<string> paragraphs) => paragraphs.Sum(p => p.Length) > 1000;

    /// <summary>
    /// The start of a biography, about as tall as the card beside it: whole paragraphs up to about
    /// eight hundred letters, never ending on a heading.
    /// </summary>
    public static IReadOnlyList<string> Opening(IReadOnlyList<string> paragraphs)
    {
        var shown = new List<string>();
        var length = 0;
        foreach (var p in paragraphs)
        {
            shown.Add(p);
            length += p.Length;
            if (length > 800 && !IsHeading(p)) break;
        }
        return shown;
    }

    public static IReadOnlyList<string> Paragraphs(string html)
    {
        var s = html;
        foreach (var tag in new[] { "</p>", "<br>", "<br/>", "<br />" }) s = s.Replace(tag, "\n", StringComparison.OrdinalIgnoreCase);
        s = DecodeEntities(Tag().Replace(s, ""));
        return s.Split('\n', '\r').Select(x => Spaces().Replace(x, " ").Trim()).Where(x => x.Length > 0).ToList();
    }

    public static string DecodeEntities(string s)
    {
        foreach (var (k, v) in new[] { ("&nbsp;", " "), ("&quot;", "\""), ("&apos;", "'"), ("&lt;", "<"), ("&gt;", ">"), ("&zwnj;", "\u200C"), ("&amp;", "&") })
            s = s.Replace(k, v);
        // Numbered ones: &#8217; and &#x2019;
        return Numbered().Replace(s, m =>
        {
            var ok = m.Groups[1].Length > 0
                ? int.TryParse(m.Groups[2].Value, NumberStyles.HexNumber, null, out var v)
                : int.TryParse(m.Groups[2].Value, out v);
            return ok && v is > 0 and <= 0x10FFFF and (< 0xD800 or > 0xDFFF) ? char.ConvertFromUtf32(v) : "";
        });
    }
}

/// <summary>
/// The site's lists of the best: IMDb's 250 films and series, and the titles 30nama's own users
/// rate highest. Each comes whole, in order, with its place.
/// </summary>
public enum TopLists { ImdbMovies, ImdbSeries, SiteMovies, SiteSeries }

public sealed record TopListEntry(int Rank, Post Post)
{
    public static string Slug(TopLists list) => list switch
    {
        TopLists.ImdbMovies => "top-movie-imdb",
        TopLists.ImdbSeries => "top-series-imdb",
        TopLists.SiteMovies => "top-movie-30nama",
        _ => "top-series-30nama",
    };

    public static IReadOnlyList<TopListEntry> Parse(Json j) =>
        j["posts"].Array.Select((item, index) =>
            SitePosts.Parse(item) is { } post
                // The place comes in Persian digits ("۱۲"); the list is in order anyway.
                ? new TopListEntry(int.TryParse(SiteText.LatinDigits(item["rank"].Text ?? ""), out var rank) ? rank : index + 1, post)
                : null).OfType<TopListEntry>().ToList();
}

/// <summary>
/// The week of a series or anime: on which day each one has a new episode. The site numbers the
/// days as the Persian week does, Saturday 0 to Friday 6.
/// </summary>
public sealed class Schedule
{
    public enum Kinds { Series, Anime }

    public sealed record Entry(Post Post, int Day);

    public IReadOnlyList<Entry> Entries { get; }

    public Schedule(Json j)
    {
        var seen = new HashSet<int>();
        Entries = j["posts"].Array.Select(item =>
            SitePosts.Parse(item["post"]) is { } post && item["day"].Int is int day and >= 0 and <= 6 && seen.Add(post.Id)
                ? new Entry(post, day) : null).OfType<Entry>().ToList();
    }

    public IReadOnlyList<Post> On(int day) => Entries.Where(e => e.Day == day).Select(e => e.Post).ToList();

    /// <summary>The site's number for the day <paramref name="date"/> falls on: Saturday is 0.</summary>
    public static int Day(DateTime date) => ((int)date.DayOfWeek + 1) % 7;

    /// <summary>The week from <paramref name="from"/> on, as the site numbers its days.</summary>
    public static IReadOnlyList<int> Week(DateTime from) => Enumerable.Range(0, 7).Select(i => (Day(from) + i) % 7).ToList();

    /// <summary>The <see cref="System.DayOfWeek"/> a day of the site's is.</summary>
    public static DayOfWeek Weekday(int day) => (DayOfWeek)((day + 6) % 7);
}
