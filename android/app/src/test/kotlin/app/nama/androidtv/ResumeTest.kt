package app.nama.androidtv

import app.nama.androidtv.data.Resume
import app.nama.core.ContinueWatching
import app.nama.core.StreamItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeTest {
  private val items = (1..4).map { StreamItem(fileID = "f$it", season = 1, episodeRaw = "$it", durationMinutes = 40) }

  @Test
  fun theFurtherPointWins() {
    // The server rounds down to thirty seconds; the TV knows the exact second.
    val p = Resume.start(items, 1, ContinueWatching(fileID = "f2", time = 600.0, duration = 2400.0), Resume.Kept("f2", 623.0), pickedByUser = false)
    assertEquals(1, p.index)
    assertEquals(623.0, p.seconds!!, 0.0)
  }

  @Test
  fun aLaterEpisodeOnThisTVWins() {
    // Episode 3 was left part-way here; the server still says episode 2.
    val p = Resume.start(items, 1, ContinueWatching(fileID = "f2", time = 1800.0, duration = 2400.0), Resume.Kept("f3", 120.0), pickedByUser = false)
    assertEquals(2, p.index)
    assertEquals(120.0, p.seconds!!, 0.0)
  }

  @Test
  fun theServerWinsWhenItIsFurther() {
    // Watched on another device since.
    val p = Resume.start(items, 0, ContinueWatching(fileID = "f4", time = 300.0, duration = 2400.0), Resume.Kept("f2", 900.0), pickedByUser = false)
    assertEquals(3, p.index)
  }

  @Test
  fun theCreditsGoOnToTheNext() {
    // Left in episode 1's credits: episode 2 from its start, not episode 1 again.
    val p = Resume.start(items, 0, null, Resume.Kept("f1", 2390.0), pickedByUser = false)
    assertEquals(1, p.index)
    assertNull(p.seconds)
  }

  @Test
  fun theLastOnesCreditsStartOver() {
    val p = Resume.start(items, 0, null, Resume.Kept("f4", 2390.0), pickedByUser = false)
    assertEquals(3, p.index)
    assertNull(p.seconds)
  }

  @Test
  fun aChosenEpisodeStartsThere() {
    val p = Resume.start(items, 3, ContinueWatching(fileID = "f2", time = 600.0, duration = 2400.0), Resume.Kept("f4", 400.0), pickedByUser = true)
    assertEquals(3, p.index)
    assertEquals(400.0, p.seconds!!, 0.0)
  }

  @Test
  fun nothingSavedStartsAtTheBeginning() {
    val p = Resume.start(items, 0, null, null, pickedByUser = false)
    assertEquals(0, p.index)
    assertNull(p.seconds)
  }
}
