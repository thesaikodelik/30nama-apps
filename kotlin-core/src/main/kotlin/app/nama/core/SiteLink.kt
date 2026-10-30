package app.nama.core

/**
 * A title's page on the 30nama website, for sharing (a user on issue #2). The site's own links read
 * `/movie/4705/X-Men-2000` and `/series/…`: the number is the app's own post id (checked against
 * the app API on 30 October 2026), and the rest the English name's words joined by hyphens, then
 * the year. As Windows' SiteLink.cs, with the same tests.
 */
object SiteLink {
    const val HOST = "https://30nama.com"

    fun of(post: Post): String {
        var words = words(post.titleEnglish ?: post.title(Lang.en))
        // 30nama's English titles sometimes end in the year already.
        post.year?.let { year ->
            if (!words.endsWith("-$year") && words != year.toString()) words = if (words.isEmpty()) year.toString() else "$words-$year"
        }
        val kind = if (post.isSeries) "series" else "movie"
        return if (words.isEmpty()) "$HOST/$kind/${post.id}" else "$HOST/$kind/${post.id}/$words"
    }

    /** Letters and digits only, each run of anything else one hyphen. */
    private fun words(title: String): String {
        val text = StringBuilder()
        for (c in title) {
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') text.append(c)
            else if (text.isNotEmpty() && text.last() != '-') text.append('-')
        }
        return text.toString().trim('-')
    }
}
