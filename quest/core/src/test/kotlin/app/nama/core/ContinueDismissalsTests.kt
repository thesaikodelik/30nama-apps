package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueDismissalsTests {
    private fun feed(vararg entries: Pair<Int, Double>): List<Post> = Post.continueList(obj("posts" to entries.map { (id, time) ->
        mapOf("id" to id, "continue_watching" to mapOf("file_id" to "f$id", "time" to time, "duration" to 3000))
    }))

    @Test fun testADismissedTitleStaysHiddenUntilWatchedAgain() {
        val dismissals = ContinueDismissals()
        val list = feed(1 to 600.0, 2 to 900.0)
        dismissals.dismiss(list[0])
        assertEquals(listOf(2), dismissals.filter(list).map { it.id })
        assertEquals("the other title moving on does not bring it back", listOf(2), dismissals.filter(feed(1 to 600.0, 2 to 930.0)).map { it.id })
        assertEquals("watched again: back on the list", listOf(1, 2), dismissals.filter(feed(1 to 1200.0, 2 to 930.0)).map { it.id })
        assertTrue("and no longer remembered", dismissals.positions.isEmpty())
    }

    @Test fun testForgetsTitlesTheServerNoLongerLists() {
        val dismissals = ContinueDismissals()
        dismissals.dismiss(feed(1 to 600.0)[0])
        dismissals.filter(feed(2 to 900.0))
        assertTrue(dismissals.positions.isEmpty())
    }

    @Test fun testSurvivesAPropertyList() {
        val dismissals = ContinueDismissals()
        dismissals.dismiss(feed(7 to 120.0)[0])
        assertEquals(dismissals, ContinueDismissals(dismissals.plist))
    }
}
