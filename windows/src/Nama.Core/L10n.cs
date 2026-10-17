namespace Nama.Core;

/// <summary>
/// Interface strings. The language is an in-app setting (the catalogue itself is served in the
/// chosen language), so these live in code rather than in resources that follow the system
/// locale. The tables are in <c>L10n.Strings.g.cs</c>, copied from the Mac app's.
/// </summary>
public sealed partial class Loc(Lang lang) : IEquatable<Loc>
{
    public Lang Lang { get; } = lang;

    public string this[S key] =>
        ((Lang == Lang.Fa ? Fa : Lang == Lang.Ar ? Ar : En).GetValueOrDefault(key) ?? En.GetValueOrDefault(key)) ?? key.ToString();

    /// <summary>Digits in the interface language.</summary>
    public string Num(string s) => Lang == Lang.Fa ? Fmt.PersianDigits(s) : s;
    public string Num(int n) => Num(n.ToString(System.Globalization.CultureInfo.InvariantCulture));

    /// <summary>
    /// Text held to the interface's direction wherever it is drawn, so mixed words and figures
    /// keep their order in a place laid out left to right.
    /// </summary>
    public string Isolated(string text) => (Lang.IsRtl() ? "⁧" : "⁦") + text + "⁩";

    /// <summary>A job as TMDB names it, in the interface's language.</summary>
    public string Job(string name) => name switch
    {
        "Director" => this[S.director],
        "Creator" => this[S.creator],
        "Screenplay" or "Writer" => this[S.writer],
        "Novel" => this[S.jobNovel],
        "Original Music Composer" => this[S.jobComposer],
        "Director of Photography" => this[S.jobPhotography],
        _ => name,
    };

    public string TitleForSection(string key) => key switch
    {
        "continue" => this[S.continueWatching],
        "for_you" => this[S.forYou],
        "new_releases" => this[S.newReleases],
        "suggested" => this[S.suggested],
        "top_10" => this[S.top10],
        "movies" => this[S.movies],
        "series" => this[S.series],
        "anime" => this[S.anime],
        _ => key,
    };

    public string Kind(Post.Kinds kind) => kind switch
    {
        Post.Kinds.Series => this[S.series],
        Post.Kinds.Anime => this[S.anime],
        _ => this[S.movie],
    };

    /// <summary>"Series · 4 seasons"</summary>
    public string KindLine(Post post)
    {
        var s = Kind(post.Kind);
        if (post.IsSeries && post.Seasons is > 0 and int n)
            s += " · " + (Lang == Lang.En ? $"{n} {(n == 1 ? "season" : this[S.seasonsCount])}" : $"{Num(n)} {this[S.seasonsCount]}");
        return s;
    }

    /// <summary>The account's plan as 30nama names it: "golden" -> "طلایی".</summary>
    public string AccountType(string raw)
    {
        var fa = new Dictionary<string, string> { ["golden"] = "طلایی", ["gold"] = "طلایی", ["silver"] = "نقره‌ای", ["bronze"] = "برنزی", ["red"] = "قرمز", ["free"] = "رایگان" };
        var ar = new Dictionary<string, string> { ["golden"] = "ذهبي", ["gold"] = "ذهبي", ["silver"] = "فضي", ["bronze"] = "برونزي", ["red"] = "أحمر", ["free"] = "مجاني" };
        return Lang switch
        {
            Lang.Fa => fa.GetValueOrDefault(raw.ToLowerInvariant(), raw),
            Lang.Ar => ar.GetValueOrDefault(raw.ToLowerInvariant(), raw),
            _ => raw.Length == 0 ? raw : char.ToUpperInvariant(raw[0]) + raw[1..],
        };
    }

    /// <summary>"Subscription until 2028-07-15 · 641 days left", in the calendar of the interface language.</summary>
    public string? SubscriptionLine(User user, DateTime? now = null)
    {
        var day = (Lang == Lang.Fa ? user.ExpiryLocal?.Replace('-', '/') : null) ?? user.Expiry;
        if (day is null) return null;
        var line = $"{this[S.subscriptionUntil]} {Num(day)}";
        if (user.DaysLeft(now) is { } days) line += $" · {Num(days)} {this[S.daysLeft]}";
        return line;
    }

    /// <summary>"S2 · E5" / "فصل ۲ · قسمت ۵"</summary>
    public string EpisodeTag(int season, string episode) => Lang switch
    {
        Lang.Fa => $"فصل {Num(season)} · قسمت {Num(episode)}",
        Lang.Ar => $"الموسم {season} · الحلقة {episode}",
        _ => $"S{season} · E{episode}",
    };

    public bool Equals(Loc? other) => other is not null && other.Lang == Lang;
    public override bool Equals(object? obj) => Equals(obj as Loc);
    public override int GetHashCode() => (int)Lang;
}
