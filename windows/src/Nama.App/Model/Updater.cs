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
/// </summary>
public sealed class Updater
{
    public enum Phases { Idle, Available, Downloading, Ready, Failed }

    public static Updater Shared { get; private set; } = null!;

    private readonly DispatcherQueue _ui;
    private readonly HttpClient _http = new() { Timeout = Timeout.InfiniteTimeSpan };
    private DateTime _lastLook = DateTime.MinValue;
    private string? _staged;

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
    }

    private static string Folder => Path.Combine(Path.GetTempPath(), "30nama-update");

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

    private static Uri Feed
    {
        get
        {
#if DEBUG
            if (Environment.GetEnvironmentVariable("NAMA_UPDATE_FEED") is { Length: > 0 } feed) return new Uri(feed);
#endif
            return WinRelease.Feed;
        }
    }

    /// <summary>
    /// A copy that the installer put in place, and so one it can replace. A copy run from anywhere
    /// else (a build folder, one copied by hand) is sent to the release page instead.
    /// </summary>
    private static bool Installed => File.Exists(Path.Combine(AppContext.BaseDirectory, "unins000.exe"));

    /// <summary>Looks for a newer release, at most every few hours: GitHub answers sixty times an hour per address.</summary>
    public async Task LookAsync()
    {
#if DEBUG
        // A build being worked on is not the released app; it looks only when told to.
        if (Environment.GetEnvironmentVariable("NAMA_UPDATE_FEED") is null) return;
#endif
        if (Phase != Phases.Idle || DateTime.UtcNow - _lastLook < TimeSpan.FromHours(4)) return;
        _lastLook = DateTime.UtcNow;
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, Feed);
            request.Headers.Accept.ParseAdd("application/vnd.github+json");
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(20));
            using var response = await _http.SendAsync(request, timeout.Token);
            if ((int)response.StatusCode != 200) return;
            var feed = Json.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token));
            if (WinRelease.Newest(feed, Current) is not { } release) return;
            Release = release;
            Set(Phases.Available);
        }
        catch (Exception) { }
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
            Open(release.Page);
            return;
        }
        Progress = 0;
        Set(Phases.Downloading);
        _ = Task.Run(async () =>
        {
            try
            {
                var staged = await FetchAsync(release);
                _ui.TryEnqueue(() => { _staged = staged; Set(Phases.Ready); });
            }
            catch (Exception)
            {
                _ui.TryEnqueue(() => Set(Phases.Failed));
            }
        });
    }

    /// <summary>The installer and its signature, checked; the path of the installer.</summary>
    private async Task<string> FetchAsync(WinRelease release)
    {
        Directory.CreateDirectory(Folder);
        var file = Path.Combine(Folder, release.Installer.Segments[^1]);
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
            var signature = await _http.GetStringAsync(release.Signature);
            await using (var stream = File.OpenRead(file))
            {
                if (!UpdateSignature.IsValid(signature, stream)) throw new InvalidDataException("signature");
            }
            return file;
        }
        catch (Exception)
        {
            // Of no use unchecked; it is not left behind in the temporary folder.
            try { File.Delete(file); } catch (Exception) { }
            throw;
        }
    }

    /// <summary>Runs the checked installer quietly and quits; it opens the new version when it is done.</summary>
    public void Relaunch(Action quit)
    {
        if (Phase != Phases.Ready || _staged is null) return;
        try
        {
            Process.Start(new ProcessStartInfo(_staged, "/SILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS") { UseShellExecute = true });
        }
        catch (Exception)
        {
            Set(Phases.Failed);
            return;
        }
        quit();
    }

    public void OpenPage()
    {
        if (Release is { } release) Open(release.Page);
    }

    private static void Open(Uri page) => _ = Windows.System.Launcher.LaunchUriAsync(page);

    private void Set(Phases phase)
    {
        Phase = phase;
        Tell();
    }

    private void Tell() => Changed?.Invoke();
}
