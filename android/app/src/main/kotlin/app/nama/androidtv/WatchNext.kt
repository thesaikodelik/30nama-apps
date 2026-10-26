package app.nama.androidtv

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvContract.WatchNextPrograms
import android.os.Build
import android.util.Log
import app.nama.androidtv.data.WatchNextRules
import app.nama.core.Episode
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.Post
import app.nama.core.S
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * "Continue watching" on the TV's own home screen, in its Play Next row (Android's Watch Next
 * table), and followed series with a new episode out. A row opens the title, or plays it on.
 * Android 8 and later; some TVs have no such table, and then nothing happens. Learnt from 30namaTV.
 */
object WatchNext {
  private val lock = Mutex()

  /** What one row offers. */
  data class Entry(val post: Post, val news: Boolean)

  /**
   * Brings the row in line with [entries]. [known] false (30nama did not answer) leaves it as it is,
   * so a slow network does not empty the viewer's home screen.
   */
  suspend fun sync(context: Context, entries: List<Entry>, lang: Lang, known: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    lock.withLock {
      withContext(Dispatchers.IO) {
        runCatching { apply(context, entries, Loc(lang), known) }.onFailure { Log.w(AppModel.TAG, "watch next: $it") }
      }
    }
  }

  /** Every row taken away, as when the viewer signs out. */
  suspend fun clear(context: Context) = sync(context, emptyList(), Lang.en, known = true)

  @SuppressLint("RestrictedApi")
  private fun apply(context: Context, entries: List<Entry>, loc: Loc, known: Boolean) {
    val resolver = context.contentResolver
    // The table only ever returns this app's own rows, and refuses a selection: read it whole.
    val rows = mutableListOf<WatchNextRules.Row>()
    resolver.query(WatchNextPrograms.CONTENT_URI, arrayOf(WatchNextPrograms._ID, WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_DATA, WatchNextPrograms.COLUMN_BROWSABLE), null, null, null)?.use { c ->
      while (c.moveToNext()) {
        val post = c.getString(1)?.toIntOrNull() ?: continue
        val key = c.getBlob(2)?.decodeToString().orEmpty()
        rows += WatchNextRules.Row(c.getLong(0), post, key, c.getInt(3) == 1)
      }
    }
    val wanted = entries.map { WatchNextRules.Want(it.post.id, key(it)) }
    val byId = entries.associateBy { it.post.id }
    val now = System.currentTimeMillis()
    for (change in WatchNextRules.plan(rows, wanted, known)) {
      when (change) {
        is WatchNextRules.Change.Delete -> resolver.delete(TvContract.buildWatchNextProgramUri(change.rowId), null, null)
        is WatchNextRules.Change.Insert -> byId[change.want.postId]?.let { resolver.insert(WatchNextPrograms.CONTENT_URI, values(context, it, change.want.key, loc, now - entries.indexOf(it) * 1000L)) }
        is WatchNextRules.Change.Update -> byId[change.want.postId]?.let {
          resolver.update(TvContract.buildWatchNextProgramUri(change.rowId), values(context, it, change.want.key, loc, now - entries.indexOf(it) * 1000L), null, null)
        }
      }
    }
    if (BuildConfig.DEBUG) {
      val count = resolver.query(WatchNextPrograms.CONTENT_URI, arrayOf(WatchNextPrograms.COLUMN_TITLE), null, null, null)?.use { it.count } ?: -1
      Log.i(AppModel.TAG, "watch next: $count rows")
    }
  }

  /** What the row offers, in a form that changes exactly when there is something new to offer. */
  private fun key(e: Entry): String {
    val r = e.post.resume
    return if (e.news) "new" else "continue ${r?.fileID} ${((r?.time ?: 0.0) / 30).toInt()}"
  }

  private fun values(context: Context, e: Entry, key: String, loc: Loc, engaged: Long) = ContentValues().apply {
    val post = e.post
    val r = post.resume
    put(WatchNextPrograms.COLUMN_TYPE, if (post.isSeries) TvContract.PreviewPrograms.TYPE_TV_EPISODE else TvContract.PreviewPrograms.TYPE_MOVIE)
    put(WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, if (e.news) WatchNextPrograms.WATCH_NEXT_TYPE_NEW else WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
    // The launcher's strings follow the app's language, not the system's.
    put(WatchNextPrograms.COLUMN_TITLE, post.title(loc.lang))
    if (post.isSeries && r?.season != null && r.episode != null) put(WatchNextPrograms.COLUMN_EPISODE_TITLE, loc.episodeTag(r.season!!, Episode.label(r.episode, 1)))
    else putNull(WatchNextPrograms.COLUMN_EPISODE_TITLE)
    if (e.news) put(WatchNextPrograms.COLUMN_SHORT_DESCRIPTION, loc(S.newEpisode)) else putNull(WatchNextPrograms.COLUMN_SHORT_DESCRIPTION)
    (post.cover ?: post.poster.big)?.let { put(WatchNextPrograms.COLUMN_POSTER_ART_URI, it.toString()) }
    put(WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_16_9)
    if (!e.news && r != null && r.time > 0) {
      put(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, (r.time * 1000).toInt())
      put(WatchNextPrograms.COLUMN_DURATION_MILLIS, (r.duration * 1000).toInt().takeIf { it > 0 } ?: ((r.time * 1000) / r.progress.coerceAtLeast(0.01)).toInt())
    } else {
      putNull(WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
      putNull(WatchNextPrograms.COLUMN_DURATION_MILLIS)
    }
    // Kept in 30nama's order.
    put(WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, engaged)
    put(WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, post.id.toString())
    put(WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_DATA, key.toByteArray())
    val open = Intent(context, MainActivity::class.java).putExtra(OPEN, post.id).putExtra(PLAY, !e.news).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    put(WatchNextPrograms.COLUMN_INTENT_URI, open.toUri(Intent.URI_INTENT_SCHEME))
  }

  /** The rows' post ids the viewer took off the home screen, for the news to forget them too. */
  fun removed(context: Context): Set<Int> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptySet()
    return runCatching {
      val out = HashSet<Int>()
      context.contentResolver.query(WatchNextPrograms.CONTENT_URI, arrayOf(WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, WatchNextPrograms.COLUMN_BROWSABLE), null, null, null)?.use { c ->
        while (c.moveToNext()) if (c.getInt(1) == 0) c.getString(0)?.toIntOrNull()?.let { out += it }
      }
      out
    }.getOrDefault(emptySet())
  }

  const val OPEN = "open"
  const val PLAY = "play"
}
