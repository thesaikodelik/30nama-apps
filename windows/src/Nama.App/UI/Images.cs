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
    /// <param name="fallback">Shown when <paramref name="url"/> gives nothing: the server lists scene pictures that are not there.</param>
    public static Grid Remote(Uri? url, string? blurhash, double decodeWidth, Stretch stretch = Stretch.UniformToFill, Uri? fallback = null)
    {
        var host = new Grid { FlowDirection = FlowDirection.LeftToRight };
        // Every layer is drawn inside its own frame; nothing is drawn past it.
        Image? stand = null;
        if (Wash(blurhash) is { } wash) host.Children.Add(stand = new Image { Source = wash, Stretch = Stretch.Fill });
        if (url is not null)
        {
            var image = new Image { Stretch = stretch, Opacity = 0 }.Springy(0.35);
            var bitmap = new BitmapImage { DecodePixelWidth = (int)Math.Max(1, decodeWidth), DecodePixelType = DecodePixelType.Physical };
            bitmap.ImageOpened += (_, _) =>
            {
                image.Opacity = 1;
                if (stand is null) return;
                // The stand-in goes once the picture is fully in. 30nama sends the same few hashes for
                // many posters, so its colour is often not the poster's, and under the picture's soft
                // outer pixel it showed as a coloured line.
                var gone = new DispatcherTimer { Interval = TimeSpan.FromSeconds(0.4) };
                gone.Tick += (_, _) =>
                {
                    gone.Stop();
                    host.Children.Remove(stand);
                };
                gone.Start();
            };
            bitmap.ImageFailed += (_, _) =>
            {
                if (fallback is null || fallback == bitmap.UriSource) return;
                bitmap.UriSource = fallback;
            };
            bitmap.UriSource = url;
            image.Source = bitmap;
            host.Children.Add(image);
        }
        return host;
    }
}
