package app.nama.androidtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import app.nama.androidtv.Updater
import app.nama.core.S

/** The row's label and value in Settings › About, for each step of an update. */
@Composable
fun updateRow(state: Updater.State, current: String): Pair<String, String> {
  val loc = LocalLoc.current
  return when (state) {
    // The version is the row above; nothing to add until there is news.
    Updater.State.Idle -> loc(S.checkForUpdate) to ""
    Updater.State.Checking -> loc(S.checkForUpdate) to "…"
    Updater.State.UpToDate -> loc(S.checkForUpdate) to loc(S.upToDate)
    is Updater.State.Available -> "${loc(S.newVersion)} ${state.release.version}" to loc(S.installUpdate)
    is Updater.State.Downloading -> loc(S.updating) to if (state.progress < 0) "…" else "${(state.progress * 100).toInt()}%"
    is Updater.State.NeedsPermission -> tv(TV.allowInstalls) to state.release.version
    is Updater.State.Installing -> tv(TV.installing) to state.release.version
    is Updater.State.Failed -> loc(S.updateFailed) to loc(S.downloadPage)
  }
}

/**
 * When updating from inside the app went wrong: the download page as a QR code for a phone, since a
 * TV seldom has a browser, and a word on where the cause was written.
 */
@Composable
fun UpdateFailedCard(page: String, retry: () -> Unit, close: () -> Unit) {
  val loc = LocalLoc.current
  val qr = remember(page) { qrImage(page) }
  val shape = RoundedCornerShape(36.dp)
  val first = remember { FocusRequester() }
  BackHandler(onBack = close)
  LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
    Row(Modifier.width(1300.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(56.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(loc(S.updateFailed), style = app(40, FontWeight.Bold), color = Theme.text1)
        Text(tv(TV.updateFailedHint), style = app(26).copy(lineHeight = app(26).fontSize * 1.5f), color = Theme.text2)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          PillButton(tv(TV.retry), retry, Modifier.focusRequester(first), kind = PillKind.Primary)
          PillButton(loc(S.later), close)
        }
      }
      Column(Modifier.width(380.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(340.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(24.dp)) {
          Image(qr, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        }
        Text(loc(S.downloadPage), style = app(22), color = Theme.text2, textAlign = TextAlign.Center)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(page, style = app(18), color = Theme.text3, maxLines = 2) }
      }
    }
  }
}

/**
 * Where the Mac and Windows open a page in the browser (renewing, a report, IMDb), the TV shows
 * its QR code to open on the phone, with the address under it.
 */
@Composable
fun LinkCard(spec: LinkSpec, close: () -> Unit) {
  val loc = LocalLoc.current
  val qr = remember(spec.url) { qrImage(spec.url) }
  val shape = RoundedCornerShape(36.dp)
  val first = remember { FocusRequester() }
  BackHandler(onBack = close)
  LaunchedEffect(spec) { runCatching { first.requestFocus() } }
  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
    Row(Modifier.width(1300.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(56.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(spec.title, style = app(40, FontWeight.Bold), color = Theme.text1)
        Text(spec.body, style = app(26).copy(lineHeight = app(26).fontSize * 1.5f), color = Theme.text2)
        Row(Modifier.padding(top = 12.dp)) { PillButton(loc(S.close), close, Modifier.focusRequester(first), kind = PillKind.Primary) }
      }
      Column(Modifier.width(380.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(340.dp).background(Color.White, RoundedCornerShape(24.dp)).padding(24.dp)) {
          Image(qr, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Text(spec.url.substringBefore('?'), style = app(18), color = Theme.text3, maxLines = 2)
        }
      }
    }
  }
}

