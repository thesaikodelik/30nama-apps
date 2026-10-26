package app.nama.androidtv.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.nama.androidtv.AppModel
import app.nama.androidtv.attempt
import app.nama.core.LoginCode
import app.nama.core.Post
import app.nama.core.S
import app.nama.core.qrLogin
import app.nama.core.qrLoginCode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Sign-in by scanning a QR code with a phone that is logged in to 30nama, as on the Apple TV.
 *
 * The rhythm is 30namaTV's, which spares a TV left on the screen: the server is asked every five
 * seconds for two minutes, then every 25; a code that runs out is replaced before it is shown dead;
 * the previous code is still asked about for one more lifetime, since a phone may approve a code it
 * scanned late; and after ten idle minutes the screen rests until a key is pressed.
 */
@Composable
fun LoginScreen() {
  val app = LocalApp.current
  val loc = LocalLoc.current
  var code by remember { mutableStateOf<LoginCode?>(null) }
  var qr by remember { mutableStateOf<ImageBitmap?>(null) }
  var status by remember { mutableStateOf(Status.Waiting) }
  /** Bumped to start again: a fresh code, a fresh two minutes of quick asking. */
  var round by remember { mutableIntStateOf(0) }
  var backdrop by remember { mutableStateOf<Post?>(null) }
  val guest = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    // A different one of the best-regarded titles each time the screen comes up.
    backdrop = attempt { app.home() }?.backdrops()?.randomOrNull()
    runCatching { guest.requestFocus() }
  }
  LaunchedEffect(round) {
    status = Status.Waiting
    qr = null
    code = null
    val started = System.currentTimeMillis()
    var previous: LoginCode? = null
    var previousUntil = 0L
    while (isActive) {
      val fresh = attempt { app.api.qrLoginCode() }
      if (fresh == null) {
        status = Status.Failed
        return@LaunchedEffect
      }
      code = fresh
      qr = qrImage(fresh.url)
      val until = System.currentTimeMillis() + LIFETIME_MS
      while (isActive && System.currentTimeMillis() < until) {
        val elapsed = System.currentTimeMillis() - started
        if (elapsed > REST_AFTER_MS) {
          status = Status.Resting
          return@LaunchedEffect
        }
        delay(if (elapsed < QUICK_FOR_MS) QUICK_MS else SLOW_MS)
        // Unused codes answer `success: false`; the token comes once the phone has approved.
        val token = app.api.qrLogin(fresh.code) ?: previous?.takeIf { System.currentTimeMillis() < previousUntil }?.let { app.api.qrLogin(it.code) }
        if (token != null) {
          status = Status.Done
          delay(500)
          app.signIn(token)
          return@LaunchedEffect
        }
      }
      previous = fresh
      previousUntil = System.currentTimeMillis() + LIFETIME_MS
    }
  }

  Box(
      Modifier.fillMaxSize().onPreviewKeyEvent {
        // Any key wakes a resting screen with a fresh code.
        if (status == Status.Resting) round++
        false
      }) {
    Stage(backdrop, dim = 0.55f)
    Row(Modifier.fillMaxSize().padding(horizontal = 110.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.widthIn(max = 900.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Wordmark(44)
        Text(loc(S.scanTitle), Modifier.padding(top = 8.dp), style = app(60, FontWeight.Bold), color = Theme.text1)
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
          listOf(S.step1, S.step2, S.step3).forEachIndexed { i, key ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(44.dp).background(Theme.accent, CircleShape), contentAlignment = Alignment.Center) {
                Text(loc.num(i + 1), style = app(24, FontWeight.Bold), color = Color.White)
              }
              Text(loc(key), style = app(28), color = Theme.text2)
            }
          }
        }
        code?.let { c ->
          Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(loc(S.orEnterCode), style = app(24), color = Theme.text3)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
              Text(c.code, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp), color = Theme.text1)
            }
          }
        }
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          PillButton(loc(S.guest), { app.continueAsGuest() }, Modifier.focusRequester(guest), kind = PillKind.Primary)
          PillButton(loc(S.newCode), { round++ })
        }
      }
      Spacer(Modifier.size(60.dp))
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Box(Modifier.size(460.dp).shadow(40.dp, RoundedCornerShape(28.dp)).background(Color.White, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
          qr?.let {
            Image(
                it, null, Modifier.padding(30.dp).fillMaxSize().alpha(if (status == Status.Waiting) 1f else 0.12f),
                contentScale = ContentScale.Fit, filterQuality = FilterQuality.None,
            )
          }
          AnimatedContent(status, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "status") { s ->
            when (s) {
              Status.Waiting -> if (qr == null) Spinner(Modifier.size(56.dp), Color.Black)
              Status.Failed, Status.Resting -> Text(loc(if (s == Status.Resting) S.expired else S.codeFailed), style = app(30, FontWeight.Bold), color = Theme.focusInk)
              Status.Done -> Icon(Icons.check, null, Modifier.size(150.dp).background(Theme.accent, CircleShape).padding(24.dp), tint = Color.White)
            }
          }
        }
        Row(Modifier.alpha(if (status == Status.Waiting && qr != null) 1f else 0f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          PulsingDot()
          Text(loc(S.waiting), style = app(24, FontWeight.Medium), color = Theme.text2)
        }
      }
    }
  }
}

private enum class Status { Waiting, Failed, Resting, Done }

private const val LIFETIME_MS = 60_000L
private const val QUICK_MS = 5_000L
private const val SLOW_MS = 25_000L
private const val QUICK_FOR_MS = 120_000L
private const val REST_AFTER_MS = 600_000L

@Composable
private fun PulsingDot() {
  val on = if (LocalLively.current) {
    rememberInfiniteTransition(label = "dot").animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "on").value
  } else 1f
  Box(Modifier.size(12.dp).alpha(on).background(Theme.accent, CircleShape))
}

/** The QR code of [text], one pixel a module; drawn without smoothing it stays sharp at any size. */
fun qrImage(text: String): ImageBitmap {
  val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
  val w = matrix.width
  val h = matrix.height
  val pixels = IntArray(w * h) { i -> if (matrix[i % w, i / w]) 0xFF0B0C10.toInt() else 0xFFFFFFFF.toInt() }
  return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
