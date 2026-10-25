package app.nama.core

import java.net.URI

/** `name=value` pairs of a query string, in order, each escaped. */
internal fun queryString(items: List<Pair<String, String>>): String =
    items.joinToString("&") { (k, v) -> percentEncode(k) { it.isUnreserved() } + "=" + percentEncode(v) { it.isUnreserved() } }

/**
 * What TMDB adds to a title page, with a key the user made on TMDB themselves: the title's own
 * logo, its trailer, the people who made it, and reviews. The app keeps no key of its own.
 */
object TMDB {
    const val API = "https://api.themoviedb.org/3"
    const val IMAGES = "https://image.tmdb.org/t/p/"

    /** A v4 read access token is sent as a bearer token; a v3 key goes in the address. */
    fun request(path: String, query: List<Pair<String, String>> = emptyList(), key: String): HttpRequest {
        val bearer = key.startsWith("eyJ")
        val items = if (bearer) query else query + ("api_key" to key)
        val url = "$API/$path" + if (items.isEmpty()) "" else "?" + queryString(items)
        val headers = LinkedHashMap<String, String>()
        if (bearer) headers["Authorization"] = "Bearer $key"
        headers["Accept"] = "application/json"
        return HttpRequest(url, headers = headers, timeoutSeconds = 20.0)
    }

    /** Finding the title: by its IMDb id when 30nama gives one, else by name and year. */
    fun findRequest(imdbID: String, key: String): HttpRequest = request("find/$imdbID", listOf("external_source" to "imdb_id"), key)

    fun searchRequest(title: String, year: Int?, isSeries: Boolean, key: String): HttpRequest {
        val query = mutableListOf("query" to title)
        if (year != null) query.add((if (isSeries) "first_air_date_year" else "year") to year.toString())
        return request("search/${if (isSeries) "tv" else "movie"}", query, key)
    }

    fun idInFind(answer: Json, isSeries: Boolean): Int? = answer[if (isSeries) "tv_results" else "movie_results"].array.firstOrNull()?.get("id")?.int

    fun idInSearch(answer: Json): Int? = answer["results"].array.firstOrNull()?.get("id")?.int

    /** Everything a title page shows, in one request. */
    fun detailsRequest(id: Int, isSeries: Boolean, key: String): HttpRequest {
        val credits = if (isSeries) "aggregate_credits" else "credits"
        return request("${if (isSeries) "tv" else "movie"}/$id", listOf(
            "append_to_response" to "images,videos,$credits,reviews",
            "include_image_language" to "fa,en,null",
        ), key)
    }

    // MARK: Without a key: the public site

    const val SITE = "https://www.themoviedb.org"

    /** The site sends this to the title's own page. */
    fun publicPage(imdbID: String): URI = URI("https://www.themoviedb.org/redirect?external_source=imdb_id&external_id=$imdbID")

    private fun isTitlePage(page: URI): Boolean = page.path?.let { it.startsWith("/movie/") || it.startsWith("/tv/") } == true

    /** The page that lists a title's logos in one language, given the title's own page. */
    fun logosPage(page: URI, language: String): URI? =
        if (isTitlePage(page)) urlOf(page.toString().substringBefore('?') + "/images/logos?image_language=" + language) else null

    /** The page that lists a title's trailers, given the title's own page. */
    fun trailersPage(page: URI): URI? =
        if (isTitlePage(page)) urlOf(page.toString().substringBefore('?') + "/videos?active_nav_item=Trailers&language=en-US") else null

    private val trailerPattern = Regex("data-site=\"YouTube\" data-id=\"[A-Za-z0-9_-]{11}\"")
    private val logoPattern = Regex("/t/p/[a-z0-9_]+/[A-Za-z0-9]+\\.(png|svg)")

    /** The YouTube id of the first trailer the page lists. */
    fun trailerInPage(html: String): String? = trailerPattern.find(html)?.value?.dropLast(1)?.takeLast(11)

    /** The first logo the page lists, which the site puts best voted first. */
    fun logoInPage(html: String): URI? {
        val found = logoPattern.find(html)?.value ?: return null
        return urlOf(IMAGES + "w500/" + found.substringAfterLast('/').replace(".svg", ".png"))
    }

    data class Person(val name: String, val job: String, val photo: URI? = null)

    data class Review(
        val author: String,
        val text: String,
        /** Out of ten, when the reviewer gave one. */
        val rating: Double? = null,
        val link: URI? = null,
    )

    data class Info(
        /** The title written as its own artwork, on a clear ground. */
        val logo: URI? = null,
        /** The YouTube id of the official trailer. */
        val trailer: String? = null,
        val crew: List<Person> = emptyList(),
        val reviews: List<Review> = emptyList(),
    ) {
        val trailerPage: URI? get() = trailer?.let { urlOf("https://www.youtube.com/watch?v=$it") }
    }

    /** The jobs worth naming on a title page, in the order they are shown. */
    val jobs = listOf("Director", "Creator", "Screenplay", "Writer", "Novel", "Original Music Composer", "Director of Photography")

    fun info(details: Json, lang: Lang): Info {
        // A logo in the interface's language if there is one, else English, else one with no text.
        var logo: URI? = null
        val logos = details["images"]["logos"].array
        for (code in (if (lang == Lang.fa) listOf("fa", "en") else listOf("en")) + "") {
            val best = logos.filter { (it["iso_639_1"].text ?: "") == code }.maxByOrNull { it["vote_average"].double ?: 0.0 }
            val path = best?.get("file_path")?.text ?: continue
            // PNG at a size that stays sharp on a large screen; TMDB keeps SVGs as PNGs too.
            logo = urlOf(IMAGES + "w500" + path.replace(".svg", ".png"))
            break
        }

        val videos = details["videos"]["results"].array.filter { it["site"].text == "YouTube" && it["type"].text == "Trailer" }
        val trailer = (videos.firstOrNull { it["official"].bool } ?: videos.firstOrNull())?.get("key")?.text

        // Films list one entry per job; series list each person once, with all their jobs.
        val people = mutableListOf<Person>()
        for (entry in details["credits"]["crew"].array + details["aggregate_credits"]["crew"].array) {
            val name = entry["name"].text ?: continue
            val jobs = entry["job"].text?.let { listOf(it) } ?: entry["jobs"].array.mapNotNull { it["job"].text }
            val photo = entry["profile_path"].text?.let { urlOf(IMAGES + "w185" + it) }
            for (job in jobs) if (job in TMDB.jobs) people.add(Person(name, job, photo))
        }
        for (creator in details["created_by"].array) {
            val name = creator["name"].text ?: continue
            people.add(Person(name, "Creator", creator["profile_path"].text?.let { urlOf(IMAGES + "w185" + it) }))
        }
        val seen = HashSet<String>()
        val crew = people
            .sortedBy { jobs.indexOf(it.job).takeIf { i -> i >= 0 } ?: 99 }
            .filter { seen.add(it.name) }
            .take(8)

        val reviews = details["reviews"]["results"].array.take(6).mapNotNull { r ->
            val author = r["author"].text ?: return@mapNotNull null
            val text = r["content"].text?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Review(author, text, r["author_details"]["rating"].double, r["url"].url)
        }
        return Info(logo, trailer, crew, reviews)
    }
}
