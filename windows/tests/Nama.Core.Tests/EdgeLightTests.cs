using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

/// <summary>Android TV's PictureTest for the light around the picture.</summary>
public class EdgeLightTests
{
    /// <summary>A frame red on its left half and blue on its right, as BGRA bytes.</summary>
    private static byte[] Frame(int w, int h)
    {
        var bytes = new byte[w * h * 4];
        for (var y = 0; y < h; y++)
            for (var x = 0; x < w; x++)
            {
                var i = (y * w + x) * 4;
                if (x < w / 2) bytes[i + 2] = 200; else bytes[i] = 200;
                bytes[i + 3] = 255;
            }
        return bytes;
    }

    private static EdgeLight? Read(byte[] bytes, int w, int h) => EdgeLight.Of(w, h, w * 4, i => (bytes[i + 2], bytes[i + 1], bytes[i]));

    [Fact]
    public void EachEdgeTakesTheColoursAlongIt()
    {
        var light = Read(Frame(64, 24), 64, 24)!;
        Assert.Equal(EdgeLight.Zones, light.Top.Length);
        // 200 made more vivid and brighter: full, its other channels pushed down to 0.
        Assert.Equal(255, (light.Top[0] >> 16) & 0xFF);
        Assert.Equal(0, light.Top[0] & 0xFF);
        Assert.Equal(255, light.Top[^1] & 0xFF);
        Assert.Equal(light.Top, light.Bottom);
        Assert.All(light.Left, c => Assert.True(((c >> 16) & 0xFF) > 0 && (c & 0xFF) == 0));
        Assert.All(light.Right, c => Assert.True((c & 0xFF) > 0 && ((c >> 16) & 0xFF) == 0));
    }

    [Fact]
    public void TheLightGoesPartWayToTheNewColours()
    {
        var black = new EdgeLight([0, 0], [0, 0], [0], [0]);
        var white = new EdgeLight([0xFFFFFF, 0xFFFFFF], [0, 0], [0], [0]);
        Assert.Equal(127, black.Toward(white, 0.5f).Top[0] & 0xFF);
        Assert.False(black.Near(white));
        Assert.True(white.Near(white));
    }

    [Fact]
    public void AFrameTooSmallGivesNoLight()
    {
        Assert.Null(Read(new byte[64], 4, 4));
    }
}
