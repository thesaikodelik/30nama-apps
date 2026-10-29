package app.nama.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.nama.androidtv.attempt
import app.nama.core.Article
import app.nama.core.ArticleBlock
import app.nama.core.ArticlePage
import app.nama.core.ArticleText
import app.nama.core.S
import app.nama.core.SiteQuery
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The Mac's news page: 30nama's news, its reviews and its own announcements, as the website has
 * them, newest first, as cards with a picture. Only the site API has them, so Browse offers this
 * page only when the build has its key.
 */
@Composable
fun NewsScreen() {
  val app = LocalApp.current
  // Entered at the chosen kind.
  DeclarePageEntry()
  val nav = LocalNav.current
  val loc = LocalLoc.current
  var kind by rememberSaveable { mutableStateOf(Article.Kind.news) }
  var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
  var page by remember { mutableIntStateOf(0) }
  var pages by remember { mutableIntStateOf(1) }
  var loading by remember { mutableStateOf(true) }
  var failed by remember { mutableStateOf(false) }
  var generation by remember { mutableIntStateOf(0) }
  val scope = rememberCoroutineScope()

  fun loadNext() {
    if (loading || page >= pages) return
    loading = true
    failed = false
    val asked = generation
    val k = kind
    scope.launch {
      val next = attempt { ArticlePage.fromSite(app.api.site(SiteQuery.articles(k, page + 1))) }
      // A change of kind started the list again; this answer belongs to the old one.
      if (asked != generation) return@launch
      loading = false
      if (next == null) {
        failed = true
        return@launch
      }
      val known = articles.map { it.id }.toSet()
      // Repeats within a page too: a repeated key crashes a lazy grid.
      articles = (articles + next.articles.filter { it.id !in known }).distinctBy { it.id }
      page = next.page
      pages = next.pages
    }
  }
  LaunchedEffect(kind) {
    generation++
    articles = emptyList()
    page = 0
    pages = 1
    loading = false
    loadNext()
  }
  val grid = rememberLazyGridState()
  ReportTop(grid)
  LaunchedEffect(grid, articles.size) {
    snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
      // Not again after a failure: a retry at the end asks for it.
      if (!failed && last >= grid.layoutInfo.totalItemsCount - 6) loadNext()
    }
  }

  Ambient {
    if (failed && articles.isEmpty()) {
      ErrorState { loadNext() }
      return@Ambient
    }
    LazyVerticalGrid(
        GridCells.Fixed(4),
        Modifier.fillMaxSize().focusRestorer(),
        state = grid,
        contentPadding = PaddingValues(start = Theme.edge, end = Theme.edge, top = 130.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        // The Mac's header: the name at the start, the three kinds at the end.
        Row(Modifier.wholeSection().fillMaxWidth().padding(bottom = 6.dp).focusRestorer(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(loc(S.news), Modifier.weight(1f), style = app(44, FontWeight.Bold), color = Theme.text1, maxLines = 1)
          Article.Kind.entries.forEach { k -> PillButton(kindName(k), { kind = k }, if (kind == k) Modifier.pageEntry() else Modifier, kind = PillKind.Chip, selected = kind == k) }
        }
      }
      items(articles, key = { it.id }) { a -> ArticleCard(a) { nav.push(Route.ArticlePage(a)) } }
      if (failed && articles.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
        Box(Modifier.padding(top = 20.dp)) { PillButton(LocalLoc.current(S.retry), { loadNext() }, icon = Icons.replay) }
      }
      if (loading) items(if (articles.isEmpty()) 8 else 4) { Skeleton(Modifier.fillMaxWidth().aspectRatio(1.05f), 16.dp) }
    }
  }
}

@Composable
private fun kindName(kind: Article.Kind): String {
  val loc = LocalLoc.current
  return when (kind) {
    Article.Kind.news -> loc(S.newsItems)
    Article.Kind.review -> loc(S.reviews)
    Article.Kind.announcement -> loc(S.announcements)
  }
}

/** The Mac's article card: its picture, title, first lines and date, in Persian whatever the app's language. */
@Composable
private fun ArticleCard(article: Article, onClick: () -> Unit) {
  // Solid, not see-through: the focus glow is a shadow under the card, and through a card of white
  // 0.05 its blurred edge showed and its middle did not, a dark box over the text (the owner). No
  // glint either, which lit only part of the text; in focus the whole card lightens evenly, with the
  // ring's red light moving softly in it (the owner).
  CardSurface(onClick, radius = 16.dp, scale = 1.03f) {
    val focused = LocalCardFocused.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      Column(Modifier.fillMaxWidth().background(if (focused) Theme.surface2 else Theme.surface).focusWash(focused)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Theme.surface2), contentAlignment = Alignment.Center) {
          Icon(Icons.newspaper, null, Modifier.size(40.dp), tint = Theme.text3)
          RemoteImage(article.thumbnail ?: article.picture, Modifier.fillMaxSize(), pixelWidth = 480)
        }
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          // Two lines' room always, so the cards of a row are one height.
          Text(article.title, style = app(22, FontWeight.Bold), color = Theme.text1, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text(article.summary ?: "", style = app(18), color = Theme.text2, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
          article.date?.let { Text(it, style = app(16, FontWeight.Medium), color = Theme.text3, maxLines = 1) }
        }
      }
    }
  }
}

/**
 * One article whole, as the Mac's: its picture, title and date, then its text as it was written,
 * with its headings and pictures, in a column narrow enough to read from the sofa. Each block takes
 * focus in turn, so the remote reads down the page; the one in focus is marked at its edge.
 */
@Composable
fun ArticleScreen(article: Article) {
  val app = LocalApp.current
  var text by remember { mutableStateOf<ArticleText?>(null) }
  var failed by remember { mutableStateOf(false) }
  var reload by remember { mutableIntStateOf(0) }
  LaunchedEffect(article.id, reload) {
    failed = false
    text = attempt { ArticleText.fromSite(app.api.site(SiteQuery.article(article.id))) }
    failed = text == null
  }
  Ambient {
    // Articles are written in Persian whatever the interface's language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
      val list = androidx.compose.foundation.lazy.rememberLazyListState()
      ReportTop(list)
      LazyColumn(
          Modifier.fillMaxSize().focusRestorer(),
          state = list,
          contentPadding = PaddingValues(top = 130.dp, bottom = 120.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(22.dp),
      ) {
        item {
          Readable {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
              article.picture?.let {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Theme.surface2, RoundedCornerShape(18.dp))) {
                  RemoteImage(it, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), pixelWidth = 1100)
                }
              }
              Text(article.title, style = app(40, FontWeight.Bold), color = Theme.text1)
              (text?.article?.date ?: article.date)?.let { Text(it, style = app(20, FontWeight.Medium), color = Theme.text3) }
            }
          }
        }
        val blocks = text?.blocks
        when {
          blocks != null -> itemsIndexed(blocks) { _, block ->
            Readable {
              when (block) {
                is ArticleBlock.Heading -> Text(block.text, Modifier.padding(top = 8.dp), style = app(30, FontWeight.Bold), color = Theme.text1)
                is ArticleBlock.Paragraph -> Text(block.text, style = app(25).copy(lineHeight = app(25).fontSize * 1.7f), color = Theme.text2)
                is ArticleBlock.Picture -> RemoteImage(block.url, Modifier.fillMaxWidth().height(560.dp).clip(RoundedCornerShape(14.dp)), pixelWidth = 1100, contentScale = ContentScale.Fit)
              }
            }
          }
          failed -> item { Box(Modifier.width(1100.dp)) { PillButton(LocalLoc.current(S.retry), { reload++ }) } }
          else -> item { Skeleton(Modifier.width(1100.dp).height(300.dp), 16.dp) }
        }
      }
    }
  }
}

/** A block of an article: the reading column's width, focusable so the remote can step through. */
@Composable
private fun Readable(content: @Composable () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val hue = LocalHue.current ?: Theme.accent
  Row(Modifier.width(1160.dp).focusable(interactionSource = interaction), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
    Box(Modifier.width(4.dp).height(if (focused) 40.dp else 0.dp).background(hue, RoundedCornerShape(50)))
    Box(Modifier.width(1100.dp)) { content() }
  }
}
