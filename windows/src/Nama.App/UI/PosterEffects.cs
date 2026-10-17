using Microsoft.UI.Xaml.Hosting;
using System.Numerics;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
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
    /// Turned in 3D by the compositor, with perspective, after a spring: not a XAML projection,
    /// which draws the poster on a surface of its own and softens its edges.
    /// </summary>
    public static void Tilt(Button button, Grid face, double degrees = 4)
    {
        if (App.Settings.ReduceMotion) return;
        // On a holder: the face grows under the pointer by XAML's own scale, which the compositor's
        // turn may not share an element with.
        var holder = new Grid();
        button.Content = null;
        holder.Children.Add(face);
        button.Content = holder;
        Lean(button, face, holder, degrees);
    }

    /// <summary>
    /// The lean itself: <paramref name="turned"/> is turned toward the pointer over
    /// <paramref name="source"/>, and <paramref name="face"/> catches the glint. The turned element
    /// must carry none of XAML's own transforms.
    /// </summary>
    public static void Lean(UIElement source, Grid face, FrameworkElement turned, double degrees)
    {
        if (App.Settings.ReduceMotion) return;
        var holder = turned;
        var button = source;
        var visual = ElementCompositionPreview.GetElementVisual(holder);
        var compositor = visual.Compositor;
        var lean = visual.Properties;
        lean.InsertScalar("X", 0);
        lean.InsertScalar("Y", 0);
        holder.SizeChanged += (_, e) =>
        {
            var centre = new Vector3((float)e.NewSize.Width / 2, (float)e.NewSize.Height / 2, 0);
            var matrix = compositor.CreateExpressionAnimation(
                "Matrix4x4.CreateTranslation(-Centre) * Matrix4x4.CreateFromAxisAngle(Vector3(1, 0, 0), Lean.X) * Matrix4x4.CreateFromAxisAngle(Vector3(0, 1, 0), Lean.Y) * Depth * Matrix4x4.CreateTranslation(Centre)");
            matrix.SetVector3Parameter("Centre", centre);
            // Perspective strong enough that a lean of a few degrees is seen, as on the Mac.
            matrix.SetMatrix4x4Parameter("Depth", new Matrix4x4(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, -1f / 280, 0, 0, 0, 1));
            matrix.SetReferenceParameter("Lean", lean);
            visual.StartAnimation("TransformMatrix", matrix);
        };
        void LeanTo(float x, float y)
        {
            // Slow to follow, so the card leans after the pointer rather than snapping to it: interactiveSpring(0.45, 0.85).
            foreach (var (name, value) in new[] { ("X", x), ("Y", y) })
            {
                var spring = compositor.CreateSpringScalarAnimation();
                spring.FinalValue = value;
                spring.Period = TimeSpan.FromSeconds(0.45 / (2 * Math.PI));
                spring.DampingRatio = 0.85f;
                lean.StartAnimation(name, spring);
            }
        }

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
        var radians = (float)(degrees * Math.PI / 180);
        button.PointerMoved += (_, e) =>
        {
            if (face.ActualWidth <= 0) return;
            var at = e.GetCurrentPoint(face).Position;
            var across = (float)(Math.Clamp(at.X / face.ActualWidth, 0, 1) - 0.5);
            var down = (float)(Math.Clamp(at.Y / face.ActualHeight, 0, 1) - 0.5);
            // A window laid out right to left mirrors the pointer's x.
            var side = face.FlowDirection == FlowDirection.RightToLeft ? -1 : 1;
            // Pressed where the pointer is: that corner goes back, the far one comes forward.
            LeanTo(-down * 2 * radians, across * 2 * radians * side);
            var origin = new Point(across + 0.5, down + 0.5);
            light.Center = origin;
            light.GradientOrigin = origin;
            glint.Opacity = 1;
        };
        button.PointerExited += (_, _) =>
        {
            glint.Opacity = 0;
            LeanTo(0, 0);
        };
    }

    /// <summary>
    /// The light a poster throws under it, as on the Mac: a faint dark shadow at rest, and under
    /// the pointer a glow in the poster's own colour that grows with it.
    /// </summary>
    public static void Glow(Button button, FrameworkElement face, Color tint) =>
        Glow(button, face, new UI.Glow.Look(Colors.Black, 0.4f, 8, 4), new UI.Glow.Look(tint, 0.55f, 32, 14), 1.07f);

    public static void Glow(Button button, FrameworkElement face, UI.Glow.Look rest, UI.Glow.Look lifted, float grow)
    {
        var glow = new UI.Glow(rest, grow);
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
