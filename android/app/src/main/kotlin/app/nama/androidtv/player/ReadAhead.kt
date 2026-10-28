package app.nama.androidtv.player

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PriorityTaskManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import app.nama.androidtv.AppModel
import java.io.File
import java.util.TreeSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What of a title is fetched ahead onto the disk, for a slow or shaky connection (Settings >
 * Read ahead). Learnt from 30namaTV:
 *  - the cache is keyed by a segment's address without the CDN's signature (the address of the
 *    viewer, an expiry and a hash in the path, and the query), so a title played again, or after
 *    the signed link has run out, finds what is already there;
 *  - the playlists themselves are never kept: their signed addresses expire;
 *  - the oldest-written is evicted first, not the least recently read, or the segments fetched
 *    ahead and not yet played would be the first to go;
 *  - fetching ahead always gives way to the player itself.
 */
@OptIn(UnstableApi::class)
object Disk {
  @Volatile private var made: SimpleCache? = null

  /** The process's one cache, a quarter of the free space up to a gigabyte, or none with under 64 MB. */
  fun cache(context: Context): SimpleCache? {
    made?.let { return it }
    synchronized(this) {
      made?.let { return it }
      val dir = File(context.cacheDir, "video")
      val free = runCatching { StatFs(context.cacheDir.path).availableBytes }.getOrDefault(0L)
      val size = minOf(free / 4, 1L shl 30)
      if (size < 64L shl 20) return null
      return runCatching { SimpleCache(dir, WrittenOrder(size), StandaloneDatabaseProvider(context)) }
          .onFailure { Log.w(AppModel.TAG, "video cache: $it") }
          .getOrNull()
          ?.also { made = it }
    }
  }

  private val signed = Regex("/\\d{1,3}(?:\\.\\d{1,3}){3}/\\d{10}/[0-9a-f]{32,}/")

  /** A segment's address as the cache knows it: without the query and the signature in its path. */
  fun key(url: String): String = url.substringBefore('?').replaceFirst(signed, "/")

  fun isPlaylist(uri: Uri): Boolean = uri.path?.endsWith(".m3u8") == true
}

/** Evicts what was written first, whatever has been read since. */
@OptIn(UnstableApi::class)
private class WrittenOrder(private val max: Long) : CacheEvictor {
  private val spans = TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it.key }.thenBy { it.position })
  private var size = 0L

  override fun requiresCacheSpanTouches(): Boolean = false

  override fun onCacheInitialized() {}

  override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
    if (length != C.LENGTH_UNSET.toLong()) while (size + length > max && spans.isNotEmpty()) cache.removeSpan(spans.first())
  }

  override fun onSpanAdded(cache: Cache, span: CacheSpan) {
    spans.add(span)
    size += span.length
    while (size > max && spans.isNotEmpty()) cache.removeSpan(spans.first())
  }

  override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
    if (spans.remove(span)) size -= span.length
  }

  override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
    onSpanRemoved(cache, oldSpan)
    onSpanAdded(cache, newSpan)
  }
}

/** Playlists straight from the network; segments through the disk. */
@OptIn(UnstableApi::class)
class Routing(private val network: DataSource.Factory, private val cached: DataSource.Factory) : DataSource.Factory {
  override fun createDataSource(): DataSource = object : DataSource {
    private val plain = network.createDataSource()
    private val disk = cached.createDataSource()
    private var open: DataSource? = null

    override fun addTransferListener(listener: TransferListener) {
      plain.addTransferListener(listener)
      disk.addTransferListener(listener)
    }

    override fun open(spec: DataSpec): Long {
      val s = if (Disk.isPlaylist(spec.uri)) plain else disk
      open = s
      return s.open(spec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = open!!.read(buffer, offset, length)

    override fun getUri(): Uri? = open?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = open?.responseHeaders ?: emptyMap()

    override fun close() {
      open?.close()
      open = null
    }
  }
}

/**
 * Walks the playing rendition's segments ahead of the playhead and writes them to the disk, up to
 * [percent] of what is left of the file. It goes on while paused, which is when a slow line
 * catches up.
 */
@OptIn(UnstableApi::class)
class ReadAhead(private val cache: Cache, private val upstream: DataSource.Factory, private val priority: PriorityTaskManager) {
  private var job: Job? = null

  fun follow(scope: CoroutineScope, player: ExoPlayer, percent: () -> Int) {
    job?.cancel()
    job = scope.launch {
      val source = CacheDataSource.Factory()
          .setCache(cache)
          .setUpstreamDataSourceFactory(upstream)
          .setCacheKeyFactory { Disk.key(it.uri.toString()) }
          .setUpstreamPriorityTaskManager(priority)
          .setUpstreamPriority(C.PRIORITY_DOWNLOAD)
          .createDataSource()
      var failures = 0
      while (isActive) {
        val share = percent()
        val manifest = player.currentManifest as? HlsManifest
        if (share <= 0 || manifest == null) {
          delay(5_000)
          continue
        }
        val playlist = manifest.mediaPlaylist
        val at = player.currentPosition * 1000
        val until = at + (playlist.durationUs - at) * share / 100
        val base = Uri.parse(playlist.baseUri)
        val next = playlist.segments.firstOrNull { s ->
          s.relativeStartTimeUs + s.durationUs > at && s.relativeStartTimeUs < until && !cached(Disk.key(base.resolve(s.url)), s.byteRangeOffset, s.byteRangeLength)
        }
        if (next == null) {
          // All that was asked for is on the disk: look again once the playhead has moved on.
          delay(5_000)
          continue
        }
        val spec = DataSpec.Builder().setUri(base.resolve(next.url)).setPosition(next.byteRangeOffset).setLength(next.byteRangeLength).build()
        val result = withContext(Dispatchers.IO) { runCatching { CacheWriter(source, spec, null, null).cache() } }
        // The player loading itself takes the line first: that is waiting, not failing.
        if (result.exceptionOrNull() is androidx.media3.common.PriorityTaskManager.PriorityTooLowException) {
          delay(2_000)
          continue
        }
        val ok = result.isSuccess
        // The CDN refusing again and again (a 403 behind another VPN exit): stop trying for this file.
        failures = if (ok) 0 else failures + 1
        if (failures >= 3) return@launch
        if (!ok) delay(2_000)
      }
    }
  }

  /**
   * The first [seconds] of another file onto the disk: the next episode, as the credits of this one
   * roll, so that autoplay starts at once. A master playlist is narrowed to the rendition [height]
   * tall, or the nearest. One segment at a time, below the playing film, and given up on any error.
   */
  fun warm(scope: CoroutineScope, url: String, height: Int?, seconds: Int = 30) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val source = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory { Disk.key(it.uri.toString()) }
            .setUpstreamPriorityTaskManager(priority)
            .setUpstreamPriority(C.PRIORITY_DOWNLOAD)
            .createDataSource()
        fun playlist(u: String): HlsPlaylist {
          // The stream opens the source itself, and closes it.
          return androidx.media3.datasource.DataSourceInputStream(upstream.createDataSource(), DataSpec(Uri.parse(u))).use {
            HlsPlaylistParser().parse(Uri.parse(u), it)
          }
        }
        var media = playlist(url)
        if (media is HlsMultivariantPlaylist) {
          val variant = media.variants.minByOrNull { v -> height?.let { kotlin.math.abs(v.format.height - it) } ?: -v.format.bitrate } ?: return@launch
          media = playlist(variant.url.toString())
        }
        val list = media as? HlsMediaPlaylist ?: return@launch
        val base = Uri.parse(list.baseUri)
        for (s in list.segments) {
          if (!isActive || s.relativeStartTimeUs >= seconds * 1_000_000L) break
          val spec = DataSpec.Builder().setUri(base.resolve(s.url)).setPosition(s.byteRangeOffset).setLength(s.byteRangeLength).build()
          // Below the playing film: while it loads, wait for it rather than give up.
          while (true) {
            try {
              CacheWriter(source, spec, null, null).cache()
              break
            } catch (e: androidx.media3.common.PriorityTaskManager.PriorityTooLowException) {
              priority.proceed(C.PRIORITY_DOWNLOAD)
            }
          }
        }
      }.onFailure { Log.i(AppModel.TAG, "warm next: $it") }
    }
  }

  private fun cached(key: String, offset: Long, length: Long): Boolean =
      length != C.LENGTH_UNSET.toLong() && cache.isCached(key, offset, length) || length == C.LENGTH_UNSET.toLong() && cache.getCachedBytes(key, 0, Long.MAX_VALUE) > 0

  fun stop() {
    job?.cancel()
  }

  private fun Uri.resolve(path: String): String = androidx.media3.common.util.UriUtil.resolve(toString(), path)
}
