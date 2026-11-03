package app.nama.androidtv.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import app.nama.androidtv.ui.LocalApp
import app.nama.androidtv.ui.LocalLoc
import app.nama.androidtv.ui.Spinner
import app.nama.androidtv.ui.Theme
import app.nama.androidtv.ui.app
import app.nama.core.S
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A YouTube trailer, full screen in the app, in YouTube's own embedded player as on the Mac
 * (TrailerSheet.swift): YouTube gives no file a player of ours can open. The remote stays with the
 * app, not the page: the middle button plays and pauses, left and right go ten seconds, Back
 * closes, and the end of the trailer closes it too. Where YouTube does not answer (it is filtered
 * on some networks), the screen says so rather than staying dark.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeTrailer(id: String) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  var view by remember { mutableStateOf<WebView?>(null) }
  var started by remember { mutableStateOf(false) }
  var unreachable by remember { mutableStateOf(false) }
  BackHandler { app.closeTrailer() }

  // Stops the sound at once: a web view left to go on its own can play a while longer.
  DisposableEffect(Unit) {
    onDispose {
      view?.run {
        loadUrl("about:blank")
        destroy()
      }
    }
  }

  // The page is YouTube's, so its video can be asked how far it has played: told to play if it
  // waits for a press, given its sound once it moves, and closed at its end.
  LaunchedEffect(view) {
    val web = view ?: return@LaunchedEffect
    var waited = 0
    while (true) {
      delay(400)
      waited += 400
      val answer = web.run(STATE)
      val at = answer.substringBefore(',').toDoubleOrNull() ?: 0.0
      if (at > 0.3) started = true
      if (answer.endsWith("ended")) {
        app.closeTrailer()
        return@LaunchedEffect
      }
      if (!started && waited > 15_000) unreachable = true
    }
  }

  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  Box(
      Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val script = when (it.key) {
          Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> TOGGLE
          Key.DirectionRight, Key.MediaFastForward -> seek(10)
          Key.DirectionLeft, Key.MediaRewind -> seek(-10)
          else -> return@onPreviewKeyEvent false
        }
        view?.evaluateJavascript(script, null)
        true
      },
      contentAlignment = Alignment.Center,
  ) {
    AndroidView(
        factory = { context ->
          WebView(context).apply {
            setBackgroundColor(AndroidColor.BLACK)
            // The remote is the app's: a page that took focus would move it among YouTube's buttons.
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Played without a press: the trailer was asked for with the button that opened it.
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            // YouTube wants to know which page its player is on, and is told (Mac's TrailerSheet).
            loadUrl(
                "https://www.youtube-nocookie.com/embed/$id?autoplay=1&rel=0&playsinline=1&iv_load_policy=3&fs=0&disablekb=1",
                mapOf("Referer" to "https://github.com/thesaikodelik/30nama-apps"),
            )
            view = this
          }
        },
        modifier = Modifier.fillMaxSize(),
    )
    if (unreachable) {
      Column(Modifier.background(Color.Black.copy(alpha = 0.8f)).padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(loc(S.trailerUnreachable), style = app(28), color = Theme.text1, textAlign = TextAlign.Center)
      }
    } else if (!started) {
      Spinner(Modifier.align(Alignment.TopStart).padding(Theme.edge).size(40.dp), Theme.accent)
    }
  }
}

/** Seconds played, and "ended" at the end; "0" until the video is there. Plays it if it waits. */
private const val STATE =
    "(function(){var v=document.querySelector('video');if(!v)return '0';var p=document.getElementById('movie_player');" +
        "if(v.paused&&!v.ended&&v.currentTime<0.2&&v.readyState>=2){if(p&&p.playVideo)p.playVideo();else v.play()}" +
        "if(!v.paused&&v.muted){if(p&&p.unMute){p.unMute();p.setVolume(100)}v.muted=false}" +
        "return v.currentTime+(v.ended?',ended':'')})()"

private const val TOGGLE =
    "(function(){var v=document.querySelector('video');var p=document.getElementById('movie_player');if(!v)return;" +
        "if(v.paused){if(p&&p.playVideo)p.playVideo();else v.play()}else{if(p&&p.pauseVideo)p.pauseVideo();else v.pause()}})()"

private fun seek(seconds: Int) =
    "(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+($seconds))})()"

/** A script's answer, unquoted. */
private suspend fun WebView.run(script: String): String = suspendCancellableCoroutine { done ->
  evaluateJavascript(script) { done.resume(it?.trim('"').orEmpty()) }
}
