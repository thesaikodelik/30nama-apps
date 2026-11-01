using System.Text;
using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

/// <summary>
/// The shape of the release text: how to install first, then a section per version, then the
/// changelog link, first-install steps and checksum. As ReleaseNotesTests.swift.
/// </summary>
public class ReleaseNotesTests
{
    private const string Body =
        "<div dir=\"rtl\">\r\n\r\nنسخهٔ ۱.۰.۶ ۳۰نما برای ویندوز.\r\n\r\n**اگر ۱.۰.۱ تا ۱.۰.۵ را دارید،** برنامه خودش خبر می‌دهد.\r\n\r\n" +
        "### ۱.۰.۶ (۱۰ آبان ۱۴۰۵)\r\n\r\n**پخش‌کننده**\r\n\r\n- کلیک روی تصویر فیلم را **نگه می‌دارد**.\r\n- کنار طول فیلم نوشته می‌شود.\r\n\r\n" +
        "### ۱.۰.۰ (۲۲ مهر ۱۴۰۵)\r\n\r\nاولین نسخهٔ ویندوز، به‌علاوهٔ:\r\n\r\n- دانلود فیلم.\r\n\r\n" +
        "همهٔ تغییرها با جزئیات در [تازه‌ها](https://example.com/CHANGELOG.md) آمده است.\r\n\r\n**نصب برای بار اول**\r\n\r\n</div>\r\n\r\nSHA-256: `abc`";

    [Fact]
    public void OnlyTheVersionSections()
    {
        Assert.Equal(
        [
            new(ReleaseNotes.Kinds.Version, "۱.۰.۶ (۱۰ آبان ۱۴۰۵)"),
            new(ReleaseNotes.Kinds.Group, "پخش‌کننده"),
            new(ReleaseNotes.Kinds.Item, "کلیک روی تصویر فیلم را **نگه می‌دارد**."),
            new(ReleaseNotes.Kinds.Item, "کنار طول فیلم نوشته می‌شود."),
            new(ReleaseNotes.Kinds.Version, "۱.۰.۰ (۲۲ مهر ۱۴۰۵)"),
            new(ReleaseNotes.Kinds.Text, "اولین نسخهٔ ویندوز، به‌علاوهٔ:"),
            new ReleaseNotes.Block(ReleaseNotes.Kinds.Item, "دانلود فیلم."),
        ], ReleaseNotes.Blocks(Body));
    }

    [Fact]
    public void ThisVersionsReleaseOrElseTheNewest()
    {
        static string Release(string tag, string notes) =>
            $$"""{"tag_name":"{{tag}}","draft":false,"body":"### {{notes}}\n\n- {{tag}}"}""";
        static Json Feed(params string[] releases) => Json.Parse(Encoding.UTF8.GetBytes("[" + string.Join(",", releases) + "]"));
        var feed = Feed(Release("mac-v2.0.7", "m"), Release("win-v1.0.6", "a"), Release("win-v1.0.5", "b"), Release("downloads", "d"));
        Assert.Equal("b", ReleaseNotes.Blocks(feed, "1.0.5")![0].Text);
        // An older version's own release is gone; the newest Windows one has its section too.
        Assert.Equal("a", ReleaseNotes.Blocks(feed, "1.0.4")![0].Text);
        Assert.Null(ReleaseNotes.Blocks(Feed(Release("mac-v2.0.7", "m")), "1.0.6"));
        Assert.Null(ReleaseNotes.Blocks(Feed("""{"tag_name":"win-v1.0.6","body":"Just a file."}"""), "1.0.6"));
    }
}
