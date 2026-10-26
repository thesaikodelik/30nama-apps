package app.nama.quest

import android.content.Context
import android.graphics.Bitmap
import app.nama.quest.data.Row
import app.nama.quest.ui.Images
import app.nama.quest.ui.X
import com.meta.spatial.core.Pose
import java.io.File

/**
 * The magazines' scene as a file quest/tools/blender/sim_render.py draws: on the computer from
 * SceneSim, and on the headset (debug builds, "--ez dump true") from what the stage really
 * placed, with the head where it really was, so the two can be laid side by side.
 * [texture] names the picture a page or cover carries (test pictures on the computer, the real
 * page numbers on the headset).
 */
class SceneDump(private val dir: File, private val texture: (String, Int) -> String) {
  fun write(name: String, sh: Shelf, r: MagazineLayout.Result, rtl: Boolean, eye: Pose, look: com.meta.spatial.core.Vector3, origin: Pose? = null) {
    dir.mkdirs()
    val items = ArrayList<String>()
    fun pose(p: Pose) = "\"pos\":[${p.t.x},${p.t.y},${p.t.z}],\"quat\":[${p.q.w},${p.q.x},${p.q.y},${p.q.z}]"
    if (r.table.visible) items += "{\"kind\":\"panel\",${pose(r.table.pose)},\"w\":${Layout.TABLE_W},\"h\":${Layout.TABLE_H},\"tex\":\"wood\"}"
    val closed = if (rtl) "magazine_closed_rtl.glb" else "magazine_closed_ltr.glb"
    for (k in r.bodies.indices) {
      val b = r.bodies[k]
      if (!b.visible) continue
      items += "{\"kind\":\"glb\",\"file\":\"$closed\",${pose(b.pose)},\"scale\":${b.scale}}"
      val c = r.covers[k]
      items += "{\"kind\":\"panel\",${pose(c.pose)},\"w\":${Layout.COVER_W * c.scale},\"h\":${Layout.COVER_H * c.scale},\"tex\":\"${texture("cover", k)}\"}"
    }
    if (r.openBody.visible) items += "{\"kind\":\"glb\",\"file\":\"magazine_open.glb\",${pose(r.openBody.pose)},\"scale\":1}"
    fun sheet(tag: String, side: Float, turn: Float, front: Int, back: Int) {
      val s = PaperShape(28, 12)
      s.lay(side, turn)
      val obj = File(dir, "$name-$tag.obj")
      obj.printWriter().use { w ->
        for (v in 0 until s.count * 2) w.println("v ${s.pos[v * 3]} ${s.pos[v * 3 + 1]} ${s.pos[v * 3 + 2]}")
        for (v in 0 until s.count * 2) w.println("vt ${s.uv[v * 2]} ${s.uv[v * 2 + 1]}")
        val half = s.indices.size / 2
        for (layer in 0..1) {
          w.println("g ${if (layer == 0) "front" else "back"}")
          for (k in layer * half until (layer + 1) * half step 3) {
            val a = s.indices[k] + 1
            val b = s.indices[k + 1] + 1
            val c = s.indices[k + 2] + 1
            w.println("f $a/$a $b/$b $c/$c")
          }
        }
      }
      items += "{\"kind\":\"sheet\",\"obj\":\"${obj.name}\",${pose(r.pages.pose)},\"front\":\"${texture("page", front)}\",\"back\":\"${texture("page", back)}\"}"
    }
    if (r.pages.visible) {
      sheet("a", r.sideA, 0f, sh.pageA, -1)
      sheet("b", -r.sideA, 0f, sh.pageB, -1)
    }
    if (r.leaf.visible) sheet("leaf", r.leafSide, r.leafTurn, sh.leafFront, sh.leafBack)
    val target = eye.t + look
    val up = eye.q * com.meta.spatial.core.Vector3(0f, 1f, 0f)
    File(dir, "$name.json").writeText(
        "{\"eye\":[${eye.t.x},${eye.t.y},${eye.t.z}],\"target\":[${target.x},${target.y},${target.z}],\"up\":[${up.x},${up.y},${up.z}],\"head\":{${pose(eye)}},${origin?.let { "\"origin\":{${pose(it)}}," } ?: ""}\"items\":[\n${items.joinToString(",\n")}\n]}")
  }

  /**
   * The pictures the magazines really carry, for the viewer on the computer (tools/sim/view
   * --device): each magazine's cover, contents and title pages, made by the same code as on the
   * headset, and the screen's first picture. art.json says how many pages each has.
   */
  suspend fun art(context: Context, model: AppModel, rows: List<Row>) {
    val out = File(dir, "art").apply { mkdirs() }
    val lang = model.lang.value
    fun save(name: String, b: Bitmap) = File(out, name).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    val counts = ArrayList<Int>()
    for ((k, row) in rows.withIndex()) {
      val first = row.tiles.firstOrNull()?.poster?.let { Images.load(it, 900) }
      save("m$k-p0.jpg", Paper.cover(context, first, row.title, "${model.loc.num(row.tiles.size)} ${X.titles(lang)}", lang))
      save("m$k-p1.jpg", Paper.contents(context, row.title, row.tiles.map { it.title }, model.loc, lang))
      var n = 2
      for (tile in row.tiles.take(ART_PAGES)) {
        val poster = (tile.poster ?: tile.backdrop)?.let { Images.load(it, 900) } ?: continue
        save("m$k-p$n.jpg", Paper.page(poster))
        n++
      }
      counts += n
    }
    rows.firstOrNull()?.tiles?.firstOrNull()?.let { t -> (t.backdrop ?: t.poster)?.let { Images.load(it, 1600) }?.let { save("screen.jpg", it) } }
    File(out, "art.json").writeText("{\"lang\":\"${lang.name}\",\"pages\":[${counts.joinToString(",")}]}")
  }

  companion object {
    /** Enough title pages to turn through a few spreads, without a minute of downloads. */
    const val ART_PAGES = 10
  }
}
