@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.nama.quest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import app.nama.core.AdvancedQuery
import app.nama.core.FilterOptions
import app.nama.core.S
import app.nama.core.SearchChoices
import app.nama.core.advancedSearch
import app.nama.quest.AppModel
import app.nama.quest.Services
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.PillChoice
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.screens.catalog.FilterCapsule
import app.nama.quest.ui.screens.catalog.FoldOut
import app.nama.quest.ui.screens.catalog.Mark
import app.nama.quest.ui.screens.catalog.Memo
import app.nama.quest.ui.screens.catalog.Page
import app.nama.quest.ui.screens.catalog.Paged
import app.nama.quest.ui.screens.catalog.PagedWall
import app.nama.quest.ui.screens.catalog.ResetCapsule
import app.nama.quest.ui.screens.catalog.SortPills
import app.nama.quest.ui.screens.catalog.TextCapsule
import app.nama.quest.ui.screens.catalog.resultsLine
import app.nama.quest.ui.screens.catalog.titleLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 30nama's catalogue narrowed by everything its advanced search acts on: the Mac's
 * AdvancedSearchView. Several genres at once, type, country, language, network, lowest score, age
 * rating, years, director, x265 and 3D. [start] is what it opens with: nothing, or a director
 * whose name was chosen on a title's page.
 */
@Composable
fun AdvancedSearchScreen(model: AppModel, start: AdvancedQuery) {
  NeedsService(model) { services -> Advanced(model, services, start) }
}

private enum class Field(val title: S) {
  genres(S.genres),
  type(S.kind),
  country(S.country),
  language(S.originalLanguage),
  network(S.network),
  score(S.minScore),
  age(S.ageRating),
  from(S.years),
  to(S.hoursTo),
}

@Composable
private fun Advanced(model: AppModel, services: Services, start: AdvancedQuery) {
  val lang by model.lang.collectAsState()
  val loc = model.loc
  val api = services.api
  val scope = rememberCoroutineScope()
  val paged = remember { Paged(scope) }
  var query by remember(start) { mutableStateOf(start) }
  var director by remember(start) { mutableStateOf(start.director ?: "") }
  var choices by remember(lang) { mutableStateOf<SearchChoices?>(null) }
  var open by remember { mutableStateOf<Field?>(null) }

  LaunchedEffect(lang) {
    choices =
        try {
          Memo.searchChoices(api, lang)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        }
  }

  LaunchedEffect(query, lang) {
    api.setLanguage(lang)
    val asked = query
    paged.restart { page ->
      val p = api.advancedSearch(asked, page)
      Page(p.posts, p.page, p.pages, p.total)
    }
  }

  // The name is sent once typing pauses, not with every letter.
  LaunchedEffect(director) {
    delay(600)
    val name = director.trim()
    query = query.copy(director = name.ifEmpty { null })
  }

  PagedWall(model, paged, empty = loc(S.noMatches)) {
    titleLine(loc(S.advancedSearch), resultsLine(model, paged.total), hint = loc(S.advancedHint))
    val c = choices
    if (c != null) {
      fullLine("filters") {
        // Newest first, as the Mac lists them.
        val years = remember(c, lang) { (c.years.last downTo c.years.first).map { FilterOptions.Choice(loc.num(it.toString()), it.toString()) } }
        fun choicesOf(f: Field) =
            when (f) {
              Field.genres -> c.genres
              Field.type -> c.types
              Field.country -> c.countries
              Field.language -> c.languages
              Field.network -> c.networks
              Field.score -> c.scores
              Field.age -> c.ages
              Field.from, Field.to -> years
            }
        fun valueOf(f: Field): String? =
            when (f) {
              Field.genres -> null
              Field.type -> query.type
              Field.country -> query.country
              Field.language -> query.language
              Field.network -> query.network
              Field.score -> query.score
              Field.age -> query.age
              Field.from -> query.fromYear?.toString()
              Field.to -> query.toYear?.toString()
            }
        fun set(f: Field, v: String?) {
          query =
              when (f) {
                Field.genres -> query.copy(genres = if (v == null) emptyList() else if (v in query.genres) query.genres - v else query.genres + v)
                Field.type -> query.copy(type = v)
                Field.country -> query.copy(country = v)
                Field.language -> query.copy(language = v)
                Field.network -> query.copy(network = v)
                Field.score -> query.copy(score = v)
                Field.age -> query.copy(age = v)
                Field.from -> query.copy(fromYear = v?.toIntOrNull())
                Field.to -> query.copy(toYear = v?.toIntOrNull())
              }
        }
        fun label(f: Field): String? {
          if (f == Field.genres) {
            val picked = c.genres.filter { it.value in query.genres }
            return when (picked.size) {
              0 -> null
              1 -> picked[0].name
              else -> loc.num(picked.size)
            }
          }
          val v = valueOf(f) ?: return null
          return choicesOf(f).firstOrNull { it.value == v }?.name ?: loc.num(v)
        }
        // A field the server offers nothing for (no networks listed, say) is left out.
        val fields = Field.entries.filter { choicesOf(it).isNotEmpty() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              fields.forEach { f -> FilterCapsule(loc(f.title), label(f), open == f) { open = if (open == f) null else f } }
              PillChoice("x265", query.x265, { query = query.copy(x265 = !query.x265) })
              PillChoice("3D", query.threeD, { query = query.copy(threeD = !query.threeD) })
              PillChoice(loc(S.streamOnly), query.streamOnly, { query = query.copy(streamOnly = !query.streamOnly) })
            }
            fields.forEach { f ->
              val many = f == Field.genres
              FoldOut(
                  visible = open == f,
                  allLabel = loc(S.allChoices),
                  choices = choicesOf(f),
                  isChosen = { if (many) it in query.genres else it == valueOf(f) },
                  noneChosen = if (many) query.genres.isEmpty() else valueOf(f) == null,
                  onAll = {
                    set(f, null)
                    open = null
                  },
                  onPick = {
                    set(f, it)
                    // Genres are picked several at once, so their tray stays open.
                    if (!many) open = null
                  },
              )
            }
          }
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextCapsule(director, { director = it }, loc(S.directorName), Mark.Director)
            if (query.count > 0 || director.isNotBlank()) {
              ResetCapsule(loc(S.reset)) {
                // Online only and the order are how the user likes to search, not part of a search.
                director = ""
                open = null
                query = AdvancedQuery(streamOnly = query.streamOnly, sort = query.sort)
              }
            }
            Spacer(Modifier.weight(1f))
            SortPills(AdvancedQuery.sorts, query.sort, { sortName(model, it) }) { query = query.copy(sort = it) }
          }
        }
      }
    }
  }
}

private fun sortName(model: AppModel, sort: String): String {
  val loc = model.loc
  return when (sort) {
    "update" -> loc(S.updated)
    "year" -> loc(S.byYear)
    "imdb-rate" -> "IMDb"
    "favorite" -> loc(S.popular)
    else -> loc(S.relevance)
  }
}
