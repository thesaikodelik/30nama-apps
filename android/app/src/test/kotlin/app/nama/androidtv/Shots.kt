package app.nama.androidtv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import app.nama.androidtv.ui.NamaRoot
import app.nama.androidtv.ui.Nav
import app.nama.androidtv.ui.Route
import app.nama.androidtv.ui.Tab
import app.nama.core.HttpResponse
import app.nama.core.Lang
import app.nama.core.Transport
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen drawn on the computer, in the three languages, at a 1080p TV's size, from the
 * shared test fixtures. Not a pass/fail test: it writes PNGs to android/build/shots for a person
 * (or an agent) to look at, so layout, wrapping and right-to-left can be checked without a TV.
 * What it can't show: the film itself, real focus movement on a remote, and the system keyboard.
 *
 *   ./gradlew :app:testDebugUnitTest -Pshots
 *
 * One language only: -Pshots.lang=fa. Only the shots whose name contains a word: -Pshots.only=home
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
class Shots {
  @get:Rule val compose = createComposeRule()

  private val out = File(System.getProperty("nama.shots") ?: "build/shots")
  private val fixtures = File(System.getProperty("nama.fixtures") ?: "../../Tests/NamaCoreTests/Fixtures")
  private val only = System.getProperty("nama.only")

  @Volatile private var lastRequest = 0L

  @Test fun signedIn() = shoot(signedIn = true)

  @Test fun guest() = shoot(signedIn = false)

  private fun shoot(signedIn: Boolean) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(FakePictures) }.build())
    context.getSharedPreferences("account", Context.MODE_PRIVATE).edit().apply {
      if (signedIn) putString("token", "sample-token") else remove("token")
    }.commit()
    val langs = System.getProperty("nama.lang")?.let { listOf(Lang.valueOf(it)) } ?: listOf(Lang.en, Lang.fa, Lang.ar)
    val who = if (signedIn) "" else "guest-"
    var current by mutableStateOf<Pair<AppModel, Nav>?>(null)
    compose.setContent { current?.let { (model, nav) -> key(model) { NamaRoot(model, nav, onExit = {}, lively = false, intro = false) } } }

    for (lang in langs) {
      context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("lang", lang.name).putBoolean("guest", !signedIn).commit()
      val model = AppModel(context, CoroutineScope(SupervisorJob() + Dispatchers.Main), FixtureNetwork(), siteKey = "sample-key")
      val nav = Nav()
      current = model to nav
      model.boot()
      waitFor("boot") { model.phase != AppModel.Phase.Booting }

      fun take(name: String, keys: List<Key> = emptyList()) {
        if (only != null && !name.contains(only)) return
        for (k in keys) {
          compose.onRoot().performKeyInput { pressKey(k) }
          settle()
        }
        settle()
        compose.onRoot().captureRoboImage(File(out, "${lang.name}/$who$name.png").path)
      }

      if (!signedIn) {
        model.requestSignIn()
        take("login")
        model.continueAsGuest()
      }
      for (tab in Tab.entries) {
        nav.tab = tab
        take("tab-${tab.name}")
      }
      nav.tab = Tab.home
      take("home-browsing", listOf(Key.DirectionDown, Key.DirectionDown))
      for ((kind, id) in listOf("movie" to MOVIE, "series" to SERIES)) {
        nav.push(Route.Details(id, null))
        take("title-$kind")
        take("title-$kind-below", listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionDown))
        nav.pop()
      }
    }
  }

  /** Waits until the network has been quiet for a while (or a few seconds), then lets Compose settle. */
  private fun settle() {
    val start = System.currentTimeMillis()
    lastRequest = start
    while (true) {
      compose.waitForIdle()
      tick()
      val now = System.currentTimeMillis()
      if ((now - lastRequest > 1_200 && now - start > 600) || now - start > 10_000) break
      Thread.sleep(60)
    }
    compose.waitForIdle()
  }

  /**
   * Both clocks of the test stand still unless moved: Robolectric's for the main thread, and
   * Compose's for coroutines started from the screens. Move them on as real time passes.
   */
  private fun tick() {
    shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
    compose.mainClock.advanceTimeBy(100)
  }

  private fun waitFor(what: String, until: () -> Boolean) {
    val end = System.currentTimeMillis() + 20_000
    while (!until()) {
      check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
      tick()
      Thread.sleep(50)
    }
  }

  /** Answers every request from the fixtures, by the path after /action/. */
  private inner class FixtureNetwork : Transport {
    override suspend fun send(request: app.nama.core.HttpRequest): HttpResponse {
      val url = request.url
      lastRequest = System.currentTimeMillis()
      val body = String(request.body ?: ByteArray(0))
      val path = url.substringAfter("/action/", "")
      val file =
          when {
            path.isEmpty() -> return ok("""[{"location":"WORLD","host":"https://api.example.com/api/v2"}]""")
            path == "home_stream" -> "home_stream"
            path == "menu" -> "menu"
            path == "single" -> if (body.contains("$SERIES")) "single_series" else "single_movie"
            path == "stream" -> if (body.contains("$SERIES")) "stream_series" else "stream_movie"
            path == "search" || path == "archive" || path == "advanced_search" -> "search"
            path == "user" -> return ok(USER)
            path == "qrlogin_code" -> return ok("""{"success":true,"msg":null,"result":{"code":"5c5635fd"}}""")
            path == "qrlogin_login" -> return ok("""{"success":false,"msg":"not yet","result":{}}""")
            path == "test" -> return ok("""{"success":true,"msg":null,"result":{}}""")
            else -> return ok("""{"success":true,"msg":null,"result":{}}""")
          }
      return HttpResponse(200, emptyMap(), File(fixtures, "$file.json").readBytes())
    }

    private fun ok(text: String) = HttpResponse(200, emptyMap(), text.toByteArray())
  }

  /** A soft two-colour picture, different for every address, in place of the real artwork. */
  private object FakePictures : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
      val h = chain.request.data.toString().hashCode()
      val hue = ((h ushr 1) % 360).toFloat()
      val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
      val a = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.55f))
      val b = android.graphics.Color.HSVToColor(floatArrayOf((hue + 40) % 360, 0.6f, 0.18f))
      Canvas(bmp).drawRect(0f, 0f, 300f, 450f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, a, b, Shader.TileMode.CLAMP) })
      return SuccessResult(image = bmp.asImage(), request = chain.request, dataSource = DataSource.MEMORY)
    }
  }

  companion object {
    const val MOVIE = 685883
    const val SERIES = 469773
    /** A made-up member; nothing here belongs to a real account. */
    val USER =
        """{"success":true,"msg":null,"result":{"user_id":"1","user_name":"sample.viewer","user_type":"VIP","user_expire":{"days":"212","g":"2027-05-25 12:00:00","j":"1406-03-04 12:00:00"}}}"""
  }
}
