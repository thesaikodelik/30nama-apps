package app.nama.quest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.size
import app.nama.core.S
import app.nama.quest.AppModel

/** Transport controls under the screen. The scene fades this panel out while nobody uses it. */
@Composable
fun ControlsPanel(model: AppModel, onActivity: () -> Unit) {
  val lang by model.lang.collectAsState()
  val state by model.video.state.collectAsState()
  val stream by model.stream.collectAsState()
  val loc = model.loc
  Directional(lang.isRTL) {
    Column(
        Modifier.fillMaxSize()
            .glass(RoundedCornerShape(40.dp))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(); onActivity() } } }
            .padding(horizontal = 40.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stream?.title.orEmpty(), style = Type.heading, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
        stream?.episodeLine?.let { Text(it, style = Type.label.copy(color = Ink.text2)) }
        Spacer(Modifier.weight(1f))
        if (state.buffering) Text(loc(S.loading), style = Type.caption)
      }
      Timeline(state.position, state.duration, onSeek = { model.video.seekTo(it); onActivity() })
      // The offers of the moment: skip the intro or recap, or go on to the next episode during
      // the credits (the pill fills as they run, and autoplay takes over when they end).
      val skip = model.skipTarget(state.position)
      val credits = model.creditsProgress(state.position, state.duration)
      if (skip != null || credits != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
          if (skip != null) {
            Pill(loc(if (skip.first) S.skipIntro else S.skipRecap), { model.video.seekExact(skip.second); onActivity() }, primary = true, icon = Icon.Forward)
          } else if (credits != null) {
            NextPill(loc(S.nextEpisode), credits) { model.playNext() }
          }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(clock(model, state.position), style = Type.label.copy(color = Ink.text2, textDirection = TextDirection.Ltr), modifier = Modifier.width(110.dp))
        Spacer(Modifier.weight(1f))
        RoundButton(Icon.Rewind, { model.video.seekBy(-10.0); onActivity() }, 58.dp)
        Spacer(Modifier.width(22.dp))
        RoundButton(if (state.paused) Icon.Play else Icon.Pause, { model.video.setPaused(!state.paused); onActivity() }, 76.dp, solid = true)
        Spacer(Modifier.width(22.dp))
        RoundButton(Icon.Forward, { model.video.seekBy(10.0); onActivity() }, 58.dp)
        val next by model.next.collectAsState()
        if (next != null) {
          Spacer(Modifier.width(22.dp))
          RoundButton(Icon.Next, { model.playNext(); onActivity() }, 58.dp)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.width(220.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
          val subsOn = state.subtitleLanguage != null
          val hasSubs = stream?.subtitles?.isNotEmpty() == true
          if (hasSubs) {
            RoundButton(Icon.Subtitles, {
              val tracks = stream?.subtitles.orEmpty()
              // Cycle: off → each language → off.
              val i = tracks.indexOfFirst { it.language == state.subtitleLanguage }
              model.video.selectSubtitle(if (!subsOn) tracks.first().language else tracks.getOrNull(i + 1)?.language)
              onActivity()
            }, 52.dp)
          }
          RoundButton(Icon.Close, { model.stop() }, 52.dp)
        }
        Text(clock(model, state.duration), style = Type.label.copy(color = Ink.text3, textDirection = TextDirection.Ltr), modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
      }
    }
  }
}

/** The red "Next episode" pill of the credits, filling from the reading start as they run. */
@Composable
private fun NextPill(text: String, progress: Float, onClick: () -> Unit) {
  val p = rememberPointed()
  Box(
      Modifier.pointable(p, onClick, 1.04f).clip(RoundedCornerShape(50)).background(Ink.accent.copy(alpha = 0.55f)),
      contentAlignment = Alignment.CenterStart,
  ) {
    Box(Modifier.matchParentSize().drawWithContent {
      val rtl = layoutDirection == LayoutDirection.Rtl
      val w = size.width * progress
      drawRect(Ink.accent, topLeft = Offset(if (rtl) size.width - w else 0f, 0f), size = Size(w, size.height))
    })
    Row(Modifier.padding(horizontal = 26.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Glyph(Icon.Next, Modifier.size(18.dp), Color.White)
      Text(text, style = Type.button.copy(color = Color.White))
    }
  }
}

/** The timeline grows from the reading start, like the Mac player's. Tap anywhere to jump there. */
@Composable
private fun Timeline(position: Double, duration: Double, onSeek: (Double) -> Unit) {
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val fraction = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
  Canvas(
      Modifier.fillMaxWidth().height(28.dp).pointerInput(duration, rtl) {
        detectTapGestures { at ->
          if (duration > 0) {
            val f = (at.x / size.width).coerceIn(0f, 1f)
            onSeek(duration * (if (rtl) 1f - f else f))
          }
        }
      },
  ) {
    val y = size.height / 2
    val h = 8.dp.toPx()
    drawRoundRect(Color(0x33F4F1EA), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h))
    val w = size.width * fraction
    val start = if (rtl) size.width - w else 0f
    drawRoundRect(Ink.accent, Offset(start, y - h / 2), Size(w, h), CornerRadius(h))
    drawCircle(Color.White, 11.dp.toPx(), Offset(if (rtl) size.width - w else w, y))
  }
}

private fun clock(model: AppModel, seconds: Double): String {
  val s = seconds.toLong().coerceAtLeast(0)
  val text = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
  return model.loc.num(text)
}

/** The toast: one short line in a small glass capsule. */
@Composable
fun ToastPanel(model: AppModel) {
  val lang by model.lang.collectAsState()
  val text by model.toast.collectAsState()
  Directional(lang.isRTL) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      text?.let {
        Text(
            it,
            style = Type.label.copy(fontSize = 20.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 30.dp, vertical = 14.dp),
        )
      }
    }
  }
}

/** Subtitles on their own panel just in front of the screen; mpv can't draw them on a direct-to-compositor surface. */
@Composable
fun SubtitlePanel(model: AppModel) {
  val state by model.video.state.collectAsState()
  val text = state.subtitle.trim()
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    if (text.isNotEmpty()) {
      Text(
          text,
          textAlign = TextAlign.Center,
          style =
              Type.title.copy(
                  fontSize = 40.sp,
                  lineHeight = 54.sp,
                  color = Color.White,
                  shadow = Shadow(Color.Black, Offset(0f, 2f), 10f),
                  textDirection = TextDirection.Content,
              ),
          modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x8C000000)).padding(horizontal = 26.dp, vertical = 8.dp),
      )
    }
  }
}
