using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Shell;

/// <summary>The floating bar at the top of the window: the same glass capsule the Mac app has.</summary>
public sealed class NavBar : StackPanel
{
    private readonly Nav _nav;
    private readonly Button _back;
    private readonly Button _downloads;
    private readonly Border _downloadsFill = new() { Margin = new Thickness(4), CornerRadius = new CornerRadius(20) };
    private readonly FontIcon _downloadsIcon = Glyph.Icon(Glyph.Download, 16, Theme.Text1.WithAlpha(0.78));
    private readonly ProgressRing _downloadsRing = new()
    {
        Width = 48, Height = 48, IsIndeterminate = false, Minimum = 0, Maximum = 1, Visibility = Visibility.Collapsed,
        Foreground = Theme.Brush(Theme.Accent), Background = Theme.White(0.14), IsHitTestVisible = false,
    };
    private readonly Border _downloadsCount = new()
    {
        MinWidth = 19, Height = 19, CornerRadius = new CornerRadius(9.5), Background = Theme.Brush(Theme.Accent), Padding = new Thickness(5, 0, 5, 0),
        HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, -3, -3, 0), Visibility = Visibility.Collapsed,
    };
    private bool _downloadsLit, _downloadsHover;

    /// <summary>
    /// A disc beside the bar, made as the bar is: the same glass and rim, and inside it the same
    /// white pill when its page is the open one. Not the lighter glass of the app's other buttons.
    /// </summary>
    private static (Button Button, Grid Face) Disc(UIElement inside, Action click)
    {
        var face = new Grid { Width = 48, Height = 48 };
        face.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(24) });
        face.Children.Add(new Border { CornerRadius = new CornerRadius(24), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        face.Children.Add(inside);
        return (Theme.Bare(face, click), face);
    }

    /// <summary>The notifications, with how many are new on it, opening their pane under the bar.</summary>
    private Button Bell()
    {
        var loc = AppModel.Shared.Loc;
        var unread = AppModel.Shared.Notifications?.Unread ?? 0;
        var face = new Grid { Height = 40, Padding = new Thickness(13, 0, 13, 0), CornerRadius = new CornerRadius(20), Background = Theme.White(0) };
        face.Children.Add(Glyph.Icon("", 16, Theme.Text1.WithAlpha(0.78)));
        if (unread > 0)
        {
            var count = Theme.Text(loc.Num(Math.Min(unread, 99)), 10, Microsoft.UI.Text.FontWeights.Bold, Microsoft.UI.Colors.White);
            count.HorizontalAlignment = HorizontalAlignment.Center;
            count.VerticalAlignment = VerticalAlignment.Center;
            face.Children.Add(new Border
            {
                MinWidth = 16, Height = 16, CornerRadius = new CornerRadius(8), Background = Theme.Brush(Theme.Accent), Padding = new Thickness(4, 0, 4, 0),
                HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 4, -6, 0), Child = count,
            });
        }
        var button = Theme.Bare(face, () => { });
        var flyout = new Flyout
        {
            Placement = Microsoft.UI.Xaml.Controls.Primitives.FlyoutPlacementMode.Bottom,
            FlyoutPresenterStyle = new Style(typeof(FlyoutPresenter))
            {
                Setters =
                {
                    new Setter(Control.BackgroundProperty, Theme.White(0)),
                    new Setter(Control.BorderThicknessProperty, new Thickness(0)),
                    new Setter(Control.PaddingProperty, new Thickness(0)),
                    new Setter(FrameworkElement.MaxWidthProperty, 600.0),
                },
            },
        };
        button.Click += (_, _) =>
        {
            flyout.Content = new Pages.NotificationsPanel(_nav, flyout.Hide) { FlowDirection = FlowDirection };
            flyout.ShowAt(button);
        };
        button.PointerEntered += (_, _) => face.Background = Theme.White(0.12);
        button.PointerExited += (_, _) => face.Background = Theme.White(0);
        ToolTipService.SetToolTip(button, loc[Extra.notifications]);
        Kit.AutomationName(button, loc[Extra.notifications]);
        return button;
    }
    private readonly StackPanel _items = new() { Orientation = Orientation.Horizontal, Spacing = 2 };

    public NavBar(Nav nav)
    {
        _nav = nav;
        Orientation = Orientation.Horizontal;
        Spacing = 10;
        HorizontalAlignment = HorizontalAlignment.Center;
        VerticalAlignment = VerticalAlignment.Top;
        // The back button keeps its place beside the bar while it is hidden, so the bar stays centred.
        var arrow = Glyph.Icon(Glyph.Back, 15, Theme.Text1);
        arrow.HorizontalAlignment = HorizontalAlignment.Center;
        arrow.MirroredWhenRightToLeft = true;
        _back = Disc(arrow, nav.Back).Button.Springy(0.2);
        Kit.AutomationName(_back, AppModel.Shared.Loc[Core.S.back]);
        Children.Add(_back);
        var capsule = new Grid();
        capsule.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(24), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        _items.Margin = new Thickness(4);
        capsule.Children.Add(_items);
        Children.Add(capsule);
        // Downloads are not a place in the catalogue; they have a disc of their own beside the bar,
        // which also balances the back button so the bar sits in the middle of the window.
        var inside = new Grid();
        inside.Children.Add(_downloadsFill);
        inside.Children.Add(_downloadsRing);
        _downloadsIcon.HorizontalAlignment = HorizontalAlignment.Center;
        inside.Children.Add(_downloadsIcon);
        var count = Theme.Text("", 11, FontWeights.ExtraBold, Colors.White);
        count.HorizontalAlignment = HorizontalAlignment.Center;
        count.VerticalAlignment = VerticalAlignment.Center;
        _downloadsCount.Child = count;
        var (downloads, discFace) = Disc(inside, () => _nav.Go(Section.Downloads));
        discFace.Children.Add(_downloadsCount);
        _downloads = downloads;
        _downloads.PointerEntered += (_, _) => { _downloadsHover = true; ShowDownloads(); };
        _downloads.PointerExited += (_, _) => { _downloadsHover = false; ShowDownloads(); };
        ToolTipService.SetToolTip(_downloads, AppModel.Shared.Loc[Core.S.downloads]);
        Kit.AutomationName(_downloads, AppModel.Shared.Loc[Core.S.downloads]);
        Children.Add(_downloads);
        nav.Changed += Refresh;
        AppModel.Shared.Changed += OnChanged;
        Downloads.Shared.Changed += ShowDownloads;
        Unloaded += (_, _) =>
        {
            AppModel.Shared.Changed -= OnChanged;
            Downloads.Shared.Changed -= ShowDownloads;
        };
        Refresh();
    }

    /// <summary>
    /// The downloads disc: white while its page is open, and while files are coming in, a red ring
    /// of how far they have all got and a count of them, as on the Mac.
    /// </summary>
    private void ShowDownloads()
    {
        var busy = Downloads.Shared.Items.Where(i => i.State is DownloadItem.States.Running or DownloadItem.States.Paused).ToList();
        _downloadsFill.Background = _downloadsLit ? Theme.Brush(Colors.White) : Theme.White(_downloadsHover ? 0.12 : 0);
        _downloadsIcon.Glyph = busy.Count > 0 ? "\uE74B" : Glyph.Download;
        _downloadsIcon.Foreground = Theme.Brush(_downloadsLit ? Theme.FocusInk : Theme.Text1.WithAlpha(_downloadsHover ? 1 : 0.78));
        _downloadsRing.Visibility = _downloadsCount.Visibility = busy.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        if (busy.Count == 0) return;
        long expected = busy.Sum(i => i.Expected), received = busy.Sum(i => i.Received);
        _downloadsRing.Value = expected > 0 ? Math.Max(0.02, Math.Min(1, (double)received / expected)) : 0.02;
        ((TextBlock)_downloadsCount.Child).Text = AppModel.Shared.Loc.Num(busy.Count);
    }

    private void OnChanged(string what)
    {
        if (what is "notifications" or "user") Refresh();
    }

    public void Refresh()
    {
        var loc = AppModel.Shared.Loc;
        var showBack = _nav.Opened.Count > 0;
        _back.Opacity = showBack ? 1 : 0;
        _back.IsHitTestVisible = showBack;
        _back.IsTabStop = showBack;
        // Lit while its section is open, as the bar's own items are.
        _downloadsLit = _nav.Section == Section.Downloads && _nav.Opened.Count == 0;
        ShowDownloads();
        _items.Children.Clear();
        foreach (var section in Nav.OnBar)
        {
            var selected = _nav.Section == section && _nav.Opened.Count == 0;
            var ink = selected ? Theme.FocusInk : Theme.Text1.WithAlpha(0.78);
            var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 7, VerticalAlignment = VerticalAlignment.Center };
            if (Nav.IconOnly(section) || section == Section.Profile) label.Children.Add(Glyph.Icon(Nav.Symbol(section), 16, ink));
            if (!Nav.IconOnly(section)) label.Children.Add(Theme.Text(loc[Nav.Title(section)], 15.5, FontWeights.SemiBold, ink));
            var face = new Border
            {
                Height = 40,
                Padding = new Thickness(Nav.IconOnly(section) ? 13 : 17, 0, Nav.IconOnly(section) ? 13 : 17, 0),
                CornerRadius = new CornerRadius(20),
                Background = selected ? Theme.Brush(Colors.White) : Theme.White(0),
                Child = label,
            };
            var button = Theme.Bare(face, () => _nav.Go(section));
            if (!selected)
            {
                button.PointerEntered += (_, _) => { face.Background = Theme.White(0.12); ((Microsoft.UI.Xaml.Controls.Panel)face.Child).Opacity = 1; };
                button.PointerExited += (_, _) => face.Background = Theme.White(0);
            }
            ToolTipService.SetToolTip(button, loc[Nav.Title(section)]);
            Kit.AutomationName(button, loc[Nav.Title(section)]);
            _items.Children.Add(button);
            // The bell sits before settings, for someone signed in: what 30nama has to tell them.
            if (section == Section.Profile && AppModel.Shared.IsLoggedIn) _items.Children.Add(Bell());
        }
    }
}
