using System.Text.RegularExpressions;
using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class MovieLinesTests
{
    [Fact]
    public void AHundredMovieLines()
    {
        Assert.Equal(100, MovieLines.All.Count);
        // No line twice.
        Assert.Equal(100, MovieLines.All.Select(l => l.Text).Distinct().Count());
        Assert.All(MovieLines.All, l => Assert.True(l.Text.Length > 0 && l.Film.Length > 0 && l.Year is >= 1900 and <= 2010 && l.Directors.Count > 0));
        Assert.Equal("Chinatown", MovieLines.All[73].Film);
        Assert.Equal(["Roman Polanski"], MovieLines.All[73].Directors);
        Assert.All(MovieLines.Addressed, a => Assert.True(a.Text.Contains("{name}") && a.Rank is >= 1 and <= 100));
        var random = new Random();
        // No name: a line as it is.
        Assert.Contains(MovieLines.Pick(null, null, random), MovieLines.All);
        Assert.Contains(MovieLines.Pick("  ", null, random), MovieLines.All);
        for (var i = 0; i < 60; i++)
        {
            var line = MovieLines.Pick("Sample", "Person", random);
            Assert.Contains("Sample", line.Text);
            Assert.DoesNotContain("{name}", line.Text);
            // The film is the one the line comes from.
            Assert.Contains(MovieLines.All, l => l.Film == line.Film && l.Year == line.Year);
        }
    }

    [Fact]
    public void ALinesFilmIsFoundOn30nama()
    {
        var line = MovieLines.All[73]; // Chinatown, 1974
        Assert.Equal(["Chinatown", "Chinatown 1974"], line.SearchQueries);
        static Post Make(int id, string title, bool series = false)
        {
            var post = Post.Parse(Json.Parse($$$"""{"id":{{{id}}},"title":{"english":"{{{title}}}"},"options":{"is_series":{{{(series ? "true" : "false")}}}}}"""))!;
            // The year comes from the info block in real answers; here from the title's end is enough.
            post.Year = int.Parse(title[^4..]);
            return post;
        }
        Post[] withYears = [Make(1, "Chinatown 2019"), Make(2, "Chinatown 1974", series: true), Make(3, "Chinatown 1974"), Make(4, "Chinatown Nights 1974")];
        // The film, of that year, not the series or a namesake.
        Assert.Equal(3, line.Match(withYears)?.Id);
        Assert.Null(line.Match([withYears[0]]));
    }

    /// <summary>The lines are written in MovieLines.swift; this copy has to be the same, word for word.</summary>
    [Fact]
    public void LinesMatchTheMacApp()
    {
        var swift = File.ReadAllText(Fixtures.RepoFile(Path.Combine("30nama", "Core", "MovieLines.swift")));
        const string hint = "run: python windows/tools/gen-movie-lines.py";
        var lines = Regex.Matches(swift, @"MovieLine\(text: ""((?:[^""\\]|\\.)*)"", film: ""((?:[^""\\]|\\.)*)"", year: (\d+), directors: \[([^\]]*)\]\)")
            .Select(m => new MovieLine(m.Groups[1].Value, m.Groups[2].Value, int.Parse(m.Groups[3].Value),
                Regex.Matches(m.Groups[4].Value, @"""([^""]*)""").Select(d => d.Groups[1].Value).ToList())).ToList();
        Assert.True(lines.SequenceEqual(MovieLines.All), "the lines differ from MovieLines.swift; " + hint);
        var addressed = Regex.Matches(swift, @"^\s*\((\d+), ""((?:[^""\\]|\\.)*)""\),\s*$", RegexOptions.Multiline)
            .Select(m => (int.Parse(m.Groups[1].Value), m.Groups[2].Value)).ToList();
        Assert.True(addressed.SequenceEqual(MovieLines.Addressed), "the addressed lines differ from MovieLines.swift; " + hint);
    }
}
