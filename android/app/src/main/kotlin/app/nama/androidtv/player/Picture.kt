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
