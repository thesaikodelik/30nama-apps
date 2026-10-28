package app.nama.core

import java.net.URI
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** The interface languages, in the order the language setting goes through them. */
@Suppress("EnumEntryName")
enum class Lang {
    en, ar, fa;

    val isRTL: Boolean get() = this != en

    /** What the language calls itself. (`name` in Swift, which every Kotlin enum already has.) */
    val nativeName: String
        get() = when (this) {
            en -> "English"
            ar -> "العربية"
            fa -> "فارسی"
        }

    /** The language to ask 30nama for titles and plots in. It has them in Persian and English only. */
    val catalogue: String get() = if (this == fa) "fa" else "en"
}

// MARK: - Images

/** Poster variants as served by the CDN (widths 50 / 172 / 214 / 428 px, all 2:3). */
data class Poster(
    val blurhash: String? = null,
    val small: URI? = null,
    val medium: URI? = null,
    val large: URI? = null,
    val big: URI? = null,
    /** JPEG variant, for places that cannot take WebP (player artwork metadata). */
    val jpeg: URI? = null,
) {
    val isEmpty: Boolean get() = small == null && medium == null && large == null && big == null

    /** Smallest variant that is at least `pixelWidth` wide, falling back to the largest available. */
    fun url(pixelWidth: Double): URI? {
        val ladder = listOf(50.0 to small, 172.0 to medium, 214.0 to large, 428.0 to big)
        return ladder.firstOrNull { it.first >= pixelWidth && it.second != null }?.second ?: big ?: large ?: medium ?: small
    }

    companion object {
        fun from(j: Json): Poster {
            fun pick(size: String) = j["webp"][size].url ?: j["jpg"][size].url
            return Poster(
                blurhash = j["preview"].text,
                small = pick("small"), medium = pick("medium"), large = pick("large"), big = pick("big"),
                jpeg = j["jpg"]["large"].url ?: j["jpg"]["medium"].url,
            )
        }
    }
}

// MARK: - Post

data class Genre(val name: String, val slug: String)

data class Person(
    val name: String,
    val role: String? = null,
    val poster: Poster? = null,
    /** "nm…": what the site API finds a person's titles by. */
    val imdb: String? = null,
    /** 30nama's own id for the person: what the site's biography page is found by. Not every person in a cast has one. */
    val siteID: Int? = null,
) {
    val id: String get() = name + "|" + (role ?: "")
}

/** MyAnimeList ("MAL") or MyDramaList ("MDL") and the score out of ten there. */
data class FanScore(val source: String, val value: Double)

/** A day as the server writes it in both calendars. */
data class DatePair(val gregorian: String? = null, val jalali: String? = null)

/**
 * A title (movie, series or anime) as it appears in lists and on the details page.
 * Two posts are the same title when their ids are, whatever else differs between the answers they came from.
 */
data class Post(
    val id: Int,
    val kind: Kind = Kind.movie,
    val isSeries: Boolean = false,
    val hasStream: Boolean = false,
    val comingSoon: Boolean = false,
    val freeStream: Boolean = false,
    val persianSubtitle: Boolean = false,
    val titleEnglish: String? = null,
    val titleLocal: String? = null,
    val plotEnglish: String? = null,
    val plotLocal: String? = null,
    val year: Int? = null,
    val yearEnd: Int? = null,
    val seasons: Int? = null,
    val age: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<Genre> = emptyList(),
    val poster: Poster = Poster(),
    val cover: URI? = null,
    val imdbScore: Double? = null,
    val imdbID: String? = null,
    val siteScore: Double? = null,
    /** Tomatometer, 0...100. */
    val rottenTomatoes: Int? = null,
    /** Metascore, 0...100. */
    val metacritic: Int? = null,
    /** MyAnimeList (anime) or MyDramaList (Asian drama) score out of 10, whichever the title has. */
    val fanScore: FanScore? = null,
    // Shown by 30nama's own apps beside the scores: places on the top lists, votes, list counts.
    val imdbTop250: Int? = null,
    val siteTop100: Int? = null,
    val imdbVotes: Int? = null,
    /** How many of 30nama's users keep it on their watchlist, and among their favourites. */
    val inWatchlists: Int? = null,
    val inFavorites: Int? = null,
    val budget: String? = null,
    val exclusiveSubtitle: Boolean = false,
    /** What 30nama has changed on the title ("new episode", "subtitle fixed"), newest first. */
    val updates: List<TitleUpdate> = emptyList(),
    val cast: List<Person> = emptyList(),
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val networks: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val language: String? = null,
    /** Spoken besides `language`, without repeating it. */
    val otherLanguages: List<String> = emptyList(),
    val airStatus: String? = null,
    val nextEpisodeCaption: String? = null,
    val nextEpisodeDate: DatePair = DatePair(),
    /** Only present on list entries of the "continue watching" feed and on `single`. */
    val resume: ContinueWatching? = null,
    /** The pages of the subtitle site 30nama sends people to: one per language, and per season of a series. */
    val subtitlePages: List<SubtitlePage> = emptyList(),
) {
    @Suppress("EnumEntryName")
    enum class Kind { movie, series, anime }

    /** The last change, with what it was. */
    val latestUpdate: TitleUpdate? get() = updates.firstOrNull()

    override fun equals(other: Any?): Boolean = other is Post && other.id == id
    override fun hashCode(): Int = id

    // MARK: Display

    /**
     * Titles arrive with the release year appended ("Animals 2026"). The year has its own place in
     * the meta line, so drop it, but only when it really is this title's year ("Blade Runner 2049"
     * keeps its number).
     */
    private fun withoutYear(title: String?): String? {
        val t = title ?: return null
        for (y in listOfNotNull(year, yearEnd)) {
            for (suffix in listOf(" $y", " ($y)")) {
                if (t.endsWith(suffix) && t.length > suffix.length) return t.dropLast(suffix.length).trimWhitespace()
            }
        }
        return t
    }

    val cleanEnglishTitle: String? get() = withoutYear(titleEnglish)
    val cleanLocalTitle: String? get() = withoutYear(titleLocal)

    fun title(lang: Lang): String {
        if (lang == Lang.fa) cleanLocalTitle?.let { return it }
        return cleanEnglishTitle ?: cleanLocalTitle ?: ""
    }

    /** The title in the other language, when there is one and it differs. */
    fun alternateTitle(lang: Lang): String? {
        val other = (if (lang == Lang.fa) cleanEnglishTitle else cleanLocalTitle) ?: return null
        return if (other != title(lang)) other else null
    }

    fun plot(lang: Lang): String {
        if (lang == Lang.fa) plotLocal?.let { return it }
        return plotEnglish ?: plotLocal ?: ""
    }

    /**
     * The day the next episode is due, when the server names one, at midnight UTC. It writes the
     * day out, "16 October 2026 Friday"; a plain "2026-10-16" is understood too.
     */
    val nextEpisodeDay: Instant?
        get() {
            val raw = nextEpisodeDate.gregorian ?: return null
            val words = raw.split(' ').filter { it.isNotEmpty() }.take(3).joinToString(" ")
            val day = runCatching { LocalDate.parse(words, spelledDay) }.getOrNull()
                ?: runCatching { LocalDate.parse(raw.take(10), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            return day?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
        }

    /**
     * The day of the next episode as people say it, weekday first and no year: "پنجشنبه ۲۳ مهر",
     * "Thursday 15 October". The server writes it the other way round ("۲۳ مهر ۱۴۰۵ پنجشنبه").
     */
    fun nextEpisodeLabel(lang: Lang): String? {
        val raw = (if (lang == Lang.fa) nextEpisodeDate.jalali else nextEpisodeDate.gregorian) ?: return null
        val parts = raw.split(' ').filter { it.isNotEmpty() }
        // Day, month, year, then the weekday, which in Persian can be two words ("سه شنبه").
        if (parts.size < 4) return raw
        return listOf(parts.drop(3).joinToString(" "), parts[0], parts[1]).joinToString(" ")
    }

    companion object {
        private val spelledDay: DateTimeFormatter =
            DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(Locale.US)

        fun from(j: Json): Post? {
            val id = j["id"].int ?: return null
            val o = j["options"]
            val info = j["info"]
            val isSeries = o["is_series"].bool
            fun positive(v: Json): Int? = v.int?.takeIf { it > 0 }
            fun names(v: Json): List<String> = v.array.mapNotNull { it["name"].text ?: it.text }
            var fanScore: FanScore? = null
            for ((key, label) in listOf("myanimelist" to "MAL", "mydramalist" to "MDL")) {
                val v = j["score"][key]["score"].double
                if (v != null && v > 0) { fanScore = FanScore(label, v); break }
            }
            val language = j["language"]["primary"]["name"].text
            val spoken = mutableListOf<String>()
            for (name in j["language"]["others"].array.mapNotNull { it["name"].text }) {
                if (name != language && name !in spoken) spoken.add(name)
            }
            return Post(
                id = id,
                isSeries = isSeries,
                kind = if (o["title_type"].text == "anime") Kind.anime else if (isSeries) Kind.series else Kind.movie,
                hasStream = o["stream"].bool,
                comingSoon = o["coming_soon"].bool,
                freeStream = o["free_stream"].bool,
                persianSubtitle = o["persian_subtitle"].bool,
                titleEnglish = j["title"]["english"].text,
                titleLocal = j["title"]["local"].text,
                plotEnglish = j["plot"]["english"].text,
                plotLocal = j["plot"]["local"].text,
                year = info["year"].int,
                yearEnd = info["year_end"].int,
                seasons = info["seasons"].int,
                age = info["age"].text,
                runtimeMinutes = positive(info["time"]["default"]),
                genres = j["genre"].array.mapNotNull { g -> g["name"].text?.let { Genre(it, g["slug"].text ?: it) } },
                poster = Poster.from(j["image"]["poster"]),
                cover = j["image"]["cover"]["webp"].url ?: j["image"]["cover"]["jpg"].url,
                imdbScore = j["score"]["imdb"]["score"].double?.takeIf { it > 0 },
                imdbID = j["score"]["imdb"]["link"].text?.let { imdbID(it) },
                siteScore = j["score"]["30nama"]["score"].double?.takeIf { it > 0 },
                rottenTomatoes = positive(j["score"]["rottentomatoes"]["score"]),
                metacritic = positive(j["score"]["metacritic"]["score"]),
                fanScore = fanScore,
                imdbTop250 = positive(j["score"]["imdb"]["top_250"]),
                siteTop100 = positive(j["score"]["30nama"]["top_100"]),
                imdbVotes = positive(j["score"]["imdb"]["votes"]),
                inWatchlists = positive(j["list"]["watchlist"]),
                inFavorites = positive(j["list"]["favorite"]),
                budget = info["budget"].text,
                exclusiveSubtitle = o["exclusive_subtitle"].bool,
                updates = j["note"]["update"].array.mapNotNull { TitleUpdate.from(it) },
                cast = j["cast"].array.mapNotNull { c ->
                    val name = c["name"].text ?: return@mapNotNull null
                    val p = Poster.from(c["image"]["poster"])
                    Person(name, c["as"].text, if (p.isEmpty) null else p, c["imdb"].text, c["id"].int)
                },
                directors = names(j["crew"]["director"]),
                writers = names(j["crew"]["writer"]),
                creators = names(j["crew"]["creator"]),
                networks = j["network"].array.mapNotNull { it.text },
                countries = names(j["country"]),
                language = language,
                otherLanguages = spoken,
                airStatus = j["air"]["air_status"].text,
                nextEpisodeCaption = j["air"]["next_episode_caption"].text,
                nextEpisodeDate = DatePair(j["air"]["next_episode_date"]["g"].text, j["air"]["next_episode_date"]["j"].text),
                subtitlePages = j["subtitle"].array.mapNotNull { entry ->
                    val link = entry["link"].url ?: return@mapNotNull null
                    // The language is written out in Persian; the address says it in a form that does not change.
                    val path = link.path ?: ""
                    SubtitlePage(link, path.contains("farsi") || path.contains("persian"), entry["season"].int)
                },
                resume = ContinueWatching.from(j["continue_watching"]),
            )
        }

        /**
         * Posts from any of the list shapes the API uses (`posts`, `items`, `list`, a bare array,
         * and entries wrapped as `{post: {...}}`). Duplicates are dropped, order is kept.
         */
        fun list(j: Json): List<Post> {
            val seen = HashSet<Int>()
            return rawList(j).mapNotNull { x -> from(if (x["post"].isNull) x else x["post"])?.takeIf { seen.add(it.id) } }
        }

        /**
         * The "continue watching" feed: like `list`, but each post carries its progress, which sits
         * inside the post (as `continue_watching`, or as `data`, which is what the server sends a
         * subscriber), next to it in a wrapper, or flat on the entry itself.
         */
        fun continueList(j: Json): List<Post> {
            val seen = HashSet<Int>()
            return rawList(j).mapNotNull { x ->
                val inner = if (x["post"].isNull) x else x["post"]
                val p = from(inner)?.takeIf { seen.add(it.id) } ?: return@mapNotNull null
                var resume = ContinueWatching.from(x["continue_watching"]) ?: p.resume
                    ?: ContinueWatching.from(inner["data"]) ?: ContinueWatching.from(x)
                // The feed gives a position and a percentage to four places, but no length: work it out.
                val percent = resume?.reportedPercent
                if (resume != null && resume.duration <= 0 && resume.time > 0 && percent != null && percent > 0) {
                    resume = resume.copy(duration = resume.time * 100 / percent)
                }
                p.copy(resume = resume)
            }
        }

        fun rawList(j: Json): List<Json> {
            if (j is Json.Arr) return j.items
            for (key in listOf("posts", "items", "list")) {
                val v = j[key]
                if (v is Json.Arr) return v.items
            }
            return emptyList()
        }

        private val imdbPattern = Regex("tt\\d{6,10}")

        fun imdbID(link: String): String? = imdbPattern.find(link)?.value

        /** The series among `posts` with an episode still to come, soonest first. */
        fun upcoming(posts: List<Post>, now: Instant = Instant.now()): List<Post> {
            val today = now.minusSeconds(86400)
            val seen = HashSet<Int>()
            return posts
                .filter { seen.add(it.id) }
                .mapNotNull { post -> post.nextEpisodeDay?.let { post to it } }
                .filter { it.second > today }
                .sortedBy { it.second }
                .map { it.first }
        }
    }
}

// MARK: - Playback

data class ContinueWatching(
    val fileID: String? = null,
    val time: Double = 0.0,
    val duration: Double = 0.0,
    val season: Int? = null,
    val episode: String? = null,
    /** Percentage watched as the server reports it, for responses that carry no duration. */
    val reportedPercent: Double? = null,
) {
    val progress: Double
        get() = if (duration > 0) (time / duration).coerceIn(0.0, 1.0) else ((reportedPercent ?: 0.0) / 100).coerceIn(0.0, 1.0)

    val remainingMinutes: Int?
        get() = if (duration > 0 && time > 0) maxOf(1, Math.round((duration - time) / 60).toInt()) else null

    companion object {
        fun from(j: Json): ContinueWatching? {
            if (j.obj == null) return null
            val info = j["info"]
            val fileID = j["file_id"].text ?: info["file_id"].text
            val time = j["time"].double ?: j["position"].double ?: 0.0
            if (fileID == null && time <= 0) return null
            return ContinueWatching(
                fileID = fileID,
                time = time,
                duration = j["duration"].double ?: 0.0,
                season = Episode.number(j["season"].string ?: info["season"].string),
                episode = j["episode"].text ?: info["episode"].text,
                reportedPercent = j["progress"].double,
            )
        }
    }
}

/**
 * Which audio mix to open. The original file is often a codec a device cannot play through its own
 * speakers (DTS, TrueHD); the server publishes stereo and 5.1 playlists beside it.
 */
@Suppress("EnumEntryName")
enum class AudioMix { original, stereo, surround }

/** One selectable rendition of a stream. */
data class Quality(
    val label: String,
    /** The original playlist (`default`), or the only URL the entry has. */
    val url: URI,
    /** Stereo downmix, when the server offers one apart from `url`. */
    val stereo: URI? = null,
    /** 5.1 downmix, when the server offers one apart from `url`. */
    val surround: URI? = null,
    /** Vertical resolution parsed from the label ("1080p" -> 1080); 0 for the adaptive entry. */
    val height: Int,
    val isAuto: Boolean,
) {
    /** The playlist for `mix`, falling back to the original when that mix was not published. */
    fun url(mix: AudioMix): URI = when (mix) {
        AudioMix.stereo -> stereo ?: url
        AudioMix.surround -> surround ?: url
        AudioMix.original -> url
    }

    /** Mixes worth showing. Original is always there; the others only when they are a different file. */
    val mixes: List<AudioMix>
        get() = buildList {
            if (stereo != null) add(AudioMix.stereo)
            if (surround != null) add(AudioMix.surround)
            add(AudioMix.original)
        }

    companion object {
        /** `m3u8` is either one URL or a list of `{label, default, 2ch, 6ch}` entries where "Auto" is the adaptive master. */
        fun parse(m: Json): List<Quality> {
            if (m is Json.Arr) {
                return m.items.mapNotNull { e ->
                    val url = entryURL(e) ?: return@mapNotNull null
                    val label = e["label"].text ?: "Auto"
                    val isAuto = label.lowercase() in setOf("auto", "خودکار")
                    val height = label.filter { it.isDigit() }.asciiInt() ?: 0
                    Quality(label, url, channelURL(e, "2ch"), channelURL(e, "6ch"), if (isAuto) 0 else height, isAuto || height == 0)
                }
            }
            return entryURL(m)?.let { listOf(Quality("Auto", it, height = 0, isAuto = true)) } ?: emptyList()
        }

        /** A sibling playlist (`2ch`, `6ch`). Missing keys stay nil so they are not mistaken for the original. */
        private fun channelURL(e: Json, key: String): URI? = if (e is Json.Obj) entryURL(e[key]) else null

        private fun entryURL(e: Json): URI? = when (e) {
            is Json.Str -> e.url
            is Json.Arr -> e.items.firstOrNull()?.let { entryURL(it) }
            // Stereo before 5.1: a device that cannot decode the original still gets sound.
            is Json.Obj -> listOf("default", "file", "url", "src", "link", "m3u8", "path", "source", "2ch", "6ch")
                .firstNotNullOfOrNull { entryURL(e[it]) }
            else -> null
        }
    }
}

object Episode {
    /** Leading integer of values like "3", "11,12" or "S02". */
    fun number(raw: String?): Int? {
        raw ?: return null
        return raw.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.asciiInt()
    }

    private val joiner = unicodeRegex("\\s*[,&+\\-–]\\s*")

    /** Combined episodes arrive as "11,12"; show them as "11–12". */
    fun label(raw: String?, fallback: Int): String {
        if (raw == null || raw.trimWhitespace().isEmpty()) return fallback.toString()
        return raw.replace(joiner, "–")
    }
}

/** Where a skip button takes playback, and whether it skips the intro (else the recap). */
data class SkipTarget(val isIntro: Boolean, val to: Double)

/** One playable file: a movie, or an episode of a series. */
data class StreamItem(
    val fileID: String,
    val postID: String? = null,
    val season: Int,
    val episodeRaw: String? = null,
    val titleEnglish: String? = null,
    val titleLocal: String? = null,
    val durationMinutes: Int? = null,
    /** Goes back with every progress report (`observer`). */
    val token: String = "",
    val introStart: Double? = null,
    val introEnd: Double? = null,
    val recapStart: Double? = null,
    val recapEnd: Double? = null,
    val creditsStart: Double? = null,
    val qualities: List<Quality> = emptyList(),
    /** By language code: "fa", "en". */
    val subtitles: Map<String, URI> = emptyMap(),
) {
    val id: String get() = fileID.ifEmpty { "$season-${episodeRaw ?: ""}" }
    val episodeNumber: Int get() = Episode.number(episodeRaw) ?: 0

    fun title(lang: Lang): String? {
        if (lang == Lang.fa) titleLocal?.let { return it }
        return titleEnglish ?: titleLocal
    }

    /** Highest fixed rendition by default; the adaptive master when asked for or when it is all there is. */
    fun quality(preferAdaptive: Boolean): Quality? {
        val fixed = qualities.filter { !it.isAuto }.maxByOrNull { it.height }
        val auto = qualities.firstOrNull { it.isAuto }
        return if (preferAdaptive) auto ?: fixed else fixed ?: auto
    }

    /** What to skip at `t`, if anything. One second of slack at the end so the button does not flash. */
    fun skipTarget(t: Double): SkipTarget? {
        introEnd?.let { end -> if (end > 0 && t >= (introStart ?: 0.0) && t < end - 1) return SkipTarget(true, end) }
        recapEnd?.let { end -> if (end > 0 && t >= (recapStart ?: 0.0) && t < end - 1) return SkipTarget(false, end) }
        return null
    }

    companion object {
        fun from(j: Json): StreamItem? {
            val info = j["info"]
            val opt = j["options"]
            val file = j["file"]
            val qualities = Quality.parse(file["m3u8"])
            val fileID = info["file_id"].text ?: ""
            if (qualities.isEmpty() && fileID.isEmpty()) return null
            val t = listOf(info["title"], info["name"], j["title"]).firstOrNull { !it.isNull } ?: Json.Null
            val titled = t.obj != null
            val d = info["duration"].double ?: info["time"].double
            val subtitles = LinkedHashMap<String, URI>()
            for (lang in listOf("fa", "en")) subtitleURL(file["subtitle"][lang])?.let { subtitles[lang] = it }
            return StreamItem(
                fileID = fileID,
                postID = info["post_id"].text,
                season = Episode.number(info["season"].string) ?: 1,
                episodeRaw = info["episode"].text,
                titleEnglish = if (titled) t["english"].text else t.text,
                titleLocal = if (titled) t["local"].text else null,
                durationMinutes = if (d != null && d > 0) Math.round(if (d > 600) d / 60 else d).toInt() else null,
                token = opt["token"].text ?: "",
                introStart = seconds(opt["intro_start"]),
                introEnd = seconds(opt["intro_end"]),
                recapStart = seconds(opt["previously_start"]),
                recapEnd = seconds(opt["previously_end"]),
                creditsStart = seconds(opt["end"]),
                qualities = qualities,
                subtitles = subtitles,
            )
        }

        /** Times arrive as seconds or as "HH:MM:SS". */
        fun seconds(v: Json): Double? {
            if (v is Json.Str && v.value.contains(':')) {
                return v.value.split(':').filter { it.isNotEmpty() }.fold(0.0) { acc, part -> acc * 60 + (part.asciiDouble() ?: 0.0) }
            }
            return v.double
        }

        private fun subtitleURL(v: Json): URI? = when (v) {
            is Json.Str -> v.url
            is Json.Arr -> v.items.firstOrNull()?.let { subtitleURL(it) }
            is Json.Obj -> listOf("url", "file", "src", "link", "vtt").firstNotNullOfOrNull { v[it].url }
            else -> null
        }
    }
}

data class StreamResult(val items: List<StreamItem>, val resume: ContinueWatching?) {
    val seasons: List<Int> get() = items.map { it.season }.distinct().sorted()

    /** Index of the file to resume, or the first one. */
    val startIndex: Int
        get() {
            val id = resume?.fileID ?: return 0
            return items.indexOfFirst { it.fileID == id }.takeIf { it >= 0 } ?: 0
        }

    companion object {
        fun from(j: Json): StreamResult {
            val items = j["hls"].array.mapNotNull { StreamItem.from(it) }
                .sortedWith(compareBy<StreamItem> { it.season }.thenBy { it.episodeNumber })
            val resume = ContinueWatching.from(j["continue_watching"])?.takeIf { it.fileID != null }
            return StreamResult(items, resume)
        }
    }
}

// MARK: - Feeds

data class HomeSection(val key: String, val posts: List<Post>) {
    val id: String get() = key
}

data class HomeFeed(val heroes: List<Post>, val sections: List<HomeSection>) {
    val isEmpty: Boolean get() = heroes.isEmpty() && sections.isEmpty()

    fun posts(key: String): List<Post> = sections.firstOrNull { it.key == key }?.posts ?: emptyList()

    /**
     * Titles fit to stand behind a screen that has none of its own, such as sign-in: the
     * best-regarded live-action films and series that have a wide picture. Animation is left
     * out, since a cartoon still sets the wrong tone for the whole app. When nothing qualifies,
     * anything with a wide picture will do.
     */
    fun backdrops(limit: Int = 10): List<Post> {
        val seen = HashSet<Int>()
        val wide = (sections.flatMap { it.posts } + heroes).filter { it.cover != null && seen.add(it.id) }
        val fit = wide.filter { p -> p.kind != Post.Kind.anime && p.genres.none { it.slug == "animation" } }
        return (fit.ifEmpty { wide }).sortedByDescending { it.imdbScore ?: 0.0 }.take(limit)
    }

    companion object {
        val sectionKeys = listOf("new_releases", "suggested", "top_10", "movies", "series", "anime")

        fun from(j: Json): HomeFeed {
            val heroes = Post.list(j["hero_header"]).take(8)
            val sections = sectionKeys.mapNotNull { key ->
                var posts = Post.list(j[key])
                if (key == "top_10") posts = posts.take(10)
                if (posts.isEmpty()) null else HomeSection(key, posts)
            }
            return HomeFeed(heroes, sections)
        }
    }
}

data class PostPage(val posts: List<Post>, val page: Int, val pages: Int, val total: Int) {
    companion object {
        fun from(j: Json): PostPage {
            val posts = Post.list(j)
            return PostPage(posts, j["page"].int ?: 1, j["pages"].int ?: 1, j["total"].int ?: posts.size)
        }
    }
}

/** A category or genre from the `menu` action. */
data class MenuEntry(
    val id: Int,
    val name: String,
    val slug: String,
    val count: Int? = null,
    val thumb: URI? = null,
    val full: URI? = null,
) {
    companion object {
        fun from(j: Json): MenuEntry? {
            val slug = j["slug"].text ?: return null
            val name = j["name"].text ?: return null
            return MenuEntry(
                id = j["id"].int ?: slug.hashCode(),
                name = name,
                slug = slug,
                count = j["count"].int,
                thumb = j["image"]["webp"]["thumb"].url ?: j["image"]["jpg"]["thumb"].url,
                full = j["image"]["webp"]["full"].url ?: j["image"]["jpg"]["full"].url,
            )
        }
    }
}

data class Menu(val categories: List<MenuEntry>, val genres: List<MenuEntry>) {
    companion object {
        fun from(j: Json): Menu = Menu(
            j["categories"].array.mapNotNull { MenuEntry.from(it) },
            j["genres"].array.mapNotNull { MenuEntry.from(it) },
        )
    }
}

// MARK: - Subtitles from the subtitle site

/** A page on the subtitle site that lists the subtitles for one film, or one season, in one language. */
data class SubtitlePage(val link: URI, val persian: Boolean, val season: Int? = null)

/**
 * Reads the subtitle site 30nama links to, so a subtitle can be fetched without visiting it.
 * The site has no interface for programs; this reads its pages, and will need mending if they change.
 */
object SubtitleSite {
    /** One subtitle on a listing: where its own page is, and the releases it was timed for. */
    data class Entry(val path: String, val releases: List<String>)

    private val listingLink = Regex("href=\"(/subtitle/[^\"]+/\\d+)\"[^>]*>([^<]*)</a>")
    private val downloadLink = Regex("https://api\\.subsource\\.net/v1/subtitle/download/[A-Za-z0-9]+")
    private val episodeTag = Regex("^s\\d{1,2}e\\d{1,3}$")

    /** The subtitles a listing page offers. One subtitle is listed once for every release it fits. */
    fun entries(html: String): List<Entry> {
        val order = mutableListOf<String>()
        val found = LinkedHashMap<String, MutableList<String>>()
        for (match in listingLink.findAll(html)) {
            // "Farsi/Persian — Some.Film.2020.1080p.WEB-DL": the release is what follows the dash.
            val text = match.groupValues[2]
            val dash = text.indexOf("— ")
            val release = (if (dash >= 0) text.substring(dash + 2) else text).trimWhitespace()
            val key = match.groupValues[1]
            if (key !in found) { order.add(key); found[key] = mutableListOf() }
            val list = found.getValue(key)
            if (release.isNotEmpty() && release !in list) list.add(release)
        }
        return order.map { Entry(it, found[it] ?: emptyList()) }
    }

    /** The address a subtitle's own page offers its file at. */
    fun download(html: String): URI? = downloadLink.find(html)?.let { urlOf(it.value) }

    /** The words of a file or release name that say what it is a copy of: "bluray", "1080p", "s01e02". */
    fun words(name: String): Set<String> {
        val lowered = name.lowercase().replace("web-dl", "webdl").replace("blu-ray", "bluray")
        return lowered.split { !it.isLetterOrDigit() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun String.split(isSeparator: (Char) -> Boolean): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (c in this) if (isSeparator(c)) { out.add(current.toString()); current.clear() } else current.append(c)
        out.add(current.toString())
        return out
    }

    private val sources = setOf("bluray", "brrip", "bdrip", "webdl", "web", "webrip", "hdtv", "dvdrip", "hdrip")

    /**
     * The subtitle most likely to be in time with `fileName`: for an episode it must be for that
     * episode or for its whole season, and beyond that the more of the copy's own marks it
     * shares (source, resolution, who released it), the better.
     */
    fun best(fileName: String, season: Int?, episode: Int?, among: List<Entry>): Entry? {
        val wanted = words(fileName)
        var best: Pair<Entry, Int>? = null
        for (entry in among) {
            var top = Int.MIN_VALUE
            for (release in entry.releases) {
                val has = words(release)
                var score = 0
                if (season != null && episode != null) {
                    val tag = format("s%02de%02d", season, episode)
                    val other = has.any { episodeTag.matches(it) }
                    if (tag in has) score += 100 else if (other) continue else score += 20
                }
                // The source matters most: a subtitle timed for a broadcast runs off against a disc.
                val shared = wanted intersect has
                score += (shared intersect sources).size * 30
                score += (shared - sources).size
                top = maxOf(top, score)
            }
            if (entry.releases.isEmpty()) top = 0
            if (top > (best?.second ?: Int.MIN_VALUE)) best = entry to top
        }
        return best?.first
    }

    /** Of the subtitle files in an archive, the one for this episode; for a film, the only or first one. */
    fun pick(names: List<String>, season: Int?, episode: Int?): String? {
        val subtitles = names.filter { it.lowercase().endsWith(".srt") }.sorted()
        if (season == null || episode == null) return subtitles.firstOrNull()
        val tags = listOf(format("s%02de%02d", season, episode), format("%dx%02d", season, episode), format("e%02d", episode))
        for (tag in tags) subtitles.firstOrNull { it.lowercase().contains(tag) }?.let { return it }
        return if (subtitles.size == 1) subtitles.first() else null
    }

    /** Subtitle text whatever it was saved as. Persian files are often in the Arabic Windows code page. */
    fun text(data: ByteArray): String? {
        data.utf8OrNull()?.let { return it }
        // Any even run of bytes passes for UTF-16, so that is only believed when the file says so itself.
        val bom = data.size >= 2 && ((data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) || (data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()))
        if (bom) data.decodeOrNull(Charsets.UTF_16)?.let { return it }
        return data.decodeOrNull(Charset.forName("windows-1256"))
    }
}

// MARK: - Downloads

/** One file 30nama offers for download: a film in one quality, or one episode of a series. */
data class DownloadFile(
    val id: String,
    val url: URI,
    val quality: String,
    /** The size as the server words it, "781 MB". */
    val size: String? = null,
    val encoder: String? = null,
    val tags: List<String> = emptyList(),
    val season: Int? = null,
    val episode: String? = null,
    /** Persian subtitles for this file, when 30nama has them. */
    val subtitle: URI? = null,
) {
    /** The name to save under: the one in the address, which carries the right extension. */
    val fileName: String
        get() {
            val name = url.lastPathComponent
            return if (name.isEmpty() || name == "/") "$id.mkv" else name
        }

    /** "x265 BluRay 1080p · 30nama · 781 MB" */
    val summary: String get() = listOfNotNull(quality, encoder, size).filter { it.isNotEmpty() }.joinToString(" · ")
}

/**
 * What can be downloaded for a title. A film is a flat list of files, one per quality. A series
 * comes as packs, a season in one quality, each holding its episodes.
 */
data class DownloadOptions(val files: List<DownloadFile> = emptyList(), val packs: List<Pack> = emptyList()) {
    data class Pack(
        val id: String,
        val season: Int,
        val quality: String,
        /** The size of one episode, as the server words it. */
        val size: String?,
        val encoder: String?,
        val files: List<DownloadFile>,
    ) {
        val summary: String get() = listOfNotNull(quality, encoder, size).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    val seasons: List<Int> get() = packs.map { it.season }.distinct().sorted()
    val isEmpty: Boolean get() = files.isEmpty() && packs.isEmpty()

    /** Every episode any pack of the season has, in order. A pack that lacks some of them is incomplete. */
    fun episodes(season: Int): List<String> {
        val seen = HashSet<String>()
        return packs.filter { it.season == season }.flatMap { it.files }.mapNotNull { it.episode }
            .filter { seen.add(it) }
            .sortedWith(compareBy<String> { Episode.number(it) ?: Int.MAX_VALUE }.thenBy { it })
    }

    companion object {
        /**
         * The server numbers its files with eighteen digits, more than a number here keeps apart:
         * several episodes came out with one id and all but one vanished from the list. What a
         * file is of, and the name in its address, tell every file from every other.
         */
        fun identity(url: URI, season: Int?, episode: String?): String = "${season ?: 0}|${episode ?: ""}|${url.lastPathComponent}"

        fun from(j: Json): DownloadOptions {
            val root = j["download"]
            fun tags(entry: Json) = entry["tags"].array.mapNotNull { it.text }
            if (root is Json.Arr) {
                return DownloadOptions(files = root.items.mapNotNull { entry ->
                    val url = entry["dl"].url ?: return@mapNotNull null
                    DownloadFile(identity(url, null, null), url, entry["quality"].text ?: "", entry["size"].text, entry["encoder"].text,
                        tags(entry), subtitle = entry["persian_subtitle"].url)
                })
            }
            val packs = mutableListOf<Pack>()
            for ((_, group) in root.obj ?: emptyMap()) {
                for (entry in group.array) {
                    val season = entry["season_int"].int ?: entry["season"].int ?: 1
                    val quality = entry["quality"].text ?: ""
                    val links = entry["link"].array.mapNotNull { link ->
                        val url = link["dl"].url ?: return@mapNotNull null
                        DownloadFile(identity(url, season, link["episode"].string), url, quality, entry["size"].text, entry["encoder"].text,
                            tags(entry), season, link["episode"].string, link["persian_subtitle"].url)
                    }
                    if (links.isEmpty()) continue
                    packs.add(Pack("$season-$quality-${entry["encoder"].text ?: ""}-${packs.size}", season, quality,
                        entry["size"].text, entry["encoder"].text, links))
                }
            }
            return DownloadOptions(packs = packs.sortedWith(compareBy<Pack> { it.season }.thenBy { it.quality }))
        }
    }
}

// MARK: - Narrowing a catalogue

/** The choices 30nama's advanced search offers, as the server lists them. */
data class FilterOptions(
    val genres: List<Choice>,
    val countries: List<Choice>,
    /** Lowest IMDb score to accept. */
    val scores: List<Choice>,
    /** First year of each decade there are titles for, newest first. */
    val decades: List<Int>,
) {
    data class Choice(val name: String, val value: String) {
        val id: String get() = value
    }

    companion object {
        fun from(j: Json): FilterOptions {
            fun choices(key: String) = j[key].array.mapNotNull { entry ->
                val name = entry["name"].text ?: return@mapNotNull null
                val value = entry["value"].string?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Choice(name, value)
            }
            val newest = (j["max_year"].int ?: 2020) / 10 * 10
            val oldest = maxOf(1950, (j["min_year"].int ?: 1950) / 10 * 10)
            return FilterOptions(
                genres = choices("genre"),
                countries = choices("country"),
                // "Under 5.0" is sent as a negative number; only the floors are offered.
                scores = choices("imdb_rate").filter { (it.value.asciiDouble() ?: 0.0) > 0 },
                decades = if (newest >= oldest) (newest downTo oldest step 10).toList() else emptyList(),
            )
        }
    }
}

/** What the user has narrowed a catalogue down to. */
data class Filter(
    val genre: String? = null,
    val country: String? = null,
    val score: String? = null,
    /** First year of the decade: 2010 stands for 2010 to 2019. */
    val decade: Int? = null,
) {
    val isEmpty: Boolean get() = genre == null && country == null && score == null && decade == null

    /**
     * The `parameters` value `advanced_search` takes: a JSON object of the chosen fields.
     * @param type "movie", "series" or "anime", to stay inside one catalogue.
     */
    fun parameters(type: String?): String {
        val fields = LinkedHashMap<String, Any>()
        fields["stream"] = "true"
        // As a list: the server ignores a type given as a single value, and the catalogue of films
        // filled up with series.
        if (type != null) fields["type"] = listOf(type)
        // The genre as a list too: a single one is ignored. The country, though, only as a single
        // value; a list of one finds nothing (both tried on the server, 6 Aban 1405).
        genre?.let { fields["genre"] = listOf(it) }
        country?.let { fields["country"] = it }
        score?.asciiDouble()?.let { fields["imdb_rate"] = it }
        decade?.let {
            fields["min_year"] = it
            fields["max_year"] = it + 9
        }
        return Json.of(fields).serialize(sortedKeys = true)
    }
}

// MARK: - Account

data class User(
    val token: String,
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    /** The day the subscription ends, as "2028-07-15". */
    val expiry: String? = null,
    /** The same day in the Persian calendar ("۱۴۰۷-۰۴-۲۵"), when the server gives it. */
    val expiryLocal: String? = null,
    val avatar: URI? = null,
) {
    /** Whole days until the subscription ends, counting today; nil when the end is unknown. */
    fun daysLeft(now: Instant = Instant.now()): Int? {
        val end = expiry?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() } ?: return null
        val seconds = java.time.Duration.between(now, end.atStartOfDay(ZoneOffset.UTC).toInstant()).toMillis() / 1000.0
        return maxOf(0, Math.ceil(seconds / 86400).toInt())
    }

    companion object {
        /** The profile is returned bare, or wrapped as `{user: {...}}`, with or without the session token. */
        fun from(j: Json, fallbackToken: String? = null): User? {
            val u = if (j["user_session"].isNull && j["user"].obj != null) j["user"] else j
            val token = u["user_session"].text ?: u["token"].text ?: fallbackToken ?: return null
            // A subscriber's expiry arrives as `{days, g, j}`: the date and time in both calendars.
            val expire = u["user_expire"]
            val dated = expire.obj != null
            return User(
                token = token,
                id = u["user_id"].text ?: u["id"].text,
                name = u["user_name"].text ?: u["user_email"].text,
                type = u["user_type"].text,
                expiry = if (dated) expire["g"].text?.take(10) else formatExpiry(expire),
                expiryLocal = if (dated) expire["j"].text?.take(10) else u["user_expire_local"].text,
                avatar = u["user_avatar"].url,
            )
        }

        private val timestamp = Regex("^\\d{9,13}$")

        /** Expiry arrives as a unix timestamp (seconds or milliseconds) or as preformatted text. */
        fun formatExpiry(v: Json): String? {
            val raw = v.text ?: return null
            if (!timestamp.matches(raw)) return raw
            var n = raw.asciiDouble() ?: return raw
            if (n > 1e11) n /= 1000
            return Instant.ofEpochMilli((n * 1000).toLong()).atOffset(ZoneOffset.UTC).toLocalDate().toString()
        }
    }
}

/** One change 30nama made to a title, as its notes list them. */
data class TitleUpdate(
    val title: String,
    val content: String?,
    /** "2026-10-09 22:41:36" and its Persian-calendar twin, as sent. */
    val gregorian: String?,
    val jalali: String?,
) {
    /** The day it was made, without the hour: in the Persian calendar for Persian. */
    fun day(persian: Boolean): String? {
        val full = if (persian) jalali ?: gregorian else gregorian ?: jalali
        return full?.split(' ')?.firstOrNull { it.isNotEmpty() }
    }

    companion object {
        fun from(j: Json): TitleUpdate? {
            val title = j["title"].text ?: return null
            return TitleUpdate(title, j["content"].text, j["date"]["g"].text, j["date"]["j"].text)
        }
    }
}
