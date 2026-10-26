package app.nama.androidtv

import app.nama.androidtv.data.KeyRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyRequestTest {
  private val token = "abcdefghjk"

  private fun read(text: String) = KeyRequest.read(text.byteInputStream(Charsets.ISO_8859_1), token)

  private fun post(body: String, path: String = "/$token", length: Int = body.toByteArray().size) =
      read("POST $path HTTP/1.1\r\nHost: tv\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: $length\r\n\r\n$body")

  @Test
  fun theFormIsServedOnlyAtTheToken() {
    assertEquals(200, read("GET /$token HTTP/1.1\r\nHost: tv\r\n\r\n").status)
    assertEquals(404, read("GET / HTTP/1.1\r\nHost: tv\r\n\r\n").status)
    assertEquals(404, read("GET /abcdefghjj HTTP/1.1\r\nHost: tv\r\n\r\n").status)
  }

  @Test
  fun aKeyComesBackTrimmedAndDecoded() {
    val a = post("key=%20abc123XYZ_-.%3D%20")
    assertEquals(200, a.status)
    assertEquals("abc123XYZ_-.=", a.key)
  }

  @Test
  fun whatIsNotAKeyIsRefused() {
    assertNull(post("key=two+words").key)
    assertEquals(400, post("key=").status)
    assertEquals(400, post("key=%D8%B3%D9%84%D8%A7%D9%85").status)
  }

  @Test
  fun otherRequestsAreRefused() {
    assertEquals(405, read("PUT /$token HTTP/1.1\r\nHost: tv\r\n\r\n").status)
    assertEquals(411, read("POST /$token HTTP/1.1\r\nHost: tv\r\n\r\nkey=abc").status)
    assertEquals(413, post("key=abc", length = 9000).status)
  }
}
