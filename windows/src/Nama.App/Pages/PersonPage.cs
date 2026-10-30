using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;
using Windows.UI;

namespace Nama.App.Pages;

/// <summary>
/// An actor clicked on a title page: who they are, as the 30nama website has it (picture, names,
/// birth, the biography 30nama wrote), and their titles newest first. Only the site API has this.
/// It finds a person by 30nama's own id; a cast member without one is found by the IMDb id instead,
/// and then there are only the titles. As PersonView on the Mac.
/// </summary>
public sealed class PersonPage : Grid
{
    private static AppModel App => AppModel.Shared;
    private static Loc Loc => App.Loc;

    private readonly Person _person;
    private readonly Nav _nav;
    private readonly ScrollViewer _scroller = new() { VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled };
    private readonly Grid _header = new();
    private readonly TextBlock _titlesHeading;
    private readonly WrapRow _wall = new(20, unmirrored: true);
    private readonly Grid _footer = new() { MinHeight = 60 };
    private readonly HashSet<int> _known = [];
    private PersonDetails? _details;
    private bool _wholeBiography;
    private int _page, _pages = 1;
    private bool _loading, _failed, _done;

    public PersonPage(Person person, Nav nav)
    {
        _person = person;
        _nav = nav;
        Children.Add(Kit.Ambient());
        _titlesHeading = Theme.Text(Loc[S.filmography], 22, FontWeights.Bold);
        _titlesHeading.Visibility = Visibility.Collapsed;
        var column = new StackPanel { Spacing = 28, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };
        column.Children.Add(_header);
        column.Children.Add(_titlesHeading);
        column.Children.Add(_wall);
        column.Children.Add(_footer);
        _scroller.Content = column;
        Children.Add(_scroller);
        _scroller.ViewChanged += (_, _) =>
        {
            if (_scroller.ScrollableHeight - _scroller.VerticalOffset < Theme.Prefetch) _ = LoadNextAsync();
        };
        RenderHeader();
        _ = LoadNextAsync();
    }

    // MARK: Who they are

    /// <summary>
    /// As the site lays it out: a card at the side with the picture and the facts, and the biography
    /// beside it in a column narrow enough to read.
    /// </summary>
    private void RenderHeader()
    {
        _header.Children.Clear();
        _header.ColumnDefinitions.Clear();
        _header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        _header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        _header.ColumnSpacing = 36;
        _header.Children.Add(Card());

        var side = new StackPanel { Spacing = 10, MaxWidth = 760, HorizontalAlignment = HorizontalAlignment.Left };
        side.Children.Add(Theme.Text(_details?.Name ?? _person.Name, 36, FontWeights.Bold, lines: 2));
        if (_details?.PersianName is { } persian && persian != _details.Name)
            side.Children.Add(Theme.Text(persian, 20, FontWeights.Medium, Theme.Text2));
        if (_details?.Biography is { Count: > 0 } bio) side.Children.Add(Biography(bio));
        SetColumn(side, 1);
        _header.Children.Add(side);
    }

    private FrameworkElement Card()
    {
        var card = new StackPanel
        {
            Width = 260,
            VerticalAlignment = VerticalAlignment.Top,
            CornerRadius = new CornerRadius(18),
            Background = new SolidColorBrush(Color.FromArgb(13, 255, 255, 255)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(20, 255, 255, 255)),
            BorderThickness = new Thickness(1),
        };
        var face = new Grid { Height = 360, Background = Theme.Brush(Theme.Surface2) };
        var silhouette = Glyph.Icon(Glyph.Person, 54, Theme.Text3);
        silhouette.HorizontalAlignment = HorizontalAlignment.Center;
        face.Children.Add(silhouette);
        if ((_details?.Picture ?? _person.Poster) is { } p) face.Children.Add(Images.Remote(p.Url(260), p.Blurhash, 260 * 1.5));
        card.Children.Add(face);

        var facts = Facts();
        if (facts.Count == 0 && _details?.Imdb is null) return card;
        var below = new StackPanel { Spacing = 12, Padding = new Thickness(18) };
        foreach (var (label, value) in facts)
        {
            var fact = new StackPanel { Spacing = 3 };
            fact.Children.Add(Theme.Text(label, 12, FontWeights.SemiBold, Theme.Text3));
            fact.Children.Add(Theme.Text(value, 15, FontWeights.Medium, lines: 3));
            below.Children.Add(fact);
        }
        if (_details?.Imdb is { } link)
        {
            var imdb = Kit.Glass(Loc[S.imdbProfile], "", () => _ = Windows.System.Launcher.LaunchUriAsync(link), height: 38);
            imdb.HorizontalAlignment = HorizontalAlignment.Stretch;
            imdb.Margin = new Thickness(0, 4, 0, 0);
            below.Children.Add(imdb);
        }
        card.Children.Add(below);
        return card;
    }

    /// <summary>The facts the site keeps, labelled. The site writes the values in Persian whatever the interface's language.</summary>
    private List<(string Label, string Value)> Facts()
    {
        var facts = new List<(string, string)>();
        if (_details is not { } d) return facts;
        if (d.FullName is { } full && full != d.Name) facts.Add((Loc[S.fullName], full));
        if (d.Birthdate is { } born) facts.Add((Loc[S.born], born));
        if (d.Deathdate is { } died) facts.Add((Loc[S.died], died));
        if (d.Age is { } age) facts.Add((Loc[S.personAge], age));
        if (d.Profession is { } job) facts.Add((Loc[S.profession], job));
        return facts;
    }

    /// <summary>
    /// The biography is written in Persian, so it reads right to left whatever the interface's
    /// language. Its short lines with no full stop are the headings 30nama put in it. Folded, it
    /// shows its start and fades out above "More".
    /// </summary>
    private FrameworkElement Biography(IReadOnlyList<string> paragraphs)
    {
        var long_ = SiteText.IsLong(paragraphs);
        var folded = long_ && !_wholeBiography;
        var shown = folded ? SiteText.Opening(paragraphs) : paragraphs;
        var block = new StackPanel { Spacing = 14, Margin = new Thickness(0, 14, 0, 0), FlowDirection = FlowDirection.RightToLeft };
        var text = new StackPanel { Spacing = 14 };
        foreach (var paragraph in shown)
        {
            var t = SiteText.IsHeading(paragraph)
                ? Theme.Text(paragraph, 17, FontWeights.Bold, lines: 0)
                : Theme.Text(paragraph, 16, color: Theme.Text2, lines: 0);
            t.TextTrimming = TextTrimming.None;
            t.TextAlignment = TextAlignment.Left;
            if (!SiteText.IsHeading(paragraph))
            {
                t.LineHeight = 16 + 8 + 8;
                t.LineStackingStrategy = LineStackingStrategy.BlockLineHeight;
            }
            text.Children.Add(t);
        }
        if (folded && text.Children.LastOrDefault() is TextBlock last)
        {
            // The fold: the last paragraph shown fades out into the page.
            var colour = Theme.Text2;
            last.Foreground = new LinearGradientBrush
            {
                StartPoint = new Windows.Foundation.Point(0.5, 0),
                EndPoint = new Windows.Foundation.Point(0.5, 1),
                GradientStops =
                {
                    new GradientStop { Color = colour, Offset = 0 },
                    new GradientStop { Color = Color.FromArgb(0, colour.R, colour.G, colour.B), Offset = 1 },
                },
            };
        }
        block.Children.Add(text);
        if (long_)
        {
            var more = Kit.Glass(_wholeBiography ? Loc[S.readLess] : Loc[S.readMore], null, () =>
            {
                _wholeBiography = !_wholeBiography;
                RenderHeader();
            }, height: 34);
            more.HorizontalAlignment = HorizontalAlignment.Left;
            block.Children.Add(more);
        }
        return block;
    }

    // MARK: Their titles

    private async Task LoadNextAsync()
    {
        if (_loading || _page >= _pages) return;
        if (_person.SiteId is null && _person.Imdb is null) return;
        _loading = true;
        _failed = false;
        RenderFooter();
        try
        {
            PostPage next;
            if (_person.SiteId is { } id)
            {
                var found = PersonDetails.Parse(await App.Api.SiteAsync(SiteQuery.Person(id, _page + 1))) ?? throw new InvalidDataException("person");
                if (_details is null)
                {
                    _details = found;
                    RenderHeader();
                }
                next = found.Titles;
            }
            else
            {
                var (path, form) = SiteQuery.Filmography(_person.Imdb!, _page + 1);
                next = SitePosts.Page(await App.Api.SiteAsync(path, form));
            }
            foreach (var post in next.Posts.Where(p => _known.Add(p.Id)))
                _wall.Add(Kit.Poster(post, () => _nav.Open(post)));
            _page = next.Page;
            _pages = next.Pages;
            _done = true;
            _titlesHeading.Visibility = Visibility.Visible;
        }
        catch (Exception) { _failed = true; }
        finally
        {
            _loading = false;
            RenderFooter();
        }
        // A wall shorter than the window gives nothing to scroll: the next page is fetched at once.
        if (!_failed && _scroller.ScrollableHeight < Theme.Prefetch && _page < _pages) await LoadNextAsync();
    }

    private void RenderFooter()
    {
        _footer.Children.Clear();
        if (_failed)
        {
            _footer.Children.Add(Kit.SiteRetry(() => _ = LoadNextAsync()));
        }
        else if (_loading) _footer.Children.Add(Kit.Spinner());
        else if (_done && _known.Count == 0)
        {
            var none = Theme.Text(Loc[S.noMatches], 16, FontWeights.Medium, Theme.Text2);
            none.HorizontalAlignment = HorizontalAlignment.Center;
            _footer.Children.Add(none);
        }
    }
}
