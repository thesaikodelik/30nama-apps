package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class AparatTests {
    @Test fun testTheMostWatchedTrueTrailerIsTaken() {
        val videos = Aparat.videosInSearch(fixture("aparat_search_exorcist"))
        assertEquals(10, videos.size)
        // 30nama's English title carries its year; the reactions to the trailer are passed over.
        val found = Aparat.trailer(videos, "The Exorcist: Believer 2023", 2023)!!
        assertEquals("تریلر فیلم جن گیر: مومن The Exorcist: Believer", found.title)
    }

    @Test fun testAShortNameNeedsItsYear() {
        val videos = Aparat.videosInSearch(fixture("aparat_search_up"))
        // "step up 6", a game's "Team Up" and "Up and Away 2018" are not the 2009 film.
        val found = Aparat.trailer(videos, "Up", 2009)!!
        assertEquals("تریلر انیمیشن Up 2009", found.title)
        assertNull(Aparat.trailer(videos, "Up", null))
    }

    @Test fun testOnlyTrailersOfAFilmAndOfAFittingLength() {
        fun video(title: String, duration: Int = 120, visits: Int = 1) = Aparat.Video(title, title, duration, visits)
        val videos = listOf(
            video("نقد و بررسی تریلر فیلم Inception", 140, 900),
            video("تریلر Inception", 140, 800),
            video("تریلر فیلم Inception ساخت هوش مصنوعی", 140, 700),
            video("تریلر فیلم Inception", 1200, 600),
            video("تریلر فیلم Inception 2024", 140, 500),
            video("تریلر فیلم Inception با بازی لئوناردو دی کاپریو (زیرنویس فارسی)", 143, 50),
            video("تریلر فیلم Inception ۲۰۱۰", 143, 40),
        )
        assertEquals(50, Aparat.trailer(videos, "Inception", 2010)?.visits)
        assertEquals("تریلر فیلم inception 2010 تلقین", Aparat.words("تریلر فیلم Inception ۲۰۱۰ (تلقین)"))
    }

    @Test fun testTheFileIs720pWhereThereIsOne() {
        val video = fixture("aparat_video")
        assertEquals("https://example.com/aparat-video/abc-720p.mp4?wmsAuthSign=x", Aparat.fileInVideo(video)?.toString())
        assertNull(Aparat.fileInVideo(obj("video" to emptyMap<String, Any>())))
    }

    @Test fun testTheSearchAsksForTheTrailerByName() {
        val request = Aparat.searchRequest("Dune: Part Two 2024")
        assertEquals("www.aparat.com", URI(request.url).host)
        assertTrue(request.url.contains("videoBySearch/text/"))
        assertTrue(URLDecoder.decode(request.url.replace("+", "%2B"), "UTF-8").contains("تریلر Dune: Part Two/perpage"))
    }
}

class LogoIndexTests {
    @Test fun testALogoInTheLanguageOfTheInterfaceThroughTheProxy() {
        val index = LogoIndex.from(parse("""{"version":1,"updated":"2026-10-25","logos":{"tt1":["en.png","fa.png"],"tt2":["only.png",""]}}"""))!!
        assertEquals(2, index.count)
        assertEquals("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/fa.png", index.logo("tt1", Lang.fa)?.toString())
        assertEquals("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/en.png", index.logo("tt1", Lang.en)?.toString())
        assertEquals("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/only.png", index.logo("tt2", Lang.fa)?.toString())
        assertNull(index.logo("tt3", Lang.en))
        assertNull(index.logo(null, Lang.en))
        assertNull(LogoIndex.from(obj("version" to 2)))
    }
}
