package app.nama.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val OK = """{"success":true,"msg":null,"result":{"posts":[]}}"""
private const val LOGIN_REQUIRED = """{"success":false,"msg":"ابتدا وارد حساب کاربری خود شوید","result":null}"""

class APIClientTests {
    private fun config() = APIConfig(backoff = listOf(0.02, 0.02), requestSpacing = 0.0)

    @Test fun testBootstrapSkipsHostsThatDoNotAnswerTheAPI() = runBlocking {
        val net = FakeNetwork { action, host ->
            when {
                action == "discovery" -> answer(0, "")                               // discovery unreachable -> built-in hosts
                host.contains("kazem") -> answer(403, "<html>blocked</html>")        // geo-blocked host answers with HTML
                else -> answer(200, OK)
            }
        }
        val api = APIClient(config(), net.transport)
        assertEquals("https://world.hana-api.com/api/v2", api.bootstrap())
    }

    @Test fun testTheIranianHostSaysTheUserIsInIran() = runBlocking {
        // Only the Iranian host answers, as from inside Iran.
        val inside = FakeNetwork { action, host ->
            if (action == "discovery") answer(200, """[{"location":"WORLD","host":"https://w.example.com/api/v2"},{"location":"IR","host":"https://i.example.com/api/v2"}]""")
            else if (host == "i.example.com") answer(200, OK) else answer(0, "")
        }
        val there = APIClient(config(), inside.transport)
        there.bootstrap()
        assertTrue(there.inIran)
        // Abroad the Iranian host turns the request away.
        val outside = FakeNetwork { action, host ->
            when {
                action == "discovery" -> answer(0, "")
                host.contains("kazem") -> answer(403, "<html>blocked</html>")
                else -> answer(200, OK)
            }
        }
        val here = APIClient(config(), outside.transport)
        here.bootstrap()
        assertFalse(here.inIran)
    }

    @Test fun testBootstrapPrefersPublishedEndpointList() = runBlocking {
        val net = FakeNetwork { action, host ->
            if (action == "discovery") answer(200, """[{"location":"X","host":"https://new.example.com/api/v2"}]""")
            else if (host == "new.example.com") answer(200, OK) else answer(0, "")
        }
        val api = APIClient(config(), net.transport)
        assertEquals("https://new.example.com/api/v2", api.bootstrap())
    }

    @Test fun testBootstrapFailsWhenNothingAnswers() = runBlocking {
        val net = FakeNetwork { _, _ -> answer(0, "") }
        val api = APIClient(config(), net.transport)
        try {
            api.bootstrap()
            fail("expected unreachable")
        } catch (e: APIError) {
            assertEquals(APIError.Unreachable, e)
        }
    }

    @Test fun testRequestCarriesHeadersAndFormBody() = runBlocking {
        val net = FakeNetwork { _, _ -> answer(200, OK) }
        val api = APIClient(config(), net.transport)
        api.useHost("https://h.example.com/api/v2")
        api.setToken("tok")
        api.setLanguage(Lang.en)
        api.get("search", mapOf("query" to "breaking bad & co", "page" to "1"))
        val last = net.last!!
        assertEquals("search", last.action)
        assertEquals("page=1&query=breaking%20bad%20%26%20co", last.body)
        assertEquals("tok", last.headers["c-token"])
        assertEquals("en", last.headers["c-language"])
        assertEquals("1.0.0", last.headers["c-version-number"])
        assertEquals(APIConfig().apiKeys[0], last.headers["c-api-key"])
    }

    @Test fun testRateLimitIsRetriedThenSucceeds() = runBlocking {
        val remaining = Counter(2)
        val net = FakeNetwork { _, _ ->
            if (remaining.take()) answer(429, "error code: 1015", mapOf("Retry-After" to "0")) else answer(200, OK)
        }
        val api = APIClient(config(), net.transport)
        api.useHost("https://h.example.com/api/v2")
        api.get("single", mapOf("post_id" to "1"))
        assertEquals(listOf("single", "single", "single"), net.calls)
    }

    @Test fun testPersistentFailureGivesUpAfterRetries() = runBlocking {
        val net = FakeNetwork { _, _ -> answer(502, "<html>502</html>") }
        val api = APIClient(config(), net.transport)
        api.useHost("https://h.example.com/api/v2")
        try {
            api.get("single")
            fail("expected failure")
        } catch (e: APIError.Transient) {
        }
        assertEquals("first try plus two retries", 3, net.calls.size)
    }

    @Test fun testProgressReportsAreNeverReplayed() = runBlocking {
        val net = FakeNetwork { _, _ -> answer(0, "") }
        val api = APIClient(config(), net.transport)
        api.useHost("https://h.example.com/api/v2")
        try { api.call("observer", mapOf("time" to "10")) } catch (_: APIError) {}
        assertEquals(listOf("observer"), net.calls)
    }

    @Test fun testAnEmptyAnswerToAProgressReportIsASuccess() = runBlocking {
        for (status in listOf(200, 204)) {
            val net = FakeNetwork { _, _ -> answer(status, "") }
            val api = APIClient(config(), net.transport)
            api.useHost("https://h.example.com/api/v2")
            val answer = api.call("observer", mapOf("time" to "30"))
            assertTrue("HTTP $status with no body", answer.success)
            assertEquals(listOf("observer"), net.calls)
        }
    }

    @Test fun testServerErrorSurfacesItsMessage() = runBlocking {
        val net = FakeNetwork { _, _ -> answer(200, """{"success":false,"msg":"nope","result":null}""") }
        val api = APIClient(config(), net.transport)
        api.useHost("https://h.example.com/api/v2")
        try {
            api.get("single")
            fail("expected failure")
        } catch (e: APIError) {
            assertEquals(APIError.Server("nope"), e)
        }
        assertEquals("a real API answer is not retried", listOf("single"), net.calls)
    }

    @Test fun testSessionExpiryIsConfirmedAgainstTheProfile() = runBlocking {
        val expired = Counter(0)
        // The stream says "login required" but the profile still loads: a subscription problem, not a dead session.
        var net = FakeNetwork { action, _ -> if (action == "user") answer(200, OK) else answer(200, LOGIN_REQUIRED) }
        var api = APIClient(config(), net.transport)
        api.useHost("https://h.example.com/api/v2")
        api.setToken("tok")
        api.setSessionExpiredHandler { expired.add() }
        val e = api.call("stream", mapOf("post_id" to "1"))
        assertFalse(e.success)
        assertEquals(listOf("stream", "user"), net.calls)
        assertEquals(0, expired.value)

        // The profile refuses too: the session is gone.
        net = FakeNetwork { _, _ -> answer(200, LOGIN_REQUIRED) }
        api = APIClient(config(), net.transport)
        api.useHost("https://h.example.com/api/v2")
        api.setToken("tok")
        api.setSessionExpiredHandler { expired.add() }
        api.call("stream", mapOf("post_id" to "1"))
        assertEquals(1, expired.value)

        // A guest is never "expired".
        net = FakeNetwork { _, _ -> answer(200, LOGIN_REQUIRED) }
        api = APIClient(config(), net.transport)
        api.useHost("https://h.example.com/api/v2")
        api.setSessionExpiredHandler { expired.add() }
        api.call("continue_watching")
        assertEquals(listOf("continue_watching"), net.calls)
        assertEquals(1, expired.value)
    }

    @Test fun testGateLimitsConcurrencyAndServesScreenCallsFirst() = runBlocking {
        val gate = RequestGate(limit = 2, spacing = 0.0)
        val order = Recorder()
        val peak = Counter(0)
        val now = Counter(0)

        // Fill both slots, then queue background work followed by one screen call.
        gate.acquire(background = false)
        gate.acquire(background = false)
        val jobs = (0 until 4).map { i ->
            launch {
                gate.acquire(background = true)
                order.add("bg$i"); peak.raise(now.add())
                delay(20)
                now.sub(); gate.release(background = true)
            }
        }
        delay(50)
        val core = launch {
            gate.acquire(background = false)
            order.add("core"); peak.raise(now.add())
            delay(20)
            now.sub(); gate.release()
        }
        delay(50)
        gate.release()
        delay(10)
        gate.release()
        (jobs + core).forEach { it.join() }
        assertEquals("screen call jumps the background queue", "core", order.values.first())
        assertEquals(5, order.values.size)
        assertTrue(peak.value <= 2)
    }

    // Not in the Swift tests: background work never holds every slot, so a page is not kept waiting.
    @Test fun testBackgroundCallsLeaveASlotForTheScreen() = runBlocking {
        val gate = RequestGate(limit = 2, spacing = 0.0)
        gate.acquire(background = true)
        val second = launch { gate.acquire(background = true) }
        delay(20)
        assertTrue("a second background call waits", second.isActive)
        kotlinx.coroutines.withTimeout(1000) { gate.acquire(background = false) }
        gate.release(background = false)
        gate.release(background = true)
        kotlinx.coroutines.withTimeout(1000) { second.join() }
        gate.release(background = true)
    }

    // Not in the Swift tests: a rate limit pauses the calls after it even when it is not retried.
    @Test fun testARateLimitPausesTheGateEvenWithoutARetry() = runBlocking {
        val net = FakeNetwork { action, _ ->
            when (action) {
                "discovery" -> answer(0, "")
                "observer" -> answer(429, "error code: 1015")
                else -> answer(200, OK)
            }
        }
        val api = APIClient(APIConfig(backoff = listOf(0.3, 0.3), requestSpacing = 0.0), net.transport)
        api.bootstrap()
        // A write is never replayed, so it fails at once; the gate still waits before the next call.
        val failure = runCatching { api.call("observer") }.exceptionOrNull()
        assertTrue((failure as? APIError.Transient)?.rateLimited == true)
        val start = System.nanoTime()
        api.call("profile")
        assertTrue("the next call waited out the pause", (System.nanoTime() - start) / 1e9 >= 0.25)
    }

    // Not in the Swift tests: a coroutine that gives up while queued must not take a slot with it.
    @Test fun testAWaiterThatIsCancelledGivesBackItsPlace() = runBlocking {
        val gate = RequestGate(limit = 1, spacing = 0.0)
        gate.acquire(background = false)
        val quitter = launch { gate.acquire(background = false) }
        delay(20)
        quitter.cancel()
        quitter.join()
        gate.release()
        val next = launch { gate.acquire(background = true); gate.release(background = true) }
        kotlinx.coroutines.withTimeout(1000) { next.join() }
    }
}
