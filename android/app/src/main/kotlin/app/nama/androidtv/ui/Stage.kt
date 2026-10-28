package app.nama.androidtv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nama.core.Post
import java.net.URI
import kotlinx.coroutines.delay

private data class Backdrop(val id: Int, val url: URI?, val blurhash: String?, val blurred: Boolean)

/**
 * The full-bleed backdrop behind a screen (the Apple TV's StageView). It crossfades to whatever
 * title is in focus and, where the TV can afford it, drifts slowly, so the page never feels like a
 * static grid. Static scrims keep the text column and the rows legible over any image.
 *
 * @param dim extra black over the image, 0...1, for screens where the backdrop is only ambience.
 * @param settleMs how long focus must rest on a title before the backdrop follows it.
 * @param video moving pictures over the still and under the shading (the title page's [Teaser]).
 */
@Composable
fun Stage(post: Post?, dim: Float = 0f, settleMs: Long = 350, glow: Boolean = true, tint: Color? = null, video: (@Composable () -> Unit)? = null) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  var shown by remember { mutableStateOf<Backdrop?>(null) }
  LaunchedEffect(post?.id) {
    post ?: return@LaunchedEffect
    // The first image appears at once; later ones wait for focus to settle.
    if (shown != null) delay(settleMs)
    if (shown?.id == post.id) return@LaunchedEffect
    // A title without a wide scene picture falls back to its poster, blurred into a wash of colour.
    shown = if (post.cover != null) Backdrop(post.id, post.cover, post.poster.blurhash, false) else Backdrop(post.id, post.poster.url(428.0), post.poster.blurhash, true)
  }
  val dimmed by animateFloatAsState(dim, tween(300), label = "dim")
  val glowColor by animateColorAsState(tint ?: post?.tint() ?: Theme.accent, tween(1000), label = "glow")

  Box(Modifier.fillMaxSize().background(Theme.bg0)) {
    Crossfade(shown, animationSpec = tween(700), label = "backdrop") { b ->
      if (b != null) Drifting(b)
    }
    video?.invoke()
    // Text side: near-opaque at the start edge, clear by two thirds across.
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to Theme.bg0.copy(alpha = 0.96f), 0.30f to Theme.bg0.copy(alpha = 0.78f), 0.68f to Theme.bg0.copy(alpha = 0f),
                startX = if (rtl) Float.POSITIVE_INFINITY else 0f, endX = if (rtl) 0f else Float.POSITIVE_INFINITY,
            )))
    // Rows side: solid at the bottom edge.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.38f to Theme.bg0.copy(alpha = 0f), 0.68f to Theme.bg0.copy(alpha = 0.85f), 1f to Theme.bg0)))
    Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Theme.bg0.copy(alpha = 0.55f), Color.Transparent))))
    // Ambient light: the scrimmed corner is not plain black but glows in the title's colour.
    if (glow && post != null) {
      Box(
          Modifier.fillMaxSize().drawBehind {
            val center = Offset(if (rtl) size.width else 0f, size.height)
            drawRect(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.34f), glowColor.copy(alpha = 0.10f), Color.Transparent), center, radius = size.width * 0.6f), blendMode = BlendMode.Plus)
          })
    }
    if (dimmed > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimmed)))
  }
}

/**
 * One backdrop layer. Where motion is affordable it keeps a slow Ken Burns drift; elsewhere it
 * holds still, because a picture that never stops moving has the whole screen redrawn every frame,
 * and a weak box then drops frames everywhere else.
 */
@Composable
private fun Drifting(b: Backdrop) {
  val perpetual = LocalLively.current && !LocalCalm.current
  // Four drift directions, picked by title so the same title always moves the same way.
  val directions = listOf(-28f to -10f, 20f to -10f, -20f to 8f, 0f to 12f)
  val (dx, dy) = directions[Math.floorMod(b.id, directions.size)]
  val drift = if (perpetual) {
    val t = rememberInfiniteTransition(label = "drift")
    t.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "t").value
  } else 0f
  RemoteImage(
      // Before Android 12 there is no blur to draw with; the blur code is already that wash.
      url = if (b.blurred && android.os.Build.VERSION.SDK_INT < 31) null else b.url,
      blurhash = b.blurhash,
      pixelWidth = if (b.blurred) 320 else 1920,
      fade = false,
      modifier = Modifier.fillMaxSize().clipToBounds()
          .then(if (b.blurred) Modifier.blur(60.dp) else Modifier)
          .graphicsLayer {
            if (perpetual) {
              val s = 1.04f + 0.08f * drift
              scaleX = s
              scaleY = s
              translationX = dx * drift * density
              translationY = dy * drift * density
            }
          },
  )
}
