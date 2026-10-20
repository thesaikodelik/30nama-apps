using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class SiteApiTests
{
    [Fact]
    public void SitePostsAreReadInTheAppShape()
    {
        var page = SitePosts.Page(Fixtures.Load("site_advanced_search")["result"]);
        Assert.Equal(1, page.Page);
        Assert.Equal(4, page.Pages);
        Assert.Equal(2, page.Posts.Count);

        var film = page.Posts[0];
        Assert.Equal(572188, film.Id);
        Assert.Equal(Post.Kinds.Movie, film.Kind);
        Assert.False(film.IsSeries);
        Assert.True(film.HasStream);
        Assert.Equal("The Exorcist: Believer 2023", film.TitleEnglish);
        Assert.Equal(2023, film.Year);
        Assert.Equal(4.8, film.ImdbScore);
        Assert.Equal(40478, film.ImdbVotes);
        Assert.Equal("tt12921446", film.ImdbId);
        Assert.Equal(5.8, film.SiteScore);
        Assert.Equal(["horror"], film.Genres.Select(g => g.Slug));
        Assert.Equal("https://example.com/poster/30572188-m_30NAMA.webp?", film.Poster.Medium?.OriginalString);
        Assert.Equal("https://example.com/poster/30572188-l_30NAMA.jpg?", film.Poster.Jpeg?.OriginalString);
        Assert.Equal("https://example.com/cover/30572188_30NAMA.webp?", film.Cover?.OriginalString);

        var series = page.Posts[1];
        Assert.True(series.IsSeries);
        Assert.Equal(Post.Kinds.Series, series.Kind);
        Assert.Equal(2022, series.Year);
    }

    [Fact]
    public void TheYearIsOnlyTakenFromTheEndOfTheTitle()
    {
        Assert.Equal(2017, SitePosts.Parse(Json.Parse("""{"id":1,"title":"Blade Runner 2049 2017"}"""))?.Year);
        Assert.Null(SitePosts.Parse(Json.Parse("""{"id":2,"title":"Untitled Tomb Raider Project"}"""))?.Year);
        Assert.Null(SitePosts.Parse(Json.Parse("""{"title":"No id 2020"}""")));
    }

    [Fact]
    public void FilmographyAsksTheSiteByImdbIdNewestFirst()
    {
        var (path, form) = SiteQuery.Filmography("nm0000995", 2);
        Assert.Equal("advanced_search/page/2/orderby/year/order/desc", path);
        Assert.Equal("""{"cast":"nm0000995"}""", form["parameters"]);
        Assert.Single(form);
    }

    [Fact]
    public async Task SiteCallsGoToTheSiteHostWithItsKeyAndAForm()
    {
        var net = new FakeNetwork((_, _) => (200, """{"success":true,"msg":null,"result":{"page":1,"pages":1,"posts":[]}}""", []));
        var api = new ApiClient(new ApiConfig { RequestSpacing = TimeSpan.Zero, SiteKey = "site-key" }, net.Transport);
        Assert.True(api.HasSite);
        var (path, form) = SiteQuery.Filmography("nm1", 1);
        await api.SiteAsync(path, form);
        var last = net.Last!;
        Assert.Equal("interface.30nama.com", last.Host);
        Assert.Equal("desc", last.Action);
        Assert.Equal("site-key", last.Headers["c-api-key"]);
        Assert.Equal("Website", last.Headers["c-platform"]);
        Assert.Equal("parameters=%7B%22cast%22%3A%22nm1%22%7D", last.Body);
    }

    [Fact]
    public async Task WithoutAKeyTheSiteIsNotAsked()
    {
        var net = new FakeNetwork((_, _) => (200, "{}", []));
        var api = new ApiClient(new ApiConfig { SiteKey = null }, net.Transport);
        Assert.False(api.HasSite);
        var e = await Assert.ThrowsAsync<ApiException>(() => api.SiteAsync("schedule/type/series"));
        Assert.Equal(ApiException.Kinds.Unreachable, e.Kind);
        Assert.Empty(net.Calls);
    }

    [Fact]
    public void CastMembersKeepTheirImdbAndSiteIds()
    {
        var post = Post.Parse(Fixtures.Load("single_movie")["result"])!;
        Assert.Equal("nm0000255", post.Cast[0].Imdb);
        Assert.Equal(151521, post.Cast[0].SiteId);
        Assert.Null(post.Cast[1].SiteId);
    }

    [Fact]
    public void APersonIsAskedBySiteIdAPageAtATime()
    {
        Assert.Equal("single/id/151521", SiteQuery.Person(151521, 1));
        Assert.Equal("single/id/151521/page/3", SiteQuery.Person(151521, 3));
    }

    [Fact]
    public void APersonsFactsBiographyAndTitles()
    {
        var person = PersonDetails.Parse(Fixtures.Load("site_person")["result"])!;
        Assert.Equal("Ben Affleck", person.Name);
        Assert.Equal("Benjamin Géza Affleck-Boldt", person.FullName);
        Assert.Equal("بن افلک", person.PersianName);
        Assert.Equal("بازیگر", person.Profession);
        Assert.Equal("۲۴ مرداد ۱۳۵۱", person.Birthdate);
        Assert.Null(person.Deathdate);
        Assert.Equal("۵۴", person.Age);
        Assert.Equal("https://www.imdb.com/name/nm0000255", person.Imdb?.OriginalString);
        Assert.Equal("https://example.com/person/30151521-m_30NAMA.webp?", person.Picture?.Medium?.OriginalString);
        Assert.Equal(["پیش زمینه", "پاراگراف نمونهٔ اول با «نقل» و ’.", "پاراگراف نمونهٔ دوم."], person.Biography);
        Assert.Equal(1, person.Titles.Page);
        Assert.Equal(4, person.Titles.Pages);
        Assert.Equal(2, person.Titles.Posts.Count);
    }

    [Fact]
    public void SiteTextKeepsParagraphsAndDropsTheEditorsMarkup()
    {
        Assert.Equal(["a&b", "line", "next ’"], SiteText.Paragraphs("<p>a&amp;b</p><p>&nbsp;</p>line<br>next &#x2019;"));
        Assert.Empty(SiteText.Paragraphs(""));
    }

    [Fact]
    public void ABiographysHeadingsAreShortLinesThatAreNotSentences()
    {
        Assert.True(SiteText.IsHeading("پیش زمینه"));
        Assert.False(SiteText.IsHeading("او در بوستون به دنیا آمد."));
        Assert.False(SiteText.IsHeading(new string('ا', 60)));
    }

    [Fact]
    public void AFoldedBiographyShowsWholeParagraphsToAboutEightHundredLetters()
    {
        var sentence = new string('ا', 299) + ".";
        string[] bio = ["آغاز", sentence, sentence, sentence, "میانه", sentence];
        Assert.True(SiteText.IsLong(bio));
        Assert.Equal(bio[..4], SiteText.Opening(bio));
        // Past the letters on a heading: the paragraph under it comes too.
        var half = new string('ب', 399) + ".";
        string[] headed = [half, half, "میانه", sentence, sentence];
        Assert.Equal(headed[..4], SiteText.Opening(headed));
        Assert.False(SiteText.IsLong(["کوتاه."]));
    }

    [Fact]
    public void NewestIsTheSitesListByPostId()
    {
        Assert.Equal("list/type/category/value/movie/page/2/orderby/id/order/desc/streamonly/yes/genre/all",
                     SiteQuery.Newest("movie", null, true, 2));
        Assert.Equal("list/type/category/value/anime/page/1/orderby/id/order/desc/streamonly/no/genre/action",
                     SiteQuery.Newest("anime", "action", false, 1));
    }

    [Fact]
    public void TopListKeepsItsPlaces()
    {
        Assert.Equal("toplist/list/top-movie-30nama", SiteQuery.TopList(TopLists.SiteMovies));
        var entries = TopListEntry.Parse(Fixtures.Load("site_toplist")["result"]);
        Assert.Equal([1, 2, 3], entries.Select(e => e.Rank));
        Assert.Equal([7583, 8863, 7143], entries.Select(e => e.Post.Id));
        Assert.Equal(2014, entries[0].Post.Year);
    }

    [Fact]
    public void TheScheduleSortsTitlesByTheSitesDays()
    {
        Assert.Equal("schedule/type/anime", SiteQuery.Schedule(Schedule.Kinds.Anime));
        var week = new Schedule(Fixtures.Load("site_schedule")["result"]);
        // A title listed twice is kept once.
        Assert.Equal(3, week.Entries.Count);
        Assert.Equal([721396], week.On(0).Select(p => p.Id));
        Assert.Equal([721194], week.On(4).Select(p => p.Id));
        Assert.Empty(week.On(2));
    }

    [Fact]
    public void TheSitesWeekStartsOnSaturday()
    {
        var saturday = new DateTime(2026, 10, 17);
        var tuesday = new DateTime(2026, 10, 20);
        Assert.Equal(0, Schedule.Day(saturday));
        Assert.Equal(3, Schedule.Day(tuesday));
        Assert.Equal([3, 4, 5, 6, 0, 1, 2], Schedule.Week(tuesday));
        Assert.Equal(DayOfWeek.Saturday, Schedule.Weekday(0));
        Assert.Equal(DayOfWeek.Sunday, Schedule.Weekday(1));
    }

    [Fact]
    public void PersianDigitsBecomeLatin()
    {
        Assert.Equal("123", SiteText.LatinDigits("۱۲۳"));
        Assert.Equal("45", SiteText.LatinDigits("٤٥"));
        Assert.Equal("7a", SiteText.LatinDigits("7a"));
    }

    [Fact]
    public void PersianTextIsTidiedForReading()
    {
        Assert.Equal("در ۴ نوامبر، ۱۹۶۹ در «Dazed and Confused» بازی کرد.",
                     SiteText.TidyPersian("در 4 نوامبر ، 1969 در \" Dazed and Confused\" بازی کرد ."));
        Assert.Equal("علاقه‌ی زیادی داشت و می‌یافت ولی نمی‌خواست",
                     SiteText.TidyPersian("علاقه ی زیادی داشت و می یافت ولی نمی خواست"));
        Assert.Equal("گفت، سپس رفت", SiteText.TidyPersian("گفت،سپس رفت"));
    }
}
