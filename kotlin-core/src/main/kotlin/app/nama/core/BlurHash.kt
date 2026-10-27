package app.nama.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/** An average colour, each channel 0...1 in sRGB. */
data class RGB(val red: Double, val green: Double, val blue: Double)

/**
 * Decoder for BlurHash strings (https://blurha.sh), which the API sends as `image.poster.preview`.
 * Produces a tiny RGBA bitmap that is stretched as a placeholder until the real poster arrives.
 */
object BlurHash {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"

    private fun decode83(s: String): Int? {
        var value = 0
        for (c in s) {
            val d = ALPHABET.indexOf(c)
            if (d < 0) return null
            value = value * 83 + d
        }
        return value
    }

    private fun linear(srgb: Int): Double {
        val v = srgb / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun srgb(linear: Double): Int {
        val v = linear.coerceIn(0.0, 1.0)
        val s = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1 / 2.4) - 0.055
        return (s * 255).roundToInt().coerceIn(0, 255)
    }

    private fun signedPow(v: Double, e: Double): Double = if (v < 0) -(-v).pow(e) else v.pow(e)

    /** The image's average colour (the hash's DC component). */
    fun average(hash: String): RGB? {
        if (hash.length < 6) return null
        val v = decode83(hash.substring(2, 6)) ?: return null
        return RGB((v shr 16) / 255.0, ((v shr 8) and 255) / 255.0, (v and 255) / 255.0)
    }

    /** RGBA8 pixels, row-major, `width * height * 4` bytes. Nil for a malformed hash. */
    fun pixels(hash: String, width: Int, height: Int, punch: Double = 1.0): ByteArray? {
        if (hash.length < 6 || width <= 0 || height <= 0) return null
        val sizeFlag = decode83(hash.substring(0, 1)) ?: return null
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1
        if (hash.length != 4 + 2 * numX * numY) return null
        val quantisedMax = decode83(hash.substring(1, 2)) ?: return null
        val maxValue = (quantisedMax + 1) / 166.0 * punch

        val colors = Array(numX * numY) { DoubleArray(3) }
        for (i in 0 until numX * numY) {
            if (i == 0) {
                val v = decode83(hash.substring(2, 6)) ?: return null
                colors[0] = doubleArrayOf(linear(v shr 16), linear((v shr 8) and 255), linear(v and 255))
            } else {
                val v = decode83(hash.substring(4 + i * 2, 6 + i * 2)) ?: return null
                fun ac(q: Int) = signedPow((q - 9) / 9.0, 2.0) * maxValue
                colors[i] = doubleArrayOf(ac(v / (19 * 19)), ac((v / 19) % 19), ac(v % 19))
            }
        }

        val out = ByteArray(width * height * 4) { -1 }
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (j in 0 until numY) {
                    val by = cos(PI * y * j / height)
                    for (i in 0 until numX) {
                        val basis = cos(PI * x * i / width) * by
                        val c = colors[i + j * numX]
                        r += c[0] * basis; g += c[1] * basis; b += c[2] * basis
                    }
                }
                val o = (y * width + x) * 4
                out[o] = srgb(r).toByte(); out[o + 1] = srgb(g).toByte(); out[o + 2] = srgb(b).toByte()
            }
        }
        return out
    }
}
