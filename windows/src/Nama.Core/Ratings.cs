using System.Globalization;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Nama.Core;

/// <summary>One title the user has rated, with just enough of the title kept to show it again without the network.</summary>
public sealed record Rating
{
    [JsonPropertyName("postID")] public int PostId { get; init; }
    /// <summary>Half-star steps from 0.5 to 5.</summary>
    [JsonPropertyName("stars")] public double Stars { get; init; }
    [JsonPropertyName("date"), JsonConverter(typeof(UnixSecondsConverter))] public DateTime Date { get; init; }
    [JsonPropertyName("titleEnglish")] public string? TitleEnglish { get; init; }
    [JsonPropertyName("titleLocal")] public string? TitleLocal { get; init; }
    [JsonPropertyName("year")] public int? Year { get; init; }
    [JsonPropertyName("isSeries")] public bool IsSeries { get; init; }
    [JsonPropertyName("isAnime")] public bool IsAnime { get; init; }
    [JsonPropertyName("genres")] public IReadOnlyList<RatedGenre> Genres { get; init; } = [];
    [JsonPropertyName("posterLarge")] public Uri? PosterLarge { get; init; }
    [JsonPropertyName("posterBig")] public Uri? PosterBig { get; init; }
    [JsonPropertyName("blurhash")] public string? Blurhash { get; init; }

    public sealed record RatedGenre([property: JsonPropertyName("name")] string Name, [property: JsonPropertyName("slug")] string Slug);

    public Rating() { }

    public Rating(Post post, double stars, DateTime? date = null)
    {
        PostId = post.Id;
        Stars = Snap(stars);
        Date = date ?? DateTime.UtcNow;
        TitleEnglish = post.TitleEnglish;
        TitleLocal = post.TitleLocal;
        Year = post.Year;
        IsSeries = post.IsSeries;
        IsAnime = post.Kind == Post.Kinds.Anime;
        Genres = post.Genres.Select(g => new RatedGenre(g.Name, g.Slug)).ToList();
        PosterLarge = post.Poster.Large;
        PosterBig = post.Poster.Big;
        Blurhash = post.Poster.Blurhash;
    }

    /// <summary>Nearest half star within 0.5...5.</summary>
    public static double Snap(double stars) => Math.Clamp(Math.Round(stars * 2, MidpointRounding.AwayFromZero) / 2, 0.5, 5);

    /// <summary>The title as a <see cref="Post"/>, so it can be drawn by the same cards and opened like any other.</summary>
    public Post? ToPost()
    {
        var poster = new Dictionary<string, object?>();
        if (Blurhash is not null) poster["preview"] = Blurhash;
        var webp = new Dictionary<string, object?>();
        if (PosterLarge is not null) webp["large"] = PosterLarge.OriginalString;
        if (PosterBig is not null) webp["big"] = PosterBig.OriginalString;
        poster["webp"] = webp;
        var title = new Dictionary<string, object?>();
        if (TitleEnglish is not null) title["english"] = TitleEnglish;
        if (TitleLocal is not null) title["local"] = TitleLocal;
        var info = new Dictionary<string, object?>();
        if (Year is not null) info["year"] = Year;
        var options = new Dictionary<string, object?> { ["is_series"] = IsSeries };
        if (IsAnime) options["title_type"] = "anime";
        return Post.Parse(Json.From(new Dictionary<string, object?>
        {
            ["id"] = PostId, ["title"] = title, ["info"] = info, ["options"] = options,
            ["genre"] = Genres.Select(g => new Dictionary<string, object?> { ["name"] = g.Name, ["slug"] = g.Slug }).ToList(),
            ["image"] = new Dictionary<string, object?> { ["poster"] = poster },
        }));
    }

    public bool Equals(Rating? other) =>
        other is not null && PostId == other.PostId && Stars == other.Stars && Date == other.Date
        && TitleEnglish == other.TitleEnglish && TitleLocal == other.TitleLocal && Year == other.Year
        && IsSeries == other.IsSeries && IsAnime == other.IsAnime && Genres.SequenceEqual(other.Genres)
        && PosterLarge == other.PosterLarge && PosterBig == other.PosterBig && Blurhash == other.Blurhash;

    public override int GetHashCode() => PostId;
}

/// <summary>Dates as seconds since 1970, the way the Mac app stores them.</summary>
internal sealed class UnixSecondsConverter : JsonConverter<DateTime>
{
    public override DateTime Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options) =>
        DateTime.UnixEpoch.AddTicks((long)Math.Round(reader.GetDouble() * TimeSpan.TicksPerSecond));

    public override void Write(Utf8JsonWriter writer, DateTime value, JsonSerializerOptions options) =>
        writer.WriteNumberValue((value.ToUniversalTime() - DateTime.UnixEpoch).Ticks / (double)TimeSpan.TicksPerSecond);
}

/// <summary>Everything the user has rated, and what that says about their taste.</summary>
public sealed class RatingBook : IEquatable<RatingBook>
{
    private readonly Dictionary<int, Rating> _ratings = [];
    /// <summary>
    /// Titles whose rating was taken away, and when. Kept so that another device, which still has
    /// the rating, learns that it is gone instead of bringing it back.
    /// </summary>
    private readonly Dictionary<int, DateTime> _removed = [];

    public IReadOnlyDictionary<int, Rating> Ratings => _ratings;
    public IReadOnlyDictionary<int, DateTime> Removed => _removed;

    public RatingBook(IEnumerable<Rating>? ratings = null)
    {
        foreach (var r in ratings ?? []) _ratings[r.PostId] = r;
    }

    private sealed record Stored(
        [property: JsonPropertyName("ratings")] List<Rating> Ratings,
        [property: JsonPropertyName("removed"), JsonConverter(typeof(RemovedConverter))] Dictionary<string, DateTime> Removed);

    private sealed class RemovedConverter : JsonConverter<Dictionary<string, DateTime>>
    {
        private static readonly UnixSecondsConverter Seconds = new();

        public override Dictionary<string, DateTime> Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
        {
            var d = new Dictionary<string, DateTime>();
            if (reader.TokenType != JsonTokenType.StartObject) throw new JsonException();
            while (reader.Read() && reader.TokenType == JsonTokenType.PropertyName)
            {
                var key = reader.GetString()!;
                reader.Read();
                d[key] = Seconds.Read(ref reader, typeof(DateTime), options);
            }
            return d;
        }

        public override void Write(Utf8JsonWriter writer, Dictionary<string, DateTime> value, JsonSerializerOptions options)
        {
            writer.WriteStartObject();
            foreach (var (k, v) in value.OrderBy(x => x.Key, StringComparer.Ordinal))
            {
                writer.WritePropertyName(k);
                Seconds.Write(writer, v, options);
            }
            writer.WriteEndObject();
        }
    }

    private static readonly JsonSerializerOptions Options = new()
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    /// <summary>Reads a stored book. Books saved before removals were recorded are a bare list of ratings.</summary>
    public static RatingBook? FromData(string data)
    {
        try
        {
            using var doc = JsonDocument.Parse(data);
            if (doc.RootElement.ValueKind == JsonValueKind.Object && doc.RootElement.Deserialize<Stored>(Options) is { } stored)
            {
                var book = new RatingBook(stored.Ratings);
                foreach (var (id, date) in stored.Removed ?? [])
                {
                    if (int.TryParse(id, out var n)) book._removed[n] = date;
                }
                return book;
            }
            if (doc.RootElement.ValueKind == JsonValueKind.Array && doc.RootElement.Deserialize<List<Rating>>(Options) is { } list)
                return new RatingBook(list);
        }
        catch (JsonException) { }
        return null;
    }

    public string Encoded() =>
        JsonSerializer.Serialize(new Stored(NewestFirst.ToList(), _removed.ToDictionary(kv => kv.Key.ToString(CultureInfo.InvariantCulture), kv => kv.Value)), Options);

    public int Count => _ratings.Count;
    public bool IsEmpty => _ratings.Count == 0;
    public IEnumerable<Rating> NewestFirst => _ratings.Values.OrderByDescending(r => r.Date).ThenByDescending(r => r.PostId);
    public double? Average => IsEmpty ? null : _ratings.Values.Average(r => r.Stars);

    public double? StarsFor(int postId) => _ratings.TryGetValue(postId, out var r) ? r.Stars : null;

    /// <summary>Sets the rating for a title, or removes it when <paramref name="stars"/> is null.</summary>
    public void Rate(Post post, double? stars, DateTime? date = null)
    {
        var when = date ?? DateTime.UtcNow;
        if (stars is { } s)
        {
            _ratings[post.Id] = new Rating(post, s, when);
            _removed.Remove(post.Id);
        }
        else if (_ratings.Remove(post.Id))
        {
            _removed[post.Id] = when;
        }
    }

    /// <summary>
    /// This book and another device's, brought together: for every title, whatever happened last
    /// stands, be it a rating or its removal. Either book can be the receiver; the result is the same.
    /// </summary>
    public RatingBook Merged(RatingBook other)
    {
        var output = new RatingBook();
        var ids = _ratings.Keys.Union(other._ratings.Keys).Union(_removed.Keys).Union(other._removed.Keys);
        foreach (var id in ids)
        {
            var rating = new[] { _ratings.GetValueOrDefault(id), other._ratings.GetValueOrDefault(id) }.OfType<Rating>()
                .OrderBy(r => r.Date).ThenBy(r => r.Stars).LastOrDefault();
            DateTime? removal = new[] { _removed, other._removed }.Where(d => d.ContainsKey(id)).Select(d => (DateTime?)d[id]).Max();
            if (rating is not null && rating.Date >= (removal ?? DateTime.MinValue)) output._ratings[id] = rating;
            else if (removal is { } r) output._removed[id] = r;
        }
        return output;
    }

    // MARK: Taste

    /// <summary>A rating above this counts for a genre, below it against.</summary>
    public const double Neutral = 3.0;

    /// <summary>
    /// How much the user likes each genre: the average of (stars - neutral) over the titles rated
    /// in it, shrunk toward zero when there are only a few, so one five-star western does not
    /// outweigh ten four-star dramas.
    /// </summary>
    public Dictionary<string, double> Taste()
    {
        var sum = new Dictionary<string, double>();
        var n = new Dictionary<string, double>();
        foreach (var r in _ratings.Values)
        {
            foreach (var g in r.Genres)
            {
                sum[g.Slug] = sum.GetValueOrDefault(g.Slug) + r.Stars - Neutral;
                n[g.Slug] = n.GetValueOrDefault(g.Slug) + 1;
            }
        }
        return sum.ToDictionary(kv => kv.Key, kv => kv.Value / (n.GetValueOrDefault(kv.Key, 1) + 2));
    }

    /// <summary>Genres the user rates highest, best first.</summary>
    public List<string> FavoriteGenres(int limit = 3) =>
        Taste().Where(kv => kv.Value > 0).OrderByDescending(kv => kv.Value).ThenByDescending(kv => kv.Key, StringComparer.Ordinal)
            .Take(limit).Select(kv => kv.Key).ToList();

    /// <summary>The genres the user rates highest, by name, with how strongly each is liked next to the strongest (0...1).</summary>
    public List<(string Name, double Strength)> FavoriteGenreNames(int limit = 4)
    {
        var taste = Taste();
        var names = new Dictionary<string, string>();
        foreach (var r in NewestFirst.Reverse())
        {
            foreach (var g in r.Genres) names[g.Slug] = g.Name;
        }
        var top = FavoriteGenres(limit);
        var best = top.Count > 0 ? taste.GetValueOrDefault(top[0]) : 0;
        if (best <= 0) return [];
        return top.Select(slug => (names.GetValueOrDefault(slug, slug), taste.GetValueOrDefault(slug) / best)).ToList();
    }

    /// <summary>How many titles got one star, two, and so on up to five. Half stars count with the star above.</summary>
    public int[] Spread()
    {
        var counts = new int[5];
        foreach (var r in _ratings.Values) counts[Math.Clamp((int)Math.Ceiling(r.Stars) - 1, 0, 4)]++;
        return counts;
    }

    /// <summary>How well a title fits the user's taste. Positive means "probably yes".</summary>
    public static double Affinity(Post post, IReadOnlyDictionary<string, double> taste)
    {
        var known = post.Genres.Where(g => taste.ContainsKey(g.Slug)).Select(g => taste[g.Slug]).ToList();
        if (known.Count == 0) return 0;
        // Between two titles that fit equally, prefer the better-regarded one.
        var acclaim = ((post.ImdbScore ?? 6.5) - 6.5) * 0.08;
        return known.Average() + acclaim;
    }

    /// <summary>Titles from <paramref name="candidates"/> the user has not rated, best fit first, leaving out poor fits.</summary>
    public List<Post> Recommend(IEnumerable<Post> candidates, int limit = 20)
    {
        var taste = Taste();
        if (taste.Count == 0) return [];
        var seen = _ratings.Keys.ToHashSet();
        return candidates
            .Where(p => seen.Add(p.Id) && !p.ComingSoon)
            .Select(p => (Post: p, Fit: Affinity(p, taste)))
            .Where(x => x.Fit > 0)
            .OrderByDescending(x => x.Fit).ThenByDescending(x => x.Post.Id)
            .Take(limit)
            .Select(x => x.Post)
            .ToList();
    }

    /// <summary>
    /// The ratings as a CSV that Letterboxd's importer reads: title, year and stars, newest first.
    /// The app cannot send ratings to 30nama, so this is how they leave it.
    /// </summary>
    public string Csv()
    {
        static string Field(string text) =>
            text.IndexOfAny([',', '"', '\n']) < 0 ? text : "\"" + text.Replace("\"", "\"\"") + "\"";
        var lines = new List<string> { "Title,Year,Rating" };
        foreach (var rating in NewestFirst)
        {
            if ((rating.ToPost()?.CleanEnglishTitle ?? rating.TitleEnglish ?? rating.TitleLocal) is not { } title) continue;
            lines.Add(string.Join(',', Field(title), rating.Year?.ToString(CultureInfo.InvariantCulture) ?? "", rating.Stars.ToString("0.0", CultureInfo.InvariantCulture)));
        }
        return string.Join('\n', lines) + "\n";
    }

    public bool Equals(RatingBook? other) =>
        other is not null
        && _ratings.Count == other._ratings.Count && _ratings.All(kv => other._ratings.TryGetValue(kv.Key, out var r) && r.Equals(kv.Value))
        && _removed.Count == other._removed.Count && _removed.All(kv => other._removed.TryGetValue(kv.Key, out var d) && d == kv.Value);

    public override bool Equals(object? obj) => Equals(obj as RatingBook);
    public override int GetHashCode() => _ratings.Count;
}
