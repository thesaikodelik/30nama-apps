package app.nama.androidtv.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView

/**
 * A mouse or an air mouse (most Iranian Android boxes come with one; LG's Magic Remote is one):
 * a click on a control focuses it and presses select, so it does just what the remote's OK does
 * there. Compose for TV's controls answer keys only, and a click went nowhere (the owner, after
 * mmahdim77/30nama-webos). A long press is held select, for what holding OK does. The remote is
 * left as it is.
 */
@Composable
fun Modifier.mouseClicks(): Modifier {
  val focus = remember { FocusRequester() }
  val view = LocalView.current
  val inputMode = LocalInputModeManager.current
  return this.focusRequester(focus).pointerInput(Unit) {
    awaitPointerEventScope {
      var downAt = 0L
      var downPos = androidx.compose.ui.geometry.Offset.Zero
      val slop = viewConfiguration.touchSlop
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull() ?: continue
        // A touch too: the emulator's window sends the computer's mouse as one, and a TV has no
        // touch screen of its own to mind it.
        if (change.type != PointerType.Mouse && change.type != PointerType.Touch) continue
        when (event.type) {
          // The press is left to the lists, so a drag still scrolls them; only a press that ends
          // where it began, not taken by a scroll, is a click.
          PointerEventType.Press -> if (change.pressed) {
            downAt = change.uptimeMillis
            downPos = change.position
          }
          PointerEventType.Move -> if (downAt != 0L && (change.position - downPos).getDistance() > slop) downAt = 0L
          PointerEventType.Release -> if (downAt != 0L) {
            if (change.isConsumed || (change.position - downPos).getDistance() > slop) {
              downAt = 0L
              continue
            }
            change.consume()
            val held = change.uptimeMillis - downAt >= LONG_PRESS_MS
            downAt = 0L
            // Out of touch mode first: in it focus will not move, and select went to whatever
            // had it before (the home page's Play, which opened its film).
            inputMode.requestInputMode(InputMode.Keyboard)
            if (!runCatching { focus.requestFocus() }.getOrDefault(false)) continue
            val time = android.os.SystemClock.uptimeMillis()
            val down = KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
            view.dispatchKeyEvent(down)
            // Held select: the down repeats past the long press, as a held key does.
            if (held) view.dispatchKeyEvent(KeyEvent.changeTimeRepeat(down, time + LONG_PRESS_MS, 1, KeyEvent.FLAG_LONG_PRESS))
            view.dispatchKeyEvent(KeyEvent(time, time + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
          }
        }
      }
    }
  }
}

private const val LONG_PRESS_MS = 600L
