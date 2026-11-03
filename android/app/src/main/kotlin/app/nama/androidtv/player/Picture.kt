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


/**
 * The light around the picture, as an Ambilight's on the wall behind a TV (the owner, with photos
 * of one): each edge's colours, a few zones along it, the zones read from a strip just inside the
 * picture's edge. Drawn where the bars are, each zone's colour shines out from the picture and
 * fades to black; until 1.1.5 the whole frame was spread there blurred, and the moving shapes in
 * it drew the eye away from the film. Colours as ARGB, from the frame's start of each edge.
 */
class EdgeLight(val top: IntArray, val bottom: IntArray, val left: IntArray, val right: IntArray) {
  /** Within a step or two of [other] in every channel of every zone: nothing left to show changing. */
  fun near(other: EdgeLight): Boolean {
    fun close(a: IntArray, b: IntArray) = a.size == b.size && a.indices.all { i ->
      listOf(16, 8, 0).all { sh -> kotlin.math.abs(((a[i] shr sh) and 0xFF) - ((b[i] shr sh) and 0xFF)) <= 2 }
    }
    return close(top, other.top) && close(bottom, other.bottom) && close(left, other.left) && close(right, other.right)
  }

  /** [share] of the way from these colours to [next]'s, zone by zone; [next] where the zones differ. */
  fun toward(next: EdgeLight, share: Float): EdgeLight {
    fun mix(a: IntArray, b: IntArray): IntArray {
      if (a.size != b.size) return b
      return IntArray(a.size) { i ->
        fun ch(shift: Int) = (((a[i] shr shift) and 0xFF) + (((b[i] shr shift) and 0xFF) - ((a[i] shr shift) and 0xFF)) * share).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
      }
    }
    return EdgeLight(mix(top, next.top), mix(bottom, next.bottom), mix(left, next.left), mix(right, next.right))
  }

  companion object {
    /** Zones along the top and bottom; the sides have fewer, by the picture's shape. */
    const val ZONES = 12

    /**
     * Reads [pixels] (ARGB, [width] by [height]) inside the part [trim] keeps. Each zone is the
     * mean of a strip a tenth of the picture deep, made a little more vivid, as light on a wall is.
     */
    fun of(pixels: IntArray, width: Int, height: Int, trim: Trim = Trim()): EdgeLight? {
      if (width < 8 || height < 8 || pixels.size < width * height) return null
      val x0 = (trim.left * width).toInt()
      val y0 = (trim.top * height).toInt()
      val x1 = width - (trim.right * width).toInt()
      val y1 = height - (trim.bottom * height).toInt()
      val w = x1 - x0
      val h = y1 - y0
      if (w < 8 || h < 8) return null
      val deepY = (h / 10).coerceAtLeast(1)
      val deepX = (w / 10).coerceAtLeast(1)
      val side = (ZONES * h / w).coerceIn(3, ZONES)
      fun mean(ax: Int, ay: Int, bx: Int, by: Int): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (y in ay until by) for (x in ax until bx) {
          val p = pixels[y * width + x]
          r += (p shr 16) and 0xFF
          g += (p shr 8) and 0xFF
          b += p and 0xFF
          n++
        }
        if (n == 0L) return 0xFF000000.toInt()
        return vivid((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
      }
      fun along(n: Int, from: Int, span: Int, zone: (Int, Int) -> Int) = IntArray(n) { i -> zone(from + span * i / n, from + span * (i + 1) / n) }
      return EdgeLight(
          top = along(ZONES, x0, w) { a, b -> mean(a, y0, b, y0 + deepY) },
          bottom = along(ZONES, x0, w) { a, b -> mean(a, y1 - deepY, b, y1) },
          left = along(side, y0, h) { a, b -> mean(x0, a, x0 + deepX, b) },
          right = along(side, y0, h) { a, b -> mean(x1 - deepX, a, x1, b) },
      )
    }

    /**
     * Saturation a third higher and a third brighter: a mean of a strip is greyer and darker than
     * what the eye takes from it, and the light on a dark scene was hardly there (the owner).
     */
    internal fun vivid(r: Int, g: Int, b: Int): Int {
      val grey = (r + g + b) / 3f
      fun push(c: Int) = ((grey + (c - grey) * 1.3f) * 1.3f).toInt().coerceIn(0, 255)
      return (0xFF shl 24) or (push(r) shl 16) or (push(g) shl 8) or push(b)
    }
  }
}
