package app.nama.androidtv

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nama.androidtv.data.Settings
import app.nama.core.APIClient
import app.nama.core.APIConfig
import app.nama.core.APIError
import app.nama.core.ContinueDismissals
import app.nama.core.HomeFeed
import app.nama.core.Json
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.Post
import app.nama.core.RatingBook
import app.nama.core.S
import app.nama.core.SiteConfig
import app.nama.core.StreamItem
import app.nama.core.ContinueWatching
import app.nama.core.Transport
import app.nama.core.User
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the player needs to start. */
data class PlaybackRequest(
    val post: Post,
    val items: List<StreamItem>,
    val index: Int,
    /** Position to resume from, when it belongs to the item being started. */
    val resume: ContinueWatching?,
    /** An episode chosen from the list, rather than Play: it starts there, whatever was watched later. */
    val pickedByUser: Boolean = false,
)

/**
 * App-wide state: boot, session, settings and the shared API client, as the Apple TV app's
 * AppModel. Screens read it as Compose state; it changes only on the main thread.
 */
class AppModel(
    private val context: Context,
    private val scope: CoroutineScope,
    transport: Transport? = null,
    siteKey: String? = BuildConfig.SITE_KEY.ifEmpty { null },
    val http: okhttp3.OkHttpClient = okhttp3.OkHttpClient(),
) {
  enum class Phase { Booting, Failed, Login, Main }

  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val account = context.getSharedPreferences("account", Context.MODE_PRIVATE)
  private val store = context.getSharedPreferences("app", Context.MODE_PRIVATE)

  val api = APIClient(APIConfig(site = SiteConfig(key = siteKey)), transport)
  private val keys = context.getSharedPreferences("keys", Context.MODE_PRIVATE)

  /**
   * A key the user made with their own account on another service ("tmdb", "subsource"). It stays
   * on this TV; the app works in full without it.
   */
  fun key(name: String): String? = keys.getString(name, null)?.trim()?.ifEmpty { null }

  fun setKey(name: String, value: String?) {
    keys.edit().apply { if (value.isNullOrBlank()) remove(name) else putString(name, value.trim()) }.apply()
  }

  /** Persian subtitles from SubSource, for what 30nama has none for. */
  val finder = app.nama.androidtv.data.SubtitleFinder(http) { key("subsource") }

  /** This TV's own record of where each title was left. */
  val kept = app.nama.androidtv.data.KeptProgress(context.getSharedPreferences("progress", Context.MODE_PRIVATE))

  /** Work that outlives the screen that started it (a report as the player closes). */
  fun background(block: suspend () -> Unit) {
    scope.launch { attempt { block() } }
  }

  /** Episode stills taken from the episodes themselves. */
  val stills = app.nama.androidtv.player.EpisodeStills(context, http)
  val updater = Updater(context, http, scope)
  val gemini = app.nama.androidtv.data.Gemini(context, http)

  /** Logos and trailers from TMDB and Aparat. */
  val extras = app.nama.androidtv.data.Extras(context, http, { key("tmdb") }, { inIran })

  /** A small set of choices over the page (a held select), shown by the shell. */
  val sheets = app.nama.androidtv.ui.Sheets()

  var phase by mutableStateOf(Phase.Booting)
    private set
  var settings by mutableStateOf(Settings.load(prefs))
    private set
  var user by mutableStateOf<User?>(null)
    private set
  /**
   * Connected through the host that answers only inside Iran: TMDB, YouTube and the website are
   * filtered, so what comes from them is not waited for (see `APIClient.inIran`).
   */
  var inIran by mutableStateOf(false)
    private set
  /** The server this TV reached 30nama through, for Settings > About. */
  var apiHost by mutableStateOf<String?>(null)
    private set
  /** Bumped when the catalogue itself may differ: sign-in, sign-out, language. */
  var contentVersion by mutableIntStateOf(0)
    private set
  /** Bumped when watch progress changed (the player closed). */
  var watchVersion by mutableIntStateOf(0)
    private set
  /** Goes up when "continue watching" should be asked for again, after a title was taken off it. */
  var continueVersion by mutableIntStateOf(0)
    private set
  var playback by mutableStateOf<PlaybackRequest?>(null)
    private set
  var toast by mutableStateOf<String?>(null)
    private set
  /** The home page has arrived at least once since launch. */
  var homeLoaded by mutableStateOf(false)
    private set
  /** The ids of the titles in each of the user's 30nama lists ("watchlist", "favorite", "notify"). */
  var lists by mutableStateOf<Map<String, Set<Int>>>(emptyMap())
    private set
  /** Files watched to the end on this device. The server keeps where each title was left, not which episodes are done. */
  var watched by mutableStateOf(store.getStringSet("watched", emptySet())!!.toSet())
    private set
  /** The user's own ratings, kept on this TV. */
  var ratings by mutableStateOf(readRatings())
    private set

  val loc: Loc get() = Loc(settings.lang)
  val isLoggedIn: Boolean get() = user != null

  private var homeCache: Triple<HomeFeed, Long, Lang>? = null
  private var toastJob: Job? = null
  private val dismissals = ContinueDismissals(readDismissals())

  // MARK: Boot and session

  fun boot() {
    scope.launch {
      phase = Phase.Booting
      api.setLanguage(settings.lang)
      api.setSessionExpiredHandler { scope.launch { sessionExpired() } }
      val host =
          try {
            api.bootstrap(account.getString("host", null))
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            Log.w(TAG, "bootstrap: $e")
            phase = Phase.Failed
            return@launch
          }
      account.edit().putString("host", host).apply()
      apiHost = runCatching { java.net.URI(host).host }.getOrNull() ?: host
      inIran = api.inIran
      savedUser()?.let { saved ->
        user = saved
        api.setToken(saved.token)
        launch { refreshProfile() }
        launch { refreshLists() }
      }
      phase = if (user != null || settings.guest) Phase.Main else Phase.Login
    }
  }

  /** The saved profile can be stale (a renewed subscription), so it is read again once per launch. */
  private suspend fun refreshProfile() {
    val token = user?.token ?: return
    val e = attempt { api.call("user") } ?: return
    if (!e.success) return
    val fresh = User.from(e.result, fallbackToken = token) ?: return
    user = fresh
    saveUser(fresh)
  }

  /** Completes a QR sign-in. The login call returns only a token; the profile follows. */
  suspend fun signIn(token: String) {
    api.setToken(token)
    var signedIn = User(token)
    attempt { api.call("user") }?.takeIf { it.success }?.let { e -> User.from(e.result, fallbackToken = token)?.let { signedIn = it } }
    user = signedIn
    saveUser(signedIn)
    update { it.copy(guest = false) }
    homeCache = null
    contentVersion++
    showToast(listOfNotNull(loc(S.welcome), signedIn.name).joinToString(" "))
    phase = Phase.Main
    refreshLists()
  }

  fun continueAsGuest() {
    update { it.copy(guest = true) }
    phase = Phase.Main
  }

  /** Leaves guest browsing for the sign-in screen. */
  fun requestSignIn() {
    phase = Phase.Login
  }

  fun signOut() {
    val api = api
    // Not waited on for long: a VPN that hangs must not keep the TV signed in.
    scope.launch {
      kotlinx.coroutines.withTimeoutOrNull(5_000) { attempt { api.call("logout") } }
      api.setToken(null)
    }
    clearSession()
  }

  private fun sessionExpired() {
    if (user == null) return
    clearSession()
  }

  private fun clearSession() {
    // Nothing of this account stays on the TV's home screen.
    scope.launch { app.nama.androidtv.WatchNext.clear(context) }
    newEpisodes = emptySet()
    user = null
    lists = emptyMap()
    account.edit().remove("token").remove("profile").apply()
    update { it.copy(guest = false) }
    homeCache = null
    playback = null
    contentVersion++
    phase = Phase.Login
  }

  private fun saveUser(u: User) {
    val profile = Json.of(
        mapOf("id" to u.id, "name" to u.name, "type" to u.type, "expiry" to u.expiry, "expiryLocal" to u.expiryLocal, "avatar" to u.avatar?.toString())
            .filterValues { it != null })
    account.edit().putString("token", u.token).putString("profile", profile.serialize()).apply()
  }

  private fun savedUser(): User? {
    val token = account.getString("token", null)?.takeIf { it.isNotEmpty() } ?: return null
    val p = account.getString("profile", null)?.let { runCatching { Json.parse(it) }.getOrNull() } ?: Json.Null
    val fields = mapOf(
        "user_session" to token, "user_id" to p["id"].text, "user_name" to p["name"].text, "user_type" to p["type"].text,
        "user_expire" to p["expiry"].text, "user_expire_local" to p["expiryLocal"].text, "user_avatar" to p["avatar"].text,
    ).filterValues { it != null }
    return User.from(Json.of(fields)) ?: User(token)
  }

  // MARK: Settings

  fun update(change: (Settings) -> Settings) {
    val next = change(settings)
    if (next == settings) return
    val languageChanged = next.lang != settings.lang
    settings = next
    next.save(prefs)
    if (languageChanged) {
      homeCache = null
      contentVersion++
      api.setLanguage(next.lang)
    }
  }

  // MARK: Home

  private fun homeFile(lang: Lang) = File(context.cacheDir, "home-${lang.name}.json")

  /**
   * The home page as it was last fetched, read from disk: shown at once on launch while the fresh
   * one is on its way, and kept when the network is down.
   */
  suspend fun cachedHome(): HomeFeed? = withContext(Dispatchers.IO) {
    val file = homeFile(settings.lang)
    val feed = runCatching { HomeFeed.from(Json.parse(file.readBytes())) }.getOrNull()?.takeIf { !it.isEmpty }
    if (feed != null) withContext(Dispatchers.Main) { homeLoaded = true }
    feed
  }

  /** Home feed, cached for 90 seconds. If the network fails, the last good feed is served instead. */
  suspend fun home(): HomeFeed {
    val lang = settings.lang
    homeCache?.let { (feed, at, l) -> if (l == lang && System.currentTimeMillis() - at < 90_000) return feed }
    val file = homeFile(lang)
    return try {
      api.setLanguage(lang)
      val result = api.get("home_stream")
      val feed = HomeFeed.from(result)
      if (feed.isEmpty) throw APIError.Server("empty")
      homeCache = Triple(feed, System.currentTimeMillis(), lang)
      // Written beside and renamed, so a TV switched off mid-write keeps the last whole page.
      withContext(Dispatchers.IO) {
        runCatching {
          val tmp = File(file.path + ".tmp")
          tmp.writeBytes(result.data())
          tmp.renameTo(file)
        }
      }
      homeLoaded = true
      feed
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      cachedHome() ?: throw e
    }
  }

  // MARK: Continue watching

  /** Nil when 30nama could not be asked, as against an empty list. */
  suspend fun continueWatchingIfReachable(background: Boolean = false): List<Post>? {
    if (!isLoggedIn) return null
    val result = attempt { api.get("continue_watching", background = background) } ?: return null
    val list = dismissals.filter(Post.continueList(result))
    writeDismissals()
    return list
  }

  suspend fun continueWatching(): List<Post> = continueWatchingIfReachable() ?: emptyList()

  /**
   * Takes a title off "continue watching", on 30nama and so on every device. The server's list is
   * cached for a while after that, so the app also keeps the title off itself until it is watched again.
   */
  fun dismissContinue(post: Post) {
    dismissals.dismiss(post)
    writeDismissals()
    scope.launch {
      attempt { api.call("continue_watching_dismiss", mapOf("id" to post.id.toString()), background = true) }
      // The server sends at most twelve, and its list lags behind for a while: asked again
      // shortly and once more later, the titles after the ones taken off move up into the row.
      for (wait in listOf(2_000L, 45_000L)) {
        delay(wait)
        continueVersion++
      }
    }
  }

  private fun readDismissals(): Map<String, Double> =
      store.getString("continueDismissals", null)?.let { s -> runCatching { Json.parse(s).obj?.mapNotNull { (k, v) -> v.double?.let { k to it } }?.toMap() }.getOrNull() }
          ?: emptyMap()

  private fun writeDismissals() {
    store.edit().putString("continueDismissals", Json.of(dismissals.plist).serialize()).apply()
  }

  // MARK: Lists

  /** Reads which titles are on the user's lists, so a title page can show where it stands. */
  suspend fun refreshLists() {
    if (!isLoggedIn) return
    val result = attempt { api.get("user_mylist") } ?: return
    lists = (result.obj ?: emptyMap()).mapValues { (_, v) -> v["post"].array.mapNotNull { it.int }.toSet() }
  }

  fun isListed(post: Post, group: String): Boolean = lists[group]?.contains(post.id) ?: false

  /**
   * Puts a title on one of the user's lists, or takes it off. The server has one action for both:
   * it adds what is missing and removes what is there.
   */
  suspend fun toggleList(post: Post, group: String) {
    if (!isLoggedIn) return
    val was = isListed(post, group)
    fun set(on: Boolean) {
      val now = lists[group].orEmpty()
      lists = lists + (group to if (on) now + post.id else now - post.id)
    }
    // Answer at once; put it back if the server says no.
    set(!was)
    val done = attempt { api.call("user_list", mapOf("action" to "add", "group" to group, "post_id" to post.id.toString())) }?.success ?: false
    if (!done) {
      set(was)
      showToast(loc(S.error))
    }
  }

  // MARK: The TV's home screen

  /** Followed series with an episode out since they were last watched. */
  var newEpisodes by mutableStateOf<Set<Int>>(emptySet())
    private set
  private val news = app.nama.androidtv.data.NewEpisodes(store.getString("newEpisodes", null))

  /** A row of the TV's home screen was chosen: the title to open, and whether to play it on. */
  var pendingOpen by mutableStateOf<Pair<Int, Boolean>?>(null)

  fun openFromLauncher(id: Int, play: Boolean) {
    pendingOpen = id to play
  }

  /**
   * Mirrors "continue watching", and followed series with a new episode, onto the TV's home screen.
   * [continuing] null means 30nama did not answer: the row is left as it is.
   */
  fun mirrorHome(continuing: List<Post>?) {
    if (!isLoggedIn) return
    scope.launch {
      // The series the viewer follows: on their watchlist or their "let me know" list.
      val followed = mutableListOf<Post>()
      for (group in listOf("notify", "watchlist")) {
        attempt { api.get("user_list", mapOf("action" to "list", "group" to group, "page" to "1", "orderby" to "add_list", "order" to "DESC"), background = true) }
            ?.let { followed += app.nama.core.PostPage.from(it).posts.filter { p -> p.isSeries } }
      }
      val resume = continuing.orEmpty().associate { it.id to (it.resume?.time ?: 0.0) }
      app.nama.androidtv.WatchNext.removed(context).forEach { news.dismiss(it) }
      val out = news.look(java.time.Instant.now(), followed.distinctBy { it.id }.associate { it.id to it.nextEpisodeDay }, resume)
      store.edit().putString("newEpisodes", news.json()).apply()
      newEpisodes = out
      val entries = continuing.orEmpty().map { app.nama.androidtv.WatchNext.Entry(it, it.id in out) } +
          followed.filter { it.id in out && continuing.orEmpty().none { c -> c.id == it.id } }.distinctBy { it.id }.map { app.nama.androidtv.WatchNext.Entry(it, true) }
      app.nama.androidtv.WatchNext.sync(context, entries, settings.lang, known = continuing != null)
    }
  }

  /** Debug builds: three titles from home on the TV's home screen, to see the row without an account. */
  fun demoWatchNext() {
    if (!BuildConfig.DEBUG) return
    scope.launch {
      val posts = attempt { home() }?.sections?.flatMap { it.posts }?.take(3).orEmpty()
      app.nama.androidtv.WatchNext.sync(context, posts.mapIndexed { i, p -> app.nama.androidtv.WatchNext.Entry(p, news = i == 2) }, settings.lang, known = true)
    }
  }

  // MARK: Search

  fun recentSearches(): List<String> =
      store.getString("recentSearches", null)?.let { runCatching { Json.parse(it).array.mapNotNull { q -> q.text } }.getOrNull() } ?: emptyList()

  fun saveRecentSearches(list: List<String>) {
    store.edit().putString("recentSearches", Json.of(list).serialize()).apply()
  }

  // MARK: Watched and ratings

  fun setWatched(fileID: String, on: Boolean) {
    if (fileID.isEmpty() || watched.contains(fileID) == on) return
    watched = if (on) watched + fileID else watched - fileID
    store.edit().putStringSet("watched", watched).apply()
  }

  private fun ratingsFile() = File(context.filesDir, "ratings.json")

  private fun readRatings(): RatingBook = runCatching { RatingBook.from(ratingsFile().readBytes()) }.getOrNull() ?: RatingBook()

  /** Rates a title, or clears its rating when [stars] is null. */
  fun rate(post: Post, stars: Double?) {
    val book = RatingBook.from(ratings.encoded()) ?: RatingBook()
    book.rate(post, stars)
    ratings = book
    val bytes = book.encoded()
    scope.launch(Dispatchers.IO) { runCatching { ratingsFile().writeBytes(bytes) } }
  }

  // MARK: Playback and messages

  fun play(request: PlaybackRequest) {
    playback = request
  }

  /** A trailer playing full screen in the app's player (Aparat's; YouTube's open in the YouTube app). */
  var trailer by mutableStateOf<Pair<String, java.net.URI>?>(null)
    private set

  fun playTrailer(title: String, url: java.net.URI) {
    trailer = title to url
  }

  fun closeTrailer() {
    trailer = null
  }

  fun closePlayer() {
    if (playback == null) return
    playback = null
    watchVersion++
  }

  fun showToast(text: String) {
    toastJob?.cancel()
    toast = text
    toastJob = scope.launch {
      delay(3_500)
      toast = null
    }
  }

  companion object {
    const val TAG = "Nama"
  }
}

/** The block's value, or null when it threw; cancellation is let through, unlike `runCatching`. */
inline fun <T> attempt(block: () -> T): T? =
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      null
    }
