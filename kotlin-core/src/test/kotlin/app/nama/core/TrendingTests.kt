package app.nama.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.Instant
import java.util.UUID

/** Scripted TMDB and 30nama search: answers from tables and counts what was asked. */
private class FakeCatalogue {
    private val tmdbLog = mutableListOf<HttpRequest>()
    private val searchLog = mutableListOf<String>()

    /** TMDB list entries by request path. */
    val lists = HashMap<String, MutableList<MutableMap<String, Any?>>>()
    /** 30nama search results by query. */
    var shelves = HashMap<String, List<Map<String, Any?>>>()
    @Volatile var tmdbStatus = 200
    @Volatile var searchFails = false

    val tmdbRequests: List<HttpRequest> get() = synchronized(tmdbLog) { tmdbLog.toList() }
    val searches: List<String> get() = synchronized(searchLog) { searchLog.toList() }

    val transport = Transport { request ->
        val url = URI(request.url)
        synchronized(tmdbLog) { tmdbLog.add(request) }
        val path = url.path.removePrefix("/3")
        val page = (url.query ?: "").split('&').firstOrNull { it.startsWith("page=") }?.removePrefix("page=") ?: "1"
        val body = Json.of(mapOf("results" to if (page == "1") (lists[path] ?: emptyList<Any>()) else emptyList<Any>()))
        HttpResponse(tmdbStatus, body = body.data())
    }

    val search: suspend (String) -> Json = { query ->
        synchronized(searchLog) { searchLog.add(query) }
        if (searchFails) throw APIError.Transient("search", 0.0)
        Json.of(mapOf("posts" to (shelves[query] ?: emptyList())))
    }
}

private class TestClock(var time: Instant) {
    val now: Instant @Synchronized get() = time
    @Synchronized fun advance(hours: Double) { time = time.plusMillis((hours * 3600 * 1000).toLong()) }
}

class TrendingTests {
    private fun tmdb(id: Int, title: String, year: Int = 2024, tv: Boolean = false, type: String? = null,
                     original: String? = null, poster: Boolean = true, popularity: Double = 10.0, votes: Int = 500): MutableMap<String, Any?> {
        val j = linkedMapOf<String, Any?>("id" to id, "popularity" to popularity, "vote_count" to votes)
        j[if (tv) "name" else "title"] = title
        j[if (tv) "first_air_date" else "release_date"] = "$year-05-01"
        if (original != null) j[if (tv) "original_name" else "original_title"] = original
        if (type != null) j["media_type"] = type
        if (poster) j["poster_path"] = "/p$id.jpg"
        return j
    }

    private fun post(id: Int, title: String, year: Int = 2024, series: Boolean = false, stream: Boolean = true, soon: Boolean = false): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to mapOf("english" to "$title $year", "local" to "عنوان $id"),
        "info" to mapOf("year" to year),
        "options" to mapOf("is_series" to series, "stream" to stream, "coming_soon" to soon),
        "image" to mapOf("poster" to mapOf("webp" to mapOf("large" to "https://example.com/$id-l.webp"))),
        "cast" to listOf(mapOf("name" to "Someone")),
    )

    private fun item(json: Map<String, Any?>, kind: TMDBItem.Kind? = null): TMDBItem = TMDBItem.from(Json.of(json), kind)!!

    private fun posts(vararg list: Map<String, Any?>): List<Post> = list.mapNotNull { Post.from(Json.of(it)) }

    private val words = (0..40).map { "N${it}x" }

    /** Six movies trending on TMDB, all of them on 30nama under the same names. */
    private fun stockedCatalogue(count: Int = 6): FakeCatalogue {
        val net = FakeCatalogue()
        net.lists["/trending/all/week"] = (1..count).map { tmdb(it, "Film ${words[it]}", type = "movie") }.toMutableList()
        for (i in 1..count) net.shelves["Film ${words[i]}"] = listOf(post(100 + i, "Film ${words[i]}"))
        return net
    }

    // MARK: Matching

    @Test fun testNormalizeAndSimilarity() {
        assertEquals("amelie and co the return", TitleMatch.normalize("Amélie & Co.: The  Return!"))
        assertEquals("بازی مرکب", TitleMatch.normalize("  بازی مرکب  "))
        assertEquals(1.0, TitleMatch.similarity("Spider-Man", "spider man"), 0.0)
        assertEquals(0.0, TitleMatch.similarity("", "anything"), 0.0)
        assertEquals(0.5, TitleMatch.similarity("The Bear", "The Boys"), 0.0)
        assertEquals("a title that only adds a subtitle counts as close", 0.82, TitleMatch.similarity("Dune", "Dune Part Two"), 0.0)
        assertTrue(TitleMatch.similarity("Dune", "Dunes of Mars") < 0.8)
    }

    @Test fun testStripYear() {
        assertEquals("Animals", TitleMatch.stripYear("Animals 2026"))
        assertEquals("Animals", TitleMatch.stripYear("Animals (2026)"))
        assertEquals("Blade Runner", TitleMatch.stripYear("Blade Runner 2049"))
        assertEquals("a title that is only a year stays", "2012", TitleMatch.stripYear("2012"))
        assertEquals("Severance", TitleMatch.stripYear("Severance"))
    }

    @Test fun testBestPostWantsTheSameKindAndYear() {
        val dune = item(tmdb(1, "Dune", year = 2021), TMDBItem.Kind.movie)
        assertEquals(2, TitleMatch.bestPost(posts(post(1, "Dune", year = 1984), post(2, "Dune", year = 2021)), dune)?.id)
        assertEquals("one year off is still the same title", 3, TitleMatch.bestPost(posts(post(3, "Dune", year = 2022)), dune)?.id)
        assertNull(TitleMatch.bestPost(posts(post(4, "Dune", year = 1984)), dune))
        assertNull("a series is not a movie", TitleMatch.bestPost(posts(post(5, "Dune", year = 2021, series = true)), dune))
        assertNull("upcoming titles are skipped", TitleMatch.bestPost(posts(post(6, "Dune", year = 2021, soon = true)), dune))
        assertNull(TitleMatch.bestPost(posts(post(7, "Dunkirk", year = 2021)), dune))
    }

    @Test fun testATitleBuriedUnderItsNamesakesIsFoundByItsYear() = runBlocking {
        val net = stockedCatalogue()
        net.shelves["Film N3x"] = listOf(post(900, "Film N3x", year = 2001), post(901, "Film N3x", year = 1990))
        net.shelves["Film N3x 2024"] = listOf(post(103, "Film N3x"))
        val trending = Trending("t", transport = net.transport, search = net.search)
        assertEquals(listOf(101, 102, 103, 104, 105, 106), trending.row(TrendingRow.trend).map { it.id })
    }

    @Test fun testBestPostPrefersWhatCanBeStreamed() {
        val dune = item(tmdb(1, "Dune", year = 2021), TMDBItem.Kind.movie)
        assertEquals(2, TitleMatch.bestPost(posts(post(1, "Dune", year = 2021, stream = false), post(2, "Dune", year = 2021)), dune)?.id)
    }

    @Test fun testBestPostMatchesTheOriginalTitle() {
        val heist = item(tmdb(1, "Money Heist", year = 2017, tv = true, original = "La casa de papel"), TMDBItem.Kind.tv)
        assertEquals(9, TitleMatch.bestPost(posts(post(9, "La Casa de Papel", year = 2017, series = true)), heist)?.id)
        assertEquals(listOf("Money Heist", "La casa de papel", "Money Heist 2017"), TitleMatch.queries(heist))
        val squid = item(tmdb(2, "Squid Game", tv = true, original = "오징어 게임"), TMDBItem.Kind.tv)
        assertEquals("an original title 30nama cannot be searched by is left out", listOf("Squid Game", "Squid Game 2024"), TitleMatch.queries(squid))
    }

    @Test fun testBestPostAgainstALiveSearchResponse() {
        val results = Post.list(fixture("search")["result"])
        val series = item(tmdb(1396, "Breaking Bad", year = 2008, tv = true), TMDBItem.Kind.tv)
        val film = item(tmdb(559969, "El Camino: A Breaking Bad Movie", year = 2019), TMDBItem.Kind.movie)
        assertEquals(12382, TitleMatch.bestPost(results, series)?.id)
        assertEquals(228418, TitleMatch.bestPost(results, film)?.id)
    }

    // MARK: Lists

    @Test fun testItemsKnowTheirKind() {
        assertEquals(TMDBItem.Kind.tv, item(tmdb(1, "A", tv = true, type = "tv")).kind)
        assertEquals("without a type, an air date means a series", TMDBItem.Kind.tv, item(tmdb(1, "A", tv = true)).kind)
        assertEquals(TMDBItem.Kind.movie, item(tmdb(1, "A")).kind)
        assertEquals(2019, item(tmdb(1, "A", year = 2019)).year)
        assertNull("people are not titles", TMDBItem.from(obj("id" to 7, "media_type" to "person", "name" to "Someone")))
    }

    @Test fun testCandidatesDropDuplicatesAndTitlesWithoutAPoster() {
        val a = item(tmdb(1, "A"))
        val b = item(tmdb(2, "B"))
        val bare = item(tmdb(3, "C", poster = false))
        assertEquals(listOf(1, 2), TrendingRow.pop.candidates(listOf(listOf(a, bare, b), listOf(b, a))).map { it.id })
    }

    @Test fun testFreshSeriesPutTheBestKnownFirst() {
        val quiet = item(tmdb(1, "Quiet", tv = true, popularity = 20.0, votes = 250), TMDBItem.Kind.tv)
        val obscure = item(tmdb(2, "Obscure", tv = true, popularity = 900.0, votes = 12), TMDBItem.Kind.tv)
        val hit = item(tmdb(3, "Hit", tv = true, popularity = 80.0, votes = 9000), TMDBItem.Kind.tv)
        assertEquals(listOf(3, 1), TrendingRow.fresh.candidates(listOf(listOf(quiet, obscure, hit))).map { it.id })
        assertEquals("other rows keep TMDB's order", listOf(1, 2, 3), TrendingRow.trend.candidates(listOf(listOf(quiet, obscure, hit))).map { it.id })
    }

    @Test fun testFreshSeriesAreAskedForTheLastThirtyDays() {
        val sources = TrendingRow.fresh.sources(Instant.ofEpochSecond(1_780_000_000))   // 2026-05-28 UTC
        assertEquals(listOf("1", "2", "3"), sources.map { it.params["page"] })
        assertEquals("2026-04-28", sources[0].params["air_date.gte"])
    }

    // MARK: Rows

    // Not the Swift test of the same place: without a key Android reads the list written each day
    // from TMDB's API (trending.json on the data branch), not TMDB's public page.
    @Test fun testReadsTheSharedListWithoutAKey() = runBlocking {
        val shared = """{"updated":"2026-10-28T03:17:00Z","lists":{"/trending/all/week?language=en-US":[""" +
            """{"id":1,"media_type":"tv","name":"Film N1x","first_air_date":"2024-05-01","poster_path":"/a.jpg"},""" +
            """{"id":2,"media_type":"movie","title":"Film N2x & Co","release_date":"2024-05-03","poster_path":"/b.jpg"}]}}"""
        val net = FakeCatalogue()
        val asked = mutableListOf<String>()
        val trending = Trending("  \n", transport = { request ->
            asked.add(request.url)
            HttpResponse(200, body = shared.toByteArray())
        }, search = net.search)
        trending.row(TrendingRow.trend)
        assertEquals(TMDBItem.SHARED_TRENDING, asked.first())
        // Titles are looked up side by side, so in no fixed order.
        assertTrue("the shared list is looked for on 30nama", net.searches.contains("Film N1x"))
        assertTrue(net.searches.contains("Film N2x & Co"))
        assertTrue("a row with no list in the file stays empty", trending.row(TrendingRow.fresh).isEmpty())
    }

    @Test fun testRowFindsTrendingTitlesOn30nama() = runBlocking {
        val net = stockedCatalogue()
        val trending = Trending(" eyJsecret\n", transport = net.transport, search = net.search)
        val row = trending.row(TrendingRow.trend)
        assertEquals("in TMDB's order", listOf(101, 102, 103, 104, 105, 106), row.map { it.id })
        assertTrue("only the fields a list draws are kept", row.all { it.cast.isEmpty() })
        assertEquals("Bearer eyJsecret", net.tmdbRequests.first().header("Authorization"))
        assertEquals("https://api.themoviedb.org/3/trending/all/week?language=en-US", net.tmdbRequests.first().url)
    }

    @Test fun testRowIsCappedAndStopsSearchingOnceFull() = runBlocking {
        val net = stockedCatalogue(count = 30)
        val trending = Trending("t", transport = net.transport, search = net.search)
        assertEquals((101..116).toList(), trending.row(TrendingRow.trend).map { it.id })
        assertTrue("at most the lookups already under way overshoot", net.searches.size <= 16 + 3)
    }

    @Test fun testRowWithTooFewTitlesIsLeftOut() = runBlocking {
        val net = stockedCatalogue(count = 6)
        net.shelves = HashMap(net.shelves.filterKeys { it in listOf("Film N1x", "Film N2x", "Film N3x", "Film N4x") })
        val trending = Trending("t", transport = net.transport, search = net.search)
        assertEquals(0, trending.row(TrendingRow.trend).size)
    }

    @Test fun testTwoTMDBTitlesThatAreOne30namaTitleShowOnce() = runBlocking {
        val net = stockedCatalogue()
        net.lists["/trending/all/week"]!![1]["title"] = "Film N1x"
        net.lists["/trending/all/week"]!![1]["id"] = 77
        val trending = Trending("t", transport = net.transport, search = net.search)
        assertEquals(listOf(101, 103, 104, 105, 106), trending.row(TrendingRow.trend).map { it.id })
    }

    @Test fun testAnswersAreCachedAcrossLaunches() = runBlocking {
        val file = File(System.getProperty("java.io.tmpdir"), "trending-${UUID.randomUUID()}.json")
        try {
            val net = stockedCatalogue(count = 7)
            net.shelves.remove("Film N7x")   // one title 30nama does not have
            val clock = TestClock(Instant.ofEpochSecond(1_780_000_000))
            fun launch() = Trending("t", cacheFile = file, transport = net.transport, now = { clock.now }, search = net.search)

            val first = launch().row(TrendingRow.trend)
            assertEquals(6, first.size)
            assertEquals(1, net.tmdbRequests.size)
            assertEquals("the missing title is also tried with its year", 8, net.searches.size)

            clock.advance(hours = 5.0)
            val second = launch().row(TrendingRow.trend)
            assertEquals(first.map { it.id }, second.map { it.id })
            assertEquals("the list is good for six hours", 1, net.tmdbRequests.size)
            assertEquals("found and missing titles are both remembered", 8, net.searches.size)

            clock.advance(hours = 20.0)
            launch().row(TrendingRow.trend)
            assertEquals("the list is asked for again", 2, net.tmdbRequests.size)
            assertEquals(10, net.searches.size)
            assertEquals("a missing title is looked for again after a day", "Film N7x 2024", net.searches.last())
        } finally {
            file.delete()
        }
    }

    @Test fun testAFailedSearchIsNotRememberedAsMissing() = runBlocking {
        val net = stockedCatalogue()
        net.searchFails = true
        val trending = Trending("t", transport = net.transport, search = net.search)
        assertEquals(0, trending.row(TrendingRow.trend).size)
        net.searchFails = false
        assertEquals(6, trending.row(TrendingRow.trend).size)
    }

    @Test fun testRowIsEmptyWhenTMDBRefuses() = runBlocking {
        val net = stockedCatalogue()
        net.tmdbStatus = 401
        val trending = Trending("wrong", transport = net.transport, search = net.search)
        assertEquals(0, trending.row(TrendingRow.trend).size)
        assertEquals(0, net.searches.size)
        net.tmdbStatus = 200
        assertEquals("a refusal is not cached", 6, trending.row(TrendingRow.trend).size)
    }
}
