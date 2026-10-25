package app.nama.quest.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nama.core.Fmt
import app.nama.core.Menu
import app.nama.core.MenuEntry
import app.nama.core.S
import app.nama.quest.AppModel
import app.nama.quest.Route
import app.nama.quest.Services
import app.nama.quest.ui.Ink
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.WallError
import app.nama.quest.ui.WallLoading
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fadeBottom
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.pointable
import app.nama.quest.ui.rememberImage
import app.nama.quest.ui.rememberPointed
import app.nama.quest.ui.screens.catalog.Mark
import app.nama.quest.ui.screens.catalog.MarkGlyph
import app.nama.quest.ui.screens.catalog.Memo
import kotlinx.coroutines.CancellationException

/**
 * Explore: the Mac's BrowseView. The website's own pages first (weekly schedule, top lists,
 * news; only with the site's key), then every genre and category as a picture tile that opens
 * its own wall of posters.
 */
@Composable
fun BrowseScreen(model: AppModel) {
  NeedsService(model) { services -> Browse(model, services) }
}

@Composable
private fun Browse(model: AppModel, services: Services) {
  val lang by model.lang.collectAsState()
  var menu by remember(lang) { mutableStateOf<Menu?>(null) }
  var failed by remember { mutableStateOf(false) }
  var attempt by remember { mutableIntStateOf(0) }
  LaunchedEffect(lang, attempt) {
    failed = false
    try {
      menu = Memo.menu(services.api, lang)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      if (menu == null) failed = true
    }
  }
  val m = menu
  when {
    m != null -> BrowseWall(model, services, m)
    failed -> WallError(model) { attempt++ }
    else -> WallLoading(model)
  }
}

@Composable
private fun BrowseWall(model: AppModel, services: Services, menu: Menu) {
  val loc = model.loc
  LazyVerticalGrid(
      GridCells.Adaptive(300.dp),
      Modifier.fillMaxSize().fadeBottom(),
      contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 90.dp),
      horizontalArrangement = Arrangement.spacedBy(18.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    // What only the 30nama website has, through its own API.
    if (services.api.hasSite) {
      fullLine("site") {
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
          SiteTile(loc(S.weeklySchedule), Mark.Calendar, Modifier.weight(1f)) { model.push(Route.Schedule) }
          SiteTile(loc(S.topLists), Mark.Trophy, Modifier.weight(1f)) { model.push(Route.TopLists) }
          SiteTile(loc(S.news), Mark.News, Modifier.weight(1f)) { model.push(Route.News) }
        }
      }
    }
    group(model, "genres", loc(S.genres), menu.genres)
    group(model, "categories", loc(S.categories), menu.categories)
  }
}

private fun LazyGridScope.group(model: AppModel, key: String, title: String, entries: List<MenuEntry>) {
  if (entries.isEmpty()) return
  fullLine("$key-title") { Text(title, style = WallType.heading, modifier = Modifier.padding(start = 4.dp, top = 14.dp)) }
  items(entries, key = { "$key-${it.slug}" }) { entry -> MenuTile(model, entry) { model.push(Route.Catalog(entry.slug, entry.name)) } }
}

/** A way into one of the site's own pages: an accent mark and its name on glass. */
@Composable
private fun SiteTile(title: String, mark: Mark, modifier: Modifier, onClick: () -> Unit) {
  val p = rememberPointed()
  val shape = RoundedCornerShape(20.dp)
  Row(
      modifier
          .height(92.dp)
          .pointable(p, onClick, 1.02f)
          .clip(shape)
          .background(if (p.hovered) Color(0x1FF4F1EA) else Color(0x0FF4F1EA))
          .border(if (p.hovered) 2.dp else 1.dp, if (p.hovered) Ink.text1.copy(alpha = 0.9f) else Ink.hairline, shape)
          .padding(horizontal = 26.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    MarkGlyph(mark, Modifier.size(34.dp), Ink.accent)
    Text(title, style = WallType.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** A genre or category: its picture, darkened toward the bottom, its name and how many titles. */
@Composable
private fun MenuTile(model: AppModel, entry: MenuEntry, onClick: () -> Unit) {
  val p = rememberPointed()
  val shape = RoundedCornerShape(18.dp)
  val image = rememberImage((entry.thumb ?: entry.full)?.toString(), 480)
  Box(
      Modifier.fillMaxWidth()
          .height(140.dp)
          .pointable(p, onClick, 1.03f)
          .clip(shape)
          .background(Ink.surface2)
          .border(if (p.hovered) 2.dp else 1.dp, if (p.hovered) Ink.text1.copy(alpha = 0.9f) else Ink.hairline, shape),
  ) {
    image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x1A000000), 1f to Color(0xD9000000))))
    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
      Text(entry.name, style = WallType.label.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
      entry.count?.takeIf { it > 0 }?.let {
        Spacer(Modifier.height(2.dp))
        Text("${Fmt.count(it, model.lang.value)} ${model.loc(S.results)}", style = WallType.caption.copy(color = Color.White.copy(alpha = 0.72f)))
      }
    }
  }
}
