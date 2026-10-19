using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// The glass of the top bar and its discs: frosted, a glare from above, and a rim bright where the
/// light strikes the top edge and faint along the bottom, with a soft shadow. A flat material with
/// an even hairline reads as grey plastic; the uneven rim is what reads as glass. As GlassLens in
/// MacKit.swift, with the same numbers (GlassLight).
/// </summary>
public static class GlassLens
{
    public const double RimWidth = 1.2;

    /// <summary>A capsule of glass filling what holds it; <paramref name="lit"/> is the brighter look of an open tab.</summary>
    public static Grid Make(bool lit = false)
    {
        var lens = new Grid { IsHitTestVisible = false };
        var shadow = Shadow(lit ? 0.3f : 0.22f);
        var fill = new Border { Background = Theme.GlassFill() };
        var body = new Border { Background = Theme.White(lit ? 0.10 : 0.02) };
        var glare = new Border { Background = Vertical((lit ? 0.30 : 0.12, 0), (lit ? 0.06 : 0.02, 0.5), (0, 0.7), (lit ? 0.10 : 0.04, 1)) };
        var rim = new Border
        {
            BorderThickness = new Thickness(RimWidth),
            BorderBrush = Vertical((lit ? 0.95 : 0.55, 0), (lit ? 0.25 : 0.12, 1 / 3.0), (0.05, 2 / 3.0), (lit ? 0.45 : 0.22, 1)),
        };
        lens.Children.Add(shadow);
        lens.Children.Add(fill);
        lens.Children.Add(body);
        lens.Children.Add(glare);
        lens.Children.Add(rim);
        // Rounded to a capsule whatever its size.
        lens.SizeChanged += (_, e) =>
        {
            var radius = new CornerRadius(Math.Min(e.NewSize.Width, e.NewSize.Height) / 2);
            fill.CornerRadius = body.CornerRadius = glare.CornerRadius = rim.CornerRadius = radius;
        };
        return lens;
    }

    /// <summary>
    /// A soft shadow of a capsule's shape, as the Mac's shadow of its glass: the capsule drawn
    /// faintly on a layer, which casts it. The faint capsule under the glass darkens it as the
    /// Mac's does, its shadow falling under the glass too.
    /// </summary>
    private static Grid Shadow(float opacity)
    {
        var host = new Grid { IsHitTestVisible = false };
        var compositor = Microsoft.UI.Xaml.Hosting.ElementCompositionPreview.GetElementVisual(host).Compositor;
        var layer = compositor.CreateLayerVisual();
        var shapes = compositor.CreateShapeVisual();
        var geometry = compositor.CreateRoundedRectangleGeometry();
        var shape = compositor.CreateSpriteShape(geometry);
        shape.FillBrush = compositor.CreateColorBrush(Color.FromArgb((byte)Math.Round(opacity * 255), 0, 0, 0));
        shapes.Shapes.Add(shape);
        layer.Children.InsertAtTop(shapes);
        var shadow = compositor.CreateDropShadow();
        shadow.Color = Color.FromArgb(255, 0, 0, 0);
        shadow.Opacity = 1;
        shadow.BlurRadius = 14;
        shadow.Offset = new System.Numerics.Vector3(0, 3, 0);
        shadow.SourcePolicy = Microsoft.UI.Composition.CompositionDropShadowSourcePolicy.InheritFromVisualContent;
        layer.Shadow = shadow;
        Microsoft.UI.Xaml.Hosting.ElementCompositionPreview.SetElementChildVisual(host, layer);
        host.SizeChanged += (_, e) =>
        {
            var size = new System.Numerics.Vector2((float)e.NewSize.Width, (float)e.NewSize.Height);
            layer.Size = shapes.Size = geometry.Size = size;
            geometry.CornerRadius = new System.Numerics.Vector2(Math.Min(size.X, size.Y) / 2);
        };
        return host;
    }

    private static LinearGradientBrush Vertical(params (double Alpha, double At)[] stops)
    {
        var brush = new LinearGradientBrush { StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1) };
        foreach (var (alpha, at) in stops) brush.GradientStops.Add(new GradientStop { Color = Color.FromArgb((byte)Math.Round(alpha * 255), 255, 255, 255), Offset = at });
        return brush;
    }
}
