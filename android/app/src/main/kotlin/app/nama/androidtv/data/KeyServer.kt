package app.nama.androidtv.data

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * A key typed on the phone instead of with the remote: while the key card is open, the TV serves
 * one small page on the home network; the QR code on the card opens it on a phone on the same
 * Wi-Fi, and the key pasted there comes back to the card. Learnt from 30namaTV:
 *  - the port is whatever the system gives, on the TV's own home-network address (never a VPN's);
 *  - the page is only at a path of ten random letters, so nobody else on the network can guess it;
 *  - it lives only while the card is open;
 *  - the key is never logged.
 * Plain HTTP on the home network: acceptable for a key the user made themselves and can revoke.
 */
class KeyServer private constructor(private val server: ServerSocket, val token: String, private val page: (sent: Boolean, error: Boolean) -> String, private val onKey: (String) -> Unit) {
  val address: String get() = "http://${server.inetAddress.hostAddress}:${server.localPort}/$token"

  private val accepting = thread(isDaemon = true, name = "nama-keys") {
    while (!server.isClosed) {
      val client = runCatching { server.accept() }.getOrNull() ?: break
      thread(isDaemon = true) { serve(client) }
    }
  }

  private fun serve(client: Socket) {
    client.use { s ->
      s.soTimeout = 10_000
      val answer = runCatching { KeyRequest.read(BufferedInputStream(s.getInputStream()), token) }.getOrElse { KeyRequest.Answer(400) }
      val html = when {
        answer.key != null -> {
          onKey(answer.key)
          page(true, false)
        }
        answer.status == 200 -> page(false, false)
        answer.status == 400 -> page(false, true)
        else -> null
      }
      val body = (html ?: "").toByteArray()
      val reason = mapOf(200 to "OK", 400 to "Bad Request", 404 to "Not Found", 405 to "Method Not Allowed", 411 to "Length Required", 413 to "Payload Too Large")[answer.status] ?: "Error"
      val status = if (answer.status == 400 && html != null) 200 else answer.status
      val head = "HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n"
      runCatching {
        s.getOutputStream().apply {
          write(head.toByteArray())
          write(body)
          flush()
        }
      }
    }
  }

  fun close() {
    runCatching { server.close() }
  }

  companion object {
    /** Letters a person can read off a screen without mistaking one for another: no 0/o, 1/l. */
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

    /** A server on the home network, or null when the TV is on none (then the QR leads to the service itself). */
    fun open(page: (sent: Boolean, error: Boolean) -> String, onKey: (String) -> Unit): KeyServer? {
      val address = homeAddress() ?: return null
      val server = runCatching { ServerSocket(0, 8, address) }.onFailure { Log.w("Nama", "key server: $it") }.getOrNull() ?: return null
      val random = SecureRandom()
      val token = (1..10).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
      return KeyServer(server, token, page, onKey)
    }

    /** The TV's own IPv4 address on Wi-Fi or Ethernet; a VPN's tunnel, which is point to point, is skipped. */
    private fun homeAddress(): InetAddress? {
      val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull() ?: return null
      val usable = interfaces.filter { runCatching { it.isUp && !it.isLoopback && !it.isPointToPoint }.getOrDefault(false) }
      val ordered = usable.sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
      return ordered.flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    }
  }
}

/** Reading one request to the key page. Pure, so it is tested on the computer (KeyRequestTest). */
object KeyRequest {
  data class Answer(val status: Int, val key: String? = null)

  private const val LIMIT = 8 * 1024

  fun read(input: InputStream, token: String): Answer {
    val head = readHead(input) ?: return Answer(400)
    val lines = head.split("\r\n")
    val (method, target) = lines.first().split(' ').let { if (it.size < 2) return Answer(400) else it[0] to it[1] }
    if (target.substringBefore('?') != "/$token") return Answer(404)
    if (method == "GET") return Answer(200)
    if (method != "POST") return Answer(405)
    val length = lines.drop(1).firstOrNull { it.startsWith("content-length:", ignoreCase = true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: return Answer(411)
    if (length > LIMIT) return Answer(413)
    val body = ByteArray(length)
    var got = 0
    while (got < length) {
      val n = input.read(body, got, length - got)
      if (n < 0) break
      got += n
    }
    val fields = String(body, 0, got).split('&').mapNotNull { pair ->
      val k = pair.substringBefore('=')
      val v = pair.substringAfter('=', "")
      runCatching { URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8") }.getOrNull()
    }.toMap()
    val key = fields["key"]?.trim().orEmpty()
    // A key is printable ASCII with no spaces: anything else is a paste gone wrong.
    if (key.isEmpty() || key.length > 512 || key.any { it.code !in 0x21..0x7E }) return Answer(400)
    return Answer(200, key)
  }

  /** The request line and headers, up to the blank line; null when they run past the limit. */
  private fun readHead(input: InputStream): String? {
    val out = ByteArrayOutputStream()
    var last4 = 0
    while (out.size() < LIMIT) {
      val b = input.read()
      if (b < 0) return null
      out.write(b)
      last4 = (last4 shl 8) or b
      if (last4 == 0x0D0A0D0A) return out.toString("ISO-8859-1").removeSuffix("\r\n\r\n")
    }
    return null
  }
}
