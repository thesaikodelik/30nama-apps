package app.nama.quest

import app.nama.quest.data.Row
import app.nama.quest.data.Tile
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The magazines' 3D scene, simulated on the computer: Meta's own simulator runs 2D apps only.
 * The same MagazineLayout, Shelf and PaperShape as on the headset place everything for each
 * moment of a script (pile, spread, chosen, the cover turning, a page turning); this writes the
 * result as a scene for quest/tools/blender/sim_render.py, which renders it from the eye.
 * Test pages carry their number and an arrow to their top, so a page upside down, mirrored or on
 * the wrong side shows at a glance.
 *
 *   ./gradlew :app:testDebugUnitTest -Psim --tests app.nama.quest.SceneSim
 *   blender --background --python quest/tools/blender/sim_render.py -- quest/build/sim
 */
class SceneSim {
  private val out = File(System.getProperty("nama.sim") ?: "build/sim")

  @Test
  fun scenes() {
    assumeTrue(System.getProperty("nama.sim") != null)
    out.mkdirs()
    for (rtl in listOf(true, false)) {
      val lang = if (rtl) "fa" else "en"
      val rows = List(6) { r -> Row("row$r", "Magazine ${r + 1}", List(5) { t -> Tile("${r}_$t", "Title ${r + 1}.${t + 1}") }) }
      val shelf = Shelf { rows }
      // Seated, eyes 1.2 m up, looking along +z (the SDK's forward for an unturned head).
      val eye = Vector3(0f, 1.2f, 0f)
      val layout = MagazineLayout(eye, Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f), Vector3(1f, 0f, 0f), eye + Vector3(0f, -0.5f, 0.62f), rtl)
      var clock = 0L
      fun shot(name: String, look: Vector3) = write("$lang-$name", shelf, layout, clock, rtl, eye, look)
      val atTable = Vector3(0f, -0.55f, 0.62f)
      val atBook = Vector3(0f, -0.3f, 0.5f)
      shot("1-pile", atTable)
      shelf.touch(0)
      clock = shelf.changedAt + 2_000
      shot("2-spread", atTable)
      shelf.touch(1)
      clock = shelf.changedAt + 2_000
      shot("3-chosen", atBook)
      shelf.holdTurn = 0.35f
      shelf.turn(1)
      shelf.step(0.01f)
      shot("4-cover-turning", atBook)
      shelf.holdTurn = null
      shelf.step(5f)
      shot("5-open", atBook)
      shelf.holdTurn = 0.25f
      shelf.turn(1)
      shelf.step(0.01f)
      shot("6a-page-turning", atBook)
      for ((tag, t) in listOf("6b" to 0.5f, "6c" to 0.75f)) {
        shelf.holdTurn = t
        shelf.step(0.01f)
        shot("$tag-page-turning", atBook)
      }
      shelf.holdTurn = null
      shelf.step(5f)
      shot("7-next-spread", atBook)
    }
  }

  private fun write(name: String, sh: Shelf, layout: MagazineLayout, now: Long, rtl: Boolean, eye: Vector3, look: Vector3) {
    val r = layout.compute(sh, home = true, ready = true, virtualTable = true, now = now)
    val items = ArrayList<String>()
    fun pose(p: Pose) = "\"pos\":[${p.t.x},${p.t.y},${p.t.z}],\"quat\":[${p.q.w},${p.q.x},${p.q.y},${p.q.z}]"
    if (r.table.visible) items += "{\"kind\":\"panel\",${pose(r.table.pose)},\"w\":${Layout.TABLE_W},\"h\":${Layout.TABLE_H},\"tex\":\"wood\"}"
    val closed = if (rtl) "magazine_closed_rtl.glb" else "magazine_closed_ltr.glb"
    for (k in r.bodies.indices) {
      val b = r.bodies[k]
      if (!b.visible) continue
      items += "{\"kind\":\"glb\",\"file\":\"$closed\",${pose(b.pose)},\"scale\":${b.scale}}"
      val c = r.covers[k]
      items += "{\"kind\":\"panel\",${pose(c.pose)},\"w\":${Layout.COVER_W * c.scale},\"h\":${Layout.COVER_H * c.scale},\"tex\":\"${texture("cover$k", "COVER ${k + 1}", 0x3C5A8C + 0x1E0000 * k)}\"}"
    }
    if (r.openBody.visible) items += "{\"kind\":\"glb\",\"file\":\"magazine_open.glb\",${pose(r.openBody.pose)},\"scale\":1}"
    fun sheet(tag: String, side: Float, turn: Float, front: Int, back: Int) {
      val s = PaperShape(28, 12)
      s.lay(side, turn)
      val obj = File(out, "$name-$tag.obj")
      obj.printWriter().use { w ->
        for (v in 0 until s.count * 2) w.println("v ${s.pos[v * 3]} ${s.pos[v * 3 + 1]} ${s.pos[v * 3 + 2]}")
        for (v in 0 until s.count * 2) w.println("vt ${s.uv[v * 2]} ${s.uv[v * 2 + 1]}")
        for (layer in 0..1) {
          w.println("g ${if (layer == 0) "front" else "back"}")
          val half = s.indices.size / 2
          for (k in layer * half until (layer + 1) * half step 3) {
            val a = s.indices[k] + 1
            val b = s.indices[k + 1] + 1
            val c = s.indices[k + 2] + 1
            w.println("f $a/$a $b/$b $c/$c")
          }
        }
      }
      items += "{\"kind\":\"sheet\",\"obj\":\"${obj.name}\",${pose(r.pages.pose)},\"front\":\"${page(front)}\",\"back\":\"${page(back)}\"}"
    }
    if (r.pages.visible) {
      sheet("a", r.sideA, 0f, sh.pageA, -1)
      sheet("b", -r.sideA, 0f, sh.pageB, -1)
    }
    if (r.leaf.visible) sheet("leaf", r.leafSide, r.leafTurn, sh.leafFront, sh.leafBack)
    val target = eye + look
    File(out, "$name.json").writeText("{\"eye\":[${eye.x},${eye.y},${eye.z}],\"target\":[${target.x},${target.y},${target.z}],\"items\":[\n${items.joinToString(",\n")}\n]}")
  }

  private fun page(n: Int): String =
      when {
        n < 0 -> texture("blank", "", 0xF0ECE4)
        n == 0 -> texture("p0", "COVER", 0x963C3C)
        n == 1 -> texture("p1", "CONTENTS", 0xE6E1D7)
        else -> texture("p$n", "PAGE $n", listOf(0x7FB3D5, 0xF5B041, 0x82E0AA, 0xD7BDE2, 0xF1948A, 0xAED6F1)[n % 6])
      }

  private val textures = LinkedHashMap<String, String>()

  /**
   * A test picture, drawn by quest/tools/blender/sim_textures.py from textures.json: its label, an
   * arrow to its top, and L and R at its sides. [rgb] is its background.
   */
  private fun texture(file: String, label: String, rgb: Int): String {
    val name = "$file.png"
    if (textures.put(name, "{\"label\":\"$label\",\"rgb\":$rgb}") == null) {
      File(out, "textures.json").writeText("{" + textures.entries.joinToString(",") { "\"${it.key}\":${it.value}" } + "}")
    }
    return name
  }
}
