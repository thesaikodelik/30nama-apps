namespace Nama.Core;

/// <summary>
/// Searches that led somewhere (a title was opened from them), newest first, eight at most, as the
/// Android TV app keeps them. Typed with an Arabic keyboard, ي and ك are the Persian ی and ک, so
/// the same search is not kept twice.
/// </summary>
public sealed class RecentSearches(IEnumerable<string>? kept = null)
{
    public const int Most = 8;

    private List<string> _items = (kept ?? []).Where(q => q.Trim().Length > 0).Take(Most).ToList();

    public IReadOnlyList<string> Items => _items;

    public void Add(string query)
    {
        var clean = Clean(query);
        if (clean.Length < 2) return;
        _items = [clean, .. _items.Where(q => !string.Equals(q, clean, StringComparison.OrdinalIgnoreCase))];
        if (_items.Count > Most) _items.RemoveRange(Most, _items.Count - Most);
    }

    public void Remove(string query) => _items.RemoveAll(q => string.Equals(q, Clean(query), StringComparison.OrdinalIgnoreCase));

    public void Clear() => _items.Clear();

    public static string Clean(string query) =>
        string.Join(' ', query.Replace('ي', 'ی').Replace('ى', 'ی').Replace('ك', 'ک').Split(' ', StringSplitOptions.RemoveEmptyEntries));
}
