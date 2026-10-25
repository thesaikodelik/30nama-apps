package app.nama.quest

import android.content.Context
import android.content.SharedPreferences
import app.nama.core.APIClient
import app.nama.core.AdvancedQuery
import app.nama.core.Article
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.Person
import app.nama.core.S
import app.nama.core.User

/**
 * The sections of the bar over the poster wall, in the Mac's order (MacShell.swift): search
 * first, the user's own page and settings last. Downloads are left out on the headset for now.
 */
enum class Section(val title: S, val iconOnly: Boolean = false) {
  search(S.search, iconOnly = true),
  home(S.home),
  movies(S.movies),
  series(S.series),
  anime(S.anime),
  browse(S.browse),
  profile(S.profile),
  settings(S.settings, iconOnly = true),
}

/** Something opened on top of a section, inside the wall; a title opens in front of the screen instead. */
sealed interface Route {
  data class Catalog(val slug: String, val title: String) : Route

  /** Set up beforehand when a name on a title page was chosen. */
  data class AdvancedSearch(val query: AdvancedQuery = AdvancedQuery()) : Route

  data class PersonPage(val person: Person) : Route

  data object Schedule : Route

  data object TopLists : Route

  data object News : Route

  data class ArticlePage(val article: Article) : Route
}

/**
 * What the screens on the wall reach 30nama through: the same client the catalogue uses, the
 * signed-in user, and the app's language. Each screen keeps its own state and talks to app.nama.core
 * directly, so screens don't depend on each other.
 */
class Services(
    val context: Context,
    val api: APIClient,
    val prefs: SharedPreferences,
    /** Read with collectAsState in a screen, so it changes when the session is restored or ends. */
    val userState: kotlinx.coroutines.flow.StateFlow<User?>,
    private val langOf: () -> Lang,
) {
  val user: User?
    get() = userState.value

  val signedIn: Boolean
    get() = userState.value != null

  val lang: Lang
    get() = langOf()

  val loc: Loc
    get() = Loc(langOf())
}
