using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class EpisodeStillTests
{
    [Fact]
    public void AQuarterOfTheWayToTheCreditsAndNoMoreThanFiveMinutes()
    {
        Assert.Equal((150, 240), EpisodeStill.Points(600));
        // A long episode: five minutes in, not a quarter of an hour.
        Assert.Equal((300, 1600), EpisodeStill.Points(4000));
        // No credits mark: as if twenty minutes long.
        Assert.Equal((300, 480), EpisodeStill.Points(null));
        Assert.Equal((300, 480), EpisodeStill.Points(0));
    }

    [Fact]
    public void ADarkFrameIsKnownByItsMeanBrightness()
    {
        static byte[] Filled(byte b, byte g, byte r) => Enumerable.Range(0, 64 * 4).Select(i => (i % 4) switch { 0 => b, 1 => g, 2 => r, _ => (byte)255 }).ToArray();
        Assert.Equal(0, EpisodeStill.Luma(Filled(0, 0, 0)));
        Assert.Equal(255, EpisodeStill.Luma(Filled(255, 255, 255)));
        // Green counts most, blue least, as the eye sees them.
        Assert.True(EpisodeStill.Luma(Filled(0, 200, 0)) > EpisodeStill.Luma(Filled(200, 0, 0)));
        Assert.True(EpisodeStill.Luma(Filled(10, 12, 14)) < EpisodeStill.Dark);
        Assert.Equal(0, EpisodeStill.Luma([]));
    }
}
