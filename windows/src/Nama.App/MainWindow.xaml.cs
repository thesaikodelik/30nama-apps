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
    private readonly Border _dragStrip = new() { Height = 72, VerticalAlignment = VerticalAlignment.Top, Background = Theme.White(0) };
    private NavBar? _bar;
    private Border? _toast;
    private UpdatePill? _update;
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
        Activated += (_, e) => IsActive = e.WindowActivationState != WindowActivationState.Deactivated;
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
        _app.Changed += OnChanged;
        _nav.Changed += ShowNav;
        _root.KeyDown += (_, e) =>
        {
            // Esc steps back out of a title, as it closes anything else.
            if (e.Key == Windows.System.VirtualKey.Escape && _app.Playback is null) _nav.Back();
        };
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
                _page.Children.Add(Splash());
                break;
            case AppModel.Phases.Failed:
                _page.Children.Add(Kit.Ambient());
                _page.Children.Add(Kit.Error(() => _ = _app.RetryBootAsync(), _app.Loc[S.noConnection]));
                break;
            case AppModel.Phases.Login:
                _page.Children.Add(new LoginPage());
                break;
            case AppModel.Phases.Main:
                _bar = new NavBar(_nav) { Margin = new Thickness(0, 14, 0, 0) };
                _overlay.Children.Add(_bar);
                _bar.SizeChanged += (_, _) => UpdateDragRegions();
                ShowNav();
                ShowUpdate();
                _ = Updater.Shared.LookAsync();
                _updateTimer.Start();
#if DEBUG
                _ = DebugLaunchAsync();
#endif
                break;
        }
        UpdateDragRegions();
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
    /// "first", and NAMA_PLAY=1 starts it. As DebugLaunch.swift on the Mac.
    /// </summary>
    private async Task DebugLaunchAsync()
    {
        if (Environment.GetEnvironmentVariable("NAMA_SECTION") is { } name && Enum.TryParse<Section>(name, true, out var section)) _nav.Go(section);
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
        FrameworkElement? page = top switch
        {
            Route.Title title => new TitlePage(title.Post, _nav, title.Playing),
            Route.Catalog catalog => new CatalogPage(catalog.Slug, catalog.Name, _nav),
            _ => null,
        };
        if (page is null) return;
        page.Opacity = 0;
        page.Springy(0.28);
        _route.Children.Add(page);
        page.Loaded += (_, _) => page.Opacity = 1;
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
