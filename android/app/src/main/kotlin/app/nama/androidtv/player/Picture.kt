package app.nama.androidtv.player

import kotlin.math.abs

/**
 * The shape to show a film in. Some TVs' decoders (reported on a Hisense) give a 2.39:1 film as a
 * 16:9 picture with the black bars inside it, and the film is then stretched to fill the screen
 * top to bottom. Where the decoder says 16:9 and the stream itself (its HLS playlist) says
 * otherwise, the stream is believed; otherwise the decoder, since a playlist that calls everything
 * 16:9 is the likelier mistake the other way. Learnt from 30namaTV.
 *
 * @return width over height, or null when neither knows yet.
 */
fun filmShape(decoder: Pair<Float, Float>?, stream: Pair<Float, Float>?): Float? {
  fun ratio(s: Pair<Float, Float>?) = s?.takeIf { it.first > 0 && it.second > 0 }?.let { it.first / it.second }
  val d = ratio(decoder)
  val s = ratio(stream)
  fun sixteenNine(r: Float) = abs(r - 16f / 9f) <= 16f / 9f * 0.02f
  return if (d != null && s != null && sixteenNine(d) && !sixteenNine(s)) s else d ?: s
}

/** How much of the frame, each side, is black bars burned into the file, 0 to 1. */
data class Trim(val top: Float = 0f, val bottom: Float = 0f, val left: Float = 0f, val right: Float = 0f) {
  /** Worth cutting: more than a sliver, and not most of the picture (a dark scene misread). */
  val worthCutting: Boolean get() = (top + bottom > 0.04f || left + right > 0.04f) && top + bottom < 0.6f && left + right < 0.6f
}

/**
 * What the frames seen so far say about the bars, as the Mac's `Watch`: the fewest black rows and
 * columns at each edge of any frame bright enough to tell. A dark scene proves nothing; one bright
 * frame with picture at an edge is enough to keep that edge.
 */
class BarWatch {
  private var top = Int.MAX_VALUE
  private var bottom = Int.MAX_VALUE
  private var left = Int.MAX_VALUE
  private var right = Int.MAX_VALUE
  private var w = 0
  private var h = 0
  var frames = 0
    private set

  /** One frame's pixels (ARGB, row by row). */
  fun see(pixels: IntArray, width: Int, height: Int) {
    if (width < 8 || height < 8 || pixels.size < width * height) return
    fun luma(p: Int) = (((p shr 16) and 0xFF) * 3 + ((p shr 8) and 0xFF) * 6 + (p and 0xFF)) / 10
    // Too dark to tell bars from picture.
    var sum = 0L
    for (p in pixels) sum += luma(p)
    if (sum / pixels.size < 28) return
    fun rowDark(y: Int): Boolean {
      for (x in 0 until width) if (luma(pixels[y * width + x]) > DARK) return false
      return true
    }
    fun columnDark(x: Int): Boolean {
      for (y in 0 until height) if (luma(pixels[y * width + x]) > DARK) return false
      return true
    }
    var t = 0
    while (t < height / 2 && rowDark(t)) t++
    var b = 0
    while (b < height / 2 && rowDark(height - 1 - b)) b++
    var l = 0
    while (l < width / 2 && columnDark(l)) l++
    var r = 0
    while (r < width / 2 && columnDark(width - 1 - r)) r++
    w = width
    h = height
    top = minOf(top, t)
    bottom = minOf(bottom, b)
    left = minOf(left, l)
    right = minOf(right, r)
    frames++
  }

  /** What to cut, once enough bright frames agree; until then nothing. */
  fun trim(): Trim {
    if (frames < 3 || w == 0 || h == 0) return Trim()
    return Trim(top.toFloat() / h, bottom.toFloat() / h, left.toFloat() / w, right.toFloat() / w)
  }

  companion object {
    /** A pixel at most this bright (of 255) is black for a bar: compressed black is rarely 0. */
    private const val DARK = 22
  }
}

