package app.nama.quest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.nama.core.Article
import app.nama.core.HttpResponse
import app.nama.core.Json
import app.nama.core.Lang
import app.nama.core.Transport
import app.nama.quest.data.LiveCatalog
import app.nama.quest.data.Stream
import app.nama.quest.player.VideoEngine
import app.nama.quest.player.VideoState
import app.nama.quest.ui.CastPanel
import app.nama.quest.ui.ControlsPanel
import app.nama.quest.ui.EpisodesPanel
import app.nama.quest.ui.HomeWall
import app.nama.quest.ui.Images
import app.nama.quest.ui.ScreenArt
import app.nama.quest.ui.SubtitlePanel
import app.nama.quest.ui.TitleInfo
import app.nama.quest.ui.TitleLogo
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper

/**
 * Every panel drawn on the computer, in the three languages, at the size it has in the headset
 * (metres × dp per metre, as NamaActivity registers it), from the shared test fixtures. Not a
 * pass/fail test: it writes PNGs to quest/build/shots for a person (or an agent) to look at, so
 * layout, wrapping and right-to-left can be checked without putting the headset on. What it can't
 * show: where panels sit in the room, the film itself, pointing, and the system keyboard.
 *
 *   ./gradlew :app:testDebugUnitTest -Pshots
 *
 * One language only: add -Pshots.lang=fa. Every request it answers, printed: -Pshots.trace.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w2100dp-h1400dp-mdpi")
class PanelShots {
  @get:Rule val compose = createComposeRule()

  private val out = File(System.getProperty("nama.shots") ?: "build/shots")
  private val fixtures = File(System.getProperty("nama.fixtures") ?: "../../Tests/NamaCoreTests/Fixtures")

  @Volatile private var lastRequest = 0L

  private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

  @Test
  fun signedIn() = shoot(signedIn = true)

  @Test
  fun guest() = shoot(signedIn = false, only = setOf("wall-profile", "wall-settings", "title-movie-info"))

  private fun shoot(signedIn: Boolean, only: Set<String>? = null) {
    Images.source = ::picture
    val context = ApplicationProvider.getApplicationContext<Context>()
    // The logo list is fetched from GitHub; an empty one keeps the run off the network.
    File(context.cacheDir, "30nama-logos.json").writeText("{}")
    context.getSharedPreferences("account", Context.MODE_PRIVATE).edit().apply {
      if (signedIn) putString("token", "sample-token") else remove("token")
    }.commit()
    context.getSharedPreferences("nama", Context.MODE_PRIVATE).edit().putBoolean("guest", !signedIn).commit()

    compose.setContent { screen?.invoke() }
    val video = StillVideo()
    val model = AppModel(context, LiveCatalog(context, FixtureNetwork(), siteKey = "sample-key"), video, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    model.start()
    waitFor("home rows") { model.rows.value.isNotEmpty() }
    val who = if (signedIn) "" else "guest-"
    val langs = System.getProperty("nama.lang")?.let { listOf(Lang.valueOf(it)) } ?: listOf(Lang.en, Lang.fa, Lang.ar)

    for (lang in langs) {
      model.setLang(lang)
      waitFor("rows in $lang") { model.rows.value.isNotEmpty() }
      fun take(name: String, wM: Float, hM: Float, dpm: Float, content: @Composable () -> Unit) {
        if (only != null && name !in only) return
        show(wM * dpm, hM * dpm, content)
        compose.onRoot().captureRoboImage(File(out, "${lang.name}/$who$name.png").path)
        if (System.getProperty("nama.trace") != null) println("PanelShots: ${System.currentTimeMillis() % 100000} shot $name")
      }

      // The magazines: a cover, the open cover page, and a title page.
      val shelf = Shelf { model.rows.value }
      take("mag-cover", Layout.COVER_W, Layout.COVER_H, NamaActivity.DP_COVER) { app.nama.quest.ui.MagazineCover(model, shelf, 0) }
      shelf.open(0)
      take("mag-page-0", Layout.PAGE_W, Layout.PAGE_H, NamaActivity.DP_PAGE) { app.nama.quest.ui.MagazinePage(model, shelf, { true }) { it.pageA } }
      take("mag-page-1", Layout.PAGE_W, Layout.PAGE_H, NamaActivity.DP_PAGE) { app.nama.quest.ui.MagazinePage(model, shelf, { false }) { it.pageB } }

      // The wall, section by section, then what opens on top of it.
      for (section in Section.entries) {
        model.go(section)
        take("wall-${section.name}", Layout.WALL_W, Layout.WALL_H, DP_FAR) { HomeWall(model) }
      }
      model.go(Section.browse)
      for ((name, route) in listOf(
          "route-advanced" to Route.AdvancedSearch(),
          "route-schedule" to Route.Schedule,
          "route-toplists" to Route.TopLists,
          "route-news" to Route.News,
          "route-article" to Route.ArticlePage(article()),
      )) {
        model.push(route)
        take(name, Layout.WALL_W, Layout.WALL_H, DP_FAR) { HomeWall(model) }
        model.back()
      }

      // A film and a series, each as the title page shows it.
      for ((kind, id) in listOf("movie" to MOVIE, "series" to SERIES)) {
        val tile = model.rows.value.flatMap { it.tiles }.firstOrNull { it.id == id.toString() } ?: placeholder(model, id)
        model.open(tile)
        waitFor("$kind details") { model.details.value != null }
        take("title-$kind-screen", Layout.SCREEN_W, Layout.SCREEN_H, DP_SCREEN) { ScreenArt(model) }
        take("title-$kind-logo", 1.3f, 0.38f, DP_NEAR) { TitleLogo(model, glow = false) }
        take("title-$kind-info", 1.1f, 0.46f, DP_NEAR) { TitleInfo(model) }
        take("title-$kind-side", 0.5f, 0.78f, DP_NEAR) { EpisodesPanel(model) }
        take("title-$kind-cast", 0.5f, 0.78f, DP_NEAR) { CastPanel(model) }
        if (kind == "series") {
          model.details.value?.post?.cast?.firstOrNull { it.siteID != null || it.imdb != null }?.let { person ->
            model.push(Route.PersonPage(person))
            take("route-person", Layout.WALL_W, Layout.WALL_H, DP_FAR) { HomeWall(model) }
            model.back()
            model.open(tile)
            waitFor("series again") { model.details.value != null }
          }
          model.play()
          waitFor("playing") { model.place.value == Place.Playing && model.stream.value != null }
          take("play-controls", 1.1f, 0.36f, DP_NEAR) { ControlsPanel(model) {} }
          take("play-subtitles", 2.4f, 0.3f, DP_SCREEN) { SubtitlePanel(model) }
          model.stop()
        }
        model.back()
      }
    }
  }

  /** Puts [content] at its panel size over the dimmed room the headset shows around it. */
  private fun show(wDp: Float, hDp: Float, content: @Composable () -> Unit) {
    screen = { Box(Modifier.requiredSize(wDp.dp, hDp.dp).background(ROOM)) { content() } }
    // Data and pictures arrive on other threads, and the API spaces its requests out: wait until
    // the network has been quiet for a while (or give up after a few seconds), then let Compose settle.
    val start = System.currentTimeMillis()
    lastRequest = start
    while (true) {
      compose.waitForIdle()
      tick()
      val now = System.currentTimeMillis()
      if ((now - lastRequest > 1_600 && now - start > 700) || now - start > 12_000) break
      Thread.sleep(80)
    }
    compose.waitForIdle()
  }

  /**
   * Both clocks of the test stand still unless moved: Robolectric's for the main thread, and
   * Compose's for coroutines started from the screens (the API spacing its requests, a debounce).
   * Move them on as real time passes, or such a delay would wait until the next screen.
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

  private fun placeholder(model: AppModel, id: Int) =
      model.rows.value.first().tiles.first().copy(id = id.toString())

  private fun article(): Article =
      Article(721620, Article.Kind.news, "Sample article", "A short summary of the article.", "۲۹ مهر ۱۴۰۵", URI("https://example.com/a.jpg"), null)

  /** Answers every request from the fixtures, by the path after /action/. */
  private inner class FixtureNetwork : Transport {
    override suspend fun send(request: app.nama.core.HttpRequest): HttpResponse {
      val url = request.url
      lastRequest = System.currentTimeMillis()
      if (System.getProperty("nama.trace") != null) println("PanelShots: ${System.currentTimeMillis() % 100000} ${url.substringAfter("/action/", url)} ${String(request.body ?: ByteArray(0)).take(160)}")
      val body = String(request.body ?: ByteArray(0))
      val path = url.substringAfter("/action/", "")
      val file =
          when {
            path.isEmpty() -> return ok("""[{"location":"WORLD","host":"https://api.example.com/api/v2"}]""")
            path == "home_stream" -> "home_stream"
            path == "menu" -> "menu"
            path == "single" -> if (body.contains("$SERIES")) "single_series" else "single_movie"
            path == "stream" -> if (body.contains("$SERIES")) "stream_series" else "stream_movie"
            path == "search" || path == "archive" || path == "advanced_search" || path.startsWith("filter") -> "search"
            path == "user" -> return ok(USER)
            path == "advanced_search_parameters" -> return ok(PARAMETERS)
            path == "test" -> return ok("""{"success":true,"msg":null,"result":{}}""")
            path.startsWith("advanced_search") || path.startsWith("list/type") -> "site_advanced_search"
            path.startsWith("article/type") -> "site_articles"
            path == "single/id/721620" -> "site_article"
            path.startsWith("single/id") -> "site_person"
            path.startsWith("schedule") -> "site_schedule"
            path.startsWith("toplist") -> "site_toplist"
            else -> {
              println("PanelShots: no fixture for $path")
              return ok("""{"success":true,"msg":null,"result":{}}""")
            }
          }
      return HttpResponse(200, emptyMap(), File(fixtures, "$file.json").readBytes())
    }

    private fun ok(text: String) = HttpResponse(200, emptyMap(), text.toByteArray())
  }

  /** A soft two-colour picture, different for every address, in place of the real artwork. */
  private fun picture(url: String): ByteArray {
    val h = url.hashCode()
    val hue = ((h ushr 1) % 360).toFloat()
    val bmp = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
    val a = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.45f, 0.55f))
    val b = android.graphics.Color.HSVToColor(floatArrayOf((hue + 40) % 360, 0.6f, 0.18f))
    Canvas(bmp).drawRect(0f, 0f, 600f, 900f, Paint().apply { shader = LinearGradient(0f, 0f, 600f, 900f, a, b, Shader.TileMode.CLAMP) })
    return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
  }

  private class StillVideo : VideoEngine {
    private val flow = MutableStateFlow(VideoState())
    override val state: StateFlow<VideoState> = flow

    override fun attach(surface: android.view.Surface) {}
    override fun detach() {}
    override fun load(stream: Stream) {
      flow.value =
          if (stream.preview) VideoState()
          else VideoState(position = 1342.0, duration = 2710.0, paused = true, subtitle = "This is how a subtitle line reads.\nاین هم یک خط زیرنویس است.", videoWidth = 1920, videoHeight = 1080)
    }
    override fun setPaused(paused: Boolean) {}
    override fun seekBy(seconds: Double) {}
    override fun seekTo(seconds: Double) {}
    override fun seekExact(seconds: Double) {}
    override fun selectSubtitle(language: String?) {}
    override fun setMuted(muted: Boolean) {}
    override fun stop() { flow.value = VideoState() }
    override fun release() {}
  }

  companion object {
    const val DP_NEAR = NamaActivity.DP_NEAR
    const val DP_FAR = NamaActivity.DP_FAR
    const val DP_SCREEN = NamaActivity.DP_SCREEN
    /** About what dimmed passthrough looks like behind the panels. */
    val ROOM = Color(0xFF26262B)
    const val MOVIE = 685883
    const val SERIES = 469773
    /** What advanced search and the catalog filters offer, cut down; the server sends far more. */
    val PARAMETERS =
        """{"success":true,"msg":null,"result":{
          "genre":[{"name":"اکشن","value":"action"},{"name":"درام","value":"drama"},{"name":"کمدی","value":"comedy"},{"name":"علمی تخیلی","value":"sci-fi"},{"name":"هیجان انگیز","value":"thriller"}],
          "type":[{"name":"فیلم","value":"movie"},{"name":"سریال","value":"series"},{"name":"انیمه","value":"anime"}],
          "country":[{"name":"آمریکا","value":"usa"},{"name":"ایران","value":"iran"},{"name":"ژاپن","value":"japan"}],
          "language":[{"name":"انگلیسی","value":"english"},{"name":"فارسی","value":"persian"}],
          "channel":[{"name":"HBO","value":"hbo"},{"name":"Netflix","value":"netflix"}],
          "imdb_rate":[{"name":"بالای ۵","value":"5"},{"name":"بالای ۷","value":"7"},{"name":"بالای ۸","value":"8"}],
          "age":["G","PG","PG-13","R"],
          "min_year":1950,"max_year":2026}}"""
    /** A made-up member; nothing here belongs to a real account. */
    val USER =
        """{"success":true,"msg":null,"result":{"user_id":"1","user_name":"sample.viewer","user_type":"VIP","user_expire":{"days":"212","g":"2027-05-25 12:00:00","j":"1406-03-04 12:00:00"}}}"""
  }
}
