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
    /// <summary>Null until it is known.</summary>
    private List<Post>? _continuing;
    private bool _trendDone, _topDone;
    /// <summary>What is trending on TMDB this week and is on 30nama; asked for once per run.</summary>
    private static List<Post> Trend = [];
    /// <summary>New and returning series, and the films now popular: with a TMDB key only.</summary>
    private static List<Post> Fresh = [], Pop = [];
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
        // The arrows stay through the turns; the rest is drawn again.
        foreach (var old in _billboard.Children.Where(c => c is not Button { Tag: int }).ToList()) _billboard.Children.Remove(old);
        if (Featured is not { } post) return;
        var tint = post.Tint();
        _stage.Show(post, tint);

        var column = new StackPanel { Spacing = 14, MaxWidth = 760, HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(Theme.Edge, 0, Theme.Edge, 30) };
        var kind = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 9 };
        kind.Children.Add(new Border { Width = 4, Height = 16, CornerRadius = new CornerRadius(2), Background = Theme.Brush(tint), VerticalAlignment = VerticalAlignment.Center });
        kind.Children.Add(Theme.Text($"{loc[S.featured]} · {loc.KindLine(post)}", 14, FontWeights.SemiBold, Theme.Text2));
        column.Children.Add(kind);
        column.Children.Add(new TitleArt(post, 56));
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
        // Behind the opening title it is there at once: fading, the title's blur missed it.
        if (!SplashCurtain.IsUp)
        {
            column.Opacity = 0;
            column.Translation = new System.Numerics.Vector3(0, 10, 0);
            column.Springy(0.45);
        }
        _billboard.Children.Add(column);
        column.Loaded += (_, _) => { column.Opacity = 1; column.Translation = default; };
        ShowDots();
        ShowHeroArrows();
    }

    /// <summary>
    /// The previous and next featured title, at the billboard's sides while the pointer is over it,
    /// as the rows' arrows (a user on issue #2: the dots alone were hard to find). Round from the
    /// last to the first; the turn starts again.
    /// </summary>
    private void ShowHeroArrows()
    {
        if (!_heroHover)
        {
            _heroHover = true;
            // Seen over its empty parts too, not only over the words and buttons.
            _billboard.Background = Theme.White(0);
            _billboard.PointerEntered += (_, _) => { _overBillboard = true; ShowHeroArrows(); };
            // Already over it when the page came: the first move shows them.
            _billboard.PointerMoved += (_, _) => { if (!_overBillboard) { _overBillboard = true; ShowHeroArrows(); } };
            _billboard.PointerExited += (_, _) => { _overBillboard = false; ShowHeroArrows(); };
        }
        if (_feed is not { Heroes.Count: > 1 } feed) return;
        foreach (var direction in new[] { -1, 1 })
        {
            if (_billboard.Children.OfType<Button>().FirstOrDefault(b => (int?)b.Tag == direction) is not { } arrow)
            {
                // Pointing out, the way the titles go: in Persian and Arabic "next" is on the left and points left.
                arrow = Kit.Glass(null, (direction < 0) != App.Loc.Lang.IsRtl() ? Glyph.ChevronStart : Glyph.ChevronEnd, () =>
                {
                    if (_feed is not { Heroes.Count: > 1 } heroes) return;
                    _hero = (_hero + direction + heroes.Heroes.Count) % heroes.Heroes.Count;
                    _turn = 0;
                    ShowBillboard();
                }, height: 38);
                arrow.Tag = direction;
                arrow.HorizontalAlignment = direction < 0 ? HorizontalAlignment.Left : HorizontalAlignment.Right;
                arrow.VerticalAlignment = VerticalAlignment.Center;
                arrow.Margin = new Thickness(16, 0, 16, 0);
                arrow.Opacity = 0;
                Kit.AutomationName(arrow, App.Loc[direction < 0 ? S.rowPrevious : S.rowNext]);
                _billboard.Children.Add(arrow.Springy(0.15));
            }
            arrow.Opacity = _overBillboard ? 1 : 0;
            arrow.IsHitTestVisible = _overBillboard;
        }
    }

    private bool _heroHover, _overBillboard;

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
            if (current)
            {
                _fill = new Border { Background = Theme.Brush(tint), HorizontalAlignment = HorizontalAlignment.Left, Width = 44, CornerRadius = new CornerRadius(2) };
                track.Children.Add(_fill);
                _filling = false;
                ShowFill();
            }
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
    private bool Resting => App.Settings.Calm || _paused || _nav.Opened.Count > 0 || App.Playback is not null
        || _scroller.VerticalOffset > _billboard.ActualHeight * 0.6 || !MainWindow.IsActive;

    /// <summary>The current segment's fill, and whether it is running smoothly toward its end.</summary>
    private Border? _fill;
    private bool _filling;

    /// <summary>
    /// The fill runs on the compositor, at an even pace from where the turn is to its end; the
    /// timer only keeps count. Resting, it stops where it is.
    /// </summary>
    private void ShowFill()
    {
        if (_fill is null) return;
        var visual = Microsoft.UI.Xaml.Hosting.ElementCompositionPreview.GetElementVisual(_fill);
        var at = App.Settings.Calm ? 1 : (float)Math.Max(4.0 / 44, _turn);
        if (Resting || App.Settings.Calm)
        {
            visual.StopAnimation("Scale.X");
            visual.Scale = new System.Numerics.Vector3(at, 1, 1);
            _filling = false;
            return;
        }
        if (_filling) return;
        _filling = true;
        visual.Scale = new System.Numerics.Vector3(at, 1, 1);
        var run = visual.Compositor.CreateScalarKeyFrameAnimation();
        run.InsertKeyFrame(0, at);
        run.InsertKeyFrame(1, 1, visual.Compositor.CreateLinearEasingFunction());
        run.Duration = TimeSpan.FromSeconds(Math.Max(0.1, (1 - _turn) * TurnLength));
        visual.StartAnimation("Scale.X", run);
    }

    private void Tick()
    {
        // Resting or not, the fill is told: it stops, or starts again from where it stopped.
        if (_filling == Resting) ShowFill();
        if (Resting || _feed is not { Heroes.Count: > 1 } feed) return;
        _turn = Math.Min(1, _turn + _timer.Interval.TotalSeconds / TurnLength);
        if (_turn < 1) return;
        _hero = (_hero + 1) % feed.Heroes.Count;
        _turn = 0;
        ShowBillboard();
    }

    // MARK: Shelves

    /// <summary>The rows that have come in already: a fresh list changes their cards where they are.</summary>
    private readonly HashSet<string> _arrived = [];
    private static readonly Windows.Foundation.Size PosterCard = new(Theme.PosterWidth, Theme.PosterWidth * 1.5);
    private static readonly Windows.Foundation.Size RankedCard = new(Theme.PosterWidth + 60, Theme.PosterWidth * 1.5);
    private static readonly Windows.Foundation.Size WideCard = new(288, 162);
    /// <summary>Kept beside the app's data: the trending row came last time, so it keeps its place while it is looked for.</summary>
    private static string TrendingSeen => System.IO.Path.Combine(Storage.Folder, "home-trending-seen");

    /// <summary>
    /// Every row arrives whole, as on the Mac: stand-ins of its cards' size hold its place until its
    /// list and the pictures of its first cards are in (three seconds at most), then the cards come
    /// in order, and nothing on the page moves.
    /// </summary>
    private void ShowShelves()
    {
        var loc = App.Loc;
        _shelves.Children.Clear();
        if (_feed is not { } feed) return;
        if (App.IsLoggedIn && _continuing is null) _shelves.Children.Add(StandIns(loc[S.continueWatching], WideCard, 3));
        else if (_continuing is { Count: > 0 } continuing)
            AddShelf("continue", loc[S.continueWatching], continuing.Select(p => Kit.Continue(p, () => _nav.Open(p), () =>
            {
                App.DismissContinue(p);
                _continuing?.RemoveAll(x => x.Id == p.Id);
                ShowShelves();
            })).ToList(), null, step: 3);
        if (Trend.Count > 0)
            AddShelf("trending", loc[S.trendingWeek], Trend.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p))).ToList(), null);
        else if (!_trendDone && System.IO.File.Exists(TrendingSeen)) _shelves.Children.Add(StandIns(loc[S.trendingWeek], PosterCard));
        foreach (var section in feed.Sections)
        {
            var posts = Distinct(section.Posts);
            var cards = section.Key == "top_10"
                ? posts.Select((p, i) => Kit.Ranked(i + 1, p, () => _nav.Open(p))).ToList()
                : posts.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p))).ToList();
            Section? target = section.Key switch { "movies" => Section.Movies, "series" => Section.Series, "anime" => Section.Anime, _ => null };
            AddShelf(section.Key, loc.TitleForSection(section.Key), cards, target is { } t ? () => _nav.Go(t) : null);
            // The two top tens sit under the first shelf, before the long lists begin.
            if (section == feed.Sections[0] && feed.Posts("top_10").Count == 0)
            {
                Ranked("top-movies", loc[S.topFilms], Top.GetValueOrDefault("movies"));
                Ranked("top-series", loc[S.topSeries], Top.GetValueOrDefault("series"));
            }
            // After the top tens, with the user's TMDB key (the Android app's order).
            if (section == feed.Sections[0])
            {
                if (Fresh.Count > 0) AddShelf("fresh", loc[Extra.freshSeries], Fresh.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p))).ToList(), null);
                if (Pop.Count > 0) AddShelf("popular", loc[Extra.popularFilms], Pop.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p))).ToList(), null);
            }
        }
    }

    private void Ranked(string key, string title, List<Post>? posts)
    {
        if (posts is null && !_topDone) _shelves.Children.Add(StandIns(title, RankedCard, 4));
        if (posts is not { Count: > 0 }) return;
        AddShelf(key, title, posts.Select((p, i) => Kit.Ranked(i + 1, p, () => _nav.Open(p))).ToList(), null, step: 4);
    }

    /// <summary>A row still on its way: grey cards of the size its own will be.</summary>
    private static FrameworkElement StandIns(string title, Windows.Foundation.Size size, double step = 5) =>
        Kit.Shelf(title, Enumerable.Range(0, 7).Select(_ => (FrameworkElement)StandIn(size)), step: step);

    private static Border StandIn(Windows.Foundation.Size? size = null) => new()
    {
        Width = size?.Width ?? double.NaN, Height = size?.Height ?? double.NaN,
        CornerRadius = new CornerRadius(Theme.Radius), Background = Theme.White(0.06), IsHitTestVisible = false,
    };

    /// <summary>A row with its cards. One not in yet shows stand-ins under unseen cards, and brings the cards in once their pictures are.</summary>
    private void AddShelf(string key, string title, IReadOnlyList<FrameworkElement> cards, Action? more, double step = 5)
    {
        if (_arrived.Contains(key) || App.Settings.Calm && _arrived.Add(key))
        {
            _shelves.Children.Add(Kit.Shelf(title, cards, more, step));
            return;
        }
        var held = cards.Select(card =>
        {
            var slot = new Grid();
            var stand = StandIn();
            slot.Children.Add(stand);
            card.Opacity = 0;
            card.Translation = new System.Numerics.Vector3(0, 10, 0);
            slot.Children.Add(card);
            return (Slot: slot, Card: card, Stand: stand);
        }).ToList();
        var shelf = Kit.Shelf(title, held.Select(h => (FrameworkElement)h.Slot), more, step);
        _shelves.Children.Add(shelf);
        shelf.Loaded += async (_, _) =>
        {
            if (!_arrived.Add(key)) return;
            // A slow picture holds the row up for three seconds at most.
            await Task.WhenAny(Task.WhenAll(held.Take(7).Select(h => Images.ShownAsync(h.Card))), Task.Delay(3000));
            for (var i = 0; i < held.Count; i++)
            {
                var (slot, card, stand) = held[i];
                card.OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.35) };
                card.TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(0.35) };
                card.Opacity = 1;
                card.Translation = default;
                _ = Task.Delay(400).ContinueWith(_ => DispatcherQueue.TryEnqueue(() => slot.Children.Remove(stand)), TaskScheduler.Default);
                if (i < 9) await Task.Delay(60);
            }
        };
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

    /// <summary>"No internet" was said for the saved page shown; not again until it has come back.</summary>
    private bool _saidOffline;

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
            void Again()
            {
                Children.Clear();
                Children.Add(_stage);
                Children.Add(_scroller);
                _ = LoadAsync();
            }
            if (_feed is null)
            {
                Children.Clear();
                Children.Add(Kit.Ambient());
                Children.Add(Kit.Error(Again));
            }
            // The saved page stays, saying so once; and the page tries again by itself every half
            // minute while it is open, instead of waiting for a click (the Android app's).
            else if (!_saidOffline)
            {
                _saidOffline = true;
                App.ShowToast(App.Loc[Extra.offline]);
            }
            var shown = _feed is not null;
            _ = Task.Delay(TimeSpan.FromSeconds(30)).ContinueWith(_ => DispatcherQueue.TryEnqueue(() =>
            {
                if (!IsLoaded) return;
                if (shown) _ = LoadAsync(); else Again();
            }), TaskScheduler.Default);
            return;
        }
        _saidOffline = false;
        _ = LoadTrendingAsync();
        await LoadTopAsync();
    }

    private async Task LoadTrendingAsync()
    {
        if (Trend.Count > 0 && (Fresh.Count > 0 || TmdbStore.Key is null)) return;
        var found = Trend.Count > 0 ? Trend : await App.TrendingThisWeekAsync();
        _trendDone = true;
        // Whether it came, for the next launch to keep its place or not.
        try
        {
            if (found.Count > 0) System.IO.File.WriteAllText(TrendingSeen, "");
            else System.IO.File.Delete(TrendingSeen);
        }
        catch (Exception) { }
        Trend = found;
        ShowShelves();
        if (Fresh.Count == 0 && await App.TmdbRowAsync(TrendingRow.Fresh) is { Count: > 0 } fresh)
        {
            Fresh = fresh;
            ShowShelves();
        }
        if (Pop.Count == 0 && await App.TmdbRowAsync(TrendingRow.Pop) is { Count: > 0 } pop)
        {
            Pop = pop;
            ShowShelves();
        }
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
        _topDone = true;
        ShowShelves();
    }
}
