using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

// Ported from Tests/NamaCoreTests/SubSourceTests.swift.
public class SubSourceTests
{
    private static byte[] ZipFixture(string name) => File.ReadAllBytes(Path.Combine(AppContext.BaseDirectory, "Fixtures", name + ".zip"));

    [Fact]
    public void ReadsDeflatedAndStoredArchives()
    {
        var files = Zip.Files(ZipFixture("subsource_deflated"));
        Assert.Equal(["Show.S01E02.WEB.srt", "Show.S01E03.WEB.srt"], files.Select(f => f.Name));
        Assert.Contains("سلام", System.Text.Encoding.UTF8.GetString(files[0].Data));
        Assert.Equal(files[0].Data, Zip.Files(ZipFixture("subsource_stored"))[0].Data);
        Assert.Empty(Zip.Files("not a zip"u8.ToArray()));
    }

    [Fact]
    public void RefusesAnEntryThatClaimsToBeHuge()
    {
        // The stored archive with its one entry rewritten to claim 4 GB unpacked.
        var bytes = ZipFixture("subsource_stored");
        var end = bytes.Length - 22;
        var directory = bytes[end + 16] | bytes[end + 17] << 8 | bytes[end + 18] << 16 | bytes[end + 19] << 24;
        for (var i = 0; i < 4; i++) bytes[directory + 24 + i] = 0xFF;
        Assert.Empty(Zip.Files(bytes)); // nothing is allocated for it
    }

    [Fact]
    public void TakesTheEpisodesFileOutOfAnArchive()
    {
        Assert.Contains("فقط قسمت سه", SubSource.TextFrom(ZipFixture("subsource_deflated"), 1, 3));
        Assert.Null(SubSource.TextFrom(ZipFixture("subsource_deflated"), 1, 9)); // no file for that episode
    }

    /// <summary>The shape of the site's own search answer.</summary>
    private static readonly Json Search = Json.Parse("""
        {"success":true,"results":[
          {"id":29130,"title":"Dark Matter","type":"tvseries","link":"/series/dark-matter","releaseYear":2015,
           "seasons":[{"season":1,"id":29130,"link":"/subtitles/dark-matter/season=1"}]},
          {"id":131507,"title":"Dark Matter","type":"tvseries","link":"/series/dark-matter-2024","releaseYear":2024,
           "seasons":[{"season":1,"id":131507,"link":"/subtitles/dark-matter-2024/season=1"},
                      {"season":2,"id":162344,"link":"/subtitles/dark-matter-2024/season=2"}]},
          {"id":29156,"title":"Dark Matter","type":"movie","link":"/subtitles/dark-matter-2007","releaseYear":2007}]}
        """);

    [Fact]
    public void PicksTheRightTitleAndSeason()
    {
        Assert.Equal("https://subsource.net/subtitles/dark-matter-2024/season-2/farsi_persian",
            SubSource.Listing(Search, new SubSource.Query("Dark Matter", 2024, null, true, 2))?.AbsoluteUri);
        Assert.Equal("https://subsource.net/subtitles/dark-matter-2007/farsi_persian",
            SubSource.Listing(Search, new SubSource.Query("Dark Matter", 2007, null, false, null))?.AbsoluteUri);
        Assert.Null(SubSource.Listing(Search, new SubSource.Query("Dark Matter", 2019, null, false, null))); // a film of another year is not this one
    }

    [Fact]
    public void OldListingAddressesAreBroughtUpToDate()
    {
        Assert.Equal("https://subsource.net/subtitles/x/season-3/farsi_persian",
            SubSource.Current(new Uri("https://subsource.net/subtitles/x/season=3/farsi_persian")).AbsoluteUri);
    }

    [Fact]
    public void ReadsTheDocumentedApi()
    {
        var found = Json.Parse("""{"success":true,"data":[{"movieId":10,"season":1},{"movieId":11,"season":2}]}""");
        Assert.Equal(11, SubSource.MovieId(found, new SubSource.Query("x", null, null, true, 2)));
        var subs = Json.Parse("""{"data":[{"subtitleId":5,"releaseInfo":["Show.S01E02.WEB-DL"]},{"releaseInfo":["no id"]}]}""");
        Assert.Equal([new SubSource.Candidate(null, 5, ["Show.S01E02.WEB-DL"])], SubSource.CandidatesInApi(subs));
        Assert.Contains("searchType=imdb&imdb=tt123", SubSource.SearchUrl(new SubSource.Query("x", null, "tt123", false, null)).OriginalString);
    }

    [Fact]
    public void RanksForTheEpisodeAndTheCopy()
    {
        static SubSource.Candidate C(string name) => new(new Uri("https://e.x/" + name), null, [name]);
        var list = new[] { C("Show.S01E05.WEB"), C("Show.S01E02.HDTV"), C("Show.S01.WEB"), C("Show.S01E02.WEB-DL") };
        Assert.Equal(["Show.S01E02.WEB-DL", "Show.S01E02.HDTV", "Show.S01.WEB"], SubSource.Ranked(list, "Show.S01E02.1080p.WEB-DL", 1, 2).Select(c => c.Name));
    }
}
