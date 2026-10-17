using System.Numerics;
using Microsoft.UI;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Hosting;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// The opening title, as on the Mac (Splash.swift), with its numbers: a sheet of clear glass over
/// the app, so the home page is seen softly through it; the logo's cube and then its name spring
/// onto the glass, a red line draws under them, and a glint runs across the glass and the letters.
/// To leave, the line runs out to both edges and the glass parts along it like a curtain.
/// </summary>
public sealed class SplashCurtain : Grid
{
    /// <summary>How long the exit takes, from <see cref="Leave"/> to nothing being left on screen.</summary>
    public static readonly TimeSpan ExitDuration = TimeSpan.FromSeconds(1.15);

    internal const double LogoHeight = 190;
    /// <summary>The logo's own proportions (1200 × 416).</summary>
    internal const double LogoWidth = LogoHeight * 1200 / 416;
    /// <summary>Where the cube ends and the name begins, as a fraction of the logo's width.</summary>
    internal const double LogoSplit = 0.29;
    /// <summary>The logo sits a little above the middle, and the seam just under it.</summary>
    internal const double LogoLift = -22, SeamBelowCentre = 84;
    internal static readonly Uri LogoSource = new("ms-appx:///Assets/Logo.png");

    private readonly Half _top, _bottom;
    private readonly Grid _line = new() { Width = 380, Height = 30, VerticalAlignment = VerticalAlignment.Top, HorizontalAlignment = HorizontalAlignment.Center, IsHitTestVisible = false };
    private bool _leaving;

    public SplashCurtain()
    {
        FlowDirection = FlowDirection.LeftToRight;
        _top = new Half(upper: true);
        _bottom = new Half(upper: false);
        Children.Add(_top);
        Children.Add(_bottom);
        // The red line and the light it throws, as the Mac draws it with a shadow of its own colour.
        _line.Children.Add(new Border
        {
            Background = new RadialGradientBrush
            {
                RadiusX = 0.5, RadiusY = 0.5,
                GradientStops = { new GradientStop { Color = Theme.Accent.WithAlpha(0.45), Offset = 0 }, new GradientStop { Color = Theme.Accent.WithAlpha(0), Offset = 1 } },
            },
        });
        _line.Children.Add(new Border
        {
            Width = 320, Height = 3, CornerRadius = new CornerRadius(1.5),
            Background = Across(Colors.Transparent, Theme.Accent, Theme.Hex(0xFF8A70), Theme.Accent, Colors.Transparent),
        });
        Children.Add(_line);
        var line = Motion.Visual(_line);
        line.CenterPoint = new Vector3(190, 15, 0);
        line.Scale = new Vector3(0, 1, 1);
        SizeChanged += (_, e) => Lay(e.NewSize);
        Loaded += (_, _) => Begin();
    }

    private void Lay(Windows.Foundation.Size size)
    {
        var seam = size.Height / 2 + SeamBelowCentre;
        _top.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, 0, size.Width, seam) };
        _bottom.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, seam, size.Width, Math.Max(0, size.Height - seam)) };
        _line.Margin = new Thickness(0, seam - 15, 0, 0);
    }

    private void Begin()
    {
        _top.Light();
        _bottom.Light();
        // The line draws out under the logo: spring(response 0.9, damping 0.8), 0.75 s in.
        Motion.Spring(_line, "Scale", Vector3.One, 0.9, 0.8, 0.75);
    }

    /// <summary>The page behind is there to be seen: the dark over it clears.</summary>
    public void Ready()
    {
        _top.Clear();
        _bottom.Clear();
    }

    /// <summary>Runs out the line, parts the glass, and takes itself away when it is done.</summary>
    public void Leave(Action gone)
    {
        if (_leaving) return;
        _leaving = true;
        IsHitTestVisible = false;
        var seam = ActualHeight / 2 + SeamBelowCentre;
        // The line runs to both edges and fades (ease in-out, 0.42 s)...
        Motion.Ease(_line, "Scale", new Vector3((float)(ActualWidth * 1.1 / 320), 1, 1), 0.42, 0, Motion.InOut);
        Motion.FadeTo(_line, 0, 0.42, 0);
        // ...and the sheets part along it: timing curve (0.7, 0, 0.2, 1) over 0.75 s, 0.38 s in.
        _top.Part(-seam);
        _bottom.Part(ActualHeight - seam);
        var timer = new DispatcherTimer { Interval = ExitDuration };
        timer.Tick += (_, _) =>
        {
            timer.Stop();
            gone();
        };
        timer.Start();
    }

    internal static LinearGradientBrush Across(params Color[] colors) => Gradient(new(0, 0.5), new(1, 0.5), colors);

    internal static LinearGradientBrush Gradient(Windows.Foundation.Point from, Windows.Foundation.Point to, params Color[] colors)
    {
        var brush = new LinearGradientBrush { StartPoint = from, EndPoint = to };
        for (var i = 0; i < colors.Length; i++) brush.GradientStops.Add(new GradientStop { Color = colors[i], Offset = (double)i / (colors.Length - 1) });
        return brush;
    }

    /// <summary>Glass, logo and its reflection: drawn twice, once for each half of the curtain.</summary>
    private sealed class Half : Grid
    {
        private readonly Border _dark = new() { Background = Theme.Black(0.82), OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.9) } };
        private readonly Grid _sheen = new() { IsHitTestVisible = false };
        private readonly Border _glint;
        private readonly Grid _logo;
        private readonly Image _cube, _name;
        private readonly Grid _edge;
        private readonly Border _reflection;
        private readonly CompositionLinearGradientBrush _letterGlint;

        public Half(bool upper)
        {
            Children.Add(_dark);
            // Crystal glass: the app behind is blurred, yet with a little of it still sharp through
            // the blur, so it reads as thick glass rather than frosted.
            Children.Add(new Border
            {
                Opacity = 0.85,
                Background = new AcrylicBrush { TintColor = Colors.Black, TintOpacity = 0, TintLuminosityOpacity = 0.01, FallbackColor = Colors.Transparent },
            });
            // A shade only where the logo sits, so it holds its own over whatever the app shows.
            Children.Add(new Border
            {
                Background = new RadialGradientBrush
                {
                    RadiusX = 0.55, RadiusY = 0.9,
                    GradientStops = { new GradientStop { Color = Theme.Hex(0, 0.5), Offset = 0 }, new GradientStop { Color = Theme.Hex(0, 0.15), Offset = 1 } },
                },
            });
            BuildSheen(out _glint);
            Children.Add(_sheen);
            Motion.Visual(_sheen).Opacity = 0;

            // The logo's reflection in the glass below it, fading as it goes down.
            var reflection = _reflection = new Border
            {
                Width = LogoWidth, Height = LogoHeight, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 2 * (LogoLift + LogoHeight + 26), 0, 0), IsHitTestVisible = false,
            };
            Children.Add(reflection);

            _logo = new Grid
            {
                Width = LogoWidth, Height = LogoHeight, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
                Margin = new Thickness(0, 2 * LogoLift, 0, 0), IsHitTestVisible = false,
            };
            // The cube arrives a beat before the name.
            _cube = Part(0, LogoSplit);
            _name = Part(LogoSplit, 1);
            _logo.Children.Add(_cube);
            _logo.Children.Add(_name);
            var letters = new Border();
            _logo.Children.Add(letters);
            Children.Add(_logo);

            // Drawn by the compositor: a white silhouette of the logo seen through a moving slit,
            // and the reflection, both cut to the logo's own shape.
            var compositor = Motion.Visual(this).Compositor;
            var surface = LoadedImageSurface.StartLoadFromUri(LogoSource);
            _letterGlint = compositor.CreateLinearGradientBrush();
            _letterGlint.MappingMode = CompositionMappingMode.Absolute;
            _letterGlint.StartPoint = new Vector2(-75, 0);
            _letterGlint.EndPoint = new Vector2(75, 0);
            _letterGlint.ColorStops.Add(compositor.CreateColorGradientStop(0, Color.FromArgb(0, 255, 255, 255)));
            _letterGlint.ColorStops.Add(compositor.CreateColorGradientStop(0.5f, Color.FromArgb(255, 255, 255, 255)));
            _letterGlint.ColorStops.Add(compositor.CreateColorGradientStop(1, Color.FromArgb(0, 255, 255, 255)));
            _letterGlint.ExtendMode = CompositionGradientExtendMode.Clamp;
            _letterGlint.CenterPoint = new Vector2(0, (float)LogoHeight / 2);
            _letterGlint.RotationAngleInDegrees = 18;
            _letterGlint.Offset = new Vector2((float)(LogoWidth / 2 - 460), 0);
            var shape = compositor.CreateSurfaceBrush(surface);
            shape.Stretch = CompositionStretch.Fill;
            var glintMask = compositor.CreateMaskBrush();
            glintMask.Source = _letterGlint;
            glintMask.Mask = shape;
            var glintSprite = compositor.CreateSpriteVisual();
            glintSprite.Size = new Vector2((float)LogoWidth, (float)LogoHeight);
            glintSprite.Brush = glintMask;
            ElementCompositionPreview.SetElementChildVisual(letters, glintSprite);

            var mirrored = compositor.CreateSurfaceBrush(surface);
            mirrored.Stretch = CompositionStretch.Fill;
            mirrored.TransformMatrix = Matrix3x2.CreateScale(1, -1, new Vector2((float)LogoWidth / 2, (float)LogoHeight / 2));
            var fade = compositor.CreateLinearGradientBrush();
            fade.StartPoint = new Vector2(0, 0);
            fade.EndPoint = new Vector2(0, 0.5f);
            fade.ColorStops.Add(compositor.CreateColorGradientStop(0, Color.FromArgb(255, 255, 255, 255)));
            fade.ColorStops.Add(compositor.CreateColorGradientStop(1, Color.FromArgb(0, 255, 255, 255)));
            var reflectionMask = compositor.CreateMaskBrush();
            reflectionMask.Source = mirrored;
            reflectionMask.Mask = fade;
            var reflectionSprite = compositor.CreateSpriteVisual();
            reflectionSprite.Size = new Vector2((float)LogoWidth, (float)LogoHeight);
            reflectionSprite.Brush = reflectionMask;
            reflectionSprite.Opacity = 0.16f;
            ElementCompositionPreview.SetElementChildVisual(reflection, reflectionSprite);
            Motion.Visual(reflection).Opacity = 0;

            // The cut edge where the two sheets part: a bright line and the light it throws, half
            // on each sheet, seen only as they separate.
            _edge = new Grid { Height = 30, VerticalAlignment = VerticalAlignment.Top, Opacity = 0, OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.3) }, IsHitTestVisible = false };
            _edge.Children.Add(new Border { Background = Gradient(new(0.5, 0), new(0.5, 1), Theme.Hex(0xFFFFFF, 0), Theme.Hex(0xFFFFFF, 0.18), Theme.Hex(0xFFFFFF, 0)) });
            _edge.Children.Add(new Border { Height = 2, Background = Theme.White(0.75) });
            Children.Add(_edge);
            SizeChanged += (_, e) => _edge.Margin = new Thickness(0, e.NewSize.Height / 2 + SeamBelowCentre - 15, 0, 0);

            ElementCompositionPreview.SetIsTranslationEnabled(this, true);
            Motion.Visual(this).Properties.InsertVector3("Translation", Vector3.Zero);
        }

        /// <summary>
        /// Light on the face of the glass: bright from the upper left, a broad reflection across the
        /// upper half, a band where the surface bends, a bright thick rim with light glowing in
        /// from it, and the glint that crosses it all with the one on the letters.
        /// </summary>
        private void BuildSheen(out Border glint)
        {
            _sheen.Children.Add(new Border
            {
                Background = new LinearGradientBrush
                {
                    StartPoint = new Windows.Foundation.Point(0, 0), EndPoint = new Windows.Foundation.Point(1, 1),
                    GradientStops =
                    {
                        new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.22), Offset = 0 },
                        new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.04), Offset = 0.35 },
                        new GradientStop { Color = Theme.Hex(0xFFFFFF, 0), Offset = 0.65 },
                        new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.08), Offset = 1 },
                    },
                },
            });
            // Bands far taller than any window, so that tilted they still run off its top and bottom.
            // On a canvas, which does not cut what overflows it as a grid does: their ends would show as hard lines.
            var canvas = new Canvas();
            _sheen.Children.Add(canvas);
            var band = new Border
            {
                Width = 900, Height = 4000,
                Background = Across(Theme.Hex(0xFFFFFF, 0), Theme.Hex(0xFFFFFF, 0.12), Theme.Hex(0xFFFFFF, 0.03), Theme.Hex(0xFFFFFF, 0)),
                RenderTransformOrigin = new Windows.Foundation.Point(0.5, 0.5), RenderTransform = new RotateTransform { Angle = 28 },
            };
            canvas.Children.Add(band);
            // Where the surface bends near the top: a soft ring of light.
            var ring = new Border
            {
                Width = 1600, Height = 700,
                Background = new RadialGradientBrush
                {
                    RadiusX = 0.5, RadiusY = 0.5,
                    GradientStops =
                    {
                        new GradientStop { Color = Theme.Hex(0xFFFFFF, 0), Offset = 0.82 },
                        new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.1), Offset = 0.92 },
                        new GradientStop { Color = Theme.Hex(0xFFFFFF, 0), Offset = 1 },
                    },
                },
            };
            canvas.Children.Add(ring);
            // The thick edge of the glass: a bright rim, and light glowing in from each side.
            foreach (var (from, to, horizontal, alignment) in new (Windows.Foundation.Point, Windows.Foundation.Point, bool, (HorizontalAlignment, VerticalAlignment))[]
            {
                (new(0.5, 0), new(0.5, 1), true, (HorizontalAlignment.Stretch, VerticalAlignment.Top)),
                (new(0.5, 1), new(0.5, 0), true, (HorizontalAlignment.Stretch, VerticalAlignment.Bottom)),
                (new(0, 0.5), new(1, 0.5), false, (HorizontalAlignment.Left, VerticalAlignment.Stretch)),
                (new(1, 0.5), new(0, 0.5), false, (HorizontalAlignment.Right, VerticalAlignment.Stretch)),
            })
            {
                var inner = new Border
                {
                    HorizontalAlignment = alignment.Item1, VerticalAlignment = alignment.Item2,
                    Background = Gradient(from, to, Theme.Hex(0xFFFFFF, 0.18), Theme.Hex(0xFFFFFF, 0)),
                };
                if (horizontal) inner.Height = 34; else inner.Width = 34;
                _sheen.Children.Add(inner);
            }
            _sheen.Children.Add(new Border
            {
                BorderThickness = new Thickness(2),
                BorderBrush = Gradient(new(0, 0), new(1, 1), Theme.Hex(0xFFFFFF, 0.7), Theme.Hex(0xFFFFFF, 0.15), Theme.Hex(0xFFFFFF, 0.35)),
            });
            glint = new Border
            {
                Width = 420, Height = 4000,
                Background = Across(Theme.Hex(0xFFFFFF, 0), Theme.Hex(0xFFFFFF, 0.22), Theme.Hex(0xFFFFFF, 0)),
                // Turned as a render transform: the compositor moves it, and the two do not mix with the XAML shorthand.
                RenderTransformOrigin = new Windows.Foundation.Point(0.5, 0.5), RenderTransform = new RotateTransform { Angle = 18 },
            };
            canvas.Children.Add(glint);
            var moving = glint;
            // Each placed by its middle, as the Mac offsets them from the middle of the screen.
            canvas.SizeChanged += (_, e) =>
            {
                void Place(FrameworkElement element, double x, double y)
                {
                    Canvas.SetLeft(element, e.NewSize.Width / 2 - element.Width / 2 + x);
                    Canvas.SetTop(element, e.NewSize.Height / 2 - element.Height / 2 + y);
                }
                Place(band, -420, -260);
                Place(ring, 0, -380);
                Place(moving, 0, 0);
            };
            var start = Motion.Visual(glint);
            ElementCompositionPreview.SetIsTranslationEnabled(glint, true);
            start.Properties.InsertVector3("Translation", new Vector3(-1500, 0, 0));
        }

        private static Image Part(double from, double to)
        {
            var image = new Image { Source = new BitmapImage(LogoSource), Width = LogoWidth, Height = LogoHeight, Stretch = Stretch.Fill };
            image.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(LogoWidth * from, 0, LogoWidth * (to - from), LogoHeight) };
            var visual = Motion.Visual(image);
            ElementCompositionPreview.SetIsTranslationEnabled(image, true);
            visual.Properties.InsertVector3("Translation", new Vector3(0, 44, 0));
            visual.CenterPoint = new Vector3((float)(LogoWidth * (from + to) / 2), (float)LogoHeight / 2, 0);
            visual.Scale = new Vector3(1.3f, 1.3f, 1);
            visual.Opacity = 0;
            return image;
        }

        public void Light()
        {
            // Light on the glass comes up over 1.6 s.
            Motion.FadeTo(_sheen, 1, 1.6, 0, Motion.Out);
            // Each part: spring(response 0.62, damping 0.68), the cube 0.2 s in and the name 0.42.
            foreach (var (part, delay) in new[] { (_cube, 0.2), (_name, 0.42) })
            {
                Motion.Spring(part, "Translation", Vector3.Zero, 0.62, 0.68, delay);
                Motion.Spring(part, "Scale", Vector3.One, 0.62, 0.68, delay);
                Motion.FadeTo(part, 1, 0.35, delay, Motion.Out);
            }
            Motion.FadeTo(_reflection, 1, 0.6, 0.42, Motion.Out);
            // The glint, 1.15 s in, crossing in 0.9 s, on the glass and on the letters together.
            Motion.Ease(_glint, "Translation", new Vector3(1500, 0, 0), 0.9, 1.15, Motion.InOut);
            var compositor = _letterGlint.Compositor;
            var sweep = compositor.CreateVector2KeyFrameAnimation();
            sweep.InsertKeyFrame(1, new Vector2((float)(LogoWidth / 2 + 460), 0), Motion.InOut(compositor));
            sweep.Duration = TimeSpan.FromSeconds(0.9);
            sweep.DelayTime = TimeSpan.FromSeconds(1.15);
            _letterGlint.StartAnimation("Offset", sweep);
        }

        public void Clear() => _dark.Opacity = 0;

        public void Part(double by)
        {
            _edge.Opacity = 1;
            Motion.Ease(this, "Translation", new Vector3(0, (float)by, 0), 0.75, 0.38, c => c.CreateCubicBezierEasingFunction(new(0.7f, 0), new(0.2f, 1)));
        }
    }
}

/// <summary>Composition animations on XAML elements: springs and curves with the Mac's own numbers.</summary>
internal static class Motion
{
    public static Visual Visual(UIElement element) => ElementCompositionPreview.GetElementVisual(element);

    public static CompositionEasingFunction InOut(Compositor c) => c.CreateCubicBezierEasingFunction(new(0.42f, 0), new(0.58f, 1));
    public static CompositionEasingFunction Out(Compositor c) => c.CreateCubicBezierEasingFunction(new(0, 0), new(0.58f, 1));

    /// <summary>
    /// A spring as SwiftUI gives one: <paramref name="response"/> is the period of the undamped
    /// spring, <paramref name="damping"/> the fraction of critical damping.
    /// </summary>
    public static void Spring(UIElement element, string property, Vector3 to, double response, double damping, double delay)
    {
        var visual = Visual(element);
        var spring = visual.Compositor.CreateSpringVector3Animation();
        spring.FinalValue = to;
        spring.Period = TimeSpan.FromSeconds(response / (2 * Math.PI));
        spring.DampingRatio = (float)damping;
        spring.DelayTime = TimeSpan.FromSeconds(delay);
        visual.StartAnimation(property, spring);
    }

    public static void Ease(UIElement element, string property, Vector3 to, double seconds, double delay, Func<Compositor, CompositionEasingFunction> curve)
    {
        var visual = Visual(element);
        var move = visual.Compositor.CreateVector3KeyFrameAnimation();
        move.InsertKeyFrame(1, to, curve(visual.Compositor));
        move.Duration = TimeSpan.FromSeconds(seconds);
        move.DelayTime = TimeSpan.FromSeconds(delay);
        visual.StartAnimation(property, move);
    }

    public static void FadeTo(UIElement element, float to, double seconds, double delay, Func<Compositor, CompositionEasingFunction>? curve = null)
    {
        var visual = Visual(element);
        var fade = visual.Compositor.CreateScalarKeyFrameAnimation();
        if (curve is null) fade.InsertKeyFrame(1, to);
        else fade.InsertKeyFrame(1, to, curve(visual.Compositor));
        fade.Duration = TimeSpan.FromSeconds(seconds);
        fade.DelayTime = TimeSpan.FromSeconds(delay);
        visual.StartAnimation("Opacity", fade);
    }
}
