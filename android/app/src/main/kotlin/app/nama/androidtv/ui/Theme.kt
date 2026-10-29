package app.nama.androidtv.ui

import androidx.compose.ui.relocation.bringIntoView as askParents
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
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
  /** The Mac's 12 on a 156 poster, kept in proportion on the TV's 200: 7.7% of the width. */
  val cardRadius: Dp = 15.dp
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

/**
 * The colour of the title this part of the screen is about, from its poster (`Post.tint()`), as the
 * Mac's `hue`: the bright button throws light in it and glass gets a rim of it, so each title's
 * page is lit in its own colour. Null where no one title is on stage.
 */
val LocalHue = compositionLocalOf<Color?> { null }

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

/**
 * Scrolls only as far as it takes to show what has focus, with a margin, as the Apple TV does.
 * Compose on a TV otherwise keeps the focused item a third of the way down the screen, which pulls
 * a title page's header up off the top as soon as its Play button has focus.
 */
@androidx.compose.foundation.ExperimentalFoundationApi
/**
 * A part of a page that is brought into view as a whole, with what in it has focus: the Apple TV's
 * sections. When the remote moves inside it, the page is asked to show from the part's top down to
 * the item in focus, so its title (a row's heading, the title page's name and story) stays in sight,
 * and a part that starts the page keeps the page at its very top (the title page opened half way
 * down: Play alone was brought into view, 120 points clear of the foot, and its title went off the
 * top). Where the part's top and the item no longer fit on one screen, only the item, as before.
 */
fun androidx.compose.ui.Modifier.wholeSection(): androidx.compose.ui.Modifier = this.then(SectionElement)

private object SectionElement : androidx.compose.ui.node.ModifierNodeElement<SectionNode>() {
  override fun create() = SectionNode()
  override fun update(node: SectionNode) = Unit
  override fun hashCode() = 7
  override fun equals(other: Any?) = other === this
}

private class SectionNode : androidx.compose.ui.Modifier.Node(), androidx.compose.ui.relocation.BringIntoViewModifierNode {
  override suspend fun bringIntoView(childCoordinates: androidx.compose.ui.layout.LayoutCoordinates, boundsProvider: () -> androidx.compose.ui.geometry.Rect?) {
    val self = requireLayoutCoordinates()
    val screen = with(requireDensity()) { 1080.dp.toPx() }
    askParents {
      val child = boundsProvider() ?: return@askParents null
      val inSelf = self.localBoundingBoxOf(childCoordinates, clipBounds = false).let { box -> child.translate(box.topLeft) }
      val whole = androidx.compose.ui.geometry.Rect(0f, 0f, self.size.width.toFloat(), inSelf.bottom)
      if (whole.height <= screen) whole else inSelf
    }
  }
}

class Minimal(private val margin: Float) : androidx.compose.foundation.gestures.BringIntoViewSpec {
  override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
    val end = offset + size
    return when {
      offset < margin -> offset - margin
      end > containerSize - margin -> minOf(end - containerSize + margin, offset - margin)
      else -> 0f
    }
  }
}
