@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nama.quest.ui.screens.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nama.core.Fmt
import app.nama.core.FilterOptions
import app.nama.core.S
import app.nama.quest.AppModel
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Pill
import app.nama.quest.ui.PillChoice
import app.nama.quest.ui.PostCard
import app.nama.quest.ui.PosterWidth
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fadeBottom
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.pointable
import app.nama.quest.ui.rememberPointed

/** How many posters from the end the next page is asked for: about three rows, as on the Mac. */
private const val PREFETCH = 21

/** "84,827 results" in the interface's digits. */
fun resultsLine(model: AppModel, total: Int?): String? = total?.let { "${Fmt.count(it, model.lang.value)} ${model.loc(S.results)}" }

/**
 * A [Paged] wall: [header] scrolls with the posters (filters folded out can be tall, and the wall
 * is short), then the posters, then a footer that says what is happening at the end: loading, a
 * Retry (with the website's own note when [site] says the posters come from there and it is out of
 * reach), or [empty] when the answer was nothing.
 */
@Composable
fun PagedWall(model: AppModel, paged: Paged, site: Boolean = false, empty: String? = null, header: LazyGridScope.() -> Unit = {}) {
  val state = rememberLazyGridState()
  val nearEnd by remember {
    derivedStateOf {
      val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= state.layoutInfo.totalItemsCount - PREFETCH
    }
  }
  LaunchedEffect(nearEnd, paged.posts.size, paged.page, paged.generation) {
    if (nearEnd && paged.canLoadMore && !paged.loading) paged.loadNext()
  }
  // A new order or filter starts at the top, where the filters are.
  LaunchedEffect(paged.generation) { if (state.firstVisibleItemIndex > 0) state.scrollToItem(0) }
  LazyVerticalGrid(
      GridCells.Adaptive(PosterWidth + 8.dp),
      Modifier.fillMaxSize().fadeBottom(),
      state = state,
      contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 4.dp, bottom = 90.dp),
      horizontalArrangement = Arrangement.spacedBy(18.dp),
      verticalArrangement = Arrangement.spacedBy(22.dp),
  ) {
    header()
    itemsIndexed(paged.posts, key = { _, p -> "post-${p.id}" }) { _, post -> PostCard(model, post) }
    item(key = "footer-${paged.generation}", span = { GridItemSpan(maxLineSpan) }) { WallFooter(model, paged, site, empty) }
  }
}

@Composable
private fun WallFooter(model: AppModel, paged: Paged, site: Boolean, empty: String?) {
  val loc = model.loc
  Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
    when {
      paged.failed -> {
        val api = model.services?.api
        val away = site && api != null && !api.siteInReach
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Text(
              loc(if (away) S.siteOutOfReach else S.error),
              style = WallType.body.copy(color = Ink.text2, textAlign = TextAlign.Center),
              modifier = Modifier.widthIn(max = 640.dp),
          )
          Pill(loc(S.retry), {
            if (site) api?.siteAgain()
            paged.loadNext()
          }, primary = true)
        }
      }
      paged.loading -> Text(loc(S.loading), style = WallType.caption)
      paged.answered && paged.posts.isEmpty() && empty != null -> Text(empty, style = WallType.heading.copy(color = Ink.text2))
      else -> Spacer(Modifier.size(1.dp))
    }
  }
}

/** A page title with its count beside it, and whatever controls the page keeps on the same line. */
fun LazyGridScope.titleLine(title: String, subtitle: String?, hint: String? = null, trailing: @Composable () -> Unit = {}) {
  fullLine("title") {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = WallType.title)
        subtitle?.let { Text(it, style = WallType.caption) }
        Spacer(Modifier.weight(1f))
        trailing()
      }
      hint?.let { Text(it, style = WallType.caption) }
    }
  }
}

/** The sort orders as pills, one chosen. */
@Composable
fun <T> SortPills(options: List<T>, chosen: T, label: (T) -> String, onPick: (T) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    options.forEach { o -> PillChoice(label(o), o == chosen, { onPick(o) }) }
  }
}

/**
 * The Mac's filter capsule, "Country: Iran ▾". White when something is chosen or its choices are
 * folded out. The choices fold out in one place under the whole row ([FoldOut]), so a long list
 * doesn't push the other capsules about.
 */
@Composable
fun FilterCapsule(title: String, value: String?, open: Boolean, onClick: () -> Unit) {
  PillChoice((if (value != null) "$title: $value" else title) + if (open) "  ▴" else "  ▾", value != null || open, onClick)
}

/**
 * Where an open filter's choices fold out: a glass tray across the wall, "All" first. Long lists
 * (countries, years) scroll inside it rather than pushing the posters a screen away.
 */
@Composable
fun FoldOut(
    visible: Boolean,
    allLabel: String,
    choices: List<FilterOptions.Choice>,
    isChosen: (String) -> Boolean,
    noneChosen: Boolean,
    onAll: () -> Unit,
    onPick: (String) -> Unit,
) {
  AnimatedVisibility(
      visible,
      enter = expandVertically(tween(220)) + fadeIn(tween(220)),
      exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
  ) {
    val shape = RoundedCornerShape(26.dp)
    FlowRow(
        Modifier.fillMaxWidth()
            .padding(top = 4.dp)
            .clip(shape)
            .background(Color(0x14F4F1EA))
            .border(1.dp, Ink.hairline, shape)
            .heightIn(max = 330.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      PillChoice(allLabel, noneChosen, onAll)
      choices.forEach { c -> PillChoice(c.name, isChosen(c.value), { onPick(c.value) }) }
    }
  }
}

/** A round "clear" capsule: resets the filters. */
@Composable
fun ResetCapsule(text: String, onClick: () -> Unit) {
  val p = rememberPointed()
  Row(
      Modifier.pointable(p, onClick, 1.04f)
          .clip(RoundedCornerShape(50))
          .background(if (p.hovered) Color(0x33F4F1EA) else Color.Transparent)
          .border(1.dp, Ink.hairline, RoundedCornerShape(50))
          .padding(horizontal = 18.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    app.nama.quest.ui.Glyph(app.nama.quest.ui.Icon.Close, Modifier.size(16.dp), if (p.hovered) Ink.text1 else Ink.text2)
    Text(text, style = WallType.label.copy(color = if (p.hovered) Ink.text1 else Ink.text2))
  }
}

/** Icons only these screens use, drawn like the rest (ui/Parts.kt Glyph). */
enum class Mark { Calendar, Trophy, News, Sliders, Director }

@Composable
fun MarkGlyph(mark: Mark, modifier: Modifier = Modifier, color: Color = Ink.text1) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val line = Stroke(w * 0.09f, cap = StrokeCap.Round)
    when (mark) {
      Mark.Calendar -> {
        drawRoundRect(color, Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.72f), CornerRadius(w * 0.12f), style = line)
        drawLine(color, Offset(w * 0.1f, h * 0.4f), Offset(w * 0.9f, h * 0.4f), w * 0.09f)
        drawLine(color, Offset(w * 0.32f, h * 0.08f), Offset(w * 0.32f, h * 0.26f), w * 0.09f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.08f), Offset(w * 0.68f, h * 0.26f), w * 0.09f, StrokeCap.Round)
        for (row in 0..1) for (col in 0..2) {
          drawCircle(color, w * 0.05f, Offset(w * (0.3f + col * 0.2f), h * (0.56f + row * 0.18f)))
        }
      }
      Mark.Trophy -> {
        val cup = Path().apply {
          moveTo(w * 0.26f, h * 0.12f)
          lineTo(w * 0.74f, h * 0.12f)
          lineTo(w * 0.72f, h * 0.38f)
          quadraticBezierTo(w * 0.7f, h * 0.6f, w * 0.5f, h * 0.62f)
          quadraticBezierTo(w * 0.3f, h * 0.6f, w * 0.28f, h * 0.38f)
          close()
        }
        drawPath(cup, color)
        drawArc(color, 90f, 180f, false, Offset(w * 0.08f, h * 0.18f), Size(w * 0.26f, h * 0.24f), style = Stroke(w * 0.07f))
        drawArc(color, -90f, 180f, false, Offset(w * 0.66f, h * 0.18f), Size(w * 0.26f, h * 0.24f), style = Stroke(w * 0.07f))
        drawLine(color, Offset(w * 0.5f, h * 0.6f), Offset(w * 0.5f, h * 0.78f), w * 0.09f)
        drawRoundRect(color, Offset(w * 0.3f, h * 0.78f), Size(w * 0.4f, h * 0.1f), CornerRadius(w * 0.03f))
      }
      Mark.News -> {
        drawRoundRect(color, Offset(w * 0.1f, h * 0.16f), Size(w * 0.8f, h * 0.68f), CornerRadius(w * 0.08f), style = line)
        drawRect(color, Offset(w * 0.22f, h * 0.3f), Size(w * 0.24f, h * 0.2f))
        drawLine(color, Offset(w * 0.56f, h * 0.33f), Offset(w * 0.78f, h * 0.33f), w * 0.07f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.56f, h * 0.47f), Offset(w * 0.78f, h * 0.47f), w * 0.07f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.22f, h * 0.62f), Offset(w * 0.78f, h * 0.62f), w * 0.07f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.22f, h * 0.74f), Offset(w * 0.62f, h * 0.74f), w * 0.07f, StrokeCap.Round)
      }
      Mark.Sliders -> {
        for ((i, knob) in listOf(0.3f, 0.66f, 0.42f).withIndex()) {
          val y = h * (0.22f + i * 0.28f)
          drawLine(color, Offset(w * 0.1f, y), Offset(w * 0.9f, y), w * 0.08f, StrokeCap.Round)
          drawCircle(color, w * 0.12f, Offset(w * knob, y))
        }
      }
      Mark.Director -> {
        drawRoundRect(color, Offset(w * 0.08f, h * 0.18f), Size(w * 0.84f, h * 0.64f), CornerRadius(w * 0.1f), style = Stroke(w * 0.08f))
        drawCircle(color, w * 0.1f, Offset(w * 0.36f, h * 0.42f))
        drawArc(color, 180f, 180f, true, Offset(w * 0.2f, h * 0.56f), Size(w * 0.32f, h * 0.3f))
        drawLine(color, Offset(w * 0.6f, h * 0.4f), Offset(w * 0.8f, h * 0.4f), w * 0.07f, StrokeCap.Round)
        drawLine(color, Offset(w * 0.6f, h * 0.56f), Offset(w * 0.76f, h * 0.56f), w * 0.07f, StrokeCap.Round)
      }
    }
  }
}

/** A glass capsule button with one of the marks, for "Advanced search". */
@Composable
fun MarkButton(text: String, mark: Mark, onClick: () -> Unit) {
  val p = rememberPointed()
  Row(
      Modifier.pointable(p, onClick, 1.04f)
          .clip(RoundedCornerShape(50))
          .background(if (p.hovered) Color(0x33F4F1EA) else Color(0x1AF4F1EA))
          .border(1.dp, Ink.hairline, RoundedCornerShape(50))
          .padding(horizontal = 20.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    MarkGlyph(mark, Modifier.size(20.dp), Ink.text1)
    Text(text, style = WallType.label)
  }
}
