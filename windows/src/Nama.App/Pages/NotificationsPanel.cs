using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// What 30nama has to tell the account, on a pane of glass under the bar: the new ones and the
/// earlier ones, each opening what it is about. From 30nama's own desktop app; the Mac app has none.
/// </summary>
public sealed class NotificationsPanel : Grid
{
    private static AppModel App => AppModel.Shared;
    private static Loc Loc => App.Loc;

    private readonly Nav _nav;
    private readonly Action _close;
    private readonly StackPanel _list = new() { Spacing = 8 };
    private bool _earlier;

    public NotificationsPanel(Nav nav, Action close)
    {
        _nav = nav;
        _close = close;
        Width = 440;
        MaxHeight = 560;
        CornerRadius = new CornerRadius(22);
        Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(22), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        var column = new Grid { Padding = new Thickness(18), RowSpacing = 14 };
        column.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        column.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        column.Children.Add(Header());
        var scroller = new ScrollViewer { Content = _list, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        SetRow(scroller, 1);
        column.Children.Add(scroller);
        Children.Add(column);
        Render();
    }

    private FrameworkElement Header()
    {
        var row = new Grid { ColumnSpacing = 10 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.Children.Add(Theme.Text(Loc[Extra.notifications], 18, FontWeights.Bold));
        var mark = Kit.Glass(Loc[Extra.markAllRead], null, () => { _ = App.MarkReadAsync("all"); Render(); }, height: 30);
        SetColumn(mark, 1);
        row.Children.Add(mark);
        return row;
    }

    private void Render()
    {
        _list.Children.Clear();
        var pills = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 0, 0, 6) };
        pills.Children.Add(Kit.Pill(Loc[Extra.unread], !_earlier, () => { _earlier = false; Render(); }));
        pills.Children.Add(Kit.Pill(Loc[Extra.earlier], _earlier, () => { _earlier = true; Render(); }));
        _list.Children.Add(pills);
        var shown = App.Notifications?.Items.Where(n => n.Read == _earlier).ToList() ?? [];
        if (shown.Count == 0)
        {
            var none = Theme.Text(Loc[Extra.noNotifications], 15, FontWeights.Medium, Theme.Text3);
            none.HorizontalAlignment = HorizontalAlignment.Center;
            none.Margin = new Thickness(0, 30, 0, 30);
            _list.Children.Add(none);
            return;
        }
        foreach (var notification in shown) _list.Children.Add(Row(notification));
    }

    private Button Row(AccountNotification n)
    {
        var face = new Grid { Padding = new Thickness(12), CornerRadius = new CornerRadius(14), Background = Theme.White(0.06), ColumnSpacing = 12 };
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var picture = n.Image ?? n.Post?.Poster.Url(60);
        if (picture is not null)
        {
            var thumb = new Grid { Width = 44, Height = 66, CornerRadius = new CornerRadius(8), Background = Theme.Brush(Theme.Surface2), VerticalAlignment = VerticalAlignment.Top };
            thumb.Children.Add(Images.Remote(picture, n.Post?.Poster.Blurhash, 90));
            face.Children.Add(thumb);
        }
        else
        {
            var dot = new Border { Width = 10, Height = 10, CornerRadius = new CornerRadius(5), Background = Theme.Brush(n.Read ? Theme.Text3 : Theme.Accent), VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 6, 0, 0) };
            face.Children.Add(dot);
        }
        var text = new StackPanel { Spacing = 4 };
        if (n.Title is { } title) text.Children.Add(Theme.Text(title, 15, FontWeights.Bold, lines: 2));
        if (n.Content is { } content) text.Children.Add(Theme.Text(content, 13, color: Theme.Text2, lines: 3));
        if ((Loc.Lang == Lang.Fa ? n.Date.Jalali ?? n.Date.Gregorian : n.Date.Gregorian ?? n.Date.Jalali) is { } date)
            text.Children.Add(Theme.Text(Loc.Num(date), 11, color: Theme.Text3));
        Grid.SetColumn(text, 1);
        face.Children.Add(text);
        var button = Theme.Bare(face, () => Open(n));
        button.HorizontalAlignment = HorizontalAlignment.Stretch;
        button.PointerEntered += (_, _) => face.Background = Theme.White(0.12);
        button.PointerExited += (_, _) => face.Background = Theme.White(0.06);
        return button;
    }

    /// <summary>Reads the message and goes where it points: a title, its player, a page on the web.</summary>
    private void Open(AccountNotification n)
    {
        if (!n.Read) _ = App.MarkReadAsync(n.Id);
        switch (n.Kind)
        {
            case AccountNotification.Kinds.Post when n.Post is { } post:
                _close();
                _nav.Open(post);
                break;
            case AccountNotification.Kinds.Stream when n.Post is { } post:
                _close();
                _nav.Open(post, playing: true);
                break;
            case AccountNotification.Kinds.Pricing:
                _ = Windows.System.Launcher.LaunchUriAsync(new Uri("https://30nama.com/pricing"));
                break;
            default:
                if (n.Link is { } link) _ = Windows.System.Launcher.LaunchUriAsync(link);
                break;
        }
        Render();
    }
}
