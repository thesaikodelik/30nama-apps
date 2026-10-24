using System.Diagnostics;
using System.Reflection;
using Microsoft.UI.Dispatching;
using Nama.Core;

namespace Nama.App.Model;

/// <summary>
/// Keeps the app up to date from the project's GitHub releases: looks for a newer one now and
/// then, downloads its installer on request, checks the project's signature on it, and runs it
/// when the user relaunches. The installer closes this copy, puts the new one in its place and
/// opens it again. As Updater.swift on the Mac.
///
/// Every step is written to update.log in the app's folder, and the installer writes its own log
/// beside it, so a failure on someone else's computer can be found without guessing. A failure
/// takes the user to the release page. A file update-feed.txt there, holding an address, points
/// any build (an installed one too) at a test feed; what it offers is still installed only when
/// signed with the project's key, and never when it is not newer.
/// </summary>
public sealed class Updater
{
    public enum Phases { Idle, Available, Downloading, Ready, Failed }

    public static Updater Shared { get; private set; } = null!;

    private readonly DispatcherQueue _ui;
    private readonly HttpClient _http = new() { Timeout = Timeout.InfiniteTimeSpan };
    private DateTime _lastLook = DateTime.MinValue;
    private readonly DateTime _opened = DateTime.UtcNow;

    /// <summary>The app has been open a few seconds: coming forward in them is the app starting, not a return to it.</summary>
    public bool Settled => DateTime.UtcNow - _opened > TimeSpan.FromSeconds(7);
    private string? _staged;
    private Uri? _failedPage;

    public Phases Phase { get; private set; }
    public WinRelease? Release { get; private set; }
    /// <summary>0...1 while downloading.</summary>
    public double Progress { get; private set; }
    /// <summary>Put off until the app is next opened.</summary>
    public bool Postponed { get; private set; }

    public event Action? Changed;

    public Updater(DispatcherQueue ui)
    {
        _ui = ui;
        Shared = this;
        _http.DefaultRequestHeaders.UserAgent.ParseAdd("30nama-windows");
        // The installer of the update that brought this version, or of one given up on.
        try { Directory.Delete(Folder, recursive: true); } catch (Exception) { }
        CheckPending();
    }

    private static string Folder => Path.Combine(Path.GetTempPath(), "30nama-update");
    private static string LogFile => Path.Combine(Storage.Folder, "update.log");
    private static string InstallerLog => Path.Combine(Storage.Folder, "update-installer.log");
    private static string PendingFile => Path.Combine(Storage.Folder, "update-pending.json");
    private static string FeedFile => Path.Combine(Storage.Folder, "update-feed.txt");
    private static readonly Uri ReleasesPage = new("https://github.com/thesaikodelik/30nama-apps/releases");

    /// <summary>One line in update.log, kept short: past a hundred kilobytes it starts again.</summary>
    private static void Log(string line)
    {
        try
        {
            if (File.Exists(LogFile) && new FileInfo(LogFile).Length > 100_000) File.Delete(LogFile);
            File.AppendAllText(LogFile, $"{DateTime.Now:yyyy-MM-dd HH:mm:ss} [{Current}] {line}{Environment.NewLine}");
        }
        catch (Exception) { }
    }

    /// <summary>
    /// An update handed to the installer last time: arrived if this is that version, otherwise the
    /// installer failed with the app closed, and this is the first chance to say so.
    /// </summary>
    private void CheckPending()
    {
        if (PendingUpdate.Parse(Storage.Read(PendingFile)) is not { } pending) return;
        try { File.Delete(PendingFile); } catch (Exception) { }
        if (pending.ArrivedIn(Current))
        {
            Log($"opened as {Current}: the update to {pending.Version} arrived");
            return;
        }
        Log($"opened as {Current}, not {pending.Version}: the installer did not finish (its log: {InstallerLog})");
        _failedPage = pending.Page;
        Phase = Phases.Failed;
    }

    public static string Current
    {
        get
        {
#if DEBUG
            if (Environment.GetEnvironmentVariable("NAMA_VERSION") is { Length: > 0 } version) return version;
#endif
            var informational = typeof(Updater).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion;
            // "1.2.0+abc123": the part after the plus is the build's commit.
            return (informational ?? "0").Split('+')[0];
        }
    }

    /// <summary>A test feed's address, from update-feed.txt (or, in a build being worked on, NAMA_UPDATE_FEED).</summary>
    private static Uri? TestFeed
    {
        get
        {
#if DEBUG
            if (Environment.GetEnvironmentVariable("NAMA_UPDATE_FEED") is { Length: > 0 } variable) return new Uri(variable);
#endif
            return Storage.Read(FeedFile)?.Trim() is { Length: > 0 } text && Uri.TryCreate(text, UriKind.Absolute, out var address)
                && address.Scheme is "http" or "https" ? address : null;
        }
    }

    private static Uri Feed => TestFeed ?? WinRelease.Feed;

    /// <summary>
    /// A copy that the installer put in place, and so one it can replace. A copy run from anywhere
    /// else (a build folder, one copied by hand) is sent to the release page instead.
    /// </summary>
    private static bool Installed => File.Exists(Path.Combine(AppContext.BaseDirectory, "unins000.exe"));

    public enum Looks { Found, UpToDate, NoAnswer }

    /// <summary>
    /// Looks for a newer release: by itself at most once an hour (GitHub answers sixty times an hour
    /// per address); at once when <paramref name="asked"/>, from the button in Settings. Null when it
    /// did not look, or an update is already on its way. As look(asked:) on the Mac.
    /// </summary>
    public async Task<Looks?> LookAsync(bool asked = false)
    {
#if DEBUG
        // A build being worked on is not the released app; it looks only when told to.
        if (TestFeed is null && !asked) return null;
#endif
        // Found before and put off for this time: asked for, its card comes back.
        if (asked && Phase == Phases.Available && Postponed)
        {
            Postponed = false;
            Tell();
            return Looks.Found;
        }
        if (Phase != Phases.Idle) return null;
        if (!asked && DateTime.UtcNow - _lastLook < TimeSpan.FromHours(1)) return null;
        _lastLook = DateTime.UtcNow;
        if (TestFeed is { } test) Log($"looking at the test feed {test}");
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, Feed);
            request.Headers.Accept.ParseAdd("application/vnd.github+json");
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(20));
            using var response = await _http.SendAsync(request, timeout.Token);
            if ((int)response.StatusCode != 200) return Looks.NoAnswer;
            var feed = Json.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token));
            if (WinRelease.Newest(feed, Current) is not { } release) return Looks.UpToDate;
            Release = release;
            Log($"found {release.Version}: {release.Installer}");
            // Asked for, its card comes even if it was put off for this time.
            if (asked) Postponed = false;
            Set(Phases.Available);
            return Looks.Found;
        }
        // Not being able to look is not a failure the user needs to hear of; it is tried again later.
        catch (Exception e)
        {
            Log($"could not look for updates: {e.GetType().Name}: {e.Message}");
            return Looks.NoAnswer;
        }
    }

    public void Postpone()
    {
        Postponed = true;
        Tell();
    }

    public void Install()
    {
        if (Phase != Phases.Available || Release is not { } release) return;
        if (!Installed)
        {
            Log($"not installed by the installer ({AppContext.BaseDirectory}); sent to the release page");
            Open(release.Page);
            return;
        }
        Progress = 0;
        Set(Phases.Downloading);
        Log($"downloading {release.Version}");
        _ = Task.Run(async () =>
        {
            try
            {
                var staged = await FetchAsync(release);
                Log($"downloaded and signature checked: {staged}");
                _ui.TryEnqueue(() => { _staged = staged; Set(Phases.Ready); });
            }
            catch (Exception e)
            {
                Log($"failed: {e.Message}");
                _ui.TryEnqueue(() => Fail(release.Page));
            }
        });
    }

    /// <summary>The installer and its signature, checked; the path of the installer.</summary>
    private async Task<string> FetchAsync(WinRelease release)
    {
        Directory.CreateDirectory(Folder);
        var file = Path.Combine(Folder, release.Installer.Segments[^1]);
        var step = "download of the installer";
        try
        {
            using (var response = await _http.GetAsync(release.Installer, HttpCompletionOption.ResponseHeadersRead))
            {
                response.EnsureSuccessStatusCode();
                var total = response.Content.Headers.ContentLength ?? 0;
                await using var source = await response.Content.ReadAsStreamAsync();
                await using var target = File.Create(file);
                var buffer = new byte[1 << 17];
                long done = 0;
                var shown = -1;
                int read;
                while ((read = await source.ReadAsync(buffer)) > 0)
                {
                    await target.WriteAsync(buffer.AsMemory(0, read));
                    done += read;
                    // In whole percents, so the capsule is not redrawn for every packet.
                    var percent = total > 0 ? (int)(done * 100 / total) : 0;
                    if (percent != shown)
                    {
                        shown = percent;
                        _ui.TryEnqueue(() => { Progress = percent / 100.0; Tell(); });
                    }
                }
            }
            step = "download of the signature";
            var signature = await _http.GetStringAsync(release.Signature);
            step = "signature check";
            await using (var stream = File.OpenRead(file))
            {
                if (!UpdateSignature.IsValid(signature, stream)) throw new InvalidDataException("not signed with the project's key");
            }
            return file;
        }
        catch (Exception e)
        {
            // Of no use unchecked; it is not left behind in the temporary folder.
            try { File.Delete(file); } catch (Exception) { }
            throw new IOException($"{step}: {e.GetType().Name}: {e.Message}", e);
        }
    }

    /// <summary>Runs the checked installer quietly and quits; it opens the new version when it is done.</summary>
    public void Relaunch(Action quit)
    {
        if (Phase != Phases.Ready || _staged is null || Release is not { } release) return;
        // Written first: if the installer fails once this copy has quit, the next one to open learns it here.
        Storage.Write(PendingFile, new PendingUpdate(release.Version, release.Page, DateTime.UtcNow).ToJson());
        try
        {
            Process.Start(new ProcessStartInfo(_staged, $"/SILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS /LOG=\"{InstallerLog}\"") { UseShellExecute = true });
            Log($"installer started for {release.Version}; quitting for it");
        }
        catch (Exception e)
        {
            try { File.Delete(PendingFile); } catch (Exception) { }
            Log($"failed: start of the installer: {e.GetType().Name}: {e.Message}");
            Fail(release.Page);
            return;
        }
        quit();
    }

    public void OpenPage() => Open(Release?.Page ?? _failedPage ?? ReleasesPage);

    private void Fail(Uri page)
    {
        _failedPage = page;
        Postponed = false;
        Set(Phases.Failed);
    }

    private static void Open(Uri page) => _ = Windows.System.Launcher.LaunchUriAsync(page);

    private void Set(Phases phase)
    {
        Phase = phase;
        Tell();
    }

    private void Tell() => Changed?.Invoke();
}
