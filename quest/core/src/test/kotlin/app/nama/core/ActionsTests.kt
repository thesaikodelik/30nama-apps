package app.nama.core

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder

/** Not in the Swift tests: the named actions (Actions.kt) and the default transport exist only here. */
class ActionsTests {
    private fun client(handler: (action: String, form: Map<String, String>) -> String): Pair<APIClient, FakeNetwork> {
        lateinit var net: FakeNetwork
        val forms = mutableMapOf<String, Map<String, String>>()
        net = FakeNetwork { action, _ -> answer(200, handler(action, forms[action] ?: emptyMap())) }
        val recording = Transport { request ->
            val action = request.url.substringAfterLast('/')
            forms[action] = String(request.body ?: ByteArray(0)).split('&').filter { it.isNotEmpty() }.associate {
                val (k, v) = it.split('=', limit = 2)
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            }
            net.transport.send(request)
        }
        val api = APIClient(APIConfig(backoff = listOf(0.01), requestSpacing = 0.0), recording)
        api.useHost("https://h.example.com/api/v2")
        return api to net
    }

    @Test fun testSigningInWithACode() = runBlocking {
        var confirmed = false
        val (api, net) = client { action, form ->
            when (action) {
                "qrlogin_code" -> """{"success":true,"result":{"code":"AB12"}}"""
                "qrlogin_login" -> if (confirmed && form["auth_code"] == "AB12") """{"success":true,"result":{"user_session":"session-token"}}"""
                else """{"success":false,"msg":"not yet","result":null}"""
                "user" -> """{"success":true,"result":{"user_session":"session-token","user_name":"sample","user_type":"golden"}}"""
                else -> """{"success":false}"""
            }
        }
        val code = api.qrLoginCode()
        assertEquals(LoginCode("AB12", "https://30nama.com/auth/AB12"), code)
        assertNull("unused codes are polled again", api.qrLogin(code.code))
        confirmed = true
        val token = api.qrLogin(code.code)!!
        val user = api.signIn(token)
        assertEquals("sample", user.name)
        assertEquals("session-token", net.last!!.headers["c-token"])
    }

    @Test fun testAStreamOrWhyNot() = runBlocking {
        val (api, _) = client { action, form ->
            if (action == "stream" && form["free_stream"] == "0") String(fixtureBytes("stream_movie.json"))
            else """{"success":false,"msg":"subscription needed","result":null}"""
        }
        val playable = api.stream(9001, signedIn = true)
        assertEquals("9001", playable.result!!.items.single().fileID)
        val refused = api.stream(9001, signedIn = false)
        assertNull(refused.result)
        assertEquals("subscription needed", refused.refusal)
    }

    @Test fun testProgressIsReportedInThirtySecondSteps() = runBlocking {
        var sent: Map<String, String>? = null
        val (api, _) = client { action, form -> if (action == "observer") { sent = form; "" } else "{}" }
        val item = StreamResult.from(fixture("stream_movie")["result"]).items.single()
        val post = Post.from(obj("id" to 42))!!
        assertNull("nothing before the first thirty seconds", WatchProgress(post, item, "7", 29.9, 6000.0).reportedTime)
        assertTrue(api.reportProgress(WatchProgress(post, item, "7", 95.4, 6000.7)))
        assertEquals("90", sent!!["time"])
        assertEquals("6000", sent!!["duration"])
        assertEquals("9001", sent!!["file_id"])
        assertEquals(item.postID ?: "42", sent!!["post_id"])
        assertEquals("7", sent!!["user_id"])
    }

    @Test fun testHomeAndLists() = runBlocking {
        val (api, _) = client { action, _ ->
            when (action) {
                "home_stream" -> String(fixtureBytes("home_stream.json"))
                "user_mylist" -> """{"success":true,"result":{"watchlist":{"post":[1,"2"]},"favorite":{"post":[]}}}"""
                else -> """{"success":false}"""
            }
        }
        assertEquals(3, api.home().heroes.size)
        assertEquals(mapOf("watchlist" to setOf(1, 2), "favorite" to emptySet<Int>()), api.myLists())
    }

    @Test fun testTheDefaultTransportTalksHTTPAndLetsGoWhenCancelled() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo") { exchange ->
            val body = exchange.requestBody.readBytes()
            exchange.responseHeaders.add("Retry-After", "3")
            exchange.sendResponseHeaders(429, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/hang") { exchange ->
            Thread.sleep(5000)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val transport = HttpURLConnectionTransport()
            val response = transport.send(HttpRequest("$base/echo", "POST", mapOf("Content-Type" to "text/plain"), "سلام".toByteArray()))
            assertEquals("an error status is an answer, not a failure", 429, response.status)
            assertEquals("سلام", String(response.body))
            assertEquals("3", response.header("retry-after"))

            val start = System.nanoTime()
            val hung = async(Dispatchers.Default) { transport.send(HttpRequest("$base/hang", timeoutSeconds = 30.0)) }
            delay(200)
            hung.cancel()
            withContext(Dispatchers.Default) { runCatching { hung.await() } }
            assertTrue("cancelled at once, not after the server's five seconds", (System.nanoTime() - start) / 1e9 < 3)
        } finally {
            server.stop(0)
        }
    }
}
