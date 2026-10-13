import XCTest
@testable import NamaCore

final class ParsingTests: XCTestCase {
    func testSubtitleSiteListingAndChoice() {
        let listing = """
        <nav><ul>
        <li><a href="/subtitle/some-show/farsi_persian/101">Farsi/Persian — Some.Show.S01E02.1080p.WEB-DL.x264-GRP</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/102">Farsi/Persian — Some.Show.S01E02.1080p.BluRay.x265-ABC</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/102">Farsi/Persian — Some.Show.S01E02.720p.BluRay.x264-XYZ</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/103">Farsi/Persian — Some.Show.S01E03.1080p.BluRay.x265-ABC</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/104">Farsi/Persian — Some.Show.Season.1.Complete.BluRay</a></li>
        </ul></nav>
        """
        let entries = SubtitleSite.entries(in: listing)
        XCTAssertEqual(entries.map(\.path).map { $0.suffix(3) }, ["101", "102", "103", "104"])
        XCTAssertEqual(entries[1].releases.count, 2, "a subtitle listed for two releases is one entry")
        XCTAssertEqual(SubtitleSite.best(for: "Some_Show_S01E02_1080p_BluRay_30nama.mkv", season: 1, episode: 2, among: entries)?.path.suffix(3), "102",
                       "the right episode, and of those the one cut from the same source")
        XCTAssertEqual(SubtitleSite.best(for: "Some.Show.S01E02.720p.WEB-DL.mkv", season: 1, episode: 2, among: entries)?.path.suffix(3), "101")
        XCTAssertEqual(SubtitleSite.best(for: "Some.Show.S01E09.BluRay.mkv", season: 1, episode: 9, among: entries)?.path.suffix(3), "104",
                       "no subtitle for the episode itself: the season's pack is the next best")
        XCTAssertNil(SubtitleSite.best(for: "x.mkv", season: nil, episode: nil, among: []))

        XCTAssertEqual(SubtitleSite.download(in: #"x "download_url":"https://api.subsource.net/v1/subtitle/download/abc123DEF" y"#)?.lastPathComponent, "abc123DEF")
        XCTAssertNil(SubtitleSite.download(in: "nothing here"))

        let pack = ["readme.txt", "Some.Show.S01E01.srt", "Some.Show.S01E02.srt", "Some.Show.S01E10.srt"]
        XCTAssertEqual(SubtitleSite.pick(from: pack, season: 1, episode: 2), "Some.Show.S01E02.srt")
        XCTAssertNil(SubtitleSite.pick(from: pack, season: 1, episode: 7), "better none than the wrong episode")
        XCTAssertEqual(SubtitleSite.pick(from: ["Film.2020.srt", "notes.nfo"], season: nil, episode: nil), "Film.2020.srt")
        XCTAssertEqual(SubtitleSite.text(Data([0xD3, 0xE1, 0xC7, 0xE3])), "سلام", "the Arabic Windows code page is read when the bytes are not UTF-8")
    }

    func testPersianSubtitlesKeepTheDirectionTheyWereWrittenFor() {
        func vtt(_ lines: [String]) -> String {
            "WEBVTT\n\n" + lines.enumerated().map { "00:00:0\($0).000 --> 00:00:0\($0).900\n\($1)" }.joined(separator: "\n\n")
        }
        let ltr = "\u{2066}", rtl = "\u{2067}", end = "\u{2069}"
        // Typed for a left-to-right player: the full stop comes first. The question mark is no evidence.
        let old = SubtitleTrack(vtt: vtt(["!باشه.فهمیدم", ".هی.بیا برقص\nچیه.ازدواج کردی؟", "خوبه"]))
        XCTAssertEqual(old.text(at: 0.5), ltr + "!باشه.فهمیدم" + end)
        XCTAssertEqual(old.text(at: 1.5), ltr + ".هی.بیا برقص" + end + "\n" + ltr + "چیه.ازدواج کردی؟" + end, "each line on its own")
        // Written properly: the full stop comes last.
        let proper = SubtitleTrack(vtt: vtt(["باشه. فهمیدم!", "خوبه."]))
        XCTAssertEqual(proper.text(at: 0.5), rtl + "باشه. فهمیدم!" + end)
        // Nothing right-to-left in it: left alone.
        XCTAssertEqual(SubtitleTrack(vtt: vtt(["Fine. I get it!"])).text(at: 0.5), "Fine. I get it!")
    }

    func testJSONAccessorsAreLenient() throws {
        let j = try JSON.parse(Data(#"{"n":"42","f":7.5,"t":true,"s":" hi ","z":null,"a":[1,"2"],"big":1234567}"#.utf8))
        XCTAssertEqual(j["n"].int, 42)
        XCTAssertEqual(j["f"].double, 7.5)
        XCTAssertEqual(j["f"].string, "7.5")
        XCTAssertEqual(j["big"].string, "1234567")
        XCTAssertTrue(j["t"].bool)
        XCTAssertFalse(j["t"].isNull)
        XCTAssertNil(j["t"].int, "a boolean is not a number")
        XCTAssertEqual(j["s"].text, "hi")
        XCTAssertNil(j["z"].text)
        XCTAssertTrue(j["missing"]["deeper"][3].isNull)
        XCTAssertEqual(j["a"][1].int, 2)
        XCTAssertEqual(try JSON.parse(XCTUnwrap(j.data())), j, "round-trips")
    }

    func testVTTParsing() {
        let vtt = "\u{FEFF}WEBVTT\r\n\r\nNOTE made by a tool\r\n\r\n1\r\n00:00:01.000 --> 00:00:03.500 line:90%\r\n<i>Hello</i> &amp; welcome\r\nsecond line\r\n\r\n00:01:02,250 --> 00:01:04,000\r\n{\\an8}سلام دنیا\r\n\r\n01:00:00.000 --> 01:00:02.000\r\nLate\r\n"
        let track = SubtitleTrack(vtt: vtt)
        XCTAssertEqual(track.cues.count, 3)
        XCTAssertEqual(track.cues[0], Cue(start: 1, end: 3.5, text: "Hello & welcome\nsecond line"))
        XCTAssertEqual(track.cues[1].start, 62.25)
        XCTAssertEqual(track.cues[1].text, "\u{2067}سلام دنیا\u{2069}", "a right-to-left line is held in its direction; the others are untouched")
        XCTAssertEqual(track.cues[2].start, 3600)
    }

    func testCueLookup() {
        let track = SubtitleTrack(cues: [
            Cue(start: 10, end: 12, text: "b"),
            Cue(start: 1, end: 3, text: "a"),
            Cue(start: 10.5, end: 11, text: "overlap"),
        ])
        XCTAssertEqual(track.text(at: 0.5), "")
        XCTAssertEqual(track.text(at: 1), "a")
        XCTAssertEqual(track.text(at: 3), "", "end is exclusive")
        XCTAssertEqual(track.text(at: 10.7), "overlap")
        XCTAssertEqual(track.text(at: 11.5), "b", "falls back to an earlier cue that is still running")
        XCTAssertEqual(track.text(at: 99), "")
        XCTAssertEqual(SubtitleTrack().text(at: 5), "")
    }

    func testBlurHashDecodes() throws {
        // Hash served by the API for a real poster.
        let px = try XCTUnwrap(BlurHash.pixels("bRIr1_%M~qxtyXtR%hRjIUxu", width: 8, height: 12))
        XCTAssertEqual(px.count, 8 * 12 * 4)
        XCTAssertTrue(stride(from: 3, to: px.count, by: 4).allSatisfy { px[$0] == 255 }, "opaque")
        XCTAssertGreaterThan(Set(px).count, 8, "not a flat colour")

        // A single-component hash is exactly its average colour.
        let flat = try XCTUnwrap(BlurHash.pixels("00OZZy", width: 2, height: 2))
        XCTAssertEqual(Array(flat.prefix(4)), Array(flat.suffix(4)))

        // ...and that colour is what `average` reports.
        let average = try XCTUnwrap(BlurHash.average("00OZZy"))
        XCTAssertEqual(UInt8((average.red * 255).rounded()), flat[0])
        XCTAssertEqual(UInt8((average.blue * 255).rounded()), flat[2])
        XCTAssertNil(BlurHash.average("0"))

        XCTAssertNil(BlurHash.pixels("bRIr1_%M", width: 4, height: 4), "wrong length")
        XCTAssertNil(BlurHash.pixels("", width: 4, height: 4))
    }

    func testFormatting() {
        XCTAssertEqual(Fmt.clock(125), "2:05")
        XCTAssertEqual(Fmt.clock(3723), "1:02:03")
        XCTAssertEqual(Fmt.clock(-4), "0:00")
        XCTAssertEqual(Fmt.runtime(minutes: 95, lang: .en), "1h 35m")
        XCTAssertEqual(Fmt.runtime(minutes: 50, lang: .en), "50m")
        XCTAssertEqual(Fmt.runtime(minutes: 120, lang: .fa), "۲ ساعت")
        XCTAssertEqual(Fmt.runtime(minutes: 95, lang: .fa), "۱ ساعت و ۳۵ دقیقه")
        XCTAssertEqual(Fmt.runtime(minutes: 95, lang: .ar), "1 س 35 د")
        XCTAssertNil(Fmt.runtime(minutes: 0, lang: .en))
        XCTAssertEqual(Fmt.persianDigits("S2 · E15"), "S۲ · E۱۵")
        XCTAssertEqual(Fmt.score(8.5), "8.5")
        XCTAssertEqual(Fmt.count(84827, lang: .en), "84,827")
        XCTAssertEqual(Episode.number("11,12"), 11)
        XCTAssertEqual(Episode.label(nil, fallback: 7), "7")
    }

    func testEveryStringIsTranslated() {
        for key in S.allCases {
            XCTAssertNotNil(Loc.en[key], "missing English: \(key)")
            XCTAssertNotNil(Loc.fa[key], "missing Persian: \(key)")
            XCTAssertNotNil(Loc.ar[key], "missing Arabic: \(key)")
        }
        XCTAssertEqual(Loc(lang: .fa).episodeTag(season: 2, episode: "5"), "فصل ۲ · قسمت ۵")
        XCTAssertEqual(Loc(lang: .en).episodeTag(season: 2, episode: "5"), "S2 · E5")
        XCTAssertEqual(Loc(lang: .ar).episodeTag(season: 2, episode: "5"), "الموسم 2 · الحلقة 5")
        XCTAssertEqual(Loc(lang: .ar)(.play), "تشغيل")
        XCTAssertEqual(Lang.allCases, [.en, .ar, .fa], "the order the language setting steps through")
        XCTAssertEqual(Lang.allCases.map(\.isRTL), [false, true, true])
        XCTAssertEqual(Lang.allCases.map(\.catalogue), ["en", "en", "fa"], "30nama has no Arabic catalogue")
    }
}
