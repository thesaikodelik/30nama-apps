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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Article
import app.nama.core.ArticlePage
import app.nama.core.Loc
import app.nama.core.S
import app.nama.core.SiteQuery
import app.nama.quest.AppModel
import app.nama.quest.Route
import app.nama.quest.Services
import app.nama.quest.ui.Ink
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.PillChoice
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fadeBottom
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.pointable
import app.nama.quest.ui.rememberImage
import app.nama.quest.ui.rememberPointed
import app.nama.quest.ui.screens.people.Gutter
import app.nama.quest.ui.screens.people.PageNote
import app.nama.quest.ui.screens.people.PageRetry
import app.nama.quest.ui.screens.people.SiteRtl
import app.nama.quest.ui.screens.people.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 30nama's news, reviews and announcements from the website (NewsView.swift), newest first. */
@Composable
fun NewsScreen(model: AppModel) {
  NeedsService(model) { services -> NewsPage(model, services) }
}

@Composable
private fun NewsPage(model: AppModel, services: Services) {
  val lang by model.lang.collectAsState()
  val loc = Loc(lang)
  var kind by remember { mutableStateOf(Article.Kind.news) }
  var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
  var page by remember { mutableIntStateOf(0) }
  var pages by remember { mutableIntStateOf(1) }
  var loading by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  var job by remember { mutableStateOf<Job?>(null) }

  fun loadNext() {
    if (loading || page >= pages) return
    loading = true
    failed = false
    val asked = kind
    job = scope.launch {
      try {
        attempt { ArticlePage.fromSite(services.api.site(SiteQuery.articles(asked, page + 1))) }
            .onSuccess { next ->
              val known = articles.mapTo(HashSet()) { it.id }
              articles = articles + next.articles.filter { it.id !in known }
              page = next.page
              pages = next.pages
            }
            .onFailure { failed = true }
      } finally {
        // A list of another kind has taken over by now; its own request owns the flag.
        if (asked == kind) loading = false
      }
    }
  }

  // A change of kind starts the list again; an answer still on its way for the old one is dropped.
  LaunchedEffect(kind) {
    job?.cancel()
    articles = emptyList()
    page = 0
    pages = 1
    loading = false
    loadNext()
  }

  val state = rememberLazyGridState()
  val nearEnd by remember {
    derivedStateOf {
      val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= state.layoutInfo.totalItemsCount - 7
    }
  }
  LaunchedEffect(nearEnd, articles.size) { if (nearEnd && articles.isNotEmpty() && !failed) loadNext() }

  LazyVerticalGrid(
      GridCells.Adaptive(400.dp),
      Modifier.fillMaxSize().fadeBottom(),
      state = state,
      contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = 90.dp),
      horizontalArrangement = Arrangement.spacedBy(24.dp),
      verticalArrangement = Arrangement.spacedBy(28.dp),
  ) {
    fullLine("head") {
      Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(loc(S.news), style = WallType.title)
        Spacer(Modifier.weight(1f))
        Article.Kind.entries.forEach { k -> PillChoice(kindTitle(loc, k), kind == k, { kind = k }) }
      }
    }
    items(articles, key = { it.id }) { a -> ArticleCard(a) { model.push(Route.ArticlePage(a)) } }
    fullLine("state") {
      when {
        failed -> PageRetry(model, services, ::loadNext)
        loading -> PageNote(loc(S.loading))
        else -> Spacer(Modifier.height(1.dp))
      }
    }
  }
}

private fun kindTitle(loc: Loc, kind: Article.Kind): String =
    when (kind) {
      Article.Kind.news -> loc(S.newsItems)
      Article.Kind.review -> loc(S.reviews)
      Article.Kind.announcement -> loc(S.announcements)
    }

/** An article in the list: its picture, title, first lines and date. Written in Persian, so right to left always. */
@Composable
private fun ArticleCard(article: Article, onOpen: () -> Unit) {
  val p = rememberPointed()
  val shape = RoundedCornerShape(20.dp)
  val image = rememberImage((article.thumbnail ?: article.picture)?.toString(), 640)
  SiteRtl {
    Column(
        Modifier.fillMaxWidth()
            .pointable(p, onOpen, 1.025f)
            .clip(shape)
            .background(if (p.hovered) Color(0x1AFFFFFF) else Color(0x0DFFFFFF))
            .border(if (p.hovered) 2.dp else 1.dp, if (p.hovered) Ink.text1.copy(alpha = 0.8f) else Ink.hairline, shape),
    ) {
      Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Ink.surface2), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      }
      Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two lines' room always, so the cards of a row are one height.
        Text(article.title, style = WallType.label.copy(fontSize = 21.sp, lineHeight = 30.sp), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text(article.summary ?: " ", style = WallType.caption.copy(fontSize = 17.sp, lineHeight = 26.sp, color = Ink.text2), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text(article.date ?: " ", style = WallType.caption)
      }
    }
  }
}
