using Nama.Core;

namespace Nama.App.Shell;

/// <summary>
/// The sections of the bar, in the Mac app's order: search first, as most apps have it; the
/// user's own page last, by settings.
/// </summary>
public enum Section { Search, Home, Movies, Series, Anime, Browse, Profile, Settings, Downloads }

/// <summary>Something opened on top of a section.</summary>
public abstract record Route
{
    public sealed record Title(Post Post, bool Playing = false) : Route;
    public sealed record Catalog(string Slug, string Name) : Route;
    public sealed record Advanced : Route;

    /// <summary>The same page: a title by its id, a catalogue by its slug.</summary>
    public bool Same(Route other) => (this, other) switch
    {
        (Title a, Title b) => a.Post.Id == b.Post.Id,
        (Catalog a, Catalog b) => a.Slug == b.Slug,
        (Advanced, Advanced) => true,
        _ => false,
    };
}

/// <summary>Where the user is: a section, and what has been opened on top of it.</summary>
public sealed class Nav
{
    public Section Section { get; private set; } = Section.Home;
    private readonly List<Route> _opened = [];
    public IReadOnlyList<Route> Opened => _opened;

    public event Action? Changed;
    /// <summary>A title whose files are to be chosen for download.</summary>
    public event Action<Post>? Picking;

    public void Pick(Post post) => Picking?.Invoke(post);

    /// <summary>A trailer to play over the window: the title's name and the YouTube id.</summary>
    public event Action<string, string>? Trailering;

    public void Trailer(string title, string video) => Trailering?.Invoke(title, video);

    public static IReadOnlyList<Section> OnBar { get; } = [Section.Search, Section.Home, Section.Movies, Section.Series, Section.Anime, Section.Browse, Section.Profile, Section.Settings];

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
        Section.Downloads => S.downloads,
        _ => S.home,
    };

    /// <summary>Search and settings are known by their glyphs alone; the rest carry their names.</summary>
    public static bool IconOnly(Section s) => s is Section.Search or Section.Settings;

    /// <summary>Opens a title's page; a play button elsewhere opens it already playing.</summary>
    public void Open(Post post, bool playing = false) => Open(new Route.Title(post, playing));

    public void Open(Route route)
    {
        if (_opened.Count > 0 && _opened[^1].Same(route)) return;
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
