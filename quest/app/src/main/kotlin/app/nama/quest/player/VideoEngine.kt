package app.nama.quest.player

import android.view.Surface
import app.nama.quest.data.Stream
import kotlinx.coroutines.flow.StateFlow

/** What the scene needs from a player. Keeps the panels independent of mpv's own API. */
interface VideoEngine {
  val state: StateFlow<VideoState>

  fun attach(surface: Surface)

  fun detach()

  fun load(stream: Stream)

  fun setPaused(paused: Boolean)

  fun seekBy(seconds: Double)

  fun seekTo(seconds: Double)

  /** To the exact frame, slower: for jumps that must land precisely, like skipping the intro. */
  fun seekExact(seconds: Double)

  /** Null turns subtitles off. */
  fun selectSubtitle(language: String?)

  fun setMuted(muted: Boolean)

  fun stop()

  fun release()
}

data class VideoState(
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val paused: Boolean = true,
    val buffering: Boolean = false,
    val subtitle: String = "",
    val subtitleLanguage: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val ended: Boolean = false,
    val error: String? = null,
) {
  val aspect: Float
    get() = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight else 16f / 9f
}
