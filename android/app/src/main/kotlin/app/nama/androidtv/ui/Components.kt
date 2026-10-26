package app.nama.androidtv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.nama.core.Fmt
import app.nama.core.Lang
import app.nama.core.Post
import app.nama.core.S

// MARK: - Buttons

enum class PillKind { Primary, Secondary, Chip }

private val capsule = RoundedCornerShape(percent = 50)

/** The red of the primary action, lit from its top corner. */
val accentGradient = Brush.linearGradient(listOf(Theme.accentLight, Theme.accent, Theme.accentDeep))

/**
 * Buttons and chips: glass at rest, solid white in focus. The primary action rests on a lit
 * accent gradient, so "Play" is findable at a glance and focus still looks the same everywhere.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: PillKind = PillKind.Secondary,
    selected: Boolean = false,
    icon: ImageVector? = null,
    busy: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val chip = kind == PillKind.Chip
  val primary = kind == PillKind.Primary
  Surface(
      onClick = onClick,
      onLongClick = onLongClick,
      modifier = modifier,
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(capsule),
      colors = ClickableSurfaceDefaults.colors(
          containerColor = Color.Transparent,
          focusedContainerColor = Color.White,
          pressedContainerColor = Color.White,
          contentColor = if (chip && !selected) Theme.text2 else Color.White,
          focusedContentColor = Theme.focusInk,
          pressedContentColor = Theme.focusInk,
      ),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f, pressedScale = 1f),
      glow = ClickableSurfaceDefaults.glow(
          glow = if (primary) Glow(Theme.accent.copy(alpha = 0.45f), 18.dp) else Glow.None,
          focusedGlow = Glow(Color.White.copy(alpha = 0.3f), 18.dp),
      ),
  ) {
    Box(
        Modifier
            .height(if (chip) 58.dp else 76.dp)
            .then(
                when {
                  focused -> Modifier
                  primary -> Modifier.background(accentGradient, capsule)
                  else -> Modifier.background(if (selected) Color.White.copy(alpha = 0.16f) else Theme.glass, capsule).border(1.dp, Theme.glassEdge, capsule)
                })
            .padding(horizontal = if (chip) 30.dp else 40.dp),
        contentAlignment = Alignment.Center,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) Spinner(Modifier.size(26.dp), color = if (focused) Theme.focusInk else Color.White)
        else if (icon != null) Icon(icon, null, Modifier.size(if (chip) 24.dp else 28.dp))
        Text(text, style = app(if (chip) 24 else 27, FontWeight.SemiBold), maxLines = 1)
      }
      if (chip && selected && !focused) {
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(26.dp, 4.dp).background(Theme.accent, capsule))
      }
    }
  }
}

/**
 * A round button with only an icon, for actions with a state of their own (on a list or not):
 * glass at rest, white in focus, the accent while on.
 */
@Composable
fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, on: Boolean = false) {
  Surface(
      onClick = onClick,
      modifier = modifier.semantics { contentDescription = label },
      shape = ClickableSurfaceDefaults.shape(CircleShape),
      colors = ClickableSurfaceDefaults.colors(
          containerColor = if (on) Theme.accent.copy(alpha = 0.9f) else Theme.glass,
          focusedContainerColor = Color.White,
          contentColor = Color.White,
          focusedContentColor = if (on) Theme.accent else Theme.focusInk,
      ),
      border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, Theme.glassEdge), shape = CircleShape)),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
      glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.3f), 18.dp)),
  ) {
    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(32.dp)) }
  }
}

/**
 * Focus treatment for artwork: the card springs forward, catches a highlight along its top edge
 * and throws a glow in its own colour. No hard outline; the lift does the work.
 */
@Composable
fun CardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = Theme.cardRadius,
    scale: Float = 1.1f,
    glow: Color = Color.White,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(radius)
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Surface(
      onClick = onClick,
      onLongClick = onLongClick,
      modifier = modifier,
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(shape),
      colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, pressedContainerColor = Color.Transparent),
      scale = ClickableSurfaceDefaults.scale(focusedScale = scale, pressedScale = scale - 0.04f),
      border = ClickableSurfaceDefaults.border(
          focusedBorder = Border(BorderStroke(3.dp, Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.45f)))), shape = shape),
      ),
      glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(glow.copy(alpha = 0.6f), 26.dp)),
  ) {
    Box(
        Modifier.drawWithContent {
          drawContent()
          // Specular sheen, as if the card tilted into the light.
          if (focused) drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.04f), Color.Transparent), Offset.Zero, Offset(size.width, size.height)))
        }) { content() }
  }
}

// MARK: - Artwork

@Composable
fun PosterArt(post: Post, width: Dp = Theme.posterWidth, loads: Boolean = true) {
  val loc = LocalLoc.current
  Box(Modifier.size(width, width * 1.5f).clip(RoundedCornerShape(Theme.cardRadius)).background(Theme.surface)) {
    // Still identifies the title if the image never arrives.
    Text(
        post.title(loc.lang), Modifier.align(Alignment.Center).padding(14.dp),
        style = app(22, FontWeight.Medium), color = Theme.text3, textAlign = TextAlign.Center, maxLines = 3,
    )
    RemoteImage(
        url = if (loads) post.poster.url(width.value * 1.1) else null,
        modifier = Modifier.fillMaxSize(),
        blurhash = post.poster.blurhash,
        pixelWidth = (width.value * 1.2f).toInt(),
    )
    when {
      post.comingSoon -> Badge(loc(S.soon), Color.Black.copy(alpha = 0.6f), Theme.text1, Modifier.align(Alignment.TopStart))
      post.freeStream -> Badge(loc(S.free), Theme.good, Theme.focusInk, Modifier.align(Alignment.TopStart))
    }
  }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color, modifier: Modifier) {
  Text(
      text, modifier.padding(10.dp).background(background, capsule).padding(horizontal = 12.dp, vertical = 2.dp),
      style = app(19, FontWeight.Bold), color = foreground,
  )
}

/** A poster that opens its title. [titled] shows the name under it (grids); rows leave it to the billboard. */
@Composable
fun PosterCard(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Theme.posterWidth,
    titled: Boolean = false,
    loads: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    CardSurface(onClick = onClick, modifier = modifier, glow = post.tint(), onLongClick = onLongClick) { PosterArt(post, width, loads) }
    // Outside the card so the caption does not scale with the poster.
    if (titled) {
      Text(post.title(LocalLoc.current.lang), Modifier.width(width), style = app(22, FontWeight.Medium), color = Theme.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

// MARK: - Text

/** Glass capsule around a fact or a score. */
@Composable
fun Chip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
      modifier.height(42.dp).background(Theme.glass, capsule).border(1.dp, Theme.glassEdge, capsule).padding(horizontal = 16.dp),
      contentAlignment = Alignment.Center,
  ) { content() }
}

@Composable
private fun ScoreChip(source: String, color: Color, value: String) {
  Chip {
    // Scores read left to right in every language: "IMDb 7.8".
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(source, style = app(19, FontWeight.ExtraBold), color = color)
        Text(value, style = app(22, FontWeight.SemiBold), color = Theme.text1)
      }
    }
  }
}

@Composable
private fun FactChip(text: String) {
  Chip { Text(text, style = app(22, FontWeight.SemiBold), color = Theme.text1, maxLines = 1) }
}

/**
 * The facts about a title as glass chips: its scores (IMDb, Rotten Tomatoes, Metacritic,
 * MyAnimeList, 30nama), then year, runtime and age rating, then genres.
 */
@Composable
fun MetaLine(post: Post, genres: Int = 2, expanded: Boolean = false) {
  val loc = LocalLoc.current
  val scores: @Composable (Boolean) -> Unit = { all ->
    post.imdbScore?.let { ScoreChip("IMDb", Theme.imdb, Fmt.score(it)) }
    // Fresh at 60% and above, as on the site.
    post.rottenTomatoes?.let { ScoreChip("RT", if (it >= 60) Color(0xFFFA4B2A) else Color(0xFF7BB661), "$it%") }
    if (all) post.metacritic?.let { ScoreChip("MC", if (it >= 61) Color(0xFF66CC33) else if (it >= 40) Color(0xFFFFCC33) else Color(0xFFFF5A4D), "$it") }
    if (all || post.rottenTomatoes == null) post.fanScore?.let { ScoreChip(it.source, Color(0xFF6C8CFF), Fmt.score(it.value)) }
    post.siteScore?.let { score ->
      Chip {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.star, null, Modifier.size(18.dp), tint = Theme.good)
          Text(loc.num(Fmt.score(score)), style = app(22, FontWeight.SemiBold), color = Theme.text1)
        }
      }
    }
  }
  val facts: @Composable () -> Unit = {
    post.year?.let { year ->
      val end = post.yearEnd
      FactChip(if (post.isSeries && end != null && end != year) loc.num(year) + "–" + loc.num(end) else loc.num(year))
    }
    Fmt.runtime(post.runtimeMinutes, loc.lang)?.let { FactChip(it) }
    post.age?.let { age -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { FactChip(age) } }
    if (post.genres.isNotEmpty()) {
      Text(
          post.genres.take(genres).joinToString(if (loc.lang == Lang.fa) "، " else ", ") { it.name },
          Modifier.padding(start = 8.dp), style = app(24, FontWeight.Medium), color = Theme.text2, maxLines = 1,
      )
    }
  }
  if (expanded) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { scores(true) }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { facts() }
    }
  } else {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      scores(false)
      facts()
    }
  }
}

/** Small coloured label above a title: what kind of thing it is. */
@Composable
fun Eyebrow(text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(6.dp, 24.dp).background(Theme.accent, capsule))
    Text(text, style = app(24, FontWeight.SemiBold), color = Theme.text1.copy(alpha = 0.9f), maxLines = 1)
  }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, modifier, style = app(34, FontWeight.Bold), color = Theme.text1)
}

/** The "30nama" wordmark. */
@Composable
fun Wordmark(size: Int = 40) {
  val text: AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = Theme.accent)) { append("30") }
    withStyle(SpanStyle(color = Theme.text1)) { append("nama") }
  }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(text, style = app(size, FontWeight.ExtraBold)) }
}

// MARK: - States

@Composable
fun ErrorState(message: String? = null, retry: () -> Unit) {
  val loc = LocalLoc.current
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)) {
    Icon(Icons.offline, null, Modifier.size(64.dp), tint = Theme.text3)
    Text(message ?: "${loc(S.error)}. ${loc(S.tryAgain)}", style = app(28), color = Theme.text2, textAlign = TextAlign.Center)
    PillButton(loc(S.retry), retry, kind = PillKind.Primary)
  }
}

/** Placeholder block with a light sweeping across it; still where motion is spared. */
@Composable
fun Skeleton(modifier: Modifier, radius: Dp = 12.dp) {
  val shape = RoundedCornerShape(radius)
  val moving = LocalLively.current && !LocalCalm.current
  val sweep = if (moving) {
    val t = rememberInfiniteTransition(label = "skeleton")
    t.animateFloat(-0.9f, 1.2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "sweep").value
  } else null
  Box(
      modifier.clip(shape).background(Color.White.copy(alpha = 0.07f)).drawWithContent {
        drawContent()
        if (sweep != null) {
          val x = size.width * sweep
          drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent), x, x + size.width * 0.7f))
        }
      })
}

@Composable
fun Toast(text: String) {
  Text(
      text,
      Modifier.background(Theme.surface2.copy(alpha = 0.92f), capsule).border(1.dp, Theme.glassEdge, capsule).padding(horizontal = 34.dp, vertical = 18.dp),
      style = app(26, FontWeight.Medium), color = Theme.text1,
  )
}

/** A small turning arc, for "waiting on the server". */
@Composable
fun Spinner(modifier: Modifier = Modifier, color: Color = Color.White) {
  val t = rememberInfiniteTransition(label = "spinner")
  val turn = t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "turn").value
  Box(
      modifier.drawWithContent {
        drawArc(color, turn, 270f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
      })
}

/** A thin bar of how far a title has been watched. */
@Composable
fun ProgressBar(progress: Double, modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.22f), capsule)) {
    Box(Modifier.fillMaxWidth(progress.toFloat().coerceIn(0.02f, 1f)).height(6.dp).background(Theme.accent, capsule))
  }
}
