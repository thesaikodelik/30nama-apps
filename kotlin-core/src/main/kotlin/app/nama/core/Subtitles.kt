package app.nama.core

data class Cue(val start: Double, val end: Double, val text: String)

/** WebVTT (and SRT, which differs only in details this parser already tolerates) subtitle track. */
class SubtitleTrack(cues: List<Cue> = emptyList()) {
    val cues: List<Cue> = cues.sortedBy { it.start }

    override fun equals(other: Any?): Boolean = other is SubtitleTrack && other.cues == cues
    override fun hashCode(): Int = cues.hashCode()

    /** Text to show at `t`, or an empty string. */
    fun text(t: Double): String {
        if (cues.isEmpty()) return ""
        // Last cue starting at or before t.
        var lo = 0
        var hi = cues.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (cues[mid].start <= t) { idx = mid; lo = mid + 1 } else hi = mid - 1
        }
        // Cues can overlap, so look a few back for one that has not ended yet.
        var j = idx
        while (j >= 0 && j > idx - 4) {
            if (cues[j].end > t) return cues[j].text
            j--
        }
        return ""
    }

    companion object {
        fun vtt(text: String): SubtitleTrack {
            val normalized = text.replace("\r\n", "\n").replace("\r", "\n").replace("﻿", "")
            val cues = mutableListOf<Cue>()
            for (block in normalized.split("\n\n")) {
                val lines = block.split("\n")
                val timing = lines.indexOfFirst { it.contains("-->") }
                if (timing < 0) continue
                val parts = lines[timing].split("-->")
                if (parts.size != 2) continue
                val start = timestamp(parts[0]) ?: continue
                val end = timestamp(parts[1].trimWhitespace().split(' ', '\t')[0]) ?: continue
                val body = clean(lines.drop(timing + 1).joinToString("\n"))
                if (body.isNotEmpty()) cues.add(Cue(start, end, body))
            }
            return SubtitleTrack(directed(persianised(cues)))
        }

        private val persianOnly = setOf('پ', 'چ', 'ژ', 'گ')
        private val swaps = mapOf('ي' to 'ی', 'ى' to 'ی', 'ك' to 'ک')

        /**
         * Persian subtitles are often typed on an Arabic keyboard: the Arabic yeh, which carries two
         * dots beneath it at the end of a word, and the Arabic kaf, which is drawn differently. A
         * Persian reader sees both as mistakes. Where the text is Persian (it uses letters Arabic
         * does not have), those two are replaced by their Persian forms.
         */
        fun persianised(cues: List<Cue>): List<Cue> {
            // Letter by letter: all of these are single UTF-16 units, so a zero-width non-joiner after one changes nothing.
            if (cues.none { cue -> cue.text.any { it in persianOnly } }) return cues
            return cues.map { cue -> cue.copy(text = String(CharArray(cue.text.length) { swaps[cue.text[it]] ?: cue.text[it] })) }
        }

        private fun rightToLeft(line: String): Boolean = line.any {
            val v = it.code
            v in 0x0590..0x08FF || v in 0xFB1D..0xFDFF || v in 0xFE70..0xFEFC
        }

        /**
         * Fixes the direction each line is laid out in, for a track in a right-to-left script.
         *
         * Persian subtitles come in two kinds. Properly written ones end a sentence with its full
         * stop. Most of the files in circulation, though, were prepared for players that lay every
         * line out left to right: there the full stop is typed first, so that it lands at the left
         * end, where a right-to-left sentence finishes. Each kind reads wrongly when drawn the
         * other's way (the full stop jumps to the start of the line), so this works out which kind
         * the file is and wraps every line in the direction it was written for.
         */
        fun directed(cues: List<Cue>): List<Cue> {
            val lines = cues.flatMap { cue -> cue.text.split('\n').filter { it.isNotEmpty() } }.filter { rightToLeft(it) }
            if (lines.isEmpty()) return cues
            // Only marks that take their direction from the line. The Arabic question mark belongs
            // to the script, stays with its sentence either way, and so says nothing here.
            val closers = setOf('.', '!', '?', '…', ':')
            var first = 0
            var last = 0
            for (line in lines) {
                val text = line.trimWhitespace()
                if (text.firstOrNull() in closers) first++
                if (text.lastOrNull() in closers) last++
            }
            // An isolate holds its direction whatever the direction of the view that draws it.
            val open = if (first > last) "⁦" else "⁧"
            val close = "⁩"
            return cues.map { cue ->
                // A line with nothing right-to-left in it needs no help and is left as it is.
                cue.copy(text = cue.text.split('\n').joinToString("\n") { if (rightToLeft(it)) open + it + close else it })
            }
        }

        /** "MM:SS.mmm" or "HH:MM:SS.mmm"; SRT uses a comma before the milliseconds. */
        fun timestamp(raw: String): Double? {
            val fields = raw.trimWhitespace().replace(',', '.').split(':').filter { it.isNotEmpty() }
            if (fields.size !in 2..3) return null
            var total = 0.0
            for (f in fields) {
                val v = f.asciiDouble()
                if (v == null || v < 0) return null
                total = total * 60 + v
            }
            return total
        }

        private val markup = Regex("<[^>]+>")
        private val position = Regex("\\{\\\\an?\\d\\}")

        /** Strips markup (`<i>`, `<c.yellow>`), ASS position overrides (`{\an8}`) and decodes the few entities that occur in practice. */
        fun clean(raw: String): String {
            var s = raw.replace(markup, "").replace(position, "")
            for ((entity, char) in listOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&nbsp;" to " ", "&lrm;" to "", "&rlm;" to "")) {
                s = s.replace(entity, char)
            }
            return s.trimWhitespaceAndNewlines()
        }
    }
}
