using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Nama.App.Model;
using Nama.Core;

namespace Nama.App.UI;

/// <summary>
/// What 30nama has lately added or mended on a title, in a thin line of plain words under its
/// buttons: one change at a time with its day, the next coming in every four seconds, round and
/// round. Still on the newest when there is one, when motion is turned down, and when the window
/// is not the one in use. As UpdatesTicker on the Mac.
/// </summary>
public sealed class UpdatesTicker : Grid
{
    private readonly IReadOnlyList<TitleUpdate> _updates;
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromSeconds(4) };
    private FrameworkElement? _line;
    private int _index;

    public UpdatesTicker(IReadOnlyList<TitleUpdate> updates)
    {
        _updates = updates;
        // Just the words: the same height every time, so the page below does not move as they change.
        Height = 22;
        MaxWidth = 640;
        HorizontalAlignment = HorizontalAlignment.Left;
        SizeChanged += (_, e) => Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, 0, e.NewSize.Width, e.NewSize.Height) };
        Show(0, moving: false);
        _timer.Tick += (_, _) =>
        {
            if (AppModel.Shared.Settings.Calm || !MainWindow.IsActive) return;
            Show((_index + 1) % _updates.Count, moving: true);
        };
        Loaded += (_, _) => { if (_updates.Count > 1) _timer.Start(); };
        Unloaded += (_, _) => _timer.Stop();
    }

    private void Show(int index, bool moving)
    {
        _index = index;
        var loc = AppModel.Shared.Loc;
        var update = _updates[index];
        // In the direction of the interface, so the day stays at the line's start in Persian too.
        var line = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10, VerticalAlignment = VerticalAlignment.Center };
        if (update.Day(persian: loc.Lang == Lang.Fa) is { } day)
        {
            var date = Theme.Text(loc.Num(day), 13, color: Theme.Text3);
            date.FlowDirection = FlowDirection.LeftToRight;
            line.Children.Add(date);
        }
        line.Children.Add(Theme.Text(loc.Num(update.Title), 13, FontWeights.SemiBold, Theme.Text1));
        if (update.Content is { } content) line.Children.Add(Theme.Text(loc.Num(content), 13, color: Theme.Text2));
        var old = _line;
        _line = line;
        if (!moving)
        {
            Children.Add(line);
            return;
        }
        // One after the other, never two at once: the old line goes up and out, then the new one
        // comes up from below. Its starting place is set before its transitions are, so it does not
        // first fade from full to nothing over the old one.
        line.Opacity = 0;
        line.Translation = new System.Numerics.Vector3(0, 9, 0);
        line.Springy(0.25);
        if (old is not null)
        {
            old.Springy(0.25);
            old.Opacity = 0;
            old.Translation = new System.Numerics.Vector3(0, -9, 0);
        }
        _ = Task.Delay(260).ContinueWith(_ => DispatcherQueue.TryEnqueue(() =>
        {
            if (old is not null) Children.Remove(old);
            if (_line != line) return;
            Children.Add(line);
            DispatcherQueue.TryEnqueue(() =>
            {
                line.Opacity = 1;
                line.Translation = default;
            });
        }), TaskScheduler.Default);
    }
}
