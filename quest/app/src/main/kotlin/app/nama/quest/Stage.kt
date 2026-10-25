package app.nama.quest

import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.Vector3
import com.meta.spatial.core.Vector4
import com.meta.spatial.core.Lut
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.runtime.Scene
import com.meta.spatial.runtime.SceneLayer
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import kotlin.math.abs
import kotlin.math.exp

/**
 * Where every panel sits in the room, and how it gets there. Positions are given relative to the
 * viewer's head at the moment the layout was made (x to the right, y up from eye height, d straight
 * ahead), so the arrangement feels the same seated or standing, and recentering rebuilds it in
 * front of wherever the user now faces.
 */
object Layout {
  // The screen: about 50° wide, a comfortable cinema seat (Spatial TV uses ~77°, 4XVR's room mode
  // ~72°; those suit a single video, we also stack panels in front of it).
  const val SCREEN_W = 2.7f
  const val SCREEN_H = SCREEN_W * 9f / 16f
  const val SCREEN_D = 2.9f

  val screenHome = Spot(0f, 0.95f, SCREEN_D)
  val screenTitle = Spot(0f, 0.24f, SCREEN_D)
  val screenPlay = Spot(0f, 0.18f, SCREEN_D - 0.05f)
  const val PLAY_SCALE = 1.18f

  // The poster wall curves around the viewer below the screen, 10–15° under the line of sight,
  // where the neck rests.
  const val WALL_RADIUS = 2.0f
  const val WALL_W = 3.4f
  const val WALL_H = 1.3f
  val wall = Spot(0f, -0.55f, WALL_RADIUS)

  val login = Spot(0f, -0.12f, 1.05f, faceEye = true)

  val logo = Spot(0f, 0.17f, 1.55f)
  val logoGlow = Spot(0f, 0.17f, 1.6f)
  val info = Spot(0f, -0.27f, 1.38f, faceEye = true)
  /** Side panels; [start] is negative x in LTR and flips for RTL. */
  val sideStart = Spot(-1.0f, -0.02f, 1.28f, faceEye = true)
  val sideEnd = Spot(1.0f, -0.02f, 1.28f, faceEye = true)

  val controls = Spot(0f, -0.5f, 1.1f, faceEye = true)
  val toast = Spot(0f, -0.2f, 0.95f, faceEye = true)
}

/** A place relative to the head. [faceEye] tilts the panel to look straight at the eyes. */
data class Spot(val x: Float, val y: Float, val d: Float, val faceEye: Boolean = false)

/** One entity the stage moves and fades. */
class Placed(val entity: Entity) {
  var pose = Pose()
  var target = Pose()
  var scale = 1f
  var targetScale = 1f
  var opacity = 0f
  var targetOpacity = 0f
  /** Brightness the compositor applies to the layer: a trailer plays dimmed behind the title. */
  var brightness = 1f
  private var shownBrightness = 1f
  /** Height relative to width, for the video's own aspect ratio. */
  var stretchY = 1f
  /** Read by the panel's Compose root, so fades work whether or not the panel is a layer. */
  val fade = mutableFloatStateOf(0f)
  var layer: SceneLayer? = null
  private var shown = false
  private var placedOnce = false

  fun show(spot: Pose, on: Boolean, scaleTo: Float = 1f) {
    targetOpacity = if (on) 1f else 0f
    targetScale = scaleTo
    if (on && !shown && opacity < 0.02f) {
      // Arrive from a little farther away and smaller, so a panel settles in instead of popping.
      pose = Pose(spot.t + spot.forward() * 0.18f, spot.q)
      scale = scaleTo * 0.94f
    }
    if (!placedOnce) {
      pose = spot
      placedOnce = true
    }
    val next = if (on) spot else Pose(spot.t + spot.forward() * 0.12f, spot.q)
    if (!samePose(next, target) || targetScale != scaleTo) settled = false
    target = next
    shown = on
  }

  private var wasVisible: Boolean? = null
  private var settled = false
  private var lastBias = -1f
  private var lastBrightness = -1f
  private var lookingUp = false

  fun step(dt: Float) {
    val k = 1f - exp(-dt * 7.5f)
    val kf = 1f - exp(-dt * 6f)
    // Ease toward the target, then stop writing once there: a settled or hidden panel costs nothing.
    val moving = !settled || (wasVisible == true && !samePose(pose, target)) || abs(targetScale - scale) > 1e-4f
    if (moving) {
      pose = Pose(pose.t.lerp(target.t, k), pose.q.slerp(target.q, k))
      scale += (targetScale - scale) * k
      if (samePose(pose, target) && abs(targetScale - scale) < 1e-4f) {
        pose = target
        scale = targetScale
        settled = true
      } else settled = false
    }
    opacity += (targetOpacity - opacity) * kf
    if (abs(targetOpacity - opacity) < 0.003f) opacity = targetOpacity
    val visible = opacity > 0.004f
    if (visible != wasVisible) {
      entity.setComponent(Visible(visible))
      wasVisible = visible
      settled = false
    }
    if (visible && (moving || stretchY != lastStretch)) {
      entity.setComponent(Transform(pose))
      entity.setComponent(Scale(Vector3(scale, scale * stretchY, scale)))
      lastStretch = stretchY
    }
    // A layer fades in the compositor; otherwise Compose fades the content. Never both, or a fade
    // would run at the square of its value.
    val l = layer
    shownBrightness += (brightness - shownBrightness) * kf
    if (abs(brightness - shownBrightness) < 0.002f) shownBrightness = brightness
    if (l != null && (opacity != lastBias || shownBrightness != lastBrightness)) {
      l.setColorScaleBias(Vector4(shownBrightness, shownBrightness, shownBrightness, opacity), Vector4(0f))
      lastBias = opacity
      lastBrightness = shownBrightness
    }
    val f = if (l != null) 1f else opacity
    if (fade.floatValue != f) fade.floatValue = f
  }

  private var lastStretch = 1f

  private fun samePose(a: Pose, b: Pose) = a.t.distanceTo(b.t) < 0.0005f && abs(a.q.dot(b.q)) > 0.99999f

  /** Looks up the panel's compositor layer once it exists; until then the Compose fade carries it. */
  fun resolveLayer(objects: SceneObjectSystem) {
    if (layer != null || lookingUp) return
    val future = objects.getSceneObject(entity) ?: return
    lookingUp = true
    future.thenAccept { so ->
      layer = (so as? PanelSceneObject)?.getLayer()
      lookingUp = false
    }
  }
}

class Stage(
    private val scene: Scene,
    private val model: AppModel,
    private val objects: SceneObjectSystem,
    /** Debug captures: lay out along the headset's full gaze, tilt included, so a headset lying on a desk still frames the panels. */
    private val alongGaze: Boolean = false,
) {
  val login = place(R.id.panel_login)
  val wall = place(R.id.panel_home)
  val screenArt = place(R.id.panel_screen_art)
  val video = place(R.id.panel_video)
  val logoGlow = place(R.id.panel_logo_glow)
  val logo = place(R.id.panel_logo)
  val info = place(R.id.panel_title_info)
  val episodes = place(R.id.panel_episodes)
  val cast = place(R.id.panel_cast)
  val controls = place(R.id.panel_controls)
  val subtitles = place(R.id.panel_subtitles)
  val toast = place(R.id.panel_toast)
  private val all = listOf(login, wall, screenArt, video, logoGlow, logo, info, episodes, cast, controls, subtitles, toast)
  private val byPanel = mapOf(
      R.id.panel_login to login, R.id.panel_home to wall, R.id.panel_screen_art to screenArt,
      R.id.panel_video to video, R.id.panel_logo_glow to logoGlow, R.id.panel_logo to logo,
      R.id.panel_title_info to info, R.id.panel_episodes to episodes, R.id.panel_cast to cast,
      R.id.panel_controls to controls, R.id.panel_subtitles to subtitles, R.id.panel_toast to toast,
  )

  fun fadeOf(panelId: Int): Float = byPanel[panelId]?.fade?.floatValue ?: 1f

  /** The head pose the layout was built from. */
  private var origin: Pose? = null
  private var waited = 0f
  private var lastActivity = 0L
  private var roomLight = 1f
  private var lutLight = -1f
  private var lastButtons = 0
  private var lastSeekAt = 0L

  private fun place(id: Int): Placed =
      Placed(Entity.create(listOf(Panel(id), Transform(), Visible(false), Scale(Vector3(1f)))))

  fun recenter() {
    val head = scene.getViewerPose()
    if (alongGaze) {
      origin = head
      return
    }
    // Only the heading matters: tilting the head while recentering must not tilt the room.
    val fwd = head.forward().let { Vector3(it.x, 0f, it.z) }
    if (fwd.length() < 1e-3f) return
    origin = Pose(head.t, Quaternion.lookRotation(fwd.normalize(), Vector3(0f, 1f, 0f)))
  }

  fun poke() {
    lastActivity = System.currentTimeMillis()
  }

  fun tick(dt: Float) {
    if (origin == null) {
      // The head pose is all zeros for the first frames; wait for a real one. Without tracking it
      // never comes, so after a moment lay out around a seated eye height instead; a recenter
      // fixes it once tracking is back.
      waited += dt
      when {
        scene.getViewerPose().t.length() > 0.01f -> recenter()
        waited > 1.5f -> origin = Pose(Vector3(0f, 1.2f, 0f), Quaternion())
        else -> return
      }
      Log.i("Nama", "layout origin ${origin?.t} after ${"%.2f".format(waited)}s")
    }
    all.forEach { it.resolveLayer(objects) }
    readControllers()
    arrange()
    all.forEach { it.step(dt) }
    dimRoom(dt)
  }

  private fun arrange() {
    val where = model.place.value
    val rtl = model.lang.value.isRTL
    val hasDetails = model.details.value != null
    val playing = where == Place.Playing
    val st = model.video.state.value
    val trailerOn = where == Place.Title && model.trailer.value != null
    // The picture stays up until the first frame of film or trailer is on the screen.
    val videoUp = (playing || trailerOn) && st.position > 0.05 && st.videoWidth > 0

    login.show(at(Layout.login), where == Place.Login)
    wall.show(at(Layout.wall), where == Place.Home)

    val screenSpot = when (where) {
      Place.Starting, Place.Home, Place.Login -> Layout.screenHome
      Place.Title -> Layout.screenTitle
      Place.Playing -> Layout.screenPlay
    }
    val screenScale = if (playing) Layout.PLAY_SCALE else 1f
    // The artwork stays until the first frame is up, so starting a film never flashes black.
    screenArt.show(at(screenSpot), !videoUp, screenScale)
    video.show(at(screenSpot.copy(d = screenSpot.d - 0.01f)), playing || (trailerOn && videoUp), screenScale)
    // Letterboxed films keep the screen's width and lose height, like a real cinema masking.
    video.brightness = if (trailerOn) TRAILER_BRIGHTNESS else 1f
    video.stretchY = if (st.videoWidth > 0) ((16f / 9f) / st.aspect).coerceIn(0.4f, 1.4f) else 1f

    val onTitle = where == Place.Title
    logoGlow.show(at(Layout.logoGlow), onTitle)
    logo.show(at(Layout.logo), onTitle)
    info.show(at(Layout.info), onTitle)
    val start = if (rtl) Layout.sideEnd else Layout.sideStart
    val end = if (rtl) Layout.sideStart else Layout.sideEnd
    episodes.show(at(start), onTitle && hasDetails)
    cast.show(at(end), onTitle && hasDetails && model.details.value?.people?.isNotEmpty() == true)

    val idle = System.currentTimeMillis() - lastActivity > CONTROLS_HIDE_MS
    // The controls also stay up while the intro can be skipped, so the offer isn't missed.
    val inIntro = model.introEnd(st.position) != null
    controls.show(at(Layout.controls), playing && (!idle || st.paused || st.buffering || inIntro))
    // Close and a little low, where the eye is already resting, whatever arrangement is up.
    toast.show(at(Layout.toast), model.toast.value != null)
    val h = Layout.SCREEN_H * screenScale
    subtitles.show(at(screenSpot.copy(y = screenSpot.y - h / 2 + 0.2f, d = screenSpot.d - 0.08f)), playing && st.subtitle.isNotBlank())
  }

  /** A spot relative to the layout origin, turned to face the viewer. */
  private fun at(spot: Spot): Pose {
    val o = origin ?: return Pose()
    val right = o.right()
    val fwd = o.forward()
    val up = if (alongGaze) o.up() else Vector3(0f, 1f, 0f)
    val p = o.t + right * spot.x + up * spot.y + fwd * spot.d
    val toPanel = p - o.t
    val look = if (spot.faceEye || alongGaze) toPanel else Vector3(toPanel.x, 0f, toPanel.z)
    return Pose(p, Quaternion.lookRotation(look.normalize(), up))
  }

  /**
   * Dims the user's real room while a film plays and brings it back after, over a couple of
   * seconds, through the passthrough colour table (the use Meta documents for it).
   */
  private fun dimRoom(dt: Float) {
    val playing = model.place.value == Place.Playing && !model.video.state.value.paused
    val target = if (playing) ROOM_DIM_PLAYING else if (model.place.value == Place.Playing) ROOM_DIM_PAUSED else 1f
    roomLight += (target - roomLight) * (1f - exp(-dt * 1.4f))
    // Rebuilding the table is cheap but not free; step it in small increments.
    if (abs(roomLight - lutLight) > 0.015f || (roomLight == target && lutLight != target)) {
      if (abs(roomLight - target) < 0.01f) roomLight = target
      lutLight = roomLight
      val lut = Lut()
      for (r in 0..15) for (g in 0..15) for (b in 0..15) {
        lut.setMapping(r, g, b, (r * 17 * roomLight).toInt(), (g * 17 * roomLight).toInt(), (b * 17 * roomLight).toInt())
      }
      scene.setPassthroughLUT(lut)
    }
  }

  /** Controller shortcuts while watching: any button shows the controls; the sticks jump 10 s. */
  private fun readControllers() {
    var buttons = 0
    Query.where { has(Controller.id) }.eval().forEach { e ->
      val c = e.getComponent<Controller>()
      if (c.isActive) buttons = buttons or c.buttonState
    }
    val pressed = buttons and lastButtons.inv()
    lastButtons = buttons
    if (pressed == 0) return
    poke()
    if (model.place.value != Place.Playing) {
      if (pressed and (ButtonBits.ButtonB or ButtonBits.ButtonY) != 0) model.back()
      return
    }
    // A stick hovering at its threshold can report several pushes; one jump per quarter second.
    val now = System.currentTimeMillis()
    val canSeek = now - lastSeekAt > 250
    when {
      pressed and (ButtonBits.ButtonThumbRL or ButtonBits.ButtonThumbLL) != 0 -> if (canSeek) {
        lastSeekAt = now
        model.video.seekBy(-10.0)
      }
      pressed and (ButtonBits.ButtonThumbRR or ButtonBits.ButtonThumbLR) != 0 -> if (canSeek) {
        lastSeekAt = now
        model.video.seekBy(10.0)
      }
      pressed and (ButtonBits.ButtonA or ButtonBits.ButtonX) != 0 -> model.video.setPaused(!model.video.state.value.paused)
      pressed and (ButtonBits.ButtonB or ButtonBits.ButtonY) != 0 -> model.back()
    }
  }

  companion object {
    private const val CONTROLS_HIDE_MS = 4_000L
    private const val ROOM_DIM_PLAYING = 0.22f
    private const val ROOM_DIM_PAUSED = 0.55f
    // Dim enough that the floating name reads over any trailer, bright enough to watch. The
    // compositor scales linear light, so 0.32 looks like about 60% brightness.
    private const val TRAILER_BRIGHTNESS = 0.32f
  }
}
