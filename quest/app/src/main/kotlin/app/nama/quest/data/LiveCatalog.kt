package app.nama.quest.data

import android.content.Context
import android.util.Log
import app.nama.core.APIClient
import app.nama.core.APIConfig
import app.nama.core.SiteConfig
import app.nama.quest.BuildConfig
import app.nama.core.Aparat
import app.nama.core.HttpURLConnectionTransport
import app.nama.core.Transport
import app.nama.core.AudioMix
import app.nama.core.Json
import app.nama.core.Lang
import app.nama.core.Loc
import app.nama.core.LogoIndex
import app.nama.core.Post
import app.nama.core.S
import app.nama.core.StreamItem
import app.nama.core.StreamResult
import app.nama.core.User
import app.nama.core.WatchProgress
import app.nama.core.continueWatching
import app.nama.core.home
import app.nama.core.profile
import app.nama.core.qrLogin
import app.nama.core.qrLoginCode
import app.nama.core.reportProgress
import app.nama.core.signIn
import app.nama.core.single
import app.nama.core.stream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The catalogue as 30nama serves it, through app.nama.core (the same logic as the Mac and Windows apps). */
/**
 * [transport] and [siteKey] are for the panel snapshots on the computer (PanelShots), which answer
 * every request from the shared test fixtures instead of the network.
 */
class LiveCatalog(
    private val context: Context,
    private val transport: Transport? = null,
    siteKey: String? = BuildConfig.SITE_KEY.ifEmpty { null },
) : Catalog {
  override val isDemo = false
  override var lang: Lang = Lang.en

  val api = APIClient(APIConfig(site = SiteConfig(key = siteKey)), transport)
  val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
  private val ready = Mutex()
  private var prepared = false
  var user: User? = null
    private set
  private var logos: LogoIndex? = null

  /** Settings the stream depends on (AppModel sets them from Settings). */
  var preferAdaptive: () -> Boolean = { false }
  var audioMix: () -> AudioMix = { AudioMix.stereo }

  /** Hides what the user took off continue watching (Library), applied to that row. */
  var continueFilter: (List<Post>) -> List<Post> = { it }

  /** What a tile, a details page and a stream point back to. */
  private val posts = HashMap<String, Post>()
  private val streams = HashMap<Int, StreamResult>()

  override val signedIn: Boolean
    get() = user != null

  override suspend fun prepare() {
    ready.withLock {
      if (prepared) return
      val started = System.currentTimeMillis()
      Log.i("Nama", "bootstrap…")
      val host = api.bootstrap(prefs.getString("host", null))
      Log.i("Nama", "bootstrap → $host in ${System.currentTimeMillis() - started} ms")
      prefs.edit().putString("host", host).apply()
      prefs.getString("token", null)?.let { token ->
        api.setToken(token)
        user = restore(token)
      }
      prepared = true
    }
  }

  /**
   * The saved session's user. Only the server saying the session is over signs the user out; a
   * network failure on the way keeps the token, so waking up offline doesn't cost a sign-in.
   */
  private suspend fun restore(token: String): User? {
    val e =
        try {
          api.call("user")
        } catch (e: kotlinx.coroutines.CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w("Nama", "profile: $e")
          return User(token)
        }
    if (e.success) return User.from(e.result, fallbackToken = token) ?: User(token)
    if (APIClient.isLoginRequired(e.message)) {
      api.setToken(null)
      prefs.edit().remove("token").apply()
      return null
    }
    return User(token)
  }

  override suspend fun home(): List<Row> {
    prepare()
    // Read once: a language switch while this is on its way starts a load of its own.
    val lang = lang
    api.setLanguage(lang)
    val loc = Loc(lang)
    val feed = api.home()
    val rows = mutableListOf<Row>()
    if (signedIn) {
      runCatching { api.continueWatching(background = true) }
          .getOrNull()
          ?.let(continueFilter)
          ?.takeIf { it.isNotEmpty() }
          ?.let { list -> rows += Row("continue", loc(S.continueWatching), list.map { tile(it, loc, lang) }, landscape = true) }
    }
    feed.heroes.takeIf { it.isNotEmpty() }?.let { rows += Row("featured", loc(S.featured), it.map { p -> tile(p, loc, lang) }, landscape = true) }
    for (section in feed.sections) rows += Row(section.key, loc.title(forSection = section.key), section.posts.map { tile(it, loc, lang) })
    // The server can list a title twice in one row; the lists key their items by id.
    return rows.map { r -> r.copy(tiles = r.tiles.distinctBy { it.id }) }
  }

  /** A title found anywhere (search, a list, a person's page) as the tile the title page opens from. */
  fun tileFor(post: Post): Tile = tile(post, Loc(lang), lang)

  /** Signs out on the server and forgets the session here. */
  suspend fun signOut() {
    runCatching { api.call("logout") }
    api.setToken(null)
    user = null
    prefs.edit().remove("token").apply()
  }

  private fun tile(post: Post, loc: Loc, lang: Lang): Tile {
    val key = post.id.toString()
    posts[key] = post
    val resume = post.resume
    return Tile(
        id = key,
        title = post.title(lang),
        originalTitle = post.titleEnglish,
        poster = post.poster.url(300.0)?.toString(),
        backdrop = (post.cover ?: post.poster.url(428.0))?.toString(),
        year = post.year?.toString(),
        genres = post.genres.map { it.name },
        rating = post.imdbScore?.let { String.format(Locale.ROOT, "%.1f", it) },
        isSeries = post.isSeries,
        progress = resume?.progress?.toFloat()?.takeIf { it > 0f },
        note = resume?.let { r ->
          listOfNotNull(
                  if (post.isSeries && r.season != null && r.episode != null) loc.episodeTag(r.season!!, r.episode!!) else null,
                  r.remainingMinutes?.let { loc.num(it) + " " + loc(S.minLeft) },
              )
              .joinToString(" · ")
              .ifEmpty { null }
        },
    )
  }

  override suspend fun details(tile: Tile): Details {
    prepare()
    val id = tile.id.toInt()
    val lang = lang
    val post = api.single(id) ?: posts[tile.id] ?: error("no title")
    posts[tile.id] = post
    val loc = Loc(lang)
    val result = runCatching { api.stream(id, signedIn).result }.getOrNull()
    if (result != null) streams[id] = result
    val seasons =
        result?.items.orEmpty().groupBy { it.season }.toSortedMap().map { (n, items) ->
          Season(n, items.map { episodeOf(it) })
        }
    val resumeItem = result?.let { r -> r.items.getOrNull(r.startIndex).takeIf { r.resume != null } }
    return Details(
        tile = tile(post, loc, lang).copy(backdrop = (post.cover ?: post.poster.url(428.0))?.toString() ?: tile.backdrop),
        overview = post.plot(lang).ifBlank { null },
        runtimeMinutes = post.runtimeMinutes,
        logo = logoIndex()?.logo(post.imdbID, lang)?.toString(),
        director = post.directors.firstOrNull(),
        people = post.cast.distinctBy { it.name }.take(16).map { Person(it.name, it.role ?: loc(S.cast), it.poster?.url(160.0)?.toString()) },
        seasons = if (post.isSeries) seasons else emptyList(),
        resume = resumeItem?.takeIf { post.isSeries }?.let(::episodeOf),
        post = post,
        result = result,
    )
  }

  private fun episodeOf(item: StreamItem) =
      Episode(
          id = item.id,
          season = item.season,
          number = item.episodeNumber,
          title = item.title(lang) ?: "",
          minutes = item.durationMinutes,
      )

  override suspend fun stream(details: Details, episode: Episode?): Stream? {
    val id = details.tile.id.toInt()
    val post = posts[details.tile.id] ?: return null
    val answer = api.stream(id, signedIn)
    val result = answer.result ?: throw Refused(answer.refusal ?: Loc(lang)(if (signedIn) S.noStream else S.loginToWatch))
    streams[id] = result
    val item = (episode?.let { e -> result.items.firstOrNull { it.id == e.id } } ?: result.items.getOrNull(result.startIndex)) ?: return null
    // The highest fixed rendition unless the user asked for the adaptive one, as on the Mac; the
    // headset plays two channels, so the server's own stereo mix unless asked otherwise.
    val quality = item.quality(preferAdaptive = preferAdaptive()) ?: return null
    // The Mac's rule: resume only past the first 5 s and not inside the last 3% (the credits).
    val resumeAt =
        result.resume
            ?.takeIf { it.fileID == item.fileID && it.time > 5 && (it.duration <= 0 || it.time < it.duration * 0.97) }
            ?.time ?: 0.0
    val loc = Loc(lang)
    return Stream(
        url = quality.url(audioMix()).toString(),
        title = item.title(lang)?.let { "${post.title(lang)} · $it" } ?: post.title(lang),
        subtitles = item.subtitles.map { (code, uri) -> SubtitleTrack(uri.toString(), code, if (code == "fa") "فارسی" else "English") },
        startAt = resumeAt,
        introStart = item.introStart,
        introEnd = item.introEnd,
        recapStart = item.recapStart,
        recapEnd = item.recapEnd,
        creditsStart = item.creditsStart,
        episode = if (post.isSeries) episodeOf(item) else null,
        episodeLine = if (post.isSeries) loc.episodeTag(item.season, item.episodeRaw ?: item.episodeNumber.toString()) else null,
        fileId = item.fileID.ifEmpty { null },
        ref = Playing(post, item),
    )
  }

  override suspend fun reportProgress(stream: Stream, seconds: Double, duration: Double) {
    if (!signedIn) return
    val ref = stream.ref as? Playing ?: return
    api.reportProgress(WatchProgress(ref.post, ref.item, user?.id, seconds, duration))
  }

  override suspend fun loginCode(): LoginCode {
    prepare()
    val c = api.qrLoginCode()
    return LoginCode(c.code, "30nama.com/auth")
  }

  override suspend fun pollLogin(code: LoginCode): Boolean {
    val token = api.qrLogin(code.code) ?: return false
    user = api.signIn(token)
    prefs.edit().putString("token", token).apply()
    return true
  }

  /**
   * A trailer from Aparat, the way the Mac finds one when TMDB has none: a search for the English
   * title, the best-matching video, then its file, asked for now because Aparat's links expire.
   */
  override suspend fun trailer(details: Details): String? {
    val post = posts[details.tile.id] ?: return null
    val title = post.titleEnglish ?: post.title(Lang.en)
    val net = transport ?: HttpURLConnectionTransport()
    suspend fun json(r: app.nama.core.HttpRequest) =
        runCatching { net.send(r).takeIf { it.status == 200 }?.let { Json.parse(it.body) } }.getOrNull()
    val found = json(Aparat.searchRequest(title))?.let { Aparat.trailer(Aparat.videosInSearch(it), title, post.year) } ?: return null
    return json(Aparat.videoRequest(found.uid))?.let(Aparat::fileInVideo)?.toString()
  }

  /** The logo list, kept for a day in the cache like the Mac does. */
  private suspend fun logoIndex(): LogoIndex? {
    logos?.let {
      return it
    }
    return withContext(Dispatchers.IO) {
      val file = File(context.cacheDir, "30nama-logos.json")
      val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 24 * 3600_000L
      val text =
          if (fresh) file.readText()
          else
              runCatching {
                    val conn = URI(LogoIndex.ADDRESS).toURL().openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 20_000
                    conn.inputStream.use { it.readBytes().decodeToString() }.also { file.writeText(it) }
                  }
                  .onFailure { Log.w("Nama", "logo index: $it") }
                  .getOrNull() ?: file.takeIf { it.exists() }?.readText()
      text?.let { runCatching { LogoIndex.from(Json.parse(it)) }.getOrNull() }.also { logos = it }
    }
  }
}

private class Playing(val post: Post, val item: StreamItem)

/** The server would not stream this title to this account; [message] says why. */
class Refused(message: String) : Exception(message)
