package app.nama.androidtv.player

import app.nama.core.DownloadFile
import app.nama.core.DownloadOptions
import app.nama.core.Episode

/**
 * The "Max" quality, as the Tizen app has it: the download file itself (MKV or MP4, often 4K and
 * HDR) played straight from 30nama, for the item playing. Files whose sound a TV seldom decodes
 * (DTS, TrueHD) are passed over; the best of the rest by its lines, then its size.
 */
object Direct {
  /** The file for a film, or for [season] and [episode] of a series; none when there is no fit. */
  fun pick(options: DownloadOptions, isSeries: Boolean, season: Int?, episode: String?): DownloadFile? {
    val wanted = Episode.number(episode)
    val files = if (!isSeries) options.files else options.packs.filter { it.season == (season ?: 1) }.flatMap { it.files }
        .filter { Episode.number(it.episode) == wanted }
    return files.filter { playable(it) }.maxWithOrNull(compareBy<DownloadFile> { lines(it) }.thenBy { megabytes(it.size) })
  }

  /** What a file says about itself: its quality, encoder, tags and name, in lower case. */
  private fun words(f: DownloadFile) = (listOf(f.quality, f.encoder ?: "", f.fileName) + f.tags).joinToString(" ").lowercase()

  /** Sound a TV's decoders seldom have; Media3 would play such a file silent. */
  fun playable(f: DownloadFile): Boolean {
    val w = words(f)
    return "dts" !in w && "truehd" !in w && "true-hd" !in w
  }

  /** The picture's lines, from what the file says: 2160 for 4K. */
  fun lines(f: DownloadFile): Int {
    val w = words(f)
    return when {
      "2160" in w || "4k" in w || "uhd" in w -> 2160
      "1440" in w -> 1440
      "1080" in w -> 1080
      "720" in w -> 720
      "576" in w -> 576
      "480" in w -> 480
      else -> 0
    }
  }

  /** "781 MB", "2.4 GB" as megabytes; 0 when not said. */
  fun megabytes(size: String?): Double {
    val m = Regex("([0-9.]+)\\s*([GMK])B", RegexOption.IGNORE_CASE).find(size ?: return 0.0) ?: return 0.0
    val n = m.groupValues[1].toDoubleOrNull() ?: return 0.0
    return when (m.groupValues[2].uppercase()) {
      "G" -> n * 1024
      "K" -> n / 1024
      else -> n
    }
  }

  /** A short name for the panel: "2160p · HDR · x265". */
  fun label(f: DownloadFile): String {
    val w = words(f)
    val parts = mutableListOf<String>()
    lines(f).takeIf { it > 0 }?.let { parts.add("${it}p") }
    when {
      "dolby vision" in w || " dv " in " $w " || "dovi" in w -> parts.add("DV")
      "hdr" in w -> parts.add("HDR")
    }
    when {
      "x265" in w || "hevc" in w || "h265" in w -> parts.add("x265")
      "x264" in w || "h264" in w || "avc" in w -> parts.add("x264")
    }
    return parts.joinToString(" · ")
  }
}
