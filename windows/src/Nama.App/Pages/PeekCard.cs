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

/// <summary>
/// What a poster opens into when the pointer rests on it: the scene, what the title is, and the
/// two things most often wanted of it, without leaving the shelf. As PeekCard in MacKit.swift.
/// </summary>
public sealed class PeekCard : Grid
{
    public const double CardWidth = 344, CardHeight = 372;

    private static AppModel App => AppModel.Shared;
    private static Loc Loc => App.Loc;

    private readonly Post _post;
    private readonly Nav _nav;
    private readonly Action _close;
    private readonly StackPanel _actions = new() { Orientation = Orientation.Horizontal, Spacing = 8 };

    public PeekCard(Post post, Nav nav, Action close)
    {
        _post = post;
        _nav = nav;
        _close = close;
        var hue = PosterColours.Of(post);
        Width = CardWidth;
        Height = CardHeight;
        CornerRadius = new CornerRadius(20);
        Children.Add(new Border { Background = Theme.GlassFill() });
        Children.Add(new Border { Background = Theme.Brush(Theme.Bg1, 0.82) });

        var column = new StackPanel();
        var scene = new Grid { Height = 194 };
        scene.Children.Add(Images.Remote(post.Cover ?? post.Poster.Big ?? post.Poster.Large, post.Poster.Blurhash, CardWidth * 1.5, fallback: post.Poster.Big ?? post.Poster.Large));
        scene.Children.Add(new Border
        {
            Background = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0.5, 0.5), EndPoint = new Windows.Foundation.Point(0.5, 1),
                GradientStops = { new GradientStop { Color = Colors.Transparent, Offset = 0 }, new GradientStop { Color = Theme.Bg1.WithAlpha(0.95), Offset = 1 } },
            },
        });
        var name = Theme.Text(post.Title(Loc.Lang), 20, FontWeights.ExtraBold, lines: 2);
        name.VerticalAlignment = VerticalAlignment.Bottom;
        name.Margin = new Thickness(16, 0, 16, 6);
        scene.Children.Add(name);
        column.Children.Add(scene);

        var body = new StackPanel { Spacing = 10, Padding = new Thickness(16, 8, 16, 16) };
        var facts = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        if (post.ImdbScore is { } score)
        {
            var imdb = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
            imdb.Children.Add(Theme.Text("IMDb", 10, FontWeights.Black, Theme.Hex(0xF5C518)));
            imdb.Children.Add(Theme.Text(Loc.Num(Fmt.Score(score)), 13, FontWeights.Bold));
            foreach (var t in imdb.Children.OfType<TextBlock>()) t.VerticalAlignment = VerticalAlignment.Center;
            facts.Children.Add(imdb);
        }
        facts.Children.Add(Theme.Text(Facts(post), 13, color: Theme.Text2));
        body.Children.Add(facts);
        var plot = Theme.Text(post.Plot(Loc.Lang), 13, color: Theme.Text2, lines: 3);
        plot.LineHeight = 20;
        plot.MinHeight = 62;
        body.Children.Add(plot);
        var row = new Grid();
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.Children.Add(_actions);
        var details = Round(Glyph.ChevronEnd, false, Loc[S.details], () => Open(playing: false));
        Grid.SetColumn(details, 1);
        row.Children.Add(details);
        body.Children.Add(row);
        column.Children.Add(body);
        Children.Add(column);
        Children.Add(new Border { CornerRadius = new CornerRadius(20), BorderThickness = new Thickness(1), BorderBrush = Kit.RimBrush(hue), IsHitTestVisible = false });
        // The card's own light, as on the Mac: a glow in the title's colour and a dark shadow under
        // it. Behind the rounded body, which would otherwise clip them.
        var rounded = new Grid { CornerRadius = new CornerRadius(20) };
        foreach (var child in Children.ToList())
        {
            Children.Remove(child);
            rounded.Children.Add(child);
        }
        CornerRadius = new CornerRadius(0);
        Children.Add(new Glow(new Glow.Look(hue, 0.35f, 56, 20)));
        Children.Add(new Glow(new Glow.Look(Colors.Black, 0.5f, 32, 12)));
        Children.Add(rounded);

        ShowActions();
        App.Changed += OnChanged;
        Unloaded += (_, _) => App.Changed -= OnChanged;
        Tapped += (_, e) =>
        {
            // A click on the card that is not on one of its buttons opens the title.
            if (e.OriginalSource is not FrameworkElement f || !InButton(f)) Open(playing: false);
        };
        PointerExited += (_, _) => _close();
        Opacity = 0;
        Scale = new System.Numerics.Vector3(0.92f, 0.92f, 1);
        this.Springy(0.22);
        Loaded += (_, _) =>
        {
            CenterPoint = new System.Numerics.Vector3((float)CardWidth / 2, (float)CardHeight / 2, 0);
            Opacity = 1;
            Scale = System.Numerics.Vector3.One;
        };
    }

    private void OnChanged(string what)
    {
        if (what == "lists") ShowActions();
    }

    private void ShowActions()
    {
        _actions.Children.Clear();
        _actions.Children.Add(PlayButton());
        if (!App.IsLoggedIn) return;
        foreach (var (empty, filled, group, help) in new[] { (Glyph.Bookmark, Glyph.BookmarkFill, "watchlist", Loc[S.watchlist]), (Glyph.Heart, Glyph.HeartFill, "favorite", Loc[S.favorites]) })
        {
            var listed = App.IsListed(_post, group);
            _actions.Children.Add(Round(listed ? filled : empty, listed, help, () => _ = App.ToggleListAsync(_post, group)));
        }
    }

    /// <summary>The same height as the round buttons beside it, as on the Mac: a white capsule, 38 high.</summary>
    private Button PlayButton()
    {
        var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center };
        var icon = Glyph.Icon(Glyph.Play, 12, Theme.FocusInk);
        icon.VerticalAlignment = VerticalAlignment.Center;
        label.Children.Add(icon);
        var text = Theme.Text(Loc[_post.Resume is not null ? S.resume : S.play], 14, FontWeights.Bold, Theme.FocusInk);
        text.VerticalAlignment = VerticalAlignment.Center;
        label.Children.Add(text);
        var face = new Border { Height = 38, Padding = new Thickness(18, 0, 18, 0), CornerRadius = new CornerRadius(19), Background = Theme.White(0.92), Child = label };
        var button = Theme.Bare(face, () => Open(playing: true));
        button.Tag = "button";
        button.PointerEntered += (_, _) => face.Background = Theme.White(1);
        button.PointerExited += (_, _) => face.Background = Theme.White(0.92);
        Kit.AutomationName(button, text.Text);
        return button;
    }

    private Button Round(string glyph, bool lit, string help, Action click)
    {
        var face = new Border { Width = 38, Height = 38, CornerRadius = new CornerRadius(19), Background = Theme.White(0.12) };
        var icon = Glyph.Icon(glyph, 14, lit ? _post.Tint() : Theme.Text1);
        // "Forward" points the way the language reads.
        icon.MirroredWhenRightToLeft = true;
        icon.VerticalAlignment = VerticalAlignment.Center;
        icon.HorizontalAlignment = HorizontalAlignment.Center;
        face.Child = icon;
        var button = Theme.Bare(face, click);
        button.Tag = "button";
        button.PointerEntered += (_, _) => face.Background = Theme.White(0.2);
        button.PointerExited += (_, _) => face.Background = Theme.White(0.12);
        ToolTipService.SetToolTip(button, help);
        Kit.AutomationName(button, help);
        return button;
    }

    private static bool InButton(FrameworkElement element)
    {
        for (DependencyObject? at = element; at is not null and not PeekCard; at = VisualTreeHelper.GetParent(at))
            if (at is Button) return true;
        return false;
    }

    private void Open(bool playing)
    {
        _close();
        _nav.Open(_post, playing);
    }

    /// <summary>The year, how long it runs and what kind of thing it is, in one line.</summary>
    private static string Facts(Post post)
    {
        var parts = new List<string>();
        if (post.Year is { } year) parts.Add(Loc.Num(year));
        if (Fmt.Runtime(post.RuntimeMinutes, Loc.Lang) is { } runtime) parts.Add(runtime);
        parts.AddRange(post.Genres.Take(2).Select(g => g.Name));
        return string.Join(" · ", parts);
    }
}
