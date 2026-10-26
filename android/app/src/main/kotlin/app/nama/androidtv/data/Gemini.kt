package app.nama.androidtv.data

import android.content.Context
import app.nama.core.Cue
import app.nama.core.Json
import app.nama.core.Lang
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google's Gemini API, with the key the viewer made in their own Google AI Studio account; the app
 * carries none. Its free tier costs nothing, within a daily number of requests, and a film is about
 * twenty. The "-latest" names follow Google's newest model of each kind, so a retired model does not
 * break the feature. Translated batches are kept on the disk, so a film is translated once.
 */
class Gemini(context: Context, http: OkHttpClient) {
  private val http = http.newBuilder().callTimeout(90, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
  private val dir = File(context.filesDir, "translations")

  sealed interface Outcome {
    data class Lines(val lines: List<String?>) : Outcome
    /** The key was refused. */
    data object Rejected : Outcome
    /** The free requests for today are spent, or the service could not be reached. */
    data object Unavailable : Outcome
  }

  /** One batch, from the disk if it was translated before. [source] names the subtitle file. */
  suspend fun translate(key: String, source: String, target: Lang, title: String, n: Int, cues: List<Cue>): Outcome {
    val file = File(dir, "${digest("$source|${target.name}")}-$n.json")
    withContext(Dispatchers.IO) { runCatching { Json.parse(file.readText()) }.getOrNull() }?.array?.let { saved ->
      if (saved.size == cues.size) return Outcome.Lines(saved.map { it.string })
    }
    val body = Translation.request(cues.map { it.text }, target, title)
    models@ for (model in MODELS) {
      // A busy minute is waited out twice; a refusal or a spent day is not.
      for (attempt in 0 until 3) {
        val (code, answer) = withContext(Dispatchers.IO) {
          try {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", key)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { r -> r.code to runCatching { Json.parse(r.body!!.bytes()) }.getOrNull() }
          } catch (_: IOException) {
            0 to null
          }
        }
        when {
          code in 200..299 -> {
            val lines = answer?.let { Translation.read(it, cues.size) } ?: continue
            withContext(Dispatchers.IO) { runCatching { dir.mkdirs(); file.writeText(Json.Arr(lines.map { it?.let(Json::Str) ?: Json.Null }).serialize()) } }
            return Outcome.Lines(lines)
          }
          code == 400 && answer.toString().contains("API_KEY_INVALID") -> return Outcome.Rejected
          code == 401 || code == 403 -> return Outcome.Rejected
          // This model is not offered to this key: the next one.
          code == 404 -> continue@models
          code == 429 && attempt < 2 && !answer.toString().contains("PerDay") -> delay(30_000L)
          code == 429 -> return Outcome.Unavailable
          code == 0 -> return Outcome.Unavailable
          else -> if (attempt < 2) delay(5_000L)
        }
      }
    }
    return Outcome.Unavailable
  }

  private fun digest(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

  companion object {
    /** The light model first: quick, and the most free requests a day. */
    val MODELS = listOf("gemini-flash-lite-latest", "gemini-flash-latest")
  }
}
