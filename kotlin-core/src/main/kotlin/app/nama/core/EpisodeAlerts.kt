package app.nama.core

import java.time.Instant

/**
 * The days the user's series have their next episode, as last seen, and which of them have come.
 *
 * The server tells only the day the next episode airs, and moves on to the one after once it has.
 * So each look remembers the days still ahead, and a remembered day that has passed by the next
 * look is an episode out since. Half a day is allowed after it for the episode to reach 30nama.
 * Not thread-safe; keep it with the state it belongs to.
 */
class EpisodeAlerts() {
    private val _expected = LinkedHashMap<Int, Instant>()
    val expected: Map<Int, Instant> get() = _expected

    constructor(plist: Map<String, Double>) : this() {
        for ((key, time) in plist) key.asciiInt()?.let { _expected[it] = Instant.ofEpochMilli((time * 1000).toLong()) }
    }

    /** Post ids as text and seconds since 1970, for preferences. */
    val plist: Map<String, Double> get() = _expected.entries.associate { it.key.toString() to it.value.toEpochMilli() / 1000.0 }

    /**
     * Takes in the series followed now and returns those with an episode out since the last look.
     * A series no longer followed is forgotten, and says nothing.
     */
    fun update(followed: List<Post>, now: Instant = Instant.now()): List<Post> {
        val byID = LinkedHashMap<Int, Post>()
        for (post in followed) byID.putIfAbsent(post.id, post)
        val out = mutableListOf<Post>()
        for ((id, day) in _expected.entries.toList()) {
            if (day.plusSeconds(GRACE) <= now) {
                byID[id]?.let { out.add(it) }
                _expected.remove(id)
            }
        }
        _expected.keys.retainAll { it in byID }
        for (post in byID.values) {
            val day = post.nextEpisodeDay ?: continue
            if (day.plusSeconds(GRACE) > now) _expected[post.id] = day
        }
        return out.sortedBy { it.id }
    }

    override fun equals(other: Any?): Boolean = other is EpisodeAlerts && other._expected == _expected
    override fun hashCode(): Int = _expected.hashCode()

    companion object {
        const val GRACE: Long = 12 * 3600
    }
}
