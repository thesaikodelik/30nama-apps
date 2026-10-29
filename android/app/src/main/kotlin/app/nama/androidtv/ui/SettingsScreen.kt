package app.nama.androidtv.ui

import app.nama.androidtv.data.SubtitlePosition
import app.nama.androidtv.data.Effects
import app.nama.androidtv.latinName
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import app.nama.androidtv.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.nama.androidtv.BuildConfig
import app.nama.androidtv.Updater
import app.nama.androidtv.player.Disk
import app.nama.androidtv.data.KeyCheck
import app.nama.androidtv.data.KeyServer
import app.nama.androidtv.data.KeyState
import app.nama.androidtv.data.Settings
import app.nama.androidtv.data.SubtitleBackground
import app.nama.androidtv.data.SubtitleColor
import app.nama.core.AudioMix
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The row in focus, as the column under the logo describes it. */
private data class Help(val text: String, val values: List<String> = emptyList(), val current: String? = null)

/**
 * Settings as the Mac's and the Apple TV's: the logo column at the start, and one list of groups
 * that scrolls beside it, so the remote only goes up and down and every setting is in sight without
 * knowing which section holds it. Each row changes in place by select (it steps to the next value,
 * or flips), so there are no dialogs. What the focused row does is written under the logo, where
 * the Mac's column is empty, so nothing moves as focus goes down the list.
 */
@Composable
fun SettingsScreen() {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val chrome = LocalChrome.current
  var help by remember { mutableStateOf<Help?>(null) }
  var editing by remember { mutableStateOf<String?>(null) }
  // A card closing gives focus back to the row that opened it; the rows were blocked meanwhile, so
  // focus would otherwise land wherever the page's restorer put it.
  val openers = remember { listOf("name", "tmdb", "subsource", "gemini").associateWith { FocusRequester() } }
  var lastEditing by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(editing) {
    if (editing != null) {
      lastEditing = editing
      return@LaunchedEffect
    }
    val was = lastEditing ?: return@LaunchedEffect
    lastEditing = null
    androidx.compose.runtime.withFrameNanos {}
    runCatching { openers[was]?.requestFocus() }
  }
  val s = app.settings
  val scroll = rememberScrollState()

  fun <T> next(value: T, values: List<T>): T = values[(values.indexOf(value) + 1) % values.size]
  // The bar steps out of the way once the list is scrolled, as on the other pages.
  LaunchedEffect(scroll.value == 0) { chrome.pageAtTop = scroll.value == 0 }
  DisposableEffect(Unit) { onDispose { chrome.pageAtTop = true } }
  FollowBar { scroll.animateScrollTo(0) }

  Ambient {
    // While a key or the name is being typed in its card, the rows behind it cannot take focus.
    Row(Modifier.fillMaxSize().blockFocus(editing != null).focusGroup().padding(start = Theme.edge, end = Theme.edge), horizontalArrangement = Arrangement.spacedBy(80.dp, Alignment.CenterHorizontally)) {
      // The Mac's logo column: the 30nama mark, the line that says what this app is, and under
      // them what the focused row does.
      Column(Modifier.width(360.dp).padding(top = 170.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.nama_logo), null, Modifier.width(265.dp))
        Text(loc(S.unofficial), style = app(17), color = Theme.text3, maxLines = 2, textAlign = TextAlign.Center)
        help?.let { Text(it.text, Modifier.padding(top = 40.dp), style = app(20).copy(lineHeight = app(20).fontSize * 1.5f), color = Theme.text2) }
      }
      Column(Modifier.width(980.dp).fillMaxHeight().verticalScroll(scroll).padding(top = 150.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
        Group(loc(S.account)) {
          val user = app.user
          Fact(loc(S.account), user?.name ?: loc(S.guestAccount))
          user?.type?.let { Fact(loc(S.accountType), loc.accountType(it)) }
          // Under a week left, in red, as a nudge to renew.
          user?.let { loc.subscriptionLine(it) }?.let { Fact(loc(S.expires), it, if ((user.daysLeft(java.time.Instant.now()) ?: 99) < 7) Theme.accent else Theme.text3) }
          if (user != null) {
            // For a private profile on the site, which will not give the name: written here (the Mac's).
            Choice("${loc(S.yourName)} (${loc(S.optional)})", app.ownName ?: tv(TV.keyNotSet), Help(tv(TV.yourNameHint)), { help = it }, Modifier.focusRequester(openers.getValue("name"))) { editing = "name" }
            Choice(loc(S.signOut), null, Help(loc(S.signOutBody)), { help = it }) {
              app.sheets.show(SheetSpec(loc(S.signOutTitle), listOf(SheetAction(loc(S.cancel)) {}, SheetAction(loc(S.signOut), Icons.signOut) { app.signOut() }), loc(S.signOutBody)))
            }
          } else Choice(loc(S.signInQR), null, Help(loc(S.signInHint)), { help = it }) { app.requestSignIn() }
        }
        Group(loc(S.language)) {
          val langs = Lang.entries.map { it.nativeName }
          // A new language builds every page anew; without this, focus came back on the first row
          // (Sign in, for a guest), and a second press there opened the sign-in.
          val languageRow = remember { FocusRequester() }
          LaunchedEffect(Unit) {
            if (!app.refocusLanguage) return@LaunchedEffect
            app.refocusLanguage = false
            androidx.compose.runtime.withFrameNanos {}
            runCatching { languageRow.requestFocus() }
          }
          Choice(loc(S.language), s.lang.nativeName, Help(tv(TV.languageHint), langs, s.lang.nativeName), { help = it }, Modifier.focusRequester(languageRow)) {
            app.refocusLanguage = true
            app.update { it.copy(lang = next(it.lang, Lang.entries.toList())) }
          }
        }
        Group(loc(S.playback)) {
          // Best (the default), Automatic, Max: the download file itself (the Tizen app's), and for a
          // slow line a fixed quality no higher than 1080p, 720p or 480p (the aliir74 app's).
          val qualities = listOf(loc(S.qualityBest), loc(S.qualityAuto), tv(TV.qualityMax)) + Settings.startCaps.map { "${it}p" }
          val qualityAt = when {
            s.maxQuality -> 2
            s.startCap > 0 -> 3 + Settings.startCaps.indexOf(s.startCap)
            s.preferAdaptive -> 1
            else -> 0
          }
          Choice(loc(S.quality), qualities[qualityAt], Help(tv(TV.qualityHint) + "\n\n" + tv(TV.qualityMaxHint) + "\n\n" + tv(TV.qualityCapHint), qualities, qualities[qualityAt]), { help = it }) {
            app.update {
              when (val to = (qualityAt + 1) % qualities.size) {
                0 -> it.copy(preferAdaptive = false, maxQuality = false, startCap = 0)
                1 -> it.copy(preferAdaptive = true, maxQuality = false, startCap = 0)
                2 -> it.copy(preferAdaptive = false, maxQuality = true, startCap = 0)
                else -> it.copy(preferAdaptive = false, maxQuality = false, startCap = Settings.startCaps[to - 3])
              }
            }
          }
          val mixes = listOf(AudioMix.stereo to loc(S.audioStereo), AudioMix.surround to loc(S.audioSurround), AudioMix.original to loc(S.audioOriginal))
          val mixName = mixes.first { it.first == s.audioMix }.second
          Choice(loc(S.audio), mixName, Help(tv(TV.audioHint), mixes.map { it.second }, mixName), { help = it }) {
            app.update { it.copy(audioMix = next(it.audioMix, mixes.map { m -> m.first })) }
          }
          Toggle(loc(S.autoplay), s.autoplayNext, tv(TV.autoplayHint), { help = it }) { app.update { it.copy(autoplayNext = !it.autoplayNext) } }
          Toggle(loc(S.autoSkip), s.autoSkip, tv(TV.autoSkipHint), { help = it }) { app.update { it.copy(autoSkip = !it.autoSkip) } }
          val ahead = Settings.readAheadChoices.map { if (it == 0) loc(S.off) else loc.num("$it%") }
          val aheadName = ahead[Settings.readAheadChoices.indexOf(s.readAhead)]
          Choice(tv(TV.readAhead), aheadName, Help(tv(TV.readAheadHint), ahead, aheadName), { help = it }) {
            app.update { it.copy(readAhead = next(it.readAhead, Settings.readAheadChoices)) }
          }
          // The Mac's choice for the room around the picture.
          val around = listOf(loc(S.sceneGlow), loc(S.blackBars))
          val aroundName = around[if (s.sceneGlow) 0 else 1]
          Choice(loc(S.aroundPicture), aroundName, Help(tv(TV.aroundHint), around, aroundName), { help = it }) { app.update { it.copy(sceneGlow = !it.sceneGlow) } }
          Toggle(tv(TV.matchFrameRate), s.matchFrameRate, tv(TV.matchFrameRateHint), { help = it }) { app.update { it.copy(matchFrameRate = !it.matchFrameRate) } }
        }
        Group(loc(S.subtitles)) {
          val langs = listOf("fa" to loc(S.persian), "en" to loc(S.english), "off" to loc(S.off))
          val langName = langs.firstOrNull { it.first == s.defaultSubtitle }?.second ?: loc(S.off)
          val hint = tv(TV.subtitlesHint)
          val subHelp = { values: List<String>, current: String -> Help(hint, values, current) }
          Choice(loc(S.defaultSubs), langName, subHelp(langs.map { it.second }, langName), { help = it }) {
            app.update { it.copy(defaultSubtitle = next(it.defaultSubtitle, langs.map { l -> l.first })) }
          }
          val sizes = listOf(loc(S.small), loc(S.medium), loc(S.large))
          val sizeName = sizes[Settings.subtitleSizes.indexOf(s.subtitleSize).coerceAtLeast(0)]
          Choice(loc(S.subSize), sizeName, subHelp(sizes, sizeName), { help = it }) { app.update { it.copy(subtitleSize = next(it.subtitleSize, Settings.subtitleSizes)) } }
          val colors = SubtitleColor.entries.map { loc(it.label) }
          Choice(loc(S.subColor), loc(s.subtitleColor.label), subHelp(colors, loc(s.subtitleColor.label)), { help = it }) {
            app.update { it.copy(subtitleColor = next(it.subtitleColor, SubtitleColor.entries.toList())) }
          }
          val backs = SubtitleBackground.entries.map { loc(it.label) }
          Choice(loc(S.subBackground), loc(s.subtitleBackground.label), subHelp(backs, loc(s.subtitleBackground.label)), { help = it }) {
            app.update { it.copy(subtitleBackground = next(it.subtitleBackground, SubtitleBackground.entries.toList())) }
          }
          val places = listOf(SubtitlePosition.bottom to tv(TV.positionBottom), SubtitlePosition.middle to tv(TV.positionMiddle), SubtitlePosition.top to tv(TV.positionTop))
          val placeName = places.first { it.first == s.subtitlePosition }.second
          Choice(tv(TV.subtitlePosition), placeName, Help(tv(TV.subtitlePositionHint), places.map { it.second }, placeName), { help = it }) {
            app.update { it.copy(subtitlePosition = next(it.subtitlePosition, places.map { p -> p.first })) }
          }
          // As the Mac's: the sample in the group, under the rows it shows.
          Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) { SubtitleSample(s) }
        }
        Group(tv(TV.sectionApp)) {
          val context = LocalContext.current
          val systemCalm = remember { systemAnimationsOff(context) }
          val motionHint = tv(TV.motionHint) + if (systemCalm) "\n\n" + tv(TV.motionOffBySystem) else ""
          // The Mac's Effects, before Reduce motion, which turns every one of them off whatever this says.
          val effectNames = listOf(tv(TV.effectsAuto), loc(S.effectsFull), tv(TV.effectsLight))
          val effectName = effectNames[s.effects.ordinal]
          Choice(loc(S.effects), effectName, Help(tv(TV.effectsHint), effectNames, effectName), { help = it }) {
            app.update { it.copy(effects = next(it.effects, Effects.entries.toList())) }
          }
          Toggle(loc(S.reduceMotion), s.reduceMotion, motionHint, { help = it }) { app.update { it.copy(reduceMotion = !it.reduceMotion) } }
          val scope = rememberCoroutineScope()
          val cleared = tv(TV.cleared)
          // What the pictures, the episode stills and the read-ahead video take on the TV.
          var used by remember { mutableStateOf<Long?>(null) }
          LaunchedEffect(Unit) { used = withContext(Dispatchers.IO) { cacheBytes(context) } }
          Choice(tv(TV.clearCaches), used?.let { "${loc.num((it / (1L shl 20)).toInt())} ${tv(TV.megabytes)}" }, Help(tv(TV.clearCaches)), { help = it }) {
            scope.launch {
              used = withContext(Dispatchers.IO) {
                listOf("images", "frames").forEach { context.cacheDir.resolve(it).deleteRecursively() }
                // The video cache stays open while the app runs: emptied entry by entry, not deleted.
                Disk.cache(context)?.let { c -> c.keys.toList().forEach { k -> runCatching { c.removeResource(k) } } }
                cacheBytes(context)
              }
              app.showToast(cleared)
            }
          }
        }
        Group(loc(S.services)) {
          KeyRow("tmdb", loc(S.tmdbKey), loc(S.tmdbKeyHint), { help = it }, Modifier.focusRequester(openers.getValue("tmdb"))) { editing = "tmdb" }
          KeyRow("subsource", loc(S.subsourceKey), loc(S.subsourceKeyHint), { help = it }, Modifier.focusRequester(openers.getValue("subsource"))) { editing = "subsource" }
          KeyRow("gemini", tv(TV.geminiKey), tv(TV.geminiKeyHint), { help = it }, Modifier.focusRequester(openers.getValue("gemini"))) { editing = "gemini" }
        }
        Group(loc(S.about)) {
          Fact(loc(S.version), BuildConfig.VERSION_NAME)
          // The Mac's report: a new issue on GitHub with what a report needs filled in, as a code.
          val reportHint = tv(TV.reportHint)
          Choice(loc(S.reportProblem), null, Help(reportHint), { help = it }) {
            val body = "**What happened / چه اتفاقی افتاد:**\n\n\n---\n30nama for Android TV ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${loc.lang.name}"
            app.sheets.link = LinkSpec(loc(S.reportProblem), reportHint, "https://github.com/thesaikodelik/30nama-apps/issues/new?body=" + java.net.URLEncoder.encode(body, "UTF-8").replace("+", "%20"))
          }
          val updater = app.updater
          val (label, value) = updateRow(updater.state, updater.current)
          val installing = updater.state is Updater.State.Installing || updater.state is Updater.State.NeedsPermission
          Choice(label, value.ifEmpty { null }, Help(tv(if (installing) TV.installHint else TV.updateHint)), { help = it }) {
            when (val st = updater.state) {
              is Updater.State.Available -> updater.install(st.release)
              is Updater.State.Failed -> updater.showFailure = true
              Updater.State.Idle, Updater.State.UpToDate -> updater.check()
              else -> Unit
            }
          }
          Fact(loc(S.apiHost), app.apiHost ?: "—")
          Choice(tv(TV.licenses), "Vazirmatn · OFL", Help(loc(S.unofficial)), { help = it }) {}
        }
      }
    }
  }
  // The bar is outside the page, so the page's own block does not reach it.
  DisposableEffect(editing) {
    chrome.modal = editing != null
    onDispose { chrome.modal = false }
  }
  editing?.let { service -> if (service == "name") NameCard { editing = null } else KeyCard(service) { editing = null } }
}

/** The Mac's group: its name small above, the rows in one pane of glass. */
@Composable
private fun Group(name: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  // A section: a row in focus brings the group's name with it while both fit.
  Column(Modifier.wholeSection(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(name, Modifier.padding(start = 10.dp), style = app(18, FontWeight.SemiBold), color = Theme.text3)
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(21.dp), fill = 0.03f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
  }
}

/**
 * A setting that changes in place when pressed, as the Mac's row with a menu: the name at the start,
 * the value in a flat capsule (the Mac's menu control, not glass) with an up-down mark at the end. In focus the row lights, as the
 * Mac's under the pointer, and the capsule turns white.
 */
@Composable
private fun Choice(label: String, value: String?, help: Help, show: (Help) -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Row(label, help, show, modifier, onClick) { focused ->
    value?.let {
      val shape = RoundedCornerShape(50)
      Row(
          Modifier.width(390.dp).height(55.dp).then(if (focused) Modifier.background(Color.White, shape) else Modifier.background(Color.White.copy(alpha = 0.07f), shape).border(1.dp, Color.White.copy(alpha = 0.10f), shape)).padding(horizontal = 22.dp),
          verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(it, Modifier.weight(1f), style = app(20, FontWeight.Medium), color = if (focused) Theme.focusInk else Theme.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.upDown, null, Modifier.size(20.dp), tint = if (focused) Theme.focusInk.copy(alpha = 0.6f) else Theme.text3)
      }
    }
  }
}

/** A switch, as the Mac's: red when on, grey when off. */
@Composable
private fun Toggle(label: String, on: Boolean, hint: String, show: (Help) -> Unit, flip: () -> Unit) {
  val loc = LocalLoc.current
  Row(label, Help(hint, listOf(loc(S.on), loc(S.off)), loc(if (on) S.on else S.off)), show, Modifier, flip) { focused ->
    val knob by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, Theme.focusSpring(), label = "switch")
    Box(
        Modifier.size(60.dp, 34.dp).background(if (on) Theme.accent else Color.White.copy(alpha = if (focused) 0.35f else 0.2f), RoundedCornerShape(50))
            .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = if (focused) 0.9f else 0f), RoundedCornerShape(50)).padding(3.dp),
    ) {
      // From the start edge when off, to the end when on.
      Box(Modifier.align(androidx.compose.ui.BiasAlignment((knob * 2 - 1), 0f)).size(28.dp).background(Color.White, CircleShape))
    }
  }
}

/** One row of a group: the name, and what [trailing] draws at the end. */
@Composable
private fun Row(label: String, help: Help, show: (Help) -> Unit, modifier: Modifier, onClick: () -> Unit, trailing: @Composable (Boolean) -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  LaunchedEffect(focused, help) { if (focused) show(help) }
  Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp).focusRing(interaction, RoundedCornerShape(16.dp)),
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
      colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.1f), contentColor = Theme.text1, focusedContentColor = Theme.text1),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
  ) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(label, Modifier.weight(1f), style = app(20, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
      trailing(focused)
    }
  }
}

@Composable
private fun Fact(label: String, value: String, color: Color = Theme.text3) {
  androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = app(20, FontWeight.Medium), color = Theme.text1)
    Text(value, style = app(20), color = color, maxLines = 1)
  }
}

@Composable
private fun KeyRow(service: String, label: String, hint: String, show: (Help) -> Unit, modifier: Modifier = Modifier, open: () -> Unit) {
  val app = LocalApp.current
  // Only its last four characters, never the key.
  val value = app.key(service)?.let { "•••• " + it.takeLast(4) } ?: tv(TV.keyNotSet)
  Choice(label, value, Help(hint), show, modifier, open)
}

/** A sample line at its real size over a bright, busy picture, where the choice of background matters. */
@Composable
private fun SubtitleSample(s: Settings) {
  val loc = LocalLoc.current
  val size = s.subtitleSize
  val style = app(size, FontWeight.Medium).copy(textAlign = TextAlign.Center)
  Box(
      Modifier.fillMaxWidth().height(170.dp).background(
          Brush.linearGradient(listOf(Color(0xFFF2A65A), Color(0xFFD9567B), Color(0xFF5B6CFF), Color(0xFF3FC1C9))), RoundedCornerShape(Theme.tileRadius)),
      contentAlignment = Alignment.Center,
  ) {
    Box(
        Modifier.then(if (s.subtitleBackground.plate > 0) Modifier.background(Color.Black.copy(alpha = s.subtitleBackground.plate), RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 24.dp, vertical = 8.dp)) {
      if (s.subtitleBackground.plate == 0f) Text(loc(S.sample), style = style.copy(drawStyle = Stroke(size * 0.12f)), color = Color.Black, maxLines = 1)
      Text(loc(S.sample), style = style, color = s.subtitleColor.color, maxLines = 1)
    }
  }
}

/**
 * A service key: typed with the remote (the system keyboard opens on the field), or sent from a
 * phone through the QR code. Saved at once, then tried.
 */
@Composable
private fun KeyCard(service: String, close: () -> Unit) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val scope = rememberCoroutineScope()
  var text by remember { mutableStateOf(app.key(service).orEmpty()) }
  var state by remember { mutableStateOf(if (app.key(service) == null) KeyState.Unset else KeyState.Works) }
  val field = remember { FocusRequester() }
  val fieldButton = remember { FocusRequester() }
  var typing by remember { mutableStateOf(false) }
  val checker = remember { KeyCheck(app.http) }
  var checking by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

  fun save(key: String) {
    app.setKey(service, key)
    text = key
    if (key.isBlank()) {
      state = KeyState.Unset
      return
    }
    state = KeyState.Checking
    // Only the newest key's answer: a slow one for the key before must not overwrite it.
    checking?.cancel()
    checking = scope.launch { state = checker.check(service, key) }
  }

  // The page the phone opens: in the app's language, right to left for Persian, nothing fetched from anywhere.
  val lang = loc.lang
  val server = remember {
    KeyServer.open(page = { sent, error -> keyPage(lang, serviceName(service, lang), sent, error) }) { key ->
      scope.launch { save(key) }
    }
  }
  DisposableEffect(server) { onDispose { server?.close() } }
  // Getting a key takes a few minutes on the phone; the screensaver would otherwise end it.
  val view = androidx.compose.ui.platform.LocalView.current
  DisposableEffect(Unit) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
  BackHandler {
    if (typing) typing = false else close()
  }
  LaunchedEffect(Unit) { runCatching { fieldButton.requestFocus() } }
  val address = server?.address ?: when (service) {
    "tmdb" -> "https://www.themoviedb.org/settings/api"
    "gemini" -> "https://aistudio.google.com/apikey"
    else -> "https://subsource.net/profile"
  }
  val qr = remember(address) { qrImage(address) }
  val shape = RoundedCornerShape(36.dp)

  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
    Row(Modifier.width(1500.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(56.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(serviceName(service, loc.lang), style = app(40, FontWeight.Bold), color = Theme.text1)
        Text(when (service) {
          "tmdb" -> loc(S.tmdbKeyHint)
          "gemini" -> tv(TV.geminiKeyHint)
          else -> loc(S.subsourceKeyHint)
        }, style = app(24).copy(lineHeight = app(24).fontSize * 1.5f), color = Theme.text2)
        // Keys are Latin letters and digits: read left to right, in a face where 0 and O differ. The
        // field is a button until pressed, so moving past it does not throw the system keyboard up.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          val shapeField = RoundedCornerShape(20.dp)
          val keyStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = app(26).fontSize, color = Theme.text1)
          if (typing) {
            BasicTextField(
                text, { text = it.filter { c -> c.code in 0x21..0x7E } },
                Modifier.fillMaxWidth().focusRequester(field).background(Theme.glass, shapeField).border(2.dp, Theme.accent, shapeField).padding(horizontal = 26.dp, vertical = 20.dp),
                textStyle = keyStyle,
                singleLine = true,
                cursorBrush = SolidColor(Theme.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                  typing = false
                  save(text.trim())
                }),
            )
            LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
          } else {
            val fieldInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = { typing = true },
                modifier = Modifier.fillMaxWidth().focusRequester(fieldButton).focusRing(fieldInteraction, shapeField),
                interactionSource = fieldInteraction,
                shape = ClickableSurfaceDefaults.shape(shapeField),
                colors = ClickableSurfaceDefaults.colors(containerColor = Theme.glass, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
              Text(
                  text.ifEmpty { tv(TV.pasteKey) }, Modifier.padding(horizontal = 26.dp, vertical = 20.dp),
                  style = if (text.isEmpty()) app(24) else keyStyle.copy(color = Color.Unspecified), maxLines = 1, overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          PillButton(tv(TV.save), { save(text.trim()) }, kind = PillKind.Primary, icon = Icons.check)
          PillButton(loc(S.removeKey), { save("") }, icon = Icons.close)
        }
        val status = when (state) {
          KeyState.Unset -> tv(TV.keyNotSet)
          KeyState.Checking -> tv(TV.keyChecking)
          KeyState.Works -> tv(TV.keyWorks)
          KeyState.Rejected -> tv(TV.keyRejected)
          KeyState.Unreachable -> tv(TV.keyUnreachable)
        }
        Text(status, style = app(26, FontWeight.SemiBold), color = when (state) {
          KeyState.Works -> Theme.good
          KeyState.Rejected -> Theme.accent
          else -> Theme.text2
        })
      }
      Column(Modifier.width(380.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(340.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(24.dp)) {
          Image(qr, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        }
        Text(tv(if (server != null) TV.keyFromPhone else TV.keyFromSite), style = app(22), color = Theme.text2, textAlign = TextAlign.Center)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(address, style = app(18), color = Theme.text3, maxLines = 2) }
      }
    }
  }
}

/** The member's own name, typed with the remote: English letters only, saved at once. */
@Composable
private fun NameCard(close: () -> Unit) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  var text by remember { mutableStateOf(app.ownName.orEmpty()) }
  val field = remember { FocusRequester() }
  val fieldButton = remember { FocusRequester() }
  var typing by remember { mutableStateOf(false) }
  BackHandler { if (typing) typing = false else close() }
  LaunchedEffect(Unit) { runCatching { fieldButton.requestFocus() } }
  fun save(name: String) {
    app.saveOwnName(name)
    close()
  }
  val shape = RoundedCornerShape(36.dp)
  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
    Column(Modifier.width(1000.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(56.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
      Text(loc(S.yourName), style = app(40, FontWeight.Bold), color = Theme.text1)
      Text(tv(TV.yourNameHint), style = app(24).copy(lineHeight = app(24).fontSize * 1.5f), color = Theme.text2)
      // English letters, left to right whatever the interface reads in.
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val shapeField = RoundedCornerShape(20.dp)
        val nameStyle = app(28, FontWeight.SemiBold).copy(color = Theme.text1)
        if (typing) {
          BasicTextField(
              text, { text = latinName(it) },
              Modifier.fillMaxWidth().focusRequester(field).background(Theme.glass, shapeField).border(2.dp, Theme.accent, shapeField).padding(horizontal = 26.dp, vertical = 20.dp),
              textStyle = nameStyle,
              singleLine = true,
              cursorBrush = SolidColor(Theme.accent),
              keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                  capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                  keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                  imeAction = androidx.compose.ui.text.input.ImeAction.Done,
              ),
              keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { typing = false }),
          )
          LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
        } else {
          val fieldInteraction = remember { MutableInteractionSource() }
          Surface(
              onClick = { typing = true },
              modifier = Modifier.fillMaxWidth().focusRequester(fieldButton).focusRing(fieldInteraction, shapeField),
              interactionSource = fieldInteraction,
              shape = ClickableSurfaceDefaults.shape(shapeField),
              colors = ClickableSurfaceDefaults.colors(containerColor = Theme.glass, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
              scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
          ) {
            Text(
                text.ifEmpty { tv(TV.typeName) }, Modifier.padding(horizontal = 26.dp, vertical = 20.dp),
                style = if (text.isEmpty()) app(24) else nameStyle.copy(color = Color.Unspecified), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PillButton(tv(TV.save), { save(text.trim()) }, kind = PillKind.Primary, icon = Icons.check)
        PillButton(loc(S.removeKey), { save("") }, icon = Icons.close)
      }
    }
  }
}

private fun serviceName(service: String, lang: Lang): String = when (service) {
  "tmdb" -> Loc(lang)(S.tmdbKey)
  "gemini" -> TV.geminiKey(lang)
  else -> Loc(lang)(S.subsourceKey)
}

/** The one page the key server serves: a field, a button, and nothing loaded from anywhere else. */
private fun keyPage(lang: Lang, title: String, sent: Boolean, error: Boolean): String {
  fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
  val dir = if (lang.isRTL) "rtl" else "ltr"
  val body = if (sent) "<p class=done>${esc(TV.keySent(lang))}</p>"
  else """
    <form method=post>
      <input name=key autocomplete=off autocapitalize=off autocorrect=off spellcheck=false dir=ltr placeholder="${esc(TV.pasteKey(lang))}" required>
      ${if (error) "<p class=err>${esc(TV.keyLooksWrong(lang))}</p>" else ""}
      <button>${esc(TV.sendToTv(lang))}</button>
    </form>"""
  return """<!doctype html><html lang=${lang.name} dir=$dir><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>30nama</title>
<style>body{margin:0;background:#07080b;color:#f4f1ea;font:18px system-ui,sans-serif;display:flex;justify-content:center}main{width:100%;max-width:440px;padding:32px 20px}
h1{font-size:24px}input{width:100%;box-sizing:border-box;height:56px;border-radius:28px;border:1px solid #333;background:#161922;color:#fff;padding:0 20px;font:18px ui-monospace,monospace}
button{margin-top:16px;width:100%;height:56px;border:0;border-radius:28px;background:#f0454a;color:#fff;font-size:18px;font-weight:600}.err{color:#f0454a}.done{color:#62d39b;font-size:20px}</style></head>
<body><main><h1><span style="color:#f0454a">30</span>nama · ${esc(title)}</h1>$body</main></body></html>"""
}

/** Bytes in the app's caches: pictures, episode stills and read-ahead video. */
private fun cacheBytes(context: android.content.Context): Long =
    listOf("images", "frames", "video").sumOf { d -> context.cacheDir.resolve(d).walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
