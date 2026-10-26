package app.nama.androidtv.ui

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
  var sizes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
  var notes by remember { mutableStateOf<NotificationPage?>(null) }
  val ratings = app.ratings

  LaunchedEffect(ratings) {
    val feed = attempt { app.home() } ?: return@LaunchedEffect
    val pool = feed.sections.flatMap { it.posts }
    if (ratings.isEmpty) {
      // Nothing to go on yet: the best-regarded titles to kick things off.
      starters = pool.filter { !it.comingSoon && it.imdbScore != null }.sortedByDescending { it.imdbScore ?: 0.0 }.distinctBy { it.id }.take(12)
    } else picks = ratings.recommend(pool, 14)
  }
  LaunchedEffect(app.user?.id) {
    if (!app.isLoggedIn) return@LaunchedEffect
    fun list(group: String) = mapOf("action" to "list", "group" to group, "page" to "1", "orderby" to "add_list", "order" to "DESC")
    watchlist = attempt { PostPage.from(app.api.get("user_list", list("watchlist"))).posts }.orEmpty()
    favorites = attempt { PostPage.from(app.api.get("user_list", list("favorite"))).posts }.orEmpty()
    sizes = attempt { (app.api.get("user_mylist").obj ?: emptyMap()).mapValues { (_, v) -> v["post"].array.size } }.orEmpty()
    notes = attempt { NotificationPage.from(app.api.get("notification", mapOf("page" to "1"), background = true)) }
  }

  Box(Modifier.fillMaxSize()) {
    Ambient {}
    Stage(favorites.firstOrNull() ?: watchlist.firstOrNull() ?: ratings.newestFirst.firstOrNull()?.post, dim = 0.5f, glow = false)
    LazyColumn(
        Modifier.fillMaxSize().focusRestorer(),
        contentPadding = PaddingValues(start = Theme.edge, end = Theme.edge, top = 170.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
    ) {
      item { Header(watchlist, favorites, sizes) }
      if (!ratings.isEmpty) item { Insights() }
      if (picks.isNotEmpty()) item { Shelf(loc(S.forYou), picks) }
      if (watchlist.isNotEmpty()) item { Shelf(loc(S.watchlist), watchlist) }
      if (favorites.isNotEmpty()) item { Shelf(loc(S.favorites), favorites) }
      if (ratings.isEmpty) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(loc(S.noRatingsYet), style = app(34, FontWeight.Bold), color = Theme.text1)
            Text(loc(S.rateHint), style = app(26), color = Theme.text2)
          }
        }
        if (starters.isNotEmpty()) item { Shelf(loc(S.startWithThese), starters) }
      } else {
        item { Shelf(loc(S.yourRating), ratings.newestFirst.mapNotNull { it.post }, stars = ratings.newestFirst.associate { it.postID to it.stars }) }
      }
      notes?.takeIf { it.items.isNotEmpty() }?.let { page -> item { Notifications(page) { notes = it } } }
      if (!app.isLoggedIn) item { PillButton(loc(S.signIn), { app.requestSignIn() }, kind = PillKind.Primary) }
    }
  }
}

@Composable
private fun Header(watchlist: List<Post>, favorites: List<Post>, sizes: Map<String, Int>) {
  val app = LocalApp.current
  val loc = LocalLoc.current
  val user = app.user
  Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(
        Modifier.size(180.dp).clip(CircleShape).background(accentGradient, CircleShape).border(3.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      val words = user?.name?.split(" ")?.filter { it.isNotEmpty() }.orEmpty()
      val initials = listOfNotNull(words.firstOrNull()?.firstOrNull(), if (words.size > 1) words.last().firstOrNull() else null).joinToString("").uppercase()
      if (initials.isNotEmpty()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Text(initials, style = app(64, FontWeight.ExtraBold), color = Color.White) }
      } else Icon(Icons.person, null, Modifier.size(90.dp), tint = Color.White)
      user?.avatar?.let { RemoteImage(it, Modifier.fillMaxSize(), pixelWidth = 240) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(user?.name ?: loc(S.guestAccount), style = app(60, FontWeight.ExtraBold), color = Theme.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        user?.type?.let {
          Text(
              loc.accountType(it),
              Modifier.background(Brush.verticalGradient(listOf(goldLight, gold)), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 4.dp),
              style = app(22, FontWeight.Bold), color = Theme.focusInk,
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val r = app.ratings
        if (!r.isEmpty) {
          Stat(loc.num(r.count), loc(S.ratedCount))
          r.average?.let { Stat(loc.num(Fmt.score(it)), loc(S.averageRating), star = true) }
        }
        if (watchlist.isNotEmpty()) Stat(loc.num(sizes["watchlist"] ?: watchlist.size), loc(S.watchlist))
        if (favorites.isNotEmpty()) Stat(loc.num(sizes["favorite"] ?: favorites.size), loc(S.favorites))
      }
      // For the user's own eyes, and not the point of the page: kept small and quiet.
      user?.let { loc.subscriptionLine(it) }?.let { Text(it, style = app(21), color = Theme.text3) }
    }
  }
}

@Composable
private fun Stat(value: String, label: String, star: Boolean = false) {
  val shape = RoundedCornerShape(Theme.tileRadius)
  Column(
      Modifier.widthIn(min = 150.dp).heightIn(min = 96.dp).background(Theme.glass, shape).border(1.dp, Theme.glassEdge, shape).padding(horizontal = 26.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      if (star) Icon(Icons.star, null, Modifier.size(24.dp), tint = gold)
      Text(value, style = app(36, FontWeight.ExtraBold), color = Theme.text1)
    }
    Text(label, style = app(20, FontWeight.Medium), color = Theme.text3)
  }
}

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
  Column(modifier.height(300.dp).background(Theme.glass, shape).border(1.dp, Theme.glassEdge, shape).padding(30.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
    Text(title, style = app(24, FontWeight.SemiBold), color = Theme.text3)
    content()
  }
}

@Composable
private fun Shelf(title: String, posts: List<Post>, stars: Map<Int, Double>? = null) {
  val nav = LocalNav.current
  val loc = LocalLoc.current
  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Text(title, style = app(32, FontWeight.Bold), color = Theme.text1)
    LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
      items(posts, key = { it.id }) { post ->
        Box {
          PosterCard(post, { nav.push(Route.Details(post)) })
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
