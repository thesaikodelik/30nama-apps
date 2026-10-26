package app.nama.quest

import app.nama.quest.data.Row
import app.nama.quest.data.Tile
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Everything the interactive viewer (quest/tools/sim/viewer.html) needs to show the magazines on
 * the computer, computed by the app's own code: where each body and cover goes in every state
 * (pile, spread, each magazine chosen shut, each open), where the open magazine and its pages
 * lie, and the paper's shape through a whole page turn. The viewer only plays these back and
 * eases between them as the headset does; it decides nothing about layout itself.
 *
 *   quest/tools/sim/view    (builds this, the pictures, and opens the viewer)
 */
class SceneExport {
  @Test
  fun export() {
    val dir = System.getProperty("nama.viewer")
    assumeTrue(dir != null)
    val out = File(dir!!)
    out.mkdirs()
    val eye = Vector3(0f, 1.2f, 0f)
    val langs = ArrayList<String>()
    for (rtl in listOf(true, false)) {
      val rows = List(6) { r -> Row("row$r", "Magazine ${r + 1}", List(7) { t -> Tile("${r}_$t", "Title ${r + 1}.${t + 1}") }) }
      val shelf = Shelf { rows }
      val layout = MagazineLayout(eye, Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f), Vector3(1f, 0f, 0f), eye + Vector3(0f, -0.5f, 0.62f), rtl)
      val states = LinkedHashMap<String, String>()
      fun state(key: String) {
        // Long after the change, so every magazine has reached where the state puts it.
        val r = layout.compute(shelf, home = true, ready = true, virtualTable = true, now = shelf.changedAt + 10_000)
        states[key] =
            "{\"table\":${place(r.table)},\"bodies\":[${r.bodies.joinToString(",") { place(it) }}],\"covers\":[${r.covers.joinToString(",") { place(it) }}]," +
                "\"open\":${place(r.openBody)},\"sideA\":${r.sideA}}"
      }
      state("pile")
      shelf.touch(0)
      state("row")
      for (k in 0 until 6) {
        shelf.open(k)
        state("shut$k")
        shelf.turn(1)
        shelf.step(5f)
        state("open$k")
        shelf.back()
      }
      langs += "\"${if (rtl) "fa" else "en"}\":{\"book\":${pose(layout.book)},\"states\":{${states.entries.joinToString(",") { "\"${it.key}\":${it.value}" }}}}"
    }

    // The paper, in the open magazine's frame: lying on each side, and every step of a turn.
    val shape = PaperShape(28, 12)
    fun arr(a: FloatArray) = a.joinToString(",") { "%.4f".format(java.util.Locale.ROOT, it) }
    val sides = ArrayList<String>()
    for (side in listOf(1f, -1f)) {
      val frames = ArrayList<String>()
      for (f in 0..FRAMES) {
        val t = f.toFloat() / FRAMES
        shape.lay(side, t * t * (3f - 2f * t))
        frames += "[${arr(shape.pos)}]"
      }
      shape.lay(side, 0f)
      sides += "\"${if (side > 0) "plus" else "minus"}\":{\"uv\":[${arr(shape.uv)}],\"frames\":[${frames.joinToString(",")}]}"
    }
    File(out, "scene.json").writeText(
        "{\"eye\":[${eye.x},${eye.y},${eye.z}],\"cover\":[${Layout.COVER_W},${Layout.COVER_H}],\"table\":[${Layout.TABLE_W},${Layout.TABLE_H}]," +
            "\"turnSeconds\":${Shelf.TURN_SECONDS},\"screen\":{\"pos\":[0,${eye.y + Layout.screenHome.y},${Layout.screenHome.d}],\"w\":${Layout.SCREEN_W},\"h\":${Layout.SCREEN_H}}," +
            "\"paper\":{\"count\":${shape.count},\"indices\":[${shape.indices.joinToString(",")}],${sides.joinToString(",")}}," +
            "\"langs\":{${langs.joinToString(",")}}}")
    // The test pictures the viewer prints on covers and pages.
    val tex = LinkedHashMap<String, String>()
    for (k in 0 until 6) tex["cover$k.png"] = "{\"label\":\"COVER ${k + 1}\",\"rgb\":${0x3C5A8C + 0x1E0000 * k}}"
    tex["p0.png"] = "{\"label\":\"COVER\",\"rgb\":${0x963C3C}}"
    tex["p1.png"] = "{\"label\":\"CONTENTS\",\"rgb\":${0xE6E1D7}}"
    val tints = listOf(0x7FB3D5, 0xF5B041, 0x82E0AA, 0xD7BDE2, 0xF1948A, 0xAED6F1)
    for (n in 2..8) tex["p$n.png"] = "{\"label\":\"PAGE $n\",\"rgb\":${tints[n % 6]}}"
    tex["blank.png"] = "{\"label\":\"\",\"rgb\":${0xF0ECE4}}"
    File(out, "textures.json").writeText("{" + tex.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + "}")
  }

  private fun pose(p: Pose) = "{\"p\":[${p.t.x},${p.t.y},${p.t.z}],\"q\":[${p.q.w},${p.q.x},${p.q.y},${p.q.z}]}"

  private fun place(p: Placement) = "{\"pose\":${pose(p.pose)},\"on\":${p.visible},\"s\":${p.scale}}"

  companion object {
    private const val FRAMES = 30
  }
}
