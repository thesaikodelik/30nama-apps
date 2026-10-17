using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Nama.Core;

// Ported from 30nama/Core/Models.swift, in the same order, so the two can be read side by side.

// MARK: - Images

/// <summary>Poster variants as served by the CDN (widths 50 / 172 / 214 / 428 px, all 2:3).</summary>
public sealed record Poster
{
    public string? Blurhash { get; init; }
    public Uri? Small { get; init; }
    public Uri? Medium { get; init; }
    public Uri? Large { get; init; }
    public Uri? Big { get; init; }
    /// <summary>JPEG variant, for places that cannot take WebP.</summary>
    public Uri? Jpeg { get; init; }

    public Poster(Json j)
    {
        Blurhash = j["preview"].Text;
        Uri? Pick(string size) => j["webp"][size].Url ?? j["jpg"][size].Url;
        Small = Pick("small"); Medium = Pick("medium"); Large = Pick("large"); Big = Pick("big");
        Jpeg = j["jpg"]["large"].Url ?? j["jpg"]["medium"].Url;
    }

    public bool IsEmpty => Small is null && Medium is null && Large is null && Big is null;

    /// <summary>Smallest variant that is at least <paramref name="pixelWidth"/> wide, falling back to the largest available.</summary>
    public Uri? Url(double pixelWidth)
    {
        (double Width, Uri? Url)[] ladder = [(50, Small), (172, Medium), (214, Large), (428, Big)];
        return ladder.FirstOrDefault(x => x.Width >= pixelWidth && x.Url is not null).Url ?? Big ?? Large ?? Medium ?? Small;
    }
}

// MARK: - Post

public sealed record Genre(string Name, string Slug);

public sealed record Person(string Name, string? Role, Poster? Poster)
{
    public string Id => Name + "|" + (Role ?? "");
}

public sealed record FanScore(string Source, double Value);

/// <summary>A title (movie, series or anime) as it appears in lists and on the details page.</summary>
public sealed class Post : IEquatable<Post>
{
    public enum Kinds { Movie, Series, Anime }

    public int Id { get; }
    public Kinds Kind { get; set; } = Kinds.Movie;
    public bool IsSeries { get; set; }
    public bool HasStream { get; set; }
    public bool ComingSoon { get; set; }
    public bool FreeStream { get; set; }
    public bool PersianSubtitle { get; set; }

    public string? TitleEnglish { get; set; }
    public string? TitleLocal { get; set; }
    public string? PlotEnglish { get; set; }
    public string? PlotLocal { get; set; }

    public int? Year { get; set; }
    public int? YearEnd { get; set; }
    public int? Seasons { get; set; }
    public string? Age { get; set; }
    public int? RuntimeMinutes { get; set; }

    public IReadOnlyList<Genre> Genres { get; set; } = [];
    public Poster Poster { get; set; } = new(Json.Null);
    public Uri? Cover { get; set; }

    public double? ImdbScore { get; set; }
    public string? ImdbId { get; set; }
    public double? SiteScore { get; set; }
    /// <summary>Tomatometer, 0...100.</summary>
    public int? RottenTomatoes { get; set; }
    /// <summary>Metascore, 0...100.</summary>
    public int? Metacritic { get; set; }
    /// <summary>MyAnimeList (anime) or MyDramaList (Asian drama) score out of 10, whichever the title has.</summary>
    public FanScore? FanScore { get; set; }

    public IReadOnlyList<Person> Cast { get; set; } = [];
    public IReadOnlyList<string> Directors { get; set; } = [];
    public IReadOnlyList<string> Writers { get; set; } = [];
    public IReadOnlyList<string> Creators { get; set; } = [];
    public IReadOnlyList<string> Networks { get; set; } = [];
    public IReadOnlyList<string> Countries { get; set; } = [];
    public string? Language { get; set; }
    public string? AirStatus { get; set; }
    public string? NextEpisodeCaption { get; set; }
    public (string? Gregorian, string? Jalali) NextEpisodeDate { get; set; }

    /// <summary>Only present on list entries of the "continue watching" feed and on <c>single</c>.</summary>
    public ContinueWatching? Resume { get; set; }
    /// <summary>The pages of the subtitle site 30nama sends people to: one per language, and per season of a series.</summary>
    public IReadOnlyList<SubtitlePage> SubtitlePages { get; set; } = [];

    private Post(int id) => Id = id;

    public static Post? Parse(Json j)
    {
        if (j["id"].Int is not { } id) return null;
        var o = j["options"];
        var info = j["info"];
        var p = new Post(id)
        {
            IsSeries = o["is_series"].Bool,
            HasStream = o["stream"].Bool,
            ComingSoon = o["coming_soon"].Bool,
            FreeStream = o["free_stream"].Bool,
            PersianSubtitle = o["persian_subtitle"].Bool,
            TitleEnglish = j["title"]["english"].Text,
            TitleLocal = j["title"]["local"].Text,
            PlotEnglish = j["plot"]["english"].Text,
            PlotLocal = j["plot"]["local"].Text,
            Year = info["year"].Int,
            YearEnd = info["year_end"].Int,
            Seasons = info["seasons"].Int,
            Age = info["age"].Text,
            RuntimeMinutes = info["time"]["default"].Int is int t and > 0 ? t : null,
        };
        p.Kind = o["title_type"].Text == "anime" ? Kinds.Anime : (p.IsSeries ? Kinds.Series : Kinds.Movie);

        p.Genres = j["genre"].Array.Select(g => g["name"].Text is { } name ? new Genre(name, g["slug"].Text ?? name) : null).OfType<Genre>().ToList();
        p.Poster = new Poster(j["image"]["poster"]);
        p.Cover = j["image"]["cover"]["webp"].Url ?? j["image"]["cover"]["jpg"].Url;

        var score = j["score"];
        p.ImdbScore = Positive(score["imdb"]["score"].Double);
        p.ImdbId = score["imdb"]["link"].Text is { } link ? ImdbIdIn(link) : null;
        p.SiteScore = Positive(score["30nama"]["score"].Double);
        p.RottenTomatoes = score["rottentomatoes"]["score"].Int is int rt and > 0 ? rt : null;
        p.Metacritic = score["metacritic"]["score"].Int is int mc and > 0 ? mc : null;
        foreach (var (key, label) in new[] { ("myanimelist", "MAL"), ("mydramalist", "MDL") })
        {
            if (score[key]["score"].Double is double v and > 0) { p.FanScore = new FanScore(label, v); break; }
        }

        p.Cast = j["cast"].Array.Select(c =>
        {
            if (c["name"].Text is not { } name) return null;
            var poster = new Poster(c["image"]["poster"]);
            return new Person(name, c["as"].Text, poster.IsEmpty ? null : poster);
        }).OfType<Person>().ToList();
        static List<string> Names(Json v) => v.Array.Select(x => x["name"].Text ?? x.Text).OfType<string>().ToList();
        p.Directors = Names(j["crew"]["director"]);
        p.Writers = Names(j["crew"]["writer"]);
        p.Creators = Names(j["crew"]["creator"]);
        p.Networks = j["network"].Array.Select(x => x.Text).OfType<string>().ToList();
        p.Countries = Names(j["country"]);
        p.Language = j["language"]["primary"]["name"].Text;
        p.AirStatus = j["air"]["air_status"].Text;
        p.NextEpisodeCaption = j["air"]["next_episode_caption"].Text;
        p.NextEpisodeDate = (j["air"]["next_episode_date"]["g"].Text, j["air"]["next_episode_date"]["j"].Text);
        p.SubtitlePages = j["subtitle"].Array.Select(entry =>
        {
            if (entry["link"].Url is not { } url) return null;
            // The language is written out in Persian; the address says it in a form that does not change.
            var path = url.AbsolutePath;
            return new SubtitlePage(url, path.Contains("farsi") || path.Contains("persian"), entry["season"].Int);
        }).OfType<SubtitlePage>().ToList();
        p.Resume = ContinueWatching.Parse(j["continue_watching"]);
        return p;
    }

    private static double? Positive(double? v) => v is > 0 ? v : null;

    /// <summary>A shallow copy, for the places that change one field of a post they were handed.</summary>
    public Post Copy() => (Post)MemberwiseClone();

    /// <summary>
    /// Posts from any of the list shapes the API uses (<c>posts</c>, <c>items</c>, <c>list</c>, a bare
    /// array, and entries wrapped as <c>{post: {...}}</c>). Duplicates are dropped, order is kept.
    /// </summary>
    public static List<Post> List(Json j)
    {
        var seen = new HashSet<int>();
        var list = new List<Post>();
        foreach (var x in RawList(j))
        {
            if (Parse(x["post"].IsNull ? x : x["post"]) is { } p && seen.Add(p.Id)) list.Add(p);
        }
        return list;
    }

    /// <summary>
    /// The "continue watching" feed: like <see cref="List"/>, but each post carries its progress, which
    /// sits inside the post (as <c>continue_watching</c>, or as <c>data</c>, which is what the server
    /// sends a subscriber), next to it in a wrapper, or flat on the entry itself.
    /// </summary>
    public static List<Post> ContinueList(Json j)
    {
        var seen = new HashSet<int>();
        var list = new List<Post>();
        foreach (var x in RawList(j))
        {
            var inner = x["post"].IsNull ? x : x["post"];
            if (Parse(inner) is not { } p || !seen.Add(p.Id)) continue;
            p.Resume = ContinueWatching.Parse(x["continue_watching"]) ?? p.Resume ?? ContinueWatching.Parse(inner["data"]) ?? ContinueWatching.Parse(x);
            // The feed gives a position and a percentage to four places, but no length: work it out.
            if (p.Resume is { Duration: <= 0, Time: > 0, ReportedPercent: double percent and > 0 } resume)
                p.Resume = resume with { Duration = resume.Time * 100 / percent };
            list.Add(p);
        }
        return list;
    }

    public static IReadOnlyList<Json> RawList(Json j)
    {
        if (j.Type == Json.Kind.Array) return j.Array;
        foreach (var key in new[] { "posts", "items", "list" })
        {
            if (j[key].Type == Json.Kind.Array) return j[key].Array;
        }
        return [];
    }

    public static string? ImdbIdIn(string link) => Regex.Match(link, @"tt\d{6,10}") is { Success: true } m ? m.Value : null;

    // MARK: Display

    /// <summary>
    /// Titles arrive with the release year appended ("Animals 2026"). The year has its own place in
    /// the meta line, so drop it, but only when it really is this title's year ("Blade Runner 2049"
    /// keeps its number).
    /// </summary>
    private string? WithoutYear(string? title)
    {
        if (title is null) return null;
        foreach (var y in new[] { Year, YearEnd }.OfType<int>())
        {
            foreach (var suffix in new[] { $" {y}", $" ({y})" })
            {
                if (title.EndsWith(suffix, StringComparison.Ordinal) && title.Length > suffix.Length)
                    return title[..^suffix.Length].Trim();
            }
        }
        return title;
    }

    public string? CleanEnglishTitle => WithoutYear(TitleEnglish);
    public string? CleanLocalTitle => WithoutYear(TitleLocal);

    public string Title(Lang lang)
    {
        if (lang == Lang.Fa && CleanLocalTitle is { } l) return l;
        return CleanEnglishTitle ?? CleanLocalTitle ?? "";
    }

    /// <summary>The title in the other language, when there is one and it differs.</summary>
    public string? AlternateTitle(Lang lang)
    {
        var other = lang == Lang.Fa ? CleanEnglishTitle : CleanLocalTitle;
        return other is null || other == Title(lang) ? null : other;
    }

    public string Plot(Lang lang)
    {
        if (lang == Lang.Fa && PlotLocal is { } l) return l;
        return PlotEnglish ?? PlotLocal ?? "";
    }

    /// <summary>
    /// The day the next episode is due, when the server names one. It writes the day out,
    /// "16 October 2026 Friday"; a plain "2026-10-16" is understood too.
    /// </summary>
    public DateTime? NextEpisodeDay
    {
        get
        {
            if (NextEpisodeDate.Gregorian is not { } raw) return null;
            const DateTimeStyles utc = DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal;
            var words = string.Join(' ', raw.Split(' ', StringSplitOptions.RemoveEmptyEntries).Take(3));
            if (DateTime.TryParseExact(words, "d MMMM yyyy", CultureInfo.InvariantCulture, utc, out var day)) return day;
            return DateTime.TryParseExact(raw[..Math.Min(10, raw.Length)], "yyyy-MM-dd", CultureInfo.InvariantCulture, utc, out day) ? day : null;
        }
    }

    /// <summary>
    /// The day of the next episode as people say it, weekday first and no year: "پنجشنبه ۲۳ مهر",
    /// "Thursday 15 October". The server writes it the other way round ("۲۳ مهر ۱۴۰۵ پنجشنبه").
    /// </summary>
    public string? NextEpisodeLabel(Lang lang)
    {
        if ((lang == Lang.Fa ? NextEpisodeDate.Jalali : NextEpisodeDate.Gregorian) is not { } raw) return null;
        var parts = raw.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        // Day, month, year, then the weekday, which in Persian can be two words ("سه شنبه").
        if (parts.Length < 4) return raw;
        return string.Join(' ', string.Join(' ', parts[3..]), parts[0], parts[1]);
    }

    /// <summary>The series among <paramref name="posts"/> with an episode still to come, soonest first.</summary>
    public static List<Post> Upcoming(IEnumerable<Post> posts, DateTime? now = null)
    {
        var today = (now ?? DateTime.UtcNow).AddDays(-1);
        var seen = new HashSet<int>();
        return posts
            .Where(p => seen.Add(p.Id))
            .Select(p => (Post: p, Day: p.NextEpisodeDay))
            .Where(x => x.Day is { } d && d > today)
            .OrderBy(x => x.Day)
            .Select(x => x.Post)
            .ToList();
    }

    public bool Equals(Post? other) => other is not null && other.Id == Id;
    public override bool Equals(object? obj) => Equals(obj as Post);
    public override int GetHashCode() => Id;
}

// MARK: - Playback

public sealed record ContinueWatching
{
    public string? FileId { get; init; }
    public double Time { get; init; }
    public double Duration { get; init; }
    public int? Season { get; init; }
    public string? Episode { get; init; }
    /// <summary>Percentage watched as the server reports it, for responses that carry no duration.</summary>
    public double? ReportedPercent { get; init; }

    public static ContinueWatching? Parse(Json j)
    {
        if (j.Object is null) return null;
        var info = j["info"];
        var c = new ContinueWatching
        {
            ReportedPercent = j["progress"].Double,
            FileId = j["file_id"].Text ?? info["file_id"].Text,
            Time = j["time"].Double ?? j["position"].Double ?? 0,
            Duration = j["duration"].Double ?? 0,
            Season = Nama.Core.Episode.Number(j["season"].String ?? info["season"].String),
            Episode = j["episode"].Text ?? info["episode"].Text,
        };
        return c.FileId is null && c.Time <= 0 ? null : c;
    }

    public double Progress => Duration > 0
        ? Math.Clamp(Time / Duration, 0, 1)
        : Math.Clamp((ReportedPercent ?? 0) / 100, 0, 1);

    public int? RemainingMinutes => Duration > 0 && Time > 0
        ? Math.Max(1, (int)Math.Round((Duration - Time) / 60, MidpointRounding.AwayFromZero))
        : null;
}

/// <summary>
/// Which audio mix to open. The original file is often a codec some devices cannot play through
/// their own speakers (DTS, TrueHD); the server publishes stereo and 5.1 playlists beside it.
/// </summary>
public enum AudioMix { Original, Stereo, Surround }

/// <summary>One selectable rendition of a stream.</summary>
public sealed record Quality(string Label, Uri Url, Uri? Stereo, Uri? Surround, int Height, bool IsAuto)
{
    /// <summary>The playlist for <paramref name="mix"/>, falling back to the original when that mix was not published.</summary>
    public Uri UrlFor(AudioMix mix) => mix switch
    {
        AudioMix.Stereo => Stereo ?? Url,
        AudioMix.Surround => Surround ?? Url,
        _ => Url,
    };

    /// <summary>Mixes worth showing. Original is always there; the others only when they are a different file.</summary>
    public IReadOnlyList<AudioMix> Mixes
    {
        get
        {
            var list = new List<AudioMix>();
            if (Stereo is not null) list.Add(AudioMix.Stereo);
            if (Surround is not null) list.Add(AudioMix.Surround);
            list.Add(AudioMix.Original);
            return list;
        }
    }

    /// <summary><c>m3u8</c> is either one URL or a list of <c>{label, default, 2ch, 6ch}</c> entries where "Auto" is the adaptive master.</summary>
    public static List<Quality> Parse(Json m)
    {
        if (m.Type == Json.Kind.Array)
        {
            var list = new List<Quality>();
            foreach (var e in m.Array)
            {
                if (EntryUrl(e) is not { } url) continue;
                var label = e["label"].Text ?? "Auto";
                var isAuto = label.ToLowerInvariant() is "auto" or "خودکار";
                var height = int.TryParse(new string(label.Where(char.IsAsciiDigit).ToArray()), out var h) ? h : 0;
                list.Add(new Quality(label, url, ChannelUrl(e, "2ch"), ChannelUrl(e, "6ch"), isAuto ? 0 : height, isAuto || height == 0));
            }
            return list;
        }
        return EntryUrl(m) is { } only ? [new Quality("Auto", only, null, null, 0, true)] : [];
    }

    /// <summary>A sibling playlist (<c>2ch</c>, <c>6ch</c>). Missing keys stay null so they are not mistaken for the original.</summary>
    private static Uri? ChannelUrl(Json e, string key) => e.Type == Json.Kind.Object ? EntryUrl(e[key]) : null;

    private static Uri? EntryUrl(Json e)
    {
        switch (e.Type)
        {
            case Json.Kind.String: return e.Url;
            case Json.Kind.Array: return e.Array.Count > 0 ? EntryUrl(e.Array[0]) : null;
            case Json.Kind.Object:
                // Stereo before 5.1: a device that cannot decode the original still gets sound.
                foreach (var key in new[] { "default", "file", "url", "src", "link", "m3u8", "path", "source", "2ch", "6ch" })
                {
                    if (EntryUrl(e[key]) is { } u) return u;
                }
                return null;
            default: return null;
        }
    }
}

public static class Episode
{
    /// <summary>Leading integer of values like "3", "11,12" or "S02".</summary>
    public static int? Number(string? raw)
    {
        if (raw is null) return null;
        var digits = new string(raw.SkipWhile(c => !char.IsDigit(c)).TakeWhile(char.IsDigit).ToArray());
        return int.TryParse(digits, NumberStyles.None, CultureInfo.InvariantCulture, out var n) ? n : null;
    }

    /// <summary>Combined episodes arrive as "11,12"; show them as "11–12".</summary>
    public static string Label(string? raw, int fallback)
    {
        if (string.IsNullOrWhiteSpace(raw)) return fallback.ToString(CultureInfo.InvariantCulture);
        return Regex.Replace(raw, @"\s*[,&+\-–]\s*", "–");
    }
}

/// <summary>What to skip at a moment of playback: the intro, or the recap of the last episode.</summary>
public readonly record struct SkipTarget(bool IsIntro, double To);

/// <summary>One playable file: a movie, or an episode of a series.</summary>
public sealed record StreamItem
{
    public string FileId { get; init; } = "";
    public string? PostId { get; init; }
    public int Season { get; init; }
    public string? EpisodeRaw { get; init; }
    public string? TitleEnglish { get; init; }
    public string? TitleLocal { get; init; }
    public int? DurationMinutes { get; init; }
    public string Token { get; init; } = "";
    public double? IntroStart { get; init; }
    public double? IntroEnd { get; init; }
    public double? RecapStart { get; init; }
    public double? RecapEnd { get; init; }
    public double? CreditsStart { get; init; }
    public IReadOnlyList<Quality> Qualities { get; init; } = [];
    public IReadOnlyDictionary<string, Uri> Subtitles { get; init; } = new Dictionary<string, Uri>();

    public string Id => FileId.Length == 0 ? $"{Season}-{EpisodeRaw ?? ""}" : FileId;
    public int EpisodeNumber => Episode.Number(EpisodeRaw) ?? 0;

    public static StreamItem? Parse(Json j)
    {
        var info = j["info"];
        var opt = j["options"];
        var file = j["file"];
        var t = new[] { info["title"], info["name"], j["title"] }.FirstOrDefault(x => !x.IsNull) ?? Json.Null;
        int? duration = null;
        if ((info["duration"].Double ?? info["time"].Double) is double d and > 0)
            duration = (int)Math.Round(d > 600 ? d / 60 : d, MidpointRounding.AwayFromZero);
        var subtitles = new Dictionary<string, Uri>();
        foreach (var lang in new[] { "fa", "en" })
        {
            if (SubtitleUrl(file["subtitle"][lang]) is { } u) subtitles[lang] = u;
        }
        var item = new StreamItem
        {
            Qualities = Quality.Parse(file["m3u8"]),
            FileId = info["file_id"].Text ?? "",
            PostId = info["post_id"].Text,
            Season = Episode.Number(info["season"].String) ?? 1,
            EpisodeRaw = info["episode"].Text,
            TitleEnglish = t.Object is not null ? t["english"].Text : t.Text,
            TitleLocal = t.Object is not null ? t["local"].Text : null,
            DurationMinutes = duration,
            Token = opt["token"].Text ?? "",
            IntroStart = Seconds(opt["intro_start"]),
            IntroEnd = Seconds(opt["intro_end"]),
            RecapStart = Seconds(opt["previously_start"]),
            RecapEnd = Seconds(opt["previously_end"]),
            CreditsStart = Seconds(opt["end"]),
            Subtitles = subtitles,
        };
        return item.Qualities.Count == 0 && item.FileId.Length == 0 ? null : item;
    }

    /// <summary>Times arrive as seconds or as "HH:MM:SS".</summary>
    public static double? Seconds(Json v)
    {
        if (v.Type == Json.Kind.String && v.String!.Contains(':'))
        {
            return v.String.Split(':', StringSplitOptions.RemoveEmptyEntries)
                .Aggregate(0.0, (acc, part) => acc * 60 + (double.TryParse(part, NumberStyles.Float, CultureInfo.InvariantCulture, out var x) ? x : 0));
        }
        return v.Double;
    }

    private static Uri? SubtitleUrl(Json v)
    {
        switch (v.Type)
        {
            case Json.Kind.String: return v.Url;
            case Json.Kind.Array: return v.Array.Count > 0 ? SubtitleUrl(v.Array[0]) : null;
            case Json.Kind.Object:
                foreach (var key in new[] { "url", "file", "src", "link", "vtt" })
                {
                    if (v[key].Url is { } u) return u;
                }
                return null;
            default: return null;
        }
    }

    public string? Title(Lang lang)
    {
        if (lang == Lang.Fa && TitleLocal is { } l) return l;
        return TitleEnglish ?? TitleLocal;
    }

    /// <summary>Highest fixed rendition by default; the adaptive master when asked for or when it is all there is.</summary>
    public Quality? PickQuality(bool preferAdaptive)
    {
        var fixedOne = Qualities.Where(q => !q.IsAuto).MaxBy(q => q.Height);
        var auto = Qualities.FirstOrDefault(q => q.IsAuto);
        return preferAdaptive ? auto ?? fixedOne : fixedOne ?? auto;
    }

    /// <summary>What to skip at <paramref name="t"/>, if anything. One second of slack at the end so the button does not flash.</summary>
    public SkipTarget? SkipTargetAt(double t)
    {
        if (IntroEnd is double end and > 0 && t >= (IntroStart ?? 0) && t < end - 1) return new SkipTarget(true, end);
        if (RecapEnd is double recap and > 0 && t >= (RecapStart ?? 0) && t < recap - 1) return new SkipTarget(false, recap);
        return null;
    }
}

public sealed class StreamResult
{
    public IReadOnlyList<StreamItem> Items { get; }
    public ContinueWatching? Resume { get; }

    public StreamResult(Json j)
    {
        Items = j["hls"].Array.Select(StreamItem.Parse).OfType<StreamItem>()
            .OrderBy(x => x.Season).ThenBy(x => x.EpisodeNumber).ToList();
        Resume = ContinueWatching.Parse(j["continue_watching"]);
        if (Resume?.FileId is null) Resume = null;
    }

    public IReadOnlyList<int> Seasons => Items.Select(x => x.Season).Distinct().Order().ToList();

    /// <summary>Index of the file to resume, or the first one.</summary>
    public int StartIndex
    {
        get
        {
            if (Resume?.FileId is not { } id) return 0;
            var i = Items.ToList().FindIndex(x => x.FileId == id);
            return i < 0 ? 0 : i;
        }
    }
}

// MARK: - Feeds

public sealed record HomeSection(string Key, IReadOnlyList<Post> Posts);

public sealed class HomeFeed
{
    public IReadOnlyList<Post> Heroes { get; }
    public IReadOnlyList<HomeSection> Sections { get; }

    public static readonly string[] SectionKeys = ["new_releases", "suggested", "top_10", "movies", "series", "anime"];

    public HomeFeed(Json j)
    {
        Heroes = Post.List(j["hero_header"]).Take(8).ToList();
        Sections = SectionKeys.Select(key =>
        {
            IReadOnlyList<Post> posts = Post.List(j[key]);
            if (key == "top_10") posts = posts.Take(10).ToList();
            return posts.Count == 0 ? null : new HomeSection(key, posts);
        }).OfType<HomeSection>().ToList();
    }

    public bool IsEmpty => Heroes.Count == 0 && Sections.Count == 0;

    public IReadOnlyList<Post> Posts(string key) => Sections.FirstOrDefault(s => s.Key == key)?.Posts ?? [];

    /// <summary>
    /// Titles fit to stand behind a screen that has none of its own, such as sign-in: the
    /// best-regarded live-action films and series that have a wide picture. Animation is left
    /// out, since a cartoon still sets the wrong tone for the whole app. When nothing qualifies,
    /// anything with a wide picture will do.
    /// </summary>
    public List<Post> Backdrops(int limit = 10)
    {
        var seen = new HashSet<int>();
        var wide = Sections.SelectMany(s => s.Posts).Concat(Heroes).Where(p => p.Cover is not null && seen.Add(p.Id)).ToList();
        var fit = wide.Where(p => p.Kind != Post.Kinds.Anime && !p.Genres.Any(g => g.Slug == "animation")).ToList();
        return (fit.Count == 0 ? wide : fit).OrderByDescending(p => p.ImdbScore ?? 0).Take(limit).ToList();
    }
}

public sealed class PostPage
{
    public IReadOnlyList<Post> Posts { get; }
    public int Page { get; }
    public int Pages { get; }
    public int Total { get; }

    public PostPage(Json j)
    {
        Posts = Post.List(j);
        Page = j["page"].Int ?? 1;
        Pages = j["pages"].Int ?? 1;
        Total = j["total"].Int ?? Posts.Count;
    }
}

/// <summary>A category or genre from the <c>menu</c> action.</summary>
public sealed record MenuEntry(int Id, string Name, string Slug, int? Count, Uri? Thumb, Uri? Full)
{
    public static MenuEntry? Parse(Json j)
    {
        if (j["slug"].Text is not { } slug || j["name"].Text is not { } name) return null;
        return new MenuEntry(j["id"].Int ?? slug.GetHashCode(), name, slug, j["count"].Int,
            j["image"]["webp"]["thumb"].Url ?? j["image"]["jpg"]["thumb"].Url,
            j["image"]["webp"]["full"].Url ?? j["image"]["jpg"]["full"].Url);
    }
}

public sealed class Menu
{
    public IReadOnlyList<MenuEntry> Categories { get; }
    public IReadOnlyList<MenuEntry> Genres { get; }

    public Menu(Json j)
    {
        Categories = j["categories"].Array.Select(MenuEntry.Parse).OfType<MenuEntry>().ToList();
        Genres = j["genres"].Array.Select(MenuEntry.Parse).OfType<MenuEntry>().ToList();
    }
}

// MARK: - Subtitles from the subtitle site

/// <summary>A page on the subtitle site that lists the subtitles for one film, or one season, in one language.</summary>
public sealed record SubtitlePage(Uri Link, bool Persian, int? Season);

/// <summary>
/// Reads the subtitle site 30nama links to, so a subtitle can be fetched without visiting it.
/// The site has no interface for programs; this reads its pages, and will need mending if they change.
/// </summary>
public static class SubtitleSite
{
    /// <summary>One subtitle on a listing: where its own page is, and the releases it was timed for.</summary>
    public sealed record Entry(string Path, IReadOnlyList<string> Releases);

    private static readonly Regex Listing = new(@"href=""(/subtitle/[^""]+/\d+)""[^>]*>([^<]*)</a>", RegexOptions.Compiled);

    /// <summary>The subtitles a listing page offers. One subtitle is listed once for every release it fits.</summary>
    public static List<Entry> Entries(string html)
    {
        var order = new List<string>();
        var found = new Dictionary<string, List<string>>();
        foreach (Match match in Listing.Matches(html))
        {
            // "Farsi/Persian — Some.Film.2020.1080p.WEB-DL": the release is what follows the dash.
            var text = match.Groups[2].Value;
            var dash = text.IndexOf("— ", StringComparison.Ordinal);
            var release = (dash >= 0 ? text[(dash + 2)..] : text).Trim();
            var key = match.Groups[1].Value;
            if (!found.ContainsKey(key)) { order.Add(key); found[key] = []; }
            if (release.Length > 0 && !found[key].Contains(release)) found[key].Add(release);
        }
        return order.Select(k => new Entry(k, found[k])).ToList();
    }

    /// <summary>The address a subtitle's own page offers its file at.</summary>
    public static Uri? Download(string html) =>
        Regex.Match(html, @"https://api\.subsource\.net/v1/subtitle/download/[A-Za-z0-9]+") is { Success: true } m ? new Uri(m.Value) : null;

    /// <summary>The words of a file or release name that say what it is a copy of: "bluray", "1080p", "s01e02".</summary>
    public static HashSet<string> Words(string name)
    {
        var lowered = name.ToLowerInvariant().Replace("web-dl", "webdl").Replace("blu-ray", "bluray");
        return Regex.Split(lowered, @"[^\p{L}\p{N}]+").Where(w => w.Length > 0).ToHashSet();
    }

    /// <summary>
    /// The subtitle most likely to be in time with <paramref name="fileName"/>: for an episode it must
    /// be for that episode or for its whole season, and beyond that the more of the copy's own marks
    /// it shares (source, resolution, who released it), the better.
    /// </summary>
    public static Entry? Best(string fileName, int? season, int? episode, IEnumerable<Entry> entries)
    {
        var wanted = Words(fileName);
        var sources = new HashSet<string> { "bluray", "brrip", "bdrip", "webdl", "web", "webrip", "hdtv", "dvdrip", "hdrip" };
        Entry? best = null;
        var bestScore = int.MinValue;
        foreach (var entry in entries)
        {
            var top = int.MinValue;
            foreach (var release in entry.Releases)
            {
                var has = Words(release);
                var score = 0;
                if (season is { } s && episode is { } e)
                {
                    var tag = $"s{s:00}e{e:00}";
                    var other = has.Any(w => Regex.IsMatch(w, @"^s\d{1,2}e\d{1,3}$"));
                    if (has.Contains(tag)) score += 100;
                    else if (other) continue;
                    else score += 20;
                }
                var shared = wanted.Intersect(has).ToList();
                // The source matters most: a subtitle timed for a broadcast runs off against a disc.
                score += shared.Count(sources.Contains) * 30;
                score += shared.Count(w => !sources.Contains(w));
                top = Math.Max(top, score);
            }
            if (entry.Releases.Count == 0) top = 0;
            if (top > bestScore) { best = entry; bestScore = top; }
        }
        return best;
    }

    /// <summary>Of the subtitle files in an archive, the one for this episode; for a film, the only or first one.</summary>
    public static string? Pick(IEnumerable<string> names, int? season, int? episode)
    {
        var subtitles = names.Where(n => n.ToLowerInvariant().EndsWith(".srt", StringComparison.Ordinal)).Order(StringComparer.Ordinal).ToList();
        if (season is not { } s || episode is not { } e) return subtitles.FirstOrDefault();
        foreach (var tag in new[] { $"s{s:00}e{e:00}", $"{s}x{e:00}", $"e{e:00}" })
        {
            if (subtitles.FirstOrDefault(n => n.ToLowerInvariant().Contains(tag)) is { } match) return match;
        }
        return subtitles.Count == 1 ? subtitles[0] : null;
    }

    /// <summary>Subtitle text whatever it was saved as. Persian files are often in the Arabic Windows code page.</summary>
    public static string? Text(byte[] data)
    {
        try { return new UTF8Encoding(false, true).GetString(data); }
        catch (DecoderFallbackException) { }
        // Any even run of bytes passes for UTF-16, so that is only believed when the file says so itself.
        if (data.Length >= 2 && ((data[0] == 0xFF && data[1] == 0xFE) || (data[0] == 0xFE && data[1] == 0xFF)))
        {
            var unicode = data[0] == 0xFF ? Encoding.Unicode : Encoding.BigEndianUnicode;
            return unicode.GetString(data, 2, data.Length - 2);
        }
        Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
        return Encoding.GetEncoding(1256).GetString(data);
    }
}

// MARK: - Downloads

/// <summary>One file 30nama offers for download: a film in one quality, or one episode of a series.</summary>
public sealed record DownloadFile
{
    public required string Id { get; init; }
    public required Uri Url { get; init; }
    public required string Quality { get; init; }
    /// <summary>The size as the server words it, "781 MB".</summary>
    public string? Size { get; init; }
    public string? Encoder { get; init; }
    public IReadOnlyList<string> Tags { get; init; } = [];
    public int? Season { get; init; }
    public string? Episode { get; init; }
    /// <summary>Persian subtitles for this file, when 30nama has them.</summary>
    public Uri? Subtitle { get; init; }

    /// <summary>The name to save under: the one in the address, which carries the right extension.</summary>
    public string FileName
    {
        get
        {
            var name = Url.LastPathComponent();
            return name.Length == 0 || name == "/" ? $"{Id}.mkv" : name;
        }
    }

    /// <summary>"x265 BluRay 1080p · 30nama · 781 MB"</summary>
    public string Summary => string.Join(" · ", new[] { Quality, Encoder, Size }.Where(x => !string.IsNullOrEmpty(x)));
}

/// <summary>
/// What can be downloaded for a title. A film is a flat list of files, one per quality. A series
/// comes as packs, a season in one quality, each holding its episodes.
/// </summary>
public sealed class DownloadOptions
{
    public sealed record Pack(string Id, int Season, string Quality, string? Size, string? Encoder, IReadOnlyList<DownloadFile> Files)
    {
        public string Summary => string.Join(" · ", new[] { Quality, Encoder, Size }.Where(x => !string.IsNullOrEmpty(x)));
    }

    public IReadOnlyList<DownloadFile> Files { get; } = [];
    public IReadOnlyList<Pack> Packs { get; } = [];

    public IReadOnlyList<int> Seasons => Packs.Select(p => p.Season).Distinct().Order().ToList();
    public bool IsEmpty => Files.Count == 0 && Packs.Count == 0;

    /// <summary>
    /// The server numbers its files with eighteen digits, more than a number here keeps apart:
    /// several episodes came out with one id and all but one vanished from the list. What a
    /// file is of, and the name in its address, tell every file from every other.
    /// </summary>
    public static string Identity(Uri url, int? season, string? episode) => $"{season ?? 0}|{episode ?? ""}|{url.LastPathComponent()}";

    /// <summary>Every episode any pack of the season has, in order. A pack that lacks some of them is incomplete.</summary>
    public List<string> Episodes(int season)
    {
        var seen = new HashSet<string>();
        return Packs.Where(p => p.Season == season).SelectMany(p => p.Files).Select(f => f.Episode).OfType<string>()
            .Where(seen.Add)
            .OrderBy(e => Episode.Number(e) ?? int.MaxValue).ThenBy(e => e, StringComparer.Ordinal)
            .ToList();
    }

    public DownloadOptions(Json j)
    {
        var root = j["download"];
        static List<string> TagsOf(Json entry) => entry["tags"].Array.Select(t => t.Text).OfType<string>().ToList();
        if (root.Type == Json.Kind.Array)
        {
            Files = root.Array.Select(entry => entry["dl"].Url is { } url
                ? new DownloadFile
                {
                    Id = Identity(url, null, null), Url = url, Quality = entry["quality"].Text ?? "",
                    Size = entry["size"].Text, Encoder = entry["encoder"].Text, Tags = TagsOf(entry),
                    Subtitle = entry["persian_subtitle"].Url,
                }
                : null).OfType<DownloadFile>().ToList();
            return;
        }
        var packs = new List<Pack>();
        foreach (var group in root.Object?.Values ?? [])
        {
            foreach (var entry in group.Array)
            {
                var season = entry["season_int"].Int ?? entry["season"].Int ?? 1;
                var quality = entry["quality"].Text ?? "";
                var links = entry["link"].Array.Select(link => link["dl"].Url is { } url
                    ? new DownloadFile
                    {
                        Id = Identity(url, season, link["episode"].String), Url = url, Quality = quality,
                        Size = entry["size"].Text, Encoder = entry["encoder"].Text, Tags = TagsOf(entry),
                        Season = season, Episode = link["episode"].String, Subtitle = link["persian_subtitle"].Url,
                    }
                    : null).OfType<DownloadFile>().ToList();
                if (links.Count == 0) continue;
                packs.Add(new Pack($"{season}-{quality}-{entry["encoder"].Text ?? ""}-{packs.Count}", season, quality,
                    entry["size"].Text, entry["encoder"].Text, links));
            }
        }
        Packs = packs.OrderBy(p => p.Season).ThenBy(p => p.Quality, StringComparer.Ordinal).ToList();
    }
}

// MARK: - Narrowing a catalogue

/// <summary>The choices 30nama's advanced search offers, as the server lists them.</summary>
public sealed class FilterOptions
{
    public sealed record Choice(string Name, string Value)
    {
        public string Id => Value;
    }

    public IReadOnlyList<Choice> Genres { get; }
    public IReadOnlyList<Choice> Countries { get; }
    /// <summary>Lowest IMDb score to accept.</summary>
    public IReadOnlyList<Choice> Scores { get; }
    /// <summary>First year of each decade there are titles for, newest first.</summary>
    public IReadOnlyList<int> Decades { get; }

    public FilterOptions(Json j)
    {
        List<Choice> Choices(string key) => j[key].Array
            .Select(e => e["name"].Text is { } name && e["value"].String is { Length: > 0 } value ? new Choice(name, value) : null)
            .OfType<Choice>().ToList();
        Genres = Choices("genre");
        Countries = Choices("country");
        // "Under 5.0" is sent as a negative number; only the floors are offered.
        Scores = Choices("imdb_rate").Where(c => double.TryParse(c.Value, NumberStyles.Float, CultureInfo.InvariantCulture, out var v) && v > 0).ToList();
        var newest = (j["max_year"].Int ?? 2020) / 10 * 10;
        var oldest = Math.Max(1950, (j["min_year"].Int ?? 1950) / 10 * 10);
        var decades = new List<int>();
        for (var y = newest; y >= oldest; y -= 10) decades.Add(y);
        Decades = decades;
    }
}

/// <summary>What the user has narrowed a catalogue down to.</summary>
public sealed record Filter(string? Genre = null, string? Country = null, string? Score = null, int? Decade = null)
{
    /// <summary>First year of the decade: 2010 stands for 2010 to 2019.</summary>
    public bool IsEmpty => Genre is null && Country is null && Score is null && Decade is null;

    /// <summary>The <c>parameters</c> value <c>advanced_search</c> takes: a JSON object of the chosen fields.</summary>
    /// <param name="type">"movie", "series" or "anime", to stay inside one catalogue.</param>
    public string Parameters(string? type)
    {
        var fields = new SortedDictionary<string, object>(StringComparer.Ordinal) { ["stream"] = "true" };
        if (type is not null) fields["type"] = type;
        if (Genre is not null) fields["genre"] = Genre;
        if (Country is not null) fields["country"] = Country;
        if (Score is not null && double.TryParse(Score, NumberStyles.Float, CultureInfo.InvariantCulture, out var floor)) fields["imdb_rate"] = floor;
        if (Decade is { } d)
        {
            fields["min_year"] = d;
            fields["max_year"] = d + 9;
        }
        return JsonSerializer.Serialize(fields, new JsonSerializerOptions { Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping });
    }
}

// MARK: - Account

public sealed record User
{
    public required string Token { get; init; }
    public string? Id { get; init; }
    public string? Name { get; init; }
    public string? Type { get; init; }
    /// <summary>The day the subscription ends, as "2028-07-15".</summary>
    public string? Expiry { get; init; }
    /// <summary>The same day in the Persian calendar ("۱۴۰۷-۰۴-۲۵"), when the server gives it.</summary>
    public string? ExpiryLocal { get; init; }
    public Uri? Avatar { get; init; }

    /// <summary>The profile is returned bare, or wrapped as <c>{user: {...}}</c>, with or without the session token.</summary>
    public static User? Parse(Json j, string? fallbackToken = null)
    {
        var u = j["user_session"].IsNull && j["user"].Object is not null ? j["user"] : j;
        if ((u["user_session"].Text ?? u["token"].Text ?? fallbackToken) is not { } token) return null;
        // A subscriber's expiry arrives as {days, g, j}: the date and time in both calendars.
        var expire = u["user_expire"];
        var isObject = expire.Object is not null;
        return new User
        {
            Token = token,
            Id = u["user_id"].Text ?? u["id"].Text,
            Name = u["user_name"].Text ?? u["user_email"].Text,
            Type = u["user_type"].Text,
            Expiry = isObject ? First10(expire["g"].Text) : FormatExpiry(expire),
            ExpiryLocal = isObject ? First10(expire["j"].Text) : u["user_expire_local"].Text,
            Avatar = u["user_avatar"].Url,
        };
    }

    private static string? First10(string? s) => s is null ? null : s[..Math.Min(10, s.Length)];

    /// <summary>Whole days until the subscription ends, counting today; null when the end is unknown.</summary>
    public int? DaysLeft(DateTime? now = null)
    {
        if (Expiry is null || !DateTime.TryParseExact(Expiry, "yyyy-MM-dd", CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal, out var end)) return null;
        return Math.Max(0, (int)Math.Ceiling((end - (now ?? DateTime.UtcNow)).TotalSeconds / 86400));
    }

    /// <summary>Expiry arrives as a unix timestamp (seconds or milliseconds) or as preformatted text.</summary>
    public static string? FormatExpiry(Json v)
    {
        if (v.Text is not { } raw) return null;
        if (!Regex.IsMatch(raw, @"^\d{9,13}$") || !double.TryParse(raw, NumberStyles.None, CultureInfo.InvariantCulture, out var n)) return raw;
        if (n > 1e11) n /= 1000;
        return DateTime.UnixEpoch.AddSeconds(n).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
    }
}
