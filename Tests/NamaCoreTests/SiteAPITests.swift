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

    func testATitleTheSiteListsTwiceIsShownOnce() throws {
        let result = try fixture("site_advanced_search")["result"]
        var posts = result["posts"].array.map(\.any)
        posts.append(posts[0])
        let page = PostPage(site: JSON(["page": 1, "pages": 1, "posts": posts] as [String: Any]))
        XCTAssertEqual(page.posts.map(\.id), [572188, page.posts[1].id])
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

    func testASiteThatGivesNoAnswerIsNotAskedAgainForAWhile() async throws {
        // As where the site is filtered: no answer at all.
        var answering = false
        let net = FakeNetwork { _, _ in answering ? (200, #"{"success":true,"msg":null,"result":{}}"#, [:]) : (0, "", [:]) }
        var c = APIConfig()
        c.requestSpacing = 0
        c.backoff = [0.02, 0.02]
        c.site.key = "site-key"
        let api = APIClient(config: c, transport: net.transport)
        do {
            _ = try await api.site("schedule/type/series")
            XCTFail("answered")
        } catch {
            XCTAssertEqual(error as? APIError, .unreachable)
        }
        // Not tried again, and the next page does not wait for it either.
        XCTAssertEqual(net.calls.count, 1)
        do {
            _ = try await api.site("toplist/list/imdb")
            XCTFail("answered")
        } catch {
            XCTAssertEqual(error as? APIError, .unreachable)
        }
        XCTAssertEqual(net.calls.count, 1)
        let inReach = await api.siteInReach
        XCTAssertFalse(inReach)
        // Asked for again by the user, with the site now answering.
        answering = true
        await api.siteAgain()
        _ = try await api.site("toplist/list/imdb")
        XCTAssertEqual(net.calls.count, 2)
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
        XCTAssertEqual(Set(MovieLines.all.map(\.text)).count, 100, "no line twice")
        XCTAssertTrue(MovieLines.all.allSatisfy { !$0.text.isEmpty && !$0.film.isEmpty && (1900...2010).contains($0.year) && !$0.directors.isEmpty })
        XCTAssertEqual(MovieLines.all[73].film, "Chinatown")
        XCTAssertEqual(MovieLines.all[73].directors, ["Roman Polanski"])
        XCTAssertTrue(MovieLines.addressed.allSatisfy { $0.text.contains("{name}") && (1...100).contains($0.rank) })
        var generator = SystemRandomNumberGenerator()
        XCTAssertTrue(MovieLines.all.contains(MovieLines.pick(name: nil, using: &generator)), "no name: a line as it is")
        XCTAssertTrue(MovieLines.all.contains(MovieLines.pick(name: "  ", using: &generator)))
        for _ in 0..<60 {
            let line = MovieLines.pick(name: "Sample", familyName: "Person", using: &generator)
            XCTAssertTrue(line.text.contains("Sample"), line.text)
            XCTAssertFalse(line.text.contains("{name}"), line.text)
            // The film is the one the line comes from.
            XCTAssertTrue(MovieLines.all.contains { $0.film == line.film && $0.year == line.year })
        }
    }

    func testArticleListsAndTheirPictures() throws {
        XCTAssertEqual(SiteQuery.articles(.review, page: 3), "article/type/review/page/3")
        XCTAssertEqual(SiteQuery.article(id: 721620), "single/id/721620")
        let page = ArticlePage(site: try fixture("site_articles")["result"])
        XCTAssertEqual(page.pages, 1182)
        XCTAssertEqual(page.articles.count, 2, "an entry with no id is left out")
        let first = page.articles[0]
        XCTAssertEqual(first.id, 721620)
        XCTAssertEqual(first.kind, .news)
        XCTAssertEqual(first.title, "عنوان خبر نمونه \u{2013} اول")
        XCTAssertEqual(first.summary, "خلاصهٔ نمونه، با فاصلهٔ پیش از ویرگول")
        XCTAssertEqual(first.date, "۲۹ مهر ۱۴۰۵")
        XCTAssertEqual(first.picture?.absoluteString, "https://example.com/attach/2026/10/sample-scaled.jpg")
        XCTAssertEqual(first.thumbnail?.absoluteString, "https://example.com/attach/2026/10/sample-scaled-309x197.jpg", "the JPEG: the WebP is mostly missing")
        let review = page.articles[1]
        XCTAssertEqual(review.kind, .review)
        XCTAssertNil(review.summary)
        XCTAssertEqual(review.picture?.absoluteString, "https://example.com/attach/2026/10/review.jpg")
    }

    func testAnArticlesTextBecomesHeadingsParagraphsAndPictures() throws {
        let text = try XCTUnwrap(ArticleText(site: try fixture("site_article")["result"]))
        XCTAssertEqual(text.article.title, "عنوان خبر نمونه")
        XCTAssertEqual(text.blocks, [
            .paragraph("پاراگراف اول با «نقل» و پیوند به Sample در سال ۲۰۲۶."),
            .heading("تیتر میانی"),
            .picture(try XCTUnwrap(URL(string: "https://example.com/attach/2026/10/big.jpg"))),
            .paragraph("پاراگراف دوم کنار عکس."),
            .picture(try XCTUnwrap(URL(string: "https://example.com/attach/2026/10/alone.png"))),
        ], "the opening picture is the article's own and is shown above it; the video is left out")
    }

    func testALinesFilmIsFoundOn30nama() throws {
        let line = MovieLines.all[73]   // Chinatown, 1974
        XCTAssertEqual(line.searchQueries, ["Chinatown", "Chinatown 1974"])
        func post(_ id: Int, _ title: String, series: Bool = false) -> Post {
            Post(.object(["id": .number(Double(id)), "title": .object(["english": .string(title)]),
                          "options": .object(["is_series": .bool(series)])]))!
        }
        var withYears = [post(1, "Chinatown 2019"), post(2, "Chinatown 1974", series: true), post(3, "Chinatown 1974"), post(4, "Chinatown Nights 1974")]
        // The year comes from the info block in real answers; here from the title's end is enough.
        for i in withYears.indices { withYears[i].year = Int(withYears[i].titleEnglish!.suffix(4)) }
        XCTAssertEqual(line.match(in: withYears)?.id, 3, "the film, of that year, not the series or a namesake")
        XCTAssertNil(line.match(in: [withYears[0]]))
    }
}
