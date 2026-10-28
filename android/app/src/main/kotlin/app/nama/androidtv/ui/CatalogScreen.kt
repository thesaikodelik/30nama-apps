package app.nama.androidtv.ui

import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import app.nama.androidtv.attempt
import app.nama.core.Filter
import app.nama.core.FilterOptions
import app.nama.core.Fmt
import app.nama.core.Menu
import app.nama.core.MenuEntry
import app.nama.core.Post
import app.nama.core.PostPage
import app.nama.core.S
import app.nama.core.SiteQuery
import app.nama.core.fromSite
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Backdrop for list screens: the base colour with a faint warm glow from the top corner. */
@Composable
fun Ambient(content: @Composable () -> Unit) {
  // The Mac's AmbientBackground: a warm light from the top right corner, a cool one from the bottom
  // left, each a gradient that costs nothing. Its 900 and 800 points are ×1.2 on a 1920 screen.
  // Not mirrored in Persian and Arabic: the Mac's stays where it is (docs/design/mac/fa, measured).
  Box(
      Modifier.fillMaxSize()
          .background(Theme.bg0)
          .drawBehind {
            drawRect(Brush.radialGradient(listOf(Theme.accent.copy(alpha = 0.16f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width, 0f), radius = 1080.dp.toPx()))
            drawRect(Brush.radialGradient(listOf(Color(0xFF2B3A67).copy(alpha = 0.25f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(0f, size.height), radius = 960.dp.toPx()))
          }) {
    content()
  }
}

/** Sort orders of a catalogue (the Apple TV's CatalogView). "Newest" needs the website's own API. */
private enum class Sort(val order: String, val label: S) { Updated("update", S.updated), Newest("id", S.newest), Popular("favorite", S.popular) }

/** Posters across a wall: (1920 − 2 × 80 + 20) / (200 + 20). */
const val WALL = 8

/**
 * What of a list fills whole rows of a wall; the rest waits for the next page, so no row stands half
 * full while more is coming. Once the list is [complete] all of it shows.
 */
fun <T> wholeRows(list: List<T>, complete: Boolean): List<T> = if (complete) list else list.take(list.size / WALL * WALL)

/**
 * A page of posters, more as the remote nears the end: [header] above, and what comes when focus
 * is within a couple of rows of the last.
 */
@Composable
fun PagedGrid(
    posts: List<Post>,
    loading: Boolean,
    /** Every page is in: the last row may then be part of one. */
    complete: Boolean,
    more: () -> Unit,
    /** The last page did not come: a retry at the end, not another try at every move. */
    failed: Boolean = false,
    header: LazyGridScope.() -> Unit = {},
    onOpen: (Post) -> Unit,
) {
  val state = rememberLazyGridState()
  ReportTop(state)
  val stopped by androidx.compose.runtime.rememberUpdatedState(failed)
  LaunchedEffect(state, posts.size) {
    snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
      if (!stopped && last >= state.layoutInfo.totalItemsCount - 15) more()
    }
  }
  LazyVerticalGrid(
      GridCells.FixedSize(Theme.posterWidth),
      Modifier.fillMaxSize().focusRestorer(),
      state = state,
      contentPadding = PaddingValues(start = Theme.edge, end = Theme.edge, top = 130.dp, bottom = 80.dp),
      horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.Start),
      verticalArrangement = Arrangement.spacedBy(26.dp),
  ) {
    header()
    // A wall of posters, as the Mac's: each one's name comes up on it in focus.
    itemsIndexed(wholeRows(posts, complete || failed), key = { _, p -> p.id }) { _, post -> PosterCard(post, { onOpen(post) }) }
    if (failed && posts.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
      Box(Modifier.padding(top = 20.dp)) { PillButton(LocalLoc.current(S.retry), more, icon = Icons.replay) }
    }
    // Whole rows of stand-ins, under rows that are whole too.
    if (loading) {
      items(if (posts.isEmpty()) WALL * 2 else WALL) { Skeleton(Modifier.size(Theme.posterWidth, Theme.posterWidth * 1.5f).padding(bottom = 8.dp), Theme.cardRadius) }
    }
  }
}

/**
 * A category or genre, a page at a time: sorted, and narrowed by genre, country, a lowest IMDb
 * score and a decade, alone or together (as on the Mac and in 30namaTV). The server reads each
 * of those in a shape of its own; core's Filter sends them so.
 */
@Composable
fun CatalogScreen(slug: String, title: String) {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  /** "movie", "series" or "anime" for the three catalogue tabs: the filters and the site's newest list are by kind. */
  val kind = mapOf("movies" to "movie", "series" to "series", "anime" to "anime")[slug]
  val sorts = Sort.entries.filter { it != Sort.Newest || (kind != null && app.api.hasSite) }
  var sort by rememberSaveable { mutableStateOf(Sort.Updated) }
  var filter by remember { mutableStateOf(Filter()) }
  var options by remember { mutableStateOf<FilterOptions?>(null) }
  var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
  var page by remember { mutableIntStateOf(0) }
  var pages by remember { mutableIntStateOf(1) }
  var total by remember { mutableStateOf<Int?>(null) }
  // Loading from the first frame, so the page opens on stand-ins rather than on "nothing found".
  var loading by remember { mutableStateOf(true) }
  var failed by remember { mutableStateOf(false) }
  var generation by remember { mutableIntStateOf(0) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  fun loadNext() {
    if (loading || page >= pages) return
    loading = true
    failed = false
    val asked = generation
    scope.launch {
      val next: PostPage? = attempt {
        when {
          !filter.isEmpty && kind != null -> PostPage.from(app.api.get("advanced_search", mapOf("parameters" to filter.parameters(kind), "orderby" to sort.order, "order" to "DESC", "page" to (page + 1).toString())))
          sort == Sort.Newest && kind != null -> PostPage.fromSite(app.api.site(SiteQuery.newest(kind, null, true, page + 1)))
          else -> PostPage.from(app.api.get("archive", mapOf("slugs" to slug, "streamonly" to "1", "page" to (page + 1).toString(), "orderby" to sort.order, "order" to "desc")))
        }
      }
      // A newer sort or filter restarted the list; this answer belongs to the old one.
      if (asked != generation) return@launch
      loading = false
      if (next == null) {
        failed = true
        return@launch
      }
      val known = posts.map { it.id }.toSet()
      // Repeats within a page too: the site's lists have them, and a repeated key crashes a lazy grid.
      posts = (posts + next.posts.filter { it.id !in known }).distinctBy { it.id }
      page = next.page
      pages = next.pages
      total = next.total.takeIf { it > 0 && !(sort == Sort.Newest && filter.isEmpty) }
    }
  }

  LaunchedEffect(sort, filter) {
    generation++
    posts = emptyList()
    page = 0
    pages = 1
    total = null
    loading = false
    loadNext()
  }
  LaunchedEffect(kind) { if (kind != null) options = attempt { FilterOptions.from(app.api.get("advanced_search_parameters")) } }

  fun choose(titleKey: S, choices: List<Pair<String, String?>>, current: String?, set: (String?) -> Unit) {
    val actions = choices.map { (name, value) -> SheetAction(name, selected = value == current) { set(value) } }
    app.sheets.show(SheetSpec(loc(titleKey), actions, focus = choices.indexOfFirst { it.second == current }.coerceAtLeast(0)))
  }

  Ambient {
    if (failed && posts.isEmpty()) {
      ErrorState { loadNext() }
    } else {
      PagedGrid(posts, loading, page >= pages, ::loadNext, failed = failed, header = {
        item(span = { GridItemSpan(maxLineSpan) }) {
          // The Mac's header: the name and its count at the start, the orders at the end, and the
          // filters as menus under the name.
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().focusRestorer(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(title, style = app(44, FontWeight.Bold), color = Theme.text1, maxLines = 1)
                total?.let { Text("${Fmt.count(it, loc.lang)} ${loc(S.results)}", Modifier.padding(bottom = 8.dp), style = app(20, FontWeight.Medium), color = Theme.text3) }
              }
              sorts.forEach { s -> PillButton(loc(s.label), { sort = s }, kind = PillKind.Chip, selected = s == sort) }
            }
            LazyRow(Modifier.bleed(14.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
              options?.let { o ->
                val all = loc(S.allChoices)
                val menu = Icons.chevronDown
                item {
                  val name = o.genres.firstOrNull { it.value == filter.genre }?.name ?: loc(S.genres)
                  PillButton(name, { choose(S.genres, listOf(all to null) + o.genres.map { it.name to it.value }, filter.genre) { filter = filter.copy(genre = it) } }, kind = PillKind.Chip, selected = filter.genre != null, trailing = menu)
                }
                item {
                  val name = o.countries.firstOrNull { it.value == filter.country }?.name ?: loc(S.country)
                  PillButton(name, { choose(S.country, listOf(all to null) + o.countries.map { it.name to it.value }, filter.country) { filter = filter.copy(country = it) } }, kind = PillKind.Chip, selected = filter.country != null, trailing = menu)
                }
                item {
                  val name = o.scores.firstOrNull { it.value == filter.score }?.name ?: loc(S.minScore)
                  PillButton(name, { choose(S.minScore, listOf(all to null) + o.scores.map { it.name to it.value }, filter.score) { filter = filter.copy(score = it) } }, kind = PillKind.Chip, selected = filter.score != null, trailing = menu)
                }
                item {
                  val name = filter.decade?.let { loc.num("${it}s") } ?: loc(S.decade)
                  PillButton(name, { choose(S.decade, listOf(all to null) + o.decades.map { loc.num("${it}s") to it.toString() }, filter.decade?.toString()) { filter = filter.copy(decade = it?.toIntOrNull()) } }, kind = PillKind.Chip, selected = filter.decade != null, trailing = menu)
                }
                if (!filter.isEmpty) item { PillButton(loc(S.reset), { filter = Filter() }, kind = PillKind.Chip, icon = Icons.close) }
              }
            }
            if (!loading && posts.isEmpty() && page >= pages) Text(loc(S.noMatches), style = app(28), color = Theme.text2)
          }
        }
      }) { nav.push(Route.Details(it)) }
    }
  }
}

/**
 * Entry point to the whole catalogue, as the Mac's: what only the website has (the weekly schedule,
 * the top lists) as wide glass tiles, then the genres and the categories as picture tiles.
 */
@Composable
fun BrowseScreen() {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  var menu by remember { mutableStateOf<Menu?>(null) }
  var failed by remember { mutableStateOf(false) }
  var reload by remember { mutableIntStateOf(0) }
  LaunchedEffect(reload, loc.lang) {
    failed = false
    app.api.setLanguage(loc.lang)
    menu = attempt { Menu.from(app.api.get("menu")) }
    failed = menu == null
  }
  Ambient {
    val m = menu
    val browseGrid = rememberLazyGridState()
    ReportTop(browseGrid)
    when {
      m != null -> LazyVerticalGrid(
          GridCells.Adaptive(273.dp),
          Modifier.fillMaxSize().focusRestorer(),
          state = browseGrid,
          contentPadding = PaddingValues(start = Theme.edge, end = Theme.edge, top = 130.dp, bottom = 80.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        // What only the 30nama website has, through its own API.
        if (app.api.hasSite) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(bottom = 22.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
              SiteTile(loc(S.weeklySchedule), Icons.calendar, Modifier.weight(1f)) { nav.push(Route.Schedule) }
              SiteTile(loc(S.topLists), Icons.trophy, Modifier.weight(1f)) { nav.push(Route.TopLists) }
              SiteTile(loc(S.news), Icons.newspaper, Modifier.weight(1f)) { nav.push(Route.News) }
            }
          }
        }
        if (m.genres.isNotEmpty()) {
          item(span = { GridItemSpan(maxLineSpan) }) { Text(loc(S.genres), style = app(34, FontWeight.Bold), color = Theme.text1) }
          items(m.genres.size) { i -> GenreTile(m.genres[i]) { nav.push(Route.Catalog(m.genres[i].slug, m.genres[i].name)) } }
        }
        if (m.categories.isNotEmpty()) {
          item(span = { GridItemSpan(maxLineSpan) }) { Text(loc(S.categories), Modifier.padding(top = 22.dp), style = app(34, FontWeight.Bold), color = Theme.text1) }
          items(m.categories.size) { i -> GenreTile(m.categories[i]) { nav.push(Route.Catalog(m.categories[i].slug, m.categories[i].name)) } }
        }
      }
      failed -> ErrorState { reload++ }
      else -> Box(Modifier.fillMaxSize().padding(start = Theme.edge, top = 260.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { repeat(6) { Skeleton(Modifier.size(273.dp, 153.dp), 18.dp) } }
      }
    }
  }
}

/** The Mac's `SiteTile`: a wide glass tile with an icon in the title's colour and a name. */
@Composable
private fun SiteTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
  val shape = RoundedCornerShape(18.dp)
  val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  androidx.tv.material3.Surface(
      onClick = onClick,
      modifier = modifier.focusRing(interaction, shape),
      interactionSource = interaction,
      shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
      colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f), focusedContainerColor = Color.White.copy(alpha = 0.12f)),
      border = androidx.tv.material3.ClickableSurfaceDefaults.border(
          border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = shape),
          focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent), shape = shape),
      ),
      scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
  ) {
    Row(Modifier.fillMaxWidth().height(99.dp).padding(horizontal = 26.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
      androidx.tv.material3.Icon(icon, null, Modifier.size(32.dp), tint = LocalHue.current ?: Theme.accent)
      Text(title, style = app(24, FontWeight.Bold), color = Theme.text1, maxLines = 1)
    }
  }
}

@Composable
private fun GenreTile(genre: MenuEntry, onClick: () -> Unit) {
  val loc = LocalLoc.current
  // The Mac's BrowseTile: a picture under a dark wash, the name and the count over its foot.
  CardSurface(onClick, radius = 18.dp, scale = 1.03f) {
    Box(Modifier.fillMaxWidth().height(153.dp).background(Theme.surface2)) {
      RemoteImage(genre.thumb ?: genre.full, Modifier.fillMaxSize(), pixelWidth = 400)
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.8f)))))
      Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(genre.name, style = app(22, FontWeight.Bold), color = Color.White, maxLines = 1)
        genre.count?.takeIf { it > 0 }?.let { Text("${Fmt.count(it, loc.lang)} ${loc(S.results)}", style = app(16, FontWeight.Medium), color = Color.White.copy(alpha = 0.7f)) }
      }
    }
  }
}
