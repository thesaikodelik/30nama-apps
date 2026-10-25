@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nama.quest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.nama.core.Filter
import app.nama.core.FilterOptions
import app.nama.core.PostPage
import app.nama.core.S
import app.nama.core.SiteQuery
import app.nama.core.archive
import app.nama.core.filtered
import app.nama.core.fromSite
import app.nama.quest.AppModel
import app.nama.quest.Services
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.screens.catalog.FilterCapsule
import app.nama.quest.ui.screens.catalog.FoldOut
import app.nama.quest.ui.screens.catalog.Memo
import app.nama.quest.ui.screens.catalog.Page
import app.nama.quest.ui.screens.catalog.Paged
import app.nama.quest.ui.screens.catalog.PagedWall
import app.nama.quest.ui.screens.catalog.ResetCapsule
import app.nama.quest.ui.screens.catalog.SortPills
import app.nama.quest.ui.screens.catalog.resultsLine
import app.nama.quest.ui.screens.catalog.titleLine
import kotlinx.coroutines.CancellationException

/**
 * Every title under one heading (movies, a genre), as a wall of posters that grows as it is
 * scrolled: the Mac's CatalogView. Movies, series and anime can be narrowed by genre, country,
 * lowest score and decade; with any of them set the wall comes from the advanced search.
 */
@Composable
fun CatalogScreen(model: AppModel, slug: String, title: String) {
  NeedsService(model) { services -> Catalog(model, services, slug, title) }
}

/**
 * The orders the app API keeps to, and "newest", which only the website's own API has (by post
 * id), so it is offered only with the site's key and on a typed catalogue.
 */
private enum class Sort(val key: String, val label: S) {
  updated("update", S.updated),
  newest("id", S.newest),
  popular("favorite", S.popular),
}

private enum class Narrow { genre, country, score, decade }

@Composable
private fun Catalog(model: AppModel, services: Services, slug: String, title: String) {
  val lang by model.lang.collectAsState()
  val loc = model.loc
  val api = services.api
  // A genre's own page has no kind to stay inside, so it can't be narrowed.
  val type = remember(slug) { mapOf("movies" to "movie", "series" to "series", "anime" to "anime")[slug] }
  val scope = rememberCoroutineScope()
  val paged = remember(slug) { Paged(scope) }
  var sort by remember(slug) { mutableStateOf(Sort.updated) }
  var filter by remember(slug) { mutableStateOf(Filter()) }
  var open by remember(slug) { mutableStateOf<Narrow?>(null) }
  var options by remember(slug, lang) { mutableStateOf<FilterOptions?>(null) }
  val sorts = Sort.entries.filter { it != Sort.newest || (type != null && api.hasSite) }

  LaunchedEffect(type, lang) {
    if (type == null) return@LaunchedEffect
    options =
        try {
          Memo.filterOptions(api, lang)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        }
  }

  LaunchedEffect(sort, filter, lang) {
    api.setLanguage(lang)
    val asked = sort
    val narrowed = filter
    paged.restart { page ->
      if (asked == Sort.newest && type != null) {
        val p = PostPage.fromSite(api.site(SiteQuery.newest(type, narrowed.genre, streamOnly = true, page = page)))
        // The site's lists give pages, not a count.
        Page(p.posts, p.page, p.pages, null)
      } else {
        val p = if (narrowed.isEmpty) api.archive(slug, asked.key, page) else api.filtered(narrowed, type, asked.key, page)
        Page(p.posts, p.page, p.pages, p.total)
      }
    }
  }

  PagedWall(model, paged, site = sort == Sort.newest, empty = loc(S.noMatches)) {
    titleLine(title, resultsLine(model, paged.total)) {
      SortPills(sorts, sort, { loc(it.label) }) {
        // What the newest list can't narrow by is let go, not kept and silently ignored.
        if (it == Sort.newest) filter = Filter(genre = filter.genre)
        if (it == Sort.newest && open != Narrow.genre) open = null
        sort = it
      }
    }
    val o = options
    if (type != null && o != null) {
      fullLine("filters") {
        val decades = remember(o, lang) { o.decades.map { FilterOptions.Choice(loc.num("$it–${it + 9}"), it.toString()) } }
        fun choicesOf(n: Narrow) =
            when (n) {
              Narrow.genre -> o.genres
              Narrow.country -> o.countries
              Narrow.score -> o.scores
              Narrow.decade -> decades
            }
        fun valueOf(n: Narrow): String? =
            when (n) {
              Narrow.genre -> filter.genre
              Narrow.country -> filter.country
              Narrow.score -> filter.score
              Narrow.decade -> filter.decade?.toString()
            }
        fun set(n: Narrow, v: String?) {
          filter =
              when (n) {
                Narrow.genre -> filter.copy(genre = v)
                Narrow.country -> filter.copy(country = v)
                Narrow.score -> filter.copy(score = v)
                Narrow.decade -> filter.copy(decade = v?.toIntOrNull())
              }
        }
        fun titleOf(n: Narrow) =
            loc(
                when (n) {
                  Narrow.genre -> S.genres
                  Narrow.country -> S.country
                  Narrow.score -> S.minScore
                  Narrow.decade -> S.decade
                }
            )
        // The site's newest list narrows by genre only.
        val shown = if (sort == Sort.newest) listOf(Narrow.genre) else Narrow.entries
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { n ->
              val value = valueOf(n)
              FilterCapsule(titleOf(n), choicesOf(n).firstOrNull { it.value == value }?.name, open == n) { open = if (open == n) null else n }
            }
            if (!filter.isEmpty) {
              ResetCapsule(loc(S.reset)) {
                filter = Filter()
                open = null
              }
            }
          }
          shown.forEach { n ->
            FoldOut(
                visible = open == n,
                allLabel = loc(S.allChoices),
                choices = choicesOf(n),
                isChosen = { it == valueOf(n) },
                noneChosen = valueOf(n) == null,
                onAll = {
                  set(n, null)
                  open = null
                },
                onPick = {
                  set(n, it)
                  open = null
                },
            )
          }
        }
      }
    }
  }
}
