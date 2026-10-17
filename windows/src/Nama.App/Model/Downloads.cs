using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.UI.Dispatching;
using Nama.Core;

namespace Nama.App.Model;

/// <summary>One download, as the list shows it and as it is remembered between launches.</summary>
public sealed record DownloadItem
{
    public enum States { Running, Paused, Done, Failed }
    /// <summary>The three parts of the downloads section.</summary>
    public enum Shelves { Movies, Series, Anime }

    public required string Id { get; init; }
    /// <summary>The title it belongs to.</summary>
    public required string Title { get; init; }
    /// <summary>Which file of that title: "S3 · E16 · x265 BluRay 1080p · 340 MB".</summary>
    public required string Detail { get; init; }
    public required string FileName { get; init; }
    public required Uri Address { get; init; }
    public States State { get; init; }
    public long Received { get; init; }
    public long Expected { get; init; }
    /// <summary>Where the finished file is.</summary>
    public string? Path { get; init; }
    public int? PostId { get; init; }
    /// <summary>"movie", "series" or "anime".</summary>
    public string? Kind { get; init; }
    public bool IsSeries { get; init; }
    public int? Season { get; init; }
    public string? Episode { get; init; }
    /// <summary>"x265 BluRay 1080p · 30nama · 340 MB", without the episode.</summary>
    public string? Quality { get; init; }
    public Uri? Cover { get; init; }
    public Uri? Poster { get; init; }
    public string? Blurhash { get; init; }
    /// <summary>Persian subtitles saved beside the file.</summary>
    public string? SubtitlePath { get; init; }
    /// <summary>The page of the subtitle site that lists Persian subtitles for this film or season.</summary>
    public Uri? SubtitlePage { get; init; }
    public DateTime Added { get; init; }

    [JsonIgnore] public double Fraction => Expected > 0 ? Math.Min(1, (double)Received / Expected) : 0;

    [JsonIgnore] public Shelves Shelf => Kind == "anime" ? Shelves.Anime : IsSeries || Season is not null ? Shelves.Series : Shelves.Movies;

    /// <summary>Episodes of one series stand together under this.</summary>
    [JsonIgnore] public string Group => PostId?.ToString() ?? Title;

    /// <summary>Enough of the title to draw its artwork and to hand to the player.</summary>
    public Post? ToPost()
    {
        var poster = new Dictionary<string, object?>();
        if (Blurhash is not null) poster["preview"] = Blurhash;
        if (Poster is not null) poster["webp"] = new Dictionary<string, object?> { ["large"] = Poster.OriginalString, ["big"] = Poster.OriginalString };
        var image = new Dictionary<string, object?> { ["poster"] = poster };
        if (Cover is not null) image["cover"] = new Dictionary<string, object?> { ["webp"] = Cover.OriginalString };
        var options = new Dictionary<string, object?> { ["is_series"] = Shelf != Shelves.Movies };
        if (Kind is not null) options["title_type"] = Kind;
        return Nama.Core.Post.Parse(Json.From(new Dictionary<string, object?>
        {
            ["id"] = PostId ?? Math.Abs(Title.GetHashCode() % 1_000_000),
            ["title"] = new Dictionary<string, object?> { ["english"] = Title, ["local"] = Title },
            ["options"] = options, ["image"] = image,
        }));
    }
}

/// <summary>
/// Fetches files from 30nama into a folder on this computer. Windows has no transfer service of
/// the kind the Mac app hands its downloads to, so the app fetches them itself: a few at a time,
/// carried on from where they stopped (the server takes byte ranges), at most as fast as the user
/// allows, and started again when the app is next opened.
/// </summary>
public sealed partial class Downloads
{
    public static Downloads Shared { get; private set; } = null!;

    private const int AtOnce = 3;
    private readonly AppModel _app;
    private readonly DispatcherQueue _ui;
    private readonly Transport _network = ApiClient.HttpTransport();
    private readonly Dictionary<string, CancellationTokenSource> _running = [];
    private readonly Dictionary<string, DateTime> _lastShown = [];
    private readonly SubtitleFinder _subtitles;
    private readonly Throttle _throttle = new();

    public List<DownloadItem> Items { get; private set; } = [];
    /// <summary>The downloads a subtitle is being looked for right now.</summary>
    public HashSet<string> Seeking { get; } = [];

    public event Action? Changed;

    private static string File => System.IO.Path.Combine(Storage.Folder, "downloads.json");
    private static readonly JsonSerializerOptions Options = new() { Converters = { new JsonStringEnumConverter() }, DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull };

    public Downloads(AppModel app, DispatcherQueue ui)
    {
        _app = app;
        _ui = ui;
        Shared = this;
        _subtitles = app.Subtitles;
        try { Items = Storage.Read(File) is { } text ? JsonSerializer.Deserialize<List<DownloadItem>>(text, Options) ?? [] : []; }
        catch (Exception) { Items = []; }
        _throttle.BytesPerSecond = app.Settings.DownloadLimit * 1024L;
        app.Changed += what => { if (what == "settings") _throttle.BytesPerSecond = app.Settings.DownloadLimit * 1024L; };
        // What was being fetched when the app closed goes on now.
        foreach (var item in Items.Where(i => i.State == DownloadItem.States.Running).ToList()) Begin(item);
    }

    public int RunningCount => Items.Count(i => i.State == DownloadItem.States.Running);

    /// <summary>Where finished files go: the folder chosen in Settings, or the user's Downloads.</summary>
    public string Folder
    {
        get
        {
            if (_app.Settings.DownloadFolder is { } chosen && Directory.Exists(chosen)) return chosen;
            return Native.DownloadsFolder() ?? System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
        }
    }

    /// <summary>How much room is left on the drive the downloads go to.</summary>
    public long? FreeSpace
    {
        get
        {
            try { return new DriveInfo(System.IO.Path.GetPathRoot(Folder)!).AvailableFreeSpace; }
            catch (Exception) { return null; }
        }
    }

    // MARK: Actions

    /// <summary>Starts fetching one file. Asking for a file that is already in the list does nothing.</summary>
    public void Start(DownloadFile file, Post post)
    {
        if (Has(file)) return;
        var loc = _app.Loc;
        var parts = new List<string>();
        if (file.Season is { } season && file.Episode is { } episode) parts.Add(loc.EpisodeTag(season, episode));
        parts.Add(file.Summary);
        var item = new DownloadItem
        {
            Id = Guid.NewGuid().ToString(), Title = post.Title(loc.Lang), Detail = string.Join(" · ", parts), FileName = file.FileName,
            Address = file.Url, State = DownloadItem.States.Running, PostId = post.Id, Kind = post.Kind.ToString().ToLowerInvariant(),
            IsSeries = post.IsSeries, Season = file.Season, Episode = file.Episode, Quality = file.Summary, Cover = post.Cover,
            Poster = post.Poster.Big ?? post.Poster.Large ?? post.Poster.Medium, Blurhash = post.Poster.Blurhash, Added = DateTime.UtcNow,
            SubtitlePage = post.SubtitlePages.FirstOrDefault(p => p.Persian && (p.Season is null || p.Season == file.Season))?.Link,
        };
        Items.RemoveAll(i => i.FileName == file.FileName);
        Items.Insert(0, item);
        Save();
        Begin(item);
        // 30nama's own subtitle when it has one; otherwise the one on the subtitle site that fits this copy.
        if (file.Subtitle is { } subtitle) _ = FetchSubtitleAsync(subtitle, item.Id);
        else FindSubtitle(item.Id, quietly: true);
        Changed?.Invoke();
    }

    /// <summary>
    /// Whether this file is already in the list. Its address carries a token that changes with
    /// every answer from the server, so a file is known by its name.
    /// </summary>
    public bool Has(DownloadFile file) => Items.Any(i => i.FileName == file.FileName && i.State != DownloadItem.States.Failed);

    public void Pause(string id)
    {
        if (_running.Remove(id, out var stop)) stop.Cancel();
        Change(id, i => i with { State = DownloadItem.States.Paused });
        Save();
        Next();
    }

    public void Resume(string id)
    {
        if (Items.FirstOrDefault(i => i.Id == id) is not { } item) return;
        Change(id, i => i with { State = DownloadItem.States.Running });
        Save();
        Begin(Items.First(i => i.Id == id));
    }

    /// <summary>
    /// Takes a download off the list. A finished file stays where it is unless
    /// <paramref name="trashing"/> says it should go too; then it and its subtitles go to the Recycle
    /// Bin, where they can be had back.
    /// </summary>
    public void Remove(string id, bool trashing = false)
    {
        if (_running.Remove(id, out var stop)) stop.Cancel();
        if (Items.FirstOrDefault(i => i.Id == id) is { } item)
        {
            if (item.State != DownloadItem.States.Done)
            {
                // Subtitles fetched for a film that never arrived, and the part that did, would only be litter in the folder.
                TryDelete(Partial(item));
                if (item.SubtitlePath is { } subtitle) TryDelete(subtitle);
            }
            else if (trashing)
            {
                foreach (var path in new[] { item.Path, item.SubtitlePath }.OfType<string>()) Native.Recycle(path);
            }
        }
        Items.RemoveAll(i => i.Id == id);
        Save();
        Changed?.Invoke();
        Next();
    }

    public void Reveal(DownloadItem item)
    {
        if (item.Path is { } path && System.IO.File.Exists(path)) Process.Start("explorer.exe", $"/select,\"{path}\"");
        else Process.Start("explorer.exe", $"\"{Folder}\"");
    }

    public void OpenFolder() => Process.Start("explorer.exe", $"\"{Folder}\"");

    // MARK: Playing what was downloaded

    /// <summary>The token of an item that is a file on this computer, not a stream. 30nama is told nothing about it.</summary>
    public const string LocalToken = "local";

    /// <summary>
    /// Plays a finished file in the app's own player, with the other downloaded episodes of the
    /// same series queued after it.
    /// </summary>
    public void Play(DownloadItem item)
    {
        if (item.Path is not { } path || !System.IO.File.Exists(path)) return;
        var queue = item.Shelf == DownloadItem.Shelves.Movies ? [item] : Items
            .Where(i => i.Group == item.Group && i.State == DownloadItem.States.Done && i.Path is not null)
            .OrderBy(i => i.Season ?? 0).ThenBy(i => Nama.Core.Episode.Number(i.Episode) ?? 0).ToList();
        var entries = queue.Select(StreamItemOf).OfType<StreamItem>().ToList();
        if (item.ToPost() is not { } post || entries.Count != queue.Count) return;
        var index = queue.FindIndex(i => i.Id == item.Id);
        var left = Positions.GetValueOrDefault("local:" + item.Id);
        var resume = left > 5 ? ContinueWatching.Parse(Json.From(new Dictionary<string, object?> { ["file_id"] = "local:" + item.Id, ["time"] = left })) : null;
        _app.Play(new PlaybackRequest(post, entries, index, resume));
    }

    /// <summary>A downloaded file in the shape the player takes a stream in.</summary>
    private static StreamItem? StreamItemOf(DownloadItem item)
    {
        if (item.Path is null) return null;
        var file = new Dictionary<string, object?> { ["m3u8"] = new Uri(item.Path).AbsoluteUri };
        if (item.SubtitlePath is { } subtitle && System.IO.File.Exists(subtitle))
            file["subtitle"] = new Dictionary<string, object?> { ["fa"] = new Uri(subtitle).AbsoluteUri };
        var info = new Dictionary<string, object?> { ["file_id"] = "local:" + item.Id, ["season"] = item.Season ?? 1 };
        if (item.Episode is not null) info["episode"] = item.Episode;
        return StreamItem.Parse(Json.From(new Dictionary<string, object?> { ["info"] = info, ["options"] = new Dictionary<string, object?> { ["token"] = LocalToken }, ["file"] = file }));
    }

    private static string PositionsFile => System.IO.Path.Combine(Storage.Folder, "local-positions.json");

    /// <summary>Where each downloaded file was left, kept here since 30nama is not told.</summary>
    public static Dictionary<string, double> Positions { get; } = LoadPositions();

    private static Dictionary<string, double> LoadPositions()
    {
        try { return Storage.Read(PositionsFile) is { } text ? JsonSerializer.Deserialize<Dictionary<string, double>>(text) ?? [] : []; }
        catch (Exception) { return []; }
    }

    public static void KeepPosition(string fileId, double seconds)
    {
        Positions[fileId] = seconds;
        Storage.Write(PositionsFile, JsonSerializer.Serialize(Positions));
    }

    // MARK: Fetching

    private static string Partial(DownloadItem item) => System.IO.Path.Combine(Storage.Folder, "partial", item.Id + ".part");

    /// <summary>Starts the transfer when there is room for it; otherwise it waits its turn.</summary>
    private void Begin(DownloadItem item)
    {
        if (_running.ContainsKey(item.Id) || _running.Count >= AtOnce) return;
        var stop = new CancellationTokenSource();
        _running[item.Id] = stop;
        _ = Task.Run(() => FetchAsync(item, stop.Token));
    }

    /// <summary>Gives a waiting download the slot one has left.</summary>
    private void Next()
    {
        foreach (var waiting in Items.Where(i => i.State == DownloadItem.States.Running && !_running.ContainsKey(i.Id)).Reverse().ToList())
            Begin(waiting);
    }

    private async Task FetchAsync(DownloadItem item, CancellationToken stop)
    {
        var partial = Partial(item);
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(partial)!);
        try
        {
            var have = System.IO.File.Exists(partial) ? new FileInfo(partial).Length : 0;
            using var request = new HttpRequestMessage(HttpMethod.Get, item.Address);
            request.Headers.TryAddWithoutValidation("User-Agent", _app.Api.Config.UserAgent);
            if (have > 0) request.Headers.Range = new System.Net.Http.Headers.RangeHeaderValue(have, null);
            using var response = await _network(request, stop);
            if ((int)response.StatusCode >= 400) throw new HttpRequestException($"HTTP {(int)response.StatusCode}");
            // A server that ignores the range sends the whole file again: start the copy over.
            if (have > 0 && response.StatusCode != System.Net.HttpStatusCode.PartialContent) have = 0;
            var expected = response.Content.Headers.ContentLength is { } length ? have + length : 0;
            _ui.TryEnqueue(() => Change(item.Id, i => i with { Expected = expected > 0 ? expected : i.Expected, Received = have }));
            await using (var source = await response.Content.ReadAsStreamAsync(stop))
            await using (var target = new FileStream(partial, have > 0 ? FileMode.Append : FileMode.Create, FileAccess.Write, FileShare.Read, 1 << 16, true))
            {
                var buffer = new byte[1 << 16];
                var received = have;
                int n;
                while ((n = await source.ReadAsync(buffer, stop)) > 0)
                {
                    await target.WriteAsync(buffer.AsMemory(0, n), stop);
                    received += n;
                    await _throttle.TakeAsync(n, stop);
                    Progressed(item.Id, received);
                }
            }
            _ui.TryEnqueue(() => Arrived(item.Id, partial));
        }
        catch (OperationCanceledException) { }
        catch (Exception)
        {
            _ui.TryEnqueue(() =>
            {
                if (!_running.Remove(item.Id) || Items.All(i => i.Id != item.Id)) return;
                Change(item.Id, i => i with { State = DownloadItem.States.Failed });
                Save();
                _app.ShowToast(_app.Loc[S.downloadFailed]);
                Next();
            });
        }
    }

    /// <summary>A few times a second is plenty for a bar; every packet would redraw the list all day.</summary>
    private void Progressed(string id, long received)
    {
        lock (_lastShown)
        {
            if (_lastShown.TryGetValue(id, out var last) && DateTime.UtcNow - last < TimeSpan.FromSeconds(0.5)) return;
            _lastShown[id] = DateTime.UtcNow;
        }
        _ui.TryEnqueue(() => Change(id, i => i with { Received = received }));
    }

    /// <summary>The whole file is here: it moves into the download folder under its own name.</summary>
    private void Arrived(string id, string partial)
    {
        _running.Remove(id);
        if (Items.FirstOrDefault(i => i.Id == id) is not { } item) { TryDelete(partial); return; }
        try
        {
            Directory.CreateDirectory(Folder);
            var target = Free(System.IO.Path.Combine(Folder, item.FileName));
            System.IO.File.Move(partial, target);
            Change(id, i => i with { State = DownloadItem.States.Done, Path = target, Received = Math.Max(i.Received, i.Expected), Expected = Math.Max(i.Received, i.Expected) });
            _app.ShowToast($"{_app.Loc[S.downloadDone]}: {item.Title}");
        }
        catch (Exception)
        {
            Change(id, i => i with { State = DownloadItem.States.Failed });
        }
        Save();
        Next();
    }

    // MARK: Subtitles

    /// <summary>
    /// Looks on the subtitle site for a subtitle in time with this copy and saves it beside the
    /// file under the file's name, so that any player picks it up by itself.
    /// </summary>
    public void FindSubtitle(string id, bool quietly = false)
    {
        if (Items.FirstOrDefault(i => i.Id == id) is not { SubtitlePage: { } page } item || !Seeking.Add(id))
        {
            if (!quietly) _app.ShowToast(_app.Loc[S.subtitleMissing]);
            return;
        }
        Changed?.Invoke();
        var target = SubtitleTarget(item.FileName, item.Path is { } p ? System.IO.Path.GetDirectoryName(p)! : Folder);
        _ = Task.Run(async () =>
        {
            var text = await _subtitles.FromListingAsync(page, item.FileName, item.Season, Nama.Core.Episode.Number(item.Episode));
            var saved = text is not null && Write(target, text);
            _ui.TryEnqueue(() =>
            {
                Seeking.Remove(id);
                if (saved) { Change(id, i => i with { SubtitlePath = target }); Save(); }
                else Changed?.Invoke();
                if (!quietly) _app.ShowToast(_app.Loc[saved ? S.subtitleSaved : S.subtitleMissing]);
            });
        });
    }

    /// <summary>
    /// Fetches only the Persian subtitles of these files, for someone who already has the films:
    /// 30nama's own where it has them, else the ones on the subtitle site that fit each copy.
    /// Returns how many were found.
    /// </summary>
    public async Task<int> SubtitlesOnlyAsync(IReadOnlyList<DownloadFile> files, Post post)
    {
        var saved = 0;
        foreach (var file in files)
        {
            var target = SubtitleTarget(file.FileName, Folder);
            string? text = null;
            if (file.Subtitle is { } own && await GetAsync(own) is { } data && SubtitleSite.Text(data) is { } t && t.Contains("-->")) text = t;
            else if (post.SubtitlePages.FirstOrDefault(p => p.Persian && (p.Season is null || p.Season == file.Season))?.Link is { } page)
                text = await _subtitles.FromListingAsync(page, file.FileName, file.Season, Nama.Core.Episode.Number(file.Episode));
            if (text is not null && Write(target, text)) saved++;
        }
        _app.ShowToast(_app.Loc[saved > 0 ? S.subtitleSaved : S.subtitleMissing]);
        return saved;
    }

    /// <summary>Whether this file's Persian subtitles are already in the download folder.</summary>
    public bool HasSubtitle(DownloadFile file) => System.IO.File.Exists(SubtitleTarget(file.FileName, Folder));

    private static string SubtitleTarget(string fileName, string folder) =>
        System.IO.Path.Combine(folder, System.IO.Path.GetFileNameWithoutExtension(fileName) + ".fa.srt");

    /// <summary>Persian subtitles are small, so they are simply fetched and put next to the film under its name.</summary>
    private async Task FetchSubtitleAsync(Uri address, string id)
    {
        if (Items.FirstOrDefault(i => i.Id == id) is not { } item || await GetAsync(address) is not { } data) return;
        var text = SubtitleSite.Text(data);
        var target = SubtitleTarget(item.FileName, Folder);
        // An archive or a format of its own is kept as it came; text is written as UTF-8, which every player reads.
        var ok = text is not null && text.Contains("-->") ? Write(target, text) : WriteBytes(System.IO.Path.ChangeExtension(target, ".zip"), data);
        if (!ok) return;
        _ui.TryEnqueue(() => { Change(id, i => i with { SubtitlePath = text is not null ? target : System.IO.Path.ChangeExtension(target, ".zip") }); Save(); });
    }

    private async Task<byte[]?> GetAsync(Uri url)
    {
        try
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, url);
            request.Headers.TryAddWithoutValidation("User-Agent", _app.Api.Config.UserAgent);
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(25));
            using var response = await _network(request, timeout.Token);
            return (int)response.StatusCode < 400 ? await response.Content.ReadAsByteArrayAsync(timeout.Token) : null;
        }
        catch (Exception) { return null; }
    }

    private static bool Write(string path, string text)
    {
        try
        {
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(path)!);
            System.IO.File.WriteAllText(path, text, new UTF8Encoding(false));
            return true;
        }
        catch (Exception) { return false; }
    }

    private static bool WriteBytes(string path, byte[] data)
    {
        try { System.IO.File.WriteAllBytes(path, data); return true; }
        catch (Exception) { return false; }
    }

    // MARK: Helpers

    private void Change(string id, Func<DownloadItem, DownloadItem> edit)
    {
        var index = Items.FindIndex(i => i.Id == id);
        if (index < 0) return;
        Items[index] = edit(Items[index]);
        Changed?.Invoke();
    }

    private void Save() => Storage.Write(File, JsonSerializer.Serialize(Items, Options));

    private static void TryDelete(string path)
    {
        try { System.IO.File.Delete(path); } catch (Exception) { }
    }

    /// <summary>The name itself if nothing is there yet, else the same name with a number after it.</summary>
    private static string Free(string wanted)
    {
        var candidate = wanted;
        var folder = System.IO.Path.GetDirectoryName(wanted)!;
        var name = System.IO.Path.GetFileNameWithoutExtension(wanted);
        var ext = System.IO.Path.GetExtension(wanted);
        for (var n = 2; System.IO.File.Exists(candidate); n++) candidate = System.IO.Path.Combine(folder, $"{name} {n}{ext}");
        return candidate;
    }

    /// <summary>Keeps all downloads together under the speed the user set, by making each wait its turn for bytes.</summary>
    private sealed class Throttle
    {
        private readonly object _lock = new();
        private double _allowance;
        private DateTime _last = DateTime.UtcNow;

        public long BytesPerSecond { get; set; }

        public async Task TakeAsync(int bytes, CancellationToken stop)
        {
            while (true)
            {
                TimeSpan wait;
                lock (_lock)
                {
                    var rate = BytesPerSecond;
                    if (rate <= 0) return;
                    var now = DateTime.UtcNow;
                    _allowance = Math.Min(rate, _allowance + (now - _last).TotalSeconds * rate);
                    _last = now;
                    if (_allowance >= bytes) { _allowance -= bytes; return; }
                    wait = TimeSpan.FromSeconds((bytes - _allowance) / rate);
                }
                await Task.Delay(wait, stop);
            }
        }
    }

    private static partial class Native
    {
        private static readonly Guid Downloads = new("374DE290-123F-4565-9164-39C4925E467B");

        [LibraryImport("shell32.dll")]
        private static partial int SHGetKnownFolderPath(in Guid id, uint flags, IntPtr token, out IntPtr path);

        /// <summary>The user's Downloads folder, wherever they have moved it.</summary>
        public static string? DownloadsFolder()
        {
            if (SHGetKnownFolderPath(Downloads, 0, IntPtr.Zero, out var p) != 0) return null;
            try { return Marshal.PtrToStringUni(p); }
            finally { Marshal.FreeCoTaskMem(p); }
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct FileOperation
        {
            public IntPtr Window;
            public uint Function;
            public string From;
            public string? To;
            public ushort Flags;
            public int Aborted;
            public IntPtr Mappings;
            public string? Title;
        }

        [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
        private static extern int SHFileOperation(ref FileOperation operation);

        /// <summary>Sends a file to the Recycle Bin, without asking.</summary>
        public static void Recycle(string path)
        {
            if (!System.IO.File.Exists(path)) return;
            var operation = new FileOperation { Function = 3 /* delete */, From = path + "\0\0", Flags = 0x0040 /* allow undo */ | 0x0010 /* no confirmation */ | 0x0004 /* silent */ };
            _ = SHFileOperation(ref operation);
        }
    }
}
