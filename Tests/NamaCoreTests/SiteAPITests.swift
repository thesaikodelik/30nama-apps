import XCTest
@testable import NamaCore

final class SiteAPITests: XCTestCase {
    func testSitePostsAreReadInTheAppShape() throws {
        let page = PostPage(site: try fixture("site_advanced_search")["result"])
        XCTAssertEqual(page.page, 1)
        XCTAssertEqual(page.pages, 4)
        XCTAssertEqual(page.posts.count, 2)

        let film = page.posts[0]
        XCTAssertEqual(film.id, 572188)
        XCTAssertEqual(film.kind, .movie)
        XCTAssertFalse(film.isSeries)
        XCTAssertTrue(film.hasStream)
        XCTAssertEqual(film.titleEnglish, "The Exorcist: Believer 2023")
        XCTAssertEqual(film.year, 2023)
        XCTAssertEqual(film.imdbScore, 4.8)
        XCTAssertEqual(film.imdbVotes, 40478)
        XCTAssertEqual(film.imdbID, "tt12921446")
        XCTAssertEqual(film.siteScore, 5.8)
        XCTAssertEqual(film.genres.map(\.slug), ["horror"])
        XCTAssertEqual(film.poster.medium?.absoluteString, "https://example.com/poster/30572188-m_30NAMA.webp?")
        XCTAssertEqual(film.poster.jpeg?.absoluteString, "https://example.com/poster/30572188-l_30NAMA.jpg?")
        XCTAssertEqual(film.cover?.absoluteString, "https://example.com/cover/30572188_30NAMA.webp?")

        let series = page.posts[1]
        XCTAssertTrue(series.isSeries)
        XCTAssertEqual(series.kind, .series)
        XCTAssertEqual(series.year, 2022)
    }

    func testTheYearIsOnlyTakenFromTheEndOfTheTitle() {
        let post = Post(site: .object(["id": .number(1), "title": .string("Blade Runner 2049 2017")]))
        XCTAssertEqual(post?.year, 2017)
        XCTAssertNil(Post(site: .object(["id": .number(2), "title": .string("Untitled Tomb Raider Project")]))?.year)
        XCTAssertNil(Post(site: .object(["title": .string("No id 2020")])))
    }

    func testFilmographyAsksTheSiteByImdbIdNewestFirst() {
        let q = SiteQuery.filmography(imdb: "nm0000995", page: 2)
        XCTAssertEqual(q.path, "advanced_search/page/2/orderby/year/order/desc")
        XCTAssertEqual(q.form, ["parameters": #"{"cast":"nm0000995"}"#])
    }

    func testSiteCallsGoToTheSiteHostWithItsKeyAndAForm() async throws {
        let net = FakeNetwork { _, _ in (200, #"{"success":true,"msg":null,"result":{"page":1,"pages":1,"posts":[]}}"#, [:]) }
        var c = APIConfig()
        c.requestSpacing = 0
        c.site.key = "site-key"
        let api = APIClient(config: c, transport: net.transport)
        XCTAssertTrue(api.hasSite)
        let q = SiteQuery.filmography(imdb: "nm1", page: 1)
        _ = try await api.site(q.path, q.form)
        let last = try XCTUnwrap(net.last)
        XCTAssertEqual(last.host, "interface.30nama.com")
        XCTAssertEqual(last.action, "desc")
        XCTAssertEqual(last.headers["c-api-key"], "site-key")
        XCTAssertEqual(last.headers["c-platform"], "Website")
        XCTAssertEqual(last.body, "parameters=%7B%22cast%22%3A%22nm1%22%7D")
    }

    func testWithoutAKeyTheSiteIsNotAsked() async {
        let net = FakeNetwork { _, _ in (200, "{}", [:]) }
        var c = APIConfig()
        c.site.key = nil
        let api = APIClient(config: c, transport: net.transport)
        XCTAssertFalse(api.hasSite)
        do {
            _ = try await api.site("schedule/type/series")
            XCTFail("expected unreachable")
        } catch {
            XCTAssertEqual(error as? APIError, .unreachable)
        }
        XCTAssertTrue(net.calls.isEmpty)
    }

    func testCastMembersKeepTheirImdbId() throws {
        let post = try XCTUnwrap(Post(try fixture("single_movie")["result"]))
        XCTAssertEqual(post.cast.first?.imdb, "nm0000255")
    }
}
