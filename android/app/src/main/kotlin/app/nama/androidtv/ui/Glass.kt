package app.nama.androidtv.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How light falls on glass, the Mac's `GlassLight` (Mac/MacKit.swift, docs/design): a faint body,
 * a glare over the top half with a little caught along the bottom edge, and a rim bright where the
 * light strikes the top edge and faint along the bottom. A flat fill with an even hairline reads as
 * grey plastic; the uneven rim is what reads as glass. `lit` is the open or focused look.
 */
object GlassLight {
  /** The Mac's 1.2pt, at the TV's ×1.3. */
  val rimWidth: Dp = 1.6.dp

  fun body(lit: Boolean) = if (lit) 0.10f else 0.02f

  fun shadow(lit: Boolean) = if (lit) 0.30f else 0.22f

  fun glare(lit: Boolean) = Brush.verticalGradient(
      0f to Color.White.copy(alpha = if (lit) 0.30f else 0.12f),
      0.5f to Color.White.copy(alpha = if (lit) 0.06f else 0.02f),
      0.7f to Color.Transparent,
      1f to Color.White.copy(alpha = if (lit) 0.10f else 0.04f),
  )

  fun rim(lit: Boolean) = Brush.verticalGradient(
      listOf(
          Color.White.copy(alpha = if (lit) 0.95f else 0.55f),
          Color.White.copy(alpha = if (lit) 0.25f else 0.12f),
          Color.White.copy(alpha = 0.05f),
          Color.White.copy(alpha = if (lit) 0.45f else 0.22f),
      ))
}

/**
 * A pane of the Mac's glass. Where the Mac frosts what is behind (a blur a weak TV cannot pay for
 * every frame), a dark veil of `bg1` stands in; the glare, rim and shadow are as they are, each
 * only a gradient. [fill] is white laid on the glass, as the Mac's glass buttons have (0.10, and
 * 0.20 under the pointer). On a title's page the rim also takes the title's colour ([LocalHue]).
 */
@Composable
fun Modifier.glass(shape: Shape, lit: Boolean = false, fill: Float = 0f, veil: Float = 0.55f): Modifier {
  val hue = LocalHue.current
  var m = this
      .shadow(if (lit) 12.dp else 9.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = GlassLight.shadow(lit)), spotColor = Color.Black.copy(alpha = GlassLight.shadow(lit)))
      .background(Theme.bg1.copy(alpha = veil), shape)
      .background(Color.White.copy(alpha = GlassLight.body(lit) + fill), shape)
      .background(GlassLight.glare(lit), shape)
      .border(GlassLight.rimWidth, GlassLight.rim(lit), shape)
  // The Mac's `hueRim`: a rim in the colour of the title on stage, brightest at the top.
  if (hue != null) m = m.border(1.3.dp, Brush.verticalGradient(listOf(hue.copy(alpha = 0.6f), hue.copy(alpha = 0.1f))), shape)
  return m
}

/**
 * A glow in a colour behind a card or a button, as the Mac's coloured shadows. A shadow takes a
 * colour only from Android 9; before that a radial gradient of the same colour is drawn behind.
 */
fun Modifier.colouredGlow(color: Color, alpha: Float, radius: Dp, shape: Shape, y: Dp = 0.dp): Modifier =
    if (alpha <= 0f) this
    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
      this.shadow(radius, shape, clip = false, ambientColor = color.copy(alpha = alpha), spotColor = color.copy(alpha = alpha))
    else this.drawBehind {
      val r = radius.toPx()
      drawCircle(
          Brush.radialGradient(listOf(color.copy(alpha = alpha * 0.8f), Color.Transparent), center = Offset(size.width / 2, size.height / 2 + y.toPx()), radius = maxOf(size.width, size.height) / 2 + r),
          radius = maxOf(size.width, size.height) / 2 + r,
          center = Offset(size.width / 2, size.height / 2 + y.toPx()),
      )
    }
