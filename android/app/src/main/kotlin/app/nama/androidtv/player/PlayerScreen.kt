package app.nama.androidtv.player

import app.nama.androidtv.ui.rememberPosterTint
import androidx.compose.foundation.clickable
import app.nama.androidtv.data.SubtitlePosition
import app.nama.androidtv.ui.RemoteImage
import app.nama.androidtv.ui.LocalLively
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import app.nama.androidtv.ui.LocalCalm
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absolutePadding
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
import androidx.compose.ui.input.key.onKeyEvent
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

/** Which side panel is open over the picture. Sound and subtitles share one, as on the Mac's bubble. */
private enum class Panel { Subtitles, Quality, Speed, Sleep, Episodes, Settings }

/** What stays behind the gear: quality and the sleep timer, each a list of its own. */
private val Panel.inSettings get() = this == Panel.Quality || this == Panel.Sleep

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
  // As each film or episode starts, its controls and timeline show for a few seconds, the skip
  // offer with them, and then step aside as after any key (the owner).
  LaunchedEffect(engine.index, engine.pictured) { if (engine.pictured) controls = true }
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
    runCatching { if (controls) opener.getValue(if (was.inSettings) Panel.Settings else was).requestFocus() else surface.requestFocus() }
  }
  // A seek lands a second after the last press.
  LaunchedEffect(lastSeekKey) {
    val target = seekTarget ?: return@LaunchedEffect
    delay(1_000)
    engine.seekTo(target)
    seekTarget = null
    seekHeldSince = 0L
  }

  fun seekBy(sign: Int, repeat: Boolean, base: Double = 5.0, doublings: Int = 4) {
    val now = System.currentTimeMillis()
    if (!repeat || seekHeldSince == 0L) seekHeldSince = now
    // Five seconds a press, doubling every second and a half the key is held, up to eighty; the
    // remote's own fast-forward and rewind thirty, up to two minutes.
    val step = base * (1 shl minOf(doublings, ((now - seekHeldSince) / 1_500).toInt()))
    // Before the length is known (the playlist still loading), no seeking: it would go to 0 and lose the resume point.
    if (engine.duration <= 0) return
    val from = seekTarget ?: engine.position
    seekTarget = (from + sign * step).coerceIn(0.0, (engine.duration - 1).coerceAtLeast(0.0))
    lastSeekKey = now
  }

  /** Lands a seek still waiting, at once; false when there is none. */
  fun landSeek(): Boolean {
    val target = seekTarget ?: return false
    engine.seekTo(target)
    seekTarget = null
    seekHeldSince = 0L
    return true
  }

  BackHandler {
    when {
      // Back from quality, sound or the timer goes back to the settings they were chosen from.
      panel != null -> panel = if (panel!!.inSettings) Panel.Settings else null
      // A seek still waiting to land is dropped, and the film stays where it was; with the controls
      // up too (seeking on the timeline), they stay.
      seekTarget != null -> {
        seekTarget = null
        seekHeldSince = 0L
      }
      controls -> controls = false
      // The countdown to the next episode stops; the episode then ends on the title's page.
      engine.offer?.kind == Offer.Kind.Next && engine.offer?.countdown != null -> engine.cancelNext()
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
              // Stop ends the film, as Back does from the picture (the Tizen app's).
              Key.MediaStop -> { engine.close(); true }
              // Thirty seconds a press, adding up and quickening while held, landing as the arrows' do.
              Key.MediaFastForward -> { seekBy(1, repeat, base = 30.0, doublings = 2); true }
              Key.MediaRewind -> { seekBy(-1, repeat, base = 30.0, doublings = 2); true }
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
                    // A seek waiting to land lands now.
                    if (!landSeek()) {
                      if (engine.offer != null) engine.acceptOffer() else controls = true
                    }
                    true
                  }
                  Key.DirectionUp, Key.DirectionDown -> { controls = true; true }
                  else -> false
                }
              }
            }
          }) {
    // The Mac's light around the picture: a small frame every few seconds, from the seek previews'
    // reader, spread soft behind the film; and black bars burned into the file cut away. Only with
    // the motion effects on, as it is a second, if small, decoder at work.
    val glowing = app.settings.sceneGlow && LocalLively.current && engine.pictured
    val watch = remember(engine.index) { BarWatch() }
    var trim by remember(engine.index) { mutableStateOf(Trim()) }
    var light by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(glowing, engine.index, engine.playing) {
      if (!glowing) {
        light = null
        return@LaunchedEffect
      }
      // Paused (or the app left, which pauses), the light stays as it was and nothing is read.
      if (!engine.playing) return@LaunchedEffect
      while (true) {
        engine.preview.glance(engine.position)
        kotlinx.coroutines.delay(GLANCE_MS)
      }
    }
    val glimpse = engine.preview.glimpse
    LaunchedEffect(glimpse, glowing) {
      val frame = glimpse ?: return@LaunchedEffect
      if (!glowing) return@LaunchedEffect
      val (seen, small) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val px = IntArray(frame.width * frame.height)
        frame.getPixels(px, 0, frame.width, 0, 0, frame.width, frame.height)
        watch.see(px, frame.width, frame.height)
        val t = watch.trim()
        // The picture without its bars, shrunk to a few dozen points: drawn large, that is the blur.
        val x = (t.left * frame.width).toInt()
        val y = (t.top * frame.height).toInt()
        val cw = (frame.width - x - (t.right * frame.width).toInt()).coerceAtLeast(1)
        val ch = (frame.height - y - (t.bottom * frame.height).toInt()).coerceAtLeast(1)
        val cut = android.graphics.Bitmap.createBitmap(frame, x, y, cw, ch)
        t to android.graphics.Bitmap.createScaledBitmap(cut, 32, (32f * ch / cw).toInt().coerceAtLeast(1), true).asImageBitmap()
      }
      // Always the newest word: a bright frame with picture at an edge takes that edge's cut back.
      trim = if (seen.worthCutting) seen else Trim()
      light = small
    }
    Crossfade(light, Modifier.fillMaxSize(), animationSpec = tween(1200), label = "light") { l ->
      if (l != null) androidx.compose.foundation.Image(
          l, null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
          contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
      )
    }
    // The picture, in its own shape; with bars burned into it, enlarged until they fall off the screen.
    BoxWithConstraints(Modifier.fillMaxSize().focusRequester(surface).focusable(), contentAlignment = Alignment.Center) {
      val aspect = engine.aspect
      val t = trim.takeIf { glowing && it.worthCutting && aspect != null }
      if (t == null || aspect == null) {
        PlayerSurface(engine.player, if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.fillMaxSize(), SURFACE_TYPE_SURFACE_VIEW)
      } else {
        val keepW = 1f - t.left - t.right
        val keepH = 1f - t.top - t.bottom
        val content = aspect * keepW / keepH
        val screen = maxWidth / maxHeight
        val cw = if (content > screen) maxWidth else maxHeight * content
        val ch = if (content > screen) maxWidth / content else maxHeight
        val sw = cw / keepW
        val sh = ch / keepH
        // The kept part's middle on the screen's middle.
        val dx = sw * ((1f - t.left - t.right) / 2f + t.left - 0.5f)
        val dy = sh * ((1f - t.top - t.bottom) / 2f + t.top - 0.5f)
        PlayerSurface(engine.player, Modifier.requiredSize(sw, sh).offset(-dx, -dy), SURFACE_TYPE_SURFACE_VIEW)
      }
    }
    // Before the first picture, the Mac's curtain: the title's artwork drawing slowly nearer, its
    // name and the episode, over the black of a stream still on its way.
    val curtain by animateFloatAsState(if (engine.pictured) 0f else 1f, tween(450), label = "curtain")
    if (curtain > 0f) Curtain(engine, Modifier.graphicsLayer { alpha = curtain })
    if (engine.buffering && engine.pictured) Spinner(Modifier.align(Alignment.TopStart).padding(Theme.edge).size(40.dp), Theme.accent)

    // Subtitles sit higher while the controls or the episodes are showing: just above the timeline,
    // as the Mac's (docs/design/mac player), and higher still while seeking, over the picture at the
    // seek point that rides above the bar.
    val lift by animateDpAsState(
        when {
          controls && seekTarget != null -> 340.dp
          controls -> 220.dp
          else -> 70.dp
        }, label = "lift")
    // At the top, clear of the title and the episode while the controls show them.
    val drop by animateDpAsState(if (controls) 250.dp else 70.dp, label = "drop")
    SubtitleLine(
        engine.cue,
        when (app.settings.subtitlePosition) {
          SubtitlePosition.bottom -> Modifier.align(Alignment.BottomCenter).padding(bottom = lift)
          SubtitlePosition.middle -> Modifier.align(Alignment.Center)
          SubtitlePosition.top -> Modifier.align(Alignment.TopCenter).padding(top = drop)
        },
    )

    // The offer stays while its stretch runs, controls or not, always at the right whatever the
    // language (the owner), above the controls while they are up.
    val shownOffer = engine.offer.takeIf { panel == null }
    val offerLift by animateDpAsState(if (controls) 250.dp else 150.dp, label = "offer")
    AnimatedVisibility(
        shownOffer != null,
        // Its margin from the right in every language too: `end` is the left in Persian.
        Modifier.align(androidx.compose.ui.AbsoluteAlignment.BottomRight).absolutePadding(right = Theme.edge, bottom = offerLift),
        enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut(),
    ) { engine.offer?.let { OfferPill(it, focusable = controls) { engine.acceptOffer() } } }

    AnimatedVisibility(controls && panel != Panel.Episodes, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) { PlayerTop(engine) }
    AnimatedVisibility((controls || seekTarget != null) && panel != Panel.Episodes, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
      Controls(engine, seekTarget, full = controls, playButton = playButton, opener = opener, open = { panel = it }, blocked = panel != null, seek = { sign, repeat -> seekBy(sign, repeat) }, land = ::landSeek)
    }
    // On the right of the picture in every language, as the Mac's and a TV's own menus are; what is
    // inside it still reads in its language.
    AnimatedVisibility(
        panel != null && panel != Panel.Episodes, Modifier.align(androidx.compose.ui.AbsoluteAlignment.CenterRight),
        enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
    ) { panel?.let { SidePanel(engine, it, go = { next -> panel = next }) { panel = null } } }
    AnimatedVisibility(
        panel == Panel.Episodes, Modifier.align(androidx.compose.ui.AbsoluteAlignment.CenterRight),
        enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
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
private fun OfferPill(offer: Offer, focusable: Boolean = false, accept: () -> Unit = {}) {
  val loc = LocalLoc.current
  val shape = RoundedCornerShape(50)
  // With the controls up it is a button of its own, Up from them; with nothing up, select takes it.
  val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val title = when (offer.kind) {
    Offer.Kind.Intro -> loc(S.skipIntro)
    Offer.Kind.Recap -> loc(S.skipRecap)
    Offer.Kind.Next -> loc(S.nextEpisode)
    Offer.Kind.StartOver -> tv(TV.startOver)
  }
  Row(
      Modifier
          .then(if (focusable) Modifier.clickable(interactionSource = interaction, indication = null, onClick = accept) else Modifier)
          .graphicsLayer { val s = if (focused) 1.06f else 1f; scaleX = s; scaleY = s }
          
          .height(80.dp).clip(shape).background(accentGradient)
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
private fun Controls(
    engine: PlayerEngine, seekTarget: Double?, full: Boolean, playButton: FocusRequester, opener: Map<Panel, FocusRequester>, open: (Panel) -> Unit, blocked: Boolean,
    seek: (sign: Int, repeat: Boolean) -> Unit, land: () -> Boolean,
) {
  val loc = LocalLoc.current
  val post = engine.post
  val item = engine.current
  // The Mac's player: a shade at the bottom, the timeline thin in the title's colour, and under it
  // plain glyphs that light as white discs (in focus here, under the pointer there). The film's
  // name is at the top, over a shade of its own (PlayerTop).
  CompositionLocalProvider(LocalHue provides (LocalHue.current ?: rememberPosterTint(post))) {
  Box(Modifier.fillMaxWidth().blockFocus(blocked).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.85f))))) {
    Column(Modifier.fillMaxWidth().padding(start = Theme.edge - 16.dp, end = Theme.edge - 16.dp, top = 120.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      // A film's time runs left to right in every language, as on tape: the bar and the transport
      // buttons are not mirrored (Material's guidance; YouTube, the Apple TV and the Mac do the same).
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Scrubber(engine, seekTarget, focusable = full, seek = seek, land = land)
      if (full) {
        // Inside the timeline's own ends (its 16-point inset), and so far in again as the largest
        // disc grows in focus (Play, 100 at 1.12), so no button reaches past the line (the owner).
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          PlayerButton(if (engine.playing) Icons.pause else Icons.play, loc(if (engine.playing) S.pause else S.play), { engine.togglePlay() }, Modifier.focusRequester(playButton), size = 100, glyph = 54)
          if (engine.items.size > 1) PlayerButton(Icons.skipPrevious, loc(S.rowPrevious), { engine.previous() })
          // The Mac's gobackward.10 and goforward.10: a turning arrow with the seconds inside it.
          PlayerButton(Icons.replay, loc(S.back10), { engine.seekTo(engine.position - 10) }, badge = "10")
          PlayerButton(Icons.replay, loc(S.forward10), { engine.seekTo(engine.position + 10) }, badge = "10", mirror = true)
          if (engine.index + 1 < engine.items.size) PlayerButton(Icons.skipNext, loc(S.nextEpisode), { engine.next() })
          val d = engine.duration
          val shown = seekTarget ?: engine.position
          // As the Mac's: Latin digits in every language, as the timeline runs left to right in
          // every language, semibold in the text's own white (docs/design/mac/fa/player).
          Text(
              "${Fmt.clock(shown)} / ${Fmt.clock(d)}", Modifier.padding(start = 18.dp),
              style = app(25, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"), color = Theme.text1,
          )
          if (d > 0) {
            // When it will end at this speed, as Netflix shows it; its digits as the clock's beside it.
            val end = LocalTime.now().plusSeconds(((d - shown) / engine.speed).toLong())
            CompositionLocalProvider(LocalLayoutDirection provides if (loc.lang.isRTL) LayoutDirection.Rtl else LayoutDirection.Ltr) {
              Text("${tv(TV.endsAt)} ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}", Modifier.padding(start = 18.dp), style = app(22, FontWeight.Medium), color = Color.White.copy(alpha = 0.55f))
            }
          }
          Spacer(Modifier.weight(1f))
          PlayerButton(Icons.speed, loc(S.speed), { open(Panel.Speed) }, Modifier.focusRequester(opener.getValue(Panel.Speed)), lit = engine.speed != 1f)
          // Sound and subtitles behind one bubble, as on the Mac.
          if (hasSoundOrSubtitles(engine)) PlayerButton(Icons.captions, tv(TV.audioAndSubtitles), { open(Panel.Subtitles) }, Modifier.focusRequester(opener.getValue(Panel.Subtitles)), lit = engine.subtitleLanguage != "off")
          // Quality and the sleep timer behind the gear. It is the moon while a timer runs, so a
          // timer is not forgotten behind it.
          PlayerButton(if (engine.sleep != Sleep.Off) Icons.moon else Icons.gear, loc(S.settings), { open(Panel.Settings) }, Modifier.focusRequester(opener.getValue(Panel.Settings)), lit = engine.sleep != Sleep.Off)
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
    Column(Modifier.padding(start = Theme.edge, end = Theme.edge, top = 54.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      // The film's logo, as on its page; its name where it has none.
      app.nama.androidtv.ui.TitleMark(post, height = 84, maxWidth = 620) {
        Text(post.title(loc.lang), style = app(40, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (post.isSeries || engine.items.size > 1) {
        val line = loc.episodeTag(item.season, Episode.label(item.episodeRaw, engine.index + 1)) + (item.title(loc.lang)?.let { " · $it" } ?: "")
        Text(line, style = app(27, FontWeight.Medium), color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/**
 * The Mac player's round button: a plain glyph, a white disc in focus. While [lit] (subtitles on, a
 * speed other than 1×) a faint disc: filled white, it looked like a second focus beside the real one.
 * A [badge] sits inside the glyph (the seconds of a skip); [mirror] turns the glyph the other way.
 */
@Composable
private fun PlayerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    lit: Boolean = false, size: Int = 84, glyph: Int = 44, badge: String? = null, mirror: Boolean = false,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  androidx.tv.material3.Surface(
      onClick = onClick,
      modifier = modifier.semantics { contentDescription = label },
      interactionSource = interaction,
      shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
      colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
          containerColor = if (lit) Color.White.copy(alpha = 0.18f) else Color.Transparent,
          focusedContainerColor = Color.White,
          contentColor = Color.White,
          focusedContentColor = Theme.focusInk,
      ),
      scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
  ) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
      androidx.tv.material3.Icon(icon, null, Modifier.size(glyph.dp).graphicsLayer { if (mirror) scaleX = -1f })
      // In the ring's middle, which sits a little below the glyph's.
      badge?.let { Text(it, Modifier.offset(y = (glyph / 22f).dp), style = app((glyph * 0.32f).toInt(), FontWeight.ExtraBold).copy(fontFeatureSettings = "tnum")) }
    }
  }
}

/**
 * The seek bar, as the Mac's: a thin line in the title's colour, a little thicker with a thumb while
 * seeking. With the controls up it takes focus too, above the buttons (the aliir74 app's): left and
 * right seek as they do with nothing on screen, select lands the seek or, with none waiting, plays
 * and pauses.
 */
@Composable
private fun Scrubber(engine: PlayerEngine, seekTarget: Double?, focusable: Boolean, seek: (sign: Int, repeat: Boolean) -> Unit, land: () -> Boolean) {
  val d = engine.duration
  val shown = seekTarget ?: engine.position
  val fraction = if (d > 0) (shown / d).toFloat().coerceIn(0f, 1f) else 0f
  val ready = if (d > 0) (engine.buffered / d).toFloat().coerceIn(0f, 1f) else 0f
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val active = seekTarget != null || (focusable && focused)
  val hue = LocalHue.current ?: Theme.accent
  val loc = LocalLoc.current
  // The file's named stretches, drawn on the line as the Mac's and Windows': the recap, the intro
  // and the credits; while seeking into one, its name under the picture.
  val item = engine.current
  val marks = remember(item, d) {
    if (d <= 0) emptyList() else listOfNotNull(
        item.recapEnd?.takeIf { it > 0 }?.let { Triple(item.recapStart ?: 0.0, it, S.recap) },
        item.introEnd?.takeIf { it > 0 }?.let { Triple(item.introStart ?: 0.0, it, S.intro) },
        item.creditsStart?.takeIf { it > 0 && it < d }?.let { Triple(it, d, S.credits) },
    )
  }
  // While seeking, the picture at that point rides above the bar's thumb.
  LaunchedEffect(seekTarget) { seekTarget?.let { engine.preview.want(it) } }
  val frame = engine.preview.frame
  val inMark = seekTarget?.let { at -> marks.firstOrNull { at >= it.first && at <= it.second } }
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
          inMark?.let { Text(loc(it.third), style = app(20, FontWeight.Bold), color = Color.White) }
        }
      }
    }
    Box(
        Modifier.fillMaxWidth().height(28.dp)
            .then(if (!focusable) Modifier else Modifier
                .onKeyEvent { e ->
                  if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                  val repeat = e.nativeKeyEvent.repeatCount > 0
                  when (e.key) {
                    // The bar runs left to right in every language, so left is always back.
                    Key.DirectionLeft -> { seek(-1, repeat); true }
                    Key.DirectionRight -> { seek(1, repeat); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                      if (!repeat && !land()) engine.togglePlay()
                      true
                    }
                    else -> false
                  }
                }
                
                .focusable(interactionSource = interaction))
            .padding(horizontal = 16.dp).drawBehind {
          val y = size.height / 2
          val h = (if (active) 8 else 5).dp.toPx()
          fun bar(to: Float, color: Color) = drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * to, h), CornerRadius(h / 2))
          bar(1f, Color.White.copy(alpha = 0.24f))
          bar(ready, Color.White.copy(alpha = 0.14f))
          bar(fraction, hue)
          if (d > 0) for ((from, to, _) in marks) {
            val x = size.width * (from / d).toFloat()
            val w = maxOf(4.dp.toPx(), size.width * ((to - from) / d).toFloat())
            drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(x, y - h / 2), Size(w, h), CornerRadius(h / 2))
          }
          if (active) drawCircle(Color.White, 9.dp.toPx(), Offset(size.width * fraction, y))
        })
  }
}

// MARK: Panels

/**
 * One row of a panel. Its [title] in large type; a [detail] under it, small and dim (a release's
 * name, a translation's progress); the [value] now chosen at its end, for a row that [opens] a list
 * of its own; or, for a [header], the name of a group of rows.
 */
private data class Choice(
    val title: String, val selected: Boolean = false, val detail: String? = null, val value: String? = null,
    val opens: Boolean = false, val header: Boolean = false, val run: () -> Unit = {},
)

/** Whether the bubble has anything to offer: a second sound, or subtitles of any kind. */
private fun hasSoundOrSubtitles(engine: PlayerEngine): Boolean =
    (engine.quality?.mixes?.size ?: 0) > 1 || engine.audioTracks.isNotEmpty() ||
        engine.current.subtitles.isNotEmpty() || engine.found.isNotEmpty() || engine.canTranslate || engine.subtitleLanguage == PlayerEngine.TRANSLATED

/** A panel of choices on the right of the picture: select applies at once, and the film plays on behind it. */
@Composable
private fun SidePanel(engine: PlayerEngine, panel: Panel, go: (Panel) -> Unit, close: () -> Unit) {
  val loc = LocalLoc.current
  val item = engine.current
  fun language(code: String) = java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.forLanguageTag(loc.lang.name)).ifEmpty { code }
  fun mixName(m: AudioMix) = loc(if (m == AudioMix.stereo) S.audioStereo else if (m == AudioMix.surround) S.audioSurround else S.audioOriginal)
  fun sleepName(s: Sleep) = when (s) {
    Sleep.Off -> loc(S.off)
    Sleep.EndOfEpisode -> loc(if (engine.post.isSeries) S.endOfEpisode else S.endOfFilm)
    is Sleep.Minutes -> "${loc.num(s.n)} ${loc(S.min)}"
  }
  val (title, choices) = when (panel) {
    Panel.Subtitles -> {
      val list = mutableListOf<Choice>()
      val mixes = engine.quality?.mixes?.takeIf { it.size > 1 }.orEmpty()
      if (mixes.isNotEmpty() || engine.audioTracks.isNotEmpty()) {
        list += Choice(loc(S.audio), header = true)
        mixes.forEach { m -> list += Choice(mixName(m), m == engine.mix) { engine.chooseMix(m) } }
        engine.audioTracks.forEach { l -> list += Choice(language(l), l == engine.audioTrack) { engine.chooseAudioTrack(l) } }
      }
      list += Choice(loc(S.subtitles), header = true)
      list += Choice(loc(S.off), engine.subtitleLanguage == "off") { engine.selectSubtitle("off", true) }
      listOf("fa", "en").filter { item.subtitles[it] != null }.forEach { l ->
        list += Choice(loc(if (l == "fa") S.persian else S.english), engine.subtitleLanguage == l) { engine.selectSubtitle(l, true) }
      }
      // Found ones are named by their release, under the language; the same name twice gets a number.
      val seen = HashMap<String, Int>()
      engine.found.forEach { c ->
        val n = (seen[c.name] ?: 0) + 1
        seen[c.name] = n
        val code = PlayerEngine.FOUND + c.id
        list += Choice(loc(S.persian), engine.subtitleLanguage == code, detail = c.name + if (n > 1) " (${loc.num(n)})" else "") { engine.selectSubtitle(code, true) }
      }
      // English put into Persian by Gemini, when there is nothing Persian to choose.
      if (engine.canTranslate || engine.subtitleLanguage == PlayerEngine.TRANSLATED) {
        val target = Translation.target(loc.lang)
        val name = if (target == Lang.ar) Lang.ar.nativeName else loc(S.persian)
        val progress = engine.translated?.takeIf { it < 1f }?.let { " · ${loc.num((it * 100).toInt())}${if (loc.lang == Lang.en) "%" else "٪"}" }.orEmpty()
        list += Choice(name, engine.subtitleLanguage == PlayerEngine.TRANSLATED, detail = tv(TV.machineTranslation) + progress) { engine.selectSubtitle(PlayerEngine.TRANSLATED, true) }
      }
      // The nudge in words, never a sign: a minus would land at the wrong end of a Persian line.
      if (engine.subtitleLanguage != "off") {
        val step = "${loc.num("0.25")} ${loc(S.secondsShort)}"
        list += Choice(loc(S.subtitleEarlier), value = step) { engine.nudgeSubtitles(-0.25) }
        list += Choice(loc(S.subtitleLater), value = step) { engine.nudgeSubtitles(0.25) }
        if (engine.subtitleOffset != 0.0) list += Choice(loc(S.reset), value = "${loc.num(String.format(java.util.Locale.ROOT, "%+.2f", engine.subtitleOffset))} ${loc(S.secondsShort)}") { engine.nudgeSubtitles(0.0) }
      }
      tv(TV.audioAndSubtitles) to list
    }
    Panel.Quality -> loc(S.quality) to (
        // "Max", the download file, first when one fits (the Tizen app's).
        listOfNotNull(engine.directFor()?.let { f -> Choice("Max", engine.direct?.id == f.id, detail = Direct.label(f)) { engine.playDirect(f, engine.position) } }) +
            item.qualities.map { q -> Choice(if (q.isAuto) loc(S.qualityAuto) else q.label, engine.direct == null && q == engine.quality) { engine.chooseQuality(q) } })
    Panel.Speed -> loc(S.speed) to listOf(0.5f, 0.75f, 0.9f, 1f, 1.05f, 1.1f, 1.25f, 1.5f, 1.75f, 2f).map { s -> Choice(loc.num(s.toString().removeSuffix(".0")) + "×", s == engine.speed) { engine.chooseSpeed(s) } }
    Panel.Sleep -> loc(S.sleepTimer) to (listOf(Sleep.Off, Sleep.EndOfEpisode) + listOf(15, 30, 45, 60).map { Sleep.Minutes(it) }).map { s -> Choice(sleepName(s), s == engine.sleep) { engine.chooseSleep(s) } }
    Panel.Episodes -> "" to emptyList()
    Panel.Settings -> {
      // Each row says what is chosen now, at its end, and opens its own list.
      val list = mutableListOf<Choice>()
      if (item.qualities.size > 1) {
        val now = engine.direct?.let { "Max" } ?: engine.quality?.let { if (it.isAuto) loc(S.qualityAuto) else it.label } ?: loc(S.qualityAuto)
        list += Choice(loc(S.quality), value = now, opens = true) { go(Panel.Quality) }
      }
      list += Choice(loc(S.sleepTimer), value = sleepName(engine.sleep), opens = true) { go(Panel.Sleep) }
      loc(S.settings) to list
    }
  }
  // Focus starts on what is chosen, or on the first row that can be chosen.
  val start = choices.indexOfFirst { it.selected && !it.header }.takeIf { it >= 0 } ?: choices.indexOfFirst { !it.header }.coerceAtLeast(0)
  val first = remember(panel) { FocusRequester() }
  val state = rememberLazyListState()
  LaunchedEffect(panel) {
    state.scrollToItem((start - 1).coerceAtLeast(0))
    withFrameNanos {}
    runCatching { first.requestFocus() }
  }
  val shape = RoundedCornerShape(36.dp)
  // A card floating on the right, as the title page's sheets are, not a slab down the whole edge.
  Column(
      Modifier.absolutePadding(right = 56.dp).width(620.dp).heightIn(max = 920.dp)
          .background(Theme.surface.copy(alpha = 0.94f), shape).border(1.dp, Theme.glassEdge, shape)
          .padding(horizontal = 24.dp, vertical = 34.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(title, Modifier.padding(start = 20.dp, bottom = 10.dp), style = app(34, FontWeight.Bold), color = Theme.text1, maxLines = 1)
    LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
      items(choices.size) { i ->
        val c = choices[i]
        if (c.header) PanelHeader(c.title, first = i == 0)
        else ChoiceRow(c, if (i == start) Modifier.focusRequester(first) else Modifier)
      }
    }
  }
}

/** The name of a group of rows (Audio, Subtitles): small, dim, apart from the rows above it. */
@Composable
private fun PanelHeader(name: String, first: Boolean) {
  Text(name, Modifier.padding(start = 20.dp, top = if (first) 4.dp else 22.dp, bottom = 6.dp), style = app(20, FontWeight.SemiBold), color = Theme.text3, maxLines = 1)
}

/**
 * A row of a panel, in the app's focus: lit glass and the turning ring, the text staying white (a
 * white bar with dark text made the panel flash as focus moved). What is chosen has the accent's
 * check and a heavier weight, in focus or not.
 */
@Composable
private fun ChoiceRow(c: Choice, modifier: Modifier) {
  val interaction = remember { MutableInteractionSource() }
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val shape = RoundedCornerShape(20.dp)
  androidx.tv.material3.Surface(
      onClick = c.run,
      modifier = modifier.fillMaxWidth(),
      interactionSource = interaction,
      shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
      colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
          containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.14f), pressedContainerColor = Color.White.copy(alpha = 0.20f),
          contentColor = Theme.text1, focusedContentColor = Theme.text1, pressedContentColor = Theme.text1,
      ),
      scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.98f),
  ) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // The check's place is kept on every row of a list, so the names line up whether chosen or not.
      if (!c.opens) Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (c.selected) Icon(Icons.check, null, Modifier.size(28.dp), tint = Theme.accent)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(c.title, style = app(26, if (c.selected) FontWeight.Bold else FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        c.detail?.let { Text(it, style = app(19, FontWeight.Medium), color = Theme.text3, maxLines = 1, overflow = TextOverflow.Ellipsis) }
      }
      c.value?.let { Text(it, style = app(22, FontWeight.Medium), color = Theme.text2, maxLines = 1) }
      if (c.opens) Icon(Icons.chevronEnd, null, Modifier.size(28.dp).graphicsLayer { if (rtl) scaleX = -1f }, tint = Theme.text3)
    }
  }
}

/**
 * The series' episodes on a card at the right of the picture, as the Mac's: the series' scene at
 * the head, then its seasons by name, one of them open at a time, and the open season's episodes
 * under it. The one playing is lit in the series' colour, the ones seen ticked.
 */
@Composable
private fun EpisodesPanel(engine: PlayerEngine, close: () -> Unit) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val post = engine.post
  val seasons = engine.items.map { it.season }.distinct().sorted()
  val titled = seasons.size > 1
  var open by remember { mutableStateOf<Int?>(engine.current.season) }
  // The rows as one list: a season's bar, then its episodes while it is open.
  val rows = buildList {
    for (season in seasons) {
      if (titled) add(-season - 1)
      if (!titled || open == season) engine.items.withIndex().filter { it.value.season == season }.forEach { add(it.index) }
    }
  }
  val playingRow = rows.indexOf(engine.index)
  val playingTile = remember { FocusRequester() }
  val state = rememberLazyListState()
  LaunchedEffect(Unit) {
    if (playingRow >= 0) state.scrollToItem((playingRow - 1).coerceAtLeast(0))
    withFrameNanos {}
    runCatching { playingTile.requestFocus() }
  }
  val shape = RoundedCornerShape(36.dp)
  CompositionLocalProvider(LocalHue provides (LocalHue.current ?: rememberPosterTint(post))) {
  Column(
      Modifier.absolutePadding(right = 56.dp).padding(vertical = 60.dp).width(640.dp).fillMaxHeight()
          .clip(shape).background(Theme.surface.copy(alpha = 0.94f)).border(1.dp, Theme.glassEdge, shape),
  ) {
    // The series' scene, fading into the card, with its name and how many seasons it has.
    Box(Modifier.fillMaxWidth().height(180.dp)) {
      RemoteImage(post.cover ?: post.poster.big ?: post.poster.large, Modifier.fillMaxSize(), blurhash = post.poster.blurhash, pixelWidth = 800)
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Theme.surface.copy(alpha = 0.96f)))))
      Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 30.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(post.title(loc.lang), style = app(30, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val count = "${loc.num(engine.items.size)} ${loc(S.episodeCount)}"
        Text(if (titled) "${loc.num(seasons.size)} ${loc(S.seasonsCount)} · $count" else count, style = app(20, FontWeight.Medium), color = Color.White.copy(alpha = 0.7f))
      }
    }
    LazyColumn(state = state, contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      items(rows.size, key = { rows[it] }) { r ->
        val row = rows[r]
        if (row < 0) {
          val season = -row - 1
          SeasonBar(season, engine.items.count { it.season == season }, open == season, playing = season == engine.current.season) {
            open = if (open == season) null else season
          }
        } else {
          val item = engine.items[row]
          val playing = row == engine.index
          EpisodeRow(
              item, row + 1, playing, seen = item.fileID in app.watched,
              progress = if (playing && engine.duration > 0) (engine.position / engine.duration).toFloat() else null,
              modifier = if (playing) Modifier.focusRequester(playingTile) else Modifier,
          ) {
            close()
            engine.start(row)
          }
        }
      }
    }
  }
  }
}

/** A season's name, which opens and closes its episodes: the season, how many, a chevron. */
@Composable
private fun SeasonBar(season: Int, count: Int, open: Boolean, playing: Boolean, toggle: () -> Unit) {
  val loc = LocalLoc.current
  val interaction = remember { MutableInteractionSource() }
  val shape = RoundedCornerShape(20.dp)
  val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
  androidx.tv.material3.Surface(
      onClick = toggle,
      modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
      interactionSource = interaction,
      shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
      colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
          containerColor = Color.White.copy(alpha = if (open) 0.08f else 0.04f), focusedContainerColor = Color.White.copy(alpha = 0.16f),
          contentColor = Theme.text1, focusedContentColor = Theme.text1,
      ),
      scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.98f),
  ) {
    Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("${loc(S.season)} ${loc.num(season)}", style = app(25, FontWeight.Bold))
      if (playing) Box(Modifier.size(10.dp).background(LocalHue.current ?: Theme.accent, CircleShape))
      Text("${loc.num(count)} ${loc(S.episodeCount)}", style = app(19, FontWeight.Medium), color = Theme.text3)
      Spacer(Modifier.weight(1f))
      Icon(Icons.chevronDown, null, Modifier.size(30.dp).graphicsLayer { rotationZ = turn }, tint = Theme.text2)
    }
  }
}

/**
 * One episode, as the Mac's row: its number on a tile of its own, filled in the series' colour for
 * the one playing; its name; "Now playing" or its length; how far in; a tick once seen.
 */
@Composable
private fun EpisodeRow(item: app.nama.core.StreamItem, position: Int, playing: Boolean, seen: Boolean, progress: Float?, modifier: Modifier, onClick: () -> Unit) {
  val loc = LocalLoc.current
  val tint = LocalHue.current ?: Theme.accent
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val shape = RoundedCornerShape(22.dp)
  androidx.tv.material3.Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      interactionSource = interaction,
      shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
      colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
          containerColor = Color.White.copy(alpha = if (playing) 0.08f else 0f), focusedContainerColor = Color.White.copy(alpha = 0.14f),
          contentColor = Theme.text1, focusedContentColor = Theme.text1,
      ),
      scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.98f),
  ) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      Box(
          Modifier.size(66.dp).background(if (playing) tint else Color.White.copy(alpha = if (focused) 0.16f else 0.08f), RoundedCornerShape(16.dp)),
          contentAlignment = Alignment.Center,
      ) {
        if (focused && !playing) Icon(Icons.play, null, Modifier.size(30.dp), tint = Theme.text1)
        else Text(loc.num(Episode.label(item.episodeRaw, position)), style = app(24, FontWeight.ExtraBold), color = if (playing) Color.White else Theme.text2, maxLines = 1)
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(item.title(loc.lang) ?: "${loc(S.episode)} ${loc.num(item.episodeNumber)}", style = app(24, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (playing) Text(loc(S.nowPlaying), style = app(18, FontWeight.Bold), color = tint)
          item.durationMinutes?.let { Text("${loc.num(it)} ${loc(S.min)}", style = app(18), color = Theme.text3) }
        }
        // Left to right in every language, as the seek bar runs.
        progress?.let { p ->
          Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)) {
            Box(Modifier.align(androidx.compose.ui.AbsoluteAlignment.CenterLeft).fillMaxWidth(p.coerceIn(0.02f, 1f)).height(5.dp).background(tint, CircleShape))
          }
        }
      }
      if (seen && !playing) Icon(Icons.check, null, Modifier.size(28.dp), tint = Theme.text3)
    }
  }
}

/** What shows before the film moves: its artwork pushing slowly in, with its name and a spinner. */
@Composable
private fun Curtain(engine: PlayerEngine, modifier: Modifier) {
  val loc = LocalLoc.current
  val calm = LocalCalm.current
  val post = engine.post
  val item = engine.current
  val near = remember { Animatable(1f) }
  LaunchedEffect(Unit) { if (!calm) near.animateTo(1.07f, tween(8000, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
  Box(modifier.fillMaxSize().background(Color.Black)) {
    app.nama.androidtv.ui.RemoteImage(
        post.cover ?: post.poster.big ?: post.poster.large,
        Modifier.fillMaxSize().graphicsLayer {
          scaleX = near.value
          scaleY = near.value
        },
        blurhash = post.poster.blurhash, pixelWidth = 1280,
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.8f)))))
    Column(Modifier.align(Alignment.BottomStart).padding(start = Theme.edge, end = Theme.edge, bottom = 230.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(post.title(loc.lang), style = app(58, FontWeight.ExtraBold), color = Theme.text1, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Spinner(Modifier.size(28.dp), Color.White)
        if (post.isSeries || engine.items.size > 1) {
          Text(loc.episodeTag(item.season, Episode.label(item.episodeRaw, engine.index + 1)), style = app(24, FontWeight.SemiBold), color = Theme.text2, maxLines = 1)
        }
      }
    }
  }
}

/** How often the light around the picture takes a new frame. */
private const val GLANCE_MS = 4000L
