package app.nama.quest.ui.title

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Fmt
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.Post
import app.nama.core.S
import app.nama.quest.data.Tile
import app.nama.quest.ui.Directional
import app.nama.quest.ui.Glyph
import app.nama.quest.ui.Icon
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Type

// The scores' own colours, as on the Mac (MacKit.swift MetaRow).
val ImdbGold = Color(0xFFF5C518)
val RottenRed = Color(0xFFFA5A3C)
val MetaGreen = Color(0xFF66CC33)
val FanBlue = Color(0xFF6C8CFF)

private val chipText = Type.label.copy(fontSize = 16.sp, lineHeight = 20.sp)

/** A small capsule of facts; sized for reading at the title panel's 1.4 m. */
@Composable
fun Chip(modifier: Modifier = Modifier, fill: Color = Color(0x1FF4F1EA), content: @Composable RowScope.() -> Unit) {
  Row(
      modifier.height(36.dp).clip(RoundedCornerShape(50)).background(fill).padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    ProvideTextStyle(chipText) { content() }
  }
}

/** "IMDb 7.8": the source's short name in its colour. Latin and figures, so always left to right. */
@Composable
private fun ScoreChip(name: String, value: String, color: Color) {
  Directional(false) {
    Chip {
      Text(name, style = chipText.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color))
      Text(value)
    }
  }
}

/** A run held left to right inside a chip (year range, age rating, "#12"). */
@Composable
private fun LtrChip(text: String) {
  Directional(false) { Chip { Text(text) } }
}

private fun joiner(lang: Lang) = if (lang == Lang.en) ", " else "، "

/** Scores, year, length, age rating and up to three genres on one line, as on the Mac. */
@Composable
fun MetaChips(post: Post, loc: Loc, modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    post.imdbScore?.let { ScoreChip("IMDb", Fmt.score(it), ImdbGold) }
    post.rottenTomatoes?.let { ScoreChip("RT", "$it%", RottenRed) }
    post.metacritic?.let { ScoreChip("MC", "$it", MetaGreen) }
    post.fanScore?.let { ScoreChip(it.source, Fmt.score(it.value), FanBlue) }
    post.siteScore?.let {
      Chip {
        Glyph(Icon.Star, Modifier.size(14.dp), Ink.good)
        Text(loc.num(Fmt.score(it)))
      }
    }
    post.year?.let { year ->
      LtrChip(loc.num(post.yearEnd?.let { end -> if (end == year) "$year" else "$year–$end" } ?: "$year"))
    }
    Fmt.runtime(post.runtimeMinutes, loc.lang)?.let { Chip { Text(it) } }
    post.age?.let { LtrChip(it) }
    if (post.genres.isNotEmpty()) {
      Text(
          post.genres.take(3).joinToString(joiner(loc.lang)) { it.name },
          style = Type.label.copy(fontSize = 17.sp, color = Ink.text2, fontWeight = FontWeight.Medium),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
      )
    }
  }
}

/** What the wall's card already knew, while the full title is on its way (and for the demo). */
@Composable
fun TileMetaChips(tile: Tile, minutes: Int?, loc: Loc, modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    tile.rating?.let { ScoreChip("IMDb", it, ImdbGold) }
    tile.year?.let { LtrChip(loc.num(it)) }
    Fmt.runtime(minutes, loc.lang)?.let { Chip { Text(it) } }
    if (tile.genres.isNotEmpty()) {
      Text(
          tile.genres.take(3).joinToString(joiner(loc.lang)),
          style = Type.label.copy(fontSize = 17.sp, color = Ink.text2, fontWeight = FontWeight.Medium),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
      )
    }
  }
}

fun hasStanding(post: Post) = post.imdbTop250 != null || post.siteTop100 != null || post.exclusiveSubtitle

/** Places on the top lists and an exclusive subtitle, as 30nama's own apps show them. */
@Composable
fun StandingChips(post: Post, loc: Loc, modifier: Modifier = Modifier) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    post.imdbTop250?.let { RankChip(loc(S.imdbTop250), it, ImdbGold, loc) }
    post.siteTop100?.let { RankChip(loc(S.siteTop100), it, Ink.accent, loc) }
    if (post.exclusiveSubtitle) {
      Chip {
        Glyph(Icon.Subtitles, Modifier.size(18.dp), Ink.good)
        Text(loc(S.exclusiveSub), maxLines = 1)
      }
    }
  }
}

@Composable
private fun RankChip(list: String, place: Int, tint: Color, loc: Loc) {
  Chip {
    Trophy(tint, Modifier.size(16.dp))
    Text(list, maxLines = 1)
    // "#12" keeps its sign before the figure in Persian and Arabic too.
    Directional(false) { Text(loc.num("#$place")) }
  }
}

/** "IMDb 1,234,567 votes · 34,000 on watchlists · …", each run apart so mixed scripts keep their order. */
@Composable
fun CountsLine(post: Post, loc: Loc, modifier: Modifier = Modifier) {
  val runs =
      listOfNotNull(
          post.imdbVotes?.let { listOf("IMDb", Fmt.count(it, loc.lang), loc(S.votes)) },
          post.inWatchlists?.let { listOf(Fmt.count(it, loc.lang), loc(S.inWatchlists)) },
          post.inFavorites?.let { listOf(Fmt.count(it, loc.lang), loc(S.inFavorites)) },
      )
  if (runs.isEmpty()) return
  val style = Type.caption.copy(fontSize = 15.sp)
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    runs.forEachIndexed { i, run ->
      if (i > 0) Text("·", style = style)
      Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { run.forEach { Text(it, style = style, maxLines = 1) } }
    }
  }
}

/** A small cup, for places on the top lists (the shared Glyph set has none). */
@Composable
fun Trophy(color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val cup =
        Path().apply {
          moveTo(w * 0.26f, h * 0.1f)
          lineTo(w * 0.74f, h * 0.1f)
          lineTo(w * 0.74f, h * 0.36f)
          cubicTo(w * 0.74f, h * 0.56f, w * 0.62f, h * 0.64f, w * 0.5f, h * 0.64f)
          cubicTo(w * 0.38f, h * 0.64f, w * 0.26f, h * 0.56f, w * 0.26f, h * 0.36f)
          close()
        }
    drawPath(cup, color)
    val handle = Stroke(w * 0.09f, cap = StrokeCap.Round)
    drawArc(color, 90f, 180f, false, Offset(w * 0.08f, h * 0.16f), Size(w * 0.26f, h * 0.26f), style = handle)
    drawArc(color, -90f, 180f, false, Offset(w * 0.66f, h * 0.16f), Size(w * 0.26f, h * 0.26f), style = handle)
    drawRect(color, Offset(w * 0.44f, h * 0.6f), Size(w * 0.12f, h * 0.2f))
    drawRoundRect(color, Offset(w * 0.28f, h * 0.78f), Size(w * 0.44f, h * 0.12f), CornerRadius(w * 0.04f))
  }
}
