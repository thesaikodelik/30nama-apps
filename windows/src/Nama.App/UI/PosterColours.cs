using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.Core;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// The colour of a title's own poster, for the glow behind a card that shows it and the lights of
/// its page: taken from the picture the card is already showing, drawn small. The blur code's
/// colour will not do, as 30nama sends the same few codes for many posters. As posterColour in
/// SceneTint.swift on the Mac.
/// </summary>
public static class PosterColours
{
    private static readonly Dictionary<int, Color?> Found = [];

    /// <summary>The poster's colour when it has been taken already.</summary>
    public static Color? Known(Post post) => Found.TryGetValue(post.Id, out var c) ? c : null;

    /// <summary>The poster's colour, or the blur code's for a picture with none to give.</summary>
    public static Color Of(Post post) => Known(post) ?? post.Tint();

    /// <summary>Takes the colour from <paramref name="picture"/>, an element showing the poster, once it has drawn it.</summary>
    public static async Task<Color> TakeAsync(Post post, FrameworkElement picture)
    {
        if (Found.TryGetValue(post.Id, out var known)) return known ?? post.Tint();
        Color? found = null;
        try
        {
            if (picture.ActualWidth > 0 && picture.XamlRoot is not null)
            {
                var small = new RenderTargetBitmap();
                await small.RenderAsync(picture, 32, 48);
                var bgra = (await small.GetPixelsAsync()).ToArray();
                // Premultiplied BGRA from the renderer; the picture is opaque, so only the order changes.
                for (var i = 0; i + 3 < bgra.Length; i += 4) (bgra[i], bgra[i + 2]) = (bgra[i + 2], bgra[i]);
                if (bgra.Length > 0 && Colours.Lively(bgra) is { } c) found = Theme.FromHsb(c.Hue, c.Saturation, c.Brightness);
            }
        }
        catch (Exception) { }
        // A picture not yet drawn is asked again next time.
        if (found is not null || picture.ActualWidth > 0) Found[post.Id] = found;
        return found ?? post.Tint();
    }
}
