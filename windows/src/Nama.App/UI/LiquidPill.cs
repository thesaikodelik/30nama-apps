using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.App.Model;
using Rect = Windows.Foundation.Rect;

namespace Nama.App.UI;

/// <summary>
/// The open tab's pill, of glass, that flows to the next tab like a drop of liquid: the leading
/// edge runs ahead, it thins in the middle of its run, and a drop stays behind joined by a neck
/// that thins and snaps; then it settles. It is seen only inside the glass it flows over (a bar,
/// a disc beside it, three pixels in), so between two pieces of glass it passes unseen and comes
/// out in the next. As LiquidPill in MacKit.swift.
///
/// The Mac draws the pieces blurred and cuts them back by their alpha (a metaball). Here, without
/// a blur to hand, the same shape is drawn from distances: each piece is a capsule, and they are
/// joined by a smooth union, which makes the same bridge between pieces close together. It is
/// drawn into a bitmap only while it moves, and once where it rests.
/// </summary>
public sealed class LiquidPill : Grid
{
    private readonly Image _image = new() { Stretch = Stretch.Fill };
    private const double Rim = 1.2;
    /// <summary>How far apart two pieces still join: the reach of the Mac's blur.</summary>
    private const double Reach = 11;

    private List<Rect> _grounds = [];
    private Dictionary<object, Rect> _tabs = [];
    private object? _selected, _from;
    private double _progress = 1;
    private DateTime _started;
    private bool _moving, _shown;
    private WriteableBitmap? _bitmap;
    private byte[] _pixels = [];

    public LiquidPill()
    {
        Children.Add(_image);
        IsHitTestVisible = false;
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetAccessibilityView(this, Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Raw);
        OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.2) };
        SizeChanged += (_, _) => Draw();
        // Not stopped when it leaves the page: a page drawn again takes it out and puts it back
        // in the same moment, and the run ends by itself in a second or so.
        Loaded += (_, _) => Draw();
    }

    /// <summary>Where the glass is and where each tab is, in this element's own space.</summary>
    public void Place(IEnumerable<Rect> grounds, Dictionary<object, Rect> tabs)
    {
        var g = grounds.ToList();
        if (g.SequenceEqual(_grounds) && tabs.Count == _tabs.Count && tabs.All(t => _tabs.TryGetValue(t.Key, out var r) && r == t.Value)) return;
        _grounds = g;
        _tabs = tabs;
        Draw();
    }

    /// <summary>The open tab; null hides the pill, which then fades rather than moving.</summary>
    public void Select(object? key)
    {
        if (Equals(key, _selected)) return;
        var old = _selected;
        _selected = key;
        Opacity = key is null ? 0 : 1;
        // From nothing (a page that is no tab was open), it only fades back in.
        if (old is null || key is null || !_shown)
        {
            _from = null;
            _progress = 1;
            Draw();
            return;
        }
        _from = old;
        _progress = 0;
        _started = DateTime.UtcNow;
        if (!_moving)
        {
            _moving = true;
            CompositionTarget.Rendering += Frame;
        }
    }

    private void Stop()
    {
        if (!_moving) return;
        _moving = false;
        CompositionTarget.Rendering -= Frame;
    }

    private void Frame(object? sender, object e)
    {
        var t = (DateTime.UtcNow - _started).TotalSeconds;
        if (AppModel.Shared.Settings.ReduceMotion)
        {
            // Short and plain: an ease out over a fifth of a second.
            var x = Math.Min(1, t / 0.18);
            _progress = 1 - Math.Pow(1 - x, 3);
        }
        else
        {
            // A spring of the Mac's numbers: response 0.55 s, damping 0.78.
            const double response = 0.55, damping = 0.78;
            var w = 2 * Math.PI / response;
            var wd = w * Math.Sqrt(1 - damping * damping);
            _progress = 1 - Math.Exp(-damping * w * t) * (Math.Cos(wd * t) + damping * w / wd * Math.Sin(wd * t));
        }
        var done = t > (AppModel.Shared.Settings.ReduceMotion ? 0.18 : 1.3);
        if (done) _progress = 1;
        Draw();
        if (done) Stop();
    }

    /// <summary>The pieces of the liquid now: the body, and the drop left where it was.</summary>
    private List<Rect> Pieces(Rect to)
    {
        if (_from is null || !_tabs.TryGetValue(_from, out var from) || from == to || _progress >= 1) return [to];
        var p = Math.Max(0, _progress);
        var settled = Math.Min(1, p);
        // The leading edge runs ahead, the trailing one follows late: the pill stretches.
        var lead = 1 - Math.Pow(1 - settled, 3) + (p - settled);
        var trail = settled * settled;
        var right = to.X + to.Width / 2 > from.X + from.Width / 2;
        var minX = from.Left + (to.Left - from.Left) * (right ? trail : lead);
        var maxX = from.Right + (to.Right - from.Right) * (right ? lead : trail);
        // Thinner in the middle of its run, as liquid is when it is pulled.
        var height = to.Height * (1 - 0.32 * Math.Sin(Math.PI * settled));
        var body = new Rect(minX, to.Y + to.Height / 2 - height / 2, Math.Max(height, maxX - minX), height);
        // What stays behind where it was, shrinking until the neck to it snaps.
        var left = Math.Pow(1 - settled, 1.6);
        var drop = new Rect(from.X + from.Width / 2 - from.Width * left / 2, from.Y + from.Height / 2 - from.Height * left / 2, from.Width * left, from.Height * left);
        return left > 0.02 ? [body, drop] : [body];
    }

    /// <summary>Distance from (x, y) to a capsule filling <paramref name="r"/>: negative inside.</summary>
    private static double Capsule(Rect r, double x, double y)
    {
        var radius = Math.Min(r.Width, r.Height) / 2;
        if (radius <= 0) return double.MaxValue;
        double ax, ay, bx, by;
        if (r.Width >= r.Height) { ay = by = r.Y + r.Height / 2; ax = r.Left + radius; bx = r.Right - radius; }
        else { ax = bx = r.X + r.Width / 2; ay = r.Top + radius; by = r.Bottom - radius; }
        double dx = bx - ax, dy = by - ay;
        var length = dx * dx + dy * dy;
        var h = length > 0 ? Math.Clamp(((x - ax) * dx + (y - ay) * dy) / length, 0, 1) : 0;
        double px = x - ax - dx * h, py = y - ay - dy * h;
        return Math.Sqrt(px * px + py * py) - radius;
    }

    /// <summary>The smooth union of two distances: pieces within <see cref="Reach"/> of each other join.</summary>
    private static double Join(double a, double b)
    {
        var h = Math.Max(Reach - Math.Abs(a - b), 0) / Reach;
        return Math.Min(a, b) - h * h * Reach / 4;
    }

    private void Draw()
    {
        if (ActualWidth <= 0 || ActualHeight <= 0 || XamlRoot is null) return;
        var scale = XamlRoot.RasterizationScale;
        int w = (int)Math.Ceiling(ActualWidth * scale), h = (int)Math.Ceiling(ActualHeight * scale);
        if (_bitmap is null || _bitmap.PixelWidth != w || _bitmap.PixelHeight != h)
        {
            _bitmap = new WriteableBitmap(w, h);
            _pixels = new byte[w * h * 4];
            _image.Source = _bitmap;
        }
        Array.Clear(_pixels);
        if (_selected is not null && _tabs.TryGetValue(_selected, out var target))
        {
            _shown = true;
            var pieces = Pieces(target);
            // Only where the pieces and their join can reach.
            var box = pieces.Aggregate((a, b) => { a.Union(b); return a; });
            int x0 = Math.Max(0, (int)((box.Left - Reach) * scale)), x1 = Math.Min(w, (int)Math.Ceiling((box.Right + Reach) * scale));
            int y0 = Math.Max(0, (int)((box.Top - Reach) * scale)), y1 = Math.Min(h, (int)Math.Ceiling((box.Bottom + Reach) * scale));
            var height = ActualHeight;
            for (var py = y0; py < y1; py++)
            {
                var y = (py + 0.5) / scale;
                var v = y / height;
                var glare = Glare(v);
                var rimLight = RimLight(v);
                for (var px = x0; px < x1; px++)
                {
                    var x = (px + 0.5) / scale;
                    var d = double.MaxValue;
                    foreach (var piece in pieces) d = d == double.MaxValue ? Capsule(piece, x, y) : Join(d, Capsule(piece, x, y));
                    var cover = Math.Clamp(0.5 - d * scale, 0, 1);
                    if (cover <= 0) continue;
                    // Seen only within the glass, three pixels in.
                    var ground = _grounds.Count == 0 ? -10 : _grounds.Min(g => Capsule(g, x, y));
                    var inside = _grounds.Count == 0 ? 1 : Math.Clamp(0.5 - (ground + 3) * scale, 0, 1);
                    if (inside <= 0) continue;
                    // The rim is the band between the shape and the same a hair inside it.
                    var inner = Math.Clamp(0.5 - (d + Rim) * scale, 0, 1);
                    var band = cover - inner;
                    var alpha = (cover * (0.10 + glare) + band * rimLight) * inside;
                    alpha = Math.Min(1, alpha);
                    var a = (byte)Math.Round(alpha * 255);
                    var i = (py * w + px) * 4;
                    // White, premultiplied.
                    _pixels[i] = _pixels[i + 1] = _pixels[i + 2] = a;
                    _pixels[i + 3] = a;
                }
            }
        }
        using (var stream = _bitmap.PixelBuffer.AsStream()) stream.Write(_pixels, 0, _pixels.Length);
        _bitmap.Invalidate();
    }

    /// <summary>A glare over the top half, and a little caught along the bottom edge (lit glass).</summary>
    private static double Glare(double v) =>
        v < 0.5 ? Lerp(0.30, 0.06, v / 0.5) : v < 0.7 ? Lerp(0.06, 0, (v - 0.5) / 0.2) : Lerp(0, 0.10, (v - 0.7) / 0.3);

    /// <summary>The rim: bright where the light strikes the top edge, faint along the bottom (lit glass).</summary>
    private static double RimLight(double v) =>
        v < 1 / 3.0 ? Lerp(0.95, 0.25, v * 3) : v < 2 / 3.0 ? Lerp(0.25, 0.05, v * 3 - 1) : Lerp(0.05, 0.45, v * 3 - 2);

    private static double Lerp(double a, double b, double t) => a + (b - a) * Math.Clamp(t, 0, 1);
}
