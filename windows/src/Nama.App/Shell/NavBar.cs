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
        _back = Kit.Glass(null, Glyph.Back, nav.Back, height: 48).Springy(0.2);
        Kit.AutomationName(_back, AppModel.Shared.Loc[Core.S.back]);
        Children.Add(_back);
        var capsule = new Grid();
        capsule.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(24), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        _items.Margin = new Thickness(4);
        capsule.Children.Add(_items);
        Children.Add(capsule);
        // Downloads are not a place in the catalogue; they have a disc of their own beside the bar,
        // which also balances the back button so the bar sits in the middle of the window.
        _downloads = Kit.Glass(null, Glyph.Download, () => _nav.Go(Section.Downloads), height: 48);
        ToolTipService.SetToolTip(_downloads, AppModel.Shared.Loc[Core.S.downloads]);
        Kit.AutomationName(_downloads, AppModel.Shared.Loc[Core.S.downloads]);
        Children.Add(_downloads);
        nav.Changed += Refresh;
        AppModel.Shared.Changed += OnChanged;
        Unloaded += (_, _) => AppModel.Shared.Changed -= OnChanged;
        Refresh();
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
        var onDownloads = _nav.Section == Section.Downloads && _nav.Opened.Count == 0;
        if (_downloads.Content is Grid disc)
        {
            disc.Children[1] = new Border { Background = onDownloads ? Theme.Brush(Microsoft.UI.Colors.White) : Theme.White(0.1), CornerRadius = new CornerRadius(24) };
            if (disc.Children[^1] is FontIcon icon) icon.Foreground = Theme.Brush(onDownloads ? Theme.FocusInk : Theme.Text1);
        }
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
