import XCTest
@testable import NamaCore

final class RatingsTests: XCTestCase {
    private func post(_ id: Int, _ genres: [String], imdb: Double? = nil, soon: Bool = false) -> Post {
        var json: [String: Any] = [
            "id": id,
            "title": ["english": "Title \(id) 2020", "local": "عنوان \(id)"],
            "info": ["year": 2020],
            "options": ["is_series": id % 2 == 0, "coming_soon": soon],
            "genre": genres.map { ["name": $0.capitalized, "slug": $0] },
            "image": ["poster": ["preview": "00OZZy", "webp": ["large": "https://example.com/\(id)-l.webp", "big": "https://example.com/\(id)-b.webp"]]],
        ]
        if let imdb { json["score"] = ["imdb": ["score": imdb]] }
        return Post(JSON(json))!
    }

    private func at(_ seconds: TimeInterval) -> Date { Date(timeIntervalSince1970: seconds) }

    func testRateChangeAndRemove() {
        var book = RatingBook()
        book.rate(post(1, ["drama"]), stars: 4.3)
        XCTAssertEqual(book.stars(for: 1), 4.5, "snaps to the nearest half star")
        book.rate(post(1, ["drama"]), stars: 9)
        XCTAssertEqual(book.stars(for: 1), 5)
        book.rate(post(2, ["comedy"]), stars: 0)
        XCTAssertEqual(book.stars(for: 2), 0.5)
        XCTAssertEqual(book.count, 2)
        XCTAssertEqual(book.average, 2.75)
        book.rate(post(1, ["drama"]), stars: nil)
        XCTAssertNil(book.stars(for: 1))
        XCTAssertEqual(book.count, 1)
        XCTAssertNil(RatingBook().average)
    }

    func testExportsACSVLetterboxdCanRead() {
        var book = RatingBook()
        book.rate(post(1, ["drama"]), stars: 4, date: at(1_000))
        book.rate(post(2, ["comedy"]), stars: 2.5, date: at(2_000))
        var awkward = post(3, ["crime"])
        awkward.titleEnglish = "Me, Myself & \"I\" 2020"
        book.rate(awkward, stars: 5, date: at(3_000))
        XCTAssertEqual(book.csv(), """
        Title,Year,Rating
        "Me, Myself & ""I\""",2020,5.0
        Title 2,2020,2.5
        Title 1,2020,4.0

        """, "newest first, the year taken off the title, commas and quotes escaped")
        XCTAssertEqual(RatingBook().csv(), "Title,Year,Rating\n")
    }

    func testSurvivesEncoding() throws {
        var book = RatingBook()
        book.rate(post(1, ["drama", "crime"]), stars: 4, date: Date(timeIntervalSince1970: 1_000))
        book.rate(post(2, ["comedy"]), stars: 2.5, date: Date(timeIntervalSince1970: 2_000))
        let restored = try XCTUnwrap(RatingBook(data: book.encoded()))
        XCTAssertEqual(restored, book)
        XCTAssertEqual(restored.newestFirst.map(\.postID), [2, 1])
        XCTAssertNil(RatingBook(data: Data("not json".utf8)))
    }

    func testBooksSavedBeforeRemovalsWereRecordedStillOpen() throws {
        let legacy = Data(#"[{"postID":3,"stars":4,"date":1000,"isSeries":false,"isAnime":false,"genres":[]}]"#.utf8)
        let book = try XCTUnwrap(RatingBook(data: legacy))
        XCTAssertEqual(book.stars(for: 3), 4)
        XCTAssertTrue(book.removed.isEmpty)
    }

    func testRemovalIsRememberedUntilTheTitleIsRatedAgain() throws {
        var book = RatingBook()
        book.rate(post(1, ["drama"]), stars: nil, date: at(5))
        XCTAssertTrue(book.removed.isEmpty, "nothing was rated, so nothing was removed")
        book.rate(post(1, ["drama"]), stars: 4, date: at(10))
        book.rate(post(1, ["drama"]), stars: nil, date: at(20))
        XCTAssertEqual(book.removed, [1: at(20)])
        XCTAssertEqual(try XCTUnwrap(RatingBook(data: book.encoded())), book, "removals survive encoding")
        book.rate(post(1, ["drama"]), stars: 3, date: at(30))
        XCTAssertTrue(book.removed.isEmpty)
    }

    func testMergeKeepsWhateverHappenedLast() {
        var here = RatingBook(), there = RatingBook()
        here.rate(post(1, ["drama"]), stars: 2, date: at(10))      // rated on both, later there
        there.rate(post(1, ["drama"]), stars: 5, date: at(20))
        here.rate(post(2, ["drama"]), stars: 4, date: at(10))      // only here
        there.rate(post(3, ["drama"]), stars: 3, date: at(10))     // only there
        here.rate(post(4, ["drama"]), stars: 4, date: at(10))      // rated here, removed there afterwards
        there.rate(post(4, ["drama"]), stars: 4, date: at(10))
        there.rate(post(4, ["drama"]), stars: nil, date: at(30))
        there.rate(post(5, ["drama"]), stars: 1, date: at(10))     // removed there, rated again here afterwards
        there.rate(post(5, ["drama"]), stars: nil, date: at(20))
        here.rate(post(5, ["drama"]), stars: 4.5, date: at(40))

        let merged = here.merged(with: there)
        XCTAssertEqual(merged.stars(for: 1), 5)
        XCTAssertEqual(merged.stars(for: 2), 4)
        XCTAssertEqual(merged.stars(for: 3), 3)
        XCTAssertNil(merged.stars(for: 4), "the removal came after the rating")
        XCTAssertEqual(merged.removed, [4: at(30)])
        XCTAssertEqual(merged.stars(for: 5), 4.5, "the rating came after the removal")
        XCTAssertEqual(there.merged(with: here), merged, "both devices arrive at the same book")
        XCTAssertEqual(merged.merged(with: here), merged)
    }

    func testRatedTitleCanBeShownAgain() throws {
        let original = post(8, ["drama"])
        let shown = try XCTUnwrap(Rating(post: original, stars: 4).post)
        XCTAssertEqual(shown.id, 8)
        XCTAssertEqual(shown.title(.en), "Title 8")
        XCTAssertEqual(shown.title(.fa), "عنوان 8")
        XCTAssertTrue(shown.isSeries)
        XCTAssertEqual(shown.poster.big, original.poster.big)
        XCTAssertEqual(shown.poster.blurhash, "00OZZy")
        XCTAssertEqual(shown.genres.map(\.slug), ["drama"])
    }

    func testTasteShrinksThinEvidence() {
        var book = RatingBook()
        for id in 1...6 { book.rate(post(id, ["drama"]), stars: 4) }
        book.rate(post(7, ["western"]), stars: 5)
        book.rate(post(8, ["horror"]), stars: 1)
        let taste = book.taste()
        XCTAssertEqual(taste["drama"] ?? 0, 0.75, accuracy: 0.0001)       // six at +1, over 6 + 2
        XCTAssertEqual(taste["western"] ?? 0, 2.0 / 3, accuracy: 0.0001)  // one at +2, over 1 + 2
        XCTAssertLessThan(taste["horror"] ?? 0, 0)
        XCTAssertEqual(book.favoriteGenres(), ["drama", "western"], "many good dramas outrank one great western")
    }

    func testTasteAtAGlance() {
        var book = RatingBook()
        XCTAssertTrue(book.favoriteGenreNames().isEmpty)
        XCTAssertEqual(book.spread(), [0, 0, 0, 0, 0])
        for id in 1...4 { book.rate(post(id, ["drama"]), stars: 4.5) }
        book.rate(post(5, ["comedy"]), stars: 4)
        book.rate(post(6, ["horror"]), stars: 1)
        book.rate(post(7, ["horror"]), stars: 0.5)
        let favourites = book.favoriteGenreNames()
        XCTAssertEqual(favourites.map(\.name), ["Drama", "Comedy"], "by name, strongest first, disliked genres left out")
        XCTAssertEqual(favourites.first?.strength, 1)
        XCTAssertLessThan(favourites[1].strength, 1)
        XCTAssertEqual(book.spread(), [2, 0, 0, 1, 4], "half stars count with the star above")
    }

    func testRecommendations() {
        var book = RatingBook()
        XCTAssertTrue(book.recommend(from: [post(50, ["drama"])]).isEmpty, "nothing to go on yet")
        for id in 1...4 { book.rate(post(id, ["drama"]), stars: 4.5) }
        book.rate(post(5, ["horror"]), stars: 1)

        let picks = book.recommend(from: [
            post(1, ["drama"]),                 // already rated
            post(10, ["drama"], imdb: 7.0),
            post(11, ["drama"], imdb: 8.5),     // same fit, better regarded
            post(12, ["horror"], imdb: 9.0),    // disliked genre
            post(13, ["drama", "horror"]),      // mixed: averages out below a pure drama
            post(14, ["musical"]),              // unknown genre: no opinion
            post(15, ["drama"], soon: true),    // not out yet
            post(11, ["drama"], imdb: 8.5),     // duplicate candidate
        ])
        XCTAssertEqual(picks.map(\.id), [11, 10, 13])
        XCTAssertEqual(book.recommend(from: [post(10, ["drama"]), post(11, ["drama"])], limit: 1).count, 1)
    }
}
