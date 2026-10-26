package app.nama.androidtv

import app.nama.androidtv.data.WatchNextRules
import app.nama.androidtv.data.WatchNextRules.Change
import app.nama.androidtv.data.WatchNextRules.Row
import app.nama.androidtv.data.WatchNextRules.Want
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextRulesTest {
  @Test
  fun newTitlesAreAddedAndGoneOnesRemoved() {
    val plan = WatchNextRules.plan(listOf(Row(1, 10, "continue 600", true)), listOf(Want(20, "continue 30")), known = true)
    assertTrue(Change.Delete(1) in plan)
    assertTrue(Change.Insert(Want(20, "continue 30")) in plan)
  }

  @Test
  fun aRowTheViewerRemovedStaysRemovedUntilThereIsSomethingNew() {
    val removed = Row(1, 10, "continue 600", browsable = false)
    assertEquals(emptyList<Change>(), WatchNextRules.plan(listOf(removed), listOf(Want(10, "continue 600")), known = true))
    assertEquals(listOf(Change.Delete(1), Change.Insert(Want(10, "next 2 4"))), WatchNextRules.plan(listOf(removed), listOf(Want(10, "next 2 4")), known = true))
  }

  @Test
  fun aSecondRowForOneTitleGoes() {
    val plan = WatchNextRules.plan(listOf(Row(1, 10, "a", true), Row(2, 10, "a", true)), listOf(Want(10, "a")), known = true)
    assertEquals(listOf(Change.Delete(2)), plan)
  }

  @Test
  fun nothingChangesWhen30namaDidNotAnswer() {
    assertEquals(emptyList<Change>(), WatchNextRules.plan(listOf(Row(1, 10, "a", true)), emptyList(), known = false))
  }

  @Test
  fun anUnchangedRowIsLeftAlone() {
    assertEquals(emptyList<Change>(), WatchNextRules.plan(listOf(Row(1, 10, "a", true)), listOf(Want(10, "a")), known = true))
  }
}
