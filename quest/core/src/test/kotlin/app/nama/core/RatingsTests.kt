package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RatingsTests {
    private fun post(id: Int, genres: List<String>, imdb: Double? = null, soon: Boolean = false): Post {
        val json = linkedMapOf<String, Any?>(
            "id" to id,
            "title" to mapOf("english" to "Title $id 2020", "local" to "عنوان $id"),
            "info" to mapOf("year" to 2020),
            "options" to mapOf("is_series" to (id % 2 == 0), "coming_soon" to soon),
            "genre" to genres.map { mapOf("name" to it.replaceFirstChar { c -> c.uppercase() }, "slug" to it) },
            "image" to mapOf("poster" to mapOf("preview" to "00OZZy", "webp" to mapOf("large" to "https://example.com/$id-l.webp", "big" to "https://example.com/$id-b.webp"))),
        )
        if (imdb != null) json["score"] = mapOf("imdb" to mapOf("score" to imdb))
        return Post.from(Json.of(json))!!
    }

    private fun at(seconds: Long): Instant = Instant.ofEpochSecond(seconds)

    @Test fun testRateChangeAndRemove() {
        val book = RatingBook()
        book.rate(post(1, listOf("drama")), 4.3)
        assertEquals("snaps to the nearest half star", 4.5, book.stars(1)!!, 0.0)
        book.rate(post(1, listOf("drama")), 9.0)
        assertEquals(5.0, book.stars(1)!!, 0.0)
        book.rate(post(2, listOf("comedy")), 0.0)
        assertEquals(0.5, book.stars(2)!!, 0.0)
        assertEquals(2, book.count)
        assertEquals(2.75, book.average!!, 0.0)
        book.rate(post(1, listOf("drama")), null)
        assertNull(book.stars(1))
        assertEquals(1, book.count)
        assertNull(RatingBook().average)
    }

    @Test fun testExportsACSVLetterboxdCanRead() {
        val book = RatingBook()
        book.rate(post(1, listOf("drama")), 4.0, at(1_000))
        book.rate(post(2, listOf("comedy")), 2.5, at(2_000))
        val awkward = post(3, listOf("crime")).copy(titleEnglish = "Me, Myself & \"I\" 2020")
        book.rate(awkward, 5.0, at(3_000))
        assertEquals("newest first, the year taken off the title, commas and quotes escaped",
            "Title,Year,Rating\n\"Me, Myself & \"\"I\"\"\",2020,5.0\nTitle 2,2020,2.5\nTitle 1,2020,4.0\n", book.csv())
        assertEquals("Title,Year,Rating\n", RatingBook().csv())
    }

    @Test fun testSurvivesEncoding() {
        val book = RatingBook()
        book.rate(post(1, listOf("drama", "crime")), 4.0, at(1_000))
        book.rate(post(2, listOf("comedy")), 2.5, at(2_000))
        val restored = RatingBook.from(book.encoded())!!
        assertEquals(book, restored)
        assertEquals(listOf(2, 1), restored.newestFirst.map { it.postID })
        assertNull(RatingBook.from("not json".toByteArray()))
    }

    @Test fun testBooksSavedBeforeRemovalsWereRecordedStillOpen() {
        val legacy = """[{"postID":3,"stars":4,"date":1000,"isSeries":false,"isAnime":false,"genres":[]}]""".toByteArray()
        val book = RatingBook.from(legacy)!!
        assertEquals(4.0, book.stars(3)!!, 0.0)
        assertTrue(book.removed.isEmpty())
    }

    @Test fun testRemovalIsRememberedUntilTheTitleIsRatedAgain() {
        val book = RatingBook()
        book.rate(post(1, listOf("drama")), null, at(5))
        assertTrue("nothing was rated, so nothing was removed", book.removed.isEmpty())
        book.rate(post(1, listOf("drama")), 4.0, at(10))
        book.rate(post(1, listOf("drama")), null, at(20))
        assertEquals(mapOf(1 to at(20)), book.removed)
        assertEquals("removals survive encoding", book, RatingBook.from(book.encoded()))
        book.rate(post(1, listOf("drama")), 3.0, at(30))
        assertTrue(book.removed.isEmpty())
    }

    @Test fun testMergeKeepsWhateverHappenedLast() {
        val here = RatingBook()
        val there = RatingBook()
        here.rate(post(1, listOf("drama")), 2.0, at(10))      // rated on both, later there
        there.rate(post(1, listOf("drama")), 5.0, at(20))
        here.rate(post(2, listOf("drama")), 4.0, at(10))      // only here
        there.rate(post(3, listOf("drama")), 3.0, at(10))     // only there
        here.rate(post(4, listOf("drama")), 4.0, at(10))      // rated here, removed there afterwards
        there.rate(post(4, listOf("drama")), 4.0, at(10))
        there.rate(post(4, listOf("drama")), null, at(30))
        there.rate(post(5, listOf("drama")), 1.0, at(10))     // removed there, rated again here afterwards
        there.rate(post(5, listOf("drama")), null, at(20))
        here.rate(post(5, listOf("drama")), 4.5, at(40))

        val merged = here.merged(there)
        assertEquals(5.0, merged.stars(1)!!, 0.0)
        assertEquals(4.0, merged.stars(2)!!, 0.0)
        assertEquals(3.0, merged.stars(3)!!, 0.0)
        assertNull("the removal came after the rating", merged.stars(4))
        assertEquals(mapOf(4 to at(30)), merged.removed)
        assertEquals("the rating came after the removal", 4.5, merged.stars(5)!!, 0.0)
        assertEquals("both devices arrive at the same book", merged, there.merged(here))
        assertEquals(merged, merged.merged(here))
    }

    @Test fun testRatedTitleCanBeShownAgain() {
        val original = post(8, listOf("drama"))
        val shown = Rating(original, 4.0).post!!
        assertEquals(8, shown.id)
        assertEquals("Title 8", shown.title(Lang.en))
        assertEquals("عنوان 8", shown.title(Lang.fa))
        assertTrue(shown.isSeries)
        assertEquals(original.poster.big, shown.poster.big)
        assertEquals("00OZZy", shown.poster.blurhash)
        assertEquals(listOf("drama"), shown.genres.map { it.slug })
    }

    @Test fun testTasteShrinksThinEvidence() {
        val book = RatingBook()
        for (id in 1..6) book.rate(post(id, listOf("drama")), 4.0)
        book.rate(post(7, listOf("western")), 5.0)
        book.rate(post(8, listOf("horror")), 1.0)
        val taste = book.taste()
        assertEquals(0.75, taste["drama"] ?: 0.0, 0.0001)        // six at +1, over 6 + 2
        assertEquals(2.0 / 3, taste["western"] ?: 0.0, 0.0001)   // one at +2, over 1 + 2
        assertTrue((taste["horror"] ?: 0.0) < 0)
        assertEquals("many good dramas outrank one great western", listOf("drama", "western"), book.favoriteGenres())
    }

    @Test fun testTasteAtAGlance() {
        val book = RatingBook()
        assertTrue(book.favoriteGenreNames().isEmpty())
        assertEquals(listOf(0, 0, 0, 0, 0), book.spread())
        for (id in 1..4) book.rate(post(id, listOf("drama")), 4.5)
        book.rate(post(5, listOf("comedy")), 4.0)
        book.rate(post(6, listOf("horror")), 1.0)
        book.rate(post(7, listOf("horror")), 0.5)
        val favourites = book.favoriteGenreNames()
        assertEquals("by name, strongest first, disliked genres left out", listOf("Drama", "Comedy"), favourites.map { it.name })
        assertEquals(1.0, favourites.first().strength, 0.0)
        assertTrue(favourites[1].strength < 1)
        assertEquals("half stars count with the star above", listOf(2, 0, 0, 1, 4), book.spread())
    }

    @Test fun testRecommendations() {
        val book = RatingBook()
        assertTrue("nothing to go on yet", book.recommend(listOf(post(50, listOf("drama")))).isEmpty())
        for (id in 1..4) book.rate(post(id, listOf("drama")), 4.5)
        book.rate(post(5, listOf("horror")), 1.0)

        val picks = book.recommend(listOf(
            post(1, listOf("drama")),                       // already rated
            post(10, listOf("drama"), imdb = 7.0),
            post(11, listOf("drama"), imdb = 8.5),          // same fit, better regarded
            post(12, listOf("horror"), imdb = 9.0),         // disliked genre
            post(13, listOf("drama", "horror")),            // mixed: averages out below a pure drama
            post(14, listOf("musical")),                    // unknown genre: no opinion
            post(15, listOf("drama"), soon = true),         // not out yet
            post(11, listOf("drama"), imdb = 8.5),          // duplicate candidate
        ))
        assertEquals(listOf(11, 10, 13), picks.map { it.id })
        assertEquals(1, book.recommend(listOf(post(10, listOf("drama")), post(11, listOf("drama"))), limit = 1).size)
    }
}
