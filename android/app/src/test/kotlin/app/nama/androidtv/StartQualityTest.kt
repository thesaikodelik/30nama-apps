package app.nama.androidtv

import app.nama.androidtv.player.StartQuality
import app.nama.core.Quality
import app.nama.core.StreamItem
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class StartQualityTest {
  private fun q(label: String) = Quality(label, URI("https://example.com/$label.m3u8"), height = label.removeSuffix("p").toIntOrNull() ?: 0, isAuto = label == "Auto")
  private fun item(vararg labels: String) = StreamItem(fileID = "f", season = 1, qualities = labels.map { q(it) })

  @Test fun noCapIsCoresChoice() {
    val i = item("Auto", "480p", "1080p", "720p")
    assertEquals("1080p", StartQuality.pick(i, adaptive = false, cap = 0)?.label)
    assertEquals("Auto", StartQuality.pick(i, adaptive = true, cap = 0)?.label)
  }

  @Test fun aCapTakesTheHighestUnderIt() {
    val i = item("Auto", "480p", "1080p", "720p", "2160p")
    assertEquals("1080p", StartQuality.pick(i, adaptive = false, cap = 1080)?.label)
    assertEquals("720p", StartQuality.pick(i, adaptive = true, cap = 720)?.label)
    assertEquals("480p", StartQuality.pick(i, adaptive = false, cap = 480)?.label)
  }

  @Test fun nothingThatLowTakesTheLowest() {
    assertEquals("720p", StartQuality.pick(item("1080p", "720p"), adaptive = false, cap = 480)?.label)
  }

  @Test fun onlyTheAdaptiveStreamStaysIt() {
    assertEquals("Auto", StartQuality.pick(item("Auto"), adaptive = false, cap = 480)?.label)
  }
}
