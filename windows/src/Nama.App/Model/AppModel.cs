using System.Text.Json;
using Microsoft.UI.Dispatching;
using Nama.Core;

namespace Nama.App.Model;

/// <summary>Everything the player needs to start.</summary>
public sealed record PlaybackRequest(Post Post, IReadOnlyList<StreamItem> Items, int Index, ContinueWatching? Resume);

/// <summary>
/// App-wide state: boot, session, settings and the shared API client. Lives on the UI thread;
/// pages listen to <see cref="Changed"/> and read what they need.
/// </summary>
public sealed class AppModel
{
    public enum Phases { Booting, Failed, Login, Main }

    public static AppModel Shared { get; private set; } = null!;

    private readonly DispatcherQueue _ui;

    public Phases Phase { get; private set; } = Phases.Booting;
    public Settings Settings { get; private set; } = Settings.Load();
    public User? User { get; private set; }
    public string? ApiHost { get; private set; }
    /// <summary>Bumped when the catalogue itself may differ: sign-in, sign-out, language.</summary>
    public int ContentVersion { get; private set; }
    /// <summary>Bumped when watch progress changed (the player closed).</summary>
    public int WatchVersion { get; private set; }
    /// <summary>Goes up when "continue watching" should be asked for again, after a title was taken off it.</summary>
    public int ContinueVersion { get; private set; }
    public PlaybackRequest? Playback { get; private set; }
    public string? Toast { get; private set; }
    public RatingBook Ratings { get; private set; } = RatingBook.FromData(Storage.Read(RatingsFile) ?? "") ?? new RatingBook();
    /// <summary>The ids of the titles in each of the user's 30nama lists ("watchlist", "favorite", "notify").</summary>
    public Dictionary<string, HashSet<int>> Lists { get; private set; } = [];
    /// <summary>
    /// Files watched to the end on this computer. The server keeps where each title was left, but
    /// not which episodes are done.
    /// </summary>
    public HashSet<string> Watched { get; } = LoadSet(WatchedFile);

    public ApiClient Api { get; } = new(new ApiConfig { UserAgent = $"30nama/{typeof(AppModel).Assembly.GetName().Version?.ToString(3)} (Windows NT 10.0; Win64; x64)" });

    /// <summary>Says which part of the state changed: "phase", "settings", "content", "watch", "continue", "lists", "ratings", "playback", "toast", "user".</summary>
    public event Action<string>? Changed;

    public Loc Loc => new(Settings.Lang);
    public bool IsLoggedIn => User is not null;

    private (HomeFeed Feed, DateTime At, Lang Lang)? _homeCache;
    private (Menu Menu, Lang Lang)? _menuCache;
    private (FilterOptions Options, Lang Lang)? _filterCache;
    private readonly ContinueDismissals _dismissals = LoadDismissals();
    private CancellationTokenSource? _toastTimer;

    private static string RatingsFile => Path.Combine(Storage.Folder, "ratings.json");
    private static string WatchedFile => Path.Combine(Storage.Folder, "watched.json");
    private static string DismissalsFile => Path.Combine(Storage.Folder, "continue-dismissals.json");
    private static string HostFile => Path.Combine(Storage.Folder, "host.txt");

    public AppModel(DispatcherQueue ui)
    {
        _ui = ui;
        Shared = this;
    }

    private void Tell(string what) => Changed?.Invoke(what);

    // MARK: Boot and session

    public async Task BootAsync()
    {
        Phase = Phases.Booting;
        Tell("phase");
        Api.SetLanguage(Settings.Lang);
        Api.SetSessionExpiredHandler(() => _ui.TryEnqueue(SessionExpired));
        try
        {
            var host = await Api.BootstrapAsync(Storage.Read(HostFile)?.Trim());
            Storage.Write(HostFile, host);
            ApiHost = host;
        }
        catch (Exception)
        {
            Phase = Phases.Failed;
            Tell("phase");
            return;
        }
        if (SessionStore.Load() is { } saved)
        {
            User = saved;
            Api.SetToken(saved.Token);
            _ = RefreshProfileAsync();
            _ = RefreshListsAsync();
        }
        Phase = User is not null || Settings.Guest ? Phases.Main : Phases.Login;
        Tell("phase");
    }

    /// <summary>The saved profile can be stale (renewed subscription), so refresh it once per launch.</summary>
    private async Task RefreshProfileAsync()
    {
        if (User?.Token is not { } token) return;
        try
        {
            var e = await Api.CallAsync("user");
            if (!e.Success || Core.User.Parse(e.Result, token) is not { } fresh) return;
            User = fresh;
            SessionStore.Save(fresh);
            Tell("user");
        }
        catch (Exception) { }
    }

    /// <summary>Completes a sign-in with a code. The login call returns only a token; the profile follows.</summary>
    public async Task SignInAsync(string token)
    {
        Api.SetToken(token);
        var signedIn = Core.User.Parse(Json.From(new Dictionary<string, object?> { ["user_session"] = token }));
        try
        {
            var e = await Api.CallAsync("user");
            if (e.Success && Core.User.Parse(e.Result, token) is { } full) signedIn = full;
        }
        catch (Exception) { }
        if (signedIn is null) return;
        User = signedIn;
        SessionStore.Save(signedIn);
        Update(s => s with { Guest = false });
        _homeCache = null;
        ContentVersion++;
        ShowToast(string.Join(' ', new[] { Loc[S.welcome], signedIn.Name }.OfType<string>()));
        Phase = Phases.Main;
        Tell("phase");
        Tell("content");
        await RefreshListsAsync();
    }

    public void ContinueAsGuest()
    {
        Update(s => s with { Guest = true });
        Phase = Phases.Main;
        Tell("phase");
    }

    /// <summary>Leaves guest browsing for the sign-in screen.</summary>
    public void RequestSignIn()
    {
        Phase = Phases.Login;
        Tell("phase");
    }

    public void SignOut()
    {
        var api = Api;
        _ = Task.Run(async () =>
        {
            try { await api.CallAsync("logout"); } catch (Exception) { }
            api.SetToken(null);
        });
        ClearSession();
    }

    private void SessionExpired()
    {
        if (User is not null) ClearSession();
    }

    private void ClearSession()
    {
        User = null;
        Lists = [];
        SessionStore.Clear();
        Update(s => s with { Guest = false });
        _homeCache = null;
        Playback = null;
        ContentVersion++;
        Phase = Phases.Login;
        Tell("playback");
        Tell("content");
        Tell("phase");
    }

    public async Task RetryBootAsync() => await BootAsync();

    // MARK: Settings

    public void Update(Func<Settings, Settings> change)
    {
        var next = change(Settings);
        if (next == Settings) return;
        var languageChanged = next.Lang != Settings.Lang;
        Settings = next;
        next.Save();
        if (languageChanged)
        {
            _homeCache = null;
            _menuCache = null;
            _filterCache = null;
            ContentVersion++;
            Api.SetLanguage(next.Lang);
        }
        Tell("settings");
        if (languageChanged) Tell("content");
    }

    // MARK: Shared data

    private static string HomeFile(Lang lang) => Path.Combine(Storage.Folder, $"home-{lang.Code()}.json");

    /// <summary>The home page as it was last fetched, read from disk: shown at once on launch, while the fresh one is on its way.</summary>
    public HomeFeed? CachedHome()
    {
        if (Storage.Read(HomeFile(Settings.Lang)) is not { } text) return null;
        try
        {
            var feed = new HomeFeed(Json.Parse(text));
            return feed.IsEmpty ? null : feed;
        }
        catch (Exception) { return null; }
    }

    /// <summary>Home feed, cached for 90 seconds. If the network fails, the last good feed is served instead.</summary>
    public async Task<HomeFeed> HomeAsync()
    {
        var lang = Settings.Lang;
        if (_homeCache is { } c && c.Lang == lang && DateTime.UtcNow - c.At < TimeSpan.FromSeconds(90)) return c.Feed;
        try
        {
            Api.SetLanguage(lang);
            var result = await Api.GetAsync("home_stream");
            var feed = new HomeFeed(result);
            if (feed.IsEmpty) throw new ApiException(ApiException.Kinds.Server, "empty");
            _homeCache = (feed, DateTime.UtcNow, lang);
            Storage.Write(HomeFile(lang), result.Serialize());
            return feed;
        }
        catch (Exception)
        {
            if (CachedHome() is { } saved) return saved;
            throw;
        }
    }

    public async Task<List<Post>> ContinueWatchingAsync() => await ContinueWatchingIfReachableAsync() ?? [];

    /// <summary>Null when 30nama could not be asked, as against an empty list.</summary>
    public async Task<List<Post>?> ContinueWatchingIfReachableAsync(bool background = false)
    {
        if (!IsLoggedIn) return null;
        try
        {
            var result = await Api.GetAsync("continue_watching", background: background);
            var list = _dismissals.Filter(Post.ContinueList(result));
            SaveDismissals();
            return list;
        }
        catch (Exception) { return null; }
    }

    /// <summary>
    /// Takes a title off "continue watching", on 30nama and so on every device. The server's list
    /// is cached for a while after that, so the app also keeps the title off itself until it is
    /// watched again.
    /// </summary>
    public void DismissContinue(Post post)
    {
        _dismissals.Dismiss(post);
        SaveDismissals();
        _ = Task.Run(async () =>
        {
            try { await Api.CallAsync("continue_watching_dismiss", new Dictionary<string, string> { ["id"] = post.Id.ToString() }, background: true); }
            catch (Exception) { }
            // The server sends at most twelve, and its list lags behind for a while: asked again
            // shortly and once more later, the titles after the ones taken off move up into the row.
            foreach (var wait in new[] { 2.0, 45.0 })
            {
                await Task.Delay(TimeSpan.FromSeconds(wait));
                _ui.TryEnqueue(() => { ContinueVersion++; Tell("continue"); });
            }
        });
    }

    private static ContinueDismissals LoadDismissals()
    {
        try
        {
            return Storage.Read(DismissalsFile) is { } text && JsonSerializer.Deserialize<Dictionary<string, double>>(text) is { } stored
                ? new ContinueDismissals(stored)
                : new ContinueDismissals();
        }
        catch (Exception) { return new ContinueDismissals(); }
    }

    private void SaveDismissals() => Storage.Write(DismissalsFile, JsonSerializer.Serialize(_dismissals.Stored));

    public async Task<Menu> MenuAsync()
    {
        if (_menuCache is { } c && c.Lang == Settings.Lang) return c.Menu;
        var menu = new Menu(await Api.GetAsync("menu"));
        _menuCache = (menu, Settings.Lang);
        return menu;
    }

    public async Task<FilterOptions> FilterOptionsAsync()
    {
        if (_filterCache is { } c && c.Lang == Settings.Lang) return c.Options;
        var options = new FilterOptions(await Api.GetAsync("advanced_search_parameters"));
        _filterCache = (options, Settings.Lang);
        return options;
    }

    /// <summary>One of the user's own lists on 30nama ("watchlist" or "favorite"), newest addition first.</summary>
    public async Task<List<Post>> MyListAsync(string group)
    {
        if (!IsLoggedIn) return [];
        try
        {
            return Post.List(await Api.GetAsync("user_list", new Dictionary<string, string>
            {
                ["action"] = "list", ["group"] = group, ["page"] = "1", ["orderby"] = "add_list", ["order"] = "DESC",
            }));
        }
        catch (Exception) { return []; }
    }

    /// <summary>How many titles the user keeps in each of their 30nama lists.</summary>
    public async Task<Dictionary<string, int>> MyListSizesAsync()
    {
        if (!IsLoggedIn) return [];
        try
        {
            var result = await Api.GetAsync("user_mylist");
            return (result.Object ?? new Dictionary<string, Json>()).ToDictionary(kv => kv.Key, kv => kv.Value["post"].Array.Count);
        }
        catch (Exception) { return []; }
    }

    /// <summary>Series on the user's watchlist or notify list with an episode still to come, soonest first.</summary>
    public async Task<List<Post>> UpcomingAsync()
    {
        if (!IsLoggedIn) return [];
        var posts = new List<Post>();
        try
        {
            foreach (var group in new[] { "watchlist", "notify" })
            {
                // A handful of pages is every list a person keeps; more would only hammer the server.
                for (int page = 1, pages = 1; page <= pages && page <= 4; page++)
                {
                    var next = new PostPage(await Api.GetAsync("user_list", new Dictionary<string, string>
                    {
                        ["action"] = "list", ["group"] = group, ["page"] = page.ToString(), ["orderby"] = "add_list", ["order"] = "DESC",
                    }, background: true));
                    posts.AddRange(next.Posts);
                    pages = next.Pages;
                }
            }
        }
        catch (Exception) { return []; }
        return Post.Upcoming(posts);
    }

    // MARK: Lists

    /// <summary>Reads which titles are on the user's lists, so a title page can show where it stands.</summary>
    public async Task RefreshListsAsync()
    {
        if (!IsLoggedIn) return;
        try
        {
            var result = await Api.GetAsync("user_mylist");
            Lists = (result.Object ?? new Dictionary<string, Json>()).ToDictionary(
                kv => kv.Key, kv => kv.Value["post"].Array.Select(x => x.Int).OfType<int>().ToHashSet());
            Tell("lists");
        }
        catch (Exception) { }
    }

    public bool IsListed(Post post, string group) => Lists.TryGetValue(group, out var ids) && ids.Contains(post.Id);

    /// <summary>
    /// Puts a title on one of the user's lists, or takes it off. The server has one action for
    /// both: it adds what is missing and removes what is there.
    /// </summary>
    public async Task ToggleListAsync(Post post, string group)
    {
        if (!IsLoggedIn) return;
        var was = IsListed(post, group);
        void Set(bool on)
        {
            if (!Lists.TryGetValue(group, out var ids)) Lists[group] = ids = [];
            if (on) ids.Add(post.Id); else ids.Remove(post.Id);
            Tell("lists");
        }
        // Answer at once; put it back if the server says no.
        Set(!was);
        var done = false;
        try
        {
            done = (await Api.CallAsync("user_list", new Dictionary<string, string> { ["action"] = "add", ["group"] = group, ["post_id"] = post.Id.ToString() })).Success;
        }
        catch (Exception) { }
        if (!done)
        {
            Set(was);
            ShowToast(Loc[S.error]);
        }
    }

    // MARK: Watched

    public void SetWatched(string fileId, bool on)
    {
        if (fileId.Length == 0 || Watched.Contains(fileId) == on) return;
        if (on) Watched.Add(fileId); else Watched.Remove(fileId);
        Storage.Write(WatchedFile, JsonSerializer.Serialize(Watched));
        Tell("watch");
    }

    private static HashSet<string> LoadSet(string file)
    {
        try { return Storage.Read(file) is { } text ? JsonSerializer.Deserialize<HashSet<string>>(text) ?? [] : []; }
        catch (Exception) { return []; }
    }

    // MARK: Ratings

    /// <summary>
    /// Rates a title, or clears its rating when <paramref name="stars"/> is null. Windows has no
    /// iCloud: the ratings stay on this computer.
    /// </summary>
    public void Rate(Post post, double? stars)
    {
        Ratings.Rate(post, stars);
        Storage.Write(RatingsFile, Ratings.Encoded());
        Tell("ratings");
    }

    // MARK: Playback and messages

    public void Play(PlaybackRequest request)
    {
        Playback = request;
        Tell("playback");
    }

    public void ClosePlayer()
    {
        Playback = null;
        WatchVersion++;
        Tell("playback");
        Tell("watch");
    }

    public void ShowToast(string text)
    {
        _toastTimer?.Cancel();
        var timer = _toastTimer = new CancellationTokenSource();
        Toast = text;
        Tell("toast");
        _ = Task.Delay(TimeSpan.FromSeconds(3.5), timer.Token).ContinueWith(t =>
        {
            if (!t.IsCanceled) _ui.TryEnqueue(() => { Toast = null; Tell("toast"); });
        }, TaskScheduler.Default);
    }
}
