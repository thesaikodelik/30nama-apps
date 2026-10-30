using System.Globalization;
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
/// The week of series and anime: which have a new episode on which day, from today on. From the
/// 30nama website's own API, as its schedule page has it. As ScheduleView on the Mac.
/// </summary>
public sealed class SchedulePage : Grid
{
    private static AppModel App => AppModel.Shared;

    private readonly Nav _nav;
    private readonly StackPanel _column = new() { Spacing = 22, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };
    private readonly Dictionary<Schedule.Kinds, Schedule> _weeks = [];
    private Schedule.Kinds _kind = Schedule.Kinds.Series;
    private int _day = Schedule.Day(DateTime.Now);
    private bool _failed;

    public SchedulePage(Nav nav)
    {
        _nav = nav;
        Children.Add(Kit.Ambient());
        Children.Add(new ScrollViewer { Content = _column, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        Render();
        _ = LoadAsync();
    }

    private void Render()
    {
        var loc = App.Loc;
        _column.Children.Clear();
        var top = new Grid();
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        top.Children.Add(Theme.Text(loc[S.weeklySchedule], 34, FontWeights.Bold));
        var kinds = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        foreach (var (kind, label) in new[] { (Schedule.Kinds.Series, S.series), (Schedule.Kinds.Anime, S.anime) })
            kinds.Children.Add(Kit.Pill(loc[label], _kind == kind, () =>
            {
                _kind = kind;
                Render();
                _ = LoadAsync();
            }));
        Grid.SetColumn(kinds, 1);
        top.Children.Add(kinds);
        _column.Children.Add(top);
        _column.Children.Add(Days());

        if (_weeks.TryGetValue(_kind, out var week))
        {
            var posts = week.On(_day);
            if (posts.Count == 0)
            {
                var none = Theme.Text(loc[S.noMatches], 16, FontWeights.Medium, Theme.Text2);
                none.HorizontalAlignment = HorizontalAlignment.Center;
                none.Margin = new Thickness(0, 30, 0, 30);
                _column.Children.Add(none);
            }
            else
            {
                var wall = new WrapRow(20, unmirrored: true);
                foreach (var post in posts) wall.Add(Kit.Poster(post, () => _nav.Open(post)));
                _column.Children.Add(wall);
            }
        }
        else if (_failed)
        {
            _column.Children.Add(Kit.SiteRetry(() => _ = LoadAsync()));
        }
        else _column.Children.Add(Kit.Spinner());
    }

    /// <summary>Today first, then the days after it, named in the interface's language.</summary>
    private FrameworkElement Days()
    {
        var loc = App.Loc;
        var names = WeekdayNames(loc.Lang);
        var today = Schedule.Day(DateTime.Now);
        var row = new WrapRow(8);
        foreach (var d in Schedule.Week(DateTime.Now))
            row.Add(Kit.Pill(d == today ? loc[S.today] : names[(int)Schedule.Weekday(d)], _day == d, () =>
            {
                _day = d;
                Render();
            }));
        return row;
    }

    private static string[] WeekdayNames(Lang lang) =>
        CultureInfo.GetCultureInfo(lang switch { Lang.Fa => "fa-IR", Lang.Ar => "ar", _ => "en-US" }).DateTimeFormat.DayNames;

    private async Task LoadAsync()
    {
        if (_weeks.ContainsKey(_kind)) return;
        _failed = false;
        var asked = _kind;
        try { _weeks[asked] = new Schedule(await App.Api.SiteAsync(SiteQuery.Schedule(asked))); }
        catch (Exception) { if (asked == _kind) _failed = true; }
        if (asked == _kind) Render();
    }
}

/// <summary>The site's lists of the best, each whole and in order, with the place on every poster. As TopListsView on the Mac.</summary>
public sealed class TopListsPage : Grid
{
    private static AppModel App => AppModel.Shared;

    private readonly Nav _nav;
    private readonly StackPanel _column = new() { Spacing = 22, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };
    private readonly Dictionary<TopLists, IReadOnlyList<TopListEntry>> _lists = [];
    private TopLists _list = TopLists.ImdbMovies;
    private bool _failed;

    public TopListsPage(Nav nav)
    {
        _nav = nav;
        Children.Add(Kit.Ambient());
        Children.Add(new ScrollViewer { Content = _column, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        Render();
        _ = LoadAsync();
    }

    private static S Title(TopLists list) => list switch
    {
        TopLists.ImdbMovies => S.imdbTopMovies,
        TopLists.ImdbSeries => S.imdbTopSeries,
        TopLists.SiteMovies => S.siteTopMovies,
        _ => S.siteTopSeries,
    };

    private void Render()
    {
        var loc = App.Loc;
        _column.Children.Clear();
        _column.Children.Add(Theme.Text(loc[S.topLists], 34, FontWeights.Bold));
        var choices = new WrapRow(8);
        foreach (var option in Enum.GetValues<TopLists>())
            choices.Add(Kit.Pill(loc[Title(option)], _list == option, () =>
            {
                _list = option;
                Render();
                _ = LoadAsync();
            }));
        _column.Children.Add(choices);

        if (_lists.TryGetValue(_list, out var entries))
        {
            var wall = new WrapRow(20, unmirrored: true);
            foreach (var entry in entries)
            {
                var card = new Grid();
                card.Children.Add(Kit.Poster(entry.Post, () => _nav.Open(entry.Post)));
                card.Children.Add(Place(entry.Rank));
                wall.Add(card);
            }
            _column.Children.Add(wall);
        }
        else if (_failed)
        {
            _column.Children.Add(Kit.SiteRetry(() => _ = LoadAsync()));
        }
        else _column.Children.Add(Kit.Spinner());
    }

    /// <summary>The place, in a dark capsule at the poster's top leading corner.</summary>
    private static FrameworkElement Place(int rank) => new Border
    {
        MinWidth = 30,
        Height = 30,
        Padding = new Thickness(4, 0, 4, 0),
        Margin = new Thickness(8),
        CornerRadius = new CornerRadius(15),
        Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(Theme.Hex(0, 0.7)),
        BorderBrush = Theme.White(0.25),
        BorderThickness = new Thickness(1),
        // At the leading corner: the wall itself is laid out unmirrored.
        HorizontalAlignment = App.Loc.Lang.IsRtl() ? HorizontalAlignment.Right : HorizontalAlignment.Left,
        VerticalAlignment = VerticalAlignment.Top,
        IsHitTestVisible = false,
        Child = new TextBlock
        {
            Text = App.Loc.Num(rank.ToString()),
            FontSize = 14,
            FontWeight = FontWeights.Black,
            FontFamily = Theme.Font(FontWeights.Bold),
            Foreground = Theme.Brush(Colors.White),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        },
    };

    private async Task LoadAsync()
    {
        if (_lists.ContainsKey(_list)) return;
        _failed = false;
        var asked = _list;
        try { _lists[asked] = TopListEntry.Parse(await App.Api.SiteAsync(SiteQuery.TopList(asked))); }
        catch (Exception) { if (asked == _list) _failed = true; }
        if (asked == _list) Render();
    }
}
