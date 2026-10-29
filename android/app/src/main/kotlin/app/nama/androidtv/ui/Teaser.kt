package app.nama.androidtv.ui

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import java.net.URI
import kotlinx.coroutines.delay

/** What plays behind a title page: a trailer from its start, or the film itself from a third in. */
data class TeaserClip(val url: URI, val trailer: Boolean, val referer: String? = null)

/**
 * The Mac's teaser: something without sound behind a title page, after three seconds on it with
 * nothing else going on, once per visit, in a small picture (it sits under shading). In Iran the
 * trailer from Aparat, for a minute and a half; else, or without one, thirty-five seconds of the
 * film itself from a third of the way in. The Mac plays TMDB's trailer from YouTube outside Iran;
 * YouTube gives no file a player can open, only its own web player, too heavy behind a page on a
 * TV. Only where the TV can afford a second decoder and motion is welcome (`lively`); elsewhere
 * the still stays. It tells 30nama nothing, so it counts neither as a request nor as watching.
 *
 * @param pick finds the clip once the page has rested, or null for none.
 * @param active the page is what the viewer is looking at: no player, trailer or page over it.
 */
@OptIn(UnstableApi::class)
@Composable
fun Teaser(key: Any?, pick: suspend () -> TeaserClip?, active: Boolean) {
  val app = LocalApp.current
  val context = LocalContext.current
  val allowed = LocalLively.current && !LocalCalm.current && !app.settings.dataSaver
  var played by remember(key) { mutableStateOf(false) }
  var running by remember { mutableStateOf<ExoPlayer?>(null) }
  var showing by remember { mutableStateOf(false) }
  /** Width over height of the picture, once known. */
  var aspect by remember { mutableStateOf(16f / 9f) }

  // Until it has started, every key starts the wait again: it plays once the viewer has rested on
  // the page a few seconds, not while moving about it (the owner). The Mac waits three seconds
  // from opening; it has no remote to rest.
  var rested by remember(key) { mutableStateOf(false) }
  LaunchedEffect(key, active && allowed, app.lastKey) {
    if (rested || !active || !allowed) return@LaunchedEffect
    delay(REST_MS)
    rested = true
  }
  LaunchedEffect(key, active && allowed, rested) {
    if (key == null || !active || !allowed || played) {
      // Stopped at once: the player or trailer opening over the page wants the decoder now.
      running?.pause()
      showing = false
      delay(1_400)
      running?.release()
      running = null
      return@LaunchedEffect
    }
    if (!rested) return@LaunchedEffect
    val clip = pick() ?: return@LaunchedEffect
    played = true
    val source = OkHttpDataSource.Factory(app.http).apply { clip.referer?.let { setDefaultRequestProperties(mapOf("Referer" to it)) } }
    val player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build()
    player.volume = 0f
    player.repeatMode = Player.REPEAT_MODE_OFF
    var sought = false
    player.addListener(object : Player.Listener {
      override fun onPlaybackStateChanged(state: Int) {
        // A trailer shorter than its turn: back to the still, not held on its last frame.
        if (state == Player.STATE_ENDED) showing = false
        if (state != Player.STATE_READY || sought) return
        sought = true
        val length = player.duration
        if (!clip.trailer && length > 120_000) player.seekTo(length / 3)
        player.play()
      }

      // Shown once it plays: by then a film has moved to its third, and the picture is the one meant.
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) showing = true
      }

      override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
        if (size.width > 0 && size.height > 0) aspect = size.width * size.pixelWidthHeightRatio / size.height
      }

      override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        showing = false
      }
    })
    // Small is enough under shading, and spares the line: at most 360 lines.
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(640, 360).build()
    player.setMediaItem(MediaItem.fromUri(clip.url.toString()))
    player.prepare()
    running = player
    // A trailer is the length it is; it is let run for a minute and a half.
    delay(if (clip.trailer) 90_000 else 35_000)
    showing = false
    delay(1_400)
    player.release()
    if (running === player) running = null
  }
  DisposableEffect(Unit) { onDispose { running?.release() } }

  val alpha by animateFloatAsState(if (showing) 1f else 0f, tween(1_200), label = "teaser")
  // A texture, not a surface: it sits under the page's shading and fades like any other layer.
  // It fills the screen as the still does, cut at its sides or top and bottom rather than framed in black.
  running?.let { player ->
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = androidx.compose.ui.Alignment.Center) {
      val screen = maxWidth / maxHeight
      val w = if (aspect > screen) maxHeight * aspect else maxWidth
      val h = if (aspect > screen) maxHeight else maxWidth / aspect
      PlayerSurface(player, Modifier.requiredSize(w, h).graphicsLayer { this.alpha = alpha }, SURFACE_TYPE_TEXTURE_VIEW)
    }
  }
}

/** How long the viewer rests on a title's page, no key pressed, before what plays behind it starts. */
private const val REST_MS = 4_000L
