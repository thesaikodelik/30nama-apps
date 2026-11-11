package app.nama.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP request, as plain data, so a test can answer it without a network. */
class HttpRequest(
    val url: String,
    val method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timeoutSeconds: Double = 20.0,
) {
    val headers: Map<String, String> = LinkedHashMap(headers)

    /** A header by name, whatever its case. */
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun with(name: String, value: String): HttpRequest = HttpRequest(url, method, headers + (name to value), body, timeoutSeconds)

    override fun toString(): String = "$method $url"
}

class HttpResponse(val status: Int, headers: Map<String, String> = emptyMap(), val body: ByteArray = ByteArray(0)) {
    val headers: Map<String, String> = LinkedHashMap(headers)

    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/**
 * Sends a request and returns whatever status came back. Throws only when no answer came at all
 * (no connection, a time-out), and lets cancellation through as it is.
 */
fun interface Transport {
    suspend fun send(request: HttpRequest): HttpResponse
}

/**
 * The default transport, on `HttpURLConnection`, which both the JVM and Android have. A request
 * that is cancelled returns at once and has its connection closed.
 */
class HttpURLConnectionTransport : Transport {
    override suspend fun send(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        // Outside the caller's scope: a connect still in DNS or TCP does not end when the connection
        // is closed, and as a child it held the caller until its own time-out. On a TV that could not
        // reach one of the two endpoint lists, `firstOf` had the other's answer in under a second and
        // still waited ten. The failure comes back as a value, so nothing is thrown into that scope.
        val work = detached.async { runCatching { exchange(connection, request) } }
        val result = try {
            work.await()
        } catch (e: CancellationException) {
            connection.disconnect()
            work.cancel()
            throw e
        }
        return result.getOrThrow()
    }

    private companion object {
        val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private fun exchange(c: HttpURLConnection, request: HttpRequest): HttpResponse {
        val timeout = (request.timeoutSeconds * 1000).toInt().coerceAtLeast(1)
        c.connectTimeout = timeout
        c.readTimeout = timeout
        c.requestMethod = request.method
        c.useCaches = false
        c.instanceFollowRedirects = true
        for ((k, v) in request.headers) c.setRequestProperty(k, v)
        try {
            val body = request.body
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val status = c.responseCode
            val stream: InputStream? = if (status >= 400) c.errorStream else runCatching { c.inputStream }.getOrElse { c.errorStream }
            val data = stream?.use { readAll(it) } ?: ByteArray(0)
            val headers = c.headerFields.entries.mapNotNull { (k, v) -> k?.let { it to (v.lastOrNull() ?: "") } }.toMap()
            return HttpResponse(status, headers, data)
        } finally {
            c.disconnect()
        }
    }

    private fun readAll(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        input.copyTo(out)
        return out.toByteArray()
    }
}
