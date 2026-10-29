package app.nama.androidtv.ui

import coil3.toBitmap
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.nama.core.Lang
import app.nama.core.Post
import app.nama.core.SubSource

/**
 * A title's name, as the Mac's `TitleArt`: its logo from TMDB when there is one, else the name in
 * large type with its other name under it. The logo is mostly in English; in Persian or Arabic the
 * name in that language follows it in smaller type. Not shown until TMDB answers (two seconds at
 * most), so the name does not flash up and give way to the logo.
 */
@Composable
fun TitleArt(post: Post, size: Int) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val context = androidx.compose.ui.platform.LocalContext.current
  // Null while it is being worked out; then the logo's picture, or none (and the name is shown).
  var logo by remember(post.id) { mutableStateOf<Result<androidx.compose.ui.graphics.ImageBitmap?>?>(null) }
  // Known already, as on going back to a page: shown at once, with nothing to wait for (the Mac's).
  var known by remember(post.id) { mutableStateOf(false) }
  LaunchedEffect(post.id) {
    // The logo is shown once its picture is in hand: a logo that has not come yet is a gap where
    // the name should be. Two seconds at most, as on the Mac; a logo found later still replaces the
    // name. The two seconds are kept apart from the asking: a request where TMDB or GitHub is
    // filtered neither answers nor fails, and blocks until its connection gives up, so a timeout
    // around it would wait as long as it does.
    val start = System.currentTimeMillis()
    launch {
      val picture = runCatching { logoPicture(context, app, post, loc.lang) }.getOrNull()
      if (picture != null && System.currentTimeMillis() - start < KNOWN_MS) known = true
      if (picture != null || logo == null) logo = Result.success(picture)
    }
    delay(2_000)
    if (logo == null) logo = Result.success(null)
  }
  val picture = logo?.getOrNull()
  // The Mac's arrival: up out of a blur from 14 points below, on its spring (a response of 0.7 s,
  // damping 0.9). What is chosen, the name or the logo, each arrives in turn: a logo that comes
  // after the name has shown comes in as the name did.
  val calm = LocalCalm.current
  val lively = LocalLively.current
  val arrival = remember(post.id) { androidx.compose.animation.core.Animatable(0f) }
  val stage = when {
    logo == null -> 0
    picture == null -> 1
    else -> 2
  }
  LaunchedEffect(stage) {
    if (stage == 0) return@LaunchedEffect
    if (known || calm) return@LaunchedEffect arrival.snapTo(1f)
    arrival.snapTo(0f)
    arrival.animateTo(1f, TITLE_SETTLE)
  }
  Column(
      Modifier
          // In one motion with the page: what is below moves down to make room as a taller logo
          // comes in, instead of jumping there (the owner, as the Mac's).
          .then(if (known || calm) Modifier else Modifier.animateContentSize(TITLE_SETTLE_SIZE, alignment = Alignment.TopStart))
          .graphicsLayer {
            val a = arrival.value
            alpha = a
            translationY = (1f - a) * 14.dp.toPx()
            // The blur only where it is affordable: on a weak TV the rise alone.
            if (lively && a < 1f && android.os.Build.VERSION.SDK_INT >= 31) {
              val r = (1f - a) * 12.dp.toPx()
              if (r > 0.5f) renderEffect = android.graphics.RenderEffect.createBlurEffect(r, r, android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect()
            }
          },
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (picture != null) {
      // Its own shape, up to eight sizes wide and 2.4 tall, from the reading start.
      val ratio = picture.width.toFloat() / picture.height.coerceAtLeast(1)
      val maxW = size * 8f
      val maxH = size * 2.4f
      val h = minOf(maxH, maxW / ratio)
      androidx.compose.foundation.Image(
          picture, post.title(loc.lang),
          Modifier.size((h * ratio).dp, h.dp),
          contentScale = ContentScale.Fit,
      )
      // Only a name in another script: a series with no Persian name would repeat the logo.
      if (loc.lang != Lang.en && SubSource.simple(post.title(loc.lang)) != SubSource.simple(post.title(Lang.en))) {
        Text(post.title(loc.lang), style = app((size * 0.38f).toInt(), FontWeight.SemiBold), color = Theme.text1)
      }
    } else {
      // Lines close together, as a heading's: Vazirmatn's own spacing (near 1.7) left a gap between
      // the two lines of a long name (the owner).
      Text(
          post.title(loc.lang),
          style = app(size, FontWeight.ExtraBold).copy(
              brush = Brush.verticalGradient(listOf(Color.White, Theme.text1.copy(alpha = 0.84f))),
              lineHeight = (size * 1.15f).sp,
              lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                  androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                  androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
              ),
          ),
          maxLines = 2, overflow = TextOverflow.Ellipsis,
      )
      post.alternateTitle(loc.lang)?.let { Text(it, style = app(30, FontWeight.Medium), color = Theme.text2, maxLines = 1) }
    }
  }
}

/** A logo in hand this soon was already known (in memory or on disk), not fetched. */
private const val KNOWN_MS = 120L
/** The Mac's `TitleArt.settle`: a spring with a response of 0.7 s and damping 0.9. */
private val TITLE_SETTLE = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.9f, stiffness = 80.6f)
private val TITLE_SETTLE_SIZE = androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 80.6f, visibilityThreshold = androidx.compose.ui.unit.IntSize.VisibilityThreshold)

/**
 * Fetches what a billboard shows for [post], its backdrop and logo, into the image cache, so both
 * appear with its text. The requests match [Stage]'s and [TitleArt]'s, or the cache would miss.
 */
suspend fun warmBillboard(context: android.content.Context, app: app.nama.androidtv.AppModel, post: Post, lang: Lang) = kotlinx.coroutines.coroutineScope {
  val backdrop = launch {
    val (url, width) = (post.cover ?: app.extras.known[post.id]?.backdrop)?.let { it to 1920 } ?: ((post.poster.big ?: post.poster.large ?: post.poster.url(428.0)) to 1000)
    if (url == null) return@launch
    val request = coil3.request.ImageRequest.Builder(context).data(url.toString())
        .size(coil3.size.Size(coil3.size.Dimension(width), coil3.size.Dimension.Undefined)).build()
    coil3.SingletonImageLoader.get(context).execute(request)
  }
  logoPicture(context, app, post, lang)
  backdrop.join()
}

/**
 * The title's logo at [height] points, for the player's top; until its picture is in hand, or where
 * there is none, [name] in its place. The title page has usually fetched it already.
 */
@Composable
fun TitleMark(post: Post, height: Int, maxWidth: Int, name: @Composable () -> Unit) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val context = androidx.compose.ui.platform.LocalContext.current
  var picture by remember(post.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
  LaunchedEffect(post.id) { picture = runCatching { logoPicture(context, app, post, loc.lang) }.getOrNull() }
  val p = picture
  if (p == null) return name()
  val ratio = p.width.toFloat() / p.height.coerceAtLeast(1)
  val h = minOf(height.toFloat(), maxWidth / ratio)
  androidx.compose.foundation.Image(p, post.title(loc.lang), Modifier.size((h * ratio).dp, h.dp), contentScale = ContentScale.Fit)
}

/** The title's logo from TMDB, decoded; null where there is none or it would not load. */
private suspend fun logoPicture(context: android.content.Context, app: app.nama.androidtv.AppModel, post: Post, lang: Lang): androidx.compose.ui.graphics.ImageBitmap? {
  val url = (app.extras.known[post.id] ?: app.extras.info(post, lang))?.logo ?: return null
  val request = coil3.request.ImageRequest.Builder(context).data(url.toString()).size(1000, 400).build()
  val result = coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult ?: return null
  return runCatching { result.image.toBitmap().asImageBitmap() }.getOrNull()
}
