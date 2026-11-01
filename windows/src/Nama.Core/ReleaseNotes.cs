namespace Nama.Core;

/// <summary>
/// What each Windows version brought, as the app shows it under "What's new": the version sections
/// of the release's own text on GitHub, newest first. The release page goes on with the link to the
/// full changelog, the first-install steps and the checksum; none of that is shown in the app.
/// As ReleaseNotes.swift.
/// </summary>
public static class ReleaseNotes
{
    public enum Kinds
    {
        /// <summary>"۱.۰.۶ (۱۰ آبان ۱۴۰۵)"</summary>
        Version,
        /// <summary>A bold line on its own: the part of the app the lines below are about.</summary>
        Group,
        Item,
        Text,
    }

    public sealed record Block(Kinds Kind, string Text);

    public static IReadOnlyList<Block> Blocks(string body)
    {
        var blocks = new List<Block>();
        foreach (var raw in body.Split('\n'))
        {
            var line = raw.Trim();
            if (line.StartsWith("### ", StringComparison.Ordinal))
            {
                blocks.Add(new(Kinds.Version, line[4..]));
                continue;
            }
            // Nothing before the first version: that is how to install, for whoever downloads it.
            if (blocks.Count == 0 || line.Length == 0) continue;
            // The versions end where the page links to the full changelog, or goes back to HTML and code.
            if (line.Contains("](") || line.StartsWith('<') || line.StartsWith("```", StringComparison.Ordinal)) break;
            if (line.StartsWith("- ", StringComparison.Ordinal)) blocks.Add(new(Kinds.Item, line[2..]));
            else if (line.Length > 4 && line.StartsWith("**") && line.EndsWith("**") && !line[2..^2].Contains("**")) blocks.Add(new(Kinds.Group, line[2..^2]));
            else blocks.Add(new(Kinds.Text, line));
        }
        return blocks;
    }

    /// <summary>
    /// The notes in a <c>releases</c> answer (WinRelease.Feed): this version's own release, or else
    /// the newest Windows one, whose text carries every version before it (older releases are taken
    /// down). Null when there is none.
    /// </summary>
    public static IReadOnlyList<Block>? Blocks(Json feed, string version)
    {
        static string? Number(Json r) =>
            !r["draft"].Bool && r["tag_name"].Text is { } tag && tag.StartsWith(WinRelease.TagPrefix, StringComparison.Ordinal) ? tag[WinRelease.TagPrefix.Length..] : null;
        var windows = feed.Array.Where(r => Number(r) is not null).ToList();
        var release = windows.FirstOrDefault(r => Number(r) == version)
            ?? windows.Aggregate((Json?)null, (best, r) => best is null || WinRelease.IsNewer(Number(r)!, Number(best)!) ? r : best);
        if (release?["body"].Text is not { } body) return null;
        var blocks = Blocks(body);
        return blocks.Count == 0 ? null : blocks;
    }
}
