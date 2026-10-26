package app.nama.androidtv.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.nama.androidtv.attempt
import app.nama.core.Lang
import app.nama.core.Post
import app.nama.core.PostPage
import app.nama.core.S
import kotlinx.coroutines.delay

/**
 * Search. Many TVs' own keyboards have no Persian, so the app has its own beside the field, driven
 * by the remote (with an English layout too, and the system's keyboard one press away on the
 * field). Results fill the grid as the typing pauses. Recent searches come back when the field is
 * empty, saved only once one of their results was opened: most people type and go down to the
 * results rather than press "search". Learnt from 30namaTV.
 */
@Composable
fun SearchScreen() {
  val app = LocalApp.current
  val nav = LocalNav.current
  val loc = LocalLoc.current
  val context = LocalContext.current
  var query by rememberSaveable { mutableStateOf("") }
  var results by remember { mutableStateOf<List<Post>>(emptyList()) }
  var heading by remember { mutableStateOf("") }
  var note by remember { mutableStateOf<String?>(null) }
  var searching by remember { mutableStateOf(false) }
  var persian by rememberSaveable { mutableStateOf(loc.lang != Lang.en) }
  val recents = remember { Recents(app) }
  val field = remember { FocusRequester() }
  val firstKey = remember { FocusRequester() }

  val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
    if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
  }
  val canSpeak = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }

  LaunchedEffect(query) {
    val text = query.trim()
    if (text.length < 2) {
      // With nothing to search for yet, what is popular instead of an empty screen.
      val feed = attempt { app.home() }
      note = null
      heading = loc(S.popularNow)
      results = (feed?.posts("suggested")?.takeIf { it.isNotEmpty() } ?: feed?.posts("movies").orEmpty()).take(20)
      return@LaunchedEffect
    }
    // Wait for typing to pause; a newer keystroke starts this over.
    delay(500)
    searching = true
    // The server refuses a query it finds too short or finds nothing for; that is no results, not an error.
    val page = try {
      PostPage.from(app.api.get("search", mapOf("query" to text, "streamonly" to "0", "page" to "1")))
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: app.nama.core.APIError.Server) {
      PostPage(emptyList(), 1, 1, 0)
    } catch (e: Exception) {
      null
    }
    searching = false
    when {
      page == null -> note = "${loc(S.error)}. ${loc(S.tryAgain)}"
      page.posts.isEmpty() -> {
        note = "${loc(S.nothingFor)} «$text»"
        val feed = attempt { app.home() }
        heading = loc(S.popularNow)
        results = feed?.posts("suggested").orEmpty().take(20)
      }
      else -> {
        note = null
        heading = "${loc(S.topResults)} · ${loc.num(page.total.takeIf { it > 0 } ?: page.posts.size)}"
        results = page.posts
      }
    }
  }
  val chrome = LocalChrome.current
  LaunchedEffect(Unit) { if (chrome.mayTakeFocus) runCatching { firstKey.requestFocus() } }

  Box(Modifier.fillMaxSize().background(Theme.bg0)) {
    Row(Modifier.fillMaxSize().padding(start = Theme.edge, end = Theme.edge, top = 150.dp), horizontalArrangement = Arrangement.spacedBy(60.dp)) {
      Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SearchField(query, { query = it }, field, loc(S.searchPrompt))
        Keyboard(
            persian = persian,
            first = firstKey,
            type = { query += it },
            delete = { query = query.dropLast(1) },
            clear = { query = "" },
            switch = { persian = !persian },
            voice = if (canSpeak) {
              {
                runCatching {
                  voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                      .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (loc.lang == Lang.en) "en-US" else "fa-IR"))
                }
              }
            } else null,
        )
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if (query.isBlank() && recents.items.isNotEmpty()) {
          Text(tv(TV.recentSearches), style = app(26, FontWeight.SemiBold), color = Theme.text2)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recents.items) { r -> PillButton(r, { query = r }, kind = PillKind.Chip) }
            item { PillButton(tv(TV.clear), { recents.clear() }, kind = PillKind.Chip, icon = Icons.close) }
          }
        }
        note?.let { Text(it, style = app(26), color = Theme.text2) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(heading, style = app(34, FontWeight.Bold), color = Theme.text1)
          if (searching) Spinner(Modifier.size(30.dp))
        }
        LazyVerticalGrid(
            GridCells.Fixed(5),
            Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalArrangement = Arrangement.spacedBy(44.dp),
        ) {
          items(results, key = { it.id }) { post ->
            PosterCard(post, {
              if (query.trim().length >= 2) recents.add(query.trim())
              nav.push(Route.Details(post))
            }, titled = true)
          }
        }
      }
    }
  }
}

@Composable
private fun SearchField(query: String, set: (String) -> Unit, focus: FocusRequester, prompt: String) {
  val shape = RoundedCornerShape(50)
  // A button until pressed: the system keyboard opens from here (with its own languages and its
  // dictation), but only when asked, so moving up past the field to the tabs does not throw it up.
  var typing by remember { mutableStateOf(false) }
  val field = remember { FocusRequester() }
  val done = {
    typing = false
    runCatching { focus.requestFocus() }
  }
  if (typing) {
    BackHandler { done() }
    Row(
        Modifier.fillMaxWidth().height(76.dp).background(Color.White, shape).border(2.dp, Theme.accent, shape).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Icon(Icons.search, null, Modifier.size(30.dp), tint = Theme.focusInk)
      BasicTextField(
          value = query,
          onValueChange = set,
          modifier = Modifier.weight(1f).focusRequester(field).onFocusChanged { if (!it.hasFocus && typing) typing = false },
          textStyle = app(27, FontWeight.Medium).copy(color = Theme.focusInk),
          singleLine = true,
          cursorBrush = SolidColor(Theme.accent),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { done() }),
      )
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
  } else {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = { typing = true },
        modifier = Modifier.fillMaxWidth().height(76.dp).focusRequester(focus),
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Theme.glass, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
        border = ClickableSurfaceDefaults.border(border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Theme.glassEdge))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
      Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.search, null, Modifier.size(30.dp), tint = if (focused) Theme.focusInk else Theme.text2)
        Text(
            query.ifEmpty { prompt }, style = app(27, if (query.isEmpty()) FontWeight.Normal else FontWeight.Medium),
            color = when {
              query.isNotEmpty() -> Color.Unspecified
              focused -> Theme.focusInk.copy(alpha = 0.5f)
              else -> Theme.text3
            },
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private const val PERSIAN = "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهیآءئؤ"
private const val LATIN = "abcdefghijklmnopqrstuvwxyz"
private const val COLUMNS = 7

/**
 * The app's own keyboard: the letters in alphabetical order (the remote reaches any one fastest
 * that way, unlike a typewriter's), digits, a space, a half space (the Persian ZWNJ) and delete.
 * It is laid out right to left in Persian in every interface language.
 */
@Composable
private fun Keyboard(persian: Boolean, first: FocusRequester, type: (String) -> Unit, delete: () -> Unit, clear: () -> Unit, switch: () -> Unit, voice: (() -> Unit)?) {
  val loc = LocalLoc.current
  val letters = if (persian) PERSIAN + "۱۲۳۴۵۶۷۸۹۰" else LATIN + "1234567890"
  CompositionLocalProvider(LocalLayoutDirection provides if (persian) LayoutDirection.Rtl else LayoutDirection.Ltr) {
    Column(Modifier.focusRestorer(first), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key(tv(TV.space), Modifier.weight(2f)) { type(" ") }
        if (persian) Key(tv(TV.zwnj), Modifier.weight(3f)) { type("‌") } else Key("'", Modifier.weight(1f)) { type("'") }
        Key(null, Modifier.weight(2f), icon = Icons.backspace, onLong = clear) { delete() }
      }
      letters.chunked(COLUMNS).forEachIndexed { r, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          row.forEachIndexed { c, ch ->
            Key(ch.toString(), Modifier.weight(1f).then(if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier)) { type(ch.toString()) }
          }
          repeat(COLUMNS - row.length) { Spacer(Modifier.weight(1f)) }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key(if (persian) "ABC" else "فا", Modifier.weight(2f)) { switch() }
        if (voice != null) Key(null, Modifier.weight(2f), icon = Icons.mic) { voice() }
        Key(tv(TV.clear), Modifier.weight(3f)) { clear() }
      }
    }
  }
}

@Composable
private fun Key(text: String?, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
  Surface(
      onClick = onClick,
      onLongClick = onLong,
      modifier = modifier.height(62.dp),
      shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
      colors = ClickableSurfaceDefaults.colors(containerColor = Theme.glass, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (icon != null) Icon(icon, null, Modifier.size(28.dp))
      else Text(text.orEmpty(), style = app(if ((text?.length ?: 0) > 1) 22 else 28, FontWeight.SemiBold), maxLines = 1)
    }
  }
}

/** Searches that led somewhere, newest first, eight at most. */
private class Recents(private val app: app.nama.androidtv.AppModel) {
  var items by mutableStateOf(app.recentSearches())
    private set

  fun add(query: String) {
    val clean = query.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
    items = (listOf(clean) + items.filter { !it.equals(clean, ignoreCase = true) }).take(8)
    app.saveRecentSearches(items)
  }

  fun clear() {
    items = emptyList()
    app.saveRecentSearches(items)
  }
}
