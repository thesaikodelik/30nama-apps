using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// 30nama's whole catalogue narrowed by more than the catalogues' own filters: several genres at
/// once, a network, a language, the age rating, a span of years, a director, x265 or 3D copies.
/// From 30nama's own desktop app, laid out as the app's own catalogue pages are.
/// </summary>
public sealed class AdvancedSearchPage : Grid
{
    private static AppModel App => AppModel.Shared;
    private static Loc Loc => App.Loc;

    private readonly Nav _nav;
    private readonly ScrollViewer _scroller = new() { VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled };
    private readonly StackPanel _header = new() { Spacing = 18 };
    private readonly WrapRow _wall = new(20);
    private readonly Grid _footer = new() { MinHeight = 60 };
    private readonly List<Post> _posts = [];
    private AdvancedQuery _query = new();
    private SearchChoices? _choices;
    private int _page, _pages = 1;
    private int? _total;
    private bool _loading, _failed;
    /// <summary>Bumped when the query changes, so an answer to the old one is dropped.</summary>
    private int _generation;

    public AdvancedSearchPage(Nav nav)
    {
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
        _ = LoadChoicesAsync();
        _ = Restart();
    }

    private async Task LoadChoicesAsync()
    {
        try { _choices = await App.SearchChoicesAsync(); }
        catch (Exception) { }
        RenderHeader();
    }

    private void Change(Func<AdvancedQuery, AdvancedQuery> edit)
    {
        _query = edit(_query);
        RenderHeader();
        _ = Restart();
    }

    // MARK: Header

    private void RenderHeader()
    {
        _header.Children.Clear();
        var top = new Grid();
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var name = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14 };
        name.Children.Add(Theme.Text(Loc[Extra.advancedSearch], 34, FontWeights.Bold));
        if (_total is { } total)
        {
            var count = Theme.Text($"{Fmt.Count(total, Loc.Lang)} {Loc[S.results]}", 15, FontWeights.Medium, Theme.Text3);
            count.VerticalAlignment = VerticalAlignment.Bottom;
            count.Margin = new Thickness(0, 0, 0, 7);
            name.Children.Add(count);
        }
        top.Children.Add(name);
        var sorts = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        foreach (var sort in AdvancedQuery.Sorts)
            sorts.Children.Add(Kit.Pill(SortName(sort), _query.Sort == sort, () => Change(q => q with { Sort = sort })));
        Grid.SetColumn(sorts, 1);
        top.Children.Add(sorts);
        _header.Children.Add(top);
        var hint = Theme.Text(Loc[Extra.advancedHint], 14, color: Theme.Text3);
        hint.Margin = new Thickness(0, -10, 0, 0);
        _header.Children.Add(hint);
        if (_choices is { } choices) _header.Children.Add(Filters(choices));
    }

    private static string SortName(string sort) => sort switch
    {
        "update" => Loc[S.updated],
        "year" => Loc[Extra.byYear],
        "imdb-rate" => "IMDb",
        "favorite" => Loc[S.popular],
        _ => Loc[Extra.relevance],
    };

    private FrameworkElement Filters(SearchChoices choices)
    {
        var rows = new StackPanel { Spacing = 12 };
        var menus = new WrapRow(8);
        menus.Add(Menu(Loc[Extra.kind], choices.Types, _query.Type, v => q => q with { Type = v }));
        menus.Add(GenreMenu(choices.Genres));
        menus.Add(Menu(Loc[S.country], choices.Countries, _query.Country, v => q => q with { Country = v }));
        menus.Add(Menu(Loc[S.language], choices.Languages, _query.Language, v => q => q with { Language = v }));
        menus.Add(Menu(Loc[S.network], choices.Networks, _query.Network, v => q => q with { Network = v }));
        menus.Add(Menu(Loc[S.minScore], choices.Scores, _query.Score, v => q => q with { Score = v }));
        menus.Add(Menu(Loc[Extra.ageRating], choices.Ages, _query.Age, v => q => q with { Age = v }));
        rows.Children.Add(menus);

        var more = new WrapRow(14);
        more.Add(Years());
        more.Add(Director());
        more.Add(Switch("x265", _query.X265, on => q => q with { X265 = on }));
        more.Add(Switch("3D", _query.ThreeD, on => q => q with { ThreeD = on }));
        more.Add(Switch(Loc[Extra.streamOnly], _query.StreamOnly, on => q => q with { StreamOnly = on }));
        if (_query.Count > 0)
        {
            var reset = Kit.Glass(Loc[S.reset], null, () => Change(q => new AdvancedQuery { Sort = q.Sort, StreamOnly = q.StreamOnly }), height: 34);
            reset.VerticalAlignment = VerticalAlignment.Center;
            more.Add(reset);
        }
        rows.Children.Add(more);
        return rows;
    }

    /// <summary>A pill that names the choice made and opens the list of choices; long lists scroll.</summary>
    private Button Menu(string title, IReadOnlyList<SearchChoices.Choice> choices, string? selection, Func<string?, Func<AdvancedQuery, AdvancedQuery>> set)
    {
        var chosen = choices.FirstOrDefault(c => c.Value == selection);
        var button = Capsule(chosen is null ? title : $"{title}: {chosen.Name}", chosen is not null);
        button.Click += (_, _) =>
        {
            var menu = new MenuFlyout();
            void Add(string text, string? value)
            {
                var item = new RadioMenuFlyoutItem { Text = text, GroupName = title, IsChecked = value == selection };
                item.Click += (_, _) => Change(set(value));
                menu.Items.Add(item);
            }
            Add(Loc[S.allChoices], null);
            foreach (var c in choices) Add(c.Name, c.Value);
            menu.ShowAt(button);
        };
        Kit.AutomationName(button, title);
        return button;
    }

    /// <summary>Genres are chosen several at once: a title has to be all of them.</summary>
    private Button GenreMenu(IReadOnlyList<SearchChoices.Choice> genres)
    {
        var picked = genres.Where(g => _query.Genres.Contains(g.Value)).Select(g => g.Name).ToList();
        var title = Loc[S.genres];
        var button = Capsule(picked.Count == 0 ? title : $"{title}: {string.Join(Loc.Lang == Lang.En ? ", " : "، ", picked)}", picked.Count > 0);
        button.Click += (_, _) =>
        {
            var wall = new WrapRow(8) { MaxWidth = 560 };
            var flyout = new Flyout { Content = new ScrollViewer { Content = wall, MaxHeight = 420, Padding = new Thickness(4) } };
            void Fill()
            {
                wall.Children.Clear();
                foreach (var genre in genres)
                {
                    var on = _query.Genres.Contains(genre.Value);
                    wall.Add(Kit.Pill(genre.Name, on, () =>
                    {
                        _query = _query with { Genres = on ? _query.Genres.Where(g => g != genre.Value).ToList() : [.. _query.Genres, genre.Value] };
                        Fill();
                    }));
                }
            }
            Fill();
            // The search runs once the choosing is done, not on every pill.
            var before = _query.Genres.ToList();
            flyout.Closed += (_, _) =>
            {
                if (!before.SequenceEqual(_query.Genres)) Change(q => q);
            };
            flyout.ShowAt(button);
        };
        Kit.AutomationName(button, title);
        return button;
    }

    private static Button Capsule(string text, bool chosen)
    {
        var ink = chosen ? Theme.FocusInk : Theme.Text1;
        var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        label.Children.Add(Theme.Text(text, 13, FontWeights.SemiBold, ink));
        var chevron = Glyph.Icon("", 9, ink);
        chevron.Opacity = 0.6;
        label.Children.Add(chevron);
        var face = new Border { Height = 30, MaxWidth = 360, Padding = new Thickness(13, 0, 13, 0), CornerRadius = new CornerRadius(15), Background = chosen ? Theme.Brush(Colors.White) : Theme.White(0.1), Child = label };
        return Theme.Bare(face, () => { });
    }

    /// <summary>From one year to another; either end may be left open.</summary>
    private FrameworkElement Years()
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        var label = Theme.Text(Loc[Extra.years], 13, FontWeights.SemiBold, Theme.Text2);
        label.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(label);
        row.Children.Add(YearBox(_query.FromYear, "1950", y => q => q with { FromYear = y }));
        var to = Theme.Text(Loc[Extra.hoursTo], 13, color: Theme.Text3);
        to.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(to);
        row.Children.Add(YearBox(_query.ToYear, DateTime.Now.Year.ToString(), y => q => q with { ToYear = y }));
        return row;
    }

    private TextBox YearBox(int? value, string placeholder, Func<int?, Func<AdvancedQuery, AdvancedQuery>> set)
    {
        var box = Field(value?.ToString() ?? "", placeholder, 74);
        box.MaxLength = 4;
        box.FlowDirection = FlowDirection.LeftToRight;
        void Apply()
        {
            // Persian or Arabic digits count as well as Latin ones.
            var digits = new string(box.Text.Select(c => char.IsDigit(c) ? (char)('0' + (int)char.GetNumericValue(c)) : c).Where(char.IsAsciiDigit).ToArray());
            int? year = int.TryParse(digits, out var y) && y is >= 1880 and <= 2100 ? y : null;
            if (year != value) Change(set(year));
        }
        box.LostFocus += (_, _) => Apply();
        box.KeyDown += (_, e) => { if (e.Key == Windows.System.VirtualKey.Enter) Apply(); };
        return box;
    }

    private TextBox Director()
    {
        var box = Field(_query.Director ?? "", Loc[Extra.directorName], 200);
        void Apply()
        {
            var name = string.IsNullOrWhiteSpace(box.Text) ? null : box.Text.Trim();
            if (name != _query.Director) Change(q => q with { Director = name });
        }
        box.LostFocus += (_, _) => Apply();
        box.KeyDown += (_, e) => { if (e.Key == Windows.System.VirtualKey.Enter) Apply(); };
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(box, Loc[S.director]);
        return box;
    }

    private static TextBox Field(string text, string placeholder, double width) => new()
    {
        Text = text, PlaceholderText = placeholder, Width = width, Height = 34, MinHeight = 34, CornerRadius = new CornerRadius(17),
        Padding = new Thickness(12, 6, 12, 6), FontFamily = Theme.Font(FontWeights.SemiBold), FontSize = 13, VerticalAlignment = VerticalAlignment.Center,
    };

    private FrameworkElement Switch(string title, bool on, Func<bool, Func<AdvancedQuery, AdvancedQuery>> set)
    {
        var toggle = new ToggleSwitch { IsOn = on, OnContent = title, OffContent = title, VerticalAlignment = VerticalAlignment.Center, MinWidth = 0 };
        toggle.Toggled += (_, _) => Change(set(toggle.IsOn));
        return toggle;
    }

    // MARK: Results

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

    private async Task LoadNextAsync()
    {
        if (_loading || _page >= _pages) return;
        _loading = true;
        _failed = false;
        RenderFooter();
        var generation = _generation;
        try
        {
            var next = new PostPage(await App.Api.GetAsync("advanced_search", _query.Request(_page + 1)));
            if (generation != _generation) return;
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
            var retry = Kit.Glass(Loc[S.retry], null, () => _ = LoadNextAsync());
            retry.HorizontalAlignment = HorizontalAlignment.Center;
            _footer.Children.Add(retry);
        }
        else if (_loading) _footer.Children.Add(Kit.Spinner());
        else if (_posts.Count == 0)
        {
            var none = Theme.Text(Loc[Extra.noMatches], 16, FontWeights.Medium, Theme.Text2);
            none.HorizontalAlignment = HorizontalAlignment.Center;
            _footer.Children.Add(none);
        }
    }
}
