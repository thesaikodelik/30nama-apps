package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** After the tests of Tachyon's segment.rs, by way of the Windows app. */
class SegmentsTests {
    private val mb: Long = 1024 * 1024

    private fun contiguous(states: List<SegmentState>, size: Long) {
        assertEquals(0L, states.first().start)
        for (i in 1 until states.size) assertEquals("no gap, no overlap", states[i - 1].end, states[i].start)
        assertEquals(size, states.last().end)
    }

    @Test fun testSplitsTheLargestInHalf() {
        val map = SegmentMap.whole(100 * mb)
        val first = map.takeUnowned()!!
        val second = map.splitLargest()!!
        assertEquals(first.end, second.start)
        assertEquals(50 * mb, second.start)
        assertEquals("both have 50 MB left; the earlier one is split", 25 * mb, map.splitLargest()?.start)
        contiguous(map.snapshot(), 100 * mb)
    }

    @Test fun testASplitCountsWhatHasArrived() {
        val map = SegmentMap.whole(100 * mb)
        val segment = map.takeUnowned()!!
        val chunk = ByteArray((60 * mb).toInt())
        assertEquals(chunk.size, segment.write(chunk) { _, _ -> })
        assertEquals("40 MB were left after 60; the split lands halfway", 80 * mb, map.splitLargest()?.start)
        assertEquals("writing stops at the new boundary", (20 * mb).toInt(), segment.write(chunk) { _, _ -> })
        assertTrue(segment.isComplete)
        contiguous(map.snapshot(), 100 * mb)
    }

    @Test fun testRefusesTinySplits() {
        val map = SegmentMap.whole(mb + mb / 2)
        map.takeUnowned()
        assertNull(map.splitLargest())
        assertNull(SegmentMap(listOf(SegmentState(0, 0, 100 * mb)), splittable = false).splitLargest())
    }

    @Test fun testLeftoversAreTakenOnce() {
        val map = SegmentMap(listOf(SegmentState(0, 10, 10), SegmentState(10, 15, 20), SegmentState(20, 22, 30)), splittable = true)
        assertEquals(10L, map.takeUnowned()?.start)
        assertEquals(20L, map.takeUnowned()?.start)
        assertNull(map.takeUnowned())
        assertEquals(10L + 5 + 2, map.downloaded)
    }

    @Test fun testAReleasedSegmentIsTakenAgain() {
        val map = SegmentMap.whole(10 * mb, have = 3 * mb)
        val segment = map.takeUnowned()!!
        assertEquals("a single file from an older run goes on where it ended", 3 * mb, segment.pos)
        assertNull(map.takeUnowned())
        map.release(segment)
        assertSame(segment, map.takeUnowned())
    }

    @Test fun testManyWritersFillTheFileExactly() {
        val size = 8 * mb + 12345
        val map = SegmentMap.whole(size)
        val file = ByteArray(size.toInt())
        val total = AtomicLong(0)
        val chunk = ByteArray(64 * 1024) { 1 }
        val pool = Executors.newFixedThreadPool(4)
        repeat(4) {
            pool.execute {
                while (true) {
                    val segment = map.nextWork() ?: break
                    while (true) {
                        val n = segment.write(chunk) { offset, data ->
                            synchronized(file) { System.arraycopy(data, 0, file, offset.toInt(), data.size) }
                        }
                        if (n == 0) break
                        total.addAndGet(n.toLong())
                    }
                }
            }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertTrue(map.isComplete)
        assertEquals(size, total.get())
        assertFalse(file.contains(0))
        contiguous(map.snapshot(), size)
    }
}
