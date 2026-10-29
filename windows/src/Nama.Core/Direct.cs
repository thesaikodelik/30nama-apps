using System.Text.RegularExpressions;

namespace Nama.Core;

/// <summary>
/// The "Max" quality, as the Tizen and Android apps have it: the download file itself (MKV or MP4,
/// often 4K and HDR) played straight from 30nama for the item playing, instead of downloading it
/// first. libmpv plays every sound these files carry (DTS and TrueHD too), so none is passed over:
/// the best by its lines, then its size.
/// </summary>
public static class Direct
{
    /// <summary>The file for a film, or for <paramref name="season"/> and <paramref name="episode"/> of a series; none when there is no fit.</summary>
    public static DownloadFile? Pick(DownloadOptions options, bool isSeries, int? season, string? episode)
    {
        var wanted = Episode.Number(episode);
        var files = isSeries
            ? options.Packs.Where(p => p.Season == (season ?? 1)).SelectMany(p => p.Files).Where(f => Episode.Number(f.Episode) == wanted)
            : options.Files;
        return files.OrderByDescending(Lines).ThenByDescending(f => Megabytes(f.Size)).FirstOrDefault();
    }

    private static string Words(DownloadFile f) => string.Join(' ', new[] { f.Quality, f.Encoder ?? "", f.Url.LastPathComponent() }.Concat(f.Tags)).ToLowerInvariant();

    /// <summary>The picture's lines, from what the file says: 2160 for 4K.</summary>
    public static int Lines(DownloadFile f)
    {
        var w = Words(f);
        if (w.Contains("2160") || w.Contains("4k") || w.Contains("uhd")) return 2160;
        foreach (var lines in new[] { 1440, 1080, 720, 576, 480 })
            if (w.Contains(lines.ToString())) return lines;
        return 0;
    }

    /// <summary>"781 MB", "2.4 GB" as megabytes; 0 when not said.</summary>
    public static double Megabytes(string? size)
    {
        if (size is null || Regex.Match(size, @"([0-9.]+)\s*([GMK])B", RegexOptions.IgnoreCase) is not { Success: true } m) return 0;
        if (!double.TryParse(m.Groups[1].Value, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var n)) return 0;
        return m.Groups[2].Value.ToUpperInvariant() switch { "G" => n * 1024, "K" => n / 1024, _ => n };
    }

    /// <summary>A short name for the quality menu: "2160p · HDR · x265".</summary>
    public static string Label(DownloadFile f)
    {
        var w = Words(f);
        var parts = new List<string>();
        if (Lines(f) is var lines and > 0) parts.Add($"{lines}p");
        if (w.Contains("dolby vision") || w.Contains("dovi") || Regex.IsMatch(w, @"\bdv\b")) parts.Add("Dolby Vision");
        else if (w.Contains("hdr")) parts.Add("HDR");
        if (w.Contains("x265") || w.Contains("hevc") || w.Contains("h265")) parts.Add("x265");
        return parts.Count > 0 ? string.Join(" · ", parts) : "Max";
    }

    /// <summary>The file as a quality the player can load: one address for every mix, as the file carries its own sound.</summary>
    public static Quality AsQuality(DownloadFile f) => new(Label(f), f.Url, null, null, Lines(f), false);
}
