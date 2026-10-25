package app.nama.quest.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nama.quest.data.Tile

/** Hover and press state for anything the ray can point at. */
class Pointed(val source: MutableInteractionSource, val hovered: Boolean, val pressed: Boolean)

@Composable
fun rememberPointed(): Pointed {
  val source = remember { MutableInteractionSource() }
  val hovered by source.collectIsHoveredAsState()
  val pressed by source.collectIsPressedAsState()
  return Pointed(source, hovered, pressed)
}

/**
 * Something you can point at and click: lifts toward the viewer when the ray rests on it and sinks a
 * little when pressed, so a controller or a finger gets an answer before the click lands.
 */
fun Modifier.pointable(p: Pointed, onClick: () -> Unit, lift: Float = 1.06f): Modifier =
    this.graphicsLayer {
          val s = if (p.pressed) 0.97f else if (p.hovered) lift else 1f
          scaleX = s
          scaleY = s
        }
        .hoverable(p.source)
        .clickable(interactionSource = p.source, indication = null, onClick = onClick)

/** A poster or still: the image when there is one, otherwise art drawn from the title's colours. */
@Composable
fun Artwork(tile: Tile, landscape: Boolean, modifier: Modifier = Modifier, decodeWidth: Int = 360) {
  val url = if (landscape) tile.backdrop ?: tile.poster else tile.poster
  val image = rememberImage(url, decodeWidth)
  Box(modifier.background(Ink.surface)) {
    if (image != null) {
      Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      DrawnArt(tile, landscape)
    }
  }
}

@Composable
private fun BoxScope.DrawnArt(tile: Tile, landscape: Boolean) {
  val deep = Color(tile.art?.deep ?: 0xFF20242F)
  val light = Color(tile.art?.light ?: 0xFF5A6070)
  Canvas(Modifier.matchParentSize()) {
    drawRect(Brush.verticalGradient(listOf(deep, deep.copy(red = deep.red * 0.45f, green = deep.green * 0.45f, blue = deep.blue * 0.45f))))
    drawCircle(
        Brush.radialGradient(listOf(light.copy(alpha = 0.95f), light.copy(alpha = 0f)), center = Offset(size.width * 0.62f, size.height * 0.36f), radius = size.maxDimension * 0.62f),
        radius = size.maxDimension * 0.62f,
        center = Offset(size.width * 0.62f, size.height * 0.36f),
    )
    val ridge = Path().apply {
      moveTo(0f, size.height * 0.68f)
      lineTo(size.width * 0.35f, size.height * 0.56f)
      lineTo(size.width * 0.62f, size.height * 0.66f)
      lineTo(size.width, size.height * 0.52f)
      lineTo(size.width, size.height)
      lineTo(0f, size.height)
      close()
    }
    drawPath(ridge, Color.Black.copy(alpha = 0.5f))
    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
  }
  if (!landscape) {
    Text(
        tile.title,
        style = Type.heading,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
    )
  }
}

/** Rating chip in the poster corner. */
@Composable
fun RatingChip(text: String, modifier: Modifier = Modifier) {
  Row(
      modifier.clip(RoundedCornerShape(50)).background(Color(0xB307080B)).padding(horizontal = 9.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Glyph(Icon.Star, Modifier.size(12.dp), Ink.star)
    Text(text, style = Type.caption.copy(color = Ink.text1))
  }
}

/** Thin progress line along the bottom of a card; grows from the reading start. */
@Composable
fun ProgressLine(value: Float, modifier: Modifier = Modifier) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Canvas(modifier.height(5.dp)) {
    drawRect(Color.White.copy(alpha = 0.25f))
    val w = size.width * value.coerceIn(0f, 1f)
    drawRect(Ink.accent, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height))
  }
}

/** The app's buttons: a filled pill for the main action, glass pills for the rest. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: Icon? = null,
    enabled: Boolean = true,
) {
  val p = rememberPointed()
  val glow by animateDpAsState(if (p.hovered && enabled) 18.dp else 0.dp, spring(stiffness = 500f), label = "glow")
  val fill = if (primary) Ink.accent else Color(0x1FF4F1EA)
  val ink = if (enabled) Color.White else Ink.text3
  Row(
      modifier
          .drawBehind { if (glow > 0.dp) drawGlow(if (primary) Ink.accent else Ink.text1, glow.toPx(), primary) }
          .then(if (enabled) Modifier.pointable(p, onClick, 1.04f) else Modifier)
          .clip(RoundedCornerShape(50))
          .background(if (p.hovered && !primary && enabled) Color(0x33F4F1EA) else fill)
          .padding(horizontal = 26.dp, vertical = 13.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (icon != null) Glyph(icon, Modifier.size(18.dp), ink)
    Text(text, style = Type.button.copy(color = ink))
  }
}

/** A round icon button, for transport controls. */
@Composable
fun RoundButton(icon: Icon, onClick: () -> Unit, size: Dp = 64.dp, solid: Boolean = false, modifier: Modifier = Modifier) {
  val p = rememberPointed()
  val ring by animateFloatAsState(if (p.hovered) 1f else 0f, label = "ring")
  Box(
      modifier
          .size(size)
          .pointable(p, onClick, 1.08f)
          .clip(CircleShape)
          .background(if (solid) Ink.text1 else Color(0x1FF4F1EA).copy(alpha = 0.12f + 0.14f * ring))
          .border(1.dp, Ink.hairline.copy(alpha = 0.14f + 0.4f * ring), CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    Glyph(icon, Modifier.size(size * 0.38f), if (solid) Ink.focusInk else Ink.text1)
  }
}

private fun DrawScope.drawGlow(color: Color, radius: Float, strong: Boolean) {
  val r = size.height / 2
  for (i in 1..4) {
    val grow = radius * i / 4f
    drawRoundRect(
        color.copy(alpha = (if (strong) 0.16f else 0.08f) / i),
        topLeft = Offset(-grow, -grow),
        size = Size(size.width + grow * 2, size.height + grow * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r + grow),
    )
  }
}

enum class Icon {
  Play,
  Pause,
  Back,
  Plus,
  Star,
  Rewind,
  Forward,
  Subtitles,
  Close,
  Check,
}

/** Icons drawn as paths: crisp at any panel density, and no icon library in the APK. */
@Composable
fun Glyph(icon: Icon, modifier: Modifier = Modifier, color: Color = Ink.text1) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
    when (icon) {
      Icon.Play -> drawPath(Path().apply { moveTo(w * 0.22f, h * 0.12f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.22f, h * 0.88f); close() }, color)
      Icon.Pause -> {
        drawRoundRect(color, Offset(w * 0.18f, h * 0.12f), Size(w * 0.22f, h * 0.76f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
        drawRoundRect(color, Offset(w * 0.6f, h * 0.12f), Size(w * 0.22f, h * 0.76f), androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
      }
      Icon.Back -> {
        // Points toward the reading start: left in English, right in Persian and Arabic.
        val a = if (rtl) w * 0.32f else w * 0.68f
        val b = if (rtl) w * 0.68f else w * 0.32f
        drawPath(Path().apply { moveTo(a, h * 0.15f); lineTo(b, h * 0.5f); lineTo(a, h * 0.85f) }, color, style = stroke)
      }
      Icon.Plus -> {
        drawLine(color, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.85f), w * 0.12f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), w * 0.12f, StrokeCap.Round)
      }
      Icon.Star -> {
        val path = Path()
        for (i in 0 until 10) {
          val r = if (i % 2 == 0) w * 0.5f else w * 0.21f
          val a = Math.toRadians((-90 + i * 36).toDouble())
          val x = w / 2 + (r * Math.cos(a)).toFloat()
          val y = h / 2 + (r * Math.sin(a)).toFloat()
          if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
      }
      Icon.Rewind, Icon.Forward -> {
        // Ten-second jumps: a curved arrow. Time runs right-to-left in RTL, like the timeline.
        val forward = (icon == Icon.Forward) != rtl
        drawArc(color, if (forward) -60f else -120f, if (forward) 280f else -280f, false, topLeft = Offset(w * 0.12f, h * 0.12f), size = Size(w * 0.76f, h * 0.76f), style = Stroke(w * 0.1f, cap = StrokeCap.Round))
        val tipX = if (forward) w * 0.72f else w * 0.28f
        val dir = if (forward) 1f else -1f
        drawPath(Path().apply { moveTo(tipX - dir * w * 0.02f, h * 0.02f); lineTo(tipX + dir * w * 0.2f, h * 0.17f); lineTo(tipX - dir * w * 0.04f, h * 0.3f); close() }, color)
      }
      Icon.Subtitles -> {
        drawRoundRect(color, Offset(w * 0.08f, h * 0.2f), Size(w * 0.84f, h * 0.6f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = Stroke(w * 0.09f))
        drawLine(color, Offset(w * 0.24f, h * 0.45f), Offset(w * 0.5f, h * 0.45f), w * 0.09f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.6f, h * 0.45f), Offset(w * 0.76f, h * 0.45f), w * 0.09f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.24f, h * 0.62f), Offset(w * 0.64f, h * 0.62f), w * 0.09f, StrokeCap.Round)
      }
      Icon.Close -> {
        drawLine(color, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.8f, h * 0.8f), w * 0.12f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.8f, h * 0.2f), Offset(w * 0.2f, h * 0.8f), w * 0.12f, StrokeCap.Round)
      }
      Icon.Check -> drawPath(Path().apply { moveTo(w * 0.15f, h * 0.52f); lineTo(w * 0.42f, h * 0.78f); lineTo(w * 0.86f, h * 0.24f) }, color, style = stroke)
    }
  }
}
