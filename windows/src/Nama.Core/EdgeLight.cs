namespace Nama.Core;

/// <summary>
/// The light around the picture, as an Ambilight's on the wall behind a TV (the owner, with photos
/// of one): each edge's colours, a few zones along it, each zone read from a strip just inside the
/// picture's edge. Brought over from Android TV's <c>Picture.kt</c>, with its tests. Colours are
/// 0xRRGGBB, from the start of each edge (left to right, top to bottom).
/// </summary>
public sealed class EdgeLight(int[] top, int[] bottom, int[] left, int[] right)
{
    /// <summary>Zones along the top and bottom; the sides have fewer, by the picture's shape.</summary>
    public const int Zones = 12;

    public int[] Top { get; } = top;
    public int[] Bottom { get; } = bottom;
    public int[] Left { get; } = left;
    public int[] Right { get; } = right;

    /// <summary>
    /// Reads a frame of 4-byte pixels (<paramref name="pixel"/> gives a pixel's red, green and blue
    /// from its byte offset), <paramref name="width"/> by <paramref name="height"/>, <paramref name="stride"/>
    /// bytes a row. Each zone is the mean of a strip a tenth of the picture deep, made more vivid.
    /// Only the strips are read: a frame of the film is millions of pixels.
    /// </summary>
    public static EdgeLight? Of(int width, int height, int stride, Func<int, (int R, int G, int B)> pixel)
    {
        if (width < 8 || height < 8 || stride < width * 4) return null;
        var deepY = Math.Max(1, height / 10);
        var deepX = Math.Max(1, width / 10);
        var side = Math.Clamp(Zones * height / width, 3, Zones);
        // Every other pixel each way is enough for a mean, and a quarter of the work.
        int Mean(int ax, int ay, int bx, int by)
        {
            long r = 0, g = 0, b = 0, n = 0;
            for (var y = ay; y < by; y += 2)
                for (var x = ax; x < bx; x += 2)
                {
                    var (pr, pg, pb) = pixel(y * stride + x * 4);
                    r += pr; g += pg; b += pb; n++;
                }
            return n == 0 ? 0 : Vivid((int)(r / n), (int)(g / n), (int)(b / n));
        }
        int[] Along(int n, int span, Func<int, int, int> zone)
        {
            var zones = new int[n];
            for (var i = 0; i < n; i++) zones[i] = zone(span * i / n, span * (i + 1) / n);
            return zones;
        }
        return new EdgeLight(
            Along(Zones, width, (a, b) => Mean(a, 0, b, deepY)),
            Along(Zones, width, (a, b) => Mean(a, height - deepY, b, height)),
            Along(side, height, (a, b) => Mean(0, a, deepX, b)),
            Along(side, height, (a, b) => Mean(width - deepX, a, width, b)));
    }

    /// <summary>
    /// Saturation a third higher and a third brighter: a mean of a strip is greyer and darker than
    /// what the eye takes from it, and the light on a dark scene was hardly there (the owner).
    /// </summary>
    public static int Vivid(int r, int g, int b)
    {
        var grey = (r + g + b) / 3f;
        int Push(int c) => Math.Clamp((int)((grey + (c - grey) * 1.3f) * 1.3f), 0, 255);
        return (Push(r) << 16) | (Push(g) << 8) | Push(b);
    }

    /// <summary><paramref name="share"/> of the way from these colours to <paramref name="next"/>'s; <paramref name="next"/> where the zones differ.</summary>
    public EdgeLight Toward(EdgeLight next, float share)
    {
        int[] Mix(int[] a, int[] b)
        {
            if (a.Length != b.Length) return b;
            var mixed = new int[a.Length];
            for (var i = 0; i < a.Length; i++)
            {
                int Channel(int shift) => Math.Clamp((int)(((a[i] >> shift) & 0xFF) + (((b[i] >> shift) & 0xFF) - ((a[i] >> shift) & 0xFF)) * share), 0, 255);
                mixed[i] = (Channel(16) << 16) | (Channel(8) << 8) | Channel(0);
            }
            return mixed;
        }
        return new EdgeLight(Mix(Top, next.Top), Mix(Bottom, next.Bottom), Mix(Left, next.Left), Mix(Right, next.Right));
    }

    /// <summary>Within a step or two of <paramref name="other"/> in every channel of every zone: nothing left to show changing.</summary>
    public bool Near(EdgeLight other)
    {
        static bool Close(int[] a, int[] b)
        {
            if (a.Length != b.Length) return false;
            for (var i = 0; i < a.Length; i++)
                foreach (var shift in new[] { 16, 8, 0 })
                    if (Math.Abs(((a[i] >> shift) & 0xFF) - ((b[i] >> shift) & 0xFF)) > 2) return false;
            return true;
        }
        return Close(Top, other.Top) && Close(Bottom, other.Bottom) && Close(Left, other.Left) && Close(Right, other.Right);
    }
}
