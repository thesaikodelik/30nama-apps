namespace Nama.Core;

/// <summary>
/// The titles new on 30nama since the last look, for an alert the user turns on in Settings (off by
/// default; from vispar). Each look takes the newest films and series; the first only remembers
/// them, so turning the alert on never brings a flood, and after that a title not seen before is
/// news. Only the newest few hundred are remembered.
/// </summary>
public sealed class NewTitles
{
    public const int Kept = 300;

    private readonly List<int> _seen;

    /// <summary>Whether a first look has been made, after which titles not seen are news.</summary>
    public bool Seeded { get; private set; }

    public IReadOnlyList<int> Seen => _seen;

    public NewTitles(IEnumerable<int>? seen = null, bool seeded = false)
    {
        _seen = seen?.ToList() ?? [];
        Seeded = seeded;
    }

    /// <summary>
    /// Takes in the newest titles, newest first, and returns those not seen before, in the same
    /// order: none on the first look. Nothing came (no answer) changes nothing.
    /// </summary>
    public List<Post> Look(IReadOnlyList<Post> newest)
    {
        if (newest.Count == 0) return [];
        var known = _seen.ToHashSet();
        var fresh = Seeded ? newest.Where(p => !known.Contains(p.Id)).DistinctBy(p => p.Id).ToList() : [];
        foreach (var post in newest.Reverse()) if (known.Add(post.Id)) _seen.Add(post.Id);
        if (_seen.Count > Kept) _seen.RemoveRange(0, _seen.Count - Kept);
        Seeded = true;
        return fresh;
    }

    /// <summary>The alert's text: up to three names, and how many more.</summary>
    public static string Line(IReadOnlyList<Post> fresh, Lang lang, Func<int, string> more)
    {
        var names = string.Join(lang == Lang.En ? ", " : "، ", fresh.Take(3).Select(p => p.Title(lang)));
        return fresh.Count > 3 ? $"{names} {more(fresh.Count - 3)}" : names;
    }
}
