package app.nama.core

/**
 * The hosts the app talks to that Iran filters. Seen on 25 October 2026 from probes on Iranian
 * networks: their names resolve to the filter's own 10.10.34.x. Nothing that comes from these
 * may hold up a page, since there a request to them is often neither answered nor refused.
 */
object FilteredHosts {
    val suffixes = listOf(
        "themoviedb.org", "tmdb.org", "fanart.tv",
        "youtube.com", "youtube-nocookie.com", "googlevideo.com", "ytimg.com",
        "subsource.net",
        // The website, its API and its image host; the app's own API and images are elsewhere.
        "30nama.com",
    )

    fun contains(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return suffixes.any { h == it || h.endsWith(".$it") }
    }
}

/**
 * The app as it is on an Iranian network that drops what it filters, as Irancell does: a request
 * to a filtered host gets no answer and fails once its time is up. For trying the app's behaviour
 * there from anywhere (the Swift side's `NAMA_FILTERED=1`); wrap the real transport in it.
 */
class FilteredTransport(private val inner: Transport) : Transport {
    override suspend fun send(request: HttpRequest): HttpResponse {
        val host = runCatching { java.net.URI(request.url).host }.getOrNull()
        if (FilteredHosts.contains(host)) {
            kotlinx.coroutines.delay((minOf(request.timeoutSeconds, 60.0) * 1000).toLong())
            throw java.net.SocketTimeoutException("timed out (filtered)")
        }
        return inner.send(request)
    }
}
