package app.nama.core

import java.net.URI

/**
 * A title's trailer from Aparat, the Iranian video site: open in Iran, where TMDB and YouTube are
 * filtered, searchable without a key, and its videos are plain files the app can play itself.
 * What is there is uploaded by anyone, so a video is taken only when its title says plainly that
 * it is this title's trailer.
 */
object Aparat {
    data class Video(val uid: String, val title: String, val duration: Int, val visits: Int)

    fun searchRequest(title: String): HttpRequest {
        val query = "تریلر " + bareTitle(title)
        // As a path segment: what a path may hold, but not "/".
        val text = percentEncode(query) { it.isUnreserved() || it in "!$&'()*+,;=:@" }
        return HttpRequest("https://www.aparat.com/etc/api/videoBySearch/text/$text/perpage/20", timeoutSeconds = 15.0)
    }

    fun videoRequest(uid: String): HttpRequest = HttpRequest("https://www.aparat.com/etc/api/video/videohash/$uid", timeoutSeconds = 15.0)

    fun videosInSearch(answer: Json): List<Video> = answer["videobysearch"].array.mapNotNull { v ->
        val uid = v["uid"].text ?: return@mapNotNull null
        val title = v["title"].text ?: return@mapNotNull null
        Video(uid, title, v["duration"].int ?: 0, v["visit_cnt"].int ?: 0)
    }

    /**
     * The trailer for a title among what a search found: the most watched of those that name
     * the title as a whole, call themselves a trailer of a film, a series or an animation, and are
     * neither a review nor a reaction nor a game's.
     */
    fun trailer(videos: List<Video>, title: String, year: Int?): Video? {
        val name = words(bareTitle(title))
        if (name.isEmpty()) return null
        // A name this short is in many other titles; it must come with its year.
        val short = name.length < 4
        return videos.filter { video ->
            val text = words(video.title)
            if (video.duration !in 30..300 || !" $text ".contains(" $name ") ||
                trailerWords.none { text.contains(it) } || kindWords.none { text.contains(it) } ||
                otherWords.any { text.contains(it) }
            ) return@filter false
            val years = years(text)
            if (year != null && years.isNotEmpty() && years.none { Math.abs(it - year) <= 1 }) return@filter false
            if (short) return@filter year != null && year in years
            true
        }.maxByOrNull { it.visits }
    }

    /** The video's file to play: 720p where there is one, else the nearest to it. */
    fun fileInVideo(answer: Json): URI? {
        val files = answer["video"]["file_link_all"].array
        for (profile in listOf("720p", "1080p", "480p", "360p", "240p", "144p")) {
            val file = files.firstOrNull { it["profile"].text == profile } ?: continue
            file["urls"].array.firstOrNull()?.url?.let { return it }
        }
        return answer["video"]["file_link"].url
    }

    private val trailerWords = listOf("تریلر", "تیزر", "trailer", "teaser")
    private val kindWords = listOf("فیلم", "سریال", "انیمیشن", "انیمه", "کارتون", "movie", "film", "series", "anime")
    private val otherWords = listOf(
        "نقد", "بررسی", "واکنش", "ری اکشن", "ریاکشن", "تحلیل", "خلاصه", "مقایسه", "پشت صحنه",
        // Not "بازی" alone: "با بازی ..." is "starring".
        "تریلر بازی", "بازی ویدیویی", "گیمر", "هوش مصنوعی", "هواداری",
        "reaction", "review", "game", "fan made", "fanmade",
    )

    private val trailingYear = Regex("(?U)\\s*\\(?(19|20)\\d\\d\\)?\\s*$")

    /** The title without a year at its end, as 30nama's English titles sometimes have. */
    private fun bareTitle(title: String): String = title.replace(trailingYear, "")

    /** Lowercase words, digits in Latin, everything else a single space. */
    fun words(text: String): String {
        val latin = buildString {
            for (c in text.lowercase()) {
                val digit = "۰۱۲۳۴۵۶۷۸۹".indexOf(c)
                append(if (digit >= 0) '0' + digit else if (c.isLetterOrDigit()) c else ' ')
            }
        }
        return latin.split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun years(text: String): List<Int> = text.split(' ').mapNotNull { word ->
        if (word.length != 4) null else word.asciiInt()?.takeIf { it in 1900..2100 }
    }
}

/** Where a title's trailer is: on YouTube (as TMDB knows it) or on Aparat. */
sealed class TrailerVideo {
    data class Youtube(val id: String) : TrailerVideo()
    data class Aparat(val uid: String) : TrailerVideo()
}
