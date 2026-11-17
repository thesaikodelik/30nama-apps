using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class NewTitlesTests
{
    private static Post P(int id) => Post.Parse(Json.Parse($$$"""{"id":{{{id}}},"title":{"english":"Film {{{id}}}"}}"""))!;

    private static List<Post> Ps(params int[] ids) => ids.Select(P).ToList();

    [Fact]
    public void TheFirstLookOnlyRemembers()
    {
        var titles = new NewTitles();
        Assert.Empty(titles.Look(Ps(5, 4, 3)));
        Assert.True(titles.Seeded);
        Assert.Equal([7, 6], titles.Look(Ps(7, 6, 5, 4)).Select(p => p.Id));
        Assert.Empty(titles.Look(Ps(7, 6, 5)));
    }

    [Fact]
    public void NoAnswerChangesNothing()
    {
        var titles = new NewTitles();
        Assert.Empty(titles.Look([]));
        Assert.False(titles.Seeded); // still the first look to come
    }

    [Fact]
    public void KeptAcrossRunsAndCapped()
    {
        var titles = new NewTitles();
        titles.Look(Enumerable.Range(1, NewTitles.Kept + 50).Reverse().Select(P).ToList());
        Assert.Equal(NewTitles.Kept, titles.Seen.Count);
        var again = new NewTitles(titles.Seen, titles.Seeded);
        Assert.Equal([1000], again.Look(Ps(1000, NewTitles.Kept + 50)).Select(p => p.Id)); // the newest kept is still known
    }

    [Fact]
    public void TheLineNamesThreeAndCountsTheRest()
    {
        Assert.Equal("Film 1, Film 2", NewTitles.Line(Ps(1, 2), Lang.En, n => $"+{n}"));
        Assert.Equal("Film 1، Film 2، Film 3 +2", NewTitles.Line(Ps(1, 2, 3, 4, 5), Lang.Fa, n => $"+{n}"));
    }
}
