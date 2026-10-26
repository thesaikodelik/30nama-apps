package app.nama.quest

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import app.nama.quest.data.DemoCatalog
import app.nama.quest.data.LiveCatalog
import app.nama.quest.player.VideoEngine
import app.nama.quest.ui.CastPanel
import app.nama.quest.ui.ControlsPanel
import app.nama.quest.ui.EpisodesPanel
import app.nama.quest.ui.HomeWall
import app.nama.quest.ui.MagazineCover
import app.nama.quest.ui.MagazinePage
import app.nama.quest.ui.VirtualTable
import app.nama.quest.ui.LoginPanel
import app.nama.quest.ui.ScreenArt
import app.nama.quest.ui.SubtitlePanel
import app.nama.quest.ui.TitleInfo
import app.nama.quest.ui.TitleLogo
import app.nama.quest.ui.ToastPanel
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.CylinderShapeOptions
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.MeshCreationSystem
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.UIPanelRenderOptions
import com.meta.spatial.toolkit.PanelRenderMode
import com.meta.spatial.runtime.PanelShapeLayerBlendType
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.mruk.MRUKFeature
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NamaActivity : AppSystemActivity() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private lateinit var video: VideoEngine
  private lateinit var model: AppModel
  private lateinit var shelf: Shelf
  private lateinit var mruk: MRUKFeature
  private lateinit var surface: SurfaceFinder
  // Observable, so a panel composed before the stage exists picks up its fades once it does.
  private var stage by mutableStateOf<Stage?>(null)
  private var lastTick = 0L

  override fun registerFeatures(): List<SpatialFeature> {
    // The room scan and the depth sensor: where the user's own table is, for the magazines.
    mruk = MRUKFeature(this, systemManager)
    return listOf(VRFeature(this), ComposeFeature(), mruk) + debugFeatures(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    video = createVideoEngine(this)
    val demo = BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)
    model = AppModel(this, if (demo) DemoCatalog() else LiveCatalog(this), video, scope)
    shelf = Shelf { model.rows.value }
    super.onCreate(savedInstanceState)
    // The thumbsticks seek while watching; they must not walk the user through their own room.
    systemManager.unregisterSystem<LocomotionSystem>()
  }

  override fun onSceneReady() {
    super.onSceneReady()
    scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
    // Debug captures over adb would otherwise include the user's own room; "--ez room false" keeps
    // it out of them.
    scene.enablePassthrough(!(BuildConfig.DEBUG && !intent.getBooleanExtra("room", true)))
    scene.setLightingEnvironment(
        ambientColor = Vector3(0.4f),
        sunColor = Vector3(1.6f),
        sunDirection = -Vector3(1f, 3f, -2f),
        environmentIntensity = 0.3f,
    )
    surface = SurfaceFinder(mruk)
    // Debug: the scene and its pictures as files, for the computer to draw the same (SceneDump).
    val dump = if (BuildConfig.DEBUG && intent.getBooleanExtra("dump", false)) SceneDump(java.io.File(getExternalFilesDir(null), "scene")) { kind, n ->
      if (kind == "cover") "cover$n.png" else if (n < 0) "blank.png" else "p${if (n > 8) 2 + (n - 2) % 7 else n}.png"
    } else null
    stage = Stage(scene, model, systemManager.findSystem<SceneObjectSystem>(), alongGaze = BuildConfig.DEBUG && intent.getBooleanExtra("gaze", false),
        gazeTilt = if (BuildConfig.DEBUG) intent.getFloatExtra("tilt", 0f) else 0f, shelf = shelf, surface = surface,
        meshes = systemManager.findSystem<MeshCreationSystem>().meshManager, art = MagazineArt(this, scope, model, shelf),
        dump = dump)
    dump?.let { d -> scope.launch(kotlinx.coroutines.Dispatchers.IO) { while (model.rows.value.isEmpty()) kotlinx.coroutines.delay(100); d.art(this@NamaActivity, model, shelf.magazines()) } }
    if (checkSelfPermission(USE_SCENE) == PackageManager.PERMISSION_GRANTED) surface.start(true) { scene.getViewerPose() }
    else requestPermissions(arrayOf(USE_SCENE), SCENE_REQUEST)
    model.start()
    if (BuildConfig.DEBUG) {
      CoreSelfTest.run()
      // adb shell am start -n app.nama.quest/.NamaActivity --es place title --es lang fa
      intent.getStringExtra("lang")?.let { l -> app.nama.core.Lang.entries.firstOrNull { it.name == l }?.let(model::setLang) }
      intent.getStringExtra("place")?.let { model.debugJump(it, intent.getStringExtra("tile"), intent.getIntExtra("row", 1)) }
      // A section of the wall, as NAMA_SECTION does on the Mac: search, movies, browse, settings…
      intent.getStringExtra("section")?.let { name -> Section.entries.firstOrNull { it.name == name }?.let(model::go) }
      // The magazines in a given state: spread, open, or mid-turn (--es shelf spread|open|turn).
      intent.getStringExtra("shelf")?.let { want ->
        scope.launch {
          while (model.rows.value.isEmpty()) kotlinx.coroutines.delay(100)
          kotlinx.coroutines.delay(1500)
          shelf.touch(0)
          if (want == "spread") return@launch
          kotlinx.coroutines.delay(1200)
          shelf.open(intent.getIntExtra("mag", 0))
          // chosen: shut in front; open: its cover turned; turn: and a page in the air.
          if (want == "chosen") return@launch
          kotlinx.coroutines.delay(1500)
          shelf.turn(1)
          if (want == "turn") {
            kotlinx.coroutines.delay(1500)
            if (intent.hasExtra("hold")) shelf.holdTurn = intent.getFloatExtra("hold", 0.5f)
            shelf.turn(1)
          }
        }
      }
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == SCENE_REQUEST) {
      surface.start(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) { scene.getViewerPose() }
    }
  }

  override fun onSceneTick() {
    super.onSceneTick()
    val now = System.nanoTime()
    val dt = if (lastTick == 0L) 0f else ((now - lastTick) / 1e9f).coerceAtMost(0.1f)
    lastTick = now
    stage?.tick(dt)
    debugTick(this, model, shelf)
  }

  override fun onRecenter(isUserInitiated: Boolean) {
    super.onRecenter(isUserInitiated)
    stage?.recenter()
  }

  // The Meta button or the universal menu takes the user away from the app: hold the film, and the
  // trailer, until they come back.
  override fun onVRPause() {
    super.onVRPause()
    model.pauseForSystem()
  }

  override fun onVRReady() {
    super.onVRReady()
    model.resumeFromSystem()
  }

  override fun onHMDUnmounted() {
    super.onHMDUnmounted()
    // Taking the headset off pauses the film, like taking off headphones.
    if (model.place.value == Place.Playing) video.setPaused(true)
  }

  override fun onSpatialShutdown() {
    video.release()
    scope.cancel()
    super.onSpatialShutdown()
  }

  override fun registerPanels(): List<PanelRegistration> =
      listOf(
          ui(R.id.panel_login, 0.64f, 0.46f) { LoginPanel(model) },
          ui(R.id.panel_home, Layout.WALL_W, Layout.WALL_H, DP_FAR, curveRadius = Layout.WALL_RADIUS) { HomeWall(model) },
          ui(R.id.panel_screen_art, Layout.SCREEN_W, Layout.SCREEN_H, DP_SCREEN) { ScreenArt(model) },
          ui(R.id.panel_logo_glow, 1.3f, 0.38f) { TitleLogo(model, glow = true) },
          ui(R.id.panel_logo, 1.3f, 0.38f) { TitleLogo(model, glow = false) },
          ui(R.id.panel_title_info, 1.1f, 0.46f) { TitleInfo(model) },
          ui(R.id.panel_episodes, 0.5f, 0.78f) { EpisodesPanel(model) },
          ui(R.id.panel_cast, 0.5f, 0.78f) { CastPanel(model) },
          ui(R.id.panel_controls, 1.1f, 0.36f) { ControlsPanel(model) { stage?.poke() } },
          ui(R.id.panel_subtitles, 2.4f, 0.3f, DP_SCREEN) { SubtitlePanel(model) },
          ui(R.id.panel_toast, 0.8f, 0.12f) { ToastPanel(model) },
          // Within arm's reach, drawn at Meta's density for the distance (500 dp per metre at
          // 1 m), so a 52 dp button is about 6 cm: a fingertip's target, as in Meta's own apps.
          ui(R.id.panel_table, Layout.TABLE_W, Layout.TABLE_H, 300f) { VirtualTable() },
          ui(R.id.panel_mag_0, Layout.COVER_W, Layout.COVER_H, DP_COVER, mesh = true) { MagazineCover(model, shelf, 0) },
          ui(R.id.panel_mag_1, Layout.COVER_W, Layout.COVER_H, DP_COVER, mesh = true) { MagazineCover(model, shelf, 1) },
          ui(R.id.panel_mag_2, Layout.COVER_W, Layout.COVER_H, DP_COVER, mesh = true) { MagazineCover(model, shelf, 2) },
          ui(R.id.panel_mag_3, Layout.COVER_W, Layout.COVER_H, DP_COVER, mesh = true) { MagazineCover(model, shelf, 3) },
          ui(R.id.panel_mag_4, Layout.COVER_W, Layout.COVER_H, DP_COVER, mesh = true) { MagazineCover(model, shelf, 4) },
          ui(R.id.panel_mag_5, Layout.COVER_W, Layout.COVER_H, DP_COVER, mesh = true) { MagazineCover(model, shelf, 5) },
          ui(R.id.panel_page_a, Paper.PW, Paper.PH, DP_PAGE) { MagazinePage(model, shelf, { true }) { it.pageA } },
          ui(R.id.panel_page_b, Paper.PW, Paper.PH, DP_PAGE) { MagazinePage(model, shelf, { false }) { it.pageB } },
          VideoSurfacePanelRegistration(
              R.id.panel_video,
              surfaceConsumer = { _, surface -> video.attach(surface) },
              settingsCreator = {
                MediaPanelSettings(
                    shape = QuadShapeOptions(width = Layout.SCREEN_W, height = Layout.SCREEN_H),
                    display = PixelDisplayOptions(width = 1920, height = 1080),
                    // Compositor layers stack by z-index, not by distance: keep the film under the
                    // subtitle and control panels that float in front of it.
                    rendering = MediaPanelRenderOptions(stereoMode = StereoMode.None, zIndex = -1),
                    style = PanelStyleOptions(themeResourceId = R.style.PanelTransparent),
                )
              },
          ),
      ) + debugPanels()

  /** A Compose panel. Density is in dp per metre: near panels are drawn finer than far ones. */
  private fun ui(
      id: Int,
      widthM: Float,
      heightM: Float,
      dpPerMeter: Float = DP_NEAR,
      curveRadius: Float? = null,
      /** Drawn as a mesh in the scene rather than a compositor layer: it hides behind what is in front of it, like a cover in a pile. */
      mesh: Boolean = false,
      content: @Composable () -> Unit,
  ): PanelRegistration =
      ComposeViewPanelRegistration(
          id,
          composeViewCreator = { _, ctx -> ComposeView(ctx).apply { setContent { Faded(id, content) } } },
          settingsCreator = {
            UIPanelSettings(
                shape =
                    if (curveRadius != null) CylinderShapeOptions(radius = curveRadius, width = widthM, height = heightM)
                    else QuadShapeOptions(width = widthM, height = heightM),
                display = DpPerMeterDisplayOptions(dpPerMeter = dpPerMeter),
                // Soft edges, glass and glows need real blending; the default masks alpha at a
                // threshold, so a 60% shadow either vanishes or turns solid.
                rendering = if (mesh) UIPanelRenderOptions(PanelRenderMode.Mesh()) else UIPanelRenderOptions(PanelRenderMode.Layer(layerBlendType = PanelShapeLayerBlendType.ALPHA_BLEND)),
                style = PanelStyleOptions(themeResourceId = R.style.PanelTransparent),
            )
          },
      )

  @Composable
  private fun Faded(id: Int, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = stage?.fadeOf(id) ?: 0f }) { content() }
  }

  companion object {
    // Text at ~1 m: 1000 dp per metre puts 17 dp body text at about 1° — the comfortable minimum.
    const val DP_NEAR = 1000f
    // The wall sits twice as far, so each dp is drawn larger to keep the same angle.
    const val DP_FAR = 600f
    const val DP_SCREEN = 520f
    const val DP_COVER = 700f
    const val DP_PAGE = 950f
    private const val USE_SCENE = "com.oculus.permission.USE_SCENE"
    private const val SCENE_REQUEST = 7
  }
}
