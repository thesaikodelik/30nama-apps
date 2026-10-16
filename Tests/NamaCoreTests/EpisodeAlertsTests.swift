import XCTest
@testable import NamaCore

final class EpisodeAlertsTests: XCTestCase {
    private func series(_ id: Int, next: String?) -> Post {
        var json: [String: Any] = ["id": id, "title": ["english": "Show \(id)"]]
        if let next { json["air"] = ["next_episode_date": ["g": next]] }
        return Post.list(JSON(["posts": [json]] as [String: Any])).first!
    }

    private func day(_ text: String) -> Date {
        let format = ISO8601DateFormatter()
        return format.date(from: text + "T00:00:00Z")!
    }

    func testTellsOfAnEpisodeOnceItsDayHasCome() {
        var alerts = EpisodeAlerts()
        XCTAssertTrue(alerts.update(with: [series(1, next: "2026-10-20")], now: day("2026-10-16")).isEmpty)
        XCTAssertTrue(alerts.update(with: [series(1, next: "2026-10-20")], now: day("2026-10-19")).isEmpty, "not yet")
        let out = alerts.update(with: [series(1, next: "2026-10-27")], now: day("2026-10-21"))
        XCTAssertEqual(out.map(\.id), [1], "the day passed, and the server has moved on to the next one")
        XCTAssertTrue(alerts.update(with: [series(1, next: "2026-10-27")], now: day("2026-10-22")).isEmpty, "said once")
        XCTAssertEqual(alerts.expected[1], day("2026-10-27"), "and the next one is awaited")
    }

    func testSaysNothingOfASeriesNoLongerFollowed() {
        var alerts = EpisodeAlerts()
        _ = alerts.update(with: [series(1, next: "2026-10-20")], now: day("2026-10-16"))
        XCTAssertTrue(alerts.update(with: [], now: day("2026-10-21")).isEmpty)
        XCTAssertTrue(alerts.expected.isEmpty)
    }

    func testSurvivesAPropertyList() {
        var alerts = EpisodeAlerts()
        _ = alerts.update(with: [series(4, next: "2026-11-02")], now: day("2026-10-16"))
        XCTAssertEqual(EpisodeAlerts(plist: alerts.plist), alerts)
    }
}
