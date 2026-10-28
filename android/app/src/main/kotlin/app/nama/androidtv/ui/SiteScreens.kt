package app.nama.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.nama.androidtv.attempt
import app.nama.core.Lang
import app.nama.core.Person
import app.nama.core.PersonDetails
import app.nama.core.Post
import app.nama.core.PostPage
import app.nama.core.S
import app.nama.core.Schedule
import app.nama.core.SiteQuery
import app.nama.core.SiteText
import app.nama.core.TopList
import app.nama.core.fromSite
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** A grid of posters on an ambient page, with [header] above it. */
@Composable
private fun PosterPage(posts: List<Post>?, failed: Boolean, retry: () -> Unit, header: @Composable () -> Unit, badge: (@Composable (Post) -> Unit)? = null, empty: Boolean = false) {
  val nav = LocalNav.current
  val loc = LocalLoc.current
  Ambient {
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    ReportTop(grid)
    LazyVerticalGrid(
        GridCells.FixedSize(Theme.posterWidth),
        Modifier.fillMaxSize().focusRestorer(),
        state = grid,
        contentPadding = PaddingValues(start = Theme.edge, end = Theme.edge, top = 70.dp, bottom = 80.dp),
        // The catalogue's wall: 8 across with the room left over shared by both sides.
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        // Room for each one's name under it.
        verticalArrangement = Arrangement.spacedBy(44.dp),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) { Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { header() } }
      when {
        posts != null -> {
          if (posts.isEmpty() || empty) item(span = { GridItemSpan(maxLineSpan) }) { Text(loc(S.noMatches), style = app(28, FontWeight.Medium), color = Theme.text2) }
          items(posts, key = { it.id }) { post ->
            Box {
              PosterCard(post, { nav.push(Route.Details(post)) }, titled = true)
              badge?.invoke(post)
            }
          }
        }
        failed -> item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.size(1500.dp, 500.dp)) { ErrorState(LocalApp.current.loc(S.siteOutOfReach).takeIf { LocalApp.current.inIran }) { retry() } } }
        else -> items(WALL * 2) { Skeleton(Modifier.size(Theme.posterWidth, Theme.posterWidth * 1.5f), Theme.cardRadius) }
      }
    }
  }
}

/**
 * The week of series and anime: which have a new episode on which day, from today on. From the
 * 30nama website's own API, as its schedule page has it.
 */
@Composable
fun ScheduleScreen() {
  val app = LocalApp.current
  val loc = LocalLoc.current
  var kind by remember { mutableStateOf(Schedule.Kind.series) }
  // Looked at again each minute, so a page left open past midnight moves on to the new day.
  val today by androidx.compose.runtime.produceState(Schedule.day(Instant.now())) {
    while (true) {
      kotlinx.coroutines.delay(60_000)
      value = Schedule.day(Instant.now())
    }
  }
  var day by remember { mutableIntStateOf(today) }
  // One who was on today stays on today as the day turns; another chosen day stays chosen.
  val week = remember(today) { Schedule.week(Instant.now()) }
  var shownToday by remember { mutableIntStateOf(today) }
  LaunchedEffect(today) {
    if (day == shownToday) day = today
    shownToday = today
  }
  val weeks = remember { mutableStateMapOf<Schedule.Kind, Schedule>() }
  var failed by remember { mutableStateOf(false) }
  var reload by remember { mutableIntStateOf(0) }
  LaunchedEffect(kind, reload) {
    if (weeks[kind] != null) return@LaunchedEffect
    failed = false
    val asked = kind
    val week = attempt { Schedule.fromSite(app.api.site(SiteQuery.schedule(asked))) }
    if (week != null) weeks[asked] = week else if (asked == kind) failed = true
  }
  val locale = Locale.forLanguageTag(if (loc.lang == Lang.fa) "fa-IR" else if (loc.lang == Lang.ar) "ar" else "en")
  // Sunday first, as the weekday names come; the site's own numbering starts on Saturday.
  val names = remember(locale) { (0..6).map { java.time.DayOfWeek.of(if (it == 0) 7 else it).getDisplayName(TextStyle.FULL, locale) } }
  PosterPage(weeks[kind]?.on(day), failed, { reload++ }, header = {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(loc(S.weeklySchedule), Modifier.weight(1f), style = app(56, FontWeight.Bold), color = Theme.text1)
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PillButton(loc(S.series), { kind = Schedule.Kind.series }, kind = PillKind.Chip, selected = kind == Schedule.Kind.series)
        PillButton(loc(S.anime), { kind = Schedule.Kind.anime }, kind = PillKind.Chip, selected = kind == Schedule.Kind.anime)
      }
    }
    LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
      items(week) { d -> PillButton(if (d == today) loc(S.today) else names[Schedule.symbolIndex(d)], { day = d }, kind = PillKind.Chip, selected = day == d) }
    }
  })
}

/** The site's lists of the best, each whole and in order, with the place on every poster. */
@Composable
fun TopListsScreen() {
  val app = LocalApp.current
  val loc = LocalLoc.current
  var list by remember { mutableStateOf(TopList.imdbMovies) }
  val lists = remember { mutableStateMapOf<TopList, List<TopList.Entry>>() }
  var failed by remember { mutableStateOf(false) }
  var reload by remember { mutableIntStateOf(0) }
  LaunchedEffect(list, reload) {
    if (lists[list] != null) return@LaunchedEffect
    failed = false
    val asked = list
    val entries = attempt { TopList.entries(app.api.site(SiteQuery.topList(asked))) }
    if (entries != null) lists[asked] = entries else if (asked == list) failed = true
  }
  val entries = lists[list]
  val ranks = entries?.associate { it.post.id to it.rank }.orEmpty()
  PosterPage(entries?.map { it.post }?.distinctBy { it.id }, failed, { reload++ }, header = {
    Text(loc(S.topLists), style = app(56, FontWeight.Bold), color = Theme.text1)
    LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
      items(TopList.entries.toList()) { option ->
        val title = when (option) {
          TopList.imdbMovies -> loc(S.imdbTopMovies)
          TopList.imdbSeries -> loc(S.imdbTopSeries)
          TopList.siteMovies -> loc(S.siteTopMovies)
          TopList.siteSeries -> loc(S.siteTopSeries)
        }
        PillButton(title, { list = option }, kind = PillKind.Chip, selected = list == option)
      }
    }
  }, badge = { post ->
    ranks[post.id]?.let { rank ->
      Text(
          loc.num(rank), Modifier.padding(12.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp),
          style = app(24, FontWeight.ExtraBold), color = Color.White,
      )
    }
  })
}

/**
 * An actor chosen on a title's page: who they are, as the 30nama website has it (picture, names,
 * birth, the biography 30nama wrote), and their titles newest first. Found by 30nama's own id; a
 * cast member without one is found by the IMDb id instead, and then there are only the titles.
 */
@Composable
fun PersonScreen(person: Person) {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  var details by remember { mutableStateOf<PersonDetails?>(null) }
  var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
  var page by remember { mutableIntStateOf(0) }
  var pages by remember { mutableIntStateOf(1) }
  var loading by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  var done by remember { mutableStateOf(false) }
  var whole by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  fun loadNext() {
    if (loading || page >= pages) return
    loading = true
    failed = false
    scope.launch {
      val next: PostPage? = attempt {
        val id = person.siteID
        val imdb = person.imdb
        when {
          id != null -> PersonDetails.fromSite(app.api.site(SiteQuery.person(id, page + 1)))?.also { if (details == null) details = it }?.titles
          imdb != null -> SiteQuery.filmography(imdb, page + 1).let { q -> PostPage.fromSite(app.api.site(q.path, q.form)) }
          else -> null
        }
      }
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
      done = true
    }
  }
  LaunchedEffect(person) { loadNext() }

  Ambient {
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    ReportTop(grid)
    LazyVerticalGrid(
        GridCells.FixedSize(Theme.posterWidth),
        Modifier.fillMaxSize().focusRestorer(),
        state = grid,
        contentPadding = PaddingValues(start = Theme.edge, end = Theme.edge, top = 70.dp, bottom = 80.dp),
        // The catalogue's wall: 8 across with the room left over shared by both sides.
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        // Room for each one's name under it.
        verticalArrangement = Arrangement.spacedBy(44.dp),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
          // Something has to take the focus as soon as the page opens: the titles come later.
          CardSurface({}, radius = Theme.tileRadius, scale = 1.03f) { Card(person, details) }
          Column(Modifier.widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(details?.name ?: person.name, style = app(56, FontWeight.Bold), color = Theme.text1)
            details?.persianName?.takeIf { it != details?.name }?.let { Text(it, style = app(32, FontWeight.Medium), color = Theme.text2) }
            details?.biography?.takeIf { it.isNotEmpty() }?.let { bio -> Biography(bio, whole) { whole = it } }
          }
        }
      }
      if (done || posts.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(loc(S.filmography), style = app(38, FontWeight.Bold), color = Theme.text1) }
      if (failed && posts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.size(1500.dp, 400.dp)) { ErrorState { loadNext() } } }
      items(wholeRows(posts, page >= pages), key = { it.id }) { post -> PosterCard(post, { nav.push(Route.Details(post)) }, titled = true) }
      if (loading) items(WALL) { Skeleton(Modifier.size(Theme.posterWidth, Theme.posterWidth * 1.5f), Theme.cardRadius) }
      if (!loading && done && posts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(loc(S.noMatches), style = app(28, FontWeight.Medium), color = Theme.text2) }
      // More as the remote nears the end.
      // Keyed on the page as well: one that brought nothing new must not stop the paging. A failure
      // stops it, with a retry, rather than asking again and again.
      if (failed && posts.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.size(1500.dp, 300.dp)) { ErrorState { loadNext() } } }
      else if (page < pages) item(span = { GridItemSpan(maxLineSpan) }) { LaunchedEffect(posts.size, page) { loadNext() } }
    }
  }
}

/** The card at the side: the picture and the facts the site keeps (in Persian, whatever the language). */
@Composable
private fun Card(person: Person, d: PersonDetails?) {
  val loc = LocalLoc.current
  val face = d?.picture ?: person.poster
  val facts = d?.let {
    listOfNotNull(
        it.fullName?.takeIf { f -> f != it.name }?.let { f -> loc(S.fullName) to f },
        it.birthdate?.let { b -> loc(S.born) to b },
        it.deathdate?.let { b -> loc(S.died) to b },
        it.age?.let { a -> loc(S.personAge) to a },
        it.profession?.let { p -> loc(S.profession) to p },
    )
  }.orEmpty()
  Column(Modifier.width(360.dp).clip(RoundedCornerShape(Theme.tileRadius)).background(Color.White.copy(alpha = 0.05f))) {
    Box(Modifier.size(360.dp, 500.dp).background(Theme.surface2), contentAlignment = Alignment.Center) {
      Icon(Icons.person, null, Modifier.size(120.dp), tint = Theme.text3)
      face?.let { RemoteImage(it.url(360.0), Modifier.fillMaxSize(), blurhash = it.blurhash, pixelWidth = 400) }
    }
    if (facts.isNotEmpty()) Column(Modifier.padding(26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
      facts.forEach { (label, value) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(label, style = app(20, FontWeight.SemiBold), color = Theme.text3)
          Text(value, style = app(26, FontWeight.Medium), color = Theme.text1)
        }
      }
    }
  }
}

/**
 * The biography is written in Persian, so it reads right to left whatever the interface's
 * language. Folded, its start is one row that opens the rest; open, every paragraph is a row of
 * its own, so the remote moves down it a paragraph at a time.
 */
@Composable
private fun Biography(paragraphs: List<String>, whole: Boolean, set: (Boolean) -> Unit) {
  val loc = LocalLoc.current
  val long = paragraphs.joinToString("").length > 1000
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      if (whole || !long) {
        paragraphs.forEach { p -> TextRow({ if (long) set(false) }) { Paragraph(p) } }
      } else {
        TextRow({ set(true) }) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SiteText.opening(paragraphs, 500).forEach { Paragraph(it) }
            Text(loc(S.readMore), style = app(24, FontWeight.Bold), color = Theme.text2)
          }
        }
      }
    }
  }
}

@Composable
private fun Paragraph(p: String) {
  val heading = SiteText.isHeading(p)
  Text(p, Modifier.fillMaxWidth().padding(vertical = 14.dp), style = app(if (heading) 28 else 26, if (heading) FontWeight.Bold else FontWeight.Normal).copy(lineHeight = app(26).fontSize * 1.55f))
}

/** A full-width row that takes the focus: glass at rest, white in focus. */
@Composable
fun TextRow(onClick: () -> Unit, content: @Composable () -> Unit) {
  androidx.tv.material3.Surface(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
      shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(Theme.tileRadius)),
      colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Theme.glass, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
      scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
  ) { Box(Modifier.padding(horizontal = 34.dp, vertical = 8.dp)) { content() } }
}
