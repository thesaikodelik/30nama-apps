namespace Nama.Core;

/// <summary>
/// Titles the user took off "continue watching", and where they had got to when they did.
///
/// The server is asked to forget them too, but goes on listing them for a while from its cache,
/// so the app hides them itself. A title comes back once it is watched again: its position then
/// moves on from the one it was dismissed at.
/// </summary>
public sealed class ContinueDismissals : IEquatable<ContinueDismissals>
{
    private Dictionary<int, double> _positions = [];

    public IReadOnlyDictionary<int, double> Positions => _positions;

    public ContinueDismissals() { }

    /// <summary>From what <see cref="Stored"/> gave back. Keys are post ids written as text.</summary>
    public ContinueDismissals(IReadOnlyDictionary<string, double> stored)
    {
        foreach (var (key, time) in stored)
        {
            if (int.TryParse(key, out var id)) _positions[id] = time;
        }
    }

    public Dictionary<string, double> Stored => _positions.ToDictionary(kv => kv.Key.ToString(), kv => kv.Value);

    public void Dismiss(Post post) => _positions[post.Id] = post.Resume?.Time ?? 0;

    /// <summary>
    /// The list without what was dismissed and not watched since. Entries for titles no longer on
    /// the list are dropped, so the record stays as long as the list at most.
    /// </summary>
    public List<Post> Filter(IReadOnlyList<Post> posts)
    {
        var shown = posts.Where(p => !_positions.TryGetValue(p.Id, out var at) || Math.Abs((p.Resume?.Time ?? 0) - at) > 1).ToList();
        var listed = posts.Select(p => p.Id).ToHashSet();
        var shownIds = shown.Select(p => p.Id).ToHashSet();
        _positions = _positions.Where(kv => listed.Contains(kv.Key) && !shownIds.Contains(kv.Key)).ToDictionary();
        return shown;
    }

    public bool Equals(ContinueDismissals? other) =>
        other is not null && _positions.Count == other._positions.Count && _positions.All(kv => other._positions.TryGetValue(kv.Key, out var v) && v == kv.Value);

    public override bool Equals(object? obj) => Equals(obj as ContinueDismissals);
    public override int GetHashCode() => _positions.Count;
}
