package app.nama.quest.ui.screens.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nama.core.APIClient
import app.nama.core.HomeFeed
import app.nama.core.Lang
import app.nama.core.Menu
import app.nama.core.Post
import app.nama.core.FilterOptions
import app.nama.core.SearchChoices
import app.nama.core.filterOptions
import app.nama.core.home
import app.nama.core.menu
import app.nama.core.searchChoices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One page of a wall: [total] is null where the source gives pages but no count (the site's newest). */
class Page(val posts: List<Post>, val page: Int, val pages: Int, val total: Int?)

/**
 * A wall of posters filled a page at a time, as the Mac's catalogue and advanced search fill
 * theirs. [restart] drops what is there and cancels the request on its way, so an answer for an
 * order or a filter the user has since left never lands on the new list.
 */
class Paged(private val scope: CoroutineScope) {
  var posts by mutableStateOf<List<Post>>(emptyList())
    private set

  var total by mutableStateOf<Int?>(null)
    private set

  var loading by mutableStateOf(false)
    private set

  var failed by mutableStateOf(false)
    private set

  /** At least one page has answered since the last restart, so an empty wall means "nothing". */
  var answered by mutableStateOf(false)
    private set

  /** Bumped on every restart; the grid goes back to the top on it. */
  var generation by mutableIntStateOf(0)
    private set

  var page by mutableIntStateOf(0)
    private set

  private var pages = 1
  private var fetch: (suspend (Int) -> Page)? = null
  private var job: Job? = null

  val canLoadMore: Boolean
    get() = page < pages && !failed

  fun restart(fetch: suspend (Int) -> Page) {
    job?.cancel()
    generation++
    posts = emptyList()
    page = 0
    pages = 1
    total = null
    loading = false
    failed = false
    answered = false
    this.fetch = fetch
    loadNext()
  }

  /** Asks for the next page, unless one is on its way or there is none. Also the Retry after a failure. */
  fun loadNext() {
    val fetch = fetch ?: return
    if (loading || page >= pages) return
    val asked = generation
    loading = true
    failed = false
    job = scope.launch {
      try {
        val next = fetch(page + 1)
        if (asked != generation) return@launch
        val known = posts.mapTo(HashSet()) { it.id }
        posts = posts + next.posts.filter { known.add(it.id) }
        // A server that keeps answering with the same page number must not stall the wall, and an
        // empty page is the end whatever the page count says.
        page = maxOf(next.page, page + 1)
        pages = if (next.posts.isEmpty()) page else next.pages
        total = next.total
        answered = true
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        if (asked == generation) failed = true
      } finally {
        if (asked == generation) loading = false
      }
    }
  }
}

/**
 * What the screens ask for once per language and keep, as the Mac's AppModel caches them: the
 * menu, the filter choices and the feed behind "Popular now". All on the main thread.
 */
object Memo {
  private val menus = HashMap<Lang, Menu>()
  private val filters = HashMap<Lang, FilterOptions>()
  private val choices = HashMap<Lang, SearchChoices>()
  private val feeds = HashMap<Lang, HomeFeed>()

  /** What was last typed in Search. */
  var searchText = ""

  suspend fun menu(api: APIClient, lang: Lang): Menu =
      menus[lang] ?: api.also { it.setLanguage(lang) }.menu().also { menus[lang] = it }

  suspend fun filterOptions(api: APIClient, lang: Lang): FilterOptions =
      filters[lang] ?: api.also { it.setLanguage(lang) }.filterOptions().also { filters[lang] = it }

  suspend fun searchChoices(api: APIClient, lang: Lang): SearchChoices =
      choices[lang] ?: api.also { it.setLanguage(lang) }.searchChoices().also { choices[lang] = it }

  suspend fun home(api: APIClient, lang: Lang): HomeFeed =
      feeds[lang] ?: api.also { it.setLanguage(lang) }.home().also { feeds[lang] = it }
}
