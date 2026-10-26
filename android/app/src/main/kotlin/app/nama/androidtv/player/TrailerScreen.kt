package app.nama.androidtv.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import app.nama.androidtv.ui.LocalApp
import app.nama.androidtv.ui.Spinner
import app.nama.androidtv.ui.Theme
import app.nama.core.S
import java.net.URI

/**
 * An Aparat trailer, full screen. Aparat checks where its video is played from, so its own page
 * is given as the referrer. The trailer ends by going back to the title page.
 */
@OptIn(UnstableApi::class)
@Composable
fun TrailerScreen(url: URI) {
  val app = LocalApp.current
  val context = LocalContext.current
  var buffering by remember { mutableStateOf(true) }
  val player = remember(url) {
    val source = OkHttpDataSource.Factory(app.http).setDefaultRequestProperties(mapOf("Referer" to "https://www.aparat.com/"))
    ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build().apply {
      setMediaItem(MediaItem.fromUri(url.toString()))
      prepare()
      play()
    }
  }
  DisposableEffect(player) {
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(state: Int) {
        buffering = state == Player.STATE_BUFFERING
        if (state == Player.STATE_ENDED) app.closeTrailer()
      }

      override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        app.showToast(app.loc(S.trailerNotLoaded))
        app.closeTrailer()
      }
    }
    player.addListener(listener)
    onDispose { player.release() }
  }
  BackHandler { app.closeTrailer() }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  Box(
      Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key in setOf(Key.DirectionCenter, Key.Enter, Key.MediaPlayPause)) {
          if (player.isPlaying) player.pause() else player.play()
          true
        } else false
      },
      contentAlignment = Alignment.Center,
  ) {
    PlayerSurface(player, Modifier.fillMaxSize(), SURFACE_TYPE_SURFACE_VIEW)
    if (buffering) Spinner(Modifier.align(Alignment.TopStart).padding(Theme.edge).size(40.dp), Theme.accent)
  }
}
