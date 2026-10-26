package app.nama.androidtv.data

import app.nama.core.Json
import java.time.Duration
import java.time.Instant

/**
 * Followed series with an episode out since the viewer last watched them. 30nama only says when the
 * next episode airs, so each look remembers those days; a remembered day that has passed (the whole
 * day, plus twelve hours, in UTC) means a new episode is out. The news goes when the viewer goes
 * back to the series (its place in "continue watching" moves), after a week, or when they take it
 * off the TV's home screen. Learnt from 30namaTV. Pure, so tested on the computer.
 */
class NewEpisodes(json: String? = null) {
  private data class Entry(val due: Instant?, val outAt: Instant?, val resumeAt: Double)

  private val entries = HashMap<Int, Entry>()

  init {
    json?.let { runCatching { Json.parse(it) }.getOrNull() }?.obj?.forEach { (k, v) ->
      val id = k.toIntOrNull() ?: return@forEach
      entries[id] = Entry(v["due"].double?.let { Instant.ofEpochSecond(it.toLong()) }, v["out"].double?.let { Instant.ofEpochSecond(it.toLong()) }, v["at"].double ?: 0.0)
    }
  }

  /**
   * One look at the followed series: [days] is when each one's next episode airs (null when 30nama
   * gives none), [resume] where each was left. Returns the series with news.
   */
  fun look(now: Instant, days: Map<Int, Instant?>, resume: Map<Int, Double>): Set<Int> {
    // Days still to come are remembered.
    for ((id, day) in days) {
      val e = entries[id]
      if (day != null && day.plus(GRACE) > now && e?.outAt == null) entries[id] = Entry(day, null, resume[id] ?: e?.resumeAt ?: 0.0)
    }
    // A remembered day that has passed: an episode is out.
    for ((id, e) in entries.toMap()) {
      if (e.due != null && e.outAt == null && e.due.plus(GRACE) <= now) entries[id] = e.copy(outAt = now, resumeAt = resume[id] ?: e.resumeAt)
    }
    for ((id, e) in entries.toMap()) {
      val out = e.outAt ?: continue
      val moved = resume[id]?.let { kotlin.math.abs(it - e.resumeAt) > 1 } ?: false
      if (moved || Duration.between(out, now) > KEEP) entries.remove(id)
    }
    return entries.filterValues { it.outAt != null }.keys
  }

  /** Taken off the TV's home screen by the viewer. */
  fun dismiss(id: Int) {
    entries.remove(id)
  }

  fun json(): String = Json.of(entries.mapKeys { it.key.toString() }.mapValues { (_, e) ->
    mapOf("due" to e.due?.epochSecond?.toDouble(), "out" to e.outAt?.epochSecond?.toDouble(), "at" to e.resumeAt).filterValues { it != null }
  }).serialize()

  companion object {
    /** The air day's own 24 hours, and twelve more for the upload. */
    val GRACE: Duration = Duration.ofHours(36)
    val KEEP: Duration = Duration.ofDays(7)
  }
}
