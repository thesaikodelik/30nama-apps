package app.nama.core

/** A stretch of a file: bytes `start` to `end`, of which those before `pos` have arrived. */
data class SegmentState(val start: Long, val pos: Long, val end: Long) {
    val remaining: Long get() = maxOf(0, end - pos)
    val downloaded: Long get() = pos - start
}

/**
 * One file fetched over several connections at once. It starts as one segment covering the
 * whole file; whenever a connection is free it halves the segment with the most bytes left and
 * takes the second half, and the connection that had it stops at the new boundary. No
 * connection idles while another still has a long tail, and a slow one loses work to fast ones.
 * Ported from the Tachyon download manager's segment.rs (MIT), by way of the Windows app.
 * Safe to use from several threads.
 */
class SegmentMap(states: List<SegmentState>, val splittable: Boolean, minimum: Long = 1024 * 1024) {
    class Segment internal constructor(state: SegmentState, internal var owned: Boolean = false) {
        private val lock = Any()
        private var _pos = state.pos
        private var _end = state.end
        val start: Long = state.start

        val state: SegmentState get() = synchronized(lock) { SegmentState(start, _pos, _end) }
        val pos: Long get() = synchronized(lock) { _pos }
        val end: Long get() = synchronized(lock) { _end }
        val isComplete: Boolean get() = synchronized(lock) { _pos >= _end }

        /**
         * Writes as much of `data` as fits before the segment's end, at its position, and returns
         * how much that was. Held under the lock, so a split can never move the end below bytes
         * being written.
         */
        fun write(data: ByteArray, put: (offset: Long, bytes: ByteArray) -> Unit): Int = synchronized(lock) {
            val n = minOf(data.size.toLong(), maxOf(0, _end - _pos)).toInt()
            if (n > 0) {
                put(_pos, if (n == data.size) data else data.copyOf(n))
                _pos += n
            }
            n
        }

        internal fun splitOff(minimum: Long): Segment? = synchronized(lock) {
            val remaining = _end - _pos
            if (remaining < 2 * minimum) return null
            var middle = _pos + remaining / 2
            val aligned = middle - middle % ALIGN
            if (aligned >= _pos + minimum) middle = aligned
            val second = Segment(SegmentState(middle, middle, _end), owned = true)
            _end = middle
            second
        }
    }

    private val lock = Any()
    private val segments: MutableList<Segment> = states.sortedBy { it.start }.map { Segment(it) }.toMutableList()
    private val minimum = maxOf(1, minimum)

    fun snapshot(): List<SegmentState> = synchronized(lock) { segments.map { it.state } }

    val downloaded: Long get() = synchronized(lock) { segments.sumOf { it.state.downloaded } }

    val isComplete: Boolean get() = synchronized(lock) { segments.all { it.isComplete } }

    /** The first unfinished segment no connection has: left from a last run, or one a connection gave up. */
    fun takeUnowned(): Segment? = synchronized(lock) {
        segments.firstOrNull { !it.owned && !it.isComplete }?.also { it.owned = true }
    }

    /** Lets go of a segment, for another connection to take up. */
    fun release(segment: Segment) {
        synchronized(lock) { segment.owned = false }
    }

    /** Halves the segment with the most left and returns the second half, already taken. */
    fun splitLargest(): Segment? {
        if (!splittable) return null
        return synchronized(lock) {
            // On a tie the earliest, so the file fills from the front.
            var victim: Segment? = null
            var most = 2 * minimum - 1
            for (segment in segments) {
                val left = segment.state.remaining
                if (left > most) {
                    most = left
                    victim = segment
                }
            }
            val second = victim?.splitOff(minimum) ?: return@synchronized null
            val at = segments.indexOfFirst { it.start > second.start }.takeIf { it >= 0 } ?: segments.size
            segments.add(at, second)
            second
        }
    }

    /** Work for a free connection: what is left over first, then a fresh split. */
    fun nextWork(): Segment? = takeUnowned() ?: splitLargest()

    companion object {
        /** Split points are aligned to this many bytes when possible. */
        private const val ALIGN: Long = 64 * 1024

        /** One segment for a whole file of `size` bytes, of which the first `have` are here. */
        fun whole(size: Long, have: Long = 0): SegmentMap = SegmentMap(listOf(SegmentState(0, minOf(have, size), size)), splittable = true)
    }
}
