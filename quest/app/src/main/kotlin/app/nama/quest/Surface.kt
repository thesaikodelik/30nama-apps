package app.nama.quest

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import com.meta.spatial.mruk.MRUKAnchor
import com.meta.spatial.mruk.MRUKEnvironmentRaycastHitResult
import com.meta.spatial.mruk.MRUKFeature
import com.meta.spatial.mruk.MRUKLabel
import com.meta.spatial.mruk.MRUKStartEnvironmentRaycasterResult
import com.meta.spatial.mruk.hasLabel
import com.meta.spatial.toolkit.Transform
import kotlin.math.cos
import kotlin.math.sin

/**
 * Finds the real surface the magazines lie on: a table from the room scan when there is one,
 * otherwise the first level surface the depth sensor sees in front of the user, at table height
 * and within reach. Null until found, and for good when there is none: the stage then brings its
 * own low table.
 */
class SurfaceFinder(private val mruk: MRUKFeature) {
  @Volatile var top: Vector3? = null
    private set

  @Volatile var done = false
    private set

  private var raycasting = false
  private var searched = 0f
  private val main = Handler(Looper.getMainLooper())

  /** Called once the user has answered the scene permission. */
  fun start(granted: Boolean, head: () -> Pose) {
    if (!granted) {
      done = true
      return
    }
    mruk.loadSceneFromDevice().whenComplete { result, _ ->
      main.post {
        val eye = head().t
        val table =
            mruk.rooms.flatMap { it.anchors }
                .filter { it.getComponent<MRUKAnchor>().hasLabel(MRUKLabel.TABLE) }
                .map { it.getComponent<Transform>().transform.t }
                .filter { flat(it - eye) < MAX_REACH }
                .minByOrNull { flat(it - eye) }
        Log.i("Nama", "surface: scene $result, table ${table ?: "none"}")
        if (table != null) {
          top = table
          done = true
        } else {
          raycasting = mruk.startEnvironmentRaycaster() == MRUKStartEnvironmentRaycasterResult.SUCCESS
          if (!raycasting) done = true
        }
      }
    }
  }

  /** While no table is known, looks down and ahead for one, a fan of rays a frame. */
  fun tick(dt: Float, head: Pose) {
    if (done || !raycasting) return
    searched += dt
    if (searched > GIVE_UP_SECONDS) {
      finish(null)
      return
    }
    val fwd = head.forward().let { Vector3(it.x, 0f, it.z) }.normalize()
    val right = head.right().let { Vector3(it.x, 0f, it.z) }.normalize()
    var best: Vector3? = null
    for (pitch in PITCHES) for (yaw in YAWS) {
      val p = Math.toRadians(pitch.toDouble()).toFloat()
      val y = Math.toRadians(yaw.toDouble()).toFloat()
      val dir = (fwd * (cos(p) * cos(y)) + right * (cos(p) * sin(y)) + Vector3(0f, -sin(p), 0f)).normalize()
      val hit = mruk.raycastEnvironment(head.t, dir)
      if (hit.result != MRUKEnvironmentRaycastHitResult.SUCCESS) continue
      val drop = head.t.y - hit.point.y
      val reach = flat(hit.point - head.t)
      // Level, below the eyes at table height, and within reach of a hand.
      if (hit.normal.y > 0.9f && drop in 0.25f..0.95f && hit.point.y > 0.3f && reach in 0.25f..MAX_REACH) {
        if (best == null || reach < flat(best - head.t)) best = hit.point
      }
    }
    if (best != null) finish(best)
  }

  private fun finish(found: Vector3?) {
    Log.i("Nama", "surface: depth ${found ?: "none"} after ${"%.1f".format(searched)} s")
    top = found
    done = true
    if (raycasting) mruk.stopEnvironmentRaycaster()
    raycasting = false
  }

  private fun flat(v: Vector3) = Vector3(v.x, 0f, v.z).length()

  companion object {
    private const val MAX_REACH = 1.2f
    private const val GIVE_UP_SECONDS = 4f
    private val PITCHES = floatArrayOf(30f, 42f, 55f, 68f)
    private val YAWS = floatArrayOf(-20f, 0f, 20f)
  }
}
