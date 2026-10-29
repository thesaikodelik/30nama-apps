package app.nama.androidtv.ui

import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

import app.nama.core.Episode
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
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

/**
 * Where the remote is, the same on every control (the owner chose it among four): a thin ring of
 * 30nama's own red gradient (coral, red, raspberry, as on the profile's picture) on the control's
 * own edge, inside it, turning slowly. It sits over whatever the control already is in focus (the
 * menu's lit glass, Play's white, a card's glow), so none of that is lost. Only the control in focus
 * turns, and its angle is read only when drawn, so nothing is rebuilt for it; on a weak TV, or with
 * reduced motion, the ring stands still.
 */
@Composable
fun Modifier.focusRing(interaction: androidx.compose.foundation.interaction.InteractionSource, shape: androidx.compose.ui.graphics.Shape, width: Dp = 2.dp, grown: Float = 1f): Modifier {
  val focused by interaction.collectIsFocusedAsState()
  return focusRing(focused, shape, width, grown)
}

@Composable
/**
 * [grown]: the control's own scale in focus, where the ring is on its outer modifier and the
 * control grows inside it (a TV Surface scales its content, not its modifiers): the ring is drawn
 * that much larger, so it sits on the grown edge and not inside it (the owner).
 */
fun Modifier.focusRing(focused: Boolean, shape: androidx.compose.ui.graphics.Shape, width: Dp = 2.dp, grown: Float = 1f): Modifier {
  val turns = LocalLively.current && !LocalCalm.current
  val angle = remember { androidx.compose.animation.core.Animatable(0f) }
  LaunchedEffect(focused, turns) {
    if (!focused || !turns) return@LaunchedEffect
    while (true) angle.animateTo(angle.value + 360f, tween(RING_TURN_MS, easing = androidx.compose.animation.core.LinearEasing))
  }
  return this.drawWithCache {
    val stroke = width.toPx()
    val inner = androidx.compose.ui.geometry.Size(size.width * grown - stroke, size.height * grown - stroke)
    val shift = stroke / 2f - size.width * (grown - 1f) / 2f
    val shiftY = stroke / 2f - size.height * (grown - 1f) / 2f
    val outline = shape.createOutline(inner, layoutDirection, this)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val shader = android.graphics.SweepGradient(cx, cy, RING_COLOURS, null)
    val matrix = android.graphics.Matrix()
    val brush = androidx.compose.ui.graphics.ShaderBrush(shader)
    onDrawWithContent {
      drawContent()
      if (focused) {
        matrix.setRotate(angle.value, cx, cy)
        shader.setLocalMatrix(matrix)
        translate(shift, shiftY) { drawOutline(outline, brush, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)) }
      }
    }
  }
}

/**
 * The light the chosen preview threw round its ring, inside a control in focus: a soft glow from its
 * edge inward in the ring's own gradient (coral, red, raspberry), turning with it, the middle left
 * as it is (the owner: a red pool in the middle was not it). Layered strokes, no blur; read only
 * when drawn; still on a weak TV or with reduced motion.
 */
@Composable
fun Modifier.focusWash(focused: Boolean, strength: Float = 0.16f, shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape): Modifier {
  val turns = LocalLively.current && !LocalCalm.current
  val shown by animateFloatAsState(if (focused) 1f else 0f, tween(260), label = "wash")
  val angle = remember { androidx.compose.animation.core.Animatable(0f) }
  LaunchedEffect(focused, turns) {
    if (!focused || !turns) return@LaunchedEffect
    while (true) angle.animateTo(angle.value + 360f, tween(WASH_TURN_MS, easing = androidx.compose.animation.core.LinearEasing))
  }
  return this.drawWithCache {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val shader = android.graphics.SweepGradient(cx, cy, RING_COLOURS, null)
    val matrix = android.graphics.Matrix()
    val brush = androidx.compose.ui.graphics.ShaderBrush(shader)
    // Deepest at the edge, fading inward: each band wider and fainter than the last.
    val reach = minOf(size.minDimension * 0.42f, 18.dp.toPx())
    val bands = (1..WASH_BANDS).map { i ->
      val w = reach * i / WASH_BANDS
      w to shape.createOutline(androidx.compose.ui.geometry.Size(size.width - w, size.height - w), layoutDirection, this)
    }
    onDrawBehind {
      if (shown <= 0f) return@onDrawBehind
      matrix.setRotate(angle.value, cx, cy)
      shader.setLocalMatrix(matrix)
      val each = strength * shown / WASH_BANDS * 1.6f
      for ((w, outline) in bands) translate(w / 2f, w / 2f) { drawOutline(outline, brush, alpha = each, style = androidx.compose.ui.graphics.drawscope.Stroke(w)) }
    }
  }
}

private const val WASH_BANDS = 10

/** One round of the wash: slower than the ring, so the two do not race. */
private const val WASH_TURN_MS = 7000

/**
 * A row of chips or buttons given room beyond its own edges, where the one in focus grows (×1.05)
 * and its ring and light reach: a lazy row clips at its edge, and cut the first and last in focus
 * (the owner). The row is laid out [by] wider each side and drawn where it was; pair with the same
 * horizontal content padding, so its first item stays where it was.
 */
fun Modifier.bleed(by: Dp): Modifier = this.layout { measurable, constraints ->
  val extra = by.roundToPx() * 2
  val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra) else constraints
  val placeable = measurable.measure(wide)
  layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

/** One turn of the ring, as slow as the preview the owner chose (3.6 s). */
private const val RING_TURN_MS = 3600
private val RING_COLOURS = intArrayOf(
    Theme.accentLight.toArgb(), Theme.accent.toArgb(), Theme.accentDeep.toArgb(), Theme.accentLight.toArgb(),
)

private val capsule = RoundedCornerShape(percent = 50)

/** The red of the primary action, lit from its top corner. */
val accentGradient = Brush.linearGradient(listOf(Theme.accentLight, Theme.accent, Theme.accentDeep))

/**
 * Buttons, as the Mac's (docs/design, "دکمه‌ها و چیپ‌ها"); a TV's focus is the Mac's hover.
 *  - [PillKind.Primary], the one bright button on a screen (play, sign in): white, throwing light
 *    in the colour of the title it is about ([LocalHue]), white light where there is none.
 *  - [PillKind.Secondary], everything else: glass filled with white 0.10, 0.20 and lit in focus.
 *  - [PillKind.Chip], one choice among a few (a season, a sort): the chosen one white, the others
 *    white 0.10. Sizes are the Apple TV app's, as a TV is seen from the sofa.
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
    trailing: ImageVector? = null,
) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val chip = kind == PillKind.Chip
  val primary = kind == PillKind.Primary
  val white = primary || (chip && selected)
  val hue = LocalHue.current
  val light = hue ?: Color.White
  Surface(
      onClick = onClick,
      onLongClick = onLongClick,
      modifier = modifier,
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(capsule),
      colors = ClickableSurfaceDefaults.colors(
          containerColor = Color.Transparent,
          focusedContainerColor = Color.Transparent,
          pressedContainerColor = Color.Transparent,
          contentColor = if (white) Theme.focusInk else if (chip) Theme.text2 else Theme.text1,
          focusedContentColor = if (white) Theme.focusInk else Theme.text1,
          pressedContentColor = if (white) Theme.focusInk else Theme.text1,
      ),
      // Every kind lifts alike in focus; the ring says where focus is.
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f, pressedScale = 0.96f),
  ) {
    Box(
        Modifier
            .height(if (chip) 58.dp else 76.dp)
            .then(
                when {
                  // The light the bright button throws is the colour of the title it is about.
                  white -> Modifier.colouredGlow(light, if (primary) (if (hue == null) (if (focused) 0.45f else 0.2f) else (if (focused) 0.75f else 0.45f)) else if (focused) 0.35f else 0f, if (focused) 28.dp else 17.dp, capsule)
                      .background(Color.White, capsule)
                  // The Mac's PillChoice and filter menus are not glass: a flat capsule of white
                  // 0.10. In focus, where the Mac's pointer makes it 0.20, a little more and an
                  // edge, to be seen from the sofa.
                  chip -> Modifier.background(Color.White.copy(alpha = if (focused) 0.24f else 0.10f), capsule)
                  else -> Modifier.glass(capsule, lit = focused, fill = if (focused) 0.20f else 0.10f)
                })
            // The ring's red light moving inside, over the button's own face (the owner); on the
            // white ones fainter, so they stay white.
            .focusWash(focused, if (white) 0.12f else 0.30f, capsule)
            .focusRing(focused, capsule)
            .padding(horizontal = if (chip) 30.dp else 38.dp),
        contentAlignment = Alignment.Center,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) Spinner(Modifier.size(26.dp), color = if (white) Theme.focusInk else Color.White)
        else if (icon != null) Icon(icon, null, Modifier.size(if (chip) 24.dp else 28.dp))
        Text(text, style = app(if (chip) 24 else 27, if (primary) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1)
        if (trailing != null) Icon(trailing, null, Modifier.size(24.dp))
      }
    }
  }
}

/**
 * A round button for a state of its own (on a list or not), as the Mac's list toggles: glass
 * filled with white 0.10, the icon filled in and in the title's colour while on. In focus, lit
 * glass with white 0.20.
 */
@Composable
fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, on: Boolean = false, onIcon: ImageVector? = null) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  val hue = LocalHue.current ?: Theme.accent
  Surface(
      onClick = onClick,
      modifier = modifier.semantics { contentDescription = label },
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(CircleShape),
      colors = ClickableSurfaceDefaults.colors(
          containerColor = Color.Transparent,
          focusedContainerColor = Color.Transparent,
          contentColor = if (on) hue else Theme.text1,
          focusedContentColor = if (on) hue else Color.White,
      ),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f, pressedScale = 0.96f),
  ) {
    Box(Modifier.size(76.dp).glass(CircleShape, lit = focused, fill = if (focused) 0.20f else 0.10f).focusWash(focused, 0.30f, CircleShape).focusRing(focused, CircleShape), contentAlignment = Alignment.Center) {
      Icon(if (on) onIcon ?: icon else icon, null, Modifier.size(32.dp))
    }
  }
}

/** Inside a [CardSurface]: whether it has focus, for what shows only then (a poster's name). */
val LocalCardFocused = compositionLocalOf { false }

/** Inside a [CardSurface]: whether the card draws a still front over itself, so what it holds leaves its words to it. */
val LocalCardFront = compositionLocalOf { false }

/**
 * A card, as the Mac's poster under the pointer (docs/design "پوستر"): at rest a hairline of white
 * 0.08; in focus ×1.07, the focus ring, and a glow in [glow] (the poster's own colour). It does not
 * lean or carry a glint in focus: on every card that was too much (the owner); the title page's
 * poster has a breathing light behind it instead.
 */
@Composable
fun CardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = Theme.cardRadius,
    scale: Float = 1.07f,
    glow: Color = Color.White,
    onLongClick: (() -> Unit)? = null,
    front: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(radius)
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  // The card's own growth in focus, for the still front to grow with it (as ClickableSurfaceDefaults does).
  val grown by animateFloatAsState(if (focused) scale else 1f, tween(150), label = "front")
  Box {
  Surface(
      onClick = onClick,
      onLongClick = onLongClick,
      modifier = modifier.colouredGlow(glow, if (focused) 0.55f else 0f, 31.dp, shape, y = 18.dp),
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(shape),
      colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, pressedContainerColor = Color.Transparent),
      scale = ClickableSurfaceDefaults.scale(focusedScale = scale, pressedScale = scale - 0.04f),
      border = ClickableSurfaceDefaults.border(
          border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = shape),
          focusedBorder = Border(BorderStroke(1.dp, Color.Transparent), shape = shape),
      ),
  ) {
    Box(Modifier.focusRing(focused, shape)) {
      CompositionLocalProvider(LocalCardFocused provides focused, LocalCardFront provides (front != null)) { content() }
    }
  }
  // Words over the card, growing with it in focus; above it, as the card in focus lifts itself over
  // its neighbours.
  if (front != null) {
    Box(
        Modifier.matchParentSize().zIndex(2f).graphicsLayer {
          scaleX = grown
          scaleY = grown
        },
    ) { CompositionLocalProvider(LocalCardFocused provides focused) { front() } }
  }
  }
}

// MARK: - Artwork

/** A poster: [surface2] until the picture comes, its name and year over the bottom in focus, as the Mac's. */
@Composable
fun PosterArt(post: Post, width: Dp = Theme.posterWidth, loads: Boolean = true) {
  val loc = LocalLoc.current
  val focused = LocalCardFocused.current
  val shown by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "caption")
  Box(Modifier.size(width, width * 1.5f).background(Theme.surface2)) {
    RemoteImage(
        url = if (loads) post.poster.url(width.value * 1.1) else null,
        modifier = Modifier.fillMaxSize(),
        blurhash = post.poster.blurhash,
        pixelWidth = (width.value * 1.2f).toInt(),
    )
    when {
      post.comingSoon -> Badge(loc(S.soon), Color.Black.copy(alpha = 0.65f), Theme.text1, Modifier.align(Alignment.TopStart))
      post.freeStream -> Badge(loc(S.free), Theme.good, Theme.focusInk, Modifier.align(Alignment.TopStart))
    }
    if (shown > 0f) {
      Column(
          Modifier.align(Alignment.BottomStart).fillMaxWidth().graphicsLayer { alpha = shown }
              .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
              .padding(start = 13.dp, end = 13.dp, top = 34.dp, bottom = 13.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        // With a still front the words are drawn there; here they only hold the band's height.
        val words = if (LocalCardFront.current) Modifier.graphicsLayer { alpha = 0f } else Modifier
        Column(words, verticalArrangement = Arrangement.spacedBy(3.dp)) { CaptionWords(post) }
      }
    }
  }
}

/** A poster's name and year in focus, on the card's still front: they stay put while the card leans. */
@Composable
fun BoxScope.PosterCaption(post: Post) {
  val focused = LocalCardFocused.current
  val shown by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "caption")
  if (shown > 0f) {
    // A name in Latin letters reads from the left even in Persian (the owner): the words take its side.
    val side = if (post.title(LocalLoc.current.lang).readsLeftToRight()) LayoutDirection.Ltr else LocalLayoutDirection.current
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
      CompositionLocalProvider(LocalLayoutDirection provides side) {
        Column(
            Modifier.fillMaxWidth().graphicsLayer { alpha = shown }
                .padding(start = 13.dp, end = 13.dp, top = 34.dp, bottom = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) { CaptionWords(post) }
      }
    }
  }
}

/** Whether text starts in a left-to-right script (its first letter that has a side), as Latin names. */
fun String.readsLeftToRight(): Boolean {
  for (c in this) {
    when (Character.getDirectionality(c)) {
      Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return true
      Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return false
    }
  }
  return false
}

@Composable
private fun CaptionWords(post: Post) {
  val loc = LocalLoc.current
  val lifted = app(19, FontWeight.Bold).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 8f))
  Text(post.title(loc.lang), style = lifted, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
  post.year?.let { Text(loc.num(it), style = app(15, FontWeight.Medium).copy(shadow = lifted.shadow), color = Color.White.copy(alpha = 0.7f)) }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color, modifier: Modifier) {
  Text(
      text, modifier.padding(10.dp).background(background, capsule).padding(horizontal = 11.dp, vertical = 2.dp),
      style = app(15, FontWeight.Bold), color = foreground,
  )
}

/** The colour of a poster's own picture, for the glow behind its card (SceneTint). */
@Composable
fun rememberPosterTint(post: Post): Color {
  val context = LocalContext.current
  var tint by remember(post.id) { mutableStateOf(SceneTint.knownPoster(post) ?: post.tint()) }
  LaunchedEffect(post.id) { tint = SceneTint.posterColour(context, post) }
  return tint
}

/** A poster that opens its title. [titled] shows the name under it (grids); rows show it on the card in focus. */
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
  val nav = LocalNav.current
  val calm = LocalCalm.current
  val app = LocalApp.current
  val loc = LocalLoc.current
  // Held select, signed in: the watchlist and favourites without opening the title (the Windows
  // card's buttons under the pointer).
  val lists = onLongClick ?: if (!app.isLoggedIn) null else {
    {
      val watch = app.isListed(post, "watchlist")
      val fav = app.isListed(post, "favorite")
      app.sheets.show(SheetSpec(post.title(loc.lang), listOf(
          SheetAction(loc(S.details), Icons.info) { nav.push(Route.Details(post)) },
          SheetAction(loc(S.watchlist), if (watch) Icons.bookmark else Icons.bookmarkOutline) { app.background { app.toggleList(post, "watchlist") } },
          SheetAction(loc(S.favorites), if (fav) Icons.heart else Icons.heartOutline) { app.background { app.toggleList(post, "favorite") } },
      )))
    }
  }
  // Where the card is, so its poster can fly from here to the title's page, as on the Mac.
  var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
  Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
    CardSurface(
        onClick = {
          if (!calm) bounds?.let { nav.flight = Flight(post, it) }
          onClick()
        },
        modifier = modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
        glow = rememberPosterTint(post), onLongClick = lists,
        front = { PosterCaption(post) },
    ) { PosterArt(post, width, loads) }
    // Outside the card so the caption does not scale with the poster.
    if (titled) {
      Text(
          post.title(LocalLoc.current.lang), Modifier.width(width),
          style = app(22, FontWeight.Medium).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
          color = Theme.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * The Mac's `RankedCard`: a poster with its place in the top ten beside it, in figures as tall as
 * the poster and tucked under its edge. The figure is a quiet grey until the card has focus, and
 * then takes the poster's colour.
 */
@Composable
fun RankedCard(rank: Int, post: Post, onClick: () -> Unit, modifier: Modifier = Modifier, loads: Boolean = true) {
  val loc = LocalLoc.current
  var focused by remember { mutableStateOf(false) }
  val tint = rememberPosterTint(post)
  val top by animateColorAsState(if (focused) tint else Theme.text1.copy(alpha = 0.5f), tween(200), label = "rank")
  val bottom by animateColorAsState(if (focused) tint.copy(alpha = 0.4f) else Theme.text1.copy(alpha = 0.07f), tween(200), label = "rank2")
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy((-31).dp)) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Text(
          loc.num(rank), Modifier.height(Theme.posterWidth * 1.5f),
          style = app(218, FontWeight.ExtraBold).copy(brush = Brush.verticalGradient(listOf(top, bottom)), lineHeight = 300.sp), maxLines = 1, softWrap = false,
      )
    }
    PosterCard(post, onClick, modifier.onFocusChanged { focused = it.hasFocus }, loads = loads)
  }
}

/**
 * The Mac's `ContinueCard`: a wide card for something half-watched, with the scene, the name, where
 * the viewer is and what is left, and how far in. In focus a white play disc comes up on it.
 */
@Composable
fun ContinueCard(post: Post, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null) {
  val loc = LocalLoc.current
  val app = LocalApp.current
  CardSurface(onClick = onClick, modifier = modifier, radius = 18.dp, scale = 1.04f, glow = rememberPosterTint(post), onLongClick = onLongClick) {
    val focused = LocalCardFocused.current
    val disc by animateFloatAsState(if (focused) 1f else 0f, Theme.focusSpring(), label = "disc")
    Box(Modifier.size(374.dp, 210.dp).background(Theme.surface2)) {
      RemoteImage(post.cover ?: post.poster.big ?: post.poster.large, Modifier.fillMaxSize(), blurhash = post.poster.blurhash, pixelWidth = 480)
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
      Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // A Latin name from the left, as on the posters; the Persian lines under it keep their side.
        Text(
            post.title(loc.lang), Modifier.fillMaxWidth(),
            style = app(20, FontWeight.Bold).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val resume = post.resume
        val line = if (post.id in app.newEpisodes) "● ${loc(S.newEpisode)}" else resume?.let { r ->
          listOfNotNull(
              if (r.season != null && r.episode != null) loc.episodeTag(r.season!!, Episode.label(r.episode, 1)) else null,
              r.remainingMinutes?.let { "${loc.num(it)} ${loc(S.minLeft)}" },
          ).joinToString(" · ").ifEmpty { null }
        }
        line?.let { Text(it, style = app(16, FontWeight.Medium), color = Color.White.copy(alpha = 0.72f), maxLines = 1) }
        // Filled from the left in every language, as time runs (the owner).
        Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.22f), capsule), contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft) {
          Box(Modifier.fillMaxWidth((resume?.progress ?: 0.0).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(Theme.accent, capsule))
        }
      }
      Box(
          Modifier.align(Alignment.Center).size(68.dp).graphicsLayer {
            alpha = disc
            scaleX = 0.8f + 0.2f * disc
            scaleY = 0.8f + 0.2f * disc
          }.background(Color.White, CircleShape),
          contentAlignment = Alignment.Center,
      ) { Icon(Icons.play, null, Modifier.size(30.dp), tint = Theme.focusInk) }
    }
  }
}

// MARK: - Text

/** A faint capsule around a fact or a score, as the Mac's `Chip`. */
@Composable
fun Chip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
      modifier.height(44.dp).background(Color.White.copy(alpha = 0.12f), capsule).padding(horizontal = 17.dp),
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
fun MetaLine(post: Post, genres: Int = 2, expanded: Boolean = false, allScores: Boolean = false, tmdbScore: Double? = null) {
  val loc = LocalLoc.current
  val scores: @Composable (Boolean) -> Unit = { all ->
    post.imdbScore?.let { ScoreChip("IMDb", Theme.imdb, Fmt.score(it)) }
    // The Apple TV's colours: Rotten Tomatoes fresh from 60% as on the site, Metacritic in its three bands.
    post.rottenTomatoes?.let { ScoreChip("RT", if (it >= 60) Color(0xFFFA4B2A) else Color(0xFF7BB661), "$it%") }
    if (all) post.metacritic?.let { ScoreChip("MC", if (it >= 61) Color(0xFF66CC33) else if (it >= 40) Color(0xFFFFCC33) else Color(0xFFFF5A4D), "$it") }
    if (all || post.rottenTomatoes == null) post.fanScore?.let { ScoreChip(it.source, Color(0xFF6C8CFF), Fmt.score(it.value)) }
    // TMDB's own, with the user's key, on a title page (the Tizen app's).
    if (all) tmdbScore?.let { ScoreChip("TMDB", Color(0xFF01B4E4), Fmt.score(it)) }
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
          Modifier.padding(start = 8.dp), style = app(24, FontWeight.Medium), color = Theme.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
      scores(allScores)
      facts()
    }
  }
}

/** Small coloured label above a title: what kind of thing it is. */
/** The Mac's kicker over a title: a short bar in the title's colour, and what it is. */
@Composable
fun Eyebrow(text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(5.dp, 21.dp).background(LocalHue.current ?: Theme.accent, capsule))
    Text(text, style = app(22, FontWeight.SemiBold), color = Theme.text2, maxLines = 1)
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
  // Filled from the left in every language, as time runs (the owner).
  Box(modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.22f), capsule), contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft) {
    Box(Modifier.fillMaxWidth(progress.toFloat().coerceIn(0.02f, 1f)).height(6.dp).background(Theme.accent, capsule))
  }
}
