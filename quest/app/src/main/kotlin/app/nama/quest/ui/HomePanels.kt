package app.nama.quest.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Lang
import app.nama.core.S
import app.nama.quest.AppModel
import app.nama.quest.Place
import app.nama.quest.R
import app.nama.quest.data.Row as CatalogRow
import app.nama.quest.data.Tile
import kotlinx.coroutines.delay

/** Sign in with a code entered on another device; a phone can't scan what's inside the headset. */
@Composable
fun LoginPanel(model: AppModel) {
  val lang by model.lang.collectAsState()
  val code by model.login.collectAsState()
  val loc = model.loc
  Directional(lang.isRTL) {
    Column(
        Modifier.fillMaxSize().glass(RoundedCornerShape(36.dp)).padding(horizontal = 44.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Image(painterResource(R.drawable.nama_logo), null, Modifier.size(44.dp))
        Text(loc(S.signIn), style = Type.title, modifier = Modifier.weight(1f))
        LanguageChips(model, lang)
      }
      Text(loc(S.signInHint), style = Type.body)
      Step(1, "${X.openOnOtherDevice(lang)} ", "30nama.com/auth", loc)
      Step(2, X.enterThisCode(lang), null, loc)
      // The code itself: large, spaced, always left to right so it reads the way it is typed.
      Box(
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(22.dp))
              .background(Ink.accent.copy(alpha = 0.10f))
              .border(1.5.dp, Ink.accent.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
              .padding(vertical = 18.dp),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            code?.code?.let(::groupCode) ?: "····  ····",
            style = Type.display.copy(color = Ink.accent, fontSize = 64.sp, letterSpacing = 6.sp, textDirection = TextDirection.Ltr),
        )
      }
      Step(3, loc(S.step3), null, loc)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pulse()
        Text(X.waitingForConfirm(lang) + "  " + X.codeRefreshes(lang), style = Type.caption, modifier = Modifier.weight(1f))
        Pill(loc(S.guest), { model.goHome() })
      }
    }
  }
}

/** Splits a code into even groups so it is easy to read out and type: 8 → 4+4, 6 → 3+3. */
private fun groupCode(code: String): String {
  val size = if (code.length % 4 == 0) 4 else 3
  return code.chunked(size).joinToString("  ")
}

@Composable
private fun Step(n: Int, text: String, link: String?, loc: app.nama.core.Loc) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Ink.surface2), contentAlignment = Alignment.Center) {
      Text(loc.num(n), style = Type.label)
    }
    Text(text, style = Type.heading.copy(fontSize = 20.sp))
    if (link != null) Text(link, style = Type.heading.copy(fontSize = 20.sp, color = Ink.accent, textDirection = TextDirection.Ltr))
  }
}

@Composable
private fun Pulse() {
  val t = rememberInfiniteTransition(label = "pulse")
  val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
  Box(Modifier.size(10.dp).alpha(a).clip(CircleShape).background(Ink.good))
}

@Composable
private fun driftZoom(): Float {
  val drift = rememberInfiniteTransition(label = "drift")
  val zoom by drift.animateFloat(1.0f, 1.08f, infiniteRepeatable(tween(24_000), RepeatMode.Reverse), label = "zoom")
  return zoom
}

@Composable
fun LanguageChips(model: AppModel, current: Lang) {
  Row(
      Modifier.clip(RoundedCornerShape(50)).background(Color(0x14F4F1EA)).padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    listOf(Lang.en to "EN", Lang.fa to "فا", Lang.ar to "ع").forEach { (l, label) ->
      val p = rememberPointed()
      Box(
          Modifier.pointable(p, { model.setLang(l) }, 1.05f)
              .clip(RoundedCornerShape(50))
              .background(if (l == current) Ink.text1 else if (p.hovered) Color(0x26F4F1EA) else Color.Transparent)
              .padding(horizontal = 14.dp, vertical = 6.dp),
      ) {
        Text(label, style = Type.label.copy(color = if (l == current) Ink.focusInk else Ink.text2))
      }
    }
  }
}

/** The poster wall: rows of the home page on a curved panel around the viewer. */
@Composable
fun HomeWall(model: AppModel) {
  val lang by model.lang.collectAsState()
  val rows by model.rows.collectAsState()
  val failed by model.rowsFailed.collectAsState()
  val loc = model.loc
  Directional(lang.isRTL) {
    Column(Modifier.fillMaxSize()) {
      Row(
          Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Image(painterResource(R.drawable.nama_logo), null, Modifier.size(40.dp))
        Text("30nama", style = Type.title.copy(fontSize = 28.sp, textDirection = TextDirection.Ltr))
        if (model.catalog.isDemo) {
          Text(
              X.demoBadge(lang),
              style = Type.caption.copy(color = Ink.accent),
              modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Ink.accent.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
          )
        }
        Spacer(Modifier.weight(1f))
        Text(X.pointToBrowse(lang), style = Type.caption)
        LanguageChips(model, lang)
      }
      when {
        rows.isEmpty() && failed ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
              Text(X.nothingLoaded(lang), style = Type.heading)
              Spacer(Modifier.height(16.dp))
              Pill(loc(S.retry), { model.loadHome() }, primary = true)
            }
        rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(loc(S.loading), style = Type.body) }
        else ->
            LazyColumn(
                // Fade the bottom edge so a cut-off row reads as "more below", not as a clipping bug.
                Modifier.fillMaxSize().fadeBottom(),
                contentPadding = PaddingValues(bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
              items(rows, key = { it.key }) { row -> WallRow(model, row) }
            }
      }
    }
  }
}

private fun Modifier.fadeBottom() =
    this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
          drawContent()
          drawRect(
              Brush.verticalGradient(0.82f to Color.Black, 1f to Color.Transparent),
              blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
          )
        }

@Composable
private fun WallRow(model: AppModel, row: CatalogRow) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(row.title, style = Type.heading.copy(fontSize = 26.sp), modifier = Modifier.padding(horizontal = 44.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      items(row.tiles, key = { row.key + it.id }) { tile ->
        if (row.landscape) LandscapeCard(model, tile) else PosterCard(model, tile)
      }
    }
  }
}

@Composable
private fun PosterCard(model: AppModel, tile: Tile) {
  val p = rememberPointed()
  HoverFocus(model, tile, p.hovered)
  Column(Modifier.width(176.dp).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(
        Modifier.size(176.dp, 264.dp)
            .pointable(p, { model.open(tile) }, 1.07f)
            .clip(RoundedCornerShape(18.dp))
            .border(if (p.hovered) 2.dp else 1.dp, if (p.hovered) Ink.text1.copy(alpha = 0.9f) else Ink.hairline, RoundedCornerShape(18.dp)),
    ) {
      Artwork(tile, landscape = false, modifier = Modifier.fillMaxSize(), decodeWidth = 300)
      tile.rating?.let { RatingChip(model.loc.num(it), Modifier.align(Alignment.TopStart).padding(10.dp)) }
    }
  }
}

@Composable
private fun LandscapeCard(model: AppModel, tile: Tile) {
  val p = rememberPointed()
  HoverFocus(model, tile, p.hovered)
  Column(Modifier.width(300.dp).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(
        Modifier.size(300.dp, 169.dp)
            .pointable(p, { model.open(tile) }, 1.06f)
            .clip(RoundedCornerShape(18.dp))
            .border(if (p.hovered) 2.dp else 1.dp, if (p.hovered) Ink.text1.copy(alpha = 0.9f) else Ink.hairline, RoundedCornerShape(18.dp)),
    ) {
      Artwork(tile, landscape = true, modifier = Modifier.fillMaxSize(), decodeWidth = 520)
      Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000))))
      Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(tile.title, style = Type.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
        tile.note?.let { Text(it, style = Type.caption.copy(color = Ink.text2), maxLines = 1) }
      }
      tile.progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
    }
  }
}

/** Resting on a tile for a moment shows it on the big screen; a pass over it doesn't. */
@Composable
private fun HoverFocus(model: AppModel, tile: Tile, hovered: Boolean) {
  LaunchedEffect(hovered) {
    if (hovered) {
      delay(160)
      model.focus(tile)
    }
  }
}

/**
 * The big screen when nothing is playing: the focused title's picture, slowly drifting, with its
 * name in the corner on the home page. On the title page it stays clean, because the logo floats in
 * front of it.
 */
@Composable
fun ScreenArt(model: AppModel) {
  val lang by model.lang.collectAsState()
  val place by model.place.collectAsState()
  val focused by model.focused.collectAsState()
  // The slow drift redraws the whole screen texture every frame; only while the picture is seen.
  val zoom = if (place == Place.Home || place == Place.Title) driftZoom() else 1f
  Directional(lang.isRTL) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
      AnimatedContent(focused to place, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) }, label = "art") { (tile, where) ->
        Box(Modifier.fillMaxSize()) {
          if (tile != null && where != Place.Login && where != Place.Starting) {
            Artwork(tile, landscape = true, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }, decodeWidth = 1280)
            if (where == Place.Home) {
              Box(Modifier.matchParentSize().background(Brush.horizontalGradient(if (lang.isRTL) listOf(Color.Transparent, Color(0xD0000000)) else listOf(Color(0xD0000000), Color.Transparent))))
              Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 70.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tile.title, style = Type.display.copy(fontSize = 64.sp), maxLines = 2)
                tile.originalTitle?.takeIf { it != tile.title }?.let { Text(it.uppercase(), style = Type.label.copy(color = Ink.text2, letterSpacing = 4.sp, textDirection = TextDirection.Ltr)) }
                Text(
                    listOfNotNull(tile.year?.let(model.loc::num), tile.genres.take(2).joinToString(" · ").ifEmpty { null }, tile.rating?.let { "★ " + model.loc.num(it) }).joinToString("  ·  "),
                    style = Type.heading.copy(color = Ink.text2, fontSize = 22.sp),
                )
              }
            } else {
              // Title page: darken the lower half so the panels in front stay readable.
              Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xB0000000))))
            }
          } else {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A0B0E), Color.Black))), contentAlignment = Alignment.Center) {
              Image(painterResource(R.drawable.nama_logo), null, Modifier.size(180.dp).alpha(0.5f))
            }
          }
        }
      }
    }
  }
}
