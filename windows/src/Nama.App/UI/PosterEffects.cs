using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.App.Model;
using Nama.Core;
using Windows.Foundation;

using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// What a poster does beyond being clicked, as on the Mac: it leans to the pointer and catches the
/// light, opens a card about its title when the pointer rests on it, and flies to the title's page
/// when it is opened. The window hosts the card and the flight; posters only say where they are.
/// </summary>
public static class PosterEffects
{
    private static AppModel App => AppModel.Shared;

    /// <summary>The pointer has rested on a poster: its title, and where it is in the window.</summary>
    public static event Action<Post, Rect>? Peeking;

    /// <summary>A poster just clicked, to fly from; taken by the window when the title's page opens.</summary>
    private static (int Post, Rect From, ImageSource? Picture, DateTime At)? _launch;

    /// <summary>Where the poster was, and its picture already in memory, so the flight never starts blank.</summary>
    public static (Rect From, ImageSource? Picture)? TakeLaunch(int post)
    {
        var launch = _launch;
        _launch = null;
        return launch is { } l && l.Post == post && DateTime.UtcNow - l.At < TimeSpan.FromSeconds(1) ? (l.From, l.Picture) : null;
    }

    /// <summary>Where an element is in the window, in the window's own left-to-right pixels... in its units.</summary>
    public static Rect Bounds(FrameworkElement element) =>
        element.TransformToVisual(null).TransformBounds(new Rect(0, 0, element.ActualWidth, element.ActualHeight));

    public static void Launched(Post post, FrameworkElement face)
    {
        if (face.ActualWidth <= 0) return;
        // The largest picture on the card that has arrived: the poster, not its blurred stand-in.
        var picture = Descendants(face).OfType<Image>().Where(i => i.Opacity > 0 && i.Source is BitmapImage { PixelWidth: > 0 }).Select(i => i.Source).LastOrDefault();
        _launch = (post.Id, Bounds(face), picture, DateTime.UtcNow);
    }

    private static IEnumerable<DependencyObject> Descendants(DependencyObject root)
    {
        for (var i = 0; i < VisualTreeHelper.GetChildrenCount(root); i++)
        {
            var child = VisualTreeHelper.GetChild(root, i);
            yield return child;
            foreach (var deeper in Descendants(child)) yield return deeper;
        }
    }

    /// <summary>
    /// Leans the poster under the pointer, as if pressed there, and runs a glint of light across
    /// it. It moves only while the pointer does, and comes back flat when the pointer leaves.
    /// </summary>
    public static void Tilt(Button button, Grid face, double degrees = 4)
    {
        if (App.Settings.ReduceMotion) return;
        // On a holder around the face: a projection cannot share an element with the scale the
        // face grows by under the pointer.
        // Only while the pointer is on it: a projected element is drawn on a surface of its own,
        // which softens its edges by a pixel, and a resting poster should be sharp.
        var projection = new PlaneProjection();
        var holder = new Grid();
        button.Content = null;
        holder.Children.Add(face);
        button.Content = holder;
        var glint = new Border { Opacity = 0, IsHitTestVisible = false, CornerRadius = face.CornerRadius }.Springy(0.2);
        var light = new RadialGradientBrush
        {
            RadiusX = 1.1, RadiusY = 0.75,
            GradientStops =
            {
                new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.30), Offset = 0 },
                new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.07), Offset = 0.45 },
                new GradientStop { Color = Theme.Hex(0xFFFFFF, 0), Offset = 1 },
            },
        };
        glint.Background = light;
        face.Children.Add(glint);
        Storyboard? settling = null;
        button.PointerMoved += (_, e) =>
        {
            if (face.ActualWidth <= 0) return;
            settling?.Stop();
            holder.Projection ??= projection;
            var at = e.GetCurrentPoint(face).Position;
            // Physical left to right: a window laid out right to left mirrors the pointer's x.
            var across = Math.Clamp(at.X / face.ActualWidth, 0, 1) - 0.5;
            var down = Math.Clamp(at.Y / face.ActualHeight, 0, 1) - 0.5;
            projection.RotationX = down * 2 * degrees;
            projection.RotationY = across * 2 * degrees * (face.FlowDirection == FlowDirection.RightToLeft ? -1 : 1);
            var origin = new Point(across + 0.5, down + 0.5);
            light.Center = origin;
            light.GradientOrigin = origin;
            glint.Opacity = 1;
        };
        button.PointerExited += (_, _) =>
        {
            glint.Opacity = 0;
            settling = Settle(projection);
            settling.Completed += (_, _) => holder.Projection = null;
            settling.Begin();
        };
    }

    private static Storyboard Settle(PlaneProjection projection)
    {
        var board = new Storyboard();
        foreach (var property in new[] { "RotationX", "RotationY" })
        {
            var back = new DoubleAnimation { To = 0, Duration = TimeSpan.FromMilliseconds(450), EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } };
            Storyboard.SetTarget(back, projection);
            Storyboard.SetTargetProperty(back, property);
            board.Children.Add(back);
        }
        return board;
    }

    /// <summary>
    /// The light a poster throws under it, as on the Mac: a faint dark shadow at rest, and under
    /// the pointer a glow in the poster's own colour that grows with it.
    /// </summary>
    public static void Glow(Button button, FrameworkElement face, Color tint)
    {
        var rest = new UI.Glow.Look(Colors.Black, 0.4f, 8, 4);
        var lifted = new UI.Glow.Look(tint, 0.55f, 32, 14);
        var glow = new UI.Glow(rest, grow: 1.07f);
        var content = (FrameworkElement)button.Content;
        button.Content = null;
        content.Width = face.Width;
        content.Height = face.Height;
        button.Content = UI.Glow.Behind(content, glow);
        button.PointerEntered += (_, _) => glow.To(lifted, lifted: true);
        button.PointerExited += (_, _) => glow.To(rest, lifted: false);
    }

    /// <summary>
    /// The pointer resting on the poster opens the card about its title. Not at once: someone still
    /// looking along the row should not have cards opening under them.
    /// </summary>
    public static void Peek(Button button, FrameworkElement face, Post post)
    {
        var timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(1300) };
        timer.Tick += (_, _) =>
        {
            timer.Stop();
            if (face.ActualWidth > 0 && face.XamlRoot is not null) Peeking?.Invoke(post, Bounds(face));
        };
        button.PointerEntered += (_, _) => timer.Start();
        button.PointerExited += (_, _) => timer.Stop();
        button.PointerPressed += (_, _) => timer.Stop();
        button.Unloaded += (_, _) => timer.Stop();
    }
}
