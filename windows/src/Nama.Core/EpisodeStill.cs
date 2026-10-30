namespace Nama.Core;

/// <summary>
/// Where a still for an episode's tile is taken from the episode itself (30nama has none): a
/// quarter of the way to the credits, or five minutes in at most; and, when that frame is nearly
/// black (a fade, a title card), a second one further in. As EpisodeStills.kt in the Android app.
/// </summary>
public static class EpisodeStill
{
    /// <summary>A frame darker than this, on average, is passed over for the second point.</summary>
    public const int Dark = 24;

    /// <summary>The first point and the one tried when it is dark, in seconds.</summary>
    public static (double First, double Second) Points(double? creditsStart)
    {
        var length = creditsStart is > 0 ? creditsStart.Value : 1200;
        return (Math.Min(length * 0.25, 300), length * 0.4);
    }

    /// <summary>Mean brightness, 0 to 255, of BGRA pixels, from every eighth pixel.</summary>
    public static int Luma(ReadOnlySpan<byte> bgra)
    {
        long sum = 0, count = 0;
        for (var i = 0; i + 3 < bgra.Length; i += 32)
        {
            sum += (114 * bgra[i] + 587 * bgra[i + 1] + 299 * bgra[i + 2]) / 1000;
            count++;
        }
        return count == 0 ? 0 : (int)(sum / count);
    }
}
