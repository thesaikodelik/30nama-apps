package app.nama.androidtv.ui

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

private enum class Section { Account, Playback, Subtitles, App, Services, About }

/** The row in focus, as the panel at the end describes it: what it does, and every value it can take. */
private data class Help(val text: String, val values: List<String> = emptyList(), val current: String? = null)

/**
 * Settings in three columns, after 30namaTV and the Apple TV: the sections; the rows of the one in
 * focus, each changed in place by select (it steps to the next value, or flips), so the remote
 * never leaves the row and there are no dialogs; and a panel that says what the focused row does,
 * with every value it can take and, for subtitles, a sample drawn at its real size.
 */
@Composable
fun SettingsScreen() {
  val app = LocalApp.current
  val loc = LocalLoc.current
  var section by remember { mutableStateOf(Section.Playback) }
  var help by remember { mutableStateOf<Help?>(null) }
  var editing by remember { mutableStateOf<String?>(null) }
  val s = app.settings

  fun <T> next(value: T, values: List<T>): T = values[(values.indexOf(value) + 1) % values.size]
  // Coming back to the sections lands on the open one, not on whichever is level with the row.
  val pills = remember { Section.entries.associateWith { FocusRequester() } }
  // And going over to the rows lands on the section's first one.
  val firstRow = remember { FocusRequester() }
  LaunchedEffect(section) { help = null }

  Ambient {
    Row(Modifier.fillMaxSize().padding(start = Theme.edge, end = Theme.edge, top = 150.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
      // Sections.
      Column(Modifier.width(320.dp).enterAt(pills.getValue(section)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Wordmark(56)
        Spacer(Modifier.height(18.dp))
        for (sec in Section.entries) {
          val name = when (sec) {
            Section.Account -> loc(S.account)
            Section.Playback -> loc(S.playback)
            Section.Subtitles -> loc(S.subtitles)
            Section.App -> tv(TV.sectionApp)
            Section.Services -> loc(S.services)
            Section.About -> loc(S.about)
          }
          SectionPill(name, sec == section, Modifier.focusRequester(pills.getValue(sec))) { section = sec }
        }
      }
      // Rows.
      Column(Modifier.width(820.dp).verticalScroll(rememberScrollState()).enterAt(firstRow), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val first = Modifier.focusRequester(firstRow)
        when (section) {
          Section.Account -> {
            val user = app.user
            Fact(loc(S.account), user?.name ?: loc(S.guestAccount))
            user?.type?.let { Fact(loc(S.accountType), loc.accountType(it)) }
            // Under a week left, in red, as a nudge to renew.
            user?.let { loc.subscriptionLine(it) }?.let { Fact(loc(S.expires), it, if ((user.daysLeft(java.time.Instant.now()) ?: 99) < 7) Theme.accent else Theme.text3) }
            if (user != null) {
              Choice(loc(S.signOut), null, Help(loc(S.signOutBody)), { help = it }, first) {
                app.sheets.show(SheetSpec(loc(S.signOutTitle), listOf(SheetAction(loc(S.cancel)) {}, SheetAction(loc(S.signOut), Icons.signOut) { app.signOut() }), loc(S.signOutBody)))
              }
            } else Choice(loc(S.signInQR), null, Help(loc(S.signInHint)), { help = it }, first) { app.requestSignIn() }
          }
          Section.Playback -> {
            val quality = listOf(false to loc(S.qualityBest), true to loc(S.qualityAuto))
            Choice(loc(S.quality), quality.first { it.first == s.preferAdaptive }.second, Help(tv(TV.qualityHint), quality.map { it.second }, quality.first { it.first == s.preferAdaptive }.second), { help = it }, first) {
              app.update { it.copy(preferAdaptive = !it.preferAdaptive) }
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
            Toggle(tv(TV.matchFrameRate), s.matchFrameRate, tv(TV.matchFrameRateHint), { help = it }) { app.update { it.copy(matchFrameRate = !it.matchFrameRate) } }
          }
          Section.Subtitles -> {
            val langs = listOf("fa" to loc(S.persian), "en" to loc(S.english), "off" to loc(S.off))
            val langName = langs.firstOrNull { it.first == s.defaultSubtitle }?.second ?: loc(S.off)
            val hint = tv(TV.subtitlesHint)
            val subHelp = { values: List<String>, current: String -> Help(hint, values, current) }
            Choice(loc(S.defaultSubs), langName, subHelp(langs.map { it.second }, langName), { help = it }, first) {
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
          }
          Section.App -> {
            val langs = Lang.entries.map { it.nativeName }
            Choice(loc(S.language), s.lang.nativeName, Help(tv(TV.languageHint), langs, s.lang.nativeName), { help = it }, first) {
              app.update { it.copy(lang = next(it.lang, Lang.entries.toList())) }
            }
            val context = LocalContext.current
            val systemCalm = remember { systemAnimationsOff(context) }
            val motionHint = tv(TV.motionHint) + if (systemCalm) "\n\n" + tv(TV.motionOffBySystem) else ""
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
          Section.Services -> {
            KeyRow("tmdb", loc(S.tmdbKey), loc(S.tmdbKeyHint), { help = it }, first) { editing = "tmdb" }
            KeyRow("subsource", loc(S.subsourceKey), loc(S.subsourceKeyHint), { help = it }) { editing = "subsource" }
            KeyRow("gemini", tv(TV.geminiKey), tv(TV.geminiKeyHint), { help = it }) { editing = "gemini" }
          }
          Section.About -> {
            Fact(loc(S.version), BuildConfig.VERSION_NAME)
            val updater = app.updater
            val (label, value) = updateRow(updater.state, updater.current)
            val installing = updater.state is Updater.State.Installing || updater.state is Updater.State.NeedsPermission
            Choice(label, value.ifEmpty { null }, Help(tv(if (installing) TV.installHint else TV.updateHint)), { help = it }, first) {
              when (val st = updater.state) {
                is Updater.State.Available -> updater.install(st.release)
                is Updater.State.Failed -> updater.showFailure = true
                Updater.State.Idle, Updater.State.UpToDate -> updater.check()
                else -> Unit
              }
            }
            Fact(loc(S.apiHost), app.apiHost ?: "—")
            Choice(tv(TV.licenses), "Vazirmatn · OFL", Help(loc(S.unofficial)), { help = it }) {}
            Text(loc(S.unofficial), Modifier.padding(top = 18.dp, start = 6.dp), style = app(24).copy(lineHeight = app(24).fontSize * 1.5f), color = Theme.text3)
          }
        }
      }
      // What the focused row does.
      HelpPanel(section, help, Modifier.weight(1f))
    }
  }
  editing?.let { service -> KeyCard(service) { editing = null } }
}

/** Focus coming into this column from outside always lands on [target], whatever is level with where it came from. */
private fun Modifier.enterAt(target: FocusRequester): Modifier =
    focusProperties { onEnter = { if (!target.requestFocus()) cancelFocusChange() } }.focusGroup()

@Composable
private fun SectionPill(name: String, selected: Boolean, modifier: Modifier, onFocus: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  LaunchedEffect(focused) { if (focused) onFocus() }
  Surface(
      onClick = onFocus,
      modifier = modifier.fillMaxWidth(),
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
      colors = ClickableSurfaceDefaults.colors(
          containerColor = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
          focusedContainerColor = Color.White,
          contentColor = if (selected) Theme.text1 else Theme.text2,
          focusedContentColor = Theme.focusInk,
      ),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
  ) { Text(name, Modifier.padding(horizontal = 28.dp, vertical = 16.dp), style = app(28, FontWeight.SemiBold), maxLines = 1) }
}

/** A setting that changes in place when pressed. */
@Composable
private fun Choice(label: String, value: String?, help: Help, show: (Help) -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  LaunchedEffect(focused, help) { if (focused) show(help) }
  Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(Theme.tileRadius)),
      colors = ClickableSurfaceDefaults.colors(containerColor = Theme.glass, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
  ) {
    Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(label, Modifier.weight(1f), style = app(28, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
      value?.let { Text(it, style = app(26), color = (if (focused) Theme.focusInk else Theme.text1).copy(alpha = 0.7f), maxLines = 1) }
    }
  }
}

@Composable
private fun Toggle(label: String, on: Boolean, hint: String, show: (Help) -> Unit, flip: () -> Unit) {
  val loc = LocalLoc.current
  Choice(label, loc(if (on) S.on else S.off), Help(hint, listOf(loc(S.on), loc(S.off)), loc(if (on) S.on else S.off)), show, onClick = flip)
}

@Composable
private fun Fact(label: String, value: String, color: Color = Theme.text3) {
  Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 34.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = app(28, FontWeight.Medium), color = Theme.text2)
    Text(value, style = app(26), color = color, maxLines = 1)
  }
}

@Composable
private fun KeyRow(service: String, label: String, hint: String, show: (Help) -> Unit, modifier: Modifier = Modifier, open: () -> Unit) {
  val app = LocalApp.current
  // Only its last four characters, never the key.
  val value = app.key(service)?.let { "•••• " + it.takeLast(4) } ?: tv(TV.keyNotSet)
  Choice(label, value, Help(hint), show, modifier, open)
}

@Composable
private fun HelpPanel(section: Section, help: Help?, modifier: Modifier) {
  val app = LocalApp.current
  val shape = RoundedCornerShape(Theme.tileRadius + 6.dp)
  Column(modifier.heightIn(min = 560.dp).background(Theme.glass, shape).border(1.dp, Theme.glassEdge, shape).padding(34.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
    help?.let { h ->
      Text(h.text, style = app(25).copy(lineHeight = app(25).fontSize * 1.5f), color = Theme.text2)
      if (h.values.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        h.values.forEach { v ->
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp)) { if (v == h.current) Icon(Icons.check, null, Modifier.size(26.dp), tint = Theme.accent) }
            Text(v, style = app(25, if (v == h.current) FontWeight.SemiBold else FontWeight.Normal), color = if (v == h.current) Theme.text1 else Theme.text3)
          }
        }
      }
    }
    if (section == Section.Subtitles) SubtitleSample(app.settings)
  }
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

  fun save(key: String) {
    app.setKey(service, key)
    text = key
    if (key.isBlank()) {
      state = KeyState.Unset
      return
    }
    state = KeyState.Checking
    scope.launch { state = checker.check(service, key) }
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
            Surface(
                onClick = { typing = true },
                modifier = Modifier.fillMaxWidth().focusRequester(fieldButton),
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
