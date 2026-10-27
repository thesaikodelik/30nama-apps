package app.nama.androidtv.ui

import coil3.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
  LaunchedEffect(post.id) {
    // The logo is shown once its picture is in hand: a logo that has not come yet is a gap where
    // the name should be. Two seconds at most, as on the Mac; a logo found later still replaces the name.
    val picture = kotlinx.coroutines.withTimeoutOrNull(2_000) { logoPicture(context, app, post, loc.lang) }
    logo = Result.success(picture)
    if (picture == null) logoPicture(context, app, post, loc.lang)?.let { logo = Result.success(it) }
  }
  val shown by animateFloatAsState(if (logo != null) 1f else 0f, tween(450), label = "title")
  val picture = logo?.getOrNull()
  Column(Modifier.graphicsLayer { alpha = shown }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
      Text(
          post.title(loc.lang),
          style = app(size, FontWeight.ExtraBold).copy(brush = Brush.verticalGradient(listOf(Color.White, Theme.text1.copy(alpha = 0.84f)))),
          maxLines = 2, overflow = TextOverflow.Ellipsis,
      )
      post.alternateTitle(loc.lang)?.let { Text(it, style = app(30, FontWeight.Medium), color = Theme.text2, maxLines = 1) }
    }
  }
}

/** The title's logo from TMDB, decoded; null where there is none or it would not load. */
private suspend fun logoPicture(context: android.content.Context, app: app.nama.androidtv.AppModel, post: Post, lang: Lang): androidx.compose.ui.graphics.ImageBitmap? {
  val url = (app.extras.known[post.id] ?: app.extras.info(post, lang))?.logo ?: return null
  val request = coil3.request.ImageRequest.Builder(context).data(url.toString()).size(1000, 400).build()
  val result = coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult ?: return null
  return runCatching { result.image.toBitmap().asImageBitmap() }.getOrNull()
}
