package app.nama.androidtv.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import app.nama.core.StreamResult
import app.nama.core.TrailerVideo
import app.nama.core.single
import kotlinx.coroutines.launch

/** A title's page: the pitch and the play button over its backdrop, then episodes, storyline and cast. */
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
  var inHeader by remember { mutableStateOf(true) }
  var trailer by remember { mutableStateOf<TrailerVideo?>(null) }
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
      if (!quietly) result.items.getOrNull(result.startIndex)?.let { first -> shelf = Shelf.of(result.items, first) }
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
  // Focus starts on Play once the page is there to hold it.
  LaunchedEffect(post != null) {
    if (post == null) return@LaunchedEffect
    withFrameNanos {}
    runCatching { play.requestFocus() }
  }
  LaunchedEffect(post?.id) {
    val p = post ?: return@LaunchedEffect
    // Only once a trailer has been found, so there is never a button that leads nowhere.
    trailer = app.extras.trailer(p, loc.lang, app.inIran)
  }
  // Back from the player: pick up the new resume point.
  LaunchedEffect(app.watchVersion) { if (stream != null) loadStream(quietly = true) }

  Box(Modifier.fillMaxSize()) {
    Stage(post, dim = if (inHeader) 0.12f else 0.62f, settleMs = 0)
    when {
      post != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(56.dp)) {
        Header(
            post, stream, items, playWhenReady && streamPending, trailer,
            play = play,
            onPlay = { startPlayback() },
            onEpisodes = { runCatching { episodesFocus.requestFocus() } },
            onTrailer = { t -> openTrailer(t, post, app, scope, context) },
            modifier = Modifier.onFocusChanged { inHeader = it.hasFocus },
        )
        if (post.isSeries && items.isNotEmpty()) {
          Episodes(items, stream, shelf ?: Shelf.of(items, items.first()), { shelf = it }, episodesFocus) { startPlayback(it) }
        }
        if (post.plot(loc.lang).length > 260 || facts(post, loc).isNotEmpty()) Story(post)
        if (post.cast.isNotEmpty()) Cast(post)
        Spacer(Modifier.height(40.dp))
      }
      failed -> ErrorState {
        failed = false
        scope.launch { attempt { app.api.single(route.id) }?.let { loaded = it } ?: run { failed = true } }
      }
    }
  }
}

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
    is TrailerVideo.Aparat -> scope.launch {
      val file = app.extras.aparatFile(t.uid)
      if (file == null) app.showToast(app.loc(S.trailerNotLoaded)) else app.playTrailer(post.title(app.settings.lang), file)
    }
  }
}

// MARK: Header

@Composable
private fun Header(
    post: Post,
    stream: StreamResult?,
    items: List<StreamItem>,
    busy: Boolean,
    trailer: TrailerVideo?,
    play: FocusRequester,
    onPlay: () -> Unit,
    onEpisodes: () -> Unit,
    onTrailer: (TrailerVideo) -> Unit,
    modifier: Modifier,
) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val scope = rememberCoroutineScope()
  // Bottom-aligned so the buttons sit at the same height whatever the title's length, with the
  // next section's heading peeking below as a cue to scroll.
  Box(modifier.fillMaxWidth().height(880.dp).padding(start = Theme.edge), contentAlignment = Alignment.BottomStart) {
    Column(Modifier.widthIn(max = 1000.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(loc.kindLine(post))
        if (post.persianSubtitle) Chip {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.subtitles, null, Modifier.size(22.dp), tint = Theme.text1)
            Text(loc(S.persianSub), style = app(22, FontWeight.SemiBold), color = Theme.text1)
          }
        }
        if (post.isSeries) {
          val date = if (loc.lang == Lang.fa) post.nextEpisodeDate.jalali else post.nextEpisodeDate.gregorian
          date?.let {
            // The server words its caption in Persian; other languages get their own.
            val caption = post.nextEpisodeCaption?.takeIf { loc.lang == Lang.fa } ?: loc(S.nextEpisode)
            Text(
                "$caption · ${loc.num(it)}",
                Modifier.height(42.dp).background(Theme.accent, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 6.dp),
                style = app(22, FontWeight.SemiBold), color = Color.White, maxLines = 1,
            )
          }
        }
      }
      Text(
          post.title(loc.lang),
          style = app(92, FontWeight.ExtraBold).copy(brush = Brush.verticalGradient(listOf(Color.White, Theme.text1.copy(alpha = 0.82f)))),
          maxLines = 2, overflow = TextOverflow.Ellipsis,
      )
      post.alternateTitle(loc.lang)?.let { Text(it, style = app(30, FontWeight.Medium), color = Theme.text2, maxLines = 1) }
      Box(Modifier.padding(top = 4.dp)) { MetaLine(post, genres = 3, expanded = true) }
      Text(
          post.plot(loc.lang), Modifier.widthIn(max = 880.dp),
          style = app(28).copy(lineHeight = app(28).fontSize * 1.45f), color = Theme.text2, maxLines = 4, overflow = TextOverflow.Ellipsis,
      )
      Row(Modifier.padding(top = 18.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PillButton(playLabel(post, stream, items), onPlay, Modifier.focusRequester(play), kind = PillKind.Primary, icon = Icons.play, busy = busy)
        if (post.isSeries && items.isNotEmpty()) PillButton(loc(S.episodes), onEpisodes, icon = Icons.list)
        trailer?.let { t -> PillButton(loc(S.trailer), { onTrailer(t) }, icon = Icons.film) }
        if (app.isLoggedIn) {
          RoundButton(Icons.bookmark, loc(S.watchlist), { scope.launch { app.toggleList(post, "watchlist") } }, on = app.isListed(post, "watchlist"))
          RoundButton(Icons.heart, loc(S.favorites), { scope.launch { app.toggleList(post, "favorite") } }, on = app.isListed(post, "favorite"))
        }
      }
      Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(loc(S.yourRating), style = app(24, FontWeight.Medium), color = Theme.text2)
        StarRating(app.ratings.stars(post.id)) { app.rate(post, it) }
      }
      val progress = stream?.resume?.progress ?: 0.0
      if (progress > 0 && !post.isSeries) Box(Modifier.width(300.dp)) { ProgressBar(progress) }
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
 * third press clears the rating.
 */
@Composable
private fun StarRating(value: Double?, set: (Double?) -> Unit) {
  val loc = LocalLoc.current
  Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
          interactionSource = interaction,
          shape = ClickableSurfaceDefaults.shape(CircleShape),
          colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
          scale = ClickableSurfaceDefaults.scale(focusedScale = 1.35f),
      ) {
        // A half star fills from the reading start, so it is mirrored in a right-to-left language.
        Icon(
            icon, null, Modifier.size(52.dp).padding(8.dp).then(if (LocalLayoutDirection.current == LayoutDirection.Rtl) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
            tint = if (focused) Color.White else if (v > n - 1) Theme.imdb else Theme.text3,
        )
      }
    }
    value?.let { Text(loc.num(Fmt.score(it)), Modifier.padding(start = 10.dp), style = app(26, FontWeight.Bold), color = Theme.text1) }
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

@Composable
private fun Episodes(items: List<StreamItem>, stream: StreamResult?, shelf: Shelf, choose: (Shelf) -> Unit, focus: FocusRequester, play: (Int) -> Unit) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val seasons = stream?.seasons.orEmpty()
  val inSeason = items.withIndex().filter { it.value.season == shelf.season }
  val groups = (inSeason.size + Shelf.SIZE - 1) / Shelf.SIZE
  val shown = inSeason.drop(shelf.group * Shelf.SIZE).take(Shelf.SIZE)
  val resumeIndex = stream?.startIndex ?: 0

  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    Row(Modifier.padding(start = Theme.edge), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
      SectionTitle(loc(S.episodes))
      Text("${loc.num(inSeason.size)} ${loc(S.episodeCount)}", style = app(24), color = Theme.text3)
    }
    if (seasons.size > 1 || groups > 1) {
      LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (s in seasons) {
          val count = items.count { it.season == s }
          val parts = if (s == shelf.season) (count + Shelf.SIZE - 1) / Shelf.SIZE else 1
          for (g in 0 until parts) {
            item(key = "$s-$g") {
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
    val state = rememberLazyListState()
    // A still for each tile in view, one at a time, from the episode itself.
    LaunchedEffect(shelf) { for ((_, item) in shown) app.stills.still(item) }
    // Coming down from the header lands on the episode to resume, not on whichever tile is nearest.
    val landing = shown.firstOrNull { it.index == resumeIndex } ?: shown.firstOrNull()
    LaunchedEffect(shelf) { landing?.let { l -> state.scrollToItem(shown.indexOf(l).coerceAtLeast(0)) } }
    LazyRow(
        Modifier.focusRestorer(focus),
        state = state,
        contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      items(shown, key = { it.value.id }) { (index, item) ->
        val progress = stream?.resume?.takeIf { it.fileID == item.fileID }?.progress ?: 0.0
        CardSurface(
            onClick = { play(index) },
            // The "Episodes" button, and coming down from the header the first time, land here.
            modifier = if (item.id == landing?.value?.id) Modifier.focusRequester(focus) else Modifier,
            radius = Theme.tileRadius, scale = 1.07f,
        ) { EpisodeTile(item, index + 1, progress, watched = item.fileID in app.watched, still = app.stills.known[item.fileID]) }
      }
    }
  }
}

/** Episode tile. The API has no stills, so the tile is typographic: a big number and the title. */
@Composable
fun EpisodeTile(item: StreamItem, position: Int, progress: Double = 0.0, nowPlaying: Boolean = false, watched: Boolean = false, still: androidx.compose.ui.graphics.ImageBitmap? = null) {
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
      androidx.compose.foundation.Image(still, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
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
      Text(item.title(loc.lang) ?: "${loc(S.episode)} ${loc.num(number)}", style = app(26, FontWeight.SemiBold), color = Theme.text1, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (progress > 0) Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 22.dp, vertical = 10.dp)) { ProgressBar(progress) }
  }
}

// MARK: Storyline and cast

private fun facts(post: Post, loc: app.nama.core.Loc): List<Pair<String, String>> {
  fun row(key: S, values: List<String>): Pair<String, String>? {
    if (values.isEmpty()) return null
    // Names mostly arrive in Latin script; a Persian comma between them reads as a typo.
    val latin = values.joinToString("").all { it.code < 0x0600 }
    return loc(key) to values.take(3).joinToString(if (latin || loc.lang == Lang.en) ", " else "، ")
  }
  return listOfNotNull(
      row(S.creator, post.creators),
      row(S.director, post.directors),
      row(S.writer, post.writers),
      row(S.network, post.networks),
      row(S.country, post.countries),
      row(S.originalLanguage, listOfNotNull(post.language)),
      row(S.status, listOfNotNull(post.airStatus)),
  )
}

@Composable
private fun Story(post: Post) {
  val loc = LocalLoc.current
  // The header shows four lines of plot; anything longer is given in full here.
  val longPlot = post.plot(loc.lang).length > 260
  val rows = facts(post, loc)
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Column(Modifier.padding(horizontal = Theme.edge), verticalArrangement = Arrangement.spacedBy(22.dp)) {
    SectionTitle(loc(if (longPlot) S.storyline else S.information))
    // A row that takes focus, so the remote can come down to it and the page scrolls.
    Surface(
        onClick = {},
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(Theme.tileRadius)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Theme.glass, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
    ) {
      Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).padding(horizontal = 34.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        if (longPlot) Text(post.plot(loc.lang), Modifier.weight(1f), style = app(27).copy(lineHeight = app(27).fontSize * 1.5f))
        Column(Modifier.then(if (longPlot) Modifier.width(560.dp) else Modifier.weight(1f)), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          rows.chunked(if (longPlot) 1 else 2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
              pair.forEach { (label, value) ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                  Text(label, Modifier.width(150.dp), style = app(24, FontWeight.Medium), color = (if (focused) Theme.focusInk else Theme.text1).copy(alpha = 0.55f))
                  Text(value, style = app(24, FontWeight.Medium), maxLines = 2)
                }
              }
              if (pair.size == 1 && !longPlot) Spacer(Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Cast(post: Post) {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    SectionTitle(loc(S.cast), Modifier.padding(start = Theme.edge))
    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = Theme.edge, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
      items(post.cast, key = { it.id }) { person ->
        // Who they are and their titles come from the site API, by 30nama's id or the IMDb one:
        // without either, the face only takes the focus.
        val opens = app.api.hasSite && (person.siteID != null || person.imdb != null)
        Face(person) { if (opens) nav.push(Route.PersonPage(person)) }
      }
    }
  }
}

@Composable
private fun Face(person: Person, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(Modifier.width(190.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Surface(
          onClick = onClick,
          interactionSource = interaction,
          shape = ClickableSurfaceDefaults.shape(CircleShape),
          colors = ClickableSurfaceDefaults.colors(containerColor = Theme.surface2, focusedContainerColor = Theme.surface2),
          scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
          border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(4.dp, Color.White), shape = CircleShape)),
      ) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
          val words = person.name.split(" ").filter { it.isNotEmpty() }
          val initials = listOfNotNull(words.firstOrNull()?.firstOrNull(), if (words.size > 1) words.last().firstOrNull() else null).joinToString("").uppercase()
          Text(initials, style = app(44, FontWeight.Bold), color = Theme.text3)
          person.poster?.let { RemoteImage(it.url(170.0), Modifier.fillMaxSize().clip(CircleShape), blurhash = it.blurhash, pixelWidth = 220) }
        }
      }
      Text(person.name, style = app(22, FontWeight.SemiBold), color = if (focused) Color.White else Theme.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(person.role ?: " ", style = app(20), color = Theme.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}
