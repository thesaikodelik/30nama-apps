package app.nama.quest

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The shape of one sheet of paper as numbers only: a grid of [cols] x [rows] quads, two layers
 * (the front face, and the back a hair behind it), in the open magazine's local frame (x across
 * with the spine at 0, y out of the paper, -z toward the top of the page). No GPU here, so the
 * scene simulator on the computer (SceneSim) lays exactly the same paper as the headset does.
 */
class PaperShape(val cols: Int, val rows: Int) {
  val count = (cols + 1) * (rows + 1)
  val pos = FloatArray(count * 2 * 3)
  val nor = FloatArray(count * 2 * 3)
  val uv = FloatArray(count * 2 * 2)
  /** Two triangles a quad, front layer first; the back layer's indices follow. */
  val indices: IntArray = IntArray(cols * rows * 6 * 2).also { idx ->
    var k = 0
    for (layer in 0..1) for (i in 0 until cols) for (j in 0 until rows) {
      val a = layer * count + i * (rows + 1) + j
      val b = a + rows + 1
      idx[k++] = a; idx[k++] = b; idx[k++] = a + 1
      idx[k++] = a + 1; idx[k++] = b; idx[k++] = b + 1
    }
  }

  private val cx = FloatArray(cols + 1)
  private val cy = FloatArray(cols + 1)
  private val cnx = FloatArray(cols + 1)
  private val cny = FloatArray(cols + 1)

  /**
   * The page on [side] (+1 right of the spine, -1 left), [turn] of the way (0..1) over to the
   * other side. A page is lifted by its top outer corner: the top rows lead and the bottom ones
   * follow a little behind, so the paper twists as well as bends, and its outer edge leads the
   * part near the spine. The front face shows the page that was lying on [side]; the back face
   * the page it shows once over.
   */
  fun lay(side: Float, turn: Float) {
    for (j in 0..rows) {
      // How far behind the top this row is: nothing lying flat, most of all mid-turn.
      val lag = CORNER_LAG * (j.toFloat() / rows) * sin(Math.PI.toFloat() * turn)
      val t = ((turn - lag) / (1f - lag)).coerceIn(0f, 1f)
      curve(cols, side, t, cx, cy, cnx, cny)
      for (layer in 0..1) {
        val behind = if (layer == 0) 0f else -0.0006f
        val sign = if (layer == 0) 1f else -1f
        // Each face is read from its own side: the front where the page lay, the back where it lands.
        val face = if (layer == 0) side else -side
        val z = -Paper.PH / 2 + Paper.PH * j / rows
        for (i in 0..cols) {
          val v = layer * count + i * (rows + 1) + j
          pos[v * 3] = cx[i] + cnx[i] * behind
          pos[v * 3 + 1] = cy[i] + cny[i] * behind
          pos[v * 3 + 2] = z
          nor[v * 3] = cnx[i] * sign
          nor[v * 3 + 1] = cny[i] * sign
          nor[v * 3 + 2] = 0f
          // u runs left to right across the page as the viewer sees it. The SDK's world is
          // left-handed (x right, y up, z forward), so with the paper's normal toward the viewer
          // and its top at -z, local +x is the viewer's LEFT: a page on +x is a left-hand page,
          // whose u runs from its outer edge (0) to the spine (1).
          uv[v * 2] = if (face > 0) 1f - i.toFloat() / cols else i.toFloat() / cols
          // Row 0 is the page's top (-z), and the picture's top row.
          uv[v * 2 + 1] = j.toFloat() / rows
        }
      }
    }
    wind()
  }

  /**
   * Turns each triangle to face the way its normal does. A page left of the spine is the right
   * one mirrored, which reverses its triangles; the SDK flips the normal of a triangle seen from
   * behind (double-sided paper), so that page was lit from its back: ambient only, a dull grey.
   */
  private fun wind() {
    for (k in indices.indices step 3) {
      val a = indices[k]
      val b = indices[k + 1]
      val c = indices[k + 2]
      val ux = pos[b * 3] - pos[a * 3]
      val uy = pos[b * 3 + 1] - pos[a * 3 + 1]
      val uz = pos[b * 3 + 2] - pos[a * 3 + 2]
      val vx = pos[c * 3] - pos[a * 3]
      val vy = pos[c * 3 + 1] - pos[a * 3 + 1]
      val vz = pos[c * 3 + 2] - pos[a * 3 + 2]
      val facing = (uy * vz - uz * vy) * nor[a * 3] + (uz * vx - ux * vz) * nor[a * 3 + 1] + (ux * vy - uy * vx) * nor[a * 3 + 2]
      if (facing * WINDING < 0f) {
        indices[k + 1] = c
        indices[k + 2] = b
      }
    }
  }

  companion object {
    /** How far (of the turn) the page's bottom trails its top. */
    private const val CORNER_LAG = 0.1f

    /**
     * The sign of (b - a) x (c - a) . normal for a triangle the SDK draws as facing its normal
     * (its world is left-handed). Measured on the headset: with +1 both pages went dull.
     */
    private const val WINDING = -1f

    /**
     * The paper's cross-section across a page on [side], [turn] of the way over: column i (0 at
     * the spine) at [x], [y] with normal [nx], [ny]. It bends as it goes, its outer edge leading.
     */
    fun curve(cols: Int, side: Float, turn: Float, x: FloatArray, y: FloatArray, nx: FloatArray, ny: FloatArray) {
      val theta = Math.PI.toFloat() * turn
      val lead = 0.55f * sin(theta)
      var px = 0f
      var py = 0f
      val step = Paper.PW / cols
      for (i in 0..cols) {
        val r = step * i
        // The angle of the paper at r: the spine's angle, plus more toward the edge mid-turn.
        val phi = min(Math.PI.toFloat(), theta + lead * Math.pow((r / Paper.PW).toDouble(), 1.4).toFloat())
        if (i > 0) {
          px += cos(phi) * step
          py += sin(phi) * step
        }
        // Flat on either side, the paper follows the gutter's dip.
        val rest = Paper.top(abs(px)) * abs(cos(theta)) + Paper.top(Paper.GUTTER) * sin(theta)
        x[i] = side * px
        y[i] = py + rest
        nx[i] = -side * sin(phi)
        ny[i] = cos(phi)
      }
    }
  }
}
