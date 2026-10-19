using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Nama.App.Model;
using Nama.App.Shell;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// The titles of an actor clicked on a title page, newest first. Only the website's API finds them
/// (by the person's IMDb id), so this page is reached only when the build has its key. As PersonView on the Mac.
/// </summary>
public sealed class PersonPage : Grid
{
    private static AppModel App => AppModel.Shared;

    private readonly Person _person;
    private readonly Nav _nav;
    private readonly ScrollViewer _scroller = new() { VerticalScrollBarVisibility = ScrollBarVisibility.Hidden, HorizontalScrollMode = ScrollMode.Disabled };
    private readonly WrapRow _wall = new(20, unmirrored: true);
    private readonly Grid _footer = new() { MinHeight = 60 };
    private readonly HashSet<int> _known = [];
    private int _page, _pages = 1;
    private bool _loading, _failed, _done;

    public PersonPage(Person person, Nav nav)
    {
        _person = person;
        _nav = nav;
        Children.Add(Kit.Ambient());
        var column = new StackPanel { Spacing = 28, Padding = new Thickness(Theme.Edge, 106, Theme.Edge, 60) };
        column.Children.Add(Header());
        column.Children.Add(_wall);
        column.Children.Add(_footer);
        _scroller.Content = column;
        Children.Add(_scroller);
        _scroller.ViewChanged += (_, _) =>
        {
            if (_scroller.ScrollableHeight - _scroller.VerticalOffset < Theme.Prefetch) _ = LoadNextAsync();
        };
        _ = LoadNextAsync();
    }

    private FrameworkElement Header()
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 20 };
        var face = new Grid { Width = 120, Height = 120, CornerRadius = new CornerRadius(60), Background = Theme.Brush(Theme.Surface2) };
        var silhouette = Glyph.Icon(Glyph.Person, 40, Theme.Text3);
        silhouette.HorizontalAlignment = HorizontalAlignment.Center;
        face.Children.Add(silhouette);
        if (_person.Poster is { } p) face.Children.Add(Images.Remote(p.Url(120), p.Blurhash, 120 * 1.5));
        row.Children.Add(face);
        var names = new StackPanel { Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        names.Children.Add(Theme.Text(_person.Name, 34, FontWeights.Bold));
        names.Children.Add(Theme.Text(App.Loc[S.filmography], 16, FontWeights.Medium, Theme.Text3));
        row.Children.Add(names);
        return row;
    }

    private async Task LoadNextAsync()
    {
        if (_person.Imdb is not { } imdb || _loading || _page >= _pages) return;
        _loading = true;
        _failed = false;
        RenderFooter();
        try
        {
            var (path, form) = SiteQuery.Filmography(imdb, _page + 1);
            var next = SitePosts.Page(await App.Api.SiteAsync(path, form));
            foreach (var post in next.Posts.Where(p => _known.Add(p.Id)))
                _wall.Add(Kit.Poster(post, () => _nav.Open(post)));
            _page = next.Page;
            _pages = next.Pages;
            _done = true;
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
            var retry = Kit.Glass(App.Loc[S.retry], null, () => _ = LoadNextAsync());
            retry.HorizontalAlignment = HorizontalAlignment.Center;
            _footer.Children.Add(retry);
        }
        else if (_loading) _footer.Children.Add(Kit.Spinner());
        else if (_done && _known.Count == 0)
        {
            var none = Theme.Text(App.Loc[S.noMatches], 16, FontWeights.Medium, Theme.Text2);
            none.HorizontalAlignment = HorizontalAlignment.Center;
            _footer.Children.Add(none);
        }
    }
}
