package app.nama.androidtv.data

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Ed25519 signature checking (RFC 8032), only the verifying half. Android has no Ed25519 of its
 * own before 13, and the app needs to check one signature per update, so a plain, slow BigInteger
 * version is enough and keeps out a cryptography library. Tested against the RFC's vectors.
 */
object Ed25519 {
  // BigInteger.TWO arrived only in Android 13.
  private val TWO = BigInteger.valueOf(2)
  private val p = BigInteger.ONE.shiftLeft(255) - BigInteger.valueOf(19)
  private val l = BigInteger.ONE.shiftLeft(252) + BigInteger("27742317777372353535851937790883648493")
  private val d = BigInteger.valueOf(-121665) * inv(BigInteger.valueOf(121666)) % p
  private val sqrtM1 = TWO.modPow((p - BigInteger.ONE) / BigInteger.valueOf(4), p)
  private val base: Point = run {
    val y = BigInteger.valueOf(4) * inv(BigInteger.valueOf(5)) % p
    Point(recoverX(y, 0)!!, y, BigInteger.ONE, recoverX(y, 0)!! * y % p)
  }

  /** Extended coordinates: x = X/Z, y = Y/Z, x*y = T/Z. */
  private class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

  private fun inv(a: BigInteger) = a.modPow(p - TWO, p)

  private fun add(a: Point, b: Point): Point {
    val aa = (a.y - a.x) * (b.y - b.x) % p
    val bb = (a.y + a.x) * (b.y + b.x) % p
    val cc = TWO * a.t * b.t % p * d % p
    val dd = TWO * a.z * b.z % p
    val e = bb - aa
    val f = dd - cc
    val g = dd + cc
    val h = bb + aa
    return Point(e * f % p, g * h % p, f * g % p, e * h % p)
  }

  private fun multiply(s: BigInteger, point: Point): Point {
    var q = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    var a = point
    var n = s
    while (n.signum() > 0) {
      if (n.testBit(0)) q = add(q, a)
      a = add(a, a)
      n = n.shiftRight(1)
    }
    return q
  }

  private fun same(a: Point, b: Point) =
      (a.x * b.z - b.x * a.z).mod(p).signum() == 0 && (a.y * b.z - b.y * a.z).mod(p).signum() == 0

  private fun recoverX(y: BigInteger, sign: Int): BigInteger? {
    if (y >= p) return null
    val x2 = (y * y - BigInteger.ONE) * inv(d * y * y + BigInteger.ONE) % p
    if (x2.mod(p).signum() == 0) return if (sign == 1) null else BigInteger.ZERO
    var x = x2.modPow((p + BigInteger.valueOf(3)) / BigInteger.valueOf(8), p)
    if ((x * x - x2).mod(p).signum() != 0) x = x * sqrtM1 % p
    if ((x * x - x2).mod(p).signum() != 0) return null
    if (x.testBit(0) != (sign == 1)) x = p - x
    return x.mod(p)
  }

  private fun little(bytes: ByteArray) = BigInteger(1, bytes.reversedArray())

  private fun decompress(bytes: ByteArray): Point? {
    if (bytes.size != 32) return null
    val sign = (bytes[31].toInt() shr 7) and 1
    val yBytes = bytes.copyOf().also { it[31] = (it[31].toInt() and 0x7f).toByte() }
    val y = little(yBytes)
    val x = recoverX(y, sign) ?: return null
    return Point(x, y, BigInteger.ONE, x * y % p)
  }

  /** True when [signature] (64 bytes) is [publicKey]'s (32 bytes) over [message]. */
  fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
      verify(publicKey, message.inputStream(), signature)

  /** The same over a stream, read once to its end: an APK is too large to hold in memory twice. */
  fun verify(publicKey: ByteArray, message: java.io.InputStream, signature: ByteArray): Boolean {
    if (publicKey.size != 32 || signature.size != 64) return false
    val a = decompress(publicKey) ?: return false
    val r = decompress(signature.copyOfRange(0, 32)) ?: return false
    val s = little(signature.copyOfRange(32, 64))
    if (s >= l) return false
    val sha = MessageDigest.getInstance("SHA-512")
    sha.update(signature, 0, 32)
    sha.update(publicKey)
    val buffer = ByteArray(64 * 1024)
    while (true) {
      val n = message.read(buffer)
      if (n < 0) break
      sha.update(buffer, 0, n)
    }
    val h = little(sha.digest()).mod(l)
    return same(multiply(s, base), add(r, multiply(h, a)))
  }
}
