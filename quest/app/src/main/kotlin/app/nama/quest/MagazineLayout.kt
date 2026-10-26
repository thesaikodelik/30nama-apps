package app.nama.quest

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Where one thing of the magazines is, how big, and whether it shows. */
data class Placement(val pose: Pose, val visible: Boolean, val scale: Float = 1f)

/**
 * Where everything of the magazines goes for a given moment (design.md): as numbers only, so the
 * stage on the headset and SceneSim on the computer place exactly the same things. The frame is
 * the user's: [eye], and [up], [fwd], [right] level with the room.
 */
class MagazineLayout(
    val eye: Vector3,
    val up: Vector3,
    val fwd: Vector3,
    val right: Vector3,
    /** The top of the table the magazines lie on. */
    val top: Vector3,
    val rtl: Boolean,
) {
  class Result(
      val table: Placement,
      val bodies: List<Placement>,
      val covers: List<Placement>,
      val openBody: Placement,
      /** Both open pages share the open magazine's pose; their paper is shaped in its frame. */
      val pages: Placement,
      val leaf: Placement,
      val overlayA: Placement,
      val overlayB: Placement,
      /**
       * The local side (sign of x) of the first page in reading order. Local +x is the viewer's
       * left (see PaperShape.lay), so a Persian magazine's first page, on the right, is -1.
       */
      val sideA: Float,
      /** The side the turning page left, and how far over it is (eased), when one turns. */
      val leafSide: Float,
      val leafTurn: Float,
  )

  /** Where the open magazine is held: ahead and under the eyes, facing them. */
  val book: Pose = run {
    val c = eye + fwd * Layout.BOOK_D + up * Layout.BOOK_Y
    val toEye = (eye - c).normalize()
    val pageUp = (up - toEye * up.dot(toEye)).normalize()
    Pose(c, Paper.basis(pageUp, toEye))
  }

  fun compute(sh: Shelf, home: Boolean, ready: Boolean, virtualTable: Boolean, now: Long): Result {
    val mags = sh.magazines()
    val n = mags.size
    val opened = home && sh.phase == ShelfPhase.Open && !sh.closed
    val bookNrm = book.q * Vector3(0f, 1f, 0f)
    val bookAcross = book.q * Vector3(1f, 0f, 0f)
    val sideA = if (rtl) -1f else 1f
    val grown = Paper.PW / Paper.CW
    // Shut, it lies over the second half, its spine on the open magazine's spine.
    val shut = Pose(book.t + bookAcross * (-sideA * Paper.PW / 2) + bookNrm * (Paper.CT * grown / 2), book.q)
    val hidden = Placement(Pose(top, Quaternion()), false)

    val bodies = ArrayList<Placement>()
    val covers = ArrayList<Placement>()
    for (k in 0 until Shelf.MAX) {
      if (k >= n) {
        bodies += hidden
        covers += hidden
        continue
      }
      // One after another off the pile; back onto it the other way.
      val delay = if (sh.phase == ShelfPhase.Row) k * 70L else (n - 1 - k) * 50L
      val phase = if (now - sh.changedAt < delay) sh.previous else sh.phase
      val chosen = sh.phase == ShelfPhase.Open && k == sh.chosen
      val pose =
          when {
            chosen -> shut
            phase == ShelfPhase.Pile -> {
              val j = PILE[k % PILE.size]
              val turn = Math.toRadians(j[2].toDouble()).toFloat()
              val p = top + right * j[0] + fwd * j[1] + up * (Paper.CT * (n - k) - Paper.CT / 2)
              Pose(p, Paper.basis(fwd * cos(turn) + right * sin(turn), up))
            }
            else -> {
              // Side by side along the table, overlapping a little, fanned toward the user; the
              // first row at the start of the reading direction.
              val slot = (k - (n - 1) / 2f) * SPREAD_STEP * (if (rtl) -1f else 1f)
              val p = top + right * slot + fwd * (0.02f * abs(slot)) + up * (Paper.CT / 2 + 0.0015f * k)
              val turn = -slot * 0.35f
              Pose(p, Paper.basis(fwd * cos(turn) + right * sin(turn), up))
            }
          }
      val show = home && ready && !(chosen && opened)
      val size = if (chosen) grown else 1f
      bodies += Placement(pose, show, size)
      covers += Placement(coverOn(pose, size), show, size)
    }

    val turning = opened && sh.turning != 0
    val nrm = bookNrm
    val half = Paper.PW / 2
    val lift = nrm * 0.004f
    val overlayA = Placement(Pose(book.t + bookAcross * (sideA * half) + lift, panelFacing(book)), opened && !turning)
    // Over the shut magazine too: touching its cover, or swiping it, opens it.
    val shutUp = home && sh.closed
    val overlayB = Placement(Pose(book.t + bookAcross * (-sideA * half) + lift + (if (shutUp) nrm * (Paper.CT * grown) else Vector3(0f)), panelFacing(book)), (opened && !turning) || shutUp)
    val leafSide = if (sh.turning > 0) -sideA else sideA
    val t = sh.turnT.coerceIn(0f, 1f)
    return Result(
        table = Placement(Pose(top - up * 0.004f, Quaternion.lookRotation(-up, fwd)), home && ready && virtualTable && n > 0),
        bodies = bodies,
        covers = covers,
        openBody = Placement(book, opened),
        pages = Placement(book, opened),
        leaf = Placement(book, turning),
        overlayA = overlayA,
        overlayB = overlayB,
        sideA = sideA,
        leafSide = leafSide,
        leafTurn = t * t * (3f - 2f * t),
    )
  }

  companion object {
    /** Magazines side by side on the table: centre to centre, a little less than a cover's width. */
    const val SPREAD_STEP = 0.175f

    /** Where each magazine lies in the pile: right and forward of its centre (m), turned (°). */
    private val PILE = listOf(floatArrayOf(0f, 0f, -3f), floatArrayOf(0.012f, -0.008f, 5f), floatArrayOf(-0.01f, 0.01f, -7f), floatArrayOf(0.016f, 0.006f, 9f), floatArrayOf(-0.006f, -0.012f, 2f), floatArrayOf(0.008f, 0.014f, -10f))

    /**
     * A panel lying on a magazine body's cover: just above its top face, facing out of it, its
     * top along the page's top. Panels face their viewer from local -z, so the panel looks along
     * the inward normal.
     */
    fun coverOn(body: Pose, size: Float): Pose {
      val nrm = body.q * Vector3(0f, 1f, 0f)
      val pageUp = body.q * Vector3(0f, 0f, -1f)
      return Pose(body.t + nrm * (Paper.CT * size / 2 + 0.0006f), Quaternion.lookRotation(-nrm, pageUp))
    }

    /** A panel lying on the open magazine, facing out of it. */
    fun panelFacing(book: Pose): Quaternion {
      val nrm = book.q * Vector3(0f, 1f, 0f)
      val pageUp = book.q * Vector3(0f, 0f, -1f)
      return Quaternion.lookRotation(-nrm, pageUp)
    }
  }
}
