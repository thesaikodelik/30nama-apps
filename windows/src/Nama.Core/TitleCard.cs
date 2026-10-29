namespace Nama.Core;

/// <summary>
/// What the back of a title's poster shows (the poster turns over when clicked): another of its
/// posters, its name in its own language and script, the collection it belongs to and its place
/// there, and the logo of the company that made it. For every viewer from <c>cards/NN.json</c> on
/// the <c>data</c> branch, which a daily job builds from TMDB's API (Tools/logos/build_cards.py); a
/// viewer with a TMDB key of their own reads TMDB's answer itself, in the same shape. TMDB pictures
/// are kept as file names. As TitleCard.kt of the Android app.
/// </summary>
public sealed record TitleCard(string? Poster = null, string? OwnName = null, TitleCard.Part? Collection = null, string? CompanyLogo = null, string? CompanyName = null)
{
    /// <summary>A collection: its names (English, and Persian or Arabic where TMDB has them), and the title's place in it.</summary>
    public sealed record Part(IReadOnlyDictionary<string, string> Names, int At, int Of)
    {
        public string? Name(Lang lang) => Names.GetValueOrDefault(lang.Code()) ?? Names.GetValueOrDefault("en");

        public bool Equals(Part? other) => other is not null && At == other.At && Of == other.Of
            && Names.Count == other.Names.Count && Names.All(n => other.Names.GetValueOrDefault(n.Key) == n.Value);

        public override int GetHashCode() => HashCode.Combine(At, Of, Names.Count);
    }

    /// <summary>Nothing to put on the back: the poster does not turn.</summary>
    public bool IsEmpty => Poster is null && OwnName is null && Collection is null && CompanyLogo is null;

    public const string Base = "https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/cards/";
    private static readonly HashSet<string> Wanted = ["fa", "ar"];

    /// <summary>The file a title is kept in: the last two digits of its IMDb number.</summary>
    public static string Shard(string imdb)
    {
        var digits = new string(imdb.Where(char.IsAsciiDigit).ToArray());
        return (digits.Length > 2 ? digits[^2..] : digits).PadLeft(2, '0');
    }

    public static Uri Address(string imdb) => new($"{Base}{Shard(imdb)}.json");

    /// <summary>A TMDB picture by its file name.</summary>
    public static Uri Picture(string name) => new($"{TMDB.Images}w500/{name}");

    /// <summary>The title's card in one of the daily files; null where it is not there.</summary>
    public static TitleCard? FromShard(Json answer, string imdb)
    {
        if (answer["version"].Int != 1) return null;
        var c = answer["cards"][imdb];
        if (c.Object is null) return null;
        Part? collection = null;
        if (c["c"] is { Object: not null } col)
        {
            var names = (col["names"].Object ?? new Dictionary<string, Json>())
                .Where(kv => kv.Value.Text is not null).ToDictionary(kv => kv.Key, kv => kv.Value.Text!);
            if (names.Count > 0 && col["at"].Int is { } at && col["of"].Int is { } of && of >= 2) collection = new Part(names, at, of);
        }
        var card = new TitleCard(c["p"].Text, c["o"].Text, collection, c["l"].Text, c["n"].Text);
        return card.IsEmpty ? null : card;
    }

    /// <summary>
    /// The same card from TMDB's own answer, for a viewer with a key: <paramref name="details"/>
    /// asked with <c>append_to_response=images</c>, <paramref name="collection"/> with
    /// <c>append_to_response=translations</c>. As build_cards.py makes it.
    /// </summary>
    public static TitleCard? FromTMDB(Json details, Json? collection = null)
    {
        var main = details["poster_path"].Text;
        var poster = details["images"]["posters"].Array
            .Where(p => p["file_path"].Text is { } f && f != main && (p["iso_639_1"].Text ?? "") is "en" or "")
            .OrderByDescending(p => p["vote_average"].Double ?? 0).ThenByDescending(p => p["vote_count"].Int ?? 0)
            .FirstOrDefault()?["file_path"].Text?.TrimStart('/');
        var original = details["original_title"].Text ?? details["original_name"].Text;
        var shown = details["title"].Text ?? details["name"].Text;
        var language = details["original_language"].Text;
        var ownName = original is not null && language is not null && language != "en" && !string.Equals(original, shown, StringComparison.OrdinalIgnoreCase) ? original : null;
        var company = new[] { "production_companies", "networks" }.SelectMany(k => details[k].Array).FirstOrDefault(c => c["logo_path"].Text is not null);
        var card = new TitleCard(
            poster,
            ownName,
            collection is null ? null : CollectionOf(collection, details["id"].Int),
            company?["logo_path"].Text?.TrimStart('/').Replace(".svg", ".png"),
            company?["name"].Text);
        return card.IsEmpty ? null : card;
    }

    private static Part? CollectionOf(Json collection, int? id)
    {
        var parts = collection["parts"].Array.Where(p => p["id"].Int is not null)
            .OrderBy(p => p["release_date"].Text ?? "9999", StringComparer.Ordinal).ThenBy(p => p["id"].Int)
            .Select(p => p["id"].Int!.Value).ToList();
        if (id is not { } own || !parts.Contains(own) || parts.Count < 2) return null;
        var names = new Dictionary<string, string>();
        if (collection["name"].Text is { } english) names["en"] = english;
        foreach (var t in collection["translations"]["translations"].Array)
        {
            if (t["iso_639_1"].Text is not { } code || !Wanted.Contains(code)) continue;
            if ((t["data"]["name"].Text ?? t["data"]["title"].Text) is { } name) names[code] = name;
        }
        return names.Count == 0 ? null : new Part(names, parts.IndexOf(own) + 1, parts.Count);
    }
}
