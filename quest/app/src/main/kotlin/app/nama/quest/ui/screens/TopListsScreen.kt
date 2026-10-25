package app.nama.quest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nama.core.Loc
import app.nama.core.S
import app.nama.core.SiteQuery
import app.nama.core.TopList
import app.nama.quest.AppModel
import app.nama.quest.Services
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.PillRow
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.screens.people.PageNote
import app.nama.quest.ui.screens.people.PageRetry
import app.nama.quest.ui.screens.people.PosterGrid
import app.nama.quest.ui.screens.people.attempt

/** The website's lists of the best (SiteListsView.swift), each whole and in order, its place on every poster. */
@Composable
fun TopListsScreen(model: AppModel) {
  NeedsService(model) { services -> TopListsPage(model, services) }
}

@Composable
private fun TopListsPage(model: AppModel, services: Services) {
  val lang by model.lang.collectAsState()
  val loc = Loc(lang)
  var list by remember { mutableStateOf(TopList.imdbMovies) }
  val lists = remember { mutableStateMapOf<TopList, List<TopList.Entry>>() }
  var failed by remember { mutableStateOf(false) }
  var tries by remember { mutableIntStateOf(0) }

  LaunchedEffect(list, tries) {
    if (lists[list] != null) return@LaunchedEffect
    failed = false
    val asked = list
    attempt { TopList.entries(services.api.site(SiteQuery.topList(asked))) }
        .onSuccess { lists[asked] = it }
        .onFailure { failed = true }
  }

  val entries = lists[list]
  PosterGrid(
      model,
      entries.orEmpty().map { it.post },
      badge = { i, _ -> entries?.getOrNull(i)?.let { loc.num(it.rank) } },
      header = {
        fullLine("head") {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
            Text(loc(S.topLists), style = WallType.title)
            PillRow(TopList.entries, list, { title(loc, it) }, { list = it })
          }
        }
      },
      footer = {
        fullLine("state") {
          when {
            entries != null && entries.isEmpty() -> PageNote(loc(S.noMatches))
            entries != null -> Spacer(Modifier.height(1.dp))
            failed -> PageRetry(model, services) { tries++ }
            else -> PageNote(loc(S.loading))
          }
        }
      },
  )
}

private fun title(loc: Loc, list: TopList): String =
    when (list) {
      TopList.imdbMovies -> loc(S.imdbTopMovies)
      TopList.imdbSeries -> loc(S.imdbTopSeries)
      TopList.siteMovies -> loc(S.siteTopMovies)
      TopList.siteSeries -> loc(S.siteTopSeries)
    }
