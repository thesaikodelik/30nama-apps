package app.nama.androidtv.ui

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
import app.nama.core.Lang
import app.nama.core.S
import kotlinx.coroutines.delay

/** What a page tells the shell: whether it is scrolled to its top, where the tab bar belongs. */
class Chrome {
  var pageAtTop by mutableStateOf(true)
  /** Moves focus up into the tab bar, as Back does from a page's root. */
  val bar = FocusRequester()
}

val LocalApp = compositionLocalOf<AppModel> { error("no app") }
val LocalNav = compositionLocalOf<Nav> { error("no nav") }
val LocalChrome = compositionLocalOf { Chrome() }

/** The whole app: the opening title over the phase it is in, toasts, and the player over everything. */
@Composable
fun NamaRoot(app: AppModel, nav: Nav, onExit: () -> Unit, lively: Boolean, intro: Boolean = true) {
  ScreenUnits {
    CompositionLocalProvider(
        LocalApp provides app,
        LocalNav provides nav,
        LocalLoc provides app.loc,
        LocalCalm provides app.settings.reduceMotion,
        LocalLively provides (lively && !app.settings.reduceMotion),
        LocalLayoutDirection provides if (app.settings.lang.isRTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
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

      Box(Modifier.fillMaxSize().background(Theme.bg0)) {
        // A new language or account means a different catalogue: every tab starts over.
        key(app.contentVersion) {
          when (app.phase) {
            AppModel.Phase.Booting -> if (splashGone) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(Modifier.size(56.dp)) }
            AppModel.Phase.Failed -> ErrorState("${app.loc(S.noConnection)}. ${app.loc(S.tryAgain)}") { app.boot() }
            AppModel.Phase.Login -> LoginScreen()
            AppModel.Phase.Main -> MainScreen(onExit)
          }
        }
        if (!splashGone) Box(Modifier.zIndex(10f)) { Splash(leaving = leaving, ready = app.homeLoaded || (app.phase != AppModel.Phase.Main && app.phase != AppModel.Phase.Booting)) }
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
  val loc = app.loc

  // Back: a page off the stack; at a tab's root, up to the tab bar; from the bar, home; from
  // home's bar, a second press within three seconds leaves.
  BackHandler(enabled = app.playback == null && app.sheets.shown == null) {
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

  CompositionLocalProvider(LocalChrome provides chrome) {
    Box(Modifier.fillMaxSize()) {
      val route = nav.top
      val sheet = app.sheets.shown != null
      val root = remember { FocusRequester() }
      // Back at a tab's root: focus returns to where it was before the page was opened.
      LaunchedEffect(route) {
        if (route != null) {
          barFocused = false
          return@LaunchedEffect
        }
        withFrameNanos {}
        runCatching { root.restoreFocusedChild() || root.requestFocus().let { true } }
      }
      Box(Modifier.fillMaxSize().blockFocus(sheet)) {
        // The tab's root page stays alive, hidden, under what is opened on top of it, so its
        // scroll, focus and loaded rows are all still there on the way back.
        Box(
            Modifier.fillMaxSize().blockFocus(route != null).graphicsLayer { alpha = if (route == null) 1f else 0f }
                .focusRequester(root).focusRestorer()) {
          holder.SaveableStateProvider(nav.tab.name) { TabRoot(nav.tab) }
        }
        if (route != null) {
          // Each page keeps its own saved state while another is shown over it.
          holder.SaveableStateProvider("${nav.tab}/${nav.stack(nav.tab).size}") { RouteScreen(route) }
        }
      }
      val barShown = barFocused || (route == null && chrome.pageAtTop)
      val shown by animateFloatAsState(if (barShown) 1f else 0f, tween(260), label = "bar")
      // A page opened on top of a tab has the screen to itself, as on the Apple TV.
      if (route == null) TabBar(
          Modifier.align(Alignment.TopCenter).padding(top = 40.dp).graphicsLayer {
            alpha = shown
            translationY = -40f * (1f - shown)
          },
          focusedChange = { barFocused = it },
      )
      SheetHost(app.sheets)
    }
  }
}

@Composable
private fun TabRoot(tab: Tab) {
  val loc = LocalLoc.current
  when (tab) {
    Tab.home -> HomeScreen()
    else -> Placeholder(tab.name)
  }
}

@Composable
private fun RouteScreen(route: Route) {
  when (route) {
    is Route.Details -> DetailsScreen(route)
    else -> Placeholder(route.toString())
  }
}

@Composable
fun Placeholder(text: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, style = app(32), color = Theme.text3) }
}

/**
 * The tab bar: a glass capsule of tab names. Moving focus along it opens each tab after a beat,
 * as tvOS does, so a held remote crossing it does not build every page on the way.
 */
@Composable
private fun TabBar(modifier: Modifier, focusedChange: (Boolean) -> Unit) {
  val nav = LocalNav.current
  val loc = LocalLoc.current
  val chrome = LocalChrome.current
  // Persian names are short enough for all eight tabs; in English and Arabic search and settings
  // go by their icons alone, as is usual on tvOS.
  val roomy = loc.lang == Lang.fa
  var pending by remember { mutableStateOf<Tab?>(null) }
  LaunchedEffect(pending) {
    val next = pending ?: return@LaunchedEffect
    delay(300)
    nav.tab = next
  }
  val shape = RoundedCornerShape(50)
  Row(
      modifier
          .background(Theme.surface.copy(alpha = 0.72f), shape)
          .border(1.dp, Theme.glassEdge, shape)
          .padding(8.dp)
          .onFocusChanged { focusedChange(it.hasFocus) },
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    for (tab in Tab.entries) {
      val (title, icon) = when (tab) {
        Tab.search -> loc(S.search) to Icons.search
        Tab.home -> loc(S.home) to null
        Tab.movies -> loc(S.movies) to null
        Tab.series -> loc(S.series) to null
        Tab.anime -> loc(S.anime) to null
        Tab.browse -> loc(S.browse) to null
        Tab.profile -> loc(S.profile) to Icons.person
        Tab.settings -> loc(S.settings) to Icons.settings
      }
      val iconOnly = icon != null && !roomy && tab != Tab.profile
      TabItem(
          title = title, icon = icon, iconOnly = iconOnly, selected = nav.tab == tab,
          modifier = if (tab == nav.tab) Modifier.focusRequester(chrome.bar) else Modifier,
          onFocus = { if (nav.tab != tab) pending = tab },
          onClick = { pending = null; nav.tab = tab },
      )
    }
  }
}

@Composable
private fun TabItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, iconOnly: Boolean, selected: Boolean, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  LaunchedEffect(focused) { if (focused) onFocus() }
  Surface(
      onClick = onClick,
      modifier = modifier,
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
      colors = ClickableSurfaceDefaults.colors(
          containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
          focusedContainerColor = Color.White,
          contentColor = if (selected) Theme.text1 else Theme.text2,
          focusedContentColor = Theme.focusInk,
      ),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
  ) {
    Row(Modifier.height(58.dp).padding(horizontal = if (iconOnly) 18.dp else 26.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (icon != null) Icon(icon, title, Modifier.size(26.dp))
      if (!iconOnly) Text(title, style = app(25, FontWeight.Bold), maxLines = 1)
    }
  }
}
