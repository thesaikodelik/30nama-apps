package app.nama.androidtv.data

/**
 * The liveliest colour in a picture, as the Mac's `SceneTint.lively` (Shared/SceneTint.swift): the
 * hue that most of its bright, saturated parts share, made as vivid as the app's other lights. Pure,
 * on the pixels of the picture drawn at 32×18, so tested on the computer.
 */
object Lively {
  /** Hue (0..1), saturation and brightness; null for a picture that is all greys and shadow. */
  data class Hsb(val hue: Float, val saturation: Float, val brightness: Float)

  fun of(argb: IntArray): Hsb? {
    // Twelve bins of hue, each weighted by how strong and bright its pixels are.
    val weight = DoubleArray(12)
    val sr = DoubleArray(12)
    val sg = DoubleArray(12)
    val sb = DoubleArray(12)
    for (p in argb) {
      val r = (p shr 16 and 0xFF) / 255.0
      val g = (p shr 8 and 0xFF) / 255.0
      val b = (p and 0xFF) / 255.0
      val high = maxOf(r, g, b)
      val span = high - minOf(r, g, b)
      if (high <= 0.25 || span / high <= 0.3) continue
      val bin = minOf(11, (hue(r, g, b, high, span) * 12).toInt())
      val strength = (span / high) * high
      weight[bin] += strength
      sr[bin] += r * strength
      sg[bin] += g * strength
      sb[bin] += b * strength
    }
    val best = weight.indices.maxByOrNull { weight[it] } ?: return null
    if (weight[best] <= 1.2) return null
    val r = sr[best] / weight[best]
    val g = sg[best] / weight[best]
    val b = sb[best] / weight[best]
    val high = maxOf(r, g, b)
    val span = high - minOf(r, g, b)
    val saturation = if (high > 0) span / high else 0.0
    return Hsb(
        hue(r, g, b, high, maxOf(span, 0.0001)).toFloat(),
        (saturation * 1.4).coerceIn(0.6, 0.95).toFloat(),
        (high * 1.5).coerceIn(0.85, 1.0).toFloat(),
    )
  }

  private fun hue(r: Double, g: Double, b: Double, high: Double, span: Double): Double {
    var h = when (high) {
      r -> ((g - b) / span) % 6
      g -> (b - r) / span + 2
      else -> (r - g) / span + 4
    } / 6
    if (h < 0) h += 1
    return h
  }
}
