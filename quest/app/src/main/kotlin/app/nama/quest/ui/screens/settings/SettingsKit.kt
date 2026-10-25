@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nama.quest.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.quest.ui.Glyph
import app.nama.quest.ui.Icon
import app.nama.quest.ui.Ink
import app.nama.quest.ui.WallType
import app.nama.quest.ui.glass
import app.nama.quest.ui.pointable
import app.nama.quest.ui.rememberPointed

/*
 * The settings page's parts, after the Mac's SettingsView: groups of rows on glass, the label on
 * the reading start and the control on the end. Popup menus can't open from a panel, so every
 * choice is laid out inline as segments, and a whole toggle row is the target, not just its switch.
 */

private val RowShape = RoundedCornerShape(22.dp)

/** A titled block of rows on glass. */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, style = WallType.label.copy(fontSize = 17.sp, color = Ink.text3), modifier = Modifier.padding(horizontal = 10.dp))
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), fill = Color(0x2E161922)).padding(vertical = 6.dp, horizontal = 6.dp),
        content = content,
    )
  }
}

/** A faint line between rows of one group. */
@Composable
fun RowDivider() {
  Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Color(0x12F4F1EA)))
}

/**
 * Label (and an optional quiet line under it) on the start side, control on the end. The control
 * is measured first, but never takes more than leaves [minLabel] for the label, so a wide choice
 * wraps instead of squeezing the label away. [onClick] makes the whole row the target.
 */
@Composable
fun SettingRow(
    label: String,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    minLabel: Dp = 150.dp,
    control: @Composable () -> Unit,
) {
  val p = rememberPointed()
  val base =
      Modifier.fillMaxWidth()
          .heightIn(min = 68.dp)
          .then(if (onClick != null) Modifier.pointable(p, onClick, 1.01f).clip(RowShape).background(if (p.hovered) Color(0x14F4F1EA) else Color.Transparent) else Modifier)
          .padding(horizontal = 18.dp, vertical = 10.dp)
  Layout(
      content = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(label, style = WallType.label.copy(fontWeight = FontWeight.Medium))
          caption?.let { Text(it, style = WallType.caption) }
        }
        Box { control() }
      },
      modifier = base,
  ) { measurables, constraints ->
    val gap = 18.dp.roundToPx()
    val width = constraints.maxWidth
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val c = measurables[1].measure(loose.copy(maxWidth = (width - minLabel.roundToPx() - gap).coerceAtLeast(0)))
    val l = measurables[0].measure(loose.copy(maxWidth = (width - c.width - gap).coerceAtLeast(0)))
    val height = maxOf(l.height, c.height, constraints.minHeight)
    layout(width, height) {
      // placeRelative mirrors both for Persian and Arabic.
      l.placeRelative(0, (height - l.height) / 2)
      c.placeRelative(width - c.width, (height - c.height) / 2)
    }
  }
}

/**
 * The Mac's dropdown, laid out: every option is a segment in one capsule, the chosen one white.
 * Wraps onto a second line rather than overflowing in a long translation.
 */
@Composable
fun <T> Segments(choices: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
  FlowRow(
      Modifier.clip(RoundedCornerShape(27.dp)).background(Color(0x14F4F1EA)).border(1.dp, Color(0x14F4F1EA), RoundedCornerShape(27.dp)).padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    choices.forEach { c ->
      val on = c == selected
      val p = rememberPointed()
      Box(
          Modifier.heightIn(min = 46.dp)
              .pointable(p, { onPick(c) }, 1.04f)
              .clip(RoundedCornerShape(50))
              .background(if (on) Ink.text1 else if (p.hovered) Color(0x24F4F1EA) else Color.Transparent)
              .padding(horizontal = 18.dp, vertical = 8.dp),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            label(c),
            style = WallType.label.copy(fontSize = 18.sp, color = if (on) Ink.focusInk else Ink.text1.copy(alpha = if (p.hovered) 1f else 0.78f)),
            maxLines = 1,
        )
      }
    }
  }
}

/** Colour choices as swatches, the chosen one ringed; the name is in the row's caption. */
@Composable
fun <T> Swatches(choices: List<T>, selected: T, color: (T) -> Color, onPick: (T) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    choices.forEach { c ->
      val on = c == selected
      val p = rememberPointed()
      Box(
          Modifier.size(50.dp)
              .pointable(p, { onPick(c) }, 1.1f)
              .clip(CircleShape)
              .border(2.dp, if (on) Ink.text1 else if (p.hovered) Ink.text1.copy(alpha = 0.4f) else Color.Transparent, CircleShape)
              .padding(6.dp)
              .clip(CircleShape)
              .background(color(c)),
          contentAlignment = Alignment.Center,
      ) {
        if (on) Glyph(Icon.Check, Modifier.size(18.dp), Ink.focusInk)
      }
    }
  }
}

/** An on/off switch, drawn: the knob rests on the reading end when on. */
@Composable
fun Switch(on: Boolean, calm: Boolean) {
  val t by animateFloatAsState(if (on) 1f else 0f, if (calm) snap() else spring(dampingRatio = 0.72f, stiffness = 520f), label = "switch")
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Canvas(Modifier.width(66.dp).height(40.dp)) {
    val r = size.height / 2
    val off = Color(0x33F4F1EA)
    val track = androidx.compose.ui.graphics.lerp(off, Ink.accent, t)
    drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    val knob = r - 4.dp.toPx()
    val from = r
    val to = size.width - r
    val x = if (rtl) to - (to - from) * t else from + (to - from) * t
    drawCircle(Color.Black.copy(alpha = 0.25f), knob + 1.dp.toPx(), Offset(x, r + 1.dp.toPx()))
    drawCircle(Color.White, knob, Offset(x, r))
  }
}

/** A toggle row: the whole row flips it. */
@Composable
fun ToggleRow(label: String, on: Boolean, calm: Boolean, caption: String? = null, onChange: (Boolean) -> Unit) {
  SettingRow(label, caption, onClick = { onChange(!on) }) { Switch(on, calm) }
}

/** The capsule every text field sits in; brighter edge while typing. */
@Composable
private fun FieldCapsule(focused: Boolean, rim: Color, width: Dp, content: @Composable () -> Unit) {
  Box(
      Modifier.width(width)
          .height(54.dp)
          .clip(RoundedCornerShape(50))
          .background(Color(0x14F4F1EA))
          .border(1.5.dp, if (focused) Ink.text1.copy(alpha = 0.55f) else rim, RoundedCornerShape(50)),
      contentAlignment = Alignment.CenterStart,
  ) {
    content()
  }
}

/** Free text through the headset's system keyboard, kept as it is typed. */
@Composable
fun TextBox(value: String, onChange: (String) -> Unit, width: Dp = 320.dp) {
  val source = remember { MutableInteractionSource() }
  val focused by source.collectIsFocusedAsState()
  val focus = LocalFocusManager.current
  FieldCapsule(focused, Ink.hairline, width) {
    BasicTextField(
        value,
        onChange,
        Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        textStyle = WallType.label.copy(fontWeight = FontWeight.Medium, textDirection = TextDirection.Content),
        singleLine = true,
        cursorBrush = SolidColor(Ink.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        interactionSource = source,
    )
  }
}

/**
 * A key the user makes on another site, as on the Mac: kept as soon as it is typed, masked unless
 * asked, with an eye to check it and × to remove it; under it, either "Saved on this headset" with
 * the app's red tick, or what the key is for.
 */
@Composable
fun KeyField(label: String, hint: String, value: String, onChange: (String) -> Unit, showLabel: String, hideLabel: String, removeLabel: String, savedLabel: String) {
  var shown by rememberSaveable { mutableStateOf(false) }
  val saved = value.isNotBlank()
  val source = remember { MutableInteractionSource() }
  val focused by source.collectIsFocusedAsState()
  val focus = LocalFocusManager.current
  Column {
    SettingRow(label, minLabel = 130.dp) {
      // A key reads left to right in every language.
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        FieldCapsule(focused, if (saved) Ink.accent.copy(alpha = 0.55f) else Ink.hairline, 350.dp) {
          Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value,
                onChange,
                Modifier.weight(1f),
                textStyle = WallType.label.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 17.sp, textDirection = TextDirection.Ltr),
                singleLine = true,
                cursorBrush = SolidColor(Ink.accent),
                visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                interactionSource = source,
            )
            if (saved) {
              SmallIconButton({ shown = !shown }, if (shown) hideLabel else showLabel) { color -> EyeGlyph(open = !shown, color) }
              SmallIconButton({
                onChange("")
                shown = false
              }, removeLabel) { color -> Glyph(Icon.Close, Modifier.size(16.dp), color) }
            }
          }
        }
      }
    }
    Row(
        Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (saved) {
        // Red on the tick only: the words in the usual colour, so it doesn't read as an error.
        Box(Modifier.size(22.dp).clip(CircleShape).background(Ink.accent), contentAlignment = Alignment.Center) {
          Glyph(Icon.Check, Modifier.size(12.dp), Color.White)
        }
        Text(savedLabel, style = WallType.caption.copy(color = Ink.text2))
      } else {
        Text(hint, style = WallType.caption)
      }
    }
  }
}

/** A 44 dp round target for the eye and the ×; [label] is spoken by accessibility services. */
@Composable
private fun SmallIconButton(onClick: () -> Unit, label: String, glyph: @Composable (Color) -> Unit) {
  val p = rememberPointed()
  Box(
      Modifier.size(44.dp)
          .pointable(p, onClick, 1.1f)
          .clip(CircleShape)
          .background(if (p.hovered) Color(0x24F4F1EA) else Color.Transparent)
          .semantics { contentDescription = label },
      contentAlignment = Alignment.Center,
  ) {
    glyph(if (p.hovered) Ink.text1 else Ink.text3)
  }
}

/** An eye, crossed out when [open] is false; drawn like the other glyphs. */
@Composable
private fun EyeGlyph(open: Boolean, color: Color) {
  Canvas(Modifier.size(22.dp)) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
    val lid = Path().apply {
      moveTo(w * 0.06f, h * 0.5f)
      quadraticBezierTo(w * 0.5f, h * 0.02f, w * 0.94f, h * 0.5f)
      quadraticBezierTo(w * 0.5f, h * 0.98f, w * 0.06f, h * 0.5f)
      close()
    }
    drawPath(lid, color, style = stroke)
    drawCircle(color, w * 0.13f, Offset(w / 2, h / 2))
    if (!open) drawLine(color, Offset(w * 0.14f, h * 0.12f), Offset(w * 0.86f, h * 0.88f), w * 0.09f, StrokeCap.Round)
  }
}

/** A plain value on the end side, for facts like the version. */
@Composable
fun FactRow(label: String, value: String) {
  SettingRow(label) {
    Text(value, style = WallType.label.copy(fontWeight = FontWeight.Medium, color = Ink.text3), maxLines = 1)
  }
}
