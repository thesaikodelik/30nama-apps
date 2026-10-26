package app.nama.androidtv

import android.util.Base64
import app.nama.core.Json
import app.nama.core.Post
import app.nama.core.StreamResult

/**
 * Debug builds only: stream files need a subscription, so with `adb shell setprop debug.nama.demo 1`
 * this stands in for the `stream` action (the Apple TV's DemoStream), and the player, subtitles and
 * the episode list can be tried without one. A public HLS test stream, and test subtitles.
 */
object DemoStream {
  val enabled: Boolean by lazy {
    BuildConfig.DEBUG && runCatching {
      Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, "debug.nama.demo") == "1"
    }.getOrDefault(false)
  }

  private const val VIDEO = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

  fun result(post: Post): StreamResult {
    fun subtitle(lines: List<String>): String {
      val vtt = StringBuilder("WEBVTT\n\n")
      lines.forEachIndexed { i, line -> vtt.append(String.format(java.util.Locale.ROOT, "00:00:%02d.000 --> 00:00:%02d.500\n", i * 4 + 1, i * 4 + 4)).append(line).append("\n\n") }
      return "data:text/vtt;base64," + Base64.encodeToString(vtt.toString().toByteArray(), Base64.NO_WRAP)
    }
    val subtitles = mapOf(
        "fa" to subtitle((1..14).map { "این خط شمارهٔ $it زیرنویس آزمایشی است\nو خط دوم آن" }),
        "en" to subtitle((1..14).map { "Sample subtitle line $it" }),
    )
    fun item(season: Int, episode: Int) = mapOf(
        "info" to mapOf("file_id" to "$season${"%02d".format(episode)}", "post_id" to post.id, "season" to season, "episode" to episode, "title" to mapOf("english" to "Sample episode $episode"), "duration" to 2640),
        "options" to mapOf("token" to "demo", "intro_start" to 8, "intro_end" to 40, "end" to 90),
        "file" to mapOf("m3u8" to listOf(mapOf("label" to "Auto", "default" to VIDEO), mapOf("label" to "1080p", "default" to VIDEO)), "subtitle" to subtitles),
    )
    // Seasons of very different lengths, as real series have.
    val items = if (post.isSeries) listOf(1 to 4, 2 to 14, 3 to 60).flatMap { (s, n) -> (1..n).map { item(s, it) } }
    else listOf(mapOf("info" to mapOf("file_id" to "1", "post_id" to post.id), "options" to mapOf("token" to "demo"), "file" to mapOf("m3u8" to VIDEO, "subtitle" to subtitles)))
    return StreamResult.from(Json.of(mapOf("hls" to items, "continue_watching" to if (post.isSeries) mapOf("file_id" to "103", "time" to 20, "duration" to 600) else null)))
  }
}
