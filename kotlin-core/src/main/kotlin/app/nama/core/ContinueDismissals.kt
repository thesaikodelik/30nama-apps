package app.nama.core

/**
 * Titles the user took off "continue watching", and where they had got to when they did.
 *
 * The server is asked to forget them too, but goes on listing them for a while from its cache,
 * so the app hides them itself. A title comes back once it is watched again: its position then
 * moves on from the one it was dismissed at. Not thread-safe; keep it with the screen state.
 */
class ContinueDismissals() {
    private val _positions = LinkedHashMap<Int, Double>()
    val positions: Map<Int, Double> get() = _positions

    /** From what `plist` gave back. Keys are post ids written as text, as preferences need. */
    constructor(plist: Map<String, Double>) : this() {
        for ((key, time) in plist) key.asciiInt()?.let { _positions[it] = time }
    }

    val plist: Map<String, Double> get() = _positions.mapKeys { it.key.toString() }

    fun dismiss(post: Post) {
        _positions[post.id] = post.resume?.time ?: 0.0
    }

    /**
     * The list without what was dismissed and not watched since. Entries for titles no longer on
     * the list are dropped, so the record stays as long as the list at most.
     */
    fun filter(posts: List<Post>): List<Post> {
        val shown = posts.filter { post ->
            val at = _positions[post.id] ?: return@filter true
            Math.abs((post.resume?.time ?: 0.0) - at) > 1
        }
        val listed = posts.map { it.id }.toSet()
        val shownIDs = shown.map { it.id }.toSet()
        _positions.keys.retainAll { it in listed && it !in shownIDs }
        return shown
    }

    override fun equals(other: Any?): Boolean = other is ContinueDismissals && other._positions == _positions
    override fun hashCode(): Int = _positions.hashCode()
}
