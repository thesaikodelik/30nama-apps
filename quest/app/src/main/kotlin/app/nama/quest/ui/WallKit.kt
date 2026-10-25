@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nama.quest.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nama.core.BlurHash
import app.nama.core.Fmt
import app.nama.core.Post
import app.nama.core.S
import app.nama.quest.AppModel

/*
 * Building blocks every wall screen uses, matching the Mac's (MacKit.swift): the poster card, a
 * paged poster grid, pill choices, a filter menu and the error state. Sizes are for the wall,
 * which sits about two metres away.
 */

/** The width of a poster card on the wall; the grid fits as many as the wall's width allows. */
val PosterWidth: Dp = 176.dp

/**
 * A poster for a [Post]: lifts and gains a white rim when pointed at, shows its name and year
 * along the bottom while pointed at (a poster doesn't always spell its title), a "Soon" badge for
 * coming-soon titles, or a [badge] of the caller's (a rank, your stars) in the top corner.
 */
@Composable
fun PostCard(
    model: AppModel,
    post: Post,
    modifier: Modifier = Modifier,
    width: Dp = PosterWidth,
    badge: String? = null,
    note: String? = null,
    onOpen: () -> Unit = { model.openPost(post) },
) {
  val p = rememberPointed()
  val loc = model.loc
  val lang = model.lang.value
  Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(
        Modifier.fillMaxWidth()
            .height(width * 1.5f)
            .pointable(p, onOpen, 1.07f)
            .clip(RoundedCornerShape(16.dp))
            .border(if (p.hovered) 2.dp else 1.dp, if (p.hovered) Ink.text1.copy(alpha = 0.9f) else Ink.hairline, RoundedCornerShape(16.dp)),
    ) {
      PosterImage(post, Modifier.fillMaxSize(), decodeWidth = 300)
      val corner = badge ?: if (post.comingSoon) loc(S.soon) else post.imdbScore?.let { "★ " + loc.num(Fmt.score(it)) }
      corner?.let {
        Text(
            it,
            style = WallType.caption.copy(color = if (badge == null && !post.comingSoon) Ink.star else Ink.text1),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0xB307080B)).padding(horizontal = 10.dp, vertical = 3.dp),
        )
      }
      if (p.hovered) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xE6000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
          Text(post.title(lang), style = WallType.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
          post.year?.let { Text(loc.num(it), style = WallType.caption.copy(color = Ink.text2)) }
        }
      }
      note?.let {
        Text(
            it,
            style = WallType.caption.copy(color = Ink.text1),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC07080B)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }
  }
}

/** A post's poster, with its blurhash standing in until the picture arrives. */
@Composable
fun PosterImage(post: Post, modifier: Modifier = Modifier, decodeWidth: Int = 300) {
  val image = rememberImage(post.poster.url(decodeWidth.toDouble())?.toString(), decodeWidth)
  val placeholder = remember(post.poster.blurhash) { post.poster.blurhash?.let(::blurBitmap) }
  Box(modifier.background(Ink.surface)) {
    val shown = image ?: placeholder
    if (shown != null) Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
  }
}

private fun blurBitmap(hash: String): ImageBitmap? {
  val w = 12
  val h = 18
  val rgba = BlurHash.pixels(hash, w, h) ?: return null
  val px = IntArray(w * h) { i ->
    val o = i * 4
    (0xFF shl 24) or ((rgba[o].toInt() and 0xFF) shl 16) or ((rgba[o + 1].toInt() and 0xFF) shl 8) or (rgba[o + 2].toInt() and 0xFF)
  }
  return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * A grid of posters that asks for the next page as the last ones come into view, as the Mac's
 * catalogue does (about three rows ahead). [header] scrolls with the grid.
 */
@Composable
fun PostGrid(
    model: AppModel,
    posts: List<Post>,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (Int, Post) -> String? = { _, _ -> null },
    header: (LazyGridScope.() -> Unit)? = null,
) {
  val state = rememberLazyGridState()
  val nearEnd by remember {
    derivedStateOf {
      val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= state.layoutInfo.totalItemsCount - 21
    }
  }
  LaunchedEffect(nearEnd, posts.size, canLoadMore) { if (nearEnd && canLoadMore && !loadingMore) onLoadMore() }
  LazyVerticalGrid(
      GridCells.Adaptive(PosterWidth + 8.dp),
      modifier.fillMaxSize().fadeBottom(),
      state = state,
      contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 80.dp),
      horizontalArrangement = Arrangement.spacedBy(18.dp),
      verticalArrangement = Arrangement.spacedBy(22.dp),
  ) {
    header?.invoke(this)
    itemsIndexed(posts, key = { i, p -> "${p.id}-$i" }) { i, post -> PostCard(model, post, badge = badge(i, post)) }
    if (loadingMore) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Text(model.loc(S.loading), style = WallType.caption) }
      }
    }
  }
}

/** A full-width line in a grid, for headers and notes. */
fun LazyGridScope.fullLine(key: String, content: @Composable () -> Unit) {
  item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** The Mac's PillChoice: a capsule that is white when selected. */
@Composable
fun PillChoice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val p = rememberPointed()
  Text(
      text,
      style = WallType.label.copy(color = if (selected) Ink.focusInk else Ink.text1.copy(alpha = if (p.hovered) 1f else 0.8f)),
      maxLines = 1,
      modifier =
          modifier
              .pointable(p, onClick, 1.04f)
              .clip(RoundedCornerShape(50))
              .background(if (selected) Ink.text1 else if (p.hovered) Color(0x33F4F1EA) else Color(0x1AF4F1EA))
              .padding(horizontal = 20.dp, vertical = 9.dp),
  )
}

/** A row of pill choices, wrapping onto more lines when it doesn't fit. */
@Composable
fun <T> PillRow(choices: List<T>, selected: T?, label: (T) -> String, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
  androidx.compose.foundation.layout.FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    choices.forEach { c -> PillChoice(label(c), c == selected, { onPick(c) }) }
  }
}

/**
 * The Mac's filter capsule ("Genre: Drama ▾"). A panel can't open a popup menu, so the choices
 * fold out in place under it, as pills, and fold back once one is picked.
 */
@Composable
fun <T> FilterMenu(
    title: String,
    choices: List<T>,
    selected: T?,
    label: (T) -> String,
    allLabel: String,
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    onPick: (T?) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    PillChoice("$title: ${selected?.let(label) ?: allLabel}  ▾", selected != null || open, { onOpen(!open) })
    if (open) {
      androidx.compose.foundation.layout.FlowRow(
          Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x14F4F1EA)).padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PillChoice(allLabel, selected == null, {
          onPick(null)
          onOpen(false)
        })
        choices.forEach { c ->
          PillChoice(label(c), c == selected, {
            onPick(c)
            onOpen(false)
          })
        }
      }
    }
  }
}

/** A page header: a big title, a quiet line beside it, and whatever controls the page has. */
@Composable
fun WallHeader(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
  Row(
      Modifier.fillMaxWidth().padding(start = 44.dp, end = 44.dp, top = 6.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(title, style = WallType.title)
    subtitle?.let { Text(it, style = WallType.caption) }
    Spacer(Modifier.weight(1f))
    trailing()
  }
}

/**
 * Something went wrong: the Mac's ErrorState, with the website's own note when it's the site that
 * can't be reached (in Iran without a VPN), and Retry.
 */
@Composable
fun WallError(model: AppModel, siteOutOfReach: Boolean = false, onRetry: () -> Unit) {
  val loc = model.loc
  Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text(loc(if (siteOutOfReach) S.siteOutOfReach else S.error), style = WallType.heading)
    Spacer(Modifier.height(18.dp))
    Pill(loc(S.retry), {
      if (siteOutOfReach) model.services?.api?.siteAgain()
      onRetry()
    }, primary = true)
  }
}

@Composable
fun WallLoading(model: AppModel) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(model.loc(S.loading), style = WallType.body) }
}

