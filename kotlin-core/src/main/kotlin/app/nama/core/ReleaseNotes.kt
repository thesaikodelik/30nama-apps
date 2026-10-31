package app.nama.core

/**
 * What each version brought, as the app shows it under "What's new": the version sections of the
 * release's own text on GitHub, newest first. The release page goes on with the link to the full
 * changelog, the first-install steps and the checksum; none of that is shown in the app.
 * As 30nama/Core/ReleaseNotes.swift, with its tests.
 */
object ReleaseNotes {
    sealed interface Block {
        /** "۲.۰.۶ (۸ آبان ۱۴۰۵)" */
        data class Version(val title: String) : Block
        /** A bold line on its own: the part of the app the lines below are about. */
        data class Group(val title: String) : Block
        data class Item(val text: String) : Block
        data class Text(val text: String) : Block
    }

    fun blocks(body: String): List<Block> {
        val blocks = mutableListOf<Block>()
        for (raw in body.split("\r\n", "\n", "\r")) {
            val line = raw.trim()
            if (line.startsWith("### ")) {
                blocks += Block.Version(line.drop(4))
                continue
            }
            // Nothing before the first version: that is how to install, for whoever downloads it.
            if (blocks.isEmpty() || line.isEmpty()) continue
            // The versions end where the page links to the full changelog, or goes back to HTML and code.
            if (line.contains("](") || line.startsWith("<") || line.startsWith("```")) break
            blocks += when {
                line.startsWith("- ") -> Block.Item(line.drop(2))
                line.length > 4 && line.startsWith("**") && line.endsWith("**") && !line.drop(2).dropLast(2).contains("**") -> Block.Group(line.drop(2).dropLast(2))
                else -> Block.Text(line)
            }
        }
        return blocks
    }

    /**
     * The notes in a `releases` answer: this version's own release (tags [tagPrefix] + version), or
     * else the newest one of that app, whose text carries every version before it (older releases
     * are taken down). Null when there is none.
     */
    fun blocks(feed: Json, version: String, tagPrefix: String): List<Block>? {
        fun number(r: Json): String? {
            if (r["draft"].bool) return null
            val tag = r["tag_name"].text ?: return null
            return if (tag.startsWith(tagPrefix)) tag.drop(tagPrefix.length) else null
        }
        val mine = feed.array.filter { number(it) != null }
        val release = mine.firstOrNull { number(it) == version } ?: mine.maxWithOrNull { a, b -> compare(number(a)!!, number(b)!!) } ?: return null
        val body = release["body"].text ?: return null
        return blocks(body).takeIf { it.isNotEmpty() }
    }

    private fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
