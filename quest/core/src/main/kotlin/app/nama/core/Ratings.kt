package app.nama.core

import java.net.URI
import java.time.Instant

private fun Instant.seconds(): Double = toEpochMilli() / 1000.0
private fun instantOf(seconds: Double): Instant = Instant.ofEpochMilli(Math.round(seconds * 1000))

/** One title the user has rated, with just enough of the title kept to show it again without the network. */
data class Rating(
    val postID: Int,
    /** Half-star steps from 0.5 to 5. */
    val stars: Double,
    val date: Instant,
    val titleEnglish: String? = null,
    val titleLocal: String? = null,
    val year: Int? = null,
    val isSeries: Boolean = false,
    val isAnime: Boolean = false,
    val genres: List<Genre> = emptyList(),
    val posterLarge: URI? = null,
    val posterBig: URI? = null,
    val blurhash: String? = null,
) {
    val id: Int get() = postID

    /** The title as a `Post`, so it can be drawn by the same cards and opened like any other. */
    val post: Post?
        get() {
            val poster = LinkedHashMap<String, Any>()
            blurhash?.let { poster["preview"] = it }
            val webp = LinkedHashMap<String, Any>()
            posterLarge?.let { webp["large"] = it.toString() }
            posterBig?.let { webp["big"] = it.toString() }
            poster["webp"] = webp
            val title = LinkedHashMap<String, Any>()
            titleEnglish?.let { title["english"] = it }
            titleLocal?.let { title["local"] = it }
            val info = LinkedHashMap<String, Any>()
            year?.let { info["year"] = it }
            val options = LinkedHashMap<String, Any>()
            options["is_series"] = isSeries
            if (isAnime) options["title_type"] = "anime"
            return Post.from(Json.of(mapOf(
                "id" to postID, "title" to title, "info" to info, "options" to options,
                "genre" to genres.map { mapOf("name" to it.name, "slug" to it.slug) },
                "image" to mapOf("poster" to poster),
            )))
        }

    /** As the Swift app stores it (`Codable`, dates in seconds since 1970), so a book reads the same on every side. */
    fun json(): Json {
        val fields = LinkedHashMap<String, Any?>()
        fields["postID"] = postID
        fields["stars"] = stars
        fields["date"] = date.seconds()
        titleEnglish?.let { fields["titleEnglish"] = it }
        titleLocal?.let { fields["titleLocal"] = it }
        year?.let { fields["year"] = it }
        fields["isSeries"] = isSeries
        fields["isAnime"] = isAnime
        fields["genres"] = genres.map { mapOf("name" to it.name, "slug" to it.slug) }
        posterLarge?.let { fields["posterLarge"] = it.toString() }
        posterBig?.let { fields["posterBig"] = it.toString() }
        blurhash?.let { fields["blurhash"] = it }
        return Json.of(fields)
    }

    companion object {
        operator fun invoke(post: Post, stars: Double, date: Instant = Instant.now()): Rating = Rating(
            postID = post.id,
            stars = snap(stars),
            date = date,
            titleEnglish = post.titleEnglish,
            titleLocal = post.titleLocal,
            year = post.year,
            isSeries = post.isSeries,
            isAnime = post.kind == Post.Kind.anime,
            genres = post.genres,
            posterLarge = post.poster.large,
            posterBig = post.poster.big,
            blurhash = post.poster.blurhash,
        )

        /** Nearest half star within 0.5...5. */
        fun snap(stars: Double): Double = (Math.round(stars * 2) / 2.0).coerceIn(0.5, 5.0)

        /** Nil when a field every rating has is missing, as Swift's decoder refuses it. */
        fun from(j: Json): Rating? {
            val postID = j["postID"].int ?: return null
            val stars = (j["stars"] as? Json.Num)?.value ?: return null
            val date = (j["date"] as? Json.Num)?.value ?: return null
            if (j["isSeries"] !is Json.Bool || j["isAnime"] !is Json.Bool || j["genres"] !is Json.Arr) return null
            return Rating(
                postID = postID, stars = stars, date = instantOf(date),
                titleEnglish = j["titleEnglish"].string, titleLocal = j["titleLocal"].string, year = j["year"].int,
                isSeries = j["isSeries"].bool, isAnime = j["isAnime"].bool,
                genres = j["genres"].array.mapNotNull { g -> g["name"].string?.let { Genre(it, g["slug"].string ?: it) } },
                posterLarge = j["posterLarge"].url, posterBig = j["posterBig"].url, blurhash = j["blurhash"].string,
            )
        }
    }
}

/** A genre the user likes, and how strongly next to the one they like best (0...1). */
data class GenreStrength(val name: String, val strength: Double)

/** Everything the user has rated, and what that says about their taste. Not thread-safe. */
class RatingBook(ratings: List<Rating> = emptyList()) {
    private val _ratings = LinkedHashMap<Int, Rating>()
    private val _removed = LinkedHashMap<Int, Instant>()

    val ratings: Map<Int, Rating> get() = _ratings

    /**
     * Titles whose rating was taken away, and when. Kept so that another device, which still has
     * the rating, learns that it is gone instead of bringing it back.
     */
    val removed: Map<Int, Instant> get() = _removed

    init {
        for (r in ratings) _ratings[r.postID] = r
    }

    fun encoded(): ByteArray = Json.of(mapOf(
        "ratings" to newestFirst.map { it.json() },
        "removed" to _removed.entries.associate { it.key.toString() to it.value.seconds() },
    )).serialize(sortedKeys = true).toByteArray(Charsets.UTF_8)

    val count: Int get() = _ratings.size
    val isEmpty: Boolean get() = _ratings.isEmpty()
    val newestFirst: List<Rating> get() = _ratings.values.sortedWith(compareByDescending<Rating> { it.date }.thenByDescending { it.postID })
    val average: Double? get() = if (isEmpty) null else _ratings.values.sumOf { it.stars } / count

    fun stars(postID: Int): Double? = _ratings[postID]?.stars

    /** Sets the rating for a title, or removes it when `stars` is nil. */
    fun rate(post: Post, stars: Double?, date: Instant = Instant.now()) {
        if (stars != null) {
            _ratings[post.id] = Rating(post, stars, date)
            _removed.remove(post.id)
        } else if (_ratings.remove(post.id) != null) {
            _removed[post.id] = date
        }
    }

    /**
     * This book and another device's, brought together: for every title, whatever happened last
     * stands, be it a rating or its removal. Either book can be the receiver; the result is the same.
     */
    fun merged(other: RatingBook): RatingBook {
        val out = RatingBook()
        for (id in _ratings.keys + other._ratings.keys + _removed.keys + other._removed.keys) {
            val rating = listOfNotNull(_ratings[id], other._ratings[id]).maxWithOrNull(compareBy<Rating> { it.date }.thenBy { it.stars })
            val removal = listOfNotNull(_removed[id], other._removed[id]).maxOrNull()
            if (rating != null && rating.date >= (removal ?: Instant.MIN)) out._ratings[id] = rating
            else if (removal != null) out._removed[id] = removal
        }
        return out
    }

    // MARK: Taste

    /**
     * How much the user likes each genre: the average of (stars - neutral) over the titles rated
     * in it, shrunk toward zero when there are only a few, so one five-star western does not
     * outweigh ten four-star dramas.
     */
    fun taste(): Map<String, Double> {
        val sum = HashMap<String, Double>()
        val n = HashMap<String, Double>()
        for (r in _ratings.values) {
            for (g in r.genres) {
                sum[g.slug] = (sum[g.slug] ?: 0.0) + r.stars - NEUTRAL
                n[g.slug] = (n[g.slug] ?: 0.0) + 1
            }
        }
        return sum.mapValues { (k, v) -> v / ((n[k] ?: 1.0) + 2) }
    }

    /** Genres the user rates highest, best first. */
    fun favoriteGenres(limit: Int = 3): List<String> = taste().filter { it.value > 0 }.entries
        .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenByDescending { it.key })
        .take(limit).map { it.key }

    /** The genres the user rates highest, by name, with how strongly each is liked next to the strongest. */
    fun favoriteGenreNames(limit: Int = 4): List<GenreStrength> {
        val taste = taste()
        val names = HashMap<String, String>()
        for (r in newestFirst.reversed()) for (g in r.genres) names[g.slug] = g.name
        val top = favoriteGenres(limit)
        val best = top.firstOrNull()?.let { taste[it] } ?: return emptyList()
        if (best <= 0) return emptyList()
        return top.map { GenreStrength(names[it] ?: it, (taste[it] ?: 0.0) / best) }
    }

    /** How many titles got one star, two, and so on up to five. Half stars count with the star above. */
    fun spread(): List<Int> {
        val counts = IntArray(5)
        for (r in _ratings.values) counts[(Math.ceil(r.stars).toInt() - 1).coerceIn(0, 4)]++
        return counts.toList()
    }

    /** How well a title fits the user's taste. Positive means "probably yes". */
    fun affinity(post: Post, taste: Map<String, Double>): Double {
        val known = post.genres.mapNotNull { taste[it.slug] }
        if (known.isEmpty()) return 0.0
        val fit = known.sum() / known.size
        // Between two titles that fit equally, prefer the better-regarded one.
        val acclaim = ((post.imdbScore ?: 6.5) - 6.5) * 0.08
        return fit + acclaim
    }

    /** Titles from `candidates` the user has not rated, best fit first, leaving out poor fits. */
    fun recommend(candidates: List<Post>, limit: Int = 20): List<Post> {
        val taste = taste()
        if (taste.isEmpty()) return emptyList()
        val seen = HashSet(_ratings.keys)
        return candidates
            .filter { seen.add(it.id) && !it.comingSoon }
            .map { it to affinity(it, taste) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Post, Double>> { it.second }.thenByDescending { it.first.id })
            .take(limit)
            .map { it.first }
    }

    /**
     * The ratings as a CSV that Letterboxd's importer reads: title, year and stars, newest first.
     * The app cannot send ratings to 30nama, so this is how they leave it.
     */
    fun csv(): String {
        fun field(text: String): String =
            if (text.none { it in ",\"\n" }) text else "\"" + text.replace("\"", "\"\"") + "\""
        val lines = mutableListOf("Title,Year,Rating")
        for (rating in newestFirst) {
            val title = rating.post?.cleanEnglishTitle ?: rating.titleEnglish ?: rating.titleLocal ?: continue
            lines.add(listOf(field(title), rating.year?.toString() ?: "", rating.stars.toString()).joinToString(","))
        }
        return lines.joinToString("\n") + "\n"
    }

    override fun equals(other: Any?): Boolean = other is RatingBook && other._ratings == _ratings && other._removed == _removed
    override fun hashCode(): Int = _ratings.hashCode() * 31 + _removed.hashCode()

    companion object {
        /** A rating above this counts for a genre, below it against. */
        const val NEUTRAL = 3.0

        /** Reads a stored book. Books saved before removals were recorded are a bare list of ratings. */
        fun from(data: ByteArray): RatingBook? {
            val json = try {
                Json.parse(data)
            } catch (_: Json.ParseException) {
                return null
            }
            if (json is Json.Obj && json["ratings"] is Json.Arr) {
                val ratings = json["ratings"].array.map { Rating.from(it) ?: return null }
                val book = RatingBook(ratings)
                for ((id, date) in json["removed"].obj ?: emptyMap()) {
                    val key = id.asciiInt() ?: continue
                    val seconds = date.double ?: continue
                    book._removed[key] = instantOf(seconds)
                }
                return book
            }
            if (json is Json.Arr) return RatingBook(json.items.map { Rating.from(it) ?: return null })
            return null
        }
    }
}
