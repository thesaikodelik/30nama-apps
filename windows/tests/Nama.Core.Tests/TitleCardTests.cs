using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class TitleCardTests
{
    private static readonly Json Details = Json.Parse("""
        {"id":604,"title":"The Matrix Reloaded","original_title":"The Matrix Reloaded","original_language":"en","poster_path":"/main.jpg",
         "images":{"posters":[
           {"file_path":"/main.jpg","iso_639_1":"en","vote_average":9},
           {"file_path":"/french.jpg","iso_639_1":"fr","vote_average":8},
           {"file_path":"/second.jpg","iso_639_1":"en","vote_average":6},
           {"file_path":"/wordless.jpg","iso_639_1":null,"vote_average":7}]},
         "production_companies":[{"name":"Silver","logo_path":null},{"name":"Village Roadshow","logo_path":"/vr.svg"}]}
        """);

    private static readonly Json Collection = Json.Parse("""
        {"name":"The Matrix Collection",
         "parts":[{"id":605,"release_date":"2003-11-05"},{"id":603,"release_date":"1999-03-31"},{"id":604,"release_date":"2003-05-15"}],
         "translations":{"translations":[
           {"iso_639_1":"fa","data":{"name":"مجموعهٔ ماتریکس"}},
           {"iso_639_1":"de","data":{"name":"Matrix"}}]}}
        """);

    [Fact]
    public void FromTMDBAsTheDailyJobMakesIt()
    {
        var card = TitleCard.FromTMDB(Details, Collection)!;
        Assert.Equal("wordless.jpg", card.Poster); // the best voted other poster, English or wordless
        Assert.Null(card.OwnName); // an English title has no name of its own to show
        Assert.Equal(new TitleCard.Part(new Dictionary<string, string> { ["en"] = "The Matrix Collection", ["fa"] = "مجموعهٔ ماتریکس" }, 2, 3), card.Collection);
        Assert.Equal("مجموعهٔ ماتریکس", card.Collection!.Name(Lang.Fa));
        Assert.Equal("The Matrix Collection", card.Collection.Name(Lang.Ar)); // Arabic falls back to English
        Assert.Equal("vr.png", card.CompanyLogo);
        Assert.Equal("Village Roadshow", card.CompanyName);
    }

    [Fact]
    public void OwnNameOnlyWhereItDiffers()
    {
        var spirited = Json.Parse("""{"id":129,"title":"Spirited Away","original_title":"千と千尋の神隠し","original_language":"ja"}""");
        Assert.Equal("千と千尋の神隠し", TitleCard.FromTMDB(spirited)!.OwnName);
        // The same name in another case is nothing to show.
        Assert.Null(TitleCard.FromTMDB(Json.Parse("""{"id":1,"title":"Amélie","original_title":"amélie","original_language":"fr"}""")));
    }

    [Fact]
    public void ASeriesFallsBackToItsNetwork()
    {
        var dark = Json.Parse("""
            {"id":1399,"name":"Dark","original_name":"Dark","original_language":"de",
             "production_companies":[{"name":"Wiedemann","logo_path":null}],"networks":[{"name":"Netflix","logo_path":"/nf.png"}]}
            """);
        Assert.Equal("nf.png", TitleCard.FromTMDB(dark)!.CompanyLogo);
    }

    [Fact]
    public void ACollectionOfOneIsNone()
    {
        Assert.Null(TitleCard.FromTMDB(Details, Json.Parse("""{"name":"Solo","parts":[{"id":604}]}"""))!.Collection);
    }

    [Fact]
    public void FromTheDailyFile()
    {
        var shard = Json.Parse("""{"version":1,"updated":"2026-10-29","cards":{"tt0234215":{"p":"wordless.jpg","c":{"names":{"en":"The Matrix Collection","fa":"مجموعهٔ ماتریکس"},"at":2,"of":3},"l":"vr.png","n":"Village Roadshow"}}}""");
        Assert.Equal(TitleCard.FromTMDB(Details, Collection), TitleCard.FromShard(shard, "tt0234215"));
        Assert.Null(TitleCard.FromShard(shard, "tt0000001"));
        Assert.Null(TitleCard.FromShard(Json.Parse("""{"version":2,"cards":{}}"""), "tt0234215"));
    }

    [Fact]
    public void TheFileIsTheLastTwoDigits()
    {
        Assert.Equal("15", TitleCard.Shard("tt0234215"));
        Assert.Equal("07", TitleCard.Shard("tt7"));
        Assert.EndsWith("/data/cards/15.json", TitleCard.Address("tt0234215").AbsoluteUri);
    }
}
