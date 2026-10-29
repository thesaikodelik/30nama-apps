package app.nama.androidtv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nama.core.Person
import app.nama.core.Post

/** Where a card or button leads. */
sealed interface Route {
  /** A title to open. Carries the list entry so the page has something to show while it loads. */
  data class Details(val id: Int, val preview: Post?, val autoplay: Boolean = false) : Route {
    constructor(post: Post, autoplay: Boolean = false) : this(post.id, post, autoplay)
  }

  data class Catalog(val slug: String, val title: String) : Route

  /** An actor's biography and titles, from the 30nama website's own API. */
  data class PersonPage(val person: Person) : Route

  data object Schedule : Route

  data object TopLists : Route

  /** 30nama's advanced search, opened with [query] (nothing chosen, or a title's director). */
  data object News : Route

  /** One of 30nama's articles, whole. */
  data class ArticlePage(val article: app.nama.core.Article) : Route

  data class AdvancedSearch(val query: app.nama.core.AdvancedQuery = app.nama.core.AdvancedQuery()) : Route
}

/** A title's poster on its way from the card that was pressed to its place on the title's page. */
data class Flight(val post: Post, val from: androidx.compose.ui.geometry.Rect)

/** The Mac's order: Search first, then the catalogue, the user's own page, and Settings last. */
@Suppress("EnumEntryName")
enum class Tab { search, home, movies, series, anime, browse, profile, settings }

/** Which tab is open, and what has been opened on top of each: one stack per tab, as on the Apple TV. */
class Nav {
  var tab by mutableStateOf(Tab.home)
  /** The poster flying to a title's page as it opens (Shell's FlyingPoster); its page holds its own back meanwhile. */
  var flight by mutableStateOf<Flight?>(null)
  private val stacks = mutableStateMapOf<Tab, List<Route>>()
  /** Each open page's own number: its saved state (scroll, what was focused) is its alone. */
  private val serials = mutableStateMapOf<Tab, List<Long>>()
  private var next = 0L
  /** Pages closed whose saved state is still to be dropped (the shell drops it once they are gone). */
  val closed = androidx.compose.runtime.mutableStateListOf<String>()

  /** The open page's key for its saved state: a page opened again starts fresh, at its top. */
  val topKey: String? get() = serials[tab]?.lastOrNull()?.let { "$tab/$it" }

  fun stack(tab: Tab): List<Route> = stacks[tab].orEmpty()

  val top: Route? get() = stack(tab).lastOrNull()

  fun push(route: Route) {
    if (top == route) return
    stacks[tab] = stack(tab) + route
    serials[tab] = serials[tab].orEmpty() + next++
  }

  /** One step back on the open tab; false when it was already at its root. */
  fun pop(): Boolean {
    val s = stack(tab)
    if (s.isEmpty()) return false
    stacks[tab] = s.dropLast(1)
    serials[tab]?.lastOrNull()?.let { closed += "$tab/$it" }
    serials[tab] = serials[tab].orEmpty().dropLast(1)
    return true
  }

  /** A new language or account means a different catalogue: every tab starts over. */
  fun reset() {
    for ((t, s) in serials) s.forEach { closed += "$t/$it" }
    stacks.clear()
    serials.clear()
    tab = Tab.home
  }
}
