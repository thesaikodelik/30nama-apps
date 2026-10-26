package app.nama.androidtv

import app.nama.androidtv.data.NewEpisodes
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodesTest {
  private val monday = Instant.parse("2026-10-26T00:00:00Z")

  @Test
  fun anEpisodeIsNewOnceItsDayAndHalfAnotherHavePassed() {
    val n = NewEpisodes()
    assertTrue(n.look(monday.minus(Duration.ofDays(2)), mapOf(7 to monday), mapOf(7 to 100.0)).isEmpty())
    // On the day itself, nothing yet.
    assertTrue(n.look(monday.plus(Duration.ofHours(20)), mapOf(7 to monday), mapOf(7 to 100.0)).isEmpty())
    assertEquals(setOf(7), n.look(monday.plus(Duration.ofHours(37)), mapOf(7 to null), mapOf(7 to 100.0)))
  }

  @Test
  fun theNewsGoesWhenTheViewerGoesBack() {
    val n = NewEpisodes()
    n.look(monday.minus(Duration.ofDays(1)), mapOf(7 to monday), mapOf(7 to 100.0))
    assertEquals(setOf(7), n.look(monday.plus(Duration.ofDays(2)), mapOf(7 to null), mapOf(7 to 100.0)))
    assertTrue(n.look(monday.plus(Duration.ofDays(3)), mapOf(7 to null), mapOf(7 to 400.0)).isEmpty())
  }

  @Test
  fun theNewsGoesAfterAWeekAndSurvivesARestart() {
    val n = NewEpisodes()
    n.look(monday.minus(Duration.ofDays(1)), mapOf(7 to monday), emptyMap())
    n.look(monday.plus(Duration.ofDays(2)), emptyMap(), emptyMap())
    val again = NewEpisodes(n.json())
    assertEquals(setOf(7), again.look(monday.plus(Duration.ofDays(4)), emptyMap(), emptyMap()))
    assertTrue(again.look(monday.plus(Duration.ofDays(10)), emptyMap(), emptyMap()).isEmpty())
  }
}
