package app.nama.core

import java.net.URI

/**
 * What the back of a title's poster shows (the Android TV app turns the poster over): another of
 * its posters, its name in its own language and script, the collection it belongs to and its place
 * there, and the logo of the company that made it. For every viewer from `cards/NN.json` on the
 * `data` branch, which a daily job builds from TMDB's API (Tools/logos/build_cards.py); a viewer
 * with a TMDB key of their own reads TMDB's answer itself, in the same shape. TMDB pictures are
 * kept as file names.
 */
data class TitleCard(
    val poster: String? = null,
    val ownName: String? = null,
    val collection: Collection? = null,
    val companyLogo: String? = null,
    val companyName: String? = null,
) {
    /** A collection: its names (English, and Persian or Arabic where TMDB has them), and the title's place in it. */
    data class Collection(val names: Map<String, String>, val at: Int, val of: Int) {
        fun name(lang: Lang): String? = names[lang.name] ?: names["en"]
    }

    /** Nothing to put on the back: the poster does not turn. */
    val isEmpty: Boolean get() = poster == null && ownName == null && collection == null && companyLogo == null

    companion object {
        const val BASE = "https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/cards/"
        private val wanted = setOf("fa", "ar")

        /** The file a title is kept in: the last two digits of its IMDb number. */
        fun shard(imdb: String): String = imdb.filter { it.isDigit() }.takeLast(2).padStart(2, '0')

        fun address(imdb: String): URI? = urlOf("$BASE${shard(imdb)}.json")

        /** A TMDB picture by its file name: from TMDB, or through wsrv.nl where TMDB is filtered. */
        fun picture(name: String, filtered: Boolean): URI? =
            if (filtered) LogoIndex.proxied(name) else urlOf("${TMDB.IMAGES}w500/$name")

        /** The title's card in one of the daily files; null where it is not there. */
        fun fromShard(answer: Json, imdb: String): TitleCard? {
            if (answer["version"].int != 1) return null
            val c = answer["cards"][imdb].takeIf { it.obj != null } ?: return null
            val collection = c["c"].takeIf { it.obj != null }?.let { col ->
                val names = (col["names"].obj ?: emptyMap()).mapNotNull { (k, v) -> v.text?.let { k to it } }.toMap()
                val at = col["at"].int
                val of = col["of"].int
                if (names.isEmpty() || at == null || of == null || of < 2) null else Collection(names, at, of)
            }
            return TitleCard(c["p"].text, c["o"].text, collection, c["l"].text, c["n"].text).takeUnless { it.isEmpty }
        }

        /**
         * The same card from TMDB's own answer, for a viewer with a key: [details] asked with
         * `append_to_response=images` and `include_image_language=en,null`, [collection] with
         * `append_to_response=translations`. As build_cards.py makes it.
         */
        fun fromTMDB(details: Json, collection: Json? = null): TitleCard? {
            val main = details["poster_path"].text
            val poster = details["images"]["posters"].array
                .filter { p -> p["file_path"].text.let { it != null && it != main } && (p["iso_639_1"].text ?: "") in setOf("en", "") }
                .maxWithOrNull(compareBy({ it["vote_average"].double ?: 0.0 }, { it["vote_count"].int ?: 0 }))
                ?.get("file_path")?.text?.trimStart('/')
            val original = (details["original_title"].text ?: details["original_name"].text)
            val shown = details["title"].text ?: details["name"].text
            val language = details["original_language"].text
            val ownName = original?.takeIf { language != null && language != "en" && !it.equals(shown, ignoreCase = true) }
            val company = listOf("production_companies", "networks").asSequence()
                .flatMap { details[it].array.asSequence() }
                .firstOrNull { it["logo_path"].text != null }
            return TitleCard(
                poster = poster,
                ownName = ownName,
                collection = collection?.let { collectionOf(it, details["id"].int) },
                companyLogo = company?.get("logo_path")?.text?.trimStart('/')?.replace(".svg", ".png"),
                companyName = company?.get("name")?.text,
            ).takeUnless { it.isEmpty }
        }

        private fun collectionOf(collection: Json, id: Int?): Collection? {
            val parts = collection["parts"].array.filter { it["id"].int != null }
                .sortedWith(compareBy({ it["release_date"].text ?: "9999" }, { it["id"].int }))
                .mapNotNull { it["id"].int }
            if (id == null || id !in parts || parts.size < 2) return null
            val names = buildMap {
                collection["name"].text?.let { put("en", it) }
                for (t in collection["translations"]["translations"].array) {
                    val code = t["iso_639_1"].text ?: continue
                    val name = t["data"]["name"].text ?: t["data"]["title"].text ?: continue
                    if (code in wanted) put(code, name)
                }
            }
            if (names.isEmpty()) return null
            return Collection(names, parts.indexOf(id) + 1, parts.size)
        }
    }
}
