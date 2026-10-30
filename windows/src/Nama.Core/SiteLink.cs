using System.Text;

namespace Nama.Core;

/// <summary>
/// A title's page on the 30nama website, for sharing (a user on issue #2). The site's own links
/// read <c>/movie/4705/X-Men-2000</c> and <c>/series/…</c>: the number is the app's own post id
/// (checked against the app API on 30 October 2026), and the rest the English name's words joined
/// by hyphens, then the year.
/// </summary>
public static class SiteLink
{
    public const string Host = "https://30nama.com";

    public static Uri For(Post post)
    {
        var words = Words(post.TitleEnglish ?? post.Title(Lang.En) ?? "");
        // 30nama's English titles sometimes end in the year already.
        if (post.Year is { } year && !words.EndsWith("-" + year, StringComparison.Ordinal) && words != year.ToString()) words = words.Length == 0 ? year.ToString() : $"{words}-{year}";
        var kind = post.IsSeries ? "series" : "movie";
        return new Uri(words.Length == 0 ? $"{Host}/{kind}/{post.Id}" : $"{Host}/{kind}/{post.Id}/{words}");
    }

    /// <summary>Letters and digits only, each run of anything else one hyphen.</summary>
    private static string Words(string title)
    {
        var text = new StringBuilder();
        foreach (var c in title)
        {
            if (char.IsAsciiLetterOrDigit(c)) text.Append(c);
            else if (text.Length > 0 && text[^1] != '-') text.Append('-');
        }
        return text.ToString().Trim('-');
    }
}
