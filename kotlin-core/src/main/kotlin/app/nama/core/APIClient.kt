package app.nama.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class APIConfig(
    val discoveryURLs: List<String> = listOf(
        "https://pub-ac69cf865ce145f8b4c1ed87dc2a9fb7.r2.dev/sdk-endpoint.json",
        "https://30update.s3.ir-thr-at1.arvanstorage.ir/sdk-endpoint.json",
    ),
    val fallbackHosts: List<String> = listOf(
        "https://world.hana-api.com/api/v2",
        "https://ir.kazem-api.com/api/v2",
    ),
    /** The hosts that answer only from inside Iran; the endpoint list says the same with "IR". */
    val iranHosts: Set<String> = setOf("https://ir.kazem-api.com/api/v2"),
    /** WebTV client key first, Android TV key as a fallback. */
    val apiKeys: List<String> = listOf(
        "83hRzBabjDnuv9SmV2GFaftpxjhZSHXYe7UnBuX5DdANdpzPvXu46jvKZHPb97As",
        "VdapnkLG9EHsSmF8yeKcb4253RAuN6YX",
    ),
    val clientVersion: String = "1.0.0",
    /** Waits between retries of a transient failure, in seconds. Its count is the number of retries. */
    val backoff: List<Double> = listOf(1.0, 3.0),
    /** Seconds between the starts of two requests. */
    val requestSpacing: Double = 0.1,
    /** The website's own API, for what only the site has. */
    val site: SiteConfig = SiteConfig(),
)

sealed class APIError(message: String) : Exception(message) {
    /**
     * Network failure, timeout, rate limiting or a non-JSON body. Worth retrying. [rateLimited]:
     * Cloudflare's rate limit (429, or its "error code: 1015" page), which pauses the whole gate.
     */
    data class Transient(val reason: String, val retryAfter: Double, val rateLimited: Boolean = false) : APIError(reason)

    /** The API answered `success: false`. */
    data class Server(val reason: String) : APIError(reason)

    /** No host answered the connection test. */
    data object Unreachable : APIError("unreachable")
}

data class Envelope(val success: Boolean, val message: String?, val result: Json)

/**
 * Limits concurrency and spacing of API calls.
 *
 * 30nama sits behind Cloudflare rate limiting, so every call goes through one gate: at most two
 * in flight, a short gap between starts, screen calls ahead of background ones, and a shared
 * pause after a rate-limit response. Background calls hold at most [backgroundLimit] of the slots
 * between them, so a page never waits behind progress reports and look-ups (learnt from 30namaTV,
 * android/docs/research.md, "شبکه"); the total stays as it was, so Cloudflare sees no more at once.
 */
class RequestGate(private val limit: Int = 2, spacing: Double = 0.1, private val backgroundLimit: Int = maxOf(1, limit - 1)) {
    private val spacingNanos = (spacing * 1e9).toLong()
    private val lock = Any()
    private var inFlight = 0
    private var backgroundInFlight = 0
    private val core = ArrayDeque<CompletableDeferred<Unit>>()
    private val background = ArrayDeque<CompletableDeferred<Unit>>()
    private var nextStart = Long.MIN_VALUE
    private var pausedUntil = Long.MIN_VALUE

    suspend fun acquire(background: Boolean) {
        var waiter: CompletableDeferred<Unit>? = null
        synchronized(lock) {
            if (inFlight < limit && (!background || backgroundInFlight < backgroundLimit)) {
                inFlight++
                if (background) backgroundInFlight++
            } else {
                waiter = CompletableDeferred<Unit>().also { (if (background) this.background else core).addLast(it) }
            }
        }
        waiter?.let { w ->
            try {
                w.await()
            } catch (e: CancellationException) {
                // Given up while queued: leave the queue, or pass on the slot if it had just been handed over.
                val handedOver = synchronized(lock) { !core.remove(w) && !this.background.remove(w) }
                if (handedOver) release(background)
                throw e
            }
        }
        val wait: Long
        synchronized(lock) {
            val now = System.nanoTime()
            val start = maxOf(now, nextStart, pausedUntil)
            nextStart = start + spacingNanos
            wait = start - now
        }
        if (wait > 0) {
            try {
                delay((wait + 999_999) / 1_000_000)
            } catch (e: CancellationException) {
                release(background)
                throw e
            }
        }
    }

    /** Gives back a slot taken with the same [background], and hands it on: screen calls first. */
    fun release(background: Boolean = false) {
        synchronized(lock) {
            inFlight--
            if (background) backgroundInFlight--
            val next = core.removeFirstOrNull()
                ?: if (backgroundInFlight < backgroundLimit) this.background.removeFirstOrNull()?.also { backgroundInFlight++ } else null
            if (next != null) {
                inFlight++
                next.complete(Unit)
            }
        }
    }

    fun pause(seconds: Double) {
        synchronized(lock) {
            pausedUntil = maxOf(pausedUntil, System.nanoTime() + (seconds * 1e9).toLong())
        }
    }
}

/**
 * RPC client for the 30nama API: `POST {host}/action/{name}` with a form body, answered by a
 * `{success, msg, result}` envelope. Safe to share between coroutines; one per app.
 */
class APIClient(val config: APIConfig = APIConfig(), transport: Transport? = null) {
    private val transport: Transport = transport ?: HttpURLConnectionTransport()
    private val gate = RequestGate(spacing = config.requestSpacing)

    /**
     * The website's own gate: its host is not the app API's, and a request of its that hangs, as
     * they do where it is filtered, must not hold a place the app's own requests are waiting for.
     */
    private val siteGate = RequestGate(spacing = config.requestSpacing)

    @Volatile private var discoveredIran: Set<String> = emptySet()

    /** Until when (`System.nanoTime`) the website is taken to be out of reach (see `site`). */
    @Volatile private var siteOutUntil = Long.MIN_VALUE

    @Volatile var host: String? = null
        private set

    @Volatile private var key: String = config.apiKeys[0]
    @Volatile private var token = ""
    @Volatile private var language = Lang.en.catalogue
    @Volatile private var onSessionExpired: (() -> Unit)? = null
    private val checkingSession = AtomicBoolean(false)

    /**
     * The host that answered is one that answers only inside Iran: the user is there, without a
     * VPN, and TMDB, YouTube and the website are filtered. Asked for no longer than is needed.
     */
    val inIran: Boolean
        get() {
            val host = host ?: return false
            return host in config.iranHosts || host in discoveredIran
        }

    fun setToken(token: String?) { this.token = token ?: "" }
    fun setLanguage(lang: Lang) { language = lang.catalogue }

    /** Called, on whatever thread noticed, when the server says the session is gone (see `call`). */
    fun setSessionExpiredHandler(handler: (() -> Unit)?) { onSessionExpired = handler }
    fun useHost(host: String) { this.host = host }

    // MARK: Endpoint discovery

    /**
     * Finds a working host and key: fetch the published endpoint list (falling back to the
     * built-in one), then run the `test` action against every host and take the first that answers.
     */
    suspend fun bootstrap(cachedHost: String? = null): String {
        val hosts = (discoverHosts() + config.fallbackHosts).distinct().toMutableList()
        if (cachedHost != null && cachedHost !in hosts) hosts.add(0, cachedHost)

        for (candidate in config.apiKeys) {
            val winner = firstOf(hosts) { h ->
                val e = try {
                    send(h, candidate, "test", emptyMap(), "", 8.0)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (e?.success == true) h else null
            }
            if (winner != null) {
                host = winner
                key = candidate
                return winner
            }
        }
        throw APIError.Unreachable
    }

    private suspend fun discoverHosts(): List<String> {
        val t = System.currentTimeMillis() / 1000
        val found = firstOf(config.discoveryURLs.map { "$it?t=$t" }) { url ->
            val list = try {
                Json.parse(transport.send(HttpRequest(url, timeoutSeconds = 6.0)).body)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            list?.array?.takeIf { it.isNotEmpty() }
        } ?: emptyList()
        discoveredIran = discoveredIran + found.filter { it["location"].text == "IR" }.mapNotNull { it["host"].text }
        return found.mapNotNull { it["host"].text }
    }

    /** Runs `work` on every item at once and returns the first non-nil answer, cancelling the rest. */
    private suspend fun <T, R : Any> firstOf(items: List<T>, work: suspend (T) -> R?): R? = coroutineScope {
        val winner = CompletableDeferred<R?>()
        val jobs = items.map { item -> launch { work(item)?.let { winner.complete(it) } } }
        launch {
            jobs.forEach { it.join() }
            winner.complete(null)
        }
        val result = winner.await()
        coroutineContext.cancelChildren()
        result
    }

    // MARK: Calls

    /**
     * Runs an action and returns its envelope, including unsuccessful ones. Throws only when the
     * server could not be reached or kept answering with something that is not the API.
     * @param background no screen is waiting for it, so it gives way to calls one is.
     */
    suspend fun call(action: String, params: Map<String, String> = emptyMap(), background: Boolean = false): Envelope {
        val host = host ?: throw APIError.Unreachable
        val retries = if (action in noRetry) 0 else config.backoff.size
        val envelope = retrying(gate, background, retries) { send(host, key, action, params, token, 20.0) }
        if (!envelope.success && action != "user" && isLoginRequired(envelope.message)) confirmSessionExpired()
        return envelope
    }

    /** Runs an action and returns its `result`, throwing `APIError.Server` when it did not succeed. */
    suspend fun get(action: String, params: Map<String, String> = emptyMap(), background: Boolean = false): Json {
        val e = call(action, params, background)
        if (!e.success) throw APIError.Server(e.message ?: "error")
        return e.result
    }

    /** Whether the website's API can be asked at all: a build without its key leaves out what only the site has. */
    val hasSite: Boolean get() = config.site.key != null

    /**
     * Runs an action of the website's API (`SiteConfig`) and returns its `result`, throwing
     * `APIError.Server` when it did not succeed. Through a gate like the app API's, with the same
     * retries: both are behind the same Cloudflare. Its parameters are in `path`, the rest in `form`.
     *
     * Iran filters the website (its API with it), and on some networks there a request to it is
     * neither answered nor refused. One that gets no answer at all is not tried again, and for
     * `SITE_OUT_FOR` the site is not asked: what needs it fails at once with `APIError.Unreachable`
     * instead of every page waiting out the same silence. `siteAgain()` lifts that.
     */
    /**
     * [signed]: as the member, with the session's token in the same `c-token` header the website
     * sends (a rating); otherwise as the website does for anyone.
     */
    suspend fun site(path: String, form: Map<String, String> = emptyMap(), background: Boolean = false, signed: Boolean = false): Json {
        val key = config.site.key ?: throw APIError.Unreachable
        if (!siteInReach) throw APIError.Unreachable
        val request = HttpRequest(
            url = config.site.host + "/action/" + path,
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "c-api-key" to key,
                "c-platform" to config.site.platform,
            ) + (if (signed && token.isNotEmpty()) mapOf("c-token" to token) else emptyMap()),
            body = formEncode(form).toByteArray(Charsets.UTF_8),
            timeoutSeconds = 12.0,
        )
        val e = retrying(siteGate, background, config.backoff.size) {
            val response = try {
                transport.send(request)
            } catch (e: CancellationException) {
                // Given up by whoever asked (a page closed) says nothing about the site.
                throw e
            } catch (_: Exception) {
                siteOutUntil = System.nanoTime() + (SITE_OUT_FOR * 1e9).toLong()
                throw APIError.Unreachable
            }
            envelope(response, path)
        }
        if (!e.success) throw APIError.Server(e.message ?: "error")
        return e.result
    }

    /** Whether the website answered the last time it was asked, or has not been asked for a while. */
    val siteInReach: Boolean get() = siteOutUntil == Long.MIN_VALUE || System.nanoTime() - siteOutUntil >= 0

    /** Asked for again by the user (a retry button, perhaps with a VPN now on): ask the site again at once. */
    fun siteAgain() { siteOutUntil = Long.MIN_VALUE }

    /** One request through the gate, tried again after a transient failure. */
    private suspend fun retrying(gate: RequestGate, background: Boolean, retries: Int, request: suspend () -> Envelope): Envelope {
        var attempt = 0
        while (true) {
            gate.acquire(background)
            val failure: APIError.Transient
            try {
                return request()
            } catch (e: APIError.Transient) {
                // A rate limit pauses every call behind it, retried or not (a write is never
                // replayed), so the next one does not walk into the same wall: 1 s, then 3, or
                // what Cloudflare asks, up to 10.
                if (e.rateLimited) gate.pause(maxOf(config.backoff[minOf(attempt, config.backoff.size - 1)], e.retryAfter))
                if (attempt >= retries) throw e
                failure = e
            } finally {
                gate.release(background)
            }
            val wait = maxOf(config.backoff[attempt], failure.retryAfter)
            gate.pause(wait)
            delay((wait * 1000).toLong())
            attempt++
        }
    }

    /**
     * "Login required" on an action can also mean "this needs a subscription". Only treat the
     * session as gone when the profile call says so too.
     */
    private suspend fun confirmSessionExpired() {
        val host = host ?: return
        if (token.isEmpty() || !checkingSession.compareAndSet(false, true)) return
        try {
            val e = try {
                send(host, key, "user", emptyMap(), token, 20.0)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return
            }
            if (e.success || !isLoginRequired(e.message)) return
            token = ""
            onSessionExpired?.invoke()
        } finally {
            checkingSession.set(false)
        }
    }

    private suspend fun send(host: String, key: String, action: String, params: Map<String, String>, token: String, timeout: Double): Envelope {
        val request = HttpRequest(
            url = "$host/action/$action",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "c-api-key" to key,
                "c-token" to token,
                "c-version-number" to config.clientVersion,
                "c-language" to language,
            ),
            body = formEncode(params).toByteArray(Charsets.UTF_8),
            timeoutSeconds = timeout,
        )
        val response = try {
            transport.send(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw APIError.Transient("$action: ${e.message ?: e.javaClass.simpleName}", 0.0)
        }
        return envelope(response, action)
    }

    private fun envelope(response: HttpResponse, action: String): Envelope {
        // A progress report is answered with nothing at all: accepted, with nothing to say.
        if (response.body.isEmpty() && response.status in 200..299) return Envelope(true, null, Json.Null)
        val json = try {
            Json.parse(response.body)
        } catch (_: Json.ParseException) {
            null
        }
        if (json?.obj == null) {
            // Cloudflare answers rate limiting and outages with HTML or plain text. A rate limit is
            // never a reason to change servers; it is waited out.
            val retryAfter = minOf(response.header("Retry-After")?.asciiDouble() ?: 0.0, 10.0)
            val limited = response.status == 429 || String(response.body, Charsets.UTF_8).contains("error code: 1015")
            throw APIError.Transient("$action: HTTP ${response.status}, not JSON", retryAfter, rateLimited = limited)
        }
        return Envelope(json["success"].bool, json["msg"].text, json["result"])
    }

    companion object {
        /** Actions that must not be replayed. */
        val noRetry: Set<String> = setOf("observer", "logout", "qrlogin_login")

        /** How long, in seconds, the website is not asked again after it gave no answer at all. */
        const val SITE_OUT_FOR: Double = 180.0

        fun isLoginRequired(message: String?): Boolean {
            val m = message?.lowercase() ?: return false
            return m.contains("login required") || m.contains("وارد حساب کاربری")
        }

        /** Keys in order, every byte outside the unreserved characters escaped. */
        fun formEncode(params: Map<String, String>): String {
            fun enc(s: String) = percentEncode(s) { it.isUnreserved() }
            return params.entries.sortedBy { it.key }.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        }
    }
}
