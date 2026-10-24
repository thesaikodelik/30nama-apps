using Microsoft.UI;
using Microsoft.UI.Input;
using Microsoft.UI.Text;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.App.Model;
using Nama.App.Pages;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App;

/// <summary>
/// The one window. Its content runs under the title bar, as the Mac app's does, with the
/// system's caption buttons left in their corner; the bar of sections floats above everything.
/// </summary>
public sealed partial class MainWindow : Window
{
    private readonly AppModel _app;
    private readonly Nav _nav = new();
    private readonly Grid _root = new() { Background = Theme.Brush(Theme.Bg0) };
    private readonly Grid _page = new();
    private readonly Grid _route = new();
    private readonly Grid _overlay = new();
    private readonly Border _dragStrip = new() { Height = 82, VerticalAlignment = VerticalAlignment.Top, Background = Theme.White(0) };
    private NavBar? _bar;
    private Border? _toast;
    private UpdatePill? _update;
    private CommandPalette? _palette;
    private SplashCurtain? _splash;
    private readonly System.Diagnostics.Stopwatch _splashClock = new();
    /// <summary>A title a notification asked for, opened once the app is signed in and showing.</summary>
    private int? _pendingPost;
    /// <summary>Asks now and then whether a followed series has a new episode.</summary>
    private readonly DispatcherTimer _episodeTimer = new() { Interval = TimeSpan.FromHours(1) };
    /// <summary>Holds the card a poster opens and the poster flying to a title's page, in window pixels left to right.</summary>
    private readonly Grid _effects = new() { FlowDirection = FlowDirection.LeftToRight };
    /// <summary>Asks now and then whether a newer version is out; the updater itself keeps to every few hours.</summary>
    private readonly DispatcherTimer _updateTimer = new() { Interval = TimeSpan.FromHours(1) };
    private Section? _shownSection;
    private Route? _shownRoute;
    private readonly Tray _tray;
    private bool _quitting;

    /// <summary>The window again, from the notification area or from a second start of the program.</summary>
    public void ShowAgain()
    {
        AppWindow.Show();
        _tray.Hide();
        Activate();
        Native.SetForegroundWindow(WinRT.Interop.WindowNative.GetWindowHandle(this));
    }

    private void Quit()
    {
        _quitting = true;
        Close();
    }

    /// <summary>The window is the one in use: things that move only for someone watching can rest otherwise.</summary>
    public static bool IsActive { get; private set; } = true;

    /// <summary>The one window, for the dialogs that have to be told whose they are.</summary>
    public static MainWindow Shared { get; private set; } = null!;

    public MainWindow()
    {
        InitializeComponent();
        Shared = this;
        Title = "30nama";
        _app = new AppModel(DispatcherQueue);
        _ = new Downloads(_app, DispatcherQueue);
        new Updater(DispatcherQueue).Changed += ShowUpdate;
        _updateTimer.Tick += (_, _) => _ = Updater.Shared.LookAsync();
        _episodeTimer.Tick += (_, _) =>
        {
            // The first look a minute in, once the home page has had its requests; then hourly.
            _episodeTimer.Interval = TimeSpan.FromHours(1);
            _ = EpisodeWatch.LookAsync(_app);
        };
        EpisodeWatch.Opened += post => DispatcherQueue.TryEnqueue(() =>
        {
            _pendingPost = post;
            ShowAgain();
            OpenPending();
        });
        _nav.Picking += ShowPicker;
        _nav.Trailering += ShowTrailer;
        Content = _root;
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(_dragStrip);
        AppWindow.TitleBar.ButtonBackgroundColor = Colors.Transparent;
        AppWindow.TitleBar.ButtonInactiveBackgroundColor = Colors.Transparent;
        AppWindow.TitleBar.ButtonForegroundColor = Theme.Text1;
        AppWindow.TitleBar.ButtonHoverBackgroundColor = Theme.Hex(0xFFFFFF, 0.1);
        AppWindow.TitleBar.PreferredHeightOption = TitleBarHeightOption.Tall;
        AppWindow.SetIcon(Path.Combine(AppContext.BaseDirectory, "Assets", "30nama.ico"));
        Resize(1440, 900);
        Activated += (_, e) =>
        {
            IsActive = e.WindowActivationState != WindowActivationState.Deactivated;
            // Coming forward is a time to look too, when an hour has gone by: a release put out
            // while the window sat behind others would otherwise wait for the next tick.
            // Not in the first seconds: that is the app opening, which looks for itself.
            if (IsActive && _app.Phase == AppModel.Phases.Main && Updater.Shared.Settled) _ = Updater.Shared.LookAsync();
        };
        _tray = new Tray(Path.Combine(AppContext.BaseDirectory, "Assets", "30nama.ico"))
        {
            Labels = () => (_app.Loc[Extra.openApp], _app.Loc[Extra.quit]),
        };
        _tray.OpenRequested += () => DispatcherQueue.TryEnqueue(ShowAgain);
        _tray.QuitRequested += () => DispatcherQueue.TryEnqueue(Quit);
        AppWindow.Closing += (_, e) =>
        {
            if (_quitting || !_app.Settings.KeepInTray) { _tray.Dispose(); return; }
            // Kept beside the clock: the window goes, the app and its downloads stay.
            e.Cancel = true;
            if (_app.Playback is not null) _app.ClosePlayer();
            AppWindow.Hide();
            _tray.Show("30nama");
        };

        _root.Children.Add(_page);
        _root.Children.Add(_route);
        _root.Children.Add(_dragStrip);
        _root.Children.Add(_overlay);
        _root.Children.Add(_effects);
        StartSplash();
        PosterEffects.Peeking += ShowPeek;
        _app.Changed += OnChanged;
        _nav.Changed += ShowNav;
        _root.KeyDown += (_, e) =>
        {
            // Esc steps back out of a title, as it closes anything else.
            if (e.Key == Windows.System.VirtualKey.Escape && _app.Playback is null) _nav.Back();
        };
        AddShortcuts();
        ApplyDirection();
        ShowPhase();
        _ = _app.BootAsync();
    }

    private void Resize(int width, int height)
    {
        var scale = GetScale();
        AppWindow.Resize(new Windows.Graphics.SizeInt32((int)(width * scale), (int)(height * scale)));
        if (DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary) is { } area)
        {
            var w = AppWindow.Size;
            AppWindow.Move(new Windows.Graphics.PointInt32(area.WorkArea.X + (area.WorkArea.Width - w.Width) / 2, area.WorkArea.Y + (area.WorkArea.Height - w.Height) / 2));
        }
    }

    private double GetScale()
    {
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
        return Native.GetDpiForWindow(hwnd) / 96.0;
    }

    private void OnChanged(string what)
    {
        switch (what)
        {
            case "phase": ShowPhase(); break;
            case "settings":
                if (ApplyDirection()) ShowPhase(rebuild: true);
                break;
            case "toast": ShowToast(); break;
            case "playback": ShowPlayer(); break;
        }
    }

    /// <summary>Lays the window out in the language's direction. True when that changed.</summary>
    private bool ApplyDirection()
    {
        var direction = _app.Settings.Lang.IsRtl() ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        if (_root.FlowDirection == direction) return false;
        _root.FlowDirection = direction;
        return true;
    }

    // MARK: Phases

    private void ShowPhase(bool rebuild = false)
    {
        _page.Children.Clear();
        _route.Children.Clear();
        _shownSection = null;
        _shownRoute = null;
        if (_bar is not null) { _overlay.Children.Remove(_bar); _bar = null; }
        switch (_app.Phase)
        {
            case AppModel.Phases.Booting:
                // Under the opening title the glass is enough; a spinner shows only once it has gone.
                _page.Children.Add(_splash is null ? Splash() : Kit.Ambient());
                break;
            case AppModel.Phases.Failed:
                _page.Children.Add(Kit.Ambient());
                _page.Children.Add(Kit.Error(() => _ = _app.RetryBootAsync(), _app.Loc[S.noConnection]));
                break;
            case AppModel.Phases.Login:
                _page.Children.Add(new LoginPage());
                break;
            case AppModel.Phases.Main:
                _bar = new NavBar(_nav) { Margin = new Thickness(0, Theme.BarTop, 0, 0) };
                _overlay.Children.Add(_bar);
                _bar.SizeChanged += (_, _) => UpdateDragRegions();
                ShowNav();
                ShowUpdate();
                _ = Updater.Shared.LookAsync();
                _updateTimer.Start();
                if (!_episodeTimer.IsEnabled)
                {
                    _episodeTimer.Interval = TimeSpan.FromMinutes(1);
                    _episodeTimer.Start();
                }
                OpenPending();
#if DEBUG
                _ = DebugLaunchAsync();
#endif
                break;
        }
        UpdateDragRegions();
    }

    /// <summary>
    /// The glass opening title over the app, as on the Mac. It plays its course once, then waits a
    /// little at most for the home page to come behind it, and parts. Not with reduced motion.
    /// </summary>
    private void StartSplash()
    {
        if (_app.Settings.ReduceMotion) return;
        _splash = new SplashCurtain();
        _root.Children.Add(_splash);
        // The app behind settles into place as the glass parts.
        void Centre(object sender, SizeChangedEventArgs e) =>
            ((UIElement)sender).CenterPoint = new System.Numerics.Vector3((float)e.NewSize.Width / 2, (float)e.NewSize.Height / 2, 0);
        foreach (var layer in new[] { _page, _route })
        {
            layer.ScaleTransition = new Vector3Transition { Duration = TimeSpan.FromSeconds(0.9) };
            layer.Scale = new System.Numerics.Vector3(1.03f, 1.03f, 1);
            layer.SizeChanged += Centre;
        }
        var watch = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(100) };
        _splash.Loaded += (_, _) => _splashClock.Start();
        watch.Tick += (_, _) =>
        {
            if (_splash is not { } splash) { watch.Stop(); return; }
            var phase = _app.Phase;
            if (phase != AppModel.Phases.Booting) splash.Ready();
            var played = _splashClock.Elapsed.TotalSeconds;
            var waiting = phase == AppModel.Phases.Booting || (phase == AppModel.Phases.Main && !_app.HomeLoaded);
            if (played < 2.8 || (waiting && played < 5.3)) return;
            watch.Stop();
            foreach (var layer in new[] { _page, _route }) layer.Scale = System.Numerics.Vector3.One;
            // Once settled, the layers carry no transform at all. Left with one, even at a scale of 1
            // about a centre on a half pixel (an odd window height), the whole app was drawn half a
            // pixel off: edges came out half covered, and a change inside a card redrew only whole
            // pixels, leaving a line along the top and left of a wall of posters.
            var settled = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
            settled.Tick += (_, _) =>
            {
                settled.Stop();
                foreach (var layer in new[] { _page, _route })
                {
                    layer.SizeChanged -= Centre;
                    layer.ScaleTransition = null;
                    layer.Scale = System.Numerics.Vector3.One;
                    layer.CenterPoint = default;
                }
            };
            settled.Start();
            splash.Leave(() =>
            {
                _root.Children.Remove(splash);
                _splash = null;
                if (_app.Phase == AppModel.Phases.Booting) ShowPhase();
            });
        };
        watch.Start();
    }

    /// <summary>The opening title: the logo over the dark, while a server is found.</summary>
    private static Grid Splash()
    {
        var g = Kit.Ambient();
        var column = new StackPanel { Spacing = 30, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        column.Children.Add(new Image { Source = new BitmapImage(new Uri("ms-appx:///Assets/Logo.png")), Height = 120, FlowDirection = FlowDirection.LeftToRight });
        column.Children.Add(Kit.Spinner());
        g.Children.Add(column);
        return g;
    }

#if DEBUG
    /// <summary>
    /// Debug builds go straight to a page: NAMA_SECTION names a section, NAMA_OPEN a title by id or
    /// "first", NAMA_PLAY=1 starts it, NAMA_PERSON opens a person and NAMA_PAGE a page of the site's. As DebugLaunch.swift on the Mac.
    /// </summary>
    private async Task DebugLaunchAsync()
    {
        if (Environment.GetEnvironmentVariable("NAMA_SECTION") is { } name && Enum.TryParse<Section>(name, true, out var section)) _nav.Go(section);
        // NAMA_NOTIFY_TEST=1: the new-episode notification for the first title on Home, at once.
        if (Environment.GetEnvironmentVariable("NAMA_NOTIFY_TEST") == "1" && (await _app.HomeAsync()).Heroes.FirstOrDefault() is { } shown)
        {
            Environment.SetEnvironmentVariable("NAMA_NOTIFY_TEST", null);
            EpisodeWatch.Show(shown, _app.Loc);
        }
        // NAMA_PAGE: one of the website's pages, "schedule", "toplists" or "news".
        switch (Environment.GetEnvironmentVariable("NAMA_PAGE"))
        {
            case "schedule": _nav.Open(new Route.Schedule()); break;
            case "toplists": _nav.Open(new Route.TopLists()); break;
            case "news": _nav.Open(new Route.News()); break;
        }
        // NAMA_PERSON: a person's page, by 30nama's id for them.
        if (int.TryParse(Environment.GetEnvironmentVariable("NAMA_PERSON"), out var person))
        {
            Environment.SetEnvironmentVariable("NAMA_PERSON", null);
            _nav.Open(new Route.Actor(new Person("…", null, null, SiteId: person)));
        }
        if (Environment.GetEnvironmentVariable("NAMA_OPEN") is not { } target) return;
        Environment.SetEnvironmentVariable("NAMA_OPEN", null); // once, not again after a sign-in
        try
        {
            var feed = await _app.HomeAsync();
            var pool = feed.Heroes.Concat(feed.Sections.SelectMany(s => s.Posts)).ToList();
            var post = target == "first" ? pool.FirstOrDefault() : pool.FirstOrDefault(p => p.Id.ToString() == target);
            if (post is not null) _nav.Open(post, playing: Environment.GetEnvironmentVariable("NAMA_PLAY") == "1");
        }
        catch (Exception) { }
    }
#endif

    // MARK: Sections and routes

    private void ShowNav()
    {
        ClosePeek();
        if (_app.Phase != AppModel.Phases.Main) return;
        if (_shownSection != _nav.Section)
        {
            _shownSection = _nav.Section;
            _page.Children.Clear();
            var loc = _app.Loc;
            _page.Children.Add(_nav.Section switch
            {
                Section.Settings => new SettingsPage(),
                Section.Search => new SearchPage(_nav),
                Section.Movies => new CatalogPage("movies", loc[S.movies], _nav),
                Section.Series => new CatalogPage("series", loc[S.series], _nav),
                Section.Anime => new CatalogPage("anime", loc[S.anime], _nav),
                Section.Browse => new BrowsePage(_nav),
                Section.Profile => new ProfilePage(_nav),
                Section.Downloads => new DownloadsPage(),
                _ => (UIElement)new HomePage(_nav),
            });
        }
        // What is open on top of the section: only the newest is built; the section stays under it.
        var top = _nav.Opened.Count > 0 ? _nav.Opened[^1] : null;
        _page.Visibility = top is null ? Visibility.Visible : Visibility.Collapsed;
        if (top is not null && _shownRoute is not null && _shownRoute.Same(top)) return;
        _shownRoute = top;
        _route.Children.Clear();
        var flight = top is Route.Title opened && !_app.Settings.ReduceMotion ? PosterEffects.TakeLaunch(opened.Post.Id) : null;
        FrameworkElement? page = top switch
        {
            Route.Title title => new TitlePage(title.Post, _nav, title.Playing, held: flight is not null),
            Route.Catalog catalog => new CatalogPage(catalog.Slug, catalog.Name, _nav),
            Route.Advanced advanced => new AdvancedSearchPage(_nav, advanced.Start),
            Route.Actor actor => new PersonPage(actor.Person, _nav),
            Route.Schedule => new SchedulePage(_nav),
            Route.TopLists => new TopListsPage(_nav),
            Route.News => new NewsPage(_nav),
            Route.Article article => new ArticleReader(article.Item),
            _ => null,
        };
        if (page is null) return;
        page.Opacity = 0;
        page.Springy(0.28);
        _route.Children.Add(page);
        page.Loaded += (_, _) => page.Opacity = 1;
        if (flight is { } launch && top is Route.Title flown) Fly(flown.Post, launch.From, launch.Picture);
    }

    private void OpenPending()
    {
        if (_pendingPost is not { } id || _app.Phase != AppModel.Phases.Main) return;
        _pendingPost = null;
        if (_app.Playback is not null) _app.ClosePlayer();
        // Only the id is known; the page asks for the rest itself.
        if (Post.Parse(Json.From(new Dictionary<string, object?> { ["id"] = id })) is { } post) _nav.Open(post);
    }

    // MARK: Poster effects

    /// <summary>The card about a title, over the poster the pointer rests on, kept inside the window.</summary>
    private void ShowPeek(Post post, Windows.Foundation.Rect poster)
    {
        // Not under a pane that has the window: the pointer there is not on the poster.
        if (_palette is not null || _app.Playback is not null || _overlay.Children.OfType<DownloadPicker>().Any() || _overlay.Children.OfType<TrailerSheet>().Any()) return;
        ClosePeek();
        var width = _root.ActualWidth;
        var height = _root.ActualHeight;
        var x = Math.Clamp(poster.X + poster.Width / 2 - PeekCard.CardWidth / 2, 16, Math.Max(16, width - PeekCard.CardWidth - 16));
        var y = Math.Clamp(poster.Y + poster.Height / 2 - PeekCard.CardHeight / 2, 16, Math.Max(16, height - PeekCard.CardHeight - 16));
        var card = new PeekCard(post, _nav, ClosePeek)
        {
            HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(x, y, 0, 0), FlowDirection = _root.FlowDirection,
        };
        _effects.Children.Add(card);
    }

    private void ClosePeek()
    {
        foreach (var card in _effects.Children.OfType<PeekCard>().ToList()) _effects.Children.Remove(card);
    }

    /// <summary>
    /// The poster of a title being opened, travelling from the card that was clicked to where the
    /// page keeps it. The page holds its own copy back until this one has landed.
    /// </summary>
    private void Fly(Post post, Windows.Foundation.Rect from, Microsoft.UI.Xaml.Media.ImageSource? picture)
    {
        const double w = 240, h = 360, top = 130;
        var rtl = _root.FlowDirection == FlowDirection.RightToLeft;
        var land = new Windows.Foundation.Point(rtl ? _root.ActualWidth - Theme.Edge - w : Theme.Edge, top);
        var card = new Grid
        {
            Width = w, Height = h, CornerRadius = new CornerRadius(16), Background = Theme.Brush(Theme.Surface2),
            HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(land.X, land.Y, 0, 0), IsHitTestVisible = false,
            // The card's own picture: it is in memory already, so the flight never starts blank.
            Translation = new System.Numerics.Vector3((float)(from.X - land.X), (float)(from.Y - land.Y), 0),
            Scale = new System.Numerics.Vector3((float)(from.Width / w), (float)(from.Height / h), 1),
        };
        card.Children.Add(picture is not null
            ? new Image { Source = picture, Stretch = Microsoft.UI.Xaml.Media.Stretch.UniformToFill }
            : Images.Remote(post.Poster.Url(Theme.PosterWidth * 1.1 * 1.5), post.Poster.Blurhash, Theme.PosterWidth * 1.5));
        _effects.Children.Add(card);
        card.Loaded += (_, _) => DispatcherQueue.TryEnqueue(() =>
        {
            card.TranslationTransition = new Vector3Transition { Duration = TimeSpan.FromMilliseconds(480) };
            card.ScaleTransition = new Vector3Transition { Duration = TimeSpan.FromMilliseconds(480) };
            card.Translation = default;
            card.Scale = System.Numerics.Vector3.One;
        });
        _ = Task.Delay(640).ContinueWith(_ => DispatcherQueue.TryEnqueue(() => _effects.Children.Remove(card)), TaskScheduler.Default);
    }

    /// <summary>
    /// The keyboard's way around, as on the Mac: Ctrl+1 to Ctrl+8 for the sections on the bar,
    /// Ctrl+K to find anything, Ctrl+F to search, Alt+Left to step back. The player has keys of its own.
    /// </summary>
    private void AddShortcuts()
    {
        _root.KeyboardAcceleratorPlacementMode = Microsoft.UI.Xaml.Input.KeyboardAcceleratorPlacementMode.Hidden;
        void Key(Windows.System.VirtualKey key, Windows.System.VirtualKeyModifiers modifiers, Action action)
        {
            var accelerator = new Microsoft.UI.Xaml.Input.KeyboardAccelerator { Key = key, Modifiers = modifiers };
            accelerator.Invoked += (_, e) =>
            {
                if (_app.Phase != AppModel.Phases.Main || _app.Playback is not null) return;
                e.Handled = true;
                action();
            };
            _root.KeyboardAccelerators.Add(accelerator);
        }
        const Windows.System.VirtualKeyModifiers ctrl = Windows.System.VirtualKeyModifiers.Control;
        for (var i = 0; i < Nav.OnBar.Count && i < 9; i++)
        {
            var section = Nav.OnBar[i];
            Key(Windows.System.VirtualKey.Number1 + i, ctrl, () => { ClosePalette(); _nav.Go(section); });
        }
        Key(Windows.System.VirtualKey.K, ctrl, TogglePalette);
        Key(Windows.System.VirtualKey.F, ctrl, () => { ClosePalette(); _nav.Go(Section.Search); });
        Key(Windows.System.VirtualKey.Left, Windows.System.VirtualKeyModifiers.Menu, () => { ClosePalette(); _nav.Back(); });
    }

    private void TogglePalette()
    {
        if (_palette is not null)
        {
            ClosePalette();
            return;
        }
        _palette = new CommandPalette(_nav, ClosePalette);
        _overlay.Children.Add(_palette);
    }

    private void ClosePalette()
    {
        if (_palette is null) return;
        _overlay.Children.Remove(_palette);
        _palette = null;
    }

    /// <summary>The pane for choosing what of a title to download, over everything but the player.</summary>
    private void ShowPicker(Post post)
    {
        DownloadPicker? picker = null;
        picker = new DownloadPicker(post, () => _overlay.Children.Remove(picker));
        _overlay.Children.Insert(_bar is null ? 0 : _overlay.Children.IndexOf(_bar) + 1, picker);
    }

    private void ShowTrailer(string title, string video)
    {
        TrailerSheet? sheet = null;
        sheet = new TrailerSheet(title, video, () => _overlay.Children.Remove(sheet));
        _overlay.Children.Insert(_bar is null ? 0 : _overlay.Children.IndexOf(_bar) + 1, sheet);
    }

    private void ShowPlayer()
    {
        _overlay.Children.Remove(_overlay.Children.OfType<PlayerPage>().FirstOrDefault());
        if (_app.Playback is { } request)
        {
            var player = new PlayerPage(request, this);
            _overlay.Children.Add(player);
            player.Focus(FocusState.Programmatic);
        }
        if (_bar is not null) _bar.Visibility = _app.Playback is null ? Visibility.Visible : Visibility.Collapsed;
        ShowUpdate();
        UpdateDragRegions();
    }

    /// <summary>The capsule that tells of a newer version, under the bar, while there is something to tell.</summary>
    private void ShowUpdate()
    {
        var updater = Updater.Shared;
        var wanted = updater.Phase != Updater.Phases.Idle && !updater.Postponed && _bar is not null && _app.Playback is null;
        if (!wanted)
        {
            if (_update is not null) _overlay.Children.Remove(_update);
            _update = null;
        }
        else if (_update is null)
        {
            _update = new UpdatePill(Quit);
            _update.SizeChanged += (_, _) => UpdateDragRegions();
            _overlay.Children.Insert(_overlay.Children.IndexOf(_bar) + 1, _update);
        }
        UpdateDragRegions();
    }

    // MARK: Messages

    private void ShowToast()
    {
        if (_toast is not null) _toast.Opacity = 0;
        if (_app.Toast is not { } text) return;
        if (_toast is null)
        {
            _toast = new Border
            {
                Height = 44, Padding = new Thickness(20, 0, 20, 0), CornerRadius = new CornerRadius(22), Background = Theme.GlassFill(),
                BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim, HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(0, 0, 0, 40), IsHitTestVisible = false,
            }.Springy(0.25);
            _overlay.Children.Add(_toast);
        }
        var label = Theme.Text(text, 15, FontWeights.SemiBold);
        label.VerticalAlignment = VerticalAlignment.Center;
        _toast.Child = label;
        _toast.Opacity = 1;
    }

    // MARK: Title bar

    /// <summary>
    /// The strip along the top drags the window; the bar of sections, which lies over it, has to
    /// be told apart so clicks on it reach its buttons.
    /// </summary>
    private void UpdateDragRegions()
    {
        if (_root.XamlRoot is null) return;
        var scale = _root.XamlRoot.RasterizationScale;
        var source = InputNonClientPointerSource.GetForWindowId(AppWindow.Id);
        if (_bar is null || _bar.Visibility != Visibility.Visible || _bar.ActualWidth == 0)
        {
            source.SetRegionRects(NonClientRegionKind.Passthrough, []);
            return;
        }
        // The update capsule lies over the strip too, just under the bar.
        source.SetRegionRects(NonClientRegionKind.Passthrough, new FrameworkElement?[] { _bar, _update }
            .OfType<FrameworkElement>()
            .Where(e => e.ActualWidth > 0)
            .Select(e =>
            {
                var bounds = e.TransformToVisual(null).TransformBounds(new Windows.Foundation.Rect(0, 0, e.ActualWidth, e.ActualHeight));
                return new Windows.Graphics.RectInt32((int)(bounds.X * scale), (int)(bounds.Y * scale), (int)(bounds.Width * scale), (int)(bounds.Height * scale));
            })
            .ToArray());
    }

    /// <summary>Full screen for the player, and back.</summary>
    public void SetFullScreen(bool on) =>
        AppWindow.SetPresenter(on ? AppWindowPresenterKind.FullScreen : AppWindowPresenterKind.Default);

    public bool IsFullScreen => AppWindow.Presenter.Kind == AppWindowPresenterKind.FullScreen;
}

internal static partial class Native
{
    [System.Runtime.InteropServices.LibraryImport("user32.dll")]
    public static partial uint GetDpiForWindow(IntPtr hwnd);

    [System.Runtime.InteropServices.LibraryImport("user32.dll")]
    [return: System.Runtime.InteropServices.MarshalAs(System.Runtime.InteropServices.UnmanagedType.Bool)]
    public static partial bool SetForegroundWindow(IntPtr hwnd);
}
