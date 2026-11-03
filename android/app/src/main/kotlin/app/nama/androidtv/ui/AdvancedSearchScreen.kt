package app.nama.androidtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import app.nama.androidtv.attempt
import app.nama.core.AdvancedQuery
import app.nama.core.FilterOptions
import app.nama.core.Fmt
import app.nama.core.Post
import app.nama.core.PostPage
import app.nama.core.S
import app.nama.core.SearchChoices
import kotlinx.coroutines.launch

/**
 * The Mac's advanced search: 30nama's catalogue narrowed by everything its advanced search acts on,
 * several genres at once, type, country, language, network, lowest score, age rating, years,
 * x265, 3D and streaming only. Each filter is a chip that opens its choices, as the catalogue's.
 * A director's and an actor's name, in English as the site writes them, are typed with the app's
 * own keyboard, as on Windows (a user on Twitter, 30 October 2026); a title's page leads here too.
 * Laid out as Windows: the title alone at the top; below it two lines, each with both ends level:
 * the filter menus and the two names, then the years and switches and the orders.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchScreen(start: AdvancedQuery) {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  var choices by remember { mutableStateOf<SearchChoices?>(null) }
  var query by remember { mutableStateOf(start) }
  var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
  var page by remember { mutableIntStateOf(0) }
  var pages by remember { mutableIntStateOf(1) }
  var total by remember { mutableStateOf<Int?>(null) }
  // Loading from the first frame, so the page opens on stand-ins rather than on "nothing found".
  var loading by remember { mutableStateOf(true) }
  var failed by remember { mutableStateOf(false) }
  var generation by remember { mutableIntStateOf(0) }
  val scope = rememberCoroutineScope()
  // Which name is being typed: "director", "actor" or none.
  var naming by remember { mutableStateOf<String?>(null) }
  val chrome = LocalChrome.current
  DisposableEffect(naming) {
    chrome.modal = naming != null
    onDispose { chrome.modal = false }
  }

  fun loadNext() {
    if (loading || page >= pages) return
    loading = true
    failed = false
    val asked = generation
    val q = query
    scope.launch {
      val next = attempt { PostPage.from(app.api.get("advanced_search", q.request(page + 1))) }
      // A newer query restarted the list; this answer belongs to the old one.
      if (asked != generation) return@launch
      loading = false
      if (next == null) {
        failed = true
        return@launch
      }
      val known = posts.map { it.id }.toSet()
      // Repeats within a page too: the site's lists have them, and a repeated key crashes a lazy grid.
      posts = (posts + next.posts.filter { it.id !in known }).distinctBy { it.id }
      page = next.page
      pages = next.pages
      total = next.total
    }
  }

  LaunchedEffect(loc.lang) { choices = attempt { SearchChoices.from(app.api.get("advanced_search_parameters")) } }
  LaunchedEffect(query) {
    generation++
    posts = emptyList()
    page = 0
    pages = 1
    total = null
    loading = false
    loadNext()
  }

  val all = loc(S.allChoices)
  fun choose(title: String, list: List<FilterOptions.Choice>, current: String?, set: (String?) -> Unit) {
    val actions = listOf(SheetAction(all, selected = current == null) { set(null) }) +
        list.map { c -> SheetAction(c.name, selected = c.value == current) { set(c.value) } }
    app.sheets.show(SheetSpec(title, actions, focus = (list.indexOfFirst { it.value == current } + 1).coerceAtLeast(0)))
  }
  // Several genres at once: each press ticks or unticks one and the list stays open on it.
  fun chooseGenres(list: List<FilterOptions.Choice>, at: Int = 0) {
    val picked = query.genres
    val actions = listOf(SheetAction(all, selected = picked.isEmpty()) { query = query.copy(genres = emptyList()) }) +
        list.mapIndexed { i, c ->
          SheetAction(c.name, selected = c.value in picked) {
            query = query.copy(genres = if (c.value in picked) picked - c.value else picked + c.value)
            chooseGenres(list, i + 1)
          }
        }
    app.sheets.show(SheetSpec(loc(S.genres), actions, focus = at))
  }

  Ambient {
    if (failed && posts.isEmpty()) {
      ErrorState { loadNext() }
    } else {
      PagedGrid(posts, loading, page >= pages, ::loadNext, failed = failed, header = {
        item(span = { GridItemSpan(maxLineSpan) }) {
          Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
              Text(loc(S.advancedSearch), style = app(44, FontWeight.Bold), color = Theme.text1, maxLines = 1)
              total?.let { Text("${Fmt.count(it, loc.lang)} ${loc(S.results)}", Modifier.padding(bottom = 8.dp), style = app(20, FontWeight.Medium), color = Theme.text3) }
            }
            Text(loc(S.advancedHint), style = app(20), color = Theme.text3)
            val menu = Icons.chevronDown
            choices?.let { c ->
              @Composable
              fun pick(title: S, list: List<FilterOptions.Choice>, current: String?, set: (String?) -> Unit) {
                val name = list.firstOrNull { it.value == current }?.name
                PillButton(name?.let { "${loc(title)}: $it" } ?: loc(title), { choose(loc(title), list, current, set) }, kind = PillKind.Chip, selected = current != null, trailing = menu)
              }
              val years = c.years.reversed().map { FilterOptions.Choice(loc.num(it.toString()), it.toString()) }
              // First line: the menus at the start, the two names level with them at the end.
              Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(
                    Modifier.weight(1f).focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  val genres = c.genres.filter { it.value in query.genres }
                  PillButton(
                      when (genres.size) {
                        0 -> loc(S.genres)
                        1 -> "${loc(S.genres)}: ${genres[0].name}"
                        else -> "${loc(S.genres)}: ${loc.num(genres.size)}"
                      },
                      { chooseGenres(c.genres) }, kind = PillKind.Chip, selected = genres.isNotEmpty(), trailing = menu,
                  )
                  pick(S.kind, c.types, query.type) { query = query.copy(type = it) }
                  pick(S.country, c.countries, query.country) { query = query.copy(country = it) }
                  pick(S.originalLanguage, c.languages, query.language) { query = query.copy(language = it) }
                  pick(S.network, c.networks, query.network) { query = query.copy(network = it) }
                  pick(S.minScore, c.scores, query.score) { query = query.copy(score = it) }
                  pick(S.ageRating, c.ages, query.age) { query = query.copy(age = it) }
                }
                // Short names, "Director" and "Actor": the long ones pushed a menu onto a line of its own.
                Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  NameField(loc(S.director), query.director) { naming = "director" }
                  NameField(tv(TV.actor), query.actor) { naming = "actor" }
                }
              }
              // Second line: the years and switches at the start, the orders at the end.
              Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(
                    Modifier.weight(1f).focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  pick(S.years, years, query.fromYear?.toString()) { query = query.copy(fromYear = it?.toIntOrNull()) }
                  pick(S.hoursTo, years, query.toYear?.toString()) { query = query.copy(toYear = it?.toIntOrNull()) }
                  PillButton("x265", { query = query.copy(x265 = !query.x265) }, kind = PillKind.Chip, selected = query.x265)
                  PillButton("3D", { query = query.copy(threeD = !query.threeD) }, kind = PillKind.Chip, selected = query.threeD)
                  PillButton(loc(S.streamOnly), { query = query.copy(streamOnly = !query.streamOnly) }, kind = PillKind.Chip, selected = query.streamOnly)
                  if (query.count > 0) {
                    PillButton(loc(S.reset), { query = AdvancedQuery(streamOnly = query.streamOnly, sort = query.sort) }, kind = PillKind.Chip, icon = Icons.close)
                  }
                }
                Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  AdvancedQuery.sorts.forEach { s -> PillButton(sortName(s), { query = query.copy(sort = s) }, kind = PillKind.Chip, selected = query.sort == s) }
                }
              }
            }
            if (!loading && posts.isEmpty() && total != null) Text(loc(S.noMatches), style = app(28), color = Theme.text2)
          }
        }
      }) { nav.push(Route.Details(it)) }
    }
  }
  naming?.let { which ->
    val director = which == "director"
    NameEntryCard(loc(if (director) S.directorName else S.actorName), (if (director) query.director else query.actor).orEmpty(), close = { naming = null }) { name ->
      val value = name.trim().ifEmpty { null }
      query = if (director) query.copy(director = value) else query.copy(actor = value)
      naming = null
    }
  }
}

/**
 * A name field as Windows': left to right in every language (the names are English), a person
 * at its start, the name or what it is for, at the height of the filter menus beside it. Always
 * as wide as its empty self, a longer name cut short: wider, it pushed a menu onto a line of its own.
 */
@Composable
private fun NameField(label: String, value: String?, open: () -> Unit) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box {
      // The empty field, unseen and out of focus's way, gives the width.
      PillButton(label, {}, Modifier.focusProperties { canFocus = false }.alpha(0f), kind = PillKind.Chip, icon = Icons.person)
      // A name given, the person's room is the name's: red already says the field is set.
      PillButton(value ?: label, open, Modifier.matchParentSize(), kind = PillKind.Chip, selected = value != null, icon = if (value == null) Icons.person else null, fill = true)
    }
  }
}

/** A name typed with the app's own keyboard, English letters and digits; Search sets it, Reset takes it off. */
@Composable
private fun NameEntryCard(title: String, start: String, close: () -> Unit, apply: (String) -> Unit) {
  val loc = LocalLoc.current
  var text by remember { mutableStateOf(start) }
  var digits by remember { mutableStateOf(false) }
  val first = remember { FocusRequester() }
  BackHandler(onBack = close)
  LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
  val shape = RoundedCornerShape(36.dp)
  Box(Modifier.fillMaxSize().zIndex(30f).background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
    Column(Modifier.width(1600.dp).background(Theme.surface, shape).border(1.dp, Theme.glassEdge, shape).padding(56.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
      Text(title, style = app(40, FontWeight.Bold), color = Theme.text1)
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val field = RoundedCornerShape(50)
        Box(
            Modifier.fillMaxWidth().height(76.dp).background(Color.White.copy(alpha = 0.07f), field).border(1.dp, Color.White.copy(alpha = 0.08f), field).padding(horizontal = 30.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
          Text(text.ifEmpty { title }, style = app(28, FontWeight.Medium), color = if (text.isEmpty()) Theme.text3 else Theme.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Keyboard(
          persian = false, digits = digits, first = first,
          type = { text += it }, delete = { text = text.dropLast(1) }, clear = { text = "" },
          switchLanguage = {}, switchDigits = { digits = !digits }, voice = null, languages = false,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PillButton(loc(S.search), { apply(text) }, kind = PillKind.Primary, icon = Icons.search)
        if (start.isNotEmpty()) PillButton(loc(S.reset), { apply("") }, icon = Icons.close)
      }
    }
  }
}

@Composable
private fun sortName(sort: String): String {
  val loc = LocalLoc.current
  return when (sort) {
    "update" -> loc(S.updated)
    "year" -> loc(S.byYear)
    "imdb-rate" -> "IMDb"
    "favorite" -> loc(S.popular)
    else -> loc(S.relevance)
  }
}
