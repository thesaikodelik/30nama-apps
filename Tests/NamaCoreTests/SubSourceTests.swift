import XCTest
@testable import NamaCore

final class SubSourceTests: XCTestCase {
    private func zip(_ name: String) throws -> Data {
        try Data(contentsOf: XCTUnwrap(Bundle.module.url(forResource: name, withExtension: "zip", subdirectory: "Fixtures")))
    }

    func testReadsDeflatedAndStoredArchives() throws {
        let files = Zip.files(in: try zip("subsource_deflated"))
        XCTAssertEqual(files.map(\.name), ["Show.S01E02.WEB.srt", "Show.S01E03.WEB.srt"])
        XCTAssertTrue(String(decoding: files[0].data, as: UTF8.self).contains("سلام"))
        XCTAssertEqual(Zip.files(in: try zip("subsource_stored")).first?.data, files[0].data)
        XCTAssertTrue(Zip.files(in: Data("not a zip".utf8)).isEmpty)
    }

    func testTakesTheEpisodesFileOutOfAnArchive() throws {
        let text = SubSource.text(from: try zip("subsource_deflated"), season: 1, episode: 3)
        XCTAssertEqual(text?.contains("فقط قسمت سه"), true)
        XCTAssertNil(SubSource.text(from: try zip("subsource_deflated"), season: 1, episode: 9), "no file for that episode")
    }

    /// The shape of the site's own search answer.
    private let search = JSON(["success": true, "results": [
        ["id": 29130, "title": "Dark Matter", "type": "tvseries", "link": "/series/dark-matter", "releaseYear": 2015,
         "seasons": [["season": 1, "id": 29130, "link": "/subtitles/dark-matter/season=1"]]],
        ["id": 131507, "title": "Dark Matter", "type": "tvseries", "link": "/series/dark-matter-2024", "releaseYear": 2024,
         "seasons": [["season": 1, "id": 131507, "link": "/subtitles/dark-matter-2024/season=1"],
                     ["season": 2, "id": 162344, "link": "/subtitles/dark-matter-2024/season=2"]]],
        ["id": 29156, "title": "Dark Matter", "type": "movie", "link": "/subtitles/dark-matter-2007", "releaseYear": 2007],
    ]] as [String: Any])

    func testPicksTheRightTitleAndSeason() {
        let series = SubSource.Query(title: "Dark Matter", year: 2024, imdbID: nil, isSeries: true, season: 2)
        XCTAssertEqual(SubSource.listing(in: search, for: series)?.absoluteString, "https://subsource.net/subtitles/dark-matter-2024/season-2/farsi_persian")
        let film = SubSource.Query(title: "Dark Matter", year: 2007, imdbID: nil, isSeries: false, season: nil)
        XCTAssertEqual(SubSource.listing(in: search, for: film)?.absoluteString, "https://subsource.net/subtitles/dark-matter-2007/farsi_persian")
        let other = SubSource.Query(title: "Dark Matter", year: 2019, imdbID: nil, isSeries: false, season: nil)
        XCTAssertNil(SubSource.listing(in: search, for: other), "a film of another year is not this one")
    }

    func testOldListingAddressesAreBroughtUpToDate() {
        XCTAssertEqual(SubSource.current(URL(string: "https://subsource.net/subtitles/x/season=3/farsi_persian")!).absoluteString,
                       "https://subsource.net/subtitles/x/season-3/farsi_persian")
    }

    func testReadsTheDocumentedAPI() {
        let found = JSON(["success": true, "data": [["movieId": 10, "season": 1], ["movieId": 11, "season": 2]]] as [String: Any])
        XCTAssertEqual(SubSource.movieID(in: found, for: .init(title: "x", year: nil, imdbID: nil, isSeries: true, season: 2)), 11)
        let subs = JSON(["data": [["subtitleId": 5, "releaseInfo": ["Show.S01E02.WEB-DL"]], ["releaseInfo": ["no id"]]]] as [String: Any])
        XCTAssertEqual(SubSource.candidates(inAPI: subs), [.init(source: .api(5), releases: ["Show.S01E02.WEB-DL"])])
        let request = SubSource.searchRequest(.init(title: "x", year: nil, imdbID: "tt123", isSeries: false, season: nil), key: "k")
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-API-Key"), "k")
        XCTAssertTrue(request.url!.absoluteString.contains("searchType=imdb&imdb=tt123"))
    }

    func testRanksForTheEpisodeAndTheCopy() {
        func c(_ name: String) -> SubSource.Candidate { .init(source: .page(URL(string: "https://e.x/" + name)!), releases: [name]) }
        let list = [c("Show.S01E05.WEB"), c("Show.S01E02.HDTV"), c("Show.S01.WEB"), c("Show.S01E02.WEB-DL")]
        XCTAssertEqual(SubSource.ranked(list, release: "Show.S01E02.1080p.WEB-DL", season: 1, episode: 2).map(\.name),
                       ["Show.S01E02.WEB-DL", "Show.S01E02.HDTV", "Show.S01.WEB"])
    }
}
