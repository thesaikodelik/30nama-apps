using System.Numerics;
using Microsoft.Graphics.Canvas.Effects;
using Microsoft.UI;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Hosting;
using Microsoft.UI.Xaml.Media;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// The opening title, as on the Mac (Splash.swift, the glass drawn where the system has no Liquid
/// Glass), with its numbers: a sheet of clear glass over the app, which is seen through it a little
/// blurred; the logo's cube and then its name spring out of a blur onto the glass, a red line draws
/// under them, and a glint runs across the glass and the letters. To leave, the line runs out to
/// both edges and the glass parts along it like a curtain, as the app behind comes sharp.
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
    private const float LineWidth = 320;

    private readonly Half _top, _bottom;
    /// <summary>The app behind, seen through the glass a little blurred: under both halves, so it does not part with them.</summary>
    private readonly Border _behind = new() { IsHitTestVisible = false };
    private readonly Border _lineHost = new() { Height = 40, VerticalAlignment = VerticalAlignment.Top, IsHitTestVisible = false };
    private readonly CompositionEffectBrush _behindBlur;
    private readonly SpriteVisual _line;
    private readonly DropShadow _lineGlow;
    private bool _leaving;

    public SplashCurtain()
    {
        FlowDirection = FlowDirection.LeftToRight;
        var c = Motion.Visual(this).Compositor;

        // No material on the glass, as on the Mac: one would blur the app past recognising. The app
        // itself is blurred a little (6) and, in MainWindow, drawn 1.03 larger; both go as it leaves.
        // Hard edges: soft ones would darken the window's own edges.
        _behindBlur = Blur.Of(c, c.CreateBackdropBrush(), 6, EffectBorderMode.Hard);
        var behind = c.CreateSpriteVisual();
        behind.Brush = _behindBlur;
        behind.RelativeSizeAdjustment = Vector2.One;
        ElementCompositionPreview.SetElementChildVisual(_behind, behind);
        Children.Add(_behind);

        _top = new Half(upper: true);
        _bottom = new Half(upper: false);
        Children.Add(_top);
        Children.Add(_bottom);

        // The red line, its light the line's own shadow in the app's colour, as the Mac draws it:
        // long and narrow like the line, its ends fading with it. It grows in width, not in scale,
        // so the light keeps its radius as the line lengthens.
        var stroke = c.CreateLinearGradientBrush();
        foreach (var (at, colour) in new[] { (0f, Colors.Transparent), (0.25f, Theme.Accent), (0.5f, Theme.Hex(0xFF8A70)), (0.75f, Theme.Accent), (1f, Colors.Transparent) })
            stroke.ColorStops.Add(c.CreateColorGradientStop(at, colour));
        _line = c.CreateSpriteVisual();
        _line.Brush = stroke;
        _line.Size = new Vector2(0, 3);
        _line.AnchorPoint = new Vector2(0.5f, 0.5f);
        _line.RelativeOffsetAdjustment = new Vector3(0.5f, 0.5f, 0);
        _lineGlow = c.CreateDropShadow();
        _lineGlow.Color = Theme.Accent;
        _lineGlow.BlurRadius = 12;
        _lineGlow.Offset = Vector3.Zero;
        _lineGlow.Mask = stroke;
        _line.Shadow = _lineGlow;
        ElementCompositionPreview.SetElementChildVisual(_lineHost, _line);
        Children.Add(_lineHost);

        SizeChanged += (_, e) => Lay(e.NewSize);
        Loaded += (_, _) => Begin();
    }

    private void Lay(Windows.Foundation.Size size)
    {
        var seam = size.Height / 2 + SeamBelowCentre;
        _top.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, 0, size.Width, seam) };
        _bottom.Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, seam, size.Width, Math.Max(0, size.Height - seam)) };
        _lineHost.Margin = new Thickness(0, seam - 20, 0, 0);
    }

    private void Begin()
    {
        _top.Light();
        _bottom.Light();
        // The line draws out under the logo: spring(response 0.9, damping 0.8), 0.75 s in.
        var c = _line.Compositor;
        var grow = c.CreateSpringScalarAnimation();
        grow.FinalValue = LineWidth;
        grow.Period = TimeSpan.FromSeconds(0.9 / (2 * Math.PI));
        grow.DampingRatio = 0.8f;
        grow.DelayTime = TimeSpan.FromSeconds(0.75);
        _line.StartAnimation("Size.X", grow);
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
        var c = _line.Compositor;
        var seam = ActualHeight / 2 + SeamBelowCentre;
        // The app comes sharp as the glass goes: 0.9 s, easing out, with its scale in MainWindow.
        var sharpen = c.CreateScalarKeyFrameAnimation();
        sharpen.InsertKeyFrame(1, 0, Motion.Out(c));
        sharpen.Duration = TimeSpan.FromSeconds(0.9);
        _behindBlur.StartAnimation("Blur.BlurAmount", sharpen);
        // The line runs to both edges, its light widening, and fades (ease in-out, 0.42 s)...
        var run = c.CreateScalarKeyFrameAnimation();
        run.InsertKeyFrame(1, (float)(ActualWidth * 1.1), Motion.InOut(c));
        run.Duration = TimeSpan.FromSeconds(0.42);
        _line.StartAnimation("Size.X", run);
        var widen = c.CreateScalarKeyFrameAnimation();
        widen.InsertKeyFrame(1, 22, Motion.InOut(c));
        widen.Duration = TimeSpan.FromSeconds(0.42);
        _lineGlow.StartAnimation("BlurRadius", widen);
        var fade = c.CreateScalarKeyFrameAnimation();
        fade.InsertKeyFrame(1, 0);
        fade.Duration = TimeSpan.FromSeconds(0.42);
        _line.StartAnimation("Opacity", fade);
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
        /// <summary>Room around anything blurred, about three times its blur: a blur cut at its sprite's edge shows as a straight line.</summary>
        private const float LogoRoom = 60, ArcRoom = 90, EdgeRoom = 54, BandRoom = 24;

        private readonly Border _dark = new() { Background = Theme.Black(0.82), OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.9) } };
        private readonly RadialGradientBrush _shade;
        private readonly Grid _sheen = new() { IsHitTestVisible = false };
        private readonly Border _glint;
        private readonly CompositionLinearGradientBrush _letterGlint;
        private readonly Grid _edge;
        private readonly Compositor _c;
        private readonly List<(SpriteVisual Sprite, CompositionEffectBrush Blur, bool Mirrored, double Delay)> _parts = [];
        private readonly SpriteVisual _arc, _band, _rimGlow;
        private readonly ShapeVisual _rimShapes;
        private readonly CompositionRectangleGeometry _rimRect;
        private readonly CompositionVisualSurface _rimSurface;

        public Half(bool upper)
        {
            _c = Motion.Visual(this).Compositor;
            Children.Add(_dark);
            // Clear glass, with a shade only where the logo sits, so it holds its own over whatever
            // the app shows: a circle of 650 about the middle, whatever the window's shape.
            _shade = new RadialGradientBrush
            {
                MappingMode = BrushMappingMode.Absolute, RadiusX = 650, RadiusY = 650,
                GradientStops = { new GradientStop { Color = Theme.Hex(0, 0.5), Offset = 0 }, new GradientStop { Color = Theme.Hex(0, 0.15), Offset = 1 } },
            };
            Children.Add(new Border { Background = _shade });

            // Light on the face of the glass, faded in as the title starts.
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
            var lights = _c.CreateContainerVisual();
            // A broad reflection across the upper half, as glass catches a window: far taller than
            // any window, so that tilted it still runs off its top and bottom; 8 blurred.
            _band = Band();
            lights.Children.InsertAtTop(_band);
            // Where the surface bends near the top: the rim of an ellipse, 30 blurred.
            _arc = Arc();
            lights.Children.InsertAtTop(_arc);
            var lightHost = new Border { IsHitTestVisible = false };
            ElementCompositionPreview.SetElementChildVisual(lightHost, lights);
            _sheen.Children.Add(lightHost);
            BuildGlint(out _glint);
            Children.Add(_sheen);
            Motion.Visual(_sheen).Opacity = 0;

            // The thick edge of the glass: a bright rim, and light glowing in from it (26 wide, 18
            // blurred), one band all round, not four that cross at the corners.
            Children.Add(new Border
            {
                BorderThickness = new Thickness(2), IsHitTestVisible = false,
                BorderBrush = Gradient(new(0, 0), new(1, 1), Theme.Hex(0xFFFFFF, 0.7), Theme.Hex(0xFFFFFF, 0.15), Theme.Hex(0xFFFFFF, 0.35)),
            });
            _rimShapes = _c.CreateShapeVisual();
            _rimRect = _c.CreateRectangleGeometry();
            var rimLine = _c.CreateSpriteShape(_rimRect);
            rimLine.StrokeBrush = _c.CreateColorBrush(Theme.Hex(0xFFFFFF, 0.18));
            rimLine.StrokeThickness = 26;
            _rimShapes.Shapes.Add(rimLine);
            _rimSurface = _c.CreateVisualSurface();
            _rimSurface.SourceVisual = _rimShapes;
            _rimGlow = _c.CreateSpriteVisual();
            _rimGlow.Brush = Blur.Of(_c, _c.CreateSurfaceBrush(_rimSurface), 18);
            var rimHost = new Border { IsHitTestVisible = false };
            ElementCompositionPreview.SetElementChildVisual(rimHost, _rimGlow);
            Children.Add(rimHost);

            // The logo's reflection in the glass below it, fading as it goes down; then the logo, its
            // cube a beat before its name, and over it the glint on the letters.
            var surface = LoadedImageSurface.StartLoadFromUri(LogoSource);
            var reflection = Holder(2 * (LogoLift + LogoHeight + 26));
            var logo = Holder(2 * LogoLift);
            var reflected = _c.CreateContainerVisual();
            var shown = _c.CreateContainerVisual();
            foreach (var (from, to, delay) in new[] { (0.0, LogoSplit, 0.2), (LogoSplit, 1.0, 0.42) })
            {
                AddPart(reflected, surface, from, to, mirrored: true, delay);
                AddPart(shown, surface, from, to, mirrored: false, delay);
            }
            ElementCompositionPreview.SetElementChildVisual(reflection, reflected);
            ElementCompositionPreview.SetElementChildVisual(logo, shown);
            Children.Add(reflection);
            Children.Add(logo);

            // A white silhouette of the logo seen through a moving slit, cut to the logo's own shape.
            var letters = Holder(2 * LogoLift);
            _letterGlint = _c.CreateLinearGradientBrush();
            _letterGlint.MappingMode = CompositionMappingMode.Absolute;
            _letterGlint.StartPoint = new Vector2(-75, 0);
            _letterGlint.EndPoint = new Vector2(75, 0);
            _letterGlint.ColorStops.Add(_c.CreateColorGradientStop(0, Color.FromArgb(0, 255, 255, 255)));
            _letterGlint.ColorStops.Add(_c.CreateColorGradientStop(0.5f, Color.FromArgb(255, 255, 255, 255)));
            _letterGlint.ColorStops.Add(_c.CreateColorGradientStop(1, Color.FromArgb(0, 255, 255, 255)));
            _letterGlint.ExtendMode = CompositionGradientExtendMode.Clamp;
            _letterGlint.CenterPoint = new Vector2(0, (float)LogoHeight / 2);
            _letterGlint.RotationAngleInDegrees = 18;
            _letterGlint.Offset = new Vector2((float)(LogoWidth / 2 - 460), 0);
            var shape = _c.CreateSurfaceBrush(surface);
            shape.Stretch = CompositionStretch.Fill;
            var glintMask = _c.CreateMaskBrush();
            glintMask.Source = _letterGlint;
            glintMask.Mask = shape;
            var glintSprite = _c.CreateSpriteVisual();
            glintSprite.Size = new Vector2((float)LogoWidth, (float)LogoHeight);
            glintSprite.Brush = glintMask;
            ElementCompositionPreview.SetElementChildVisual(letters, glintSprite);
            Children.Add(letters);

            // The cut edge where the two sheets part: a bright line and the light it throws, half
            // on each sheet, seen only as they separate.
            _edge = new Grid { Height = 30, VerticalAlignment = VerticalAlignment.Top, Opacity = 0, OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.3) }, IsHitTestVisible = false };
            _edge.Children.Add(new Border { Background = Gradient(new(0.5, 0), new(0.5, 1), Theme.Hex(0xFFFFFF, 0), Theme.Hex(0xFFFFFF, 0.18), Theme.Hex(0xFFFFFF, 0)) });
            _edge.Children.Add(new Border { Height = 2, Background = Theme.White(0.75) });
            Children.Add(_edge);

            SizeChanged += (_, e) => Lay(e.NewSize);
            ElementCompositionPreview.SetIsTranslationEnabled(this, true);
            Motion.Visual(this).Properties.InsertVector3("Translation", Vector3.Zero);
        }

        /// <summary>A place the size of the logo, a little above the middle by <paramref name="lift"/> (a margin, so twice the shift).</summary>
        private static Border Holder(double lift) => new()
        {
            Width = LogoWidth, Height = LogoHeight, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, lift, 0, 0), IsHitTestVisible = false,
        };

        private void Lay(Windows.Foundation.Size size)
        {
            var w = (float)size.Width;
            var h = (float)size.Height;
            var middle = new Windows.Foundation.Point(w / 2, h / 2);
            _shade.Center = middle;
            _shade.GradientOrigin = middle;
            _edge.Margin = new Thickness(0, h / 2 + SeamBelowCentre - 15, 0, 0);
            // Each placed by its middle, as the Mac offsets them from the middle of the screen.
            _arc.Offset = new Vector3(w / 2 - _arc.Size.X / 2, h / 2 - 380 - _arc.Size.Y / 2, 0);
            _band.Offset = new Vector3(w / 2 - 420 - _band.Size.X / 2, h / 2 - 260 - _band.Size.Y / 2, 0);
            // The light in from the rim, as wide as the window, with room around it for its blur.
            var room = new Vector2(w + 2 * EdgeRoom, h + 2 * EdgeRoom);
            _rimShapes.Size = room;
            _rimRect.Offset = new Vector2(EdgeRoom + 13, EdgeRoom + 13);
            _rimRect.Size = new Vector2(Math.Max(0, w - 26), Math.Max(0, h - 26));
            _rimSurface.SourceSize = room;
            _rimGlow.Size = room;
            _rimGlow.Offset = new Vector3(-EdgeRoom, -EdgeRoom, 0);
        }

        private SpriteVisual Band()
        {
            var size = new Vector2(900 + 2 * BandRoom, 4000);
            var across = _c.CreateLinearGradientBrush();
            across.MappingMode = CompositionMappingMode.Absolute;
            across.StartPoint = new Vector2(BandRoom, 0);
            across.EndPoint = new Vector2(BandRoom + 900, 0);
            foreach (var (at, alpha) in new[] { (0f, 0.0), (1 / 3f, 0.12), (2 / 3f, 0.03), (1f, 0.0) })
                across.ColorStops.Add(_c.CreateColorGradientStop(at, Theme.Hex(0xFFFFFF, alpha)));
            var flat = _c.CreateSpriteVisual();
            flat.Size = size;
            flat.Brush = across;
            var band = _c.CreateSpriteVisual();
            band.Size = size;
            band.Brush = Blur.Of(_c, Blur.Picture(_c, flat, size), 8);
            band.CenterPoint = new Vector3(size / 2, 0);
            band.RotationAngleInDegrees = 28;
            return band;
        }

        private SpriteVisual Arc()
        {
            var size = new Vector2(1600 + 40 + 2 * ArcRoom, 700 + 40 + 2 * ArcRoom);
            var shapes = _c.CreateShapeVisual();
            shapes.Size = size;
            var ellipse = _c.CreateEllipseGeometry();
            ellipse.Center = size / 2;
            ellipse.Radius = new Vector2(800, 350);
            var rim = _c.CreateSpriteShape(ellipse);
            rim.StrokeBrush = _c.CreateColorBrush(Theme.Hex(0xFFFFFF, 0.16));
            rim.StrokeThickness = 40;
            shapes.Shapes.Add(rim);
            var surface = _c.CreateVisualSurface();
            surface.SourceVisual = shapes;
            surface.SourceSize = size;
            var arc = _c.CreateSpriteVisual();
            arc.Size = size;
            arc.Brush = Blur.Of(_c, _c.CreateSurfaceBrush(surface), 30);
            return arc;
        }

        /// <summary>
        /// One part of the logo (or of its reflection, upside down and faded as it goes down), blurred
        /// through a brush whose amount is moved: it comes out of the blur as it arrives.
        /// </summary>
        private void AddPart(ContainerVisual into, LoadedImageSurface surface, double from, double to, bool mirrored, double delay)
        {
            var w = (float)LogoWidth;
            var h = (float)LogoHeight;
            var size = new Vector2(w + 2 * LogoRoom, h + 2 * LogoRoom);
            // The part: the whole logo drawn and cut by a clip, upside down for the reflection.
            var image = _c.CreateSurfaceBrush(surface);
            image.Stretch = CompositionStretch.Fill;
            var drawn = _c.CreateSpriteVisual();
            drawn.Brush = image;
            drawn.Size = new Vector2(w, h);
            drawn.Offset = new Vector3(LogoRoom, LogoRoom, 0);
            if (mirrored)
            {
                drawn.CenterPoint = new Vector3(w / 2, h / 2, 0);
                drawn.Scale = new Vector3(1, -1, 1);
            }
            var left = from <= 0 ? 0 : LogoRoom + w * (float)from;
            var right = to >= 1 ? size.X : LogoRoom + w * (float)to;
            var cut = _c.CreateContainerVisual();
            cut.Size = size;
            cut.Clip = _c.CreateInsetClip(left, 0, size.X - right, 0);
            cut.Children.InsertAtTop(drawn);
            var clear = Color.FromArgb(0, 255, 255, 255);
            var solid = Color.FromArgb(255, 255, 255, 255);
            var part = Blur.Picture(_c, cut, size);
            var blur = Blur.Of(_c, part, 18);
            var sprite = _c.CreateSpriteVisual();
            sprite.Size = size;
            if (mirrored)
            {
                var fade = _c.CreateLinearGradientBrush();
                fade.MappingMode = CompositionMappingMode.Absolute;
                fade.StartPoint = new Vector2(0, LogoRoom);
                fade.EndPoint = new Vector2(0, LogoRoom + h / 2);
                fade.ColorStops.Add(_c.CreateColorGradientStop(0, solid));
                fade.ColorStops.Add(_c.CreateColorGradientStop(1, clear));
                var faded = _c.CreateMaskBrush();
                faded.Source = blur;
                faded.Mask = fade;
                sprite.Brush = faded;
            }
            else sprite.Brush = blur;
            sprite.Offset = new Vector3(-LogoRoom, -LogoRoom, 0);
            // About the middle of the whole logo, not of the part: from 1.3 the cube comes in from
            // further left and the name from further right, closing on each other.
            sprite.CenterPoint = new Vector3(size / 2, 0);
            sprite.Scale = new Vector3(1.3f, 1.3f, 1);
            sprite.Opacity = 0;
            into.Children.InsertAtTop(sprite);
            _parts.Add((sprite, blur, mirrored, delay));
        }

        private void BuildGlint(out Border glint)
        {
            // The glint that crosses the glass with the one on the letters, far taller than any
            // window; on a canvas, which does not cut what overflows it as a grid does.
            var canvas = new Canvas();
            _sheen.Children.Add(canvas);
            glint = new Border
            {
                Width = 420, Height = 4000,
                Background = Across(Theme.Hex(0xFFFFFF, 0), Theme.Hex(0xFFFFFF, 0.22), Theme.Hex(0xFFFFFF, 0)),
                // Turned as a render transform: the compositor moves it, and the two do not mix with the XAML shorthand.
                RenderTransformOrigin = new Windows.Foundation.Point(0.5, 0.5), RenderTransform = new RotateTransform { Angle = 18 },
            };
            canvas.Children.Add(glint);
            var moving = glint;
            canvas.SizeChanged += (_, e) =>
            {
                Canvas.SetLeft(moving, e.NewSize.Width / 2 - moving.Width / 2);
                Canvas.SetTop(moving, e.NewSize.Height / 2 - moving.Height / 2);
            };
            ElementCompositionPreview.SetIsTranslationEnabled(glint, true);
            Motion.Visual(glint).Properties.InsertVector3("Translation", new Vector3(-1500, 0, 0));
        }

        public void Light()
        {
            // Light on the glass comes up over 1.6 s.
            Motion.FadeTo(_sheen, 1, 1.6, 0, Motion.Out);
            // Each part: one spring(response 0.62, damping 0.68), the cube 0.2 s in and the name
            // 0.42, moving blur 18 → 0, opacity 0 → 1, a drop of 44 → 0 and size 1.3 → 1 together.
            // The spring passes 1 and comes back: right for the drop and the size; blur below 0 and
            // opacity above 1 mean nothing, and are cut there as SwiftUI cuts them.
            foreach (var (sprite, blur, mirrored, delay) in _parts)
            {
                var clock = _c.CreatePropertySet();
                clock.InsertScalar("t", 0);
                var spring = _c.CreateSpringScalarAnimation();
                spring.FinalValue = 1;
                spring.Period = TimeSpan.FromSeconds(0.62 / (2 * Math.PI));
                spring.DampingRatio = 0.68f;
                spring.DelayTime = TimeSpan.FromSeconds(delay);
                clock.StartAnimation("t", spring);
                // The reflection: upside down, so it rises; 0.16 strong, with a blur of its own 1.5.
                Follow(blur, "Blur.BlurAmount", mirrored ? "1.5 + Max(0, 18 * (1 - p.t))" : "Max(0, 18 * (1 - p.t))", clock);
                Follow(sprite, "Opacity", mirrored ? "0.16 * Clamp(p.t, 0, 1)" : "Clamp(p.t, 0, 1)", clock);
                Follow(sprite, "Offset.Y", $"{-LogoRoom} {(mirrored ? "-" : "+")} 44 * (1 - p.t)", clock);
                Follow(sprite, "Scale", "Vector3(1.3 - 0.3 * p.t, 1.3 - 0.3 * p.t, 1)", clock);
            }
            // The glint, 1.15 s in, crossing in 0.9 s, on the glass and on the letters together.
            Motion.Ease(_glint, "Translation", new Vector3(1500, 0, 0), 0.9, 1.15, Motion.InOut);
            var sweep = _c.CreateVector2KeyFrameAnimation();
            sweep.InsertKeyFrame(1, new Vector2((float)(LogoWidth / 2 + 460), 0), Motion.InOut(_c));
            sweep.Duration = TimeSpan.FromSeconds(0.9);
            sweep.DelayTime = TimeSpan.FromSeconds(1.15);
            _letterGlint.StartAnimation("Offset", sweep);
        }

        private void Follow(CompositionObject target, string property, string expression, CompositionPropertySet clock)
        {
            var follow = _c.CreateExpressionAnimation(expression);
            follow.SetReferenceParameter("p", clock);
            target.StartAnimation(property, follow);
        }

        public void Clear() => _dark.Opacity = 0;

        public void Part(double by)
        {
            _edge.Opacity = 1;
            Motion.Ease(this, "Translation", new Vector3(0, (float)by, 0), 0.75, 0.38, c => c.CreateCubicBezierEasingFunction(new(0.7f, 0), new(0.2f, 1)));
        }
    }
}

/// <summary>
/// A Gaussian blur for the compositor, from Win2D: WinUI 3 has no blur of its own. Its amount can be
/// moved as <c>Blur.BlurAmount</c>.
/// </summary>
internal static class Blur
{
    /// <summary>
    /// What <paramref name="visual"/> draws, as a brush an effect can take: a blur takes a surface,
    /// not a mask or a gradient.
    /// </summary>
    public static CompositionSurfaceBrush Picture(Compositor c, Visual visual, Vector2 size)
    {
        var surface = c.CreateVisualSurface();
        surface.SourceVisual = visual;
        surface.SourceSize = size;
        return c.CreateSurfaceBrush(surface);
    }

    public static CompositionEffectBrush Of(Compositor c, CompositionBrush source, float amount, EffectBorderMode border = EffectBorderMode.Soft)
    {
        var effect = new GaussianBlurEffect
        {
            Name = "Blur", BlurAmount = amount, BorderMode = border, Optimization = EffectOptimization.Balanced,
            Source = new CompositionEffectSourceParameter("source"),
        };
        var brush = c.CreateEffectFactory(effect, ["Blur.BlurAmount"]).CreateBrush();
        brush.SetSourceParameter("source", source);
        return brush;
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
