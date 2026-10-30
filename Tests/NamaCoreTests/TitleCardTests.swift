import XCTest
@testable import NamaCore

/// As the Windows app's TitleCardTests and the Android app's TitleCardTests.
final class TitleCardTests: XCTestCase {
    private func json(_ text: String) -> JSON { try! JSON.parse(Data(text.utf8)) }

    private var details: JSON {
        json(#"""
        {"id":604,"title":"The Matrix Reloaded","original_title":"The Matrix Reloaded","original_language":"en","poster_path":"/main.jpg",
         "images":{"posters":[
           {"file_path":"/main.jpg","iso_639_1":"en","vote_average":9},
           {"file_path":"/french.jpg","iso_639_1":"fr","vote_average":8},
           {"file_path":"/second.jpg","iso_639_1":"en","vote_average":6},
           {"file_path":"/wordless.jpg","iso_639_1":null,"vote_average":7}]},
         "production_companies":[{"name":"Silver","logo_path":null},{"name":"Village Roadshow","logo_path":"/vr.svg"}]}
        """#)
    }

    private var collection: JSON {
        json(#"""
        {"name":"The Matrix Collection",
         "parts":[{"id":605,"release_date":"2003-11-05"},{"id":603,"release_date":"1999-03-31"},{"id":604,"release_date":"2003-05-15"}],
         "translations":{"translations":[
           {"iso_639_1":"fa","data":{"name":"مجموعهٔ ماتریکس"}},
           {"iso_639_1":"de","data":{"name":"Matrix"}}]}}
        """#)
    }

    func testFromTMDBAsTheDailyJobMakesIt() throws {
        let card = try XCTUnwrap(TitleCard.fromTMDB(details, collection: collection))
        XCTAssertEqual(card.poster, "wordless.jpg", "the best voted other poster, English or wordless")
        XCTAssertNil(card.ownName, "an English title has no name of its own to show")
        XCTAssertEqual(card.collection, .init(names: ["en": "The Matrix Collection", "fa": "مجموعهٔ ماتریکس"], at: 2, of: 3))
        XCTAssertEqual(card.collection?.name(.fa), "مجموعهٔ ماتریکس")
        XCTAssertEqual(card.collection?.name(.ar), "The Matrix Collection", "Arabic falls back to English")
        XCTAssertEqual(card.companyLogo, "vr.png")
        XCTAssertEqual(card.companyName, "Village Roadshow")
    }

    func testOwnNameOnlyWhereItDiffers() {
        let spirited = json(#"{"id":129,"title":"Spirited Away","original_title":"千と千尋の神隠し","original_language":"ja"}"#)
        XCTAssertEqual(TitleCard.fromTMDB(spirited)?.ownName, "千と千尋の神隠し")
        // The same name in another case is nothing to show.
        XCTAssertNil(TitleCard.fromTMDB(json(#"{"id":1,"title":"Amélie","original_title":"amélie","original_language":"fr"}"#)))
    }

    func testASeriesFallsBackToItsNetwork() {
        let dark = json(#"""
            {"id":1399,"name":"Dark","original_name":"Dark","original_language":"de",
             "production_companies":[{"name":"Wiedemann","logo_path":null}],"networks":[{"name":"Netflix","logo_path":"/nf.png"}]}
            """#)
        XCTAssertEqual(TitleCard.fromTMDB(dark)?.companyLogo, "nf.png")
    }

    func testACollectionOfOneIsNone() {
        XCTAssertNil(TitleCard.fromTMDB(details, collection: json(#"{"name":"Solo","parts":[{"id":604}]}"#))?.collection)
    }

    func testFromTheDailyFile() {
        let shard = json(#"{"version":1,"updated":"2026-10-29","cards":{"tt0234215":{"p":"wordless.jpg","c":{"names":{"en":"The Matrix Collection","fa":"مجموعهٔ ماتریکس"},"at":2,"of":3},"l":"vr.png","n":"Village Roadshow"}}}"#)
        XCTAssertEqual(TitleCard.fromShard(shard, imdb: "tt0234215"), TitleCard.fromTMDB(details, collection: collection))
        XCTAssertNil(TitleCard.fromShard(shard, imdb: "tt0000001"))
        XCTAssertNil(TitleCard.fromShard(json(#"{"version":2,"cards":{}}"#), imdb: "tt0234215"))
    }

    func testTheFileIsTheLastTwoDigits() {
        XCTAssertEqual(TitleCard.shard("tt0234215"), "15")
        XCTAssertEqual(TitleCard.shard("tt7"), "07")
        XCTAssertTrue(TitleCard.address("tt0234215").absoluteString.hasSuffix("/data/cards/15.json"))
    }
}
