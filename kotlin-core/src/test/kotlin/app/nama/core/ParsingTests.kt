package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTests {
    @Test fun testSubtitleSiteListingAndChoice() {
        val listing = """
        <nav><ul>
        <li><a href="/subtitle/some-show/farsi_persian/101">Farsi/Persian — Some.Show.S01E02.1080p.WEB-DL.x264-GRP</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/102">Farsi/Persian — Some.Show.S01E02.1080p.BluRay.x265-ABC</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/102">Farsi/Persian — Some.Show.S01E02.720p.BluRay.x264-XYZ</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/103">Farsi/Persian — Some.Show.S01E03.1080p.BluRay.x265-ABC</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/104">Farsi/Persian — Some.Show.Season.1.Complete.BluRay</a></li>
        </ul></nav>
        """
        val entries = SubtitleSite.entries(listing)
        assertEquals(listOf("101", "102", "103", "104"), entries.map { it.path.takeLast(3) })
        assertEquals("a subtitle listed for two releases is one entry", 2, entries[1].releases.size)
        assertEquals("the right episode, and of those the one cut from the same source",
            "102", SubtitleSite.best("Some_Show_S01E02_1080p_BluRay_30nama.mkv", 1, 2, entries)?.path?.takeLast(3))
        assertEquals("101", SubtitleSite.best("Some.Show.S01E02.720p.WEB-DL.mkv", 1, 2, entries)?.path?.takeLast(3))
        assertEquals("no subtitle for the episode itself: the season's pack is the next best",
            "104", SubtitleSite.best("Some.Show.S01E09.BluRay.mkv", 1, 9, entries)?.path?.takeLast(3))
        assertNull(SubtitleSite.best("x.mkv", null, null, emptyList()))

        assertEquals("abc123DEF", SubtitleSite.download("""x "download_url":"https://api.subsource.net/v1/subtitle/download/abc123DEF" y""")?.lastPathComponent)
        assertNull(SubtitleSite.download("nothing here"))

        val pack = listOf("readme.txt", "Some.Show.S01E01.srt", "Some.Show.S01E02.srt", "Some.Show.S01E10.srt")
        assertEquals("Some.Show.S01E02.srt", SubtitleSite.pick(pack, 1, 2))
        assertNull("better none than the wrong episode", SubtitleSite.pick(pack, 1, 7))
        assertEquals("Film.2020.srt", SubtitleSite.pick(listOf("Film.2020.srt", "notes.nfo"), null, null))
        assertEquals("the Arabic Windows code page is read when the bytes are not UTF-8",
            "سلام", SubtitleSite.text(byteArrayOf(0xD3.toByte(), 0xE1.toByte(), 0xC7.toByte(), 0xE3.toByte())))
    }

    @Test fun testPersianSubtitlesKeepTheDirectionTheyWereWrittenFor() {
        fun vtt(lines: List<String>): String =
            "WEBVTT\n\n" + lines.mapIndexed { i, l -> "00:00:0$i.000 --> 00:00:0$i.900\n$l" }.joinToString("\n\n")
        val ltr = "⁦"
        val rtl = "⁧"
        val end = "⁩"
        // Typed for a left-to-right player: the full stop comes first. The question mark is no evidence.
        val old = SubtitleTrack.vtt(vtt(listOf("!باشه.فهمیدم", ".هی.بیا برقص\nچیه.ازدواج کردی؟", "خوبه")))
        assertEquals(ltr + "!باشه.فهمیدم" + end, old.text(0.5))
        assertEquals("each line on its own", ltr + ".هی.بیا برقص" + end + "\n" + ltr + "چیه.ازدواج کردی؟" + end, old.text(1.5))
        // Written properly: the full stop comes last.
        val proper = SubtitleTrack.vtt(vtt(listOf("باشه. فهمیدم!", "خوبه.")))
        assertEquals(rtl + "باشه. فهمیدم!" + end, proper.text(0.5))
        // Typed on an Arabic keyboard: the yeh with dots and the Arabic kaf become the Persian letters.
        val typed = SubtitleTrack.vtt(vtt(listOf("چي اومدي", "كتاب")))
        assertEquals(rtl + "چی اومدی" + end, typed.text(0.5))
        assertEquals(rtl + "کتاب" + end, typed.text(1.5))
        // The same before a zero-width non-joiner, which clings to the letter in front of it.
        val joined = SubtitleTrack.vtt(vtt(listOf("مي‌دوني چي")))
        assertEquals(rtl + "می‌دونی چی" + end, joined.text(0.5))
        // Arabic itself keeps its own letters.
        assertEquals(rtl + "في كتاب" + end, SubtitleTrack.vtt(vtt(listOf("في كتاب"))).text(0.5))
        // Nothing right-to-left in it: left alone.
        assertEquals("Fine. I get it!", SubtitleTrack.vtt(vtt(listOf("Fine. I get it!"))).text(0.5))
    }

    @Test fun testJSONAccessorsAreLenient() {
        val j = parse("""{"n":"42","f":7.5,"t":true,"s":" hi ","z":null,"a":[1,"2"],"big":1234567}""")
        assertEquals(42, j["n"].int)
        assertEquals(7.5, j["f"].double!!, 0.0)
        assertEquals("7.5", j["f"].string)
        assertEquals("1234567", j["big"].string)
        assertTrue(j["t"].bool)
        assertFalse(j["t"].isNull)
        assertNull("a boolean is not a number", j["t"].int)
        assertEquals("hi", j["s"].text)
        assertNull(j["z"].text)
        assertTrue(j["missing"]["deeper"][3].isNull)
        assertEquals(2, j["a"][1].int)
        assertEquals("round-trips", j, Json.parse(j.data()))
    }

    // Not in the Swift tests, which lean on Foundation's parser: the hand-written one here.
    @Test fun testTheParserReadsWhatJSONAllowsAndNothingElse() {
        assertEquals(Json.Str("a\"b\\c/é😀\n"), parse("\"a\\\"b\\\\c\\/\\u00e9\\ud83d\\ude00\\n\""))
        assertEquals(Json.Num(-12.5e3), parse(" -12.5e3 "))
        assertEquals(Json.Arr(emptyList()), parse("[ ]"))
        assertEquals(Json.Null, parse("null"))
        assertEquals("a BOM before the document is skipped", Json.Obj(emptyMap()), Json.parse(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), '{'.code.toByte(), '}'.code.toByte())))
        for (bad in listOf("", "{", "[1,]", "{\"a\":1,}", "01", "1.", "tru", "\"x", "{'a':1}", "[1] 2", "<html>")) {
            try {
                parse(bad)
                throw AssertionError("parsed $bad")
            } catch (_: Json.ParseException) {
            }
        }
        val deep = "[".repeat(10_000) + "]".repeat(10_000)
        try {
            parse(deep)
            throw AssertionError("parsed a document nested ten thousand deep")
        } catch (_: Json.ParseException) {
        }
        assertEquals("an 18-digit id is not an Int", null, parse("278294959021668101").int)
        assertEquals(1.0e15, parse("\"1e15\"").double!!, 0.0)
        assertNull("a number only in ASCII digits", parse("\"۴۲\"").int)
        assertNull(parse("\"1.5f\"").double)
    }

    @Test fun testVTTParsing() {
        val vtt = "﻿WEBVTT\r\n\r\nNOTE made by a tool\r\n\r\n1\r\n00:00:01.000 --> 00:00:03.500 line:90%\r\n<i>Hello</i> &amp; welcome\r\nsecond line\r\n\r\n00:01:02,250 --> 00:01:04,000\r\n{\\an8}سلام دنیا\r\n\r\n01:00:00.000 --> 01:00:02.000\r\nLate\r\n"
        val track = SubtitleTrack.vtt(vtt)
        assertEquals(3, track.cues.size)
        assertEquals(Cue(1.0, 3.5, "Hello & welcome\nsecond line"), track.cues[0])
        assertEquals(62.25, track.cues[1].start, 0.0)
        assertEquals("a right-to-left line is held in its direction; the others are untouched", "⁧سلام دنیا⁩", track.cues[1].text)
        assertEquals(3600.0, track.cues[2].start, 0.0)
    }

    @Test fun testCueLookup() {
        val track = SubtitleTrack(listOf(Cue(10.0, 12.0, "b"), Cue(1.0, 3.0, "a"), Cue(10.5, 11.0, "overlap")))
        assertEquals("", track.text(0.5))
        assertEquals("a", track.text(1.0))
        assertEquals("end is exclusive", "", track.text(3.0))
        assertEquals("overlap", track.text(10.7))
        assertEquals("falls back to an earlier cue that is still running", "b", track.text(11.5))
        assertEquals("", track.text(99.0))
        assertEquals("", SubtitleTrack().text(5.0))
    }

    @Test fun testBlurHashDecodes() {
        // Hash served by the API for a real poster.
        val px = BlurHash.pixels("bRIr1_%M~qxtyXtR%hRjIUxu", 8, 12)!!
        assertEquals(8 * 12 * 4, px.size)
        assertTrue("opaque", (3 until px.size step 4).all { px[it] == 255.toByte() })
        assertTrue("not a flat colour", px.toSet().size > 8)

        // A single-component hash is exactly its average colour.
        val flat = BlurHash.pixels("00OZZy", 2, 2)!!
        assertEquals(flat.take(4), flat.takeLast(4))

        // ...and that colour is what `average` reports.
        val average = BlurHash.average("00OZZy")!!
        assertEquals(Math.round(average.red * 255).toInt(), flat[0].toInt() and 0xFF)
        assertEquals(Math.round(average.blue * 255).toInt(), flat[2].toInt() and 0xFF)
        assertNull(BlurHash.average("0"))

        assertNull("wrong length", BlurHash.pixels("bRIr1_%M", 4, 4))
        assertNull(BlurHash.pixels("", 4, 4))
    }

    @Test fun testFormatting() {
        assertEquals("2:05", Fmt.clock(125.0))
        assertEquals("1:02:03", Fmt.clock(3723.0))
        assertEquals("0:00", Fmt.clock(-4.0))
        assertEquals("1h 35m", Fmt.runtime(95, Lang.en))
        assertEquals("50m", Fmt.runtime(50, Lang.en))
        assertEquals("۲ ساعت", Fmt.runtime(120, Lang.fa))
        assertEquals("۱ ساعت و ۳۵ دقیقه", Fmt.runtime(95, Lang.fa))
        assertEquals("1 س 35 د", Fmt.runtime(95, Lang.ar))
        assertNull(Fmt.runtime(0, Lang.en))
        assertEquals("S۲ · E۱۵", Fmt.persianDigits("S2 · E15"))
        assertEquals("8.5", Fmt.score(8.5))
        assertEquals("84,827", Fmt.count(84827, Lang.en))
        assertEquals(11, Episode.number("11,12"))
        assertEquals("7", Episode.label(null, 7))
    }

    // Not in the Swift tests: the ways Java's formatting differs from C's, which the Swift side gets for free.
    @Test fun testFormattingDoesNotFollowTheDevice() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("fa", "IR"))
            assertEquals("ASCII digits whatever the device's language", "1:02:03", Fmt.clock(3723.0))
            assertEquals("8.5", Fmt.score(8.5))
        } finally {
            java.util.Locale.setDefault(saved)
        }
        assertEquals("a tie rounds to even, as printf does", "8.2", Fmt.score(8.25))
        assertEquals("10", Fmt.score(10.0))
        assertEquals("۸۴٬۸۲۷", Fmt.count(84827, Lang.fa))
    }

    @Test fun testEveryStringIsTranslated() {
        for (key in S.entries) {
            assertNotNull("missing English: $key", Loc.en[key])
            assertNotNull("missing Persian: $key", Loc.fa[key])
            assertNotNull("missing Arabic: $key", Loc.ar[key])
        }
        assertEquals("فصل ۲ · قسمت ۵", Loc(Lang.fa).episodeTag(2, "5"))
        assertEquals("S2 · E5", Loc(Lang.en).episodeTag(2, "5"))
        assertEquals("الموسم 2 · الحلقة 5", Loc(Lang.ar).episodeTag(2, "5"))
        assertEquals("تشغيل", Loc(Lang.ar)(S.play))
        assertEquals("the order the language setting steps through", listOf(Lang.en, Lang.ar, Lang.fa), Lang.entries.toList())
        assertEquals(listOf(false, true, true), Lang.entries.map { it.isRTL })
        assertEquals("30nama has no Arabic catalogue", listOf("en", "en", "fa"), Lang.entries.map { it.catalogue })
    }

    // Not in the Swift tests: the Kotlin tables are generated from L10n.swift; this catches a run that was forgotten.
    @Test fun testTheTablesMatchTheSwiftFile() {
        val swift = java.io.File("../30nama/Core/L10n.swift")
        assertTrue("the Swift sources sit beside kotlin-core/ in the repository", swift.exists())
        val text = swift.readText()
        val cases = Regex("enum S: String, CaseIterable \\{(.*?)\\n\\}", RegexOption.DOT_MATCHES_ALL).find(text)!!.groupValues[1]
        val keys = Regex("case (.+)").findAll(cases).flatMap { m -> m.groupValues[1].split(',').map { it.trim() } }.filter { it.isNotEmpty() }.toList()
        assertEquals("run kotlin-core/tools/gen-l10n.py", keys, S.entries.map { it.name })
        assertEquals("Up to date", Loc(Lang.en)(S.cloudSynced))
    }
}
