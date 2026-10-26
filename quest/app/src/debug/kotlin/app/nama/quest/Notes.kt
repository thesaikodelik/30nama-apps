package app.nama.quest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.quest.ui.Directional
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Pill
import app.nama.quest.ui.WallType
import app.nama.quest.ui.glass
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Query
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.PanelShapeLayerBlendType
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelRenderMode
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelRenderOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.Visible
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug only: a note for Claude from inside the headset. Clicking the left stick logs
 * "note-start <id>", and tools/notes.sh, watching the log on the computer, takes the headset's
 * screenshot at once (an app can't capture the compositor) and puts it next to the note. The
 * panel comes up a moment later, so the picture is of what was being looked at. Its text, typed
 * or dictated with the system keyboard's microphone, goes to files/notes/<id>/note.json with the
 * app's state then.
 */
object Notes {
  private const val PANEL_W = 0.62f
  private const val PANEL_H = 0.34f
  /** Long enough for the computer to take the picture before the panel is in it. */
  private const val SHOW_AFTER_MS = 700L

  private val open = MutableStateFlow(false)
  private var entity: Entity? = null
  private var dir: File? = null
  private var id = ""
  private var state = JSONObject()
  private var showAt = 0L
  private var lastButtons = 0
  private var listening = false
  @Volatile private var asked = false
  @Volatile private var askedText: String? = null

  fun panel(): PanelRegistration =
      ComposeViewPanelRegistration(
          R.id.panel_note,
          composeViewCreator = { _, ctx -> ComposeView(ctx).apply { setContent { NotePanel() } } },
          settingsCreator = {
            UIPanelSettings(
                shape = QuadShapeOptions(width = PANEL_W, height = PANEL_H),
                display = DpPerMeterDisplayOptions(dpPerMeter = NamaActivity.DP_NEAR),
                rendering = UIPanelRenderOptions(PanelRenderMode.Layer(layerBlendType = PanelShapeLayerBlendType.ALPHA_BLEND)),
                style = PanelStyleOptions(themeResourceId = R.style.PanelTransparent),
            )
          },
      )

  fun tick(activity: AppSystemActivity, model: AppModel, shelf: Shelf) {
    if (dir == null) dir = activity.getExternalFilesDir(null)?.resolve("notes")
    val e = entity ?: Entity.create(listOf(Panel(R.id.panel_note), Transform(), Visible(false))).also { entity = it }
    var buttons = 0
    Query.where { has(Controller.id) }.eval().forEach { c ->
      val ctl = c.getComponent<Controller>()
      if (ctl.isActive) buttons = buttons or ctl.buttonState
    }
    val pressed = buttons and lastButtons.inv()
    lastButtons = buttons
    if (!listening) {
      listening = true
      // Also from adb, to try it with the headset on the desk:
      //   adb shell am broadcast -a app.nama.quest.NOTE -p app.nama.quest [--es text "…"]
      activity.registerReceiver(object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
          asked = true
          askedText = i.getStringExtra("text")
        }
      }, IntentFilter("app.nama.quest.NOTE"), Context.RECEIVER_EXPORTED)
    }
    if ((pressed and ButtonBits.ButtonThumbLClick != 0 || asked) && !open.value && showAt == 0L) {
      asked = false
      id = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
      state = stateOf(activity, model, shelf)
      Log.i("Nama", "note-start $id")
      showAt = System.currentTimeMillis() + SHOW_AFTER_MS
    }
    askedText?.let { t ->
      // Sent with its text: no panel, once the computer has had its moment for the picture.
      if (showAt != 0L && System.currentTimeMillis() >= showAt) {
        showAt = 0L
        askedText = null
        save(t)
      }
    }
    if (showAt != 0L && System.currentTimeMillis() >= showAt) {
      showAt = 0L
      // Fixed where it opens, at eye level and within reach, above the open magazine (held under
      // the eye) so it is never behind it; it doesn't follow the head.
      val head = activity.scene.getViewerPose()
      val f = head.forward().let { Vector3(it.x, 0f, it.z) }.normalize()
      val p = head.t + f * 0.5f + Vector3(0f, 0.03f, 0f)
      e.setComponent(Transform(Pose(p, Quaternion.lookRotation((p - head.t).normalize(), Vector3(0f, 1f, 0f)))))
      e.setComponent(Visible(true))
      open.value = true
    }
    if (!open.value && showAt == 0L) e.setComponent(Visible(false))
  }

  private fun stateOf(activity: AppSystemActivity, model: AppModel, shelf: Shelf): JSONObject {
    val head = activity.scene.getViewerPose()
    return JSONObject()
        .put("place", model.place.value.name)
        .put("section", model.section.value.name)
        .put("lang", model.lang.value.name)
        .put("shelf", JSONObject().put("phase", shelf.phase.name).put("chosen", shelf.chosen).put("spread", shelf.spread))
        .put("head", JSONObject().put("pos", JSONArray(listOf(head.t.x, head.t.y, head.t.z))).put("quat", JSONArray(listOf(head.q.w, head.q.x, head.q.y, head.q.z))))
  }

  private fun save(text: String) {
    val out = dir?.resolve(id)?.apply { mkdirs() } ?: return
    out.resolve("note.json").writeText(
        JSONObject().put("id", id).put("time", Date().toString()).put("text", text).put("state", state).toString(2))
    Log.i("Nama", "note-saved $id")
    open.value = false
  }

  private fun cancel() {
    Log.i("Nama", "note-cancel $id")
    open.value = false
  }

  @Composable
  private fun NotePanel() {
    val shown by open.collectAsState()
    if (!shown) return
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
      delay(200)
      runCatching { focus.requestFocus() }
    }
    // A tool for the developer, in Persian like the conversation it feeds; not in L10n.
    Directional(true) {
      // Nearly opaque: it opens over whatever is there, and the text must not mix with it.
      Box(Modifier.fillMaxSize().glass(RoundedCornerShape(28.dp), Color(0xF2141316)).padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text("یادداشت برای Claude", style = WallType.body.copy(fontSize = 22.sp, color = Ink.text1))
          BasicTextField(
              text,
              { text = it },
              Modifier.fillMaxWidth().weight(1f).glass(RoundedCornerShape(18.dp)).padding(16.dp).focusRequester(focus),
              textStyle = WallType.body.copy(fontSize = 20.sp, lineHeight = 30.sp, color = Ink.text1, textDirection = TextDirection.Content),
              cursorBrush = SolidColor(Ink.accent),
              decorationBox = { inner ->
                Box {
                  if (text.isEmpty()) Text("بنویسید، یا میکروفون کیبورد را بزنید", style = WallType.body.copy(fontSize = 20.sp, color = Ink.text3))
                  inner()
                }
              },
          )
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("بفرست", { save(text) }, primary = true, enabled = text.isNotBlank())
            Pill("بی‌خیال", ::cancel)
          }
        }
      }
    }
  }
}
