namespace Nama.Core;

/// <summary>
/// The hosts the app talks to that Iran filters. Seen on 25 October 2026 from probes on Iranian
/// networks: their names resolve to the filter's own 10.10.34.x. Nothing that comes from these
/// may hold up a page, since there a request to them is often neither answered nor refused.
/// As FilteredHosts.swift.
/// </summary>
public static class FilteredHosts
{
    public static readonly IReadOnlyList<string> Suffixes =
    [
        "themoviedb.org", "tmdb.org", "fanart.tv",
        "youtube.com", "youtube-nocookie.com", "googlevideo.com", "ytimg.com",
        "subsource.net",
        // The website, its API and its image host; the app's own API and images are elsewhere.
        "30nama.com",
    ];

    public static bool Contains(string? host)
    {
        if (host is null) return false;
        var name = host.ToLowerInvariant();
        return Suffixes.Any(s => name == s || name.EndsWith("." + s, StringComparison.Ordinal));
    }

    /// <summary>
    /// <c>NAMA_FILTERED=1</c> in a debug build: the app as it is on an Iranian network that drops
    /// what it filters, as Irancell does. A request to a filtered host gets no answer and fails
    /// once its time is up (<see cref="ApiClient.HttpTransport"/>).
    /// </summary>
    public static bool Simulated { get; } =
#if DEBUG
        Environment.GetEnvironmentVariable("NAMA_FILTERED") == "1";
#else
        false;
#endif
}
