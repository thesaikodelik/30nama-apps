package app.nama.quest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nama.core.Fmt
import app.nama.core.HomeFeed
import app.nama.core.Loc
import app.nama.core.MovieLine
import app.nama.core.MovieLines
import app.nama.core.NotificationPage
import app.nama.core.Post
import app.nama.core.PostPage
import app.nama.core.S
import app.nama.core.SiteProfile
import app.nama.core.SiteQuery
import app.nama.core.markNotificationRead
import app.nama.core.notifications
import app.nama.core.search
import app.nama.core.userList
import app.nama.quest.AppModel
import app.nama.quest.Library
import app.nama.quest.Services
import app.nama.quest.ui.Icon
import app.nama.quest.ui.Ink
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.Pill
import app.nama.quest.ui.PostCard
import app.nama.quest.ui.PosterWidth
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fadeBottom
import app.nama.quest.ui.screens.people.Figure
import app.nama.quest.ui.screens.people.Gutter
import app.nama.quest.ui.screens.people.Insights
import app.nama.quest.ui.screens.people.NoRatings
import app.nama.quest.ui.screens.people.NotificationsPanel
import app.nama.quest.ui.screens.people.ProfileCard
import app.nama.quest.ui.screens.people.Quote
import app.nama.quest.ui.screens.people.Shelf
import app.nama.quest.ui.screens.people.Wash
import app.nama.quest.ui.screens.people.attempt
import app.nama.quest.ui.screens.people.openInBrowser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The user's own page (ProfileView.swift): who they are, what their stars say about their taste,
 * the lists they keep on 30nama, and what all that suggests they watch next. Ratings live on the
 * headset only; there is no iCloud here.
 */
@Composable
fun ProfileScreen(model: AppModel) {
  NeedsService(model) { services -> ProfilePage(model, services) }
}

/** 30nama's own page for buying or renewing a plan. */
private const val PRICING = "https://30nama.com/pricing"

/**
 * The line from a film of this run of the app, the first name it was picked for, and the film on
 * 30nama: picked once per launch, and again only when a name arrives later (as on the Mac).
 */
private object LineMemory {
  var name: String? = null
  var line: MovieLine? = null
  var film: Post? = null
  var searched = false
}

@Composable
private fun ProfilePage(model: AppModel, services: Services) {
  val lang by model.lang.collectAsState()
  val loc = Loc(lang)
  val api = services.api
  val user = services.user
  val lists by model.library.lists.collectAsState()
  val ratingsVersion by model.library.ratingsVersion.collectAsState()
  val book = model.library.ratings
  val typedName by model.settings.displayName.state.collectAsState()
  val scope = rememberCoroutineScope()

  // The name the member gave the website: the app API sends only the username.
  var siteProfile by remember { mutableStateOf<SiteProfile?>(null) }
  var profileAsked by remember { mutableStateOf(false) }
  LaunchedEffect(user?.name) {
    siteProfile = null
    profileAsked = false
    try {
      val username = user?.name
      if (username != null && api.hasSite) {
        siteProfile = attempt { SiteProfile.fromSite(api.site(SiteQuery.profile(username), background = true)) }.getOrNull()
      }
    } finally {
      profileAsked = true
    }
  }

  // The name written in Settings, for a private profile; never for a guest.
  val ownName = typedName.trim().takeIf { it.isNotEmpty() && user != null }
  val displayName = siteProfile?.fullName ?: ownName ?: user?.name
  val names: Pair<String?, String?> =
      when {
        siteProfile?.fullName != null -> siteProfile?.firstName to siteProfile?.lastName
        ownName != null -> ownName.split(' ').filter { it.isNotBlank() }.let { w -> w.firstOrNull() to w.drop(1).joinToString(" ").ifEmpty { null } }
        else -> null to null
      }

  // The line from a film, said to the first name (never the username).
  var line by remember { mutableStateOf(LineMemory.line) }
  var film by remember { mutableStateOf(LineMemory.film) }
  LaunchedEffect(profileAsked, names.first) {
    if (!profileAsked) return@LaunchedEffect
    if (LineMemory.line != null && LineMemory.name == names.first) {
      line = LineMemory.line
      film = LineMemory.film
      if (LineMemory.searched) return@LaunchedEffect
    } else {
      val picked = MovieLines.pick(names.first, names.second)
      LineMemory.name = names.first
      LineMemory.line = picked
      LineMemory.film = null
      LineMemory.searched = false
      line = picked
      film = null
    }
    // Quietly, after the page's own requests: whether 30nama has the film.
    val picked = LineMemory.line ?: return@LaunchedEffect
    for (q in picked.searchQueries) {
      val found = attempt { api.search(q, streamOnly = false, page = 1, background = true) }.getOrNull() ?: continue
      val match = picked.match(found.posts) ?: continue
      LineMemory.film = match
      film = match
      break
    }
    LineMemory.searched = true
  }

  // The lists kept on 30nama, again whenever one changes anywhere in the app. A guest has none.
  var watchlist by remember { mutableStateOf<List<Post>>(emptyList()) }
  var favorites by remember { mutableStateOf<List<Post>>(emptyList()) }
  var upcoming by remember { mutableStateOf<List<Post>>(emptyList()) }
  LaunchedEffect(user?.id, lists) {
    if (user == null) {
      watchlist = emptyList()
      favorites = emptyList()
      upcoming = emptyList()
      return@LaunchedEffect
    }
    coroutineScope {
      val w = async { attempt { api.userList(Library.WATCHLIST).posts }.getOrNull() }
      val f = async { attempt { api.userList(Library.FAVORITE).posts }.getOrNull() }
      w.await()?.let { watchlist = it }
      f.await()?.let { favorites = it }
    }
    upcomingOf(services)?.let { upcoming = it }
  }

  // The home feed is what "For you" and "Start with these" are picked from.
  var pool by remember { mutableStateOf<List<Post>>(emptyList()) }
  LaunchedEffect(lang) {
    attempt { HomeFeed.from(api.get("home_stream", background = true)) }.getOrNull()?.let { feed ->
      val seen = HashSet<Int>()
      pool = feed.sections.flatMap { it.posts }.filter { seen.add(it.id) }
    }
  }
  val rated = remember(ratingsVersion) { book.newestFirst.mapNotNull { r -> r.post?.let { it to r.stars } } }
  val picks = remember(pool, ratingsVersion) { if (book.isEmpty) emptyList() else book.recommend(pool, 16) }
  val starters =
      remember(pool, ratingsVersion) {
        if (!book.isEmpty) emptyList() else pool.filter { !it.comingSoon && it.imdbScore != null }.sortedByDescending { it.imdbScore }.take(14)
      }

  // The account's messages: the button shows only when there are any.
  var notes by remember { mutableStateOf<NotificationPage?>(null) }
  var showingNotes by remember { mutableStateOf(false) }
  LaunchedEffect(user?.id) { notes = if (user == null) null else attempt { api.notifications(1) }.getOrNull() }
  fun markRead(id: String) {
    notes = notes?.markRead(id)
    scope.launch { attempt { api.markNotificationRead(id) } }
  }

  var confirmingSignOut by remember { mutableStateOf(false) }

  val figures = buildList {
    val w = lists[Library.WATCHLIST]?.size ?: watchlist.size
    val f = lists[Library.FAVORITE]?.size ?: favorites.size
    if (w > 0) add(Figure(loc.num(w), loc(S.watchlist)))
    if (f > 0) add(Figure(loc.num(f), loc(S.favorites)))
    if (!book.isEmpty) {
      add(Figure(loc.num(book.count), loc(S.ratedCount)))
      book.average?.let { add(Figure(loc.num(Fmt.score(it)), loc(S.averageRating), star = true)) }
    }
    user?.daysLeft()?.let { add(Figure(loc.num(it), loc(S.subscriptionDays))) }
  }

  // Something the user chose washes the page; else something they rated.
  val backdrop = favorites.firstOrNull() ?: watchlist.firstOrNull() ?: rated.firstOrNull()?.first

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns = ((maxWidth - Gutter * 2 + 18.dp) / (PosterWidth + 18.dp)).toInt().coerceAtLeast(1)
    Wash((backdrop?.cover ?: backdrop?.poster?.url(160.0))?.toString())
    LazyColumn(
        Modifier.fillMaxSize().fadeBottom(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp),
    ) {
      item(key = "card") {
        Box(Modifier.padding(horizontal = Gutter)) {
          ProfileCard(
              name = displayName ?: loc(S.guestAccount),
              under =
                  when {
                    user == null -> loc(S.signInHint)
                    displayName != user.name -> user.name
                    else -> null
                  },
              plan = user?.type?.let { loc.accountType(it) },
              avatarUrl = user?.avatar?.toString(),
              initials = initials(if (user == null) null else displayName),
              figures = figures,
              // The Mac shows the plan's last day under the pointer; here it is simply written.
              figuresNote = user?.let { loc.subscriptionLine(it) },
              quote = { line?.let { Quote(it, film) { p -> model.openPost(p) } } },
              actions = {
                when {
                  user == null -> Pill(loc(S.signIn), { model.requestSignIn() }, primary = true, icon = Icon.Person)
                  confirmingSignOut ->
                      SignOutConfirm(loc, onCancel = { confirmingSignOut = false }, onConfirm = {
                        confirmingSignOut = false
                        model.signOut()
                      })
                  else ->
                      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (notes?.items?.isNotEmpty() == true) {
                          Pill(loc(S.notifications), { showingNotes = !showingNotes }, icon = Icon.Bell)
                        }
                        Pill(loc(S.renewSubscription), { openInBrowser(model, services, PRICING) })
                        Pill(loc(S.signOut), { confirmingSignOut = true })
                      }
                }
              },
          )
        }
      }
      val page = notes
      if (showingNotes && page != null) {
        item(key = "notifications") {
          Box(Modifier.padding(horizontal = Gutter)) {
            NotificationsPanel(
                loc,
                page,
                onMarkAll = { markRead("all") },
                onClose = { showingNotes = false },
                onChoose = { n ->
                  if (!n.read) markRead(n.id)
                  val post = n.post
                  val link = n.link
                  when {
                    post != null -> model.openPost(post)
                    link != null -> openInBrowser(model, services, link.toString())
                  }
                },
            )
          }
        }
      }
      item(key = "taste") {
        Box(Modifier.padding(horizontal = Gutter)) {
          if (book.isEmpty) NoRatings(loc) else Insights(loc, remember(ratingsVersion) { book.favoriteGenreNames() }, remember(ratingsVersion) { book.spread() })
        }
      }
      if (upcoming.isNotEmpty()) item(key = "upcoming") { Shelf(model, loc(S.upcoming), upcoming, note = { it.nextEpisodeLabel(lang) }) }
      if (picks.isNotEmpty()) item(key = "picks") { Shelf(model, loc(S.forYou), picks) }
      if (watchlist.isNotEmpty()) item(key = "watchlist") { Shelf(model, loc(S.watchlist), watchlist) }
      if (favorites.isNotEmpty()) item(key = "favorites") { Shelf(model, loc(S.favorites), favorites) }
      if (book.isEmpty) {
        if (starters.isNotEmpty()) item(key = "starters") { Shelf(model, loc(S.startWithThese), starters) }
      } else {
        item(key = "ratedTitle") { Text(loc(S.yourRating), style = WallType.heading, modifier = Modifier.padding(horizontal = Gutter)) }
        items(rated.chunked(columns), key = { row -> "rated-" + row.first().first.id }) { row ->
          Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            row.forEach { (post, stars) -> PostCard(model, post, badge = "★ " + loc.num(Fmt.score(stars))) }
          }
        }
      }
    }
  }
}

/** The Mac's "Sign out?" alert, in place of the buttons: a panel can't open an alert over itself. */
@Composable
private fun SignOutConfirm(loc: Loc, onCancel: () -> Unit, onConfirm: () -> Unit) {
  Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(loc(S.signOutTitle), style = WallType.label.copy(fontSize = 22.sp))
    Text(loc(S.signOutBody), style = WallType.caption.copy(color = Ink.text2), textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 480.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Pill(loc(S.cancel), onCancel)
      Pill(loc(S.signOut), onConfirm, primary = true)
    }
  }
}

/** Initials of a name, or null for a guest (who gets a person instead). */
private fun initials(name: String?): String? {
  val words = name?.split(' ')?.filter { it.isNotBlank() } ?: return null
  val letters = listOfNotNull(words.firstOrNull()?.firstOrNull(), if (words.size > 1) words.last().firstOrNull() else null)
  return if (letters.isEmpty()) null else letters.joinToString("").uppercase()
}

/**
 * Series the user follows that have an episode on the way, soonest first: a few pages of the
 * watchlist and the "tell me" list, quietly. Null when a list couldn't be read.
 */
private suspend fun upcomingOf(services: Services): List<Post>? {
  val posts = mutableListOf<Post>()
  for (group in listOf(Library.WATCHLIST, "notify")) {
    var page = 1
    var pages = 1
    // A handful of pages is every list a person keeps; more would only hammer the server.
    while (page <= pages && page <= 4) {
      val next: PostPage = attempt { services.api.userList(group, page, background = true) }.getOrNull() ?: return null
      posts += next.posts
      pages = next.pages
      page++
    }
  }
  return Post.upcoming(posts)
}
