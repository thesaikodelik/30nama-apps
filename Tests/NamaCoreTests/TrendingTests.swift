import XCTest
@testable import NamaCore

/// Scripted TMDB and 30nama search: answers from tables and counts what was asked.
private final class FakeCatalogue: @unchecked Sendable {
    private let lock = NSLock()
    private var tmdbLog: [URLRequest] = []
    private var searchLog: [String] = []

    /// TMDB list entries by request path.
    var lists: [String: [[String: Any]]] = [:]
    /// 30nama search results by query.
    var shelves: [String: [[String: Any]]] = [:]
    var tmdbStatus = 200
    var searchFails = false

    var tmdbRequests: [URLRequest] { lock.withLock { tmdbLog } }
    var searches: [String] { lock.withLock { searchLog } }

    var transport: Transport {
        { [self] request in
            let url = request.url!
            lock.withLock { tmdbLog.append(request) }
            let path = url.path.replacingOccurrences(of: "/3", with: "", options: .anchored)
            let page = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "page" }?.value ?? "1"
            let body = try JSONSerialization.data(withJSONObject: ["results": page == "1" ? (lists[path] ?? []) : []])
            return (body, HTTPURLResponse(url: url, statusCode: tmdbStatus, httpVersion: nil, headerFields: nil)!)
        }
    }

    var search: Trending.Search {
        { [self] query in
            lock.withLock { searchLog.append(query) }
            if searchFails { throw APIError.transient("search", retryAfter: 0) }
            return JSON(["posts": shelves[query] ?? []] as [String: Any])
        }
    }
}

final class TrendingTests: XCTestCase {
    private func tmdb(_ id: Int, _ title: String, year: Int = 2024, tv: Bool = false, type: String? = nil,
                      original: String? = nil, poster: Bool = true, popularity: Double = 10, votes: Int = 500) -> [String: Any] {
        var j: [String: Any] = ["id": id, "popularity": popularity, "vote_count": votes]
        j[tv ? "name" : "title"] = title
        j[tv ? "first_air_date" : "release_date"] = "\(year)-05-01"
        if let original { j[tv ? "original_name" : "original_title"] = original }
        if let type { j["media_type"] = type }
        if poster { j["poster_path"] = "/p\(id).jpg" }
        return j
    }

    private func post(_ id: Int, _ title: String, year: Int = 2024, series: Bool = false, stream: Bool = true, soon: Bool = false) -> [String: Any] {
        [
            "id": id,
            "title": ["english": "\(title) \(year)", "local": "عنوان \(id)"],
            "info": ["year": year],
            "options": ["is_series": series, "stream": stream, "coming_soon": soon],
            "image": ["poster": ["webp": ["large": "https://example.com/\(id)-l.webp"]]],
            "cast": [["name": "Someone"]],
        ]
    }

    private func item(_ json: [String: Any], kind: TMDBItem.Kind? = nil) -> TMDBItem {
        TMDBItem(JSON(json), defaultKind: kind)!
    }

    private func posts(_ list: [[String: Any]]) -> [Post] { list.compactMap { Post(JSON($0)) } }

    /// Six movies trending on TMDB, all of them on 30nama under the same names.
    private func stockedCatalogue(count: Int = 6) -> FakeCatalogue {
        let net = FakeCatalogue()
        net.lists["/trending/all/week"] = (1...count).map { tmdb($0, "Film \(TrendingTests.words[$0])", type: "movie") }
        for i in 1...count { net.shelves["Film \(TrendingTests.words[i])"] = [post(100 + i, "Film \(TrendingTests.words[i])")] }
        return net
    }

    private static let words = (0...40).map { "N\($0)x" }

    // MARK: Matching

    func testNormalizeAndSimilarity() {
        XCTAssertEqual(TitleMatch.normalize("Amélie & Co.: The  Return!"), "amelie and co the return")
        XCTAssertEqual(TitleMatch.normalize("  بازی مرکب  "), "بازی مرکب")
        XCTAssertEqual(TitleMatch.similarity("Spider-Man", "spider man"), 1)
        XCTAssertEqual(TitleMatch.similarity("", "anything"), 0)
        XCTAssertEqual(TitleMatch.similarity("The Bear", "The Boys"), 0.5)
        XCTAssertEqual(TitleMatch.similarity("Dune", "Dune Part Two"), 0.82, "a title that only adds a subtitle counts as close")
        XCTAssertLessThan(TitleMatch.similarity("Dune", "Dunes of Mars"), 0.8)
    }

    func testStripYear() {
        XCTAssertEqual(TitleMatch.stripYear("Animals 2026"), "Animals")
        XCTAssertEqual(TitleMatch.stripYear("Animals (2026)"), "Animals")
        XCTAssertEqual(TitleMatch.stripYear("Blade Runner 2049"), "Blade Runner")
        XCTAssertEqual(TitleMatch.stripYear("2012"), "2012", "a title that is only a year stays")
        XCTAssertEqual(TitleMatch.stripYear("Severance"), "Severance")
    }

    func testBestPostWantsTheSameKindAndYear() {
        let dune = item(tmdb(1, "Dune", year: 2021), kind: .movie)
        XCTAssertEqual(TitleMatch.bestPost(in: posts([post(1, "Dune", year: 1984), post(2, "Dune", year: 2021)]), for: dune)?.id, 2)
        XCTAssertEqual(TitleMatch.bestPost(in: posts([post(3, "Dune", year: 2022)]), for: dune)?.id, 3, "one year off is still the same title")
        XCTAssertNil(TitleMatch.bestPost(in: posts([post(4, "Dune", year: 1984)]), for: dune))
        XCTAssertNil(TitleMatch.bestPost(in: posts([post(5, "Dune", year: 2021, series: true)]), for: dune), "a series is not a movie")
        XCTAssertNil(TitleMatch.bestPost(in: posts([post(6, "Dune", year: 2021, soon: true)]), for: dune), "upcoming titles are skipped")
        XCTAssertNil(TitleMatch.bestPost(in: posts([post(7, "Dunkirk", year: 2021)]), for: dune))
    }

    func testATitleBuriedUnderItsNamesakesIsFoundByItsYear() async throws {
        let net = stockedCatalogue()
        net.shelves["Film N3x"] = [post(900, "Film N3x", year: 2001), post(901, "Film N3x", year: 1990)]
        net.shelves["Film N3x 2024"] = [post(103, "Film N3x")]
        let trending = Trending(key: "t", transport: net.transport, search: net.search)
        let row = await trending.row(.trend)
        XCTAssertEqual(row.map(\.id), [101, 102, 103, 104, 105, 106])
    }

    func testBestPostPrefersWhatCanBeStreamed() {
        let dune = item(tmdb(1, "Dune", year: 2021), kind: .movie)
        let found = TitleMatch.bestPost(in: posts([post(1, "Dune", year: 2021, stream: false), post(2, "Dune", year: 2021)]), for: dune)
        XCTAssertEqual(found?.id, 2)
    }

    func testBestPostMatchesTheOriginalTitle() {
        let heist = item(tmdb(1, "Money Heist", year: 2017, tv: true, original: "La casa de papel"), kind: .tv)
        XCTAssertEqual(TitleMatch.bestPost(in: posts([post(9, "La Casa de Papel", year: 2017, series: true)]), for: heist)?.id, 9)
        XCTAssertEqual(TitleMatch.queries(for: heist), ["Money Heist", "La casa de papel", "Money Heist 2017"])
        let squid = item(tmdb(2, "Squid Game", tv: true, original: "오징어 게임"), kind: .tv)
        XCTAssertEqual(TitleMatch.queries(for: squid), ["Squid Game", "Squid Game 2024"], "an original title 30nama cannot be searched by is left out")
    }

    func testBestPostAgainstALiveSearchResponse() throws {
        let results = Post.list(try fixture("search")["result"])
        let series = item(tmdb(1396, "Breaking Bad", year: 2008, tv: true), kind: .tv)
        let film = item(tmdb(559969, "El Camino: A Breaking Bad Movie", year: 2019), kind: .movie)
        XCTAssertEqual(TitleMatch.bestPost(in: results, for: series)?.id, 12382)
        XCTAssertEqual(TitleMatch.bestPost(in: results, for: film)?.id, 228418)
    }

    // MARK: Lists

    func testItemsKnowTheirKind() {
        XCTAssertEqual(item(tmdb(1, "A", tv: true, type: "tv")).kind, .tv)
        XCTAssertEqual(item(tmdb(1, "A", tv: true)).kind, .tv, "without a type, an air date means a series")
        XCTAssertEqual(item(tmdb(1, "A")).kind, .movie)
        XCTAssertEqual(item(tmdb(1, "A", year: 2019)).year, 2019)
        XCTAssertNil(TMDBItem(JSON(["id": 7, "media_type": "person", "name": "Someone"] as [String: Any])), "people are not titles")
    }

    func testCandidatesDropDuplicatesAndTitlesWithoutAPoster() {
        let a = item(tmdb(1, "A")), b = item(tmdb(2, "B")), bare = item(tmdb(3, "C", poster: false))
        XCTAssertEqual(TrendingRow.pop.candidates(from: [[a, bare, b], [b, a]]).map(\.id), [1, 2])
    }

    func testFreshSeriesPutTheBestKnownFirst() {
        let quiet = item(tmdb(1, "Quiet", tv: true, popularity: 20, votes: 250), kind: .tv)
        let obscure = item(tmdb(2, "Obscure", tv: true, popularity: 900, votes: 12), kind: .tv)
        let hit = item(tmdb(3, "Hit", tv: true, popularity: 80, votes: 9000), kind: .tv)
        XCTAssertEqual(TrendingRow.fresh.candidates(from: [[quiet, obscure, hit]]).map(\.id), [3, 1])
        XCTAssertEqual(TrendingRow.trend.candidates(from: [[quiet, obscure, hit]]).map(\.id), [1, 2, 3], "other rows keep TMDB's order")
    }

    func testFreshSeriesAreAskedForTheLastThirtyDays() {
        let sources = TrendingRow.fresh.sources(now: Date(timeIntervalSince1970: 1_780_000_000))   // 2026-05-28 UTC
        XCTAssertEqual(sources.map(\.params["page"]), ["1", "2", "3"])
        XCTAssertEqual(sources[0].params["air_date.gte"], "2026-04-28")
    }

    // MARK: Rows

    func testReadsThePublicPanelWithoutAKey() async throws {
        let html = ##"<a data-media-type="tv" href="/tv/1-show-one?language=en-US"><h2 class="x"><span>Film N1x</span></h2></a></div><span class="release_date w-full">May 1, 2024</span>"##
            + ##"<a data-media-type="movie" href="/movie/2-two?language=en-US"><h2 class="x"><span>Film N2x &amp; Co</span></h2></a></div><span class="release_date w-full">May 3, 2024</span>"##
        let items = TMDBItem.list(TMDBItem.publicList(html))
        XCTAssertEqual(items.map(\.title), ["Film N1x", "Film N2x & Co"])
        XCTAssertEqual(items.map(\.kind), [.tv, .movie])
        XCTAssertEqual(items.map(\.year), [2024, 2024])
        XCTAssertTrue(items.allSatisfy(\.hasPoster))

        let net = FakeCatalogue()
        let trending = Trending(key: "  \n", transport: { request in
            XCTAssertEqual(request.url, TMDBItem.publicTrending)
            return (Data(html.utf8), HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
        }, search: net.search)
        _ = await trending.row(.trend)
        XCTAssertEqual(Array(net.searches.prefix(1)), ["Film N1x"], "the public list is looked for on 30nama")
        let fresh = await trending.row(.fresh)
        XCTAssertTrue(fresh.isEmpty, "rows that need the API stay empty without a key")
    }

    func testRowFindsTrendingTitlesOn30nama() async throws {
        let net = stockedCatalogue()
        let trending = Trending(key: " eyJsecret\n", transport: net.transport, search: net.search)
        let row = await trending.row(.trend)
        XCTAssertEqual(row.map(\.id), [101, 102, 103, 104, 105, 106], "in TMDB's order")
        XCTAssertTrue(row.allSatisfy { $0.cast.isEmpty }, "only the fields a list draws are kept")
        XCTAssertEqual(net.tmdbRequests.first?.value(forHTTPHeaderField: "Authorization"), "Bearer eyJsecret")
        XCTAssertEqual(net.tmdbRequests.first?.url?.absoluteString, "https://api.themoviedb.org/3/trending/all/week?language=en-US")
    }

    func testRowIsCappedAndStopsSearchingOnceFull() async throws {
        let net = stockedCatalogue(count: 30)
        let trending = Trending(key: "t", transport: net.transport, search: net.search)
        let row = await trending.row(.trend)
        XCTAssertEqual(row.map(\.id), Array(101...116))
        XCTAssertLessThanOrEqual(net.searches.count, 16 + 3, "at most the lookups already under way overshoot")
    }

    func testRowWithTooFewTitlesIsLeftOut() async throws {
        let net = stockedCatalogue(count: 6)
        net.shelves = net.shelves.filter { ["Film N1x", "Film N2x", "Film N3x", "Film N4x"].contains($0.key) }
        let trending = Trending(key: "t", transport: net.transport, search: net.search)
        let row = await trending.row(.trend)
        XCTAssertEqual(row.count, 0)
    }

    func testTwoTMDBTitlesThatAreOne30namaTitleShowOnce() async throws {
        let net = stockedCatalogue()
        net.lists["/trending/all/week"]?[1]["title"] = "Film N1x"
        net.lists["/trending/all/week"]?[1]["id"] = 77
        let trending = Trending(key: "t", transport: net.transport, search: net.search)
        let row = await trending.row(.trend)
        XCTAssertEqual(row.map(\.id), [101, 103, 104, 105, 106])
    }

    func testAnswersAreCachedAcrossLaunches() async throws {
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("trending-\(UUID().uuidString).json")
        defer { try? FileManager.default.removeItem(at: file) }
        let net = stockedCatalogue(count: 7)
        net.shelves["Film N7x"] = nil   // one title 30nama does not have
        let clock = TestClock(Date(timeIntervalSince1970: 1_780_000_000))
        func launch() throws -> Trending {
            Trending(key: "t", cacheFile: file, transport: net.transport, now: { clock.now }, search: net.search)
        }

        let first = try await launch().row(.trend)
        XCTAssertEqual(first.count, 6)
        XCTAssertEqual(net.tmdbRequests.count, 1)
        XCTAssertEqual(net.searches.count, 8, "the missing title is also tried with its year")

        clock.advance(hours: 5)
        let second = try await launch().row(.trend)
        XCTAssertEqual(second.map(\.id), first.map(\.id))
        XCTAssertEqual(net.tmdbRequests.count, 1, "the list is good for six hours")
        XCTAssertEqual(net.searches.count, 8, "found and missing titles are both remembered")

        clock.advance(hours: 20)
        _ = try await launch().row(.trend)
        XCTAssertEqual(net.tmdbRequests.count, 2, "the list is asked for again")
        XCTAssertEqual(net.searches.count, 10)
        XCTAssertEqual(net.searches.last, "Film N7x 2024", "a missing title is looked for again after a day")
    }

    func testAFailedSearchIsNotRememberedAsMissing() async throws {
        let net = stockedCatalogue()
        net.searchFails = true
        let trending = Trending(key: "t", transport: net.transport, search: net.search)
        let down = await trending.row(.trend)
        XCTAssertEqual(down.count, 0)
        net.searchFails = false
        let up = await trending.row(.trend)
        XCTAssertEqual(up.count, 6)
    }

    func testRowIsEmptyWhenTMDBRefuses() async throws {
        let net = stockedCatalogue()
        net.tmdbStatus = 401
        let trending = Trending(key: "wrong", transport: net.transport, search: net.search)
        let refused = await trending.row(.trend)
        XCTAssertEqual(refused.count, 0)
        XCTAssertEqual(net.searches.count, 0)
        net.tmdbStatus = 200
        let allowed = await trending.row(.trend)
        XCTAssertEqual(allowed.count, 6, "a refusal is not cached")
    }
}

private final class TestClock: @unchecked Sendable {
    private let lock = NSLock()
    private var time: Date

    init(_ time: Date) { self.time = time }

    var now: Date { lock.withLock { time } }
    func advance(hours: Double) { lock.withLock { time += hours * 3600 } }
}
