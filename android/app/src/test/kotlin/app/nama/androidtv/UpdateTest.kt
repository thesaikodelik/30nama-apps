package app.nama.androidtv

import app.nama.androidtv.data.AndroidRelease
import app.nama.androidtv.data.Ed25519
import app.nama.androidtv.data.PendingUpdate
import app.nama.androidtv.data.UpdateSignature
import app.nama.core.Json
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
  private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

  private fun release(tag: String, assets: List<String> = listOf("30nama-androidtv.apk", "30nama-androidtv.apk.sig"), draft: Boolean = false) =
      """{"tag_name":"$tag","draft":$draft,"prerelease":false,"html_url":"https://example.com/releases/$tag",
        "assets":[${assets.joinToString(",") { """{"name":"$it","browser_download_url":"https://example.com/$tag/$it"}""" }}]}"""

  @Test
  fun picksTheNewestCompleteAndroidRelease() {
    val feed = Json.parse("[" + listOf(
        release("android-v1.10.0", listOf("30nama-androidtv.apk")),
        release("android-v1.9.0"),
        release("android-v2.0.0", draft = true),
        release("win-v3.0.0", listOf("x.apk", "x.apk.sig")),
        release("android-v1.2.0"),
    ).joinToString(",") + "]")
    val newest = AndroidRelease.newest(feed, "1.1.0")!!
    assertEquals("1.10 has no signature yet, 2.0 is a draft, 3.0 is not for Android", "1.9.0", newest.version)
    assertTrue(newest.apk.endsWith("/30nama-androidtv.apk"))
    assertTrue(newest.signature.endsWith("/30nama-androidtv.apk.sig"))
    assertNull(AndroidRelease.newest(feed, "1.9.0"))
  }

  @Test
  fun versionsCompareByNumber() {
    assertTrue(AndroidRelease.isNewer("1.10.0", "1.9.2"))
    assertTrue(AndroidRelease.isNewer("1.2", "1.1.9"))
    assertFalse(AndroidRelease.isNewer("1.1.0", "1.1.0"))
    assertFalse(AndroidRelease.isNewer("1.1", "1.1.0"))
  }

  /** RFC 8032, section 7.1, tests 1 to 3. */
  @Test
  fun rfcVectors() {
    val cases = listOf(
        Triple("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", "",
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"),
        Triple("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c", "72",
            "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"),
        Triple("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025", "af82",
            "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a"),
    )
    for ((key, message, signature) in cases) {
      assertTrue(Ed25519.verify(hex(key), hex(message), hex(signature)))
      val wrong = hex(signature).also { it[10] = (it[10] + 1).toByte() }
      assertFalse(Ed25519.verify(hex(key), hex(message), wrong))
      assertFalse(Ed25519.verify(hex(key), hex(message + "00"), hex(signature)))
    }
  }

  /** A signature made the way the signing tool makes one: the JDK's own Ed25519. */
  @Test
  fun signature() {
    val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val publicKey = Base64.getEncoder().encodeToString(pair.public.encoded.takeLast(32).toByteArray())
    val data = "an apk".toByteArray()
    val signature = Signature.getInstance("Ed25519").run {
      initSign(pair.private)
      update(data)
      Base64.getEncoder().encodeToString(sign()) + "\n"
    }
    assertTrue(UpdateSignature.isValid(signature, data.inputStream(), publicKey))
    assertFalse("a changed file", UpdateSignature.isValid(signature, "an apl".toByteArray().inputStream(), publicKey))
    assertFalse("signed with another key than the app's", UpdateSignature.isValid(signature, data.inputStream()))
    assertFalse(UpdateSignature.isValid("not base64", data.inputStream(), publicKey))
  }

  @Test
  fun pendingUpdate() {
    val pending = PendingUpdate("1.2.4", "https://example.com/r/android-v1.2.4")
    val read = PendingUpdate.parse(pending.serialize())!!
    assertEquals(pending, read)
    assertTrue(read.arrived("1.2.4"))
    assertTrue("a later version came in the meantime", read.arrived("1.3.0"))
    assertFalse("still the old app: the update did not arrive", read.arrived("1.2.3"))
    assertNull(PendingUpdate.parse("not json"))
    assertNull(PendingUpdate.parse("{}"))
    assertNull(PendingUpdate.parse(null))
  }
}
