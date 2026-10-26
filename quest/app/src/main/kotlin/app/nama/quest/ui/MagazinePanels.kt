package app.nama.quest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Lang
import app.nama.core.S
import app.nama.quest.AppModel
import app.nama.quest.Shelf
import app.nama.quest.data.Tile

// Magazine stock and print, as in the design preview: cream paper, near-black ink, 30nama red.
private val Paper = Color(0xFFF4F0E8)
private val PaperInk = Color(0xFF1D1A17)
private val PaperInk2 = Color(0xFF6B645B)

/** One magazine's cover, on the table or standing in the row. Touching it raises the pile or opens it. */
@Composable
fun MagazineCover(model: AppModel, shelf: Shelf, index: Int) {
  val lang by model.lang.collectAsState()
  model.rows.collectAsState().value
  val row = shelf.magazines().getOrNull(index) ?: return
  val first = row.tiles.first()
  val p = rememberPointed()
  Directional(lang.isRTL) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).pointable(p, { shelf.touch(index) }, 1.03f)) {
      Artwork(first, landscape = false, modifier = Modifier.fillMaxSize(), decodeWidth = 300)
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.35f to Color(0x00000000), 0.55f to Color(0x00000000), 1f to Color(0xD9000000))))
      Column(Modifier.fillMaxSize().padding(12.dp)) {
        Masthead(lang, 22)
        Spacer(Modifier.weight(1f))
        Text(row.title, style = Type.title.copy(fontSize = 21.sp, lineHeight = 25.sp, color = Color.White), maxLines = 2)
        Text("${model.loc.num(row.tiles.size)} ${X.titles(lang)}", style = Type.caption.copy(color = Color(0xCCFFFFFF)))
      }
    }
  }
}

@Composable
private fun Masthead(lang: Lang, size: Int) {
  Row {
    Text(if (lang == Lang.fa) "۳۰" else "30", style = Type.display.copy(fontSize = size.sp, lineHeight = size.sp, color = Ink.accent, fontWeight = FontWeight.Black))
    Text(if (lang == Lang.fa) "نما" else "nama", style = Type.display.copy(fontSize = size.sp, lineHeight = size.sp, color = Color.White, fontWeight = FontWeight.Black))
  }
}

/**
 * What lies over one page of the open magazine: the page itself is printed on the paper below
 * (the poster, full-bleed), so this is clear but for a small sticker with what can be done with
 * the title, and the page's outer edge, which turns it. A swipe across turns it too. [firstOf]
 * says whether this is the half that comes first in reading order.
 */
@Composable
fun MagazinePage(model: AppModel, shelf: Shelf, firstOf: (Shelf) -> Boolean, page: (Shelf) -> Int) {
  val lang by model.lang.collectAsState()
  model.rows.collectAsState().value
  val index = page(shelf)
  val first = firstOf(shelf)
  val rtl = lang.isRTL
  Directional(rtl) {
    Box(
        Modifier.fillMaxSize()
            .pointerInput(rtl) {
              var dx = 0f
              detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                // A Persian magazine reads right to left: the next page comes from the left.
                val next = if (rtl) dx > 0 else dx < 0
                if (kotlin.math.abs(dx) > 30.dp.toPx()) shelf.turn(if (next) 1 else -1)
              }) { _, amount -> dx += amount }
            },
    ) {
      if (index == 0) {
        // The shut magazine's cover: the whole of it opens the magazine.
        val cover = rememberPointed()
        Box(Modifier.fillMaxSize().pointable(cover, { shelf.turn(1) }, 1f))
      }
      val edge = rememberPointed()
      Box(
          Modifier.align(if (first) Alignment.CenterStart else Alignment.CenterEnd)
              .width(40.dp)
              .fillMaxHeight()
              .background(if (edge.hovered) Brush.horizontalGradient(listOf(Color(0x00000000), Color(0x30000000))) else Brush.horizontalGradient(listOf(Color(0x00000000), Color(0x00000000))))
              .pointable(edge, { shelf.turn(if (first) -1 else 1) }, 1f),
      )
      shelf.tileAt(index)?.let { tile ->
        // A sticker in the page's lower outer corner, like a price tag on a cover.
        Row(
            Modifier.align(if (first) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xD9141316))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          PageButton(model.loc(if (tile.progress != null) S.resume else S.play), primary = true) { model.openAndPlay(tile) }
          PageButton(model.loc(S.details), primary = false) { model.open(tile) }
        }
      }
    }
  }
}

@Composable
private fun CoverPage(model: AppModel, shelf: Shelf, lang: Lang) {
  val row = shelf.row() ?: return
  Box(Modifier.fillMaxSize()) {
    Artwork(row.tiles.first(), landscape = false, modifier = Modifier.fillMaxSize(), decodeWidth = 600)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x80000000), 0.3f to Color(0x00000000), 0.5f to Color(0x00000000), 1f to Color(0xE6000000))))
    Column(Modifier.fillMaxSize().padding(22.dp)) {
      Masthead(lang, 44)
      Spacer(Modifier.height(6.dp))
      Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color.White))
      Text(X.thisWeek(lang), style = Type.caption.copy(color = Color(0xCCFFFFFF)), modifier = Modifier.padding(vertical = 3.dp))
      Spacer(Modifier.weight(1f))
      Text(row.title, style = Type.display.copy(fontSize = 38.sp, lineHeight = 42.sp, color = Color.White))
      Spacer(Modifier.height(8.dp))
      row.tiles.take(3).forEach { t ->
        Text("› ${t.title}", style = Type.label.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** A whole page for one title: picture, name, figures, a few lines, and what can be done with it. */
@Composable
private fun TitlePage(model: AppModel, tile: Tile, rank: Int?) {
  val loc = model.loc
  Column(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxWidth().weight(0.56f)) {
      Artwork(tile, landscape = true, modifier = Modifier.fillMaxSize(), decodeWidth = 640)
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color(0x00000000), 1f to Color(0xB3000000))))
      if (rank != null) {
        Text(loc.num(rank), style = Type.display.copy(fontSize = 64.sp, lineHeight = 64.sp, color = Color.White), modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
      }
      Text(tile.title, style = Type.title.copy(fontSize = 28.sp, lineHeight = 32.sp, color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 12.dp))
      tile.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
    }
    Column(Modifier.fillMaxWidth().weight(0.44f).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val facts = listOfNotNull(tile.rating?.let { "★ ${loc.num(it)}" }, tile.year?.let(loc::num), tile.genres.take(2).joinToString("، ").ifEmpty { null })
      Text(facts.joinToString("  ·  "), style = Type.label.copy(color = PaperInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
      tile.note?.let { Text(it, style = Type.caption.copy(color = PaperInk2, fontSize = 14.sp), maxLines = 1) }
      tile.originalTitle?.takeIf { it != tile.title }?.let { Text(it, style = Type.caption.copy(color = PaperInk2, fontSize = 14.sp), maxLines = 1) }
      Spacer(Modifier.weight(1f))
      // Big enough for a fingertip at arm's length: 52 dp is about 6 cm on this page.
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PageButton(loc(if (tile.progress != null) S.resume else S.play), primary = true) { model.openAndPlay(tile) }
        PageButton(loc(S.details), primary = false) { model.open(tile) }
      }
    }
  }
}

@Composable
private fun PageButton(text: String, primary: Boolean, onClick: () -> Unit) {
  val p = rememberPointed()
  Box(
      Modifier.heightIn(min = 52.dp)
          .clip(RoundedCornerShape(50))
          .background(if (primary) Ink.accent else Color(0x26FFFFFF))
          .pointable(p, onClick, 1.05f)
          .padding(horizontal = 22.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(text, style = Type.button.copy(fontSize = 18.sp, color = Color.White))
  }
}

@Composable
private fun EndPage(lang: Lang) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Masthead(lang, 30)
  }
}

/** The table the magazines lie on when the room has none to offer. */
@Composable
fun VirtualTable() {
  Box(
      Modifier.fillMaxSize()
          .clip(RoundedCornerShape(28.dp))
          .background(Brush.radialGradient(listOf(Color(0xFF5A4637), Color(0xFF3A2C22), Color(0xFF2A2019)))),
  )
}
