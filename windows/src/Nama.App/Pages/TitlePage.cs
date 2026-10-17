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

/// <summary>One title: its artwork behind, what it is, the user's own stars, its episodes and the people in it.</summary>
public sealed class TitlePage : Grid
{
    private static AppModel App => AppModel.Shared;
    private const double PosterWidth = 240, PosterHeight = 360, Top = 130;

    /// <summary>What the list already knew about the title, shown at once while the rest loads.</summary>
    private readonly Post _preview;
    private readonly Nav _nav;
    private readonly bool _autoplay;
    private readonly Stage _stage = new(dim: 0.3);
    private readonly StackPanel _column = new() { Spacing = 40, Padding = new Thickness(Theme.Edge, Top, Theme.Edge, 60) };
    private Post? _loaded;
    private StreamResult? _stream;
    /// <summary>The stream is being asked for; the play button waits on it.</summary>
    private bool _streamPending;
    /// <summary>Why 30nama would not hand over the stream, in its own words.</summary>
    private string? _refusal;
    private int _season = 1;

    public Post Post => _loaded ?? _preview;
    private Color Hue => Post.Tint();

    public TitlePage(Post preview, Nav nav, bool autoplay)
    {
        _preview = preview;
        _nav = nav;
        _autoplay = autoplay;
        Background = Theme.Brush(Theme.Bg0);
        Children.Add(_stage);
        Children.Add(new ScrollViewer { Content = _column, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        _stage.Show(Post);
        Render();
        App.Changed += OnChanged;
        TmdbStore.Shared.TrailerFound += OnTrailer;
        Unloaded += (_, _) =>
        {
            App.Changed -= OnChanged;
            TmdbStore.Shared.TrailerFound -= OnTrailer;
        };
        _ = LoadAsync();
        _ = LoadTmdbAsync();
    }

    private void OnTrailer(int post)
    {
        if (post == Post.Id) Render();
    }

    /// <summary>The logo, trailer, crew and reviews TMDB has; the page is drawn again when they come.</summary>
    private async Task LoadTmdbAsync()
    {
        var known = TmdbStore.Shared.Known(_preview.Id);
        var info = await TmdbStore.Shared.InfoAsync(_preview, App.Loc.Lang);
        if (info is not null && !ReferenceEquals(info, known)) Render();
    }

    private void OnChanged(string what)
    {
        if (what is "lists" or "ratings") Render();
        // Back from the player: where to resume has moved.
        if (what == "watch" && _stream is not null) _ = LoadStreamAsync(quietly: true);
    }

    private async Task LoadAsync()
    {
        try { _loaded = Post.Parse(await App.Api.GetAsync("single", new Dictionary<string, string> { ["post_id"] = _preview.Id.ToString() })); }
        catch (Exception) { }
        Render();
        await LoadStreamAsync(quietly: false);
        // Opened by a play button elsewhere: start without asking again.
        if (_autoplay) Play();
    }

    // MARK: Stream

    /// <summary>Stream files need a subscription; guests only get them for free titles.</summary>
    private async Task LoadStreamAsync(bool quietly)
    {
#if DEBUG
        if (Player.DemoStream.IsEnabled)
        {
            _stream = Player.DemoStream.Result(Post);
            Render();
            return;
        }
#endif
        if (!App.IsLoggedIn && !Post.FreeStream) return;
        if (!quietly) { _streamPending = true; Render(); }
        Envelope? envelope = null;
        try
        {
            envelope = await App.Api.CallAsync("stream", new Dictionary<string, string>
            {
                ["post_id"] = Post.Id.ToString(), ["free_stream"] = App.IsLoggedIn ? "0" : "1",
            });
        }
        catch (Exception) { }
        var result = envelope is { Success: true } ? new StreamResult(envelope.Result) : null;
        _refusal = envelope is { Success: false } ? envelope.Message : null;
        if (result is not null)
        {
            _stream = result;
            if (!quietly && result.StartIndex < result.Items.Count) _season = result.Items[result.StartIndex].Season;
        }
        else if (!quietly) _stream = null;
        _streamPending = false;
        Render();
    }

    /// <summary>Plays the item at <paramref name="index"/>, or the one to resume.</summary>
    private void Play(int? index = null)
    {
        var loc = App.Loc;
        if (_stream is not { Items.Count: > 0 } stream)
        {
            App.ShowToast(Post.ComingSoon ? loc[S.comingSoonNote] : App.IsLoggedIn ? _refusal ?? loc[S.noStream] : loc[S.loginToWatch]);
            return;
        }
        App.Play(new PlaybackRequest(Post, stream.Items, index ?? stream.StartIndex, stream.Resume));
    }

    // MARK: Page

    private void Render()
    {
        _column.Children.Clear();
        _column.Children.Add(Header());
        if (Post.IsSeries && _stream is { Items.Count: > 1 } stream) _column.Children.Add(Episodes(stream));
        if (Post.Cast.Count > 0) _column.Children.Add(Cast());
        // From TMDB, with the user's own key: the people behind the camera, and reviews.
        if (TmdbStore.Shared.Known(Post.Id) is { } extra)
        {
            if (extra.Crew.Count > 0) _column.Children.Add(Crew(extra.Crew));
            if (extra.Reviews.Count > 0) _column.Children.Add(Reviews(extra.Reviews));
        }
        _column.Children.Add(Facts());
    }

    private FrameworkElement Header()
    {
        var loc = App.Loc;
        var post = Post;
        var row = new Grid { ColumnSpacing = 40 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        var poster = new Grid { Width = PosterWidth, Height = PosterHeight, CornerRadius = new CornerRadius(16), VerticalAlignment = VerticalAlignment.Top };
        // Under the full-size picture, the small one the shelves already have: the poster is
        // never a blur while the large one is on its way.
        poster.Children.Add(Images.Remote(post.Poster.Url(Theme.PosterWidth * 1.1 * 1.5), post.Poster.Blurhash, Theme.PosterWidth * 1.5));
        poster.Children.Add(Images.Remote(post.Poster.Big ?? post.Poster.Large, null, PosterWidth * 1.5));
        poster.Children.Add(new Border { CornerRadius = new CornerRadius(16), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.14) });
        var lifted = new Border { Child = poster, CornerRadius = new CornerRadius(16), Translation = new System.Numerics.Vector3(0, 0, 48), Shadow = new ThemeShadow(), VerticalAlignment = VerticalAlignment.Top };
        row.Children.Add(lifted);

        var info = new StackPanel { Spacing = 14 };
        info.Children.Add(Theme.Text(loc.KindLine(post), 14, FontWeights.SemiBold, Theme.Text2));
        info.Children.Add(new TitleArt(post, 48));
        var meta = Kit.MetaRow(post);
        meta.Margin = new Thickness(0, 2, 0, 0);
        info.Children.Add(meta);
        var plot = Theme.Text(post.Plot(loc.Lang), 16, color: Theme.Text2, lines: 0);
        plot.LineHeight = 26;
        plot.MaxWidth = 700;
        plot.HorizontalAlignment = HorizontalAlignment.Left;
        info.Children.Add(plot);
        // Everything to do with the title in one row: play it, rate it, keep it. A window too
        // narrow for the row puts the user's own part on a second one.
        var buttons = new WrapRow(14);
        buttons.Add(PlayButton());
        if (App.IsLoggedIn
#if DEBUG
            || Player.DemoStream.IsEnabled
#endif
            )
        {
            var download = Kit.Glass(App.Loc[S.download], Glyph.Download, () => _nav.Pick(Post), rim: Hue);
            buttons.Add(download);
        }
        // Only once a trailer has been found, so there is never a button that leads nowhere.
        if (TmdbStore.Shared.Known(post.Id)?.Trailer is { } video)
            buttons.Add(Kit.Glass(loc[S.trailer], Glyph.Video, () => _nav.Trailer(post.Title(loc.Lang), video), rim: Hue));
        buttons.Add(Rating());
        if (App.IsLoggedIn)
        {
            buttons.Add(ListToggle(Glyph.Bookmark, Glyph.BookmarkFill, "watchlist", loc[S.watchlist]));
            buttons.Add(ListToggle(Glyph.Heart, Glyph.HeartFill, "favorite", loc[S.favorites]));
        }
        buttons.Margin = new Thickness(0, 10, 0, 0);
        info.Children.Add(buttons);
        Grid.SetColumn(info, 1);
        row.Children.Add(info);
        return row;
    }

    private Button PlayButton()
    {
        var loc = App.Loc;
        var label = loc[S.play];
        var resume = _stream?.Resume ?? Post.Resume;
        if (resume is not null)
        {
            // "Resume · S2 · E5" for a series left part-way.
            if (Post.IsSeries && _stream is { } stream && stream.StartIndex < stream.Items.Count)
            {
                var item = stream.Items[stream.StartIndex];
                label = $"{loc[S.resume]} · {loc.EpisodeTag(item.Season, item.EpisodeRaw ?? item.EpisodeNumber.ToString())}";
            }
            else if (resume.Time > 5) label = loc[S.resume];
        }
        var button = Kit.Primary(label, _streamPending ? null : Glyph.Play, () => Play(), Hue);
        button.IsEnabled = !_streamPending;
        return button;
    }

    /// <summary>The user's own stars in one bar: five stars that take half steps.</summary>
    private FrameworkElement Rating()
    {
        var loc = App.Loc;
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12, VerticalAlignment = VerticalAlignment.Center };
        var label = Theme.Text(loc[S.yourRating], 14, FontWeights.SemiBold, Theme.Text2);
        label.VerticalAlignment = VerticalAlignment.Center;
        row.Children.Add(label);
        row.Children.Add(StarRater(App.Ratings.StarsFor(Post.Id), 18, stars => App.Rate(Post, stars)));
        var face = new Grid { Height = 46 };
        face.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(23) });
        face.Children.Add(new Border { Background = Theme.White(0.1), CornerRadius = new CornerRadius(23), BorderThickness = new Thickness(1), BorderBrush = Kit.RimBrush(Hue) });
        row.Margin = new Thickness(18, 0, 18, 0);
        face.Children.Add(row);
        return face;
    }

    /// <summary>
    /// Five stars that take half steps: the start half of a star is the half, the end half the
    /// whole. Clicking the rating there is clears it.
    /// </summary>
    private StackPanel StarRater(double? value, double size, Action<double?> set)
    {
        var stars = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        var icons = new List<FontIcon>();
        var on = Hue;
        void Show(double shown)
        {
            for (var n = 1; n <= 5; n++)
            {
                var icon = icons[n - 1];
                icon.Glyph = shown >= n ? Glyph.StarFill : shown >= n - 0.5 ? "\uE7C6" : Glyph.Star;
                icon.Foreground = Theme.Brush(shown >= n - 0.5 ? on : Theme.Text3);
            }
        }
        for (var n = 1; n <= 5; n++)
        {
            var icon = Glyph.Icon(Glyph.Star, size);
            icons.Add(icon);
            var cell = new Grid { Width = size * 1.35, Height = 34, Background = Theme.White(0) };
            cell.Children.Add(icon);
            var halves = new Grid();
            halves.ColumnDefinitions.Add(new ColumnDefinition());
            halves.ColumnDefinitions.Add(new ColumnDefinition());
            foreach (var (column, worth) in new[] { (0, n - 0.5), (1, (double)n) })
            {
                var half = new Border { Background = Theme.White(0) };
                Grid.SetColumn(half, column);
                half.PointerEntered += (_, _) => Show(worth);
                half.Tapped += (_, _) => set(value == worth ? null : worth);
                halves.Children.Add(half);
            }
            cell.Children.Add(halves);
            stars.Children.Add(cell);
        }
        stars.PointerExited += (_, _) => Show(value ?? 0);
        Show(value ?? 0);
        Kit.AutomationName(stars, $"{App.Loc[S.yourRating]}: {(value is { } v ? App.Loc.Num(Fmt.Score(v)) : "—")}");
        return stars;
    }

    /// <summary>A round button that keeps a title on one of the user's 30nama lists: filled in when it is there.</summary>
    private Button ListToggle(string glyph, string filled, string group, string help)
    {
        var listed = App.IsListed(Post, group);
        var button = Kit.Glass(null, listed ? filled : glyph, () => _ = App.ToggleListAsync(Post, group), height: 46, rim: Hue);
        if (listed && button.Content is Grid face && face.Children[^1] is FontIcon icon) icon.Foreground = Theme.Brush(Hue);
        ToolTipService.SetToolTip(button, help);
        Kit.AutomationName(button, help);
        return button;
    }

    // MARK: Episodes

    private FrameworkElement Episodes(StreamResult stream)
    {
        var loc = App.Loc;
        var column = new StackPanel { Spacing = 14 };
        var heading = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14 };
        heading.Children.Add(Theme.Text(loc[S.episodes], 20, FontWeights.Bold));
        if (stream.Seasons.Count > 1)
        {
            var pills = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            foreach (var number in stream.Seasons)
                pills.Children.Add(Kit.Pill($"{loc[S.season]} {loc.Num(number)}", _season == number, () => { _season = number; Render(); }));
            heading.Children.Add(new ScrollViewer
            {
                Content = pills, HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Enabled,
                VerticalScrollMode = ScrollMode.Disabled, VerticalAlignment = VerticalAlignment.Center,
            });
        }
        column.Children.Add(heading);
        var grid = new WrapRow(12);
        for (var i = 0; i < stream.Items.Count; i++)
        {
            var item = stream.Items[i];
            if (item.Season != _season) continue;
            var index = i;
            grid.Add(EpisodeTile(item, stream.Resume?.FileId == item.FileId ? stream.Resume : null, App.Watched.Contains(item.FileId), () => Play(index)));
        }
        column.Children.Add(grid);
        return column;
    }

    /// <summary>One episode: its number, name and length, and how far in the user is.</summary>
    private FrameworkElement EpisodeTile(StreamItem item, ContinueWatching? resume, bool watched, Action play)
    {
        var loc = App.Loc;
        var face = new Grid { Width = 300, Height = 62, CornerRadius = new CornerRadius(14), Background = Theme.White(0.07), Padding = new Thickness(12, 0, 12, 0), ColumnSpacing = 14 };
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(40) });
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        face.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var number = Theme.Text(loc.Num(item.EpisodeRaw ?? item.EpisodeNumber.ToString()), 20, FontWeights.ExtraBold, resume is not null ? Hue : Theme.Text2);
        number.HorizontalAlignment = HorizontalAlignment.Center;
        number.VerticalAlignment = VerticalAlignment.Center;
        var playIcon = Glyph.Icon(Glyph.Play, 16, Colors.White);
        playIcon.Opacity = 0;
        var mark = new Grid();
        mark.Children.Add(number);
        mark.Children.Add(playIcon);
        face.Children.Add(mark);
        var text = new StackPanel { Spacing = 3, VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(Theme.Text(item.Title(loc.Lang) ?? $"{loc[S.episode]} {loc.Num(item.EpisodeNumber)}", 15, FontWeights.SemiBold));
        if (item.DurationMinutes is { } minutes) text.Children.Add(Theme.Text($"{loc.Num(minutes)} {loc[S.min]}", 12, color: Theme.Text3));
        Grid.SetColumn(text, 1);
        face.Children.Add(text);
        if (watched)
        {
            var check = Glyph.Icon("\uE930", 16, Theme.Good);
            check.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(check, 2);
            face.Children.Add(check);
        }
        if (resume is { Progress: > 0 } r)
        {
            var bar = new Border
            {
                Height = 3, CornerRadius = new CornerRadius(1.5), Background = Theme.Brush(Hue), HorizontalAlignment = HorizontalAlignment.Left,
                VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(0, 0, 0, 5), Width = Math.Max(8, (300 - 24) * r.Progress),
            };
            Grid.SetColumnSpan(bar, 3);
            face.Children.Add(bar);
        }
        var button = Theme.Bare(face, play);
        Kit.AutomationName(button, item.Title(loc.Lang) ?? $"{loc[S.episode]} {item.EpisodeNumber}");
        button.PointerEntered += (_, _) => { face.Background = Theme.White(0.16); number.Opacity = 0; playIcon.Opacity = 1; };
        button.PointerExited += (_, _) => { face.Background = Theme.White(0.07); number.Opacity = 1; playIcon.Opacity = 0; };
        var menu = new MenuFlyout();
        var toggle = new MenuFlyoutItem { Text = loc[watched ? S.markUnwatched : S.markWatched] };
        toggle.Click += (_, _) => { App.SetWatched(item.FileId, !watched); Render(); };
        menu.Items.Add(toggle);
        button.ContextFlyout = menu;
        return button;
    }

    // MARK: People and facts

    private FrameworkElement Cast()
    {
        var loc = App.Loc;
        var column = new StackPanel { Spacing = 14 };
        column.Children.Add(Theme.Text(loc[S.cast], 20, FontWeights.Bold));
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 20 };
        foreach (var person in Post.Cast)
        {
            var card = new StackPanel { Width = 112, Spacing = 8 };
            var face = new Grid { Width = 96, Height = 96, CornerRadius = new CornerRadius(48), Background = Theme.Brush(Theme.Surface2), HorizontalAlignment = HorizontalAlignment.Center };
            var silhouette = Glyph.Icon(Glyph.Person, 30, Theme.Text3);
            silhouette.HorizontalAlignment = HorizontalAlignment.Center;
            face.Children.Add(silhouette);
            if (person.Poster is { } p) face.Children.Add(Images.Remote(p.Url(96), p.Blurhash, 96 * 1.5));
            card.Children.Add(face);
            var name = Theme.Text(person.Name, 13, FontWeights.SemiBold);
            name.HorizontalAlignment = HorizontalAlignment.Center;
            card.Children.Add(name);
            if (person.Role is { } role)
            {
                var r = Theme.Text(role, 12, color: Theme.Text3);
                r.HorizontalAlignment = HorizontalAlignment.Center;
                card.Children.Add(r);
            }
            row.Children.Add(card);
        }
        column.Children.Add(new ScrollViewer
        {
            Content = row, HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Enabled,
            VerticalScrollMode = ScrollMode.Disabled,
        });
        return column;
    }

    /// <summary>A row that scrolls sideways under a heading, as the cast does.</summary>
    private static FrameworkElement Strip(string heading, IEnumerable<FrameworkElement> items, double spacing)
    {
        var column = new StackPanel { Spacing = 14 };
        column.Children.Add(Theme.Text(heading, 20, FontWeights.Bold));
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = spacing };
        foreach (var item in items) row.Children.Add(item);
        column.Children.Add(new ScrollViewer
        {
            Content = row, HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Enabled,
            VerticalScrollMode = ScrollMode.Disabled,
        });
        return column;
    }

    private static FrameworkElement Crew(IReadOnlyList<TMDB.Person> people)
    {
        var loc = App.Loc;
        return Strip(loc[S.crew], people.Select(person =>
        {
            var card = new StackPanel { Width = 112, Spacing = 8 };
            var face = new Grid { Width = 96, Height = 96, CornerRadius = new CornerRadius(48), Background = Theme.Brush(Theme.Surface2), HorizontalAlignment = HorizontalAlignment.Center };
            var silhouette = Glyph.Icon(Glyph.Person, 30, Theme.Text3);
            silhouette.HorizontalAlignment = HorizontalAlignment.Center;
            face.Children.Add(silhouette);
            if (person.Photo is { } photo) face.Children.Add(Images.Remote(photo, null, 96 * 1.5));
            card.Children.Add(face);
            var name = Theme.Text(person.Name, 13, FontWeights.SemiBold);
            name.HorizontalAlignment = HorizontalAlignment.Center;
            card.Children.Add(name);
            var job = Theme.Text(loc.Job(person.Job), 12, color: Theme.Text3);
            job.HorizontalAlignment = HorizontalAlignment.Center;
            card.Children.Add(job);
            return (FrameworkElement)card;
        }), 20);
    }

    /// <summary>Reviews from TMDB, in the language they were written in (mostly English), cut short; the whole of one opens on TMDB.</summary>
    private static FrameworkElement Reviews(IReadOnlyList<TMDB.Review> reviews)
    {
        var loc = App.Loc;
        return Strip(loc[S.reviews], reviews.Select(review =>
        {
            var face = new Grid { Width = 340, Height = 180, Padding = new Thickness(16), CornerRadius = new CornerRadius(18), Background = Theme.White(0.06), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.1), RowSpacing = 10 };
            face.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            face.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            var top = new Grid { ColumnSpacing = 8 };
            top.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            top.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            top.Children.Add(Theme.Text(review.Author, 14, FontWeights.Bold));
            if (review.Rating is { } rating)
            {
                var score = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
                var star = Glyph.Icon(Glyph.StarFill, 10, Color.FromArgb(255, 0xF5, 0xC5, 0x18));
                star.VerticalAlignment = VerticalAlignment.Center;
                score.Children.Add(star);
                score.Children.Add(Theme.Text($"{loc.Num(Fmt.Score(rating))}/{loc.Num(10)}", 12, FontWeights.Bold, Theme.Text2));
                Grid.SetColumn(score, 1);
                top.Children.Add(score);
            }
            face.Children.Add(top);
            var text = Theme.Text(review.Text, 13, color: Theme.Text2, lines: 6);
            text.LineHeight = 19;
            // Written left to right whatever the interface reads in.
            text.FlowDirection = FlowDirection.LeftToRight;
            text.TextAlignment = TextAlignment.Left;
            Grid.SetRow(text, 1);
            face.Children.Add(text);
            var button = Theme.Bare(face, () => { if (review.Link is { } link) _ = Windows.System.Launcher.LaunchUriAsync(link); });
            button.PointerEntered += (_, _) => face.Background = Theme.White(0.1);
            button.PointerExited += (_, _) => face.Background = Theme.White(0.06);
            Kit.AutomationName(button, $"{review.Author}: {review.Text}");
            return (FrameworkElement)button;
        }), 16);
    }

    /// <summary>
    /// Who made it and where, and what 30nama's own apps show beside the scores: places on the top
    /// lists, how many people keep it, the budget and the newest addition.
    /// </summary>
    private FrameworkElement Facts()
    {
        var loc = App.Loc;
        var post = Post;
        string Join(IEnumerable<string> names) => string.Join(loc.Lang == Lang.En ? ", " : "، ", names);
        var rows = new List<(string Label, string Value)>();
        foreach (var (key, names) in new[] { (S.director, post.Directors), (S.creator, post.Creators), (S.writer, post.Writers), (S.network, post.Networks), (S.country, post.Countries) })
            if (names.Count > 0) rows.Add((loc[key], Join(names)));
        if (post.Language is { } language) rows.Add((loc[S.originalLanguage], language));
        // A row of its own: names in English and a label in Persian on one line come out in a jumbled order.
        if (post.OtherLanguages.Count > 0) rows.Add((loc[Extra.otherLanguages], Join(post.OtherLanguages.Take(5))));
        if (post.AirStatus is { } status) rows.Add((loc[S.status], post.NextEpisodeLabel(loc.Lang) is { } next ? $"{status} · {post.NextEpisodeCaption ?? loc[S.nextEpisode]}: {next}" : status));
        if (post.ImdbTop250 is { } top) rows.Add((loc[Extra.imdbTop250], $"#{loc.Num(top)}"));
        if (post.SiteTop100 is { } siteTop) rows.Add((loc[Extra.siteTop100], $"#{loc.Num(siteTop)}"));
        if (post.ImdbVotes is { } votes) rows.Add(("IMDb", $"{Fmt.Count(votes, loc.Lang)} {loc[Extra.votes]}"));
        var people = new List<string>();
        if (post.InWatchlists is { } w) people.Add($"{Fmt.Count(w, loc.Lang)} {loc[Extra.inWatchlists]}");
        if (post.InFavorites is { } f) people.Add($"{Fmt.Count(f, loc.Lang)} {loc[Extra.inFavorites]}");
        if (people.Count > 0) rows.Add(("30nama", string.Join(" · ", people)));
        if (post.Budget is { } budget) rows.Add((loc[Extra.budget], budget));
        if (post.ExclusiveSubtitle) rows.Add((loc[S.subtitles], loc[Extra.exclusiveSub]));
        if (post.LatestUpdate is { } update) rows.Add((loc[Extra.latestUpdate], update.Content is { } c ? $"{update.Title} · {c}" : update.Title));

        var grid = new Grid { ColumnSpacing = 16, RowSpacing = 10 };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(150) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        for (var i = 0; i < rows.Count; i++)
        {
            grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            var label = Theme.Text(rows[i].Label, 14, FontWeights.Medium, Theme.Text3);
            var value = Theme.Text(rows[i].Value, 15, lines: 2);
            Grid.SetRow(label, i);
            Grid.SetRow(value, i);
            Grid.SetColumn(value, 1);
            grid.Children.Add(label);
            grid.Children.Add(value);
        }
        return grid;
    }
}

/// <summary>Lays its children out in rows, moving on to a new row where one is full.</summary>
public sealed class WrapRow : Panel
{
    private readonly double _spacing;

    public WrapRow(double spacing) => _spacing = spacing;

    public void Add(UIElement child) => Children.Add(child);

    protected override Windows.Foundation.Size MeasureOverride(Windows.Foundation.Size available)
    {
        double x = 0, y = 0, rowHeight = 0, width = 0;
        foreach (var child in Children)
        {
            child.Measure(new Windows.Foundation.Size(available.Width, double.PositiveInfinity));
            var size = child.DesiredSize;
            if (x > 0 && x + size.Width > available.Width) { y += rowHeight + _spacing; x = 0; rowHeight = 0; }
            x += size.Width + _spacing;
            width = Math.Max(width, x - _spacing);
            rowHeight = Math.Max(rowHeight, size.Height);
        }
        return new Windows.Foundation.Size(width, y + rowHeight);
    }

    protected override Windows.Foundation.Size ArrangeOverride(Windows.Foundation.Size final)
    {
        double x = 0, y = 0, rowHeight = 0;
        foreach (var child in Children)
        {
            var size = child.DesiredSize;
            if (x > 0 && x + size.Width > final.Width) { y += rowHeight + _spacing; x = 0; rowHeight = 0; }
            child.Arrange(new Windows.Foundation.Rect(x, y, size.Width, size.Height));
            x += size.Width + _spacing;
            rowHeight = Math.Max(rowHeight, size.Height);
        }
        return final;
    }
}
