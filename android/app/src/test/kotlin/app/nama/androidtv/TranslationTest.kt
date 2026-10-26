package app.nama.androidtv

import app.nama.androidtv.data.Translation
import app.nama.core.Cue
import app.nama.core.Json
import app.nama.core.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationTest {
  private val cues = (0 until 200).map { Cue(it * 3.0, it * 3.0 + 2, "line $it") }

  @Test
  fun startsWhereTheViewerIsThenGoesBack() {
    assertEquals(3, Translation.batchCount(cues))
    val at = Translation.batchAt(cues, 90 * 3.0)
    assertEquals(1, at)
    assertEquals(listOf(1, 2, 0), Translation.order(3, at))
    assertEquals(listOf(0, 1, 2), Translation.order(3, 0))
    assertEquals("past the last line, the last batch", 2, Translation.batchAt(cues, 10_000.0))
    assertEquals(40, Translation.batch(cues, 2).size)
  }

  @Test
  fun theRequestAsksForOneStringPerLine() {
    val body = Json.parse(Translation.request(listOf("Hello.", "Two\nlines"), Lang.fa, "Heat"))
    val sent = Json.parse(body["contents"][0]["parts"][0]["text"].string!!).array.map { it.string }
    assertEquals(listOf("Hello.", "Two | lines"), sent)
    assertEquals("ARRAY", body["generationConfig"]["responseSchema"]["type"].string)
    assertTrue(body["systemInstruction"]["parts"][0]["text"].string!!.contains("Persian"))
  }

  private fun answer(text: String) = Json.parse(
      Json.Obj(mapOf("candidates" to Json.Arr(listOf(Json.Obj(mapOf("content" to Json.Obj(mapOf("parts" to Json.Arr(listOf(Json.Obj(mapOf("text" to Json.Str(text))))))))))))).serialize())

  @Test
  fun readsTheAnswerLineByLine() {
    assertEquals(listOf("سلام.", "دو\nخط"), Translation.read(answer("""["سلام.", "دو | خط"]"""), 2))
    assertEquals("a line that did not come back keeps its original", listOf("سلام.", null), Translation.read(answer("""["سلام."]"""), 2))
    assertEquals(listOf("سلام."), Translation.read(answer("```json\n[\"سلام.\"]\n```"), 1))
    assertNull(Translation.read(answer("Sorry, I can't."), 1))
    assertNull(Translation.read(Json.parse("{}"), 1))
  }

  @Test
  fun aBatchReplacesItsOwnLinesOnly() {
    val out = Translation.apply(cues, 1, listOf("یک", null))
    assertEquals("یک", out[80].text)
    assertEquals("line 81", out[81].text)
    assertEquals("line 79", out[79].text)
    assertEquals(cues[80].start, out[80].start, 0.0)
  }

  @Test
  fun arabicForAnArabicInterfacePersianOtherwise() {
    assertEquals(Lang.ar, Translation.target(Lang.ar))
    assertEquals(Lang.fa, Translation.target(Lang.en))
  }
}
