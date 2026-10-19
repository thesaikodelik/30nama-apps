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

    public ProfilePage(Nav nav)
    {
        _nav = nav;
        Children.Add(Kit.Ambient());
        Children.Add(_stage);
        Children.Add(new ScrollViewer { Content = _column, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        Render();
        App.Changed += OnChanged;
        Unloaded += (_, _) => App.Changed -= OnChanged;
        _ = RefreshAsync();
        _ = LoadListsAsync();
    }

    private void OnChanged(string what)
    {
        if (what == "ratings") _ = RefreshAsync();
        if (what is "lists" or "user") _ = LoadListsAsync();
    }

    /// <summary>The title whose artwork washes the page: something the user chose, else something they rated.</summary>
    private Post? Backdrop => _favorites.FirstOrDefault() ?? _watchlist.FirstOrDefault() ?? App.Ratings.NewestFirst.FirstOrDefault()?.ToPost();

    private void Render()
    {
        var loc = App.Loc;
        _stage.Show(Backdrop);
        _column.Children.Clear();
        var top = new StackPanel { Spacing = 30, Padding = new Thickness(Theme.Edge, 0, Theme.Edge, 0) };
        top.Children.Add(Header());
        if (App.Ratings.IsEmpty)
        {
            var empty = new StackPanel { Spacing = 8 };
            empty.Children.Add(Theme.Text(loc[S.noRatingsYet], 22, FontWeights.Bold));
            empty.Children.Add(Theme.Text(loc[S.rateHint], 16, color: Theme.Text2, lines: 2));
            top.Children.Add(empty);
        }
        else top.Children.Add(Insights());
        _column.Children.Add(top);
        if (_upcoming.Count > 0)
            _column.Children.Add(Kit.Shelf(loc[S.upcoming], _upcoming.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p), note: p.NextEpisodeLabel(loc.Lang)))));
        Shelf(loc[S.forYou], _picks);
        Shelf(loc[S.watchlist], _watchlist);
        Shelf(loc[S.favorites], _favorites);
        if (App.Ratings.IsEmpty) Shelf(loc[S.startWithThese], _starters);
        else _column.Children.Add(Rated());
    }

    private void Shelf(string title, List<Post> posts)
    {
        if (posts.Count > 0) _column.Children.Add(Kit.Shelf(title, posts.Select(p => (FrameworkElement)Kit.Poster(p, () => _nav.Open(p)))));
    }

    // MARK: Header

    /// <summary>
    /// One tidy column beside the picture: the name with the subscription's badge, the date it runs
    /// to (a guest's way in, for a guest), and the counts on one line. The account's buttons are at
    /// the far end. As the Mac's profile header.
    /// </summary>
    private FrameworkElement Header()
    {
        var loc = App.Loc;
        var user = App.User;
        var grid = new Grid { ColumnSpacing = 24 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.Children.Add(Avatar());
        var column = new StackPanel { Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        var name = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        name.Children.Add(Theme.Text(user?.Name ?? loc[S.guestAccount], 36, FontWeights.Black));
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
        if (user is not null && loc.SubscriptionLine(user) is { } line) column.Children.Add(Theme.Text(line, 13, color: Theme.Text3));
        // A guest otherwise had no way in from here but the settings.
        else if (user is null)
        {
            var hint = Theme.Text(loc[S.signInHint], 14, color: Theme.Text2, lines: 2);
            hint.MaxWidth = 460;
            hint.HorizontalAlignment = HorizontalAlignment.Left;
            column.Children.Add(hint);
        }
        var stats = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14, Margin = new Thickness(0, 4, 0, 0) };
        void Stat(string value, string label, bool star = false)
        {
            if (stats.Children.Count > 0) stats.Children.Add(Centered(Theme.Text("·", 15, color: Theme.Text3)));
            var item = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
            if (star) item.Children.Add(Centered(Glyph.Icon(Glyph.StarFill, 12, Gold)));
            item.Children.Add(Centered(Theme.Text(value, 17, FontWeights.Black)));
            item.Children.Add(Centered(Theme.Text(label, 13, FontWeights.Medium, Theme.Text3)));
            stats.Children.Add(item);
        }
        if (!App.Ratings.IsEmpty)
        {
            Stat(loc.Num(App.Ratings.Count), loc[S.ratedCount]);
            if (App.Ratings.Average is { } average) Stat(loc.Num(Fmt.Score(average)), loc[S.averageRating], star: true);
        }
        if (_watchlist.Count > 0) Stat(loc.Num(_listSizes.GetValueOrDefault("watchlist", _watchlist.Count)), loc[S.watchlist]);
        if (_favorites.Count > 0) Stat(loc.Num(_listSizes.GetValueOrDefault("favorite", _favorites.Count)), loc[S.favorites]);
        if (stats.Children.Count > 0) column.Children.Add(stats);
        Grid.SetColumn(column, 1);
        grid.Children.Add(column);
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10, VerticalAlignment = VerticalAlignment.Center };
        if (user is null) buttons.Children.Add(Kit.Primary(loc[S.signIn], Glyph.Person, App.RequestSignIn));
        else
        {
            if (!App.Ratings.IsEmpty) buttons.Children.Add(Kit.Glass(loc[S.exportRatings], "", ExportRatings, height: 34));
            buttons.Children.Add(Kit.Glass(loc[S.signOut], "", ConfirmSignOut, height: 34));
        }
        Grid.SetColumn(buttons, 2);
        grid.Children.Add(buttons);
        return grid;
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
        catch (Exception) { Render(); return; }
        // The same title can sit in two sections of the feed; it is wanted once.
        var seen = new HashSet<int>();
        var pool = feed.Sections.SelectMany(s => s.Posts).Where(p => seen.Add(p.Id)).ToList();
        if (App.Ratings.IsEmpty)
            // Nothing to go on yet: offer the best-regarded titles to kick things off.
            _starters = pool.Where(p => !p.ComingSoon && p.ImdbScore is not null).OrderByDescending(p => p.ImdbScore).Take(14).ToList();
        else _picks = App.Ratings.Recommend(pool, 16);
        Render();
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
        Render();
        _upcoming = await App.UpcomingAsync();
        if (_upcoming.Count > 0) Render();
    }
}
