package app.nama.androidtv.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Text
import app.nama.androidtv.Updater
import app.nama.core.S

/** The row's label and value in Settings › About, for each step of an update. */
@Composable
fun updateRow(state: Updater.State, current: String): Pair<String, String> {
  val loc = LocalLoc.current
  return when (state) {
    // The version is the row above; nothing to add until there is news.
    Updater.State.Idle -> loc(S.checkForUpdate) to ""
    Updater.State.Checking -> loc(S.checkForUpdate) to "…"
    Updater.State.UpToDate -> loc(S.checkForUpdate) to loc(S.upToDate)
    is Updater.State.Available -> "${loc(S.newVersion)} ${state.release.version}" to loc(S.installUpdate)
    is Updater.State.Downloading -> loc(S.updating) to if (state.progress < 0) "…" else "${(state.progress * 100).toInt()}%"
    is Updater.State.NeedsPermission -> tv(TV.allowInstalls) to state.release.version
    is Updater.State.Installing -> tv(TV.installing) to state.release.version
    is Updater.State.Failed -> loc(S.updateFailed) to loc(S.downloadPage)
  }
}

/**
 * When updating from inside the app went wrong: the download page as a QR code for a phone, since a
 * TV seldom has a browser, and a word on where the cause was written.
 */
@Composable
fun UpdateFailedCard(page: String, step: String, blocked: Boolean, retry: () -> Unit, close: () -> Unit) {
  val loc = LocalLoc.current
  val qr = remember(page) { qrImage(page) }
  val shape = RoundedCornerShape(36.dp)
  val first = remember { FocusRequester() }
  BackHandler(onBack = close)
  LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
    Row(Modifier.width(1300.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(56.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(loc(S.updateFailed), style = app(40, FontWeight.Bold), color = Theme.text1)
        Text(tv(if (blocked) TV.installsNotAllowed else TV.updateFailedHint), style = app(26).copy(lineHeight = app(26).fontSize * 1.5f), color = Theme.text2)
        // The step in the log's own words: a TV's update.log is out of reach without adb, a photo is not.
        if (step.isNotEmpty()) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(step, style = app(18), color = Theme.text3, maxLines = 3)
          }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          PillButton(tv(TV.retry), retry, Modifier.focusRequester(first), kind = PillKind.Primary)
          PillButton(loc(S.later), close)
        }
      }
      Column(Modifier.width(380.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(340.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(24.dp)) {
          Image(qr, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        }
        Text(loc(S.downloadPage), style = app(22), color = Theme.text2, textAlign = TextAlign.Center)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(page, style = app(18), color = Theme.text3, maxLines = 2) }
      }
    }
  }
}

/**
 * Before the first update, Android's one question: may 30nama install apps. The card says why and
 * opens the nearest settings screen the TV has; the paths below are for a TV whose screen opens
 * elsewhere, in English as TVs show them. Back with the switch on, the update goes on by itself.
 */
@Composable
fun AllowInstallsCard(open: () -> Unit, later: () -> Unit) {
  val loc = LocalLoc.current
  val shape = RoundedCornerShape(36.dp)
  val first = remember { FocusRequester() }
  BackHandler(onBack = later)
  LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
    Column(Modifier.width(1300.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(56.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
      Text(tv(TV.allowInstalls), style = app(40, FontWeight.Bold), color = Theme.text1)
      Text(tv(TV.allowBody), style = app(26).copy(lineHeight = app(26).fontSize * 1.5f), color = Theme.text2)
      Row(Modifier.padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PillButton(tv(TV.openSettings), open, Modifier.focusRequester(first), kind = PillKind.Primary)
        PillButton(loc(S.later), later)
      }
      Text(tv(TV.allowWhere), style = app(22), color = Theme.text3)
      SettingsPath(listOf("Settings", "Privacy", "Security & restrictions", "Unknown sources", "30nama"))
      Text(tv(TV.allowOr), style = app(22), color = Theme.text3)
      SettingsPath(listOf("Settings", "Apps", "Security & restrictions", "Unknown sources", "30nama"))
    }
  }
}

/** A path through the TV's own settings, as chips, read left to right as the TV's menus are. */
@Composable
private fun SettingsPath(steps: List<String>) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      steps.forEachIndexed { i, step ->
        if (i > 0) Text("›", style = app(24), color = Theme.text3)
        val last = i == steps.lastIndex
        Text(
            step,
            Modifier.background(if (last) Theme.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp),
            style = app(22, if (last) FontWeight.SemiBold else FontWeight.Normal),
            color = Theme.text1,
        )
      }
    }
  }
}

/**
 * Where the Mac and Windows open a page in the browser (renewing, a report, IMDb), the TV shows
 * its QR code to open on the phone, with the address under it.
 */
@Composable
fun LinkCard(spec: LinkSpec, close: () -> Unit) {
  val loc = LocalLoc.current
  val qr = remember(spec.url) { qrImage(spec.url) }
  val shape = RoundedCornerShape(36.dp)
  val first = remember { FocusRequester() }
  BackHandler(onBack = close)
  LaunchedEffect(spec) { runCatching { first.requestFocus() } }
  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
    Row(Modifier.width(1300.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(56.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(spec.title, style = app(40, FontWeight.Bold), color = Theme.text1)
        Text(spec.body, style = app(26).copy(lineHeight = app(26).fontSize * 1.5f), color = Theme.text2)
        Row(Modifier.padding(top = 12.dp)) { PillButton(loc(S.close), close, Modifier.focusRequester(first), kind = PillKind.Primary) }
      }
      Column(Modifier.width(380.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(340.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(24.dp)) {
          Image(qr, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Text(spec.url.substringBefore('?'), style = app(18), color = Theme.text3, maxLines = 2)
        }
      }
    }
  }
}

/** A long text to read whole: its heading, its text, and whether it reads left to right. */
data class ReadSpec(val title: String, val text: String, val ltr: Boolean = false)

/**
 * A text too long for its card, whole (the Mac opens a review on TMDB): up and down scroll it a
 * screenful at a time, Back closes it.
 */
@Composable
fun ReadCard(spec: ReadSpec, close: () -> Unit) {
  val scroll = androidx.compose.foundation.rememberScrollState()
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val focus = remember { FocusRequester() }
  val shape = RoundedCornerShape(36.dp)
  BackHandler(onBack = close)
  LaunchedEffect(spec) { runCatching { focus.requestFocus() } }
  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
    CompositionLocalProvider(LocalLayoutDirection provides if (spec.ltr) LayoutDirection.Ltr else LocalLayoutDirection.current) {
      Column(
          Modifier.width(1300.dp).heightIn(max = 900.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape)
              .focusRequester(focus).focusable()
              .onPreviewKeyEvent { e ->
                if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (e.key) {
                  androidx.compose.ui.input.key.Key.DirectionDown -> 420f
                  androidx.compose.ui.input.key.Key.DirectionUp -> -420f
                  else -> return@onPreviewKeyEvent e.key == androidx.compose.ui.input.key.Key.DirectionLeft || e.key == androidx.compose.ui.input.key.Key.DirectionRight
                }
                scope.launch { scroll.animateScrollBy(step) }
                true
              }
              .verticalScroll(scroll).padding(56.dp),
          verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        Text(spec.title, style = app(38, FontWeight.Bold), color = Theme.text1)
        Text(spec.text, style = app(27).copy(lineHeight = app(27).fontSize * 1.6f), color = Theme.text2)
      }
    }
  }
}

/**
 * The Mac's update capsule (`UpdatePill`): a newer version, told in a glass capsule that drops from
 * under the bar, with Install and Later. While it downloads it fills with 30nama's red from the
 * side a line starts on, like a glass being poured; then Android's installer asks. Down from the
 * bar reaches its buttons; Up goes back to the bar and Down on into the page.
 */
@Composable
fun UpdatePill(updater: Updater, focus: FocusRequester, up: () -> Unit, down: () -> Boolean, modifier: Modifier = Modifier) {
  val loc = LocalLoc.current
  val state = updater.state
  val release = when (state) {
    is Updater.State.Available -> state.release.takeIf { !updater.postponed }
    is Updater.State.Downloading -> state.release
    is Updater.State.NeedsPermission -> state.release
    is Updater.State.Installing -> state.release
    else -> null
  }
  val failed = state is Updater.State.Failed && !updater.postponed
  val fill = when (state) {
    is Updater.State.Downloading -> state.progress.coerceAtLeast(0f)
    is Updater.State.NeedsPermission, is Updater.State.Installing -> 1f
    else -> 0f
  }
  val level by androidx.compose.animation.core.animateFloatAsState(fill, androidx.compose.animation.core.tween(400), label = "pour")
  androidx.compose.animation.AnimatedVisibility(
      visible = release != null || failed,
      modifier = modifier,
      // Out from behind the bar, and back behind it when it goes.
      enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)) +
          androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 120f)) { -it },
      exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it },
  ) {
    val shape = RoundedCornerShape(50)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val message = when (state) {
      is Updater.State.Downloading -> loc(S.updating) + if (state.progress >= 0) " ${loc.num((state.progress * 100).toInt())}${if (loc.lang == app.nama.core.Lang.en) "%" else "٪"}" else ""
      is Updater.State.NeedsPermission -> tv(TV.allowInstalls)
      // Android's window sits over the page with Cancel in focus; the capsule above it says which to press.
      is Updater.State.Installing -> tv(TV.pressUpdate)
      is Updater.State.Failed -> loc(S.updateFailed)
      else -> "${loc(S.newVersion)} ${release?.version?.let { loc.num(it) } ?: ""}"
    }
    val icon = when (state) {
      is Updater.State.Failed -> Icons.close
      is Updater.State.Installing, is Updater.State.NeedsPermission -> Icons.check
      else -> Icons.download
    }
    val hasButtons = state is Updater.State.Available || failed
    // The buttons going while one had focus (Install pressed): focus back to the bar, not lost.
    var holds by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(hasButtons) { if (!hasButtons && holds) up() }
    Row(
        Modifier
            .onFocusChanged { holds = it.hasFocus }
            .onPreviewKeyEvent { e ->
              if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
              when (e.key) {
                androidx.compose.ui.input.key.Key.DirectionUp -> { up(); true }
                androidx.compose.ui.input.key.Key.DirectionDown -> down()
                else -> false
              }
            }
            .shadowGlow(level)
            .clip(shape)
            .drawBehind {
              // The red rising from the side a line starts on.
              val w = size.width * level
              if (w > 0f) drawRect(
                  androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Theme.accentLight, Theme.accent, Theme.accentDeep)),
                  topLeft = androidx.compose.ui.geometry.Offset(if (rtl) size.width - w else 0f, 0f),
                  size = androidx.compose.ui.geometry.Size(w, size.height),
              )
            }
            .glass(shape)
            .padding(start = 26.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      androidx.tv.material3.Icon(icon, null, Modifier.size(28.dp), tint = if (level > 0f) Color.White else Theme.accent)
      Text(message, style = app(24, FontWeight.SemiBold), color = Theme.text1, maxLines = 1)
      when {
        state is Updater.State.Available -> {
          PillButton(loc(S.installUpdate), { updater.install(state.release) }, Modifier.focusRequester(focus), kind = PillKind.Primary)
          PillButton("", { updater.postpone(); up() }, Modifier.semantics { contentDescription = loc(S.later) }, kind = PillKind.Chip, icon = Icons.close)
        }
        failed -> {
          PillButton(loc(S.downloadPage), { updater.showFailure = true }, Modifier.focusRequester(focus), kind = PillKind.Primary)
          PillButton("", { updater.postpone(); up() }, Modifier.semantics { contentDescription = loc(S.later) }, kind = PillKind.Chip, icon = Icons.close)
        }
        else -> Box(Modifier.size(12.dp, 60.dp))
      }
    }
  }
}

/** The capsule's glow, in the red it fills with. */
private fun Modifier.shadowGlow(level: Float): Modifier =
    if (level <= 0f) this else this.colouredGlow(Theme.accent, level * 0.45f, 28.dp, RoundedCornerShape(50), y = 6.dp)

/** Whether the capsule is up with buttons to reach, for Down from the bar. */
fun Updater.pillHasButtons(): Boolean = (state is Updater.State.Available || state is Updater.State.Failed) && !postponed
