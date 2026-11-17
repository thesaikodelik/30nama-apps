using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class RecentSearchesTests
{
    [Fact]
    public void NewestFirstOnceEachAndEightAtMost()
    {
        var recent = new RecentSearches();
        foreach (var q in new[] { "heat", "dune", "Heat", "  the   matrix " }) recent.Add(q);
        Assert.Equal(["the matrix", "Heat", "dune"], recent.Items); // the same search again moves to the top, as last typed
        for (var i = 0; i < 10; i++) recent.Add($"film {i}");
        Assert.Equal(RecentSearches.Most, recent.Items.Count);
        Assert.Equal("film 9", recent.Items[0]);
    }

    [Fact]
    public void ArabicLettersAreThePersianOnes()
    {
        var recent = new RecentSearches();
        recent.Add("مكانيكي");
        recent.Add("مکانیکی");
        Assert.Equal(["مکانیکی"], recent.Items);
    }

    [Fact]
    public void TooShortIsNotKeptAndRemovingWorks()
    {
        var recent = new RecentSearches(["dune", "", "heat"]);
        recent.Add("a");
        Assert.Equal(["dune", "heat"], recent.Items);
        recent.Remove("DUNE");
        Assert.Equal(["heat"], recent.Items);
        recent.Clear();
        Assert.Empty(recent.Items);
    }
}
