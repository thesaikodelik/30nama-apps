package app.nama.quest

import android.content.Context
import android.util.Log
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.quest.data.Catalog
import app.nama.quest.data.Details
import app.nama.quest.data.Episode
import app.nama.quest.data.LoginCode
import app.nama.quest.data.Row
import app.nama.quest.data.Stream
import app.nama.quest.data.Tile
import app.nama.quest.player.VideoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Where the user is. Each place has its own arrangement of panels in the room. */
enum class Place {
  /** Finding a host and restoring the session: only the logo on the screen. */
  Starting,
  Login,
  Home,
  Title,
  Playing,
}

/**
 * The whole app's state, shared by every panel. Panels are separate windows in the room, so they
 * read one model rather than passing state between them.
 */
class AppModel(
    private val context: Context,
    val catalog: Catalog,
    val video: VideoEngine,
    private val scope: CoroutineScope,
) {
  private val prefs = context.getSharedPreferences("nama", Context.MODE_PRIVATE)

  private val _lang = MutableStateFlow(Lang.valueOf(prefs.getString("lang", null) ?: Lang.en.name))
  val lang: StateFlow<Lang> = _lang.asStateFlow()
  val loc: Loc
    get() = Loc(_lang.value)

  private val _place = MutableStateFlow(Place.Starting)
  val place: StateFlow<Place> = _place.asStateFlow()

  private val _rows = MutableStateFlow<List<Row>>(emptyList())
  val rows: StateFlow<List<Row>> = _rows.asStateFlow()

  private val _rowsFailed = MutableStateFlow(false)
  val rowsFailed: StateFlow<Boolean> = _rowsFailed.asStateFlow()

  /** The tile under the pointer on the wall; the big screen shows its picture. */
  private val _focused = MutableStateFlow<Tile?>(null)
  val focused: StateFlow<Tile?> = _focused.asStateFlow()

  private val _details = MutableStateFlow<Details?>(null)
  val details: StateFlow<Details?> = _details.asStateFlow()

  private val _season = MutableStateFlow(1)
  val season: StateFlow<Int> = _season.asStateFlow()

  private val _login = MutableStateFlow<LoginCode?>(null)
  val login: StateFlow<LoginCode?> = _login.asStateFlow()

  private val _stream = MutableStateFlow<Stream?>(null)
  val stream: StateFlow<Stream?> = _stream.asStateFlow()

  /** The trailer playing behind the title page, if any, and whether its sound is on. */
  private val _trailer = MutableStateFlow<Stream?>(null)
  val trailer: StateFlow<Stream?> = _trailer.asStateFlow()
  private val _trailerSound = MutableStateFlow(false)
  val trailerSound: StateFlow<Boolean> = _trailerSound.asStateFlow()
  private var trailerJob: Job? = null

  private val _notice = MutableStateFlow<String?>(null)
  val notice: StateFlow<String?> = _notice.asStateFlow()

  /** A short message in a small panel, gone after 3.5 s, like the Mac's toast. */
  private val _toast = MutableStateFlow<String?>(null)
  val toast: StateFlow<String?> = _toast.asStateFlow()
  private var toastJob: Job? = null

  fun showToast(text: String) {
    toastJob?.cancel()
    _toast.value = text
    toastJob = scope.launch {
      delay(3_500)
      _toast.value = null
    }
  }

  private var loginJob: Job? = null
  private var detailsJob: Job? = null

  /** The bar over the wall: which section, and what has been opened on top of it. */
  private val _section = MutableStateFlow(Section.home)
  val section: StateFlow<Section> = _section.asStateFlow()
  private val _routes = MutableStateFlow<List<Route>>(emptyList())
  val routes: StateFlow<List<Route>> = _routes.asStateFlow()

  val settings = Settings(prefs)

  /** Lists, ratings, watched episodes, continue-watching dismissals: shared by every screen. */
  val library: Library = Library(prefs) { services?.api }

  /** What wall screens use to reach 30nama; null in the demo, which has only the home rows. */
  val services: Services? =
      (catalog as? app.nama.quest.data.LiveCatalog)?.let { live ->
        Services(context, live.api, prefs, { live.user }, { _lang.value })
      }

  fun go(to: Section) {
    _routes.value = emptyList()
    _section.value = to
    if (_place.value == Place.Title) back()
  }

  fun push(route: Route) {
    if (_routes.value.lastOrNull() == route) return
    _routes.value = _routes.value + route
    if (_place.value == Place.Title) {
      detailsJob?.cancel()
      playJob?.cancel()
      stopTrailer()
      _place.value = Place.Home
    }
  }

  /** Opens a title found on any wall screen. */
  fun openPost(post: app.nama.core.Post) {
    val live = catalog as? app.nama.quest.data.LiveCatalog ?: return
    open(live.tileFor(post))
  }

  /** "Continue as guest", remembered so the code isn't asked for on every launch. */
  fun continueAsGuest() {
    prefs.edit().putBoolean("guest", true).apply()
    goHome()
  }

  /** From the profile or settings, for a guest who now wants to sign in. */
  fun requestSignIn() {
    prefs.edit().putBoolean("guest", false).apply()
    startLogin()
  }

  /** Signs out and goes back to the code, as the Mac's settings do. */
  fun signOut() {
    val live = catalog as? app.nama.quest.data.LiveCatalog ?: return
    scope.launch {
      live.signOut()
      library.clear()
      _rows.value = emptyList()
      _section.value = Section.home
      _routes.value = emptyList()
      startLogin()
    }
  }

  init {
    catalog.lang = _lang.value
    watchPlayback()
  }

  /** The end of a film, or a stream that fails, brings the title page back instead of a dark screen. */
  private fun watchPlayback() {
    scope.launch {
      video.state.collect { st ->
        if (_place.value != Place.Playing) return@collect
        when {
          st.ended && st.duration > 0 -> stop()
          st.error != null -> {
            Log.w(TAG, "playback: ${st.error}")
            stop()
            _notice.value = loc(app.nama.core.S.playError)
          }
        }
      }
    }
  }

  /** Where the intro ends, while the film is inside it; the controls offer to skip there. */
  fun introEnd(position: Double): Double? {
    val s = _stream.value ?: return null
    val start = s.introStart ?: 0.0
    val end = s.introEnd ?: return null
    // A second of slack at the end so the button doesn't flash as the intro finishes.
    return end.takeIf { it > 0 && position >= start && position < end - 1 }
  }

  /** Signed-in users and the demo go straight home; everyone else sees the code first, with a way in as a guest. */
  fun start() {
    scope.launch {
      safely { catalog.prepare() }.onFailure { Log.w(TAG, "prepare: $it") }
      (catalog as? app.nama.quest.data.LiveCatalog)?.continueFilter = library::visibleContinue
      if (catalog.signedIn) launch { library.refreshLists() }
      if (catalog.signedIn || catalog.isDemo || prefs.getBoolean("guest", false)) goHome() else startLogin()
    }
  }

  fun setLang(lang: Lang) {
    if (lang == _lang.value) return
    _lang.value = lang
    catalog.lang = lang
    prefs.edit().putString("lang", lang.name).apply()
    // The catalogue itself is served in the chosen language, so the rows are fetched again.
    _rows.value = emptyList()
    loadHome()
  }

  fun goHome() {
    loginJob?.cancel()
    _place.value = Place.Home
    if (_rows.value.isEmpty()) loadHome()
  }

  fun loadHome() {
    // A newer load (another language) replaces an older one still on its way.
    homeJob?.cancel()
    homeJob = scope.launch {
      _rowsFailed.value = false
      safely { catalog.home() }
          .onSuccess {
            _rows.value = it
            if (_focused.value == null) _focused.value = it.firstOrNull()?.tiles?.firstOrNull()
          }
          .onFailure {
            Log.w(TAG, "home: $it")
            _rowsFailed.value = true
          }
    }
  }

  fun focus(tile: Tile) {
    _focused.value = tile
  }

  fun open(tile: Tile) {
    _focused.value = tile
    _details.value = null
    _notice.value = null
    playJob?.cancel()
    _place.value = Place.Title
    detailsJob?.cancel()
    detailsJob =
        scope.launch {
          safely { catalog.details(tile) }
              .onSuccess {
                _details.value = it
                _season.value = it.resume?.season ?: it.seasons.firstOrNull()?.number ?: 1
                startTrailer(it)
              }
              .onFailure {
                Log.w(TAG, "details: $it")
                _notice.value = loc(app.nama.core.S.error)
              }
        }
  }

  private fun startTrailer(d: Details) {
    trailerJob?.cancel()
    trailerJob =
        scope.launch {
          val url = safely { catalog.trailer(d) }.getOrNull() ?: return@launch
          // Only if the user is still looking at this title.
          if (_place.value != Place.Title || _details.value !== d) return@launch
          val s = Stream(url = url, title = d.tile.title, preview = true)
          _trailerSound.value = false
          _trailer.value = s
          video.selectSubtitle(null)
          video.load(s)
        }
  }

  fun toggleTrailerSound() {
    val on = !_trailerSound.value
    _trailerSound.value = on
    video.setMuted(!on)
  }

  private fun stopTrailer() {
    trailerJob?.cancel()
    if (_trailer.value != null) {
      _trailer.value = null
      video.stop()
    }
  }

  fun chooseSeason(n: Int) {
    _season.value = n
  }

  fun back() {
    when (_place.value) {
      Place.Playing -> stop()
      Place.Title -> {
        detailsJob?.cancel()
        playJob?.cancel()
        stopTrailer()
        _place.value = Place.Home
      }
      Place.Home -> if (_routes.value.isNotEmpty()) _routes.value = _routes.value.dropLast(1)
      else -> Unit
    }
  }

  fun play(episode: Episode? = null) {
    val d = _details.value ?: return
    trailerJob?.cancel()
    // One request at a time: a second press, or an episode after Play, replaces the first.
    playJob?.cancel()
    playJob = scope.launch {
      _notice.value = null
      val s =
          try {
            catalog.stream(d, episode ?: d.resume)
          } catch (e: app.nama.quest.data.Refused) {
            _notice.value = e.message
            return@launch
          } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "stream: $e")
            null
          }
      if (s == null) {
        _notice.value = loc(app.nama.core.S.noStream)
        return@launch
      }
      // The user may have gone back while the stream was being asked for.
      if (_place.value != Place.Title || _details.value !== d) return@launch
      _trailer.value = null
      _stream.value = s
      _place.value = Place.Playing
      // Persian subtitles on by default when the title has them, as in the other apps.
      video.selectSubtitle(s.subtitles.firstOrNull { it.language == "fa" }?.language)
      video.load(s)
      reportWhilePlaying(s)
    }
  }

  private var reportJob: Job? = null
  private var homeJob: Job? = null
  private var playJob: Job? = null

  /** runCatching that lets cancellation through, so a replaced job doesn't read as an error. */
  private inline fun <T> safely(block: () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }

  /** Keeps the server's "continue watching" current, in the 30-second steps it records. */
  private fun reportWhilePlaying(s: Stream) {
    reportJob?.cancel()
    reportJob =
        scope.launch {
          var last = -1L
          while (isActive && _stream.value === s) {
            delay(15_000)
            val st = video.state.value
            val step = (st.position / 30).toLong()
            if (st.duration > 0 && step > 0 && step != last) {
              last = step
              runCatching { catalog.reportProgress(s, st.position, st.duration) }
            }
          }
        }
  }

  fun stop() {
    val s = _stream.value
    val st = video.state.value
    if (s != null && st.duration > 0) scope.launch { runCatching { catalog.reportProgress(s, st.position, st.duration) } }
    reportJob?.cancel()
    video.stop()
    _stream.value = null
    _place.value = if (_details.value != null) Place.Title else Place.Home
  }

  fun startLogin() {
    _place.value = Place.Login
    loginJob?.cancel()
    loginJob =
        scope.launch {
          while (isActive) {
            val code =
                try {
                  catalog.loginCode()
                } catch (e: kotlinx.coroutines.CancellationException) {
                  throw e
                } catch (e: Exception) {
                  Log.w(TAG, "login code: $e")
                  null
                }
            _login.value = code
            if (code == null) {
              delay(5_000)
              continue
            }
            // The same rhythm as the other apps (core's QRLogin); when the code expires a fresh one
            // simply takes its place, since pressing "New code" in a headset is one step too many.
            val until = System.currentTimeMillis() + (app.nama.core.QRLogin.LIFETIME_SECONDS * 1000).toLong()
            while (isActive && System.currentTimeMillis() < until) {
              delay((app.nama.core.QRLogin.POLL_SECONDS * 1000).toLong())
              if (runCatching { catalog.pollLogin(code) }.getOrDefault(false)) {
                _login.value = null
                prefs.edit().putBoolean("guest", false).apply()
                launch { library.refreshLists() }
                val name = services?.user?.name
                showToast(if (name.isNullOrBlank()) loc(app.nama.core.S.welcome) else "${loc(app.nama.core.S.welcome)} $name")
                _rows.value = emptyList()
                goHome()
                return@launch
              }
            }
          }
        }
  }

  /**
   * Debug builds: open a place straight from adb, the way NAMA_OPEN does on the Mac, so each
   * arrangement can be looked at without a controller in hand.
   */
  fun debugJump(where: String) {
    scope.launch {
      when (where) {
        "login" -> startLogin()
        "home" -> {
          while (_place.value == Place.Starting) delay(100)
          goHome()
        }
        "title", "play" -> {
          while (_place.value == Place.Starting) delay(100)
          if (_place.value == Place.Login) goHome()
          while (_rows.value.isEmpty()) delay(100)
          val tile = _rows.value.getOrNull(1)?.tiles?.firstOrNull() ?: return@launch
          open(tile)
          if (where == "play") {
            while (_details.value == null) delay(100)
            play()
          }
        }
      }
    }
  }

  private var resumeTrailer = false

  /** Called when the system takes over the headset (home, menus). */
  fun pauseForSystem() {
    when (_place.value) {
      Place.Playing -> video.setPaused(true)
      Place.Title -> if (_trailer.value != null) {
        resumeTrailer = true
        video.setPaused(true)
      }
      else -> Unit
    }
  }

  /** Back from the system: a trailer carries on; a film stays paused until the user plays it. */
  fun resumeFromSystem() {
    if (resumeTrailer && _place.value == Place.Title && _trailer.value != null) video.setPaused(false)
    resumeTrailer = false
  }

  fun dismissNotice() {
    _notice.value = null
  }

  companion object {
    private const val TAG = "Nama"
  }
}
