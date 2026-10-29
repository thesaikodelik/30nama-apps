package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleCardTests {
    private val details = obj(
        "id" to 604, "title" to "The Matrix Reloaded", "original_title" to "The Matrix Reloaded", "original_language" to "en",
        "poster_path" to "/main.jpg",
        "images" to mapOf("posters" to listOf(
            mapOf("file_path" to "/main.jpg", "iso_639_1" to "en", "vote_average" to 9),
            mapOf("file_path" to "/french.jpg", "iso_639_1" to "fr", "vote_average" to 8),
            mapOf("file_path" to "/second.jpg", "iso_639_1" to "en", "vote_average" to 6),
            mapOf("file_path" to "/wordless.jpg", "iso_639_1" to null, "vote_average" to 7),
        )),
        "production_companies" to listOf(mapOf("name" to "Silver", "logo_path" to null), mapOf("name" to "Village Roadshow", "logo_path" to "/vr.svg")),
    )
    private val collection = obj(
        "name" to "The Matrix Collection",
        "parts" to listOf(mapOf("id" to 605, "release_date" to "2003-11-05"), mapOf("id" to 603, "release_date" to "1999-03-31"), mapOf("id" to 604, "release_date" to "2003-05-15")),
        "translations" to mapOf("translations" to listOf(
            mapOf("iso_639_1" to "fa", "data" to mapOf("name" to "مجموعهٔ ماتریکس")),
            mapOf("iso_639_1" to "de", "data" to mapOf("name" to "Matrix")),
        )),
    )

    @Test fun testFromTMDBAsTheDailyJobMakesIt() {
        val card = TitleCard.fromTMDB(details, collection)!!
        assertEquals("the best voted other poster, English or wordless", "wordless.jpg", card.poster)
        assertNull("an English title has no name of its own to show", card.ownName)
        assertEquals(TitleCard.Collection(mapOf("en" to "The Matrix Collection", "fa" to "مجموعهٔ ماتریکس"), 2, 3), card.collection)
        assertEquals("مجموعهٔ ماتریکس", card.collection!!.name(Lang.fa))
        assertEquals("Arabic falls back to English", "The Matrix Collection", card.collection!!.name(Lang.ar))
        assertEquals("vr.png", card.companyLogo)
        assertEquals("Village Roadshow", card.companyName)
    }

    @Test fun testOwnNameOnlyWhereItDiffers() {
        val spirited = obj("id" to 129, "title" to "Spirited Away", "original_title" to "千と千尋の神隠し", "original_language" to "ja")
        assertEquals("千と千尋の神隠し", TitleCard.fromTMDB(spirited)!!.ownName)
        assertNull("the same name in another case is nothing to show",
            TitleCard.fromTMDB(obj("id" to 1, "title" to "Amélie", "original_title" to "amélie", "original_language" to "fr")))
    }

    @Test fun testASeriesFallsBackToItsNetwork() {
        val dark = obj("id" to 1399, "name" to "Dark", "original_name" to "Dark", "original_language" to "de",
            "production_companies" to listOf(mapOf("name" to "Wiedemann", "logo_path" to null)),
            "networks" to listOf(mapOf("name" to "Netflix", "logo_path" to "/nf.png")))
        assertEquals("nf.png", TitleCard.fromTMDB(dark)!!.companyLogo)
    }

    @Test fun testACollectionOfOneIsNone() {
        val alone = obj("name" to "Solo", "parts" to listOf(mapOf("id" to 604)))
        assertNull(TitleCard.fromTMDB(details, alone)!!.collection)
    }

    @Test fun testFromTheDailyFile() {
        val shard = Json.parse("""{"version":1,"updated":"2026-10-29","cards":{"tt0234215":{"p":"wordless.jpg","c":{"names":{"en":"The Matrix Collection","fa":"مجموعهٔ ماتریکس"},"at":2,"of":3},"l":"vr.png","n":"Village Roadshow"}}}""".toByteArray())
        assertEquals(TitleCard.fromTMDB(details, collection), TitleCard.fromShard(shard, "tt0234215"))
        assertNull("not in the file", TitleCard.fromShard(shard, "tt0000015"))
        assertNull("a file of another version", TitleCard.fromShard(Json.parse("""{"version":2,"cards":{}}""".toByteArray()), "tt0234215"))
    }

    @Test fun testWhereATitleIsKept() {
        assertEquals("15", TitleCard.shard("tt0234215"))
        assertEquals("07", TitleCard.shard("tt7"))
        assertEquals("https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/cards/15.json", TitleCard.address("tt0234215").toString())
        assertEquals("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/a.jpg", TitleCard.picture("a.jpg", filtered = true).toString())
        assertEquals("https://image.tmdb.org/t/p/w500/a.jpg", TitleCard.picture("a.jpg", filtered = false).toString())
    }
}
