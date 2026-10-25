package app.nama.core

import java.time.LocalDate

/**
 * What 30nama's advanced search can be narrowed by, as `advanced_search_parameters` lists it.
 * Only the fields the server was seen to act on are kept: it also lists quality, cast, creator
 * and release group, and ignores them.
 */
data class SearchChoices(
    val genres: List<FilterOptions.Choice>,
    val types: List<FilterOptions.Choice>,
    val countries: List<FilterOptions.Choice>,
    val languages: List<FilterOptions.Choice>,
    val networks: List<FilterOptions.Choice>,
    /** Lowest IMDb score to accept. */
    val scores: List<FilterOptions.Choice>,
    val ages: List<FilterOptions.Choice>,
    /** The years there are titles for, oldest and newest. */
    val years: IntRange,
) {
    companion object {
        fun from(j: Json): SearchChoices {
            // Entries are {name, value}, or for some fields a bare value that is its own name.
            fun choices(key: String) = j[key].array.mapNotNull { entry ->
                if (entry.obj == null) return@mapNotNull entry.text?.let { FilterOptions.Choice(it, it) }
                val name = entry["name"].text ?: return@mapNotNull null
                val value = entry["value"].string?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                FilterOptions.Choice(name, value)
            }
            val newest = j["max_year"].int ?: LocalDate.now().year
            val oldest = minOf(newest, j["min_year"].int ?: 1920)
            return SearchChoices(
                genres = choices("genre"),
                // Anime films are refused outright; anime as a whole stands for them.
                types = choices("type").filter { it.value != "movie_anime" },
                countries = choices("country"),
                languages = choices("language"),
                networks = choices("channel"),
                // "Under 5.0" is sent as a negative number; only the floors are offered.
                scores = choices("imdb_rate").filter { (it.value.asciiDouble() ?: 0.0) > 0 },
                ages = choices("age"),
                years = oldest..newest,
            )
        }
    }
}

/** An advanced search: the fields chosen, and the order the titles come in. */
data class AdvancedQuery(
    val genres: List<String> = emptyList(),
    val type: String? = null,
    val country: String? = null,
    val language: String? = null,
    val network: String? = null,
    val score: String? = null,
    val age: String? = null,
    val director: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val x265: Boolean = false,
    val threeD: Boolean = false,
    val streamOnly: Boolean = true,
    val sort: String = "relevant",
) {
    /** How many fields narrow the search, for the count on the filters button. */
    val count: Int
        get() = listOf(type, country, language, network, score, age, director, fromYear, toYear).count { it != null } +
            genres.size + (if (x265) 1 else 0) + (if (threeD) 1 else 0)

    /** The `parameters` value `advanced_search` takes: a JSON object of the chosen fields. */
    fun parameters(): String {
        val fields = LinkedHashMap<String, Any>()
        if (genres.isNotEmpty()) fields["genre"] = genres
        // A list, as Filter sends it: a single value is ignored.
        type?.let { fields["type"] = listOf(it) }
        country?.let { fields["country"] = it }
        language?.let { fields["language"] = it }
        network?.let { fields["channel"] = it }
        score?.asciiDouble()?.let { fields["imdb_rate"] = it }
        age?.let { fields["age"] = it }
        director?.trimWhitespace()?.takeIf { it.isNotEmpty() }?.let { fields["director"] = it }
        // The years either way round: someone writing 2010 to 2000 means the same decade.
        var from = fromYear ?: 0
        var to = toYear ?: 0
        if (fromYear != null && toYear != null && from > to) { val t = from; from = to; to = t }
        if (from > 0) fields["min_year"] = from.toString()
        if (to > 0) fields["max_year"] = to.toString()
        if (x265) fields["x265"] = "true"
        if (threeD) fields["3D"] = "true"
        if (streamOnly) fields["stream"] = "true"
        return Json.of(fields).serialize(sortedKeys = true)
    }

    fun request(page: Int): Map<String, String> = mapOf("parameters" to parameters(), "orderby" to sort, "order" to "DESC", "page" to page.toString())

    companion object {
        val sorts = listOf("relevant", "update", "year", "imdb-rate", "favorite")
    }
}
