package app.nama.quest.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Person
import app.nama.core.PersonDetails
import app.nama.core.Post
import app.nama.core.PostPage
import app.nama.core.S
import app.nama.core.SiteQuery
import app.nama.core.SiteText
import app.nama.core.fromSite
import app.nama.quest.AppModel
import app.nama.quest.Services
import app.nama.quest.ui.Glyph
import app.nama.quest.ui.Icon
import app.nama.quest.ui.Ink
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.Pill
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.rememberImage
import app.nama.quest.ui.screens.people.PageNote
import app.nama.quest.ui.screens.people.PageRetry
import app.nama.quest.ui.screens.people.PosterGrid
import app.nama.quest.ui.screens.people.SiteRtl
import app.nama.quest.ui.screens.people.attempt
import app.nama.quest.ui.screens.people.openInBrowser
import kotlinx.coroutines.launch

/**
 * Someone from a title's cast (PersonView.swift): as the 30nama website has them, a card with
 * their picture and facts, the biography beside it in a column narrow enough to read, and their
 * titles newest first. Found by 30nama's own id; a cast member without one is found by the IMDb id
 * instead, and then there are only the titles.
 */
@Composable
fun PersonScreen(model: AppModel, person: Person) {
  NeedsService(model) { services -> PersonPage(model, services, person) }
}

@Composable
private fun PersonPage(model: AppModel, services: Services, person: Person) {
  val lang by model.lang.collectAsState()
  val loc = app.nama.core.Loc(lang)
  var details by remember(person) { mutableStateOf<PersonDetails?>(null) }
  var posts by remember(person) { mutableStateOf<List<Post>>(emptyList()) }
  var page by remember(person) { mutableIntStateOf(0) }
  var pages by remember(person) { mutableIntStateOf(1) }
  var loading by remember(person) { mutableStateOf(false) }
  var failed by remember(person) { mutableStateOf(false) }
  var done by remember(person) { mutableStateOf(false) }
  var whole by remember(person) { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  fun loadNext() {
    if (loading || page >= pages) return
    loading = true
    failed = false
    scope.launch {
      try {
        val next: PostPage? =
            attempt {
                  val id = person.siteID
                  val imdb = person.imdb
                  when {
                    id != null -> {
                      val found = PersonDetails.fromSite(services.api.site(SiteQuery.person(id, page + 1))) ?: error("person")
                      if (details == null) details = found
                      found.titles
                    }
                    imdb != null -> {
                      val q = SiteQuery.filmography(imdb, page + 1)
                      PostPage.fromSite(services.api.site(q.path, q.form))
                    }
                    else -> null
                  }
                }
                .onFailure { failed = true }
                .getOrNull()
        if (next != null) {
          val known = posts.mapTo(HashSet()) { it.id }
          posts = posts + next.posts.filter { it.id !in known }
          page = next.page
          pages = next.pages
        }
        if (!failed) done = true
      } finally {
        loading = false
      }
    }
  }

  LaunchedEffect(person) { loadNext() }

  PosterGrid(
      model,
      posts,
      canLoadMore = page < pages && !failed,
      loading = loading,
      onLoadMore = ::loadNext,
      header = {
        fullLine("person") { Header(model, services, person, details, whole) { whole = !whole } }
        if (done || posts.isNotEmpty()) {
          fullLine("filmography") { Text(loc(S.filmography), style = WallType.heading, modifier = Modifier.padding(top = 18.dp)) }
        }
      },
      footer = {
        fullLine("footer") {
          when {
            failed -> PageRetry(model, services, ::loadNext)
            loading -> PageNote(loc(S.loading))
            done && posts.isEmpty() -> PageNote(loc(S.noMatches))
            else -> Spacer(Modifier.height(1.dp))
          }
        }
      },
  )
}

/** As the site lays it out: the card at the side, and the name and biography beside it. */
@Composable
private fun Header(model: AppModel, services: Services, person: Person, details: PersonDetails?, whole: Boolean, onToggle: () -> Unit) {
  val loc = model.loc
  Row(Modifier.fillMaxWidth().padding(top = 10.dp).animateContentSize(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
    Card(model, services, person, details)
    Column(Modifier.weight(1f, fill = false).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(details?.name ?: person.name, style = WallType.title.copy(fontSize = 44.sp, lineHeight = 54.sp))
      val persian = details?.persianName
      if (persian != null && persian != details.name) {
        Text(persian, style = WallType.heading.copy(color = Ink.text2, fontWeight = FontWeight.Medium))
      }
      if (details == null) {
        person.role?.takeIf { it.isNotBlank() }?.let { Text(it, style = WallType.body) }
      }
      val bio = details?.biography.orEmpty()
      if (bio.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Biography(bio, whole, loc(if (whole) S.readLess else S.readMore), onToggle)
      }
    }
  }
}

private class Fact(val label: String, val value: String)

/** The picture, the facts the site keeps (in Persian whatever the interface's language), and IMDb. */
@Composable
private fun Card(model: AppModel, services: Services, person: Person, details: PersonDetails?) {
  val loc = model.loc
  val shape = RoundedCornerShape(22.dp)
  val face = details?.picture ?: person.poster
  val image = rememberImage(face?.url(500.0)?.toString(), 500)
  val facts =
      details?.let { d ->
        listOfNotNull(
            d.fullName?.takeIf { it != d.name }?.let { Fact(loc(S.fullName), it) },
            d.birthdate?.let { Fact(loc(S.born), it) },
            d.deathdate?.let { Fact(loc(S.died), it) },
            d.age?.let { Fact(loc(S.personAge), it) },
            d.profession?.let { Fact(loc(S.profession), it) },
        )
      }.orEmpty()
  Column(Modifier.width(280.dp).clip(shape).background(Color(0x0FFFFFFF)).border(1.dp, Ink.hairline, shape)) {
    Box(Modifier.width(280.dp).height(390.dp).background(Ink.surface2), contentAlignment = Alignment.Center) {
      Glyph(Icon.Person, Modifier.size(90.dp), Ink.text3)
      if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
    val imdb = details?.imdb
    if (facts.isNotEmpty() || imdb != null) {
      Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        facts.forEach { f ->
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.label, style = WallType.caption)
            Text(f.value, style = WallType.label.copy(fontWeight = FontWeight.Medium))
          }
        }
        if (imdb != null) {
          Pill(loc(S.imdbProfile), { openInBrowser(model, services, imdb.toString()) }, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
      }
    }
  }
}

/**
 * Written in Persian, so it reads right to left whatever the interface's language; short lines
 * without a full stop are the headings 30nama put in it. A long one is folded to its opening,
 * fading out above "Read more".
 */
@Composable
private fun Biography(paragraphs: List<String>, whole: Boolean, toggleLabel: String, onToggle: () -> Unit) {
  val long = paragraphs.sumOf { it.length } > 1000
  val folded = long && !whole
  val shown = if (folded) SiteText.opening(paragraphs) else paragraphs
  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    SiteRtl {
      Column(
          Modifier.fillMaxWidth()
              .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
              .drawWithContent {
                drawContent()
                if (folded) drawRect(Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
              },
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        shown.forEach { p ->
          if (SiteText.isHeading(p)) {
            Text(p, style = Reading.heading, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
          } else {
            Text(p, style = Reading.body, modifier = Modifier.fillMaxWidth())
          }
        }
      }
    }
    if (long) Pill(toggleLabel, onToggle)
  }
}

/** Long Persian text at two metres: a size up from the wall's body, with room between the lines. */
internal object Reading {
  val body = WallType.body.copy(fontSize = 21.sp, lineHeight = 38.sp, color = Ink.text1.copy(alpha = 0.84f))
  val heading = WallType.heading.copy(fontSize = 24.sp, lineHeight = 36.sp)
}
