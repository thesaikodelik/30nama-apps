using System.Text.Json;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Nama.App.Model;
using Nama.Core;

namespace Nama.App.Player;

/// <summary>
/// What drives playback: the stream's renditions and audio mixes, the app's own subtitles, the
/// skip and next-episode offers, and telling 30nama how far the user has got. The Mac app's
/// PlayerEngine, with mpv as the only decoder.
/// </summary>
public sealed class PlayerEngine
{
    public enum Offers { Intro, Recap, Next }

    /// <summary>The subtitle choice that is neither a language nor "off": a file the user picked.</summary>
    public const string FileSubtitle = "file";
    /// <summary>The subtitle choice for one found on SubSource: this, followed by the candidate's id.</summary>
    public const string Found = "found:";
    public static readonly double[] Speeds = [0.5, 0.75, 0.9, 1, 1.05, 1.1, 1.25, 1.5, 1.75, 2];
    /// <summary>What [ and ] change the speed by: small enough to catch up with a slow scene unnoticed.</summary>
    public const double SpeedStep = 0.05;

    public static string SpeedName(double speed) =>
        speed == 1 ? "1×" : $"{speed.ToString("0.##", System.Globalization.CultureInfo.InvariantCulture)}×";

    /// <summary>A stretch of the file with a name: the recap, the opening titles, the end credits.</summary>
    public sealed record Mark(Mark.Kinds Kind, double From, double To)
    {
        public enum Kinds { Recap, Intro, Credits }
    }

    public abstract record SleepTimer
    {
        public sealed record Off : SleepTimer;
        public sealed record EndOfEpisode : SleepTimer;
        public sealed record Minutes(int Count) : SleepTimer;
        public static IReadOnlyList<SleepTimer> Choices { get; } = [new Off(), new EndOfEpisode(), new Minutes(15), new Minutes(30), new Minutes(45), new Minutes(60)];
    }

    public Post Post { get; }
    public IReadOnlyList<StreamItem> Items { get; }
    public MpvPlayback Playback { get; }
    private readonly AppModel _app;
    private readonly DispatcherQueue _ui;
    private readonly DispatcherTimer _clock = new() { Interval = TimeSpan.FromMilliseconds(250) };
    private readonly DispatcherTimer _heartbeat = new() { Interval = TimeSpan.FromSeconds(10) };

    public int Index { get; private set; }
    public Quality? Quality { get; private set; }
    public AudioMix Mix { get; private set; }
    public double Time { get; private set; }
    public double Duration { get; private set; }
    /// <summary>What the user asked for; the picture may still be waiting on the network.</summary>
    public bool Playing { get; private set; } = true;
    public bool Buffering { get; private set; } = true;
    /// <summary>
    /// The picture is moving: the clock has gone forward between two looks at it. Until then the
    /// player shows the title's own artwork instead of a black window.
    /// </summary>
    public bool Started { get; private set; }
    private double? _lastSeen;
    public double Speed { get; private set; } = 1;
    public double Volume { get; private set; }
    public bool Muted { get; private set; }
    public string Cue { get; private set; } = "";
    public Offers? Offer { get; private set; }
    /// <summary>Next episode only: how far the credits have run, 0...1, when the next one starts by itself.</summary>
    public double? Countdown { get; private set; }
    public string SubtitleLanguage { get; private set; }
    /// <summary>Seconds the subtitles are shown later (or, negative, earlier) than their file says.</summary>
    public double SubtitleOffset { get; private set; }
    public string? SubtitleFileName { get; private set; }
    public bool Pinned { get; private set; }
    public SleepTimer Sleep { get; private set; } = new SleepTimer.Off();
    private CancellationTokenSource? _sleepTimer;
    /// <summary>The stretch last jumped over by itself, so that one the user goes back into is left alone.</summary>
    private string? _skippedOn;

    /// <summary>Persian subtitles found on SubSource for an item 30nama gives none for, best first.</summary>
    public IReadOnlyList<SubSource.Candidate> FoundSubtitles { get; private set; } = [];
    public bool FindingSubtitles { get; private set; }
    private CancellationTokenSource? _finding;

    private ContinueWatching? _resume;
    private SubtitleTrack _track = new();
    private SubtitleTrack? _fileTrack;
    private readonly Dictionary<Uri, SubtitleTrack> _subtitleCache = [];
    private CancellationTokenSource? _subtitleLoad;
    /// <summary>The last position reported for the current item, so the same one is not sent twice.</summary>
    private int _reportedTime;
    private bool _closed;
    private bool _markedWatched;

    /// <summary>Something the controls show has changed.</summary>
    public event Action? Changed;
    /// <summary>The window should be in full screen, or out of it, or kept above the others.</summary>
    public event Action? FullScreenToggled;
    public event Action<bool>? PinnedChanged;

    private Loc Loc => _app.Loc;
    public StreamItem Current => Items[Index];
    public bool HasNext => Index + 1 < Items.Count;

    public PlayerEngine(PlaybackRequest request, AppModel app, DispatcherQueue ui)
    {
        Post = request.Post;
        Items = request.Items;
        Index = Math.Clamp(request.Index, 0, Math.Max(0, request.Items.Count - 1));
        _app = app;
        // From whichever is further on, 30nama's record or this computer's: the server's keeps only
        // steps of thirty seconds and sometimes nothing at all.
        var serverIndex = request.Resume?.FileId is { } at ? request.Items.ToList().FindIndex(i => i.FileId == at) : -1;
        var picked = Index != Math.Max(serverIndex, 0);
        var start = Resume.Start(request.Items, Index, request.Resume, app.KeptPosition(request.Post.Id), picked);
        Index = Math.Clamp(start.Index, 0, Math.Max(0, request.Items.Count - 1));
        _resume = start.Seconds is { } seconds ? new ContinueWatching { FileId = request.Items[Index].FileId, Time = seconds } : null;
        _ui = ui;
        Mix = app.Settings.AudioMix;
        SubtitleLanguage = app.Settings.DefaultSubtitle;
        Volume = app.Settings.Volume;
        Playback = new MpvPlayback(ui, app.Settings.BufferSeconds);
        Playback.Ended += Finished;
        Playback.Failed += Failed;
        _clock.Tick += (_, _) => Tick();
        _heartbeat.Tick += (_, _) => Report();
    }

    /// <summary>The named stretches of the file being played, as far as the server knows them.</summary>
    public IReadOnlyList<Mark> Marks
    {
        get
        {
            if (Duration <= 0) return [];
            var found = new List<Mark>();
            var item = Current;
            if (item.RecapEnd is double recap and > 0) found.Add(new Mark(Mark.Kinds.Recap, item.RecapStart ?? 0, Math.Min(recap, Duration)));
            if (item.IntroEnd is double intro and > 0) found.Add(new Mark(Mark.Kinds.Intro, item.IntroStart ?? 0, Math.Min(intro, Duration)));
            if (item.CreditsStart is double credits and > 0 && credits < Duration) found.Add(new Mark(Mark.Kinds.Credits, credits, Duration));
            return found.Where(m => m.To > m.From).ToList();
        }
    }

    // MARK: Lifecycle

    public void Begin()
    {
        if (Post.IsSeries && SeriesChoices.TryGetValue(Post.Id, out var kept))
        {
            Speed = kept.Speed;
            SubtitleOffset = kept.SubtitleOffset;
        }
        Playback.SetVolume(Volume);
        _clock.Start();
        _heartbeat.Start();
        if (Items.Count == 0) Close(); else Start(Index);
    }

    /// <summary>
    /// A series keeps its speed and subtitle timing from one episode, and one evening, to the next:
    /// a subtitle that runs late on one episode usually runs late on them all.
    /// </summary>
    private sealed record Kept(double Speed, double SubtitleOffset);

    private static string SeriesFile => Path.Combine(Storage.Folder, "series-choices.json");
    private static readonly Dictionary<int, Kept> SeriesChoices = LoadSeriesChoices();

    private static Dictionary<int, Kept> LoadSeriesChoices()
    {
        try { return Storage.Read(SeriesFile) is { } text ? JsonSerializer.Deserialize<Dictionary<int, Kept>>(text) ?? [] : []; }
        catch (Exception) { return []; }
    }

    private void KeepSeriesChoices()
    {
        if (!Post.IsSeries) return;
        SeriesChoices[Post.Id] = new Kept(Speed, SubtitleOffset);
        Storage.Write(SeriesFile, JsonSerializer.Serialize(SeriesChoices));
    }

    public void Shutdown()
    {
        if (_closed) return;
        Report();
        _closed = true;
        _clock.Stop();
        _heartbeat.Stop();
        _subtitleLoad?.Cancel();
        _sleepTimer?.Cancel();
        DropPendingSkip();
        _app.Update(s => s with { Volume = Volume });
        Playback.Dispose();
    }

    public void Close() => _app.ClosePlayer();

    // MARK: Items

    /// <summary>Starts item <paramref name="i"/>, from <paramref name="position"/> if given, else from where the user left off.</summary>
    public void Start(int i, double? position = null)
    {
        if (i < 0 || i >= Items.Count) { Close(); return; }
        // Arrow presses still being added up were for the item that was playing.
        DropPendingSkip();
        Report();
        Index = i;
        _reportedTime = 0;
        _markedWatched = false;
        if (Current.PickQuality(_app.Settings.PreferAdaptive) is not { } quality)
        {
            _app.ShowToast(Loc[S.noStream]);
            Close();
            return;
        }
        var from = position ?? ResumePosition();
        // "Max": the download file itself, once 30nama has said which there are; the stream where
        // there is none, or where it would not play (Failed).
        _direct = false;
        if (_app.Settings.MaxQuality && Current.Token != Downloads.LocalToken)
        {
            if (_downloads is null) _ = StartDirectAsync(Index, quality, from);
            else if (DirectFor() is { } file) PlayDirect(file, from);
            else Load(quality, from);
        }
        else Load(quality, from);
        _resume = null;
        // A file belongs to the episode it was picked for.
        _fileTrack = null;
        SubtitleFileName = null;
        var kept = SubtitleLanguage == FileSubtitle || SubtitleLanguage.StartsWith(Found);
        SelectSubtitle(kept ? _app.Settings.DefaultSubtitle : SubtitleLanguage, remember: false);
        LookForSubtitles();
        Changed?.Invoke();
    }

    /// <summary>The download files of the title, once asked for; "Max" plays one of them.</summary>
    private DownloadOptions? _downloads;
    /// <summary>The file playing is the download file itself, not the stream.</summary>
    private bool _direct;

    private Quality? DirectFor() =>
        _downloads is { } options && Direct.Pick(options, Post.IsSeries, Current.Season, Current.EpisodeRaw) is { } file ? Direct.AsQuality(file) : null;

    private void PlayDirect(Quality file, double? from)
    {
        _direct = true;
        Load(file, from);
    }

    /// <summary>Asks 30nama for the download files, then starts item <paramref name="index"/> on one, or on the stream.</summary>
    private async Task StartDirectAsync(int index, Quality stream, double? from)
    {
        Buffering = true;
        Changed?.Invoke();
        try
        {
#if DEBUG
            if (DemoStream.IsEnabled) _downloads = DemoStream.Downloads(Post);
            else
#endif
            {
            using var patience = new CancellationTokenSource(TimeSpan.FromSeconds(6));
            var answer = await _app.Api.CallAsync("download", new Dictionary<string, string> { ["post_id"] = Post.Id.ToString(), ["free_download"] = _app.IsLoggedIn ? "0" : "1" }).WaitAsync(patience.Token);
            _downloads = answer.Success ? new DownloadOptions(answer.Result) : new DownloadOptions(Json.Null);
            }
        }
        catch (Exception) { _downloads = new DownloadOptions(Json.Null); }
        if (_closed || index != Index) return;
        if (DirectFor() is { } file) PlayDirect(file, from); else Load(stream, from);
        Changed?.Invoke();
    }

    private double? ResumePosition()
    {
        if (_resume is not { } resume || resume.FileId != Current.FileId || resume.Time <= 5) return null;
        // Left in the closing credits: start over rather than resume into the end.
        if (resume.Duration > 0 && resume.Time > resume.Duration * 0.97) return null;
        return resume.Time;
    }

    private void Load(Quality quality, double? position)
    {
        Quality = quality;
        Time = position ?? 0;
        Duration = 0;
        Buffering = true;
        Offer = null;
        Countdown = null;
        Playback.Load(quality.UrlFor(Mix).OriginalString, position);
        Playback.SetSpeed(Speed);
        Playing = true;
        Playback.Play();
    }

    /// <summary>Episodes begun on their own in a row, with nothing pressed or moved since.</summary>
    private int _unattended;

    /// <summary>A key, or the mouse: someone is watching.</summary>
    public void Touched() => _unattended = 0;

    /// <summary>
    /// Where the credits begin: 30nama's mark, or without one the last 25 seconds, so the next
    /// episode is offered and counted down to all the same (the Android app's).
    /// </summary>
    private double? CreditsAt => Current.CreditsStart is double credits and > 0 && credits < Duration ? credits
        : Duration > 120 ? Duration - 25 : null;

    private void Finished()
    {
        if (Sleep is SleepTimer.EndOfEpisode)
        {
            Sleep = new SleepTimer.Off();
            Close();
            return;
        }
        if (HasNext && _app.Settings.AutoplayNext)
        {
            // After three episodes in a row with nothing pressed, nobody may be watching: stop there,
            // with no dialog asking (the Android app's).
            if (_unattended >= 3)
            {
                _app.ShowToast(Loc[Extra.stillWatchingStopped]);
                Close();
                return;
            }
            _unattended++;
            Start(Index + 1);
        }
        else Close();
    }

    private void Failed()
    {
        if (_closed) return;
        // The download file would not play: the stream takes over at the same moment.
        if (_direct && Current.PickQuality(_app.Settings.PreferAdaptive) is { } stream)
        {
            _direct = false;
            Load(stream, Time > 1 ? Time : null);
            Changed?.Invoke();
            return;
        }
        _app.ShowToast(Loc[S.playError]);
        Close();
    }

    // MARK: Transport

    public void Toggle()
    {
        Playing = !Playing;
        if (Playing) Playback.Play(); else Playback.Pause();
        Changed?.Invoke();
    }

    public void Seek(double seconds)
    {
        // A place chosen on the timeline wins over arrow presses still being added up.
        DropPendingSkip();
        Place(seconds);
    }

    private void Place(double seconds)
    {
        var target = Math.Max(0, Duration > 0 ? Math.Min(seconds, Duration - 1) : seconds);
        Time = target;
        Playback.Seek(target);
        Changed?.Invoke();
    }

    /// <summary>
    /// Presses of the arrow keys in quick succession, added up: the picture jumps once, to where
    /// they lead, instead of stalling at every step on the way.
    /// </summary>
    public double PendingSkip { get; private set; }
    private double? _skipTarget;
    private CancellationTokenSource? _skipWait;

    private void DropPendingSkip()
    {
        _skipWait?.Cancel();
        _skipWait = null;
        _skipTarget = null;
        PendingSkip = 0;
    }

    public void Skip(double seconds)
    {
        var target = (_skipTarget ?? Playback.Position) + seconds;
        target = Math.Max(0, Duration > 0 ? Math.Min(target, Duration - 1) : target);
        _skipTarget = target;
        PendingSkip += seconds;
        Time = target;
        _skipWait?.Cancel();
        var wait = _skipWait = new CancellationTokenSource();
        Changed?.Invoke();
        _ = Task.Delay(450, wait.Token).ContinueWith(t =>
        {
            if (t.IsCanceled) return;
            _ui.TryEnqueue(() =>
            {
                if (wait.IsCancellationRequested || _skipTarget is not { } at) return;
                _skipTarget = null;
                PendingSkip = 0;
                Place(at);
            });
        }, TaskScheduler.Default);
    }

    public void SetSpeed(double value)
    {
        Speed = value;
        Playback.SetSpeed(value);
        KeepSeriesChoices();
        Changed?.Invoke();
    }

    /// <summary>Counted in hundredths, so that a step lands on exactly the speed the menu lists.</summary>
    public void StepSpeed(int direction)
    {
        var hundredths = (int)Math.Round(Speed * 100) + direction * (int)Math.Round(SpeedStep * 100);
        SetSpeed(Math.Clamp(hundredths, 50, 200) / 100.0);
    }

    public void SetSleep(SleepTimer timer)
    {
        Sleep = timer;
        _sleepTimer?.Cancel();
        Changed?.Invoke();
        if (timer is not SleepTimer.Minutes { Count: var minutes }) return;
        var wait = _sleepTimer = new CancellationTokenSource();
        _ = Task.Delay(TimeSpan.FromMinutes(minutes), wait.Token).ContinueWith(t =>
        {
            if (t.IsCanceled) return;
            _ui.TryEnqueue(() =>
            {
                Sleep = new SleepTimer.Off();
                if (Playing) Toggle();
            });
        }, TaskScheduler.Default);
    }

    public void SetVolume(double value)
    {
        Volume = Math.Clamp(value, 0, 1);
        Playback.SetVolume(Volume);
        if (Volume > 0 && Muted) ToggleMute();
        Changed?.Invoke();
    }

    public void ToggleMute()
    {
        Muted = !Muted;
        Playback.SetMuted(Muted);
        Changed?.Invoke();
    }

    public void SelectQuality(Quality option)
    {
        if (option == Quality) return;
        _direct = false;
        Load(option, CurrentPosition);
        Changed?.Invoke();
    }

    public void SelectMix(AudioMix option)
    {
        if (option == Mix || Quality is not { } quality) return;
        Mix = option;
        _app.Update(s => s with { AudioMix = option });
        Load(quality, CurrentPosition);
        Changed?.Invoke();
    }

    private double? CurrentPosition => Playback.Position > 0 ? Playback.Position : null;

    public void ToggleFullScreen() => FullScreenToggled?.Invoke();

    /// <summary>Keeps the window above the others, for watching while working in something else.</summary>
    public void TogglePinned()
    {
        Pinned = !Pinned;
        PinnedChanged?.Invoke(Pinned);
        Changed?.Invoke();
    }

    // MARK: Offers

    public void AcceptOffer()
    {
        switch (Offer)
        {
            case Offers.Intro or Offers.Recap when Current.SkipTargetAt(Playback.Position) is { } skip:
                Seek(skip.To);
                break;
            case Offers.Next:
                Start(Index + 1);
                break;
        }
    }

    private void Tick()
    {
        if (_closed) return;
        var seconds = Playback.Position;
        if (!double.IsFinite(seconds)) return;
        var changed = false;
        // While presses are being added up, the clock shows where they lead.
        if (_skipTarget is null && Math.Abs(Time - seconds) > 0.01) { Time = seconds; changed = true; }
        var length = Playback.Duration;
        if (length > 0 && length != Duration) { Duration = length; changed = true; }
        var waiting = Playback.Waiting;
        if (waiting != Buffering) { Buffering = waiting; changed = true; }
        if (!Started)
        {
            if (_lastSeen is { } last && !waiting && seconds > last && seconds - last < 1.5) { Started = true; changed = true; }
            _lastSeen = seconds;
        }
        var paused = Playback.Paused;
        if (Started && paused == Playing && PendingSkip == 0)
        {
            // Paused or played from outside the controls: the media keys, or mpv itself at the end.
            Playing = !paused;
            changed = true;
        }

        // Into the last tenth: whatever is left is credits, so the file counts as watched.
        if (!_markedWatched && Duration > 60 && seconds / Duration > 0.9)
        {
            _markedWatched = true;
            _app.SetWatched(Current.FileId, true);
        }

        var text = _track.TextAt(seconds - SubtitleOffset);
        if (text != Cue) { Cue = text; changed = true; }

        Offers? due = null;
        double? left = null;
        if (Current.SkipTargetAt(seconds) is { } skip)
        {
            due = skip.IsIntro ? Offers.Intro : Offers.Recap;
            // Once per stretch: going back into it on purpose is not undone.
            var stretch = $"{Current.Id}@{skip.To}";
            if (_app.Settings.AutoSkip && Started && _skippedOn != stretch)
            {
                _skippedOn = stretch;
                Seek(skip.To);
                due = null;
            }
        }
        else if (CreditsAt is double credits && seconds >= credits && HasNext)
        {
            due = Offers.Next;
            if (_app.Settings.AutoplayNext && Sleep is not SleepTimer.EndOfEpisode && _unattended < 3 && Duration > credits)
                // In steps, so the pill is not redrawn four times a second.
                left = Math.Round(Math.Clamp((seconds - credits) / (Duration - credits), 0, 1) * 50) / 50;
        }
        if (due != Offer) { Offer = due; changed = true; }
        if (left != Countdown) { Countdown = left; changed = true; }
        if (changed) Changed?.Invoke();
    }

    // MARK: Progress

    /// <summary>
    /// Tells the server where playback is, which drives "continue watching" on every device.
    /// The position goes up in steps of thirty seconds, which is how the 30nama website reports
    /// it. The server accepts any other position just the same and then does not record it.
    /// </summary>
    private void Report()
    {
        if (_closed || Index >= Items.Count || Duration <= 0) return;
        // A file on this computer: 30nama is told nothing; where it was left is kept here instead.
        if (Current.Token == Downloads.LocalToken)
        {
            if (Playback.Position > 5) Downloads.KeepPosition(Current.FileId, Playback.Position);
            return;
        }
        var seconds = Playback.Position;
        if (!double.IsFinite(seconds)) return;
        // Kept here to the second, beside what 30nama is told (see Resume).
        if (seconds > 5) _app.KeepPosition(Post.Id, Current.FileId, seconds);
        if (!_app.IsLoggedIn) return;
        var position = (int)seconds / 30 * 30;
        if (position <= 0 || position == _reportedTime) return;
        _reportedTime = position;
        var item = Current;
        var parameters = new Dictionary<string, string>
        {
            ["post_id"] = item.PostId ?? Post.Id.ToString(),
            ["file_id"] = item.FileId,
            ["user_id"] = _app.User?.Id ?? "",
            ["time"] = position.ToString(),
            ["duration"] = ((int)Duration).ToString(),
            ["token"] = item.Token,
        };
        var api = _app.Api;
        _ = Task.Run(async () =>
        {
            try { await api.CallAsync("observer", parameters); } catch (Exception) { }
        });
    }

    // MARK: Subtitles

    /// <summary>Languages the stream offers, in the order they are listed.</summary>
    public IReadOnlyList<string> SubtitleLanguages => new[] { "fa", "en" }.Where(Current.Subtitles.ContainsKey).ToList();

    /// <summary>
    /// 30nama has no Persian subtitle for this item: look on SubSource, and show the best one at
    /// once if Persian is what the user watches with.
    /// </summary>
    private void LookForSubtitles()
    {
        _finding?.Cancel();
        FoundSubtitles = [];
        FindingSubtitles = false;
        if (Current.Subtitles.ContainsKey("fa") || Current.Token == Downloads.LocalToken) return;
        var finding = _finding = new CancellationTokenSource();
        var item = Index;
        var query = new SubSource.Query(Post.TitleEnglish ?? Post.Title(Lang.En), Post.Year, Post.ImdbId, Post.IsSeries, Post.IsSeries ? Current.Season : null);
        var release = string.Join(' ', new[] { Current.Title(Lang.En), Quality?.Label }.OfType<string>());
        int? episode = Post.IsSeries ? Current.EpisodeNumber : null;
        FindingSubtitles = true;
        _ = FindAsync();

        async Task FindAsync()
        {
            var list = await _app.Subtitles.CandidatesAsync(query, release, episode);
            if (finding.IsCancellationRequested || _closed || Index != item) return;
            FindingSubtitles = false;
            FoundSubtitles = list.Take(8).ToList();
            if (list.Count > 0 && SubtitleLanguage == "fa") SelectSubtitle(Found + list[0].Id, remember: false);
            Changed?.Invoke();
        }
    }

    public void SelectSubtitle(string language, bool remember)
    {
        SubtitleLanguage = language;
        // A subtitle found for this title is remembered as Persian, which is what it is.
        if (remember && language != FileSubtitle) _app.Update(s => s with { DefaultSubtitle = language.StartsWith(Found) ? "fa" : language });
        _subtitleLoad?.Cancel();
        _track = new SubtitleTrack();
        Cue = "";
        Changed?.Invoke();
        if (language.StartsWith(Found))
        {
            var id = language[Found.Length..];
            if (FoundSubtitles.FirstOrDefault(c => c.Id == id) is not { } candidate) return;
            var found = _subtitleLoad = new CancellationTokenSource();
            int? season = Post.IsSeries ? Current.Season : null, episode = Post.IsSeries ? Current.EpisodeNumber : null;
            _ = Task.Run(async () =>
            {
                var text = await _app.Subtitles.TextAsync(candidate, season, episode);
                var parsed = text is null ? null : SubtitleTrack.FromVtt(text);
                _ui.TryEnqueue(() =>
                {
                    if (found.IsCancellationRequested || SubtitleLanguage != language) return;
                    if (parsed is null) _app.ShowToast(Loc[S.subFailed]);
                    else _track = parsed;
                });
            });
            return;
        }
        if (language == FileSubtitle)
        {
            _track = _fileTrack ?? new SubtitleTrack();
            return;
        }
        if (language == "off" || !Current.Subtitles.TryGetValue(language, out var url)) return;
        if (_subtitleCache.TryGetValue(url, out var cached))
        {
            _track = cached;
            return;
        }
        var load = _subtitleLoad = new CancellationTokenSource();
        _ = Task.Run(async () =>
        {
            SubtitleTrack? parsed = null;
            try
            {
                var data = url.IsFile ? await File.ReadAllBytesAsync(url.LocalPath, load.Token) : await Subtitles.FetchAsync(url, load.Token);
                parsed = SubtitleTrack.FromVtt(SubtitleSite.Text(data) ?? System.Text.Encoding.UTF8.GetString(data));
            }
            catch (Exception) { }
            _ui.TryEnqueue(() =>
            {
                if (load.IsCancellationRequested) return;
                if (parsed is null) { _app.ShowToast(Loc[S.subFailed]); return; }
                _subtitleCache[url] = parsed;
                if (SubtitleLanguage == language && Current.Subtitles.GetValueOrDefault(language) == url) _track = parsed;
            });
        });
    }

    public void NudgeSubtitles(double seconds)
    {
        SubtitleOffset = seconds == 0 ? 0 : Math.Round((SubtitleOffset + seconds) * 4) / 4;
        KeepSeriesChoices();
        Changed?.Invoke();
    }

    /// <summary>SubRip or WebVTT. Persian files are often not UTF-8, so the Arabic Windows code page is tried next.</summary>
    public void LoadSubtitleFile(string path)
    {
        if (!new[] { ".srt", ".vtt" }.Contains(Path.GetExtension(path).ToLowerInvariant())) return;
        byte[] data;
        try { data = File.ReadAllBytes(path); }
        catch (Exception) { return; }
        if (SubtitleSite.Text(data) is not { } text || SubtitleTrack.FromVtt(text) is not { Cues.Count: > 0 } parsed)
        {
            _app.ShowToast(Loc[S.subFailed]);
            return;
        }
        _fileTrack = parsed;
        SubtitleFileName = Path.GetFileNameWithoutExtension(path);
        SelectSubtitle(FileSubtitle, remember: false);
    }
}

/// <summary>Fetches subtitle files over the same network path as the API.</summary>
internal static class Subtitles
{
    private static readonly Transport Network = ApiClient.HttpTransport();

    public static async Task<byte[]> FetchAsync(Uri url, CancellationToken cancel)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.TryAddWithoutValidation("User-Agent", AppModel.Shared.Api.Config.UserAgent);
        using var response = await Network(request, cancel);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadAsByteArrayAsync(cancel);
    }
}
