using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>Home: a billboard of featured titles over their own artwork, with the shelves scrolling up over it.</summary>
public sealed class HomePage : Grid
{
    private static AppModel App => AppModel.Shared;

    private readonly Nav _nav;
    private readonly Stage _stage = new();
    private readonly ScrollViewer _scroller = new() { HorizontalScrollMode = ScrollMode.Disabled, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden };
    private readonly Grid _billboard = new();
    private readonly StackPanel _shelves = new() { Spacing = 20, Padding = new Thickness(0, 14, 0, 60) };
    private readonly StackPanel _dots = new() { Orientation = Orientation.Horizontal, Spacing = 6, Margin = new Thickness(0, 22, 0, 0) };
    private readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromSeconds(0.25) };
    private StackPanel? _billboardColumn;

    private HomeFeed? _feed;
    private List<Post> _continuing = [];
    /// <summary>
    /// The ten best-loved films and series. The home feed no longer names a top ten of its own, so
    /// these are the first ten of the catalogue's "most popular" order, asked for once per run.
    /// </summary>
    private static readonly Dictionary<string, List<Post>> Top = [];
    private int _hero;
    /// <summary>How much of the featured title's turn has run, 0...1. It holds while the billboard rests.</summary>
    private double _turn;
    /// <summary>The pointer is on the billboard: someone is reading it, so it should not move on.</summary>
    private bool _paused;

    /// <summary>How long each featured title is shown.</summary>
    private const double TurnLength = 12;

    public HomePage(Nav nav)
    {
        _nav = nav;
        Children.Add(_stage);
        var column = new StackPanel();
        column.Children.Add(_billboard);
        // The shelves rise out of the artwork: clear where they begin, solid a little further down.
        _shelves.Background = new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
            GradientStops =
            {
                new GradientStop { Color = Theme.Bg0.WithAlpha(0), Offset = 0 },
                new GradientStop { Color = Theme.Bg0.WithAlpha(0.9), Offset = 0.1 },
                new GradientStop { Color = Theme.Bg0, Offset = 0.24 },
            },
        };
        column.Children.Add(_shelves);
        _scroller.Content = column;
        Children.Add(_scroller);
        SizeChanged += (_, e) => _billboard.Height = Math.Max(500, e.NewSize.Height * 0.72);
        _timer.Tick += (_, _) => Tick();
        Loaded += (_, _) => _timer.Start();
        Unloaded += (_, _) => _timer.Stop();
        App.Changed += OnChanged;
        Unloaded += (_, _) => App.Changed -= OnChanged;
        _ = LoadAsync();
    }

    private void OnChanged(string what)
    {
        if (what == "content") _ = LoadAsync();
        if (what is "watch" or "continue") _ = RefreshContinueAsync();
    }

    private Post? Featured => _feed is { Heroes.Count: > 0 } f ? f.Heroes[Math.Min(_hero, f.Heroes.Count - 1)] : null;

    // MARK: Billboard

    private void ShowBillboard()
    {
        var loc = App.Loc;
        _billboard.Children.Clear();
        if (Featured is not { } post) return;
        var tint = post.Tint();
        _stage.Show(post, tint);

        var column = new StackPanel { Spacing = 14, MaxWidth = 760, HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(Theme.Edge, 0, Theme.Edge, 30) };
        var kind = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 9 };
        kind.Children.Add(new Border { Width = 4, Height = 16, CornerRadius = new CornerRadius(2), Background = Theme.Brush(tint), VerticalAlignment = VerticalAlignment.Center });
        kind.Children.Add(Theme.Text($"{loc[S.featured]} · {loc.KindLine(post)}", 14, FontWeights.SemiBold, Theme.Text2));
        column.Children.Add(kind);
        column.Children.Add(TitleArt(post, 56));
        var meta = Kit.MetaRow(post);
        meta.Margin = new Thickness(0, 2, 0, 0);
        column.Children.Add(meta);
        var plot = Theme.Text(post.Plot(loc.Lang), 16, color: Theme.Text2, lines: 3);
        plot.LineHeight = 26;
        plot.MaxWidth = 580;
        plot.HorizontalAlignment = HorizontalAlignment.Left;
        column.Children.Add(plot);
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12, Margin = new Thickness(0, 8, 0, 0) };
        buttons.Children.Add(Kit.Primary(loc[post.Resume is not null ? S.resume : S.play], Glyph.Play, () => _nav.Open(post, playing: true), tint));
        buttons.Children.Add(Kit.Glass(loc[S.details], Glyph.Info, () => _nav.Open(post), rim: tint));
        column.Children.Add(buttons);
        // The pager outlives each billboard: it is taken from the one before first.
        _billboardColumn?.Children.Remove(_dots);
        _billboardColumn = column;
        column.Children.Add(_dots);
        column.PointerEntered += (_, _) => _paused = true;
        column.PointerExited += (_, _) => _paused = false;
        column.Opacity = 0;
        column.Translation = new System.Numerics.Vector3(0, 10, 0);
        column.Springy(0.45);
        _billboard.Children.Add(column);
        column.Loaded += (_, _) => { column.Opacity = 1; column.Translation = default; };
        ShowDots();
    }

    /// <summary>A title's name in large type, with its name in the other language under it.</summary>
    public static StackPanel TitleArt(Post post, double size)
    {
        var loc = App.Loc;
        var column = new StackPanel { Spacing = 14 };
        var name = Theme.Text(post.Title(loc.Lang), size, FontWeights.ExtraBold, lines: 2);
        name.LineHeight = size * 1.15;
        name.Foreground = new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
            GradientStops = { new GradientStop { Color = Colors.White, Offset = 0 }, new GradientStop { Color = Theme.Text1.WithAlpha(0.84), Offset = 1 } },
        };
        column.Children.Add(name);
        if (post.AlternateTitle(loc.Lang) is { } other) column.Children.Add(Theme.Text(other, 18, FontWeights.Medium, Theme.Text2));
        return column;
    }

    /// <summary>Story-style pager: the current segment fills as its turn runs out.</summary>
    private void ShowDots()
    {
        _dots.Children.Clear();
        if (_feed is null || _feed.Heroes.Count < 2) return;
        var tint = Featured?.Tint() ?? Colors.White;
        for (var i = 0; i < _feed.Heroes.Count; i++)
        {
            var index = i;
            var current = i == _hero;
            var track = new Grid { Width = current ? 44 : 12, Height = 4, CornerRadius = new CornerRadius(2), Background = Theme.White(0.28), Margin = new Thickness(0, 8, 0, 8) };
            if (current) track.Children.Add(new Border { Background = Theme.Brush(tint), HorizontalAlignment = HorizontalAlignment.Left, Width = App.Settings.ReduceMotion ? 44 : Math.Max(4, 44 * _turn), CornerRadius = new CornerRadius(2) });
            var dot = Theme.Bare(track, () =>
            {
                _hero = index;
                _turn = 0;
                ShowBillboard();
            });
            Kit.AutomationName(dot, $"{App.Loc[S.featured]} {App.Loc.Num(index + 1)}");
            _dots.Children.Add(dot);
        }
    }

    /// <summary>
    /// The billboard has no audience: something covers it, the window is not the one in use, it
    /// has scrolled away, or the user is reading it. It costs nothing while it rests.
    /// </summary>
    private bool Resting => App.Settings.ReduceMotion || _paused || _nav.Opened.Count > 0 || App.Playback is not null
        || _scroller.VerticalOffset > _billboard.ActualHeight * 0.6 || !MainWindow.IsActive;

    private void Tick()
    {
        if (Resting || _feed is not { Heroes.Count: > 1 } feed) return;
        _turn = Math.Min(1, _turn + _timer.Interval.TotalSeconds / TurnLength);
        if (_turn < 1)
        {
            if (_dots.Children.Count > _hero && ((Button)_dots.Children[_hero]).Content is Grid { Children.Count: > 0 } track)
                ((Border)track.Children[0]).Width = Math.Max(4, 44 * _turn);
            return;
        }
        _hero = (_hero + 1) % feed.Heroes.Count;
        _turn = 0;
        ShowBillboard();
    }

    // MARK: Shelves

    private void ShowShelves()
    {
        var loc = App.Loc;
        _shelves.Children.Clear();
        if (_feed is not { } feed) return;
        if (_continuing.Count > 0)
            _shelves.Children.Add(Kit.Shelf(loc[S.continueWatching], _continuing.Select(p => Kit.Continue(p, () => _nav.Open(p), () =>
            {
                App.DismissContinue(p);
                _continuing.RemoveAll(x => x.Id == p.Id);
                ShowShelves();
            })), step: 3));
        foreach (var section in feed.Sections)
        {
            var posts = Distinct(section.Posts);
            IEnumerable<FrameworkElement> cards = section.Key == "top_10"
                ? posts.Select((p, i) => Kit.Ranked(i + 1, p, () => _nav.Open(p)))
                : posts.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p)));
            Section? target = section.Key switch { "movies" => Section.Movies, "series" => Section.Series, "anime" => Section.Anime, _ => null };
            _shelves.Children.Add(Kit.Shelf(loc.TitleForSection(section.Key), cards, target is { } t ? () => _nav.Go(t) : null));
            // The two top tens sit under the first shelf, before the long lists begin.
            if (section == feed.Sections[0] && feed.Posts("top_10").Count == 0)
            {
                Ranked(loc[S.topFilms], Top.GetValueOrDefault("movies"));
                Ranked(loc[S.topSeries], Top.GetValueOrDefault("series"));
            }
        }
    }

    private void Ranked(string title, List<Post>? posts)
    {
        if (posts is not { Count: > 0 }) return;
        _shelves.Children.Add(Kit.Shelf(title, posts.Select((p, i) => Kit.Ranked(i + 1, p, () => _nav.Open(p))), step: 4));
    }

    /// <summary>
    /// Each title once. A list drawn from several of the home feed's sections can name a title
    /// twice.
    /// </summary>
    private static List<Post> Distinct(IEnumerable<Post> posts)
    {
        var seen = new HashSet<int>();
        return posts.Where(p => seen.Add(p.Id)).ToList();
    }

    // MARK: Data

    private async Task LoadAsync()
    {
        // Last time's page at once, so there is something to see from the first frame; the fresh
        // one replaces it when it comes.
        if (_feed is null && App.CachedHome() is { } saved) Show(saved);
        try
        {
            var fresh = await App.HomeAsync();
            _hero = Math.Min(_hero, Math.Max(0, fresh.Heroes.Count - 1));
            _continuing = await App.ContinueWatchingAsync();
            Show(fresh);
        }
        catch (Exception)
        {
            if (_feed is null)
            {
                Children.Clear();
                Children.Add(Kit.Ambient());
                Children.Add(Kit.Error(() =>
                {
                    Children.Clear();
                    Children.Add(_stage);
                    Children.Add(_scroller);
                    _ = LoadAsync();
                }));
            }
            return;
        }
        await LoadTopAsync();
    }

    private void Show(HomeFeed feed)
    {
        var first = _feed is null;
        _feed = feed;
        if (first || Featured is not { } f || !feed.Heroes.Contains(f)) ShowBillboard();
        ShowShelves();
    }

    private async Task RefreshContinueAsync()
    {
        if (_feed is null) return;
        _continuing = await App.ContinueWatchingAsync();
        ShowShelves();
    }

    private async Task LoadTopAsync()
    {
        if (Top.Count > 0) return;
        foreach (var slug in new[] { "movies", "series" })
        {
            try
            {
                var result = await App.Api.GetAsync("archive", new Dictionary<string, string>
                {
                    ["slugs"] = slug, ["streamonly"] = "1", ["page"] = "1", ["orderby"] = "favorite", ["order"] = "desc",
                }, background: true);
                Top[slug] = new PostPage(result).Posts.Take(10).ToList();
            }
            catch (Exception) { }
        }
        ShowShelves();
    }
}
