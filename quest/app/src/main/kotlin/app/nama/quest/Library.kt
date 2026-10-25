package app.nama.quest

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import app.nama.core.APIClient
import app.nama.core.ContinueDismissals
import app.nama.core.Post
import app.nama.core.RatingBook
import app.nama.core.dismissContinue
import app.nama.core.myLists
import app.nama.core.toggleList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the user keeps across screens, as on the Mac's AppModel: the watchlist and favourites
 * (server lists), star ratings (on the headset only; the Mac syncs them through iCloud), the
 * episodes marked watched, and titles taken off "continue watching". Every part is a StateFlow so
 * the title page, the profile and the home rows stay in step.
 */
class Library(private val prefs: SharedPreferences, private val apiOf: () -> APIClient?) {
  /** Group ("watchlist", "favorite", …) to post ids. Empty until [refreshLists] for a signed-in user. */
  private val _lists = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
  val lists: StateFlow<Map<String, Set<Int>>> = _lists.asStateFlow()

  private var book = prefs.getString("ratings", null)?.let { s -> runCatching { RatingBook.from(Base64.decode(s, Base64.NO_WRAP)) }.getOrNull() } ?: RatingBook()
  /** Bumped on every change; [ratings] is the same mutable book, so screens collect this. */
  private val _ratingsVersion = MutableStateFlow(0)
  val ratingsVersion: StateFlow<Int> = _ratingsVersion.asStateFlow()
  val ratings: RatingBook
    get() = book

  /** File ids watched to the end (over 90%), or marked so by hand. */
  private val _watched = MutableStateFlow(prefs.getStringSet("watched", emptySet())!!.toSet())
  val watched: StateFlow<Set<String>> = _watched.asStateFlow()

  private val dismissals =
      ContinueDismissals(
          prefs.getString("continueDismissals", null)?.split(';')?.mapNotNull { e ->
            val (k, v) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toDoubleOrNull()?.let { k to it }
          }?.toMap() ?: emptyMap(),
      )

  fun isListed(postID: Int, group: String): Boolean = _lists.value[group]?.contains(postID) == true

  suspend fun refreshLists() {
    val api = apiOf() ?: return
    try {
      _lists.value = api.myLists()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w("Nama", "lists: $e")
    }
  }

  /**
   * Adds or removes, at once on screen; the server's own action toggles it. Returns false when the
   * server said no, after putting the old state back (the caller shows the error).
   */
  suspend fun toggle(post: Post, group: String): Boolean {
    val api = apiOf() ?: return false
    val before = _lists.value
    val set = before[group].orEmpty()
    _lists.value = before + (group to if (post.id in set) set - post.id else set + post.id)
    val ok =
        try {
          api.toggleList(post.id, group)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          false
        }
    if (!ok) _lists.value = before
    return ok
  }

  fun stars(postID: Int): Double? = book.stars(postID)

  /** Half steps from 0.5 to 5; null takes the rating away. */
  fun rate(post: Post, stars: Double?) {
    book.rate(post, stars)
    prefs.edit().putString("ratings", Base64.encodeToString(book.encoded(), Base64.NO_WRAP)).apply()
    _ratingsVersion.value++
  }

  fun isWatched(fileID: String): Boolean = fileID in _watched.value

  fun setWatched(fileID: String, on: Boolean) {
    if (fileID.isEmpty()) return
    val next = if (on) _watched.value + fileID else _watched.value - fileID
    if (next == _watched.value) return
    _watched.value = next
    prefs.edit().putStringSet("watched", next).apply()
  }

  /** The continue-watching list without what the user took off it. */
  fun visibleContinue(posts: List<Post>): List<Post> {
    val shown = dismissals.filter(posts)
    saveDismissals()
    return shown
  }

  /** Takes a title off "continue watching": hidden here at once, and the server asked to forget it. */
  suspend fun dismissContinue(post: Post) {
    dismissals.dismiss(post)
    saveDismissals()
    runCatching { apiOf()?.dismissContinue(post.id) }
  }

  fun clear() {
    _lists.value = emptyMap()
  }

  private fun saveDismissals() {
    prefs.edit().putString("continueDismissals", dismissals.plist.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
  }

  companion object {
    const val WATCHLIST = "watchlist"
    const val FAVORITE = "favorite"
  }
}
