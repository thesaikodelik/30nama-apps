import XCTest
@testable import NamaCore

/// Scripted network: answers each request from a handler and records what was asked.
final class FakeNetwork: @unchecked Sendable {
    private let lock = NSLock()
    private var log: [(action: String, host: String, headers: [String: String], body: String)] = []
    private var handler: (String, String) -> (Int, String, [String: String])

    init(_ handler: @escaping (_ action: String, _ host: String) -> (Int, String, [String: String])) {
        self.handler = handler
    }

    var calls: [String] { lock.withLock { log.map(\.action) } }
    var last: (action: String, host: String, headers: [String: String], body: String)? { lock.withLock { log.last } }

    var transport: Transport {
        { [self] request in
            let url = request.url!
            let action = url.path.contains("/action/") ? url.lastPathComponent : "discovery"
            let host = url.host ?? ""
            let (status, body, headers) = lock.withLock { () -> (Int, String, [String: String]) in
                log.append((action, host, request.allHTTPHeaderFields ?? [:], String(data: request.httpBody ?? Data(), encoding: .utf8) ?? ""))
                return handler(action, host)
            }
            if status == 0 { throw URLError(.timedOut) }
            return (Data(body.utf8), HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: headers)!)
        }
    }
}

private let ok = #"{"success":true,"msg":null,"result":{"posts":[]}}"#
private let loginRequired = #"{"success":false,"msg":"ابتدا وارد حساب کاربری خود شوید","result":null}"#

final class APIClientTests: XCTestCase {
    private func config() -> APIConfig {
        var c = APIConfig()
        c.backoff = [0.02, 0.02]
        c.requestSpacing = 0
        return c
    }

    func testBootstrapSkipsHostsThatDoNotAnswerTheAPI() async throws {
        let net = FakeNetwork { action, host in
            if action == "discovery" { return (0, "", [:]) }                       // discovery unreachable -> built-in hosts
            if host.contains("kazem") { return (403, "<html>blocked</html>", [:]) } // geo-blocked host answers with HTML
            return (200, ok, [:])
        }
        let api = APIClient(config: config(), transport: net.transport)
        let host = try await api.bootstrap()
        XCTAssertEqual(host, "https://world.hana-api.com/api/v2")
    }

    func testTheIranianHostSaysTheUserIsInIran() async throws {
        // Only the Iranian host answers, as from inside Iran.
        let inside = FakeNetwork { action, host in
            if action == "discovery" { return (200, #"[{"location":"WORLD","host":"https://w.example.com/api/v2"},{"location":"IR","host":"https://i.example.com/api/v2"}]"#, [:]) }
            return host == "i.example.com" ? (200, ok, [:]) : (0, "", [:])
        }
        let there = APIClient(config: config(), transport: inside.transport)
        _ = try await there.bootstrap()
        let inIran = await there.inIran
        XCTAssertTrue(inIran)
        // Abroad the Iranian host turns the request away.
        let outside = FakeNetwork { action, host in
            if action == "discovery" { return (0, "", [:]) }
            if host.contains("kazem") { return (403, "<html>blocked</html>", [:]) }
            return (200, ok, [:])
        }
        let here = APIClient(config: config(), transport: outside.transport)
        _ = try await here.bootstrap()
        let abroad = await here.inIran
        XCTAssertFalse(abroad)
    }

    func testBootstrapPrefersPublishedEndpointList() async throws {
        let net = FakeNetwork { action, host in
            if action == "discovery" { return (200, #"[{"location":"X","host":"https://new.example.com/api/v2"}]"#, [:]) }
            return host == "new.example.com" ? (200, ok, [:]) : (0, "", [:])
        }
        let api = APIClient(config: config(), transport: net.transport)
        let host = try await api.bootstrap()
        XCTAssertEqual(host, "https://new.example.com/api/v2")
    }

    func testBootstrapFailsWhenNothingAnswers() async {
        let net = FakeNetwork { _, _ in (0, "", [:]) }
        let api = APIClient(config: config(), transport: net.transport)
        do {
            try await api.bootstrap()
            XCTFail("expected unreachable")
        } catch {
            XCTAssertEqual(error as? APIError, .unreachable)
        }
    }

    func testRequestCarriesHeadersAndFormBody() async throws {
        let net = FakeNetwork { _, _ in (200, ok, [:]) }
        let api = APIClient(config: config(), transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        await api.setToken("tok")
        await api.setLanguage(.en)
        _ = try await api.get("search", ["query": "breaking bad & co", "page": "1"])
        let last = try XCTUnwrap(net.last)
        XCTAssertEqual(last.action, "search")
        XCTAssertEqual(last.body, "page=1&query=breaking%20bad%20%26%20co")
        XCTAssertEqual(last.headers["c-token"], "tok")
        XCTAssertEqual(last.headers["c-language"], "en")
        XCTAssertEqual(last.headers["c-version-number"], "1.0.0")
        XCTAssertEqual(last.headers["c-api-key"], APIConfig().apiKeys[0])
    }

    func testRateLimitIsRetriedThenSucceeds() async throws {
        let remaining = Counter(2)
        let net = FakeNetwork { _, _ in
            remaining.take() ? (429, "error code: 1015", ["Retry-After": "0"]) : (200, ok, [:])
        }
        let api = APIClient(config: config(), transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        _ = try await api.get("single", ["post_id": "1"])
        XCTAssertEqual(net.calls, ["single", "single", "single"])
    }

    func testPersistentFailureGivesUpAfterRetries() async {
        let net = FakeNetwork { _, _ in (502, "<html>502</html>", [:]) }
        let api = APIClient(config: config(), transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        do {
            _ = try await api.get("single")
            XCTFail("expected failure")
        } catch {
            guard case APIError.transient = error else { return XCTFail("wrong error \(error)") }
        }
        XCTAssertEqual(net.calls.count, 3, "first try plus two retries")
    }

    func testProgressReportsAreNeverReplayed() async {
        let net = FakeNetwork { _, _ in (0, "", [:]) }
        let api = APIClient(config: config(), transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        _ = try? await api.call("observer", ["time": "10"])
        XCTAssertEqual(net.calls, ["observer"])
    }

    func testAnEmptyAnswerToAProgressReportIsASuccess() async throws {
        for status in [200, 204] {
            let net = FakeNetwork { _, _ in (status, "", [:]) }
            let api = APIClient(config: config(), transport: net.transport)
            await api.useHost("https://h.example.com/api/v2")
            let answer = try await api.call("observer", ["time": "30"])
            XCTAssertTrue(answer.success, "HTTP \(status) with no body")
            XCTAssertEqual(net.calls, ["observer"])
        }
    }

    func testServerErrorSurfacesItsMessage() async {
        let net = FakeNetwork { _, _ in (200, #"{"success":false,"msg":"nope","result":null}"#, [:]) }
        let api = APIClient(config: config(), transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        do {
            _ = try await api.get("single")
            XCTFail("expected failure")
        } catch {
            XCTAssertEqual(error as? APIError, .server("nope"))
        }
        XCTAssertEqual(net.calls, ["single"], "a real API answer is not retried")
    }

    func testSessionExpiryIsConfirmedAgainstTheProfile() async throws {
        let expired = Counter(0)
        // The stream says "login required" but the profile still loads: a subscription problem, not a dead session.
        var net = FakeNetwork { action, _ in action == "user" ? (200, ok, [:]) : (200, loginRequired, [:]) }
        var api = APIClient(config: config(), transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        await api.setToken("tok")
        await api.setSessionExpiredHandler { expired.add() }
        let e = try await api.call("stream", ["post_id": "1"])
        XCTAssertFalse(e.success)
        XCTAssertEqual(net.calls, ["stream", "user"])
        XCTAssertEqual(expired.value, 0)

        // The profile refuses too: the session is gone.
        net = FakeNetwork { _, _ in (200, loginRequired, [:]) }
        api = APIClient(config: config(), transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        await api.setToken("tok")
        await api.setSessionExpiredHandler { expired.add() }
        _ = try await api.call("stream", ["post_id": "1"])
        XCTAssertEqual(expired.value, 1)

        // A guest is never "expired".
        net = FakeNetwork { _, _ in (200, loginRequired, [:]) }
        api = APIClient(config: config(), transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        await api.setSessionExpiredHandler { expired.add() }
        _ = try await api.call("continue_watching")
        XCTAssertEqual(net.calls, ["continue_watching"])
        XCTAssertEqual(expired.value, 1)
    }

    func testGateLimitsConcurrencyAndServesScreenCallsFirst() async {
        let gate = RequestGate(limit: 2, spacing: 0)
        let order = Recorder()
        let peak = Counter(0), now = Counter(0)

        // Fill both slots, then queue background work followed by one screen call.
        await gate.acquire(background: false)
        await gate.acquire(background: false)
        await withTaskGroup(of: Void.self) { group in
            for i in 0..<4 {
                group.addTask {
                    await gate.acquire(background: true)
                    order.add("bg\(i)"); peak.raise(to: now.add())
                    try? await Task.sleep(nanoseconds: 20_000_000)
                    now.sub(); await gate.release(background: true)
                }
            }
            try? await Task.sleep(nanoseconds: 50_000_000)
            group.addTask {
                await gate.acquire(background: false)
                order.add("core"); peak.raise(to: now.add())
                try? await Task.sleep(nanoseconds: 20_000_000)
                now.sub(); await gate.release()
            }
            try? await Task.sleep(nanoseconds: 50_000_000)
            await gate.release()
            try? await Task.sleep(nanoseconds: 10_000_000)
            await gate.release()
        }
        XCTAssertEqual(order.values.first, "core", "screen call jumps the background queue")
        XCTAssertEqual(order.values.count, 5)
        XCTAssertLessThanOrEqual(peak.value, 2)
    }

    func testBackgroundCallsLeaveASlotForTheScreen() async throws {
        let gate = RequestGate(limit: 2, spacing: 0)
        await gate.acquire(background: true)
        let got = Counter(0)
        let second = Task { await gate.acquire(background: true); _ = got.add() }
        try await Task.sleep(nanoseconds: 20_000_000)
        XCTAssertEqual(got.value, 0, "a second background call waits")
        // The screen's call gets the free slot at once.
        await gate.acquire(background: false)
        await gate.release(background: false)
        await gate.release(background: true)
        await second.value
        XCTAssertEqual(got.value, 1)
        await gate.release(background: true)
    }

    func testARateLimitPausesTheGateEvenWithoutARetry() async throws {
        let net = FakeNetwork { action, _ in
            action == "observer" ? (429, "error code: 1015", [:]) : (200, ok, [:])
        }
        var c = config()
        c.backoff = [0.3, 0.3]
        let api = APIClient(config: c, transport: net.transport)
        await api.useHost("https://h.example.com/api/v2")
        // A write is never replayed, so it fails at once; the gate still waits before the next call.
        do {
            _ = try await api.call("observer", ["time": "10"])
            XCTFail("expected failure")
        } catch {
            guard case APIError.transient(_, _, true) = error else { return XCTFail("not a rate limit: \(error)") }
        }
        let start = Date()
        _ = try await api.get("profile")
        XCTAssertGreaterThanOrEqual(Date().timeIntervalSince(start), 0.25, "the next call waited out the pause")
    }
}

final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var n: Int
    init(_ n: Int) { self.n = n }
    var value: Int { lock.withLock { n } }
    /// Decrements while positive; true if it did.
    func take() -> Bool { lock.withLock { if n > 0 { n -= 1; return true }; return false } }
    @discardableResult func add() -> Int { lock.withLock { n += 1; return n } }
    func sub() { lock.withLock { n -= 1 } }
    func raise(to v: Int) { lock.withLock { n = max(n, v) } }
}

final class Recorder: @unchecked Sendable {
    private let lock = NSLock()
    private var items: [String] = []
    var values: [String] { lock.withLock { items } }
    func add(_ s: String) { lock.withLock { items.append(s) } }
}
