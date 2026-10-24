using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Shell;

/// <summary>
/// The floating bar at the top of the window, as the Mac's: a capsule of glass with the sections,
/// a disc beside it for downloads, the bell, and the back button on the other side. The open tab
/// sits on a pill of brighter glass that flows from tab to tab like a drop of liquid, out of the
/// bar and into the downloads disc too (<see cref="LiquidPill"/>).
/// </summary>
public sealed class NavBar : Grid
{
    private readonly Nav _nav;
    private readonly StackPanel _row = new() { Orientation = Orientation.Horizontal, Spacing = 10 };
    private readonly Grid _capsule = new();
    private readonly LiquidPill _pill = new();
    private readonly Dictionary<Section, FrameworkElement> _tabs = [];
    private readonly StackPanel _items = new() { Orientation = Orientation.Horizontal, Spacing = 2 };
    private readonly Button _back;
    private readonly Button _downloads;
    private readonly Grid _bellSlot = new() { Width = 48, Height = 48 };
    private readonly Border _balance = new() { Width = 48, Height = 48 };
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

    /// <summary>A disc beside the bar, of the bar's own glass.</summary>
    private static (Button Button, Grid Face) Disc(UIElement inside, Action click)
    {
        var face = new Grid { Width = 48, Height = 48 };
        face.Children.Add(GlassLens.Make());
        face.Children.Add(inside);
        return (Theme.Bare(face, click), face);
    }

    /// <summary>The notifications, with how many are new on it, opening their pane under the bar: a disc of its own, as on the Mac.</summary>
    private Button Bell()
    {
        var loc = AppModel.Shared.Loc;
        var unread = AppModel.Shared.Notifications?.Unread ?? 0;
        var inside = new Grid { Margin = new Thickness(4), CornerRadius = new CornerRadius(20), Background = Theme.White(0) };
        var icon = Glyph.Icon("", 16, Theme.Text1.WithAlpha(0.78));
        icon.HorizontalAlignment = HorizontalAlignment.Center;
        inside.Children.Add(icon);
        var (button, face) = Disc(inside, () => { });
        if (unread > 0)
        {
            var count = Theme.Text(loc.Num(Math.Min(unread, 99)), 10, FontWeights.Bold, Colors.White);
            count.HorizontalAlignment = HorizontalAlignment.Center;
            count.VerticalAlignment = VerticalAlignment.Center;
            face.Children.Add(new Border
            {
                MinWidth = 16, Height = 16, CornerRadius = new CornerRadius(8), Background = Theme.Brush(Theme.Accent), Padding = new Thickness(4, 0, 4, 0),
                HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, -2, -2, 0), Child = count,
            });
        }
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
        // The bell stays while its pane is open: marking them all read would otherwise take it, and
        // the pane with it, from under the pointer.
        flyout.Opened += (_, _) => _bellPaneOpen = true;
        flyout.Closed += (_, _) =>
        {
            _bellPaneOpen = false;
            Refresh();
        };
        button.PointerEntered += (_, _) => { inside.Background = Theme.White(0.12); icon.Foreground = Theme.Brush(Theme.Text1); };
        button.PointerExited += (_, _) => { inside.Background = Theme.White(0); icon.Foreground = Theme.Brush(Theme.Text1.WithAlpha(0.78)); };
        ToolTipService.SetToolTip(button, loc[S.notifications]);
        Kit.AutomationName(button, loc[S.notifications]);
        return button;
    }

    public NavBar(Nav nav)
    {
        _nav = nav;
        HorizontalAlignment = HorizontalAlignment.Center;
        VerticalAlignment = VerticalAlignment.Top;
        // Room the size of the bell on the other side, so the bar stays centred.
        _row.Children.Add(_balance);
        // The back button keeps its place beside the bar while it is hidden, so the bar stays centred.
        // Lit under the pointer as the bell and downloads are: a white disc inside, and the arrow brighter.
        var arrow = Glyph.Icon(Glyph.Back, 15, Theme.Text1.WithAlpha(0.78));
        arrow.HorizontalAlignment = HorizontalAlignment.Center;
        arrow.MirroredWhenRightToLeft = true;
        var backInside = new Grid { Margin = new Thickness(4), CornerRadius = new CornerRadius(20), Background = Theme.White(0) };
        backInside.Children.Add(arrow);
        _back = Disc(backInside, nav.Back).Button.Springy(0.2);
        _back.PointerEntered += (_, _) => { backInside.Background = Theme.White(0.12); arrow.Foreground = Theme.Brush(Theme.Text1); };
        _back.PointerExited += (_, _) => { backInside.Background = Theme.White(0); arrow.Foreground = Theme.Brush(Theme.Text1.WithAlpha(0.78)); };
        Kit.AutomationName(_back, AppModel.Shared.Loc[S.back]);
        _row.Children.Add(_back);
        _capsule.Children.Add(GlassLens.Make());
        _items.Margin = new Thickness(4);
        _capsule.Children.Add(_items);
        _row.Children.Add(_capsule);
        // Downloads are not a place in the catalogue; they have a disc of their own beside the bar,
        // which is a tab of the pill too.
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
        ToolTipService.SetToolTip(_downloads, AppModel.Shared.Loc[S.downloads]);
        Kit.AutomationName(_downloads, AppModel.Shared.Loc[S.downloads]);
        _row.Children.Add(_downloads);
        _row.Children.Add(_bellSlot);
        Children.Add(_row);
        // Over the glass and the words: the words are white, and the pill's light over them leaves them so.
        // On a canvas, so it takes the row's size rather than giving the bar one.
        // Left to right, and measured in its own space: a mirrored bitmap would put the pill at the wrong end.
        var stage = new Canvas { IsHitTestVisible = false, FlowDirection = FlowDirection.LeftToRight };
        stage.Children.Add(_pill);
        Children.Add(stage);
        _row.SizeChanged += (_, e) =>
        {
            _pill.Width = e.NewSize.Width;
            _pill.Height = e.NewSize.Height;
        };
        _row.LayoutUpdated += (_, _) => PlacePill();
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

    /// <summary>Tells the pill where the glass and the tabs are now, in this bar's own space.</summary>
    private void PlacePill()
    {
        if (XamlRoot is null || ActualWidth <= 0 || _pill.ActualWidth <= 0) return;
        Windows.Foundation.Rect Frame(FrameworkElement e) => e.TransformToVisual(_pill).TransformBounds(new Windows.Foundation.Rect(0, 0, e.ActualWidth, e.ActualHeight));
        var tabs = new Dictionary<object, Windows.Foundation.Rect>();
        foreach (var (section, tab) in _tabs) if (tab.ActualWidth > 0) tabs[section] = Frame(tab);
        var disc = Frame(_downloads);
        // The pill in the disc is the size of a tab in the bar: four pixels in.
        tabs[Section.Downloads] = new Windows.Foundation.Rect(disc.X + 4, disc.Y + 4, disc.Width - 8, disc.Height - 8);
        _pill.Place([Frame(_capsule), disc], tabs);
    }

    /// <summary>
    /// The downloads disc: under the pill while its page is open, and while files are coming in, a
    /// red ring of how far they have all got and a count of them, as on the Mac.
    /// </summary>
    private void ShowDownloads()
    {
        var busy = Downloads.Shared.Items.Where(i => i.State is DownloadItem.States.Running or DownloadItem.States.Paused).ToList();
        _downloadsFill.Background = Theme.White(!_downloadsLit && _downloadsHover ? 0.12 : 0);
        _downloadsIcon.Glyph = busy.Count > 0 ? "" : Glyph.Download;
        _downloadsIcon.Foreground = Theme.Brush(Theme.Text1.WithAlpha(_downloadsLit || _downloadsHover ? 1 : 0.78));
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

    private bool _bellShown, _bellPaneOpen;

    /// <summary>The bell comes and goes with a fade and a little growing, as on the Mac.</summary>
    private void ShowBell(bool show)
    {
        var was = _bellShown;
        _bellShown = show;
        if (!show)
        {
            if (!was) return;
            if (_bellSlot.Children.FirstOrDefault() is FrameworkElement leaving)
            {
                leaving.Opacity = 0;
                leaving.ScaleTo(0.85);
            }
            _ = Task.Delay(260).ContinueWith(_ => DispatcherQueue.TryEnqueue(() =>
            {
                if (_bellShown) return;
                _bellSlot.Children.Clear();
                _bellSlot.Visibility = _balance.Visibility = Visibility.Collapsed;
            }), TaskScheduler.Default);
            return;
        }
        // Not while its pane is open: the pane hangs from it.
        if (was && _bellPaneOpen) return;
        // Built again each time: its count of unread ones may have changed.
        _bellSlot.Children.Clear();
        var bell = Bell();
        _bellSlot.Children.Add(bell);
        _bellSlot.Visibility = _balance.Visibility = Visibility.Visible;
        if (was)
        {
            bell.Springy(0.25);
            return;
        }
        // Its starting look is set before its transitions are, so it grows from it rather than to it.
        bell.Opacity = 0;
        bell.Loaded += (_, _) =>
        {
            bell.ScaleTo(0.85);
            DispatcherQueue.TryEnqueue(() =>
            {
                bell.Springy(0.25);
                bell.Opacity = 1;
                bell.ScaleTo(1);
            });
        };
    }

    public void Refresh()
    {
        var loc = AppModel.Shared.Loc;
        var showBack = _nav.Opened.Count > 0;
        _back.Opacity = showBack ? 1 : 0;
        _back.IsHitTestVisible = showBack;
        _back.IsTabStop = showBack;
        _downloadsLit = _nav.Section == Section.Downloads && _nav.Opened.Count == 0;
        ShowDownloads();
        // The bell only while 30nama has something new to tell: with none, it would take room and the
        // eye for nothing. The room on the other side, which keeps the bar centred, goes with it. Read
        // ones fill the list too; they are under "Notifications" on the profile.
        var any = AppModel.Shared.IsLoggedIn && (AppModel.Shared.Notifications is { Unread: > 0 } || _bellPaneOpen);
        ShowBell(any);
        _items.Children.Clear();
        _tabs.Clear();
        foreach (var section in Nav.OnBar)
        {
            var selected = _nav.Section == section && _nav.Opened.Count == 0;
            var ink = Theme.Text1.WithAlpha(selected ? 1 : 0.7);
            var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 7, VerticalAlignment = VerticalAlignment.Center };
            if (Nav.IconOnly(section) || section == Section.Profile) label.Children.Add(Glyph.Icon(Nav.Symbol(section), 16, ink));
            if (!Nav.IconOnly(section)) label.Children.Add(Theme.Text(loc[Nav.Title(section)], 15.5, FontWeights.SemiBold, ink));
            var face = new Border
            {
                Height = 40,
                Padding = new Thickness(Nav.IconOnly(section) ? 13 : 17, 0, Nav.IconOnly(section) ? 13 : 17, 0),
                CornerRadius = new CornerRadius(20),
                Background = Theme.White(0),
                Child = label,
            };
            var button = Theme.Bare(face, () => _nav.Go(section));
            if (!selected)
            {
                button.PointerEntered += (_, _) => { face.Background = Theme.White(0.12); label.Opacity = 1; foreach (var c in label.Children) Tint(c, Theme.Text1); };
                button.PointerExited += (_, _) => { face.Background = Theme.White(0); foreach (var c in label.Children) Tint(c, Theme.Text1.WithAlpha(0.7)); };
            }
            ToolTipService.SetToolTip(button, loc[Nav.Title(section)]);
            Kit.AutomationName(button, loc[Nav.Title(section)]);
            _items.Children.Add(button);
            _tabs[section] = face;
        }
        // No pill while a title or other page is open over the sections.
        _pill.Select(_nav.Opened.Count == 0 ? _nav.Section : null);
        PlacePill();
    }

    private static void Tint(UIElement e, Windows.UI.Color colour)
    {
        if (e is TextBlock t) t.Foreground = Theme.Brush(colour);
        else if (e is FontIcon i) i.Foreground = Theme.Brush(colour);
    }
}
