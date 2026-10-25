package app.nama.quest.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * A small image loader. Posters are decoded at the size they are drawn, because every panel is a
 * texture in GPU memory and a full-size backdrop per tile would add up fast.
 */
object Images {
  private val cache =
      object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
      }
  // A handful at a time, so a row of posters doesn't starve the stream or the API.
  private val gate = Semaphore(4)

  /** Where the bytes come from; the panel snapshots on the computer draw their own. */
  @Volatile var source: (String) -> ByteArray = { url ->
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8_000
    conn.readTimeout = 15_000
    conn.setRequestProperty("User-Agent", "30nama-quest")
    conn.inputStream.use { it.readBytes() }
  }

  fun cached(url: String, maxWidth: Int): Bitmap? = cache.get("$maxWidth|$url")

  suspend fun load(url: String, maxWidth: Int): Bitmap? {
    val key = "$maxWidth|$url"
    cache.get(key)?.let {
      return it
    }
    return gate.withPermit {
      withContext(Dispatchers.IO) {
        runCatching {
              val bytes = source(url)
              val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
              var sample = 1
              while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
            .getOrNull()
            ?.also { cache.put(key, it) }
      }
    }
  }
}

@Composable
fun rememberImage(url: String?, maxWidth: Int): ImageBitmap? {
  var image by remember(url, maxWidth) { mutableStateOf(url?.let { Images.cached(it, maxWidth)?.asImageBitmap() }) }
  LaunchedEffect(url, maxWidth) {
    if (url != null && image == null) image = Images.load(url, maxWidth)?.asImageBitmap()
  }
  return image
}
