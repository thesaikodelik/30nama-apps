package app.nama.core

import java.time.Instant
import java.time.ZoneId

/**
 * When a download that failed is worth trying again, and how long to wait first. A dropped
 * connection, a busy server or a time-out are passing; a refusal (an expired link, a missing
 * file) is not. Waits double from two seconds, with a little jitter, and a server's own
 * Retry-After is honoured. The schedule is after the Tachyon download manager's (MIT).
 */
object RetryPolicy {
    const val ATTEMPTS = 6
    private const val LONGEST = 120.0

    /** Whether an answer with this status may come right on a later try. */
    fun passing(status: Int?): Boolean = status == null || status == 408 || status == 429 || status >= 500

    /**
     * The wait in seconds before try number `attempt` + 1, or nil to give up.
     * @param attempt tries made so far, from 1.
     * @param status the HTTP status that ended the last try; nil for a dropped connection or a time-out.
     */
    fun next(attempt: Int, status: Int?, retryAfter: Double? = null, jitter: Double = 0.5): Double? {
        if (attempt >= ATTEMPTS || !passing(status)) return null
        if (retryAfter != null && retryAfter > 0) return minOf(retryAfter, LONGEST)
        val seconds = minOf(LONGEST, 2 * Math.pow(2.0, (attempt - 1).toDouble()))
        return seconds * (0.75 + 0.5 * jitter.coerceIn(0.0, 1.0))
    }
}

/**
 * The hours of the day downloads may run in, for a line that is cheaper or freer at night. From
 * 30nama's own desktop app. `from` to `to` in whole hours of local time, and across midnight
 * when `from` is the later one: 2 to 8 is the early morning, 23 to 7 the night.
 */
object DownloadHours {
    /** Whether downloads may run at `date`. No hours set, or the same hour twice, is any time. */
    fun open(from: Int?, to: Int?, date: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (from == null || to == null || from == to) return true
        val hour = date.atZone(zone).hour
        return if (from < to) hour in from until to else hour >= from || hour < to
    }
}

/**
 * A cap on how fast downloads come in, shared by every connection: a bucket that fills at the
 * allowed rate and holds at most a second's worth. Each piece that arrives is taken from it, and
 * a connection that has overdrawn it waits until it is back to zero.
 */
class RateLimit {
    private val lock = Any()
    private var allowance = 0.0
    private var last: Double? = null
    private var rate = 0.0

    /** Bytes a second; zero or less is no limit. */
    var bytesPerSecond: Double
        get() = synchronized(lock) { rate }
        set(value) = synchronized(lock) {
            rate = value
            allowance = minOf(allowance, maxOf(0.0, value))
        }

    /** Takes `bytes` from the budget at time `now` (seconds, any steady clock) and says how many seconds to wait before reading more. */
    fun take(bytes: Int, now: Double = System.nanoTime() / 1e9): Double = synchronized(lock) {
        if (rate <= 0) return 0.0
        last?.let { allowance = minOf(rate, allowance + (now - it) * rate) }
        last = now
        allowance -= bytes
        if (allowance >= 0) 0.0 else -allowance / rate
    }
}
