using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class WindowPlaceTests
{
    private static readonly (int, int, int, int)[] OneScreen = [(0, 0, 1920, 1040)];

    [Fact]
    public void KeptAndReadBack()
    {
        var place = new WindowPlace(100, 80, 1440, 900, true);
        Assert.Equal("100,80,1440,900,1", place.Text);
        Assert.Equal(place, WindowPlace.Parse(place.Text));
        Assert.Null(WindowPlace.Parse(null));
        Assert.Null(WindowPlace.Parse("1,2,3"));
        Assert.Null(WindowPlace.Parse("0,0,50,50,0")); // too small to have been a real window
    }

    [Fact]
    public void OnlyWhereItCanStillBeTakenHoldOf()
    {
        Assert.True(new WindowPlace(100, 80, 1440, 900, false).IsOn(OneScreen));
        // Partly off to the side is still in reach.
        Assert.True(new WindowPlace(1700, 80, 1440, 900, false).IsOn(OneScreen));
        // On a second monitor that is no longer there.
        Assert.False(new WindowPlace(2200, 80, 1440, 900, false).IsOn(OneScreen));
        // Its top above the screen: the title bar cannot be reached.
        Assert.False(new WindowPlace(100, -400, 1440, 900, false).IsOn(OneScreen));
        // A screen to the left, at negative coordinates.
        Assert.True(new WindowPlace(-1800, 50, 1440, 900, false).IsOn([(-1920, 0, 1920, 1080), (0, 0, 1920, 1040)]));
    }
}
