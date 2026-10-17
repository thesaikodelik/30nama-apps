using Microsoft.UI;
using Microsoft.UI.Text;
using FontWeight = Windows.UI.Text.FontWeight;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Nama.App.Model;
using Nama.Core;
using Color = Windows.UI.Color;

namespace Nama.App.UI;

/// <summary>Glyphs from the system's icon font, standing in for the Mac app's SF Symbols.</summary>
public static class Glyph
{
    public const string Play = "\uF5B0", Pause = "\uE769", Info = "\uE946", Search = "\uE721", Settings = "\uE713",
        Download = "\uE896", Back = "\uE72B", Forward = "\uE72A", ChevronStart = "\uE76B", ChevronEnd = "\uE76C",
        Person = "\uE77B", Close = "\uE711", Heart = "\uEB51", HeartFill = "\uEB52", Bookmark = "\uE8A4", BookmarkFill = "\uE735",
        Star = "\uE734", StarFill = "\uE735", Globe = "\uE774", Volume = "\uE767", Mute = "\uE74F", FullScreen = "\uE740",
        ExitFullScreen = "\uE73F", Captions = "\uE7F0", Rewind = "\uED3C", FastForward = "\uED3D", Next = "\uE893",
        Speed = "\uEC4A", Wifi = "\uE871", Check = "\uE73E", List = "\uE8FD", Audio = "\uE8D6", Quality = "\uE9E9",
        Video = "\uE714";

    public static FontIcon Icon(string glyph, double size, Color? color = null) => new()
    {
        Glyph = glyph,
        FontSize = size,
        FontFamily = new FontFamily("Segoe Fluent Icons,Segoe MDL2 Assets"),
        Foreground = Theme.Brush(color ?? Theme.Text1),
        IsTextScaleFactorEnabled = false,
    };
}

public static class Kit
{
    private static Loc Loc => AppModel.Shared.Loc;

    /// <summary>A label with an icon before it, in the reading direction.</summary>
    public static StackPanel Label(string text, string? glyph, double size, FontWeight weight, Color color)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        if (glyph is not null) row.Children.Add(Glyph.Icon(glyph, size * 0.9, color));
        row.Children.Add(Theme.Text(text, size, weight, color));
        return row;
    }

    // MARK: Buttons

    /// <summary>The one bright button on a screen: play, sign in. Its glow is the colour of the title it is about.</summary>
    public static Button Primary(string text, string? glyph, Action click, Color? glow = null)
    {
        var face = new Border
        {
            Background = Theme.Brush(Colors.White),
            CornerRadius = new CornerRadius(23),
            Height = 46,
            Padding = new Thickness(26, 0, 26, 0),
            Child = Label(text, glyph, 16, FontWeights.Bold, Theme.FocusInk),
            Translation = new System.Numerics.Vector3(0, 0, 24),
            Shadow = new ThemeShadow(),
        }.Springy(0.2);
        var button = Theme.Bare(face, click);
        AutomationName(button, text);
        return Hovering(button, face, 1.04);
    }

    /// <summary>Everything else: a pane of glass that brightens under the pointer.</summary>
    public static Button Glass(string? text, string? glyph, Action click, double height = 46, Color? rim = null)
    {
        var tint = new Border { Background = Theme.White(0.1), CornerRadius = new CornerRadius(height / 2) };
        var content = text is null
            ? (FrameworkElement)Glyph.Icon(glyph!, height * 0.36)
            : Label(text, glyph, 16, FontWeights.SemiBold, Theme.Text1);
        content.HorizontalAlignment = HorizontalAlignment.Center;
        content.Margin = text is null ? new Thickness(0) : new Thickness(height * 0.5, 0, height * 0.5, 0);
        var face = new Grid { Height = height, MinWidth = height }.Springy(0.2);
        face.Children.Add(new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(height / 2) });
        face.Children.Add(tint);
        face.Children.Add(new Border
        {
            CornerRadius = new CornerRadius(height / 2),
            BorderThickness = new Thickness(1),
            BorderBrush = rim is { } r ? RimBrush(r) : Theme.GlassRim,
        });
        face.Children.Add(content);
        var button = Theme.Bare(face, click);
        if (text is not null) AutomationName(button, text);
        button.PointerEntered += (_, _) => tint.Background = Theme.White(0.2);
        button.PointerExited += (_, _) => tint.Background = Theme.White(0.1);
        return Hovering(button, face, 1.03);
    }

    /// <summary>A rim in the colour of the title on stage, brightest at the top, for a pane of glass.</summary>
    public static Brush RimBrush(Color hue) => new LinearGradientBrush
    {
        StartPoint = new Windows.Foundation.Point(0.5, 0),
        EndPoint = new Windows.Foundation.Point(0.5, 1),
        GradientStops = { new GradientStop { Color = hue.WithAlpha(0.6), Offset = 0 }, new GradientStop { Color = hue.WithAlpha(0.1), Offset = 1 } },
    };

    /// <summary>Grows the face a little under the pointer and shrinks it while pressed.</summary>
    public static Button Hovering(Button button, FrameworkElement face, double grow)
    {
        var over = false;
        button.PointerEntered += (_, _) => { over = true; face.ScaleTo(grow); };
        button.PointerExited += (_, _) => { over = false; face.ScaleTo(1); };
        button.AddHandler(UIElement.PointerPressedEvent, new Microsoft.UI.Xaml.Input.PointerEventHandler((_, _) => face.ScaleTo(0.96)), true);
        button.AddHandler(UIElement.PointerReleasedEvent, new Microsoft.UI.Xaml.Input.PointerEventHandler((_, _) => face.ScaleTo(over ? grow : 1)), true);
        return button;
    }

    /// <summary>A small capsule that is one choice among a few: a season, a sort order.</summary>
    public static Button Pill(string title, bool selected, Action click)
    {
        var face = new Border
        {
            Height = 30,
            Padding = new Thickness(13, 0, 13, 0),
            CornerRadius = new CornerRadius(15),
            Background = selected ? Theme.Brush(Colors.White) : Theme.White(0.1),
            Child = Theme.Text(title, 13, FontWeights.SemiBold, selected ? Theme.FocusInk : Theme.Text1),
        };
        ((TextBlock)face.Child).VerticalAlignment = VerticalAlignment.Center;
        var button = Theme.Bare(face, click);
        AutomationName(button, title);
        if (!selected)
        {
            button.PointerEntered += (_, _) => face.Background = Theme.White(0.2);
            button.PointerExited += (_, _) => face.Background = Theme.White(0.1);
        }
        return button;
    }

    // MARK: Facts about a title

    public static Border Chip(UIElement content) => new()
    {
        Height = 26,
        Padding = new Thickness(10, 0, 10, 0),
        CornerRadius = new CornerRadius(13),
        Background = Theme.White(0.12),
        Child = content,
        VerticalAlignment = VerticalAlignment.Center,
    };

    private static Border ChipText(string text) => Chip(Centered(Theme.Text(text, 13, FontWeights.SemiBold)));

    private static T Centered<T>(T element) where T : FrameworkElement
    {
        element.VerticalAlignment = VerticalAlignment.Center;
        return element;
    }

    /// <summary>A score with the name of where it comes from, in that site's colour.</summary>
    private static Border ScoreChip(string name, string value, Color color)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, FlowDirection = FlowDirection.LeftToRight };
        row.Children.Add(Centered(Theme.Text(name, 11, FontWeights.ExtraBold, color)));
        row.Children.Add(Centered(Theme.Text(value, 13, FontWeights.SemiBold)));
        return Chip(row);
    }

    /// <summary>Scores, year, length, age rating and genres on one line.</summary>
    public static StackPanel MetaRow(Post post)
    {
        var loc = Loc;
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        if (post.ImdbScore is { } imdb) row.Children.Add(ScoreChip("IMDb", Fmt.Score(imdb), Theme.Hex(0xF5C518)));
        if (post.RottenTomatoes is { } rt) row.Children.Add(ScoreChip("RT", $"{rt}%", Theme.Hex(0xFA5A3C)));
        if (post.Metacritic is { } mc) row.Children.Add(ScoreChip("MC", $"{mc}", Theme.Hex(0x66CC33)));
        if (post.FanScore is { } fan) row.Children.Add(ScoreChip(fan.Source, Fmt.Score(fan.Value), Theme.Hex(0x6C8CFF)));
        if (post.SiteScore is { } site)
        {
            var star = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 5 };
            star.Children.Add(Centered(Glyph.Icon(Glyph.StarFill, 10, Theme.Good)));
            star.Children.Add(Centered(Theme.Text(loc.Num(Fmt.Score(site)), 13, FontWeights.SemiBold)));
            row.Children.Add(Chip(star));
        }
        if (post.Year is { } year)
            row.Children.Add(ChipText(loc.Num(post.YearEnd is { } end && end != year ? $"{year}–{end}" : $"{year}")));
        if (Fmt.Runtime(post.RuntimeMinutes, loc.Lang) is { } runtime) row.Children.Add(ChipText(runtime));
        if (post.Age is { } age)
        {
            var chip = ChipText(age);
            chip.FlowDirection = FlowDirection.LeftToRight;
            row.Children.Add(chip);
        }
        if (post.Genres.Count > 0)
        {
            var genres = Theme.Text(string.Join(loc.Lang == Lang.En ? ", " : "، ", post.Genres.Take(3).Select(g => g.Name)), 14, color: Theme.Text2);
            genres.Margin = new Thickness(4, 0, 0, 0);
            row.Children.Add(Centered(genres));
        }
        return row;
    }

    // MARK: Cards

    /// <summary>A poster that lifts toward the pointer and shows its name.</summary>
    /// <param name="stars">A star rating to pin on the corner, as on the profile's own shelf.</param>
    /// <param name="note">A line that stays on the card whatever the pointer does: the day an episode is due.</param>
    public static Button Poster(Post post, Action click, double width = Theme.PosterWidth, double? stars = null, string? note = null)
    {
        var loc = Loc;
        var height = width * 1.5;
        // The grey tile only when there is no blurred stand-in to hold the place: under a picture it
        // shows through the rounded edge as a light line.
        var face = new Grid { Width = width, Height = height, CornerRadius = new CornerRadius(Theme.Radius), Background = post.Poster.Blurhash is null ? Theme.Brush(Theme.Surface2) : null }.Springy(0.22);
        face.Children.Add(Images.Remote(post.Poster.Url(width * 1.1 * 1.5), post.Poster.Blurhash, width * 1.5));

        var caption = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom, Padding = new Thickness(10, 26, 10, 10), Spacing = 2, Opacity = 0 }.Springy(0.18);
        caption.Background = new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
            GradientStops = { new GradientStop { Color = Colors.Transparent, Offset = 0 }, new GradientStop { Color = Theme.Hex(0, 0.88), Offset = 1 } },
        };
        caption.Children.Add(Theme.Text(post.Title(loc.Lang), 13, FontWeights.Bold, Colors.White, lines: 2));
        if (post.Year is { } year) caption.Children.Add(Theme.Text(loc.Num(year), 11, FontWeights.Medium, Theme.Hex(0xFFFFFF, 0.7)));
        face.Children.Add(caption);

        if (note is not null)
        {
            var line = Theme.Text(note, 11, FontWeights.Bold, Colors.White);
            line.HorizontalAlignment = HorizontalAlignment.Center;
            line.VerticalAlignment = VerticalAlignment.Center;
            // In the direction of the words in it; only Persian has its own, elsewhere the day is in English.
            line.FlowDirection = loc.Lang == Lang.Fa ? FlowDirection.RightToLeft : FlowDirection.LeftToRight;
            face.Children.Add(new Border { MinHeight = 26, VerticalAlignment = VerticalAlignment.Bottom, Background = Theme.Black(0.72), Padding = new Thickness(8, 0, 8, 0), Child = line });
        }
        if (stars is { } rated)
        {
            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
            row.Children.Add(Centered(Glyph.Icon(Glyph.StarFill, 10, Theme.Hex(0xF5C518))));
            row.Children.Add(Centered(Theme.Text(loc.Num(Fmt.Score(rated)), 12, FontWeights.Bold, Colors.White)));
            face.Children.Add(new Border
            {
                Height = 22, Padding = new Thickness(8, 0, 8, 0), Margin = new Thickness(8), CornerRadius = new CornerRadius(11),
                Background = Theme.Black(0.72), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top, Child = row,
            });
        }
        else if (post.ComingSoon)
        {
            var badge = new Border
            {
                Height = 22, Padding = new Thickness(8, 0, 8, 0), Margin = new Thickness(8), CornerRadius = new CornerRadius(11),
                Background = Theme.Black(0.65), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top,
                Child = Centered(Theme.Text(loc[S.soon], 11, FontWeights.Bold)),
            };
            face.Children.Add(badge);
        }
        // A rim only under the pointer; at rest it read as a light line round the dark posters.
        var rim = new Border { CornerRadius = new CornerRadius(Theme.Radius), BorderThickness = new Thickness(0), BorderBrush = Theme.White(0) };
        face.Children.Add(rim);

        var button = Theme.Bare(face, () =>
        {
            // The poster leaves from here for the title's page.
            PosterEffects.Launched(post, face);
            click();
        });
        PosterEffects.Tilt(button, face);
        PosterEffects.Peek(button, face, post);
        PosterEffects.Glow(button, face, post.Tint());
        AutomationName(button, post.Year is { } y ? $"{post.Title(loc.Lang)}, {loc.Num(y)}" : post.Title(loc.Lang));
        button.PointerEntered += (_, _) =>
        {
            caption.Opacity = 1;
            rim.BorderBrush = Theme.White(0.9);
            rim.BorderThickness = new Thickness(2);
            Canvas.SetZIndex(button, 1);
        };
        button.PointerExited += (_, _) =>
        {
            caption.Opacity = 0;
            rim.BorderBrush = Theme.White(0);
            rim.BorderThickness = new Thickness(0);
            Canvas.SetZIndex(button, 0);
        };
        return Hovering(button, face, 1.07);
    }

    /// <summary>A poster with its place in the top ten beside it, in figures as tall as the poster.</summary>
    public static FrameworkElement Ranked(int rank, Post post, Action click)
    {
        var tint = post.Tint();
        var figure = Theme.Text(Loc.Num(rank), 170, FontWeights.ExtraBold);
        figure.FlowDirection = FlowDirection.LeftToRight;
        figure.VerticalAlignment = VerticalAlignment.Bottom;
        figure.LineHeight = 170;
        figure.LineStackingStrategy = LineStackingStrategy.BlockLineHeight;
        figure.Margin = new Thickness(0, 0, -24, -14);
        Brush Fill(Color top, Color bottom) => new LinearGradientBrush
        {
            StartPoint = new Windows.Foundation.Point(0.5, 0), EndPoint = new Windows.Foundation.Point(0.5, 1),
            GradientStops = { new GradientStop { Color = top, Offset = 0 }, new GradientStop { Color = bottom, Offset = 1 } },
        };
        figure.Foreground = Fill(Theme.Text1.WithAlpha(0.5), Theme.Text1.WithAlpha(0.07));
        var card = Poster(post, click);
        card.PointerEntered += (_, _) => figure.Foreground = Fill(tint, tint.WithAlpha(0.4));
        card.PointerExited += (_, _) => figure.Foreground = Fill(Theme.Text1.WithAlpha(0.5), Theme.Text1.WithAlpha(0.07));
        var row = new StackPanel { Orientation = Orientation.Horizontal, Height = Theme.PosterWidth * 1.5 };
        row.Children.Add(figure);
        row.Children.Add(card);
        return row;
    }

    /// <summary>A wide card for something half-watched: the scene, how far in, and what is left.</summary>
    public static FrameworkElement Continue(Post post, Action click, Action? remove)
    {
        var loc = Loc;
        const double w = 288, h = 162;
        var face = new Grid { Width = w, Height = h, CornerRadius = new CornerRadius(Theme.Radius + 2), Background = post.Poster.Blurhash is null ? Theme.Brush(Theme.Surface2) : null }.Springy(0.22);
        face.Children.Add(Images.Remote(post.Cover ?? post.Poster.Big ?? post.Poster.Large, post.Poster.Blurhash, w * 1.5));
        face.Children.Add(new Border
        {
            Background = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0.5, 0.5), EndPoint = new Windows.Foundation.Point(0.5, 1),
                GradientStops = { new GradientStop { Color = Colors.Transparent, Offset = 0 }, new GradientStop { Color = Theme.Hex(0, 0.85), Offset = 1 } },
            },
        });
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(12), Spacing = 4 };
        text.Children.Add(Theme.Text(post.Title(loc.Lang), 15, FontWeights.Bold, Colors.White));
        var parts = new List<string>();
        if (post.Resume is { Season: { } season, Episode: { } episode }) parts.Add(loc.EpisodeTag(season, episode));
        if (post.Resume?.RemainingMinutes is { } left) parts.Add($"{loc.Num(left)} {loc[S.minLeft]}");
        if (parts.Count > 0) text.Children.Add(Theme.Text(string.Join(" · ", parts), 12, FontWeights.Medium, Theme.Hex(0xFFFFFF, 0.72)));
        var track = new Grid { Height = 4, Margin = new Thickness(0, 4, 0, 0) };
        track.Children.Add(new Border { CornerRadius = new CornerRadius(2), Background = Theme.White(0.22) });
        var bar = new Border { CornerRadius = new CornerRadius(2), Background = Theme.Brush(Theme.Accent), HorizontalAlignment = HorizontalAlignment.Left };
        track.SizeChanged += (_, e) => bar.Width = Math.Max(6, e.NewSize.Width * (post.Resume?.Progress ?? 0));
        track.Children.Add(bar);
        text.Children.Add(track);
        face.Children.Add(text);

        var play = new Border
        {
            Width = 52, Height = 52, CornerRadius = new CornerRadius(26), Background = Theme.Brush(Colors.White), Opacity = 0,
            HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
            Child = Glyph.Icon(Glyph.Play, 20, Theme.FocusInk),
        }.Springy(0.18);
        face.Children.Add(play);
        var rim = new Border { CornerRadius = new CornerRadius(Theme.Radius + 2), BorderThickness = new Thickness(0), BorderBrush = Theme.White(0) };
        face.Children.Add(rim);

        var button = Theme.Bare(face, click);
        AutomationName(button, post.Title(loc.Lang));
        // As the Mac's card for something half-watched: it leans 3 degrees to the pointer and
        // glows in the title's colour under it.
        PosterEffects.Tilt(button, face, 4.5);
        PosterEffects.Glow(button, face, new Glow.Look(Colors.Black, 0, 0, 0), new Glow.Look(post.Tint(), 0.5f, 32, 14), 1.04f);
        var host = new Grid();
        host.Children.Add(Hovering(button, face, 1.04));
        Button? close = null;
        if (remove is not null)
        {
            // Off the card itself, so a click on it does not also open the title.
            close = Theme.Bare(new Border
            {
                Width = 26, Height = 26, CornerRadius = new CornerRadius(13), Background = Theme.Black(0.6),
                BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.25), Child = Glyph.Icon(Glyph.Close, 10, Colors.White),
            }, remove);
            close.HorizontalAlignment = HorizontalAlignment.Right;
            close.VerticalAlignment = VerticalAlignment.Top;
            close.Margin = new Thickness(10);
            close.Opacity = 0;
            close.Springy(0.15);
            ToolTipService.SetToolTip(close, loc[S.removeFromContinue]);
            AutomationName(close, loc[S.removeFromContinue]);
            host.Children.Add(close);
            var menu = new MenuFlyout();
            var item = new MenuFlyoutItem { Text = loc[S.removeFromContinue] };
            item.Click += (_, _) => remove();
            menu.Items.Add(item);
            button.ContextFlyout = menu;
        }
        host.PointerEntered += (_, _) =>
        {
            play.Opacity = 1;
            rim.BorderBrush = Theme.White(0.9);
            rim.BorderThickness = new Thickness(2);
            if (close is not null) close.Opacity = 1;
            Canvas.SetZIndex(host, 1);
        };
        host.PointerExited += (_, _) =>
        {
            play.Opacity = 0;
            rim.BorderBrush = Theme.White(0);
            rim.BorderThickness = new Thickness(0);
            if (close is not null) close.Opacity = 0;
            Canvas.SetZIndex(host, 0);
        };
        return host;
    }

    // MARK: Shelf

    /// <summary>A titled row that scrolls sideways, with arrows that appear under the pointer for a mouse without a wheel.</summary>
    /// <summary>
    /// Lets the mouse wheel over a row that scrolls sideways move the page up and down, as on the
    /// Mac: Windows otherwise turns the wheel into the row's own sideways scroll. A touchpad's
    /// sideways swipe, or Shift with the wheel, still moves the row.
    /// </summary>
    public static ScrollViewer Sideways(ScrollViewer row)
    {
        if (row.Content is not UIElement content) return row;
        content.PointerWheelChanged += (_, e) =>
        {
            var point = e.GetCurrentPoint(content);
            if (point.Properties.IsHorizontalMouseWheel || e.KeyModifiers.HasFlag(Windows.System.VirtualKeyModifiers.Shift)) return;
            if (Upward(row) is not { } page) return;
            e.Handled = true;
            page.ChangeView(null, Math.Clamp(page.VerticalOffset - point.Properties.MouseWheelDelta, 0, page.ScrollableHeight), null);
        };
        return row;
    }

    /// <summary>The nearest scroller above that goes up and down.</summary>
    private static ScrollViewer? Upward(DependencyObject from)
    {
        for (var at = VisualTreeHelper.GetParent(from); at is not null; at = VisualTreeHelper.GetParent(at))
            if (at is ScrollViewer { VerticalScrollMode: not ScrollMode.Disabled } page && page.ScrollableHeight > 0) return page;
        return null;
    }

    public static FrameworkElement Shelf(string title, IEnumerable<FrameworkElement> cards, Action? more = null, double step = 5)
    {
        var column = new StackPanel { Spacing = 4 };
        FrameworkElement heading;
        if (more is not null)
        {
            var label = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
            label.Children.Add(Theme.Text(title, 20, FontWeights.Bold));
            // The way on, shown under the pointer so the name reads as a name the rest of the time.
            var chevron = Centered(Glyph.Icon(Glyph.ChevronEnd, 14));
            chevron.Opacity = 0;
            chevron.Springy(0.15);
            label.Children.Add(chevron);
            var link = Theme.Bare(label, more);
            link.PointerEntered += (_, _) => chevron.Opacity = 1;
            link.PointerExited += (_, _) => chevron.Opacity = 0;
            heading = link;
        }
        else heading = Theme.Text(title, 20, FontWeights.Bold);
        heading.HorizontalAlignment = HorizontalAlignment.Left;
        heading.Margin = new Thickness(Theme.Edge, 0, Theme.Edge, 0);
        column.Children.Add(heading);

        // Room inside the row for a card grown under the pointer and the glow it throws, which the
        // row would otherwise cut off; given back outside it, so the page does not move.
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 16, Padding = new Thickness(Theme.Edge, 30, Theme.Edge, 60) };
        foreach (var card in cards) row.Children.Add(card);
        var scroller = Sideways(new ScrollViewer
        {
            Content = row,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Hidden,
            HorizontalScrollMode = ScrollMode.Enabled,
            VerticalScrollMode = ScrollMode.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Disabled,
        });
        var area = new Grid { Margin = new Thickness(0, -12, 0, -42) };
        area.Children.Add(scroller);
        Button Arrow(int direction)
        {
            var arrow = Glass(null, direction < 0 ? Glyph.ChevronStart : Glyph.ChevronEnd, () =>
            {
                var width = row.Children.Count > 0 ? ((FrameworkElement)row.Children[0]).ActualWidth + 16 : 300;
                scroller.ChangeView(Math.Max(0, scroller.HorizontalOffset + direction * width * step), null, null);
            }, height: 38);
            arrow.HorizontalAlignment = direction < 0 ? HorizontalAlignment.Left : HorizontalAlignment.Right;
            arrow.VerticalAlignment = VerticalAlignment.Center;
            arrow.Margin = new Thickness(8, 0, 8, 0);
            arrow.Opacity = 0;
            arrow.IsHitTestVisible = false;
            AutomationName(arrow, Loc[direction < 0 ? S.rowPrevious : S.rowNext]);
            return arrow.Springy(0.15);
        }
        var back = Arrow(-1);
        var on = Arrow(1);
        area.Children.Add(back);
        area.Children.Add(on);
        void ShowArrows(bool show)
        {
            var atStart = scroller.HorizontalOffset < 2;
            var atEnd = scroller.HorizontalOffset > scroller.ScrollableWidth - 2;
            back.Opacity = show && !atStart ? 1 : 0;
            back.IsHitTestVisible = show && !atStart;
            on.Opacity = show && !atEnd ? 1 : 0;
            on.IsHitTestVisible = show && !atEnd;
        }
        var hovering = false;
        area.PointerEntered += (_, _) => { hovering = true; ShowArrows(true); };
        area.PointerExited += (_, _) => { hovering = false; ShowArrows(false); };
        scroller.ViewChanged += (_, _) => ShowArrows(hovering);
        column.Children.Add(area);
        return column;
    }

    // MARK: States

    public static FrameworkElement Error(Action retry, string? message = null)
    {
        var column = new StackPanel { Spacing = 18, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        var icon = Glyph.Icon(Glyph.Wifi, 44, Theme.Text3);
        icon.HorizontalAlignment = HorizontalAlignment.Center;
        column.Children.Add(icon);
        var text = Theme.Text(message ?? Loc[S.error], 18, FontWeights.Medium, Theme.Text2);
        text.HorizontalAlignment = HorizontalAlignment.Center;
        column.Children.Add(text);
        var button = Glass(Loc[S.retry], null, retry);
        button.HorizontalAlignment = HorizontalAlignment.Center;
        column.Children.Add(button);
        return column;
    }

    public static ProgressRing Spinner() => new()
    {
        IsActive = true, Width = 40, Height = 40, Foreground = Theme.Brush(Theme.Text2),
        HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center,
    };

    /// <summary>The backdrop of a screen that has no title of its own to show.</summary>
    public static Grid Ambient()
    {
        var g = new Grid { Background = Theme.Brush(Theme.Bg0) };
        g.Children.Add(new Rectangle { Fill = Radial(Theme.Accent.WithAlpha(0.16), new Windows.Foundation.Point(1, 0), 0.9) });
        g.Children.Add(new Rectangle { Fill = Radial(Theme.Hex(0x2B3A67, 0.25), new Windows.Foundation.Point(0, 1), 0.8) });
        return g;
    }

    public static RadialGradientBrush Radial(Color color, Windows.Foundation.Point center, double radius) => new()
    {
        Center = center,
        GradientOrigin = center,
        RadiusX = radius,
        RadiusY = radius * 1.6,
        MappingMode = BrushMappingMode.RelativeToBoundingBox,
        GradientStops = { new GradientStop { Color = color, Offset = 0 }, new GradientStop { Color = color.WithAlpha(0), Offset = 1 } },
    };

    public static void AutomationName(UIElement element, string name) =>
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(element, name);
}
