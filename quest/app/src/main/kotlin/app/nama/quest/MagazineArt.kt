package app.nama.quest

import android.content.Context
import android.graphics.Bitmap
import app.nama.quest.ui.Images
import app.nama.quest.ui.X
import com.meta.spatial.runtime.SceneTexture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pictures printed on the magazine's pages: a page's poster full-bleed, and page 0 the cover.
 * Made once per page and kept; until one is ready the page is plain paper.
 */
class MagazineArt(private val context: Context, private val scope: CoroutineScope, private val model: AppModel, private val shelf: Shelf) {
  private val ready = HashMap<String, SceneTexture>()
  private val asked = HashSet<String>()
  val blank: SceneTexture by lazy { Paper.blank() }

  /** The texture for [page] of the open magazine, or null while it is being made (it is asked for). */
  fun page(page: Int): SceneTexture? {
    val row = shelf.row() ?: return null
    if (page < 0) return blank
    val lang = model.lang.value
    val key = "${row.key}|$page|${lang.name}"
    ready[key]?.let { return it }
    if (asked.add(key)) {
      scope.launch {
        val bitmap: Bitmap? =
            if (page == 1) {
              withContext(Dispatchers.Default) { Paper.contents(context, row.title, row.tiles.map { it.title }, model.loc, lang) }
            } else if (page == 0) {
              val poster = row.tiles.firstOrNull()?.poster?.let { Images.load(it, 900) }
              withContext(Dispatchers.Default) { Paper.cover(context, poster, row.title, "${model.loc.num(row.tiles.size)} ${X.titles(lang)}", lang) }
            } else {
              val tile = shelf.tileAt(page) ?: return@launch
              val poster = (tile.poster ?: tile.backdrop)?.let { Images.load(it, 900) } ?: return@launch
              withContext(Dispatchers.Default) { Paper.page(poster) }
            }
        if (bitmap != null) ready[key] = Paper.texture(bitmap)
      }
    }
    return null
  }
}
