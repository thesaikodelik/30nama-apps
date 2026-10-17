using System.Numerics;
using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// The opening title, as on the Mac (Splash.swift): a sheet of glass over the app, so the home
/// page is seen softly through it once it has loaded; the logo's cube and then its name rise
/// onto the glass, a red line draws under them and a glint runs across. To leave, the line runs
/// out to both edges and the glass parts along it like a curtain, uncovering the app.
/// </summary>
public sealed class SplashCurtain : Grid
{
    /// <summary>How long the exit takes, from <see cref="Leave"/> to nothing being left on screen.</summary>
    public static readonly TimeSpan ExitDuration = TimeSpan.FromSeconds(1.15);

    private const double LogoHeight = 190;
    /// <summary>Where the cube ends and the name begins, as a fraction of the logo's width.</summary>
    private const double LogoSplit = 0.29;
    /// <summary>The seam sits where the line is: just under the logo.</summary>
    private const double SeamBelowCentre = 84;

    private readonly Half _top, _bottom;
    private readonly Border _line;
    private bool _leaving;

    public SplashCurtain()
    {
        IsHitTestVisible = true;
        FlowDirection = FlowDirection.LeftToRight;
        _top = new Half(upper: true);
        _bottom = new Half(upper: false);
        Children.Add(_top);
        Children.Add(_bottom);
        _line = new Border
        {
            Width = 320, Height = 3, CornerRadius = new CornerRadius(1.5), VerticalAlignment = VerticalAlignment.Top, HorizontalAlignment = HorizontalAlignment.Center,
            Background = Across(Colors.Transparent, Theme.Accent, Theme.Hex(0xFF8A70), Theme.Accent, Colors.Transparent),
            Scale = new Vector3(0, 1, 1),
            ScaleTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(0.9) },
            OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.42) },
        };
        Children.Add(_line);
        SizeChanged += (_, e) => Lay(e.NewSize);
        Loaded += (_, _) => Begin();
    }

    private double Seam => ActualHeight / 2 + SeamBelowCentre;

    private void Lay(Windows.Foundation.Size size)
    {
        var seam = size.Height / 2 + SeamBelowCentre;
        _top.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, 0, size.Width, seam) };
        _bottom.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, seam, size.Width, Math.Max(0, size.Height - seam)) };
        _line.Margin = new Thickness(0, seam - 1.5, 0, 0);
        _line.CenterPoint = new Vector3(160, 1.5f, 0);
    }

    private void Begin()
    {
        _top.Light();
        _bottom.Light();
        After(0.75, () => { if (!_leaving) _line.Scale = Vector3.One; });
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
        _line.ScaleTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(0.42) };
        _line.Scale = new Vector3((float)(ActualWidth * 1.1 / 320), 1, 1);
        _line.Opacity = 0;
        var seam = Seam;
        After(0.38, () =>
        {
            _top.Part(-seam);
            _bottom.Part(ActualHeight - seam);
        });
        After(ExitDuration.TotalSeconds, gone);
    }

    private void After(double seconds, Action action)
    {
        var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(seconds) };
        timer.Tick += (_, _) =>
        {
            timer.Stop();
            action();
        };
        timer.Start();
    }

    private static LinearGradientBrush Across(params Color[] colors)
    {
        var brush = new LinearGradientBrush { StartPoint = new Windows.Foundation.Point(0, 0.5), EndPoint = new Windows.Foundation.Point(1, 0.5) };
        for (var i = 0; i < colors.Length; i++) brush.GradientStops.Add(new GradientStop { Color = colors[i], Offset = (double)i / (colors.Length - 1) });
        return brush;
    }

    /// <summary>Glass, logo and its reflection: drawn twice, once for each half of the curtain.</summary>
    private sealed class Half : Grid
    {
        private readonly Border _dark = new() { Background = Theme.Black(0.82), OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.9) } };
        private readonly Grid _sheen = new() { Opacity = 0, OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(1.6) } };
        private readonly Border _glint;
        private readonly Image _cube, _name;
        private readonly Border _edge;

        public Half(bool upper)
        {
            TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(0.75) };
            Children.Add(_dark);
            // Glass: the app behind softened, and a shade where the logo sits so it holds its own.
            Children.Add(new Border
            {
                Background = new Microsoft.UI.Xaml.Media.AcrylicBrush { TintColor = Theme.Bg0, TintOpacity = 0.25, TintLuminosityOpacity = 0.1, FallbackColor = Theme.Bg1 },
            });
            Children.Add(new Border
            {
                Background = new RadialGradientBrush
                {
                    RadiusX = 0.6, RadiusY = 0.8,
                    GradientStops = { new GradientStop { Color = Theme.Hex(0, 0.5), Offset = 0 }, new GradientStop { Color = Theme.Hex(0, 0.15), Offset = 1 } },
                },
            });
            // Light on the face of the glass, bright from the upper left, and its thick bright rim.
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
            _sheen.Children.Add(new Border { BorderThickness = new Thickness(2), BorderBrush = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0, 0), EndPoint = new Windows.Foundation.Point(1, 1),
                GradientStops = { new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.7), Offset = 0 }, new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.15), Offset = 0.5 }, new GradientStop { Color = Theme.Hex(0xFFFFFF, 0.35), Offset = 1 } },
            } });
            // The glint, crossing the glass once: far taller than the window, so tilted it still runs off both edges.
            _glint = new Border
            {
                Width = 420, Height = 4000, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center,
                Background = Across(Theme.Hex(0xFFFFFF, 0), Theme.Hex(0xFFFFFF, 0.22), Theme.Hex(0xFFFFFF, 0)),
                Rotation = 18, CenterPoint = new Vector3(210, 2000, 0),
                Translation = new Vector3(-1500, 0, 0),
                TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(0.9) },
            };
            _sheen.Children.Add(_glint);
            Children.Add(_sheen);

            // The logo's reflection in the glass below it, faint.
            var reflection = Logo();
            reflection.Opacity = 0.12;
            reflection.RenderTransformOrigin = new Windows.Foundation.Point(0.5, 0.5);
            reflection.RenderTransform = new ScaleTransform { ScaleY = -1 };
            reflection.Margin = new Thickness(0, 2 * (LogoHeight + 26) - 44, 0, 0);
            reflection.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(-2000, LogoHeight * 0.55, 4000, LogoHeight * 0.45) };
            Children.Add(reflection);

            // The cube arrives a beat before the name.
            _cube = Part(0, LogoSplit);
            _name = Part(LogoSplit, 1);
            Children.Add(_cube);
            Children.Add(_name);

            // The cut edge, seen only as the two sheets part.
            _edge = new Border
            {
                Height = 2, Background = Theme.White(0.75), Opacity = 0, VerticalAlignment = upper ? VerticalAlignment.Top : VerticalAlignment.Top,
                OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.3) },
            };
            Children.Add(_edge);
            SizeChanged += (_, e) => _edge.Margin = new Thickness(0, e.NewSize.Height / 2 + SeamBelowCentre - (upper ? 2 : 0), 0, 0);
        }

        private static Image Logo() => new()
        {
            Source = new BitmapImage(new Uri("ms-appx:///Assets/Logo.png")), Height = LogoHeight,
            HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, -44, 0, 0),
        };

        /// <summary>One part of the logo, cut out of the whole by width once its size is known.</summary>
        private static Image Part(double from, double to)
        {
            var image = Logo();
            image.Opacity = 0;
            image.Translation = new Vector3(0, 44, 0);
            image.Scale = new Vector3(1.3f, 1.3f, 1);
            image.OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.62) };
            image.TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(0.62) };
            image.ScaleTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(0.62) };
            image.SizeChanged += (_, e) =>
            {
                image.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(e.NewSize.Width * from, 0, e.NewSize.Width * (to - from), e.NewSize.Height) };
                image.CenterPoint = new Vector3((float)(e.NewSize.Width * (from + to) / 2), (float)(e.NewSize.Height / 2), 0);
            };
            return image;
        }

        public void Light()
        {
            _sheen.Opacity = 1;
            Arrive(_cube, 0.2);
            Arrive(_name, 0.42);
            Later(1.15, () => _glint.Translation = new Vector3(1500, 0, 0));
        }

        private static void Arrive(Image part, double delay) => Later(delay, () =>
        {
            part.Opacity = 1;
            part.Translation = Vector3.Zero;
            part.Scale = Vector3.One;
        });

        public void Clear() => _dark.Opacity = 0;

        public void Part(double by)
        {
            _edge.Opacity = 1;
            Translation = new Vector3(0, (float)by, 0);
        }

        private static void Later(double seconds, Action action)
        {
            var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(seconds) };
            timer.Tick += (_, _) =>
            {
                timer.Stop();
                action();
            };
            timer.Start();
        }
    }
}
