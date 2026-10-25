package app.nama.quest.ui.title

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Type
import app.nama.quest.ui.pointable
import app.nama.quest.ui.rememberPointed

/**
 * What a tap on star [n] makes of the rating. Halves by pointing at half a star are fiddly with a
 * ray, so: a star gives that many, the same star again takes half away, and once more clears it.
 */
fun nextRating(current: Double?, n: Int): Double? =
    when (current) {
      n.toDouble() -> n - 0.5
      n - 0.5 -> null
      else -> n.toDouble()
    }

/** "Your rating" and five stars in one capsule, the height of the buttons beside it. */
@Composable
fun RatingCapsule(label: String, value: Double?, enabled: Boolean, onSet: (Double?) -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(50)
  Row(
      modifier
          .height(60.dp)
          .alpha(if (enabled) 1f else 0.45f)
          .clip(shape)
          .background(Color(0x1AF4F1EA))
          .border(1.dp, Ink.hairline, shape)
          .padding(start = 22.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(label, style = Type.label.copy(fontSize = 16.sp, color = Ink.text2), maxLines = 1)
    StarRater(value, enabled, onSet)
  }
}

/** Five 56 dp stars. Pointing at one previews, faintly, what a tap would make of the rating. */
@Composable
fun StarRater(value: Double?, enabled: Boolean, onSet: (Double?) -> Unit, tint: Color = ImdbGold) {
  val pointed = List(5) { rememberPointed() }
  val over = pointed.indexOfFirst { it.hovered } + 1
  val preview = if (enabled && over > 0) nextRating(value, over) else value
  val previewing = enabled && over > 0
  Row {
    for (n in 1..5) {
      val p = pointed[n - 1]
      val shown = preview ?: 0.0
      val fill = if (shown >= n) 1f else if (shown >= n - 0.5) 0.5f else 0f
      Box(
          Modifier.size(56.dp).then(if (enabled) Modifier.pointable(p, { onSet(nextRating(value, n)) }, 1.14f) else Modifier),
          contentAlignment = Alignment.Center,
      ) {
        val ink by animateFloatAsState(if (previewing) 0.6f else 1f, label = "star")
        StarShape(fill, tint.copy(alpha = ink), Modifier.size(32.dp))
      }
    }
  }
}

/** A star filled whole, by its leading half, or not at all (an outline). */
@Composable
private fun StarShape(fill: Float, tint: Color, modifier: Modifier) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Canvas(modifier) {
    val path = starPath(size)
    if (fill < 1f) drawPath(path, Ink.text3, style = Stroke(size.width * 0.07f, join = StrokeJoin.Round))
    if (fill > 0f) {
      val w = size.width
      val left = if (fill < 1f && rtl) w / 2 else 0f
      val right = if (fill < 1f && !rtl) w / 2 else w
      clipRect(left = left, right = right) { drawPath(path, tint) }
    }
  }
}

private fun starPath(size: Size): Path {
  val w = size.width
  val h = size.height
  val path = Path()
  for (i in 0 until 10) {
    val r = if (i % 2 == 0) w * 0.5f else w * 0.22f
    val a = Math.toRadians((-90 + i * 36).toDouble())
    val x = w / 2 + (r * Math.cos(a)).toFloat()
    val y = h * 0.53f + (r * Math.sin(a)).toFloat()
    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  path.close()
  return path
}
