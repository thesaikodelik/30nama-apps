package app.nama.androidtv.ui

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.Spacer
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.nama.androidtv.AppModel
import app.nama.androidtv.player.PlayerScreen
import app.nama.androidtv.player.TrailerScreen
import app.nama.core.Lang
import app.nama.core.S
import kotlinx.coroutines.delay

/** What a page tells the shell: whether it is scrolled to its top, where the tab bar belongs. */
class Chrome {
  var pageAtTop by mutableStateOf(true)
  /** The same for a page opened over a tab (a title's page), which scrolls on its own. */
  var routeAtTop by mutableStateOf(true)
  /** The tab's root page is the one on screen (nothing opened over it). */
  var rootShown by mutableStateOf(true)
  /** The viewer is on the tab bar: a tab opening as they cross it must leave focus there. */
  var barFocused by mutableStateOf(false)
  /**
   * Counts the times focus went up into the bar from a page. The page showing then scrolls to its
   * very top ([FollowBar]): focus had stopped at its first control, so whatever stands above that
   * (a title's header, a heading) stayed off the screen, and the page looked stuck half way up.
   */
  var toTop by androidx.compose.runtime.mutableIntStateOf(0)
  /** A card over the page (a key, the member's name) is open: the bar takes no focus until it closes. */
  var modal by mutableStateOf(false)
  /** A page may move focus into itself when it opens. */
  val mayTakeFocus get() = rootShown && !barFocused
  /** Moves focus up into the tab bar, as Back does from a page's root. */
  val bar = FocusRequester()
}

val LocalApp = compositionLocalOf<AppModel> { error("no app") }
val LocalNav = compositionLocalOf<Nav> { error("no nav") }
val LocalChrome = compositionLocalOf { Chrome() }

/** True inside a page opened over a tab (a title, the schedule), false on a tab's own page. */
val LocalOnRoute = compositionLocalOf { false }

/**
 * Tells the shell whether this page is at its top, so the tab bar steps out of the way once it
 * scrolls, on every page alike: left showing, it stood over the first row of a wall (the owner).
 */
@Composable
fun ReportTop(atTop: Boolean) {
  val chrome = LocalChrome.current
  val onRoute = LocalOnRoute.current
  LaunchedEffect(atTop, onRoute) { if (onRoute) chrome.routeAtTop = atTop else chrome.pageAtTop = atTop }
  androidx.compose.runtime.DisposableEffect(onRoute) { onDispose { if (onRoute) chrome.routeAtTop = true else chrome.pageAtTop = true } }
}

/** [ReportTop] for a lazy grid: at its top while its first item is less than 60 points in. */
@Composable
fun ReportTop(state: androidx.compose.foundation.lazy.grid.LazyGridState) {
  val atTop by remember(state) { androidx.compose.runtime.derivedStateOf { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 60 } }
  ReportTop(atTop)
  FollowBar { state.animateScrollToItem(0) }
}

/** [ReportTop] for a lazy list. */
@Composable
fun ReportTop(state: androidx.compose.foundation.lazy.LazyListState) {
  val atTop by remember(state) { androidx.compose.runtime.derivedStateOf { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 60 } }
  ReportTop(atTop)
  FollowBar { state.animateScrollToItem(0) }
}

/**
 * Every scrolling page's way back up, in one place: when focus goes up into the bar, the page
 * showing under it runs [toTop]. A page hidden under an open one does not.
 */
@Composable
fun FollowBar(toTop: suspend () -> Unit) {
  val chrome = LocalChrome.current
  val onRoute = LocalOnRoute.current
  val asked = chrome.toTop
  LaunchedEffect(asked) {
    if (asked == 0 || onRoute == chrome.rootShown) return@LaunchedEffect
    toTop()
  }
}

/** The whole app: the opening title over the phase it is in, toasts, and the player over everything. */
@Composable
fun NamaRoot(app: AppModel, nav: Nav, onExit: () -> Unit, lively: Boolean, intro: Boolean = true) {
  // The TV's own "animations off" (an accessibility setting) counts as reduced motion too.
  val context = androidx.compose.ui.platform.LocalContext.current
  val systemCalm = remember { systemAnimationsOff(context) }
  ScreenUnits {
    // Every page, list, panel and menu scrolls as the title page does (Minimal): what has focus is
    // always whole on the screen, 120 points clear of each edge (so of the tab bar too), and the
    // page moves only as far as that takes. Compose's default on a TV holds focus a third of the way
    // down and keeps no margin, so cards stood half under the bar or cut at the foot (the owner).
    // A list with a way of its own (home's rows, its row of cards) still sets it where it is.
    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val minimal = remember(margin) { Minimal(margin) }
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    CompositionLocalProvider(
        LocalApp provides app,
        LocalNav provides nav,
        LocalLoc provides app.loc,
        LocalCalm provides (app.settings.reduceMotion || systemCalm),
        LocalLively provides (app.settings.effects.lively(lively) && !app.settings.reduceMotion && !systemCalm),
        LocalLayoutDirection provides if (app.settings.lang.isRTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides minimal,
    ) {
      // The opening title runs its course once; after that it only waits, a little, on the network.
      var introPlayed by remember { mutableStateOf(!intro) }
      var splashGone by remember { mutableStateOf(!intro) }
      val leaving = introPlayed && app.phase != AppModel.Phase.Booting
      LaunchedEffect(Unit) {
        delay(2_800)
        val deadline = System.currentTimeMillis() + 2_500
        while (!app.homeLoaded && (app.phase == AppModel.Phase.Main || app.phase == AppModel.Phase.Booting) && System.currentTimeMillis() < deadline) delay(100)
        introPlayed = true
      }
      LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        delay(SPLASH_EXIT_MS)
        splashGone = true
      }

      // Backspace is Back too, on a box run with a keyboard, as in most TV apps: only when nothing
      // took it, so a text field being typed in still deletes. (Escape is turned to Back in MainActivity.)
      // A text field takes only the key going down; its coming up is let through, so Back follows
      // only a Backspace whose going down reached here too.
      val back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      var backspaceDown by remember { mutableStateOf(false) }
      Box(Modifier.fillMaxSize().background(Theme.bg0).onKeyEvent { e ->
        if (e.key != Key.Backspace) return@onKeyEvent false
        if (e.type == KeyEventType.KeyDown) backspaceDown = true
        else if (e.type == KeyEventType.KeyUp && backspaceDown) {
          backspaceDown = false
          back?.onBackPressed()
        }
        true
      }) {
        // A new language or account means a different catalogue: every tab starts over.
        val updater = app.updater
        val failed = (updater.state as? app.nama.androidtv.Updater.State.Failed)?.takeIf { updater.showFailure && app.playback == null }
        // What had focus before the failure card came up gets it back when the card goes.
        val content = remember { FocusRequester() }
        var cardWasUp by remember { mutableStateOf(false) }
        LaunchedEffect(failed != null) {
          if (failed != null) cardWasUp = true
          else if (cardWasUp) {
            cardWasUp = false
            withFrameNanos {}
            runCatching { content.requestFocus() }
          }
        }
        // The restorer has a group of its own: without one its way in reached every page's own
        // group below it, so entering an open page from the bar was turned back to the hidden one.
        key(app.contentVersion) {
          Box(Modifier.fillMaxSize().blockFocus(app.playback != null || app.trailer != null || failed != null).focusRequester(content).focusRestorer().focusGroup()) {
          when (app.phase) {
            AppModel.Phase.Booting -> if (splashGone) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(Modifier.size(56.dp)) }
            AppModel.Phase.Failed -> ErrorState("${app.loc(S.noConnection)}. ${app.loc(S.tryAgain)}") { app.boot() }
            AppModel.Phase.Login -> LoginScreen()
            AppModel.Phase.Main -> MainScreen(onExit)
          }
          }
        }
        if (!splashGone) Box(Modifier.zIndex(10f)) { Splash(leaving = leaving, ready = app.homeLoaded || (app.phase != AppModel.Phase.Main && app.phase != AppModel.Phase.Booting)) }
        // The player, and a trailer, over everything; what is under them cannot take focus meanwhile.
        app.playback?.let { request -> Box(Modifier.fillMaxSize().zIndex(15f)) { PlayerScreen(request) } }
        app.trailer?.let { (_, url) -> Box(Modifier.fillMaxSize().zIndex(15f)) { TrailerScreen(url) } }
        // The updater's word: it arrived, a new one is out, or it went wrong.
        val updatedTo = tv(TV.updatedTo)
        LaunchedEffect(updater.arrived, splashGone) { if (splashGone) updater.arrived?.let { app.showToast("$updatedTo ${it}") } }
        failed?.let { failed ->
          UpdateFailedCard(failed.page, retry = { updater.showFailure = false; updater.check() }, close = { updater.showFailure = false })
        }
        AnimatedVisibility(
            visible = app.toast != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp).zIndex(20f),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut(),
        ) { app.toast?.let { Toast(it) } }
      }
    }
  }
}

/** Tabs along the top, each with its own stack of pages, as on the Apple TV. */
@Composable
fun MainScreen(onExit: () -> Unit) {
  val app = LocalApp.current
  val nav = LocalNav.current
  val chrome = remember { Chrome() }
  val holder = rememberSaveableStateHolder()
  var barFocused by remember { mutableStateOf(false) }
  var lastBack by remember { mutableLongStateOf(0L) }
  var paging by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val loc = app.loc

  // Back: a page off the stack; at a tab's root, up to the tab bar; from the bar, home; from
  // home's bar, a second press within three seconds leaves.
  BackHandler(enabled = app.playback == null && app.trailer == null && app.sheets.shown == null) {
    when {
      nav.pop() -> Unit
      !barFocused -> runCatching { chrome.bar.requestFocus() }
      nav.tab != Tab.home -> nav.tab = Tab.home
      System.currentTimeMillis() - lastBack < 3_000 -> onExit()
      else -> {
        lastBack = System.currentTimeMillis()
        app.showToast(TV.pressBackAgain(loc.lang))
      }
    }
  }

  // A row of the TV's home screen: its title over home.
  LaunchedEffect(app.pendingOpen) {
    val (id, play) = app.pendingOpen ?: return@LaunchedEffect
    app.pendingOpen = null
    nav.reset()
    nav.push(Route.Details(id, null, autoplay = play))
  }
  CompositionLocalProvider(LocalChrome provides chrome) {
    Box(Modifier.fillMaxSize()) {
      val route = nav.top
      chrome.rootShown = route == null
      val sheet = app.sheets.shown != null || app.sheets.link != null || app.sheets.reading != null
      val root = remember { FocusRequester() }
      // Back at a tab's root: focus returns to where it was before the page was opened.
      LaunchedEffect(route) {
        if (route != null) return@LaunchedEffect
        withFrameNanos {}
        runCatching { root.restoreFocusedChild() || root.requestFocus().let { true } }
      }
      // A page opened over the tab takes focus, if it has not taken it itself: what was pressed
      // to open it is still focused under it, hidden, and the remote would go on working there.
      val routeFocus = remember { FocusRequester() }
      val focus = androidx.compose.ui.platform.LocalFocusManager.current
      var routeHasFocus by remember { mutableStateOf(false) }
      LaunchedEffect(route) {
        if (route == null) return@LaunchedEffect
        var tries = 0
        // Until the page has something to hold focus, however long its first answer takes.
        while (true) {
          withFrameNanos {}
          if (routeHasFocus || barFocused) return@LaunchedEffect
          runCatching { routeFocus.requestFocus() }
          // Still nothing on the page to take it: off the hidden page at least, so the remote does nothing there.
          if (++tries == 2 && !routeHasFocus && !barFocused) focus.clearFocus(force = true)
          delay(if (tries < 40) 50 else 200)
        }
      }
      // Channel up and down (Page Up and Down on a keyboard) go a few steps at once, about a
      // screenful, where a page is long: an article, a wall of posters. Only what a page leaves
      // unused comes here; the player keeps them for the next and last episode.
      Box(Modifier.fillMaxSize().blockFocus(sheet).onKeyEvent { e ->
        val down = when (e.key) {
          Key.ChannelDown, Key.PageDown -> true
          Key.ChannelUp, Key.PageUp -> false
          else -> return@onKeyEvent false
        }
        // One run at a time: a held button repeats faster than the steps are taken.
        if (e.type == KeyEventType.KeyDown && !barFocused && paging?.isActive != true) paging = scope.launch {
          repeat(PAGE_STEPS) {
            if (!focus.moveFocus(if (down) FocusDirection.Down else FocusDirection.Up)) return@launch
            withFrameNanos {}
          }
        }
        true
      }) {
        // The tab's root page stays alive, hidden, under what is opened on top of it, so its
        // scroll, focus and loaded rows are all still there on the way back.
        // Its block on focus is a group of its own around it: on the same group as focusRestorer, the
        // restorer's way in replaced the block, and the hidden page took focus from the one showing.
        Box(Modifier.fillMaxSize().blockFocus(route != null).focusGroup()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = if (route == null) 1f else 0f }
                .focusRequester(root).focusRestorer()) {
          holder.SaveableStateProvider(nav.tab.name) { TabRoot(nav.tab) }
        }
        }
        if (route != null) {
          // Each page keeps its own saved state while another is shown over it.
          // The Mac's page entrance: it fades in from a touch larger, 0.28 s.
          val calm = LocalCalm.current
          val appear = remember(route) { androidx.compose.animation.core.Animatable(if (calm) 1f else 0f) }
          LaunchedEffect(route) { appear.animateTo(1f, androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
          Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = appear.value
            val scale = 1.02f - 0.02f * appear.value
            scaleX = scale
            scaleY = scale
          }.focusRequester(routeFocus).onFocusChanged { routeHasFocus = it.hasFocus }) {
            holder.SaveableStateProvider("${nav.tab}/${nav.stack(nav.tab).size}/${route.hashCode()}") { CompositionLocalProvider(LocalOnRoute provides true) { RouteScreen(route) } }
          }
        }
      }
      val pillFocus = remember { FocusRequester() }
      val updater = app.updater
      // Into the page under the bar: back to where the viewer was on the tab's page, or into its
      // first item. The tab may have changed this very press (a pending one): the page open now,
      // not the drawn one. An open page is entered at its first control, as when it opened; from
      // Back, at the bar's far end, the search found nothing of it below (the owner).
      fun enterPage(): Boolean {
        return if (nav.top != null) {
          scope.launch {
            withFrameNanos {}
            runCatching { routeFocus.requestFocus() }
          }
          true
        } else {
          scope.launch {
            withFrameNanos {}
            runCatching { root.restoreFocusedChild() || root.requestFocus().let { true } }
          }
          true
        }
      }
      // Over every page, as on the Mac; it steps out of the way once a page scrolls down.
      val barShown = barFocused || (if (route == null) chrome.pageAtTop else chrome.routeAtTop)
      val shown by animateFloatAsState(if (barShown) 1f else 0f, tween(260), label = "bar")
      LaunchedEffect(route) { chrome.routeAtTop = true }
      TabBar(
          Modifier.align(Alignment.TopCenter).padding(top = 30.dp).graphicsLayer {
            alpha = shown
            translationY = -40f * (1f - shown)
          },
          routeOpen = route != null,
          onBack = { nav.pop() },
          focusedChange = {
            if (it && !barFocused) chrome.toTop++
            barFocused = it
            chrome.barFocused = it
          },
          // Down from the bar goes back to where the viewer was on the tab's page, or into its
          // first item. Left to the geometric search, it found nothing below: a page that is one
          // grid (a catalogue, search) starts under the bar, not below it.
          down = {
            // The update capsule under the bar first, while it has something to press.
            if (updater.pillHasButtons() && runCatching { pillFocus.requestFocus() }.isSuccess) return@TabBar true
            enterPage()
          },
      )
      // The Mac's update capsule, out from under the bar, and with it when the bar steps away.
      UpdatePill(
          updater, pillFocus, up = { runCatching { chrome.bar.requestFocus() } }, down = { enterPage() },
          modifier = Modifier.align(Alignment.TopCenter).padding(top = 112.dp).graphicsLayer {
            alpha = shown
            translationY = -40f * (1f - shown)
          },
      )
      nav.flight?.let { f -> if ((route as? Route.Details)?.id == f.post.id) FlyingPoster(f) { nav.flight = null } else LaunchedEffect(f) { nav.flight = null } }
      SheetHost(app.sheets)
      app.sheets.link?.let { LinkCard(it) { app.sheets.link = null } }
      app.sheets.reading?.let { ReadCard(it) { app.sheets.reading = null } }
    }
  }
}

/**
 * The Mac's poster in flight: the pressed card's picture travels from where the card was to where
 * the title's page keeps its poster, growing on the way, while the page fades in under it. The
 * page holds its own poster back until this one has landed.
 */
@Composable
private fun FlyingPoster(flight: Flight, landed: () -> Unit) {
  val density = androidx.compose.ui.platform.LocalDensity.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val t = remember(flight) { androidx.compose.animation.core.Animatable(0f) }
  LaunchedEffect(flight) {
    // The Mac's spring: a response of 0.48 s, damping 0.86.
    t.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.86f, stiffness = 171f))
    landed()
  }
  androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val to = with(density) {
      val w = DETAILS_POSTER_W.dp.toPx()
      val h = DETAILS_POSTER_H.dp.toPx()
      val x = if (rtl) maxWidth.toPx() - Theme.edge.toPx() - w else Theme.edge.toPx()
      androidx.compose.ui.geometry.Rect(x, DETAILS_TOP.dp.toPx(), x + w, DETAILS_TOP.dp.toPx() + h)
    }
    val p = t.value
    val from = flight.from
    fun mix(a: Float, b: Float) = a + (b - a) * p
    val left = mix(from.left, to.left)
    val top = mix(from.top, to.top)
    val w = with(density) { mix(from.width, to.width).toDp() }
    val h = with(density) { mix(from.height, to.height).toDp() }
    val radius = mix(Theme.cardRadius.value, 24f).dp
    RemoteImage(
        flight.post.poster.url(Theme.posterWidth.value * 1.1), blurhash = flight.post.poster.blurhash, pixelWidth = 300, fade = false,
        modifier = Modifier.absoluteOffset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }.size(w, h)
            .clip(RoundedCornerShape(radius)),
    )
  }
}

/** Steps a channel button takes: about a screenful of posters or of an article's paragraphs. */
private const val PAGE_STEPS = 4

@Composable
private fun TabRoot(tab: Tab) {
  val loc = LocalLoc.current
  when (tab) {
    Tab.home -> HomeScreen()
    Tab.search -> SearchScreen()
    Tab.movies -> CatalogScreen("movies", loc(S.movies))
    Tab.series -> CatalogScreen("series", loc(S.series))
    Tab.anime -> CatalogScreen("anime", loc(S.anime))
    Tab.browse -> BrowseScreen()
    Tab.profile -> ProfileScreen()
    Tab.settings -> SettingsScreen()
  }
}

@Composable
private fun RouteScreen(route: Route) {
  when (route) {
    is Route.Details -> DetailsScreen(route)
    is Route.Catalog -> CatalogScreen(route.slug, route.title)
    is Route.PersonPage -> PersonScreen(route.person)
    Route.Schedule -> ScheduleScreen()
    Route.TopLists -> TopListsScreen()
    is Route.AdvancedSearch -> AdvancedSearchScreen(route.query)
    Route.News -> NewsScreen()
    is Route.ArticlePage -> ArticleScreen(route.article)
  }
}

@Composable
fun Placeholder(text: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, style = app(32), color = Theme.text3) }
}

/**
 * The Mac's top bar (Mac/MacShell.swift, docs/design "نوار بالا"): a glass capsule at the top centre;
 * the tabs' words a little dim, full under focus with a faint pill; the open tab under a lit glass
 * pill that runs to the next like a drop of liquid, its leading edge first. Search and Settings go
 * by their icons, Profile by its icon and name. With a page open over a tab, a round glass Back
 * button comes in beside the bar and the pill stays on the tab the page belongs to (the owner: where
 * one is must always show; the Mac takes the pill away). Moving focus along it opens each tab after a
 * beat, as tvOS does, so a held remote crossing it does not build every page on the way.
 */
@Composable
private fun TabBar(modifier: Modifier, routeOpen: Boolean, onBack: () -> Unit, focusedChange: (Boolean) -> Unit, down: () -> Boolean) {
  val nav = LocalNav.current
  val loc = LocalLoc.current
  val chrome = LocalChrome.current
  val calm = LocalCalm.current
  var pending by remember { mutableStateOf<Tab?>(null) }
  LaunchedEffect(pending) {
    val next = pending ?: return@LaunchedEffect
    delay(300)
    nav.tab = next
  }
  // The tab changed from elsewhere (Back to home, a title opened from the TV's home screen): a
  // move along the bar still waiting must not switch away from it.
  LaunchedEffect(nav.tab) { if (pending != nav.tab) pending = null }
  val back = remember { FocusRequester() }
  // Coming up from a page lands on the open tab, or on Back while a page is open, not on whichever
  // is nearest, which would open that one instead: from outside, only that one takes focus.
  var inside by remember { mutableStateOf(false) }
  var focusedTab by remember { mutableStateOf<Tab?>(null) }
  // Along the bar by its order, Back first while it is there: the geometric search went the wrong
  // way here, past the open tab to the one beyond it.
  val steps = remember { Tab.entries.associateWith { FocusRequester() } }
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  fun step(forward: Boolean): Boolean {
    val order = Tab.entries
    val at = order.indexOfFirst { t -> focusedTab == t }
    if (at < 0) {
      // On Back: forward is the first tab.
      if (forward) runCatching { steps.getValue(order.first()).requestFocus() }
      return true
    }
    val next = at + if (forward) 1 else -1
    when {
      next in order.indices -> runCatching { steps.getValue(order[next]).requestFocus() }
      next < 0 && routeOpen -> runCatching { back.requestFocus() }
    }
    return true
  }
  // Where each tab sits in the bar, for the pill to go to.
  val places = remember { mutableStateMapOf<Tab, Rect>() }
  Row(
      modifier
          .onFocusChanged {
            inside = it.hasFocus
            focusedChange(it.hasFocus)
          }
          .onPreviewKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
              androidx.compose.ui.input.key.Key.DirectionLeft -> step(forward = rtl)
              androidx.compose.ui.input.key.Key.DirectionRight -> step(forward = !rtl)
              androidx.compose.ui.input.key.Key.DirectionDown -> {
                // A tab still waiting its beat opens now, so Down goes into the page it shows.
                pending?.let { nav.tab = it }
                pending = null
                down()
              }
              else -> false
            }
          },
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // Back has a place of its own beside the bar, kept while hidden, and the same room is kept on
    // the other side, so the bar stays centred and its tabs do not shift as Back comes and goes.
    BackDisc(routeOpen, onBack, Modifier.focusRequester(back).onFocusChanged { if (it.hasFocus) focusedTab = null })
    Box(Modifier.glass(RoundedCornerShape(50)).padding(6.dp)) {
      LiquidPill(places[nav.tab], calm)
      Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (tab in Tab.entries) {
          val (title, icon) = when (tab) {
            Tab.search -> loc(S.search) to Icons.search
            Tab.home -> loc(S.home) to null
            Tab.movies -> loc(S.movies) to null
            Tab.series -> loc(S.series) to null
            Tab.anime -> loc(S.anime) to null
            Tab.browse -> loc(S.browse) to null
            Tab.profile -> loc(S.profile) to Icons.personCircle
            Tab.settings -> loc(S.settings) to Icons.gear
          }
          TabItem(
              title = title, icon = icon, iconOnly = tab == Tab.search || tab == Tab.settings, selected = nav.tab == tab,
              modifier = (if (tab == nav.tab) Modifier.focusRequester(chrome.bar) else Modifier)
                  .focusRequester(steps.getValue(tab))
                  .onFocusChanged { if (it.hasFocus) focusedTab = tab }
                  .focusProperties { canFocus = !chrome.modal && (inside || (!routeOpen && tab == nav.tab)) }
                  .onGloballyPositioned { places[tab] = Rect(it.positionInParent(), it.size.toSize()) },
              // While a page is open over the tab, moving along the bar only moves; pressing goes.
              onFocus = { if (nav.tab != tab && !routeOpen) pending = tab },
              onClick = {
                pending = null
                if (nav.tab == tab) while (nav.pop()) Unit else nav.tab = tab
              },
          )
        }
      }
    }
    Spacer(Modifier.size(58.dp))
  }
}

/**
 * The open tab's pill, of lit glass. Moving to another tab its two edges travel apart, the leading
 * one quicker, so it stretches toward the new tab and settles there: the Mac's drop of liquid,
 * drawn with two springs instead of a blurred metaball, which a weak TV could not pay for.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.LiquidPill(target: Rect?, calm: Boolean) {
  val density = LocalDensity.current
  val left = remember { Animatable(0f) }
  val right = remember { Animatable(0f) }
  var placed by remember { mutableStateOf(false) }
  LaunchedEffect(target) {
    target ?: return@LaunchedEffect
    if (!placed || calm) {
      left.snapTo(target.left)
      right.snapTo(target.right)
      placed = true
      return@LaunchedEffect
    }
    val towardRight = target.left > left.value
    val quick = spring<Float>(dampingRatio = 0.8f, stiffness = 520f)
    val slow = spring<Float>(dampingRatio = 0.86f, stiffness = 170f)
    launch { left.animateTo(target.left, if (towardRight) slow else quick) }
    launch { right.animateTo(target.right, if (towardRight) quick else slow) }
  }
  val shown by animateFloatAsState(if (target != null && placed) 1f else 0f, tween(200), label = "pill")
  val top = target?.top ?: 0f
  val height = target?.height ?: 0f
  with(density) {
    Box(
        // Measured from the left edge, so placed from it too, whichever way the interface reads.
        Modifier
            .align(androidx.compose.ui.AbsoluteAlignment.TopLeft)
            .absoluteOffset { IntOffset(left.value.toInt(), top.toInt()) }
            .size((right.value - left.value).coerceAtLeast(0f).toDp(), height.toDp())
            .graphicsLayer { alpha = shown }
            .glass(RoundedCornerShape(50), lit = true, veil = 0f),
    )
  }
}

/** The Mac's round glass Back button beside the bar, while a page is open over a tab. */
@Composable
private fun BackDisc(shownNow: Boolean, onBack: () -> Unit, modifier: Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val shown by animateFloatAsState(if (shownNow) 1f else 0f, tween(220), label = "back")
  val modal = LocalChrome.current.modal
  Surface(
      onClick = onBack,
      enabled = shownNow,
      // Hidden, it takes no focus: a TV's Surface keeps it even when disabled.
      modifier = modifier.focusProperties { canFocus = shownNow && !modal }.graphicsLayer {
        alpha = shown
        scaleX = 0.8f + 0.2f * shown
        scaleY = 0.8f + 0.2f * shown
      }.focusRing(interaction, CircleShape),
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(CircleShape),
      colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, contentColor = Theme.text1.copy(alpha = 0.85f), focusedContentColor = Theme.text1),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
  ) {
    Box(Modifier.size(58.dp).glass(CircleShape, lit = focused), contentAlignment = Alignment.Center) {
      Box(Modifier.size(46.dp).background(Color.White.copy(alpha = if (focused) 0.12f else 0f), CircleShape))
      // The arrow points back in the reading direction.
      Icon(Icons.chevronBack, null, Modifier.size(28.dp).then(if (LocalLayoutDirection.current == LayoutDirection.Rtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier))
    }
  }
}

@Composable
private fun TabItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, iconOnly: Boolean, selected: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  LaunchedEffect(focused) { if (focused) onFocus() }
  // Dark words on the dark bar until the pill is under them; full under focus, as under the Mac's pointer.
  val ink by animateColorAsState(if (selected || focused) Theme.text1 else Theme.text1.copy(alpha = 0.7f), tween(180), label = "ink")
  Surface(
      onClick = onClick,
      modifier = modifier,
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
      colors = ClickableSurfaceDefaults.colors(
          containerColor = Color.Transparent,
          focusedContainerColor = if (selected) Color.Transparent else Color.White.copy(alpha = 0.12f),
          contentColor = ink,
          focusedContentColor = ink,
      ),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
  ) {
    // The menu's glass stays as it is; the ring is drawn over it.
    Row(Modifier.focusWash(focused, 0.30f, RoundedCornerShape(50)).focusRing(focused, RoundedCornerShape(50)).height(58.dp).padding(horizontal = if (iconOnly) 18.dp else 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (icon != null) Icon(icon, title, Modifier.size(26.dp))
      if (!iconOnly) Text(title, style = app(25, FontWeight.SemiBold), maxLines = 1)
    }
  }
}

/** Animations switched off for the whole TV, in its accessibility or developer settings. */
fun systemAnimationsOff(context: android.content.Context): Boolean =
    runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
