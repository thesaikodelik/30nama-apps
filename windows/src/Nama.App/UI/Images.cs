using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.Core;

namespace Nama.App.UI;

/// <summary>Pictures from the network, with the poster's BlurHash in their place until they arrive.</summary>
public static class Images
{
    private static readonly Dictionary<string, WriteableBitmap> Washes = [];

    /// <summary>The blurred stand-in for a picture: a few pixels, stretched.</summary>
    public static ImageSource? Wash(string? hash)
    {
        if (hash is null) return null;
        if (Washes.TryGetValue(hash, out var cached)) return cached;
        const int w = 8, h = 12;
        if (BlurHash.Pixels(hash, w, h) is not { } rgba) return null;
        // RGBA from the decoder, BGRA for the bitmap.
        for (var i = 0; i < rgba.Length; i += 4) (rgba[i], rgba[i + 2]) = (rgba[i + 2], rgba[i]);
        var bitmap = new WriteableBitmap(w, h);
        using (var stream = bitmap.PixelBuffer.AsStream()) stream.Write(rgba, 0, rgba.Length);
        bitmap.Invalidate();
        if (Washes.Count > 400) Washes.Clear();
        Washes[hash] = bitmap;
        return bitmap;
    }

    /// <summary>
    /// A picture that fills its space. Held left to right: a window laid out right to left would
    /// otherwise show every picture mirrored.
    /// </summary>
    public static Grid Remote(Uri? url, string? blurhash, double decodeWidth, Stretch stretch = Stretch.UniformToFill)
    {
        var host = new Grid { FlowDirection = FlowDirection.LeftToRight };
        // A picture is drawn with its outermost pixel half see-through; whatever is under it then
        // shows as a thin line round the frame. Drawn a pixel larger on every side, that edge falls
        // outside the frame and is cut away.
        var bleed = stretch == Stretch.UniformToFill ? new Thickness(-1) : new Thickness(0);
        // Cut to its own place, so the pixel drawn past it never shows outside, whatever holds it.
        if (stretch == Stretch.UniformToFill)
            host.SizeChanged += (_, e) => host.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, 0, e.NewSize.Width, e.NewSize.Height) };
        Image? stand = null;
        if (Wash(blurhash) is { } wash) host.Children.Add(stand = new Image { Source = wash, Stretch = Stretch.Fill, Margin = bleed });
        if (url is not null)
        {
            // Shown at once, not faded in: the last steps of a fade left the outermost row of a
            // wall of posters undrawn, a light line along its top and left until something redrew it.
            var image = new Image { Stretch = stretch, Margin = bleed };
            var bitmap = new BitmapImage { DecodePixelWidth = (int)Math.Max(1, decodeWidth), DecodePixelType = DecodePixelType.Physical };
            // The blurred stand-in is done once the picture is in.
            bitmap.ImageOpened += (_, _) => { if (stand is not null) host.Children.Remove(stand); };
            bitmap.UriSource = url;
            image.Source = bitmap;
            host.Children.Add(image);
        }
        return host;
    }
}
