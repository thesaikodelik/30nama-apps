using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class ColoursTests
{
    private static byte[] Picture(params (byte R, byte G, byte B, int Count)[] parts)
    {
        var bytes = new List<byte>();
        foreach (var (r, g, b, count) in parts)
            for (var i = 0; i < count; i++) bytes.AddRange([r, g, b, 255]);
        return [.. bytes];
    }

    [Fact]
    public void TheHueMostOfTheBrightSaturatedPartsShare()
    {
        // A dark poster with a red title over most of its lit part, and a little blue.
        var colour = Colours.Lively(Picture((200, 30, 30, 120), (30, 60, 200, 30), (20, 20, 20, 426)))!.Value;
        Assert.True(colour.Hue < 0.05 || colour.Hue > 0.95, $"red, not {colour.Hue}");
        Assert.InRange(colour.Saturation, 0.6, 0.95);
        Assert.InRange(colour.Brightness, 0.85, 1);
    }

    [Fact]
    public void GreysAndShadowHaveNoColour()
    {
        Assert.Null(Colours.Lively(Picture((128, 128, 128, 300), (10, 10, 10, 276))));
        Assert.Null(Colours.Lively(Picture((200, 30, 30, 1)))); // too little of it to count
    }
}
