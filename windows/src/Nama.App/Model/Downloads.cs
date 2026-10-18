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
    /// <summary>The parts of a file fetched over several connections, and how far each has got, to go on from after a restart.</summary>
    public List<SegmentState>? Segments { get; init; }

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

    /// <summary>
    /// Connections per file, at most. Measured on 30nama's own server: one gave 11 to 18 MB/s, four
    /// 33, eight 28, so four is where more stops helping. A server that takes fewer gets fewer.
    /// </summary>
    private const int MostConnections = 4;
    /// <summary>How long a connection may go without an answer, or without a byte, before it counts as dropped.</summary>
    private static readonly TimeSpan Silence = TimeSpan.FromSeconds(30);
    private readonly AppModel _app;
    private readonly DispatcherQueue _ui;
    private readonly Transport _network = ApiClient.HttpTransport(decompress: false);
    private readonly Dictionary<string, CancellationTokenSource> _running = [];
    private readonly Dictionary<string, DateTime> _lastShown = [];
    private readonly SubtitleFinder _subtitles;
    private readonly RateLimit _limit = new();
    /// <summary>Notices the download hours opening and closing.</summary>
    private readonly Microsoft.UI.Xaml.DispatcherTimer _hours = new() { Interval = TimeSpan.FromSeconds(30) };
    private bool _wasOpen = true;

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
        _limit.BytesPerSecond = app.Settings.DownloadLimit * 1024.0;
        app.Changed += what =>
        {
            if (what != "settings") return;
            _limit.BytesPerSecond = app.Settings.DownloadLimit * 1024.0;
            CheckHours();
            ApplyQueue();
        };
        _wasOpen = !OutsideHours;
        _hours.Tick += (_, _) => CheckHours();
        _hours.Start();
        // What was being fetched when the app closed goes on now, oldest first.
        foreach (var item in Items.Where(i => i.State == DownloadItem.States.Running).OrderBy(i => i.Added).ToList()) Begin(item);
#if DEBUG
        // NAMA_DOWNLOAD_TEST=<address>[ <address>...]: files of any server, for the engine to be tried on.
        foreach (var address in (Environment.GetEnvironmentVariable("NAMA_DOWNLOAD_TEST") ?? "").Split(' ', StringSplitOptions.RemoveEmptyEntries))
        {
            var url = new Uri(address);
            var test = new DownloadItem
            {
                Id = Guid.NewGuid().ToString(), Title = "Test " + url.Host, Detail = url.AbsolutePath, FileName = "test-" + System.IO.Path.GetFileName(url.AbsolutePath),
                Address = url, State = DownloadItem.States.Running, Added = DateTime.UtcNow.AddMilliseconds(Items.Count),
            };
            Items.Insert(0, test);
            Save();
            Begin(test);
        }
#endif
    }

    public int RunningCount => Items.Count(i => i.State == DownloadItem.States.Running);

    /// <summary>It is outside the hours set for downloads: those not paused wait for them.</summary>
    public bool OutsideHours => !DownloadHours.Open(_app.Settings.DownloadFrom, _app.Settings.DownloadTo, DateTime.Now);

    /// <summary>Whether this download's bytes are coming in right now, rather than waiting its turn or its hours.</summary>
    public bool IsActive(string id) => _running.ContainsKey(id);

    /// <summary>How many files come in at once (Settings); the rest wait in the order they were added.</summary>
    private int AtOnce => Math.Max(1, _app.Settings.DownloadsAtOnce);

    /// <summary>
    /// After the number allowed at once changed: past it, the ones added last go back to waiting,
    /// from where they got to; under it, the next ones waiting start.
    /// </summary>
    private void ApplyQueue()
    {
        var extra = Items.Where(i => _running.ContainsKey(i.Id)).OrderBy(i => i.Added).Skip(AtOnce).ToList();
        foreach (var item in extra)
            if (_running.Remove(item.Id, out var stop)) stop.Cancel();
        Next();
        Changed?.Invoke();
    }

    /// <summary>The hours have opened: what waited starts. They have closed: what runs stops where it is, to go on later.</summary>
    private void CheckHours()
    {
        var open = !OutsideHours;
        if (open == _wasOpen) return;
        _wasOpen = open;
        if (open) Next();
        else
        {
            foreach (var stop in _running.Values) stop.Cancel();
            _running.Clear();
        }
        Changed?.Invoke();
    }

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
        if (_running.ContainsKey(item.Id) || _running.Count >= AtOnce || OutsideHours) return;
        var stop = new CancellationTokenSource();
        _running[item.Id] = stop;
        _ = Task.Run(() => FetchAsync(item, stop.Token));
    }

    /// <summary>Gives a waiting download the slot one has left.</summary>
    private void Next()
    {
        foreach (var waiting in Items.Where(i => i.State == DownloadItem.States.Running && !_running.ContainsKey(i.Id)).OrderBy(i => i.Added).ToList())
            Begin(waiting);
    }

    /// <summary>A transfer that ended with this HTTP status, and the wait the server asked for, if any.</summary>
    private sealed class Refused(int status, TimeSpan? retryAfter) : Exception($"HTTP {status}")
    {
        public int Status { get; } = status;
        public TimeSpan? RetryAfter { get; } = retryAfter;
    }

    /// <summary>
    /// Fetches the file, from where the part already here ends. A dropped connection or a busy
    /// server is tried again a few times, a little later each time, from wherever it got to; a
    /// refusal, such as a link that has expired, fails the download at once.
    /// </summary>
    private async Task FetchAsync(DownloadItem item, CancellationToken stop)
    {
        var partial = Partial(item);
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(partial)!);
        Note(item.Id, $"started {item.FileName}");
        for (var attempt = 1; ; attempt++)
        {
            try
            {
                await TransferAsync(item, partial, stop);
                _ui.TryEnqueue(() => Arrived(item.Id, partial));
                return;
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { return; }
            catch (Exception e)
            {
                var refused = e as Refused;
                if (RetryPolicy.Next(attempt, refused?.Status, refused?.RetryAfter, Random.Shared.NextDouble()) is { } wait)
                {
                    try { await Task.Delay(wait, stop); }
                    catch (OperationCanceledException) { return; }
                    continue;
                }
                break;
            }
        }
        // Given up: the download is marked failed, and its place goes to the next one.
        _ui.TryEnqueue(() =>
        {
            if (!_running.Remove(item.Id) || Items.All(i => i.Id != item.Id)) return;
            Change(item.Id, i => i with { State = DownloadItem.States.Failed });
            Save();
            _app.ShowToast(_app.Loc[S.downloadFailed]);
            Next();
        });
    }

    /// <summary>
    /// One try at the rest of the file. Returns once it has all arrived; throws otherwise. The first
    /// request asks from where the file has got to; when the server answers with a part of it and
    /// says how large it is, more connections join, each taking half of what is left of the
    /// largest part (<see cref="SegmentMap"/>). A server that sends the whole file instead is
    /// read to the end over the one connection.
    /// </summary>
    private async Task TransferAsync(DownloadItem item, string partial, CancellationToken stop)
    {
        var current = Items.FirstOrDefault(i => i.Id == item.Id) ?? item;
        var size = System.IO.File.Exists(partial) ? new FileInfo(partial).Length : 0;
        if (current.Segments is { Count: > 0 } kept && current.Expected > 0 && size == current.Expected)
        {
            await PartsAsync(item.Id, partial, new SegmentMap(kept, splittable: true), null, stop);
            return;
        }
        // A file from before the parts, or none yet: it goes on from where it ends.
        var have = current.Segments is null ? size : 0;
        var request = Request(item, have, null);
        using var quiet = CancellationTokenSource.CreateLinkedTokenSource(stop);
        quiet.CancelAfter(TimeSpan.FromSeconds(45));
        var response = await _network(request, quiet.Token);
        try
        {
            Check(response);
            if (response.StatusCode == System.Net.HttpStatusCode.PartialContent && response.Content.Headers.ContentRange?.Length is { } total and > 0)
            {
                await using (var file = new FileStream(partial, FileMode.OpenOrCreate, FileAccess.Write, FileShare.ReadWrite)) file.SetLength(total);
                var map = SegmentMap.Whole(total, have);
                _ui.TryEnqueue(() => Change(item.Id, i => i with { Expected = total, Received = have, Segments = map.Snapshot() }));
                await PartsAsync(item.Id, partial, map, response, stop);
                response = null;
                return;
            }
            // The whole file, from its first byte, over this one connection.
            var expected = response.Content.Headers.ContentLength ?? 0;
            _ui.TryEnqueue(() => Change(item.Id, i => i with { Expected = expected > 0 ? expected : i.Expected, Received = 0, Segments = null }));
            var received = 0L;
            await using (var source = await response.Content.ReadAsStreamAsync(quiet.Token))
            await using (var target = new FileStream(partial, FileMode.Create, FileAccess.Write, FileShare.Read, 1 << 16, true))
            {
                var buffer = new byte[1 << 16];
                int n;
                while (true)
                {
                    quiet.CancelAfter(TimeSpan.FromSeconds(45));
                    if ((n = await source.ReadAsync(buffer, quiet.Token)) <= 0) break;
                    await target.WriteAsync(buffer.AsMemory(0, n), stop);
                    received += n;
                    await HoldAsync(n, stop);
                    Progressed(item.Id, received);
                }
            }
            // A connection that ended early is not a finished file.
            if (expected > 0 && received < expected) throw new IOException($"ended at {received} of {expected}");
        }
        finally { response?.Dispose(); }
    }

    private HttpRequestMessage Request(DownloadItem item, long from, long? to)
    {
        var request = new HttpRequestMessage(HttpMethod.Get, item.Address);
        request.Headers.TryAddWithoutValidation("User-Agent", _app.Api.Config.UserAgent);
        // Asked as a range even from the first byte: the answer says whether the server can send parts.
        request.Headers.Range = new System.Net.Http.Headers.RangeHeaderValue(from, to);
        return request;
    }

    private static void Check(HttpResponseMessage response)
    {
        if ((int)response.StatusCode >= 400)
            throw new Refused((int)response.StatusCode, response.Headers.RetryAfter?.Delta ?? (response.Headers.RetryAfter?.Date - DateTimeOffset.UtcNow));
    }

    /// <summary>Waits as long as the speed limit says for bytes just read: the whole debt they leave.</summary>
    private async Task HoldAsync(long bytes, CancellationToken stop)
    {
        var wait = _limit.Take(bytes);
        if (wait > TimeSpan.Zero) await Task.Delay(wait, stop);
    }

    /// <summary>What a test reads of a transfer: a line now and then, in a build being worked on.</summary>
    [Conditional("DEBUG")]
    private static void Note(string id, string line)
    {
        try { System.IO.File.AppendAllText(System.IO.Path.Combine(Storage.Folder, "downloads-debug.log"), $"{DateTime.Now:HH:mm:ss.fff} {id[..8]} {line}{Environment.NewLine}"); }
        catch (Exception) { }
    }

    /// <summary>
    /// The file over several connections until every part is in. Up to four work at once, joining
    /// a little apart so the server is not hit all at once; a server that takes fewer from one
    /// address (it leaves the rest unanswered, or says 429 or 503) gets fewer: such a connection,
    /// while others work, gives its part back and the most allowed comes down to those working. A
    /// connection waiting to try again still counts, or new ones would join in its place. Where
    /// each part has got to is kept every few seconds, and when it stops for any reason.
    /// </summary>
    private async Task PartsAsync(string id, string partial, SegmentMap map, HttpResponseMessage? opened, CancellationToken stop)
    {
        using var all = CancellationTokenSource.CreateLinkedTokenSource(stop);
        var keeper = Task.Run(async () =>
        {
            while (!all.IsCancellationRequested)
            {
                try { await Task.Delay(500, all.Token); } catch (OperationCanceledException) { return; }
                Progressed(id, map.Downloaded);
            }
        });
        var crew = new Crew { Cap = MostConnections };
        var running = new List<Task>();
        Exception? failure = null;
        var launched = 0;
        void Fill()
        {
            lock (crew)
            {
                while (crew.Busy < crew.Cap && !all.IsCancellationRequested)
                {
                    var first = opened is not null;
                    var segment = first ? map.TakeUnowned() : map.NextWork();
                    if (segment is null) return;
                    crew.Busy++;
                    // The first few join a little apart; one that replaces another joins at once.
                    var index = launched++;
                    var delay = index < MostConnections ? TimeSpan.FromMilliseconds(400 * index) : TimeSpan.Zero;
                    var answer = opened;
                    opened = null;
                    running.Add(Task.Run(async () =>
                    {
                        try { await ConnectionAsync(id, partial, map, segment, answer, delay, crew, all.Token); }
                        catch (OperationCanceledException) { }
                        catch (Exception e)
                        {
                            // A refusal, or one connection giving up for good, ends the try for all of them.
                            failure ??= e;
                            all.Cancel();
                        }
                        finally { lock (crew) crew.Busy--; }
                    }));
                }
            }
        }
        while (true)
        {
            Fill();
            Task[] now;
            lock (crew) now = running.ToArray();
            if (now.Length == 0) break;
            var done = await Task.WhenAny(now);
            lock (crew) running.Remove(done);
            if (failure is not null || all.IsCancellationRequested)
            {
                await Task.WhenAll(now);
                break;
            }
        }
        all.Cancel();
        await keeper;
        var snapshot = map.Snapshot();
        _ui.TryEnqueue(() => { Change(id, i => i with { Segments = snapshot, Received = map.Downloaded }); Save(); });
        stop.ThrowIfCancellationRequested();
        if (failure is not null) throw failure;
        if (!map.IsComplete) throw new IOException("parts missing");
    }

    /// <summary>The connections of one file: how many may work, how many hold a part, how many are bringing bytes in.</summary>
    private sealed class Crew
    {
        public int Cap;
        public int Busy;
        public int Working;
    }

    /// <summary>
    /// One connection: fetches its part to its end, or until another connection takes its tail. A
    /// drop is tried again after a wait, keeping the part meanwhile; one that brought nothing
    /// while others work gives the part back and lowers the most allowed instead.
    /// </summary>
    private async Task ConnectionAsync(string id, string partial, SegmentMap map, SegmentMap.Segment segment, HttpResponseMessage? opened,
        TimeSpan delay, Crew crew, CancellationToken stop)
    {
        var item = Items.FirstOrDefault(i => i.Id == id) ?? throw new OperationCanceledException();
        if (delay > TimeSpan.Zero) await Task.Delay(delay, stop);
        await using var file = new FileStream(partial, FileMode.Open, FileAccess.Write, FileShare.ReadWrite);
        var buffer = new byte[1 << 16];
        for (var attempt = 1; ; )
        {
            var received = 0L;
            int? status = null;
            lock (crew) crew.Working++;
            var working = true;
            try
            {
                using var quiet = CancellationTokenSource.CreateLinkedTokenSource(stop);
                quiet.CancelAfter(Silence);
                var response = opened ?? await _network(Request(item, segment.Pos, segment.End - 1), quiet.Token);
                opened = null;
                using (response)
                {
                    status = (int)response.StatusCode;
                    Check(response);
                    await using var source = await response.Content.ReadAsStreamAsync(quiet.Token);
                    while (!segment.IsComplete)
                    {
                        quiet.CancelAfter(Silence);
                        var n = await source.ReadAsync(buffer, quiet.Token);
                        if (n <= 0) break;
                        var kept = segment.Write(file, buffer.AsSpan(0, n));
                        received += kept;
                        attempt = 1;
                        // Another connection took the rest of this part.
                        if (kept < n) break;
                        await HoldAsync(kept, stop);
                    }
                }
                if (!segment.IsComplete && segment.Pos < segment.End) throw new IOException("part ended early");
                return;
            }
            catch (OperationCanceledException) when (stop.IsCancellationRequested) { segment.Release(); throw; }
            catch (Exception e)
            {
                lock (crew) { crew.Working--; working = false; }
                var refused = e as Refused;
                var code = refused?.Status ?? (status >= 400 ? status : null);
                int others;
                lock (crew) others = crew.Working;
                if (Connections.GivesWay(code, received, others))
                {
                    lock (crew) crew.Cap = Math.Max(1, others);
                    segment.Release();
                    Note(id, $"a connection {(code is { } c ? $"got {c}" : "went unanswered")}; going on with {Math.Max(1, others)}");
                    return;
                }
                if (RetryPolicy.Next(attempt++, code, refused?.RetryAfter, Random.Shared.NextDouble()) is not { } wait)
                {
                    segment.Release();
                    throw;
                }
                Note(id, $"part at {segment.Pos / 1_048_576} MB: {(code is { } c2 ? $"HTTP {c2}" : e.GetType().Name)}; again in {wait.TotalSeconds:0} s");
                // The part stays this connection's while it waits, and the connection still counts.
                await Task.Delay(wait, stop);
            }
            finally
            {
                if (working) lock (crew) crew.Working--;
            }
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
        Note(id, "arrived");
        _running.Remove(id);
        if (Items.FirstOrDefault(i => i.Id == id) is not { } item) { TryDelete(partial); return; }
        try
        {
            Directory.CreateDirectory(Folder);
            var target = Free(System.IO.Path.Combine(Folder, item.FileName));
            System.IO.File.Move(partial, target);
            Change(id, i => i with { State = DownloadItem.States.Done, Path = target, Received = Math.Max(i.Received, i.Expected), Expected = Math.Max(i.Received, i.Expected), Segments = null });
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
