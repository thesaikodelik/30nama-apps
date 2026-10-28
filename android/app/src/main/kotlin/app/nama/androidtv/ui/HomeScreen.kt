package app.nama.androidtv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.nama.androidtv.attempt
import app.nama.core.Episode
import app.nama.core.HomeFeed
import app.nama.core.HomeSection
import app.nama.core.Post
import app.nama.core.S
import kotlinx.coroutines.delay

/**
 * Home: a billboard over a living backdrop, with rows of posters below (the Apple TV's HomeView).
 * On arrival it is a hero carousel. Moving down turns the billboard into a preview of whatever card
 * is in focus, while the backdrop crossfades to that title. The rows never scroll on their own:
 * the focused row's header always sits on one line under the billboard, and the row above shows
 * through as a quiet strip, a hint that there is more up there.
 */
@Composable
fun HomeScreen() {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  val chrome = LocalChrome.current
  val calm = LocalCalm.current

  var feed by remember { mutableStateOf<HomeFeed?>(null) }
  var continuing by remember { mutableStateOf<List<Post>>(emptyList()) }
  var forYou by remember { mutableStateOf<List<Post>>(emptyList()) }
  var trending by remember { mutableStateOf<List<Post>>(emptyList()) }
  var topMovies by remember { mutableStateOf<List<Post>>(emptyList()) }
  var topSeries by remember { mutableStateOf<List<Post>>(emptyList()) }
  var failed by remember { mutableStateOf(false) }
  var heroIndex by rememberSaveable { mutableIntStateOf(0) }
  /** The card in focus, when focus is in the rows. */
  var browsing by remember { mutableStateOf<Selection?>(null) }
  /** `browsing`, once focus has rested on it for a moment. */
  var settled by remember { mutableStateOf<Selection?>(null) }
  var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var heroProgress by remember { mutableStateOf(0f) }
  /** Rows that have had focus, whose cards beyond the first screenful may now load their posters. */
  val opened = remember { mutableSetOf<String>() }
  var reload by remember { mutableIntStateOf(0) }
  val play = remember { FocusRequester() }

  val heroes = feed?.heroes.orEmpty()
  val rows: List<HomeSection> = feed?.let { f ->
    // The Mac's order: the week's trending after where the viewer left off, and the two top tens
    // under the first shelf when 30nama's own home has none.
    val tops = if (f.sections.none { it.key == "top_10" }) listOf(HomeSection(TOP_MOVIES, topMovies), HomeSection(TOP_SERIES, topSeries)).filter { it.posts.isNotEmpty() } else emptyList()
    listOfNotNull(
        continuing.takeIf { it.isNotEmpty() }?.let { HomeSection("continue", it) },
        forYou.takeIf { it.isNotEmpty() }?.let { HomeSection("for_you", it) },
        trending.takeIf { it.isNotEmpty() }?.let { HomeSection(TRENDING, it) },
    ) + f.sections.take(1) + tops + f.sections.drop(1)
  }.orEmpty()
  val hero = heroes.getOrNull(heroIndex) ?: heroes.firstOrNull()
  val featured = if (browsing == null) hero else (settled ?: browsing)?.post

  LaunchedEffect(reload) {
    failed = false
    // Last time's page at once, so there is something to see from the first frame.
    if (feed == null) app.cachedHome()?.let { feed = it }
    val loaded = attempt { app.home() }
    if (loaded == null) {
      if (feed == null) failed = true
      return@LaunchedEffect
    }
    feed = loaded
    val reached = app.continueWatchingIfReachable()
    continuing = reached.orEmpty()
    app.mirrorHome(reached)
    forYou = app.ratings.recommend(loaded.sections.flatMap { it.posts }, limit = 14)
    // Neither is waited for: the page is already there, and the rows join it as they come.
    // In the app's own scope, so a slow answer keeps nothing of this page waiting.
    app.background {
      if (loaded.sections.none { it.key == "top_10" }) {
        topMovies = app.topTen("movies")
        topSeries = app.topTen("series")
      }
      if (trending.isEmpty()) trending = app.trendingThisWeek()
    }
  }
  // Without a connection home stays as it was saved, says so, and tries again every half minute.
  val offlineNote = tv(TV.offline)
  LaunchedEffect(app.homeOffline) { if (app.homeOffline) app.showToast(offlineNote) }
  LaunchedEffect(app.homeOffline, reload) {
    if (!app.homeOffline) return@LaunchedEffect
    delay(30_000)
    reload++
  }
  // Focus to Play once there is a page, unless a page opened over home has the screen.
  LaunchedEffect(feed != null) { if (feed != null && browsing == null && chrome.mayTakeFocus) runCatching { play.requestFocus() } }
  // Back from the player, or a title taken off the row: ask for "continue watching" again.
  LaunchedEffect(app.watchVersion, app.continueVersion) {
    if (feed == null) return@LaunchedEffect
    val reached = app.continueWatchingIfReachable()
    reached?.let { continuing = it }
    app.mirrorHome(reached)
  }
  // Billboard text follows focus after a beat, so a held remote does not strobe it.
  LaunchedEffect(browsing) {
    delay(140)
    settled = browsing
  }
  // The billboard's text and the backdrop change together, once the next title's picture and logo
  // are in hand (a moment at most): otherwise its text, and a gap for its logo, sat over the last
  // title's picture, as when the saved home gave way to the fresh one.
  var staged by remember { mutableStateOf<Post?>(null) }
  val context = androidx.compose.ui.platform.LocalContext.current
  LaunchedEffect(featured?.id) {
    val next = featured ?: return@LaunchedEffect
    if (staged != null) {
      delay(300)
      kotlinx.coroutines.withTimeoutOrNull(1_500) { warmBillboard(context, app, next, loc.lang) }
    }
    staged = next
  }
  LaunchedEffect(browsing == null) { chrome.pageAtTop = browsing == null }
  // Advances the hero after nine seconds on screen with the remote idle. A press pauses the turn
  // where it is, and it runs on once the remote has rested a moment: started over at each press,
  // the pager's fill leapt back to its start as the viewer moved between Play and Details.
  LaunchedEffect(heroes.size, browsing == null, calm) {
    if (calm || browsing != null || heroes.size < 2) return@LaunchedEffect
    var run = (heroProgress * 9_000).toLong()
    while (true) {
      delay(250)
      if (System.currentTimeMillis() - lastInput < 1_500) continue
      run += 250
      if (run >= 9_000) {
        run = 0
        heroProgress = 0f
        heroIndex = (heroIndex + 1) % heroes.size
      } else heroProgress = run / 9_000f
    }
  }

  // Home is lit in the featured title's colour, from its scene picture, as on the Mac.
  val light = rememberSceneTint(staged)
  CompositionLocalProvider(LocalHue provides light) {
  Box(Modifier.fillMaxSize()) {
    Stage(staged, settleMs = 0, tint = light)
    when {
      feed != null -> {
        Rows(
            rows = rows, heroes = heroes, hero = hero, heroIndex = heroIndex, heroProgress = { heroProgress }, continuing = continuing,
            browsing = browsing, opened = opened, play = play,
            onFocus = { sel ->
              lastInput = System.currentTimeMillis()
              if (sel != null) opened += sel.row
              browsing = sel
            },
            onDismiss = { post ->
              app.dismissContinue(post)
              continuing = continuing.filter { it.id != post.id }
            },
        )
        Billboard(staged, eyebrow(staged, browsing, listOf(settled, browsing).firstOrNull { it != null && it.post.id == staged?.id }), plotLines = if (browsing == null) 3 else 2, browsing = browsing != null)
      }
      failed -> ErrorState { reload++ }
      else -> HomeSkeleton()
    }
  }
  }
}

data class Selection(val row: String, val post: Post, val rank: Int)

private const val TRENDING = "tm_trend"
private const val TOP_MOVIES = "top_movies"
private const val TOP_SERIES = "top_series"
private val ranked = setOf("top_10", TOP_MOVIES, TOP_SERIES)

/** A row's name: 30nama's sections by their key, and the rows this app adds. */
@Composable
private fun rowTitle(key: String): String {
  val loc = LocalLoc.current
  return when (key) {
    TRENDING -> loc(S.trendingWeek)
    TOP_MOVIES -> loc(S.topFilms)
    TOP_SERIES -> loc(S.topSeries)
    else -> loc.title(key)
  }
}

private object Layout {
  /** Bottom edge of the billboard text while the hero is showing. */
  const val HERO_TEXT_BOTTOM = 596
  /** While browsing the text ends higher, leaving a band where the row above shows through. */
  const val BROWSE_TEXT_BOTTOM = 548
  const val BUTTONS_TOP = 628
  /** Where the first row rests while the hero is showing: just its header and a strip of posters. */
  const val RESTING_ROW_TOP = 852
  /** Where the focused row's header sits while browsing. */
  const val FOCUS_LINE = 632
  const val HEADER_HEIGHT = 40
  const val HEADER_GAP = 26
  const val ROW_HEIGHT = HEADER_HEIGHT + HEADER_GAP + 300
  const val ROW_SPACING = 36
}

@Composable
private fun eyebrow(post: Post?, browsing: Selection?, selection: Selection?): String {
  val loc = LocalLoc.current
  post ?: return ""
  if (browsing == null || selection == null) return "${loc(S.featured)} · ${loc.kindLine(post)}"
  return when (selection.row) {
    in ranked -> "${rowTitle(selection.row)} · #${loc.num(selection.rank)}"
    "continue" -> loc(S.continueWatching)
    else -> loc.kindLine(post)
  }
}

/** Title block for the featured title: what it is, its name, the facts, the pitch. */
@Composable
private fun Billboard(post: Post?, eyebrow: String, plotLines: Int, browsing: Boolean) {
  val loc = LocalLoc.current
  val calm = LocalCalm.current
  val bottom by animateDpAsState(if (browsing) Layout.BROWSE_TEXT_BOTTOM.dp else Layout.HERO_TEXT_BOTTOM.dp, Theme.glide(), label = "bottom")
  Box(Modifier.padding(start = Theme.edge).width(1000.dp).height(bottom), contentAlignment = Alignment.BottomStart) {
    AnimatedContent(
        post,
        transitionSpec = {
          if (calm) fadeIn(tween(340)) togetherWith fadeOut(tween(200))
          else (fadeIn(tween(340)) + slideInVertically(tween(340)) { 14 }) togetherWith fadeOut(tween(200))
        },
        contentKey = { it?.id },
        label = "billboard",
    ) { p ->
      if (p == null) return@AnimatedContent
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(eyebrow)
        // Its logo where TMDB has one, as the Mac's billboard.
        TitleArt(p, size = 76)
        Box(Modifier.padding(top = 6.dp)) { MetaLine(p, genres = 3, allScores = true) }
        Text(
            p.plot(loc.lang), Modifier.widthIn(max = 820.dp).padding(top = 2.dp),
            style = app(26).copy(lineHeight = app(26).fontSize * 1.5f), color = Theme.text2, maxLines = plotLines, overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Rows(
    rows: List<HomeSection>,
    heroes: List<Post>,
    hero: Post?,
    heroIndex: Int,
    heroProgress: () -> Float,
    continuing: List<Post>,
    browsing: Selection?,
    opened: Set<String>,
    play: FocusRequester,
    onFocus: (Selection?) -> Unit,
    onDismiss: (Post) -> Unit,
) {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  val keys = rows.map { it.key }
  val focusedRow = browsing?.let { keys.indexOf(it.row) } ?: -1
  // The rows sit still while the hero shows; browsing, the focused row's header goes to the focus line.
  val target = if (focusedRow < 0) Layout.RESTING_ROW_TOP else Layout.FOCUS_LINE - focusedRow * (Layout.ROW_HEIGHT + Layout.ROW_SPACING)
  val top by animateDpAsState(target.dp, Theme.glide(), label = "rows")
  val edgePx = with(LocalDensity.current) { Theme.edge.toPx() }
  // Netflix-style rows: the focused card keeps to the start edge, so the eye stays in one place.
  val startEdge = remember(edgePx) {
    object : BringIntoViewSpec {
      override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - edgePx
    }
  }

  Box(
      Modifier.fillMaxSize()
          // Everything above the billboard's bottom edge belongs to the billboard; just below it the
          // row above fades in as a thin strip, never under the text.
          .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
          .drawWithContent {
            drawContent()
            val h = size.height
            val clear = (Layout.BROWSE_TEXT_BOTTOM + 10) / 1080f
            val solid = (Layout.FOCUS_LINE - Layout.ROW_SPACING + 4) / 1080f
            if (browsing != null) drawRect(Brush.verticalGradient(0f to Color.Transparent, clear to Color.Transparent, solid to Color.Black, endY = h), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
          }) {
    // The hero's buttons and pager scroll with the rows, up under the billboard as browsing starts.
    // As tall as all the rows: the screen only shows a window onto them.
    Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).offset(y = top - (Layout.RESTING_ROW_TOP - Layout.BUTTONS_TOP).dp)) {
      // The hero's buttons and pager, under the billboard.
      Column(Modifier.requiredHeight((Layout.RESTING_ROW_TOP - Layout.BUTTONS_TOP).dp).padding(start = Theme.edge), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        if (hero != null) {
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PillButton(
                if (continuing.any { it.id == hero.id }) loc(S.resume) else loc(S.play),
                onClick = { nav.push(Route.Details(hero, autoplay = true)) },
                modifier = Modifier.focusRequester(play).onFocusChanged { if (it.isFocused) onFocus(null) },
                kind = PillKind.Primary,
                icon = Icons.play,
            )
            PillButton(loc(S.details), { nav.push(Route.Details(hero)) }, Modifier.onFocusChanged { if (it.isFocused) onFocus(null) }, icon = Icons.info)
          }
        }
        if (heroes.size > 1) {
          // Story-style pager: the current segment fills as its turn runs out.
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            heroes.indices.forEach { i ->
              // The Mac's pager: the open one longer, filling in the title's colour as its turn runs.
              val w by animateDpAsState(if (i == heroIndex) 57.dp else 16.dp, Theme.glide(), label = "seg")
              Box(Modifier.width(w).height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.28f))) {
                if (i == heroIndex) PagerFill(heroIndex, heroProgress, LocalHue.current ?: Color.White, LocalCalm.current)
              }
            }
          }
        }
      }

    Column(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(Layout.ROW_SPACING.dp)) {
      rows.forEachIndexed { index, section ->
        val current = index == focusedRow
        // The row above the focused one stays visible only as a quiet strip under the billboard.
        val above = focusedRow >= 0 && index < focusedRow
        val alpha by animateFloatAsState(if (above) 0.45f else 1f, tween(300), label = "above")
        Column(Modifier.requiredHeight(Layout.ROW_HEIGHT.dp).graphicsLayer { this.alpha = alpha }, verticalArrangement = Arrangement.spacedBy(Layout.HEADER_GAP.dp)) {
          val scale by animateFloatAsState(if (current) 1.06f else 1f, Theme.focusSpring(), label = "header")
          val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
          Text(
              rowTitle(section.key),
              Modifier.height(Layout.HEADER_HEIGHT.dp).padding(start = Theme.edge).graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (rtl) 1f else 0f, 0.5f)
              },
              style = app(30, FontWeight.Bold), color = Theme.text1, maxLines = 1,
          )
          CompositionLocalProvider(LocalBringIntoViewSpec provides startEdge) {
            LazyRow(
                Modifier.fillMaxWidth().focusRestorer(),
                contentPadding = PaddingValues(horizontal = Theme.edge),
                horizontalArrangement = Arrangement.spacedBy(if (section.key in ranked) 4.dp else 21.dp),
            ) {
              itemsIndexed(section.posts, key = { _, p -> p.id }) { i, post ->
                val focus = Modifier.onFocusChanged { if (it.isFocused) onFocus(Selection(section.key, post, i + 1)) }
                val loads = i < 10 || section.key in opened
                when (section.key) {
                  "continue" -> ContinueCard(
                      post, { nav.push(Route.Details(post)) }, focus,
                      // Held select: off the row, on 30nama too.
                      onLongClick = { app.sheets.show(SheetSpec(post.title(loc.lang), listOf(SheetAction(loc(S.removeFromContinue), Icons.close) { onDismiss(post) }))) },
                  )
                  in ranked -> RankedCard(i + 1, post, { nav.push(Route.Details(post)) }, focus, loads = loads)
                  else -> PosterCard(post, { nav.push(Route.Details(post)) }, focus, loads = loads)
                }
              }
              if (section.key in setOf("movies", "series", "anime")) {
                item(key = "all") {
                  SeeAllCard(loc.title(section.key)) {
                    nav.tab = when (section.key) {
                      "movies" -> Tab.movies
                      "series" -> Tab.series
                      else -> Tab.anime
                    }
                  }
                }
              }
            }
          }
        }
      }
      if (!app.isLoggedIn) GuestNote()
      Box(Modifier.height(200.dp))
    }
    }
  }
}

/** The last card of a row that is a whole section of the catalogue: it leads to that section's tab. */
@Composable
private fun SeeAllCard(title: String, onClick: () -> Unit) {
  val loc = LocalLoc.current
  CardSurface(onClick = onClick) {
    Column(
        Modifier.size(Theme.posterWidth, Theme.posterWidth * 1.5f).background(Color.White.copy(alpha = 0.08f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
      Icon(Icons.grid, null, Modifier.size(40.dp), tint = Theme.text1)
      Text(loc(S.seeAll), style = app(24, FontWeight.Bold), color = Theme.text1)
      Text(title, style = app(20, FontWeight.Medium), color = Theme.text2)
    }
  }
}

@Composable
private fun GuestNote() {
  val app = LocalApp.current
  val loc = LocalLoc.current
  Row(Modifier.padding(start = Theme.edge, top = 8.dp, bottom = 90.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(loc(S.loginToStream), style = app(26), color = Theme.text2)
    PillButton(loc(S.signIn), { app.requestSignIn() })
  }
}

@Composable
private fun HomeSkeleton() {
  Box(Modifier.fillMaxSize()) {
    Column(Modifier.padding(start = Theme.edge, top = 330.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
      Skeleton(Modifier.size(600.dp, 64.dp))
      Skeleton(Modifier.size(420.dp, 28.dp))
      Skeleton(Modifier.size(760.dp, 28.dp))
      Skeleton(Modifier.size(760.dp, 28.dp))
    }
    Row(Modifier.padding(start = Theme.edge, top = (Layout.RESTING_ROW_TOP + Layout.HEADER_HEIGHT + Layout.HEADER_GAP).dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      repeat(9) { Skeleton(Modifier.size(Theme.posterWidth, Theme.posterWidth * 1.5f), Theme.cardRadius) }
    }
  }
}

/**
 * The open pager segment filling as its turn runs. The turn is measured four times a second; the
 * fill runs on between those at an even pace, so it moves smoothly, and it is read only when drawn,
 * so the home screen around it is not rebuilt each time.
 */
@Composable
private fun PagerFill(index: Int, progress: () -> Float, color: Color, calm: Boolean) {
  val fill = remember(index) { Animatable(0f) }
  LaunchedEffect(index) {
    androidx.compose.runtime.snapshotFlow { progress() }.collect { p ->
      // Back to the start (the remote was used): at once, not drawn backwards.
      if (p < fill.value) fill.snapTo(p) else fill.animateTo(p, tween(250, easing = LinearEasing))
    }
  }
  // From the reading start: the right in Persian and Arabic, as the segments themselves run.
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Box(Modifier.fillMaxSize().drawBehind {
    val w = size.width * if (calm) 1f else fill.value.coerceIn(0.08f, 1f)
    drawRoundRect(
        color, topLeft = androidx.compose.ui.geometry.Offset(if (rtl) size.width - w else 0f, 0f),
        size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
    )
  })
}
