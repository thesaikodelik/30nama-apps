package app.nama.androidtv.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.inspector.frame.FrameExtractor
import app.nama.androidtv.AppModel
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures

/**
 * The picture at the point being sought to, over the seek bar, as the Apple TV shows it. 30nama
 * has no thumbnail strips, so frames come from a second, small reader of the stream's lowest
 * quality, at the keyframe nearest each point (no decoding up to the exact frame). Learnt from 30namaTV:
 *  - only software decoders: a second hardware decoder next to the film's can stall or break it
 *    on an older TV; and when the film itself is decoded in software (a box with no hardware
 *    decoder for it), there are no previews at all, so they never take the film's processor;
 *  - points in steps of five seconds, one request at a time, the newest point replacing any waiting;
 *  - a request is never cancelled midway: a cancelled one wedges the reader for the rest of the run;
 *  - a network error closes the reader, and the next seek opens it again on a fresh connection;
 *    three misses in a row and previews are off for this file.
 */
@OptIn(UnstableApi::class)
class SeekPreview(private val context: Context, private val network: DataSource.Factory) {
  /** The frame for the point last asked about, when it has come. */
  var frame by mutableStateOf<Pair<Long, Bitmap>?>(null)
    private set

  private val frames = LruCache<Long, Bitmap>(40)
  private var extractor: FrameExtractor? = null
  private var url: String? = null
  private var busy = false
  private var wanted: Long? = null
  private var misses = 0
  private var off = false

  /** The file playing changed: previews start afresh on its lowest quality. */
  fun use(lowest: String?) {
    close()
    url = lowest
    frames.evictAll()
    frame = null
    misses = 0
    off = lowest == null
  }

  /** The film is being decoded by [decoder]; a software one means no previews. */
  fun filmDecoder(decoder: String) {
    if (isSoftware(decoder)) {
      Log.i(AppModel.TAG, "seek previews off: the film decodes in software ($decoder)")
      off = true
      close()
    }
  }

  /** Asks for the frame at [seconds]; [frame] follows when it has come. */
  fun want(seconds: Double) {
    if (off) return
    val point = (seconds / STEP).toLong() * STEP * 1000
    frames.get(point)?.let {
      frame = point to it
      return
    }
    wanted = point
    pump()
  }

  private fun pump() {
    if (busy || off) return
    val point = wanted ?: return
    wanted = null
    val reader = extractor ?: open() ?: return
    busy = true
    Futures.addCallback(reader.getFrame(point), object : FutureCallback<FrameExtractor.Frame> {
      override fun onSuccess(result: FrameExtractor.Frame) {
        busy = false
        misses = 0
        frames.put(point, result.bitmap)
        frame = point to result.bitmap
        pump()
      }

      override fun onFailure(t: Throwable) {
        busy = false
        misses++
        Log.w(AppModel.TAG, "seek preview at $point: $t")
        if (misses >= 3 || t.cause !is HttpDataSource.HttpDataSourceException) {
          off = true
          close()
        } else {
          close()
          pump()
        }
      }
    }, androidx.core.content.ContextCompat.getMainExecutor(context))
  }

  private fun open(): FrameExtractor? {
    val u = url ?: return null
    return runCatching {
      val item = MediaItem.Builder().setUri(u).apply { if (u.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
      FrameExtractor.Builder(context, item)
          .setMediaSourceFactory(DefaultMediaSourceFactory(network))
          .setSeekParameters(SeekParameters.CLOSEST_SYNC)
          .setMediaCodecSelector(SOFTWARE)
          .setEffects(listOf(Presentation.createForHeight(180)))
          .build()
    }.onFailure {
      Log.w(AppModel.TAG, "seek previews: $it")
      off = true
    }.getOrNull()?.also { extractor = it }
  }

  fun close() {
    // Never cancelled midway; a request in flight finishes on a reader that is already closing.
    runCatching { extractor?.close() }
    extractor = null
  }

  companion object {
    /** Seconds between previews, the arrows' own step. */
    const val STEP = 5L

    private val SOFTWARE = MediaCodecSelector { mime, secure, tunneling ->
      MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).filter { it.softwareOnly }
    }

    /**
     * Google's own decoders are software, whatever an emulator's codec list says about them; the
     * others, from Android 10, say themselves.
     */
    fun isSoftware(name: String): Boolean {
      if (name.startsWith("OMX.google.") || name.startsWith("c2.android.")) return true
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
      return runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name }?.isSoftwareOnly }.getOrNull() ?: false
    }
  }
}
