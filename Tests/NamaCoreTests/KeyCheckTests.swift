import XCTest
@testable import NamaCore

final class KeyCheckTests: XCTestCase {
    private func answer(_ status: Int, asked: Recorder? = nil) -> Transport {
        { request in
            asked?.add(request.url!.path)
            return (Data(), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
        }
    }

    func testARefusalIsTheKeyAnythingElseTheService() async {
        let asked = Recorder()
        let works = await KeyCheck.check(.tmdb, key: "abc", transport: answer(200, asked: asked))
        XCTAssertEqual(works, .works)
        XCTAssertTrue(asked.values[0].hasSuffix("/3/configuration"))
        let refused = await KeyCheck.check(.tmdb, key: "abc", transport: answer(401))
        XCTAssertEqual(refused, .rejected)
        let forbidden = await KeyCheck.check(.subsource, key: "abc", transport: answer(403))
        XCTAssertEqual(forbidden, .rejected)
        let down = await KeyCheck.check(.tmdb, key: "abc", transport: answer(502))
        XCTAssertEqual(down, .unreachable)
        let gemini = await KeyCheck.check(.gemini, key: "abc", transport: answer(400))
        XCTAssertEqual(gemini, .rejected, "Gemini answers a wrong key with 400")
    }

    func testNoAnswerKeepsTheKey() async {
        // From Iran the service may be filtered while the key is fine: unreachable, not wrong.
        let state = await KeyCheck.check(.subsource, key: "abc") { _ in throw URLError(.timedOut) }
        XCTAssertEqual(state, .unreachable)
    }

    func testSubSourceIsAskedWithItsOwnHeader() {
        XCTAssertEqual(KeyCheck.request(.subsource, key: "k1").value(forHTTPHeaderField: "X-API-Key"), "k1")
    }
}

final class ReadOnlyTests: XCTestCase {
    private func request(_ action: String, _ body: String = "") -> URLRequest {
        var r = URLRequest(url: URL(string: "https://h.example.com/api/v2/action/\(action)")!)
        r.httpMethod = "POST"
        r.httpBody = Data(body.utf8)
        return r
    }

    func testOnlyTheAccountsWritesAreHeldBack() async throws {
        XCTAssertTrue(ReadOnly.isWrite(request("observer", "time=30")))
        XCTAssertTrue(ReadOnly.isWrite(request("user_list", "action=add&post_id=1")))
        XCTAssertFalse(ReadOnly.isWrite(request("user_list", "action=list")), "reading a list is not a change")
        XCTAssertFalse(ReadOnly.isWrite(request("single", "post_id=1")))
        let asked = Recorder()
        let net = ReadOnly.wrap { r in
            asked.add(r.url!.lastPathComponent)
            return (Data("{}".utf8), HTTPURLResponse(url: r.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
        }
        let (data, _) = try await net(request("observer", "time=30"))
        XCTAssertTrue(String(decoding: data, as: UTF8.self).contains(#""success":true"#))
        _ = try await net(request("single"))
        XCTAssertEqual(asked.values, ["single"], "the write never reached the network")
    }
}
