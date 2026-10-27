package app.nama.core

import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

/** A fixture shared with the Swift tests (Tests/NamaCoreTests/Fixtures), read off the test classpath. */
fun fixtureBytes(file: String): ByteArray =
    (Thread.currentThread().contextClassLoader.getResourceAsStream(file) ?: error("no fixture $file")).use { it.readBytes() }

fun fixture(name: String): Json = Json.parse(fixtureBytes("$name.json"))

/** A JSON object from Kotlin values, as the Swift tests build one from a dictionary literal. */
fun obj(vararg pairs: Pair<String, Any?>): Json = Json.of(linkedMapOf(*pairs))

fun parse(text: String): Json = Json.parse(text)

/** Scripted network: answers each request from a handler and records what was asked. */
class FakeNetwork(private val handler: (action: String, host: String) -> Answer) {
    data class Answer(val status: Int, val body: String, val headers: Map<String, String> = emptyMap())
    data class Logged(val action: String, val host: String, val headers: Map<String, String>, val body: String)

    private val log = mutableListOf<Logged>()

    val calls: List<String> get() = synchronized(log) { log.map { it.action } }
    val last: Logged? get() = synchronized(log) { log.lastOrNull() }

    val transport = Transport { request ->
        val url = URI(request.url)
        val action = if ((url.path ?: "").contains("/action/")) url.lastPathComponent else "discovery"
        val host = url.host ?: ""
        val answer = synchronized(log) {
            log.add(Logged(action, host, request.headers, String(request.body ?: ByteArray(0), Charsets.UTF_8)))
            handler(action, host)
        }
        if (answer.status == 0) throw java.net.SocketTimeoutException("timed out")
        HttpResponse(answer.status, answer.headers, answer.body.toByteArray(Charsets.UTF_8))
    }
}

fun answer(status: Int, body: String, headers: Map<String, String> = emptyMap()) = FakeNetwork.Answer(status, body, headers)

class Counter(n: Int) {
    private val n = AtomicInteger(n)
    val value: Int get() = n.get()

    /** Decrements while positive; true if it did. */
    fun take(): Boolean {
        while (true) {
            val v = n.get()
            if (v <= 0) return false
            if (n.compareAndSet(v, v - 1)) return true
        }
    }

    fun add(): Int = n.incrementAndGet()
    fun sub() { n.decrementAndGet() }
    fun raise(to: Int) { n.accumulateAndGet(to) { a, b -> maxOf(a, b) } }
}

class Recorder {
    private val items = mutableListOf<String>()
    val values: List<String> get() = synchronized(items) { items.toList() }
    fun add(s: String) { synchronized(items) { items.add(s) } }
}
