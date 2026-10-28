package app.nama.androidtv.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.nama.androidtv.data.Lively
import app.nama.core.Post
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colour a title's page is lit in, as the Mac's `SceneTint`: taken from its wide scene picture
 * when that has a lively colour in it, and from its poster otherwise. A poster is often dark or grey
 * where the scene behind the page is full of colour; and the blur code's average (`Post.tint`) is
 * only the last resort, as the server sends the same few codes for many posters. Worked out once
 * for each title, from a small copy of the picture the backdrop already has.
 */
object SceneTint {
  /** What each title's scene gave; a missing colour is kept as null so it is not looked for again. */
  private val known = ConcurrentHashMap<Int, Optional>()
  private val posters = ConcurrentHashMap<Int, Optional>()

  private class Optional(val color: Color?)

  /** The colour for this title if it is known already, else the poster's blur code's. */
  fun known(post: Post): Color = known[post.id]?.color ?: post.tint()

  /** The poster's own colour, for the glow behind a card, if it has been worked out. */
  fun knownPoster(post: Post): Color? = posters[post.id]?.color

  suspend fun colour(context: Context, post: Post): Color {
    known[post.id]?.let { return it.color ?: post.tint() }
    val scene = post.cover?.let { lively(context, it) }
    val poster = if (scene?.getOrNull() != null) null else (post.poster.big ?: post.poster.large)?.let { lively(context, it) }
    val found = scene?.getOrNull() ?: poster?.getOrNull()
    // A picture that did not come is asked for again next time; only a real answer is kept.
    if (found != null || (scene?.isFailure != true && poster?.isFailure != true)) known[post.id] = Optional(found)
    return found ?: post.tint()
  }

  suspend fun posterColour(context: Context, post: Post): Color {
    posters[post.id]?.let { return it.color ?: post.tint() }
    val result = post.poster.url(Theme.posterWidth.value * 1.1)?.let { lively(context, it) }
    val found = result?.getOrNull()
    if (result?.isFailure != true) posters[post.id] = Optional(found)
    return found ?: post.tint()
  }

  /** The picture's lively colour, null when it has none; a failure when the picture did not come. */
  private suspend fun lively(context: Context, url: URI): Result<Color?> {
    val request = ImageRequest.Builder(context).data(url.toString()).size(64, 36).allowHardware(false).build()
    val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image
        ?: return Result.failure(IllegalStateException("picture did not come"))
    return Result.success(withContext(Dispatchers.Default) {
      val small = Bitmap.createScaledBitmap(image.toBitmap(), 32, 18, true)
      val pixels = IntArray(32 * 18).also { small.getPixels(it, 0, 32, 0, 0, 32, 18) }
      Lively.of(pixels)?.let { Color.hsv(it.hue * 360f, it.saturation, it.brightness) }
    })
  }
}

/** The colour of [post]'s page: the blur code's at once, the scene's once it is worked out. */
@Composable
fun rememberSceneTint(post: Post?): Color? {
  val context = LocalContext.current
  var tint by remember(post?.id) { mutableStateOf(post?.let { SceneTint.known(it) }) }
  LaunchedEffect(post?.id) { post?.let { tint = SceneTint.colour(context, it) } }
  return tint
}
