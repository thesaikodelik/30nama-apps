import XCTest
@testable import NamaCore

/// The shape of the Mac release text: how to install first, then a section per version, then the
/// changelog link, first-install steps and checksum.
final class ReleaseNotesTests: XCTestCase {
    private let body = """
    <div dir="rtl">\r
    \r
    نسخهٔ ۲.۰.۶ ۳۰نما برای مک.\r
    \r
    **اگر ۱.۲.۴ یا هر نسخهٔ ۲ را دارید،** برنامه خودش خبر می‌دهد.\r
    \r
    ### ۲.۰.۶ (۸ آبان ۱۴۰۵)\r
    \r
    **پخش‌کننده**\r
    \r
    - کلیک روی تصویر فیلم را **نگه می‌دارد**.\r
    - کنار طول فیلم نوشته می‌شود.\r
    \r
    ### ۱.۰.۰ (۲۲ مهر ۱۴۰۵)\r
    \r
    اولین نسخهٔ مک، به‌علاوهٔ:\r
    \r
    - دانلود فیلم.\r
    \r
    همهٔ تغییرها با جزئیات در [تازه‌ها](https://example.com/CHANGELOG.md) آمده است.\r
    \r
    **نصب برای بار اول**\r
    \r
    </div>\r
    \r
    SHA-256: `abc`
    """

    func testOnlyTheVersionSections() {
        XCTAssertEqual(ReleaseNotes.blocks(in: body), [
            .version("۲.۰.۶ (۸ آبان ۱۴۰۵)"),
            .group("پخش‌کننده"),
            .item("کلیک روی تصویر فیلم را **نگه می‌دارد**."),
            .item("کنار طول فیلم نوشته می‌شود."),
            .version("۱.۰.۰ (۲۲ مهر ۱۴۰۵)"),
            .text("اولین نسخهٔ مک، به‌علاوهٔ:"),
            .item("دانلود فیلم."),
        ])
    }

    func testThisVersionsReleaseOrElseTheNewest() {
        func release(_ tag: String, _ notes: String) -> [String: Any] {
            ["tag_name": tag, "draft": false, "body": "### \(notes)\n\n- \(tag)"]
        }
        let feed = JSON([release("win-v1.0.5", "w"), release("mac-v2.0.6", "a"), release("mac-v2.0.5", "b"), release("downloads", "d")] as [Any])
        XCTAssertEqual(ReleaseNotes.blocks(inFeed: feed, version: "2.0.5")?.first, .version("b"))
        // An older version's own release is gone; the newest Mac one has its section too.
        XCTAssertEqual(ReleaseNotes.blocks(inFeed: feed, version: "2.0.4")?.first, .version("a"))
        XCTAssertNil(ReleaseNotes.blocks(inFeed: JSON([release("win-v1.0.5", "w")] as [Any]), version: "2.0.6"))
        XCTAssertNil(ReleaseNotes.blocks(inFeed: JSON([["tag_name": "mac-v2.0.6", "body": "Just a file."]] as [Any]), version: "2.0.6"))
    }
}
