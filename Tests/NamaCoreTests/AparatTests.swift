import XCTest
@testable import NamaCore

final class AparatTests: XCTestCase {
    func testTheMostWatchedTrueTrailerIsTaken() throws {
        let videos = Aparat.videos(inSearch: try fixture("aparat_search_exorcist"))
        XCTAssertEqual(videos.count, 10)
        // 30nama's English title carries its year; the reactions to the trailer are passed over.
        let found = try XCTUnwrap(Aparat.trailer(in: videos, title: "The Exorcist: Believer 2023", year: 2023))
        XCTAssertEqual(found.title, "تریلر فیلم جن گیر: مومن The Exorcist: Believer")
    }

    func testAShortNameNeedsItsYear() throws {
        let videos = Aparat.videos(inSearch: try fixture("aparat_search_up"))
        // "step up 6", a game's "Team Up" and "Up and Away 2018" are not the 2009 film.
        let found = try XCTUnwrap(Aparat.trailer(in: videos, title: "Up", year: 2009))
        XCTAssertEqual(found.title, "تریلر انیمیشن Up 2009")
        XCTAssertNil(Aparat.trailer(in: videos, title: "Up", year: nil))
    }

    func testOnlyTrailersOfAFilmAndOfAFittingLength() {
        func video(_ title: String, _ duration: Int = 120, _ visits: Int = 1) -> Aparat.Video {
            Aparat.Video(uid: title, title: title, duration: duration, visits: visits)
        }
        let videos = [
            video("نقد و بررسی تریلر فیلم Inception", 140, 900),
            video("تریلر Inception", 140, 800),
            video("تریلر فیلم Inception ساخت هوش مصنوعی", 140, 700),
            video("تریلر فیلم Inception", 1200, 600),
            video("تریلر فیلم Inception 2024", 140, 500),
            video("تریلر فیلم Inception با بازی لئوناردو دی کاپریو (زیرنویس فارسی)", 143, 50),
            video("تریلر فیلم Inception ۲۰۱۰", 143, 40),
        ]
        XCTAssertEqual(Aparat.trailer(in: videos, title: "Inception", year: 2010)?.visits, 50)
        XCTAssertEqual(Aparat.words("تریلر فیلم Inception ۲۰۱۰ (تلقین)"), "تریلر فیلم inception 2010 تلقین")
    }

    func testTheFileIs720pWhereThereIsOne() throws {
        let video = try fixture("aparat_video")
        XCTAssertEqual(Aparat.file(inVideo: video)?.absoluteString, "https://example.com/aparat-video/abc-720p.mp4?wmsAuthSign=x")
        XCTAssertNil(Aparat.file(inVideo: JSON(["video": [:]] as [String: Any])))
    }

    func testTheSearchAsksForTheTrailerByName() {
        let request = Aparat.searchRequest(title: "Dune: Part Two 2024")
        XCTAssertEqual(request.url?.host, "www.aparat.com")
        XCTAssertTrue(request.url!.absoluteString.contains("videoBySearch/text/"))
        XCTAssertEqual(request.url!.absoluteString.removingPercentEncoding?.contains("تریلر Dune: Part Two/perpage"), true)
    }
}

final class LogoIndexTests: XCTestCase {
    func testALogoInTheLanguageOfTheInterfaceThroughTheProxy() throws {
        let index = try XCTUnwrap(LogoIndex(try JSON.parse(Data(#"{"version":1,"updated":"2026-10-25","logos":{"tt1":["en.png","fa.png"],"tt2":["only.png",""]}}"#.utf8))))
        XCTAssertEqual(index.count, 2)
        XCTAssertEqual(index.logo(imdb: "tt1", lang: .fa)?.absoluteString, "https://wsrv.nl/?url=image.tmdb.org/t/p/w500/fa.png")
        XCTAssertEqual(index.logo(imdb: "tt1", lang: .en)?.absoluteString, "https://wsrv.nl/?url=image.tmdb.org/t/p/w500/en.png")
        XCTAssertEqual(index.logo(imdb: "tt2", lang: .fa)?.absoluteString, "https://wsrv.nl/?url=image.tmdb.org/t/p/w500/only.png")
        XCTAssertNil(index.logo(imdb: "tt3", lang: .en))
        XCTAssertNil(index.logo(imdb: nil, lang: .en))
        XCTAssertNil(LogoIndex(JSON(["version": 2] as [String: Any])))
    }
}
