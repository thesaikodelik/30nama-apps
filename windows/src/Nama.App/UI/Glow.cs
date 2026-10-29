using System.Numerics;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Hosting;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>
/// A shadow in any colour, cast by nothing visible: the light a card throws on what is under it,
/// as the Mac's <c>.shadow(color:radius:y:)</c>. XAML's own shadow is grey only. It sits behind
/// the card, the same size, and moves between two looks: at rest and lifted.
/// </summary>
public sealed class Glow : Grid
{
    public readonly record struct Look(Color Color, float Opacity, float Radius, float Y);

    private readonly SpriteVisual _sprite;
    private readonly DropShadow _shadow;
    private readonly float _grow;

    public Glow(Look rest, float grow = 1)
    {
        IsHitTestVisible = false;
        _grow = grow;
        var compositor = ElementCompositionPreview.GetElementVisual(this).Compositor;
        _shadow = compositor.CreateDropShadow();
        // No brush: the sprite draws nothing and its shadow is its whole rectangle, softened.
        _sprite = compositor.CreateSpriteVisual();
        _sprite.Shadow = _shadow;
        Set(rest);
        ElementCompositionPreview.SetElementChildVisual(this, _sprite);
        SizeChanged += (_, e) =>
        {
            _sprite.Size = new Vector2((float)e.NewSize.Width, (float)e.NewSize.Height);
            _sprite.CenterPoint = new Vector3(_sprite.Size / 2, 0);
        };
    }

    private void Set(Look look)
    {
        _shadow.Color = look.Color;
        _shadow.Opacity = look.Opacity;
        _shadow.BlurRadius = look.Radius;
        _shadow.Offset = new Vector3(0, look.Y, 0);
    }

    /// <summary>Moves to <paramref name="look"/> over a short ease, growing with the card when lifted.</summary>
    public void To(Look look, bool lifted)
    {
        var compositor = _sprite.Compositor;
        var ease = compositor.CreateCubicBezierEasingFunction(new(0.2f, 0), new(0.2f, 1));
        var duration = TimeSpan.FromSeconds(0.3);
        void Animate(CompositionObject target, string property, Func<KeyFrameAnimation> make)
        {
            var animation = make();
            animation.Duration = duration;
            target.StartAnimation(property, animation);
        }
        Animate(_shadow, "Color", () => { var a = compositor.CreateColorKeyFrameAnimation(); a.InsertKeyFrame(1, look.Color, ease); return a; });
        Animate(_shadow, "Opacity", () => { var a = compositor.CreateScalarKeyFrameAnimation(); a.InsertKeyFrame(1, look.Opacity, ease); return a; });
        Animate(_shadow, "BlurRadius", () => { var a = compositor.CreateScalarKeyFrameAnimation(); a.InsertKeyFrame(1, look.Radius, ease); return a; });
        Animate(_shadow, "Offset", () => { var a = compositor.CreateVector3KeyFrameAnimation(); a.InsertKeyFrame(1, new Vector3(0, look.Y, 0), ease); return a; });
        var scale = lifted ? _grow : 1;
        Animate(_sprite, "Scale", () => { var a = compositor.CreateVector3KeyFrameAnimation(); a.InsertKeyFrame(1, new Vector3(scale, scale, 1), ease); return a; });
    }

    /// <summary>
    /// The light breathes: swells and fades, slowly, about nine seconds a breath (the Android app's
    /// title page). Off, it holds at <paramref name="rest"/>. The compositor runs it, but it still
    /// redraws the window every frame, so it is stopped wherever the page is not seen.
    /// </summary>
    public void Breathe(bool on, Look rest)
    {
        if (!on)
        {
            _shadow.StopAnimation("Opacity");
            _shadow.StopAnimation("BlurRadius");
            Set(rest);
            return;
        }
        var compositor = _sprite.Compositor;
        var ease = compositor.CreateCubicBezierEasingFunction(new(0.4f, 0), new(0.2f, 1));
        void Swing(string property, float low, float high)
        {
            var a = compositor.CreateScalarKeyFrameAnimation();
            a.InsertKeyFrame(0, low);
            a.InsertKeyFrame(1, high, ease);
            a.Duration = TimeSpan.FromSeconds(4.5);
            a.IterationBehavior = AnimationIterationBehavior.Forever;
            a.Direction = AnimationDirection.Alternate;
            _shadow.StartAnimation(property, a);
        }
        Swing("Opacity", rest.Opacity * 0.5f, Math.Min(1, rest.Opacity * 1.9f));
        Swing("BlurRadius", rest.Radius * 0.85f, rest.Radius * 1.35f);
    }

    /// <summary>Puts <paramref name="content"/> in front of a glow of the same size, in a holder that does not clip the glow.</summary>
    public static Grid Behind(FrameworkElement content, Glow glow)
    {
        var holder = new Grid();
        glow.Width = content.Width;
        glow.Height = content.Height;
        glow.HorizontalAlignment = content.HorizontalAlignment;
        glow.VerticalAlignment = content.VerticalAlignment;
        holder.Children.Add(glow);
        holder.Children.Add(content);
        return holder;
    }
}
