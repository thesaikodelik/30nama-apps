import XCTest
@testable import NamaCore

/// The shape 30nama's own desktop app reads; not yet checked against a live answer.
final class NotificationTests: XCTestCase {
    func testReadsTheShapeTheOfficialAppReads() throws {
        var page = NotificationPage(try JSON.parse(Data("""
            {"notifications":[
              {"id":12,"title":"قسمت تازه","content":"<p>اپیزود 4 <b>فصل 2</b><br>اضافه شد</p>","read":false,"notification_type":"post",
               "post":{"id":469773,"title":{"english":"Link Click 2021"}},"date":{"g":"2026-10-16 10:00:00","j":"۱۴۰۵-۰۷-۲۴"}},
              {"id":"13","title":"Offer","notification_type":"pricing","read":1,"date":"2026-10-15"},
              {"title":"no id"}],
             "total_unread":1,"page":1}
            """.utf8)))
        XCTAssertEqual(page.items.count, 2, "one without an id is no notification")
        XCTAssertEqual(page.unread, 1)
        let first = page.items[0]
        XCTAssertEqual(first.id, "12")
        XCTAssertEqual(first.content, "اپیزود 4 فصل 2\nاضافه شد", "the HTML it comes with is taken out")
        XCTAssertEqual(first.kind, .post)
        XCTAssertEqual(first.post?.id, 469773)
        XCTAssertEqual(first.date.jalali, "۱۴۰۵-۰۷-۲۴")
        XCTAssertTrue(page.items[1].read)
        XCTAssertEqual(page.items[1].date.gregorian, "2026-10-15")
        XCTAssertEqual(page.items[1].kind, .pricing)

        page.markRead("12")
        XCTAssertEqual(page.unread, 0)
    }
}
