package app.nama.androidtv.player

import app.nama.core.Quality
import app.nama.core.StreamItem

/**
 * The quality a title starts in, as Settings › Quality says: core's choice (the highest fixed one,
 * or the adaptive stream), or for a slow line the highest no taller than [cap] lines (the aliir74
 * app's 1080p, 720p and 480p). With none that low, the lowest there is; with no fixed one at all,
 * the adaptive stream, which is better than nothing.
 */
object StartQuality {
  fun pick(item: StreamItem, adaptive: Boolean, cap: Int): Quality? {
    val fixed = item.qualities.filter { !it.isAuto && it.height > 0 }
    if (cap <= 0 || fixed.isEmpty()) return item.quality(adaptive)
    return fixed.filter { it.height <= cap }.maxByOrNull { it.height } ?: fixed.minBy { it.height }
  }
}
