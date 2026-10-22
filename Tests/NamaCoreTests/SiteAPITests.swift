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

    func testCastMembersKeepTheirImdbAndSiteIds() throws {
        let post = try XCTUnwrap(Post(try fixture("single_movie")["result"]))
        XCTAssertEqual(post.cast.first?.imdb, "nm0000255")
        XCTAssertEqual(post.cast.first?.siteID, 151521)
        XCTAssertNil(post.cast[1].siteID)
    }

    func testAPersonIsAskedBySiteIdAPageAtATime() {
        XCTAssertEqual(SiteQuery.person(id: 151521, page: 1), "single/id/151521")
        XCTAssertEqual(SiteQuery.person(id: 151521, page: 3), "single/id/151521/page/3")
    }

    func testAPersonsFactsBiographyAndTitles() throws {
        let person = try XCTUnwrap(PersonDetails(site: try fixture("site_person")["result"]))
        XCTAssertEqual(person.name, "Ben Affleck")
        XCTAssertEqual(person.fullName, "Benjamin Géza Affleck-Boldt")
        XCTAssertEqual(person.persianName, "بن افلک")
        XCTAssertEqual(person.profession, "بازیگر")
        XCTAssertEqual(person.birthdate, "۲۴ مرداد ۱۳۵۱")
        XCTAssertNil(person.deathdate)
        XCTAssertEqual(person.age, "۵۴")
        XCTAssertEqual(person.imdb?.absoluteString, "https://www.imdb.com/name/nm0000255")
        XCTAssertEqual(person.picture?.medium?.absoluteString, "https://example.com/person/30151521-m_30NAMA.webp?")
        XCTAssertEqual(person.biography, ["پیش زمینه", "پاراگراف نمونهٔ اول با «نقل» و \u{2019}.", "پاراگراف نمونهٔ دوم."])
        XCTAssertEqual(person.titles.page, 1)
        XCTAssertEqual(person.titles.pages, 4)
        XCTAssertEqual(person.titles.posts.count, 2)
    }

    func testSiteTextKeepsParagraphsAndDropsTheEditorsMarkup() {
        XCTAssertEqual(SiteText.paragraphs("<p>a&amp;b</p><p>&nbsp;</p>line<br>next &#x2019;"), ["a&b", "line", "next \u{2019}"])
        XCTAssertEqual(SiteText.paragraphs(""), [])
    }

    func testNewestIsTheSitesListByPostId() {
        XCTAssertEqual(SiteQuery.newest(kind: "movie", genre: nil, streamOnly: true, page: 2),
                       "list/type/category/value/movie/page/2/orderby/id/order/desc/streamonly/yes/genre/all")
        XCTAssertEqual(SiteQuery.newest(kind: "anime", genre: "action", streamOnly: false, page: 1),
                       "list/type/category/value/anime/page/1/orderby/id/order/desc/streamonly/no/genre/action")
    }

    func testTopListKeepsItsPlaces() throws {
        XCTAssertEqual(SiteQuery.topList(.siteMovies), "toplist/list/top-movie-30nama")
        let entries = TopList.entries(site: try fixture("site_toplist")["result"])
        XCTAssertEqual(entries.map(\.rank), [1, 2, 3])
        XCTAssertEqual(entries.map(\.post.id), [7583, 8863, 7143])
        XCTAssertEqual(entries.first?.post.year, 2014)
    }

    func testTheScheduleSortsTitlesByTheSitesDays() throws {
        XCTAssertEqual(SiteQuery.schedule(.anime), "schedule/type/anime")
        let week = Schedule(site: try fixture("site_schedule")["result"])
        XCTAssertEqual(week.entries.count, 3, "a title listed twice is kept once")
        XCTAssertEqual(week.on(0).map(\.id), [721396])
        XCTAssertEqual(week.on(4).map(\.id), [721194])
        XCTAssertTrue(week.on(2).isEmpty)
    }

    func testTheSitesWeekStartsOnSaturday() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "UTC"))
        calendar.locale = Locale(identifier: "en_US")
        let saturday = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 17)))
        let tuesday = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 20)))
        XCTAssertEqual(Schedule.day(of: saturday, calendar: calendar), 0)
        XCTAssertEqual(Schedule.day(of: tuesday, calendar: calendar), 3)
        XCTAssertEqual(Schedule.week(from: tuesday, calendar: calendar), [3, 4, 5, 6, 0, 1, 2])
        XCTAssertEqual(calendar.weekdaySymbols[Schedule.symbolIndex(of: 0)], "Saturday")
        XCTAssertEqual(calendar.weekdaySymbols[Schedule.symbolIndex(of: 1)], "Sunday")
    }

    func testPersianDigitsBecomeLatin() {
        XCTAssertEqual(SiteText.latinDigits("۱۲۳"), "123")
        XCTAssertEqual(SiteText.latinDigits("٤٥"), "45")
        XCTAssertEqual(SiteText.latinDigits("7a"), "7a")
    }

    func testPersianTextIsTidiedForReading() {
        XCTAssertEqual(SiteText.tidyPersian(#"در 4 نوامبر ، 1969 در " Dazed and Confused" بازی کرد ."#),
                       "در ۴ نوامبر، ۱۹۶۹ در «Dazed and Confused» بازی کرد.")
        XCTAssertEqual(SiteText.tidyPersian("علاقه ی زیادی داشت و می یافت ولی نمی خواست"),
                       "علاقه\u{200C}ی زیادی داشت و می\u{200C}یافت ولی نمی\u{200C}خواست")
        XCTAssertEqual(SiteText.tidyPersian("گفت،سپس رفت"), "گفت، سپس رفت")
    }

    func testABiographyFoldsAfterWholeParagraphsNotOnAHeading() {
        let long = String(repeating: "متن بلند. ", count: 30)
        XCTAssertTrue(SiteText.isHeading("اوایل زندگی و حرفه"))
        XCTAssertFalse(SiteText.isHeading("او در تگزاس به دنیا آمد."))
        XCTAssertEqual(SiteText.opening(of: ["تیتر", long, "تیتر دوم", long], letters: 200), ["تیتر", long])
        XCTAssertEqual(SiteText.opening(of: ["کوتاه."], letters: 200), ["کوتاه."])
    }

    func testTheSitesProfileGivesTheNameTheAppAPILeavesOut() {
        XCTAssertEqual(SiteQuery.profile(username: "sample.user"), "profile/user/sample.user")
        XCTAssertEqual(SiteQuery.profile(username: "a/b c"), "profile/user/a%2Fb%20c")
        let both = SiteProfile(site: .object(["first_name": .string("Sample"), "last_name": .string(" Person ")]))
        XCTAssertEqual(both.fullName, "Sample Person")
        XCTAssertEqual(both.firstName, "Sample")
        XCTAssertEqual(SiteProfile(site: .object(["first_name": .string("Sample"), "last_name": .string("")])).fullName, "Sample")
        XCTAssertNil(SiteProfile(site: .object(["first_name": .null])).fullName)
    }

    func testAHundredMovieLines() {
        XCTAssertEqual(MovieLines.all.count, 100)
        XCTAssertEqual(Set(MovieLines.all).count, 100, "no line twice")
        XCTAssertTrue(MovieLines.all.allSatisfy { !$0.trimmingCharacters(in: .whitespaces).isEmpty })
        var generator = SystemRandomNumberGenerator()
        XCTAssertTrue(MovieLines.all.contains(MovieLines.pick(using: &generator)))
    }
}
