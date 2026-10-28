package app.nama.androidtv

import app.nama.androidtv.player.Direct
import app.nama.core.DownloadFile
import app.nama.core.DownloadOptions
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectTest {
  private fun file(name: String, quality: String, size: String, tags: List<String> = emptyList(), season: Int? = null, episode: String? = null) =
      DownloadFile(name, URI("https://example.com/$name"), quality, size, null, tags, season, episode)

  @Test fun aFilmTakesTheSharpestFileWhoseSoundATvPlays() {
    val options = DownloadOptions(files = listOf(
        file("a.1080p.mkv", "1080p BluRay", "2.1 GB"),
        file("a.2160p.TrueHD.mkv", "2160p REMUX", "40 GB"),
        file("a.2160p.mkv", "2160p WEB-DL HDR", "12 GB"),
        file("a.720p.mkv", "720p", "900 MB"),
    ))
    assertEquals("a.2160p.mkv", Direct.pick(options, isSeries = false, season = null, episode = null)?.fileName)
  }

  @Test fun anEpisodeTakesItsOwnFile() {
    val options = DownloadOptions(packs = listOf(
        DownloadOptions.Pack("p1", 1, "1080p", "1 GB", null, listOf(file("s1e1.mkv", "1080p", "1 GB", season = 1, episode = "1"), file("s1e2.mkv", "1080p", "1 GB", season = 1, episode = "2"))),
        DownloadOptions.Pack("p2", 2, "1080p", "1 GB", null, listOf(file("s2e2.mkv", "1080p", "1 GB", season = 2, episode = "2"))),
    ))
    assertEquals("s1e2.mkv", Direct.pick(options, isSeries = true, season = 1, episode = "02")?.fileName)
    assertNull(Direct.pick(options, isSeries = true, season = 3, episode = "1"))
  }

  @Test fun sizesAndLabels() {
    assertEquals(2457.6, Direct.megabytes("2.4 GB"), 0.01)
    assertEquals(781.0, Direct.megabytes("781 MB"), 0.01)
    assertEquals("2160p · HDR · x265", Direct.label(file("b.mkv", "2160p HDR x265", "10 GB")))
  }
}
