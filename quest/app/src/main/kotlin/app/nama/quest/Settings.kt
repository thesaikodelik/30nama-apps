package app.nama.quest

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import app.nama.core.AudioMix
import app.nama.core.S
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's preferences, with the Mac's names, keys and defaults (30nama/Shared/AppModel.swift,
 * `Settings`) so the apps behave alike. Each one is a StateFlow: the settings page writes it, the
 * player and the panels read it. The language stays in AppModel, which owns its consequences.
 */
class Settings(private val prefs: SharedPreferences) {
  /** "off", "fa" or "en". */
  val defaultSubtitle = string("sub", "fa")
  val subtitleSize = int("subSize", 46, valid = SUBTITLE_SIZES::contains)
  val subtitleColor = enumOf("subColor", SubtitleColor.white)
  val subtitleBackground = enumOf("subBackground", SubtitleBackground.none)
  val autoplayNext = bool("autoplay", true)
  /** Jump over the opening titles and the recap without being asked. */
  val autoSkip = bool("autoSkip", false)
  /** The headset plays two channels; stereo unless the user asks for the file's own mix. */
  val audioMix = enumOf("audioMix", AudioMix.stereo)
  /** Seconds of a stream to hold ahead; zero leaves it to the player. */
  val bufferAhead = int("bufferAhead", 0, valid = { it >= 0 })
  /** The adaptive stream instead of the highest fixed rendition. */
  val preferAdaptive = bool("adaptive", false)
  /** Stops the drifting picture and the trailer behind the title, for comfort. */
  val reduceMotion = bool("calm", false)
  /** Shown on the profile instead of the username when the site has no name. */
  val displayName = string("displayName", "")
  val tmdbKey = string("tmdbKey", "")
  val subsourceKey = string("subsourceKey", "")

  /** One stored preference, readable as a flow and writable in one call. */
  inner class Pref<T>(private val key: String, initial: T, private val write: SharedPreferences.Editor.(String, T) -> Unit) {
    private val flow = MutableStateFlow(initial)
    val value: T
      get() = flow.value

    val state: StateFlow<T> = flow.asStateFlow()

    fun set(v: T) {
      flow.value = v
      prefs.edit().apply { write(key, v) }.apply()
    }
  }

  private fun string(key: String, default: String) =
      Pref(key, prefs.getString(key, null) ?: default) { k, v -> putString(k, v) }

  private fun bool(key: String, default: Boolean) =
      Pref(key, if (prefs.contains(key)) prefs.getBoolean(key, default) else default) { k, v -> putBoolean(k, v) }

  private fun int(key: String, default: Int, valid: (Int) -> Boolean) =
      Pref(key, prefs.getInt(key, default).takeIf(valid) ?: default) { k, v -> putInt(k, v) }

  private inline fun <reified E : Enum<E>> enumOf(key: String, default: E) =
      Pref(key, prefs.getString(key, null)?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: default) { k, v -> putString(k, v.name) }

  companion object {
    val SUBTITLE_SIZES = listOf(38, 46, 54)
    val BUFFER_CHOICES = listOf(0, 15, 30, 60, 120)
  }
}

enum class SubtitleColor(val label: S, val color: Color) {
  white(S.white, Color.White),
  yellow(S.yellow, Color(1.0f, 0.90f, 0.30f)),
  cyan(S.cyan, Color(0.45f, 0.93f, 1.0f)),
  green(S.green, Color(0.55f, 0.95f, 0.55f)),
}

/**
 * What sits behind subtitle text. The blur styles of the Mac can't be had here: a panel can't see
 * the video layer behind it. The plate's alpha is set for the headset's linear-light blending,
 * where a nominal 50% black reads far lighter than on a monitor.
 */
enum class SubtitleBackground(val label: S, val plateAlpha: Float) {
  none(S.bgNone, 0f),
  half(S.bgHalf, 0.8f),
  solid(S.bgSolid, 1f),
}
