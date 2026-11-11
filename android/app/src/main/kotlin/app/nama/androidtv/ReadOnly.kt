package app.nama.androidtv

import android.util.Log
import app.nama.core.HttpResponse
import app.nama.core.HttpURLConnectionTransport
import app.nama.core.Transport

/**
 * Debug builds only: with `adb shell setprop debug.nama.readonly 1`, nothing the app sends changes
 * the account. Where playback was, lists, "continue watching" and sign-out are answered here with a
 * plain success, so automated key presses during a test cannot move a real person's progress.
 */
object ReadOnly {
  private val writes = setOf("observer", "user_list", "continue_watching_dismiss", "logout", "notification_read")

  val enabled: Boolean by lazy {
    BuildConfig.DEBUG && runCatching {
      Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, "debug.nama.readonly") == "1"
    }.getOrDefault(false)
  }

  /**
   * The transport the app's API client uses in a debug build: the network, minus writes when
   * read-only, with each request's time in the log (what a slow start on a real TV waits for).
   * A release build keeps core's own.
   */
  fun transport(inner: Transport = HttpURLConnectionTransport()): Transport? {
    if (!BuildConfig.DEBUG) return null
    if (enabled) Log.w(AppModel.TAG, "read-only: account writes are answered locally")
    return Transport { request ->
      val action = request.url.substringAfterLast("/action/", "")
      // Lists are read with the same action that changes them; only a change is held back.
      val body = request.body?.decodeToString().orEmpty()
      val write = enabled && action in writes && !(action == "user_list" && "action=list" in body)
      if (write) return@Transport HttpResponse(200, mapOf("Content-Type" to "application/json"), """{"success":true,"msg":"","result":{}}""".toByteArray())
      val start = System.currentTimeMillis()
      val what = request.url.substringBefore('?')
      try {
        inner.send(request).also { Log.d(AppModel.TAG, "http ${it.status} ${System.currentTimeMillis() - start} ms $what") }
      } catch (e: Exception) {
        Log.d(AppModel.TAG, "http failed ${System.currentTimeMillis() - start} ms $what: $e")
        throw e
      }
    }
  }
}
