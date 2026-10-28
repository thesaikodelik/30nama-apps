package app.nama.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ModelTests {
    @Test fun testDownloadOptionsForAFilmAndForASeries() {
        val film = DownloadOptions.from(parse("""
        {"download":[
          {"dl":"https://example.com/files/Some.Film.2020.1080p.x265.mkv","encoder":"30nama","id":239523688524,"quality":"x265 BluRay 1080p",
           "size":"2.1 GB","tags":["x265"],"persian_subtitle":"https://example.com/sub/1","source":null},
          {"dl":null,"quality":"broken"}
        ]}
        """))
        assertEquals("an entry without an address is not an offer", 1, film.files.size)
        assertTrue(film.packs.isEmpty())
        assertEquals("Some.Film.2020.1080p.x265.mkv", film.files[0].fileName)
        assertEquals("x265 BluRay 1080p · 30nama · 2.1 GB", film.files[0].summary)
        assertNotNull(film.files[0].subtitle)

        val series = DownloadOptions.from(parse("""
        {"download":{
          "s2":[{"id":8590,"season":2,"season_int":2,"quality":"x265 BluRay 1080p","size":"333 MB","encoder":"30nama","tags":["x265"],
                 "link":[{"dl":"https://example.com/s2/Show.S02E01.mkv","episode":1,"id":1,"persian_subtitle":null},
                         {"dl":"https://example.com/s2/Show.S02E02.mkv","episode":2,"id":2,"persian_subtitle":null}]}],
          "s1":[{"id":8589,"season":1,"season_int":1,"quality":"WEB-DL 720p","size":"200 MB","encoder":"30nama","tags":[],
                 "link":[{"dl":"https://example.com/s1/Show%20S01E00%20Special.mkv","episode":"Special","id":3,"persian_subtitle":"https://example.com/sub/3"}]},
                {"id":1,"season":1,"season_int":1,"quality":"empty","link":[]}]
        }}
        """))
        assertTrue(series.files.isEmpty())
        assertEquals(listOf(1, 2), series.seasons)
        assertEquals("by season; a pack with no files is dropped", listOf("WEB-DL 720p", "x265 BluRay 1080p"), series.packs.map { it.quality })
        assertEquals(listOf("1", "2"), series.packs[1].files.map { it.episode })
        assertEquals("Special", series.packs[0].files[0].episode)
        assertEquals("percent escapes are undone", "Show S01E00 Special.mkv", series.packs[0].files[0].fileName)
        assertEquals(2, series.packs[1].files[0].season)
        assertTrue(DownloadOptions.from(obj("download" to null)).isEmpty)

        // The server's ids run to eighteen digits; read as numbers, neighbours come out the same.
        val long = DownloadOptions.from(parse("""
        {"download":{"s1":[
          {"id":1,"season_int":1,"quality":"1080p","link":[
            {"dl":"https://example.com/a/Show.S01E01.mkv","episode":1,"id":278294959021668101},
            {"dl":"https://example.com/a/Show.S01E02.mkv","episode":2,"id":278294959021668102},
            {"dl":"https://example.com/a/Show.S01E03.mkv","episode":3,"id":278294959021668103}]},
          {"id":2,"season_int":1,"quality":"720p","link":[
            {"dl":"https://example.com/b/Show.S01E01.mkv","episode":1,"id":278294959021668201},
            {"dl":"https://example.com/b/Show.S01E10.mkv","episode":10,"id":278294959021668210}]}
        ]}}
        """))
        assertEquals("every file keeps an id of its own", 3, long.packs[0].files.map { it.id }.toSet().size)
        assertEquals("what the season has across all packs, in number order", listOf("1", "2", "3", "10"), long.episodes(1))
        assertEquals("this pack lacks two of the four", 2, long.packs.firstOrNull { it.quality == "720p" }?.files?.size)
    }

    @Test fun testFilterBuildsTheParametersAdvancedSearchTakes() {
        val options = FilterOptions.from(parse("""
        {"genre":[{"name":"Drama","value":"drama"},{"name":"All","value":""}],
         "country":[{"name":"United States","value":"US"}],
         "imdb_rate":[{"name":"Above 8.5","value":8.5},{"name":"Above 8.0","value":8},{"name":"Under 5.0","value":-5}],
         "min_year":1890,"max_year":2026}
        """))
        assertEquals("the server's own All entry is not a choice", listOf("drama"), options.genres.map { it.value })
        assertEquals("only floors", listOf("8.5", "8"), options.scores.map { it.value })
        assertEquals(2020, options.decades.first())
        assertEquals(1950, options.decades.last())

        assertTrue(Filter().isEmpty)
        val filter = Filter(genre = "drama", country = "US", score = "8.5", decade = 2010)
        assertFalse(filter.isEmpty)
        assertEquals("""{"country":"US","genre":["drama"],"imdb_rate":8.5,"max_year":2019,"min_year":2010,"stream":"true","type":["movie"]}""",
            filter.parameters(type = "movie"))
        assertEquals("""{"imdb_rate":8,"stream":"true"}""", Filter(score = "8").parameters(type = null))
    }

    @Test fun testUpcomingEpisodesAreSortedSoonestFirst() {
        fun series(id: Int, next: String?): Post {
            val air = linkedMapOf<String, Any?>("air_status" to "Returning")
            if (next != null) air["next_episode_date"] = mapOf("g" to next, "j" to "۱۴۰۵-۰۱-۰۱")
            return Post.from(obj("id" to id, "title" to mapOf("english" to "Show $id"), "options" to mapOf("is_series" to true), "air" to air))!!
        }
        val now = Instant.parse("2026-10-13T12:00:00Z")
        // The server writes the day out; the plain form is accepted as well.
        val posts = listOf(series(1, "2 November 2026 Monday"), series(2, null), series(3, "13 October 2026 Tuesday"),
            series(4, "1 September 2026 Tuesday"), series(5, "2026-10-20"), series(3, "13 October 2026 Tuesday"))
        assertEquals("today counts, the past and the undated do not, nothing twice", listOf(3, 5, 1), Post.upcoming(posts, now).map { it.id })
    }

    @Test fun testBackdropsAreTheBestLiveActionTitlesWithAWidePicture() {
        fun entry(id: Int, imdb: Double, type: String = "movie", genre: String = "drama", cover: Boolean = true): Map<String, Any?> {
            val image = linkedMapOf<String, Any?>("poster" to mapOf("webp" to mapOf("large" to "https://example.com/$id.webp")))
            if (cover) image["cover"] = mapOf("webp" to "https://example.com/$id-wide.webp")
            return mapOf("id" to id, "title" to mapOf("english" to "Title $id"), "options" to mapOf("title_type" to type),
                "genre" to listOf(mapOf("name" to genre.replaceFirstChar { it.uppercase() }, "slug" to genre)), "score" to mapOf("imdb" to mapOf("score" to imdb)), "image" to image)
        }
        val feed = HomeFeed.from(obj(
            "hero_header" to mapOf("posts" to listOf(entry(1, 8.5, type = "anime"))),
            "movies" to mapOf("posts" to listOf(entry(2, 7.1), entry(3, 9.0), entry(4, 9.5, cover = false), entry(5, 8.8, genre = "animation"))),
            "series" to mapOf("posts" to listOf(entry(6, 8.2), entry(3, 9.0))),
        ))
        assertEquals("best first; no anime, no animation, nothing without a wide picture, nothing twice", listOf(3, 6, 2), feed.backdrops().map { it.id })
        assertEquals(listOf(3, 6), feed.backdrops(limit = 2).map { it.id })
        val cartoons = HomeFeed.from(obj("hero_header" to mapOf("posts" to listOf(entry(1, 8.5, type = "anime")))))
        assertEquals("with nothing else to show, animation is better than a blank screen", listOf(1), cartoons.backdrops().map { it.id })
    }

    @Test fun testHomeFeedFromLiveResponse() {
        val feed = HomeFeed.from(fixture("home_stream")["result"])
        assertEquals(3, feed.heroes.size)
        assertEquals(listOf("suggested", "movies", "series", "anime"), feed.sections.map { it.key })

        val hero = feed.heroes.first()
        assertEquals(469773, hero.id)
        assertEquals(Post.Kind.anime, hero.kind)
        assertTrue(hero.isSeries)
        assertEquals(3, hero.seasons)
        assertEquals(24, hero.runtimeMinutes)
        assertEquals(8.5, hero.imdbScore!!, 0.0)
        assertEquals("tt14976292", hero.imdbID)
        assertEquals("bRIr1_%M~qxtyXtR%hRjIUxu", hero.poster.blurhash)
        assertEquals("cdn-world.hana-api.com", hero.cover?.host)
        assertFalse(hero.genres.isEmpty())
    }

    @Test fun testTitleDropsItsOwnYearOnly() {
        val hero = HomeFeed.from(fixture("home_stream")["result"]).heroes.first()
        assertEquals("Link Click 2021", hero.titleEnglish)
        assertEquals("Link Click", hero.title(Lang.en))
        assertEquals("falls back to English when there is no local title", "Link Click", hero.title(Lang.fa))
        assertNull(hero.alternateTitle(Lang.fa))

        val blade = Post.from(obj("id" to 1, "title" to mapOf("english" to "Blade Runner 2049", "local" to "بلید رانر"), "info" to mapOf("year" to 2017)))!!
        assertEquals("Blade Runner 2049", blade.title(Lang.en))
        assertEquals("بلید رانر", blade.title(Lang.fa))
        assertEquals("Blade Runner 2049", blade.alternateTitle(Lang.fa))

        val war = Post.from(obj("id" to 2, "title" to mapOf("english" to "1917 2019"), "info" to mapOf("year" to 2019)))!!
        assertEquals("1917", war.title(Lang.en))

        val local = Post.from(obj("id" to 3, "title" to mapOf("english" to "1917 2019", "local" to "۱۹۱۷ 2019"), "info" to mapOf("year" to 2019)))!!
        assertEquals("local titles carry the year too", "۱۹۱۷", local.title(Lang.fa))
    }

    @Test fun testSingleCarriesCastAndCrew() {
        val movie = Post.from(fixture("single_movie")["result"])!!
        assertEquals(Post.Kind.movie, movie.kind)
        assertEquals("Ben Affleck", movie.cast.first().name)
        assertEquals("Milo Bradford", movie.cast.first().role)
        assertNotNull(movie.cast.first().poster)
        assertEquals(listOf("Ben Affleck"), movie.directors)
        assertFalse(movie.writers.isEmpty())
        assertEquals("United States", movie.countries.first())
        assertNull(movie.resume)

        val series = Post.from(fixture("single_series")["result"])!!
        assertNotNull(series.airStatus)
        assertNotNull(series.nextEpisodeDate.jalali)
        // What 30nama's own apps show beside the scores.
        assertEquals(7639, series.imdbVotes)
        assertEquals(195, series.inWatchlists)
        assertEquals(184, series.inFavorites)
        assertNull("sent as an empty string: not on the list", series.imdbTop250)
        assertEquals("Chinese", series.otherLanguages.first())
        assertFalse("the primary language is not repeated", series.otherLanguages.contains("Mandarin"))
        assertEquals("اضافه شدن قسمت جدید", series.latestUpdate?.title)
        assertEquals("اپیزود 10 فصل 3", series.latestUpdate?.content)
        assertEquals("the whole history, newest first", 2, series.updates.size)
        assertEquals("اپیزود 9 فصل 3", series.updates.last().content)
        assertEquals("۱۴۰۵-۰۷-۱۷", series.updates.first().day(persian = true))
        assertEquals("2026-10-09", series.updates.first().day(persian = false))
        assertEquals(12, Post.from(obj("id" to 1, "score" to mapOf("imdb" to mapOf("top_250" to "12"))))?.imdbTop250)
    }

    @Test fun testCriticScores() {
        val dune = Post.from(obj("id" to 9, "score" to mapOf(
            "imdb" to mapOf("score" to 8.0), "rottentomatoes" to mapOf("link" to "https://www.rottentomatoes.com/m/dune_2021", "score" to 85),
            "metacritic" to mapOf("score" to 74), "mydramalist" to mapOf("score" to 0),
        )))!!
        assertEquals(85, dune.rottenTomatoes)
        assertEquals(74, dune.metacritic)
        assertNull("a zero score means the site has none", dune.fanScore)

        val hero = HomeFeed.from(fixture("home_stream")["result"]).heroes.first()
        assertEquals("MAL", hero.fanScore?.source)
        assertEquals(8.72, hero.fanScore!!.value, 0.0)
        assertNull(hero.rottenTomatoes)
    }

    @Test fun testPosterPicksSmallestSufficientVariant() {
        val poster = HomeFeed.from(fixture("home_stream")["result"]).heroes.first().poster
        assertTrue(poster.url(200.0)!!.toString().contains("-l_"))
        assertTrue(poster.url(400.0)!!.toString().contains("-b_"))
        assertTrue("largest when nothing is big enough", poster.url(900.0)!!.toString().contains("-b_"))
    }

    @Test fun testMenuAndSearchPage() {
        val menu = Menu.from(fixture("menu")["result"])
        assertEquals("movies", menu.categories.first().slug)
        assertEquals(3, menu.genres.size)
        assertNotNull(menu.genres.first().thumb)

        val page = PostPage.from(fixture("search")["result"])
        assertEquals(2, page.posts.size)
        assertEquals(4, page.pages)
        assertEquals(86, page.total)
    }

    @Test fun testSeriesStreamIsSortedNumerically() {
        val stream = StreamResult.from(fixture("stream_series")["result"])
        assertEquals(listOf("1x2", "1x10", "1x11,12", "2x1"), stream.items.map { "${it.season}x${it.episodeRaw ?: ""}" })
        assertEquals(listOf(1, 2), stream.seasons)
        assertEquals("102", stream.resume?.fileID)
        assertEquals(0, stream.startIndex)
        assertEquals(34, stream.resume?.remainingMinutes)

        val first = stream.items[0]
        assertEquals(44, first.durationMinutes)
        assertEquals(90.0, first.introEnd!!, 0.0)
        assertEquals(2470.0, first.creditsStart!!, 0.0)
        assertEquals("fa.vtt", first.subtitles["fa"]?.lastPathComponent)
        assertEquals("en.vtt", first.subtitles["en"]?.lastPathComponent)
        assertEquals("11–12", Episode.label(stream.items[2].episodeRaw, 3))
    }

    @Test fun testStreamAsServedToASubscriber() {
        val stream = StreamResult.from(fixture("stream_subscriber")["result"])
        assertEquals(listOf(1, 2, 3, 4, 5, 6), stream.items.map { it.episodeNumber })
        assertEquals("resumes the episode named by continue_watching", 5, stream.startIndex)
        assertEquals(60.0, stream.resume!!.time, 0.0)
        assertEquals("no duration: falls back to the reported percentage", 0.01, stream.resume!!.progress, 0.0001)
        assertNull(stream.resume?.remainingMinutes)

        val item = stream.items[0]
        assertEquals("182990", item.fileID)
        assertEquals(listOf("خودکار", "720p", "1080p"), item.qualities.map { it.label })
        assertEquals("1080.m3u8", item.quality(preferAdaptive = false)?.url?.lastPathComponent)
        assertEquals("auto.m3u8", item.quality(preferAdaptive = true)?.url?.lastPathComponent)
        val full = item.qualities.firstOrNull { it.label == "1080p" }!!
        assertEquals("1080-2ch.m3u8", full.stereo?.lastPathComponent)
        assertEquals("1080-6ch.m3u8", full.surround?.lastPathComponent)
        assertEquals("1080-2ch.m3u8", full.url(AudioMix.stereo).lastPathComponent)
        assertEquals("1080.m3u8", full.url(AudioMix.original).lastPathComponent)
        assertEquals(listOf(AudioMix.stereo, AudioMix.surround, AudioMix.original), full.mixes)
        assertEquals(403.0, item.introStart!!, 0.0)
        assertEquals(413.0, item.introEnd!!, 0.0)
        assertNull(item.recapEnd)
        assertEquals(3328.0, item.creditsStart!!, 0.0)
        assertEquals(2, item.subtitles.size)
    }

    @Test fun testQualityPreference() {
        val item = StreamResult.from(fixture("stream_series")["result"]).items[0]
        assertEquals(listOf("Auto", "720p", "1080p"), item.qualities.map { it.label })
        assertEquals("URL is trimmed", "https://example.com/1080.m3u8", item.quality(preferAdaptive = false)?.url?.toString())
        assertEquals("Auto", item.quality(preferAdaptive = true)?.label)

        val movie = StreamResult.from(fixture("stream_movie")["result"])
        assertEquals(1, movie.items.size)
        assertEquals("numeric ids are read as strings", "9001", movie.items[0].fileID)
        assertEquals("movie.m3u8", movie.items[0].quality(preferAdaptive = false)?.url?.lastPathComponent)
        assertNull(movie.resume)
    }

    @Test fun testSkipWindows() {
        val item = StreamResult.from(fixture("stream_series")["result"]).items[0]
        assertNull(item.skipTarget(2.0))
        assertEquals(90.0, item.skipTarget(30.0)!!.to, 0.0)
        assertEquals(true, item.skipTarget(30.0)?.isIntro)
        assertNull("no button in the last second", item.skipTarget(89.5))
        assertNull(item.skipTarget(200.0))
    }

    @Test fun testContinueWatchingListShapes() {
        val wrapped = obj("posts" to listOf(mapOf("post" to mapOf("id" to 5), "continue_watching" to mapOf("file_id" to 9, "time" to "120", "duration" to 1200, "season" to 2, "episode" to 3))))
        val p = Post.continueList(wrapped)
        assertEquals(5, p.first().id)
        assertEquals(0.1, p.first().resume!!.progress, 0.0)
        assertEquals(2, p.first().resume?.season)

        val nested = Json.of(listOf(mapOf("id" to 6, "continue_watching" to mapOf("info" to mapOf("file_id" to "x", "season" to "1", "episode" to "4"), "position" to 60, "duration" to 600))))
        assertEquals("4", Post.continueList(nested).first().resume?.episode)
        assertEquals(60.0, Post.continueList(nested).first().resume!!.time, 0.0)
    }

    @Test fun testContinueWatchingAsServedToASubscriber() {
        // The shape of one entry of a live `continue_watching` response.
        val feed = obj("posts" to listOf(mapOf(
            "id" to 283405, "title" to mapOf("english" to "Reacher 2022"), "options" to mapOf("is_series" to true),
            "data" to mapOf("display_time" to "00:17:30", "episode" to 4, "episode_title" to "فصل 3 - قسمت 4", "id" to 283405, "progress" to "35.9836", "season" to 3, "time" to 1050),
        )))
        val resume = Post.continueList(feed).first().resume!!
        assertEquals(3, resume.season)
        assertEquals("4", resume.episode)
        assertEquals(1050.0, resume.time, 0.0)
        assertEquals(0.359836, resume.progress, 0.0001)
        assertEquals("the length is worked out from the position and the percentage", 31, resume.remainingMinutes)
    }

    @Test fun testNextEpisodeLabelPutsTheWeekdayFirst() {
        val post = Post.list(obj("posts" to listOf(mapOf("id" to 1, "air" to mapOf("next_episode_date" to mapOf("g" to "20 October 2026 Tuesday", "j" to "۲۸ مهر ۱۴۰۵ سه شنبه")))))).first()
        assertEquals("سه شنبه ۲۸ مهر", post.nextEpisodeLabel(Lang.fa))
        assertEquals("Tuesday 20 October", post.nextEpisodeLabel(Lang.en))
    }

    @Test fun testUserShapes() {
        val bare = User.from(obj("user_session" to "abc", "user_name" to "sara", "user_expire" to 1_800_000_000, "user_id" to 12))
        assertEquals("abc", bare?.token)
        assertEquals("12", bare?.id)
        assertEquals("2027-01-15", bare?.expiry)

        // What a subscriber's profile carries.
        val subscriber = User.from(obj("user_session" to "s", "user_type" to "golden", "user_avatar" to "https://example.com/a.png",
            "user_expire" to mapOf("days" to 641, "g" to "2028-07-15 15:50:45", "j" to "۱۴۰۷-۰۴-۲۵ ۱۵:۵۰:۴۵")))!!
        assertEquals("2028-07-15", subscriber.expiry)
        assertEquals("۱۴۰۷-۰۴-۲۵", subscriber.expiryLocal)
        assertEquals("a.png", subscriber.avatar?.lastPathComponent)
        val midOctober2026 = Instant.ofEpochSecond(1_791_849_600)
        assertEquals(641, subscriber.daysLeft(midOctober2026))
        assertEquals("اعتبار اشتراک تا ۱۴۰۷/۰۴/۲۵ · ۶۴۱ روز مانده", Loc(Lang.fa).subscriptionLine(subscriber, midOctober2026))
        assertEquals("Subscription until 2028-07-15 · 641 days left", Loc(Lang.en).subscriptionLine(subscriber, midOctober2026))
        assertNull(Loc(Lang.fa).subscriptionLine(User.from(obj("token" to "t"))!!))

        val wrapped = User.from(obj("user" to mapOf("user_session" to "zzz", "user_email" to "a@b.c")))
        assertEquals("a@b.c", wrapped?.name)

        val tokenOnly = User.from(obj("token" to "t1"))
        assertEquals("t1", tokenOnly?.token)
        assertNull(User.from(obj("user_name" to "x")))
        assertEquals("keep", User.from(obj("user_name" to "x"), fallbackToken = "keep")?.token)
    }
}
