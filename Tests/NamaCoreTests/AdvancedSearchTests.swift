import XCTest
@testable import NamaCore

final class AdvancedSearchTests: XCTestCase {
    func testReadsTheChoicesTheServerActsOn() throws {
        let choices = SearchChoices(try JSON.parse(Data("""
            {"genre":[{"name":"Drama","value":"drama"},{"name":"All","value":""}],
             "type":[{"name":"Movie","value":"movie"},{"name":"Movie Anime","value":"movie_anime"},{"name":"Anime","value":"anime"}],
             "channel":[{"name":"Netflix","value":"Netflix"}],
             "language":[{"name":"Korean","value":"ko"}],
             "imdb_rate":[{"name":"Above 8.5","value":8.5},{"name":"Under 5.0","value":-5}],
             "age":["PG-13","R"],
             "quality":[{"name":"4K","value":"4K"}],
             "min_year":1950,"max_year":2026}
            """.utf8)))
        XCTAssertEqual(choices.genres.map(\.value), ["drama"], "the server's own All entry is not a choice")
        XCTAssertEqual(choices.types.map(\.value), ["movie", "anime"], "anime films are refused by the server")
        XCTAssertEqual(choices.networks.map(\.name), ["Netflix"])
        XCTAssertEqual(choices.scores.map(\.value), ["8.5"], "only floors")
        XCTAssertEqual(choices.ages.map(\.value), ["PG-13", "R"], "bare values are their own names")
        XCTAssertEqual(choices.years, 1950...2026)
    }

    func testBuildsTheParameters() {
        XCTAssertEqual(AdvancedQuery().parameters(), #"{"stream":"true"}"#)
        XCTAssertEqual(AdvancedQuery().count, 0)
        let query = AdvancedQuery(genres: ["drama", "crime"], type: "series", language: "ko", network: "Netflix", score: "8", age: "R",
                                  director: "  Bong Joon Ho ", fromYear: 2020, toYear: 2010, x265: true, streamOnly: false, sort: "imdb-rate")
        XCTAssertEqual(query.parameters(),
                       #"{"age":"R","channel":"Netflix","director":"Bong Joon Ho","genre":["drama","crime"],"imdb_rate":8,"language":"ko","max_year":"2020","min_year":"2010","type":["series"],"x265":"true"}"#,
                       "the years put the right way round; the type as a list")
        XCTAssertEqual(query.count, 11)
        let request = query.request(page: 3)
        XCTAssertEqual(request["orderby"], "imdb-rate")
        XCTAssertEqual(request["page"], "3")
    }

    func testOneYearIsEnough() {
        XCTAssertEqual(AdvancedQuery(fromYear: 1990).parameters(), #"{"min_year":"1990","stream":"true"}"#)
        XCTAssertEqual(AdvancedQuery(toYear: 1995).parameters(), #"{"max_year":"1995","stream":"true"}"#)
    }
}
