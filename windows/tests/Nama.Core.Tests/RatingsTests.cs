using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

// Ported from Tests/NamaCoreTests/RatingsTests.swift and ContinueDismissalsTests.swift.
public class RatingsTests
{
    private static Post MakePost(int id, string[] genres, double? imdb = null, bool soon = false)
    {
        var json = new Dictionary<string, object?>
        {
            ["id"] = id,
            ["title"] = new Dictionary<string, object?> { ["english"] = $"Title {id} 2020", ["local"] = $"عنوان {id}" },
            ["info"] = new Dictionary<string, object?> { ["year"] = 2020 },
            ["options"] = new Dictionary<string, object?> { ["is_series"] = id % 2 == 0, ["coming_soon"] = soon },
            ["genre"] = genres.Select(g => new Dictionary<string, object?> { ["name"] = char.ToUpperInvariant(g[0]) + g[1..], ["slug"] = g }).ToList(),
            ["image"] = new Dictionary<string, object?>
            {
                ["poster"] = new Dictionary<string, object?>
                {
                    ["preview"] = "00OZZy",
                    ["webp"] = new Dictionary<string, object?> { ["large"] = $"https://example.com/{id}-l.webp", ["big"] = $"https://example.com/{id}-b.webp" },
                },
            },
        };
        if (imdb is not null) json["score"] = new Dictionary<string, object?> { ["imdb"] = new Dictionary<string, object?> { ["score"] = imdb } };
        return Post.Parse(Json.From(json))!;
    }

    private static DateTime At(double seconds) => DateTime.UnixEpoch.AddSeconds(seconds);

    [Fact]
    public void RateChangeAndRemove()
    {
        var book = new RatingBook();
        book.Rate(MakePost(1, ["drama"]), 4.3);
        Assert.Equal(4.5, book.StarsFor(1)); // snaps to the nearest half star
        book.Rate(MakePost(1, ["drama"]), 9);
        Assert.Equal(5, book.StarsFor(1));
        book.Rate(MakePost(2, ["comedy"]), 0);
        Assert.Equal(0.5, book.StarsFor(2));
        Assert.Equal(2, book.Count);
        Assert.Equal(2.75, book.Average);
        book.Rate(MakePost(1, ["drama"]), null);
        Assert.Null(book.StarsFor(1));
        Assert.Equal(1, book.Count);
        Assert.Null(new RatingBook().Average);
    }

    [Fact]
    public void ExportsACsvLetterboxdCanRead()
    {
        var book = new RatingBook();
        book.Rate(MakePost(1, ["drama"]), 4, At(1_000));
        book.Rate(MakePost(2, ["comedy"]), 2.5, At(2_000));
        var awkward = MakePost(3, ["crime"]);
        awkward.TitleEnglish = "Me, Myself & \"I\" 2020";
        book.Rate(awkward, 5, At(3_000));
        // Newest first, the year taken off the title, commas and quotes escaped.
        Assert.Equal("Title,Year,Rating\n\"Me, Myself & \"\"I\"\"\",2020,5.0\nTitle 2,2020,2.5\nTitle 1,2020,4.0\n", book.Csv());
        Assert.Equal("Title,Year,Rating\n", new RatingBook().Csv());
    }

    [Fact]
    public void SurvivesEncoding()
    {
        var book = new RatingBook();
        book.Rate(MakePost(1, ["drama", "crime"]), 4, At(1_000));
        book.Rate(MakePost(2, ["comedy"]), 2.5, At(2_000));
        var restored = RatingBook.FromData(book.Encoded())!;
        Assert.Equal(book, restored);
        Assert.Equal([2, 1], restored.NewestFirst.Select(r => r.PostId));
        Assert.Null(RatingBook.FromData("not json"));
    }

    [Fact]
    public void BooksSavedBeforeRemovalsWereRecordedStillOpen()
    {
        var book = RatingBook.FromData("""[{"postID":3,"stars":4,"date":1000,"isSeries":false,"isAnime":false,"genres":[]}]""")!;
        Assert.Equal(4, book.StarsFor(3));
        Assert.Empty(book.Removed);
    }

    [Fact]
    public void ReadsABookTheMacAppSaved()
    {
        var book = RatingBook.FromData("""
        {"ratings":[{"blurhash":"00OZZy","date":2000.5,"genres":[{"name":"Drama","slug":"drama"}],"isAnime":false,"isSeries":true,
                     "postID":8,"posterBig":"https:\/\/example.com\/8-b.webp","stars":4.5,"titleEnglish":"Title 8 2020","year":2020}],
         "removed":{"9":3000}}
        """)!;
        Assert.Equal(4.5, book.StarsFor(8));
        Assert.Equal(At(3000), book.Removed[9]);
        Assert.Equal("https://example.com/8-b.webp", book.Ratings[8].PosterBig?.OriginalString);
    }

    [Fact]
    public void RemovalIsRememberedUntilTheTitleIsRatedAgain()
    {
        var book = new RatingBook();
        book.Rate(MakePost(1, ["drama"]), null, At(5));
        Assert.Empty(book.Removed); // nothing was rated, so nothing was removed
        book.Rate(MakePost(1, ["drama"]), 4, At(10));
        book.Rate(MakePost(1, ["drama"]), null, At(20));
        Assert.Equal(At(20), book.Removed[1]);
        Assert.Equal(book, RatingBook.FromData(book.Encoded())); // removals survive encoding
        book.Rate(MakePost(1, ["drama"]), 3, At(30));
        Assert.Empty(book.Removed);
    }

    [Fact]
    public void MergeKeepsWhateverHappenedLast()
    {
        var here = new RatingBook();
        var there = new RatingBook();
        here.Rate(MakePost(1, ["drama"]), 2, At(10));      // rated on both, later there
        there.Rate(MakePost(1, ["drama"]), 5, At(20));
        here.Rate(MakePost(2, ["drama"]), 4, At(10));      // only here
        there.Rate(MakePost(3, ["drama"]), 3, At(10));     // only there
        here.Rate(MakePost(4, ["drama"]), 4, At(10));      // rated here, removed there afterwards
        there.Rate(MakePost(4, ["drama"]), 4, At(10));
        there.Rate(MakePost(4, ["drama"]), null, At(30));
        there.Rate(MakePost(5, ["drama"]), 1, At(10));     // removed there, rated again here afterwards
        there.Rate(MakePost(5, ["drama"]), null, At(20));
        here.Rate(MakePost(5, ["drama"]), 4.5, At(40));

        var merged = here.Merged(there);
        Assert.Equal(5, merged.StarsFor(1));
        Assert.Equal(4, merged.StarsFor(2));
        Assert.Equal(3, merged.StarsFor(3));
        Assert.Null(merged.StarsFor(4)); // the removal came after the rating
        Assert.Equal(new Dictionary<int, DateTime> { [4] = At(30) }, merged.Removed);
        Assert.Equal(4.5, merged.StarsFor(5)); // the rating came after the removal
        Assert.Equal(merged, there.Merged(here)); // both devices arrive at the same book
        Assert.Equal(merged, merged.Merged(here));
    }

    [Fact]
    public void RatedTitleCanBeShownAgain()
    {
        var original = MakePost(8, ["drama"]);
        var shown = new Rating(original, 4).ToPost()!;
        Assert.Equal(8, shown.Id);
        Assert.Equal("Title 8", shown.Title(Lang.En));
        Assert.Equal("عنوان 8", shown.Title(Lang.Fa));
        Assert.True(shown.IsSeries);
        Assert.Equal(original.Poster.Big, shown.Poster.Big);
        Assert.Equal("00OZZy", shown.Poster.Blurhash);
        Assert.Equal(["drama"], shown.Genres.Select(g => g.Slug));
    }

    [Fact]
    public void TasteShrinksThinEvidence()
    {
        var book = new RatingBook();
        for (var id = 1; id <= 6; id++) book.Rate(MakePost(id, ["drama"]), 4);
        book.Rate(MakePost(7, ["western"]), 5);
        book.Rate(MakePost(8, ["horror"]), 1);
        var taste = book.Taste();
        Assert.Equal(0.75, taste["drama"], 4);       // six at +1, over 6 + 2
        Assert.Equal(2.0 / 3, taste["western"], 4);  // one at +2, over 1 + 2
        Assert.True(taste["horror"] < 0);
        Assert.Equal(["drama", "western"], book.FavoriteGenres()); // many good dramas outrank one great western
    }

    [Fact]
    public void TasteAtAGlance()
    {
        var book = new RatingBook();
        Assert.Empty(book.FavoriteGenreNames());
        Assert.Equal([0, 0, 0, 0, 0], book.Spread());
        for (var id = 1; id <= 4; id++) book.Rate(MakePost(id, ["drama"]), 4.5);
        book.Rate(MakePost(5, ["comedy"]), 4);
        book.Rate(MakePost(6, ["horror"]), 1);
        book.Rate(MakePost(7, ["horror"]), 0.5);
        var favourites = book.FavoriteGenreNames();
        Assert.Equal(["Drama", "Comedy"], favourites.Select(f => f.Name)); // by name, strongest first, disliked genres left out
        Assert.Equal(1, favourites[0].Strength);
        Assert.True(favourites[1].Strength < 1);
        Assert.Equal([2, 0, 0, 1, 4], book.Spread()); // half stars count with the star above
    }

    [Fact]
    public void Recommendations()
    {
        var book = new RatingBook();
        Assert.Empty(book.Recommend([MakePost(50, ["drama"])])); // nothing to go on yet
        for (var id = 1; id <= 4; id++) book.Rate(MakePost(id, ["drama"]), 4.5);
        book.Rate(MakePost(5, ["horror"]), 1);

        var picks = book.Recommend([
            MakePost(1, ["drama"]),                 // already rated
            MakePost(10, ["drama"], imdb: 7.0),
            MakePost(11, ["drama"], imdb: 8.5),     // same fit, better regarded
            MakePost(12, ["horror"], imdb: 9.0),    // disliked genre
            MakePost(13, ["drama", "horror"]),      // mixed: averages out below a pure drama
            MakePost(14, ["musical"]),              // unknown genre: no opinion
            MakePost(15, ["drama"], soon: true),    // not out yet
            MakePost(11, ["drama"], imdb: 8.5),     // duplicate candidate
        ]);
        Assert.Equal([11, 10, 13], picks.Select(p => p.Id));
        Assert.Single(book.Recommend([MakePost(10, ["drama"]), MakePost(11, ["drama"])], limit: 1));
    }
}

public class ContinueDismissalsTests
{
    private static List<Post> Feed(params (int Id, double Time)[] entries) =>
        Post.ContinueList(Json.From(new Dictionary<string, object?>
        {
            ["posts"] = entries.Select(e => new Dictionary<string, object?>
            {
                ["id"] = e.Id,
                ["continue_watching"] = new Dictionary<string, object?> { ["file_id"] = $"f{e.Id}", ["time"] = e.Time, ["duration"] = 3000 },
            }).ToList(),
        }));

    [Fact]
    public void ADismissedTitleStaysHiddenUntilWatchedAgain()
    {
        var dismissals = new ContinueDismissals();
        var list = Feed((1, 600), (2, 900));
        dismissals.Dismiss(list[0]);
        Assert.Equal([2], dismissals.Filter(list).Select(p => p.Id));
        Assert.Equal([2], dismissals.Filter(Feed((1, 600), (2, 930))).Select(p => p.Id)); // the other title moving on does not bring it back
        Assert.Equal([1, 2], dismissals.Filter(Feed((1, 1200), (2, 930))).Select(p => p.Id)); // watched again: back on the list
        Assert.Empty(dismissals.Positions); // and no longer remembered
    }

    [Fact]
    public void ForgetsTitlesTheServerNoLongerLists()
    {
        var dismissals = new ContinueDismissals();
        dismissals.Dismiss(Feed((1, 600))[0]);
        dismissals.Filter(Feed((2, 900)));
        Assert.Empty(dismissals.Positions);
    }

    [Fact]
    public void SurvivesStorage()
    {
        var dismissals = new ContinueDismissals();
        dismissals.Dismiss(Feed((7, 120))[0]);
        Assert.Equal(dismissals, new ContinueDismissals(dismissals.Stored));
    }
}
