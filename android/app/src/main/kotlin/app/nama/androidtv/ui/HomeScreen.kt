package app.nama.androidtv.ui

import app.nama.core.TrendingRow
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import kotlinx.coroutines.launch

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
  var freshSeries by remember { mutableStateOf<List<Post>>(emptyList()) }
  var popularFilms by remember { mutableStateOf<List<Post>>(emptyList()) }
  var topMovies by remember { mutableStateOf<List<Post>>(emptyList()) }
  var topSeries by remember { mutableStateOf<List<Post>>(emptyList()) }
  /** 30nama's own top ten and new releases, from `home`. */
  var siteTop by remember { mutableStateOf<List<Post>>(emptyList()) }
  var newReleases by remember { mutableStateOf<List<Post>>(emptyList()) }
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
    // 30nama's own top ten when `home` gave one; else the two made from the most liked.
    val tops = when {
      f.sections.any { it.key == "top_10" } -> emptyList()
      siteTop.size >= 5 -> listOf(HomeSection("top_10", siteTop))
      else -> listOf(HomeSection(TOP_MOVIES, topMovies), HomeSection(TOP_SERIES, topSeries)).filter { it.posts.isNotEmpty() }
    }
    val fresh = newReleases.takeIf { it.isNotEmpty() && f.sections.none { s -> s.key == "new_releases" } }?.let { listOf(HomeSection("new_releases", it)) }.orEmpty()
    listOfNotNull(
        continuing.takeIf { it.isNotEmpty() }?.let { HomeSection("continue", it) },
        forYou.takeIf { it.isNotEmpty() }?.let { HomeSection("for_you", it) },
        trending.takeIf { it.isNotEmpty() }?.let { HomeSection(TRENDING, it) },
    ) + f.sections.take(1) + fresh + tops + listOfNotNull(
        // With the user's TMDB key: new and returning series, and the films now popular (the Tizen app's).
        freshSeries.takeIf { it.isNotEmpty() }?.let { HomeSection(FRESH, it) },
        popularFilms.takeIf { it.isNotEmpty() }?.let { HomeSection(POPULAR, it) },
    ) + f.sections.drop(1)
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
    if (!app.homeOffline) app.background {
      if (loaded.sections.none { it.key == "top_10" }) {
        val (top, fresh) = app.homeExtras()
        siteTop = top
        newReleases = fresh
        if (top.size < 5) {
          topMovies = app.topTen("movies")
          topSeries = app.topTen("series")
        }
      }
      if (trending.isEmpty()) trending = app.trendingThisWeek()
      if (freshSeries.isEmpty()) freshSeries = app.tmdbRow(TrendingRow.fresh)
      if (popularFilms.isEmpty()) popularFilms = app.tmdbRow(TrendingRow.pop)
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
  // The same title from the fresh home: its newer facts at once, nothing to wait for.
  if (featured != null && staged?.id == featured.id && staged !== featured) staged = featured
  LaunchedEffect(featured?.id) {
    val next = featured ?: return@LaunchedEffect
    if (staged != null) {
      delay(300)
      // Waited on apart from the fetch itself: a filtered host blocks until its connection gives up.
      val warming = launch { runCatching { warmBillboard(context, app, next, loc.lang) } }
      kotlinx.coroutines.withTimeoutOrNull(1_500) { warming.join() }
    }
    staged = next
  }
  LaunchedEffect(browsing == null) { chrome.pageAtTop = browsing == null }
  // A fresh home with fewer heroes than the saved one: start over rather than point past the end.
  LaunchedEffect(heroes.size) {
    if (heroes.isNotEmpty() && heroIndex >= heroes.size) {
      heroIndex = 0
      heroProgress = 0f
    }
  }
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
              val left = continuing.filter { it.id != post.id }
              continuing = left
              // The card in focus went: focus to Play when the row went with it; else its row's card
              // in the same place, which the row's requester now points at.
              if (browsing?.post?.id == post.id && left.isEmpty()) {
                browsing = null
                runCatching { play.requestFocus() }
              }
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

/** Room above and below a row of cards for a focused card's glow and growth. */
private const val GLOW_ROOM = 44

private const val TRENDING = "tm_trend"
private const val FRESH = "tm_fresh"
private const val POPULAR = "tm_pop"
private const val TOP_MOVIES = "top_movies"
private const val TOP_SERIES = "top_series"
private val ranked = setOf("top_10", TOP_MOVIES, TOP_SERIES)

/** A row's name: 30nama's sections by their key, and the rows this app adds. */
@Composable
private fun rowTitle(key: String): String {
  val loc = LocalLoc.current
  return when (key) {
    TRENDING -> loc(S.trendingWeek)
    FRESH -> tv(TV.freshSeries)
    POPULAR -> tv(TV.popularFilms)
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
  /** How far the rows go up for the guest's sign-in under the last one: it whole, clear of the foot. */
  const val NOTE_LIFT = 160
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
  // Down and up go row to row by hand, as on the Apple TV: left to Compose's geometric search, a
  // row starting below the screen's edge was not found, and focus fell back to the first row.
  // Each row is entered at the card last focused on it, or its first.
  val entries = remember(keys) { keys.associateWith { FocusRequester() } }
  val lastIndex = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
  /** The row a card in focus is on, as focus itself says (browsing may run ahead of it). */
  var focusOn by remember { mutableStateOf<String?>(null) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  var move by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
  val currentEntries = androidx.compose.runtime.rememberUpdatedState(entries)
  // A card taken off "continue watching" while in focus: the card now in its place takes it.
  val continueSize = rows.firstOrNull { it.key == "continue" }?.posts?.size ?: 0
  LaunchedEffect(continueSize) {
    if (focusOn != "continue" || continueSize == 0) return@LaunchedEffect
    androidx.compose.runtime.withFrameNanos {}
    runCatching { entries["continue"]?.requestFocus() }
  }
  val focusedRow = browsing?.let { keys.indexOf(it.row) } ?: -1
  // The guest's sign-in under the last row, in focus: no row of its own, so the rows go up by it.
  var onNote by remember { mutableStateOf(false) }
  // The rows sit still while the hero shows; browsing, the focused row's header goes to the focus line.
  val target = when {
    onNote -> Layout.FOCUS_LINE - (rows.size - 1) * (Layout.ROW_HEIGHT + Layout.ROW_SPACING) - Layout.NOTE_LIFT
    focusedRow < 0 -> Layout.RESTING_ROW_TOP
    else -> Layout.FOCUS_LINE - focusedRow * (Layout.ROW_HEIGHT + Layout.ROW_SPACING)
  }
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
        // Each row's state follows its key: rows arrive late (the top tens) and move the others down.
        androidx.compose.runtime.key(section.key) {
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
            // Taller than the row by the room a card's glow and its growth in focus take, above and
            // below, so neither is cut at the row's edge; the row itself keeps its height.
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            LazyRow(
                Modifier.fillMaxWidth().requiredHeight((300 + GLOW_ROOM * 2).dp)
                    .onPreviewKeyEvent { e ->
                      if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                      val to = when (e.key) {
                        androidx.compose.ui.input.key.Key.DirectionDown -> keys.getOrNull(index + 1)
                        androidx.compose.ui.input.key.Key.DirectionUp -> keys.getOrNull(index - 1)
                        else -> return@onPreviewKeyEvent false
                      } ?: return@onPreviewKeyEvent false
                      // The rows move first, so the next one is on the screen; then it takes focus.
                      val target = rows.first { it.key == to }
                      val at = (lastIndex[to] ?: 0).coerceIn(0, (target.posts.size - 1).coerceAtLeast(0))
                      target.posts.getOrNull(at)?.let { onFocus(Selection(to, it, at + 1)) }
                      // One move at a time, through the current requesters: a row arriving meanwhile replaces them.
                      move?.cancel()
                      move = scope.launch {
                        repeat(30) {
                          androidx.compose.runtime.withFrameNanos {}
                          if (focusOn == to) return@launch
                          runCatching { currentEntries.value[to]?.requestFocus() }
                        }
                      }
                      true
                    },
                contentPadding = PaddingValues(horizontal = Theme.edge, vertical = GLOW_ROOM.dp),
                horizontalArrangement = Arrangement.spacedBy(if (section.key in ranked) 4.dp else 21.dp),
            ) {
              itemsIndexed(section.posts, key = { _, p -> p.id }) { i, post ->
                val focus = Modifier.onFocusChanged {
                  if (it.isFocused) {
                    focusOn = section.key
                    lastIndex[section.key] = i
                    onFocus(Selection(section.key, post, i + 1))
                  }
                }.then(if (i == (lastIndex[section.key] ?: 0).coerceAtMost(section.posts.lastIndex)) Modifier.focusRequester(entries.getValue(section.key)) else Modifier)
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
        }
      }
      if (!app.isLoggedIn) GuestNote(Modifier.onFocusChanged { onNote = it.hasFocus }.onPreviewKeyEvent { e ->
        // Up goes back to the card last in focus on the last row, as between rows; the geometric
        // search found the first row instead.
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || e.key != androidx.compose.ui.input.key.Key.DirectionUp) return@onPreviewKeyEvent false
        val last = rows.lastOrNull() ?: return@onPreviewKeyEvent false
        val at = (lastIndex[last.key] ?: 0).coerceIn(0, (last.posts.size - 1).coerceAtLeast(0))
        last.posts.getOrNull(at)?.let { onFocus(Selection(last.key, it, at + 1)) }
        move?.cancel()
        move = scope.launch {
          repeat(30) {
            androidx.compose.runtime.withFrameNanos {}
            if (focusOn == last.key && !onNote) return@launch
            runCatching { currentEntries.value[last.key]?.requestFocus() }
          }
        }
        true
      })
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
        // Solid: the focus glow is a shadow under the card and showed through a see-through one.
        Modifier.size(Theme.posterWidth, Theme.posterWidth * 1.5f).background(Theme.surface),
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
private fun GuestNote(modifier: Modifier = Modifier) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  Row(modifier.padding(start = Theme.edge, top = 8.dp, bottom = 90.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
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
