package app.nama.androidtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Where focus goes when the shell gives a page focus: on opening, and with Down from the bar the
 * first time. The Apple TV's preferred focus: a page says which control it starts on (the title
 * page's Play), and the shell waits for that control rather than taking whatever control it finds
 * first, which in Persian could be one far down the page, and the page scrolled there (the owner).
 */
@Stable
class PageEntry {
  internal val requester = FocusRequester()

  /** The page has said it has an entry: the shell waits for it, however long its data takes. */
  var declared by mutableStateOf(false)
    internal set

  /** The entry control is on the screen and can take focus. */
  var ready by mutableStateOf(false)
    internal set
}

/** The open page's entry, provided by the shell around each page. */
val LocalPageEntry = staticCompositionLocalOf<PageEntry?> { null }

/**
 * Says that this page has an entry control, from the first frame, before the control itself
 * exists (it may wait for the page's data). Call it at the top of the page.
 */
@Composable
fun DeclarePageEntry() {
  val entry = LocalPageEntry.current ?: return
  entry.declared = true
}

/** Marks the control the shell gives focus to when it enters this page. */
@Composable
fun Modifier.pageEntry(): Modifier {
  val entry = LocalPageEntry.current ?: return this
  DisposableEffect(entry) {
    entry.ready = true
    onDispose { entry.ready = false }
  }
  return this.focusRequester(entry.requester)
}
