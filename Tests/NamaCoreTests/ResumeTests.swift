import XCTest
@testable import NamaCore

/// As the Android app's ResumeTest and the Windows app's ResumeTests.
final class ResumeTests: XCTestCase {
    private let items: [StreamItem] = (1...4).compactMap { i in
        StreamItem(JSON(["info": ["file_id": "f\(i)", "season": 1, "episode": "\(i)", "duration": 2400]] as [String: Any]))
    }

    private func server(_ file: String, _ time: Double) -> ContinueWatching? {
        ContinueWatching(JSON(["file_id": file, "time": time, "duration": 2400] as [String: Any]))
    }

    func testTheFurtherPointWins() {
        // The server rounds down to thirty seconds; this device knows the exact second.
        let p = Resume.start(items, chosen: 1, server: server("f2", 600), kept: .init(fileID: "f2", seconds: 623), pickedByUser: false)
        XCTAssertEqual(p, .init(index: 1, seconds: 623))
    }

    func testALaterEpisodeHereWins() {
        let p = Resume.start(items, chosen: 1, server: server("f2", 1800), kept: .init(fileID: "f3", seconds: 120), pickedByUser: false)
        XCTAssertEqual(p, .init(index: 2, seconds: 120))
    }

    func testTheServerWinsWhenItIsFurther() {
        // Watched on another device since.
        let p = Resume.start(items, chosen: 0, server: server("f4", 300), kept: .init(fileID: "f2", seconds: 900), pickedByUser: false)
        XCTAssertEqual(p.index, 3)
    }

    func testTheCreditsGoOnToTheNext() {
        let p = Resume.start(items, chosen: 0, server: nil, kept: .init(fileID: "f1", seconds: 2390), pickedByUser: false)
        XCTAssertEqual(p, .init(index: 1, seconds: nil))
    }

    func testTheServersLengthServesWhenTheStreamHasNone() {
        let bare = (1...2).compactMap { i in StreamItem(JSON(["info": ["file_id": "f\(i)", "season": 1, "episode": "\(i)"]] as [String: Any])) }
        let p = Resume.start(bare, chosen: 0, server: server("f1", 2370), kept: .init(fileID: "f1", seconds: 2390), pickedByUser: false)
        XCTAssertEqual(p, .init(index: 1, seconds: nil), "in the credits by 30nama's length")
    }

    func testTheLastOnesCreditsStartOver() {
        let p = Resume.start(items, chosen: 0, server: nil, kept: .init(fileID: "f4", seconds: 2390), pickedByUser: false)
        XCTAssertEqual(p, .init(index: 3, seconds: nil))
    }

    func testAChosenEpisodeStartsThere() {
        let p = Resume.start(items, chosen: 3, server: server("f2", 600), kept: .init(fileID: "f4", seconds: 400), pickedByUser: true)
        XCTAssertEqual(p, .init(index: 3, seconds: 400))
    }

    func testNothingSavedStartsAtTheBeginning() {
        XCTAssertEqual(Resume.start(items, chosen: 0, server: nil, kept: nil, pickedByUser: false), .init(index: 0, seconds: nil))
    }
}
