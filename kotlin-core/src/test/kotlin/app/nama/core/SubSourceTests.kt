package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class SubSourceTests {
    private fun zip(name: String): ByteArray = fixtureBytes("$name.zip")

    @Test fun testReadsDeflatedAndStoredArchives() {
        val files = Zip.files(zip("subsource_deflated"))
        assertEquals(listOf("Show.S01E02.WEB.srt", "Show.S01E03.WEB.srt"), files.map { it.name })
        assertTrue(String(files[0].data, Charsets.UTF_8).contains("سلام"))
        assertTrue(Zip.files(zip("subsource_stored")).first().data.contentEquals(files[0].data))
        assertTrue(Zip.files("not a zip".toByteArray()).isEmpty())
    }

    @Test fun testRefusesAnEntryThatClaimsToBeHuge() {
        // The stored archive with its one entry rewritten to claim 4 GB unpacked.
        val bytes = zip("subsource_stored")
        val end = bytes.size - 22
        fun u(i: Int) = bytes[i].toInt() and 0xFF
        val directory = u(end + 16) or (u(end + 17) shl 8) or (u(end + 18) shl 16) or (u(end + 19) shl 24)
        for (i in 0 until 4) bytes[directory + 24 + i] = 0xFF.toByte()
        assertTrue("nothing is allocated for it", Zip.files(bytes).isEmpty())
    }

    @Test fun testTakesTheEpisodesFileOutOfAnArchive() {
        val text = SubSource.text(zip("subsource_deflated"), 1, 3)
        assertEquals(true, text?.contains("فقط قسمت سه"))
        assertNull("no file for that episode", SubSource.text(zip("subsource_deflated"), 1, 9))
    }

    /** The shape of the site's own search answer. */
    private val search = obj("success" to true, "results" to listOf(
        mapOf("id" to 29130, "title" to "Dark Matter", "type" to "tvseries", "link" to "/series/dark-matter", "releaseYear" to 2015,
            "seasons" to listOf(mapOf("season" to 1, "id" to 29130, "link" to "/subtitles/dark-matter/season=1"))),
        mapOf("id" to 131507, "title" to "Dark Matter", "type" to "tvseries", "link" to "/series/dark-matter-2024", "releaseYear" to 2024,
            "seasons" to listOf(mapOf("season" to 1, "id" to 131507, "link" to "/subtitles/dark-matter-2024/season=1"),
                mapOf("season" to 2, "id" to 162344, "link" to "/subtitles/dark-matter-2024/season=2"))),
        mapOf("id" to 29156, "title" to "Dark Matter", "type" to "movie", "link" to "/subtitles/dark-matter-2007", "releaseYear" to 2007),
    ))

    @Test fun testPicksTheRightTitleAndSeason() {
        val series = SubSource.Query("Dark Matter", 2024, null, isSeries = true, season = 2)
        assertEquals("https://subsource.net/subtitles/dark-matter-2024/season-2/farsi_persian", SubSource.listing(search, series)?.toString())
        val film = SubSource.Query("Dark Matter", 2007, null, isSeries = false, season = null)
        assertEquals("https://subsource.net/subtitles/dark-matter-2007/farsi_persian", SubSource.listing(search, film)?.toString())
        val other = SubSource.Query("Dark Matter", 2019, null, isSeries = false, season = null)
        assertNull("a film of another year is not this one", SubSource.listing(search, other))
    }

    @Test fun testOldListingAddressesAreBroughtUpToDate() {
        assertEquals("https://subsource.net/subtitles/x/season-3/farsi_persian",
            SubSource.current(URI("https://subsource.net/subtitles/x/season=3/farsi_persian")).toString())
    }

    @Test fun testReadsTheDocumentedAPI() {
        val found = obj("success" to true, "data" to listOf(mapOf("movieId" to 10, "season" to 1), mapOf("movieId" to 11, "season" to 2)))
        assertEquals(11, SubSource.movieID(found, SubSource.Query("x", null, null, isSeries = true, season = 2)))
        val subs = obj("data" to listOf(mapOf("subtitleId" to 5, "releaseInfo" to listOf("Show.S01E02.WEB-DL")), mapOf("releaseInfo" to listOf("no id"))))
        assertEquals(listOf(SubSource.Candidate(SubSource.Candidate.Source.Api(5), listOf("Show.S01E02.WEB-DL"))), SubSource.candidatesInAPI(subs))
        val request = SubSource.searchRequest(SubSource.Query("x", null, "tt123", isSeries = false, season = null), "k")
        assertEquals("k", request.header("X-API-Key"))
        assertTrue(request.url.contains("searchType=imdb&imdb=tt123"))
    }

    @Test fun testRanksForTheEpisodeAndTheCopy() {
        fun c(name: String) = SubSource.Candidate(SubSource.Candidate.Source.Page(URI("https://e.x/$name")), listOf(name))
        val list = listOf(c("Show.S01E05.WEB"), c("Show.S01E02.HDTV"), c("Show.S01.WEB"), c("Show.S01E02.WEB-DL"))
        assertEquals(listOf("Show.S01E02.WEB-DL", "Show.S01E02.HDTV", "Show.S01.WEB"),
            SubSource.ranked(list, "Show.S01E02.1080p.WEB-DL", 1, 2).map { it.name })
    }
}
