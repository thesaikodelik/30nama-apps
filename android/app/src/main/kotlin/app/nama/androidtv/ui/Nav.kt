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
}

/** Search first, as most apps have it; the user's own page last, by settings. */
@Suppress("EnumEntryName")
enum class Tab { search, home, movies, series, anime, browse, profile, settings }

/** Which tab is open, and what has been opened on top of each: one stack per tab, as on the Apple TV. */
class Nav {
  var tab by mutableStateOf(Tab.home)
  private val stacks = mutableStateMapOf<Tab, List<Route>>()

  fun stack(tab: Tab): List<Route> = stacks[tab].orEmpty()

  val top: Route? get() = stack(tab).lastOrNull()

  fun push(route: Route) {
    if (top == route) return
    stacks[tab] = stack(tab) + route
  }

  /** One step back on the open tab; false when it was already at its root. */
  fun pop(): Boolean {
    val s = stack(tab)
    if (s.isEmpty()) return false
    stacks[tab] = s.dropLast(1)
    return true
  }

  /** A new language or account means a different catalogue: every tab starts over. */
  fun reset() {
    stacks.clear()
    tab = Tab.home
  }
}
