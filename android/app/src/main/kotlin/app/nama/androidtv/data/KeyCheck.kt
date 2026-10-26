package app.nama.androidtv.data

import app.nama.core.HttpRequest
import app.nama.core.SubSource
import app.nama.core.TMDB
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** What came of trying a key. */
enum class KeyState { Unset, Checking, Works, Rejected, Unreachable }

/**
 * A key is saved first and tried after, with one light request: from Iran the service may be out
 * of reach while the key is fine, and that must not throw the key away. Learnt from 30namaTV.
 */
class KeyCheck(http: OkHttpClient) {
  private val http = http.newBuilder().callTimeout(12, TimeUnit.SECONDS).build()

  suspend fun check(service: String, key: String): KeyState {
    val request = when (service) {
      "tmdb" -> TMDB.request("configuration", key = key)
      "subsource" -> SubSource.searchRequest(SubSource.Query("Inception", 2010, "tt1375666", false, null), key)
      "gemini" -> HttpRequest(GEMINI_MODELS, headers = mapOf("x-goog-api-key" to key))
      else -> return KeyState.Unset
    }
    return withContext(Dispatchers.IO) {
      try {
        send(request).let { code ->
          when {
            code in 200..299 -> KeyState.Works
            code == 401 || code == 403 -> KeyState.Rejected
            // Gemini answers a wrong key with 400 API_KEY_INVALID.
            code == 400 && service == "gemini" -> KeyState.Rejected
            else -> KeyState.Unreachable
          }
        }
      } catch (_: IOException) {
        KeyState.Unreachable
      }
    }
  }

  private companion object {
    const val GEMINI_MODELS = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1"
  }

  private fun send(r: HttpRequest): Int {
    val b = Request.Builder().url(r.url)
    r.headers.forEach { (k, v) -> b.header(k, v) }
    return http.newCall(b.build()).execute().use { it.code }
  }
}
