package app.nama.quest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.S
import app.nama.core.Schedule
import app.nama.core.SiteQuery
import app.nama.quest.AppModel
import app.nama.quest.Services
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.PillChoice
import app.nama.quest.ui.PillRow
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.screens.people.PageNote
import app.nama.quest.ui.screens.people.PageRetry
import app.nama.quest.ui.screens.people.PosterGrid
import app.nama.quest.ui.screens.people.attempt
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

/**
 * The week of series and anime from the 30nama website (SiteListsView.swift): which have a new
 * episode on which day, from today on.
 */
@Composable
fun ScheduleScreen(model: AppModel) {
  NeedsService(model) { services -> SchedulePage(model, services) }
}

@Composable
private fun SchedulePage(model: AppModel, services: Services) {
  val lang by model.lang.collectAsState()
  val loc = Loc(lang)
  var kind by remember { mutableStateOf(Schedule.Kind.series) }
  val today = remember { Schedule.day(Instant.now()) }
  var day by remember { mutableIntStateOf(today) }
  // Each kind's week is asked for once while the page is open, as on the Mac.
  val weeks = remember { mutableStateMapOf<Schedule.Kind, Schedule>() }
  var failed by remember { mutableStateOf(false) }
  var tries by remember { mutableIntStateOf(0) }

  LaunchedEffect(kind, tries) {
    if (weeks[kind] != null) return@LaunchedEffect
    failed = false
    val asked = kind
    attempt { Schedule.fromSite(services.api.site(SiteQuery.schedule(asked))) }
        .onSuccess { weeks[asked] = it }
        .onFailure { failed = true }
  }

  val week = weeks[kind]
  val posts = week?.on(day).orEmpty()
  val days = remember { Schedule.week(Instant.now()) }
  PosterGrid(
      model,
      posts,
      header = {
        fullLine("head") {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(loc(S.weeklySchedule), style = WallType.title)
              Spacer(Modifier.weight(1f))
              PillChoice(loc(S.series), kind == Schedule.Kind.series, { kind = Schedule.Kind.series })
              PillChoice(loc(S.anime), kind == Schedule.Kind.anime, { kind = Schedule.Kind.anime })
            }
            PillRow(days, day, { d -> if (d == today) loc(S.today) else weekdayName(d, lang) }, { day = it })
          }
        }
      },
      footer = {
        fullLine("state") {
          when {
            week != null && posts.isEmpty() -> PageNote(loc(S.noMatches))
            week != null -> Spacer(Modifier.height(1.dp))
            failed -> PageRetry(model, services) { tries++ }
            else -> PageNote(loc(S.loading))
          }
        }
      },
  )
}

/** A day as the site numbers it (Saturday 0), named in the interface's language. */
private fun weekdayName(day: Int, lang: Lang): String {
  val locale = when (lang) {
    Lang.fa -> Locale("fa", "IR")
    Lang.ar -> Locale("ar")
    else -> Locale.US
  }
  return DayOfWeek.SATURDAY.plus(day.toLong()).getDisplayName(TextStyle.FULL, locale)
}
