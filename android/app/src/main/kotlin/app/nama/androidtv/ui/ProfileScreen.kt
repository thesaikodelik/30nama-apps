package app.nama.androidtv.ui

import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import app.nama.core.SiteProfile
import app.nama.core.SiteQuery
import androidx.compose.foundation.layout.wrapContentSize

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.nama.androidtv.attempt
import app.nama.core.AccountNotification
import app.nama.core.Fmt
import app.nama.core.Lang
import app.nama.core.NotificationPage
import app.nama.core.Post
import app.nama.core.PostPage
import app.nama.core.S

private val gold = Color(0xFFF5C518)
private val goldLight = Color(0xFFFFE27A)

/**
 * The user's own page (the Apple TV's ProfileView): who they are, what their ratings say about
 * their taste, the lists they keep on 30nama, what all that suggests they watch next, and the
 * messages 30nama has for them.
 */
@Composable
fun ProfileScreen() {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  var picks by remember { mutableStateOf<List<Post>>(emptyList()) }
  var starters by remember { mutableStateOf<List<Post>>(emptyList()) }
  var watchlist by remember { mutableStateOf<List<Post>>(emptyList()) }
  var favorites by remember { mutableStateOf<List<Post>>(emptyList()) }
  var upcoming by remember { mutableStateOf<List<Post>>(emptyList()) }
  var sizes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
  var notes by remember { mutableStateOf<NotificationPage?>(null) }
  val ratings = app.ratings
  // What has come: the page shows its shelves once they are all in, two and a half seconds at
  // most, so they do not push each other down one by one as each answer comes (the owner). What
  // comes later slides in.
  var listsIn by remember { mutableStateOf(false) }
  var picksIn by remember { mutableStateOf(false) }
  var upcomingIn by remember { mutableStateOf(false) }
  var nameIn by remember { mutableStateOf(false) }
  // The film the line on the card comes from, on 30nama: its picture is the page's backdrop, its
  // poster where it has no scene picture (Stage).
  var film by remember { mutableStateOf<Post?>(null) }
  var filmIn by remember { mutableStateOf(false) }
  var ready by remember { mutableStateOf(false) }
  var revealed by remember { mutableIntStateOf(0) }
  val calm = LocalCalm.current
  LaunchedEffect(Unit) {
    val start = System.currentTimeMillis()
    while (!(listsIn && picksIn && upcomingIn && nameIn && filmIn) && System.currentTimeMillis() - start < 2_500) delay(100)
    ready = true
    for (step in 1..8) {
      revealed = step
      if (!calm) delay(70)
    }
  }

  LaunchedEffect(ratings) {
    val feed = attempt { app.home() } ?: return@LaunchedEffect run { picksIn = true }
    val pool = feed.sections.flatMap { it.posts }
    if (ratings.isEmpty) {
      // Nothing to go on yet: the best-regarded titles to kick things off.
      starters = pool.filter { !it.comingSoon && it.imdbScore != null }.sortedByDescending { it.imdbScore ?: 0.0 }.distinctBy { it.id }.take(12)
    } else picks = ratings.recommend(pool, 14)
    picksIn = true
  }
  LaunchedEffect(app.user?.id) {
    if (!app.isLoggedIn) {
      listsIn = true
      upcomingIn = true
      return@LaunchedEffect
    }
    fun list(group: String) = mapOf("action" to "list", "group" to group, "page" to "1", "orderby" to "add_list", "order" to "DESC")
    watchlist = attempt { PostPage.from(app.api.get("user_list", list("watchlist"))).posts }.orEmpty()
    favorites = attempt { PostPage.from(app.api.get("user_list", list("favorite"))).posts }.orEmpty()
    sizes = attempt { (app.api.get("user_mylist").obj ?: emptyMap()).mapValues { (_, v) -> v["post"].array.size } }.orEmpty()
    listsIn = true
    notes = attempt { NotificationPage.from(app.api.get("notification", mapOf("page" to "1"), background = true)) }
    // The Mac's "Coming up": series on the watchlist or notify list with an episode still to come,
    // soonest first. A handful of pages is every list a person keeps.
    val kept = mutableListOf<Post>()
    for (group in listOf("watchlist", "notify")) {
      var page = 1
      var pages = 1
      while (page <= pages && page <= 4) {
        val next = attempt { PostPage.from(app.api.get("user_list", list(group) + ("page" to "$page"), background = true)) } ?: break
        kept += next.posts
        pages = next.pages
        page++
      }
    }
    upcoming = Post.upcoming(kept)
    upcomingIn = true
  }

  Box(Modifier.fillMaxSize()) {
    Ambient {}
    // Chosen once the lists are in, not one picture and then another as each list comes.
    // The film the line is from (the owner), else what the viewer keeps or rated.
    Stage(if (ready) film ?: favorites.firstOrNull() ?: watchlist.firstOrNull() ?: ratings.newestFirst.firstOrNull()?.post else null, dim = 0.5f, glow = false)
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    ReportTop(list)
    LazyColumn(
        Modifier.fillMaxSize().focusRestorer(),
        state = list,
        contentPadding = PaddingValues(start = Theme.edge, end = Theme.edge, top = 130.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
      // A section: its buttons, at its foot, in focus bring the whole card back, and so the page's top.
      item(key = "header") { Box(Modifier.wholeSection()) { Header(watchlist, favorites, sizes, ready, written = revealed >= 8, nameCame = { nameIn = true }) { found ->
          film = found
          filmIn = true
        } } }
      if (!ratings.isEmpty) item(key = "insights") { Box(Modifier.rising(1, revealed)) { Insights() } }
      if (!ready) return@LazyColumn
      if (upcoming.isNotEmpty()) item(key = "upcoming") { Box(Modifier.animateItem().rising(2, revealed)) { Shelf(loc(S.upcoming), upcoming, note = { it.nextEpisodeLabel(loc.lang) }) } }
      if (picks.isNotEmpty()) item(key = "picks") { Box(Modifier.animateItem().rising(3, revealed)) { Shelf(loc(S.forYou), picks) } }
      if (watchlist.isNotEmpty()) item(key = "watchlist") { Box(Modifier.animateItem().rising(4, revealed)) { Shelf(loc(S.watchlist), watchlist) } }
      if (favorites.isNotEmpty()) item(key = "favorites") { Box(Modifier.animateItem().rising(5, revealed)) { Shelf(loc(S.favorites), favorites) } }
      if (ratings.isEmpty) {
        item(key = "empty") {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(loc(S.noRatingsYet), style = app(26, FontWeight.Bold), color = Theme.text1)
            Text(loc(S.rateHint), style = app(19), color = Theme.text2)
          }
        }
        if (starters.isNotEmpty()) item(key = "starters") { Box(Modifier.animateItem().rising(6, revealed)) { Shelf(loc(S.startWithThese), starters) } }
      } else {
        item(key = "rated") { Box(Modifier.animateItem().rising(6, revealed)) { Shelf(loc(S.yourRating), ratings.newestFirst.mapNotNull { it.post }, stars = ratings.newestFirst.associate { it.postID to it.stars }) } }
      }
      notes?.takeIf { it.items.isNotEmpty() }?.let { page -> item(key = "notes") { Box(Modifier.animateItem().rising(7, revealed)) { Notifications(page) { notes = it } } } }
    }
  }
}

/**
 * The Mac's profile card (Mac/ProfileView.swift): one pane of glass with the picture, the name and
 * its plan, a line from a film said to the viewer; across from them the counts as figures, and the
 * buttons under them.
 */
@Composable
private fun Header(watchlist: List<Post>, favorites: List<Post>, sizes: Map<String, Int>, ready: Boolean, written: Boolean, nameCame: () -> Unit, filmFound: (Post?) -> Unit) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val user = app.user
  val shape = RoundedCornerShape(36.dp)
  // The name the member gave the site, or failing that (a private profile) the one written in
  // Settings, or failing that the username, as on the Mac.
  // Asked of the site, answered or not: the names are settled, and the line with them.
  var asked by remember(user?.name) { mutableStateOf(false) }
  val site by androidx.compose.runtime.produceState<SiteProfile?>(null, user?.name) {
    value = null
    val username = user?.name
    if (username != null && app.api.hasSite) value = runCatching { SiteProfile.fromSite(app.api.site(SiteQuery.profile(username), background = true)) }.getOrNull()
    asked = true
  }
  // The name, the counts and the line wait for the site's name and the lists: shown at once, one
  // name gave way to another and the counts came one by one.
  LaunchedEffect(asked) { if (asked) nameCame() }
  val words by animateFloatAsState(if (ready) 1f else 0f, if (LocalCalm.current) snap() else tween(450), label = "words")
  val own = app.ownName?.takeIf { user != null }
  val shown = site?.fullName ?: own ?: user?.name
  // First and family name for the line from a film: the site's, or the one written here.
  val names: Pair<String?, String?> = when {
    site?.fullName != null -> site?.firstName to site?.lastName
    own != null -> own.split(" ").let { w -> w.first() to w.drop(1).joinToString(" ").ifEmpty { null } }
    else -> null to null
  }
  Row(
      Modifier.fillMaxWidth().glass(shape, fill = 0.04f).padding(horizontal = 36.dp, vertical = 31.dp),
      horizontalArrangement = Arrangement.spacedBy(29.dp), verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
        Modifier.size(156.dp).colouredGlow(Theme.accent, 0.4f, 34.dp, CircleShape, y = 10.dp).clip(CircleShape).background(accentGradient, CircleShape)
            .border(2.6.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      val words = shown?.split(" ")?.filter { it.isNotEmpty() }.orEmpty()
      val initials = listOfNotNull(words.firstOrNull()?.firstOrNull(), if (words.size > 1) words.last().firstOrNull() else null).joinToString("").uppercase()
      if (initials.isNotEmpty()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(initials, style = app(55, FontWeight.ExtraBold), color = Color.White) }
      } else Icon(Icons.person, null, Modifier.size(60.dp), tint = Color.White)
      user?.avatar?.let { RemoteImage(it, Modifier.fillMaxSize(), pixelWidth = 200) }
    }
    Column(Modifier.weight(1f).graphicsLayer { alpha = words }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(shown ?: loc(S.guestAccount), style = app(44, FontWeight.ExtraBold), color = Theme.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        user?.type?.let {
          Text(
              loc.accountType(it),
              Modifier.background(Brush.verticalGradient(listOf(goldLight, gold)), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 3.dp),
              style = app(17, FontWeight.Bold), color = Theme.focusInk,
          )
        }
      }
      // A guest otherwise has no way in from here but the settings.
      if (user == null) Text(loc(S.signInHint), Modifier.widthIn(max = 600.dp), style = app(19), color = Theme.text2)
      // The username, small, under a name given instead of it.
      else if (shown != user.name) user.name?.let { Text(it, style = app(18, FontWeight.Medium), color = Theme.text3, maxLines = 1) }
      Box(Modifier.padding(top = 12.dp)) { Quote(names.first, names.second, settled = asked, write = written, filmFound = filmFound) }
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(23.dp)) {
      Box(Modifier.graphicsLayer { alpha = words }) { Figures(watchlist, favorites, sizes) }
      Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        if (user == null) PillButton(loc(S.signIn), { app.requestSignIn() }, kind = PillKind.Primary, icon = Icons.person)
        // The Mac's renew button; on a TV, the page's code for the phone.
        val renewHint = tv(TV.renewHint)
        if (user != null) PillButton(loc(S.renewSubscription), { app.sheets.link = LinkSpec(loc(S.renewSubscription), renewHint, "https://30nama.com/pricing") })
        if (user != null) PillButton(loc(S.signOut), {
          app.sheets.show(SheetSpec(loc(S.signOutTitle), listOf(SheetAction(loc(S.cancel)) {}, SheetAction(loc(S.signOut), Icons.signOut) { app.signOut() }), loc(S.signOutBody)))
        }, icon = Icons.signOut)
      }
    }
  }
}

/** The counts as figures with a word under each: the lists, what was rated and how, the plan's days. */
@Composable
private fun Figures(watchlist: List<Post>, favorites: List<Post>, sizes: Map<String, Int>) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val r = app.ratings
  Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Top) {
    @Composable fun figure(value: String, label: String, star: Boolean = false) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          if (star) Icon(Icons.star, null, Modifier.size(17.dp), tint = gold)
          Text(value, style = app(34, FontWeight.ExtraBold), color = Theme.text1)
        }
        Text(label, style = app(16, FontWeight.Medium), color = Theme.text3, maxLines = 1)
      }
    }
    if (watchlist.isNotEmpty()) figure(loc.num(sizes["watchlist"] ?: watchlist.size), loc(S.watchlist))
    if (favorites.isNotEmpty()) figure(loc.num(sizes["favorite"] ?: favorites.size), loc(S.favorites))
    if (!r.isEmpty) {
      figure(loc.num(r.count), loc(S.ratedCount))
      r.average?.let { figure(loc.num(Fmt.score(it)), loc(S.averageRating), star = true) }
    }
    app.user?.daysLeft(java.time.Instant.now())?.let { figure(loc.num(it), loc(S.subscriptionDays)) }
  }
}

/**
 * A line from a film, in the film's own English, said to the viewer by first name where a line has
 * one, and under it the film, its year and its director: the Mac's greeting. One is picked each
 * time the app opens. Written left to right inside a Persian page, kept to the name's edge.
 *
 * The last thing on the page to come (the owner): once [write], it is written out by hand, letter
 * by letter behind a soft pen edge, quick inside a word and resting at a space or a stop; then the
 * line under it comes in from the left. Once [settled] (the names known, so the line is the one
 * that stays), its film is looked for on 30nama, as the Mac does, for the page's backdrop.
 */
@Composable
private fun Quote(first: String?, family: String?, settled: Boolean, write: Boolean, filmFound: (Post?) -> Unit) {
  val model = LocalApp.current
  val hue = LocalHue.current ?: Theme.accent
  val key = "$first|$family"
  val line = remember(key) { picked?.takeIf { it.first == key }?.second ?: app.nama.core.MovieLines.pick(first, family).also { picked = key to it } }
  LaunchedEffect(line, settled) {
    if (!settled) return@LaunchedEffect
    val found = line.searchQueries.firstNotNullOfOrNull { query ->
      runCatching { line.match(PostPage.from(model.api.get("search", mapOf("query" to query, "page" to "1"), background = true)).posts) }.getOrNull()
    }
    filmFound(found)
  }
  val calm = LocalCalm.current
  // How far the pen has gone, in letters; then how far the line under it has come in, 0 to 1.
  val pen = remember(line) { androidx.compose.animation.core.Animatable(0f) }
  val under = remember(line) { androidx.compose.animation.core.Animatable(0f) }
  LaunchedEffect(line, write) {
    if (!write) return@LaunchedEffect
    val text = line.text
    if (calm || pen.value >= text.length) {
      pen.snapTo(text.length.toFloat())
      under.snapTo(1f)
      return@LaunchedEffect
    }
    delay(250)
    for (i in pen.value.toInt() until text.length) {
      val c = text[i]
      val ms = when {
        c in ".!?" -> 260
        c in ",;:—–" -> 190
        c == ' ' -> 70
        else -> 42
      }
      pen.animateTo(i + 1f, tween(ms, easing = androidx.compose.animation.core.LinearEasing))
    }
    delay(200)
    under.animateTo(1f, tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing))
  }
  var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
  val serif = androidx.compose.ui.text.font.FontFamily.Serif
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(Modifier.widthIn(max = 806.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Box {
        // Hung at the line's start without taking room of its own; it comes with the first letter.
        Text(
            "“", Modifier.size(0.dp).wrapContentSize(Alignment.TopStart, unbounded = true).offset(x = (-3).dp, y = (-16).dp).graphicsLayer { alpha = pen.value.coerceIn(0f, 1f) },
            style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = app(65).fontSize), color = hue.copy(alpha = 0.75f),
        )
        Text(
            line.text,
            Modifier.padding(start = 34.dp)
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                  drawContent()
                  val l = layout ?: return@drawWithContent
                  hideUnwritten(l, pen.value, line.text.length)
                },
            style = androidx.compose.ui.text.TextStyle(fontFamily = serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = app(25).fontSize, lineHeight = app(25).fontSize * 1.3f),
            color = Theme.text1.copy(alpha = 0.92f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
        )
      }
      Row(
          Modifier.padding(start = 34.dp)
              .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
              .drawWithContent {
                drawContent()
                // In from the left behind a soft edge.
                val edge = 60.dp.toPx()
                val x = (size.width + edge) * under.value - edge
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = x, endX = x + edge),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
                )
              },
          horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(21.dp, 2.6.dp).background(hue, RoundedCornerShape(50)))
        Text(
            "${line.film.uppercase()} · ${line.year} · ${line.directors.joinToString(", ")}",
            style = app(14, FontWeight.SemiBold).copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Theme.text3, maxLines = 1,
        )
      }
    }
  }
}

/**
 * Takes away what the pen has not written yet: the rest of the line it is on, behind a soft edge
 * just past the pen, and every line after it.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.hideUnwritten(layout: androidx.compose.ui.text.TextLayoutResult, pen: Float, length: Int) {
  if (pen >= length) return
  val out = androidx.compose.ui.graphics.BlendMode.DstOut
  val visibleEnd = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
  val at = pen.toInt().coerceIn(0, visibleEnd)
  val row = layout.getLineForOffset(at)
  val from = layout.getHorizontalPosition(at, true)
  val to = if (at + 1 <= layout.getLineEnd(row)) layout.getHorizontalPosition(at + 1, true) else from
  val x = from + (to - from) * (pen - pen.toInt())
  val edge = 14.dp.toPx()
  val top = layout.getLineTop(row)
  val bottom = layout.getLineBottom(row)
  drawRect(
      Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = x, endX = x + edge),
      topLeft = androidx.compose.ui.geometry.Offset(x, top), size = androidx.compose.ui.geometry.Size(size.width - x + edge, bottom - top), blendMode = out,
  )
  if (bottom < size.height) {
    drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(0f, bottom), size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom), blendMode = out)
  }
}

/** The line of this opening of the app, and the first name it was picked for. */
private var picked: Pair<String?, app.nama.core.MovieLine>? = null

/** What the ratings add up to: the genres that do best, and how generous the user is with stars. */
@Composable
private fun Insights() {
  val app = LocalApp.current
  val loc = LocalLoc.current
  Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
    Panel(loc(S.yourTaste), Modifier.weight(1f)) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        app.ratings.favoriteGenreNames().forEach { g ->
          Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(g.name, Modifier.width(190.dp), style = app(24, FontWeight.SemiBold), color = Theme.text1, maxLines = 1)
            BoxWithConstraints(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
              Box(Modifier.width(maxOf(12.dp, maxWidth * g.strength.toFloat())).height(12.dp).background(accentGradient, RoundedCornerShape(50)))
            }
          }
        }
      }
    }
    Panel(loc(S.howYouRate), Modifier.weight(1f)) {
      val spread = app.ratings.spread()
      val most = maxOf(1, spread.maxOrNull() ?: 1)
      Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Bottom) {
        spread.forEachIndexed { i, count ->
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (count > 0) loc.num(count) else " ", style = app(20, FontWeight.SemiBold), color = Theme.text2)
            Box(
                Modifier.size(54.dp, maxOf(8.dp, 110.dp * (count.toFloat() / most)))
                    .background(if (count > 0) Brush.verticalGradient(listOf(goldLight, gold)) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(8.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(loc.num(i + 1), style = app(20, FontWeight.SemiBold), color = Theme.text3)
              Icon(Icons.star, null, Modifier.size(16.dp), tint = Theme.text3)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable () -> Unit) {
  val shape = RoundedCornerShape(Theme.tileRadius + 6.dp)
  Column(modifier.height(300.dp).glass(shape, fill = 0.04f).padding(30.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
    Text(title, style = app(24, FontWeight.SemiBold), color = Theme.text3)
    content()
  }
}

@Composable
private fun Shelf(title: String, posts: List<Post>, stars: Map<Int, Double>? = null, note: ((Post) -> String?)? = null) {
  val nav = LocalNav.current
  val loc = LocalLoc.current
  // A section: a poster in focus brings the shelf's name with it.
  Column(Modifier.wholeSection(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Text(title, style = app(30, FontWeight.Bold), color = Theme.text1)
    LazyRow(Modifier.bleed(14.dp).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(21.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
      items(posts, key = { it.id }) { post ->
        Box {
          PosterCard(post, { nav.push(Route.Details(post)) })
          // When its next episode comes, over the poster's top (the Mac's note).
          note?.invoke(post)?.let { day ->
            Text(
                day, Modifier.align(Alignment.TopStart).padding(10.dp).background(Theme.accent, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 3.dp),
                style = app(16, FontWeight.Bold), color = Color.White, maxLines = 1,
            )
          }
          stars?.get(post.id)?.let { s ->
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.star, null, Modifier.size(16.dp), tint = gold)
              Text(loc.num(Fmt.score(s)), style = app(20, FontWeight.Bold), color = Color.White)
            }
          }
        }
      }
    }
  }
}

/** 30nama's messages for the account, newest first; one opened is read here and on the server. */
@Composable
private fun Notifications(page: NotificationPage, changed: (NotificationPage) -> Unit) {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(loc(S.notifications), style = app(32, FontWeight.Bold), color = Theme.text1)
      if (page.unread > 0) PillButton(loc(S.markAllRead), {
        changed(page.markRead("all"))
        app.background { app.api.call("notification_read", mapOf("notification_id" to "all"), background = true) }
      }, kind = PillKind.Chip)
    }
    page.items.take(12).forEach { n: AccountNotification ->
      TextRow({
        if (!n.read) {
          changed(page.markRead(n.id))
          app.background { app.api.call("notification_read", mapOf("notification_id" to n.id), background = true) }
        }
        n.post?.let { nav.push(Route.Details(it)) }
      }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(12.dp).background(if (n.read) Color.Transparent else Theme.accent, CircleShape))
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            n.title?.let { Text(it, style = app(26, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            n.content?.let { Text(it, style = app(22), maxLines = 2, overflow = TextOverflow.Ellipsis) }
          }
          (if (loc.lang == Lang.fa) n.date.jalali else n.date.gregorian)?.let { Text(loc.num(it), style = app(20), maxLines = 1) }
        }
      }
    }
  }
}

/**
 * In its place from the start, seen once [revealed] reaches [order], rising the last 16 points as
 * it comes: the title page's `comingIn`, so the shelves come in one after another.
 */
@Composable
private fun Modifier.rising(order: Int, revealed: Int): Modifier {
  val calm = LocalCalm.current
  val shown by animateFloatAsState(
      if (revealed >= order) 1f else 0f,
      if (calm) snap() else spring(dampingRatio = 0.82f, stiffness = 224f),
      label = "rising",
  )
  return graphicsLayer {
    alpha = shown
    translationY = (1f - shown) * 16.dp.toPx()
  }
}
