package app.nama.quest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nama.core.Lang
import app.nama.core.Post
import app.nama.core.S
import app.nama.core.search
import app.nama.quest.AppModel
import app.nama.quest.Route
import app.nama.quest.Services
import app.nama.quest.ui.Ink
import app.nama.quest.ui.NeedsService
import app.nama.quest.ui.PillChoice
import app.nama.quest.ui.PostGrid
import app.nama.quest.ui.WallType
import app.nama.quest.ui.fullLine
import app.nama.quest.ui.screens.catalog.Mark
import app.nama.quest.ui.screens.catalog.MarkButton
import app.nama.quest.ui.screens.catalog.Memo
import app.nama.quest.ui.screens.catalog.SearchField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Search as you type, the Mac's SearchView: a field that raises the headset's keyboard, "Online
 * only" and the way to the advanced search under it, and the results as posters. With too little
 * typed, or nothing found, what is popular stands in instead of an empty wall.
 */
@Composable
fun SearchScreen(model: AppModel) {
  NeedsService(model) { services -> Search(model, services) }
}

/** The Mac's @AppStorage key, so the choice is kept the same way. */
private const val STREAM_ONLY = "searchStreamOnly"

@Composable
private fun Search(model: AppModel, services: Services) {
  val lang by model.lang.collectAsState()
  val loc = model.loc
  val api = services.api
  // Kept for the session, so coming back from a title's wall or the advanced search finds it still typed.
  var query by remember { mutableStateOf(Memo.searchText) }
  var streamOnly by remember { mutableStateOf(services.prefs.getBoolean(STREAM_ONLY, false)) }
  var results by remember { mutableStateOf<List<Post>>(emptyList()) }
  var heading by remember { mutableStateOf("") }
  var note by remember { mutableStateOf<String?>(null) }
  var searching by remember { mutableStateOf(false) }

  /** With nothing to show for the query, offer what is popular instead of an empty screen. */
  suspend fun showPopular(lang: Lang, why: String?) {
    val feed =
        try {
          Memo.home(api, lang)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        }
    note = why
    heading = loc(S.popularNow)
    val suggested = feed?.posts("suggested").orEmpty()
    results = suggested.ifEmpty { feed?.posts("movies").orEmpty() }.take(24)
  }

  // Restarted by every keystroke, which cancels the wait of the one before: the debounce.
  val text = query.trim()
  LaunchedEffect(text, streamOnly, lang) {
    if (text.length < 2) {
      showPopular(lang, null)
      return@LaunchedEffect
    }
    delay(450)
    searching = true
    try {
      api.setLanguage(lang)
      val page = api.search(text, streamOnly)
      if (page.posts.isEmpty()) {
        showPopular(lang, "${loc(S.nothingFor)} «$text»")
      } else {
        note = null
        heading = loc(S.topResults)
        results = page.posts
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      showPopular(lang, "${loc(S.error)}. ${loc(S.tryAgain)}")
    } finally {
      searching = false
    }
  }

  Column(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      SearchField(
          value = query,
          onValueChange = {
            query = it
            Memo.searchText = it
          },
          placeholder = loc(S.searchPrompt),
          busy = searching,
          clearLabel = loc(S.clearSearch),
          focusOnShow = true,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PillChoice(loc(S.streamOnly), streamOnly, {
          streamOnly = !streamOnly
          services.prefs.edit().putBoolean(STREAM_ONLY, streamOnly).apply()
        })
        MarkButton(loc(S.advancedSearch), Mark.Sliders) { model.push(Route.AdvancedSearch()) }
      }
    }
    Box(Modifier.weight(1f)) {
      PostGrid(model, results, canLoadMore = false, loadingMore = false, onLoadMore = {}) {
        note?.let { n -> fullLine("note") { Text(n, style = WallType.body.copy(color = Ink.text2), modifier = Modifier.padding(start = 4.dp)) } }
        if (results.isNotEmpty()) fullLine("heading") { Text(heading, style = WallType.heading, modifier = Modifier.padding(start = 4.dp)) }
      }
    }
  }
}
