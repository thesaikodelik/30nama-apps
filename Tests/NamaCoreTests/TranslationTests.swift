import XCTest
@testable import NamaCore

/// As the Android app's TranslationTest and the Windows app's TranslationTests.
final class TranslationTests: XCTestCase {
    private let cues = (0..<200).map { Cue(start: Double($0) * 3, end: Double($0) * 3 + 2, text: "line \($0)") }

    func testStartsWhereTheViewerIsThenGoesBack() {
        XCTAssertEqual(Translation.batchCount(cues), 3)
        let at = Translation.batch(at: 90 * 3, in: cues)
        XCTAssertEqual(at, 1)
        XCTAssertEqual(Translation.order(count: 3, start: at), [1, 2, 0])
        XCTAssertEqual(Translation.order(count: 3, start: 0), [0, 1, 2])
        XCTAssertEqual(Translation.batch(at: 10_000, in: cues), 2, "past the last line, the last batch")
        XCTAssertEqual(Translation.batch(2, of: cues).count, 40)
    }

    func testTheRequestAsksForOneStringPerLine() throws {
        let body = try JSON.parse(Translation.request(["Hello.", "Two\nlines"], target: .fa, title: "Heat"))
        let sent = try JSON.parse(Data((body["contents"][0]["parts"][0]["text"].string ?? "").utf8)).array.map(\.string)
        XCTAssertEqual(sent, ["Hello.", "Two | lines"])
        XCTAssertEqual(body["generationConfig"]["responseSchema"]["type"].string, "ARRAY")
        XCTAssertTrue(body["systemInstruction"]["parts"][0]["text"].string?.contains("Persian") == true)
    }

    private func answer(_ text: String) -> JSON {
        JSON(["candidates": [["content": ["parts": [["text": text]]]]]] as [String: Any])
    }

    func testReadsTheAnswerLineByLine() {
        XCTAssertEqual(Translation.read(answer(#"["سلام.", "دو | خط"]"#), count: 2), ["سلام.", "دو\nخط"])
        XCTAssertEqual(Translation.read(answer(#"["سلام."]"#), count: 2), ["سلام.", nil], "a line that did not come back keeps its original")
        XCTAssertEqual(Translation.read(answer("```json\n[\"سلام.\"]\n```"), count: 1), ["سلام."])
        XCTAssertNil(Translation.read(answer("Sorry, I can't."), count: 1))
        XCTAssertNil(Translation.read(JSON([String: Any]()), count: 1))
    }

    func testABatchReplacesItsOwnLinesOnly() {
        let output = Translation.apply(cues, batch: 1, lines: ["یک", nil])
        XCTAssertEqual(output[80].text, "یک")
        XCTAssertEqual(output[81].text, "line 81")
        XCTAssertEqual(output[79].text, "line 79")
        XCTAssertEqual(output[80].start, cues[80].start)
    }

    func testArabicForAnArabicInterfacePersianOtherwise() {
        XCTAssertEqual(Translation.target(.ar), .ar)
        XCTAssertEqual(Translation.target(.en), .fa)
    }

    private func server(asked: Recorder? = nil, _ reply: @escaping @Sendable (URLRequest) -> (Int, String)) -> Transport {
        { request in
            asked?.add(request.url!.path)
            let (status, body) = reply(request)
            return (Data(body.utf8), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
        }
    }

    private func folder() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent("nama-gemini-" + UUID().uuidString) }

    private func encoded(_ json: JSON) -> String { String(decoding: json.data()!, as: UTF8.self) }

    func testABatchIsTranslatedOnceAndKept() async throws {
        let asked = Recorder()
        let place = folder()
        let reply = encoded(answer(#"["یک", "دو"]"#))
        let gemini = Gemini(transport: server(asked: asked) { _ in (200, reply) }, folder: place, wait: { _ in })
        let two = Array(cues.prefix(2))
        let first = await gemini.translate(key: "key", source: "file.vtt", target: .fa, title: "Heat", batch: 0, cues: two)
        XCTAssertEqual(first, .lines(["یک", "دو"]))
        let again = await gemini.translate(key: "key", source: "file.vtt", target: .fa, title: "Heat", batch: 0, cues: two)
        XCTAssertEqual(again, .lines(["یک", "دو"]))
        XCTAssertEqual(asked.values.count, 1, "the second came from the disk")
        XCTAssertTrue(asked.values[0].hasSuffix("gemini-flash-lite-latest:generateContent"))
        try? FileManager.default.removeItem(at: place)
    }

    func testARefusedKeyAndASpentDayStop() async {
        let two = Array(cues.prefix(2))
        let refused = Gemini(transport: server { _ in (400, #"{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}"#) }, folder: folder(), wait: { _ in })
        let r = await refused.translate(key: "bad", source: "f", target: .fa, title: "Heat", batch: 0, cues: two)
        XCTAssertEqual(r, .rejected)
        let asked = Recorder()
        let spent = Gemini(transport: server(asked: asked) { _ in (429, #"{"error":{"message":"GenerateRequestsPerDay quota"}}"#) }, folder: folder(), wait: { _ in })
        let s = await spent.translate(key: "key", source: "f", target: .fa, title: "Heat", batch: 0, cues: two)
        XCTAssertEqual(s, .unavailable)
        XCTAssertEqual(asked.values.count, 1, "a spent day is not asked again")
    }

    func testAModelNotOfferedGivesWayToTheNext() async {
        let asked = Recorder()
        let reply = encoded(answer(#"["یک"]"#))
        let gemini = Gemini(transport: server(asked: asked) { r in r.url!.path.contains("lite") ? (404, "{}") : (200, reply) }, folder: folder(), wait: { _ in })
        let out = await gemini.translate(key: "key", source: "f", target: .fa, title: "Heat", batch: 0, cues: Array(cues.prefix(1)))
        XCTAssertEqual(out, .lines(["یک"]))
        XCTAssertEqual(asked.values.count, 2)
    }
}
