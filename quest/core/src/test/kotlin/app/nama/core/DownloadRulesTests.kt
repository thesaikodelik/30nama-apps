package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DownloadRulesTests {
    @Test fun testWaitsLongerEachTime() {
        assertEquals(2.0, RetryPolicy.next(1, null)!!, 0.0)
        assertEquals(4.0, RetryPolicy.next(2, 503)!!, 0.0)
        assertEquals(16.0, RetryPolicy.next(4, null)!!, 0.0)
        assertEquals("a little either way, so many downloads do not knock at once", 1.5, RetryPolicy.next(1, null, jitter = 0.0)!!, 0.0)
        assertNull("and then gives up", RetryPolicy.next(RetryPolicy.ATTEMPTS, null))
    }

    @Test fun testARefusalIsNotTriedAgain() {
        assertNull("an expired link", RetryPolicy.next(1, 403))
        assertNull(RetryPolicy.next(1, 404))
        assertNotNull(RetryPolicy.next(1, 429))
        assertNotNull(RetryPolicy.next(1, 408))
    }

    @Test fun testHonoursTheServersOwnWait() {
        assertEquals(30.0, RetryPolicy.next(1, 429, retryAfter = 30.0)!!, 0.0)
        assertEquals("within reason", 120.0, RetryPolicy.next(1, 503, retryAfter = 3600.0)!!, 0.0)
    }

    private fun at(hour: Int, minute: Int = 0): Instant = LocalDateTime.of(2026, 10, 17, hour, minute).atZone(ZoneId.systemDefault()).toInstant()

    @Test fun testAnyTimeWithoutHours() {
        assertTrue(DownloadHours.open(null, null, at(15)))
        assertTrue("the same hour twice is no limit", DownloadHours.open(3, 3, at(15)))
    }

    @Test fun testWithinTheDay() {
        assertFalse(DownloadHours.open(2, 8, at(1, 59)))
        assertTrue(DownloadHours.open(2, 8, at(2)))
        assertTrue(DownloadHours.open(2, 8, at(7, 59)))
        assertFalse("up to the hour, not through it", DownloadHours.open(2, 8, at(8)))
    }

    @Test fun testAcrossMidnight() {
        assertTrue(DownloadHours.open(23, 7, at(23, 30)))
        assertTrue(DownloadHours.open(23, 7, at(0)))
        assertTrue(DownloadHours.open(23, 7, at(6, 59)))
        assertFalse(DownloadHours.open(23, 7, at(7)))
        assertFalse(DownloadHours.open(23, 7, at(12)))
    }

    @Test fun testRateLimitKeepsToTheRate() {
        val limit = RateLimit()
        assertEquals("no limit set", 0.0, limit.take(10_000_000, now = 0.0), 0.0)
        limit.bytesPerSecond = 1000.0
        assertEquals("an empty bucket: half a second for half a second's worth", 0.5, limit.take(500, now = 10.0), 0.001)
        assertEquals("a second later the debt is paid, and this piece with it", 0.0, limit.take(500, now = 11.0), 0.001)
        assertEquals("a long rest saves at most a second's worth", 2.0, limit.take(3000, now = 100.0), 0.001)
    }
}
