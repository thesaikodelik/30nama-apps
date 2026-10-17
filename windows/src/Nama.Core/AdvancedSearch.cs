using System.Globalization;
using System.Text.Json;

namespace Nama.Core;

/// <summary>
/// What 30nama's advanced search can be narrowed by, as <c>advanced_search_parameters</c> lists
/// it. Only the fields the server was seen to act on are kept: it also lists quality, cast,
/// creator and release group, and ignores them.
/// </summary>
public sealed class SearchChoices
{
    public sealed record Choice(string Name, string Value);

    public IReadOnlyList<Choice> Genres { get; }
    public IReadOnlyList<Choice> Types { get; }
    public IReadOnlyList<Choice> Countries { get; }
    public IReadOnlyList<Choice> Languages { get; }
    public IReadOnlyList<Choice> Networks { get; }
    /// <summary>Lowest IMDb score to accept.</summary>
    public IReadOnlyList<Choice> Scores { get; }
    public IReadOnlyList<Choice> Ages { get; }

    public SearchChoices(Json j)
    {
        // Entries are {name, value}, or for some fields a bare value that is its own name.
        List<Choice> Choices(string key) => j[key].Array
            .Select(e => e.Object is null
                ? e.String is { Length: > 0 } bare ? new Choice(bare, bare) : null
                : e["name"].String is { } name && e["value"].String is { Length: > 0 } value ? new Choice(name, value) : null)
            .OfType<Choice>().ToList();
        Genres = Choices("genre");
        // Anime films are refused outright; anime as a whole stands for them.
        Types = Choices("type").Where(c => c.Value != "movie_anime").ToList();
        Countries = Choices("country");
        Languages = Choices("language");
        Networks = Choices("channel");
        // "Under 5.0" is sent as a negative number; only the floors are offered.
        Scores = Choices("imdb_rate").Where(c => double.TryParse(c.Value, NumberStyles.Float, CultureInfo.InvariantCulture, out var v) && v > 0).ToList();
        Ages = Choices("age");
    }
}

/// <summary>An advanced search: the fields chosen, and the order the titles come in.</summary>
public sealed record AdvancedQuery
{
    public static readonly string[] Sorts = ["relevant", "update", "year", "imdb-rate", "favorite"];

    public IReadOnlyList<string> Genres { get; init; } = [];
    public string? Type { get; init; }
    public string? Country { get; init; }
    public string? Language { get; init; }
    public string? Network { get; init; }
    public string? Score { get; init; }
    public string? Age { get; init; }
    public string? Director { get; init; }
    public int? FromYear { get; init; }
    public int? ToYear { get; init; }
    public bool X265 { get; init; }
    public bool ThreeD { get; init; }
    public bool StreamOnly { get; init; } = true;
    public string Sort { get; init; } = "relevant";

    /// <summary>How many fields narrow the search, for the count on the filters button.</summary>
    public int Count =>
        new object?[] { Type, Country, Language, Network, Score, Age, Director, FromYear, ToYear }.Count(v => v is not null)
        + Genres.Count + (X265 ? 1 : 0) + (ThreeD ? 1 : 0);

    /// <summary>The <c>parameters</c> value <c>advanced_search</c> takes: a JSON object of the chosen fields.</summary>
    public string Parameters()
    {
        var fields = new SortedDictionary<string, object>(StringComparer.Ordinal);
        if (Genres.Count > 0) fields["genre"] = Genres;
        // A list, as Filter sends it: a single value is ignored.
        if (Type is not null) fields["type"] = new[] { Type };
        if (Country is not null) fields["country"] = Country;
        if (Language is not null) fields["language"] = Language;
        if (Network is not null) fields["channel"] = Network;
        if (Score is not null && double.TryParse(Score, NumberStyles.Float, CultureInfo.InvariantCulture, out var floor)) fields["imdb_rate"] = floor;
        if (Age is not null) fields["age"] = Age;
        if (Director?.Trim() is { Length: > 0 } director) fields["director"] = director;
        // The years either way round: someone writing 2010 to 2000 means the same decade.
        if (FromYear is { } from && ToYear is { } to && from > to) (from, to) = (to, from);
        else (from, to) = (FromYear ?? 0, ToYear ?? 0);
        if (FromYear is not null || ToYear is not null)
        {
            if (from > 0) fields["min_year"] = from.ToString(CultureInfo.InvariantCulture);
            if (to > 0) fields["max_year"] = to.ToString(CultureInfo.InvariantCulture);
        }
        if (X265) fields["x265"] = "true";
        if (ThreeD) fields["3D"] = "true";
        if (StreamOnly) fields["stream"] = "true";
        return JsonSerializer.Serialize(fields, new JsonSerializerOptions { Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping });
    }

    public Dictionary<string, string> Request(int page) => new()
    {
        ["parameters"] = Parameters(), ["orderby"] = Sort, ["order"] = "DESC", ["page"] = page.ToString(CultureInfo.InvariantCulture),
    };
}
