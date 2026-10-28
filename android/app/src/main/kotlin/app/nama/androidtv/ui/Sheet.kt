package app.nama.androidtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text

/** One choice in a sheet. */
data class SheetAction(val title: String, val icon: ImageVector? = null, val primary: Boolean = false, val selected: Boolean = false, val run: () -> Unit)

/** A small set of choices over the page, as a held select brings up a context menu on the Apple TV. */
data class SheetSpec(val title: String?, val actions: List<SheetAction>, val message: String? = null, val focus: Int = 0)

/** Shows the sheet [AppModel.sheet] asks for; the rest of the screen cannot take focus meanwhile. */
class Sheets {
  var shown by mutableStateOf<SheetSpec?>(null)
    private set

  /** A web page to open on the phone (a TV has no browser to speak of): its QR code, in a card. */
  var link by mutableStateOf<LinkSpec?>(null)

  fun show(spec: SheetSpec) {
    shown = spec
  }

  fun dismiss() {
    shown = null
  }
}

/** Keeps focus out of whatever lies under an overlay, and lets it back in when the overlay goes. */
fun Modifier.blockFocus(blocked: Boolean): Modifier = focusProperties { if (blocked) onEnter = { cancelFocusChange() } }

@Composable
fun SheetHost(sheets: Sheets) {
  val spec = sheets.shown ?: return
  val first = remember(spec) { FocusRequester() }
  BackHandler { sheets.dismiss() }
  val list = androidx.compose.foundation.lazy.rememberLazyListState()
  LaunchedEffect(spec) {
    list.scrollToItem(spec.focus.coerceIn(0, (spec.actions.size - 1).coerceAtLeast(0)))
    androidx.compose.runtime.withFrameNanos {}
    runCatching { first.requestFocus() }
  }
  val shape = RoundedCornerShape(36.dp)
  Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
    Column(
        Modifier.widthIn(min = 560.dp, max = 820.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
      spec.title?.let { Text(it, style = app(34, FontWeight.Bold), color = Theme.text1, textAlign = TextAlign.Center, maxLines = 2) }
      spec.message?.let { Text(it, style = app(26), color = Theme.text2, textAlign = TextAlign.Center) }
      // A long list (every genre) scrolls; the first choice, or the current one, has focus.
      androidx.compose.foundation.lazy.LazyColumn(
          Modifier.heightIn(max = 640.dp),
          state = list,
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
      ) {
        items(spec.actions.size) { i ->
          val a = spec.actions[i]
          PillButton(
              a.title,
              onClick = {
                sheets.dismiss()
                a.run()
              },
              modifier = if (i == spec.focus) Modifier.focusRequester(first) else Modifier,
              kind = if (a.primary) PillKind.Primary else PillKind.Secondary,
              icon = a.icon,
              selected = a.selected,
          )
        }
      }
    }
  }
}

/** A page to open on a phone: what it is, a line on why, and its address. */
data class LinkSpec(val title: String, val body: String, val url: String)
