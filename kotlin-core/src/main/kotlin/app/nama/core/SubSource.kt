package app.nama.core

import java.net.URI

/**
 * Finding a Persian subtitle on SubSource for a title 30nama has none for.
 *
 * Two ways in. With the user's own SubSource key, its documented API. Without one, the requests
 * the site's own pages make, which need no key but may change when the site does. The app keeps
 * no key of its own: anything shipped inside it could be taken out of it.
 */
object SubSource {
    const val SITE = "https://subsource.net"
    const val API = "https://api.subsource.net/api/v1"
    const val PERSIAN = "farsi_persian"

    /** What is being looked for. */
    data class Query(val title: String, val year: Int?, val imdbID: String?, val isSeries: Boolean, val season: Int?)

    /** A subtitle that may fit: how to fetch it, and the releases it was timed for. */
    data class Candidate(val source: Source, val releases: List<String>) {
        sealed class Source {
            data class Page(val url: URI) : Source()
            data class Api(val id: Int) : Source()
        }

        val id: String
            get() = when (source) {
                is Source.Page -> source.url.toString()
                is Source.Api -> "api:${source.id}"
            }

        /** The release it is named after, for a menu. */
        val name: String get() = releases.firstOrNull() ?: "SubSource"
    }

    // MARK: Without a key: the site's own requests

    /** The body of the site's search request. */
    fun searchBody(query: Query): ByteArray =
        Json.of(mapOf("query" to query.title, "includeSeasons" to query.isSeries, "limit" to 15)).data()

    /**
     * The listing of Persian subtitles for the result that is this title: the same kind (film or
     * series), the same year when both say one, and the name closest to it.
     */
    fun listing(answer: Json, query: Query): URI? {
        val results = answer["results"].array.filter { (it["type"].text ?: "").contains("series") == query.isSeries }
        val wanted = simple(query.title)
        val scored = results.mapNotNull { r ->
            var score = 0
            val name = simple(r["title"].text ?: "")
            if (name == wanted) score += 10 else if (name.contains(wanted) || wanted.contains(name)) score += 3 else return@mapNotNull null
            val year = query.year
            val theirs = r["releaseYear"].int
            if (year != null && theirs != null) {
                if (theirs == year) score += 10 else if (Math.abs(theirs - year) > 1) return@mapNotNull null
            }
            r to score
        }
        val best = scored.maxByOrNull { it.second }?.first ?: return null
        val base = if (query.isSeries) {
            val season = query.season ?: 1
            best["seasons"].array.firstOrNull { it["season"].int == season }?.get("link")?.text
        } else {
            best["link"].text
        } ?: return null
        return runCatching { URI(SITE + "/").resolve(current(base) + "/" + PERSIAN) }.getOrNull()
    }

    /** A listing address in the form the site uses now: "season-2", where it once had "season=2". */
    fun current(path: String): String = path.replace("season=", "season-")

    /** The same, for a whole address, such as one 30nama links to. */
    fun current(url: URI): URI = urlOf(current(url.toString())) ?: url

    /** Simplified for comparing names: lower case, letters and digits only. */
    fun simple(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    // MARK: With the user's key: the documented API

    fun searchRequest(query: Query, key: String): HttpRequest {
        val items = mutableListOf<Pair<String, String>>()
        if (query.imdbID != null) {
            items += "searchType" to "imdb"
            items += "imdb" to query.imdbID
        } else {
            items += "searchType" to "text"
            items += "q" to query.title
            items += "type" to if (query.isSeries) "series" else "movie"
            query.year?.let { items += "year" to it.toString() }
        }
        if (query.isSeries && query.season != null) items += "season" to query.season.toString()
        return keyed("$API/movies/search?" + queryString(items), key)
    }

    /** The id of the title the search found: for a series, the entry for the season wanted. */
    fun movieID(answer: Json, query: Query): Int? {
        val found = answer["data"].array
        if (query.isSeries && query.season != null) {
            found.firstOrNull { it["season"].int == query.season }?.let { return it["movieId"].int }
        }
        return found.firstOrNull()?.get("movieId")?.int
    }

    fun subtitlesRequest(movieID: Int, key: String): HttpRequest =
        keyed("$API/subtitles?" + queryString(listOf("movieId" to movieID.toString(), "language" to PERSIAN, "sort" to "rating")), key)

    fun candidatesInAPI(answer: Json): List<Candidate> = answer["data"].array.mapNotNull { s ->
        val id = s["subtitleId"].int ?: return@mapNotNull null
        Candidate(Candidate.Source.Api(id), s["releaseInfo"].array.mapNotNull { it.text })
    }

    fun downloadRequest(id: Int, key: String): HttpRequest = keyed("$API/subtitles/$id/download", key)

    private fun keyed(url: String, key: String) = HttpRequest(url, headers = mapOf("X-API-Key" to key), timeoutSeconds = 20.0)

    // MARK: Choosing

    /**
     * The candidates in the order they are likely to be in time with what is playing: for an
     * episode, only those for it or for its whole season; then those that share the copy's marks.
     */
    fun ranked(candidates: List<Candidate>, release: String, season: Int?, episode: Int?): List<Candidate> {
        val left = candidates.map { SubtitleSite.Entry(it.id, it.releases) }.toMutableList()
        val order = mutableListOf<SubtitleSite.Entry>()
        while (true) {
            val next = SubtitleSite.best(release, season, episode, left) ?: break
            // `best` passes over subtitles for other episodes only when something better is there.
            if (season != null && episode != null && !fits(next.releases, season, episode)) break
            order.add(next)
            left.removeAll { it == next }
        }
        return order.mapNotNull { entry -> candidates.firstOrNull { it.id == entry.path } }
    }

    private val episodeTag = Regex("^s\\d{1,2}e\\d{1,3}$")

    private fun fits(releases: List<String>, season: Int, episode: Int): Boolean {
        val tag = format("s%02de%02d", season, episode)
        val anyEpisode = releases.any { r -> SubtitleSite.words(r).any { episodeTag.matches(it) } }
        return !anyEpisode || releases.any { tag in SubtitleSite.words(it) }
    }

    /** The subtitle text in what was downloaded: an archive, or the file itself. */
    fun text(data: ByteArray, season: Int?, episode: Int?): String? {
        val isZip = data.size >= 2 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte()
        if (!isZip) return SubtitleSite.text(data)?.takeIf { it.contains("-->") }
        val files = Zip.files(data)
        val name = SubtitleSite.pick(files.map { it.name }, season, episode) ?: return null
        val file = files.firstOrNull { it.name == name } ?: return null
        return SubtitleSite.text(file.data)?.takeIf { it.contains("-->") }
    }
}
