package app.nama.quest.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Article
import app.nama.core.ArticleBlock
import app.nama.core.ArticleText
import app.nama.core.Loc
import app.nama.core.S
import app.nama.core.SiteQuery
import app.nama.quest.AppModel
import app.nama.quest.Services
import app.nama.quest.ui.Ink
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fadeBottom
import app.nama.quest.ui.rememberImage
import app.nama.quest.ui.screens.people.PageNote
import app.nama.quest.ui.screens.people.PageRetry
import app.nama.quest.ui.screens.people.SiteRtl
import app.nama.quest.ui.screens.people.attempt

/**
 * One article whole (NewsView.swift, ArticleView): its picture, title and date, then its text as
 * it was written, with its headings and pictures, in a column narrow enough to read at two metres.
 */
@Composable
fun ArticleScreen(model: AppModel, article: Article) {
  NeedsService(model) { services -> ArticlePage(model, services, article) }
}

/** The reading column: about sixty Persian letters a line at the wall's distance. */
private val ReadingWidth = 900.dp

@Composable
private fun ArticlePage(model: AppModel, services: Services, article: Article) {
  val lang by model.lang.collectAsState()
  val loc = Loc(lang)
  var text by remember(article) { mutableStateOf<ArticleText?>(null) }
  var failed by remember(article) { mutableStateOf(false) }
  var tries by remember(article) { mutableIntStateOf(0) }

  LaunchedEffect(article, tries) {
    failed = false
    attempt { ArticleText.fromSite(services.api.site(SiteQuery.article(article.id))) }
        .onSuccess { if (it == null) failed = true else text = it }
        .onFailure { failed = true }
  }

  val shown = text
  // Written in Persian whatever the interface's language.
  SiteRtl {
    LazyColumn(
        Modifier.fillMaxSize().fadeBottom(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      article.picture?.let { picture ->
        item(key = "picture") {
          val big = rememberImage(picture.toString(), 1400)
          val small = rememberImage(article.thumbnail?.toString(), 640)
          val image = big ?: small
          Box(Modifier.widthIn(max = ReadingWidth).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)).background(Ink.surface2)) {
            if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
          }
        }
      }
      item(key = "title") {
        Column(Modifier.widthIn(max = ReadingWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(article.title, style = WallType.title.copy(fontSize = 38.sp, lineHeight = 54.sp))
          (shown?.article?.date ?: article.date)?.let { Text(it, style = WallType.caption.copy(fontSize = 18.sp)) }
        }
      }
      if (shown != null) {
        itemsIndexed(shown.blocks, key = { i, _ -> "b$i" }) { _, block ->
          Box(Modifier.widthIn(max = ReadingWidth).fillMaxWidth()) {
            when (block) {
              is ArticleBlock.Heading -> Text(block.text, style = Reading.heading, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
              is ArticleBlock.Paragraph -> Text(block.text, style = Reading.body, modifier = Modifier.fillMaxWidth())
              is ArticleBlock.Picture -> ArticlePicture(block.url.toString())
            }
          }
        }
      } else {
        item(key = "state") {
          Box(Modifier.widthIn(max = ReadingWidth).fillMaxWidth()) {
            if (failed) PageRetry(model, services) { tries++ } else PageNote(loc(S.loading))
          }
        }
      }
    }
  }
}

/** A picture in an article, in its own proportions: they are not all the same shape. */
@Composable
private fun ArticlePicture(url: String) {
  val image = rememberImage(url, 1400)
  if (image == null) {
    Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(Ink.surface2))
  } else {
    val ratio = image.width.toFloat() / image.height.coerceAtLeast(1)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Image(
          image,
          null,
          // Small pictures keep their size rather than being blown up past their pixels.
          Modifier.widthIn(max = (image.width / 1.6f).dp.coerceAtLeast(240.dp)).fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(16.dp)),
          contentScale = ContentScale.Fit,
      )
    }
  }
}
