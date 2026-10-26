package app.nama.androidtv.data

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import app.nama.core.AudioMix
import app.nama.core.Lang
import app.nama.core.S

/** User preferences, the Apple TV app's (Shared/AppModel.swift) with the same keys and defaults. */
data class Settings(
    /** English until the user picks another. */
    val lang: Lang = Lang.en,
    /** The user chose to browse without signing in. */
    val guest: Boolean = false,
    /** "off", "fa" or "en". */
    val defaultSubtitle: String = "fa",
    val subtitleSize: Int = 46,
    val subtitleColor: SubtitleColor = SubtitleColor.white,
    val subtitleBackground: SubtitleBackground = SubtitleBackground.none,
    val autoplayNext: Boolean = true,
    /** Jump over the opening titles and the recap without being asked. */
    val autoSkip: Boolean = false,
    /** Stereo by default: the original mix is often silent on a TV's own speakers. */
    val audioMix: AudioMix = AudioMix.stereo,
    /** Use the adaptive stream instead of the highest fixed rendition. */
    val preferAdaptive: Boolean = false,
    val reduceMotion: Boolean = false,
    /** How much of what is left of a title to fetch ahead onto the disk, in percent. */
    val readAhead: Int = 25,
    /** Switch the TV's refresh rate to the film's frame rate, even where that blanks the screen. */
    val matchFrameRate: Boolean = false,
) {
  companion object {
    val subtitleSizes = listOf(38, 46, 54)
    val readAheadChoices = listOf(0, 10, 25, 50, 100)

    fun load(p: SharedPreferences): Settings {
      val d = Settings()
      fun <E : Enum<E>> enumOf(values: Array<E>, key: String, fallback: E) = p.getString(key, null)?.let { s -> values.firstOrNull { it.name == s } } ?: fallback
      return Settings(
          lang = enumOf(Lang.entries.toTypedArray(), "lang", d.lang),
          guest = p.getBoolean("guest", d.guest),
          defaultSubtitle = p.getString("sub", null) ?: d.defaultSubtitle,
          subtitleSize = p.getInt("subSize", d.subtitleSize).takeIf { it in subtitleSizes } ?: d.subtitleSize,
          subtitleColor = enumOf(SubtitleColor.entries.toTypedArray(), "subColor", d.subtitleColor),
          subtitleBackground = enumOf(SubtitleBackground.entries.toTypedArray(), "subBackground", d.subtitleBackground),
          autoplayNext = p.getBoolean("autoplay", d.autoplayNext),
          autoSkip = p.getBoolean("autoSkip", d.autoSkip),
          audioMix = enumOf(AudioMix.entries.toTypedArray(), "audioMix", d.audioMix),
          preferAdaptive = p.getBoolean("adaptive", d.preferAdaptive),
          reduceMotion = p.getBoolean("calm", d.reduceMotion),
          readAhead = p.getInt("readAhead", d.readAhead).takeIf { it in readAheadChoices } ?: d.readAhead,
          matchFrameRate = p.getBoolean("matchFrameRate", d.matchFrameRate),
      )
    }
  }

  fun save(p: SharedPreferences) {
    p.edit()
        .putString("lang", lang.name)
        .putBoolean("guest", guest)
        .putString("sub", defaultSubtitle)
        .putInt("subSize", subtitleSize)
        .putString("subColor", subtitleColor.name)
        .putString("subBackground", subtitleBackground.name)
        .putBoolean("autoplay", autoplayNext)
        .putBoolean("autoSkip", autoSkip)
        .putString("audioMix", audioMix.name)
        .putBoolean("adaptive", preferAdaptive)
        .putBoolean("calm", reduceMotion)
        .putInt("readAhead", readAhead)
        .putBoolean("matchFrameRate", matchFrameRate)
        .apply()
  }
}

@Suppress("EnumEntryName")
enum class SubtitleColor(val label: S, val color: Color) {
  white(S.white, Color.White),
  yellow(S.yellow, Color(1.0f, 0.90f, 0.30f)),
  cyan(S.cyan, Color(0.45f, 0.93f, 1.0f)),
  green(S.green, Color(0.55f, 0.95f, 0.55f)),
}

/**
 * What sits behind subtitle text: nothing (outlined text), or a black plate at half or full
 * strength. The Apple TV's blurred plates are left out: blurring the picture behind a line costs a
 * weak TV a frame now and then, and a dropped frame shows in a film.
 */
@Suppress("EnumEntryName")
enum class SubtitleBackground(val label: S, val plate: Float) {
  none(S.bgNone, 0f),
  half(S.bgHalf, 0.5f),
  solid(S.bgSolid, 1f),
}
