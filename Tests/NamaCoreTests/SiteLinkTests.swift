import XCTest
@testable import NamaCore

final class SiteLinkTests: XCTestCase {
    private func title(_ id: Int, _ english: String, _ year: Int?, series: Bool = false) -> Post {
        Post(JSON(["id": id, "title": ["english": english], "info": ["year": year as Any],
                   "options": ["is_series": series]] as [String: Any]))!
    }

    func testAsTheSitesOwnLinks() {
        // Links as they are in the site's own articles (30 October 2026).
        XCTAssertEqual(SiteLink.url(for: title(4705, "X-Men", 2000)).absoluteString, "https://30nama.com/movie/4705/X-Men-2000")
        XCTAssertEqual(SiteLink.url(for: title(582455, "The Batman: Part II", 2028)).absoluteString, "https://30nama.com/movie/582455/The-Batman-Part-II-2028")
        XCTAssertEqual(SiteLink.url(for: title(128728, "The Death of Stalin", 2017)).absoluteString, "https://30nama.com/movie/128728/The-Death-of-Stalin-2017")
    }

    func testASeriesAndTheEdges() {
        XCTAssertEqual(SiteLink.url(for: title(1, "Friends", 1994, series: true)).absoluteString, "https://30nama.com/series/1/Friends-1994")
        // A year already at the end of the name is not written twice.
        XCTAssertEqual(SiteLink.url(for: title(2, "Up 2009", 2009)).absoluteString, "https://30nama.com/movie/2/Up-2009")
        // Punctuation and spaces together are one hyphen.
        XCTAssertEqual(SiteLink.url(for: title(3, "Spider-Man: Across the Spider-Verse", 2023)).absoluteString,
                       "https://30nama.com/movie/3/Spider-Man-Across-the-Spider-Verse-2023")
        XCTAssertEqual(SiteLink.url(for: title(4, "", nil)).absoluteString, "https://30nama.com/movie/4")
    }
}
