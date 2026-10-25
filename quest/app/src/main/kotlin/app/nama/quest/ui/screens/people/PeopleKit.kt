package app.nama.quest.ui.screens.people

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.nama.core.Post
import app.nama.core.S
import app.nama.quest.AppModel
import app.nama.quest.Services
import app.nama.quest.ui.Glyph
import app.nama.quest.ui.Icon
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Pill
import app.nama.quest.ui.PostCard
import app.nama.quest.ui.PosterWidth
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fadeBottom
import app.nama.quest.ui.pointable
import app.nama.quest.ui.rememberPointed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Parts shared by the profile, person, schedule, top-list and news pages: a shelf with paging
 * arrows, a paged poster grid with room for a header and a footer, the website's Persian held right
 * to left, and a way out to the browser.
 */

/** The wall's side gutter, the same as the home rows and the page headers. */
val Gutter = 44.dp

/** runCatching that lets cancellation through, so a page closed mid-request isn't read as a failure. */
suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

/** Whether a failure of the website's API is the site being out of reach (filtered in Iran) or absent from this build. */
fun siteOut(services: Services): Boolean = !services.api.hasSite || !services.api.siteInReach

/** The website writes in Persian whatever the interface's language, so its text reads right to left (as on the Mac). */
@Composable
fun SiteRtl(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
}

@Composable
fun Ltr(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/** Opens a page in the headset's browser, which comes up as its own window beside the app. */
fun openInBrowser(model: AppModel, services: Services, url: String) {
  try {
    services.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  } catch (_: ActivityNotFoundException) {
    model.showToast(model.loc(S.error))
  } catch (_: SecurityException) {
    model.showToast(model.loc(S.error))
  }
}

/**
 * A titled row of posters. Dragging a row with a ray is tiring, so round arrows sit on its two
 * ends and page it by five; each shows only while there is something on its side.
 */
@Composable
fun Shelf(
    model: AppModel,
    title: String,
    posts: List<Post>,
    note: (Post) -> String? = { null },
    badge: (Post) -> String? = { null },
) {
  val state = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val canBack by remember { derivedStateOf { state.canScrollBackward } }
  val canForward by remember { derivedStateOf { state.canScrollForward } }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(title, style = WallType.heading, modifier = Modifier.padding(horizontal = Gutter))
    Box {
      LazyRow(state = state, contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        itemsIndexed(posts, key = { i, p -> "${p.id}-$i" }) { _, post -> PostCard(model, post, badge = badge(post), note = note(post)) }
      }
      // Centred on the posters, which are 1.5 times as tall as they are wide.
      val arrowTop = PosterWidth * 0.75f - 30.dp
      ShelfArrow(next = false, visible = canBack, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = arrowTop)) {
        scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 5).coerceAtLeast(0)) }
      }
      ShelfArrow(next = true, visible = canForward, Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = arrowTop)) {
        scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex + 5).coerceAtMost(posts.lastIndex.coerceAtLeast(0))) }
      }
    }
  }
}

@Composable
private fun ShelfArrow(next: Boolean, visible: Boolean, modifier: Modifier, onClick: () -> Unit) {
  if (!visible) return
  val p = rememberPointed()
  Box(
      modifier
          .size(60.dp)
          .pointable(p, onClick, 1.1f)
          .clip(CircleShape)
          // Dark enough to read over any poster; the room behind a panel can be bright.
          .background(if (p.hovered) Color(0xF2202430) else Color(0xD90E1016))
          .border(1.dp, if (p.hovered) Ink.text1.copy(alpha = 0.7f) else Ink.hairline, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    // Icon.Back points toward the reading start in either direction; mirrored, it points onward.
    Glyph(Icon.Back, Modifier.size(22.dp).graphicsLayer { if (next) scaleX = -1f }, Ink.text1)
  }
}

/**
 * A poster grid that asks for the next page as its last rows come into view, with whole-width
 * lines above it (the page's header) and under it (loading, retry, "no matches").
 */
@Composable
fun PosterGrid(
    model: AppModel,
    posts: List<Post>,
    canLoadMore: Boolean = false,
    loading: Boolean = false,
    onLoadMore: () -> Unit = {},
    badge: (Int, Post) -> String? = { _, _ -> null },
    header: LazyGridScope.() -> Unit = {},
    footer: LazyGridScope.() -> Unit = {},
) {
  val state = rememberLazyGridState()
  val nearEnd by remember {
    derivedStateOf {
      val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= state.layoutInfo.totalItemsCount - 21
    }
  }
  LaunchedEffect(nearEnd, posts.size, canLoadMore, loading) { if (nearEnd && canLoadMore && !loading && posts.isNotEmpty()) onLoadMore() }
  LazyVerticalGrid(
      GridCells.Adaptive(PosterWidth + 8.dp),
      Modifier.fillMaxSize().fadeBottom(),
      state = state,
      contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = 90.dp),
      horizontalArrangement = Arrangement.spacedBy(18.dp),
      verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    header()
    itemsIndexed(posts, key = { i, p -> "${p.id}-$i" }) { i, post -> PostCard(model, post, badge = badge(i, post)) }
    footer()
  }
}

/** A quiet line in the middle of the page: "Loading…", "No matches". */
@Composable
fun PageNote(text: String, modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
    Text(text, style = WallType.body.copy(color = Ink.text2), textAlign = TextAlign.Center)
  }
}

/**
 * The Mac's SiteRetry inside a page: the website's own note when it is the site that can't be
 * reached, and Retry, which first lets the client ask the site again.
 */
@Composable
fun PageRetry(model: AppModel, services: Services, onRetry: () -> Unit) {
  val loc = model.loc
  val out = siteOut(services)
  Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(loc(if (out) S.siteOutOfReach else S.error), style = WallType.body.copy(color = Ink.text1), textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Pill(loc(S.retry), {
      services.api.siteAgain()
      onRetry()
    }, primary = true)
  }
}

/** A thin rule, for between the rows of a list. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().height(1.dp).background(Ink.hairline))
}
