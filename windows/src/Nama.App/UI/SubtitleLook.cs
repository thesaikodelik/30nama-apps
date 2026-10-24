using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Nama.App.Model;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// A subtitle line as the settings make it: its colour, and its plate or outline. The player draws
/// its lines with this, and the settings show a sample with it, so the two never differ.
/// </summary>
public static class SubtitleLook
{
    public static Color Ink(SubtitleColor colour) => colour switch
    {
        SubtitleColor.Yellow => Color.FromArgb(255, 255, 230, 77),
        SubtitleColor.Cyan => Color.FromArgb(255, 115, 237, 255),
        SubtitleColor.Green => Color.FromArgb(255, 140, 242, 140),
        _ => Colors.White,
    };

    /// <summary><paramref name="text"/> at <paramref name="size"/>, styled by <paramref name="s"/>.</summary>
    public static FrameworkElement Line(string text, double size, Settings s, bool wrap = true)
    {
        TextBlock Copy(Color ink) => new()
        {
            Text = text, FontSize = size, FontFamily = Theme.Font(FontWeights.SemiBold), FontWeight = FontWeights.SemiBold,
            Foreground = Theme.Brush(ink), TextAlignment = TextAlignment.Center, TextWrapping = wrap ? TextWrapping.Wrap : TextWrapping.NoWrap,
            TextTrimming = wrap ? TextTrimming.None : TextTrimming.CharacterEllipsis,
            HorizontalAlignment = HorizontalAlignment.Center, IsTextScaleFactorEnabled = false,
        };
        var plate = s.SubtitleBackground switch { SubtitleBackground.Half => 0.5, SubtitleBackground.Solid => 1.0, _ => 0 };
        var holder = new Grid { Padding = new Thickness(size * 0.5, size * 0.22, size * 0.5, size * 0.22), CornerRadius = new CornerRadius(size * 0.3), HorizontalAlignment = HorizontalAlignment.Center };
        if (plate > 0) holder.Background = Theme.Black(plate);
        else
        {
            // No plate: an outline, from copies of the line in black just around it.
            var d = Math.Max(1, size / 22);
            foreach (var (x, y) in new[] { (-d, 0.0), (d, 0.0), (0.0, -d), (0.0, d), (-d, -d), (d, d), (-d, d), (d, -d) })
            {
                var shadow = Copy(Colors.Black);
                shadow.Opacity = 0.85;
                shadow.RenderTransform = new TranslateTransform { X = x, Y = y + d * 0.5 };
                holder.Children.Add(shadow);
            }
        }
        holder.Children.Add(Copy(Ink(s.SubtitleColor)));
        return holder;
    }

    /// <summary>
    /// The sample under the subtitle settings: a line at half its size in the player, over a bright,
    /// busy picture, where the choice of background actually matters. As the Mac's.
    /// </summary>
    public static FrameworkElement Sample(Settings s, string text)
    {
        var picture = new LinearGradientBrush { StartPoint = new Windows.Foundation.Point(0, 0), EndPoint = new Windows.Foundation.Point(1, 1) };
        foreach (var (hex, at) in new[] { (0xF2A65Au, 0.0), (0xD9567Bu, 1 / 3.0), (0x5B6CFFu, 2 / 3.0), (0x3FC1C9u, 1.0) })
            picture.GradientStops.Add(new GradientStop { Color = Theme.Hex(hex), Offset = at });
        var line = Line(text, s.SubtitleSize * 0.5, s, wrap: false);
        line.VerticalAlignment = VerticalAlignment.Center;
        line.Margin = new Thickness(14, 0, 14, 0);
        return new Grid
        {
            Height = 96, CornerRadius = new CornerRadius(12), Background = picture, Margin = new Thickness(0, 10, 0, 10),
            Children = { line },
        };
    }
}
