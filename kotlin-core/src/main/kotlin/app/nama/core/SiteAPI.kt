package app.nama.core

import java.net.URI
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * 30nama's website talks to an API of its own beside the one the apps use, and some of the site
 * is only there: a person's titles, the weekly schedule, the top lists, the newest titles. Same
 * account system and same `{success, msg, result}` envelope, but its own host and key, the
 * action's parameters in the path, and posts that are flat where the app API's are nested.
 *
 * The key is the one the site gives every browser. It is not kept in the repository: the app
 * passes it in from its build configuration. Without it, or if 30nama changes it, these parts of
 * the app stay hidden and the rest carries on: everything else is on the app API.
 */
data class SiteConfig(
    val host: String = "https://interface.30nama.com/api/v1",
    val key: String? = null,
    val platform: String = "Website",
)

/** A site action: its path, and the form that goes with it. */
data class SiteRequest(val path: String, val form: Map<String, String>)

object SiteQuery {
    /**
     * A person's titles, newest first: what the site's own "explore?cast=nm…" asks. Only the site
     * API acts on `cast`, and only with the IMDb id; the app API ignores it.
     */
    fun filmography(imdb: String, page: Int): SiteRequest =
        SiteRequest("advanced_search/page/$page/orderby/year/order/desc", mapOf("parameters" to Json.of(mapOf("cast" to imdb)).serialize(true)))

    /**
     * A person as the site's biography page has them, with their titles newest first, a page at a
     * time: by 30nama's own id for the person (`cast[].id` of `single`), not the IMDb one.
     */
    fun person(id: Int, page: Int): String = if (page > 1) "single/id/$id/page/$page" else "single/id/$id"

    /**
     * The titles most recently added to 30nama, of one kind ("movie", "series", "anime"): what
     * the site's own "newest" is, by post id. The app API has no such order.
     */
    fun newest(kind: String, genre: String?, streamOnly: Boolean, page: Int): String =
        "list/type/category/value/$kind/page/$page/orderby/id/order/desc/streamonly/${if (streamOnly) "yes" else "no"}/genre/${genre ?: "all"}"

    /** A member's public page on the site: it has the first and last name the app API leaves out. */
    fun profile(username: String): String = "profile/user/" + percentEncode(username) { it.isUnreserved() || it in "!$&'()*+,;=:@" }

    /** The site's news, reviews and 30nama's own announcements, newest first, 24 a page. */
    fun articles(kind: Article.Kind, page: Int): String = "article/type/${kind.name}/page/$page"

    /** One article whole: the same action as a title's or a person's page. */
    fun article(id: Int): String = "single/id/$id"

    fun schedule(kind: Schedule.Kind): String = "schedule/type/${kind.name}"

    fun topList(list: TopList): String = "toplist/list/${list.rawValue}"

    /** The site's poster sizes, flat (`medium`, `medium_webp`…), in the app API's nested shape. */
    fun posterShape(v: Json): Json {
        val webp = LinkedHashMap<String, Json>()
        val jpg = LinkedHashMap<String, Json>()
        for (size in listOf("small", "medium", "large", "big")) {
            webp[size] = v[size + "_webp"]
            jpg[size] = v[size]
        }
        return Json.Obj(mapOf("webp" to Json.Obj(webp), "jpg" to Json.Obj(jpg)))
    }
}

/**
 * What the site knows of a person: the facts beside their picture, the biography 30nama wrote,
 * and the first page of their titles. Many have no biography; then only the titles are there.
 */
data class PersonDetails(
    val name: String?,
    val fullName: String?,
    val persianName: String?,
    val profession: String?,
    /** As the site writes them, in Persian ("۲۴ مرداد ۱۳۵۱"). */
    val birthdate: String?,
    val deathdate: String?,
    val age: String?,
    val imdb: URI?,
    val picture: Poster?,
    /** The biography, a paragraph each, as plain text. */
    val biography: List<String>,
    val titles: PostPage,
) {
    companion object {
        fun fromSite(j: Json): PersonDetails? {
            if (j["id"].int == null) return null
            val p = Poster.from(SiteQuery.posterShape(j["image"]["poster"]))
            return PersonDetails(
                name = j["name"].text,
                fullName = j["full_name"].text,
                persianName = j["persian_name"].text,
                profession = j["profession"].text,
                birthdate = j["birthdate"].text,
                deathdate = j["deathdate"].text,
                age = j["age"].text,
                imdb = j["imdb"].url,
                picture = if (p.isEmpty) null else p,
                biography = SiteText.paragraphs(j["content"].text ?: j["summary"].text ?: "").map { SiteText.tidyPersian(it) },
                titles = PostPage.fromSite(j["posts"]),
            )
        }
    }
}

/** The site's texts are HTML written in its editor: paragraphs, bold, entities. */
object SiteText {
    fun latinDigits(s: String): String = buildString(s.length) {
        for (c in s) {
            val persian = "۰۱۲۳۴۵۶۷۸۹".indexOf(c)
            val arabic = "٠١٢٣٤٥٦٧٨٩".indexOf(c)
            append(if (persian >= 0) '0' + persian else if (arabic >= 0) '0' + arabic else c)
        }
    }

    private val straightQuotes = unicodeRegex("\"\\s*([^\"\\n]+?)\\s*\"")
    private val spaceBeforeMark = unicodeRegex("\\s+([،؛:.!؟])")
    private val commaWithoutSpace = unicodeRegex("،(?=\\S)")
    private val mi = unicodeRegex("(^|\\s)(ن?می) (?=[آ-ی])")
    private val ezafe = unicodeRegex("ه ی(?=\\s|$)")

    /**
     * The site's Persian is typed loosely: Latin digits, a space before every comma, straight
     * quotes that the right-to-left layout puts on the wrong side of what they quote, and the
     * half-space left out. Put right for reading, not for meaning.
     */
    fun tidyPersian(text: String): String {
        var s = text
        // A straight quote is neither left nor right, so in Persian it lands beside the wrong word;
        // Persian quotes have a side.
        s = s.replace(straightQuotes, "«$1»")
        // No space before a comma, a semicolon or a full stop; one after a comma.
        s = s.replace(spaceBeforeMark, "$1")
        s = s.replace(commaWithoutSpace, "، ")
        // The half-space after "می" and "نمی", and before the "ی" of "خانه ی".
        s = s.replace(mi, "$1$2\u200C")
        s = s.replace(ezafe, "ه\u200Cی")
        return Fmt.persianDigits(s)
    }

    /** A heading 30nama put in a biography: a short line that is not a sentence. */
    fun isHeading(paragraph: String): Boolean = paragraph.length < 48 && paragraph.none { it in ".،؛!؟:" }

    /**
     * The start of a long text, for showing it folded: whole paragraphs up to about `letters`,
     * and never ending on a heading.
     */
    fun opening(paragraphs: List<String>, letters: Int = 800): List<String> {
        val out = mutableListOf<String>()
        var length = 0
        for (p in paragraphs) {
            out.add(p)
            length += p.length
            if (length > letters && !isHeading(p)) break
        }
        return out
    }

    private val linkedPicture = Regex("<a[^>]*href=\"([^\"]+\\.(?:jpe?g|png|webp|gif))\"[^>]*>\\s*<img[^>]*>\\s*</a>", RegexOption.IGNORE_CASE)
    private val picture = Regex("<img[^>]*\\ssrc=\"([^\"]+)\"[^>]*>", RegexOption.IGNORE_CASE)
    private val media = Regex("<video[\\s\\S]*?</video>|<iframe[\\s\\S]*?</iframe>", RegexOption.IGNORE_CASE)
    private val headingOpen = Regex("<h[1-6][^>]*>", RegexOption.IGNORE_CASE)
    private val headingClose = Regex("</h[1-6]>", RegexOption.IGNORE_CASE)

    /**
     * An article's HTML as what the page is built of: headings, paragraphs and pictures, in
     * order. A picture that links to a larger one is the larger one. Videos and the rest of the
     * editor's markup are left out; links are kept as their words.
     */
    fun blocks(html: String): List<ArticleBlock> {
        val pictureMark = "\u0001"
        val headingMark = "\u0002"
        var s = html
        s = s.replace(linkedPicture) { "\n$pictureMark${it.groupValues[1]}\n" }
        s = s.replace(picture) { "\n$pictureMark${it.groupValues[1]}\n" }
        s = s.replace(media, "\n")
        s = s.replace(headingOpen, "\n$headingMark")
        s = s.replace(headingClose, "\n")
        return paragraphs(s).mapNotNull { line ->
            when {
                line.startsWith(pictureMark) -> urlOf(line.drop(1).trimWhitespace())?.let { ArticleBlock.Picture(it) }
                line.startsWith(headingMark) -> tidyPersian(line.drop(1).trimWhitespace()).takeIf { it.isNotEmpty() }?.let { ArticleBlock.Heading(it) }
                else -> ArticleBlock.Paragraph(tidyPersian(line))
            }
        }
    }

    private val breaks = listOf("</p>", "<br>", "<br/>", "<br />").map { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) }
    private val tag = Regex("<[^>]+>")
    private val runOfSpaces = Regex("[ \t\u00A0]+")

    fun paragraphs(html: String): List<String> {
        var s = html
        for (b in breaks) s = s.replace(b, "\n")
        s = s.replace(tag, "")
        s = decodeEntities(s)
        return s.split('\n', '\r', '\u000B', '\u000C', '\u0085', '\u2028', '\u2029')
            .map { it.replace(runOfSpaces, " ").trimWhitespace() }
            .filter { it.isNotEmpty() }
    }

    private val numbered = Regex("&#x?[0-9A-Fa-f]+;")

    fun decodeEntities(s: String): String {
        var out = s
        // `&amp;` last, so that "&amp;quot;" is left as the text "&quot;" and not a quote mark.
        for ((k, v) in listOf("&nbsp;" to " ", "&quot;" to "\"", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">", "&zwnj;" to "\u200C", "&amp;" to "&")) {
            out = out.replace(k, v)
        }
        // Numbered ones: &#8217; and &#x2019;
        while (true) {
            val m = numbered.find(out) ?: break
            val body = m.value.substring(2, m.value.length - 1)
            val value = if (body.startsWith("x") || body.startsWith("X")) body.drop(1).toLongOrNull(16) else body.asciiInt()?.toLong()
            val char = value?.takeIf { it in 0..0x10FFFF && it !in 0xD800..0xDFFF }?.let { String(Character.toChars(it.toInt())) } ?: ""
            out = out.replaceRange(m.range, char)
        }
        return out
    }
}

/** A post as the site API lists it, put in the app API's shape so it is read by the one parser. */
fun Post.Companion.fromSite(j: Json): Post? {
    if (j["id"].int == null) return null
    val title = j["title"].text ?: ""
    // The site has no year of its own on a list entry: it is the one at the end of the title.
    val year = siteYear.find(title)?.let { Json.Str(it.value) } ?: Json.Null
    val imdb = j["imdb_id"].text?.let { Json.Str("https://www.imdb.com/title/$it/") } ?: Json.Null
    fun o(vararg pairs: Pair<String, Json>) = Json.Obj(linkedMapOf(*pairs))
    val shape = o(
        "id" to j["id"],
        "options" to o(
            "title_type" to j["title_type"],
            "is_series" to j["is_series"],
            "stream" to j["stream"],
            "coming_soon" to j["coming_soon"],
            "free_stream" to j["free_stream"],
            "persian_subtitle" to j["persian_subtitle"],
        ),
        "title" to o("english" to j["title"], "local" to j["persian_title"]),
        "plot" to o("english" to j["english_plot"], "local" to j["persian_plot"]),
        "info" to o("year" to year, "budget" to j["budget"]),
        "genre" to j["genre"],
        "image" to o(
            "poster" to SiteQuery.posterShape(j["image"]["poster"]),
            "cover" to o("webp" to j["image"]["cover_webp"], "jpg" to j["image"]["cover"]),
        ),
        "score" to o(
            "imdb" to o("score" to j["imdb_score"], "votes" to j["imdb_votes"], "link" to imdb, "top_250" to j["imdb_250"]),
            "30nama" to o("score" to j["30nama_score"]),
            "myanimelist" to o("score" to j["myanimelist_score"]),
            "mydramalist" to o("score" to j["mydramalist_score"]),
        ),
    )
    return Post.from(shape)
}

private val siteYear = unicodeRegex("(?<=\\s)\\d{4}$")

/** A page of the site API's lists: `{page, pages, posts}`, with no total. A title the site lists twice is kept once. */
fun PostPage.Companion.fromSite(j: Json): PostPage {
    val posts = j["posts"].array.mapNotNull { Post.fromSite(it) }.distinctBy { it.id }
    return PostPage(posts, j["page"].int ?: 1, j["pages"].int ?: 1, posts.size)
}

/**
 * The site's lists of the best: IMDb's 250 films and series, and the titles 30nama's own users
 * rate highest. Each comes whole, in order, with its place.
 */
enum class TopList(val rawValue: String) {
    imdbMovies("top-movie-imdb"), imdbSeries("top-series-imdb"),
    siteMovies("top-movie-30nama"), siteSeries("top-series-30nama");

    data class Entry(val rank: Int, val post: Post) {
        val id: Int get() = post.id
    }

    companion object {
        fun entries(j: Json): List<Entry> = j["posts"].array.mapIndexedNotNull { index, item ->
            val post = Post.fromSite(item) ?: return@mapIndexedNotNull null
            // The place comes in Persian digits ("۱۲"); the list is in order anyway.
            Entry(SiteText.latinDigits(item["rank"].text ?: "").asciiInt() ?: (index + 1), post)
        }.distinctBy { it.id } // A title listed twice keeps its first, higher place.
    }
}

/**
 * The week of a series or anime: on which day each one has a new episode. The site numbers the
 * days as the Persian week does, Saturday 0 to Friday 6.
 */
data class Schedule(val entries: List<Entry>) {
    @Suppress("EnumEntryName")
    enum class Kind { series, anime }

    data class Entry(val post: Post, val day: Int) {
        val id: Int get() = post.id
    }

    fun on(day: Int): List<Post> = entries.filter { it.day == day }.map { it.post }

    companion object {
        fun fromSite(j: Json): Schedule {
            val seen = HashSet<Int>()
            return Schedule(j["posts"].array.mapNotNull { item ->
                val post = Post.fromSite(item["post"]) ?: return@mapNotNull null
                val day = item["day"].int?.takeIf { it in 0..6 } ?: return@mapNotNull null
                if (!seen.add(post.id)) null else Entry(post, day)
            })
        }

        /** The site's number for a day of the week: Saturday is 0. */
        fun day(of: DayOfWeek): Int = (of.value % 7 + 1) % 7

        /** The site's number for the day `date` falls on in `zone`. */
        fun day(date: Instant, zone: ZoneId = ZoneId.systemDefault()): Int = day(date.atZone(zone).dayOfWeek)

        /** The week from today on, as the site numbers its days. */
        fun week(from: Instant, zone: ZoneId = ZoneId.systemDefault()): List<Int> {
            val today = day(from, zone)
            return (0 until 7).map { (today + it) % 7 }
        }

        /** Where a day of the site's falls in a list of weekday names that starts on Sunday. */
        fun symbolIndex(day: Int): Int = (day + 6) % 7
    }
}

/**
 * The name a member gave the site, which the app API does not send: only the username, which
 * cannot be changed and is often not a name at all.
 */
data class SiteProfile(val firstName: String?, val lastName: String?) {
    /** First and last name together, or nil when neither is filled in. */
    val fullName: String? get() = listOfNotNull(firstName, lastName).takeIf { it.isNotEmpty() }?.joinToString(" ")

    companion object {
        fun fromSite(j: Json): SiteProfile = SiteProfile(j["first_name"].text, j["last_name"].text)
    }
}

/** A piece of an article's page. */
sealed class ArticleBlock {
    data class Heading(val text: String) : ArticleBlock()
    data class Paragraph(val text: String) : ArticleBlock()
    data class Picture(val url: URI) : ArticleBlock()
}

/**
 * An article as the site lists it: news, a review, or one of 30nama's own announcements. Its
 * text is fetched apart, when it is opened.
 */
data class Article(
    val id: Int,
    val kind: Kind,
    val title: String,
    /** The first lines of the text, as the list gives them. */
    val summary: String?,
    /** As the site writes it, in Persian ("۲۹ مهر ۱۴۰۵"). */
    val date: String?,
    val picture: URI?,
    val thumbnail: URI?,
) {
    @Suppress("EnumEntryName")
    enum class Kind { news, review, announcement }

    companion object {
        fun fromSite(j: Json): Article? {
            val id = j["id"].int ?: return null
            val title = j["title"].text ?: return null
            // The JPEG first: the WebP the site lists is missing from its server for most articles.
            val picture = j["image"]["full"].url ?: j["poster"]["large"].url ?: j["image"]["full_webp"].url
            return Article(
                id = id,
                kind = Kind.entries.firstOrNull { it.name == j["article_type"].text } ?: Kind.news,
                title = SiteText.decodeEntities(title),
                summary = j["description"].text?.let { SiteText.tidyPersian(SiteText.paragraphs(it).joinToString(" ")) }?.takeIf { it.isNotEmpty() },
                date = j["date"].text,
                picture = picture,
                thumbnail = j["image"]["thumb"].url ?: picture,
            )
        }
    }
}

data class ArticlePage(val articles: List<Article>, val page: Int, val pages: Int) {
    companion object {
        fun fromSite(j: Json): ArticlePage =
            ArticlePage(j["posts"].array.mapNotNull { Article.fromSite(it) }.distinctBy { it.id }, j["page"].int ?: 1, j["pages"].int ?: 1)
    }
}

/** An article whole: what the list had, and its text in blocks. */
data class ArticleText(val article: Article, val blocks: List<ArticleBlock>) {
    companion object {
        fun fromSite(j: Json): ArticleText? {
            val article = Article.fromSite(j) ?: return null
            var blocks = SiteText.blocks(j["content"].text ?: "")
            // The article's own picture is shown above it; the text often opens with it again.
            val first = blocks.firstOrNull()
            val own = article.picture?.lastPathComponent?.withoutExtension()?.withoutExtension()
            if (first is ArticleBlock.Picture && own != null && first.url.lastPathComponent.withoutExtension().startsWith(own)) {
                blocks = blocks.drop(1)
            }
            return ArticleText(article, blocks)
        }
    }
}
