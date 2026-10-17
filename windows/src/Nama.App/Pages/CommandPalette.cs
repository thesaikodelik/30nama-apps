using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;
using Windows.System;

namespace Nama.App.Pages;

/// <summary>
/// Search from anywhere: Ctrl+K brings a bar up in the middle of the window that finds titles as
/// they are typed and the app's own sections by name. The arrows choose, Enter opens. As
/// CommandPalette.swift.
/// </summary>
public sealed class CommandPalette : Grid
{
    private static AppModel App => AppModel.Shared;
    private static Loc Loc => App.Loc;

    private abstract record Row
    {
        public sealed record Place(Section Section) : Row;
        public sealed record Title(Post Post) : Row;
    }

    private readonly Nav _nav;
    private readonly Action _close;
    private readonly TextBox _field = new();
    private readonly ProgressRing _busy = new() { Width = 16, Height = 16, IsActive = false };
    private readonly StackPanel _list = new() { Spacing = 2, Padding = new Thickness(8) };
    private readonly Border _divider = new() { Height = 1, Background = Theme.White(0.1) };
    private List<Post> _posts = [];
    private List<Row> _rows = [];
    private int _chosen;
    private CancellationTokenSource? _typing;

    public CommandPalette(Nav nav, Action close)
    {
        _nav = nav;
        _close = close;
        var dim = new Grid { Background = Theme.Black(0.45) };
        dim.Tapped += (_, e) => { if (ReferenceEquals(e.OriginalSource, dim)) _close(); };
        Children.Add(dim);

        var card = new Grid { Width = 640, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 110, 0, 0), CornerRadius = new CornerRadius(22) };
        card.Children.Add(new Border { Background = Theme.GlassFill() });
        card.Children.Add(new Border { Background = Theme.Brush(Theme.Bg1, 0.6) });
        var column = new StackPanel();
        column.Children.Add(Field());
        column.Children.Add(_divider);
        column.Children.Add(_list);
        card.Children.Add(column);
        card.Children.Add(new Border { CornerRadius = new CornerRadius(22), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.2), IsHitTestVisible = false });
        card.Translation = new System.Numerics.Vector3(0, 0, 40);
        card.Shadow = new Microsoft.UI.Xaml.Media.ThemeShadow();
        Children.Add(card);

        Opacity = 0;
        this.Springy(0.18);
        Loaded += (_, _) =>
        {
            Opacity = 1;
            _field.Focus(FocusState.Programmatic);
        };
        Render();
    }

    private FrameworkElement Field()
    {
        var row = new Grid { Height = 62, Padding = new Thickness(20, 0, 20, 0), ColumnSpacing = 12 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(20) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var mark = new Grid { VerticalAlignment = VerticalAlignment.Center };
        mark.Children.Add(Glyph.Icon(Glyph.Search, 18, Theme.Text2));
        mark.Children.Add(_busy);
        row.Children.Add(mark);
        _field.PlaceholderText = Loc[S.searchPrompt];
        _field.FontSize = 20;
        _field.FontFamily = Theme.Font(FontWeights.Medium);
        _field.BorderThickness = new Thickness(0);
        _field.Background = Theme.White(0);
        _field.VerticalAlignment = VerticalAlignment.Center;
        _field.Resources["TextControlBackgroundFocused"] = Theme.White(0);
        _field.Resources["TextControlBackgroundPointerOver"] = Theme.White(0);
        _field.Resources["TextControlBorderBrushFocused"] = Theme.White(0);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(_field, Loc[S.search]);
        _field.TextChanged += (_, _) => Typed();
        // Taken before the field sees them: the arrows choose a row, Enter opens it.
        _field.PreviewKeyDown += OnKey;
        Grid.SetColumn(_field, 1);
        row.Children.Add(_field);
        var esc = new Border { Height = 22, Padding = new Thickness(7, 0, 7, 0), CornerRadius = new CornerRadius(6), Background = Theme.White(0.1), VerticalAlignment = VerticalAlignment.Center };
        var label = Theme.Text("esc", 11, FontWeights.SemiBold, Theme.Text3);
        label.VerticalAlignment = VerticalAlignment.Center;
        esc.Child = label;
        Grid.SetColumn(esc, 2);
        row.Children.Add(esc);
        return row;
    }

    private void OnKey(object sender, KeyRoutedEventArgs e)
    {
        switch (e.Key)
        {
            case VirtualKey.Escape:
                _close();
                break;
            case VirtualKey.Down:
                Move(1);
                break;
            case VirtualKey.Up:
                Move(-1);
                break;
            case VirtualKey.Enter when _chosen < _rows.Count:
                Open(_rows[_chosen]);
                break;
            default:
                return;
        }
        e.Handled = true;
    }

    private void Move(int step)
    {
        if (_rows.Count == 0) return;
        _chosen = (_chosen + step + _rows.Count) % _rows.Count;
        Render();
    }

    /// <summary>Waits for a pause in the typing: each search is a request to a server that counts them.</summary>
    private void Typed()
    {
        _typing?.Cancel();
        var typing = _typing = new CancellationTokenSource();
        var query = _field.Text.Trim();
        if (query.Length < 2)
        {
            _posts = [];
            _busy.IsActive = false;
            Render();
            return;
        }
        Render();
        _ = SearchAsync(query, typing.Token);
    }

    private async Task SearchAsync(string query, CancellationToken cancel)
    {
        try { await Task.Delay(380, cancel); } catch (TaskCanceledException) { return; }
        _busy.IsActive = true;
        List<Post> found;
        try
        {
            found = new PostPage(await App.Api.GetAsync("search", new Dictionary<string, string>
            {
                ["query"] = query, ["streamonly"] = "0", ["page"] = "1",
            })).Posts.ToList();
        }
        catch (Exception) { found = []; }
        if (cancel.IsCancellationRequested) return;
        _posts = found;
        _busy.IsActive = false;
        Render();
    }

    private void Render()
    {
        var query = _field.Text.Trim();
        // Sections whose name holds what was typed; all of them while nothing has been.
        var places = Nav.OnBar.Append(Section.Downloads)
            .Where(s => s != Section.Search && (query.Length == 0 || Loc[Nav.Title(s)].Contains(query, StringComparison.CurrentCultureIgnoreCase)))
            .Select(s => (Row)new Row.Place(s));
        _rows = places.Concat(_posts.Take(7).Select(p => (Row)new Row.Title(p))).ToList();
        _chosen = Math.Min(_chosen, Math.Max(0, _rows.Count - 1));
        _list.Children.Clear();
        _divider.Visibility = _list.Visibility = _rows.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        for (var i = 0; i < _rows.Count; i++) _list.Children.Add(Line(_rows[i], i));
    }

    private FrameworkElement Line(Row row, int index)
    {
        var lit = index == _chosen;
        var face = new Grid { MinHeight = 46, Padding = new Thickness(12, 3, 12, 3), CornerRadius = new CornerRadius(12), Background = Theme.White(lit ? 0.14 : 0), ColumnSpacing = 14 };
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        switch (row)
        {
            case Row.Place place:
            {
                var icon = new Border { Width = 34, Height = 34, CornerRadius = new CornerRadius(9), Background = Theme.White(0.08) };
                var glyph = Glyph.Icon(Symbol(place.Section), 15, Theme.Text2);
                glyph.HorizontalAlignment = HorizontalAlignment.Center;
                icon.Child = glyph;
                face.Children.Add(icon);
                var name = Theme.Text(Loc[Nav.Title(place.Section)], 15, FontWeights.SemiBold);
                name.VerticalAlignment = VerticalAlignment.Center;
                Grid.SetColumn(name, 1);
                face.Children.Add(name);
                break;
            }
            case Row.Title title:
            {
                var post = title.Post;
                var poster = new Grid { Width = 34, Height = 51, CornerRadius = new CornerRadius(6), Background = Theme.Brush(Theme.Surface2) };
                poster.Children.Add(Images.Remote(post.Poster.Url(60), post.Poster.Blurhash, 60 * 1.5));
                face.Children.Add(poster);
                var text = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
                text.Children.Add(Theme.Text(post.Title(Loc.Lang), 15, FontWeights.SemiBold));
                text.Children.Add(Theme.Text(Detail(post), 12, color: Theme.Text3));
                Grid.SetColumn(text, 1);
                face.Children.Add(text);
                break;
            }
        }
        if (lit)
        {
            var enter = Glyph.Icon("", 12, Theme.Text3);
            enter.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(enter, 2);
            face.Children.Add(enter);
        }
        var button = Theme.Bare(face, () => Open(row));
        button.HorizontalAlignment = HorizontalAlignment.Stretch;
        button.PointerEntered += (_, _) =>
        {
            if (_chosen == index) return;
            _chosen = index;
            Render();
        };
        return button;
    }

    private static string Detail(Post post)
    {
        var parts = new List<string> { Loc.KindLine(post) };
        if (post.Year is { } year) parts.Add(Loc.Num(year));
        if (post.ImdbScore is { } score) parts.Add("IMDb " + Loc.Num(Fmt.Score(score)));
        return string.Join(" · ", parts);
    }

    private static string Symbol(Section section) => section switch
    {
        Section.Home => "",
        Section.Movies => "",
        Section.Series => "",
        Section.Anime => "",
        Section.Browse => "",
        Section.Profile => Glyph.Person,
        Section.Settings => Glyph.Settings,
        Section.Downloads => Glyph.Download,
        _ => Glyph.Search,
    };

    private void Open(Row row)
    {
        _close();
        switch (row)
        {
            case Row.Place place: _nav.Go(place.Section); break;
            case Row.Title title: _nav.Open(title.Post); break;
        }
    }
}
