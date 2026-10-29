using System.Runtime.InteropServices;
using System.Text.Json;
using Microsoft.Win32;
using Nama.Core;
using Windows.Data.Xml.Dom;
using Windows.UI.Notifications;

namespace Nama.App.Model;

/// <summary>
/// Tells the user, with a Windows notification, when a series they follow has a new episode: one
/// on their watchlist or notify list, or one they are part way through. As EpisodeWatch.swift.
/// Clicking the notification while the app runs opens the series.
/// </summary>
public static partial class EpisodeWatch
{
    /// <summary>
    /// What Windows knows the app's notifications by. Windows App SDK's own notifications need a
    /// runtime installed apart from the app, which a self-contained app does not have, so the
    /// system's toasts are used, under a name the app registers for itself.
    /// </summary>
    public const string AppId = "thesaikodelik.30nama";

    /// <summary>Looks every few hours: each look is several list requests to a rate-limited server.</summary>
    private static readonly TimeSpan Spacing = TimeSpan.FromHours(6);

    private static string File => Path.Combine(Storage.Folder, "episode-alerts.json");

    /// <summary>What is kept between runs; <paramref name="News"/> is each series with news: when it came, and where it was left then.</summary>
    private sealed record Kept(double Last, Dictionary<string, double> Expected, Dictionary<string, double[]>? News = null);

    /// <summary>A new episode stays news for a week, or until the series is watched on (the Android app's, from 30namaTV).</summary>
    private static readonly TimeSpan NewsFor = TimeSpan.FromDays(7);

    private static Dictionary<string, double[]>? _news;

    /// <summary>
    /// The series has an episode out since it was last watched: said on its card in the app as well
    /// as by the notification. Gone once its place in "continue watching" moves.
    /// </summary>
    public static bool IsNew(Post post)
    {
        lock (Shown) _news ??= Load().News ?? [];
        double[]? entry;
        lock (Shown) entry = _news.GetValueOrDefault(post.Id.ToString());
        if (entry is not [var at, var left]) return false;
        if (DateTime.UtcNow - DateTime.UnixEpoch.AddSeconds(at) > NewsFor) return false;
        return post.Resume is not { } resume || Math.Abs(resume.Time - left) <= 1;
    }

    /// <summary>A click on a notification, with the post it is about. Raised on a thread of Windows' own.</summary>
    public static event Action<int>? Opened;

    /// <summary>Shown toasts, kept so their click handlers live as long as they can be clicked.</summary>
    private static readonly List<ToastNotification> Shown = [];

    /// <summary>The app's name and picture for its notifications, and the same name on the process, before any window.</summary>
    public static void Register()
    {
        try
        {
            SetCurrentProcessExplicitAppUserModelID(AppId);
            using var key = Registry.CurrentUser.CreateSubKey($@"Software\Classes\AppUserModelId\{AppId}");
            key.SetValue("DisplayName", "30nama");
            key.SetValue("IconUri", Path.Combine(AppContext.BaseDirectory, "Assets", "30nama-256.png"));
        }
        catch (Exception e) { App.Log("notifications: " + e.Message); }
    }

    [LibraryImport("shell32.dll", StringMarshalling = StringMarshalling.Utf16)]
    private static partial int SetCurrentProcessExplicitAppUserModelID(string appId);

    public static async Task LookAsync(AppModel app)
    {
        if (!app.IsLoggedIn) return;
        var kept = Load();
        if (DateTime.UtcNow - DateTime.UnixEpoch.AddSeconds(kept.Last) < Spacing) return;
        // Only when both lists came: with one missing, the days remembered for its series would be
        // dropped as if they were no longer followed, and their episodes never told of.
        if (await app.UpcomingIfReachableAsync() is not { } upcoming || await app.ContinueWatchingIfReachableAsync(background: true) is not { } watching) return;
        var followed = upcoming.Concat(watching.Where(p => p.IsSeries)).ToList();
        var alerts = new EpisodeAlerts(kept.Expected);
        var due = followed.Count > 0 ? alerts.Update(followed) : [];
        var now = (DateTime.UtcNow - DateTime.UnixEpoch).TotalSeconds;
        var news = (kept.News ?? []).Where(n => n.Value is [var at, _] && now - at <= NewsFor.TotalSeconds).ToDictionary();
        foreach (var post in due) news[post.Id.ToString()] = [now, watching.FirstOrDefault(w => w.Id == post.Id)?.Resume?.Time ?? 0];
        lock (Shown) _news = news;
        // Counted from a look that worked; one that failed is tried again within the hour.
        Storage.Write(File, JsonSerializer.Serialize(new Kept(now, followed.Count > 0 ? alerts.Stored : kept.Expected, news)));
        foreach (var post in due) Show(post, app.Loc);
    }

    public static void Show(Post post, Loc loc)
    {
        try
        {
            var xml = new XmlDocument();
            var title = System.Security.SecurityElement.Escape(loc[S.newEpisode]);
            var name = System.Security.SecurityElement.Escape(post.Title(loc.Lang));
            xml.LoadXml($"""<toast launch="post={post.Id}"><visual><binding template="ToastGeneric"><text>{title}</text><text>{name}</text></binding></visual></toast>""");
            var toast = new ToastNotification(xml) { Tag = $"episode-{post.Id}" };
            toast.Activated += (_, args) =>
            {
                if (args is ToastActivatedEventArgs { Arguments: { } text } && text.StartsWith("post=") && int.TryParse(text[5..], out var id)) Opened?.Invoke(id);
            };
            lock (Shown)
            {
                Shown.Add(toast);
                if (Shown.Count > 20) Shown.RemoveAt(0);
            }
            ToastNotificationManager.CreateToastNotifier(AppId).Show(toast);
        }
        catch (Exception e) { App.Log("notification: " + e.Message); }
    }

    private static Kept Load()
    {
        try { return Storage.Read(File) is { } text ? JsonSerializer.Deserialize<Kept>(text) ?? new Kept(0, []) : new Kept(0, []); }
        catch (Exception) { return new Kept(0, []); }
    }
}
