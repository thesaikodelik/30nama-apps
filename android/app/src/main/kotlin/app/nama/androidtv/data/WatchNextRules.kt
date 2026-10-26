package app.nama.androidtv.data

/**
 * Which rows of the TV's own home screen ("Play Next", Android's Watch Next table) to add, change or
 * take away, from what the app would show there. Pure, so tested on the computer. Learnt from
 * 30namaTV:
 *  - each row carries the title's id and a key for what it offers ("continue S1 E3 at 20:30", "new
 *    episode", "next episode"); a row the viewer removed from the home screen stays removed while
 *    its key is unchanged, and comes back only with something new to offer;
 *  - a second row for one title is a stray, and goes;
 *  - when what to show could not be worked out this time (30nama did not answer), nothing changes.
 */
object WatchNextRules {
  /** A row that is on the home screen now. */
  data class Row(val rowId: Long, val postId: Int, val key: String, val browsable: Boolean)

  /** A row the app would like there. */
  data class Want(val postId: Int, val key: String)

  sealed interface Change {
    data class Insert(val want: Want) : Change
    data class Update(val rowId: Long, val want: Want) : Change
    data class Delete(val rowId: Long) : Change
  }

  /** The changes, given what [rows] are there and what is [wanted]; [known] false means leave all as it is. */
  fun plan(rows: List<Row>, wanted: List<Want>, known: Boolean): List<Change> {
    if (!known) return emptyList()
    val out = mutableListOf<Change>()
    val byPost = rows.groupBy { it.postId }
    for ((_, list) in byPost) list.drop(1).forEach { out += Change.Delete(it.rowId) }
    val wantedIds = wanted.map { it.postId }.toSet()
    for (row in byPost.values.map { it.first() }) if (row.postId !in wantedIds) out += Change.Delete(row.rowId)
    for (want in wanted) {
      val row = byPost[want.postId]?.first()
      when {
        row == null -> out += Change.Insert(want)
        // Removed by the viewer: stays removed until there is something new to offer.
        !row.browsable && row.key == want.key -> Unit
        // Only the system may show a row again: a removed one with something new is made anew.
        !row.browsable -> {
          out += Change.Delete(row.rowId)
          out += Change.Insert(want)
        }
        row.key != want.key -> out += Change.Update(row.rowId, want)
        else -> Unit
      }
    }
    return out
  }
}
