package app.nama.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URI
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class SiteAPITests {
    @Test fun testSitePostsAreReadInTheAppShape() {
        val page = PostPage.fromSite(fixture("site_advanced_search")["result"])
        assertEquals(1, page.page)
        assertEquals(4, page.pages)
        assertEquals(2, page.posts.size)

        val film = page.posts[0]
        assertEquals(572188, film.id)
        assertEquals(Post.Kind.movie, film.kind)
        assertFalse(film.isSeries)
        assertTrue(film.hasStream)
        assertEquals("The Exorcist: Believer 2023", film.titleEnglish)
        assertEquals(2023, film.year)
        assertEquals(4.8, film.imdbScore!!, 0.0)
        assertEquals(40478, film.imdbVotes)
        assertEquals("tt12921446", film.imdbID)
        assertEquals(5.8, film.siteScore!!, 0.0)
        assertEquals(listOf("horror"), film.genres.map { it.slug })
        assertEquals("https://example.com/poster/30572188-m_30NAMA.webp?", film.poster.medium?.toString())
        assertEquals("https://example.com/poster/30572188-l_30NAMA.jpg?", film.poster.jpeg?.toString())
        assertEquals("https://example.com/cover/30572188_30NAMA.webp?", film.cover?.toString())

        val series = page.posts[1]
        assertTrue(series.isSeries)
        assertEquals(Post.Kind.series, series.kind)
        assertEquals(2022, series.year)
    }

    @Test fun testTheYearIsOnlyTakenFromTheEndOfTheTitle() {
        assertEquals(2017, Post.fromSite(obj("id" to 1, "title" to "Blade Runner 2049 2017"))?.year)
        assertNull(Post.fromSite(obj("id" to 2, "title" to "Untitled Tomb Raider Project"))?.year)
        assertNull(Post.fromSite(obj("title" to "No id 2020")))
    }

    @Test fun testFilmographyAsksTheSiteByImdbIdNewestFirst() {
        val q = SiteQuery.filmography("nm0000995", 2)
        assertEquals("advanced_search/page/2/orderby/year/order/desc", q.path)
        assertEquals(mapOf("parameters" to """{"cast":"nm0000995"}"""), q.form)
    }

    @Test fun testASiteThatGivesNoAnswerIsNotAskedAgainForAWhile() = runBlocking {
        // As where the site is filtered: no answer at all.
        val answering = java.util.concurrent.atomic.AtomicBoolean(false)
        val net = FakeNetwork { _, _ -> if (answering.get()) answer(200, """{"success":true,"msg":null,"result":{}}""") else answer(0, "") }
        val c = APIConfig(requestSpacing = 0.0, backoff = listOf(0.02, 0.02), site = SiteConfig(key = "site-key"))
        val api = APIClient(c, net.transport)
        try {
            api.site("schedule/type/series")
            fail("answered")
        } catch (e: APIError) {
            assertEquals(APIError.Unreachable, e)
        }
        // Not tried again, and the next page does not wait for it either.
        assertEquals(1, net.calls.size)
        try {
            api.site("toplist/list/imdb")
            fail("answered")
        } catch (e: APIError) {
            assertEquals(APIError.Unreachable, e)
        }
        assertEquals(1, net.calls.size)
        assertFalse(api.siteInReach)
        // Asked for again by the user, with the site now answering.
        answering.set(true)
        api.siteAgain()
        api.site("toplist/list/imdb")
        assertEquals(2, net.calls.size)
    }

    @Test fun testSiteCallsGoToTheSiteHostWithItsKeyAndAForm() = runBlocking {
        val net = FakeNetwork { _, _ -> answer(200, """{"success":true,"msg":null,"result":{"page":1,"pages":1,"posts":[]}}""") }
        val api = APIClient(APIConfig(requestSpacing = 0.0, site = SiteConfig(key = "site-key")), net.transport)
        assertTrue(api.hasSite)
        val q = SiteQuery.filmography("nm1", 1)
        api.site(q.path, q.form)
        val last = net.last!!
        assertEquals("interface.30nama.com", last.host)
        assertEquals("desc", last.action)
        assertEquals("site-key", last.headers["c-api-key"])
        assertEquals("Website", last.headers["c-platform"])
        assertEquals("parameters=%7B%22cast%22%3A%22nm1%22%7D", last.body)
    }

    @Test fun testWithoutAKeyTheSiteIsNotAsked() = runBlocking {
        val net = FakeNetwork { _, _ -> answer(200, "{}") }
        val api = APIClient(APIConfig(site = SiteConfig(key = null)), net.transport)
        assertFalse(api.hasSite)
        try {
            api.site("schedule/type/series")
            fail("expected unreachable")
        } catch (e: APIError) {
            assertEquals(APIError.Unreachable, e)
        }
        assertTrue(net.calls.isEmpty())
    }

    @Test fun testCastMembersKeepTheirImdbAndSiteIds() {
        val post = Post.from(fixture("single_movie")["result"])!!
        assertEquals("nm0000255", post.cast.first().imdb)
        assertEquals(151521, post.cast.first().siteID)
        assertNull(post.cast[1].siteID)
    }

    @Test fun testAPersonIsAskedBySiteIdAPageAtATime() {
        assertEquals("single/id/151521", SiteQuery.person(151521, 1))
        assertEquals("single/id/151521/page/3", SiteQuery.person(151521, 3))
    }

    @Test fun testAPersonsFactsBiographyAndTitles() {
        val person = PersonDetails.fromSite(fixture("site_person")["result"])!!
        assertEquals("Ben Affleck", person.name)
        assertEquals("Benjamin Géza Affleck-Boldt", person.fullName)
        assertEquals("بن افلک", person.persianName)
        assertEquals("بازیگر", person.profession)
        assertEquals("۲۴ مرداد ۱۳۵۱", person.birthdate)
        assertNull(person.deathdate)
        assertEquals("۵۴", person.age)
        assertEquals("https://www.imdb.com/name/nm0000255", person.imdb?.toString())
        assertEquals("https://example.com/person/30151521-m_30NAMA.webp?", person.picture?.medium?.toString())
        assertEquals(listOf("پیش زمینه", "پاراگراف نمونهٔ اول با «نقل» و ’.", "پاراگراف نمونهٔ دوم."), person.biography)
        assertEquals(1, person.titles.page)
        assertEquals(4, person.titles.pages)
        assertEquals(2, person.titles.posts.size)
    }

    @Test fun testSiteTextKeepsParagraphsAndDropsTheEditorsMarkup() {
        assertEquals(listOf("a&b", "line", "next ’"), SiteText.paragraphs("<p>a&amp;b</p><p>&nbsp;</p>line<br>next &#x2019;"))
        assertEquals(emptyList<String>(), SiteText.paragraphs(""))
    }

    @Test fun testNewestIsTheSitesListByPostId() {
        assertEquals("list/type/category/value/movie/page/2/orderby/id/order/desc/streamonly/yes/genre/all",
            SiteQuery.newest("movie", null, streamOnly = true, page = 2))
        assertEquals("list/type/category/value/anime/page/1/orderby/id/order/desc/streamonly/no/genre/action",
            SiteQuery.newest("anime", "action", streamOnly = false, page = 1))
    }

    @Test fun testTopListKeepsItsPlaces() {
        assertEquals("toplist/list/top-movie-30nama", SiteQuery.topList(TopList.siteMovies))
        val entries = TopList.entries(fixture("site_toplist")["result"])
        assertEquals(listOf(1, 2, 3), entries.map { it.rank })
        assertEquals(listOf(7583, 8863, 7143), entries.map { it.post.id })
        assertEquals(2014, entries.first().post.year)
    }

    // Not in the Swift tests: the site sometimes lists a title twice, and a list keyed by id must not.
    @Test fun testTheSitesListsKeepEachTitleOnce() {
        fun twice(name: String): Json {
            val result = fixture(name)["result"]
            val posts = result["posts"].array
            return obj("posts" to posts + posts, "page" to 1, "pages" to 1)
        }
        val page = PostPage.fromSite(twice("site_advanced_search"))
        assertEquals(page.posts.map { it.id }.distinct(), page.posts.map { it.id })
        assertEquals(PostPage.fromSite(fixture("site_advanced_search")["result"]).posts.size, page.posts.size)
        val top = TopList.entries(twice("site_toplist"))
        assertEquals("each keeps its first place", listOf(1, 2, 3), top.map { it.rank })
        assertEquals(listOf(7583, 8863, 7143), top.map { it.post.id })
        assertEquals(2, ArticlePage.fromSite(twice("site_articles")).articles.size)
    }

    @Test fun testTheScheduleSortsTitlesByTheSitesDays() {
        assertEquals("schedule/type/anime", SiteQuery.schedule(Schedule.Kind.anime))
        val week = Schedule.fromSite(fixture("site_schedule")["result"])
        assertEquals("a title listed twice is kept once", 3, week.entries.size)
        assertEquals(listOf(721396), week.on(0).map { it.id })
        assertEquals(listOf(721194), week.on(4).map { it.id })
        assertTrue(week.on(2).isEmpty())
    }

    @Test fun testTheSitesWeekStartsOnSaturday() {
        val saturday = Instant.parse("2026-10-17T00:00:00Z")
        val tuesday = Instant.parse("2026-10-20T00:00:00Z")
        assertEquals(0, Schedule.day(saturday, ZoneOffset.UTC))
        assertEquals(3, Schedule.day(tuesday, ZoneOffset.UTC))
        assertEquals(listOf(3, 4, 5, 6, 0, 1, 2), Schedule.week(tuesday, ZoneOffset.UTC))
        // Weekday names as Foundation lists them, from Sunday.
        val symbols = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1)
        assertEquals(DayOfWeek.SATURDAY, symbols[Schedule.symbolIndex(0)])
        assertEquals(DayOfWeek.SUNDAY, symbols[Schedule.symbolIndex(1)])
    }

    @Test fun testPersianDigitsBecomeLatin() {
        assertEquals("123", SiteText.latinDigits("۱۲۳"))
        assertEquals("45", SiteText.latinDigits("٤٥"))
        assertEquals("7a", SiteText.latinDigits("7a"))
    }

    @Test fun testPersianTextIsTidiedForReading() {
        assertEquals("در ۴ نوامبر، ۱۹۶۹ در «Dazed and Confused» بازی کرد.",
            SiteText.tidyPersian("""در 4 نوامبر ، 1969 در " Dazed and Confused" بازی کرد ."""))
        assertEquals("علاقه‌ی زیادی داشت و می‌یافت ولی نمی‌خواست",
            SiteText.tidyPersian("علاقه ی زیادی داشت و می یافت ولی نمی خواست"))
        assertEquals("گفت، سپس رفت", SiteText.tidyPersian("گفت،سپس رفت"))
    }

    @Test fun testABiographyFoldsAfterWholeParagraphsNotOnAHeading() {
        val long = "متن بلند. ".repeat(30)
        assertTrue(SiteText.isHeading("اوایل زندگی و حرفه"))
        assertFalse(SiteText.isHeading("او در تگزاس به دنیا آمد."))
        assertEquals(listOf("تیتر", long), SiteText.opening(listOf("تیتر", long, "تیتر دوم", long), letters = 200))
        assertEquals(listOf("کوتاه."), SiteText.opening(listOf("کوتاه."), letters = 200))
    }

    @Test fun testTheSitesProfileGivesTheNameTheAppAPILeavesOut() {
        assertEquals("profile/user/sample.user", SiteQuery.profile("sample.user"))
        assertEquals("profile/user/a%2Fb%20c", SiteQuery.profile("a/b c"))
        val both = SiteProfile.fromSite(obj("first_name" to "Sample", "last_name" to " Person "))
        assertEquals("Sample Person", both.fullName)
        assertEquals("Sample", both.firstName)
        assertEquals("Sample", SiteProfile.fromSite(obj("first_name" to "Sample", "last_name" to "")).fullName)
        assertNull(SiteProfile.fromSite(obj("first_name" to null)).fullName)
    }

    @Test fun testAHundredMovieLines() {
        assertEquals(100, MovieLines.all.size)
        assertEquals("no line twice", 100, MovieLines.all.map { it.text }.toSet().size)
        assertTrue(MovieLines.all.all { it.text.isNotEmpty() && it.film.isNotEmpty() && it.year in 1900..2010 && it.directors.isNotEmpty() })
        assertEquals("Chinatown", MovieLines.all[73].film)
        assertEquals(listOf("Roman Polanski"), MovieLines.all[73].directors)
        assertTrue(MovieLines.addressed.all { (rank, text) -> text.contains("{name}") && rank in 1..100 })
        val random = Random(7)
        assertTrue("no name: a line as it is", MovieLines.all.contains(MovieLines.pick(null, random = random)))
        assertTrue(MovieLines.all.contains(MovieLines.pick("  ", random = random)))
        repeat(60) {
            val line = MovieLines.pick("Sample", familyName = "Person", random = random)
            assertTrue(line.text, line.text.contains("Sample"))
            assertFalse(line.text, line.text.contains("{name}"))
            // The film is the one the line comes from.
            assertTrue(MovieLines.all.any { it.film == line.film && it.year == line.year })
        }
    }

    @Test fun testArticleListsAndTheirPictures() {
        assertEquals("article/type/review/page/3", SiteQuery.articles(Article.Kind.review, 3))
        assertEquals("single/id/721620", SiteQuery.article(721620))
        val page = ArticlePage.fromSite(fixture("site_articles")["result"])
        assertEquals(1182, page.pages)
        assertEquals("an entry with no id is left out", 2, page.articles.size)
        val first = page.articles[0]
        assertEquals(721620, first.id)
        assertEquals(Article.Kind.news, first.kind)
        assertEquals("عنوان خبر نمونه – اول", first.title)
        assertEquals("خلاصهٔ نمونه، با فاصلهٔ پیش از ویرگول", first.summary)
        assertEquals("۲۹ مهر ۱۴۰۵", first.date)
        assertEquals("https://example.com/attach/2026/10/sample-scaled.jpg", first.picture?.toString())
        assertEquals("the JPEG: the WebP is mostly missing", "https://example.com/attach/2026/10/sample-scaled-309x197.jpg", first.thumbnail?.toString())
        val review = page.articles[1]
        assertEquals(Article.Kind.review, review.kind)
        assertNull(review.summary)
        assertEquals("https://example.com/attach/2026/10/review.jpg", review.picture?.toString())
    }

    @Test fun testAnArticlesTextBecomesHeadingsParagraphsAndPictures() {
        val text = ArticleText.fromSite(fixture("site_article")["result"])!!
        assertEquals("عنوان خبر نمونه", text.article.title)
        assertEquals("the opening picture is the article's own and is shown above it; the video is left out", listOf(
            ArticleBlock.Paragraph("پاراگراف اول با «نقل» و پیوند به Sample در سال ۲۰۲۶."),
            ArticleBlock.Heading("تیتر میانی"),
            ArticleBlock.Picture(URI("https://example.com/attach/2026/10/big.jpg")),
            ArticleBlock.Paragraph("پاراگراف دوم کنار عکس."),
            ArticleBlock.Picture(URI("https://example.com/attach/2026/10/alone.png")),
        ), text.blocks)
    }

    @Test fun testALinesFilmIsFoundOn30nama() {
        val line = MovieLines.all[73]   // Chinatown, 1974
        assertEquals(listOf("Chinatown", "Chinatown 1974"), line.searchQueries)
        fun post(id: Int, title: String, series: Boolean = false): Post =
            Post.from(obj("id" to id, "title" to mapOf("english" to title), "options" to mapOf("is_series" to series)))!!
        // The year comes from the info block in real answers; here from the title's end is enough.
        val withYears = listOf(post(1, "Chinatown 2019"), post(2, "Chinatown 1974", series = true), post(3, "Chinatown 1974"), post(4, "Chinatown Nights 1974"))
            .map { it.copy(year = it.titleEnglish!!.takeLast(4).toInt()) }
        assertEquals("the film, of that year, not the series or a namesake", 3, line.match(withYears)?.id)
        assertNull(line.match(listOf(withYears[0])))
    }
}
