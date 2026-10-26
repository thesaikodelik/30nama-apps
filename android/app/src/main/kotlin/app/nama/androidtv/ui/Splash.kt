package app.nama.androidtv.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.nama.androidtv.R

/**
 * The opening title, after the Apple TV's: the 30nama logo resolves on a dark sheet over the app,
 * a red line runs out under it, and to leave the line runs to both edges and the sheet parts
 * along it like a curtain, uncovering the app. The real glass of the Apple TV is a dark veil here:
 * a weak TV cannot blur the whole screen while home is being built behind it.
 */
@Composable
fun Splash(leaving: Boolean, ready: Boolean) {
  var lit by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { lit = true }
  val parting = tween<Float>(750, delayMillis = 380, easing = CubicBezierEasing(0.7f, 0f, 0.2f, 1f))
  val part by animateFloatAsState(if (leaving) 1f else 0f, parting, label = "part")
  val logo by animateFloatAsState(if (lit) 1f else 0f, tween(1600, easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)), label = "logo")
  val line by animateFloatAsState(if (leaving) 2f else if (lit) 1f else 0f, tween(if (leaving) 420 else 900, delayMillis = if (leaving) 0 else 750), label = "line")
  val veil by animateFloatAsState(if (ready) 0.62f else 0.9f, tween(900), label = "veil")

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val height = maxHeight
    val seam = height / 2 + 84.dp
    @Composable
    fun sheet(top: Boolean) {
      Box(
          Modifier.fillMaxSize()
              .graphicsLayer {
                translationY = if (top) -seam.toPx() * part else (height - seam).toPx() * part
                // Each half of the curtain shows only its side of the seam.
                clip = true
                shape = HalfShape(top, seam.toPx())
              }) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = veil)))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x33F0454A), Color.Transparent), radius = 1100f)))
        Image(
            painterResource(R.drawable.nama_logo), null,
            Modifier.align(Alignment.Center).offset(y = (-22).dp).height(190.dp)
                .graphicsLayer {
                  alpha = logo
                  val s = 1.06f - 0.06f * logo
                  scaleX = s
                  scaleY = s
                },
            contentScale = ContentScale.Fit,
        )
      }
    }
    sheet(top = true)
    sheet(top = false)
    // The red line under the logo, and the seam the curtain parts along.
    val lineWidth = if (line <= 1f) 320.dp * line else 320.dp + (maxWidth * 1.1f - 320.dp) * (line - 1f)
    Box(
        Modifier.align(Alignment.TopCenter).offset(y = seam - 1.5.dp).width(lineWidth).height(3.dp)
            .graphicsLayer { alpha = if (leaving) (1f - part * 1.6f).coerceAtLeast(0f) else 1f }
            .shadow(12.dp, RoundedCornerShape(50), ambientColor = Theme.accent, spotColor = Theme.accent)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Theme.accent, Color(0xFFFF8A70), Theme.accent, Color.Transparent)), RoundedCornerShape(50))
            .clipToBounds())
  }
}

/** The half of the screen above (or below) the seam. */
private class HalfShape(private val top: Boolean, private val seam: Float) : androidx.compose.ui.graphics.Shape {
  override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
      androidx.compose.ui.graphics.Outline.Rectangle(
          if (top) androidx.compose.ui.geometry.Rect(0f, 0f, size.width, seam) else androidx.compose.ui.geometry.Rect(0f, seam, size.width, size.height))
}

/** How long the exit takes, from `leaving` turning true to nothing being left on screen. */
const val SPLASH_EXIT_MS = 1_150L
