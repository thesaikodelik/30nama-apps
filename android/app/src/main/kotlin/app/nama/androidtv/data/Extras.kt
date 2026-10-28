package app.nama.androidtv.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import app.nama.androidtv.attempt
import app.nama.core.Aparat
import app.nama.core.HttpRequest
import app.nama.core.Json
import app.nama.core.Lang
import app.nama.core.LogoIndex
import app.nama.core.Post
import app.nama.core.TMDB
import app.nama.core.TrailerVideo
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * What TMDB and Aparat say about titles: the logo and the trailer (the Apple TV and Mac apps'
 * TMDBStore and AparatStore). With the user's own TMDB key, through the API; without one, read
 * from TMDB's public pages. Where TMDB is filtered (`inIran`) it is never asked: logos come from
 * this repository's daily list, and trailers from Aparat. Nothing here is ever waited on for long.
 */
class Extras(private val context: Context, http: OkHttpClient, private val key: () -> String?, private val filtered: () -> Boolean) {
  private val http = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

  /** What is known, by 30nama post id. Kept for the session; titles do not change their logo. */
  val known = mutableStateMapOf<Int, TMDB.Info>()
  /** Trailers found on Aparat, by post id. */
  val aparat = mutableStateMapOf<Int, Aparat.Video>()
  private val running = HashMap<Int, Deferred<TMDB.Info?>>()
  private val settled = HashSet<Int>()
  private val asked = HashSet<Int>()
  private var index: LogoIndex? = null

  /** What TMDB has for [post], fetched the first time it is asked for. */
  suspend fun info(post: Post, lang: Lang): TMDB.Info? {
    known[post.id]?.let { return it }
    // In a scope of its own: the first asker giving up (a timeout, a page closed) must not cancel
    // the lookup for everyone after, nor leave a cancelled one cached for the session.
    val task = running[post.id]?.takeIf { !it.isCancelled } ?: lookups.async {
      try {
        look(post, lang).also { info ->
          // Failed: there will be no trailer this time, and no one should wait for one.
          if (info == null) settled += post.id
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        settled += post.id
        null
      } finally {
        running.remove(post.id)
      }
    }.also { running[post.id] = it }
    return task.await()
  }

  private val lookups = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

  /**
   * The trailer to offer for [post], waiting up to [patienceMs] and no longer: where TMDB is
   * filtered its requests are neither answered nor refused, and a page must not hold its buttons.
   */
  suspend fun trailer(post: Post, lang: Lang, inIran: Boolean, patienceMs: Long = 4_000): TrailerVideo? = coroutineScope {
    val tmdb = async { withTimeoutOrNull(patienceMs) { info(post, lang)?.trailer ?: waitTrailer(post.id) } }
    if (!inIran) tmdb.await()?.let { return@coroutineScope TrailerVideo.Youtube(it) }
    // In Iran, and wherever TMDB had none: Aparat's.
    lookAparat(post)
    aparat[post.id]?.let { TrailerVideo.Aparat(it.uid) }
  }

  private suspend fun waitTrailer(id: Int): String? {
    while (true) {
      known[id]?.trailer?.let { return it }
      if (id in settled) return null
      delay(250)
    }
  }

  private suspend fun look(post: Post, lang: Lang): TMDB.Info? {
    if (filtered()) return indexedLook(post, lang)
    val key = key() ?: return publicLook(post, lang)
    // TMDB's answer kept on disk, as the Tizen app does: a week when found, a day when not, so
    // each opening of the app does not ask again for every title on the screen.
    when (val kept = stored(post)) {
      is Kept.Found -> {
        val info = TMDB.info(kept.details, lang)
        known[post.id] = info
        settled += post.id
        return info
      }
      Kept.Missed -> {
        settled += post.id
        return null
      }
      Kept.None -> Unit
    }
    var id: Int? = null
    post.imdbID?.let { imdb -> json(TMDB.findRequest(imdb, key))?.let { id = TMDB.idInFind(it, post.isSeries) } }
    // By name: the result that is this title, with its year, then without it (a year off by one at the site).
    val name = post.titleEnglish ?: post.title(Lang.en)
    if (id == null) json(TMDB.searchRequest(name, post.year, post.isSeries, key))?.let { id = TMDB.bestInSearch(it, name, post.year) }
    if (id == null && post.year != null) json(TMDB.searchRequest(name, null, post.isSeries, key))?.let { id = TMDB.bestInSearch(it, name, post.year) }
    // Asked and not found (TMDB answered): kept as a miss for a day.
    if (id == null && System.currentTimeMillis() >= tmdbQuietUntil) store(post, null)
    val details = id?.let { json(TMDB.detailsRequest(it, post.isSeries, key)) } ?: return null
    store(post, details)
    val info = TMDB.info(details, lang)
    known[post.id] = info
    settled += post.id
    return info
  }

  /** Where TMDB is filtered: the logo from the list on GitHub, and no trailer (Aparat has those). */
  private suspend fun indexedLook(post: Post, lang: Lang): TMDB.Info {
    val list = index ?: loadIndex()?.also { index = it }
    val info = TMDB.Info(logo = list?.logo(post.imdbID, lang))
    known[post.id] = info
    settled += post.id
    return info
  }

  private fun storeFile(post: Post) = File(File(context.cacheDir, "tmdb").apply { mkdirs() }, "${post.id}.json")

  private sealed interface Kept {
    data class Found(val details: Json) : Kept
    data object Missed : Kept
    data object None : Kept
  }

  /** The answer kept on disk, while fresh: a week for one found, a day for a miss. */
  private suspend fun stored(post: Post): Kept = withContext(Dispatchers.IO) {
    val file = storeFile(post)
    if (!file.exists()) return@withContext Kept.None
    val age = System.currentTimeMillis() - file.lastModified()
    val text = runCatching { file.readText() }.getOrNull() ?: return@withContext Kept.None
    when {
      text.isEmpty() -> if (age < MISS_FOR) Kept.Missed else Kept.None
      age >= FOUND_FOR -> Kept.None
      else -> runCatching { Json.parse(text.toByteArray()) }.getOrNull()?.let { Kept.Found(it) } ?: Kept.None
    }
  }

  private suspend fun store(post: Post, details: Json?) = withContext(Dispatchers.IO) {
    runCatching { storeFile(post).writeText(details?.serialize() ?: "") }
  }

  /** The list of logos, kept on disk and fetched again once a day; an old copy serves when GitHub does not answer. */
  private suspend fun loadIndex(): LogoIndex? = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "30nama-logos.json")
    val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 24 * 3600_000L
    if (fresh) runCatching { LogoIndex.from(Json.parse(file.readBytes())) }.getOrNull()?.let { return@withContext it }
    val fetched = text(LogoIndex.ADDRESS)
    if (fetched != null) runCatching { LogoIndex.from(Json.parse(fetched)) }.getOrNull()?.let {
      file.writeText(fetched)
      return@withContext it
    }
    runCatching { LogoIndex.from(Json.parse(file.readBytes())) }.getOrNull()
  }

  /** Without a key, the logo and the trailer, read from the site's public pages. */
  private suspend fun publicLook(post: Post, lang: Lang): TMDB.Info? {
    val imdb = post.imdbID ?: return null
    // The redirect lands on the title's own page; that address is all that is wanted of it.
    val page = withContext(Dispatchers.IO) {
      guarded(TMDB.publicPage(imdb).toString()) { http.newCall(Request.Builder().url(TMDB.publicPage(imdb).toString()).head().header("User-Agent", AGENT).build()).execute().use { URI(it.request.url.toString()) } }
    } ?: return null
    var info = TMDB.Info()
    for (language in if (lang == Lang.fa) listOf("fa", "en") else listOf("en")) {
      val html = TMDB.logosPage(page, language)?.let { text(it.toString()) } ?: break
      TMDB.logoInPage(html)?.let {
        info = info.copy(logo = it)
        break
      }
    }
    known[post.id] = info
    // The trailer follows into `known` without holding up the logo.
    TMDB.trailersPage(page)?.let { text(it.toString()) }?.let(TMDB::trailerInPage)?.let { known[post.id] = (known[post.id] ?: info).copy(trailer = it) }
    settled += post.id
    return known[post.id]
  }

  /** Looks for the title's trailer on Aparat, if it has not been looked for already. */
  suspend fun lookAparat(post: Post) {
    if (!asked.add(post.id)) return
    val title = post.titleEnglish ?: post.title(Lang.en)
    val answer = json(Aparat.searchRequest(title))
    if (answer == null) {
      // No answer: tried again next time the title is opened.
      asked -= post.id
      return
    }
    Aparat.trailer(Aparat.videosInSearch(answer), title, post.year)?.let { aparat[post.id] = it }
  }

  /** The file to play. Asked for when it is played: Aparat's addresses are signed and do not last. */
  suspend fun aparatFile(uid: String): URI? = json(Aparat.videoRequest(uid))?.let(Aparat::fileInVideo)

  /**
   * The smallest of the trailer's files worth seeing, for the silent picture behind a title page: it
   * sits under shading, and a weak line or box should not pay for 720p there.
   */
  suspend fun aparatSmallFile(uid: String): URI? = json(Aparat.videoRequest(uid))?.let { answer ->
    val files = answer["video"]["file_link_all"].array
    listOf("360p", "480p", "240p", "720p", "144p", "1080p").firstNotNullOfOrNull { profile ->
      files.firstOrNull { it["profile"].text == profile }?.let { file -> file["urls"].array.firstOrNull()?.url }
    } ?: Aparat.fileInVideo(answer)
  }

  private suspend fun json(r: HttpRequest): Json? = withContext(Dispatchers.IO) {
    guarded(r.url) {
      val b = Request.Builder().url(r.url)
      r.headers.forEach { (k, v) -> b.header(k, v) }
      http.newCall(b.build()).execute().use { if (it.code == 200) Json.parse(it.body!!.bytes()) else null }
    }
  }

  private suspend fun text(url: String): String? = withContext(Dispatchers.IO) {
    guarded(url) { http.newCall(Request.Builder().url(url).header("User-Agent", AGENT).build()).execute().use { if (it.code == 200) it.body!!.string() else null } }
  }

  /** Until when TMDB is not asked: after it failed to answer, a minute, as 30namaTV does. */
  @Volatile private var tmdbQuietUntil = 0L

  /**
   * One request. TMDB not answering (a timeout, a reset connection) holds every request to it for a
   * minute, so a home page of forty titles does not wait out forty timeouts on a filtered line.
   */
  private inline fun <T> guarded(url: String, block: () -> T?): T? {
    val tmdb = url.contains("themoviedb.org")
    if (tmdb && System.currentTimeMillis() < tmdbQuietUntil) return null
    return try {
      block()
    } catch (e: java.io.IOException) {
      if (tmdb) tmdbQuietUntil = System.currentTimeMillis() + 60_000
      null
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      null
    }
  }

  companion object {
    private const val AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
  }
}

private const val FOUND_FOR = 7 * 24 * 3600_000L
private const val MISS_FOR = 24 * 3600_000L
