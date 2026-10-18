import XCTest
@testable import NamaCore

func fixture(_ name: String) throws -> JSON {
    let url = try XCTUnwrap(Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures"))
    return try JSON.parse(Data(contentsOf: url))
}

final class ModelTests: XCTestCase {
    func testDownloadOptionsForAFilmAndForASeries() throws {
        let film = DownloadOptions(try JSON.parse(Data(#"""
        {"download":[
          {"dl":"https://example.com/files/Some.Film.2020.1080p.x265.mkv","encoder":"30nama","id":239523688524,"quality":"x265 BluRay 1080p",
           "size":"2.1 GB","tags":["x265"],"persian_subtitle":"https://example.com/sub/1","source":null},
          {"dl":null,"quality":"broken"}
        ]}
        """#.utf8)))
        XCTAssertEqual(film.files.count, 1, "an entry without an address is not an offer")
        XCTAssertTrue(film.packs.isEmpty)
        XCTAssertEqual(film.files[0].fileName, "Some.Film.2020.1080p.x265.mkv")
        XCTAssertEqual(film.files[0].summary, "x265 BluRay 1080p · 30nama · 2.1 GB")
        XCTAssertNotNil(film.files[0].subtitle)

        let series = DownloadOptions(try JSON.parse(Data(#"""
        {"download":{
          "s2":[{"id":8590,"season":2,"season_int":2,"quality":"x265 BluRay 1080p","size":"333 MB","encoder":"30nama","tags":["x265"],
                 "link":[{"dl":"https://example.com/s2/Show.S02E01.mkv","episode":1,"id":1,"persian_subtitle":null},
                         {"dl":"https://example.com/s2/Show.S02E02.mkv","episode":2,"id":2,"persian_subtitle":null}]}],
          "s1":[{"id":8589,"season":1,"season_int":1,"quality":"WEB-DL 720p","size":"200 MB","encoder":"30nama","tags":[],
                 "link":[{"dl":"https://example.com/s1/Show%20S01E00%20Special.mkv","episode":"Special","id":3,"persian_subtitle":"https://example.com/sub/3"}]},
                {"id":1,"season":1,"season_int":1,"quality":"empty","link":[]}]
        }}
        """#.utf8)))
        XCTAssertTrue(series.files.isEmpty)
        XCTAssertEqual(series.seasons, [1, 2])
        XCTAssertEqual(series.packs.map(\.quality), ["WEB-DL 720p", "x265 BluRay 1080p"], "by season; a pack with no files is dropped")
        XCTAssertEqual(series.packs[1].files.map(\.episode), ["1", "2"])
        XCTAssertEqual(series.packs[0].files[0].episode, "Special")
        XCTAssertEqual(series.packs[0].files[0].fileName, "Show S01E00 Special.mkv", "percent escapes are undone")
        XCTAssertEqual(series.packs[1].files[0].season, 2)
        XCTAssertTrue(DownloadOptions(JSON(["download": NSNull()] as [String: Any])).isEmpty)

        // The server's ids run to eighteen digits; read as numbers, neighbours come out the same.
        let long = DownloadOptions(try JSON.parse(Data(#"""
        {"download":{"s1":[
          {"id":1,"season_int":1,"quality":"1080p","link":[
            {"dl":"https://example.com/a/Show.S01E01.mkv","episode":1,"id":278294959021668101},
            {"dl":"https://example.com/a/Show.S01E02.mkv","episode":2,"id":278294959021668102},
            {"dl":"https://example.com/a/Show.S01E03.mkv","episode":3,"id":278294959021668103}]},
          {"id":2,"season_int":1,"quality":"720p","link":[
            {"dl":"https://example.com/b/Show.S01E01.mkv","episode":1,"id":278294959021668201},
            {"dl":"https://example.com/b/Show.S01E10.mkv","episode":10,"id":278294959021668210}]}
        ]}}
        """#.utf8)))
        XCTAssertEqual(Set(long.packs[0].files.map(\.id)).count, 3, "every file keeps an id of its own")
        XCTAssertEqual(long.episodes(inSeason: 1), ["1", "2", "3", "10"], "what the season has across all packs, in number order")
        XCTAssertEqual(long.packs.first { $0.quality == "720p" }?.files.count, 2, "this pack lacks two of the four")
    }

    func testFilterBuildsTheParametersAdvancedSearchTakes() throws {
        let options = FilterOptions(try JSON.parse(Data(#"""
        {"genre":[{"name":"Drama","value":"drama"},{"name":"All","value":""}],
         "country":[{"name":"United States","value":"US"}],
         "imdb_rate":[{"name":"Above 8.5","value":8.5},{"name":"Above 8.0","value":8},{"name":"Under 5.0","value":-5}],
         "min_year":1890,"max_year":2026}
        """#.utf8)))
        XCTAssertEqual(options.genres.map(\.value), ["drama"], "the server's own All entry is not a choice")
        XCTAssertEqual(options.scores.map(\.value), ["8.5", "8"], "only floors")
        XCTAssertEqual(options.decades.first, 2020)
        XCTAssertEqual(options.decades.last, 1950)

        XCTAssertTrue(Filter().isEmpty)
        let filter = Filter(genre: "drama", country: "US", score: "8.5", decade: 2010)
        XCTAssertFalse(filter.isEmpty)
        XCTAssertEqual(filter.parameters(type: "movie"),
                       #"{"country":"US","genre":"drama","imdb_rate":8.5,"max_year":2019,"min_year":2010,"stream":"true","type":["movie"]}"#)
        XCTAssertEqual(Filter(score: "8").parameters(type: nil), #"{"imdb_rate":8,"stream":"true"}"#)
    }

    func testUpcomingEpisodesAreSortedSoonestFirst() {
        func series(_ id: Int, next: String?) -> Post {
            var air: [String: Any] = ["air_status": "Returning"]
            if let next { air["next_episode_date"] = ["g": next, "j": "۱۴۰۵-۰۱-۰۱"] }
            return Post(JSON(["id": id, "title": ["english": "Show \(id)"], "options": ["is_series": true], "air": air] as [String: Any]))!
        }
        let now = ISO8601DateFormatter().date(from: "2026-10-13T12:00:00Z")!
        // The server writes the day out; the plain form is accepted as well.
        let posts = [series(1, next: "2 November 2026 Monday"), series(2, next: nil), series(3, next: "13 October 2026 Tuesday"),
                     series(4, next: "1 September 2026 Tuesday"), series(5, next: "2026-10-20"), series(3, next: "13 October 2026 Tuesday")]
        XCTAssertEqual(Post.upcoming(posts, now: now).map(\.id), [3, 5, 1], "today counts, the past and the undated do not, nothing twice")
    }

    func testBackdropsAreTheBestLiveActionTitlesWithAWidePicture() {
        func entry(_ id: Int, imdb: Double, type: String = "movie", genre: String = "drama", cover: Bool = true) -> [String: Any] {
            var image: [String: Any] = ["poster": ["webp": ["large": "https://example.com/\(id).webp"]]]
            if cover { image["cover"] = ["webp": "https://example.com/\(id)-wide.webp"] }
            return ["id": id, "title": ["english": "Title \(id)"], "options": ["title_type": type],
                    "genre": [["name": genre.capitalized, "slug": genre]], "score": ["imdb": ["score": imdb]], "image": image]
        }
        let feed = HomeFeed(JSON([
            "hero_header": ["posts": [entry(1, imdb: 8.5, type: "anime")]],
            "movies": ["posts": [entry(2, imdb: 7.1), entry(3, imdb: 9.0), entry(4, imdb: 9.5, cover: false), entry(5, imdb: 8.8, genre: "animation")]],
            "series": ["posts": [entry(6, imdb: 8.2), entry(3, imdb: 9.0)]],
        ] as [String: Any]))
        XCTAssertEqual(feed.backdrops().map(\.id), [3, 6, 2], "best first; no anime, no animation, nothing without a wide picture, nothing twice")
        XCTAssertEqual(feed.backdrops(limit: 2).map(\.id), [3, 6])
        let cartoons = HomeFeed(JSON(["hero_header": ["posts": [entry(1, imdb: 8.5, type: "anime")]]] as [String: Any]))
        XCTAssertEqual(cartoons.backdrops().map(\.id), [1], "with nothing else to show, animation is better than a blank screen")
    }

    func testHomeFeedFromLiveResponse() throws {
        let feed = HomeFeed(try fixture("home_stream")["result"])
        XCTAssertEqual(feed.heroes.count, 3)
        XCTAssertEqual(feed.sections.map(\.key), ["suggested", "movies", "series", "anime"])

        let hero = try XCTUnwrap(feed.heroes.first)
        XCTAssertEqual(hero.id, 469773)
        XCTAssertEqual(hero.kind, .anime)
        XCTAssertTrue(hero.isSeries)
        XCTAssertEqual(hero.seasons, 3)
        XCTAssertEqual(hero.runtimeMinutes, 24)
        XCTAssertEqual(hero.imdbScore, 8.5)
        XCTAssertEqual(hero.imdbID, "tt14976292")
        XCTAssertEqual(hero.poster.blurhash, "bRIr1_%M~qxtyXtR%hRjIUxu")
        XCTAssertEqual(hero.cover?.host, "cdn-world.hana-api.com")
        XCTAssertFalse(hero.genres.isEmpty)
    }

    func testTitleDropsItsOwnYearOnly() throws {
        let hero = try XCTUnwrap(HomeFeed(try fixture("home_stream")["result"]).heroes.first)
        XCTAssertEqual(hero.titleEnglish, "Link Click 2021")
        XCTAssertEqual(hero.title(.en), "Link Click")
        XCTAssertEqual(hero.title(.fa), "Link Click", "falls back to English when there is no local title")
        XCTAssertNil(hero.alternateTitle(.fa))

        let blade = try XCTUnwrap(Post(JSON(["id": 1, "title": ["english": "Blade Runner 2049", "local": "بلید رانر"], "info": ["year": 2017]] as [String: Any])))
        XCTAssertEqual(blade.title(.en), "Blade Runner 2049")
        XCTAssertEqual(blade.title(.fa), "بلید رانر")
        XCTAssertEqual(blade.alternateTitle(.fa), "Blade Runner 2049")

        let war = try XCTUnwrap(Post(JSON(["id": 2, "title": ["english": "1917 2019"], "info": ["year": 2019]] as [String: Any])))
        XCTAssertEqual(war.title(.en), "1917")

        let local = try XCTUnwrap(Post(JSON(["id": 3, "title": ["english": "1917 2019", "local": "۱۹۱۷ 2019"], "info": ["year": 2019]] as [String: Any])))
        XCTAssertEqual(local.title(.fa), "۱۹۱۷", "local titles carry the year too")
    }

    func testSingleCarriesCastAndCrew() throws {
        let movie = try XCTUnwrap(Post(try fixture("single_movie")["result"]))
        XCTAssertEqual(movie.kind, .movie)
        XCTAssertEqual(movie.cast.first?.name, "Ben Affleck")
        XCTAssertEqual(movie.cast.first?.role, "Milo Bradford")
        XCTAssertNotNil(movie.cast.first?.poster)
        XCTAssertEqual(movie.directors, ["Ben Affleck"])
        XCTAssertFalse(movie.writers.isEmpty)
        XCTAssertEqual(movie.countries.first, "United States")
        XCTAssertNil(movie.resume)

        let series = try XCTUnwrap(Post(try fixture("single_series")["result"]))
        XCTAssertNotNil(series.airStatus)
        XCTAssertNotNil(series.nextEpisodeDate.jalali)
        // What 30nama's own apps show beside the scores.
        XCTAssertEqual(series.imdbVotes, 7639)
        XCTAssertEqual(series.inWatchlists, 195)
        XCTAssertEqual(series.inFavorites, 184)
        XCTAssertNil(series.imdbTop250, "sent as an empty string: not on the list")
        XCTAssertEqual(series.otherLanguages.first, "Chinese")
        XCTAssertFalse(series.otherLanguages.contains("Mandarin"), "the primary language is not repeated")
        XCTAssertEqual(series.latestUpdate?.title, "اضافه شدن قسمت جدید")
        XCTAssertEqual(series.latestUpdate?.content, "اپیزود 10 فصل 3")
        XCTAssertEqual(Post(JSON(["id": 1, "score": ["imdb": ["top_250": "12"]]] as [String: Any]))?.imdbTop250, 12)
    }

    func testCriticScores() throws {
        let dune = try XCTUnwrap(Post(JSON(["id": 9, "score": [
            "imdb": ["score": 8.0], "rottentomatoes": ["link": "https://www.rottentomatoes.com/m/dune_2021", "score": 85],
            "metacritic": ["score": 74], "mydramalist": ["score": 0],
        ]] as [String: Any])))
        XCTAssertEqual(dune.rottenTomatoes, 85)
        XCTAssertEqual(dune.metacritic, 74)
        XCTAssertNil(dune.fanScore, "a zero score means the site has none")

        let hero = try XCTUnwrap(HomeFeed(try fixture("home_stream")["result"]).heroes.first)
        XCTAssertEqual(hero.fanScore?.source, "MAL")
        XCTAssertEqual(hero.fanScore?.value, 8.72)
        XCTAssertNil(hero.rottenTomatoes)
    }

    func testPosterPicksSmallestSufficientVariant() throws {
        let poster = try XCTUnwrap(HomeFeed(try fixture("home_stream")["result"]).heroes.first).poster
        XCTAssertTrue(try XCTUnwrap(poster.url(pixelWidth: 200)).absoluteString.contains("-l_"))
        XCTAssertTrue(try XCTUnwrap(poster.url(pixelWidth: 400)).absoluteString.contains("-b_"))
        XCTAssertTrue(try XCTUnwrap(poster.url(pixelWidth: 900)).absoluteString.contains("-b_"), "largest when nothing is big enough")
    }

    func testMenuAndSearchPage() throws {
        let menu = Menu(try fixture("menu")["result"])
        XCTAssertEqual(menu.categories.first?.slug, "movies")
        XCTAssertEqual(menu.genres.count, 3)
        XCTAssertNotNil(menu.genres.first?.thumb)

        let page = PostPage(try fixture("search")["result"])
        XCTAssertEqual(page.posts.count, 2)
        XCTAssertEqual(page.pages, 4)
        XCTAssertEqual(page.total, 86)
    }

    func testSeriesStreamIsSortedNumerically() throws {
        let stream = StreamResult(try fixture("stream_series")["result"])
        XCTAssertEqual(stream.items.map { "\($0.season)x\($0.episodeRaw ?? "")" }, ["1x2", "1x10", "1x11,12", "2x1"])
        XCTAssertEqual(stream.seasons, [1, 2])
        XCTAssertEqual(stream.resume?.fileID, "102")
        XCTAssertEqual(stream.startIndex, 0)
        XCTAssertEqual(stream.resume?.remainingMinutes, 34)

        let first = stream.items[0]
        XCTAssertEqual(first.durationMinutes, 44)
        XCTAssertEqual(first.introEnd, 90)
        XCTAssertEqual(first.creditsStart, 2470)
        XCTAssertEqual(first.subtitles["fa"]?.lastPathComponent, "fa.vtt")
        XCTAssertEqual(first.subtitles["en"]?.lastPathComponent, "en.vtt")
        XCTAssertEqual(Episode.label(stream.items[2].episodeRaw, fallback: 3), "11–12")
    }

    func testStreamAsServedToASubscriber() throws {
        let stream = StreamResult(try fixture("stream_subscriber")["result"])
        XCTAssertEqual(stream.items.map(\.episodeNumber), [1, 2, 3, 4, 5, 6])
        XCTAssertEqual(stream.startIndex, 5, "resumes the episode named by continue_watching")
        XCTAssertEqual(stream.resume?.time, 60)
        XCTAssertEqual(stream.resume?.progress ?? 0, 0.01, accuracy: 0.0001, "no duration: falls back to the reported percentage")
        XCTAssertNil(stream.resume?.remainingMinutes)

        let item = stream.items[0]
        XCTAssertEqual(item.fileID, "182990")
        XCTAssertEqual(item.qualities.map(\.label), ["خودکار", "720p", "1080p"])
        XCTAssertEqual(item.quality(preferAdaptive: false)?.url.lastPathComponent, "1080.m3u8")
        XCTAssertEqual(item.quality(preferAdaptive: true)?.url.lastPathComponent, "auto.m3u8")
        let full = item.qualities.first { $0.label == "1080p" }
        XCTAssertEqual(full?.stereo?.lastPathComponent, "1080-2ch.m3u8")
        XCTAssertEqual(full?.surround?.lastPathComponent, "1080-6ch.m3u8")
        XCTAssertEqual(full?.url(for: .stereo).lastPathComponent, "1080-2ch.m3u8")
        XCTAssertEqual(full?.url(for: .original).lastPathComponent, "1080.m3u8")
        XCTAssertEqual(full?.mixes, [.stereo, .surround, .original])
        XCTAssertEqual(item.introStart, 403)
        XCTAssertEqual(item.introEnd, 413)
        XCTAssertNil(item.recapEnd)
        XCTAssertEqual(item.creditsStart, 3328)
        XCTAssertEqual(item.subtitles.count, 2)
    }

    func testQualityPreference() throws {
        let item = StreamResult(try fixture("stream_series")["result"]).items[0]
        XCTAssertEqual(item.qualities.map(\.label), ["Auto", "720p", "1080p"])
        XCTAssertEqual(item.quality(preferAdaptive: false)?.url.absoluteString, "https://example.com/1080.m3u8", "URL is trimmed")
        XCTAssertEqual(item.quality(preferAdaptive: true)?.label, "Auto")

        let movie = StreamResult(try fixture("stream_movie")["result"])
        XCTAssertEqual(movie.items.count, 1)
        XCTAssertEqual(movie.items[0].fileID, "9001", "numeric ids are read as strings")
        XCTAssertEqual(movie.items[0].quality(preferAdaptive: false)?.url.lastPathComponent, "movie.m3u8")
        XCTAssertNil(movie.resume)
    }

    func testSkipWindows() throws {
        let item = StreamResult(try fixture("stream_series")["result"]).items[0]
        XCTAssertNil(item.skipTarget(at: 2))
        XCTAssertEqual(item.skipTarget(at: 30)?.to, 90)
        XCTAssertEqual(item.skipTarget(at: 30)?.isIntro, true)
        XCTAssertNil(item.skipTarget(at: 89.5), "no button in the last second")
        XCTAssertNil(item.skipTarget(at: 200))
    }

    func testContinueWatchingListShapes() {
        let wrapped = JSON(["posts": [["post": ["id": 5], "continue_watching": ["file_id": 9, "time": "120", "duration": 1200, "season": 2, "episode": 3]]]] as [String: Any])
        let p = Post.continueList(wrapped)
        XCTAssertEqual(p.first?.id, 5)
        XCTAssertEqual(p.first?.resume?.progress, 0.1)
        XCTAssertEqual(p.first?.resume?.season, 2)

        let nested = JSON([["id": 6, "continue_watching": ["info": ["file_id": "x", "season": "1", "episode": "4"], "position": 60, "duration": 600]]] as [Any])
        XCTAssertEqual(Post.continueList(nested).first?.resume?.episode, "4")
        XCTAssertEqual(Post.continueList(nested).first?.resume?.time, 60)
    }

    func testContinueWatchingAsServedToASubscriber() throws {
        // The shape of one entry of a live `continue_watching` response.
        let feed = JSON(["posts": [[
            "id": 283405, "title": ["english": "Reacher 2022"], "options": ["is_series": true],
            "data": ["display_time": "00:17:30", "episode": 4, "episode_title": "فصل 3 - قسمت 4", "id": 283405, "progress": "35.9836", "season": 3, "time": 1050],
        ]]] as [String: Any])
        let resume = try XCTUnwrap(Post.continueList(feed).first?.resume)
        XCTAssertEqual(resume.season, 3)
        XCTAssertEqual(resume.episode, "4")
        XCTAssertEqual(resume.time, 1050)
        XCTAssertEqual(resume.progress, 0.359836, accuracy: 0.0001)
        XCTAssertEqual(resume.remainingMinutes, 31, "the length is worked out from the position and the percentage")
    }

    func testNextEpisodeLabelPutsTheWeekdayFirst() {
        let post = Post.list(JSON(["posts": [["id": 1, "air": ["next_episode_date": ["g": "20 October 2026 Tuesday", "j": "۲۸ مهر ۱۴۰۵ سه شنبه"]]]]] as [String: Any])).first!
        XCTAssertEqual(post.nextEpisodeLabel(.fa), "سه شنبه ۲۸ مهر")
        XCTAssertEqual(post.nextEpisodeLabel(.en), "Tuesday 20 October")
    }

    func testUserShapes() {
        let bare = User(JSON(["user_session": "abc", "user_name": "sara", "user_expire": 1_800_000_000, "user_id": 12] as [String: Any]))
        XCTAssertEqual(bare?.token, "abc")
        XCTAssertEqual(bare?.id, "12")
        XCTAssertEqual(bare?.expiry, "2027-01-15")

        // What a subscriber's profile carries.
        let subscriber = User(JSON(["user_session": "s", "user_type": "golden", "user_avatar": "https://example.com/a.png",
                                    "user_expire": ["days": 641, "g": "2028-07-15 15:50:45", "j": "۱۴۰۷-۰۴-۲۵ ۱۵:۵۰:۴۵"]] as [String: Any]))
        XCTAssertEqual(subscriber?.expiry, "2028-07-15")
        XCTAssertEqual(subscriber?.expiryLocal, "۱۴۰۷-۰۴-۲۵")
        XCTAssertEqual(subscriber?.avatar?.lastPathComponent, "a.png")
        let midOctober2026 = Date(timeIntervalSince1970: 1_791_849_600)
        XCTAssertEqual(subscriber?.daysLeft(now: midOctober2026), 641)
        XCTAssertEqual(Loc(lang: .fa).subscriptionLine(subscriber!, now: midOctober2026), "اعتبار اشتراک تا ۱۴۰۷/۰۴/۲۵ · ۶۴۱ روز مانده")
        XCTAssertEqual(Loc(lang: .en).subscriptionLine(subscriber!, now: midOctober2026), "Subscription until 2028-07-15 · 641 days left")
        XCTAssertNil(Loc(lang: .fa).subscriptionLine(User(JSON(["token": "t"] as [String: Any]))!))

        let wrapped = User(JSON(["user": ["user_session": "zzz", "user_email": "a@b.c"]] as [String: Any]))
        XCTAssertEqual(wrapped?.name, "a@b.c")

        let tokenOnly = User(JSON(["token": "t1"] as [String: Any]))
        XCTAssertEqual(tokenOnly?.token, "t1")
        XCTAssertNil(User(JSON(["user_name": "x"] as [String: Any])))
        XCTAssertEqual(User(JSON(["user_name": "x"] as [String: Any]), fallbackToken: "keep")?.token, "keep")
    }
}
