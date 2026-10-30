package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteLinkTests {
    private fun title(id: Int, english: String, year: Int?, series: Boolean = false): Post =
        Post.from(Json.parse("""{"id":$id,"title":{"english":"$english"},"info":{"year":${year ?: "null"}},"options":{"is_series":$series}}""".toByteArray()))!!

    @Test fun asTheSitesOwnLinks() {
        // Links as they are in the site's own articles (30 October 2026).
        assertEquals("https://30nama.com/movie/4705/X-Men-2000", SiteLink.of(title(4705, "X-Men", 2000)))
        assertEquals("https://30nama.com/movie/582455/The-Batman-Part-II-2028", SiteLink.of(title(582455, "The Batman: Part II", 2028)))
        assertEquals("https://30nama.com/movie/128728/The-Death-of-Stalin-2017", SiteLink.of(title(128728, "The Death of Stalin", 2017)))
    }

    @Test fun aSeriesAndTheEdges() {
        assertEquals("https://30nama.com/series/1/Friends-1994", SiteLink.of(title(1, "Friends", 1994, series = true)))
        // A year already at the end of the name is not written twice.
        assertEquals("https://30nama.com/movie/2/Up-2009", SiteLink.of(title(2, "Up 2009", 2009)))
        // Punctuation and spaces together are one hyphen.
        assertEquals("https://30nama.com/movie/3/Spider-Man-Across-the-Spider-Verse-2023", SiteLink.of(title(3, "Spider-Man: Across the Spider-Verse", 2023)))
        assertEquals("https://30nama.com/movie/4", SiteLink.of(title(4, "", null)))
    }
}
