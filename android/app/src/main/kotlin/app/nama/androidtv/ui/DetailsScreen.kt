package app.nama.androidtv.ui

import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.nama.androidtv.DemoStream
import app.nama.androidtv.PlaybackRequest
import app.nama.androidtv.attempt
import app.nama.core.Episode
import app.nama.core.Fmt
import app.nama.core.Lang
import app.nama.core.Person
import app.nama.core.Post
import app.nama.core.S
import app.nama.core.StreamItem
import app.nama.core.SubSource
import app.nama.core.StreamResult
import app.nama.core.TrailerVideo
import app.nama.core.single
import kotlinx.coroutines.launch

/** A title's page: the pitch and the play button over its backdrop, then episodes, storyline and cast. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(route: Route.Details) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  var loaded by remember { mutableStateOf<Post?>(null) }
  var stream by remember { mutableStateOf<StreamResult?>(null) }
  var streamPending by remember { mutableStateOf(true) }
  var failed by remember { mutableStateOf(false) }
  /** The season open in the list, and which group of fifty episodes of it for a long one. */
  var shelf by remember { mutableStateOf<Shelf?>(null) }
  /** "Play" was pressed (or asked for by the hero) before the stream was known. */
  var playWhenReady by remember { mutableStateOf(route.autoplay) }
  /** Why the server gave no stream, in its own words. */
  var refusal by remember { mutableStateOf<String?>(null) }
  var trailer by remember { mutableStateOf<TrailerVideo?>(null) }
  var trailerTried by remember { mutableStateOf(false) }
  /** How many of the header's buttons have come in (Header, `comingIn`). */
  var revealed by remember { mutableIntStateOf(0) }
  val play = remember { FocusRequester() }
  val episodesFocus = remember { FocusRequester() }

  val post = loaded ?: route.preview
  val items = stream?.items.orEmpty()

  fun explainNoStream() = app.showToast(if (app.isLoggedIn) refusal ?: loc(S.noStream) else loc(S.loginToWatch))

  fun startPlayback(index: Int? = null) {
    // Read from the state, not from `post`: these run long after the composition that made them.
    val p = loaded ?: route.preview ?: return
    if (streamPending) {
      playWhenReady = true
      return
    }
    val s = stream?.takeIf { it.items.isNotEmpty() } ?: return explainNoStream()
    app.play(PlaybackRequest(p, s.items, index ?: s.startIndex, s.resume, pickedByUser = index != null))
  }

  /** Stream files need a subscription; guests only get them for free titles. */
  suspend fun loadStream(quietly: Boolean) {
    val p = loaded ?: route.preview ?: return
    if (DemoStream.enabled) {
      stream = DemoStream.result(p).also { r -> r.items.getOrNull(r.startIndex)?.let { shelf = Shelf.of(r.items, it) } }
      streamPending = false
      if (playWhenReady) {
        playWhenReady = false
        startPlayback()
      }
      return
    }
    if (!app.isLoggedIn && !p.freeStream) {
      streamPending = false
      if (playWhenReady) {
        playWhenReady = false
        explainNoStream()
      }
      return
    }
    if (!quietly) streamPending = true
    val e = attempt { app.api.call("stream", mapOf("post_id" to p.id.toString(), "free_stream" to if (app.isLoggedIn) "0" else "1")) }
    val result = e?.takeIf { it.success }?.let { StreamResult.from(it.result) }
    refusal = if (e?.success == false) e.message else null
    if (result != null) {
      stream = result
      // Back from the player too: autoplay may have carried on into another season.
      result.items.getOrNull(result.startIndex)?.let { first -> shelf = Shelf.of(result.items, first) }
    } else if (!quietly) stream = null
    streamPending = false
    if (playWhenReady) {
      playWhenReady = false
      startPlayback()
    }
  }

  LaunchedEffect(route.id) {
    failed = false
    val fetched = attempt { app.api.single(route.id) }
    if (fetched != null) loaded = fetched else if (route.preview == null) failed = true
    loadStream(quietly = false)
  }
  // Focus starts on Play: the shell enters the page there, and waits for it (PageEntry).
  DeclarePageEntry()
  val scroll = rememberScrollState()
  val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
  val minimal = remember(margin) { Minimal(margin) }
  LaunchedEffect(post?.id) {
    val p = post ?: return@LaunchedEffect
    // Only once a trailer has been found, so there is never a button that leads nowhere.
    trailer = app.extras.trailer(p, loc.lang, app.inIran, patienceMs = 1_500)
    trailerTried = true
  }
  // As the Mac's: the buttons come in once what changes their size is known, Play's label (it
  // says "Resume" once the stream is in) and whether there is a trailer, two seconds at most.
  // Coming as each was ready, they pushed each other along. Then one after another, Play first.
  val calm = LocalCalm.current
  LaunchedEffect(route.id) {
    revealed = 0
    val start = System.currentTimeMillis()
    while ((streamPending || !trailerTried) && System.currentTimeMillis() - start < 2_000) delay(100)
    for (step in 1..7) {
      revealed = step
      if (!calm) delay(70)
    }
  }
  // The bar stays over the page while it is at its top, and steps out of the way below.
  val chrome = LocalChrome.current
  LaunchedEffect(scroll) { androidx.compose.runtime.snapshotFlow { scroll.value < 60 }.collect { chrome.routeAtTop = it } }
  FollowBar { scroll.animateScrollTo(0) }
  // Back from the player: pick up the new resume point.
  LaunchedEffect(app.watchVersion) { if (stream != null) loadStream(quietly = true) }

  // The page is lit in the colour of the title it is about, as on the Mac.
  // In the colour of its scene picture once that is worked out (SceneTint), the poster's until then.
  val hue = rememberSceneTint(post)
  CompositionLocalProvider(LocalHue provides hue) {
  Box(Modifier.fillMaxSize()) {
    // A few seconds on the page with nothing else going on: the film itself plays behind it.
    val small = items.firstOrNull()?.qualities?.let { q -> q.filter { !it.isAuto && it.height > 0 }.minByOrNull { it.height } ?: q.firstOrNull() }
    val nav = LocalNav.current
    val looking = app.playback == null && app.trailer == null && app.sheets.shown == null && nav.top == route
    Stage(post, dim = 0.3f, settleMs = 0, tint = hue, deep = true) {
      // Keyed on the film being known as well, so the clip is chosen with what is known by then.
      Teaser(post?.id?.takeIf { small != null || app.inIran }?.let { it to (small != null) }, {
        // In Iran Aparat's trailer first; Aparat checks where its video is played from.
        val p = loaded ?: route.preview
        val aparat = if (app.inIran && p != null) {
          app.extras.lookAparat(p)
          app.extras.aparat[p.id]?.let { app.extras.aparatSmallFile(it.uid) }
        } else null
        aparat?.let { TeaserClip(it, trailer = true, referer = "https://www.aparat.com/") } ?: small?.url?.let { TeaserClip(it, trailer = false) }
      }, looking)
    }
    when {
      post != null -> CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides minimal) { Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(64.dp)) {
        Header(
            post, stream, items, playWhenReady && streamPending, trailer, revealed,
            play = play,
            onPlay = { startPlayback() },
            onTrailer = { t -> openTrailer(t, post, app, scope, context) },
            // A section: Play, the stars or the trailer in focus keep the whole header, and so the
            // page's top, in view.
            modifier = Modifier.wholeSection(),
        )
        // Each part below the header comes in softly as it arrives, rather than appearing at once.
        if (post.isSeries && items.size > 1) {
          Arriving { Episodes(post, items, stream, shelf ?: Shelf.of(items, items.first()), { shelf = it }, episodesFocus) { startPlayback(it) } }
        }
        if (post.cast.isNotEmpty()) Arriving { Cast(post) }
        // From TMDB, with the user's own key: the people behind the camera, and reviews.
        app.extras.known[post.id]?.let { extra ->
          if (extra.crew.isNotEmpty()) Arriving { Crew(extra.crew) }
          if (extra.reviews.isNotEmpty()) Arriving { Reviews(extra.reviews) }
          // TMDB's recommendations that 30nama has (the Tizen app's).
          val similar by androidx.compose.runtime.produceState(emptyList<Post>(), extra.similar) { value = app.moreLikeThis(extra.similar) }
          if (similar.isNotEmpty()) Arriving { MoreLikeThis(similar) }
        }
        // A plot longer than the header shows: whole here, as the Apple TV's storyline.
        if (post.plot(LocalLoc.current.lang).length > 260) Arriving { Storyline(post) }
        Arriving { Facts(post) }
        Spacer(Modifier.height(40.dp))
      } }
      failed -> ErrorState {
        failed = false
        // The stream too: it was not asked for while there was no title to ask for.
        scope.launch {
          attempt { app.api.single(route.id) }?.let {
            loaded = it
            loadStream(quietly = false)
          } ?: run { failed = true }
        }
      }
    }
  }
  }
}

private var trailerLook: kotlinx.coroutines.Job? = null

/** A trailer: YouTube's in the YouTube app (or the browser), Aparat's in the app's own player. */
private fun openTrailer(t: TrailerVideo, post: Post, app: app.nama.androidtv.AppModel, scope: kotlinx.coroutines.CoroutineScope, context: android.content.Context) {
  when (t) {
    is TrailerVideo.Youtube -> {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${t.id}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try {
        context.startActivity(intent)
      } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${t.id}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { app.showToast(app.loc(S.trailerUnreachable)) }
      }
    }
    is TrailerVideo.Aparat -> {
      // One look at a time, and none that lands over a film started meanwhile.
      trailerLook?.cancel()
      trailerLook = scope.launch {
        val file = app.extras.aparatFile(t.uid)
        when {
          app.playback != null -> Unit
          file == null -> app.showToast(app.loc(S.trailerNotLoaded))
          else -> app.playTrailer(post.title(app.settings.lang), file)
        }
      }
    }
  }
}

// MARK: Header

/** A part of the page that fades in and rises the last 16 points the first time it is drawn. */
@Composable
private fun Arriving(content: @Composable () -> Unit) {
  val calm = LocalCalm.current
  val shown = remember { androidx.compose.animation.core.Animatable(if (calm) 1f else 0f) }
  LaunchedEffect(Unit) { shown.animateTo(1f, androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
  // Each part a section: entering it brings its title into view with what has focus.
  Box(Modifier.wholeSection().graphicsLayer {
    alpha = shown.value
    translationY = (1f - shown.value) * 16.dp.toPx()
  }) { content() }
}

/** Where the title's page lays its poster out, shared with the poster that flies there (Shell). */
const val DETAILS_TOP = 140
const val DETAILS_POSTER_W = 360
const val DETAILS_POSTER_H = 540

/**
 * The Mac's `comingIn`: in its place from the start, so nothing moves when it shows; seen once
 * [revealed] reaches [order], rising the last 10 points as it comes. It can take focus all along,
 * so Play holds focus from the first frame and is simply not drawn yet.
 */
@Composable
private fun Modifier.comingIn(order: Int, revealed: Int): Modifier {
  val calm = LocalCalm.current
  // The Mac's spring: a response of 0.42 s, damping 0.82.
  val shown by animateFloatAsState(
      if (revealed >= order) 1f else 0f,
      if (calm) snap() else spring(dampingRatio = 0.82f, stiffness = 224f),
      label = "coming in",
  )
  return graphicsLayer {
    alpha = shown
    translationY = (1f - shown) * 10.dp.toPx()
  }
}

/**
 * The Mac's header: the poster, lit in its own colour, beside what the title is (its logo or its
 * name, scores, standing, story) and one row of everything to do with it.
 */
@Composable
private fun Header(
    post: Post,
    stream: StreamResult?,
    items: List<StreamItem>,
    busy: Boolean,
    trailer: TrailerVideo?,
    revealed: Int,
    play: FocusRequester,
    onPlay: () -> Unit,
    onTrailer: (TrailerVideo) -> Unit,
    modifier: Modifier,
) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val scope = rememberCoroutineScope()
  val hue = LocalHue.current ?: Color.White
  val shape = RoundedCornerShape(24.dp)
  val flying = LocalNav.current.flight?.post?.id == post.id
  // The light behind the poster breathes, slowly, in the poster's own colour (the owner's choice
  // among five). Read only where drawn; still at its middle on a weak TV or with reduced motion.
  val breathes = LocalLively.current && !LocalCalm.current
  val breath = remember { androidx.compose.animation.core.Animatable(0.5f) }
  LaunchedEffect(breathes) {
    if (!breathes) return@LaunchedEffect breath.snapTo(0.5f)
    while (true) {
      breath.animateTo(1f, tween(BREATH_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
      breath.animateTo(0f, tween(BREATH_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
  }
  Row(modifier.fillMaxWidth().padding(start = Theme.edge, end = Theme.edge, top = DETAILS_TOP.dp), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
    // Over its blur code at once, the large picture following.
    Box(
        Modifier.size(DETAILS_POSTER_W.dp, DETAILS_POSTER_H.dp)
            // Held back, frame and all, while the pressed card's poster is still flying to this spot.
            .graphicsLayer { alpha = if (flying) 0f else 1f }
            // The breathing light: a soft pool of the poster's colour behind it, a little below its
            // middle, swelling and fading.
            .drawBehind {
              val b = breath.value
              val r = size.height * (0.6f + 0.25f * b)
              val c = Offset(size.width / 2f, size.height * 0.56f)
              drawCircle(
                  Brush.radialGradient(listOf(hue.copy(alpha = 0.15f + 0.65f * b), hue.copy(alpha = 0.05f + 0.30f * b), Color.Transparent), c, r),
                  radius = r, center = c,
              )
            }
            .shadow(48.dp, shape, ambientColor = hue.copy(alpha = 0.5f), spotColor = hue.copy(alpha = 0.6f))
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    ) {
      // The cards' own request, in memory already: the landed poster is never a blur.
      RemoteImage(post.poster.url(Theme.posterWidth.value * 1.1), Modifier.fillMaxSize(), blurhash = post.poster.blurhash, pixelWidth = (Theme.posterWidth.value * 1.2f).toInt())
      RemoteImage(post.poster.big ?: post.poster.large, Modifier.fillMaxSize(), pixelWidth = 540)
    }
    // A soft shadow under every line of the header: over a busy picture the words and chips were
    // hard to read from the sofa (the owner).
    CompositionLocalProvider(androidx.tv.material3.LocalTextStyle provides androidx.tv.material3.LocalTextStyle.current.copy(shadow = TEXT_SHADOW)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
      // The kind line, and beside it as the Apple TV's: Persian subtitles, and a series' next episode.
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(loc.kindLine(post), style = app(24, FontWeight.SemiBold), color = Theme.text2)
        if (post.persianSubtitle) Chip {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.subtitles, null, Modifier.size(20.dp), tint = Theme.text1)
            Text(loc(S.persianSub), style = app(20, FontWeight.SemiBold), color = Theme.text1)
          }
        }
        val next = if (loc.lang == Lang.fa) post.nextEpisodeDate.jalali else post.nextEpisodeDate.gregorian
        if (post.isSeries && next != null) {
          Text(
              "${post.nextEpisodeCaption ?: loc(S.nextEpisode)} · $next",
              Modifier.background(Theme.accent, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 5.dp),
              style = app(20, FontWeight.SemiBold), color = Color.White, maxLines = 1,
          )
        }
      }
      TitleArt(post, size = 84)
      // TMDB's own line for the title, in Persian when it has one, and its score.
      val extra = app.extras.known[post.id]
      // TMDB's line comes after the page: it opens room for itself rather than pushing all below at once.
      androidx.compose.animation.AnimatedVisibility(
          extra?.tagline != null,
          enter = androidx.compose.animation.fadeIn(tween(400)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 80.6f, visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold)),
          exit = androidx.compose.animation.fadeOut(),
      ) { extra?.tagline?.let { Text(it, Modifier.widthIn(max = 1150.dp), style = app(26, FontWeight.Medium).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = Theme.text2, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
      Box(Modifier.padding(top = 2.dp)) { MetaLine(post, genres = 3, allScores = true, tmdbScore = extra?.score) }
      Standing(post)
      Text(
          post.plot(loc.lang), Modifier.widthIn(max = 1150.dp),
          style = app(27).copy(lineHeight = app(27).fontSize * 1.55f), color = Theme.text2, maxLines = 6, overflow = TextOverflow.Ellipsis,
      )
      // Everything to do with the title in one row: play it, its trailer, rate it, keep it.
      Row(Modifier.padding(top = 14.dp).focusRestorer(play), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(playLabel(post, stream, items), onPlay, Modifier.focusRequester(play).pageEntry().comingIn(1, revealed), kind = PillKind.Primary, icon = Icons.play, busy = busy)
        trailer?.let { t -> PillButton(loc(S.trailer), { onTrailer(t) }, Modifier.comingIn(2, revealed), icon = Icons.film) }
        Box(Modifier.comingIn(3, revealed)) { Mine(post) }
        if (app.isLoggedIn) {
          Box(Modifier.comingIn(4, revealed)) {
            RoundButton(Icons.bookmarkOutline, loc(S.watchlist), { scope.launch { app.toggleList(post, "watchlist") } }, on = app.isListed(post, "watchlist"), onIcon = Icons.bookmark)
          }
          Box(Modifier.comingIn(5, revealed)) {
            RoundButton(Icons.heartOutline, loc(S.favorites), { scope.launch { app.toggleList(post, "favorite") } }, on = app.isListed(post, "favorite"), onIcon = Icons.heart)
          }
        }
      }
      val progress = stream?.resume?.progress ?: 0.0
      if (progress > 0 && !post.isSeries) Box(Modifier.width(320.dp).comingIn(6, revealed)) { ProgressBar(progress) }
      if (post.updates.isNotEmpty()) Box(Modifier.comingIn(7, revealed)) { UpdatesTicker(post.updates) }
    }
    }
  }
}

/** What the user makes of the title: "Your rating" and five stars, in one pane of glass. */
@Composable
private fun Mine(post: Post) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val capsule = RoundedCornerShape(50)
  Row(
      Modifier.height(76.dp).glass(capsule, fill = 0.10f).padding(start = 30.dp, end = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(loc(S.yourRating), style = app(24, FontWeight.SemiBold), color = Theme.text2, maxLines = 1)
    StarRating(app.ratings.stars(post.id)) { app.rate(post, it) }
  }
}

/**
 * Where the title stands, as 30nama's own apps show it: places on the top lists, an exclusive
 * subtitle, and how many voted for it and keep it.
 */
@Composable
private fun Standing(post: Post) {
  val loc = LocalLoc.current
  val ranked = post.imdbTop250 != null || post.siteTop100 != null || post.exclusiveSubtitle
  // Each a run of its own: Latin, digits and Persian in one string fall out of order.
  val counts = listOfNotNull(
      post.imdbVotes?.let { listOf("IMDb", Fmt.count(it, loc.lang), loc(S.votes)) },
      post.inWatchlists?.let { listOf(Fmt.count(it, loc.lang), loc(S.inWatchlists)) },
      post.inFavorites?.let { listOf(Fmt.count(it, loc.lang), loc(S.inFavorites)) },
  )
  if (!ranked && counts.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (ranked) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        post.imdbTop250?.let { Rank(loc(S.imdbTop250), it, Theme.imdb) }
        post.siteTop100?.let { Rank(loc(S.siteTop100), it, Theme.accent) }
        if (post.exclusiveSubtitle) Chip {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.subtitles, null, Modifier.size(20.dp), tint = Theme.good)
            Text(loc(S.exclusiveSub), style = app(22, FontWeight.SemiBold), color = Theme.text1)
          }
        }
      }
    }
    if (counts.isNotEmpty()) {
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        counts.forEachIndexed { i, parts ->
          if (i > 0) Text("·", style = app(22), color = Theme.text3)
          Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { parts.forEach { Text(it, style = app(22), color = Theme.text3) } }
        }
      }
    }
  }
}

@Composable
private fun Rank(list: String, place: Int, tint: Color) {
  val loc = LocalLoc.current
  Chip {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.trophy, null, Modifier.size(20.dp), tint = tint)
      Text(list, style = app(22, FontWeight.SemiBold), color = Theme.text1)
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(loc.num("#$place"), style = app(22, FontWeight.SemiBold), color = Theme.text1) }
    }
  }
}

/**
 * What 30nama has lately added or mended on the title, one change at a time with its day, the next
 * coming in every few seconds. Still when there is one, and when motion is turned down.
 */
@Composable
private fun UpdatesTicker(updates: List<app.nama.core.TitleUpdate>) {
  val loc = LocalLoc.current
  val calm = LocalCalm.current
  var index by remember(updates) { mutableStateOf(0) }
  LaunchedEffect(updates, calm) {
    if (updates.size < 2 || calm) return@LaunchedEffect
    while (true) {
      kotlinx.coroutines.delay(4_000)
      index = (index + 1) % updates.size
    }
  }
  androidx.compose.animation.AnimatedContent(
      updates[index.coerceAtMost(updates.size - 1)], Modifier.height(36.dp).widthIn(max = 1100.dp),
      transitionSpec = {
        (androidx.compose.animation.fadeIn(tween(500)) + androidx.compose.animation.slideInVertically(tween(500)) { it / 3 }) togetherWith
            (androidx.compose.animation.fadeOut(tween(500)) + androidx.compose.animation.slideOutVertically(tween(500)) { -it / 3 })
      },
      label = "updates",
  ) { update ->
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      update.day(loc.lang == Lang.fa)?.let { day ->
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(loc.num(day), style = app(22), color = Theme.text3) }
      }
      Text(loc.num(update.title), style = app(22, FontWeight.SemiBold), color = Theme.text1, maxLines = 1)
      update.content?.let { Text(loc.num(it), style = app(22), color = Theme.text2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
  }
}

@Composable
private fun playLabel(post: Post, stream: StreamResult?, items: List<StreamItem>): String {
  val loc = LocalLoc.current
  val item = items.getOrNull(stream?.startIndex ?: 0)
  if (stream?.resume == null || item == null) return if (post.comingSoon) loc(S.comingSoonNote) else loc(S.play)
  if (!post.isSeries) return loc(S.resume)
  return "${loc(S.resume)} · ${loc.episodeTag(item.season, Episode.label(item.episodeRaw, 1))}"
}

/**
 * Five stars. Pressing a star gives that many; pressing it again takes half a star off, and a
 * third press clears the rating. Lit in the title's colour, as on the Mac.
 */
@Composable
private fun StarRating(value: Double?, set: (Double?) -> Unit) {
  val hue = LocalHue.current ?: Theme.imdb
  Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
    for (n in 1..5) {
      val v = value ?: 0.0
      val icon = if (v >= n) Icons.star else if (v >= n - 0.5) Icons.starHalf else Icons.starOutline
      val interaction = remember { MutableInteractionSource() }
      val focused by interaction.collectIsFocusedAsState()
      Surface(
          onClick = {
            set(
                when (value) {
                  n.toDouble() -> n - 0.5
                  n - 0.5 -> null
                  else -> n.toDouble()
                })
          },
          modifier = Modifier.focusRing(interaction, CircleShape, grown = 1.2f),
          interactionSource = interaction,
          shape = ClickableSurfaceDefaults.shape(CircleShape),
          colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White),
          scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
      ) {
        // A half star fills from the reading start, so it is mirrored in a right-to-left language.
        Icon(
            icon, null, Modifier.size(50.dp).padding(8.dp).then(if (LocalLayoutDirection.current == LayoutDirection.Rtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
            tint = if (focused) Theme.focusInk else if (v >= n - 0.5) hue else Theme.text3,
        )
      }
    }
  }
}

// MARK: Episodes

/**
 * Which episodes the list shows: a season, and for a season longer than fifty episodes (long anime)
 * one group of fifty of it, as the official app has them.
 */
private data class Shelf(val season: Int, val group: Int) {
  companion object {
    const val SIZE = 50

    fun of(items: List<StreamItem>, item: StreamItem): Shelf {
      val inSeason = items.filter { it.season == item.season }
      return Shelf(item.season, inSeason.indexOf(item).coerceAtLeast(0) / SIZE)
    }
  }
}

/** The Mac's episode list: seasons as choices on lines that wrap, then the episodes in a grid. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Episodes(post: Post, items: List<StreamItem>, stream: StreamResult?, shelf: Shelf, choose: (Shelf) -> Unit, focus: FocusRequester, play: (Int) -> Unit) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val seasons = stream?.seasons.orEmpty()
  val inSeason = items.withIndex().filter { it.value.season == shelf.season }
  // Names for the episodes 30nama leaves unnamed, from TMDB with the user's key (the Tizen app's).
  val tmdbNames by androidx.compose.runtime.produceState(emptyMap<Int, String>(), post.id, shelf.season, loc.lang, app.extras.known[post.id]?.id) {
    value = if (inSeason.all { it.value.title(loc.lang) != null }) emptyMap() else app.extras.episodeNames(post, shelf.season, loc.lang)
  }
  val groups = (inSeason.size + Shelf.SIZE - 1) / Shelf.SIZE
  val shown = inSeason.drop(shelf.group * Shelf.SIZE).take(Shelf.SIZE)
  val resumeIndex = stream?.startIndex ?: 0
  // Coming down from the header lands on the episode to resume, not on whichever tile is nearest.
  val landing = shown.firstOrNull { it.index == resumeIndex } ?: shown.firstOrNull()

  // The Apple TV's: the heading with the season's count, the seasons in a row of chips, and the
  // episodes as large tiles in one row that slides, the number big at the top, the name at the foot
  // (the owner). A still from the episode itself where one has been taken.
  val nav = LocalNav.current
  val lively = LocalLively.current
  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    Row(Modifier.padding(horizontal = Theme.edge), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      SectionTitle(loc(S.episodes))
      Text("${loc.num(inSeason.size)} ${loc(S.episodeCount)}", Modifier.padding(bottom = 4.dp), style = app(24), color = Theme.text3)
    }
    if (seasons.size > 1 || groups > 1) {
      LazyRow(
          Modifier.bleed(14.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(horizontal = Theme.edge + 14.dp, vertical = 8.dp),
      ) {
        for (s in seasons) {
          val count = items.count { it.season == s }
          val parts = if (s == shelf.season) (count + Shelf.SIZE - 1) / Shelf.SIZE else 1
          for (g in 0 until parts) {
            item(key = "$s/$g") {
              val name = if (parts > 1) {
                val from = g * Shelf.SIZE + 1
                val to = minOf(count, (g + 1) * Shelf.SIZE)
                "${loc(S.season)} ${loc.num(s)} · ${loc.num(from)}–${loc.num(to)}"
              } else "${loc(S.season)} ${loc.num(s)}"
              PillButton(name, { choose(Shelf(s, g)) }, kind = PillKind.Chip, selected = s == shelf.season && g == shelf.group)
            }
          }
        }
      }
    }
    LazyRow(
        Modifier.focusRestorer(focus), horizontalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 18.dp),
    ) {
      items(shown, key = { it.value.id }) { (index, item) ->
        val resume = stream?.resume?.takeIf { it.fileID == item.fileID }
        val done = item.fileID in app.watched
        // A still from the episode itself, once on screen; on a weak TV only those already taken.
        if (lively) LaunchedEffect(item.fileID) { app.stills.still(item) }
        val still = app.stills.known[item.fileID]
        CardSurface(
            onClick = { play(index) },
            modifier = if (item.id == landing?.value?.id) Modifier.focusRequester(focus) else Modifier,
            radius = Theme.tileRadius, glow = rememberPosterTint(post),
            // Held select: the tick set or taken off by hand, as the Mac's menu on an episode.
            onLongClick = {
              val name = item.title(loc.lang) ?: tmdbNames[item.episodeNumber] ?: "${loc(S.episode)} ${loc.num(item.episodeNumber)}"
              app.sheets.show(SheetSpec(name, listOf(
                  SheetAction(loc(S.play), Icons.play) { play(index) },
                  if (done) SheetAction(loc(S.markUnwatched), Icons.close) { app.setWatched(item.fileID, false) }
                  else SheetAction(loc(S.markWatched), Icons.check) { app.setWatched(item.fileID, true) },
              )))
            },
        ) {
          EpisodeTile(item, index + 1, progress = resume?.progress ?: 0.0, watched = done, still = still, name = tmdbNames[item.episodeNumber])
        }
      }
    }
  }
}

/** One episode, as the Mac's tile: its number, name and length, a tick once watched, and how far in. */
@Composable
private fun EpisodeRow(item: StreamItem, progress: Double?, watched: Boolean, modifier: Modifier, name: String? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
  val loc = LocalLoc.current
  val hue = LocalHue.current ?: Theme.accent
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val shape = RoundedCornerShape(22.dp)
  Surface(
      onClick = onClick,
      onLongClick = onLongClick,
      modifier = modifier,
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(shape),
      // The Mac's tile under the pointer: the same dark glass a shade lighter and the words as they
      // were, the number giving way to a play mark; the ring says where focus is. A white tile with
      // dark words was the only one of its kind (the owner).
      colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.07f), focusedContainerColor = Color.White.copy(alpha = 0.16f), contentColor = Theme.text1, focusedContentColor = Theme.text1),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
  ) {
    // The ring inside the tile, so it grows with it: on the tile's own modifier it stayed the
    // tile's size at rest while the tile grew past it in focus (the owner).
    Box(Modifier.fillMaxWidth().height(104.dp).focusRing(focused, shape)) {
      Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(60.dp), contentAlignment = Alignment.Center) {
          if (focused) Icon(Icons.play, null, Modifier.size(30.dp), tint = Color.White)
          else Text(loc.num(item.episodeRaw ?: "${item.episodeNumber}"), style = app(34, FontWeight.ExtraBold), color = if (progress != null) hue else Theme.text2, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(item.title(loc.lang) ?: name ?: "${loc(S.episode)} ${loc.num(item.episodeNumber)}", style = app(25, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
          item.durationMinutes?.let { Text("${loc.num(it)} ${loc(S.min)}", style = app(20), color = Theme.text3) }
        }
        if (watched) Icon(Icons.check, null, Modifier.size(28.dp), tint = Theme.good)
      }
      if (progress != null && progress > 0) {
        // Filled from the left in every language, as time runs (the owner).
        Box(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 9.dp).fillMaxWidth().height(5.dp), contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft) {
          Box(Modifier.fillMaxWidth(progress.toFloat().coerceIn(0.03f, 1f)).fillMaxHeight().background(hue, RoundedCornerShape(50)))
        }
      }
    }
  }
}

/** Episode tile for the player's list. The API has no stills, so the tile is typographic: a big number and the title. */
@Composable
fun EpisodeTile(item: StreamItem, position: Int, progress: Double = 0.0, nowPlaying: Boolean = false, watched: Boolean = false, still: androidx.compose.ui.graphics.ImageBitmap? = null, name: String? = null) {
  val loc = LocalLoc.current
  val number = Episode.label(item.episodeRaw, position)
  val shape = RoundedCornerShape(Theme.tileRadius)
  Box(
      Modifier.size(360.dp, 200.dp).clip(shape)
          .background(Theme.surface.copy(alpha = 0.72f))
          .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)))
          .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
  ) {
    if (still != null) {
      androidx.compose.foundation.Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      // Dark enough for the number and the title over any picture.
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.8f)))))
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Text(
              loc.num(if (number.length == 1) "0$number" else number),
              style = app(if (number.length > 3) 44 else 64, FontWeight.ExtraBold),
              color = if (nowPlaying) Theme.accent else if (still != null) Theme.text1.copy(alpha = 0.8f) else Theme.text3,
          )
        }
        Spacer(Modifier.weight(1f))
        when {
          nowPlaying -> Text(loc(S.nowPlaying), style = app(20, FontWeight.SemiBold), color = Theme.accent)
          watched -> Icon(Icons.check, null, Modifier.size(28.dp), tint = Theme.good)
          else -> item.durationMinutes?.let { Text("${loc.num(it)} ${loc(S.min)}", style = app(22, FontWeight.Medium), color = Theme.text2) }
        }
      }
      Spacer(Modifier.weight(1f))
      Text(item.title(loc.lang) ?: name ?: "${loc(S.episode)} ${loc.num(number)}", style = app(26, FontWeight.SemiBold), color = Theme.text1, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (progress > 0) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 22.dp, vertical = 10.dp)) { ProgressBar(progress) }
  }
}

// MARK: People and facts

/** The Mac's cast: faces in a row, each with a name and a part, opening their titles where it can. */
@Composable
private fun Cast(post: Post) {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    SectionTitle(loc(S.cast), Modifier.padding(start = Theme.edge))
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
      items(post.cast, key = { it.id }) { person ->
        // Who they are and their titles come from the site API, by 30nama's id or the IMDb one;
        // without the site, their titles from the app API by their IMDb id.
        val opens = person.imdb != null || (app.api.hasSite && person.siteID != null)
        Face(person.name, person.role, person.poster?.url(160.0), person.poster?.blurhash) { if (opens) nav.push(Route.PersonPage(person)) }
      }
    }
  }
}

/** From TMDB, with the user's own key: the people behind the camera. */
@Composable
private fun Crew(people: List<app.nama.core.TMDB.Person>) {
  val loc = LocalLoc.current
  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    SectionTitle(loc(S.crew), Modifier.padding(start = Theme.edge))
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
      items(people, key = { it.name + it.job }) { person -> Face(person.name, loc.job(person.job), person.photo, null) {} }
    }
  }
}

@Composable
private fun Face(name: String, role: String?, photo: java.net.URI?, blurhash: String?, onClick: () -> Unit) {
  val hue = LocalHue.current ?: Theme.accent
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(Modifier.width(180.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Surface(
          onClick = onClick,
          modifier = Modifier.focusRing(interaction, CircleShape, width = 3.dp, grown = 1.08f),
          interactionSource = interaction,
          shape = ClickableSurfaceDefaults.shape(CircleShape),
          colors = ClickableSurfaceDefaults.colors(containerColor = Theme.surface2, focusedContainerColor = Theme.surface2),
          scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
      ) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
          Icon(Icons.person, null, Modifier.size(56.dp), tint = Theme.text3)
          photo?.let { RemoteImage(it, Modifier.fillMaxSize().clip(CircleShape), blurhash = blurhash, pixelWidth = 200) }
        }
      }
      Text(name, style = app(22, FontWeight.SemiBold), color = if (focused) hue else Theme.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
      role?.let { Text(it, style = app(20), color = Theme.text3, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
  }
}

/** From TMDB, with the user's own key: reviews, in the language they were written in, cut short. */
@Composable
private fun Reviews(list: List<app.nama.core.TMDB.Review>) {
  val loc = LocalLoc.current
  val app = LocalApp.current
  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    SectionTitle(loc(S.reviews), Modifier.padding(start = Theme.edge))
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      items(list) { review ->
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val shape = RoundedCornerShape(26.dp)
        Surface(
            // The whole review, to read on the TV (the Mac opens it on TMDB).
            onClick = { app.sheets.reading = ReadSpec(review.author, review.text, ltr = true) },
            modifier = Modifier.focusRing(interaction, shape, grown = 1.03f),
            interactionSource = interaction,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f), focusedContainerColor = Color.White.copy(alpha = 0.14f)),
            border = ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), shape = shape),
                focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.Transparent), shape = shape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        ) {
          // Written left to right whatever the interface reads in.
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.size(560.dp, 300.dp).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(review.author, Modifier.weight(1f), style = app(24, FontWeight.Bold), color = Theme.text1, maxLines = 1)
                review.rating?.let {
                  Icon(Icons.star, null, Modifier.size(20.dp), tint = Theme.imdb)
                  Text(" ${loc.num(Fmt.score(it))}/${loc.num(10)}", style = app(20, FontWeight.Bold), color = Theme.text2)
                }
              }
              Text(review.text, style = androidx.compose.ui.text.TextStyle(fontSize = app(22).fontSize, lineHeight = app(22).fontSize * 1.4f), color = Theme.text2, maxLines = 7, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }
}

/** "More like this": TMDB's recommendations that 30nama has. */
@Composable
private fun MoreLikeThis(posts: List<Post>) {
  val nav = LocalNav.current
  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    SectionTitle(tv(TV.moreLikeThis), Modifier.padding(start = Theme.edge))
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
      items(posts, key = { it.id }) { p -> PosterCard(p, { nav.push(Route.Details(p)) }, titled = true) }
    }
  }
}

/** The whole plot, under its own title: the header cuts it at six lines. Select opens it to read. */
@Composable
private fun Storyline(post: Post) {
  val loc = LocalLoc.current
  val app = LocalApp.current
  val interaction = remember { MutableInteractionSource() }
  val shape = RoundedCornerShape(22.dp)
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    SectionTitle(loc(S.storyline), Modifier.padding(start = Theme.edge))
    Surface(
        onClick = { app.sheets.reading = ReadSpec(post.title(loc.lang), post.plot(loc.lang)) },
        modifier = Modifier.padding(horizontal = Theme.edge - 24.dp).focusRing(interaction, shape),
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.08f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
      Text(
          post.plot(loc.lang), Modifier.widthIn(max = 1500.dp).padding(24.dp),
          style = app(27).copy(lineHeight = app(27).fontSize * 1.6f), color = Theme.text1, maxLines = 12, overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun facts(post: Post, loc: app.nama.core.Loc): List<Pair<String, String>> {
  fun row(key: S, values: List<String>): Pair<String, String>? {
    if (values.isEmpty()) return null
    return loc(key) to values.joinToString(if (loc.lang == Lang.en) ", " else "، ")
  }
  return listOfNotNull(
      row(S.director, post.directors),
      row(S.creator, post.creators),
      row(S.writer, post.writers),
      row(S.network, post.networks),
      row(S.country, post.countries),
      row(S.originalLanguage, listOfNotNull(post.language)),
      row(S.otherLanguages, post.otherLanguages),
      row(S.budget, listOfNotNull(post.budget)),
      row(S.status, listOfNotNull(post.airStatus)),
  )
}

/**
 * The Mac's facts at the foot of the page: a label and its value on each line. Each director is a
 * chip that leads to their other titles, as on the Mac (30nama's advanced search takes a director's
 * name, not an actor's). Without a director, one block that takes focus without showing much, so
 * the remote can still come down to it and the page scrolls to the end.
 */
@Composable
private fun Facts(post: Post) {
  val loc = LocalLoc.current
  val nav = LocalNav.current
  val rows = facts(post, loc)
  if (rows.isEmpty()) return
  val director = loc(S.director)
  // A director's chip is the block's only stop: in focus it brings the whole block on screen, as
  // the rows under it (writers, country, language) have nothing of their own to bring them.
  val whole = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
  val scope = rememberCoroutineScope()
  @Composable
  fun lines() {
    Column(Modifier.bringIntoViewRequester(whole).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      rows.forEach { (label, value) ->
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(label, Modifier.width(200.dp), style = app(23, FontWeight.Medium), color = Theme.text3)
          if (label == director) {
            Row(Modifier.focusRestorer().onFocusChanged { if (it.hasFocus) scope.launch { whole.bringIntoView() } }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              post.directors.forEach { name ->
                PillButton(name, { nav.push(Route.AdvancedSearch(app.nama.core.AdvancedQuery(director = post.directorIMDb[name] ?: name, sort = "year"))) }, kind = PillKind.Chip)
              }
            }
          } else {
            Text(value, style = app(25), color = Theme.text1, maxLines = 2)
          }
        }
      }
    }
  }
  if (post.directors.isNotEmpty()) {
    Box(Modifier.padding(horizontal = Theme.edge - 24.dp)) { lines() }
    return
  }
  val interaction = remember { MutableInteractionSource() }
  Surface(
      onClick = {},
      modifier = Modifier.padding(horizontal = Theme.edge - 24.dp).focusRing(interaction, RoundedCornerShape(22.dp)),
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
      colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.08f)),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
  ) { lines() }
}

/** Half a breath of the light behind the title page's poster: in, then out, about nine seconds. */
private const val BREATH_MS = 4_500

/** Under the header's words, so they hold over a busy picture. */
private val TEXT_SHADOW = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 2f), 14f)
