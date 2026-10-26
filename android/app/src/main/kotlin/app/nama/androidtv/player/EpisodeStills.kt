package app.nama.androidtv.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.inspector.frame.FrameExtractor
import app.nama.androidtv.attempt
import app.nama.core.AudioMix
import app.nama.core.StreamItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * A still for each episode tile, taken from the episode itself: 30nama has none. A quarter of the
 * way to the credits (or five minutes in), from the lowest quality, with a software decoder, one at
 * a time; a frame that is nearly black (a fade, a title card) is passed over for one further in.
 * Kept on the disk as small JPEGs, so each episode costs this once. Learnt from 30namaTV.
 */
@OptIn(UnstableApi::class)
class EpisodeStills(private val context: Context, http: OkHttpClient) {
  /** What has been taken, by file id. */
  val known = mutableStateMapOf<String, ImageBitmap>()
  private val failed = HashSet<String>()
  private val lock = Mutex()
  private val network = OkHttpDataSource.Factory(http)
  private val dir = File(context.cacheDir, "frames").apply { mkdirs() }

  suspend fun still(item: StreamItem) {
    val id = item.fileID.ifEmpty { return }
    if (id in known || id in failed) return
    val file = File(dir, "$id.jpg")
    withContext(Dispatchers.IO) { if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { known[id] = it.asImageBitmap() } }
    if (id in known) return
    val url = (item.qualities.filter { !it.isAuto }.minByOrNull { it.height } ?: item.qualities.firstOrNull())?.url(AudioMix.stereo)?.toString() ?: return
    lock.withLock {
      // Waited on to the end, never cancelled midway: a cancelled request wedges the reader.
      val bitmap = withContext(NonCancellable) { take(url, item) }
      if (bitmap == null) {
        failed += id
        return
      }
      known[id] = bitmap.asImageBitmap()
      withContext(Dispatchers.IO) {
        runCatching {
          val tmp = File(dir, "$id.part")
          tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
          tmp.renameTo(file)
          trim()
        }
      }
    }
  }

  private suspend fun take(url: String, item: StreamItem): Bitmap? {
    val credits = item.creditsStart?.takeIf { it > 0 }
    val first = ((credits ?: 1200.0) * 0.25).coerceAtMost(300.0)
    val media = MediaItem.Builder().setUri(url).apply { if (url.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
    val extractor = attempt {
      FrameExtractor.Builder(context, media)
          .setMediaSourceFactory(DefaultMediaSourceFactory(network))
          .setSeekParameters(SeekParameters.CLOSEST_SYNC)
          .setMediaCodecSelector { mime, secure, tunneling -> MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).filter { it.softwareOnly } }
          .setEffects(listOf(Presentation.createForHeight(200)))
          .build()
    } ?: return null
    try {
      val a = attempt { extractor.getFrame((first * 1000).toLong()).await().bitmap } ?: return null
      if (luma(a) >= 24) return a
      val b = attempt { extractor.getFrame((((credits ?: 1200.0) * 0.4) * 1000).toLong()).await().bitmap }
      return if (b != null && luma(b) > luma(a)) b else a
    } finally {
      runCatching { extractor.close() }
    }
  }

  /** Mean brightness, 0...255, of a tiny copy of the picture. */
  private fun luma(b: Bitmap): Int {
    val s = Bitmap.createScaledBitmap(b, 16, 9, true)
    var sum = 0L
    for (y in 0 until 9) for (x in 0 until 16) {
      val p = s.getPixel(x, y)
      sum += (299 * ((p shr 16) and 255) + 587 * ((p shr 8) and 255) + 114 * (p and 255)) / 1000
    }
    return (sum / 144).toInt()
  }

  /** Twenty megabytes of stills at most; the oldest go first. */
  private fun trim() {
    val files = dir.listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.lastModified() } ?: return
    var total = files.sumOf { it.length() }
    for (f in files) {
      if (total <= 20L * 1024 * 1024) break
      total -= f.length()
      f.delete()
    }
  }
}

/** The future's value, without the guava-coroutines library for one function. */
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { c ->
      com.google.common.util.concurrent.Futures.addCallback(this, object : com.google.common.util.concurrent.FutureCallback<T> {
        override fun onSuccess(result: T) = c.resumeWith(Result.success(result))
        override fun onFailure(t: Throwable) = c.resumeWith(Result.failure(t))
      }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
    }
