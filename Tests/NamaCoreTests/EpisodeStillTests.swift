import XCTest
@testable import NamaCore

/// As the Windows app's EpisodeStillTests.
final class EpisodeStillTests: XCTestCase {
    func testAQuarterOfTheWayToTheCreditsAndNoMoreThanFiveMinutes() {
        XCTAssertTrue(EpisodeStill.points(credits: 600) == (150, 240))
        // A long episode: five minutes in, not a quarter of an hour.
        XCTAssertTrue(EpisodeStill.points(credits: 4000) == (300, 1600))
        // No credits mark: as if twenty minutes long.
        XCTAssertTrue(EpisodeStill.points(credits: nil) == (300, 480))
        XCTAssertTrue(EpisodeStill.points(credits: 0) == (300, 480))
    }

    func testADarkFrameIsKnownByItsMeanBrightness() {
        func filled(_ b: UInt8, _ g: UInt8, _ r: UInt8) -> [UInt8] { (0..<64).flatMap { _ in [b, g, r, 255] } }
        XCTAssertEqual(EpisodeStill.luma(filled(0, 0, 0)), 0)
        XCTAssertEqual(EpisodeStill.luma(filled(255, 255, 255)), 255)
        // Green counts most, blue least, as the eye sees them.
        XCTAssertGreaterThan(EpisodeStill.luma(filled(0, 200, 0)), EpisodeStill.luma(filled(200, 0, 0)))
        XCTAssertLessThan(EpisodeStill.luma(filled(10, 12, 14)), EpisodeStill.dark)
        XCTAssertEqual(EpisodeStill.luma([]), 0)
    }
}
