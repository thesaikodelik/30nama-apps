package app.nama.quest.ui.screens.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.quest.ui.Glyph
import app.nama.quest.ui.Icon
import app.nama.quest.ui.Ink
import app.nama.quest.ui.RoundButton
import app.nama.quest.ui.WallType
import kotlinx.coroutines.delay

/**
 * The search field: a wide glass capsule that raises the headset's keyboard when pointed at and
 * clicked. Left to right in every language, as on the Mac: titles are searched by their English
 * names. [focusOnShow] asks for the keyboard as the page opens, like the Mac focusing the field.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    busy: Boolean,
    clearLabel: String,
    focusOnShow: Boolean = false,
) {
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  var focused by remember { mutableStateOf(false) }
  if (focusOnShow) {
    LaunchedEffect(Unit) {
      // After the wall's cross-fade, so the keyboard doesn't arrive over a page still fading in.
      delay(320)
      try {
        focus.requestFocus()
      } catch (_: IllegalStateException) {
        // Not attached yet (the page was left at once); the field is still a click away.
      }
    }
  }
  val shape = RoundedCornerShape(50)
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
        Modifier.width(800.dp)
            .height(70.dp)
            .clip(shape)
            .background(if (focused) Color(0x26F4F1EA) else Color(0x1AF4F1EA))
            .border(if (focused) 2.dp else 1.dp, if (focused) Ink.text1.copy(alpha = 0.55f) else Ink.hairline, shape)
            .padding(start = 26.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Ink.text2, strokeWidth = 2.5.dp)
        else Glyph(Icon.Search, Modifier.size(24.dp), Ink.text3)
      }
      BasicTextField(
          value,
          onValueChange,
          Modifier.weight(1f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
          singleLine = true,
          textStyle = WallType.body.copy(fontSize = 24.sp, lineHeight = 32.sp, color = Ink.text1, textDirection = TextDirection.Ltr),
          cursorBrush = SolidColor(Ink.accent),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
          decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
              if (value.isEmpty()) Text(placeholder, style = WallType.body.copy(fontSize = 24.sp, lineHeight = 32.sp, color = Ink.text3), maxLines = 1)
              inner()
            }
          },
      )
      // Kept in place while empty, so the text doesn't shift when it comes.
      Box(Modifier.size(46.dp)) {
        if (value.isNotEmpty()) RoundButton(Icon.Close, { onValueChange("") }, 46.dp, modifier = Modifier.semantics { contentDescription = clearLabel })
      }
    }
  }
}

/** A small text field in a capsule the size of a filter pill, with a mark in front: the director's name. */
@Composable
fun TextCapsule(value: String, onValueChange: (String) -> Unit, placeholder: String, mark: Mark, width: Dp = 340.dp) {
  val keyboard = LocalSoftwareKeyboardController.current
  var focused by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(50)
  Row(
      Modifier.width(width)
          .height(46.dp)
          .clip(shape)
          .background(if (focused) Color(0x26F4F1EA) else Color(0x1AF4F1EA))
          .border(if (focused) 2.dp else 1.dp, if (focused) Ink.text1.copy(alpha = 0.55f) else Color.Transparent, shape)
          .padding(start = 18.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    MarkGlyph(mark, Modifier.size(20.dp), Ink.text3)
    BasicTextField(
        value,
        onValueChange,
        Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = WallType.label.copy(color = Ink.text1),
        cursorBrush = SolidColor(Ink.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        decorationBox = { inner ->
          Box(contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = WallType.label.copy(color = Ink.text3), maxLines = 1)
            inner()
          }
        },
    )
    if (value.isNotEmpty()) {
      RoundButton(Icon.Close, { onValueChange("") }, 40.dp)
    }
  }
}
