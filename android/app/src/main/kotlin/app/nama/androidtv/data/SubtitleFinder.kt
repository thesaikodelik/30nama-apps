package app.nama.androidtv.data

import app.nama.androidtv.attempt
import app.nama.core.HttpRequest
import app.nama.core.Json
import app.nama.core.SubSource
import app.nama.core.SubtitleSite
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Looks on SubSource for Persian subtitles to a title 30nama gives none for, and fetches them (the
 * Apple TV's SubtitleFinder). With a key the user made on SubSource it goes through the site's
 * documented API; without one, or when that fails, through the requests the site's own pages make.
 * SubSource is filtered in Iran: nothing waits on it, and a search that does not answer in a few
 * seconds is let go.
 */
class SubtitleFinder(http: OkHttpClient, private val key: () -> String?) {
  private val http = http.newBuilder().callTimeout(12, TimeUnit.SECONDS).build()

  /** What was found for a title and season, so the next episode does not search again. */
  private val found = HashMap<String, List<SubSource.Candidate>>()
  private val texts = HashMap<String, String>()

  /** The subtitles that may fit, best first; empty when there are none. */
  suspend fun candidates(query: SubSource.Query, release: String, episode: Int?): List<SubSource.Candidate> {
    val cacheKey = "${SubSource.simple(query.title)}|${query.year ?: 0}|${query.season ?: 0}"
    val all = found[cacheKey] ?: run {
      var list = key()?.let { viaAPI(query, it) }.orEmpty()
      if (list.isEmpty()) list = viaPages(query)
      // Nothing found may be the network; asked again for the next episode.
      if (list.isNotEmpty()) found[cacheKey] = list
      list
    }
    return SubSource.ranked(all, release, if (query.isSeries) query.season else null, episode)
  }

  /** The text of one subtitle, taken out of its archive and made readable. */
  suspend fun text(candidate: SubSource.Candidate, season: Int?, episode: Int?): String? {
    val cacheKey = "${candidate.id}|${season ?: 0}|${episode ?: 0}"
    texts[cacheKey]?.let { return it }
    val data = when (val s = candidate.source) {
      is SubSource.Candidate.Source.Api -> key()?.let { fetch(SubSource.downloadRequest(s.id, it)) }
      is SubSource.Candidate.Source.Page -> {
        val html = fetch(HttpRequest(s.url.toString()))?.decodeToString() ?: return null
        SubtitleSite.download(html)?.let { fetch(HttpRequest(it.toString())) }
      }
    } ?: return null
    val text = withContext(Dispatchers.Default) { SubSource.text(data, season, episode) } ?: return null
    texts[cacheKey] = text
    return text
  }

  private suspend fun viaAPI(query: SubSource.Query, key: String): List<SubSource.Candidate> {
    val answer = json(SubSource.searchRequest(query, key)) ?: return emptyList()
    val movie = SubSource.movieID(answer, query) ?: return emptyList()
    val list = json(SubSource.subtitlesRequest(movie, key)) ?: return emptyList()
    return SubSource.candidatesInAPI(list)
  }

  private suspend fun viaPages(query: SubSource.Query): List<SubSource.Candidate> {
    val search = HttpRequest(
        "https://api.subsource.net/v1/movie/search", method = "POST",
        headers = mapOf("Content-Type" to "application/json", "Origin" to SubSource.SITE),
        body = SubSource.searchBody(query),
    )
    val answer = json(search) ?: return emptyList()
    val listing = SubSource.listing(answer, query) ?: return emptyList()
    val html = fetch(HttpRequest(listing.toString()))?.decodeToString() ?: return emptyList()
    return SubtitleSite.entries(html).mapNotNull { entry ->
      runCatching { URI(SubSource.SITE).resolve(entry.path) }.getOrNull()?.let { SubSource.Candidate(SubSource.Candidate.Source.Page(it), entry.releases) }
    }
  }

  private suspend fun fetch(r: HttpRequest): ByteArray? = withContext(Dispatchers.IO) {
    attempt {
      val b = Request.Builder().url(r.url).header("User-Agent", AGENT)
      r.headers.forEach { (k, v) -> b.header(k, v) }
      if (r.method == "POST") b.post((r.body ?: ByteArray(0)).toRequestBody((r.header("Content-Type") ?: "application/json").toMediaType()))
      http.newCall(b.build()).execute().use { if (it.code < 400) it.body?.bytes() else null }
    }
  }

  private suspend fun json(r: HttpRequest): Json? = fetch(r)?.let { attempt { Json.parse(it) } }

  companion object {
    private const val AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
  }
}
