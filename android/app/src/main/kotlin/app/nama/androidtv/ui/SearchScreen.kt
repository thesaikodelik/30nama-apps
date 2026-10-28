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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
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
import app.nama.core.Post
import app.nama.core.PostPage
import app.nama.core.S
import kotlinx.coroutines.delay

/**
 * Search, as the Apple TV's: the field, one line of letters under it, and the results under that,
 * all on one page that scrolls, so going down to the results takes the keyboard up out of the way.
 * Many TVs' own keyboards have no Persian, so the line is the app's own: English first, as most
 * titles are searched by their English names, Persian a press away (and the system's keyboard on
 * the field). Results fill the wall as the typing pauses. Recent searches come back when the field
 * is empty, saved only once one of their results was opened: most people type and go down to the
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
  /** The wall shows what the query found, not popular titles in its place. */
  var found by remember { mutableStateOf(false) }
  var persian by rememberSaveable { mutableStateOf(false) }
  var digits by rememberSaveable { mutableStateOf(false) }
  val recents = remember { Recents(app) }
  // Only what can be watched here and now, as the Mac's chip under its field; kept between visits.
  val prefs = remember { context.getSharedPreferences("search", android.content.Context.MODE_PRIVATE) }
  var streamOnly by remember { mutableStateOf(prefs.getBoolean("streamOnly", false)) }
  val field = remember { FocusRequester() }
  val firstKey = remember { FocusRequester() }
  val grid = rememberLazyGridState()

  val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
    if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
  }
  val canSpeak = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }

  LaunchedEffect(query, streamOnly) {
    // A search cut short by the next keystroke never got to say it was done.
    searching = false
    // The server finds nothing for two spaces running, which a remote types easily.
    val text = query.trim().replace(Regex("\\s+"), " ")
    if (text.length < 2) {
      found = false
      // With nothing to search for yet, what is popular instead of an empty screen.
      val feed = attempt { app.home() }
      note = null
      heading = loc(S.popularNow)
      results = (feed?.posts("suggested")?.takeIf { it.isNotEmpty() } ?: feed?.posts("movies").orEmpty()).take(WALL * 3)
      return@LaunchedEffect
    }
    // Wait for typing to pause; a newer keystroke starts this over.
    delay(500)
    searching = true
    // The server refuses a query it finds too short or finds nothing for; that is no results, not an error.
    val page = try {
      PostPage.from(app.api.get("search", mapOf("query" to text, "streamonly" to if (streamOnly) "1" else "0", "page" to "1")))
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: app.nama.core.APIError.Server) {
      PostPage(emptyList(), 1, 1, 0)
    } catch (e: Exception) {
      null
    }
    searching = false
    when {
      page == null -> {
        // Not the last query's results under this one's error.
        found = false
        note = "${loc(S.error)}. ${loc(S.tryAgain)}"
        val feed = attempt { app.home() }
        heading = loc(S.popularNow)
        results = feed?.posts("suggested").orEmpty().take(WALL * 3)
      }
      page.posts.isEmpty() -> {
        found = false
        note = "${loc(S.nothingFor)} «$text»"
        val feed = attempt { app.home() }
        heading = loc(S.popularNow)
        results = feed?.posts("suggested").orEmpty().take(WALL * 3)
      }
      else -> {
        found = true
        note = null
        heading = "${loc(S.topResults)} · ${loc.num(page.total.takeIf { it > 0 } ?: page.posts.size)}"
        // Only the first page is asked for: of it, whole rows, unless it is all there is.
        results = wholeRows(page.posts, page.pages <= 1).ifEmpty { page.posts }
      }
    }
  }
  val chrome = LocalChrome.current
  // The keyboard is a lazy grid's first item, laid out a frame or two after this starts.
  LaunchedEffect(Unit) {
    repeat(10) {
      if (!chrome.mayTakeFocus || runCatching { firstKey.requestFocus() }.isSuccess) return@LaunchedEffect
      delay(50)
    }
  }

  // As the Apple TV's: down from the keyboard, it goes up out of sight whole, the bar with it; back
  // on the keyboard, the page returns to its top. Not halfway, the field caught under the bar.
  var onKeyboard by remember { mutableStateOf(false) }
  var onPage by remember { mutableStateOf(false) }
  // Past the grid's top padding as well (130, less the 26 between items), so no strip of the
  // keyboard is left at the top; what follows keeps a margin of its own from the screen's edge.
  val past = with(LocalDensity.current) { 104.dp.roundToPx() }
  LaunchedEffect(onKeyboard, onPage) {
    when {
      onKeyboard -> grid.animateScrollToItem(0)
      onPage && grid.firstVisibleItemIndex == 0 && grid.layoutInfo.totalItemsCount > 1 -> grid.animateScrollToItem(1, past)
    }
  }
  val atTop by remember { derivedStateOf { grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0 } }
  LaunchedEffect(atTop) { chrome.pageAtTop = atTop }
  DisposableEffect(Unit) { onDispose { chrome.pageAtTop = true } }

  // The Mac's search page (the field a dark capsule under the bar, then a wall of posters) with
  // the Apple TV's keyboard, a line of letters between the two. One grid holds it all.
  Ambient {
    LazyVerticalGrid(
        GridCells.FixedSize(Theme.posterWidth),
        Modifier.fillMaxSize().focusRestorer().onFocusChanged { onPage = it.hasFocus },
        state = grid,
        contentPadding = PaddingValues(start = Theme.edge, end = Theme.edge, top = 130.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
      item(span = { GridItemSpan(maxLineSpan) }, key = "keyboard") {
        Column(
            Modifier.fillMaxWidth().onFocusChanged { onKeyboard = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(Modifier.width(840.dp)) { SearchField(query, { query = it }, field, loc(S.searchPrompt), searching) }
          Keyboard(
              persian = persian,
              digits = digits,
              first = firstKey,
              type = { query += it },
              delete = { query = query.dropLast(1) },
              clear = { query = "" },
              switchLanguage = { persian = !persian; digits = false },
              switchDigits = { digits = !digits },
              voice = if (canSpeak) {
                {
                  runCatching {
                    voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (persian) "fa-IR" else "en-US"))
                  }
                }
              } else null,
          )
        }
      }
      item(span = { GridItemSpan(maxLineSpan) }, key = "heading") {
        Column(Modifier.fillMaxWidth().padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
          // The Mac's two chips under its field: streaming only, and the advanced search.
          Row(Modifier.align(Alignment.CenterHorizontally).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(loc(S.streamOnly), {
              streamOnly = !streamOnly
              prefs.edit().putBoolean("streamOnly", streamOnly).apply()
            }, kind = PillKind.Chip, selected = streamOnly)
            PillButton(loc(S.advancedSearch), { nav.push(Route.AdvancedSearch()) }, kind = PillKind.Chip, icon = Icons.tune)
          }
          if (query.isBlank() && recents.items.isNotEmpty()) {
            // Centred under the keyboard, as the Mac's chips sit under its field.
            Text(tv(TV.recentSearches), Modifier.align(Alignment.CenterHorizontally), style = app(24, FontWeight.SemiBold), color = Theme.text3)
            LazyRow(Modifier.align(Alignment.CenterHorizontally).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              items(recents.items) { r -> PillButton(r, { query = r }, kind = PillKind.Chip) }
              item { PillButton(tv(TV.clear), { recents.clear() }, kind = PillKind.Chip, icon = Icons.close) }
            }
          }
          note?.let { Text(it, style = app(25), color = Theme.text2) }
          Text(heading, style = app(30, FontWeight.Bold), color = Theme.text1)
        }
      }
      // A wall of posters, as the Mac's: each one's name comes up on it in focus.
      items(results, key = { it.id }) { post ->
        PosterCard(post, {
          val text = query.trim().replace(Regex("\\s+"), " ")
          // Only a search that found something is worth keeping, not one that fell back to popular titles.
          if (found && text.length >= 2) recents.add(text)
          nav.push(Route.Details(post))
        })
      }
    }
  }
}

/**
 * The Mac's field: a capsule, left to right in every language (titles are searched by their
 * English names), the magnifier at the start or a spinner while searching, and a round clear button
 * at the end once there is text.
 */
@Composable
private fun SearchField(query: String, set: (String) -> Unit, focus: FocusRequester, prompt: String, searching: Boolean) {
  val shape = RoundedCornerShape(50)
  // A button until pressed: the system keyboard opens from here (with its own languages and its
  // dictation), but only when asked, so moving up past the field to the tabs does not throw it up.
  var typing by remember { mutableStateOf(false) }
  var focused by remember { mutableStateOf(false) }
  val input = remember { FocusRequester() }
  val done = {
    typing = false
    runCatching { focus.requestFocus() }
  }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    // Not glass: the Mac's field is a flat dark capsule, white 0.07 on the page with a faint hairline
    // (docs/design/mac tab-search). In focus, where the Mac shows its cursor, a little lighter with
    // a soft white edge, as a TV's focus must be seen from the sofa.
    val active = focused || typing
    Row(
        Modifier.fillMaxWidth().height(70.dp).onFocusChanged { focused = it.hasFocus }
            .background(Color.White.copy(alpha = if (active) 0.12f else 0.07f), shape)
            .border(if (active) 2.dp else 1.dp, Color.White.copy(alpha = if (active) 0.55f else 0.08f), shape)
            .padding(start = 28.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (searching) Spinner(Modifier.size(26.dp)) else Icon(Icons.search, null, Modifier.size(28.dp), tint = Theme.text3)
      }
      if (typing) {
        BackHandler { done() }
        BasicTextField(
            value = query,
            onValueChange = set,
            modifier = Modifier.weight(1f).focusRequester(input).onFocusChanged { if (!it.hasFocus && typing) typing = false },
            textStyle = app(25, FontWeight.Medium).copy(color = Theme.text1),
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { done() }),
        )
        LaunchedEffect(Unit) { runCatching { input.requestFocus() } }
      } else {
        Surface(
            onClick = { typing = true },
            modifier = Modifier.weight(1f).fillMaxSize().focusRequester(focus),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, contentColor = Theme.text1, focusedContentColor = Theme.text1),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Text(
                query.ifEmpty { prompt }, style = app(25, FontWeight.Medium),
                color = if (query.isNotEmpty()) Theme.text1 else Theme.text3,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
          }
        }
      }
      if (query.isNotEmpty()) RoundKey(Icons.close, 46.dp) { set(""); runCatching { focus.requestFocus() } }
    }
  }
}

private const val PERSIAN = "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهیآءئؤ"
private const val LATIN = "abcdefghijklmnopqrstuvwxyz"
// Digits stay Latin in Persian too: 30nama writes the numbers in its titles so ("1917", "2046").
private const val DIGITS = "1234567890-:'&.!?"

/**
 * The Apple TV's keyboard: every letter on one line, in alphabetical order (the remote reaches any
 * one fastest that way, unlike a typewriter's), then space and delete at its end (held, it clears). Under it, smaller: digits, the other language, a half space (the Persian ZWNJ), the
 * microphone and clear. English first; Persian is laid out right to left in every interface language.
 */
@Composable
private fun Keyboard(
    persian: Boolean,
    digits: Boolean,
    first: FocusRequester,
    type: (String) -> Unit,
    delete: () -> Unit,
    clear: () -> Unit,
    switchLanguage: () -> Unit,
    switchDigits: () -> Unit,
    voice: (() -> Unit)?,
) {
  val keys = if (digits) DIGITS else if (persian) PERSIAN else LATIN
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    CompositionLocalProvider(LocalLayoutDirection provides if (persian && !digits) LayoutDirection.Rtl else LayoutDirection.Ltr) {
      Row(
          Modifier.fillMaxWidth().height(76.dp).background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50)).padding(horizontal = 14.dp).focusRestorer(first),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        keys.forEachIndexed { i, ch ->
          LineKey(ch.toString(), Modifier.weight(1f).then(if (i == 0) Modifier.focusRequester(first) else Modifier)) { type(ch.toString()) }
        }
        LineKey(tv(TV.space), Modifier.weight(2.2f)) { type(" ") }
        LineKey(null, Modifier.weight(1.6f), icon = Icons.backspace, onLong = clear) { delete() }
      }
    }
    Row(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      PillButton(if (digits) (if (persian) "ابپ" else "abc") else "123", switchDigits, kind = PillKind.Chip)
      PillButton(if (persian) "English" else "فارسی", switchLanguage, kind = PillKind.Chip)
      if (persian) PillButton(tv(TV.zwnj), { type("‌") }, kind = PillKind.Chip)
      if (voice != null) RoundKey(Icons.mic, 58.dp, voice)
      PillButton(tv(TV.clear), clear, kind = PillKind.Chip)
    }
  }
}

/** One key on the line: a letter on the glass, a white tile lifted under focus. */
@Composable
private fun LineKey(text: String?, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
  val shape = RoundedCornerShape(14.dp)
  Surface(
      onClick = onClick,
      onLongClick = onLong,
      modifier = modifier.height(60.dp),
      shape = ClickableSurfaceDefaults.shape(shape),
      colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White, contentColor = Theme.text1, focusedContentColor = Theme.focusInk),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.18f),
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (icon != null) Icon(icon, null, Modifier.size(28.dp))
      else Text(text.orEmpty(), style = app(if ((text?.length ?: 0) > 1) 21 else 27, FontWeight.SemiBold), maxLines = 1)
    }
  }
}

/** A round button with an icon, a flat disc as the Mac's controls (not glass), white in focus. */
@Composable
private fun RoundKey(icon: androidx.compose.ui.graphics.vector.ImageVector, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val focused by interaction.collectIsFocusedAsState()
  Surface(
      onClick = onClick,
      modifier = Modifier.size(size),
      interactionSource = interaction,
      shape = ClickableSurfaceDefaults.shape(CircleShape),
      colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White, contentColor = Theme.text2, focusedContentColor = Theme.focusInk),
      scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
  ) {
    Box(Modifier.fillMaxSize().then(if (focused) Modifier else Modifier.background(Color.White.copy(alpha = 0.10f), CircleShape)), contentAlignment = Alignment.Center) {
      Icon(icon, null, Modifier.size(size * 0.48f))
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
