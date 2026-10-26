package app.nama.androidtv.data

import app.nama.core.Cue
import app.nama.core.Json
import app.nama.core.Lang

/**
 * Subtitles translated by a language model (Google's Gemini, with the viewer's own key), for an item
 * that has no Persian subtitle anywhere. Pure, so tested on the computer; the requests are in
 * [Gemini]. The film starts at once with the original lines: batches are translated from where the
 * viewer is, onwards, then the part before, and each replaces its lines as it arrives.
 */
object Translation {
  /** Lines per request: enough context for names and tone, small enough to come back in seconds. */
  const val BATCH = 80

  /** The batch that holds the line on screen at [t] seconds, or the next one. */
  fun batchAt(cues: List<Cue>, t: Double): Int {
    val i = cues.indexOfFirst { it.end > t }.takeIf { it >= 0 } ?: cues.lastIndex.coerceAtLeast(0)
    return i / BATCH
  }

  /** Batches in the order to translate them: from [start] to the end, then the ones before it. */
  fun order(count: Int, start: Int): List<Int> {
    val s = start.coerceIn(0, (count - 1).coerceAtLeast(0))
    return (s until count) + (0 until s)
  }

  fun batch(cues: List<Cue>, n: Int): List<Cue> = cues.subList(n * BATCH, minOf(cues.size, (n + 1) * BATCH))

  fun batchCount(cues: List<Cue>) = (cues.size + BATCH - 1) / BATCH

  /** A line break inside a cue, written so the model keeps it. */
  private const val BREAK = " | "

  /**
   * The generateContent body: the lines as a JSON array, and an answer asked for as a JSON array of
   * the same length, so each translation lands on its own line and a missing one shows.
   */
  fun request(lines: List<String>, target: Lang, title: String): String {
    val language = if (target == Lang.ar) "Arabic" else "Persian (Farsi), as spoken in Iran"
    val instruction = "You translate film and TV subtitles into $language. The title is \"$title\". " +
        "Translate each string of the JSON array the user sends, in order, into natural, spoken $language that fits on screen. " +
        "Keep names as they are said. \"$BREAK\" marks a line break; keep it. " +
        "Answer with a JSON array of exactly ${lines.size} strings, one per input string, and nothing else."
    fun str(s: String) = Json.Str(s)
    val body = Json.Obj(mapOf(
        "systemInstruction" to Json.Obj(mapOf("parts" to Json.Arr(listOf(Json.Obj(mapOf("text" to str(instruction))))))),
        "contents" to Json.Arr(listOf(Json.Obj(mapOf(
            "role" to str("user"),
            "parts" to Json.Arr(listOf(Json.Obj(mapOf("text" to str(Json.Arr(lines.map { str(it.replace("\n", BREAK)) }).serialize()))))),
        )))),
        "generationConfig" to Json.Obj(mapOf(
            "temperature" to Json.Num(0.3),
            "responseMimeType" to str("application/json"),
            "responseSchema" to Json.Obj(mapOf("type" to str("ARRAY"), "items" to Json.Obj(mapOf("type" to str("STRING"))))),
        )),
    ))
    return body.serialize()
  }

  /**
   * The translated lines from a generateContent answer, one per line sent; a line that did not come
   * back is null and keeps its original. Null for an answer that is not a list at all.
   */
  fun read(answer: Json, count: Int): List<String?>? {
    val text = answer["candidates"][0]["content"]["parts"].array.mapNotNull { it["text"].string }.joinToString("")
    val list = runCatching { Json.parse(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")) }.getOrNull()?.array ?: return null
    if (list.isEmpty()) return null
    return (0 until count).map { i -> list.getOrNull(i)?.text?.replace(BREAK.trim(), "\n")?.lines()?.joinToString("\n") { it.trim() } }
  }

  /** [cues] with the translated [lines] of batch [n] put in. */
  fun apply(cues: List<Cue>, n: Int, lines: List<String?>): List<Cue> {
    val out = cues.toMutableList()
    lines.forEachIndexed { i, line ->
      val at = n * BATCH + i
      if (line != null && at < out.size) out[at] = out[at].copy(text = line)
    }
    return out
  }

  /** The target language: Arabic for an Arabic interface, Persian otherwise. */
  fun target(lang: Lang) = if (lang == Lang.ar) Lang.ar else Lang.fa
}
