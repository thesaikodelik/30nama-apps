package app.nama.quest.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.AdvancedQuery
import app.nama.core.Loc
import app.nama.core.S
import app.nama.core.StreamItem
import app.nama.quest.AppModel
import app.nama.quest.Library
import app.nama.quest.Place
import app.nama.quest.Route
import app.nama.quest.data.Details
import app.nama.quest.data.Episode
import app.nama.quest.data.Tile
import app.nama.quest.ui.title.Chip
import app.nama.quest.ui.title.CountsLine
import app.nama.quest.ui.title.Facts
import app.nama.quest.ui.title.ListToggle
import app.nama.quest.ui.title.MetaChips
import app.nama.quest.ui.title.RatingCapsule
import app.nama.quest.ui.title.StandingChips
import app.nama.quest.ui.title.TX
import app.nama.quest.ui.title.TileMetaChips
import app.nama.quest.ui.title.UpdatesTicker
import app.nama.quest.ui.title.hasFacts
import app.nama.quest.ui.title.hasStanding
import app.nama.quest.ui.title.holdable
import kotlinx.coroutines.launch

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


/**
 * Under the logo: what the title is, where it stands, and what can be done with it. Laid out for
 * 1.1 × 0.46 m. The buttons sit at the bottom in fixed places (play at the reading start, back at
 * the end, rating and lists below), so nothing moves under the ray while the title fills in.
 */
@Composable
fun TitleInfo(model: AppModel) {
  val lang by model.lang.collectAsState()
  val details by model.details.collectAsState()
  val focused by model.focused.collectAsState()
  val notice by model.notice.collectAsState()
  val place by model.place.collectAsState()
  val trailer by model.trailer.collectAsState()
  val sound by model.trailerSound.collectAsState()
  val lists by model.library.lists.collectAsState()
  val ratingsVersion by model.library.ratingsVersion.collectAsState()
  val scope = rememberCoroutineScope()
  val loc = model.loc
  val tile = details?.tile ?: focused ?: return
  val post = details?.post
  // The demo has no account and no 30nama title behind its tiles: no rating, no lists.
  val live = model.services != null
  val signedIn = model.services?.userState?.collectAsState()?.value != null
  Directional(lang.isRTL) {
    Column(
        Modifier.fillMaxSize().glass(RoundedCornerShape(32.dp)).padding(horizontal = 40.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            post?.let(loc::kindLine).orEmpty(),
            style = Type.label.copy(fontSize = 17.sp, color = Ink.text2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (post?.comingSoon == true) {
          Chip(fill = Ink.accent.copy(alpha = 0.22f)) { Text(loc(S.comingSoonNote), maxLines = 1) }
        }
        Spacer(Modifier.weight(1f))
        post?.let { CountsLine(it, loc) }
      }
      if (post != null) MetaChips(post, loc) else TileMetaChips(tile, details?.runtimeMinutes, loc)
      if (post != null && hasStanding(post)) StandingChips(post, loc)
      Text(
          details?.overview ?: if (details == null) loc(S.loading) else "",
          style = Type.body.copy(fontSize = 17.sp),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.weight(1f))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Pill(text = playLabel(loc, details), onClick = { model.play() }, primary = true, icon = Icon.Play, enabled = details != null)
        if (trailer != null) Pill(loc(S.trailer), { model.toggleTrailerSound() }, icon = if (sound) Icon.Sound else Icon.Muted)
        Spacer(Modifier.weight(1f))
        Pill(loc(S.back), { model.back() }, icon = Icon.Back)
      }
      if (live) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          val stars = ratingsVersion.let { post?.let { model.library.stars(it.id) } }
          RatingCapsule(loc(S.yourRating), stars, enabled = post != null, onSet = { v -> post?.let { model.library.rate(it, v) } })
          Spacer(Modifier.weight(1f))
          if (signedIn) {
            for (group in listOf(Library.WATCHLIST, Library.FAVORITE)) {
              ListToggle(
                  heart = group == Library.FAVORITE,
                  on = post != null && lists[group]?.contains(post.id) == true,
                  enabled = post != null,
              ) {
                // The library flips it at once and puts it back if 30nama says no.
                val p = post ?: return@ListToggle
                scope.launch { if (!model.library.toggle(p, group)) model.showToast(loc(S.error)) }
              }
            }
          }
        }
      }
      // One line kept for what changed lately, or for a message about playing; never both, and
      // always the same height so the buttons above stay put.
      Box(Modifier.fillMaxWidth().height(26.dp).clipToBounds(), contentAlignment = Alignment.CenterStart) {
        val updates = post?.updates.orEmpty()
        when {
          notice != null -> Text(notice!!, style = Type.label.copy(fontSize = 15.sp, color = Ink.accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
          updates.isNotEmpty() -> UpdatesTicker(updates, loc, running = place == Place.Title, modifier = Modifier.fillMaxWidth())
        }
      }
    }
  }
}

/**
 * "Resume · S2 · E5" for a series left part-way; "Resume" for a film left more than 5 s in and not
 * in its last 3% (the credits); plain "Play" otherwise.
 */
private fun playLabel(loc: Loc, d: Details?): String {
  d ?: return loc(S.play)
  val result = d.result
  val resume = result?.resume ?: d.post?.resume
  if (d.tile.isSeries) {
    val item = result?.items?.getOrNull(result.startIndex)
    if (resume != null && item != null) {
      return "${loc(S.resume)} · ${loc.episodeTag(item.season, item.episodeRaw ?: "${item.episodeNumber}")}"
    }
    // The demo knows its resume episode without a stream answer.
    d.resume?.takeIf { result == null }?.let { return "${loc(S.resume)} · ${loc.episodeTag(it.season, "${it.number}")}" }
    return loc(S.play)
  }
  resume ?: return loc(S.play)
  val atEnd = resume.progress > 0.97
  return if (resume.time > 5 && !atEnd) loc(S.resume) else loc(S.play)
}

/** The side panel at the reading start: episodes for a series, the story and the facts for a film. */
@Composable
fun EpisodesPanel(model: AppModel) {
  val lang by model.lang.collectAsState()
  val details by model.details.collectAsState()
  val season by model.season.collectAsState()
  val watched by model.library.watched.collectAsState()
  val loc = model.loc
  val d = details ?: return
  Directional(lang.isRTL) {
    if (d.seasons.isEmpty()) {
      Column(
          Modifier.fillMaxSize().glass(RoundedCornerShape(30.dp)).verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 26.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(loc(S.storyline), style = Type.title.copy(fontSize = 26.sp))
        Text(d.overview.orEmpty(), style = Type.body)
        val post = d.post
        if (post != null) {
          if (hasFacts(post)) {
            Divider()
            Facts(post, loc, onDirector = { openDirector(model, it) })
          }
        } else {
          d.director?.let { OldFact(loc(S.director), it) }
          d.runtimeMinutes?.let { OldFact(X.runtime(lang), loc.num(it) + " " + loc(S.min)) }
        }
      }
      return@Directional
    }
    Column(Modifier.fillMaxSize().glass(RoundedCornerShape(30.dp)).padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(loc(S.episodes), style = Type.title.copy(fontSize = 26.sp), modifier = Modifier.padding(horizontal = 28.dp))
      if (d.seasons.size > 1) {
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
                        .padding(horizontal = 18.dp, vertical = 11.dp),
            )
          }
        }
      }
      val items = remember(d) { d.result?.items.orEmpty().associateBy { it.id } }
      val resume = d.result?.resume
      val episodes = d.seasons.firstOrNull { it.number == season }?.episodes.orEmpty()
      LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(episodes, key = { it.id }) { e ->
          val item = items[e.id]
          // The demo has no stream answer: its episode ids stand in for file ids.
          val fileID = if (d.result == null) e.id else item?.fileID.orEmpty()
          val isResume =
              if (d.result == null) d.resume?.id == e.id
              else item != null && item.fileID.isNotEmpty() && item.fileID == resume?.fileID
          EpisodeRow(
              model = model,
              e = e,
              item = item,
              fileID = fileID,
              watched = e.watched || (fileID.isNotEmpty() && fileID in watched),
              isResume = isResume,
              progress = if (isResume) resume?.progress?.toFloat() ?: 0f else 0f,
          )
        }
      }
    }
  }
}

/**
 * One episode: its number (in the accent when it's the one to resume, a play mark under the ray),
 * its name and length. A held trigger marks it watched or not, the headset's stand-in for the Mac's
 * right-click menu; a line fills along the bottom while the trigger is held.
 */
@Composable
private fun EpisodeRow(model: AppModel, e: Episode, item: StreamItem?, fileID: String, watched: Boolean, isResume: Boolean, progress: Float) {
  val p = rememberPointed()
  val loc = model.loc
  val lang = loc.lang
  val canMark = fileID.isNotEmpty()
  val holdMs = LocalViewConfiguration.current.longPressTimeoutMillis.toInt()
  val hold by animateFloatAsState(
      if (p.pressed && canMark) 1f else 0f,
      if (p.pressed && canMark) tween(holdMs, easing = LinearEasing) else snap(),
      label = "hold",
  )
  val shape = RoundedCornerShape(16.dp)
  Box(
      Modifier.fillMaxWidth()
          .holdable(p, { model.play(e) }, if (canMark) ({ model.library.setWatched(fileID, !watched) }) else null)
          .clip(shape)
          .background(if (p.hovered) Color(0x26F4F1EA) else if (isResume) Ink.accent.copy(alpha = 0.1f) else Color(0x0FF4F1EA)),
  ) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.widthIn(min = 52.dp), contentAlignment = Alignment.Center) {
        if (p.hovered) {
          Glyph(Icon.Play, Modifier.size(22.dp), Ink.text1)
        } else {
          Text(
              loc.num(app.nama.core.Episode.label(item?.episodeRaw, e.number)),
              style = Type.heading.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = if (isResume) Ink.accent else Ink.text2),
              maxLines = 1,
          )
        }
      }
      Column(Modifier.weight(1f)) {
        val title = item?.title(lang) ?: e.title.takeIf { it.isNotBlank() } ?: "${loc(S.episode)} ${loc.num(e.number)}"
        Text(title, style = Type.label.copy(fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val minutes = e.minutes?.let { loc.num(it) + " " + loc(S.min) }
        // The hold is told about while pointing, never needed to find it: play is the plain click.
        val hint = if (p.hovered && canMark) (if (watched) TX.holdToMarkUnwatched else TX.holdToMarkWatched)(lang) else null
        val line = listOfNotNull(minutes, hint).joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = Type.caption.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (watched) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Ink.good), contentAlignment = Alignment.Center) {
          Glyph(Icon.Check, Modifier.size(16.dp), Ink.focusInk)
        }
      }
    }
    if (progress > 0f) {
      ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 80.dp, end = 14.dp, bottom = 8.dp).clip(RoundedCornerShape(50)))
    }
    if (hold > 0f) {
      Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(hold).height(3.dp).background(Ink.text1.copy(alpha = 0.7f)))
    }
  }
}

@Composable
private fun Divider() {
  Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.hairline))
}

@Composable
private fun OldFact(label: String, value: String) {
  Column {
    Text(label, style = Type.caption)
    Text(value, style = Type.label.copy(fontSize = 18.sp))
  }
}

/** A director's other titles on the wall, newest first, as the Mac's facts open them. */
private fun openDirector(model: AppModel, name: String) {
  model.push(Route.AdvancedSearch(AdvancedQuery(director = name, sort = "year")))
}

/** The side panel at the reading end: the people in the title, and for a series the facts below them. */
@Composable
fun CastPanel(model: AppModel) {
  val lang by model.lang.collectAsState()
  val details by model.details.collectAsState()
  val loc = model.loc
  val d = details ?: return
  if (d.people.isEmpty()) return
  val post = d.post
  // Who they are and their titles come from the site API, by 30nama's id or the IMDb one.
  val hasSite = model.services?.api?.hasSite == true
  val showFacts = d.seasons.isNotEmpty() && post != null && hasFacts(post)
  Directional(lang.isRTL) {
    Column(Modifier.fillMaxSize().glass(RoundedCornerShape(30.dp)).padding(vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(loc(S.cast), style = Type.title.copy(fontSize = 26.sp), modifier = Modifier.padding(horizontal = 28.dp))
      LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (post != null && post.cast.isNotEmpty()) {
          items(post.cast.distinctBy { it.name }, key = { it.id }) { person ->
            val open = if (hasSite && (person.siteID != null || person.imdb != null)) ({ model.push(Route.PersonPage(person)) }) else null
            CastRow(person.name, person.role, person.poster?.url(160.0)?.toString(), open)
          }
        } else {
          items(d.people, key = { it.name + it.role }) { person -> CastRow(person.name, person.role, person.photo, null) }
        }
        if (showFacts) {
          item(key = "facts") {
            Column(Modifier.padding(horizontal = 10.dp).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Divider()
              Facts(post!!, loc, onDirector = { openDirector(model, it) })
            }
          }
        }
      }
    }
  }
}

/** A face, a name and a part; a button to their page when [open] is given, lit like a link under the ray. */
@Composable
private fun CastRow(name: String, role: String?, photoURL: String?, open: (() -> Unit)?) {
  val p = rememberPointed()
  val lit = p.hovered && open != null
  Row(
      Modifier.fillMaxWidth()
          .then(if (open != null) Modifier.pointable(p, open, 1.02f) else Modifier)
          .clip(RoundedCornerShape(16.dp))
          .background(if (lit) Color(0x1FF4F1EA) else Color.Transparent)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val photo = rememberImage(photoURL, 160)
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(Ink.surface2).border(if (lit) 2.dp else 1.dp, if (lit) Ink.accent else Ink.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      else Glyph(Icon.Person, Modifier.size(30.dp), Ink.text3)
    }
    Column(Modifier.weight(1f)) {
      Text(
          name,
          style = Type.label.copy(fontSize = 18.sp, color = if (lit) Ink.accent else Ink.text1, textDecoration = if (lit) TextDecoration.Underline else null),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      role?.takeIf { it.isNotBlank() }?.let { Text(it, style = Type.caption.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
  }
}
