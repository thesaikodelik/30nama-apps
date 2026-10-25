package app.nama.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset

// TMDB's trending lists, turned into titles 30nama carries. With the user's own TMDB key the
// documented API is asked; without one, the week's trending list is read from TMDB's public page,
// and the rows that need the API stay empty. Ported from the Tizen client's tmdb.js.

/** A title as TMDB lists it, cut down to what matching needs. */
data class TMDBItem(
    val id: Int,
    val kind: Kind,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val hasPoster: Boolean,
    val popularity: Double,
    val voteCount: Int,
) {
    @Suppress("EnumEntryName")
    enum class Kind { movie, tv }

    val key: String get() = kind.name + id

    companion object {
        /** The fields of a list entry worth caching. */
        val keptKeys = setOf(
            "id", "media_type", "title", "name", "original_title", "original_name",
            "release_date", "first_air_date", "poster_path", "popularity", "vote_count",
        )

        /** `defaultKind` is for lists that hold one kind and so do not say which. */
        fun from(j: Json, defaultKind: Kind? = null): TMDBItem? {
            val id = j["id"].int ?: return null
            if (j["media_type"].text == "person") return null
            val kind = when (j["media_type"].text) {
                "tv" -> Kind.tv
                "movie" -> Kind.movie
                else -> defaultKind ?: if (j["first_air_date"].isNull) Kind.movie else Kind.tv
            }
            return TMDBItem(
                id = id,
                kind = kind,
                title = j["title"].text ?: j["name"].text ?: "",
                originalTitle = j["original_title"].text ?: j["original_name"].text,
                year = (j["release_date"].text ?: j["first_air_date"].text)?.take(4)?.asciiInt(),
                hasPoster = j["poster_path"].text != null,
                popularity = j["popularity"].double ?: 0.0,
                voteCount = j["vote_count"].int ?: 0,
            )
        }

        fun list(results: Json, defaultKind: Kind? = null): List<TMDBItem> = results.array.mapNotNull { from(it, defaultKind) }

        /** TMDB's public "trending this week" panel, written as the list entries the API would give, so the rest of the way is the same. */
        const val PUBLIC_TRENDING = "https://www.themoviedb.org/remote/panel?panel=trending_scroller&group=this-week&language=en-US"

        private val panelEntry = Regex(
            "href=\"/(movie|tv)/(\\d+)[^\"]*\"><h2[^>]*><span>([^<]+)</span></h2></a></div><span class=\"release_date[^\"]*\">([^<]*)</span>",
        )
        private val fourDigits = Regex("\\d{4}")

        fun publicList(html: String): Json {
            val seen = HashSet<String>()
            val entries = panelEntry.findAll(html).mapNotNull { m ->
                val kind = m.groupValues[1]
                val id = m.groupValues[2].asciiInt() ?: return@mapNotNull null
                if (!seen.add(kind + id)) return@mapNotNull null
                val entry = linkedMapOf<String, Json>(
                    "id" to Json.Num(id.toDouble()),
                    "media_type" to Json.Str(kind),
                    (if (kind == "tv") "name" else "title") to Json.Str(unescape(m.groupValues[3])),
                    "poster_path" to Json.Str("/public"),
                )
                fourDigits.find(m.groupValues[4])?.let {
                    entry[if (kind == "tv") "first_air_date" else "release_date"] = Json.Str(it.value + "-01-01")
                }
                Json.Obj(entry)
            }.toList()
            return Json.Arr(entries)
        }

        private fun unescape(s: String): String =
            s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&#x27;", "'")
    }
}

/** The rows TMDB adds to Home. */
@Suppress("EnumEntryName")
enum class TrendingRow {
    /** Trending this week, movies and series together. */
    trend,
    /** New and returning series. */
    fresh,
    /** Movies in cinemas and trending today. */
    pop;

    data class Source(val path: String, val params: Map<String, String>, val kind: TMDBItem.Kind?)

    val sectionKey: String get() = "tm_$name"

    /** Most titles a row shows. */
    val cap: Int get() = if (this == trend) 16 else 14

    fun sources(now: Instant = Instant.now()): List<Source> = when (this) {
        trend -> listOf(Source("/trending/all/week", mapOf("language" to "en-US"), null))
        fresh -> {
            val since = now.minusSeconds(30 * 86400).atOffset(ZoneOffset.UTC).toLocalDate().toString()
            (1..3).map { page ->
                Source("/discover/tv", mapOf(
                    "language" to "en-US", "air_date.gte" to since, "sort_by" to "popularity.desc", "vote_count.gte" to "200",
                    // No talk shows, news, reality or soaps.
                    "without_genres" to "10767,10763,10764,10766", "page" to page.toString(),
                ), TMDBItem.Kind.tv)
            }
        }
        pop -> listOf(
            Source("/movie/now_playing", mapOf("language" to "en-US", "region" to "US"), TMDBItem.Kind.movie),
            Source("/trending/movie/day", mapOf("language" to "en-US"), TMDBItem.Kind.movie),
        )
    }

    /** The titles to look for on 30nama, in the order the row should show them. */
    fun candidates(lists: List<List<TMDBItem>>): List<TMDBItem> {
        val seen = HashSet<String>()
        var out = lists.flatten().filter { it.title.isNotEmpty() && it.hasPoster && seen.add(it.key) }
        if (this == fresh) {
            // Best-known series first, obscure ones dropped.
            fun weight(item: TMDBItem) = item.popularity + Math.sqrt(item.voteCount.toDouble()) / 5
            out = out.filter { it.voteCount >= 200 }.sortedByDescending { weight(it) }
        }
        return out
    }

    companion object {
        /** A row with fewer titles than this is not worth showing. */
        const val MINIMUM = 5
    }
}

/** Decides whether a 30nama title and a TMDB title are the same one. */
object TitleMatch {
    private val marks = Regex("[̀-ͯ]")
    private val notWord = Regex("[^a-z0-9؀-ۿ]+")
    private val trailingYear = Regex("(?U)\\s*[\\(\\[]?\\b(19|20)\\d\\d[\\)\\]]?\\s*$")
    private val latin = Regex("[a-zA-Z]")

    /** Lowercase, without accents or punctuation: "Amélie & Co." -> "amelie and co". */
    fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")
        return decomposed.replace("&", " and ").replace(notWord, " ").trimWhitespace()
    }

    /** 30nama appends the release year to its titles ("Animals 2026"). */
    fun stripYear(s: String): String {
        val stripped = s.replace(trailingYear, "").trimWhitespace()
        return stripped.ifEmpty { s }
    }

    /** 0...1: how many words two titles share. A title that only adds a subtitle to the other still counts as close. */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val wordsA = x.split(' ').filter { it.isNotEmpty() }
        val wordsB = y.split(' ').filter { it.isNotEmpty() }
        val inB = wordsB.toSet()
        val shared = wordsA.count { it in inB }
        val dice = 2.0 * shared / (wordsA.size + wordsB.size)
        return if (x.startsWith("$y ") || y.startsWith("$x ")) maxOf(dice, 0.82) else dice
    }

    /**
     * What to search 30nama for: the title, then the original one when it differs and is in Latin
     * script, then the title with its year, the way 30nama itself writes it. That last one finds
     * titles with a common name ("Animals") that the plain search buries under their namesakes.
     */
    fun queries(item: TMDBItem): List<String> {
        val out = mutableListOf(item.title)
        val original = item.originalTitle
        if (original != null && normalize(original) != normalize(item.title) && latin.containsMatchIn(original)) out.add(original)
        item.year?.let { out.add("${item.title} $it") }
        return out
    }

    /**
     * The search result that is this TMDB title: same kind, a close enough name, the same year
     * give or take one. Titles that can be streamed win over ones that cannot; upcoming ones are skipped.
     */
    fun bestPost(posts: List<Post>, item: TMDBItem): Post? {
        var best: Pair<Post, Double>? = null
        for (post in posts) {
            if (post.isSeries != (item.kind == TMDBItem.Kind.tv) || post.comingSoon) continue
            val raw = post.titleEnglish ?: ""
            val name = listOfNotNull(item.title, item.originalTitle).filter { it.isNotEmpty() }
                .maxOfOrNull { maxOf(similarity(stripYear(raw), it), similarity(raw, it)) } ?: 0.0
            if (name < 0.8) continue
            var score = name
            val wanted = item.year
            val year = post.year
            if (wanted != null && year != null) {
                when (Math.abs(wanted - year)) {
                    0 -> score += 0.25
                    1 -> score += 0.1
                    else -> continue
                }
            }
            if (post.hasStream) score += 0.1
            if (score > (best?.second ?: 0.0)) best = post to score
        }
        return best?.first
    }
}

/**
 * Builds the trending rows: asks TMDB what is popular, then finds each title on 30nama.
 *
 * Both halves are cached on disk, because finding one title costs a 30nama search and 30nama is
 * rate limited: TMDB's lists for six hours, a title that was found for a week, one that was not for a day.
 *
 * @param key the user's TMDB key; nil or blank reads the public page instead.
 * @param search runs a 30nama title search and returns its result (`api.get("search", …, background = true)`).
 */
class Trending(
    key: String?,
    private val cacheFile: File? = null,
    transport: Transport? = null,
    private val now: () -> Instant = { Instant.now() },
    private val search: suspend (query: String) -> Json,
) {
    private class Entry(val at: Instant, val value: Json)

    /** The user's own TMDB key; nil for the public page. */
    val key: String? = key?.trimWhitespaceAndNewlines()?.takeIf { it.isNotEmpty() }
    private val transport: Transport = transport ?: HttpURLConnectionTransport()
    private val lock = Any()
    private val cache = HashMap<String, Entry>()
    private var cacheLoaded = false
    private val running = HashMap<TrendingRow, Deferred<List<Post>>>()

    /** Rows outlive whichever screen asked first: a second asker shares the work under way. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The titles of a row, in TMDB's order. Empty when too few of them are on 30nama, or TMDB could not be reached. */
    suspend fun row(row: TrendingRow): List<Post> {
        val task = synchronized(lock) {
            running.getOrPut(row) {
                scope.async { build(row) }.also { job -> job.invokeOnCompletion { synchronized(lock) { if (running[row] === job) running.remove(row) } } }
            }
        }
        return task.await()
    }

    private suspend fun build(row: TrendingRow): List<Post> {
        loadCache()
        val lists = row.sources(now()).map { list(it) }
        val candidates = row.candidates(lists)

        // Up to four lookups at a time, and no new ones once the row is full.
        val found = mutableListOf<Pair<Int, Post>>()
        coroutineScope {
            val results = Channel<Pair<Int, Post?>>(Channel.UNLIMITED)
            var next = 0
            var active = 0
            while (true) {
                while (active < 4 && next < candidates.size && found.size < row.cap) {
                    val index = next
                    launch { results.send(index to resolve(candidates[index])) }
                    next++
                    active++
                }
                if (active == 0) break
                val (index, post) = results.receive()
                active--
                if (post != null) found.add(index to post)
            }
        }
        saveCache()

        val seen = HashSet<Int>()
        val posts = found.sortedBy { it.first }.map { it.second }.filter { seen.add(it.id) }.take(row.cap)
        return if (posts.size >= TrendingRow.MINIMUM) posts else emptyList()
    }

    private suspend fun list(source: TrendingRow.Source): List<TMDBItem> {
        val query = source.params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        val cacheKey = "l:${source.path}?$query"
        cached(cacheKey)?.let { return TMDBItem.list(it, source.kind) }
        if (key == null) {
            // Without a key only the week's trending list, from the public page.
            if (source.path != "/trending/all/week") return emptyList()
            val html = attempt { fetchPublic() } ?: return emptyList()
            val list = TMDBItem.publicList(html)
            if (list.array.isEmpty()) return emptyList()
            store(cacheKey, list)
            return TMDBItem.list(list)
        }
        val fresh = attempt { fetch(source, key) } ?: return emptyList()
        val slim = Json.Arr(fresh["results"].array.map { entry ->
            Json.Obj((entry.obj ?: emptyMap()).filterKeys { it in TMDBItem.keptKeys })
        })
        store(cacheKey, slim)
        return TMDBItem.list(slim, source.kind)
    }

    private suspend fun fetch(source: TrendingRow.Source, key: String): Json {
        val items = source.params.entries.sortedBy { it.key }.map { it.key to it.value }
        val response = transport.send(TMDB.request(source.path.drop(1), items, key))
        if (response.status != 200) throw IllegalStateException("TMDB answered ${response.status}")
        return Json.parse(response.body)
    }

    private suspend fun fetchPublic(): String {
        val request = HttpRequest(
            TMDBItem.PUBLIC_TRENDING,
            headers = mapOf("User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"),
            timeoutSeconds = 15.0,
        )
        val response = transport.send(request)
        if (response.status != 200) throw IllegalStateException("TMDB answered ${response.status}")
        return String(response.body, Charsets.UTF_8)
    }

    /** The 30nama title for a TMDB one, or nil when 30nama does not have it. */
    private suspend fun resolve(item: TMDBItem): Post? {
        val cacheKey = "p:" + item.key
        cached(cacheKey)?.let { return Post.from(it) }
        var match: Json = Json.Null
        try {
            for (query in TitleMatch.queries(item)) {
                val entries = Post.rawList(search(query)).map { if (it["post"].isNull) it else it["post"] }
                val post = TitleMatch.bestPost(entries.mapNotNull { Post.from(it) }, item) ?: continue
                val raw = entries.firstOrNull { it["id"].int == post.id } ?: continue
                match = Json.Obj((raw.obj ?: emptyMap()).filterKeys { it in keptPostKeys })
                break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The search itself failed, which says nothing about the title: ask again next time.
            return null
        }
        store(cacheKey, match)
        return Post.from(match)
    }

    // MARK: Cache

    private fun store(key: String, value: Json) {
        synchronized(lock) { cache[key] = Entry(now(), value) }
    }

    private fun cached(key: String): Json? = synchronized(lock) {
        val entry = cache[key] ?: return null
        if (seconds(entry.at, now()) < life(key, entry.value)) entry.value else null
    }

    private fun loadCache() {
        synchronized(lock) {
            if (cacheLoaded) return
            cacheLoaded = true
        }
        val file = cacheFile ?: return
        val stored = attempt { Json.parse(file.readBytes()).obj } ?: return
        synchronized(lock) {
            for ((key, entry) in stored) {
                val at = entry["t"].double ?: continue
                cache[key] = Entry(Instant.ofEpochMilli((at * 1000).toLong()), entry["v"])
            }
        }
    }

    private fun saveCache() {
        val file = cacheFile ?: return
        val time = now()
        val stored = synchronized(lock) {
            cache.entries.removeAll { seconds(it.value.at, time) >= life(it.key, it.value.value) }
            Json.Obj(cache.mapValues { (_, e) -> Json.Obj(mapOf("t" to Json.Num(e.at.toEpochMilli() / 1000.0), "v" to e.value)) })
        }
        attempt {
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeBytes(stored.data())
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    private companion object {
        /** The fields of a 30nama post that lists and the billboard draw from. */
        val keptPostKeys = setOf("id", "post_type", "options", "info", "title", "plot", "genre", "image", "score")

        fun life(key: String, value: Json): Double = when {
            key.startsWith("l:") -> 6 * 3600.0
            value.isNull -> 24 * 3600.0
            else -> 7 * 24 * 3600.0
        }

        fun seconds(from: Instant, to: Instant): Double = java.time.Duration.between(from, to).toMillis() / 1000.0
    }
}
