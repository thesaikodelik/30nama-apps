@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.nama.quest.ui.title

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.Post
import app.nama.core.S
import app.nama.core.TitleUpdate
import app.nama.quest.ui.Directional
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Pointed
import app.nama.quest.ui.Type
import app.nama.quest.ui.pointable
import app.nama.quest.ui.rememberPointed
import kotlinx.coroutines.delay

/**
 * Like `pointable`, with a held trigger as a second action: the headset's stand-in for the Mac's
 * right-click menu. The press still shows, so the hold has an answer from its first moment.
 */
fun Modifier.holdable(p: Pointed, onClick: () -> Unit, onHold: (() -> Unit)?, lift: Float = 1.02f): Modifier =
    this.graphicsLayer {
          val s = if (p.pressed) 0.98f else if (p.hovered) lift else 1f
          scaleX = s
          scaleY = s
        }
        .hoverable(p.source)
        .combinedClickable(interactionSource = p.source, indication = null, onLongClick = onHold, onClick = onClick)

/** A round button that keeps the title on one of the user's 30nama lists: filled in when it is. */
@Composable
fun ListToggle(heart: Boolean, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val p = rememberPointed()
  val ink by animateColorAsState(if (on) Ink.accent else Ink.text1, label = "ink")
  val fill by animateColorAsState(
      if (on) Ink.accent.copy(alpha = 0.16f) else Color(0xFFF4F1EA).copy(alpha = if (p.hovered && enabled) 0.22f else 0.1f),
      label = "fill",
  )
  Box(
      Modifier.size(60.dp)
          .alpha(if (enabled) 1f else 0.45f)
          .then(if (enabled) Modifier.pointable(p, onClick, 1.08f) else Modifier)
          .clip(CircleShape)
          .background(fill)
          .border(1.dp, if (on) Ink.accent.copy(alpha = 0.6f) else Ink.hairline, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    ListGlyph(heart, filled = on, color = ink, modifier = Modifier.size(24.dp))
  }
}

/** Heart or bookmark, filled when on and drawn as an outline when off, as SF Symbols do on the Mac. */
@Composable
private fun ListGlyph(heart: Boolean, filled: Boolean, color: Color, modifier: Modifier) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val path =
        if (heart) {
          Path().apply {
            moveTo(w * 0.5f, h * 0.86f)
            cubicTo(w * 0.1f, h * 0.58f, w * 0.02f, h * 0.3f, w * 0.26f, h * 0.18f)
            cubicTo(w * 0.4f, h * 0.12f, w * 0.48f, h * 0.22f, w * 0.5f, h * 0.3f)
            cubicTo(w * 0.52f, h * 0.22f, w * 0.6f, h * 0.12f, w * 0.74f, h * 0.18f)
            cubicTo(w * 0.98f, h * 0.3f, w * 0.9f, h * 0.58f, w * 0.5f, h * 0.86f)
            close()
          }
        } else {
          Path().apply {
            moveTo(w * 0.26f, h * 0.1f)
            lineTo(w * 0.74f, h * 0.1f)
            lineTo(w * 0.74f, h * 0.9f)
            lineTo(w * 0.5f, h * 0.7f)
            lineTo(w * 0.26f, h * 0.9f)
            close()
          }
        }
    if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(w * 0.09f, join = StrokeJoin.Round))
  }
}

/**
 * What 30nama lately added or mended on the title, one change at a time with its day. It moves on
 * every 4 s only while [running] (the title arrangement is up), so a hidden panel never redraws.
 */
@Composable
fun UpdatesTicker(updates: List<TitleUpdate>, loc: Loc, running: Boolean, modifier: Modifier = Modifier) {
  val list = updates.take(6)
  if (list.isEmpty()) return
  var index by remember(list) { mutableIntStateOf(0) }
  LaunchedEffect(list, running) {
    if (!running || list.size < 2) return@LaunchedEffect
    while (true) {
      delay(4_000)
      index = (index + 1) % list.size
    }
  }
  AnimatedContent(
      targetState = index.coerceAtMost(list.size - 1),
      transitionSpec = {
        (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }) togetherWith
            (fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 2 })
      },
      modifier = modifier,
      label = "ticker",
  ) { i ->
    val u = list[i]
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      u.day(persian = loc.lang == Lang.fa)?.let { day ->
        Directional(false) { Text(loc.num(day), style = Type.caption.copy(fontSize = 15.sp), maxLines = 1) }
      }
      Text(loc.num(u.title), style = Type.label.copy(fontSize = 15.sp), maxLines = 1)
      u.content?.let {
        Text(
            loc.num(it),
            style = Type.caption.copy(fontSize = 15.sp, color = Ink.text2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
      }
    }
  }
}

/** True when [Facts] has anything to show. */
fun hasFacts(post: Post) =
    listOf(post.directors, post.creators, post.writers, post.networks, post.countries, post.otherLanguages).any { it.isNotEmpty() } ||
        post.language != null ||
        post.budget != null

/**
 * Director, creator, writer, network, country, languages and budget, as on the Mac. Each director
 * opens their other titles, newest first.
 */
@Composable
fun Facts(post: Post, loc: Loc, onDirector: (String) -> Unit, modifier: Modifier = Modifier) {
  val join = if (loc.lang == Lang.en) ", " else "، "
  val rows =
      listOf(
              S.director to post.directors,
              S.creator to post.creators,
              S.writer to post.writers,
              S.network to post.networks,
              S.country to post.countries,
              S.originalLanguage to listOfNotNull(post.language),
              S.otherLanguages to post.otherLanguages,
              S.budget to listOfNotNull(post.budget),
          )
          .filter { it.second.isNotEmpty() }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    rows.forEach { (key, names) ->
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(loc(key), style = Type.label.copy(fontSize = 15.sp, color = Ink.text3), modifier = Modifier.width(130.dp).padding(top = if (key == S.director) 10.dp else 0.dp))
        if (key == S.director) {
          FlowRow(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            names.forEachIndexed { i, name ->
              PersonLink(name) { onDirector(name) }
              // The comma stays outside the link, so only the name lights up.
              if (i < names.size - 1) {
                Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                  Text(join.trimEnd(), style = Type.label.copy(fontSize = 17.sp), modifier = Modifier.padding(end = 8.dp))
                }
              }
            }
          }
        } else {
          Text(names.joinToString(join), style = Type.label.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

/** A name that opens the titles of the person it names: lit and underlined under the ray, like a link. */
@Composable
fun PersonLink(name: String, onClick: () -> Unit) {
  val p = rememberPointed()
  val ink by animateColorAsState(if (p.hovered) Ink.accent else Ink.text1, label = "link")
  Box(Modifier.heightIn(min = 44.dp).pointable(p, onClick, 1.04f), contentAlignment = Alignment.CenterStart) {
    Text(
        name,
        style = Type.label.copy(fontSize = 17.sp, color = ink, textDecoration = if (p.hovered) TextDecoration.Underline else TextDecoration.None),
        modifier = Modifier.padding(horizontal = 2.dp),
    )
  }
}
