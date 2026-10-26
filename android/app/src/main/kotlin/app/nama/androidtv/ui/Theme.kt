package app.nama.androidtv.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.androidtv.R
import app.nama.core.Lang
import app.nama.core.Loc

/**
 * Design tokens, the Apple TV app's (Shared/Theme.swift). Dark, glassy chrome over full-bleed
 * artwork: surfaces are translucent, the focused title lends its own colour to the glow around it,
 * and one red accent marks the primary action and progress.
 */
object Theme {
  val bg0 = Color(0xFF07080B)
  val bg1 = Color(0xFF0E1016)
  val surface = Color(0xFF161922)
  val surface2 = Color(0xFF20242F)
  val line = Color.White.copy(alpha = 0.08f)
  val text1 = Color(0xFFF4F1EA)
  val text2 = Color(0xFFF4F1EA).copy(alpha = 0.72f)
  val text3 = Color(0xFFF4F1EA).copy(alpha = 0.46f)
  val accent = Color(0xFFF0454A)
  val accentLight = Color(0xFFFF6A4D)
  val accentDeep = Color(0xFFD92B58)
  val good = Color(0xFF62D39B)
  val focusInk = Color(0xFF0B0C10)
  val imdb = Color(0xFFF5C518)
  /** Glass: a light veil with a lit edge. Real backdrop blur costs a weak TV too much. */
  val glass = Color.White.copy(alpha = 0.10f)
  val glassEdge = Color.White.copy(alpha = 0.14f)

  /** Horizontal inset of content from the screen edge (title-safe area). */
  val edge: Dp = 80.dp
  val posterWidth: Dp = 200.dp
  val cardRadius: Dp = 20.dp
  val tileRadius: Dp = 24.dp

  /** Focus changes: quick, with a little overshoot. */
  fun <T> focusSpring() = spring<T>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
  /** Larger moves: rows, panels, the billboard. */
  fun <T> glide() = spring<T>(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow)
}

/** A lively version of the poster's average colour, used for the glow around the title. */
fun app.nama.core.Post.tint(): Color {
  val c = poster.blurhash?.let { app.nama.core.BlurHash.average(it) } ?: return Theme.accent
  val hsv = FloatArray(3)
  android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
  // Greys have no meaningful hue; keep them as a cool white instead of a random colour.
  if (hsv[1] <= 0.1f) return Color.hsv(223f, 0.12f, 0.95f)
  return Color.hsv(hsv[0], (hsv[1] * 1.5f).coerceIn(0.55f, 0.95f), (hsv[2] * 1.7f).coerceIn(0.8f, 1f))
}

/** Vazirmatn covers Persian and Latin in one family, so mixed strings stay visually consistent. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold),
    Font(R.font.vazirmatn_extrabold, FontWeight.Black),
)

/** The app's type at a size in screen points, as `.app(size, weight)` on the Apple TV. */
fun app(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontFamily = Vazirmatn, fontSize = size.sp, fontWeight = weight)

/** Interface strings in the chosen language. */
val LocalLoc = staticCompositionLocalOf { Loc(Lang.en) }

/** "Reduce motion": no backdrop drift, no hero rotation, fades instead of travel. */
val LocalCalm = compositionLocalOf { false }

/**
 * Motion that never ends is allowed. False on a TV too weak to redraw the screen all day: a slow
 * box drops frames under a drifting backdrop, and the same then shows as stutter while browsing.
 */
val LocalLively = compositionLocalOf { true }

/**
 * Lays the app out on a screen 1920 points wide, whatever the TV's pixels and density, so the
 * Apple TV app's numbers (an 80 point edge, a 200 point poster) are used as they are and look the
 * same on a 720p set and a 4K one. Text is not scaled by the system font size either: a TV layout
 * is fixed, and larger type would push rows off the screen.
 */
@Composable
fun ScreenUnits(content: @Composable () -> Unit) {
  BoxWithConstraints {
    val px = with(LocalDensity.current) { maxWidth.toPx() }
    val density = Density(density = px / 1920f, fontScale = 1f)
    CompositionLocalProvider(LocalDensity provides density) { content() }
  }
}
