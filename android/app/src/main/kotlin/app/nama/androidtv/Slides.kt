package app.nama.androidtv

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.os.Build
import android.util.Log
import app.nama.core.Lang
import app.nama.core.Post

/**
 * The home page's slider, also on the TV's own home screen: a preview channel ("30nama") with its
 * titles, each with its wide picture, marked searchable so a launcher other than Google TV can read it.
 * The owner's launcher (CleanYourTV, the owner's own build) shows them behind the dock while 30nama's icon is
 * in focus. Written again, whole, whenever the home page is fetched; nothing else reads it back.
 */
object Slides {
  private const val TAG = "Nama"
  private const val CHANNEL = "slides"

  /** [wide]: TMDB's wide picture for a title 30nama has none for, or null. */
  suspend fun sync(context: Context, posts: List<Post>, lang: Lang, wide: suspend (Post) -> java.net.URI?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || posts.isEmpty()) return
    // Asked before the channel is written: its slides are replaced at once, not one by one.
    val pictures = posts.take(8).map { post ->
      // Never the tall poster stretched across the launcher's backdrop: such a title is left out.
      post.cover ?: kotlinx.coroutines.withTimeoutOrNull(10_000) { runCatching { wide(post) }.getOrNull() }
    }
    runCatching {
      val resolver = context.contentResolver
      val channel = channel(context) ?: return Log.w(TAG, "slides: no channel").let { }
      resolver.delete(TvContract.buildPreviewProgramsUriForChannel(channel), null, null)
      var written = 0
      posts.take(8).forEachIndexed { i, post ->
        val picture = pictures[i] ?: return@forEachIndexed
        val open = Intent(context, MainActivity::class.java).putExtra(WatchNext.OPEN, post.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, ContentValues().apply {
          put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channel)
          put(TvContract.PreviewPrograms.COLUMN_TYPE, if (post.isSeries) TvContract.PreviewPrograms.TYPE_TV_SERIES else TvContract.PreviewPrograms.TYPE_MOVIE)
          put(TvContract.PreviewPrograms.COLUMN_TITLE, post.title(lang))
          (post.plotLocal ?: post.plotEnglish)?.let { put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, it.take(200)) }
          put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, picture.toString())
          put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_16_9)
          put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, open.toUri(Intent.URI_INTENT_SCHEME))
          put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, post.id.toString())
          put(TvContract.PreviewPrograms.COLUMN_WEIGHT, 100 - i)
          put(TvContract.PreviewPrograms.COLUMN_SEARCHABLE, 1)
        })?.let { written++ }
      }
      Log.i(TAG, "slides: $written of ${posts.size} in channel $channel")
    }.onFailure { Log.w(TAG, "slides: $it") }
  }

  /** The channel's id, made the first time and kept. */
  private fun channel(context: Context): Long? {
    val prefs = context.getSharedPreferences("tvhome", Context.MODE_PRIVATE)
    val known = prefs.getLong(CHANNEL, -1L)
    val resolver = context.contentResolver
    if (known >= 0) {
      val there = resolver.query(TvContract.buildChannelUri(known), arrayOf(TvContract.Channels._ID), null, null, null)?.use { it.moveToFirst() } == true
      if (there) return known
    }
    val uri = resolver.insert(TvContract.Channels.CONTENT_URI, ContentValues().apply {
      put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
      put(TvContract.Channels.COLUMN_DISPLAY_NAME, "30nama")
      put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME))
      put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, CHANNEL)
    }) ?: return null
    val id = uri.lastPathSegment?.toLongOrNull() ?: return null
    prefs.edit().putLong(CHANNEL, id).apply()
    return id
  }
}
