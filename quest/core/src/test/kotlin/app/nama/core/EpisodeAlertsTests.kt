package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EpisodeAlertsTests {
    private fun series(id: Int, next: String?): Post {
        val json = linkedMapOf<String, Any?>("id" to id, "title" to mapOf("english" to "Show $id"))
        if (next != null) json["air"] = mapOf("next_episode_date" to mapOf("g" to next))
        return Post.list(obj("posts" to listOf(json))).first()
    }

    private fun day(text: String): Instant = Instant.parse(text + "T00:00:00Z")

    @Test fun testTellsOfAnEpisodeOnceItsDayHasCome() {
        val alerts = EpisodeAlerts()
        assertTrue(alerts.update(listOf(series(1, "2026-10-20")), day("2026-10-16")).isEmpty())
        assertTrue("not yet", alerts.update(listOf(series(1, "2026-10-20")), day("2026-10-19")).isEmpty())
        val out = alerts.update(listOf(series(1, "2026-10-27")), day("2026-10-21"))
        assertEquals("the day passed, and the server has moved on to the next one", listOf(1), out.map { it.id })
        assertTrue("said once", alerts.update(listOf(series(1, "2026-10-27")), day("2026-10-22")).isEmpty())
        assertEquals("and the next one is awaited", day("2026-10-27"), alerts.expected[1])
    }

    @Test fun testSaysNothingOfASeriesNoLongerFollowed() {
        val alerts = EpisodeAlerts()
        alerts.update(listOf(series(1, "2026-10-20")), day("2026-10-16"))
        assertTrue(alerts.update(emptyList(), day("2026-10-21")).isEmpty())
        assertTrue(alerts.expected.isEmpty())
    }

    @Test fun testSurvivesAPropertyList() {
        val alerts = EpisodeAlerts()
        alerts.update(listOf(series(4, "2026-11-02")), day("2026-10-16"))
        assertEquals(alerts, EpisodeAlerts(alerts.plist))
    }
}
