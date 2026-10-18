import XCTest
@testable import NamaCore

final class DownloadRulesTests: XCTestCase {
    func testWaitsLongerEachTime() {
        XCTAssertEqual(RetryPolicy.next(attempt: 1, status: nil), 2)
        XCTAssertEqual(RetryPolicy.next(attempt: 2, status: 503), 4)
        XCTAssertEqual(RetryPolicy.next(attempt: 4, status: nil), 16)
        XCTAssertEqual(RetryPolicy.next(attempt: 1, status: nil, jitter: 0), 1.5, "a little either way, so many downloads do not knock at once")
        XCTAssertNil(RetryPolicy.next(attempt: RetryPolicy.attempts, status: nil), "and then gives up")
    }

    func testARefusalIsNotTriedAgain() {
        XCTAssertNil(RetryPolicy.next(attempt: 1, status: 403), "an expired link")
        XCTAssertNil(RetryPolicy.next(attempt: 1, status: 404))
        XCTAssertNotNil(RetryPolicy.next(attempt: 1, status: 429))
        XCTAssertNotNil(RetryPolicy.next(attempt: 1, status: 408))
    }

    func testHonoursTheServersOwnWait() {
        XCTAssertEqual(RetryPolicy.next(attempt: 1, status: 429, retryAfter: 30), 30)
        XCTAssertEqual(RetryPolicy.next(attempt: 1, status: 503, retryAfter: 3600), 120, "within reason")
    }

    private func at(_ hour: Int, _ minute: Int = 0) -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 17, hour: hour, minute: minute))!
    }

    func testAnyTimeWithoutHours() {
        XCTAssertTrue(DownloadHours.open(from: nil, to: nil, at: at(15)))
        XCTAssertTrue(DownloadHours.open(from: 3, to: 3, at: at(15)), "the same hour twice is no limit")
    }

    func testWithinTheDay() {
        XCTAssertFalse(DownloadHours.open(from: 2, to: 8, at: at(1, 59)))
        XCTAssertTrue(DownloadHours.open(from: 2, to: 8, at: at(2)))
        XCTAssertTrue(DownloadHours.open(from: 2, to: 8, at: at(7, 59)))
        XCTAssertFalse(DownloadHours.open(from: 2, to: 8, at: at(8)), "up to the hour, not through it")
    }

    func testAcrossMidnight() {
        XCTAssertTrue(DownloadHours.open(from: 23, to: 7, at: at(23, 30)))
        XCTAssertTrue(DownloadHours.open(from: 23, to: 7, at: at(0)))
        XCTAssertTrue(DownloadHours.open(from: 23, to: 7, at: at(6, 59)))
        XCTAssertFalse(DownloadHours.open(from: 23, to: 7, at: at(7)))
        XCTAssertFalse(DownloadHours.open(from: 23, to: 7, at: at(12)))
    }

    func testRateLimitKeepsToTheRate() {
        let limit = RateLimit()
        XCTAssertEqual(limit.take(10_000_000, now: 0), 0, "no limit set")
        limit.bytesPerSecond = 1000
        XCTAssertEqual(limit.take(500, now: 10), 0.5, accuracy: 0.001, "an empty bucket: half a second for half a second's worth")
        XCTAssertEqual(limit.take(500, now: 11), 0, accuracy: 0.001, "a second later the debt is paid, and this piece with it")
        XCTAssertEqual(limit.take(3000, now: 100), 2, accuracy: 0.001, "a long rest saves at most a second's worth")
    }
}
