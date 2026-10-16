import XCTest
@testable import NamaCore

final class ContinueDismissalsTests: XCTestCase {
    private func feed(_ entries: [(id: Int, time: Double)]) -> [Post] {
        Post.continueList(JSON(["posts": entries.map { ["id": $0.id, "continue_watching": ["file_id": "f\($0.id)", "time": $0.time, "duration": 3000]] }] as [String: Any]))
    }

    func testADismissedTitleStaysHiddenUntilWatchedAgain() {
        var dismissals = ContinueDismissals()
        let list = feed([(1, 600), (2, 900)])
        dismissals.dismiss(list[0])
        XCTAssertEqual(dismissals.filter(list).map(\.id), [2])
        XCTAssertEqual(dismissals.filter(feed([(1, 600), (2, 930)])).map(\.id), [2], "the other title moving on does not bring it back")
        XCTAssertEqual(dismissals.filter(feed([(1, 1200), (2, 930)])).map(\.id), [1, 2], "watched again: back on the list")
        XCTAssertTrue(dismissals.positions.isEmpty, "and no longer remembered")
    }

    func testForgetsTitlesTheServerNoLongerLists() {
        var dismissals = ContinueDismissals()
        dismissals.dismiss(feed([(1, 600)])[0])
        _ = dismissals.filter(feed([(2, 900)]))
        XCTAssertTrue(dismissals.positions.isEmpty)
    }

    func testSurvivesAPropertyList() {
        var dismissals = ContinueDismissals()
        dismissals.dismiss(feed([(7, 120)])[0])
        XCTAssertEqual(ContinueDismissals(plist: dismissals.plist), dismissals)
    }
}
