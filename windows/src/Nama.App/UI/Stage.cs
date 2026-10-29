using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Microsoft.UI.Xaml.Shapes;
using Nama.Core;
using Windows.Foundation;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// The full-bleed backdrop behind a screen. It crossfades to whatever title is on stage. Fixed
/// scrims keep the text column and the rows legible over any picture, and the dark corner under
/// the text glows in the title's own colour.
/// </summary>
public sealed class Stage : Grid
{
    private readonly Grid _pictures = new() { FlowDirection = FlowDirection.LeftToRight };
    private readonly Rectangle _glow = new() { Opacity = 0 };
    private readonly Rectangle _dim = new() { Fill = Theme.Black(1), Opacity = 0 };
    private int? _shown;
    private int _asked;

    public Stage(double dim = 0, bool deep = false)
    {
        Background = Theme.Brush(Theme.Bg0);
        Children.Add(_pictures);
        // Text side: near-opaque at the start edge, clear by two thirds across. Drawn in the
        // reading direction, so in Persian it darkens the right. Deep, under a title's page, where
        // the words run across most of the window: dark further across, so they hold over a busy
        // picture (the Android app's).
        Children.Add(new Rectangle
        {
            Fill = deep ? Linear(new Point(0, 0.5), new Point(1, 0.5), (0.97, 0), (0.86, 0.45), (0, 0.88))
                : Linear(new Point(0, 0.5), new Point(1, 0.5), (0.96, 0), (0.78, 0.30), (0, 0.68)),
        });
        // Rows side: solid at the bottom edge.
        Children.Add(new Rectangle { Fill = Linear(new Point(0.5, 1), new Point(0.5, 0), (1, 0), (0.85, 0.32), (0, 0.62)) });
        Children.Add(new Rectangle { Height = 220, VerticalAlignment = VerticalAlignment.Top, Fill = Linear(new Point(0.5, 0), new Point(0.5, 1), (0.55, 0), (0, 1)) });
        _glow.OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(1) };
        Children.Add(_glow);
        _dim.Opacity = dim;
        _dim.OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.3) };
        Children.Add(_dim);
        IsHitTestVisible = false;
    }

    public double Dim { set => _dim.Opacity = value; }

    private static LinearGradientBrush Linear(Point start, Point end, params (double Alpha, double At)[] stops)
    {
        var brush = new LinearGradientBrush { StartPoint = start, EndPoint = end };
        foreach (var (alpha, at) in stops) brush.GradientStops.Add(new GradientStop { Color = Theme.Bg0.WithAlpha(alpha), Offset = at });
        return brush;
    }

    /// <summary>Puts a title on stage. Its wide picture when it has one, otherwise its poster blurred into a wash of colour.</summary>
    public void Show(Post? post, Color? tint = null)
    {
        if (post is null) return;
        var colour = tint ?? post.Tint();
        _glow.Fill = new RadialGradientBrush
        {
            Center = new Point(0, 1), GradientOrigin = new Point(0, 1), RadiusX = 0.85, RadiusY = 1.3,
            GradientStops =
            {
                new GradientStop { Color = colour.WithAlpha(0.34), Offset = 0 },
                new GradientStop { Color = colour.WithAlpha(0.10), Offset = 0.5 },
                new GradientStop { Color = colour.WithAlpha(0), Offset = 1 },
            },
        };
        _glow.Opacity = 1;
        if (_shown == post.Id) return;
        var asked = ++_asked;
        _shown = post.Id;
        Load(post.Cover, post, asked);
    }

    /// <summary>The wide picture, or, when there is none or the one listed is not there, the poster blurred.</summary>
    private void Load(Uri? cover, Post post, int asked)
    {
        if ((cover ?? post.Poster.Big ?? post.Poster.Large) is not { } url) return;
        // In the tree from the start, unseen: a bitmap is only fetched once an image shows it.
        // A poster is read small and drawn large: softened, not a wash, so it is still the poster
        // (the Android app's), and from its top, where its picture usually is.
        var bitmap = new BitmapImage { DecodePixelWidth = cover is null ? 220 : 1920, DecodePixelType = DecodePixelType.Physical };
        var picture = new Image
        {
            Source = bitmap, Stretch = Stretch.UniformToFill, Opacity = 0, HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = cover is null ? VerticalAlignment.Top : VerticalAlignment.Center,
        };
        picture.OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.7) };
        UIElement layer = picture;
        if (cover is null)
        {
            var softened = new Grid();
            softened.Children.Add(picture);
            softened.Children.Add(new Border { Background = Theme.Brush(Theme.Bg0.WithAlpha(0.25)) });
            layer = softened;
        }
        bitmap.ImageOpened += async (_, _) =>
        {
            // A later title was put on stage while this one loaded.
            if (asked != _asked)
            {
                _pictures.Children.Remove(layer);
                return;
            }
            picture.Opacity = 1;
            // The ones before fade out under the new one, and then go.
            var old = _pictures.Children.TakeWhile(c => c != layer).ToList();
            await Task.Delay(800);
            foreach (var o in old) _pictures.Children.Remove(o);
        };
        bitmap.ImageFailed += (_, _) =>
        {
            _pictures.Children.Remove(layer);
            // A scene picture the server lists but does not have: the poster, as for a title without one.
            if (cover is not null && asked == _asked) Load(null, post, asked);
        };
        _pictures.Children.Add(layer);
        bitmap.UriSource = url;
    }
}
