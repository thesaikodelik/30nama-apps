namespace Nama.Core;

/// <summary>
/// The days the user's series have their next episode, as last seen, and which of them have come.
///
/// The server tells only the day the next episode airs, and moves on to the one after once it has.
/// So each look remembers the days still ahead, and a remembered day that has passed by the next
/// look is an episode out since. Half a day is allowed after it for the episode to reach 30nama.
/// As EpisodeAlerts.swift.
/// </summary>
public sealed class EpisodeAlerts
{
    public static readonly TimeSpan Grace = TimeSpan.FromHours(12);

    private readonly Dictionary<int, DateTime> _expected = [];

    public IReadOnlyDictionary<int, DateTime> Expected => _expected;

    public EpisodeAlerts() { }

    /// <summary>As kept on disk: post id to the day, in seconds since 1970.</summary>
    public EpisodeAlerts(IReadOnlyDictionary<string, double> stored)
    {
        foreach (var (key, time) in stored)
            if (int.TryParse(key, out var id)) _expected[id] = DateTime.UnixEpoch.AddSeconds(time);
    }

    public Dictionary<string, double> Stored => _expected.ToDictionary(kv => kv.Key.ToString(), kv => (kv.Value - DateTime.UnixEpoch).TotalSeconds);

    /// <summary>
    /// Takes in the series followed now and returns those with an episode out since the last look.
    /// A series no longer followed is forgotten, and says nothing.
    /// </summary>
    public List<Post> Update(IEnumerable<Post> followed, DateTime? now = null)
    {
        var time = now ?? DateTime.UtcNow;
        var byId = new Dictionary<int, Post>();
        foreach (var post in followed) byId.TryAdd(post.Id, post);
        var output = new List<Post>();
        foreach (var (id, day) in _expected.ToList())
        {
            if (day + Grace > time) continue;
            if (byId.TryGetValue(id, out var post)) output.Add(post);
            _expected.Remove(id);
        }
        foreach (var id in _expected.Keys.Where(id => !byId.ContainsKey(id)).ToList()) _expected.Remove(id);
        foreach (var post in byId.Values)
            if (post.NextEpisodeDay is { } day && day + Grace > time) _expected[post.Id] = day;
        return output.OrderBy(p => p.Id).ToList();
    }
}
