package app.nama.quest.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.S
import app.nama.quest.AppModel
import app.nama.quest.data.Episode
import app.nama.quest.data.Tile

/**
 * The film's name as it floats in front of the screen. Two panels draw this: the glow a few
 * centimetres behind, the sharp logo in front, so the name has depth when the head moves.
 */
@Composable
fun TitleLogo(model: AppModel, glow: Boolean) {
  val focused by model.focused.collectAsState()
  val details by model.details.collectAsState()
  val lang by model.lang.collectAsState()
  val tile = details?.tile ?: focused ?: return
  val logo = rememberImage(details?.logo, 1200)
  val tint = Color(tile.art?.light ?: 0xFFF0454A)
  Directional(lang.isRTL) {
    if (glow) {
      // Behind the sharp name, the title's own colour glowing. (Readability over a trailer comes
      // from dimming the trailer itself; a translucent dark halo barely shows on a headset layer.)
      LogoShape(tile, logo, tint.copy(alpha = 0.6f), Modifier.fillMaxSize().blur(36.dp))
    } else {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (logo != null) {
          Image(logo, null, Modifier.fillMaxSize().padding(24.dp), contentScale = ContentScale.Fit)
        } else {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tile.title,
                textAlign = TextAlign.Center,
                maxLines = 2,
                style =
                    Type.display.copy(
                        fontSize = 96.sp,
                        brush = Brush.verticalGradient(listOf(Color.White, lerpTint(tint))),
                        shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), 14f),
                    ),
            )
            tile.originalTitle?.takeIf { it != tile.title }?.let {
              Text(
                  it.uppercase(),
                  textAlign = TextAlign.Center,
                  style =
                      Type.label.copy(
                          fontSize = 22.sp,
                          letterSpacing = 8.sp,
                          color = lerpTint(tint),
                          shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 10f),
                      ),
              )
            }
          }
        }
      }
    }
  }
}

/** The name's silhouette in one colour, for the glow layer. */
@Composable
private fun LogoShape(tile: Tile, logo: androidx.compose.ui.graphics.ImageBitmap?, color: Color, modifier: Modifier) {
  Box(modifier, contentAlignment = Alignment.Center) {
    if (logo != null) {
      Image(logo, null, Modifier.fillMaxSize().padding(24.dp), contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(color))
    } else {
      Text(tile.title, textAlign = TextAlign.Center, maxLines = 2, style = Type.display.copy(fontSize = 96.sp, color = color))
    }
  }
}

private fun lerpTint(c: Color) = Color(0.55f + c.red * 0.45f, 0.55f + c.green * 0.45f, 0.55f + c.blue * 0.45f)

/** Under the logo: what the title is, and the buttons. */
@Composable
fun TitleInfo(model: AppModel) {
  val lang by model.lang.collectAsState()
  val details by model.details.collectAsState()
  val focused by model.focused.collectAsState()
  val notice by model.notice.collectAsState()
  val loc = model.loc
  val tile = details?.tile ?: focused ?: return
  Directional(lang.isRTL) {
    Column(
        Modifier.fillMaxSize().glass(RoundedCornerShape(32.dp)).padding(horizontal = 40.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(metaLine(model, tile, details?.runtimeMinutes), style = Type.label.copy(color = Ink.text2, fontSize = 18.sp))
      Text(
          details?.overview ?: loc(S.loading),
          style = Type.body.copy(fontSize = 17.sp),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
      )
      Spacer(Modifier.weight(1f))
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        val resume = details?.resume
        Pill(
            text = if (resume != null && tile.isSeries) "${loc(S.resume)} · ${loc.num(resume.season)}:${loc.num(resume.number)}" else loc(S.play),
            onClick = { model.play() },
            primary = true,
            icon = Icon.Play,
            enabled = details != null,
        )
        val trailer by model.trailer.collectAsState()
        val sound by model.trailerSound.collectAsState()
        if (trailer != null) Pill(loc(S.trailer), { model.toggleTrailerSound() }, icon = if (sound) Icon.Sound else Icon.Muted)
        Pill(loc(S.back), { model.back() }, icon = Icon.Back)
      }
      notice?.let {
        Text(it, style = Type.caption.copy(color = Ink.accent))
      }
    }
  }
}

private fun metaLine(model: AppModel, tile: Tile, minutes: Int?): String {
  val loc = model.loc
  return listOfNotNull(
          tile.year?.let(loc::num),
          tile.genres.take(3).joinToString(" · ").ifEmpty { null },
          minutes?.let { loc.num(it) + " " + loc(S.min) },
          tile.rating?.let { "★ " + loc.num(it) },
      )
      .joinToString("   ·   ")
}

/** The side panel at the reading start: episodes for a series, the story for a film. */
@Composable
fun EpisodesPanel(model: AppModel) {
  val lang by model.lang.collectAsState()
  val details by model.details.collectAsState()
  val season by model.season.collectAsState()
  val loc = model.loc
  val d = details ?: return
  Directional(lang.isRTL) {
    Column(Modifier.fillMaxSize().glass(RoundedCornerShape(30.dp)).padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      if (d.seasons.isEmpty()) {
        Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text(loc(S.storyline), style = Type.title.copy(fontSize = 26.sp))
          Text(d.overview.orEmpty(), style = Type.body)
          d.director?.let { Fact(loc(S.director), it) }
          d.runtimeMinutes?.let { Fact(X.runtime(lang), loc.num(it) + " " + loc(S.min)) }
        }
        return@Column
      }
      Text(loc(S.episodes), style = Type.title.copy(fontSize = 26.sp), modifier = Modifier.padding(horizontal = 28.dp))
      LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(d.seasons, key = { it.number }) { s ->
          val p = rememberPointed()
          val on = s.number == season
          Text(
              "${loc(S.season)} ${loc.num(s.number)}",
              style = Type.label.copy(color = if (on) Ink.focusInk else Ink.text2),
              modifier =
                  Modifier.pointable(p, { model.chooseSeason(s.number) }, 1.05f)
                      .clip(RoundedCornerShape(50))
                      .background(if (on) Ink.text1 else if (p.hovered) Color(0x33F4F1EA) else Color(0x14F4F1EA))
                      .padding(horizontal = 16.dp, vertical = 7.dp),
          )
        }
      }
      val episodes = d.seasons.firstOrNull { it.number == season }?.episodes.orEmpty()
      LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(episodes, key = { it.id }) { e -> EpisodeRow(model, d.tile, e) }
      }
    }
  }
}

@Composable
private fun EpisodeRow(model: AppModel, tile: Tile, e: Episode) {
  val p = rememberPointed()
  val loc = model.loc
  Row(
      Modifier.fillMaxWidth()
          .pointable(p, { model.play(e) }, 1.02f)
          .clip(RoundedCornerShape(16.dp))
          .background(if (p.hovered) Color(0x1FF4F1EA) else Color.Transparent)
          .padding(10.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(132.dp, 74.dp).clip(RoundedCornerShape(10.dp))) {
      Artwork(tile.copy(backdrop = e.still ?: tile.backdrop), landscape = true, modifier = Modifier.fillMaxSize(), decodeWidth = 220)
      if (p.hovered) Box(Modifier.matchParentSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) { Glyph(Icon.Play, Modifier.size(26.dp)) }
    }
    Column(Modifier.weight(1f)) {
      Text("${loc(S.episode)} ${loc.num(e.number)}", style = Type.label)
      Text(
          listOfNotNull(e.title.takeIf { !it.endsWith(e.number.toString()) }, e.minutes?.let { loc.num(it) + " " + loc(S.min) }).joinToString(" · "),
          style = Type.caption,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    if (e.watched) Glyph(Icon.Check, Modifier.size(18.dp), Ink.good)
  }
}

@Composable
private fun Fact(label: String, value: String) {
  Column {
    Text(label, style = Type.caption)
    Text(value, style = Type.label.copy(fontSize = 18.sp))
  }
}

/** The side panel at the reading end: the people behind the title. */
@Composable
fun CastPanel(model: AppModel) {
  val lang by model.lang.collectAsState()
  val details by model.details.collectAsState()
  val loc = model.loc
  val d = details ?: return
  if (d.people.isEmpty()) return
  Directional(lang.isRTL) {
    Column(Modifier.fillMaxSize().glass(RoundedCornerShape(30.dp)).padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(loc(S.cast), style = Type.title.copy(fontSize = 26.sp), modifier = Modifier.padding(horizontal = 28.dp))
      LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(d.people, key = { it.name + it.role }) { person ->
          Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val photo = rememberImage(person.photo, 160)
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Ink.surface2).border(1.dp, Ink.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
              if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
              else Text(person.name.take(1), style = Type.heading)
            }
            Column {
              Text(person.name, style = Type.label.copy(fontSize = 18.sp))
              Text(person.role, style = Type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }
}
