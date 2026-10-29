package app.nama.androidtv.ui

import android.content.Context
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.nama.core.BlurHash
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * Pictures. 30nama's CDN keeps art at its edge for a few hours only, after which every poster comes
 * from the origin again, so what was fetched once stays on the disk; and a page of posters is a
 * few dozen requests to one host, so more of them go at once than OkHttp's default five.
 */
object Images {
  fun loader(context: Context, client: OkHttpClient): ImageLoader {
    val dispatcher = Dispatcher().apply {
      maxRequests = 32
      maxRequestsPerHost = 16
    }
    val images = client.newBuilder().dispatcher(dispatcher).connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    return ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { images })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images").toOkioPath()).maxSizeBytes(250L * 1024 * 1024).build() }
        .build()
  }

  private val hashes = LruCache<String, ImageBitmap>(200)

  /** A blur code as a tiny picture, the placeholder until the real one comes. */
  fun blurhash(hash: String?): ImageBitmap? {
    hash ?: return null
    hashes.get(hash)?.let { return it }
    val w = 16
    val h = 24
    val px = BlurHash.pixels(hash, w, h) ?: return null
    val colors = IntArray(w * h) { i ->
      val o = i * 4
      (0xFF shl 24) or ((px[o].toInt() and 0xFF) shl 16) or ((px[o + 1].toInt() and 0xFF) shl 8) or (px[o + 2].toInt() and 0xFF)
    }
    return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888).asImageBitmap().also { hashes.put(hash, it) }
  }
}

/**
 * A picture from the network, drawn over its blur code while it loads. [pixelWidth] is the size
 * it is decoded at: a weak TV has room for many posters only when each is no bigger than drawn.
 */
@Composable
fun RemoteImage(
    url: URI?,
    modifier: Modifier = Modifier,
    blurhash: String? = null,
    pixelWidth: Int = 400,
    contentScale: ContentScale = ContentScale.Crop,
    fade: Boolean = true,
    alignment: Alignment = Alignment.Center,
) {
  val context = LocalContext.current
  val placeholder = remember(blurhash) { Images.blurhash(blurhash) }
  // As the Mac's RemoteImage: a picture already in memory is there at once; one that had to come
  // fades in over its blur, and the blur goes once it is in, so it never shows at a rounded edge.
  var arrived by remember(url) { mutableStateOf(false) }
  var instant by remember(url) { mutableStateOf(!fade) }
  val shown by animateFloatAsState(if (arrived) 1f else 0f, if (instant) snap() else tween(350, easing = LinearOutSlowInEasing), label = "picture")
  Box(modifier) {
    if (placeholder != null && shown < 1f) Image(placeholder, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    if (url != null) {
      val request = remember(url, pixelWidth) {
        ImageRequest.Builder(context).data(url.toString()).size(coil3.size.Size(coil3.size.Dimension(pixelWidth), coil3.size.Dimension.Undefined)).build()
      }
      AsyncImage(
          model = request, contentDescription = null,
          modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown },
          contentScale = contentScale,
          alignment = alignment,
          onSuccess = { state ->
            if (state.result.dataSource == coil3.decode.DataSource.MEMORY_CACHE) instant = true
            arrived = true
          },
      )
    }
  }
}
