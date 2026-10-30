import XCTest
@testable import NamaCore

final class TMDBTests: XCTestCase {
    func testKeysOfBothKinds() {
        let bearer = TMDB.findRequest(imdbID: "tt1", key: "eyJhbGciOi")
        XCTAssertEqual(bearer.value(forHTTPHeaderField: "Authorization"), "Bearer eyJhbGciOi")
        XCTAssertFalse(bearer.url!.absoluteString.contains("api_key"))
        let plain = TMDB.findRequest(imdbID: "tt1", key: "abc123")
        XCTAssertNil(plain.value(forHTTPHeaderField: "Authorization"))
        XCTAssertTrue(plain.url!.absoluteString.contains("api_key=abc123"))
    }

    func testFindsTheTitle() {
        let found = JSON(["movie_results": [["id": 7]], "tv_results": [["id": 9]]] as [String: Any])
        XCTAssertEqual(TMDB.id(inFind: found, isSeries: false), 7)
        XCTAssertEqual(TMDB.id(inFind: found, isSeries: true), 9)
    }

    func testPicksTheResultThatIsTheTitle() {
        let answer = JSON(["results": [
            ["id": 1, "title": "Dune Drifter", "release_date": "2020-01-01"],
            ["id": 2, "title": "Dune", "release_date": "1984-12-14"],
            ["id": 3, "title": "Dune", "release_date": "2021-09-15"],
        ]] as [String: Any])
        XCTAssertEqual(TMDB.best(inSearch: answer, title: "Dune 2021", year: 2021), 3)
        XCTAssertEqual(TMDB.best(inSearch: answer, title: "Dune", year: 1984), 2)
        XCTAssertNil(TMDB.best(inSearch: answer, title: "Arrival", year: 2016))
    }

    private let film = JSON([
        "images": ["logos": [
            ["file_path": "/de.png", "iso_639_1": "de", "vote_average": 9],
            ["file_path": "/en-low.png", "iso_639_1": "en", "vote_average": 2],
            ["file_path": "/en.svg", "iso_639_1": "en", "vote_average": 5],
        ]],
        "videos": ["results": [
            ["key": "teaser", "site": "YouTube", "type": "Teaser", "official": true],
            ["key": "fan", "site": "YouTube", "type": "Trailer", "official": false],
            ["key": "real", "site": "YouTube", "type": "Trailer", "official": true],
        ]],
        "credits": ["crew": [
            ["name": "Sam Grip", "job": "Gaffer"],
            ["name": "Ann Write", "job": "Screenplay", "profile_path": "/a.jpg"],
            ["name": "Dee Rect", "job": "Director"],
            ["name": "Dee Rect", "job": "Screenplay"],
        ]],
        "reviews": ["results": [["author": "critic", "content": "Fine.", "author_details": ["rating": 7]], ["author": "empty", "content": ""]]],
    ] as [String: Any])

    func testReadsWhatAPageShows() {
        let info = TMDB.info(in: film, lang: .en)
        XCTAssertEqual(info.logo?.absoluteString, "https://image.tmdb.org/t/p/w500/en.png", "English, the best voted, as PNG")
        XCTAssertEqual(info.trailer, "real")
        XCTAssertEqual(info.crew.map(\.name), ["Dee Rect", "Ann Write"], "director first, each person once, no gaffer")
        XCTAssertEqual(info.crew.last?.photo?.absoluteString, "https://image.tmdb.org/t/p/w185/a.jpg")
        XCTAssertEqual(info.reviews, [.init(author: "critic", text: "Fine.", rating: 7, link: nil)])
    }

    func testSeriesCrewAndCreators() {
        let series = JSON([
            "created_by": [["name": "Cree Ator"]],
            "aggregate_credits": ["crew": [["name": "Com Poser", "jobs": [["job": "Original Music Composer"]]]]],
        ] as [String: Any])
        XCTAssertEqual(TMDB.info(in: series, lang: .fa).crew.map(\.job), ["Creator", "Original Music Composer"])
    }

    func testReadsTheLogoFromThePublicPage() {
        let html = #"<img src="https://media.themoviedb.org/t/p/w500/aEbOsxbcx4.png"><img src="/t/p/w500/second.png">"#
        XCTAssertEqual(TMDB.logo(inPage: html)?.absoluteString, "https://image.tmdb.org/t/p/w500/aEbOsxbcx4.png")
        XCTAssertNil(TMDB.logo(inPage: "<html>no logos</html>"))
        let page = URL(string: "https://www.themoviedb.org/movie/475557-joker")!
        XCTAssertEqual(TMDB.logosPage(title: page, language: "en")?.absoluteString, "https://www.themoviedb.org/movie/475557-joker/images/logos?image_language=en")
        XCTAssertNil(TMDB.logosPage(title: URL(string: "https://www.themoviedb.org/search")!, language: "en"), "not a title page")
    }

    func testReadsTheTrailerFromThePublicPage() {
        XCTAssertEqual(TMDB.trailer(inPage: ##"<a data-site="YouTube" data-id="t433PEQGErc" href="#">"##), "t433PEQGErc")
        XCTAssertNil(TMDB.trailer(inPage: "<p>none</p>"))
        XCTAssertEqual(TMDB.trailersPage(title: URL(string: "https://www.themoviedb.org/tv/196322-dark-matter")!)?.absoluteString,
                       "https://www.themoviedb.org/tv/196322-dark-matter/videos?active_nav_item=Trailers&language=en-US")
    }
}
