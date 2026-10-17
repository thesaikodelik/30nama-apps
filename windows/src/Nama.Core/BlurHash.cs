namespace Nama.Core;

/// <summary>
/// Decoder for BlurHash strings (https://blurha.sh), which the API sends as <c>image.poster.preview</c>.
/// Produces a tiny RGBA bitmap that is stretched as a placeholder until the real poster arrives.
/// </summary>
public static class BlurHash
{
    private const string Alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";

    private static int? Decode83(ReadOnlySpan<char> s)
    {
        var value = 0;
        foreach (var c in s)
        {
            var d = Alphabet.IndexOf(c);
            if (d < 0) return null;
            value = value * 83 + d;
        }
        return value;
    }

    private static double Linear(int srgb)
    {
        var v = srgb / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.Pow((v + 0.055) / 1.055, 2.4);
    }

    private static byte Srgb(double linear)
    {
        var v = Math.Clamp(linear, 0, 1);
        var s = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.Pow(v, 1 / 2.4) - 0.055;
        return (byte)Math.Clamp(Math.Round(s * 255, MidpointRounding.AwayFromZero), 0, 255);
    }

    private static double SignedPow(double v, double e) => v < 0 ? -Math.Pow(-v, e) : Math.Pow(v, e);

    /// <summary>The image's average colour (the hash's DC component), each channel 0...1 in sRGB.</summary>
    public static (double Red, double Green, double Blue)? Average(string hash)
    {
        if (hash.Length < 6 || Decode83(hash.AsSpan(2, 4)) is not { } v) return null;
        return ((v >> 16) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0);
    }

    /// <summary>RGBA8 pixels, row-major, <c>width * height * 4</c> bytes. Null for a malformed hash.</summary>
    public static byte[]? Pixels(string hash, int width, int height, double punch = 1)
    {
        if (hash.Length < 6 || width <= 0 || height <= 0 || Decode83(hash.AsSpan(0, 1)) is not { } sizeFlag) return null;
        int numY = sizeFlag / 9 + 1, numX = sizeFlag % 9 + 1;
        if (hash.Length != 4 + 2 * numX * numY || Decode83(hash.AsSpan(1, 1)) is not { } quantisedMax) return null;
        var maxValue = (quantisedMax + 1) / 166.0 * punch;

        var colors = new (double R, double G, double B)[numX * numY];
        for (var i = 0; i < colors.Length; i++)
        {
            if (i == 0)
            {
                if (Decode83(hash.AsSpan(2, 4)) is not { } v) return null;
                colors[i] = (Linear(v >> 16), Linear((v >> 8) & 255), Linear(v & 255));
            }
            else
            {
                if (Decode83(hash.AsSpan(4 + i * 2, 2)) is not { } v) return null;
                double Ac(int q) => SignedPow((q - 9) / 9.0, 2) * maxValue;
                colors[i] = (Ac(v / (19 * 19)), Ac(v / 19 % 19), Ac(v % 19));
            }
        }

        var output = new byte[width * height * 4];
        Array.Fill(output, (byte)255);
        for (var y = 0; y < height; y++)
        {
            for (var x = 0; x < width; x++)
            {
                double r = 0, g = 0, b = 0;
                for (var j = 0; j < numY; j++)
                {
                    var by = Math.Cos(Math.PI * y * j / height);
                    for (var i = 0; i < numX; i++)
                    {
                        var basis = Math.Cos(Math.PI * x * i / width) * by;
                        var c = colors[i + j * numX];
                        r += c.R * basis; g += c.G * basis; b += c.B * basis;
                    }
                }
                var o = (y * width + x) * 4;
                output[o] = Srgb(r); output[o + 1] = Srgb(g); output[o + 2] = Srgb(b);
            }
        }
        return output;
    }
}
