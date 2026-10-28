package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class TMDBTests {
    @Test fun testKeysOfBothKinds() {
        val bearer = TMDB.findRequest("tt1", "eyJhbGciOi")
        assertEquals("Bearer eyJhbGciOi", bearer.header("Authorization"))
        assertFalse(bearer.url.contains("api_key"))
        val plain = TMDB.findRequest("tt1", "abc123")
        assertNull(plain.header("Authorization"))
        assertTrue(plain.url.contains("api_key=abc123"))
    }

    @Test fun testSearchTakesTheTitleNotTheFirstResult() {
        val answer = obj("results" to listOf(
            mapOf("id" to 1, "title" to "Dune Drifter", "release_date" to "2020-01-01"),
            mapOf("id" to 2, "title" to "Dune", "release_date" to "1984-12-14"),
            mapOf("id" to 3, "title" to "Dune", "release_date" to "2021-09-15"),
        ))
        assertEquals(3, TMDB.bestInSearch(answer, "Dune 2021", 2021))
        assertEquals(2, TMDB.bestInSearch(answer, "Dune", 1984))
        assertNull(TMDB.bestInSearch(answer, "Arrival", 2016))
    }

    @Test fun testFindsTheTitle() {
        val found = obj("movie_results" to listOf(mapOf("id" to 7)), "tv_results" to listOf(mapOf("id" to 9)))
        assertEquals(7, TMDB.idInFind(found, isSeries = false))
        assertEquals(9, TMDB.idInFind(found, isSeries = true))
    }

    private val film = obj(
        "images" to mapOf("logos" to listOf(
            mapOf("file_path" to "/de.png", "iso_639_1" to "de", "vote_average" to 9),
            mapOf("file_path" to "/en-low.png", "iso_639_1" to "en", "vote_average" to 2),
            mapOf("file_path" to "/en.svg", "iso_639_1" to "en", "vote_average" to 5),
        )),
        "videos" to mapOf("results" to listOf(
            mapOf("key" to "teaser", "site" to "YouTube", "type" to "Teaser", "official" to true),
            mapOf("key" to "fan", "site" to "YouTube", "type" to "Trailer", "official" to false),
            mapOf("key" to "real", "site" to "YouTube", "type" to "Trailer", "official" to true),
        )),
        "credits" to mapOf("crew" to listOf(
            mapOf("name" to "Sam Grip", "job" to "Gaffer"),
            mapOf("name" to "Ann Write", "job" to "Screenplay", "profile_path" to "/a.jpg"),
            mapOf("name" to "Dee Rect", "job" to "Director"),
            mapOf("name" to "Dee Rect", "job" to "Screenplay"),
        )),
        "reviews" to mapOf("results" to listOf(
            mapOf("author" to "critic", "content" to "Fine.", "author_details" to mapOf("rating" to 7)),
            mapOf("author" to "empty", "content" to ""),
        )),
    )

    @Test fun testReadsWhatAPageShows() {
        val info = TMDB.info(film, Lang.en)
        assertEquals("English, the best voted, as PNG", "https://image.tmdb.org/t/p/w500/en.png", info.logo?.toString())
        assertEquals("real", info.trailer)
        assertEquals("director first, each person once, no gaffer", listOf("Dee Rect", "Ann Write"), info.crew.map { it.name })
        assertEquals("https://image.tmdb.org/t/p/w185/a.jpg", info.crew.last().photo?.toString())
        assertEquals(listOf(TMDB.Review("critic", "Fine.", 7.0, null)), info.reviews)
    }

    @Test fun testSeriesCrewAndCreators() {
        val series = obj(
            "created_by" to listOf(mapOf("name" to "Cree Ator")),
            "aggregate_credits" to mapOf("crew" to listOf(mapOf("name" to "Com Poser", "jobs" to listOf(mapOf("job" to "Original Music Composer"))))),
        )
        assertEquals(listOf("Creator", "Original Music Composer"), TMDB.info(series, Lang.fa).crew.map { it.job })
    }

    @Test fun testReadsTheLogoFromThePublicPage() {
        val html = """<img src="https://media.themoviedb.org/t/p/w500/aEbOsxbcx4.png"><img src="/t/p/w500/second.png">"""
        assertEquals("https://image.tmdb.org/t/p/w500/aEbOsxbcx4.png", TMDB.logoInPage(html)?.toString())
        assertNull(TMDB.logoInPage("<html>no logos</html>"))
        val page = URI("https://www.themoviedb.org/movie/475557-joker")
        assertEquals("https://www.themoviedb.org/movie/475557-joker/images/logos?image_language=en", TMDB.logosPage(page, "en")?.toString())
        assertNull("not a title page", TMDB.logosPage(URI("https://www.themoviedb.org/search"), "en"))
    }

    @Test fun testReadsTheTrailerFromThePublicPage() {
        assertEquals("t433PEQGErc", TMDB.trailerInPage("""<a data-site="YouTube" data-id="t433PEQGErc" href="#">"""))
        assertNull(TMDB.trailerInPage("<p>none</p>"))
        assertEquals("https://www.themoviedb.org/tv/196322-dark-matter/videos?active_nav_item=Trailers&language=en-US",
            TMDB.trailersPage(URI("https://www.themoviedb.org/tv/196322-dark-matter"))?.toString())
    }
}
