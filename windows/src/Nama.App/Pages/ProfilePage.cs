using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;
using Color = Windows.UI.Color;

namespace Nama.App.Pages;

/// <summary>
/// The user's own page: who they are, what their stars say about their taste, the lists they keep
/// on 30nama, and what all that suggests they watch next.
/// </summary>
public sealed class ProfilePage : Grid
{
    private static AppModel App => AppModel.Shared;
    private static readonly Color Gold = Theme.Hex(0xF5C518);

    private readonly Nav _nav;
    private readonly Stage _stage = new(dim: 0.5);
    private readonly StackPanel _column = new() { Spacing = 34, Padding = new Thickness(0, 110, 0, 60) };
    private List<Post> _picks = [], _starters = [], _watchlist = [], _favorites = [], _upcoming = [];
    private Dictionary<string, int> _listSizes = [];
    private Grid _quoteSlot = new();
    /// <summary>The name the member gave the site; the app API sends only the username.</summary>
    private SiteProfile? _siteProfile;
    /// <summary>A line from a film, and the film on 30nama when it was found.</summary>
    private MovieLine? _line;
    private Post? _filmOnSite;
    /// <summary>The line of this opening of the app, the name it was picked for, and its film on 30nama.</summary>
    private static (string? Name, MovieLine Line, Post? Post)? _kept;
    private readonly TaskCompletionSource _lineReady = new(), _listsReady = new();

    /// <summary>
    /// One row of the page, its place kept from the start. It comes in when its data has come and
    /// every row above it is in, so nothing is ever put above what is already seen: drawing the
    /// page again for each answer moved what was there down under what came.
    /// </summary>
    private sealed class Row(Func<FrameworkElement?> build)
    {
        public readonly Grid Slot = new() { Visibility = Visibility.Collapsed };
        public readonly Func<FrameworkElement?> Build = build;
        public bool Ready, Shown;
    }

    private readonly Row _headerRow, _tasteRow, _upcomingRow, _picksRow, _watchRow, _favRow, _endRow;
    private readonly List<Row> _rows;
    private readonly FrameworkElement _wait = Kit.Spinner();

    public ProfilePage(Nav nav)
    {
        _nav = nav;
        _headerRow = new(Header);
        _tasteRow = new(() => App.Ratings.IsEmpty ? NoRatings() : Insights());
        _upcomingRow = new(() => _upcoming.Count == 0 ? null
            : Kit.Shelf(App.Loc[S.upcoming], _upcoming.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p), note: p.NextEpisodeLabel(App.Loc.Lang)))));
        _picksRow = new(() => App.Ratings.IsEmpty ? null : Shelf(App.Loc[S.forYou], _picks));
        _watchRow = new(() => Shelf(App.Loc[S.watchlist], _watchlist));
        _favRow = new(() => Shelf(App.Loc[S.favorites], _favorites));
        _endRow = new(() => App.Ratings.IsEmpty ? Shelf(App.Loc[S.startWithThese], _starters) : Rated());
        _rows = [_headerRow, _tasteRow, _upcomingRow, _picksRow, _watchRow, _favRow, _endRow];
        foreach (var row in new[] { _headerRow, _tasteRow }) row.Slot.Padding = new Thickness(Theme.Edge, 0, Theme.Edge, 0);
        foreach (var row in _rows) _column.Children.Add(row.Slot);
        _wait.HorizontalAlignment = HorizontalAlignment.Center;
        _column.Children.Add(_wait);

        Children.Add(Kit.Ambient());
        Children.Add(_stage);
        Children.Add(new ScrollViewer { Content = _column, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        App.Changed += OnChanged;
        Unloaded += (_, _) => App.Changed -= OnChanged;
        // What is kept here, not asked of the server, is in already.
        _tasteRow.Ready = true;
        _endRow.Ready = !App.Ratings.IsEmpty;
        _ = RefreshAsync();
        _ = LoadListsAsync();
        _ = LoadNameAsync();
        _ = HeaderAsync();
    }

    /// <summary>
    /// The card, once what is in it has come: the counts of the lists, and the name and its line.
    /// The name is asked quietly, behind the page's own requests: it is waited for only a little.
    /// </summary>
    private async Task HeaderAsync()
    {
        var named = Task.WhenAny(_lineReady.Task, Task.Delay(1500));
        await Task.WhenAny(Task.WhenAll(_listsReady.Task, named), Task.Delay(4000));
        Update(_headerRow);
    }

    /// <summary>A row's data has come (again): drawn in its place if shown, else let in when its turn comes.</summary>
    private void Update(Row row)
    {
        if (row.Shown) Fill(row);
        else
        {
            row.Ready = true;
            Flow();
        }
    }

    /// <summary>Brings in, from the top, every row that is ready and has nothing waiting above it.</summary>
    private void Flow()
    {
        foreach (var row in _rows)
        {
            if (!row.Ready) break;
            if (row.Shown) continue;
            row.Shown = true;
            Fill(row);
            if (row.Slot.Visibility != Visibility.Visible) continue;
            var slot = row.Slot;
            slot.Opacity = 0;
            slot.Translation = new System.Numerics.Vector3(0, 18, 0);
            slot.Springy(0.35);
            DispatcherQueue.TryEnqueue(() => { slot.Opacity = 1; slot.Translation = System.Numerics.Vector3.Zero; });
        }
        _wait.Visibility = _rows.All(r => r.Shown) ? Visibility.Collapsed : Visibility.Visible;
    }

    private static void Fill(Row row)
    {
        row.Slot.Children.Clear();
        var content = row.Build();
        if (content is not null) row.Slot.Children.Add(content);
        row.Slot.Visibility = content is null ? Visibility.Collapsed : Visibility.Visible;
    }

    private void OnChanged(string what)
    {
        if (what == "ratings")
        {
            Update(_tasteRow);
            _ = RefreshAsync();
        }
        if (what is "lists" or "user") _ = LoadListsAsync();
        if (what == "user") _ = LoadNameAsync();
        if (what is "notifications" or "settings") RenderHeader();
    }

    /// <summary>
    /// The member's name from the site, quietly, then the line from a film: it waits for the name,
    /// which goes into it, and is the same all the time the app is open.
    /// </summary>
    private async Task LoadNameAsync()
    {
        _siteProfile = null;
        if (App.User?.Name is { } username && App.Api.HasSite)
        {
            try { _siteProfile = SiteProfile.Parse(await App.Api.SiteAsync(SiteQuery.Profile(username), background: true)); }
            // A private profile on the site gives no name: the username, or the one written in Settings, stands.
            catch (Exception) { }
        }
        RenderHeader();
        await PickLineAsync();
    }

    private async Task PickLineAsync()
    {
        var (first, family) = Names();
        // Once per opening of the app, unless the name has only now come.
        if (_kept is { } kept && kept.Name == first)
        {
            (_line, _filmOnSite) = (kept.Line, kept.Post);
            _lineReady.TrySetResult();
            RenderHeader();
            return;
        }
        var picked = MovieLines.Pick(first, family, Random.Shared);
        (_line, _filmOnSite) = (picked, null);
        _kept = (first, picked, null);
        _lineReady.TrySetResult();
        RenderHeader();
        // Quietly, after the page's own requests: whether 30nama has the film.
        foreach (var query in picked.SearchQueries)
        {
            try
            {
                var found = new PostPage(await App.Api.GetAsync("search", new Dictionary<string, string> { ["query"] = query, ["streamonly"] = "0", ["page"] = "1" }, background: true));
                if (picked.Match(found.Posts) is not { } post) continue;
                if (_line != picked) return;
                _filmOnSite = post;
                _kept = (first, picked, post);
                if (_headerRow.Shown) RenderQuote();
                return;
            }
            catch (Exception) { }
        }
    }

    /// <summary>The name written in Settings, when there is one and someone is signed in.</summary>
    private static string? OwnName => App.User is null ? null : App.Settings.DisplayName?.Trim() is { Length: > 0 } name ? name : null;

    /// <summary>The name to show: the one given the site, or failing that (a private profile) the one given here, or failing that the username.</summary>
    private string? DisplayName => _siteProfile?.FullName ?? OwnName ?? App.User?.Name;

    /// <summary>First and family name for the line from a film: the site's, or the one written here. Never the username.</summary>
    private (string? First, string? Family) Names()
    {
        if (_siteProfile is { FullName: not null } site) return (site.FirstName, site.LastName);
        if (OwnName is not { } own) return (null, null);
        var words = own.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        return (words[0], words.Length > 1 ? string.Join(" ", words[1..]) : null);
    }

    /// <summary>The title whose artwork washes the page: something the user chose, else something they rated.</summary>
    private Post? Backdrop => _favorites.FirstOrDefault() ?? _watchlist.FirstOrDefault() ?? App.Ratings.NewestFirst.FirstOrDefault()?.ToPost();

    private static FrameworkElement NoRatings()
    {
        var empty = new StackPanel { Spacing = 8 };
        empty.Children.Add(Theme.Text(App.Loc[S.noRatingsYet], 22, FontWeights.Bold));
        empty.Children.Add(Theme.Text(App.Loc[S.rateHint], 16, color: Theme.Text2, lines: 2));
        return empty;
    }

    private FrameworkElement? Shelf(string title, List<Post> posts) =>
        posts.Count == 0 ? null : Kit.Shelf(title, posts.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p))));

    // MARK: Header

    private void RenderHeader()
    {
        if (_headerRow.Shown) Fill(_headerRow);
    }

    /// <summary>Only the line, when the film is found on 30nama: the rest of the card (the picture) is not drawn again.</summary>
    private void RenderQuote()
    {
        _quoteSlot.Children.Clear();
        if (_line is not { } line) return;
        var quote = Quote(line, _filmOnSite);
        quote.Margin = new Thickness(0, 10, 0, 0);
        _quoteSlot.Children.Add(quote);
    }

    /// <summary>
    /// One card instead of a stack of lines, of the top bar's glass, so who it is stands apart from
    /// the rows below: the picture, and beside it the name, the username and the line from a film;
    /// across from them the counts as figures, with the buttons under them. As the Mac's profile header.
    /// </summary>
    private FrameworkElement Header()
    {
        var loc = App.Loc;
        var user = App.User;
        var grid = new Grid { ColumnSpacing = 22 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var avatar = Avatar();
        avatar.VerticalAlignment = VerticalAlignment.Center;
        grid.Children.Add(avatar);

        var column = new StackPanel { Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        var name = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        name.Children.Add(Theme.Text(DisplayName ?? loc[S.guestAccount], 34, FontWeights.Black));
        if (user?.Type is { } type)
        {
            var badge = Theme.Text(loc.AccountType(type), 13, FontWeights.Bold, Theme.FocusInk);
            badge.VerticalAlignment = VerticalAlignment.Center;
            name.Children.Add(new Border
            {
                Height = 26, Padding = new Thickness(12, 0, 12, 0), CornerRadius = new CornerRadius(13), VerticalAlignment = VerticalAlignment.Center, Child = badge,
                Background = Vertical(Theme.Hex(0xFFE27A), Gold),
            });
        }
        column.Children.Add(name);
        if (user is null)
        {
            // A guest otherwise had no way in from here but the settings.
            var hint = Theme.Text(loc[S.signInHint], 14, color: Theme.Text2, lines: 2);
            hint.MaxWidth = 460;
            hint.HorizontalAlignment = HorizontalAlignment.Left;
            column.Children.Add(hint);
        }
        else if (DisplayName != user.Name && user.Name is { } username)
        {
            // The username, under a name given instead of it.
            column.Children.Add(Theme.Text(username, 13, FontWeights.Medium, Theme.Text3));
        }
        _quoteSlot = new Grid();
        column.Children.Add(_quoteSlot);
        RenderQuote();
        Grid.SetColumn(column, 1);
        grid.Children.Add(column);

        var side = new StackPanel { Spacing = 18, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Right };
        if (Figures() is { } figures) side.Children.Add(figures);
        side.Children.Add(Buttons());
        Grid.SetColumn(side, 2);
        grid.Children.Add(side);

        return new Border
        {
            Padding = new Thickness(28, 24, 28, 24),
            CornerRadius = new CornerRadius(28),
            Background = Theme.GlassFill(),
            BorderThickness = new Thickness(1),
            BorderBrush = Theme.GlassRim,
            Child = grid,
        };
    }

    /// <summary>
    /// The counts as figures with a word under each: the lists kept on 30nama, what was rated and
    /// how generously, and the days of the plan left (its last day under the pointer).
    /// </summary>
    private FrameworkElement? Figures()
    {
        var loc = App.Loc;
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 28, HorizontalAlignment = HorizontalAlignment.Right };
        void Figure(string value, string label, bool star = false, string? hint = null)
        {
            var item = new StackPanel { Spacing = 2 };
            var top = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 5, HorizontalAlignment = HorizontalAlignment.Center };
            if (star) top.Children.Add(Centered(Glyph.Icon(Glyph.StarFill, 13, Gold)));
            top.Children.Add(Centered(Theme.Text(value, 26, FontWeights.Black)));
            item.Children.Add(top);
            var word = Theme.Text(label, 12, FontWeights.Medium, Theme.Text3);
            word.HorizontalAlignment = HorizontalAlignment.Center;
            item.Children.Add(word);
            if (hint is not null) ToolTipService.SetToolTip(item, hint);
            row.Children.Add(item);
        }
        if (_watchlist.Count > 0) Figure(loc.Num(_listSizes.GetValueOrDefault("watchlist", _watchlist.Count)), loc[S.watchlist]);
        if (_favorites.Count > 0) Figure(loc.Num(_listSizes.GetValueOrDefault("favorite", _favorites.Count)), loc[S.favorites]);
        if (!App.Ratings.IsEmpty)
        {
            Figure(loc.Num(App.Ratings.Count), loc[S.ratedCount]);
            if (App.Ratings.Average is { } average) Figure(loc.Num(Fmt.Score(average)), loc[S.averageRating], star: true);
        }
        if (App.User is { } user && user.DaysLeft() is { } days) Figure(loc.Num(days), loc[S.subscriptionDays], hint: loc.SubscriptionLine(user));
        return row.Children.Count > 0 ? row : null;
    }

    private FrameworkElement Buttons()
    {
        var loc = App.Loc;
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10, HorizontalAlignment = HorizontalAlignment.Right };
        if (App.User is null)
        {
            buttons.Children.Add(Kit.Primary(loc[S.signIn], Glyph.Person, App.RequestSignIn));
            return buttons;
        }
        if (!App.Ratings.IsEmpty) buttons.Children.Add(Kit.Glass(loc[S.exportRatings], "\uE898", ExportRatings, height: 34));
        // The bar's bell shows only while something is new; the older ones are here.
        if (App.Notifications is { Items.Count: > 0 })
        {
            Button? bell = null;
            bell = Kit.Glass(loc[S.notifications], "\uEA8F", () =>
            {
                var flyout = new Flyout
                {
                    Placement = Microsoft.UI.Xaml.Controls.Primitives.FlyoutPlacementMode.Bottom,
                    FlyoutPresenterStyle = new Style(typeof(FlyoutPresenter))
                    {
                        Setters =
                        {
                            new Setter(Control.BackgroundProperty, Theme.White(0)),
                            new Setter(Control.BorderThicknessProperty, new Thickness(0)),
                            new Setter(Control.PaddingProperty, new Thickness(0)),
                            new Setter(FrameworkElement.MaxWidthProperty, 600.0),
                        },
                    },
                };
                flyout.Content = new NotificationsPanel(_nav, flyout.Hide) { FlowDirection = FlowDirection };
                flyout.ShowAt(bell!);
            }, height: 34);
            buttons.Children.Add(bell);
        }
        buttons.Children.Add(Kit.Glass(loc[S.renewSubscription], "\uE8C7", () => _ = Windows.System.Launcher.LaunchUriAsync(Pricing), height: 34));
        buttons.Children.Add(Kit.Glass(loc[S.signOut], "\uF3B1", ConfirmSignOut, height: 34));
        return buttons;
    }

    /// <summary>30nama's own page for buying or renewing a plan.</summary>
    private static readonly Uri Pricing = new("https://30nama.com/pricing");

    /// <summary>
    /// A line from a film, in the film's own English, with their first name where a name or the word
    /// for them was; under it the film, its year and its director, which open the film's page when
    /// 30nama has it. Never the username: a line said to an account name means nothing. English in a
    /// Persian or Arabic page: written left to right, kept to the same edge as the name above it.
    /// </summary>
    private FrameworkElement Quote(MovieLine line, Post? post)
    {
        var tint = Backdrop is { } shown ? PosterColours.Of(shown) : Theme.Accent;
        var serif = new FontFamily("Georgia,Cambria,Times New Roman");
        // "Left" is the page's leading edge: the parent is laid out in the interface's direction.
        var block = new Grid { MaxWidth = 620, FlowDirection = FlowDirection.LeftToRight, HorizontalAlignment = HorizontalAlignment.Left };
        var mark = new TextBlock { Text = "\u201C", FontFamily = serif, FontSize = 50, FontWeight = FontWeights.Bold, Foreground = new SolidColorBrush(tint.WithAlpha(0.75)), Margin = new Thickness(-2, -14, 0, 0), IsHitTestVisible = false };
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetAccessibilityView(mark, Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Raw);
        block.Children.Add(mark);
        var words = new StackPanel { Spacing = 8, Margin = new Thickness(26, 0, 0, 0) };
        words.Children.Add(new TextBlock
        {
            Text = line.Text, FontFamily = serif, FontSize = 19, FontStyle = Windows.UI.Text.FontStyle.Italic,
            Foreground = new SolidColorBrush(Theme.Text1.WithAlpha(0.92)), TextWrapping = TextWrapping.WrapWholeWords, MaxLines = 2,
            TextTrimming = TextTrimming.CharacterEllipsis, IsTextScaleFactorEnabled = false,
        });
        var credit = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        var rule = new Border { Width = 16, Height = 2, CornerRadius = new CornerRadius(1), Background = new SolidColorBrush(tint), VerticalAlignment = VerticalAlignment.Center };
        credit.Children.Add(rule);
        var signature = new TextBlock
        {
            Text = $"{line.Film.ToUpperInvariant()} · {line.Year} · {string.Join(", ", line.Directors)}",
            FontSize = 11, FontWeight = FontWeights.SemiBold, CharacterSpacing = 80, Foreground = Theme.Brush(Theme.Text3),
            TextTrimming = TextTrimming.CharacterEllipsis, VerticalAlignment = VerticalAlignment.Center, IsTextScaleFactorEnabled = false,
        };
        credit.Children.Add(signature);
        // In a button whether or not the film is on 30nama: the line keeps its height when the film is found.
        var open = Theme.Bare(credit, () => { if (post is not null) _nav.Open(post); });
        open.HorizontalAlignment = HorizontalAlignment.Left;
        if (post is not null)
        {
            Kit.AutomationName(open, line.Film);
            open.PointerEntered += (_, _) => { signature.Foreground = new SolidColorBrush(tint); signature.TextDecorations = Windows.UI.Text.TextDecorations.Underline; };
            open.PointerExited += (_, _) => { signature.Foreground = Theme.Brush(Theme.Text3); signature.TextDecorations = Windows.UI.Text.TextDecorations.None; };
        }
        else (open.IsTabStop, open.IsHitTestVisible) = (false, false);
        words.Children.Add(open);
        block.Children.Add(words);
        return block;
    }

    private static FrameworkElement Centered(FrameworkElement element)
    {
        element.VerticalAlignment = VerticalAlignment.Center;
        return element;
    }

    private async void ExportRatings()
    {
        var picker = new Windows.Storage.Pickers.FileSavePicker { SuggestedFileName = "30nama-ratings" };
        picker.FileTypeChoices.Add("CSV", [".csv"]);
        WinRT.Interop.InitializeWithWindow.Initialize(picker, WinRT.Interop.WindowNative.GetWindowHandle(MainWindow.Shared));
        if (await picker.PickSaveFileAsync() is not { } file) return;
        try
        {
            await File.WriteAllTextAsync(file.Path, App.Ratings.Csv(), new System.Text.UTF8Encoding(false));
            App.ShowToast(App.Loc[S.exportDone]);
        }
        catch (Exception) { App.ShowToast(App.Loc[S.error]); }
    }

    private async void ConfirmSignOut()
    {
        var loc = App.Loc;
        var dialog = new ContentDialog
        {
            Title = loc[S.signOutTitle], Content = loc[S.signOutBody], PrimaryButtonText = loc[S.signOut], CloseButtonText = loc[S.cancel],
            DefaultButton = ContentDialogButton.Close, XamlRoot = XamlRoot, FlowDirection = loc.Lang.IsRtl() ? FlowDirection.RightToLeft : FlowDirection.LeftToRight,
            RequestedTheme = ElementTheme.Dark,
        };
        if (await dialog.ShowAsync() == ContentDialogResult.Primary) App.SignOut();
    }

    private static LinearGradientBrush Vertical(Color top, Color bottom) => new()
    {
        StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
        GradientStops = { new GradientStop { Color = top, Offset = 0 }, new GradientStop { Color = bottom, Offset = 1 } },
    };

    private static FrameworkElement Avatar()
    {
        var face = new Grid { Width = 120, Height = 120, CornerRadius = new CornerRadius(60) };
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
        // Initials of the signed-in name, or a person glyph for a guest.
        var words = App.User?.Name?.Split(' ', StringSplitOptions.RemoveEmptyEntries) ?? [];
        var initials = string.Concat(new[] { words.FirstOrDefault()?[..1], words.Length > 1 ? words[^1][..1] : null }.OfType<string>()).ToUpperInvariant();
        FrameworkElement mark = initials.Length > 0 ? Theme.Text(initials, 42, FontWeights.ExtraBold, Colors.White) : Glyph.Icon(Glyph.Person, 46, Colors.White);
        mark.HorizontalAlignment = HorizontalAlignment.Center;
        mark.VerticalAlignment = VerticalAlignment.Center;
        mark.FlowDirection = FlowDirection.LeftToRight;
        face.Children.Add(mark);
        if (App.User?.Avatar is { } url) face.Children.Add(Images.Remote(url, null, 180));
        face.Children.Add(new Border { CornerRadius = new CornerRadius(60), BorderThickness = new Thickness(2), BorderBrush = Vertical(Theme.Hex(0xFFFFFF, 0.7), Theme.Hex(0xFFFFFF, 0.1)) });
        return face;
    }

    // MARK: Taste

    /// <summary>What the stars add up to: the genres that do best, and how generous the user is with them.</summary>
    private FrameworkElement Insights()
    {
        var loc = App.Loc;
        var row = new Grid { ColumnSpacing = 18 };
        row.ColumnDefinitions.Add(new ColumnDefinition());
        row.ColumnDefinitions.Add(new ColumnDefinition());

        var genres = new StackPanel { Spacing = 12 };
        foreach (var (name, strength) in App.Ratings.FavoriteGenreNames())
        {
            var line = new Grid { ColumnSpacing = 14 };
            line.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(120) });
            line.ColumnDefinitions.Add(new ColumnDefinition());
            line.Children.Add(Theme.Text(name, 15, FontWeights.SemiBold));
            var track = new Grid { Height = 8, CornerRadius = new CornerRadius(4), Background = Theme.White(0.1), VerticalAlignment = VerticalAlignment.Center };
            var bar = new Border
            {
                CornerRadius = new CornerRadius(4), HorizontalAlignment = HorizontalAlignment.Left,
                Background = new LinearGradientBrush
                {
                    StartPoint = new Windows.Foundation.Point(0, 0.5), EndPoint = new Windows.Foundation.Point(1, 0.5),
                    GradientStops = { new GradientStop { Color = Theme.Hex(0xFF6A4D), Offset = 0 }, new GradientStop { Color = Theme.Accent, Offset = 1 } },
                },
            };
            track.SizeChanged += (_, e) => bar.Width = Math.Max(8, e.NewSize.Width * strength);
            track.Children.Add(bar);
            Grid.SetColumn(track, 1);
            line.Children.Add(track);
            genres.Children.Add(line);
        }
        row.Children.Add(Panel(loc[S.yourTaste], genres));

        var spread = App.Ratings.Spread();
        var most = Math.Max(1, spread.Max());
        var bars = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 16, VerticalAlignment = VerticalAlignment.Bottom };
        for (var i = 0; i < spread.Length; i++)
        {
            var count = spread[i];
            var column = new StackPanel { Spacing = 6, VerticalAlignment = VerticalAlignment.Bottom };
            var number = Theme.Text(count > 0 ? loc.Num(count) : " ", 13, FontWeights.SemiBold, Theme.Text2);
            number.HorizontalAlignment = HorizontalAlignment.Center;
            column.Children.Add(number);
            column.Children.Add(new Border
            {
                Width = 38, Height = Math.Max(6, 56.0 * count / most), CornerRadius = new CornerRadius(6),
                Background = count > 0 ? Vertical(Theme.Hex(0xFFE27A), Gold) : Theme.White(0.1),
            });
            var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 3, HorizontalAlignment = HorizontalAlignment.Center };
            label.Children.Add(Theme.Text(loc.Num(i + 1), 13, FontWeights.SemiBold, Theme.Text3));
            var star = Glyph.Icon(Glyph.StarFill, 9, Theme.Text3);
            star.VerticalAlignment = VerticalAlignment.Center;
            label.Children.Add(star);
            column.Children.Add(label);
            bars.Children.Add(column);
        }
        var spreadPanel = Panel(loc[S.howYouRate], bars);
        Grid.SetColumn(spreadPanel, 1);
        row.Children.Add(spreadPanel);
        return row;
    }

    private static FrameworkElement Panel(string title, FrameworkElement content)
    {
        var card = new Grid { Height = 190 };
        card.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(18), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        var column = new StackPanel { Spacing = 14, Margin = new Thickness(20) };
        column.Children.Add(Theme.Text(title, 15, FontWeights.SemiBold, Theme.Text3));
        column.Children.Add(content);
        card.Children.Add(column);
        return card;
    }

    private FrameworkElement Rated()
    {
        var column = new StackPanel { Spacing = 16, Padding = new Thickness(Theme.Edge, 0, Theme.Edge, 0) };
        column.Children.Add(Theme.Text(App.Loc[S.yourRating], 20, FontWeights.Bold));
        var wall = new WrapRow(20, unmirrored: true);
        foreach (var rating in App.Ratings.NewestFirst)
        {
            if (rating.ToPost() is { } post) wall.Add(Kit.Poster(post, () => _nav.Open(post), stars: rating.Stars));
        }
        column.Children.Add(wall);
        return column;
    }

    // MARK: Data

    private async Task RefreshAsync()
    {
        HomeFeed feed;
        try { feed = await App.HomeAsync(); }
        catch (Exception)
        {
            Update(_picksRow);
            Update(_endRow);
            return;
        }
        // The same title can sit in two sections of the feed; it is wanted once.
        var seen = new HashSet<int>();
        var pool = feed.Sections.SelectMany(s => s.Posts).Where(p => seen.Add(p.Id)).ToList();
        if (App.Ratings.IsEmpty)
            // Nothing to go on yet: offer the best-regarded titles to kick things off.
            _starters = pool.Where(p => !p.ComingSoon && p.ImdbScore is not null).OrderByDescending(p => p.ImdbScore).Take(14).ToList();
        else _picks = App.Ratings.Recommend(pool, 16);
        Update(_picksRow);
        Update(_endRow);
    }

    /// <summary>The lists the user keeps on 30nama itself. A guest has none.</summary>
    private async Task LoadListsAsync()
    {
        var watch = App.MyListAsync("watchlist");
        var liked = App.MyListAsync("favorite");
        var sizes = App.MyListSizesAsync();
        _watchlist = await watch;
        _favorites = await liked;
        _listSizes = await sizes;
        _stage.Show(Backdrop);
        _listsReady.TrySetResult();
        RenderHeader();
        Update(_watchRow);
        Update(_favRow);
        _upcoming = await App.UpcomingAsync();
        Update(_upcomingRow);
    }
}
