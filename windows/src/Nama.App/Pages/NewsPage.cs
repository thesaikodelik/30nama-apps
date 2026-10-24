using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// 30nama's news, its reviews and its own announcements, as the website has them, newest first.
/// Only the site API has them, so this page is reached only when the build has its key. As NewsView on the Mac.
/// </summary>
public sealed class NewsPage : Grid
{
    private static AppModel App => AppModel.Shared;

    private readonly Nav _nav;
    private readonly ScrollViewer _scroller = new() { VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled };
    private readonly Grid _header = new();
    private readonly WrapRow _wall = new(22);
    private readonly Grid _footer = new() { MinHeight = 60 };
    private readonly HashSet<int> _known = [];
    private readonly List<(StackPanel Face, Grid Picture)> _cards = [];
    private double _cardWidth = 340;
    private Article.Kinds _kind = Article.Kinds.News;
    private int _page, _pages = 1;
    private bool _loading, _failed;
    /// <summary>Bumped when the kind changes, so an answer for the old one is dropped.</summary>
    private int _generation;

    public NewsPage(Nav nav)
    {
        _nav = nav;
        Children.Add(Kit.Ambient());
        var column = new StackPanel { Spacing = 24, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };
        column.Children.Add(_header);
        column.Children.Add(_wall);
        column.Children.Add(_footer);
        _scroller.Content = column;
        Children.Add(_scroller);
        _scroller.ViewChanged += (_, _) =>
        {
            if (_scroller.ScrollableHeight - _scroller.VerticalOffset < Theme.Prefetch) _ = LoadNextAsync();
        };
        _wall.SizeChanged += (_, e) => Fit(e.NewSize.Width);
        RenderHeader();
        _ = LoadNextAsync();
    }

    /// <summary>As many cards across as fit at 300 or more, sharing the row out to 420 each.</summary>
    private void Fit(double room)
    {
        if (room <= 0) return;
        var across = Math.Max(1, (int)((room + 22) / (300 + 22)));
        var width = Math.Min(420, Math.Floor((room - 22 * (across - 1)) / across));
        if (Math.Abs(width - _cardWidth) < 1) return;
        _cardWidth = width;
        foreach (var (face, picture) in _cards) Size(face, picture, width);
    }

    private static void Size(StackPanel face, Grid picture, double width)
    {
        face.Width = width;
        picture.Height = Math.Round(width * 9 / 16);
    }

    private static S Title(Article.Kinds kind) => kind switch
    {
        Article.Kinds.Review => S.reviews,
        Article.Kinds.Announcement => S.announcements,
        _ => S.newsItems,
    };

    private void RenderHeader()
    {
        var loc = App.Loc;
        _header.Children.Clear();
        _header.ColumnDefinitions.Clear();
        _header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        _header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        _header.Children.Add(Theme.Text(loc[S.news], 34, FontWeights.Bold));
        var kinds = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        foreach (var option in Enum.GetValues<Article.Kinds>())
            kinds.Children.Add(Kit.Pill(loc[Title(option)], _kind == option, () =>
            {
                if (_kind == option) return;
                _kind = option;
                RenderHeader();
                Restart();
            }));
        SetColumn(kinds, 1);
        _header.Children.Add(kinds);
    }

    private void Restart()
    {
        _generation++;
        _known.Clear();
        _cards.Clear();
        _wall.Children.Clear();
        _page = 0;
        _pages = 1;
        _loading = false;
        _scroller.ChangeView(null, 0, null, true);
        _ = LoadNextAsync();
    }

    private async Task LoadNextAsync()
    {
        if (_loading || _page >= _pages) return;
        _loading = true;
        _failed = false;
        RenderFooter();
        var generation = _generation;
        try
        {
            var next = ArticlePage.Parse(await App.Api.SiteAsync(SiteQuery.Articles(_kind, _page + 1)));
            if (generation != _generation) return;
            foreach (var article in next.Articles.Where(a => _known.Add(a.Id)))
                _wall.Add(Card(article, () => _nav.Open(new Route.Article(article))));
            Fit(_wall.ActualWidth);
            _page = next.Page;
            _pages = next.Pages;
        }
        catch (Exception) { if (generation == _generation) _failed = true; }
        finally
        {
            if (generation == _generation) _loading = false;
            RenderFooter();
        }
        // A wall shorter than the window gives nothing to scroll: the next page is fetched at once.
        if (generation == _generation && !_failed && _scroller.ScrollableHeight < Theme.Prefetch && _page < _pages) await LoadNextAsync();
    }

    private void RenderFooter()
    {
        _footer.Children.Clear();
        if (_failed)
        {
            var retry = Kit.Glass(App.Loc[S.retry], null, () => _ = LoadNextAsync());
            retry.HorizontalAlignment = HorizontalAlignment.Center;
            _footer.Children.Add(retry);
        }
        else if (_loading) _footer.Children.Add(Kit.Spinner());
    }

    /// <summary>An article in the list: its picture, title, date and first lines.</summary>
    private Button Card(Article article, Action open)
    {
        var face = new StackPanel
        {
            CornerRadius = new CornerRadius(16),
            Background = Theme.White(0.05),
            BorderBrush = Theme.White(0.08),
            BorderThickness = new Thickness(1),
            // Articles are written in Persian whatever the interface's language.
            FlowDirection = FlowDirection.RightToLeft,
        }.Springy(0.2);
        var picture = new Grid { Background = Theme.Brush(Theme.Surface2) };
        Size(face, picture, _cardWidth);
        _cards.Add((face, picture));
        var mark = Glyph.Icon("", 34, Theme.Text3);
        mark.HorizontalAlignment = HorizontalAlignment.Center;
        picture.Children.Add(mark);
        picture.Children.Add(Images.Remote(article.Thumbnail, null, 420 * 1.5, fallback: article.Picture));
        face.Children.Add(picture);
        var words = new StackPanel { Spacing = 8, Padding = new Thickness(14) };
        // Two lines' room always, so the cards of a row are one height.
        words.Children.Add(Lines(article.Title, 16, FontWeights.Bold, Theme.Text1, 24));
        words.Children.Add(Lines(article.Summary ?? " ", 13, FontWeights.Normal, Theme.Text2, 20));
        if (article.Date is { } date) words.Children.Add(Theme.Text(date, 12, FontWeights.Medium, Theme.Text3));
        face.Children.Add(words);
        var button = Theme.Bare(face, open);
        Kit.AutomationName(button, article.Title);
        button.PointerEntered += (_, _) => { face.Background = Theme.White(0.09); face.BorderBrush = Theme.White(0.5); };
        button.PointerExited += (_, _) => { face.Background = Theme.White(0.05); face.BorderBrush = Theme.White(0.08); };
        return Kit.Hovering(button, face, 1.015);
    }

    private static TextBlock Lines(string text, double size, Windows.UI.Text.FontWeight weight, Windows.UI.Color color, double lineHeight)
    {
        var t = Theme.Text(text, size, weight, color, lines: 2);
        t.LineHeight = lineHeight;
        t.LineStackingStrategy = LineStackingStrategy.BlockLineHeight;
        t.Height = 2 * lineHeight;
        t.TextAlignment = TextAlignment.Left;
        return t;
    }
}

/// <summary>
/// One article, whole: its picture, title and date, then its text as it was written, with its
/// headings and pictures, in a column narrow enough to read. As ArticleView on the Mac.
/// </summary>
public sealed class ArticleReader : Grid
{
    private static AppModel App => AppModel.Shared;

    private readonly Article _article;
    private readonly StackPanel _column = new()
    {
        Spacing = 18,
        MaxWidth = 760,
        // Articles are written in Persian whatever the interface's language.
        FlowDirection = FlowDirection.RightToLeft,
    };
    private readonly StackPanel _body = new() { Spacing = 18 };

    public ArticleReader(Article article)
    {
        _article = article;
        Children.Add(Kit.Ambient());
        var outer = new Grid { Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };
        outer.Children.Add(_column);
        Children.Add(new ScrollViewer { Content = outer, VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled });
        if (article.Picture is { } picture)
        {
            var top = new Grid { CornerRadius = new CornerRadius(18), Background = Theme.Brush(Theme.Surface2) };
            top.Children.Add(Images.Remote(picture, null, 760 * 1.5, fallback: article.Thumbnail));
            top.SizeChanged += (_, e) => top.Height = e.NewSize.Width * 9 / 16;
            _column.Children.Add(top);
        }
        var title = Theme.Text(article.Title, 30, FontWeights.Bold, lines: 0);
        title.TextTrimming = TextTrimming.None;
        title.TextAlignment = TextAlignment.Left;
        _column.Children.Add(title);
        _column.Children.Add(_body);
        _ = LoadAsync();
    }

    private async Task LoadAsync()
    {
        _body.Children.Clear();
        if (_article.Date is { } listed) _body.Children.Add(Theme.Text(listed, 14, FontWeights.Medium, Theme.Text3));
        _body.Children.Add(Kit.Spinner());
        ArticleText? text = null;
        try { text = ArticleText.Parse(await App.Api.SiteAsync(SiteQuery.Article(_article.Id))); }
        catch (Exception) { }
        _body.Children.Clear();
        if (text is null)
        {
            var retry = Kit.Glass(App.Loc[S.retry], null, () => _ = LoadAsync());
            retry.HorizontalAlignment = HorizontalAlignment.Center;
            _body.Children.Add(retry);
            return;
        }
        if ((text.Article.Date ?? _article.Date) is { } date) _body.Children.Add(Theme.Text(date, 14, FontWeights.Medium, Theme.Text3));
        foreach (var block in text.Blocks)
            _body.Children.Add(block switch
            {
                ArticleBlock.Heading h => Paragraph(h.Text, 20, FontWeights.Bold, Theme.Text1, new Thickness(0, 8, 0, 0)),
                ArticleBlock.Paragraph p => Paragraph(p.Text, 16, FontWeights.Normal, Theme.Text2, default, 16 + 8 + 8),
                ArticleBlock.Picture pic => Picture(pic.Url),
                _ => new Border(),
            });
    }

    private static TextBlock Paragraph(string text, double size, Windows.UI.Text.FontWeight weight, Windows.UI.Color color, Thickness margin, double lineHeight = 0)
    {
        var t = Theme.Text(text, size, weight, color, lines: 0);
        t.TextTrimming = TextTrimming.None;
        t.TextAlignment = TextAlignment.Left;
        t.Margin = margin;
        if (lineHeight > 0)
        {
            t.LineHeight = lineHeight;
            t.LineStackingStrategy = LineStackingStrategy.BlockLineHeight;
        }
        return t;
    }

    /// <summary>A picture in an article, in its own proportions: they are not all the same shape.</summary>
    private static FrameworkElement Picture(Uri url)
    {
        var frame = new Border { Height = 200, CornerRadius = new CornerRadius(12), Background = Theme.Brush(Theme.Surface2), HorizontalAlignment = HorizontalAlignment.Center, FlowDirection = FlowDirection.LeftToRight };
        var image = new Image { Stretch = Stretch.Uniform, Opacity = 0 }.Springy(0.2);
        // Not decoded smaller: its own width is the most it is shown at.
        var bitmap = new BitmapImage();
        bitmap.ImageOpened += (_, _) =>
        {
            frame.Height = double.NaN;
            frame.Background = null;
            image.MaxWidth = Math.Min(bitmap.PixelWidth, 760);
            image.Opacity = 1;
        };
        bitmap.ImageFailed += (_, _) => frame.Visibility = Visibility.Collapsed;
        bitmap.UriSource = url;
        image.Source = bitmap;
        frame.Child = image;
        return frame;
    }
}
