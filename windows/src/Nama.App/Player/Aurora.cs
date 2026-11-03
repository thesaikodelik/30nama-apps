using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.Core;

namespace Nama.App.Player;

/// <summary>
/// The light in one bar round the picture, as Android TV draws it (<c>AmbientLight</c> in its
/// <c>PlayerScreen.kt</c>): the colours at that edge of the picture, a low glow rising off it, and
/// over it curtains like an aurora's, each in the colour under it, slowly growing and fading on its
/// own wave; the screen's own edge left black for a tenth of the bar, easing out in many small steps.
/// Drawn small on the processor into a bitmap that the page stretches over the bar, so the stretch
/// itself makes it soft. No Win2D: the app has none.
/// </summary>
internal sealed class AuroraBand
{
    /// <summary>Points along the bar and across it: the stretch smooths the rest.</summary>
    private const int Along = 240, Deep = 28;

    /// <summary>Curtains along a full width; a side bar has fewer, by its length.</summary>
    private const int Rays = 28;

    /// <summary>The black at the screen's edge, from the edge in (0) to the picture (1): Android's DARK_EDGE.</summary>
    private static readonly (float At, float Dark)[] DarkEdge =
    [
        (0f, 1f), (0.06f, 0.95f), (0.1f, 0.82f), (0.15f, 0.6f), (0.2f, 0.4f), (0.26f, 0.24f),
        (0.33f, 0.12f), (0.4f, 0.05f), (0.48f, 0f),
    ];

    private readonly bool _across;
    private readonly bool _outwardIsStart;
    private readonly int _w, _h;
    private readonly byte[] _pixels;
    private readonly float[] _dark = new float[Deep];
    private (double Start, double Span, double Length) _shape;
    private float[,]? _sideways;
    private int _rays;

    public WriteableBitmap Bitmap { get; }

    /// <param name="across">A bar above or below the picture (else beside it).</param>
    /// <param name="outwardIsStart">The screen's edge is at the bar's top (above) or its left (beside).</param>
    public AuroraBand(bool across, bool outwardIsStart)
    {
        _across = across;
        _outwardIsStart = outwardIsStart;
        (_w, _h) = across ? (Along, Deep) : (Deep, Along);
        _pixels = new byte[_w * _h * 4];
        Bitmap = new WriteableBitmap(_w, _h);
        for (var d = 0; d < Deep; d++)
        {
            // Measured from the screen's edge, as DarkEdge is.
            var e = 1f - (d + 0.5f) / Deep;
            _dark[d] = 1f - Dark(e);
        }
    }

    private static float Dark(float e)
    {
        for (var i = 1; i < DarkEdge.Length; i++)
        {
            if (e > DarkEdge[i].At) continue;
            var (a, da) = DarkEdge[i - 1];
            var (b, db) = DarkEdge[i];
            return da + (db - da) * (e - a) / (b - a);
        }
        return 0f;
    }

    /// <summary>
    /// Draws <paramref name="colours"/> (the picture's edge, start to end) at time <paramref name="t"/>.
    /// The bar is <paramref name="length"/> long, and the picture's edge runs along it from
    /// <paramref name="start"/> for <paramref name="span"/>. Without <paramref name="moving"/>, only the glow.
    /// </summary>
    public void Draw(int[] colours, double length, double start, double span, float t, bool moving)
    {
        if (colours.Length == 0 || length <= 0 || span <= 0) return;
        if (_shape != (start, span, length) || _sideways is null)
        {
            _shape = (start, span, length);
            _rays = _across ? Rays : Math.Max(6, (int)(Rays * span / Math.Max(length, 1) / 2));
            // Each curtain's soft sides, where it stands along the bar: worked out once per shape.
            _sideways = new float[_rays, Along];
            // Wide enough that the curtains run into one another, as Android's circles do: no stripes.
            var width = 1.5 / _rays;
            for (var i = 0; i < _rays; i++)
            {
                var f = (i + 0.5) / _rays;
                for (var x = 0; x < Along; x++)
                {
                    var u = (x + 0.5) / Along * length;
                    var g = ((u - start) / span - f) / width;
                    _sideways[i, x] = (float)Math.Exp(-g * g);
                }
            }
        }
        // Each curtain's height and light now, Android's waves.
        Span<float> reach = stackalloc float[_rays];
        Span<float> glow = stackalloc float[_rays];
        Span<int> tint = stackalloc int[_rays];
        for (var i = 0; i < _rays; i++)
        {
            var f = (i + 0.5f) / _rays;
            var wave = 0.5f + 0.5f * MathF.Sin(t * (0.55f + 0.23f * ((i * 7) % 5)) + i * 1.9f + f * 6f);
            var drift = 0.5f + 0.5f * MathF.Sin(t * 0.31f - f * 9f);
            reach[i] = 0.55f + 0.75f * wave * drift;
            glow[i] = moving ? 0.18f + 0.32f * wave : 0f;
            tint[i] = ColourAt(colours, f);
        }
        for (var x = 0; x < Along; x++)
        {
            var u = ((x + 0.5) / Along * length - start) / span;
            var c = ColourAt(colours, (float)Math.Clamp(u, 0, 1));
            // Beyond the picture's ends, into the corners, the glow thins out.
            var past = u < 0 ? -u : u > 1 ? u - 1 : 0;
            var ends = (float)Math.Max(0, 1 - past * span / Math.Max(length - span, 1) * 2);
            for (var d = 0; d < Deep; d++)
            {
                // 0 against the picture, 1 at the screen's edge.
                var away = (d + 0.5f) / Deep;
                var baseLight = 0.32f * MathF.Pow(1 - away, 1.5f) * ends;
                float r = ((c >> 16) & 0xFF) * baseLight, g = ((c >> 8) & 0xFF) * baseLight, b = (c & 0xFF) * baseLight;
                for (var i = 0; i < _rays; i++)
                {
                    if (glow[i] <= 0) break;
                    var up = 1 - away / reach[i];
                    if (up <= 0) continue;
                    var k = 0.6f * glow[i] * _sideways![i, x] * up * up;
                    if (k < 0.002f) continue;
                    r += ((tint[i] >> 16) & 0xFF) * k;
                    g += ((tint[i] >> 8) & 0xFF) * k;
                    b += (tint[i] & 0xFF) * k;
                }
                var keep = _dark[d];
                Put(x, d, r * keep, g * keep, b * keep);
            }
        }
        using (var stream = Bitmap.PixelBuffer.AsStream()) stream.Write(_pixels, 0, _pixels.Length);
        Bitmap.Invalidate();
    }

    /// <summary>One point, at <paramref name="along"/> and <paramref name="away"/> from the picture, into the bitmap's own rows and columns.</summary>
    private void Put(int along, int away, float r, float g, float b)
    {
        // Away from the picture is toward the screen's edge: up above it, down below, and so on.
        var across = _outwardIsStart ? Deep - 1 - away : away;
        var (x, y) = _across ? (along, across) : (across, along);
        var o = (y * _w + x) * 4;
        _pixels[o] = (byte)Math.Min(255, b);
        _pixels[o + 1] = (byte)Math.Min(255, g);
        _pixels[o + 2] = (byte)Math.Min(255, r);
        _pixels[o + 3] = 255;
    }

    private static int ColourAt(int[] colours, float f)
    {
        var x = Math.Clamp(f * colours.Length - 0.5f, 0, colours.Length - 1);
        var i = Math.Clamp((int)x, 0, Math.Max(0, colours.Length - 2));
        var j = Math.Min(i + 1, colours.Length - 1);
        var s = x - i;
        int Mix(int shift) => (int)(((colours[i] >> shift) & 0xFF) + (((colours[j] >> shift) & 0xFF) - ((colours[i] >> shift) & 0xFF)) * s);
        return (Mix(16) << 16) | (Mix(8) << 8) | Mix(0);
    }
}
