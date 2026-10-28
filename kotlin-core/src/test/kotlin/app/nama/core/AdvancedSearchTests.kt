package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedSearchTests {
    @Test fun testReadsTheChoicesTheServerActsOn() {
        val choices = SearchChoices.from(parse("""
            {"genre":[{"name":"Drama","value":"drama"},{"name":"All","value":""}],
             "type":[{"name":"Movie","value":"movie"},{"name":"Movie Anime","value":"movie_anime"},{"name":"Anime","value":"anime"}],
             "channel":[{"name":"Netflix","value":"Netflix"}],
             "language":[{"name":"Korean","value":"ko"}],
             "imdb_rate":[{"name":"Above 8.5","value":8.5},{"name":"Under 5.0","value":-5}],
             "age":["PG-13","R"],
             "quality":[{"name":"4K","value":"4K"}],
             "min_year":1950,"max_year":2026}
            """))
        assertEquals("the server's own All entry is not a choice", listOf("drama"), choices.genres.map { it.value })
        assertEquals("anime films are refused by the server", listOf("movie", "anime"), choices.types.map { it.value })
        assertEquals(listOf("Netflix"), choices.networks.map { it.name })
        assertEquals("only floors", listOf("8.5"), choices.scores.map { it.value })
        assertEquals("bare values are their own names", listOf("PG-13", "R"), choices.ages.map { it.value })
        assertEquals(1950..2026, choices.years)
    }

    @Test fun testBuildsTheParameters() {
        assertEquals("""{"stream":"true"}""", AdvancedQuery().parameters())
        assertEquals(0, AdvancedQuery().count)
        val query = AdvancedQuery(genres = listOf("drama", "crime"), type = "series", language = "ko", network = "Netflix", score = "8", age = "R",
            director = "  Bong Joon Ho ", fromYear = 2020, toYear = 2010, x265 = true, streamOnly = false, sort = "imdb-rate")
        assertEquals("the years put the right way round; the type as a list",
            """{"age":"R","channel":"Netflix","director":"Bong Joon Ho","genre":["drama","crime"],"imdb_rate":8,"language":"ko","max_year":"2020","min_year":"2010","type":["series"],"x265":"true"}""",
            query.parameters())
        assertEquals(11, query.count)
        val request = query.request(page = 3)
        assertEquals("imdb-rate", request["orderby"])
        assertEquals("3", request["page"])
    }

    @Test fun testOneYearIsEnough() {
        assertEquals("""{"min_year":"1990","stream":"true"}""", AdvancedQuery(fromYear = 1990).parameters())
        assertEquals("""{"actor":"nm0000206","stream":"true"}""", AdvancedQuery(actor = "nm0000206").parameters())
        assertEquals("""{"max_year":"1995","stream":"true"}""", AdvancedQuery(toYear = 1995).parameters())
    }
}
