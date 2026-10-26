package app.nama.quest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nama.quest.data.Row
import app.nama.quest.data.Tile

enum class ShelfPhase { Pile, Row, Open }

/**
 * The magazines of the home screen (quest/docs/design.md): every home row is a magazine; they lie
 * in a pile on a table, spread out side by side on it when touched, and the one chosen comes up
 * and opens, a title (its poster) to a page. Page 0 is the cover. The stage reads this to place things; the page panels
 * read it to know which page they show.
 */
/** [rows]: the home rows, read when needed (the stage and SceneSim give their own). */
class Shelf(private val rows: () -> List<Row>) {
  var phase by mutableStateOf(ShelfPhase.Pile)
    private set

  /** The phase before the last change and when it changed, so the stage can stagger the move. */
  var previous = ShelfPhase.Pile
    private set

  var changedAt = 0L
    private set

  var chosen by mutableIntStateOf(0)
    private set

  var spread by mutableIntStateOf(0)
    private set

  // What each sheet shows while a page turns: the two halves underneath, and both sides of the
  // leaf in the air. -1 shows nothing.
  var pageA by mutableIntStateOf(0)
    private set

  var pageB by mutableIntStateOf(1)
    private set

  var leafFront by mutableIntStateOf(-1)
    private set

  var leafBack by mutableIntStateOf(-1)
    private set

  /** +1 turning forward, -1 back, 0 still; [turnT] runs 0..1 over the turn. */
  var turning = 0
    private set

  var turnT = 0f
    private set

  private var target = 0

  fun magazines(): List<Row> = rows().filter { it.tiles.isNotEmpty() }.take(MAX)

  fun pageCount(): Int = 2 + (magazines().getOrNull(chosen)?.tiles?.size ?: 0)

  fun tileAt(page: Int): Tile? = if (page < 2) null else magazines().getOrNull(chosen)?.tiles?.getOrNull(page - 2)

  /** Spread 0 is the magazine shut, cover up, as it was brought from the table. */
  val closed: Boolean
    get() = phase == ShelfPhase.Open && spread == 0 && turning == 0

  fun row(): Row? = magazines().getOrNull(chosen)

  /** A touch on the pile raises them all; a touch on one of the row opens it. */
  fun touch(index: Int) {
    when (phase) {
      ShelfPhase.Pile -> set(ShelfPhase.Row)
      ShelfPhase.Row -> open(index)
      ShelfPhase.Open -> Unit
    }
  }

  fun open(index: Int) {
    chosen = index
    spread = 0
    pageA = -1
    pageB = 0
    leafFront = -1
    leafBack = -1
    turning = 0
    set(ShelfPhase.Open)
  }

  /** One step back: the open magazine to the row, the row to the pile. False when already in the pile. */
  fun back(): Boolean {
    when (phase) {
      ShelfPhase.Open -> set(ShelfPhase.Row)
      ShelfPhase.Row -> set(ShelfPhase.Pile)
      ShelfPhase.Pile -> return false
    }
    return true
  }

  /** Turns one spread forward (+1) or back (-1), in reading order. */
  fun turn(dir: Int) {
    if (turning != 0 || phase != ShelfPhase.Open) return
    val last = pageCount() / 2
    val to = (spread + dir).coerceIn(0, last)
    if (to == spread) return
    target = to
    turning = dir
    turnT = 0f
    val a = spread * 2 - 1
    val n = to * 2 - 1
    // Underneath, the far half already shows what it will after the turn.
    if (dir > 0) {
      pageA = a; pageB = n + 1; leafFront = a + 1; leafBack = n
    } else {
      pageA = n; pageB = a + 1; leafFront = a; leafBack = n + 1
    }
  }

  /** Debug captures: hold a turning page at this point of its turn. */
  var holdTurn: Float? = null

  fun step(dt: Float) {
    if (turning == 0) return
    holdTurn?.let {
      turnT = it
      return
    }
    turnT += dt / TURN_SECONDS
    if (turnT >= 1f) {
      spread = target
      pageA = spread * 2 - 1
      pageB = spread * 2
      leafFront = -1
      leafBack = -1
      turning = 0
      turnT = 0f
    }
  }

  private fun set(next: ShelfPhase) {
    previous = phase
    phase = next
    changedAt = System.currentTimeMillis()
  }

  companion object {
    const val MAX = 6
    /** A page takes its time over: lifted, carried, laid down. */
    const val TURN_SECONDS = 0.85f
  }
}
