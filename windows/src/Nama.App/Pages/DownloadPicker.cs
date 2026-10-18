using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;
using Color = Windows.UI.Color;

namespace Nama.App.Pages;

/// <summary>
/// Choosing what to download for one title, as a pane of glass over the window. A film: pick a
/// quality, and it starts. A series: pick a season, then the quality the season comes in, then
/// single episodes or the whole season at once. Each step under its own heading, so there is
/// never a doubt about what the next click will do.
/// </summary>
public sealed class DownloadPicker : Grid
{
    private static AppModel App => AppModel.Shared;
    private static Downloads Downloads => Downloads.Shared;
    private static Loc Loc => App.Loc;

    private readonly Post _post;
    private readonly Action _close;
    private readonly Color _hue;
    private readonly Grid _content = new();
    private readonly Grid _footer = new() { Height = 76, Padding = new Thickness(26, 0, 26, 0) };
    private DownloadOptions? _options;
    private int _season = 1;
    private string? _pack;

    public DownloadPicker(Post post, Action close)
    {
        _post = post;
        _close = close;
        _hue = post.Tint();
        var dim = new Grid { Background = Theme.Black(0.55) };
        dim.Tapped += (_, e) => { if (ReferenceEquals(e.OriginalSource, dim)) close(); };
        Children.Add(dim);

        var pane = new Grid { Width = 900, MaxHeight = 680, Margin = new Thickness(0, 60, 0, 30), VerticalAlignment = VerticalAlignment.Center, CornerRadius = new CornerRadius(28) };
        pane.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        pane.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        pane.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        var glass = new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(28), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.2) };
        SetRowSpan(glass, 3);
        pane.Children.Add(glass);
        pane.Children.Add(Header());
        _content.MinHeight = 420;
        _content.BorderThickness = new Thickness(0, 1, 0, 1);
        _content.BorderBrush = Theme.White(0.1);
        SetRow(_content, 1);
        pane.Children.Add(_content);
        SetRow(_footer, 2);
        pane.Children.Add(_footer);
        Children.Add(pane);
        _content.Children.Add(Kit.Spinner());
        RenderFooter();
        Downloads.Changed += Refresh;
        Unloaded += (_, _) => Downloads.Changed -= Refresh;
        _ = LoadAsync();
    }

    private void Refresh()
    {
        if (_options is not null) Render();
    }

    private FrameworkElement Header()
    {
        var row = new Grid { Padding = new Thickness(26, 20, 26, 20), ColumnSpacing = 16 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var poster = new Grid { Width = 54, Height = 81, CornerRadius = new CornerRadius(9), Background = Theme.Brush(Theme.Surface2) };
        poster.Children.Add(Images.Remote(_post.Poster.Url(60), _post.Poster.Blurhash, 90));
        row.Children.Add(poster);
        var text = new StackPanel { Spacing = 3, VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(Theme.Text(Loc[S.download], 13, FontWeights.SemiBold, _hue));
        text.Children.Add(Theme.Text(_post.Title(Loc.Lang), 24, FontWeights.Bold));
        text.Children.Add(Theme.Text(Loc.KindLine(_post), 13, color: Theme.Text3));
        SetColumn(text, 1);
        row.Children.Add(text);
        var close = Kit.Glass(null, Glyph.Close, _close, height: 36);
        close.VerticalAlignment = VerticalAlignment.Top;
        Kit.AutomationName(close, Loc[S.close]);
        SetColumn(close, 2);
        row.Children.Add(close);
        return row;
    }

    private void RenderFooter()
    {
        _footer.Children.Clear();
        _footer.ColumnDefinitions.Clear();
        _footer.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        _footer.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var place = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10, VerticalAlignment = VerticalAlignment.Center };
        place.Children.Add(Glyph.Icon("", 13, Theme.Text3));
        place.Children.Add(Theme.Text(Loc[S.saveTo], 13, color: Theme.Text3));
        // A path reads left to right in any language; on its own, or its slashes change sides.
        var path = Theme.Text(Downloads.Folder, 13, FontWeights.SemiBold, Theme.Text2);
        path.FlowDirection = FlowDirection.LeftToRight;
        path.MaxWidth = 300;
        place.Children.Add(path);
        // How much room is left there, as 30nama's own desktop app shows.
        if (Downloads.FreeSpace is { } free)
            place.Children.Add(Theme.Text($"· {Size(free)} {Loc[S.freeSpace]}", 13, color: Theme.Text3));
        _footer.Children.Add(place);
        if (ChosenPack is { } chosen)
        {
            var left = chosen.Files.Where(f => !Downloads.Has(f)).ToList();
            var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10, VerticalAlignment = VerticalAlignment.Center };
            buttons.Children.Add(SubtitleButton(wide: true, had: false, () => Downloads.SubtitlesOnlyAsync(chosen.Files, _post)));
            var whole = Kit.Primary(Loc.Isolated($"{Loc[S.wholeSeason]} · {Loc.Num(left.Count)} {Loc[S.episode]}"), Glyph.Download, () =>
            {
                foreach (var file in left) Downloads.Start(file, _post);
                App.ShowToast(Loc[S.downloadStarted]);
                _close();
            }, _hue);
            whole.IsEnabled = left.Count > 0;
            whole.Opacity = left.Count > 0 ? 1 : 0.4;
            buttons.Children.Add(whole);
            SetColumn(buttons, 1);
            _footer.Children.Add(buttons);
        }
    }

    /// <summary>Bytes as people read them: "781 MB", "2.1 GB".</summary>
    public static string Size(long bytes)
    {
        string[] units = ["B", "KB", "MB", "GB", "TB"];
        double value = bytes;
        var unit = 0;
        while (value >= 1000 && unit < units.Length - 1) { value /= 1000; unit++; }
        return Loc.Num(value.ToString(unit >= 3 ? "0.0" : "0", System.Globalization.CultureInfo.InvariantCulture)) + " " + units[unit];
    }

    private void Render()
    {
        _content.Children.Clear();
        if (_options is not { } options) return;
        if (options.IsEmpty)
        {
            _content.Children.Add(Note(Loc[S.noDownload]));
        }
        else if (options.Packs.Count == 0)
        {
            _content.Children.Add(new ScrollViewer { Content = Film(options.Files), Padding = new Thickness(26) });
        }
        else
        {
            // Two columns, so the whole choice is in view at once: what to take on one side, the
            // episodes that choice holds on the other.
            var two = new Grid();
            two.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(520) });
            two.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            two.Children.Add(new ScrollViewer { Content = Packs(options), Padding = new Thickness(26), BorderThickness = new Thickness(0, 0, 1, 0), BorderBrush = Theme.White(0.1) });
            var episodes = new ScrollViewer { Content = Episodes(options), Padding = new Thickness(26) };
            SetColumn(episodes, 1);
            two.Children.Add(episodes);
            _content.Children.Add(two);
        }
        RenderFooter();
    }

    private static TextBlock Note(string text)
    {
        var t = Theme.Text(text, 16, FontWeights.Medium, Theme.Text2, lines: 2);
        t.HorizontalAlignment = HorizontalAlignment.Center;
        t.VerticalAlignment = VerticalAlignment.Center;
        return t;
    }

    private static FrameworkElement Heading(string text, string? aside = null)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        row.Children.Add(Theme.Text(text, 15, FontWeights.Bold));
        if (aside is not null)
        {
            var a = Theme.Text(aside, 13, color: Theme.Text3);
            a.VerticalAlignment = VerticalAlignment.Bottom;
            row.Children.Add(a);
        }
        return row;
    }

    // MARK: A film

    /// <summary>The qualities a film comes in, the sharpest first, each one a row to click.</summary>
    private FrameworkElement Film(IReadOnlyList<DownloadFile> files)
    {
        var column = new StackPanel { Spacing = 20 };
        foreach (var group in files.GroupBy(f => Resolution(f.Quality)).OrderByDescending(g => g.Key))
        {
            var block = new StackPanel { Spacing = 8 };
            block.Children.Add(Heading(group.Key > 0 ? $"{group.Key}p" : Loc[S.fileQuality], group.Key >= 2160 ? "4K" : null));
            foreach (var file in group)
            {
                var listed = Downloads.Has(file);
                block.Children.Add(FileRow(file.Quality, file.Encoder, file.Tags, file.Size, file.Subtitle is not null, null, listed ? Mark.Listed : Mark.Ready,
                    () =>
                    {
                        Downloads.Start(file, _post);
                        App.ShowToast(Loc[S.downloadStarted]);
                        _close();
                    },
                    SubtitleButton(wide: false, Downloads.HasSubtitle(file), () => Downloads.SubtitlesOnlyAsync([file], _post))));
            }
            column.Children.Add(block);
        }
        return column;
    }

    private static int Resolution(string quality) => new[] { 2160, 1080, 720, 480 }.FirstOrDefault(lines => quality.Contains(lines.ToString()));

    // MARK: A series

    private DownloadOptions.Pack? ChosenPack
    {
        get
        {
            if (_options is not { Packs.Count: > 0 } options) return null;
            var packs = options.Packs.Where(p => p.Season == _season).ToList();
            return packs.FirstOrDefault(p => p.Id == _pack) ?? packs.FirstOrDefault();
        }
    }

    private FrameworkElement Packs(DownloadOptions options)
    {
        var column = new StackPanel { Spacing = 22 };
        var seasons = new StackPanel { Spacing = 10 };
        seasons.Children.Add(Heading(Loc[S.season]));
        var choices = new WrapRow(8);
        foreach (var number in options.Seasons)
        {
            var selected = _season == number;
            var face = new Border { Width = 92, Height = 38, CornerRadius = new CornerRadius(19), Background = selected ? Theme.Brush(Colors.White) : Theme.White(0.1) };
            var label = Theme.Text($"{Loc[S.season]} {Loc.Num(number)}", 14, FontWeights.SemiBold, selected ? Theme.FocusInk : Theme.Text1);
            label.HorizontalAlignment = HorizontalAlignment.Center;
            label.VerticalAlignment = VerticalAlignment.Center;
            face.Child = label;
            choices.Add(Theme.Bare(face, () => { _season = number; _pack = null; Render(); }));
        }
        seasons.Children.Add(choices);
        column.Children.Add(seasons);

        var qualities = new StackPanel { Spacing = 8 };
        qualities.Children.Add(Heading(Loc[S.fileQuality], Loc[S.perEpisode]));
        var whole = options.Episodes(_season).Count;
        foreach (var option in options.Packs.Where(p => p.Season == _season))
        {
            // A pack that lacks episodes says so, and how many it does have.
            var shortfall = option.Files.Count < whole ? $"{Loc[S.incomplete]} · {Loc.Num(option.Files.Count)} {Loc[S.ofTotal]} {Loc.Num(whole)} {Loc[S.episode]}" : null;
            qualities.Children.Add(FileRow(option.Quality, option.Encoder, option.Files.FirstOrDefault()?.Tags ?? [], option.Size,
                option.Files.Any(f => f.Subtitle is not null), shortfall, option.Id == ChosenPack?.Id ? Mark.Chosen : Mark.Choice,
                () => { _pack = option.Id; Render(); }, null));
        }
        column.Children.Add(qualities);
        return column;
    }

    /// <summary>The episodes of the chosen season in the chosen quality: click one to fetch just that one.</summary>
    private FrameworkElement Episodes(DownloadOptions options)
    {
        var column = new StackPanel { Spacing = 10 };
        if (ChosenPack is not { } chosen) return column;
        var all = options.Episodes(chosen.Season);
        column.Children.Add(Heading(Loc[S.episodes], chosen.Files.Count < all.Count ? $"{Loc.Num(chosen.Files.Count)} {Loc[S.ofTotal]} {Loc.Num(all.Count)}" : Loc.Num(all.Count)));
        var grid = new WrapRow(8);
        // Every episode of the season keeps its place. One this quality does not have is shown
        // switched off, not left out: a gap reads as a fault in the list.
        foreach (var episode in all)
        {
            var file = chosen.Files.FirstOrDefault(f => f.Episode == episode);
            var listed = file is not null && Downloads.Has(file);
            var available = file is not null;
            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 7, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
            row.Children.Add(Glyph.Icon(!available ? "" : listed ? Glyph.Check : Glyph.Download, 11, listed ? Theme.Good : Theme.Text2));
            row.Children.Add(Theme.Text($"{Loc[S.episode]} {Loc.Num(episode)}", 13, FontWeights.SemiBold, listed ? Theme.Text3 : Theme.Text1));
            var face = new Border
            {
                Width = 104, Height = 40, CornerRadius = new CornerRadius(12), Child = row,
                Background = Theme.White(!available ? 0.03 : listed ? 0.04 : 0.09), Opacity = available ? 1 : 0.38,
            };
            var button = Theme.Bare(face, () =>
            {
                if (file is null || listed) return;
                Downloads.Start(file, _post);
                App.ShowToast(Loc[S.downloadStarted]);
            });
            button.IsEnabled = available && !listed;
            if (!available) ToolTipService.SetToolTip(button, Loc[S.notInPack]);
            if (available && !listed)
            {
                button.PointerEntered += (_, _) => face.Background = Theme.White(0.2);
                button.PointerExited += (_, _) => face.Background = Theme.White(0.09);
            }
            grid.Add(button);
        }
        column.Children.Add(grid);
        return column;
    }

    // MARK: Rows

    private enum Mark { Ready, Listed, Choice, Chosen }

    /// <summary>
    /// A file or a pack of files as one wide row: what it is on one side, how big on the other.
    /// For a film the row is the download button; for a series it chooses the quality.
    /// </summary>
    private FrameworkElement FileRow(string title, string? encoder, IReadOnlyList<string> tags, string? size, bool subtitled, string? shortfall, Mark mark, Action action, FrameworkElement? extra)
    {
        var row = new Grid { MinHeight = 58, CornerRadius = new CornerRadius(14), Background = Theme.White(mark == Mark.Chosen ? 0.16 : 0.06), BorderThickness = new Thickness(mark == Mark.Chosen ? 1.5 : 1), BorderBrush = Theme.White(mark == Mark.Chosen ? 0.55 : 0.06) };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var inner = new Grid { Padding = new Thickness(16, 8, 16, 8), ColumnSpacing = 14 };
        inner.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(26) });
        inner.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        inner.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var symbol = mark switch { Mark.Listed => "", Mark.Choice => "", Mark.Chosen => "", _ => "" };
        var icon = Glyph.Icon(symbol, 19, mark == Mark.Listed ? Theme.Good : mark == Mark.Chosen ? _hue : Theme.Text2);
        icon.VerticalAlignment = VerticalAlignment.Center;
        inner.Children.Add(icon);
        var text = new StackPanel { Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(Theme.Text(title, 15, FontWeights.Bold));
        var marks = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        if (encoder is not null) marks.Children.Add(Theme.Text(encoder, 12, FontWeights.Medium, Theme.Text3));
        foreach (var tag in tags.Take(4)) marks.Children.Add(Badge(tag));
        if (subtitled) marks.Children.Add(Badge(Loc[S.persianSub], Glyph.Captions));
        if (shortfall is not null) marks.Children.Add(Badge(shortfall, ""));
        if (marks.Children.Count > 0) text.Children.Add(marks);
        Grid.SetColumn(text, 1);
        inner.Children.Add(text);
        if (size is not null)
        {
            var s = Theme.Text(size, 14, FontWeights.SemiBold);
            s.FlowDirection = FlowDirection.LeftToRight;
            s.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(s, 2);
            inner.Children.Add(s);
        }
        var button = Theme.Bare(inner, action);
        button.IsEnabled = mark != Mark.Listed;
        button.HorizontalAlignment = HorizontalAlignment.Stretch;
        if (mark is not Mark.Chosen)
        {
            button.PointerEntered += (_, _) => row.Background = Theme.White(0.12);
            button.PointerExited += (_, _) => row.Background = Theme.White(0.06);
        }
        row.Children.Add(button);
        if (extra is not null)
        {
            // Its own button, apart from the row's: the film is not wanted, only its subtitles.
            extra.Margin = new Thickness(0, 0, 14, 0);
            extra.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(extra, 1);
            row.Children.Add(extra);
        }
        return row;
    }

    private static Border Badge(string text, string? glyph = null)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        if (glyph is not null) row.Children.Add(Glyph.Icon(glyph, 9, Theme.Text2));
        row.Children.Add(Theme.Text(text, 11, FontWeights.Bold, Theme.Text2));
        return new Border { Height = 19, Padding = new Thickness(7, 0, 7, 0), CornerRadius = new CornerRadius(9.5), Background = Theme.White(0.1), Child = row };
    }

    /// <summary>Fetches Persian subtitles without the film, and says on its own face how that went.</summary>
    private static Button SubtitleButton(bool wide, bool had, Func<Task<int>> fetch)
    {
        var icon = Glyph.Icon(had ? Glyph.Check : Glyph.Captions, wide ? 14 : 12, had ? Theme.Good : Theme.Text1);
        var label = Theme.Text(wide ? Loc[S.getSubtitle] : Loc[S.subtitles], wide ? 16 : 12, wide ? FontWeights.SemiBold : FontWeights.Bold);
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = wide ? 8 : 5, VerticalAlignment = VerticalAlignment.Center };
        var mark = new Grid { Width = wide ? 18 : 15, Height = wide ? 18 : 15 };
        mark.Children.Add(icon);
        row.Children.Add(mark);
        row.Children.Add(label);
        var face = new Border { Height = wide ? 46 : 32, Padding = new Thickness(wide ? 22 : 12, 0, wide ? 22 : 12, 0), CornerRadius = new CornerRadius(wide ? 23 : 16), Background = Theme.White(0.12), Child = row };
        var working = false;
        var button = Theme.Bare(face, async () =>
        {
            if (working) return;
            working = true;
            mark.Children.Clear();
            mark.Children.Add(new ProgressRing { IsActive = true, Width = 14, Height = 14, Foreground = Theme.Brush(Theme.Text1) });
            var saved = await fetch() > 0;
            mark.Children.Clear();
            mark.Children.Add(Glyph.Icon(saved ? Glyph.Check : Glyph.Close, wide ? 14 : 12, saved ? Theme.Good : Theme.Text1));
            if (!saved) label.Text = Loc[S.subtitleMissing];
            working = false;
        });
        button.PointerEntered += (_, _) => face.Background = Theme.White(0.2);
        button.PointerExited += (_, _) => face.Background = Theme.White(0.12);
        ToolTipService.SetToolTip(button, Loc[S.getSubtitle]);
        Kit.AutomationName(button, Loc[S.getSubtitle]);
        return button;
    }

    private async Task LoadAsync()
    {
#if DEBUG
        if (Player.DemoStream.IsEnabled)
        {
            _options = Player.DemoStream.Downloads(_post);
            _season = _options.Seasons.FirstOrDefault(1);
            Render();
            return;
        }
#endif
        Envelope? answer = null;
        try
        {
            answer = await App.Api.CallAsync("download", new Dictionary<string, string> { ["post_id"] = _post.Id.ToString(), ["free_download"] = App.IsLoggedIn ? "0" : "1" });
        }
        catch (Exception) { }
        _content.Children.Clear();
        if (answer is null)
        {
            _content.Children.Add(Note($"{Loc[S.error]}. {Loc[S.tryAgain]}"));
            return;
        }
        if (!answer.Success)
        {
            _content.Children.Add(Note(answer.Message ?? Loc[S.noDownload]));
            return;
        }
        var found = new DownloadOptions(answer.Result);
        _season = found.Seasons.FirstOrDefault(1);
        _options = found;
        Render();
    }
}
