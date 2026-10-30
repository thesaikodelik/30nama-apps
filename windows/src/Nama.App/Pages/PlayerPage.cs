using System.Runtime.InteropServices;
using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Nama.App.Model;
using Nama.App.Player;
using Nama.App.UI;
using Nama.Core;
using Windows.System;
using Color = Windows.UI.Color;

namespace Nama.App.Pages;

/// <summary>
/// The picture, filling the window, with the app's own controls over it. The controls fade
/// when the pointer rests, as does the pointer. The keyboard does everything they do: space, the
/// arrows, Return, F, M, T, N, E, G and H, [ and ], and / for the list of them.
/// </summary>
public sealed partial class PlayerPage : Grid
{
    private static AppModel App => AppModel.Shared;
    private static Loc Loc => App.Loc;

    private readonly PlaybackRequest _request;
    private readonly MainWindow _window;
    private readonly PlayerEngine _engine;
    private NowPlaying? _nowPlaying;
    private readonly Color _hue;

    private readonly VideoSurface _surface = new();
    private readonly Grid _curtain = new() { IsHitTestVisible = false };
    private readonly ProgressRing _spinner = Kit.Spinner();
    private readonly Grid _subtitle = new() { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Bottom, IsHitTestVisible = false, MaxWidth = 1100 };
    private readonly Grid _chrome = new();
    private readonly Grid _offer = new() { HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Bottom };
    private readonly Border _skipPill = new() { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, IsHitTestVisible = false, Opacity = 0 };
    private readonly Grid _panelHost = new();

    // The parts of the controls that change while playing.
    private readonly TextBlock _clock = new() { FontSize = 13, Foreground = Theme.White(0.85), VerticalAlignment = VerticalAlignment.Center, FontFamily = new FontFamily("Segoe UI Variable Text,Segoe UI") };
    private readonly TextBlock _episodeLine = Theme.Text("", 14, FontWeights.Medium, Theme.Hex(0xFFFFFF, 0.72));
    private readonly Scrubber _scrubber;
    private readonly Level _level;
    private Button _playButton = null!, _muteButton = null!, _speedButton = null!, _pinButton = null!, _nextButton = null!;

    private readonly DispatcherTimer _hide = new() { Interval = TimeSpan.FromSeconds(3) };
    private bool _controls = true;
    private bool _episodesOpen;
    private bool _keysOpen;
    private string _shownCue = "";

    public PlayerPage(PlaybackRequest request, MainWindow window)
    {
        _request = request;
        _window = window;
        _hue = request.Post.Tint();
        _engine = new PlayerEngine(request, App, DispatcherQueue);
        _scrubber = new Scrubber(_hue, seconds => _engine.Seek(seconds), seconds => _engine.Preview.Ask(seconds));
        _engine.Preview.Arrived += _scrubber.Arrived;
        _level = new Level(value => _engine.SetVolume(value));
        Background = Theme.Brush(Colors.Black);
        IsTabStop = true;
        TabFocusNavigation = KeyboardNavigationMode.Cycle;

        _surface.Playback = _engine.Playback;
        Children.Add(_surface);
        Children.Add(_glow);
        BuildCurtain();
        Children.Add(_curtain);
        _spinner.Foreground = Theme.Brush(Colors.White);
        _spinner.Visibility = Visibility.Collapsed;
        _spinner.IsHitTestVisible = false;
        Children.Add(_spinner);
        Children.Add(_subtitle);
        BuildChrome();
        Children.Add(_chrome);
        _skipPill.Springy(0.18);
        Children.Add(_skipPill);
        _offer.Margin = new Thickness(Theme.Edge, 0, Theme.Edge, 150);
        Children.Add(_offer);
        Children.Add(_panelHost);

        _engine.Changed += Refresh;
        _engine.FullScreenToggled += () => _window.SetFullScreen(!_window.IsFullScreen);
        _engine.PinnedChanged += pinned =>
        {
            if (_window.AppWindow.Presenter is Microsoft.UI.Windowing.OverlappedPresenter presenter) presenter.IsAlwaysOnTop = pinned;
        };
        _hide.Tick += (_, _) => HideControls();
        // The mouse, a click or a key: someone is watching (autoplay stops after three episodes without).
        PointerMoved += (_, _) =>
        {
            _engine.Touched();
            Wake();
        };
        Tapped += OnTapped;
        DoubleTapped += (_, e) =>
        {
            if (e.OriginalSource is FrameworkElement f && IsOnPicture(f)) _engine.ToggleFullScreen();
        };
        KeyDown += OnKey;
        AllowDrop = true;
        DragOver += (_, e) => e.AcceptedOperation = Windows.ApplicationModel.DataTransfer.DataPackageOperation.Copy;
        Drop += async (_, e) =>
        {
            if (!e.DataView.Contains(Windows.ApplicationModel.DataTransfer.StandardDataFormats.StorageItems)) return;
            var items = await e.DataView.GetStorageItemsAsync();
            if (items.FirstOrDefault() is Windows.Storage.StorageFile file) _engine.LoadSubtitleFile(file.Path);
        };
        SizeChanged += (_, _) =>
        {
            ShowCue(force: true);
            ShowGlow();
        };
        Loaded += (_, _) =>
        {
            _nowPlaying = new NowPlaying(_engine, WinRT.Interop.WindowNative.GetWindowHandle(_window), DispatcherQueue);
            _engine.Begin();
            Focus(FocusState.Programmatic);
            Wake();
        };
        Unloaded += (_, _) =>
        {
            _hide.Stop();
            _nowPlaying?.Dispose();
            _engine.Shutdown();
            ShowPointer(true);
            if (_window.IsFullScreen) _window.SetFullScreen(false);
            if (_window.AppWindow.Presenter is Microsoft.UI.Windowing.OverlappedPresenter presenter) presenter.IsAlwaysOnTop = false;
        };
    }

    // MARK: Showing and hiding

    /// <summary>Brings the controls up and lets them go again a few seconds after the pointer stops.</summary>
    private void Wake()
    {
        if (!_controls)
        {
            _controls = true;
            _chrome.Opacity = 1;
            _chrome.IsHitTestVisible = true;
            ShowPointer(true);
            PlaceOverlays();
        }
        _hide.Stop();
        _hide.Start();
    }

    private void HideControls()
    {
        _hide.Stop();
        if (!_engine.Playing || _episodesOpen || _keysOpen || !_controls) return;
        _controls = false;
        _chrome.Opacity = 0;
        _chrome.IsHitTestVisible = false;
        ShowPointer(false);
        PlaceOverlays();
    }

    /// <summary>The subtitle and the offer move down to the edge while the controls are away.</summary>
    private void PlaceOverlays()
    {
        _subtitle.Margin = new Thickness(Theme.Edge, 0, Theme.Edge, _controls ? 138 : 56);
        _offer.Margin = new Thickness(Theme.Edge, 0, Theme.Edge, _controls ? 150 : 70);
    }

    private bool _pointerHidden;

    private void ShowPointer(bool show)
    {
        if (show == !_pointerHidden) return;
        _pointerHidden = !show;
        _ = Native.ShowCursor(show);
    }

    /// <summary>A click on the picture shows the controls, or puts them away; pausing is Space or the button.</summary>
    private void OnTapped(object sender, TappedRoutedEventArgs e)
    {
        _engine.Touched();
        if (e.OriginalSource is not FrameworkElement f || !IsOnPicture(f)) return;
        if (_episodesOpen) { SetEpisodes(false); Wake(); }
        else if (_controls) HideControls();
        else Wake();
    }

    private bool IsOnPicture(FrameworkElement source) => source == this || source == _surface || source == _chrome || source.Parent == _chrome;

    // MARK: Keys

    /// <summary>When the arrow now held went down; its repeats seek further the longer it is held.</summary>
    private DateTime _heldSince;

    /// <summary>
    /// Ten seconds a press, doubling every second and a half an arrow is held, up to eighty (the
    /// Android app's, from ten as here and on the Mac): a long film is crossed without a hundred presses.
    /// </summary>
    private double HeldStep(KeyRoutedEventArgs e)
    {
        var now = DateTime.UtcNow;
        if (!e.KeyStatus.WasKeyDown) _heldSince = now;
        return 10 * (1 << Math.Min(3, (int)((now - _heldSince).TotalSeconds / 1.5)));
    }

    private void OnKey(object sender, KeyRoutedEventArgs e)
    {
        _engine.Touched();
        Wake();
        var handled = true;
        switch (e.Key)
        {
            case VirtualKey.Space: _engine.Toggle(); break;
            case VirtualKey.Left: _engine.Skip(-HeldStep(e)); break;
            case VirtualKey.Right: _engine.Skip(HeldStep(e)); break;
            case VirtualKey.Up: _engine.SetVolume(_engine.Volume + 0.1); break;
            case VirtualKey.Down: _engine.SetVolume(_engine.Volume - 0.1); break;
            // Takes the offer on screen; with none, full screen, as in other players.
            case VirtualKey.Enter:
                if (_engine.Offer is not null) _engine.AcceptOffer(); else _engine.ToggleFullScreen();
                break;
            case VirtualKey.Escape: Leave(); break;
            case VirtualKey.F: _engine.ToggleFullScreen(); break;
            case VirtualKey.M: _engine.ToggleMute(); break;
            case VirtualKey.T: _engine.TogglePinned(); break;
            case VirtualKey.N: if (_engine.HasNext) _engine.Start(_engine.Index + 1); break;
            case VirtualKey.E: if (_engine.Items.Count > 1) SetEpisodes(!_episodesOpen); break;
            case VirtualKey.G: _engine.NudgeSubtitles(-0.25); break;
            case VirtualKey.H: _engine.NudgeSubtitles(0.25); break;
            case (VirtualKey)219: _engine.StepSpeed(-1); break; // [
            case (VirtualKey)221: _engine.StepSpeed(1); break;  // ]
            case (VirtualKey)191: SetKeys(!_keysOpen); break;   // / and ?
            default: handled = false; break;
        }
        e.Handled = handled;
    }

    /// <summary>Esc closes what is open over the picture, then leaves full screen, and the player only after that.</summary>
    private void Leave()
    {
        if (_keysOpen) SetKeys(false);
        else if (_episodesOpen) SetEpisodes(false);
        else if (_window.IsFullScreen) _window.SetFullScreen(false);
        else _engine.Close();
    }

    // MARK: State

    private void Refresh()
    {
        var engine = _engine;
        _curtain.Opacity = engine.Started ? 0 : 1;
        _spinner.Visibility = engine.Started && engine.Buffering ? Visibility.Visible : Visibility.Collapsed;
        // "Ends at" beside the length, at the speed it is playing (the Android app's).
        var ends = engine.EndsAt is { } end ? $"   ·   {Loc[Extra.endsAt]} {Loc.Num(end.ToString("HH:mm"))}" : "";
        _clock.Text = $"{Fmt.Clock(engine.Time)} / {Fmt.Clock(engine.Duration)}{ends}";
        _scrubber.Update(engine.Time, engine.Duration, engine.Marks);
        _level.Update(engine.Muted ? 0 : engine.Volume);
        SetGlyph(_playButton, engine.Playing ? Glyph.Pause : Glyph.Play, Loc[engine.Playing ? S.pause : S.play]);
        SetGlyph(_muteButton, engine.Muted || engine.Volume == 0 ? Glyph.Mute : Glyph.Volume, Loc[S.sound]);
        _speedButton.Content = SpeedFace();
        _pinButton.Background = engine.Pinned ? Theme.Brush(Colors.White) : null;
        ((FontIcon)((Grid)_pinButton.Content).Children[^1]).Foreground = Theme.Brush(engine.Pinned ? Theme.FocusInk : Colors.White);
        _nextButton.Visibility = engine.HasNext ? Visibility.Visible : Visibility.Collapsed;
        _episodeLine.Text = EpisodeLine();
        _episodeLine.Visibility = _episodeLine.Text.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
        ShowCue();
        ShowGlow();
        ShowOffer();
        _skipPill.Opacity = engine.PendingSkip != 0 ? 1 : 0;
        if (engine.PendingSkip != 0)
        {
            var seconds = (int)engine.PendingSkip;
            var text = Theme.Text((seconds > 0 ? "+" : "−") + Loc.Num(Math.Abs(seconds)) + " " + Loc[S.secondsShort], 22, FontWeights.Bold, Colors.White);
            text.FlowDirection = FlowDirection.LeftToRight;
            _skipPill.Child = text;
        }
        if (!engine.Playing) Wake();
    }

    // MARK: Around the picture

    /// <summary>The bars the picture leaves, lit in the title's colour (Settings › Around the picture).</summary>
    private readonly Grid _glow = new() { IsHitTestVisible = false, FlowDirection = FlowDirection.LeftToRight };
    private (double Aspect, double Width, double Height, bool On) _glowShown;

    /// <summary>
    /// Brightest next to the picture and fading out to the window's edge, above and below a wide
    /// film or beside a tall one; nothing where the bars are thinner than ten points. As the Mac's
    /// AmbientLight, drawn again only when the picture's shape or the window changes.
    /// </summary>
    private void ShowGlow()
    {
        var aspect = _engine.Playback.Aspect;
        var shown = (aspect, ActualWidth, ActualHeight, App.Settings.SceneGlow);
        if (shown == _glowShown) return;
        _glowShown = shown;
        _glow.Children.Clear();
        _glow.RowDefinitions.Clear();
        _glow.ColumnDefinitions.Clear();
        if (!App.Settings.SceneGlow || aspect <= 0 || ActualWidth <= 0 || ActualHeight <= 0) return;
        var wide = aspect > ActualWidth / ActualHeight;
        var bar = wide ? (ActualHeight - ActualWidth / aspect) / 2 : (ActualWidth - ActualHeight * aspect) / 2;
        if (bar < 10) return;
        Windows.Foundation.Point P(double x, double y) => new(x, y);
        Border Light(Windows.Foundation.Point from, Windows.Foundation.Point to) => new()
        {
            Background = new LinearGradientBrush
            {
                StartPoint = from, EndPoint = to,
                GradientStops =
                {
                    new GradientStop { Color = _hue.WithAlpha(0.08), Offset = 0 },
                    new GradientStop { Color = _hue.WithAlpha(0.4), Offset = 1 },
                },
            },
        };
        if (wide)
        {
            _glow.RowDefinitions.Add(new RowDefinition { Height = new GridLength(bar) });
            _glow.RowDefinitions.Add(new RowDefinition());
            _glow.RowDefinitions.Add(new RowDefinition { Height = new GridLength(bar) });
            _glow.Children.Add(Light(P(0.5, 0), P(0.5, 1)));
            var below = Light(P(0.5, 1), P(0.5, 0));
            Grid.SetRow(below, 2);
            _glow.Children.Add(below);
        }
        else
        {
            _glow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(bar) });
            _glow.ColumnDefinitions.Add(new ColumnDefinition());
            _glow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(bar) });
            _glow.Children.Add(Light(P(0, 0.5), P(1, 0.5)));
            var right = Light(P(1, 0.5), P(0, 0.5));
            Grid.SetColumn(right, 2);
            _glow.Children.Add(right);
        }
    }

    private string EpisodeLine()
    {
        if (!_engine.Post.IsSeries) return "";
        var item = _engine.Current;
        var parts = new List<string> { Loc.EpisodeTag(item.Season, item.EpisodeRaw ?? item.EpisodeNumber.ToString()) };
        if (item.Title(Loc.Lang) is { } title) parts.Add(title);
        return string.Join(" · ", parts);
    }

    // MARK: Subtitles

    /// <summary>
    /// The cue in the user's size, colour and plate. Sizes are set for a 1080-line picture; the
    /// text keeps its share of whatever the window is.
    /// </summary>
    private void ShowCue(bool force = false)
    {
        var cue = _engine.Started ? _engine.Cue : "";
        if (cue == _shownCue && !force) return;
        _shownCue = cue;
        _subtitle.Children.Clear();
        if (cue.Length == 0 || ActualHeight <= 0) return;
        var s = App.Settings;
        var size = Math.Max(15, s.SubtitleSize * ActualHeight / 1080);
        _subtitle.Children.Add(SubtitleLook.Line(cue, size, s));
    }

    // MARK: Building

    private void BuildCurtain()
    {
        var post = _request.Post;
        _curtain.Background = Theme.Brush(Colors.Black);
        _curtain.Children.Add(Images.Remote(post.Cover ?? post.Poster.Big ?? post.Poster.Large, post.Poster.Blurhash, 1920, fallback: post.Poster.Big ?? post.Poster.Large));
        _curtain.Children.Add(new Border
        {
            Background = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
                GradientStops = { new GradientStop { Color = Theme.Hex(0, 0.25), Offset = 0 }, new GradientStop { Color = Theme.Hex(0, 0.8), Offset = 1 } },
            },
        });
        var text = new StackPanel { Spacing = 8, VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(Theme.Edge, 0, Theme.Edge, 170) };
        text.Children.Add(Theme.Text(post.Title(Loc.Lang), 44, FontWeights.ExtraBold, lines: 2));
        var line = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        var ring = new ProgressRing { IsActive = true, Width = 18, Height = 18, Foreground = Theme.Brush(Colors.White) };
        line.Children.Add(ring);
        if (post.IsSeries && _request.Items.Count > _request.Index && _request.Index >= 0)
        {
            var item = _request.Items[_request.Index];
            line.Children.Add(Theme.Text(Loc.EpisodeTag(item.Season, item.EpisodeRaw ?? item.EpisodeNumber.ToString()), 17, FontWeights.SemiBold, Theme.Text2));
        }
        text.Children.Add(line);
        _curtain.Children.Add(text);
        _curtain.OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.9) };
    }

    private void BuildChrome()
    {
        _chrome.OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(0.25) };
        _chrome.Background = Theme.White(0); // takes clicks on the picture while the controls are up
        _chrome.Children.Add(new Border
        {
            Height = 150, VerticalAlignment = VerticalAlignment.Top, IsHitTestVisible = false,
            Background = Gradient(Theme.Hex(0, 0.75), Colors.Transparent),
        });
        _chrome.Children.Add(new Border
        {
            Height = 230, VerticalAlignment = VerticalAlignment.Bottom, IsHitTestVisible = false,
            Background = Gradient(Colors.Transparent, Theme.Hex(0, 0.88)),
        });
        _chrome.Children.Add(TopBar());
        _chrome.Children.Add(BottomBar());
        PlaceOverlays();
    }

    private static LinearGradientBrush Gradient(Color top, Color bottom) => new()
    {
        StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
        GradientStops = { new GradientStop { Color = top, Offset = 0 }, new GradientStop { Color = bottom, Offset = 1 } },
    };

    private FrameworkElement TopBar()
    {
        var bar = new Grid { VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(Theme.Edge - 12, 50, Theme.Edge - 12, 0), ColumnSpacing = 14 };
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.Children.Add(Round(Glyph.Back, Loc[S.back], _engine.Close));
        var titles = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        titles.Children.Add(Theme.Text(_engine.Post.Title(Loc.Lang), 20, FontWeights.Bold, Colors.White));
        titles.Children.Add(_episodeLine);
        Grid.SetColumn(titles, 1);
        bar.Children.Add(titles);
        var end = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        _pinButton = Round("\uE718", Loc[S.alwaysOnTop], _engine.TogglePinned);
        end.Children.Add(_pinButton);
        end.Children.Add(Round(Glyph.FullScreen, Loc[S.fullScreen], _engine.ToggleFullScreen));
        Grid.SetColumn(end, 2);
        bar.Children.Add(end);
        return bar;
    }

    private FrameworkElement BottomBar()
    {
        // A timeline runs left to right in every language.
        var column = new StackPanel { Spacing = 6, VerticalAlignment = VerticalAlignment.Bottom, FlowDirection = FlowDirection.LeftToRight, Margin = new Thickness(Theme.Edge - 12, 0, Theme.Edge - 12, 22) };
        column.Children.Add(_scrubber);
        var row = new Grid();
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var start = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        _playButton = Round(Glyph.Pause, Loc[S.pause], _engine.Toggle, size: 46, glyphSize: 20);
        start.Children.Add(_playButton);
        start.Children.Add(Round("\uED3C", Loc[S.back10], () => _engine.Skip(-10)));
        start.Children.Add(Round("\uED3D", Loc[S.forward10], () => _engine.Skip(10)));
        _nextButton = Round(Glyph.Next, Loc[S.nextEpisode], () => { if (_engine.HasNext) _engine.Start(_engine.Index + 1); });
        start.Children.Add(_nextButton);
        _muteButton = Round(Glyph.Volume, Loc[S.sound], _engine.ToggleMute);
        start.Children.Add(_muteButton);
        _level.Width = 84;
        start.Children.Add(_level);
        _clock.Margin = new Thickness(10, 0, 0, 0);
        start.Children.Add(_clock);
        row.Children.Add(start);
        var end = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        _speedButton = Menu(SpeedFace(), Loc[S.speed], SpeedMenu);
        end.Children.Add(_speedButton);
        end.Children.Add(Menu(Icon(Glyph.Captions), Loc[S.subtitles], SubtitleMenu));
        end.Children.Add(Menu(Icon(Glyph.Settings), Loc[S.settings], SettingsMenu));
        if (_engine.Items.Count > 1) end.Children.Add(Round(Glyph.List, Loc[S.episodes], () => SetEpisodes(!_episodesOpen)));
        Grid.SetColumn(end, 2);
        row.Children.Add(end);
        column.Children.Add(row);
        return column;
    }

    // MARK: Buttons

    private static FontIcon Icon(string glyph, double size = 15) => Glyph.Icon(glyph, size, Colors.White);

    /// <summary>A round button on the picture: a glyph, its name under the pointer.</summary>
    private static Button Round(string glyph, string help, Action action, double size = 38, double glyphSize = 15)
    {
        var face = new Grid { Width = size, Height = size, CornerRadius = new CornerRadius(size / 2), Background = Theme.White(0) }.Springy(0.14);
        var icon = Icon(glyph, glyphSize);
        icon.HorizontalAlignment = HorizontalAlignment.Center;
        face.Children.Add(icon);
        var button = Theme.Bare(face, action);
        button.CornerRadius = new CornerRadius(size / 2);
        button.PointerEntered += (_, _) => { if (button.Background is null) face.Background = Theme.White(0.22); face.ScaleTo(1.08); };
        button.PointerExited += (_, _) => { face.Background = Theme.White(0); face.ScaleTo(1); };
        ToolTipService.SetToolTip(button, help);
        Kit.AutomationName(button, help);
        return button;
    }

    private static void SetGlyph(Button button, string glyph, string help)
    {
        if (button.Content is Grid { Children.Count: > 0 } face && face.Children[^1] is FontIcon icon) icon.Glyph = glyph;
        ToolTipService.SetToolTip(button, help);
        Kit.AutomationName(button, help);
    }

    /// <summary>A button that opens a menu of choices over the picture.</summary>
    private static Button Menu(UIElement face, string help, Func<MenuFlyout> build)
    {
        var holder = new Grid { MinWidth = 38, Height = 38, CornerRadius = new CornerRadius(19), Background = Theme.White(0) };
        holder.Children.Add(face);
        var button = Theme.Bare(holder, () => { });
        button.Click += (_, _) => build().ShowAt(button, new Microsoft.UI.Xaml.Controls.Primitives.FlyoutShowOptions { Placement = Microsoft.UI.Xaml.Controls.Primitives.FlyoutPlacementMode.Top });
        button.PointerEntered += (_, _) => holder.Background = Theme.White(0.22);
        button.PointerExited += (_, _) => holder.Background = Theme.White(0);
        ToolTipService.SetToolTip(button, help);
        Kit.AutomationName(button, help);
        return button;
    }

    /// <summary>Shows the speed itself when it is not the usual one, so a step with [ or ] is seen.</summary>
    private Grid SpeedFace()
    {
        var face = new Grid { MinWidth = 38, Height = 38, CornerRadius = new CornerRadius(19) };
        UIElement content = _engine.Speed == 1
            ? Icon(Glyph.Speed)
            : new TextBlock { Text = PlayerEngine.SpeedName(_engine.Speed), FontSize = 13, FontWeight = FontWeights.Bold, Foreground = Theme.Brush(Colors.White), Margin = new Thickness(8, 0, 8, 0), VerticalAlignment = VerticalAlignment.Center };
        face.Children.Add(content);
        return face;
    }

    // MARK: Menus

    private MenuFlyout Flyout()
    {
        var menu = new MenuFlyout();
        menu.Closed += (_, _) => Focus(FocusState.Programmatic);
        return menu;
    }

    private static RadioMenuFlyoutItem Radio(string text, string group, bool on, Action pick)
    {
        var item = new RadioMenuFlyoutItem { Text = text, GroupName = group, IsChecked = on };
        item.Click += (_, _) => pick();
        return item;
    }

    private MenuFlyout SpeedMenu()
    {
        var menu = Flyout();
        // A speed reached with the keys is listed too, so the menu still marks it.
        foreach (var speed in PlayerEngine.Speeds.Append(_engine.Speed).Distinct().Order())
            menu.Items.Add(Radio(PlayerEngine.SpeedName(speed), "speed", speed == _engine.Speed, () => _engine.SetSpeed(speed)));
        return menu;
    }

    private MenuFlyout SubtitleMenu()
    {
        var menu = Flyout();
        var current = _engine.SubtitleLanguage;
        menu.Items.Add(Radio(Loc[S.off], "sub", current == "off", () => _engine.SelectSubtitle("off", remember: true)));
        foreach (var language in _engine.SubtitleLanguages)
            menu.Items.Add(Radio(Loc[language == "fa" ? S.persian : S.english], "sub", current == language, () => _engine.SelectSubtitle(language, remember: true)));
        if (_engine.SubtitleFileName is { } name)
            menu.Items.Add(Radio(name, "sub", current == PlayerEngine.FileSubtitle, () => _engine.SelectSubtitle(PlayerEngine.FileSubtitle, remember: false)));
        // 30nama has no Persian subtitle for this one: those found on SubSource, best first.
        if (_engine.FindingSubtitles) menu.Items.Add(new MenuFlyoutItem { Text = Loc[S.findingSubtitles], IsEnabled = false });
        else if (_engine.FoundSubtitles.Count > 0)
        {
            var found = new MenuFlyoutSubItem { Text = Loc[S.fromSubSource] };
            for (var i = 0; i < _engine.FoundSubtitles.Count; i++)
            {
                var candidate = _engine.FoundSubtitles[i];
                // The same release name twice gets a number, so the two can be told apart.
                var earlier = _engine.FoundSubtitles.Take(i).Count(c => c.Name == candidate.Name);
                var tag = PlayerEngine.Found + candidate.Id;
                found.Items.Add(Radio(earlier > 0 ? $"{candidate.Name} ({Loc.Num(earlier + 1)})" : candidate.Name, "sub", current == tag, () => _engine.SelectSubtitle(tag, remember: true)));
            }
            menu.Items.Add(found);
        }
        // English put into Persian by Gemini, when there is nothing Persian to choose.
        if (_engine.CanTranslate || current == PlayerEngine.Translated)
        {
            var into = Nama.Core.Translation.Target(Loc.Lang) == Lang.Ar ? Lang.Ar.Name() : Loc[S.persian];
            var share = _engine.TranslatedShare is { } part && part < 1 ? $" · {Loc.Num((int)(part * 100))}{(Loc.Lang == Lang.En ? "%" : "٪")}" : "";
            menu.Items.Add(Radio($"{into} ({Loc[Extra.machineTranslation]}{share})", "sub", current == PlayerEngine.Translated, () => _engine.SelectSubtitle(PlayerEngine.Translated, remember: true)));
        }
        var file = new MenuFlyoutItem { Text = Loc[S.subtitleFile] };
        file.Click += async (_, _) => await ChooseSubtitleFileAsync();
        menu.Items.Add(file);
        menu.Items.Add(new MenuFlyoutSeparator());
        // The nudge in words, never a sign: a minus lands at the wrong end of a Persian line (the Android app's).
        var offset = _engine.SubtitleOffset;
        string Seconds(double s) => $"{Loc.Num(s.ToString("0.00", System.Globalization.CultureInfo.InvariantCulture))} {Loc[S.secondsShort]}";
        var now = offset == 0 ? "" : $" · {(offset > 0 ? Loc[S.subtitleLater] : Loc[S.subtitleEarlier])} {Seconds(Math.Abs(offset))}";
        var timing = new MenuFlyoutSubItem { Text = Loc[S.subtitleTiming] + now };
        void Nudge(string text, double by)
        {
            var item = new MenuFlyoutItem { Text = text };
            item.Click += (_, _) => _engine.NudgeSubtitles(by);
            timing.Items.Add(item);
        }
        Nudge($"{Loc[S.subtitleLater]} · {Seconds(0.25)} (H)", 0.25);
        Nudge($"{Loc[S.subtitleEarlier]} · {Seconds(0.25)} (G)", -0.25);
        if (offset != 0) Nudge(Loc[S.reset], 0);
        menu.Items.Add(timing);
        return menu;
    }

    private MenuFlyout SettingsMenu()
    {
        var menu = Flyout();
        if (_engine.Current.Qualities.Count > 1)
        {
            var qualities = new MenuFlyoutSubItem { Text = Loc[S.quality] };
            foreach (var option in _engine.Current.Qualities)
                qualities.Items.Add(Radio(option.IsAuto ? Loc[S.qualityAuto] : option.Label, "quality", option == _engine.Quality, () => _engine.SelectQuality(option)));
            menu.Items.Add(qualities);
        }
        if (_engine.Quality is { Mixes.Count: > 1 } quality)
        {
            var sounds = new MenuFlyoutSubItem { Text = Loc[S.audio] };
            foreach (var mix in quality.Mixes)
                sounds.Items.Add(Radio(Loc[mix switch { AudioMix.Stereo => S.audioStereo, AudioMix.Surround => S.audioSurround, _ => S.audioOriginal }], "mix", mix == _engine.Mix, () => _engine.SelectMix(mix)));
            menu.Items.Add(sounds);
        }
        var sleep = new MenuFlyoutSubItem { Text = Loc[S.sleepTimer] };
        foreach (var choice in PlayerEngine.SleepTimer.Choices)
        {
            var name = choice switch
            {
                PlayerEngine.SleepTimer.EndOfEpisode => Loc[_engine.Post.IsSeries ? S.endOfEpisode : S.endOfFilm],
                PlayerEngine.SleepTimer.Minutes m => $"{Loc.Num(m.Count)} {Loc[S.min]}",
                _ => Loc[S.off],
            };
            sleep.Items.Add(Radio(name, "sleep", choice == _engine.Sleep, () => _engine.SetSleep(choice)));
        }
        menu.Items.Add(sleep);
        menu.Items.Add(new MenuFlyoutSeparator());
        var keys = new MenuFlyoutItem { Text = $"{Loc[S.shortcuts]}  (?)" };
        keys.Click += (_, _) => SetKeys(true);
        menu.Items.Add(keys);
        return menu;
    }

    private async Task ChooseSubtitleFileAsync()
    {
        var picker = new Windows.Storage.Pickers.FileOpenPicker();
        picker.FileTypeFilter.Add(".srt");
        picker.FileTypeFilter.Add(".vtt");
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(_window));
        var wasPlaying = _engine.Playing;
        if (wasPlaying) _engine.Toggle();
        var file = await picker.PickSingleFileAsync();
        if (file is not null) _engine.LoadSubtitleFile(file.Path);
        if (wasPlaying) _engine.Toggle();
        Focus(FocusState.Programmatic);
    }

    // MARK: Offer

    /// <summary>
    /// Skip the intro, skip the recap, or go on to the next episode: the red pill. Return accepts
    /// it, and so does a click. The next episode starts by itself when the light in it has filled.
    /// </summary>
    private void ShowOffer()
    {
        var offer = _engine.Offer;
        if (offer is null) { _offer.Children.Clear(); _offerShown = null; return; }
        if (_offerShown == offer && _countdown is not null)
        {
            _countdown.Width = _offerWidth * (_engine.Countdown ?? 0);
            return;
        }
        _offerShown = offer;
        _offer.Children.Clear();
        var face = new Grid { Height = 46, CornerRadius = new CornerRadius(23) };
        face.Background = new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0, 0), EndPoint = new Windows.Foundation.Point(1, 1),
            GradientStops =
            {
                new GradientStop { Color = Theme.Hex(0xFF6A4D), Offset = 0 },
                new GradientStop { Color = Theme.Accent, Offset = 0.5 },
                new GradientStop { Color = Theme.Hex(0xD92B58), Offset = 1 },
            },
        };
        _countdown = new Border { Background = Theme.White(0.24), HorizontalAlignment = HorizontalAlignment.Left, Width = 0 };
        face.Children.Add(_countdown);
        var words = offer switch
        {
            PlayerEngine.Offers.Intro => Loc[S.skipIntro],
            PlayerEngine.Offers.Recap => Loc[S.skipRecap],
            PlayerEngine.Offers.StartOver => Loc[Extra.startOver],
            _ => Loc[S.nextEpisode],
        };
        var label = Kit.Label(words, offer switch { PlayerEngine.Offers.Next => Glyph.Play, PlayerEngine.Offers.StartOver => "\uE72C", _ => "\uEB9D" }, 16, FontWeights.Bold, Colors.White);
        label.Margin = new Thickness(22, 0, 22, 0);
        face.Children.Add(label);
        face.SizeChanged += (_, e) => { _offerWidth = e.NewSize.Width; _countdown.Width = _offerWidth * (_engine.Countdown ?? 0); };
        var button = Theme.Bare(face, _engine.AcceptOffer);
        button.Translation = new System.Numerics.Vector3(0, 0, 24);
        button.Shadow = new ThemeShadow();
        _offer.Children.Add(button);
    }

    private PlayerEngine.Offers? _offerShown;
    private Border? _countdown;
    private double _offerWidth;

    // MARK: Panels

    private void SetEpisodes(bool open)
    {
        _episodesOpen = open;
        _panelHost.Children.Clear();
        if (open) _panelHost.Children.Add(EpisodePanel());
        Focus(FocusState.Programmatic);
    }

    /// <summary>
    /// The episodes on a glass card at the side of the picture, one season at a time. The one
    /// playing is lit in the series' colour, the ones seen ticked.
    /// </summary>
    private FrameworkElement EpisodePanel()
    {
        var seasons = _engine.Items.Select(i => i.Season).Distinct().Order().ToList();
        var shownSeason = _engine.Current.Season;
        var card = new Grid { Width = 380, Margin = new Thickness(0, 110, 24, 110), HorizontalAlignment = HorizontalAlignment.Right, CornerRadius = new CornerRadius(22) };
        card.Children.Add(new Border { Background = Theme.GlassFill(), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim, CornerRadius = new CornerRadius(22) });
        var column = new Grid { Padding = new Thickness(14) , RowSpacing = 10 };
        column.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        column.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        var pills = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        var list = new StackPanel { Spacing = 6 };
        void Show(int season)
        {
            shownSeason = season;
            pills.Children.Clear();
            foreach (var s in seasons) pills.Children.Add(Kit.Pill($"{Loc[S.season]} {Loc.Num(s)}", s == shownSeason, () => Show(s)));
            list.Children.Clear();
            for (var i = 0; i < _engine.Items.Count; i++)
            {
                var item = _engine.Items[i];
                if (item.Season != season) continue;
                var index = i;
                var playing = index == _engine.Index;
                var row = new Grid { Height = 52, Padding = new Thickness(12, 0, 12, 0), CornerRadius = new CornerRadius(12), ColumnSpacing = 12, Background = playing ? Theme.Brush(_hue, 0.25) : Theme.White(0.06) };
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(32) });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                var number = Theme.Text(Loc.Num(item.EpisodeRaw ?? item.EpisodeNumber.ToString()), 16, FontWeights.ExtraBold, playing ? _hue : Theme.Text2);
                number.VerticalAlignment = VerticalAlignment.Center;
                row.Children.Add(number);
                var title = Theme.Text(item.Title(Loc.Lang) ?? $"{Loc[S.episode]} {Loc.Num(item.EpisodeNumber)}", 14, FontWeights.SemiBold);
                title.VerticalAlignment = VerticalAlignment.Center;
                Grid.SetColumn(title, 1);
                row.Children.Add(title);
                if (App.Watched.Contains(item.FileId))
                {
                    var check = Glyph.Icon("\uE930", 14, Theme.Good);
                    Grid.SetColumn(check, 2);
                    row.Children.Add(check);
                }
                var button = Theme.Bare(row, () => { _engine.Start(index); SetEpisodes(false); });
                button.HorizontalAlignment = HorizontalAlignment.Stretch;
                button.PointerEntered += (_, _) => { if (!playing) row.Background = Theme.White(0.14); };
                button.PointerExited += (_, _) => { if (!playing) row.Background = Theme.White(0.06); };
                list.Children.Add(button);
            }
        }
        Show(shownSeason);
        column.Children.Add(Kit.Sideways(new ScrollViewer { Content = pills, HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Enabled, VerticalScrollMode = ScrollMode.Disabled }));
        var scroller = new ScrollViewer { Content = list, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        Grid.SetRow(scroller, 1);
        column.Children.Add(scroller);
        card.Children.Add(column);
        // From the right in every language, like the button that opens it.
        card.FlowDirection = App.Settings.Lang.IsRtl() ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
        var host = new Grid { FlowDirection = FlowDirection.LeftToRight };
        host.Children.Add(card);
        return host;
    }

    private void SetKeys(bool open)
    {
        _keysOpen = open;
        _panelHost.Children.Clear();
        if (open) _panelHost.Children.Add(ShortcutSheet());
        Focus(FocusState.Programmatic);
    }

    /// <summary>The keyboard's way around the player, on one card.</summary>
    private FrameworkElement ShortcutSheet()
    {
        var rows = new (string Keys, string What)[]
        {
            ("Space", $"{Loc[S.play]} / {Loc[S.pause]}"), ("← →", $"{Loc[S.back10]} / {Loc[S.forward10]}"), ("↑ ↓", Loc[S.volume]),
            ("Enter", $"{Loc[S.skipIntro]} · {Loc[S.nextEpisode]}"), ("F", Loc[S.fullScreen]), ("M", Loc[S.mute]), ("T", Loc[S.alwaysOnTop]),
            ("N", Loc[S.nextEpisode]), ("E", Loc[S.episodes]), ("G / H", $"{Loc[S.subtitleEarlier]} / {Loc[S.subtitleLater]}"),
            ("[ / ]", $"{Loc[S.slower]} / {Loc[S.faster]}"), ("Esc", Loc[S.back]),
        };
        var grid = new Grid { ColumnSpacing = 24, RowSpacing = 12 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        for (var i = 0; i < rows.Length; i++)
        {
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var key = new Border
            {
                Padding = new Thickness(10, 3, 10, 3), CornerRadius = new CornerRadius(8), Background = Theme.White(0.14), HorizontalAlignment = HorizontalAlignment.Left,
                Child = new TextBlock { Text = rows[i].Keys, FontSize = 14, FontWeight = FontWeights.SemiBold, Foreground = Theme.Brush(Colors.White), FlowDirection = FlowDirection.LeftToRight },
            };
            var what = Theme.Text(rows[i].What, 15, color: Theme.Text1);
            what.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetRow(key, i);
            Grid.SetRow(what, i);
            Grid.SetColumn(what, 1);
            grid.Children.Add(key);
            grid.Children.Add(what);
        }
        var column = new StackPanel { Spacing = 18 };
        column.Children.Add(Theme.Text(Loc[S.shortcuts], 22, FontWeights.Bold));
        column.Children.Add(grid);
        var card = new Grid { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, CornerRadius = new CornerRadius(24) };
        card.Children.Add(new Border { Background = Theme.GlassFill(), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim, CornerRadius = new CornerRadius(24) });
        column.Margin = new Thickness(32, 28, 32, 28);
        card.Children.Add(column);
        var dim = new Grid { Background = Theme.Black(0.35) };
        dim.Tapped += (_, _) => SetKeys(false);
        dim.Children.Add(card);
        return dim;
    }

    private static partial class Native
    {
        /// <summary>Shows or hides the pointer over the window; the system keeps a count, so calls come in pairs.</summary>
        [LibraryImport("user32.dll")]
        public static partial int ShowCursor([MarshalAs(UnmanagedType.Bool)] bool show);
    }
}

/// <summary>The timeline: click or drag anywhere on it, and the time under the pointer shows above.</summary>
public sealed class Scrubber : Grid
{
    private readonly Action<double> _seek;
    private readonly Border _track = new() { Height = 4, CornerRadius = new CornerRadius(2), Background = Theme.White(0.24), VerticalAlignment = VerticalAlignment.Center };
    private readonly Border _fill = new() { Height = 4, CornerRadius = new CornerRadius(2), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Center };
    private readonly Canvas _marks = new() { IsHitTestVisible = false };
    private readonly Microsoft.UI.Xaml.Shapes.Ellipse _thumb = new() { Width = 14, Height = 14, Fill = Theme.Brush(Colors.White), Opacity = 0, HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Center };
    private readonly Border _label = new() { Height = 24, Padding = new Thickness(8, 0, 8, 0), CornerRadius = new CornerRadius(12), Background = Theme.Black(0.8), VerticalAlignment = VerticalAlignment.Top, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, -28, 0, 0), Opacity = 0, IsHitTestVisible = false };
    private readonly TextBlock _labelText = new() { FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = Theme.Brush(Colors.White), VerticalAlignment = VerticalAlignment.Center };
    /// <summary>The picture at the point under the pointer, over the time; there once a frame has come.</summary>
    private readonly Border _frame = new()
    {
        Width = PreviewWidth, CornerRadius = new CornerRadius(8), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.25),
        Background = Theme.Black(0.8), VerticalAlignment = VerticalAlignment.Top, HorizontalAlignment = HorizontalAlignment.Left, Opacity = 0, IsHitTestVisible = false,
    };
    private readonly Image _framePicture = new() { Stretch = Stretch.UniformToFill };
    private readonly Func<double, PreviewFrame?> _preview;
    private const double PreviewWidth = 200;
    /// <summary>The point whose frame is shown, so a late one for another point is not.</summary>
    private double? _framePoint;
    private double _time, _duration;
    private IReadOnlyList<PlayerEngine.Mark> _markList = [];
    private double? _drag, _hover;

    public Scrubber(Color hue, Action<double> seek, Func<double, PreviewFrame?> preview)
    {
        _seek = seek;
        _preview = preview;
        _frame.Child = _framePicture;
        Height = 22;
        Background = Theme.White(0);
        _fill.Background = Theme.Brush(hue);
        _label.Child = _labelText;
        Children.Add(_track);
        Children.Add(_marks);
        Children.Add(_fill);
        Children.Add(_thumb);
        Children.Add(_label);
        Children.Add(_frame);
        PointerMoved += (_, e) =>
        {
            var x = e.GetCurrentPoint(this).Position.X;
            _hover = Fraction(x);
            if (_drag is not null) _drag = _hover;
            Draw();
        };
        PointerExited += (_, _) => { _hover = null; Draw(); };
        PointerPressed += (_, e) =>
        {
            CapturePointer(e.Pointer);
            _drag = Fraction(e.GetCurrentPoint(this).Position.X);
            Draw();
        };
        PointerReleased += (_, e) =>
        {
            if (_drag is { } at && _duration > 0) _seek(at * _duration);
            _drag = null;
            ReleasePointerCapture(e.Pointer);
            Draw();
        };
        SizeChanged += (_, _) => Draw();
    }

    private double Fraction(double x) => ActualWidth > 0 ? Math.Clamp(x / ActualWidth, 0, 1) : 0;

    public void Update(double time, double duration, IReadOnlyList<PlayerEngine.Mark> marks)
    {
        _time = time;
        _duration = duration;
        _markList = marks;
        Draw();
    }

    private void Draw()
    {
        var width = Math.Max(1, ActualWidth);
        var fraction = _drag ?? (_duration > 0 ? Math.Clamp(_time / _duration, 0, 1) : 0);
        var active = _drag is not null || _hover is not null;
        var thickness = active ? 6 : 4;
        _track.Height = _fill.Height = thickness;
        _fill.Width = width * fraction;
        _thumb.Opacity = active ? 1 : 0;
        _thumb.Margin = new Thickness(width * fraction - 7, 0, 0, 0);
        _marks.Children.Clear();
        if (_duration > 0)
        {
            foreach (var mark in _markList)
            {
                var bar = new Border { Height = thickness, CornerRadius = new CornerRadius(2), Background = Theme.White(0.55), Width = Math.Max(4, width * (mark.To - mark.From) / _duration) };
                Canvas.SetLeft(bar, width * mark.From / _duration);
                Canvas.SetTop(bar, (Height - thickness) / 2);
                _marks.Children.Add(bar);
            }
        }
        if ((_drag ?? _hover) is { } at && _duration > 0)
        {
            _labelText.Text = Label(at * _duration);
            _label.Opacity = 1;
            _label.Measure(new Windows.Foundation.Size(double.PositiveInfinity, double.PositiveInfinity));
            var w = _label.DesiredSize.Width;
            _label.Margin = new Thickness(Math.Clamp(width * at - w / 2, 0, Math.Max(0, width - w)), -28, 0, 0);
        }
        else _label.Opacity = 0;
        ShowFrame(width);
    }

    /// <summary>A frame has come: shown if it is for the point still under the pointer.</summary>
    public void Arrived(double point, PreviewFrame frame)
    {
        if ((_drag ?? _hover) is { } at && _duration > 0 && SeekPreview.PointOf(at * _duration) == point) Put(point, frame);
    }

    private void ShowFrame(double width)
    {
        if ((_drag ?? _hover) is not { } at || _duration <= 0)
        {
            _frame.Opacity = 0;
            _framePoint = null;
            return;
        }
        var point = SeekPreview.PointOf(at * _duration);
        if (point != _framePoint && _preview(at * _duration) is { } frame) Put(point, frame);
        // The frame of the point before stays until the new one comes: no flicker while moving along.
        if (_framePoint is null) return;
        var height = _frame.Height;
        _frame.Margin = new Thickness(Math.Clamp(width * at - PreviewWidth / 2, 0, Math.Max(0, width - PreviewWidth)), -36 - height, 0, 0);
        _frame.Opacity = 1;
    }

    private void Put(double point, PreviewFrame frame)
    {
        var bitmap = new Microsoft.UI.Xaml.Media.Imaging.WriteableBitmap(frame.Width, frame.Height);
        using (var stream = System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions.AsStream(bitmap.PixelBuffer))
            stream.Write(frame.Pixels, 0, frame.Pixels.Length);
        bitmap.Invalidate();
        _framePicture.Source = bitmap;
        _frame.Height = Math.Round(PreviewWidth * frame.Height / frame.Width);
        _framePoint = point;
        Draw();
    }

    /// <summary>The time under the pointer, with the name of the stretch it falls in.</summary>
    private string Label(double seconds)
    {
        var loc = AppModel.Shared.Loc;
        var clock = Fmt.Clock(seconds);
        if (_markList.FirstOrDefault(m => seconds >= m.From && seconds <= m.To) is not { } mark) return clock;
        var name = loc[mark.Kind switch { PlayerEngine.Mark.Kinds.Recap => S.recap, PlayerEngine.Mark.Kinds.Intro => S.intro, _ => S.credits }];
        return $"{name} · {clock}";
    }
}

/// <summary>A small bar for the volume.</summary>
public sealed class Level : Grid
{
    private readonly Border _fill = new() { Height = 4, CornerRadius = new CornerRadius(2), Background = Theme.Brush(Colors.White), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Center };
    private double _value;

    public Level(Action<double> set)
    {
        Height = 22;
        Background = Theme.White(0);
        Children.Add(new Border { Height = 4, CornerRadius = new CornerRadius(2), Background = Theme.White(0.24), VerticalAlignment = VerticalAlignment.Center });
        Children.Add(_fill);
        void At(PointerRoutedEventArgs e) => set(ActualWidth > 0 ? Math.Clamp(e.GetCurrentPoint(this).Position.X / ActualWidth, 0, 1) : 0);
        PointerPressed += (_, e) => { CapturePointer(e.Pointer); At(e); };
        PointerMoved += (_, e) => { if (e.Pointer.IsInContact) At(e); };
        PointerReleased += (_, e) => ReleasePointerCapture(e.Pointer);
        SizeChanged += (_, _) => _fill.Width = ActualWidth * _value;
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(this, AppModel.Shared.Loc[S.volume]);
    }

    public void Update(double value)
    {
        _value = value;
        _fill.Width = ActualWidth * value;
    }
}
