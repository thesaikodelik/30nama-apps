using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

// Ported from Tests/NamaCoreTests/ModelTests.swift. Inline values are written as JSON text.
public class ModelTests
{
    private static Json J(string text) => Json.Parse(text);

    [Fact]
    public void DownloadOptionsForAFilmAndForASeries()
    {
        var film = new DownloadOptions(J("""
        {"download":[
          {"dl":"https://example.com/files/Some.Film.2020.1080p.x265.mkv","encoder":"30nama","id":239523688524,"quality":"x265 BluRay 1080p",
           "size":"2.1 GB","tags":["x265"],"persian_subtitle":"https://example.com/sub/1","source":null},
          {"dl":null,"quality":"broken"}
        ]}
        """));
        Assert.Single(film.Files); // an entry without an address is not an offer
        Assert.Empty(film.Packs);
        Assert.Equal("Some.Film.2020.1080p.x265.mkv", film.Files[0].FileName);
        Assert.Equal("x265 BluRay 1080p · 30nama · 2.1 GB", film.Files[0].Summary);
        Assert.NotNull(film.Files[0].Subtitle);

        var series = new DownloadOptions(J("""
        {"download":{
          "s2":[{"id":8590,"season":2,"season_int":2,"quality":"x265 BluRay 1080p","size":"333 MB","encoder":"30nama","tags":["x265"],
                 "link":[{"dl":"https://example.com/s2/Show.S02E01.mkv","episode":1,"id":1,"persian_subtitle":null},
                         {"dl":"https://example.com/s2/Show.S02E02.mkv","episode":2,"id":2,"persian_subtitle":null}]}],
          "s1":[{"id":8589,"season":1,"season_int":1,"quality":"WEB-DL 720p","size":"200 MB","encoder":"30nama","tags":[],
                 "link":[{"dl":"https://example.com/s1/Show%20S01E00%20Special.mkv","episode":"Special","id":3,"persian_subtitle":"https://example.com/sub/3"}]},
                {"id":1,"season":1,"season_int":1,"quality":"empty","link":[]}]
        }}
        """));
        Assert.Empty(series.Files);
        Assert.Equal([1, 2], series.Seasons);
        Assert.Equal(["WEB-DL 720p", "x265 BluRay 1080p"], series.Packs.Select(p => p.Quality)); // by season; a pack with no files is dropped
        Assert.Equal(["1", "2"], series.Packs[1].Files.Select(f => f.Episode));
        Assert.Equal("Special", series.Packs[0].Files[0].Episode);
        Assert.Equal("Show S01E00 Special.mkv", series.Packs[0].Files[0].FileName); // percent escapes are undone
        Assert.Equal(2, series.Packs[1].Files[0].Season);
        Assert.True(new DownloadOptions(J("""{"download":null}""")).IsEmpty);

        // The server's ids run to eighteen digits; read as numbers, neighbours come out the same.
        var longIds = new DownloadOptions(J("""
        {"download":{"s1":[
          {"id":1,"season_int":1,"quality":"1080p","link":[
            {"dl":"https://example.com/a/Show.S01E01.mkv","episode":1,"id":278294959021668101},
            {"dl":"https://example.com/a/Show.S01E02.mkv","episode":2,"id":278294959021668102},
            {"dl":"https://example.com/a/Show.S01E03.mkv","episode":3,"id":278294959021668103}]},
          {"id":2,"season_int":1,"quality":"720p","link":[
            {"dl":"https://example.com/b/Show.S01E01.mkv","episode":1,"id":278294959021668201},
            {"dl":"https://example.com/b/Show.S01E10.mkv","episode":10,"id":278294959021668210}]}
        ]}}
        """));
        Assert.Equal(3, longIds.Packs[0].Files.Select(f => f.Id).Distinct().Count()); // every file keeps an id of its own
        Assert.Equal(["1", "2", "3", "10"], longIds.Episodes(1)); // what the season has across all packs, in number order
        Assert.Equal(2, longIds.Packs.First(p => p.Quality == "720p").Files.Count); // this pack lacks two of the four
    }

    [Fact]
    public void FilterBuildsTheParametersAdvancedSearchTakes()
    {
        var options = new FilterOptions(J("""
        {"genre":[{"name":"Drama","value":"drama"},{"name":"All","value":""}],
         "country":[{"name":"United States","value":"US"}],
         "imdb_rate":[{"name":"Above 8.5","value":8.5},{"name":"Above 8.0","value":8},{"name":"Under 5.0","value":-5}],
         "min_year":1890,"max_year":2026}
        """));
        Assert.Equal(["drama"], options.Genres.Select(c => c.Value)); // the server's own All entry is not a choice
        Assert.Equal(["8.5", "8"], options.Scores.Select(c => c.Value)); // only floors
        Assert.Equal(2020, options.Decades[0]);
        Assert.Equal(1950, options.Decades[^1]);

        Assert.True(new Filter().IsEmpty);
        var filter = new Filter(Genre: "drama", Country: "US", Score: "8.5", Decade: 2010);
        Assert.False(filter.IsEmpty);
        Assert.Equal("""{"country":"US","genre":"drama","imdb_rate":8.5,"max_year":2019,"min_year":2010,"stream":"true","type":"movie"}""", filter.Parameters("movie"));
        Assert.Equal("""{"imdb_rate":8,"stream":"true"}""", new Filter(Score: "8").Parameters(null));
    }

    [Fact]
    public void UpcomingEpisodesAreSortedSoonestFirst()
    {
        static Post Series(int id, string? next)
        {
            var date = next is null ? "" : $$$""","next_episode_date":{"g":"{{{next}}}","j":"۱۴۰۵-۰۱-۰۱"}""";
            return Post.Parse(J($$$"""{"id":{{{id}}},"title":{"english":"Show {{{id}}}"},"options":{"is_series":true},"air":{"air_status":"Returning"{{{date}}}}}"""))!;
        }
        var now = new DateTime(2026, 10, 13, 12, 0, 0, DateTimeKind.Utc);
        // The server writes the day out; the plain form is accepted as well.
        var posts = new[] { Series(1, "2 November 2026 Monday"), Series(2, null), Series(3, "13 October 2026 Tuesday"),
                            Series(4, "1 September 2026 Tuesday"), Series(5, "2026-10-20"), Series(3, "13 October 2026 Tuesday") };
        Assert.Equal([3, 5, 1], Post.Upcoming(posts, now).Select(p => p.Id)); // today counts, the past and the undated do not, nothing twice
    }

    [Fact]
    public void BackdropsAreTheBestLiveActionTitlesWithAWidePicture()
    {
        static string Entry(int id, double imdb, string type = "movie", string genre = "drama", bool cover = true)
        {
            var wide = cover ? $$$""","cover":{"webp":"https://example.com/{{{id}}}-wide.webp"}""" : "";
            var name = char.ToUpperInvariant(genre[0]) + genre[1..];
            var score = imdb.ToString(System.Globalization.CultureInfo.InvariantCulture);
            return $$$"""{"id":{{{id}}},"title":{"english":"Title {{{id}}}"},"options":{"title_type":"{{{type}}}"},"genre":[{"name":"{{{name}}}","slug":"{{{genre}}}"}],"score":{"imdb":{"score":{{{score}}}}},"image":{"poster":{"webp":{"large":"https://example.com/{{{id}}}.webp"}}{{{wide}}}}}""";
        }
        var feed = new HomeFeed(J($$$"""
        {"hero_header":{"posts":[{{{Entry(1, 8.5, type: "anime")}}}]},
         "movies":{"posts":[{{{Entry(2, 7.1)}}},{{{Entry(3, 9.0)}}},{{{Entry(4, 9.5, cover: false)}}},{{{Entry(5, 8.8, genre: "animation")}}}]},
         "series":{"posts":[{{{Entry(6, 8.2)}}},{{{Entry(3, 9.0)}}}]}}
        """));
        Assert.Equal([3, 6, 2], feed.Backdrops().Select(p => p.Id)); // best first; no anime, no animation, nothing without a wide picture, nothing twice
        Assert.Equal([3, 6], feed.Backdrops(limit: 2).Select(p => p.Id));
        var cartoons = new HomeFeed(J($$$"""{"hero_header":{"posts":[{{{Entry(1, 8.5, type: "anime")}}}]}}"""));
        Assert.Equal([1], cartoons.Backdrops().Select(p => p.Id)); // with nothing else to show, animation is better than a blank screen
    }

    [Fact]
    public void HomeFeedFromLiveResponse()
    {
        var feed = new HomeFeed(Fixtures.Load("home_stream")["result"]);
        Assert.Equal(3, feed.Heroes.Count);
        Assert.Equal(["suggested", "movies", "series", "anime"], feed.Sections.Select(s => s.Key));

        var hero = feed.Heroes[0];
        Assert.Equal(469773, hero.Id);
        Assert.Equal(Post.Kinds.Anime, hero.Kind);
        Assert.True(hero.IsSeries);
        Assert.Equal(3, hero.Seasons);
        Assert.Equal(24, hero.RuntimeMinutes);
        Assert.Equal(8.5, hero.ImdbScore);
        Assert.Equal("tt14976292", hero.ImdbId);
        Assert.Equal("bRIr1_%M~qxtyXtR%hRjIUxu", hero.Poster.Blurhash);
        Assert.Equal("cdn-world.hana-api.com", hero.Cover?.Host);
        Assert.NotEmpty(hero.Genres);
    }

    [Fact]
    public void TitleDropsItsOwnYearOnly()
    {
        var hero = new HomeFeed(Fixtures.Load("home_stream")["result"]).Heroes[0];
        Assert.Equal("Link Click 2021", hero.TitleEnglish);
        Assert.Equal("Link Click", hero.Title(Lang.En));
        Assert.Equal("Link Click", hero.Title(Lang.Fa)); // falls back to English when there is no local title
        Assert.Null(hero.AlternateTitle(Lang.Fa));

        var blade = Post.Parse(J("""{"id":1,"title":{"english":"Blade Runner 2049","local":"بلید رانر"},"info":{"year":2017}}"""))!;
        Assert.Equal("Blade Runner 2049", blade.Title(Lang.En));
        Assert.Equal("بلید رانر", blade.Title(Lang.Fa));
        Assert.Equal("Blade Runner 2049", blade.AlternateTitle(Lang.Fa));

        var war = Post.Parse(J("""{"id":2,"title":{"english":"1917 2019"},"info":{"year":2019}}"""))!;
        Assert.Equal("1917", war.Title(Lang.En));

        var local = Post.Parse(J("""{"id":3,"title":{"english":"1917 2019","local":"۱۹۱۷ 2019"},"info":{"year":2019}}"""))!;
        Assert.Equal("۱۹۱۷", local.Title(Lang.Fa)); // local titles carry the year too
    }

    [Fact]
    public void SingleCarriesCastAndCrew()
    {
        var movie = Post.Parse(Fixtures.Load("single_movie")["result"])!;
        Assert.Equal(Post.Kinds.Movie, movie.Kind);
        Assert.Equal("Ben Affleck", movie.Cast[0].Name);
        Assert.Equal("Milo Bradford", movie.Cast[0].Role);
        Assert.NotNull(movie.Cast[0].Poster);
        Assert.Equal(["Ben Affleck"], movie.Directors);
        Assert.NotEmpty(movie.Writers);
        Assert.Equal("United States", movie.Countries[0]);
        Assert.Null(movie.Resume);

        var series = Post.Parse(Fixtures.Load("single_series")["result"])!;
        Assert.NotNull(series.AirStatus);
        Assert.NotNull(series.NextEpisodeDate.Jalali);
        // What 30nama's own apps show beside the scores.
        Assert.Equal(7639, series.ImdbVotes);
        Assert.Equal(195, series.InWatchlists);
        Assert.Equal(184, series.InFavorites);
        Assert.Null(series.ImdbTop250); // sent as an empty string: not on the list
        Assert.Equal("Chinese", series.OtherLanguages[0]);
        Assert.DoesNotContain("Mandarin", series.OtherLanguages); // the primary language is not repeated
        Assert.Equal("اضافه شدن قسمت جدید", series.LatestUpdate?.Title);
        Assert.Equal("اپیزود 10 فصل 3", series.LatestUpdate?.Content);
        Assert.Equal(12, Post.Parse(Json.Parse("""{"id":1,"score":{"imdb":{"top_250":"12"}}}"""))!.ImdbTop250);
    }

    [Fact]
    public void CriticScores()
    {
        var dune = Post.Parse(J("""
        {"id":9,"score":{"imdb":{"score":8.0},"rottentomatoes":{"link":"https://www.rottentomatoes.com/m/dune_2021","score":85},
         "metacritic":{"score":74},"mydramalist":{"score":0}}}
        """))!;
        Assert.Equal(85, dune.RottenTomatoes);
        Assert.Equal(74, dune.Metacritic);
        Assert.Null(dune.FanScore); // a zero score means the site has none

        var hero = new HomeFeed(Fixtures.Load("home_stream")["result"]).Heroes[0];
        Assert.Equal("MAL", hero.FanScore?.Source);
        Assert.Equal(8.72, hero.FanScore?.Value);
        Assert.Null(hero.RottenTomatoes);
    }

    [Fact]
    public void PosterPicksSmallestSufficientVariant()
    {
        var poster = new HomeFeed(Fixtures.Load("home_stream")["result"]).Heroes[0].Poster;
        Assert.Contains("-l_", poster.Url(200)!.OriginalString);
        Assert.Contains("-b_", poster.Url(400)!.OriginalString);
        Assert.Contains("-b_", poster.Url(900)!.OriginalString); // largest when nothing is big enough
    }

    [Fact]
    public void MenuAndSearchPage()
    {
        var menu = new Menu(Fixtures.Load("menu")["result"]);
        Assert.Equal("movies", menu.Categories[0].Slug);
        Assert.Equal(3, menu.Genres.Count);
        Assert.NotNull(menu.Genres[0].Thumb);

        var page = new PostPage(Fixtures.Load("search")["result"]);
        Assert.Equal(2, page.Posts.Count);
        Assert.Equal(4, page.Pages);
        Assert.Equal(86, page.Total);
    }

    [Fact]
    public void SeriesStreamIsSortedNumerically()
    {
        var stream = new StreamResult(Fixtures.Load("stream_series")["result"]);
        Assert.Equal(["1x2", "1x10", "1x11,12", "2x1"], stream.Items.Select(i => $"{i.Season}x{i.EpisodeRaw}"));
        Assert.Equal([1, 2], stream.Seasons);
        Assert.Equal("102", stream.Resume?.FileId);
        Assert.Equal(0, stream.StartIndex);
        Assert.Equal(34, stream.Resume?.RemainingMinutes);

        var first = stream.Items[0];
        Assert.Equal(44, first.DurationMinutes);
        Assert.Equal(90, first.IntroEnd);
        Assert.Equal(2470, first.CreditsStart);
        Assert.Equal("fa.vtt", first.Subtitles["fa"].LastPathComponent());
        Assert.Equal("en.vtt", first.Subtitles["en"].LastPathComponent());
        Assert.Equal("11–12", Episode.Label(stream.Items[2].EpisodeRaw, 3));
    }

    [Fact]
    public void StreamAsServedToASubscriber()
    {
        var stream = new StreamResult(Fixtures.Load("stream_subscriber")["result"]);
        Assert.Equal([1, 2, 3, 4, 5, 6], stream.Items.Select(i => i.EpisodeNumber));
        Assert.Equal(5, stream.StartIndex); // resumes the episode named by continue_watching
        Assert.Equal(60, stream.Resume?.Time);
        Assert.Equal(0.01, stream.Resume!.Progress, 4); // no duration: falls back to the reported percentage
        Assert.Null(stream.Resume.RemainingMinutes);

        var item = stream.Items[0];
        Assert.Equal("182990", item.FileId);
        Assert.Equal(["خودکار", "720p", "1080p"], item.Qualities.Select(q => q.Label));
        Assert.Equal("1080.m3u8", item.PickQuality(preferAdaptive: false)!.Url.LastPathComponent());
        Assert.Equal("auto.m3u8", item.PickQuality(preferAdaptive: true)!.Url.LastPathComponent());
        var full = item.Qualities.First(q => q.Label == "1080p");
        Assert.Equal("1080-2ch.m3u8", full.Stereo?.LastPathComponent());
        Assert.Equal("1080-6ch.m3u8", full.Surround?.LastPathComponent());
        Assert.Equal("1080-2ch.m3u8", full.UrlFor(AudioMix.Stereo).LastPathComponent());
        Assert.Equal("1080.m3u8", full.UrlFor(AudioMix.Original).LastPathComponent());
        Assert.Equal([AudioMix.Stereo, AudioMix.Surround, AudioMix.Original], full.Mixes);
        Assert.Equal(403, item.IntroStart);
        Assert.Equal(413, item.IntroEnd);
        Assert.Null(item.RecapEnd);
        Assert.Equal(3328, item.CreditsStart);
        Assert.Equal(2, item.Subtitles.Count);
    }

    [Fact]
    public void QualityPreference()
    {
        var item = new StreamResult(Fixtures.Load("stream_series")["result"]).Items[0];
        Assert.Equal(["Auto", "720p", "1080p"], item.Qualities.Select(q => q.Label));
        Assert.Equal("https://example.com/1080.m3u8", item.PickQuality(preferAdaptive: false)!.Url.OriginalString); // URL is trimmed
        Assert.Equal("Auto", item.PickQuality(preferAdaptive: true)!.Label);

        var movie = new StreamResult(Fixtures.Load("stream_movie")["result"]);
        Assert.Single(movie.Items);
        Assert.Equal("9001", movie.Items[0].FileId); // numeric ids are read as strings
        Assert.Equal("movie.m3u8", movie.Items[0].PickQuality(preferAdaptive: false)!.Url.LastPathComponent());
        Assert.Null(movie.Resume);
    }

    [Fact]
    public void SkipWindows()
    {
        var item = new StreamResult(Fixtures.Load("stream_series")["result"]).Items[0];
        Assert.Null(item.SkipTargetAt(2));
        Assert.Equal(90, item.SkipTargetAt(30)?.To);
        Assert.Equal(true, item.SkipTargetAt(30)?.IsIntro);
        Assert.Null(item.SkipTargetAt(89.5)); // no button in the last second
        Assert.Null(item.SkipTargetAt(200));
    }

    [Fact]
    public void ContinueWatchingListShapes()
    {
        var wrapped = J("""{"posts":[{"post":{"id":5},"continue_watching":{"file_id":9,"time":"120","duration":1200,"season":2,"episode":3}}]}""");
        var p = Post.ContinueList(wrapped);
        Assert.Equal(5, p[0].Id);
        Assert.Equal(0.1, p[0].Resume?.Progress);
        Assert.Equal(2, p[0].Resume?.Season);

        var nested = J("""[{"id":6,"continue_watching":{"info":{"file_id":"x","season":"1","episode":"4"},"position":60,"duration":600}}]""");
        Assert.Equal("4", Post.ContinueList(nested)[0].Resume?.Episode);
        Assert.Equal(60, Post.ContinueList(nested)[0].Resume?.Time);
    }

    [Fact]
    public void ContinueWatchingAsServedToASubscriber()
    {
        // The shape of one entry of a live continue_watching response.
        var feed = J("""
        {"posts":[{"id":283405,"title":{"english":"Reacher 2022"},"options":{"is_series":true},
          "data":{"display_time":"00:17:30","episode":4,"episode_title":"فصل 3 - قسمت 4","id":283405,"progress":"35.9836","season":3,"time":1050}}]}
        """);
        var resume = Post.ContinueList(feed)[0].Resume!;
        Assert.Equal(3, resume.Season);
        Assert.Equal("4", resume.Episode);
        Assert.Equal(1050, resume.Time);
        Assert.Equal(0.359836, resume.Progress, 4);
        Assert.Equal(31, resume.RemainingMinutes); // the length is worked out from the position and the percentage
    }

    [Fact]
    public void NextEpisodeLabelPutsTheWeekdayFirst()
    {
        var post = Post.List(J("""{"posts":[{"id":1,"air":{"next_episode_date":{"g":"20 October 2026 Tuesday","j":"۲۸ مهر ۱۴۰۵ سه شنبه"}}}]}"""))[0];
        Assert.Equal("سه شنبه ۲۸ مهر", post.NextEpisodeLabel(Lang.Fa));
        Assert.Equal("Tuesday 20 October", post.NextEpisodeLabel(Lang.En));
    }

    [Fact]
    public void UserShapes()
    {
        var bare = User.Parse(J("""{"user_session":"abc","user_name":"sara","user_expire":1800000000,"user_id":12}"""));
        Assert.Equal("abc", bare?.Token);
        Assert.Equal("12", bare?.Id);
        Assert.Equal("2027-01-15", bare?.Expiry);

        // What a subscriber's profile carries.
        var subscriber = User.Parse(J("""
        {"user_session":"s","user_type":"golden","user_avatar":"https://example.com/a.png",
         "user_expire":{"days":641,"g":"2028-07-15 15:50:45","j":"۱۴۰۷-۰۴-۲۵ ۱۵:۵۰:۴۵"}}
        """))!;
        Assert.Equal("2028-07-15", subscriber.Expiry);
        Assert.Equal("۱۴۰۷-۰۴-۲۵", subscriber.ExpiryLocal);
        Assert.Equal("a.png", subscriber.Avatar?.LastPathComponent());
        var midOctober2026 = DateTime.UnixEpoch.AddSeconds(1_791_849_600);
        Assert.Equal(641, subscriber.DaysLeft(midOctober2026));
        Assert.Equal("اعتبار اشتراک تا ۱۴۰۷/۰۴/۲۵ · ۶۴۱ روز مانده", new Loc(Lang.Fa).SubscriptionLine(subscriber, midOctober2026));
        Assert.Equal("Subscription until 2028-07-15 · 641 days left", new Loc(Lang.En).SubscriptionLine(subscriber, midOctober2026));
        Assert.Null(new Loc(Lang.Fa).SubscriptionLine(User.Parse(J("""{"token":"t"}"""))!));

        var wrapped = User.Parse(J("""{"user":{"user_session":"zzz","user_email":"a@b.c"}}"""));
        Assert.Equal("a@b.c", wrapped?.Name);

        Assert.Equal("t1", User.Parse(J("""{"token":"t1"}"""))?.Token);
        Assert.Null(User.Parse(J("""{"user_name":"x"}""")));
        Assert.Equal("keep", User.Parse(J("""{"user_name":"x"}"""), fallbackToken: "keep")?.Token);
    }
}
