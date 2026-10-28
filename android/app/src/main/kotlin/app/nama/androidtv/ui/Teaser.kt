package app.nama.androidtv.ui

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/**
 * The Mac's teaser: a stretch of the film itself, without sound, behind its title page, in its
 * smallest picture, from a third of the way in. After three seconds on the page with nothing else
 * going on, for thirty-five seconds, once per visit. Only where the TV can afford a second
 * decoder and motion is welcome (`lively`); elsewhere the still stays. It tells 30nama nothing, so
 * it counts neither as a request nor as watching. The Mac plays the trailer first where TMDB has
 * one; a YouTube trailer needs a web view, too heavy behind a page on a TV, so here it is the film.
 *
 * @param active the page is what the viewer is looking at: no player, trailer or page over it.
 */
@OptIn(UnstableApi::class)
@Composable
fun Teaser(url: URI?, active: Boolean) {
  val app = LocalApp.current
  val context = LocalContext.current
  val allowed = LocalLively.current && !LocalCalm.current
  var played by remember(url) { mutableStateOf(false) }
  var running by remember { mutableStateOf<ExoPlayer?>(null) }
  var showing by remember { mutableStateOf(false) }

  LaunchedEffect(url, active && allowed) {
    if (url == null || !active || !allowed || played) {
      showing = false
      delay(1_400)
      running?.release()
      running = null
      return@LaunchedEffect
    }
    delay(3_000)
    played = true
    val source = OkHttpDataSource.Factory(app.http)
    val player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build()
    player.volume = 0f
    player.repeatMode = Player.REPEAT_MODE_OFF
    var sought = false
    player.addListener(object : Player.Listener {
      override fun onPlaybackStateChanged(state: Int) {
        if (state != Player.STATE_READY || sought) return
        sought = true
        val length = player.duration
        if (length > 120_000) player.seekTo(length / 3)
        player.play()
      }

      override fun onRenderedFirstFrame() {
        if (sought) showing = true
      }

      override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        showing = false
      }
    })
    player.setMediaItem(MediaItem.fromUri(url.toString()))
    player.prepare()
    running = player
    delay(35_000)
    showing = false
    delay(1_400)
    player.release()
    if (running === player) running = null
  }
  DisposableEffect(Unit) { onDispose { running?.release() } }

  val alpha by animateFloatAsState(if (showing) 1f else 0f, tween(1_200), label = "teaser")
  // A texture, not a surface: it sits under the page's shading and fades like any other layer.
  running?.let { PlayerSurface(it, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, SURFACE_TYPE_TEXTURE_VIEW) }
}
