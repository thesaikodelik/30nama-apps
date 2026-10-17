using Nama.Core;

namespace Nama.App.Shell;

/// <summary>
/// The sections of the bar, in the Mac app's order: search first, as most apps have it; the
/// user's own page last, by settings. Only those the Windows app has so far are on the bar.
/// </summary>
public enum Section { Search, Home, Movies, Series, Anime, Browse, Profile, Settings }

/// <summary>Something opened on top of a section.</summary>
public abstract record Route
{
    public sealed record Title(Post Post, bool Playing = false) : Route;
}

/// <summary>Where the user is: a section, and what has been opened on top of it.</summary>
public sealed class Nav
{
    public Section Section { get; private set; } = Section.Home;
    private readonly List<Route> _opened = [];
    public IReadOnlyList<Route> Opened => _opened;

    public event Action? Changed;

    public static IReadOnlyList<Section> OnBar { get; } = [Section.Home, Section.Settings];

    public static string Symbol(Section s) => s switch
    {
        Section.Search => UI.Glyph.Search,
        Section.Settings => UI.Glyph.Settings,
        Section.Profile => UI.Glyph.Person,
        _ => "",
    };

    public static S Title(Section s) => s switch
    {
        Section.Search => S.search,
        Section.Movies => S.movies,
        Section.Series => S.series,
        Section.Anime => S.anime,
        Section.Browse => S.browse,
        Section.Profile => S.profile,
        Section.Settings => S.settings,
        _ => S.home,
    };

    /// <summary>Search and settings are known by their glyphs alone; the rest carry their names.</summary>
    public static bool IconOnly(Section s) => s is Section.Search or Section.Settings;

    /// <summary>Opens a title's page; a play button elsewhere opens it already playing.</summary>
    public void Open(Post post, bool playing = false) => Open(new Route.Title(post, playing));

    public void Open(Route route)
    {
        if (_opened.Count > 0 && _opened[^1] is Route.Title a && route is Route.Title b && a.Post.Id == b.Post.Id) return;
        _opened.Add(route);
        Changed?.Invoke();
    }

    public void Back()
    {
        if (_opened.Count == 0) return;
        _opened.RemoveAt(_opened.Count - 1);
        Changed?.Invoke();
    }

    public void Go(Section section)
    {
        _opened.Clear();
        Section = section;
        Changed?.Invoke();
    }
}
