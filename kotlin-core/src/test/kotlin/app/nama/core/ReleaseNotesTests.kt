package app.nama.core

import app.nama.core.ReleaseNotes.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shape of the release text: how to install first, then a section per version, then the
 * changelog link, first-install steps and checksum. As ReleaseNotesTests.swift.
 */
class ReleaseNotesTests {
    private val body = listOf(
        "<div dir=\"rtl\">", "",
        "نسخهٔ ۲.۰.۶ ۳۰نما برای مک.", "",
        "**اگر ۱.۲.۴ یا هر نسخهٔ ۲ را دارید،** برنامه خودش خبر می‌دهد.", "",
        "### ۲.۰.۶ (۸ آبان ۱۴۰۵)", "",
        "**پخش‌کننده**", "",
        "- کلیک روی تصویر فیلم را **نگه می‌دارد**.",
        "- کنار طول فیلم نوشته می‌شود.", "",
        "### ۱.۰.۰ (۲۲ مهر ۱۴۰۵)", "",
        "اولین نسخهٔ مک، به‌علاوهٔ:", "",
        "- دانلود فیلم.", "",
        "همهٔ تغییرها با جزئیات در [تازه‌ها](https://example.com/CHANGELOG.md) آمده است.", "",
        "**نصب برای بار اول**", "",
        "</div>", "",
        "SHA-256: `abc`",
    ).joinToString("\r\n")

    @Test fun onlyTheVersionSections() {
        assertEquals(listOf(
            Block.Version("۲.۰.۶ (۸ آبان ۱۴۰۵)"),
            Block.Group("پخش‌کننده"),
            Block.Item("کلیک روی تصویر فیلم را **نگه می‌دارد**."),
            Block.Item("کنار طول فیلم نوشته می‌شود."),
            Block.Version("۱.۰.۰ (۲۲ مهر ۱۴۰۵)"),
            Block.Text("اولین نسخهٔ مک، به‌علاوهٔ:"),
            Block.Item("دانلود فیلم."),
        ), ReleaseNotes.blocks(body))
    }

    @Test fun thisVersionsReleaseOrElseTheNewest() {
        fun release(tag: String, notes: String) = mapOf("tag_name" to tag, "draft" to false, "body" to "### $notes\n\n- $tag")
        val feed = Json.of(listOf(release("win-v1.0.5", "w"), release("android-v1.1.3", "a"), release("android-v1.1.2", "b"), release("downloads", "d")))
        assertEquals(Block.Version("b"), ReleaseNotes.blocks(feed, "1.1.2", "android-v")?.first())
        // An older version's own release is gone; the newest one of this app has its section too.
        assertEquals(Block.Version("a"), ReleaseNotes.blocks(feed, "1.1.1", "android-v")?.first())
        assertNull(ReleaseNotes.blocks(Json.of(listOf(release("win-v1.0.5", "w"))), "1.1.3", "android-v"))
        assertNull(ReleaseNotes.blocks(Json.of(listOf(mapOf("tag_name" to "android-v1.1.3", "body" to "Just a file."))), "1.1.3", "android-v"))
    }
}
