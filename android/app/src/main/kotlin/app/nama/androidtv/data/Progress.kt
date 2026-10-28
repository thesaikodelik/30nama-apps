package app.nama.androidtv.data

import android.content.SharedPreferences
import app.nama.core.ContinueWatching
import app.nama.core.StreamItem

/**
 * Where a title was left, by this TV. 30nama's own record lags behind: it keeps positions in steps
 * of thirty seconds, and an episode left part-way can come back as nowhere at all. So the TV keeps
 * its own point for each title too, and playback starts from whichever of the two is further on
 * (later in the list of files, then later in the file). Learnt from 30namaTV.
 */
object Resume {
  data class Point(val index: Int, val seconds: Double?)

  /** A point this TV kept: the file, and the second in it. */
  data class Kept(val fileID: String, val seconds: Double)

  /**
   * Where to start [items]: [chosen] is the file the user picked (or the server's), [server] what
   * 30nama remembers and [kept] what this TV does. A position inside the last 3% is the credits:
   * that file starts over. A position in the first five seconds is no position at all.
   */
  fun start(items: List<StreamItem>, chosen: Int, server: ContinueWatching?, kept: Kept?, pickedByUser: Boolean): Point {
    fun position(fileID: String, seconds: Double, duration: Double): Double? =
        seconds.takeIf { it > 5 && (duration <= 0 || it < duration * 0.97) }

    val serverIndex = server?.fileID?.let { id -> items.indexOfFirst { it.fileID == id } }?.takeIf { it >= 0 }
    val keptIndex = kept?.let { k -> items.indexOfFirst { it.fileID == k.fileID } }?.takeIf { it >= 0 }
    // An episode chosen from the list starts there; only its own saved point can apply.
    if (pickedByUser) {
      val fileID = items.getOrNull(chosen)?.fileID ?: return Point(chosen, null)
      val fromServer = server?.takeIf { it.fileID == fileID }?.let { position(fileID, it.time, it.duration) }
      val fromTV = kept?.takeIf { it.fileID == fileID }?.seconds?.let { position(fileID, it, items[chosen].durationSeconds) }
      return Point(chosen, listOfNotNull(fromServer, fromTV).maxOrNull())
    }
    val s = serverIndex?.let { it to server!!.time }
    val k = keptIndex?.let { it to kept!!.seconds }
    val best = listOfNotNull(s, k).maxWithOrNull(compareBy<Pair<Int, Double>> { it.first }.thenBy { it.second }) ?: return Point(chosen, null)
    val duration = if (best === s) server!!.duration else items[best.first].durationSeconds
    // Left in its credits, and another follows: that one is next, not this one again from the start.
    if (duration > 0 && best.second >= duration * 0.97 && best.first < items.lastIndex) return Point(best.first + 1, null)
    return Point(best.first, position(items[best.first].fileID, best.second, duration))
  }

  private val StreamItem.durationSeconds: Double get() = (durationMinutes ?: 0) * 60.0
}

/** This TV's own record of where each title was left, by post id. */
class KeptProgress(private val prefs: SharedPreferences) {
  fun get(postID: Int): Resume.Kept? {
    val raw = prefs.getString("left.$postID", null) ?: return null
    val (file, seconds) = raw.split(' ', limit = 2).takeIf { it.size == 2 } ?: return null
    return seconds.toDoubleOrNull()?.let { Resume.Kept(file, it) }
  }

  fun put(postID: Int, fileID: String, seconds: Double) {
    if (fileID.isEmpty() || !seconds.isFinite()) return
    prefs.edit().putString("left.$postID", "$fileID ${seconds.toLong()}").apply()
  }

  /** Every title's place forgotten: the account they belong to signed out. */
  fun clear() = prefs.edit().clear().apply()
}
