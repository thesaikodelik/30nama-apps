using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class AdvancedSearchTests
{
    [Fact]
    public void ReadsTheChoicesTheServerActsOn()
    {
        var choices = new SearchChoices(Json.Parse("""
        {"genre":[{"name":"Drama","value":"drama"},{"name":"All","value":""}],
         "type":[{"name":"Movie","value":"movie"},{"name":"Movie Anime","value":"movie_anime"},{"name":"Anime","value":"anime"}],
         "channel":[{"name":"Netflix","value":"Netflix"}],
         "language":[{"name":"Korean","value":"ko"}],
         "imdb_rate":[{"name":"Above 8.5","value":8.5},{"name":"Under 5.0","value":-5}],
         "age":["PG-13","R"],
         "quality":[{"name":"4K","value":"4K"}]}
        """));
        Assert.Equal(["drama"], choices.Genres.Select(c => c.Value)); // the server's own All entry is not a choice
        Assert.Equal(["movie", "anime"], choices.Types.Select(c => c.Value)); // anime films are refused by the server
        Assert.Equal(["Netflix"], choices.Networks.Select(c => c.Name));
        Assert.Equal(["8.5"], choices.Scores.Select(c => c.Value)); // only floors
        Assert.Equal(["PG-13", "R"], choices.Ages.Select(c => c.Value)); // bare values are their own names
    }

    [Fact]
    public void BuildsTheParameters()
    {
        Assert.Equal("""{"stream":"true"}""", new AdvancedQuery().Parameters());
        Assert.Equal(0, new AdvancedQuery().Count);
        var query = new AdvancedQuery
        {
            Genres = ["drama", "crime"], Type = "series", Network = "Netflix", Language = "ko", Score = "8", Age = "R",
            Director = "  Bong Joon Ho ", FromYear = 2020, ToYear = 2010, X265 = true, StreamOnly = false, Sort = "imdb-rate",
        };
        Assert.Equal(
            """{"age":"R","channel":"Netflix","director":"Bong Joon Ho","genre":["drama","crime"],"imdb_rate":8,"language":"ko","max_year":"2020","min_year":"2010","type":["series"],"x265":"true"}""",
            query.Parameters()); // the years put the right way round; the type as a list
        Assert.Equal(11, query.Count);
        var request = query.Request(3);
        Assert.Equal("imdb-rate", request["orderby"]);
        Assert.Equal("3", request["page"]);
    }

    [Fact]
    public void OneYearIsEnough()
    {
        Assert.Equal("""{"min_year":"1990","stream":"true"}""", new AdvancedQuery { FromYear = 1990 }.Parameters());
        Assert.Equal("""{"max_year":"1995","stream":"true"}""", new AdvancedQuery { ToYear = 1995 }.Parameters());
    }
}
