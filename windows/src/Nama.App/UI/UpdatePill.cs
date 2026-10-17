using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Nama.App.Model;
using Nama.Core;

namespace Nama.App.UI;

/// <summary>
/// A newer version, told in a glass capsule that drops from under the top bar. While it
/// downloads, the capsule fills with the app's red from the side a line starts on; full, it
/// offers to relaunch into the new version. As UpdatePill.swift.
/// </summary>
public sealed class UpdatePill : Grid
{
    /// <summary>Where its top sits: just clear of the bar's lower edge.</summary>
    public const double Below = 14 + 48 + 6;

    private static Updater Updater => Updater.Shared;
    private static Loc Loc => AppModel.Shared.Loc;

    private readonly Action _quit;
    private readonly Border _fill = new() { HorizontalAlignment = HorizontalAlignment.Left, Width = 0 };
    private readonly StackPanel _row = new() { Orientation = Orientation.Horizontal, Spacing = 12, Padding = new Thickness(16, 0, 6, 0) };

    public UpdatePill(Action quit)
    {
        _quit = quit;
        Height = 44;
        CornerRadius = new CornerRadius(22);
        HorizontalAlignment = HorizontalAlignment.Center;
        VerticalAlignment = VerticalAlignment.Top;
        Margin = new Thickness(0, Below, 0, 0);
        Children.Add(new Border { Background = Theme.GlassFill() });
        _fill.Background = new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0, 0.5), EndPoint = new Windows.Foundation.Point(1, 0.5),
            GradientStops =
            {
                new GradientStop { Color = Theme.Accent.WithAlpha(0.95), Offset = 0 },
                new GradientStop { Color = Theme.Hex(0xFF7A5C, 0.9), Offset = 1 },
            },
        };
        Children.Add(_fill);
        Children.Add(new Border { CornerRadius = new CornerRadius(22), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        _row.VerticalAlignment = VerticalAlignment.Center;
        Children.Add(_row);
        // Out from behind the bar.
        Opacity = 0;
        Translation = new System.Numerics.Vector3(0, -24, 0);
        this.Springy(0.6);
        Loaded += (_, _) => { Opacity = 1; Translation = default; };
        SizeChanged += (_, _) => Show();
        Updater.Changed += Show;
        Unloaded += (_, _) => Updater.Changed -= Show;
        Show();
    }

    private void Show()
    {
        var phase = Updater.Phase;
        var fill = phase switch { Updater.Phases.Downloading => Updater.Progress, Updater.Phases.Ready => 1, _ => 0 };
        _fill.Width = ActualWidth * fill;
        _row.Children.Clear();
        var icon = Glyph.Icon(phase switch
        {
            Updater.Phases.Ready => Glyph.Check,
            Updater.Phases.Failed => "",
            _ => Glyph.Download,
        }, 15, fill > 0 ? Colors.White : Theme.Accent);
        icon.VerticalAlignment = VerticalAlignment.Center;
        _row.Children.Add(icon);
        var version = Updater.Release?.Version ?? "";
        var message = phase switch
        {
            Updater.Phases.Downloading => $"{Loc[S.updating]} {Loc.Num((int)(Updater.Progress * 100))}{(Loc.Lang == Lang.En ? "%" : "٪")}",
            Updater.Phases.Ready => Loc[S.updateReady],
            Updater.Phases.Failed => Loc[S.updateFailed],
            _ => $"{Loc[S.newVersion]} {Loc.Num(version)}",
        };
        var text = Theme.Text(message, 14, FontWeights.SemiBold);
        text.VerticalAlignment = VerticalAlignment.Center;
        _row.Children.Add(text);
        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        switch (phase)
        {
            case Updater.Phases.Available:
                actions.Children.Add(Button(Loc[S.installUpdate], null, true, Updater.Install));
                actions.Children.Add(Button(Loc[S.later], Glyph.Close, false, Updater.Postpone));
                break;
            case Updater.Phases.Ready:
                actions.Children.Add(Button(Loc[S.relaunch], null, true, () => Updater.Relaunch(_quit)));
                break;
            case Updater.Phases.Failed:
                actions.Children.Add(Button(Loc[S.downloadPage], null, true, Updater.OpenPage));
                actions.Children.Add(Button(Loc[S.later], Glyph.Close, false, Updater.Postpone));
                break;
            default:
                actions.Children.Add(new Border { Width = 6 });
                break;
        }
        _row.Children.Add(actions);
    }

    private static Button Button(string title, string? glyph, bool prominent, Action click)
    {
        var face = new Border
        {
            Height = 32, CornerRadius = new CornerRadius(16), MinWidth = 32,
            Padding = glyph is null ? new Thickness(14, 0, 14, 0) : new Thickness(0),
            Background = prominent ? Theme.White(0.92) : Theme.White(0),
        };
        FrameworkElement content = glyph is null
            ? Theme.Text(title, 13.5, FontWeights.Bold, prominent ? Theme.FocusInk : Theme.Text1)
            : Glyph.Icon(glyph, 11, Theme.Text1.WithAlpha(0.7));
        content.HorizontalAlignment = HorizontalAlignment.Center;
        content.VerticalAlignment = VerticalAlignment.Center;
        face.Child = content;
        var button = Theme.Bare(face, click);
        button.PointerEntered += (_, _) => face.Background = prominent ? Theme.White(1) : Theme.White(0.14);
        button.PointerExited += (_, _) => face.Background = prominent ? Theme.White(0.92) : Theme.White(0);
        Kit.AutomationName(button, title);
        if (glyph is not null) ToolTipService.SetToolTip(button, title);
        return button;
    }
}
