package app.nama.androidtv.player

import app.nama.core.Quality
import app.nama.core.StreamItem

/**
 * The quality a title starts in, as Settings › Quality says: core's choice (the highest fixed one,
 * or the adaptive stream), or for a slow line the highest no taller than [cap] lines (the aliir74
 * app's 1080p, 720p and 480p). "Best" is also no taller than [device], what this screen shows and
 * its decoder reads (DeviceCeiling): a 4K file on a 1080p TV is four times the line for nothing
 * (the owner); the adaptive stream is held to it by the player instead. With none that low, the
 * lowest there is; with no fixed one at all, the adaptive stream, which is better than nothing.
 */
object StartQuality {
  fun pick(item: StreamItem, adaptive: Boolean, cap: Int, device: Int = 0): Quality? {
    val fixed = item.qualities.filter { !it.isAuto && it.height > 0 }
    if (fixed.isEmpty()) return item.quality(adaptive)
    // The adaptive stream is held down by the player; a fixed file, chosen also where adaptive was
    // asked for but the title has none, is held here.
    val adaptiveThere = adaptive && item.qualities.any { it.isAuto }
    val limit = if (cap > 0) cap else if (!adaptiveThere) device else 0
    if (limit <= 0) return item.quality(adaptive)
    val most = if (device > 0) minOf(limit, device) else limit
    return fixed.filter { it.height <= most }.maxByOrNull { it.height } ?: fixed.minBy { it.height }
  }
}
