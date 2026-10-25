package app.nama.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.SocketTimeoutException

class FilteredHostsTests {
    @Test fun testWhatIranFilters() {
        for (host in listOf("www.themoviedb.org", "api.tmdb.org", "image.tmdb.org", "www.youtube-nocookie.com", "i.ytimg.com",
            "api.subsource.net", "interface.30nama.com", "cdn.30nama.com", "30nama.com")) {
            assertTrue(host, FilteredHosts.contains(host))
        }
        // The app's own API and images, the updater and Aparat get through.
        for (host in listOf("ir.kazem-api.com", "world.hana-api.com", "cdn-world.hana-api.com", "api.github.com",
            "www.aparat.com", "not30nama.com", null)) {
            assertFalse(host ?: "nil", FilteredHosts.contains(host))
        }
    }

    @Test fun testAFilteredRequestWaitsOutItsTimeAndFails() = runBlocking {
        val transport = FilteredTransport { HttpResponse(200) }
        val start = System.nanoTime()
        try {
            transport.send(HttpRequest("https://www.themoviedb.org/", timeoutSeconds = 0.3))
            fail("a filtered host answered")
        } catch (_: SocketTimeoutException) {
            assertTrue((System.nanoTime() - start) / 1e9 >= 0.25)
        }
        assertTrue("others pass", transport.send(HttpRequest("https://www.aparat.com/")).status == 200)
    }
}
