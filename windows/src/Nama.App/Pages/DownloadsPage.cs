using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// Everything being fetched and everything fetched, in three parts: films, series and anime.
/// Each part wears the artwork of the title that was downloaded into it last.
/// </summary>
public sealed class DownloadsPage : Grid
{
    private static AppModel App => AppModel.Shared;
    private static Downloads Downloads => Downloads.Shared;
    private static Loc Loc => App.Loc;

    private readonly Stage _stage = new(dim: 0.5);
    private readonly StackPanel _column = new() { Spacing = 22, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60), MaxWidth = 1020 };
    /// <summary>The part on show. Until the user picks one, it is the part the newest download is in.</summary>
    private DownloadItem.Shelves? _chosen;
    /// <summary>The series whose episodes are folded out.</summary>
    private readonly HashSet<string> _unfolded = [];
    /// <summary>The rows that ask what removing should mean: off the list only, or the file as well.</summary>
    private readonly HashSet<string> _asking = [];
    private readonly DispatcherTimer _redraw = new() { Interval = TimeSpan.FromSeconds(0.5) };
    private bool _dirty;

    public DownloadsPage()
    {
        Children.Add(Kit.Ambient());
        Children.Add(_stage);
        Children.Add(new ScrollViewer { Content = _column, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        // The series downloaded into most recently starts folded out.
        if (Downloads.Items.FirstOrDefault(i => i.Shelf != DownloadItem.Shelves.Movies && i.Season is not null) is { } latest) _unfolded.Add(latest.Group);
        // Progress comes in often; the list is drawn again at most twice a second.
        Downloads.Changed += MarkDirty;
        _redraw.Tick += (_, _) => { if (_dirty) { _dirty = false; Render(); } };
        Loaded += (_, _) => _redraw.Start();
        Unloaded += (_, _) => { _redraw.Stop(); Downloads.Changed -= MarkDirty; };
        Render();
    }

    private void MarkDirty() => _dirty = true;

    private DownloadItem.Shelves Shelf => _chosen ?? Downloads.Items.FirstOrDefault()?.Shelf ?? DownloadItem.Shelves.Movies;

    private void Render()
    {
        var shown = Downloads.Items.Where(i => i.Shelf == Shelf).ToList();
        _stage.Show(shown.FirstOrDefault()?.ToPost());
        _column.Children.Clear();
        _column.Children.Add(Header());
        if (shown.Count == 0)
        {
            var empty = new StackPanel { Spacing = 12, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 120, 0, 0) };
            var icon = Glyph.Icon(Glyph.Download, 44, Theme.Text3);
            icon.HorizontalAlignment = HorizontalAlignment.Center;
            empty.Children.Add(icon);
            empty.Children.Add(Theme.Text(Loc[S.noDownloads], 17, FontWeights.Medium, Theme.Text2));
            _column.Children.Add(empty);
            return;
        }
        var list = new StackPanel { Spacing = 10 };
        // A film stands alone; the episodes of a series stand together, in the order they are watched.
        foreach (var group in shown.GroupBy(i => i.Season is not null ? i.Group : i.Id))
        {
            var items = group.ToList();
            if (items[0].Season is not null)
                list.Children.Add(SeriesGroup(items.OrderBy(i => i.Season ?? 0).ThenBy(i => Nama.Core.Episode.Number(i.Episode) ?? 0).ToList()));
            else list.Children.Add(Glass(Row(items[0], episode: false)));
        }
        _column.Children.Add(list);
    }

    private FrameworkElement Header()
    {
        var row = new Grid { ColumnSpacing = 14 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.Children.Add(Theme.Text(Loc[S.downloads], 34, FontWeights.Bold));
        var parts = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 2, Margin = new Thickness(4) };
        foreach (var part in Enum.GetValues<DownloadItem.Shelves>())
        {
            var selected = Shelf == part;
            var count = Downloads.Items.Count(i => i.Shelf == part);
            var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 7, VerticalAlignment = VerticalAlignment.Center };
            label.Children.Add(Theme.Text(Loc[part switch { DownloadItem.Shelves.Series => S.series, DownloadItem.Shelves.Anime => S.anime, _ => S.movies }], 15.5, FontWeights.SemiBold, selected ? Theme.FocusInk : Theme.Text1.WithAlpha(0.8)));
            if (count > 0)
            {
                var badge = Theme.Text(Loc.Num(count), 12, FontWeights.Bold, selected ? Theme.FocusInk : Theme.Text1);
                badge.VerticalAlignment = VerticalAlignment.Center;
                label.Children.Add(new Border { Height = 20, Padding = new Thickness(7, 0, 7, 0), CornerRadius = new CornerRadius(10), Background = selected ? Theme.Black(0.12) : Theme.White(0.14), Child = badge });
            }
            var face = new Border { Height = 40, Padding = new Thickness(18, 0, 18, 0), CornerRadius = new CornerRadius(20), Background = selected ? Theme.Brush(Colors.White) : Theme.White(0), Child = label };
            parts.Children.Add(Theme.Bare(face, () => { _chosen = part; Render(); }));
        }
        var capsule = new Grid { VerticalAlignment = VerticalAlignment.Center };
        capsule.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(24), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        capsule.Children.Add(parts);
        Grid.SetColumn(capsule, 1);
        row.Children.Add(capsule);
        var folder = Kit.Glass(Path.GetFileName(Downloads.Folder.TrimEnd('\\')), "", Downloads.OpenFolder, height: 34);
        folder.VerticalAlignment = VerticalAlignment.Center;
        ToolTipService.SetToolTip(folder, Downloads.Folder);
        Grid.SetColumn(folder, 3);
        row.Children.Add(folder);
        return row;
    }

    private static Grid Glass(FrameworkElement content)
    {
        var card = new Grid();
        card.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(16), BorderThickness = new Thickness(1), BorderBrush = Theme.GlassRim });
        card.Children.Add(content);
        return card;
    }

    /// <summary>A series in the list: one bar that folds out into its downloaded episodes.</summary>
    private FrameworkElement SeriesGroup(List<DownloadItem> items)
    {
        var open = _unfolded.Contains(items[0].Group);
        var column = new StackPanel();
        var bar = new Grid { MinHeight = 84, Padding = new Thickness(18, 0, 18, 0), ColumnSpacing = 16 };
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        bar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        bar.Children.Add(Thumb(items[0]));
        var text = new StackPanel { Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(Theme.Text(items[0].Title, 17, FontWeights.Bold));
        var bytes = items.Sum(i => Math.Max(i.Expected, i.Received));
        // The size is Latin text inside a right-to-left line: held together so its parts do not trade places.
        var summary = $"{Loc.Num(items.Count)} {Loc[S.episode]}" + (bytes > 0 ? " · ⁨" + DownloadPicker.Size(bytes) + "⁩" : "");
        text.Children.Add(Theme.Text(summary, 13, color: Theme.Text3));
        Grid.SetColumn(text, 1);
        bar.Children.Add(text);
        var chevron = new Border { Width = 34, Height = 34, CornerRadius = new CornerRadius(17), Background = Theme.White(0.08), Child = Glyph.Icon(open ? "" : "", 13, Theme.Text2), VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(chevron, 2);
        bar.Children.Add(chevron);
        var toggle = Theme.Bare(bar, () =>
        {
            if (!_unfolded.Add(items[0].Group)) _unfolded.Remove(items[0].Group);
            Render();
        });
        toggle.HorizontalAlignment = HorizontalAlignment.Stretch;
        column.Children.Add(toggle);
        if (open)
        {
            foreach (var item in items)
            {
                column.Children.Add(new Border { Height = 1, Background = Theme.White(0.06), Margin = new Thickness(18, 0, 18, 0) });
                column.Children.Add(Row(item, episode: true));
            }
        }
        return Glass(column);
    }

    /// <summary>The poster of what a download belongs to, small.</summary>
    private static FrameworkElement Thumb(DownloadItem item)
    {
        var thumb = new Grid { Width = 40, Height = 60, CornerRadius = new CornerRadius(6), Background = Theme.Brush(Theme.Surface2), VerticalAlignment = VerticalAlignment.Center };
        thumb.Children.Add(Images.Remote(item.Poster, item.Blurhash, 60));
        return thumb;
    }

    private FrameworkElement Row(DownloadItem item, bool episode)
    {
        var row = new Grid { MinHeight = episode ? 62 : 84, Padding = new Thickness(18, 8, 18, 8), ColumnSpacing = 16 };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        if (episode)
        {
            var symbol = item.State switch { DownloadItem.States.Running => Glyph.Download, DownloadItem.States.Paused => Glyph.Pause, DownloadItem.States.Done => "", _ => "" };
            var tint = item.State switch { DownloadItem.States.Done => Theme.Good, DownloadItem.States.Failed => Theme.Accent, _ => Theme.Text2 };
            var icon = Glyph.Icon(symbol, 18, tint);
            icon.Width = 40;
            icon.VerticalAlignment = VerticalAlignment.Center;
            row.Children.Add(icon);
        }
        else row.Children.Add(Thumb(item));

        var text = new StackPanel { Spacing = 5, VerticalAlignment = VerticalAlignment.Center };
        var heading = episode && item.Season is { } season && item.Episode is { } ep ? Loc.EpisodeTag(season, ep) : item.Title;
        text.Children.Add(Theme.Text(heading, episode ? 15 : 17, FontWeights.Bold));
        text.Children.Add(Theme.Text(episode ? item.Quality ?? item.Detail : item.Detail, 13, color: Theme.Text3));
        if (item.State is DownloadItem.States.Running or DownloadItem.States.Paused)
        {
            var track = new Grid { Height = 5, CornerRadius = new CornerRadius(2.5), Background = Theme.White(0.14), Margin = new Thickness(0, 2, 0, 0) };
            var fill = new Border { CornerRadius = new CornerRadius(2.5), HorizontalAlignment = HorizontalAlignment.Left, Background = Theme.Brush(item.State == DownloadItem.States.Paused ? Theme.Text3 : Theme.Accent) };
            track.SizeChanged += (_, e) => fill.Width = Math.Max(4, e.NewSize.Width * item.Fraction);
            track.Children.Add(fill);
            text.Children.Add(track);
        }
        Grid.SetColumn(text, 1);
        row.Children.Add(text);

        var status = Theme.Text(Status(item), 13, FontWeights.Medium, item.State == DownloadItem.States.Failed ? Theme.Accent : Theme.Text2);
        status.FlowDirection = FlowDirection.LeftToRight;
        status.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(status, 2);
        row.Children.Add(status);

        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
        if (_asking.Contains(item.Id))
        {
            buttons.Children.Add(Kit.Glass(Loc[S.removeFromList], null, () => { _asking.Remove(item.Id); Downloads.Remove(item.Id); }, height: 32));
            buttons.Children.Add(Kit.Glass(Loc[S.moveToTrash], "", () => { _asking.Remove(item.Id); Downloads.Remove(item.Id, trashing: true); }, height: 32));
            buttons.Children.Add(RowButton("", Loc[S.cancel], () => { _asking.Remove(item.Id); Render(); }));
        }
        else
        {
            switch (item.State)
            {
                case DownloadItem.States.Running:
                    buttons.Children.Add(RowButton(Glyph.Pause, Loc[S.pause], () => Downloads.Pause(item.Id)));
                    break;
                case DownloadItem.States.Paused or DownloadItem.States.Failed:
                    buttons.Children.Add(RowButton("", Loc[S.resume], () => Downloads.Resume(item.Id)));
                    break;
                case DownloadItem.States.Done:
                    if (item.SubtitlePath is not null) buttons.Children.Add(RowButton("", Loc[S.withSubtitle], () => { }, tint: Theme.Good));
                    else if (Downloads.Seeking.Contains(item.Id)) buttons.Children.Add(new ProgressRing { IsActive = true, Width = 18, Height = 18, Margin = new Thickness(8) });
                    else if (item.SubtitlePage is not null) buttons.Children.Add(RowButton(Glyph.Captions, Loc[S.getSubtitle], () => Downloads.FindSubtitle(item.Id)));
                    buttons.Children.Add(RowButton(Glyph.Play, Loc[S.play], () => Downloads.Play(item), bright: true));
                    buttons.Children.Add(RowButton("", Loc[Extra.showInFolder], () => Downloads.Reveal(item)));
                    break;
            }
            buttons.Children.Add(RowButton(Glyph.Close, Loc[S.remove], () =>
            {
                // Only a file that is there raises the question; anything else just leaves the list.
                if (item.State == DownloadItem.States.Done && item.Path is { } path && File.Exists(path)) { _asking.Add(item.Id); Render(); }
                else Downloads.Remove(item.Id);
            }));
        }
        Grid.SetColumn(buttons, 3);
        row.Children.Add(buttons);
        return row;
    }

    private static string Status(DownloadItem item) => item.State switch
    {
        DownloadItem.States.Done => item.Expected > 0 ? DownloadPicker.Size(item.Expected) : "",
        DownloadItem.States.Failed => Loc[S.downloadFailed],
        DownloadItem.States.Running when Downloads.OutsideHours && !Downloads.IsActive(item.Id) => Loc[S.waitingForHours],
        _ => item.Expected > 0 ? $"{DownloadPicker.Size(item.Received)} / {DownloadPicker.Size(item.Expected)}" : DownloadPicker.Size(item.Received),
    };

    private static Button RowButton(string glyph, string help, Action action, bool bright = false, Windows.UI.Color? tint = null)
    {
        var face = new Border { Width = 34, Height = 34, CornerRadius = new CornerRadius(17), Background = bright ? Theme.Brush(Colors.White) : Theme.White(0.08), Child = Glyph.Icon(glyph, 13, bright ? Theme.FocusInk : tint ?? Theme.Text1) };
        var button = Theme.Bare(face, action);
        if (!bright)
        {
            button.PointerEntered += (_, _) => face.Background = Theme.White(0.2);
            button.PointerExited += (_, _) => face.Background = Theme.White(0.08);
        }
        ToolTipService.SetToolTip(button, help);
        Kit.AutomationName(button, help);
        return button;
    }
}
