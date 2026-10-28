package app.nama.androidtv.player

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.nama.androidtv.ui.tint
import app.nama.androidtv.ui.LocalHue
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.nama.androidtv.PlaybackRequest
import app.nama.androidtv.data.Translation
import app.nama.androidtv.ui.CardSurface
import app.nama.androidtv.ui.EpisodeTile
import app.nama.androidtv.ui.Icons
import app.nama.androidtv.ui.LocalApp
import app.nama.androidtv.ui.LocalLoc
import app.nama.androidtv.ui.PillButton
import app.nama.androidtv.ui.PillKind
import app.nama.androidtv.ui.RoundButton
import app.nama.androidtv.ui.Spinner
import app.nama.androidtv.ui.TV
import app.nama.androidtv.ui.Theme
import app.nama.androidtv.ui.accentGradient
import app.nama.androidtv.ui.app
import app.nama.androidtv.ui.blockFocus
import app.nama.androidtv.ui.tv
import app.nama.core.AudioMix
import app.nama.core.Episode
import app.nama.core.Fmt
import app.nama.core.Lang
import app.nama.core.S
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Which side panel is open over the picture. */
private enum class Panel { Subtitles, Audio, Quality, Speed, Sleep, Episodes }

/**
 * Full-screen playback. The picture alone until a key is pressed; then the title, the seek bar and
 * one row of buttons along the bottom, as on YouTube and 30namaTV. Left and right with nothing on
 * screen seek at once, in steps that grow while the key is held, and the seek lands a second after
 * the last press. Channel up and down (on remotes that have no track keys) go to the next and the
 * previous episode.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(request: PlaybackRequest) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val context = LocalContext.current
  val engine = remember(request) { PlayerEngine(context.applicationContext, app, request, app.http, request.pickedByUser) }
  DisposableEffect(engine) {
    engine.begin { app.closePlayer() }
    onDispose { engine.close() }
  }
  // The TV may sleep while a film is paused, never while one plays.
  val view = LocalView.current
  DisposableEffect(engine.playing) {
    view.keepScreenOn = engine.playing
    onDispose { view.keepScreenOn = false }
  }
  // Leaving the app (Home, another input) pauses; nothing plays to an empty room.
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle) {
    // Leaving the app pauses the film and drops the sleep timer, which would otherwise stop it out of the blue on coming back.
    val observer = LifecycleEventObserver { _, e ->
      if (e == Lifecycle.Event.ON_STOP) {
        engine.player.pause()
        engine.chooseSleep(Sleep.Off)
      }
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }

  // The TV's refresh rate to the film's, when asked for; back as it was afterwards.
  val activity = context as? android.app.Activity
  LaunchedEffect(engine.frameRate) { if (activity != null && app.settings.matchFrameRate && engine.frameRate > 0) FrameRate.apply(activity, engine.frameRate) }
  DisposableEffect(Unit) { onDispose { activity?.let { FrameRate.restore(it) } } }

  var controls by remember { mutableStateOf(false) }
  var panel by remember { mutableStateOf<Panel?>(null) }
  var lastKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
  /** Where a seek with the arrow keys is headed, until it lands. */
  var seekTarget by remember { mutableStateOf<Double?>(null) }
  var seekHeldSince by remember { mutableLongStateOf(0L) }
  var lastSeekKey by remember { mutableLongStateOf(0L) }
  val playButton = remember { FocusRequester() }
  val surface = remember { FocusRequester() }

  // The controls step aside a few seconds after the last key, while the film plays.
  LaunchedEffect(controls, lastKey, engine.playing, panel) {
    if (!controls || panel != null || !engine.playing) return@LaunchedEffect
    delay(5_000)
    controls = false
  }
  LaunchedEffect(controls) {
    withFrameNanos {}
    runCatching { if (controls) playButton.requestFocus() else surface.requestFocus() }
  }
  // A panel closing gives focus back to the button that opened it.
  val opener = remember { Panel.entries.associateWith { FocusRequester() } }
  var lastPanel by remember { mutableStateOf<Panel?>(null) }
  LaunchedEffect(panel) {
    if (panel != null) {
      lastPanel = panel
      return@LaunchedEffect
    }
    val was = lastPanel ?: return@LaunchedEffect
    lastPanel = null
    withFrameNanos {}
    runCatching { if (controls) opener.getValue(was).requestFocus() else surface.requestFocus() }
  }
  // A seek lands a second after the last press.
  LaunchedEffect(lastSeekKey) {
    val target = seekTarget ?: return@LaunchedEffect
    delay(1_000)
    engine.seekTo(target)
    seekTarget = null
    seekHeldSince = 0L
  }

  fun seekBy(sign: Int, repeat: Boolean) {
    val now = System.currentTimeMillis()
    if (!repeat || seekHeldSince == 0L) seekHeldSince = now
    // Five seconds a press, doubling every second and a half the key is held, up to eighty.
    val step = 5.0 * (1 shl minOf(4, ((now - seekHeldSince) / 1_500).toInt()))
    // Before the length is known (the playlist still loading), no seeking: it would go to 0 and lose the resume point.
    if (engine.duration <= 0) return
    val from = seekTarget ?: engine.position
    seekTarget = (from + sign * step).coerceIn(0.0, (engine.duration - 1).coerceAtLeast(0.0))
    lastSeekKey = now
  }

  BackHandler {
    when {
      panel != null -> panel = null
      controls -> controls = false
      else -> engine.close()
    }
  }

  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Box(
      Modifier.fillMaxSize().background(Color.Black)
          .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            lastKey = System.currentTimeMillis()
            engine.attended()
            val repeat = e.nativeKeyEvent.repeatCount > 0
            when (e.key) {
              Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                engine.togglePlay()
                true
              }
              Key.MediaFastForward -> { engine.seekTo(engine.position + 10); true }
              Key.MediaRewind -> { engine.seekTo(engine.position - 10); true }
              // Next and previous are episodes; with none that way the key goes to the TV.
              Key.MediaNext, Key.ChannelUp -> if (engine.index + 1 < engine.items.size) { if (!repeat) engine.next(); true } else false
              Key.MediaPrevious, Key.ChannelDown -> if (engine.index > 0 || engine.position > 10) { if (!repeat) engine.previous(); true } else false
              else -> {
                if (controls || panel != null) return@onPreviewKeyEvent false
                // Nothing on screen: the arrows seek (along the bar, which runs left to right in every
                // language), select takes the offer on the pill, and anything else brings up the controls.
                when (e.key) {
                  Key.DirectionLeft -> { seekBy(-1, repeat); true }
                  Key.DirectionRight -> { seekBy(1, repeat); true }
                  Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (engine.offer != null) engine.acceptOffer() else controls = true
                    true
                  }
                  Key.DirectionUp, Key.DirectionDown -> { controls = true; true }
                  else -> false
                }
              }
            }
          }) {
    // The picture, in its own shape.
    Box(Modifier.fillMaxSize().focusRequester(surface).focusable(), contentAlignment = Alignment.Center) {
      val aspect = engine.aspect
      PlayerSurface(engine.player, if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize(), SURFACE_TYPE_SURFACE_VIEW)
    }
    if (engine.buffering) Spinner(Modifier.align(Alignment.TopStart).padding(Theme.edge).size(40.dp), Theme.accent)

    // Subtitles sit higher while the controls or the episodes are showing.
    val lift by animateDpAsState(
        when {
          panel == Panel.Episodes -> 520.dp
          controls -> 300.dp
          else -> 70.dp
        }, label = "lift")
    SubtitleLine(engine.cue, Modifier.align(Alignment.BottomCenter).padding(bottom = lift))

    val shownOffer = engine.offer.takeIf { !controls && panel == null }
    AnimatedVisibility(
        shownOffer != null,
        Modifier.align(Alignment.BottomEnd).padding(end = Theme.edge, bottom = 230.dp),
        enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut(),
    ) { engine.offer?.let { OfferPill(it) } }

    AnimatedVisibility(controls && panel != Panel.Episodes, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) { PlayerTop(engine) }
    AnimatedVisibility((controls || seekTarget != null) && panel != Panel.Episodes, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
      Controls(engine, seekTarget, full = controls, playButton = playButton, opener = opener, open = { panel = it }, blocked = panel != null)
    }
    AnimatedVisibility(
        panel != null && panel != Panel.Episodes, Modifier.align(Alignment.CenterEnd),
        enter = slideInHorizontally { if (rtl) -it else it } + fadeIn(), exit = slideOutHorizontally { if (rtl) -it else it } + fadeOut(),
    ) { panel?.let { SidePanel(engine, it) { panel = null } } }
    AnimatedVisibility(
        panel == Panel.Episodes, Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
    ) { EpisodesPanel(engine) { panel = null } }
  }
}

// MARK: Subtitles

/**
 * One cue, in the app's type: on a plate at the chosen strength, or bare with an outline and a
 * shadow that keep it readable over a bright scene. Its direction follows the text itself.
 */
@Composable
private fun SubtitleLine(text: String, modifier: Modifier) {
  if (text.isEmpty()) return
  val s = LocalApp.current.settings
  val size = s.subtitleSize
  val style = app(size, FontWeight.Medium).copy(lineHeight = (size * 1.3).sp, textAlign = TextAlign.Center)
  val plate = s.subtitleBackground.plate
  Box(
      modifier.widthIn(max = 1580.dp)
          .then(if (plate > 0) Modifier.background(Color.Black.copy(alpha = plate), RoundedCornerShape(14.dp)) else Modifier)
          .padding(horizontal = 26.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
  ) {
    if (plate == 0f) {
      Text(text, style = style.copy(drawStyle = Stroke(width = size * 0.12f)), color = Color.Black)
      Text(text, style = style.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 2f), 6f)), color = s.subtitleColor.color)
    } else {
      Text(text, style = style, color = s.subtitleColor.color)
    }
  }
}

// MARK: The pill

@Composable
private fun OfferPill(offer: Offer) {
  val loc = LocalLoc.current
  val shape = RoundedCornerShape(50)
  val title = when (offer.kind) {
    Offer.Kind.Intro -> loc(S.skipIntro)
    Offer.Kind.Recap -> loc(S.skipRecap)
    Offer.Kind.Next -> loc(S.nextEpisode)
    Offer.Kind.StartOver -> tv(TV.startOver)
  }
  Row(
      Modifier.height(80.dp).clip(shape).background(accentGradient)
          .drawBehind {
            // Fills as the credits run: when it is full, the next episode starts.
            offer.countdown?.let { drawRect(Color.White.copy(alpha = 0.26f), size = Size(size.width * it, size.height)) }
          }
          .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))), shape)
          .padding(horizontal = 40.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
        when (offer.kind) {
          Offer.Kind.Next -> Icons.skipNext
          Offer.Kind.StartOver -> Icons.replay
          else -> Icons.forward
        },
        null, Modifier.size(28.dp), tint = Color.White,
    )
    Text(title, style = app(27, FontWeight.SemiBold), color = Color.White, maxLines = 1)
  }
}

// MARK: Controls

@Composable
private fun Controls(engine: PlayerEngine, seekTarget: Double?, full: Boolean, playButton: FocusRequester, opener: Map<Panel, FocusRequester>, open: (Panel) -> Unit, blocked: Boolean) {
  val loc = LocalLoc.current
  val post = engine.post
  val item = engine.current
  // The Mac's player: a shade at the bottom, the timeline thin in the title's colour, and under it
  // plain glyphs that light as white discs (in focus here, under the pointer there). The film's
  // name is at the top, over a shade of its own (PlayerTop).
  CompositionLocalProvider(LocalHue provides (LocalHue.current ?: post.tint())) {
  Box(Modifier.fillMaxWidth().blockFocus(blocked).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.85f))))) {
    Column(Modifier.fillMaxWidth().padding(start = Theme.edge - 16.dp, end = Theme.edge - 16.dp, top = 120.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      // A film's time runs left to right in every language, as on tape: the bar and the transport
      // buttons are not mirrored (Material's guidance; YouTube, the Apple TV and the Mac do the same).
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Scrubber(engine, seekTarget)
      if (full) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          PlayerButton(if (engine.playing) Icons.pause else Icons.play, loc(if (engine.playing) S.pause else S.play), { engine.togglePlay() }, Modifier.focusRequester(playButton), size = 60, glyph = 28)
          if (engine.items.size > 1) PlayerButton(Icons.skipPrevious, loc(S.rowPrevious), { engine.previous() })
          PlayerButton(Icons.replay, loc(S.back10), { engine.seekTo(engine.position - 10) })
          PlayerButton(Icons.forward, loc(S.forward10), { engine.seekTo(engine.position + 10) })
          if (engine.index + 1 < engine.items.size) PlayerButton(Icons.skipNext, loc(S.nextEpisode), { engine.next() })
          val d = engine.duration
          val shown = seekTarget ?: engine.position
          Text(
              loc.num("${Fmt.clock(shown)} / ${Fmt.clock(d)}"), Modifier.padding(start = 14.dp),
              style = app(19, FontWeight.Medium).copy(fontFeatureSettings = "tnum"), color = Color.White.copy(alpha = 0.85f),
          )
          if (d > 0) {
            // When it will end at this speed, as Netflix shows it.
            val end = LocalTime.now().plusSeconds(((d - shown) / engine.speed).toLong())
            CompositionLocalProvider(LocalLayoutDirection provides if (loc.lang.isRTL) LayoutDirection.Rtl else LayoutDirection.Ltr) {
              Text("${tv(TV.endsAt)} ${loc.num(end.format(DateTimeFormatter.ofPattern("HH:mm")))}", Modifier.padding(start = 14.dp), style = app(17, FontWeight.Medium), color = Color.White.copy(alpha = 0.55f))
            }
          }
          Spacer(Modifier.weight(1f))
          PlayerButton(Icons.speed, loc(S.speed), { open(Panel.Speed) }, Modifier.focusRequester(opener.getValue(Panel.Speed)), lit = engine.speed != 1f)
          if (item.subtitles.isNotEmpty() || engine.found.isNotEmpty()) PlayerButton(Icons.subtitles, loc(S.subtitles), { open(Panel.Subtitles) }, Modifier.focusRequester(opener.getValue(Panel.Subtitles)), lit = engine.subtitleLanguage != "off")
          if ((engine.quality?.mixes?.size ?: 0) > 1 || engine.audioTracks.isNotEmpty()) PlayerButton(Icons.volume, loc(S.audio), { open(Panel.Audio) }, Modifier.focusRequester(opener.getValue(Panel.Audio)))
          if (item.qualities.size > 1) PlayerButton(Icons.quality, loc(S.quality), { open(Panel.Quality) }, Modifier.focusRequester(opener.getValue(Panel.Quality)))
          PlayerButton(Icons.moon, loc(S.sleepTimer), { open(Panel.Sleep) }, Modifier.focusRequester(opener.getValue(Panel.Sleep)), lit = engine.sleep != Sleep.Off)
          if (engine.items.size > 1) PlayerButton(Icons.list, loc(S.episodes), { open(Panel.Episodes) }, Modifier.focusRequester(opener.getValue(Panel.Episodes)))
        }
      }
      }
    }
  }
  }
}

/**
 * The top of the player, as the Mac's: the film's name and, for a series, the episode, over a shade
 * from the top edge. Shown with the controls.
 */
@Composable
fun PlayerTop(engine: PlayerEngine) {
  val loc = LocalLoc.current
  val post = engine.post
  val item = engine.current
  Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))) {
    Column(Modifier.padding(start = Theme.edge, end = Theme.edge, top = 50.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(post.title(loc.lang), style = app(26, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (post.isSeries || engine.items.size > 1) {
        val line = loc.episodeTag(item.season, Episode.label(item.episodeRaw, engine.index + 1)) + (item.title(loc.lang)?.let { " · $it" } ?: "")
        Text(line, style = app(18, FontWeight.Medium), color = Color.White.copy(alpha = 0.72f), maxLines = 1)
      }
    }
  }
}

/** The Mac player's round button: a plain glyph, a white disc in focus, filled white while [lit]. */
@Composable
private fun PlayerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, lit: Boolean = false, size: Int = 50, glyph: Int = 22) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  androidx.tv.material3.Surface(
      onClick = onClick,
      modifier = modifier.semantics { contentDescription = label },
      interactionSource = interaction,
      shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
      colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
          containerColor = if (lit) Color.White.copy(alpha = 0.9f) else Color.Transparent,
          focusedContainerColor = Color.White,
          contentColor = if (lit) Theme.focusInk else Color.White,
          focusedContentColor = Theme.focusInk,
      ),
      scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
  ) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { androidx.tv.material3.Icon(icon, null, Modifier.size(glyph.dp)) }
  }
}

/** The seek bar, as the Mac's: a thin line in the title's colour, a little thicker with a thumb while seeking. */
@Composable
private fun Scrubber(engine: PlayerEngine, seekTarget: Double?) {
  val d = engine.duration
  val shown = seekTarget ?: engine.position
  val fraction = if (d > 0) (shown / d).toFloat().coerceIn(0f, 1f) else 0f
  val ready = if (d > 0) (engine.buffered / d).toFloat().coerceIn(0f, 1f) else 0f
  val active = seekTarget != null
  val hue = LocalHue.current ?: Theme.accent
  // While seeking, the picture at that point rides above the bar's thumb.
  LaunchedEffect(seekTarget) { seekTarget?.let { engine.preview.want(it) } }
  val frame = engine.preview.frame
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (seekTarget != null && frame != null) {
      BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp)) {
        val card = 320.dp
        val x = (maxWidth * fraction - card / 2).coerceIn(0.dp, maxWidth - card)
        Column(Modifier.offset(x = x).width(card), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          androidx.compose.foundation.Image(
              frame.second.asImageBitmap(), null,
              Modifier.width(card).height(180.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, Color.White, RoundedCornerShape(14.dp)),
              contentScale = androidx.compose.ui.layout.ContentScale.Crop,
          )
        }
      }
    }
    Box(
        Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 16.dp).drawBehind {
          val y = size.height / 2
          val h = (if (active) 8 else 5).dp.toPx()
          fun bar(to: Float, color: Color) = drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * to, h), CornerRadius(h / 2))
          bar(1f, Color.White.copy(alpha = 0.24f))
          bar(ready, Color.White.copy(alpha = 0.14f))
          bar(fraction, hue)
          if (active) drawCircle(Color.White, 9.dp.toPx(), Offset(size.width * fraction, y))
        })
  }
}

// MARK: Panels

private data class Choice(val title: String, val selected: Boolean, val run: () -> Unit)

/** A side panel of choices from the end edge: select applies at once, and the film plays on behind it. */
@Composable
private fun SidePanel(engine: PlayerEngine, panel: Panel, close: () -> Unit) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val item = engine.current
  val (title, choices) = when (panel) {
    Panel.Subtitles -> {
      val languages = listOf("fa", "en").filter { item.subtitles[it] != null }
      val list = mutableListOf(Choice(loc(S.off), engine.subtitleLanguage == "off") { engine.selectSubtitle("off", true) })
      languages.forEach { l -> list += Choice(loc(if (l == "fa") S.persian else S.english), engine.subtitleLanguage == l) { engine.selectSubtitle(l, true) } }
      // Found ones are named by their release; the same name twice gets a number.
      val seen = HashMap<String, Int>()
      engine.found.forEach { c ->
        val n = (seen[c.name] ?: 0) + 1
        seen[c.name] = n
        val code = PlayerEngine.FOUND + c.id
        list += Choice("${loc(S.persian)} · ${c.name}" + if (n > 1) " (${loc.num(n)})" else "", engine.subtitleLanguage == code) { engine.selectSubtitle(code, true) }
      }
      // English put into Persian by Gemini, when there is nothing Persian to choose.
      if (engine.canTranslate || engine.subtitleLanguage == PlayerEngine.TRANSLATED) {
        val target = Translation.target(loc.lang)
        val name = if (target == Lang.ar) Lang.ar.nativeName else loc(S.persian)
        val progress = engine.translated?.takeIf { it < 1f }?.let { " (${loc.num((it * 100).toInt())}${if (loc.lang == Lang.en) "%" else "٪"})" }.orEmpty()
        list += Choice("$name · ${tv(TV.machineTranslation)}$progress", engine.subtitleLanguage == PlayerEngine.TRANSLATED) { engine.selectSubtitle(PlayerEngine.TRANSLATED, true) }
      }
      // The nudge in words, never a sign: a minus would land at the wrong end of a Persian line.
      val quarter = loc.num("0.25")
      list += Choice("${loc(S.subtitleEarlier)} ($quarter ${loc(S.secondsShort)})", false) { engine.nudgeSubtitles(-0.25) }
      list += Choice("${loc(S.subtitleLater)} ($quarter ${loc(S.secondsShort)})", false) { engine.nudgeSubtitles(0.25) }
      if (engine.subtitleOffset != 0.0) list += Choice("${loc(S.reset)} (${loc.num(String.format(java.util.Locale.ROOT, "%+.2f", engine.subtitleOffset))} ${loc(S.secondsShort)})", false) { engine.nudgeSubtitles(0.0) }
      loc(S.subtitles) to list
    }
    Panel.Audio -> {
      val list = mutableListOf<Choice>()
      engine.quality?.mixes?.takeIf { it.size > 1 }?.forEach { m ->
        list += Choice(loc(if (m == AudioMix.stereo) S.audioStereo else if (m == AudioMix.surround) S.audioSurround else S.audioOriginal), m == engine.mix) { engine.chooseMix(m) }
      }
      engine.audioTracks.forEach { l -> list += Choice(java.util.Locale.forLanguageTag(l).getDisplayLanguage(java.util.Locale.forLanguageTag(loc.lang.name)).ifEmpty { l }, l == engine.audioTrack) { engine.chooseAudioTrack(l) } }
      loc(S.audio) to list
    }
    Panel.Quality -> loc(S.quality) to item.qualities.map { q -> Choice(if (q.isAuto) loc(S.qualityAuto) else q.label, q == engine.quality) { engine.chooseQuality(q) } }
    Panel.Speed -> loc(S.speed) to listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { s -> Choice(loc.num(Fmt.score(s.toDouble()).removeSuffix(".0")) + "×", s == engine.speed) { engine.chooseSpeed(s) } }
    Panel.Sleep -> loc(S.sleepTimer) to (listOf(Sleep.Off, Sleep.EndOfEpisode) + listOf(15, 30, 45, 60).map { Sleep.Minutes(it) }).map { s ->
      val name = when (s) {
        Sleep.Off -> loc(S.off)
        Sleep.EndOfEpisode -> loc(if (engine.post.isSeries) S.endOfEpisode else S.endOfFilm)
        is Sleep.Minutes -> "${loc.num(s.n)} ${loc(S.min)}"
      }
      Choice(name, s == engine.sleep) { engine.chooseSleep(s) }
    }
    Panel.Episodes -> "" to emptyList()
  }
  val first = remember(panel) { FocusRequester() }
  LaunchedEffect(panel) {
    withFrameNanos {}
    runCatching { first.requestFocus() }
  }
  Column(
      Modifier.fillMaxHeight().width(580.dp).background(Theme.surface.copy(alpha = 0.96f)).padding(horizontal = 40.dp, vertical = 70.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(title, Modifier.padding(bottom = 18.dp), style = app(36, FontWeight.Bold), color = Theme.text1)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
      items(choices.size) { i ->
        val c = choices[i]
        ChoiceRow(c, if (i == 0) Modifier.focusRequester(first) else Modifier)
      }
    }
  }
}

@Composable
private fun ChoiceRow(c: Choice, modifier: Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  androidx.tv.material3.Surface(
      onClick = c.run,
      modifier = modifier.fillMaxWidth(),
      interactionSource = interaction,
      shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
      colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
      scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
  ) {
    Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (c.selected) Icon(Icons.check, null, Modifier.size(28.dp), tint = if (focused) Theme.accent else Theme.accent)
      }
      Text(c.title, style = app(26, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** The series' episodes from the bottom: the seasons in a row, and the episodes of the one chosen. */
@Composable
private fun EpisodesPanel(engine: PlayerEngine, close: () -> Unit) {
  val loc = LocalLoc.current
  val seasons = engine.items.map { it.season }.distinct().sorted()
  val playingSeason = engine.current.season
  var season by remember { mutableStateOf(playingSeason) }
  val entries = engine.items.withIndex().filter { it.value.season == season }
  val playingTile = remember { FocusRequester() }
  val state = rememberLazyListState()
  LaunchedEffect(Unit) {
    entries.indexOfFirst { it.index == engine.index }.takeIf { it >= 0 }?.let { state.scrollToItem(it) }
    withFrameNanos {}
    runCatching { playingTile.requestFocus() }
  }
  Column(
      Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))).padding(top = 60.dp, bottom = 40.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (seasons.size > 1) {
      LazyRow(contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(seasons) { s -> PillButton("${loc(S.season)} ${loc.num(s)}", { season = s }, kind = PillKind.Chip, selected = s == season) }
      }
    }
    LazyRow(state = state, contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      // By place: two versions of one episode without a file id share an id.
      items(entries, key = { it.index }) { (i, item) ->
        CardSurface(
            onClick = {
              close()
              engine.start(i)
            },
            modifier = if (i == engine.index) Modifier.focusRequester(playingTile) else Modifier,
            radius = Theme.tileRadius, scale = 1.06f,
        ) { EpisodeTile(item, i + 1, nowPlaying = i == engine.index) }
      }
    }
  }
}
