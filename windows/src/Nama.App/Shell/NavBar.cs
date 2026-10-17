using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Nama.App.Model;
using Nama.App.UI;

namespace Nama.App.Shell;

/// <summary>The floating bar at the top of the window: the same glass capsule the Mac app has.</summary>
public sealed class NavBar : StackPanel
{
    private readonly Nav _nav;
    private readonly Button _back;
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
        // Balances the back button, so the bar sits in the middle of the window.
        Children.Add(new Border { Width = 48 });
        nav.Changed += Refresh;
        Refresh();
    }

    public void Refresh()
    {
        var loc = AppModel.Shared.Loc;
        var showBack = _nav.Opened.Count > 0;
        _back.Opacity = showBack ? 1 : 0;
        _back.IsHitTestVisible = showBack;
        _back.IsTabStop = showBack;
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
        }
    }
}
