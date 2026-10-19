using System.Numerics;
using Microsoft.UI;
using Microsoft.UI.Text;
using FontWeight = Windows.UI.Text.FontWeight;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Markup;
using Microsoft.UI.Xaml.Media;
using Nama.Core;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// Design tokens, the same as the Mac app's: dark, glassy chrome over full-bleed artwork, the
/// title on stage lending its colour to the light around it, and one red accent for the primary
/// action and progress.
/// </summary>
public static class Theme
{
    public static readonly Color Bg0 = Hex(0x07080B);
    public static readonly Color Bg1 = Hex(0x0E1016);
    public static readonly Color Surface = Hex(0x161922);
    public static readonly Color Surface2 = Hex(0x20242F);
    public static readonly Color Text1 = Hex(0xF4F1EA);
    public static readonly Color Text2 = Hex(0xF4F1EA, 0.72);
    public static readonly Color Text3 = Hex(0xF4F1EA, 0.46);
    public static readonly Color Accent = Hex(0xF0454A);
    public static readonly Color Good = Hex(0x62D39B);
    public static readonly Color FocusInk = Hex(0x0B0C10);

    /// <summary>Horizontal inset of content from the window edge.</summary>
    public const double Edge = 48;
    /// <summary>How far the bar of sections sits below the top of the window, clear of its edge.</summary>
    public const double BarTop = 24;
    public const double PosterWidth = 156;
    /// <summary>
    /// How far from the end of a wall of posters the next page is asked for: about three rows, so
    /// it is in by the time the wall runs out.
    /// </summary>
    public const double Prefetch = 3 * (PosterWidth * 1.5 + 20);
    public const double Radius = 12;

    public static Color Hex(uint hex, double alpha = 1) =>
        Color.FromArgb((byte)Math.Round(alpha * 255), (byte)(hex >> 16), (byte)(hex >> 8), (byte)hex);

    public static Color WithAlpha(this Color c, double alpha) => Color.FromArgb((byte)Math.Round(alpha * 255), c.R, c.G, c.B);

    public static SolidColorBrush Brush(Color c) => new(c);
    public static SolidColorBrush Brush(Color c, double alpha) => new(c.WithAlpha(alpha));
    public static SolidColorBrush White(double alpha) => new(Color.FromArgb((byte)Math.Round(alpha * 255), 255, 255, 255));
    public static SolidColorBrush Black(double alpha) => new(Color.FromArgb((byte)Math.Round(alpha * 255), 0, 0, 0));

    /// <summary>A lively version of the poster's average colour, used for the glow around the title.</summary>
    public static Color Tint(this Post post)
    {
        if (post.Poster.Blurhash is not { } hash || BlurHash.Average(hash) is not { } c) return Accent;
        var high = Math.Max(c.Red, Math.Max(c.Green, c.Blue));
        var low = Math.Min(c.Red, Math.Min(c.Green, c.Blue));
        var span = high - low;
        var s = high > 0 ? span / high : 0;
        var h = 0.0;
        if (span > 0)
        {
            if (high == c.Red) h = (c.Green - c.Blue) / span % 6;
            else if (high == c.Green) h = (c.Blue - c.Red) / span + 2;
            else h = (c.Red - c.Green) / span + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        // Greys have no meaningful hue; keep them as a cool white instead of a random colour.
        if (s <= 0.1) return FromHsb(0.62, 0.12, 0.95);
        return FromHsb(h, Math.Min(0.95, Math.Max(0.55, s * 1.5)), Math.Min(1, Math.Max(0.8, high * 1.7)));
    }

    public static Color FromHsb(double h, double s, double b)
    {
        var i = (int)Math.Floor(h * 6) % 6;
        var f = h * 6 - Math.Floor(h * 6);
        double p = b * (1 - s), q = b * (1 - f * s), t = b * (1 - (1 - f) * s);
        var (r, g, bl) = i switch { 0 => (b, t, p), 1 => (q, b, p), 2 => (p, b, t), 3 => (p, q, b), 4 => (t, p, b), _ => (b, p, q) };
        return Color.FromArgb(255, (byte)(r * 255), (byte)(g * 255), (byte)(bl * 255));
    }

    // MARK: Type

    /// <summary>Vazirmatn covers Persian and Latin in one family, so mixed strings stay visually consistent.</summary>
    // Vazirmatn is the work of Saber Rastikerdar (صابر راستی‌کردار), who gave it to everyone, free,
    // and who has since passed away. Every Persian word in this app is set in his letters. With
    // gratitude, and in his memory.
    private static readonly Dictionary<int, FontFamily> Families = [];

    public static FontFamily Font(FontWeight weight)
    {
        var file = weight.Weight switch
        {
            >= 800 => "ExtraBold",
            >= 700 => "Bold",
            >= 600 => "SemiBold",
            >= 500 => "Medium",
            _ => "Regular",
        };
        if (!Families.TryGetValue(weight.Weight, out var family))
            Families[weight.Weight] = family = new FontFamily($"ms-appx:///Fonts/Vazirmatn-{file}.ttf#Vazirmatn");
        return family;
    }

    public static TextBlock Text(string text, double size, FontWeight? weight = null, Color? color = null, int lines = 1)
    {
        var w = weight ?? FontWeights.Normal;
        var t = new TextBlock
        {
            Text = text,
            FontSize = size,
            FontFamily = Font(w),
            FontWeight = w,
            Foreground = Brush(color ?? Text1),
            TextTrimming = TextTrimming.CharacterEllipsis,
            TextWrapping = lines == 1 ? TextWrapping.NoWrap : TextWrapping.WrapWholeWords,
            MaxLines = lines,
            IsTextScaleFactorEnabled = false,
        };
        return t;
    }

    // MARK: Glass

    /// <summary>Translucent surface: in-app acrylic with a thin light rim, as the Mac app's material.</summary>
    public static Brush GlassFill() => new Microsoft.UI.Xaml.Media.AcrylicBrush
    {
        TintColor = Hex(0x1A1C22),
        TintOpacity = 0.55,
        TintLuminosityOpacity = 0.45,
        FallbackColor = Hex(0x1A1C22, 0.85),
    };

    public static Brush GlassRim => White(0.14);

    // MARK: Motion

    /// <summary>Has changes to scale and opacity glide instead of jump, as SwiftUI's springs do on the Mac.</summary>
    public static T Springy<T>(this T element, double seconds = 0.25) where T : UIElement
    {
        element.ScaleTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(seconds) };
        element.OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(seconds) };
        element.TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(seconds) };
        return element;
    }

    /// <summary>Scales about the element's centre.</summary>
    public static void ScaleTo(this FrameworkElement element, double scale)
    {
        element.CenterPoint = new Vector3((float)element.ActualWidth / 2, (float)element.ActualHeight / 2, 0);
        element.Scale = new Vector3((float)scale, (float)scale, 1);
    }

    // MARK: Buttons

    private static ControlTemplate? _bare;

    /// <summary>
    /// A button with no look of its own: the content draws itself, and the button keeps what makes
    /// it a button (the keyboard, the screen reader, Click).
    /// </summary>
    public static Button Bare(UIElement content, Action click)
    {
        _bare ??= (ControlTemplate)XamlReader.Load(
            "<ControlTemplate xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\" TargetType=\"Button\">" +
            "<ContentPresenter Content=\"{TemplateBinding Content}\" Background=\"Transparent\" " +
            "HorizontalContentAlignment=\"Stretch\" VerticalContentAlignment=\"Stretch\"/></ControlTemplate>");
        var b = new Button { Template = _bare, Content = content, Padding = new Thickness(0), UseSystemFocusVisuals = true };
        b.Click += (_, _) => click();
        return b;
    }
}
