namespace Nama.Core;

/// <summary>Colours taken from pictures. As SceneTint.lively on the Mac.</summary>
public static class Colours
{
    /// <summary>
    /// The liveliest colour in a picture: the hue that most of its bright, saturated parts share,
    /// made as vivid as the app's other lights, as hue, saturation and brightness, each 0...1. Null
    /// for a picture that is all greys and shadow. <paramref name="rgba"/> is RGBA8, row after row;
    /// a small copy (32 by 18) is enough.
    /// </summary>
    public static (double Hue, double Saturation, double Brightness)? Lively(ReadOnlySpan<byte> rgba)
    {
        // Twelve bins of hue, each weighted by how strong and bright its pixels are.
        Span<double> weight = stackalloc double[12];
        Span<double> sumR = stackalloc double[12], sumG = stackalloc double[12], sumB = stackalloc double[12];
        for (var i = 0; i + 3 < rgba.Length; i += 4)
        {
            double r = rgba[i] / 255.0, g = rgba[i + 1] / 255.0, b = rgba[i + 2] / 255.0;
            double high = Math.Max(r, Math.Max(g, b)), low = Math.Min(r, Math.Min(g, b)), span = high - low;
            if (!(high > 0.25) || !(span / high > 0.3)) continue;
            var bin = Math.Min(11, (int)(Hue(r, g, b, high, span) * 12));
            var strength = span / high * high;
            weight[bin] += strength;
            sumR[bin] += r * strength; sumG[bin] += g * strength; sumB[bin] += b * strength;
        }
        var best = 0;
        for (var i = 1; i < 12; i++) if (weight[i] > weight[best]) best = i;
        if (!(weight[best] > 1.2)) return null;
        double R = sumR[best] / weight[best], G = sumG[best] / weight[best], B = sumB[best] / weight[best];
        double h2 = Math.Max(R, Math.Max(G, B)), s2 = h2 - Math.Min(R, Math.Min(G, B));
        var saturation = h2 > 0 ? s2 / h2 : 0;
        return (Hue(R, G, B, h2, Math.Max(s2, 0.0001)), Math.Min(0.95, Math.Max(0.6, saturation * 1.4)), Math.Min(1, Math.Max(0.85, h2 * 1.5)));
    }

    private static double Hue(double r, double g, double b, double high, double span)
    {
        double hue;
        if (high == r) hue = (g - b) / span % 6;
        else if (high == g) hue = (b - r) / span + 2;
        else hue = (r - g) / span + 4;
        hue /= 6;
        return hue < 0 ? hue + 1 : hue;
    }
}
