using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

namespace Nama.Core;

public sealed record Cue(double Start, double End, string Text);

/// <summary>WebVTT (and SRT, which differs only in details this parser already tolerates) subtitle track.</summary>
public sealed class SubtitleTrack : IEquatable<SubtitleTrack>
{
    public IReadOnlyList<Cue> Cues { get; }

    public SubtitleTrack(IEnumerable<Cue>? cues = null) => Cues = (cues ?? []).OrderBy(c => c.Start).ToList();

    public static SubtitleTrack FromVtt(string text)
    {
        var normalized = text.Replace("\r\n", "\n").Replace('\r', '\n').Replace("﻿", "");
        var cues = new List<Cue>();
        foreach (var block in normalized.Split("\n\n"))
        {
            var lines = block.Split('\n');
            var timing = Array.FindIndex(lines, l => l.Contains("-->"));
            if (timing < 0) continue;
            var parts = lines[timing].Split("-->");
            if (parts.Length != 2
                || Timestamp(parts[0]) is not { } start
                || Timestamp(parts[1].Trim().Split(' ', '\t')[0]) is not { } end) continue;
            var body = Clean(string.Join('\n', lines[(timing + 1)..]));
            if (body.Length > 0) cues.Add(new Cue(start, end, body));
        }
        return new SubtitleTrack(Directed(Persianised(cues)));
    }

    /// <summary>
    /// Persian subtitles are often typed on an Arabic keyboard: the Arabic yeh, which carries two
    /// dots beneath it at the end of a word, and the Arabic kaf, which is drawn differently. A
    /// Persian reader sees both as mistakes. Where the text is Persian (it uses letters Arabic
    /// does not have), those two are replaced by their Persian forms.
    /// </summary>
    public static List<Cue> Persianised(List<Cue> cues)
    {
        const string persianOnly = "پچژگ";
        if (!cues.Any(c => c.Text.IndexOfAny(persianOnly.ToCharArray()) >= 0)) return cues;
        return cues.Select(c => c with
        {
            Text = c.Text.Replace('ي', 'ی').Replace('ى', 'ی').Replace('ك', 'ک'),
        }).ToList();
    }

    private static bool RightToLeft(string line) => line.Any(c =>
        c is >= '֐' and <= 'ࣿ' or >= 'יִ' and <= '﷿' or >= 'ﹰ' and <= 'ﻼ');

    /// <summary>
    /// Fixes the direction each line is laid out in, for a track in a right-to-left script.
    ///
    /// Persian subtitles come in two kinds. Properly written ones end a sentence with its full
    /// stop. Most of the files in circulation, though, were prepared for players that lay every
    /// line out left to right: there the full stop is typed first, so that it lands at the left
    /// end, where a right-to-left sentence finishes. Each kind reads wrongly when drawn the
    /// other's way (the full stop jumps to the start of the line), so this works out which kind
    /// the file is and wraps every line in the direction it was written for.
    /// </summary>
    public static List<Cue> Directed(List<Cue> cues)
    {
        var lines = cues.SelectMany(c => c.Text.Split('\n', StringSplitOptions.RemoveEmptyEntries)).Where(RightToLeft).ToList();
        if (lines.Count == 0) return cues;
        // Only marks that take their direction from the line. The Arabic question mark belongs
        // to the script, stays with its sentence either way, and so says nothing here.
        const string closers = ".!?…:";
        int first = 0, last = 0;
        foreach (var line in lines)
        {
            var text = line.Trim();
            if (text.Length == 0) continue;
            if (closers.Contains(text[0])) first++;
            if (closers.Contains(text[^1])) last++;
        }
        // An isolate holds its direction whatever the direction of the view that draws it.
        var open = first > last ? "⁦" : "⁧";
        const string close = "⁩";
        return cues.Select(c => c with
        {
            // A line with nothing right-to-left in it needs no help and is left as it is.
            Text = string.Join('\n', c.Text.Split('\n').Select(l => RightToLeft(l) ? open + l + close : l)),
        }).ToList();
    }

    /// <summary>"MM:SS.mmm" or "HH:MM:SS.mmm"; SRT uses a comma before the milliseconds.</summary>
    public static double? Timestamp(string raw)
    {
        var fields = raw.Trim().Replace(',', '.').Split(':', StringSplitOptions.RemoveEmptyEntries);
        if (fields.Length is < 2 or > 3) return null;
        var total = 0.0;
        foreach (var f in fields)
        {
            if (!double.TryParse(f, NumberStyles.Float, CultureInfo.InvariantCulture, out var v) || v < 0) return null;
            total = total * 60 + v;
        }
        return total;
    }

    /// <summary>Strips markup (<c>&lt;i&gt;</c>, <c>&lt;c.yellow&gt;</c>), ASS position overrides (<c>{\an8}</c>) and decodes the few entities that occur in practice.</summary>
    public static string Clean(string raw)
    {
        var s = Regex.Replace(raw, "<[^>]+>", "");
        s = Regex.Replace(s, @"\{\\an?\d\}", "");
        foreach (var (entity, c) in new[] { ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&nbsp;", " "), ("&lrm;", ""), ("&rlm;", "") })
            s = s.Replace(entity, c);
        return s.Trim();
    }

    /// <summary>Text to show at <paramref name="t"/>, or an empty string.</summary>
    public string TextAt(double t)
    {
        if (Cues.Count == 0) return "";
        // Last cue starting at or before t.
        int lo = 0, hi = Cues.Count - 1, idx = -1;
        while (lo <= hi)
        {
            var mid = (lo + hi) / 2;
            if (Cues[mid].Start <= t) { idx = mid; lo = mid + 1; } else hi = mid - 1;
        }
        // Cues can overlap, so look a few back for one that has not ended yet.
        for (var j = idx; j >= 0 && j > idx - 4; j--)
        {
            if (Cues[j].End > t) return Cues[j].Text;
        }
        return "";
    }

    /// <summary>The track as SRT, for a player that draws subtitles itself (mpv) once the lines have been mended.</summary>
    public string ToSrt()
    {
        static string Stamp(double s)
        {
            var ms = (long)Math.Round(Math.Max(0, s) * 1000, MidpointRounding.AwayFromZero);
            return $"{ms / 3_600_000:00}:{ms / 60_000 % 60:00}:{ms / 1000 % 60:00},{ms % 1000:000}";
        }
        var sb = new StringBuilder();
        for (var i = 0; i < Cues.Count; i++)
            sb.Append(i + 1).Append('\n').Append(Stamp(Cues[i].Start)).Append(" --> ").Append(Stamp(Cues[i].End)).Append('\n').Append(Cues[i].Text).Append("\n\n");
        return sb.ToString();
    }

    public bool Equals(SubtitleTrack? other) => other is not null && Cues.SequenceEqual(other.Cues);
    public override bool Equals(object? obj) => Equals(obj as SubtitleTrack);
    public override int GetHashCode() => Cues.Count;
}
