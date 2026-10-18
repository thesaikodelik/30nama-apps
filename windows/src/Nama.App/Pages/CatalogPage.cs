using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>Every title under one heading (movies, a genre), as a wall of posters that grows as it is scrolled.</summary>
public sealed class CatalogPage : Grid
{
    private static AppModel App => AppModel.Shared;

    private enum Sort { Newest, Popular, Updated }
    private static string Order(Sort s) => s switch { Sort.Popular => "favorite", Sort.Updated => "update", _ => "date" };
    private static S Label(Sort s) => s switch { Sort.Popular => S.popular, Sort.Updated => S.updated, _ => S.newest };

    private readonly string _slug;
    private readonly string _title;
    private readonly Nav _nav;
    private readonly ScrollViewer _scroller = new() { VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled };
    private readonly StackPanel _header = new() { Spacing = 18 };
    private readonly WrapRow _wall = new(20, unmirrored: true);
    private readonly Grid _footer = new() { MinHeight = 60 };
    private readonly List<Post> _posts = [];
    private int _page, _pages = 1;
    private int? _total;
    private Sort _sort = Sort.Newest;
    private Filter _filter = new();
    private FilterOptions? _options;
    private bool _loading, _failed;
    /// <summary>Bumped when the order or the filter changes, so an answer to the old one is dropped.</summary>
    private int _generation;

    /// <summary>
    /// The catalogues that can be narrowed: the search behind the filters has to be told which
    /// kind of title to stay with, and a genre's own page has no such kind.
    /// </summary>
    private string? Type => _slug switch { "movies" => "movie", "series" => "series", "anime" => "anime", _ => null };

    public CatalogPage(string slug, string title, Nav nav)
    {
        _slug = slug;
        _title = title;
        _nav = nav;
        Children.Add(Kit.Ambient());
        var column = new StackPanel { Spacing = 26, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };
        column.Children.Add(_header);
        column.Children.Add(_wall);
        column.Children.Add(_footer);
        _scroller.Content = column;
        Children.Add(_scroller);
        // Near the end of the wall is the cue to fetch the next page.
        _scroller.ViewChanged += (_, _) =>
        {
            if (_scroller.VerticalOffset > _scroller.ScrollableHeight - 900) _ = LoadNextAsync();
        };
        RenderHeader();
        _ = Restart();
        if (Type is not null) _ = LoadOptionsAsync();
    }

    private async Task LoadOptionsAsync()
    {
        try { _options = await App.FilterOptionsAsync(); }
        catch (Exception) { }
        RenderHeader();
    }

    private async Task Restart()
    {
        _generation++;
        _posts.Clear();
        _wall.Children.Clear();
        _page = 0;
        _pages = 1;
        _total = null;
        _loading = false;
        _scroller.ChangeView(null, 0, null, true);
        await LoadNextAsync();
    }

    private void RenderHeader()
    {
        var loc = App.Loc;
        _header.Children.Clear();
        var top = new Grid();
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var name = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14 };
        name.Children.Add(Theme.Text(_title, 34, FontWeights.Bold));
        if (_total is { } total)
        {
            var count = Theme.Text($"{Fmt.Count(total, loc.Lang)} {loc[S.results]}", 15, FontWeights.Medium, Theme.Text3);
            count.VerticalAlignment = VerticalAlignment.Bottom;
            count.Margin = new Thickness(0, 0, 0, 7);
            name.Children.Add(count);
        }
        top.Children.Add(name);
        var sorts = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        foreach (var option in Enum.GetValues<Sort>())
            sorts.Children.Add(Kit.Pill(loc[Label(option)], _sort == option, () =>
            {
                _sort = option;
                RenderHeader();
                _ = Restart();
            }));
        Grid.SetColumn(sorts, 1);
        top.Children.Add(sorts);
        _header.Children.Add(top);
        // The filters' place is kept until they come, so the wall does not jump down under them:
        // posters moved after they were drawn kept a light line along the wall's top and left edges.
        if (Type is not null) _header.Children.Add(_options is { } options ? Filters(options) : new Border { Height = 30 });
    }

    /// <summary>
    /// Genre, country, lowest score and decade. With any of them set, the wall is filled by
    /// 30nama's advanced search instead of the plain archive.
    /// </summary>
    private FrameworkElement Filters(FilterOptions options)
    {
        var loc = App.Loc;
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        row.Children.Add(FilterMenu(loc[S.genres], options.Genres, _filter.Genre, v => _filter = _filter with { Genre = v }));
        row.Children.Add(FilterMenu(loc[S.country], options.Countries, _filter.Country, v => _filter = _filter with { Country = v }));
        row.Children.Add(FilterMenu(loc[S.minScore], options.Scores, _filter.Score, v => _filter = _filter with { Score = v }));
        var decades = options.Decades.Select(d => new FilterOptions.Choice(loc.Num($"{d}–{d + 9}"), d.ToString())).ToList();
        row.Children.Add(FilterMenu(loc[S.decade], decades, _filter.Decade?.ToString(), v => _filter = _filter with { Decade = v is null ? null : int.Parse(v) }));
        if (!_filter.IsEmpty)
        {
            var reset = Theme.Bare(Glyph.Icon("", 16, Theme.Text3), () =>
            {
                _filter = new Filter();
                RenderHeader();
                _ = Restart();
            });
            reset.VerticalAlignment = VerticalAlignment.Center;
            ToolTipService.SetToolTip(reset, loc[S.reset]);
            Kit.AutomationName(reset, loc[S.reset]);
            row.Children.Add(reset);
        }
        return row;
    }

    /// <summary>One filter: a pill that names the choice made, and opens the list of choices.</summary>
    private Button FilterMenu(string title, IReadOnlyList<FilterOptions.Choice> choices, string? selection, Action<string?> set)
    {
        var loc = App.Loc;
        var chosen = choices.FirstOrDefault(c => c.Value == selection);
        var ink = chosen is not null ? Theme.FocusInk : Theme.Text1;
        var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        label.Children.Add(Theme.Text(chosen is null ? title : $"{title}: {chosen.Name}", 13, FontWeights.SemiBold, ink));
        var chevron = Glyph.Icon("", 9, ink);
        chevron.Opacity = 0.6;
        label.Children.Add(chevron);
        var face = new Border { Height = 30, Padding = new Thickness(13, 0, 13, 0), CornerRadius = new CornerRadius(15), Background = chosen is not null ? Theme.Brush(Colors.White) : Theme.White(0.1), Child = label };
        var button = Theme.Bare(face, () => { });
        button.Click += (_, _) =>
        {
            var menu = new MenuFlyout();
            void Add(string text, string? value)
            {
                var item = new RadioMenuFlyoutItem { Text = text, GroupName = title, IsChecked = value == selection };
                item.Click += (_, _) =>
                {
                    set(value);
                    RenderHeader();
                    _ = Restart();
                };
                menu.Items.Add(item);
            }
            Add(loc[S.allChoices], null);
            foreach (var c in choices) Add(c.Name, c.Value);
            menu.ShowAt(button);
        };
        Kit.AutomationName(button, title);
        return button;
    }

    private async Task LoadNextAsync()
    {
        if (_loading || _page >= _pages) return;
        _loading = true;
        _failed = false;
        RenderFooter();
        var generation = _generation;
        var narrowed = _filter;
        try
        {
            var result = narrowed.IsEmpty
                ? await App.Api.GetAsync("archive", new Dictionary<string, string>
                {
                    ["slugs"] = _slug, ["streamonly"] = "1", ["page"] = (_page + 1).ToString(), ["orderby"] = Order(_sort), ["order"] = "desc",
                })
                : await App.Api.GetAsync("advanced_search", new Dictionary<string, string>
                {
                    ["parameters"] = narrowed.Parameters(Type), ["page"] = (_page + 1).ToString(), ["orderby"] = Order(_sort), ["order"] = "DESC",
                });
            if (generation != _generation) return;
            var next = new PostPage(result);
            var known = _posts.Select(p => p.Id).ToHashSet();
            foreach (var post in next.Posts.Where(p => !known.Contains(p.Id)))
            {
                _posts.Add(post);
                _wall.Add(Kit.Poster(post, () => _nav.Open(post)));
            }
            _page = next.Page;
            _pages = next.Pages;
            var hadTotal = _total is not null;
            _total = next.Total;
            if (!hadTotal) RenderHeader();
        }
        catch (Exception)
        {
            if (generation == _generation) _failed = true;
        }
        finally
        {
            if (generation == _generation) _loading = false;
            RenderFooter();
        }
        // A wall shorter than the window gives nothing to scroll: the next page is fetched at once.
        if (generation == _generation && !_failed && _scroller.ScrollableHeight < 200 && _page < _pages) await LoadNextAsync();
    }

    private void RenderFooter()
    {
        _footer.Children.Clear();
        if (_failed)
        {
            var retry = Kit.Glass(App.Loc[S.retry], null, () => _ = LoadNextAsync());
            retry.HorizontalAlignment = HorizontalAlignment.Center;
            _footer.Children.Add(retry);
        }
        else if (_loading) _footer.Children.Add(Kit.Spinner());
    }
}

/// <summary>Genres and categories as tiles; each opens its own wall of posters.</summary>
public sealed class BrowsePage : Grid
{
    private static AppModel App => AppModel.Shared;
    private readonly Nav _nav;
    private readonly StackPanel _column = new() { Spacing = 34, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };

    public BrowsePage(Nav nav)
    {
        _nav = nav;
        Children.Add(Kit.Ambient());
        Children.Add(new ScrollViewer { Content = _column, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        _ = LoadAsync();
    }

    private async Task LoadAsync()
    {
        _column.Children.Clear();
        _column.Children.Add(Kit.Spinner());
        Menu menu;
        try { menu = await App.MenuAsync(); }
        catch (Exception)
        {
            _column.Children.Clear();
            _column.Children.Add(Kit.Error(() => _ = LoadAsync()));
            return;
        }
        _column.Children.Clear();
        _column.Children.Add(AdvancedCard());
        if (menu.Genres.Count > 0) _column.Children.Add(Group(App.Loc[S.genres], menu.Genres));
        if (menu.Categories.Count > 0) _column.Children.Add(Group(App.Loc[S.categories], menu.Categories));
    }

    /// <summary>The way into the advanced search, above the genres it goes beyond.</summary>
    private Button AdvancedCard()
    {
        var loc = App.Loc;
        var face = new Grid { Height = 92, MaxWidth = 760, HorizontalAlignment = HorizontalAlignment.Left, Padding = new Thickness(22, 0, 22, 0), CornerRadius = new CornerRadius(18), ColumnSpacing = 18 }.Springy(0.2);
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var glass = new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(18), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim, Margin = new Thickness(-22, 0, -22, 0) };
        Grid.SetColumnSpan(glass, 3);
        face.Children.Add(glass);
        var icon = Glyph.Icon("", 24, Theme.Accent);
        icon.VerticalAlignment = VerticalAlignment.Center;
        face.Children.Add(icon);
        var text = new StackPanel { Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(Theme.Text(loc[Extra.advancedSearch], 19, FontWeights.Bold));
        text.Children.Add(Theme.Text(loc[Extra.advancedHint], 13, color: Theme.Text2, lines: 2));
        Grid.SetColumn(text, 1);
        face.Children.Add(text);
        var arrow = Glyph.Icon(Glyph.ChevronEnd, 14, Theme.Text3);
        arrow.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(arrow, 2);
        face.Children.Add(arrow);
        var button = Theme.Bare(face, () => _nav.Open(new Route.Advanced()));
        Kit.AutomationName(button, loc[Extra.advancedSearch]);
        return Kit.Hovering(button, face, 1.02);
    }

    private FrameworkElement Group(string title, IEnumerable<MenuEntry> entries)
    {
        var group = new StackPanel { Spacing = 16 };
        group.Children.Add(Theme.Text(title, 26, FontWeights.Bold));
        var tiles = new WrapRow(16);
        foreach (var entry in entries) tiles.Add(Tile(entry));
        group.Children.Add(tiles);
        return group;
    }

    private Button Tile(MenuEntry entry)
    {
        var loc = App.Loc;
        var face = new Grid { Width = 240, Height = 118, CornerRadius = new CornerRadius(14), Background = Theme.Brush(Theme.Surface2) }.Springy(0.2);
        face.Children.Add(Images.Remote(entry.Thumb ?? entry.Full, null, 360));
        face.Children.Add(new Border
        {
            Background = new Microsoft.UI.Xaml.Media.LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
                GradientStops =
                {
                    new Microsoft.UI.Xaml.Media.GradientStop { Color = Theme.Hex(0, 0.1), Offset = 0 },
                    new Microsoft.UI.Xaml.Media.GradientStop { Color = Theme.Hex(0, 0.8), Offset = 1 },
                },
            },
        });
        var text = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(12) };
        text.Children.Add(Theme.Text(entry.Name, 17, FontWeights.Bold, Colors.White));
        if (entry.Count is int count and > 0)
            text.Children.Add(Theme.Text($"{Fmt.Count(count, loc.Lang)} {loc[S.results]}", 12, FontWeights.Medium, Theme.Hex(0xFFFFFF, 0.7)));
        face.Children.Add(text);
        var rim = new Border { CornerRadius = new CornerRadius(14), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.08) };
        face.Children.Add(rim);
        var button = Theme.Bare(face, () => _nav.Open(new Route.Catalog(entry.Slug, entry.Name)));
        Kit.AutomationName(button, entry.Name);
        button.PointerEntered += (_, _) => { rim.BorderBrush = Theme.White(0.9); rim.BorderThickness = new Thickness(2); };
        button.PointerExited += (_, _) => { rim.BorderBrush = Theme.White(0.08); rim.BorderThickness = new Thickness(1); };
        return Kit.Hovering(button, face, 1.03);
    }
}

/// <summary>
/// Search as you type, with what is popular standing in while there is nothing to show. A switch
/// keeps the results to what can be watched online, as 30nama's own desktop app offers.
/// </summary>
public sealed class SearchPage : Grid
{
    private static AppModel App => AppModel.Shared;
    private readonly Nav _nav;
    private readonly TextBox _field = new();
    private readonly ProgressRing _busy = new() { Width = 18, Height = 18, IsActive = false, Foreground = Theme.Brush(Theme.Text2) };
    private readonly TextBlock _note = Theme.Text("", 16, FontWeights.Medium, Theme.Text2, lines: 2);
    private readonly TextBlock _heading = Theme.Text("", 22, FontWeights.Bold);
    private readonly WrapRow _results = new(20, unmirrored: true);
    private bool _streamOnly;
    private CancellationTokenSource? _typing;

    public SearchPage(Nav nav)
    {
        _nav = nav;
        Children.Add(Kit.Ambient());
        var column = new StackPanel { Spacing = 26, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };
        column.Children.Add(Field());
        column.Children.Add(_note);
        column.Children.Add(_heading);
        column.Children.Add(_results);
        Children.Add(new ScrollViewer { Content = column, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        _field.TextChanged += (_, _) => Schedule();
        Loaded += (_, _) => _field.Focus(FocusState.Programmatic);
        _ = ShowPopularAsync(null);
    }

    private FrameworkElement Field()
    {
        var loc = App.Loc;
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14, HorizontalAlignment = HorizontalAlignment.Center };
        var capsule = new Grid { Width = 640, Height = 54 };
        capsule.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(27), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        var inner = new Grid { Margin = new Thickness(20, 0, 20, 0), ColumnSpacing = 12 };
        inner.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(20) });
        inner.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var mark = new Grid { VerticalAlignment = VerticalAlignment.Center };
        mark.Children.Add(Glyph.Icon(Glyph.Search, 17, Theme.Text3));
        mark.Children.Add(_busy);
        inner.Children.Add(mark);
        _field.PlaceholderText = loc[S.searchPrompt];
        _field.FontSize = 19;
        _field.FontFamily = Theme.Font(FontWeights.Medium);
        _field.BorderThickness = new Thickness(0);
        _field.Background = Theme.White(0);
        _field.VerticalAlignment = VerticalAlignment.Center;
        _field.Resources["TextControlBackgroundFocused"] = Theme.White(0);
        _field.Resources["TextControlBackgroundPointerOver"] = Theme.White(0);
        _field.Resources["TextControlBorderBrushFocused"] = Theme.White(0);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(_field, loc[S.search]);
        Grid.SetColumn(_field, 1);
        inner.Children.Add(_field);
        capsule.Children.Add(inner);
        row.Children.Add(capsule);
        var only = new ToggleSwitch { IsOn = _streamOnly, OnContent = App.Loc[Extra.streamOnly], OffContent = App.Loc[Extra.streamOnly], VerticalAlignment = VerticalAlignment.Center };
        only.Toggled += (_, _) => { _streamOnly = only.IsOn; Schedule(now: true); };
        row.Children.Add(only);
        var filters = Kit.Glass(loc[Extra.advancedSearch], "", () => _nav.Open(new Route.Advanced()), height: 40);
        filters.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(filters);
        return row;
    }

    /// <summary>Waits for typing to pause; a newer keystroke starts the wait again.</summary>
    private void Schedule(bool now = false)
    {
        _typing?.Cancel();
        var typing = _typing = new CancellationTokenSource();
        _ = RunAsync(_field.Text.Trim(), now ? 0 : 450, typing.Token);
    }

    private async Task RunAsync(string text, int wait, CancellationToken cancel)
    {
        var loc = App.Loc;
        if (text.Length < 2)
        {
            await ShowPopularAsync(null);
            return;
        }
        try { await Task.Delay(wait, cancel); } catch (TaskCanceledException) { return; }
        _busy.IsActive = true;
        try
        {
            var page = new PostPage(await App.Api.GetAsync("search", new Dictionary<string, string>
            {
                ["query"] = text, ["streamonly"] = _streamOnly ? "1" : "0", ["page"] = "1",
            }));
            if (cancel.IsCancellationRequested) return;
            if (page.Posts.Count == 0) await ShowPopularAsync($"{loc[S.nothingFor]} «{text}»");
            else Show(loc[S.topResults], page.Posts, null);
        }
        catch (Exception)
        {
            if (!cancel.IsCancellationRequested) await ShowPopularAsync($"{loc[S.error]}. {loc[S.tryAgain]}");
        }
        finally
        {
            if (!cancel.IsCancellationRequested) _busy.IsActive = false;
        }
    }

    /// <summary>With nothing to show for the query, offer what is popular instead of an empty screen.</summary>
    private async Task ShowPopularAsync(string? note)
    {
        HomeFeed? feed = null;
        try { feed = await App.HomeAsync(); } catch (Exception) { }
        var suggested = feed?.Posts("suggested") ?? [];
        Show(App.Loc[S.popularNow], (suggested.Count == 0 ? feed?.Posts("movies") ?? [] : suggested).Take(24), note);
    }

    private void Show(string heading, IEnumerable<Post> posts, string? note)
    {
        _note.Text = note ?? "";
        _note.Visibility = note is null ? Visibility.Collapsed : Visibility.Visible;
        _heading.Text = heading;
        _results.Children.Clear();
        foreach (var post in posts) _results.Add(Kit.Poster(post, () => _nav.Open(post)));
    }
}
