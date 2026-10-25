package app.nama.quest.ui.screens.people

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.AccountNotification
import app.nama.core.GenreStrength
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.MovieLine
import app.nama.core.NotificationPage
import app.nama.core.Post
import app.nama.core.S
import app.nama.quest.ui.Glyph
import app.nama.quest.ui.Icon
import app.nama.quest.ui.Ink
import app.nama.quest.ui.Pill
import app.nama.quest.ui.RoundButton
import app.nama.quest.ui.WallType
import app.nama.quest.ui.glass
import app.nama.quest.ui.pointable
import app.nama.quest.ui.rememberImage
import app.nama.quest.ui.rememberPointed

/*
 * The pieces of the profile page (ProfileView.swift): the card at the top, the line from a film,
 * the taste panels and the account's messages. The page itself, and what it loads, is in
 * ProfileScreen.kt.
 */

/** The plan badge and the star figures: the gold of the Mac's profile (IMDb's yellow). */
val Gold = Color(0xFFF5C518)
private val GoldLight = Color(0xFFFFE27A)
private val Ember = Color(0xFFFF6A4D)

/** A count on the card: a big number with a word under it. */
class Figure(val value: String, val label: String, val star: Boolean = false)

/**
 * One glass card for who the user is: the picture, and beside it the name, the plan, the
 * username and the line from a film; across from them the counts, with the buttons under them.
 */
@Composable
fun ProfileCard(
    name: String,
    under: String?,
    plan: String?,
    avatarUrl: String?,
    initials: String?,
    figures: List<Figure>,
    figuresNote: String?,
    quote: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
  Row(
      Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp)).padding(horizontal = 38.dp, vertical = 32.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(32.dp),
  ) {
    Avatar(avatarUrl, initials, 144.dp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(name, style = WallType.title.copy(fontSize = 44.sp, lineHeight = 56.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        plan?.let { PlanBadge(it) }
      }
      under?.let { Text(it, style = WallType.body.copy(color = Ink.text2), modifier = Modifier.widthIn(max = 620.dp)) }
      Box(Modifier.padding(top = 14.dp)) { quote() }
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(20.dp)) {
      if (figures.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(38.dp)) { figures.forEach { FigureView(it) } }
      }
      figuresNote?.let { Text(it, style = WallType.caption) }
      actions()
    }
  }
}

@Composable
private fun PlanBadge(text: String) {
  Text(
      text,
      style = WallType.label.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink.focusInk),
      maxLines = 1,
      modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.verticalGradient(listOf(GoldLight, Gold))).padding(horizontal = 16.dp, vertical = 4.dp),
  )
}

@Composable
private fun FigureView(f: Figure) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      if (f.star) Glyph(Icon.Star, Modifier.size(20.dp), Gold)
      Text(f.value, style = WallType.title.copy(fontSize = 34.sp, lineHeight = 42.sp))
    }
    Text(f.label, style = WallType.caption, maxLines = 1)
  }
}

/** The account's picture over a warm gradient with the initials, or a person for a guest. */
@Composable
private fun Avatar(url: String?, initials: String?, size: Dp) {
  val image = rememberImage(url, 300)
  Box(
      Modifier.size(size)
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(Ember, Ink.accent, Color(0xFFD92B58))))
          .border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    if (initials != null) {
      Ltr { Text(initials, style = WallType.title.copy(fontSize = 52.sp, lineHeight = 60.sp, color = Color.White)) }
    } else {
      Glyph(Icon.Person, Modifier.size(size * 0.46f), Color.White)
    }
    if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
  }
}

/**
 * The line from a film as a quotation: a large quotation mark, the words in a serif, and under
 * them the film, its year and its director, which open the film's page when 30nama has it. In the
 * film's own English, so left to right, kept to the same edge as the name above it.
 */
@Composable
fun Quote(line: MovieLine, film: Post?, onOpen: (Post) -> Unit) {
  Box(Modifier.widthIn(max = 760.dp)) {
    Ltr {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
          Text(
              "“",
              style = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 64.sp, color = Ink.accent.copy(alpha = 0.75f)),
              modifier = Modifier.offset(x = (-2).dp, y = (-16).dp),
          )
          Text(
              line.text,
              style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 23.sp, lineHeight = 32.sp, color = Ink.text1.copy(alpha = 0.92f)),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(start = 32.dp),
          )
        }
        val p = rememberPointed()
        val lit = p.hovered && film != null
        Row(
            Modifier.padding(start = 32.dp)
                .heightIn(min = 44.dp)
                .then(if (film != null) Modifier.pointable(p, { onOpen(film) }, 1.02f) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(Modifier.width(20.dp).height(2.dp).clip(RoundedCornerShape(50)).background(Ink.accent))
          Text(
              "${line.film.uppercase()} · ${line.year} · ${line.directors.joinToString(", ")}",
              style =
                  TextStyle(
                      fontSize = 15.sp,
                      fontWeight = FontWeight.SemiBold,
                      letterSpacing = 1.sp,
                      color = if (lit) Ink.accent else Ink.text3,
                      textDecoration = if (lit) TextDecoration.Underline else null,
                  ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** What the stars add up to: the genres that do best, and how generous the user is with them. */
@Composable
fun Insights(loc: Loc, genres: List<GenreStrength>, spread: List<Int>) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
    InsightPanel(loc(S.yourTaste), Modifier.weight(1f)) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        genres.forEach { g ->
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(g.name, style = WallType.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(190.dp))
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0x1AFFFFFF))) {
              Box(
                  Modifier.fillMaxHeight()
                      .fillMaxWidth(g.strength.toFloat().coerceIn(0.04f, 1f))
                      .clip(RoundedCornerShape(50))
                      .background(Brush.horizontalGradient(listOf(Ember, Ink.accent))),
              )
            }
          }
        }
      }
    }
    InsightPanel(loc(S.howYouRate), Modifier.weight(1f)) {
      val most = (spread.maxOrNull() ?: 1).coerceAtLeast(1)
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        spread.forEachIndexed { i, count ->
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (count > 0) loc.num(count) else " ", style = WallType.caption.copy(color = Ink.text2, fontWeight = FontWeight.SemiBold))
            Box(
                Modifier.width(56.dp)
                    .height((104f * count / most).coerceAtLeast(8f).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (count > 0) Brush.verticalGradient(listOf(GoldLight, Gold)) else Brush.verticalGradient(listOf(Color(0x1AFFFFFF), Color(0x1AFFFFFF)))),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(loc.num(i + 1), style = WallType.caption.copy(fontWeight = FontWeight.SemiBold))
              Glyph(Icon.Star, Modifier.size(12.dp), Ink.text3)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InsightPanel(title: String, modifier: Modifier, content: @Composable () -> Unit) {
  Column(
      modifier.height(260.dp).glass(RoundedCornerShape(24.dp), fill = Color(0xB30E1016)).padding(26.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Text(title, style = WallType.label.copy(color = Ink.text3))
    content()
  }
}

/**
 * The account's messages, unfolded on the page under the card (a panel can't open a popover).
 * Choosing one marks it read and opens its title or its link.
 */
@Composable
fun NotificationsPanel(loc: Loc, page: NotificationPage, onMarkAll: () -> Unit, onClose: () -> Unit, onChoose: (AccountNotification) -> Unit) {
  Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp)).padding(vertical = 14.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Glyph(Icon.Bell, Modifier.size(24.dp), Ink.text1)
      Text(loc(S.notifications), style = WallType.heading)
      Spacer(Modifier.weight(1f))
      if (page.unread > 0) Pill(loc(S.markAllRead), onMarkAll)
      RoundButton(Icon.Close, onClose, 52.dp)
    }
    if (page.items.isEmpty()) {
      PageNote(loc(S.noNotifications))
    } else {
      page.items.forEachIndexed { i, item ->
        if (i > 0) Hairline(Modifier.padding(horizontal = 28.dp))
        NotificationRow(loc, item) { onChoose(item) }
      }
    }
  }
}

@Composable
private fun NotificationRow(loc: Loc, item: AccountNotification, onClick: () -> Unit) {
  val p = rememberPointed()
  Row(
      Modifier.fillMaxWidth()
          .pointable(p, onClick, 1.005f)
          .background(if (p.hovered) Color(0x12FFFFFF) else Color.Transparent)
          .padding(horizontal = 28.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(Modifier.padding(top = 10.dp).size(10.dp).clip(CircleShape).background(if (item.read) Color.Transparent else Ink.accent))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      item.title?.let { Text(it, style = WallType.label.copy(fontWeight = if (item.read) FontWeight.Medium else FontWeight.Bold)) }
      item.content?.let { Text(it, style = WallType.body, maxLines = 4, overflow = TextOverflow.Ellipsis) }
      val date = if (loc.lang == Lang.fa) item.date.jalali ?: item.date.gregorian else item.date.gregorian ?: item.date.jalali
      date?.let { Text(loc.num(it), style = WallType.caption) }
    }
  }
}

/** Nothing rated yet: what the stars are for. */
@Composable
fun NoRatings(loc: Loc) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(loc(S.noRatingsYet), style = WallType.heading)
    Text(loc(S.rateHint), style = WallType.body)
  }
}

/**
 * The title the page is washed in, far behind everything: decoded tiny and blurred, so it is a
 * colour more than a picture, and fading out down the page.
 */
@Composable
fun Wash(url: String?, modifier: Modifier = Modifier) {
  val image = rememberImage(url, 160) ?: return
  Image(
      image,
      null,
      modifier
          .fillMaxWidth()
          .height(640.dp)
          .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
          .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
          }
          .blur(48.dp)
          .alpha(0.5f),
      contentScale = ContentScale.Crop,
  )
}
