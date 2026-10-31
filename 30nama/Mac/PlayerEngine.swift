import SwiftUI
import AVKit
import UniformTypeIdentifiers

/// What drives playback on the Mac: the stream's renditions and audio mixes, side-loaded
/// subtitles, the skip and next-episode offers, and telling 30nama how far the user has got.
///
/// The controls are the app's own (see `PlayerView`), so everything they show lives here as state.
@MainActor
@Observable
final class PlayerEngine {
    enum Offer: Equatable { case intro, recap, next }

    /// The subtitle choice that is neither a language nor "off": a file the user picked.
    static let fileSubtitle = "file"
    /// The token of an item that is a file on this Mac, not a stream. 30nama is told nothing
    /// about it; where it was left is remembered here instead.
    static let localToken = "local"
    static let speeds: [Float] = [0.5, 0.75, 0.9, 1, 1.05, 1.1, 1.25, 1.5, 1.75, 2]
    /// What [ and ] change the speed by: small enough to catch up with a slow scene unnoticed.
    static let speedStep: Float = 0.05

    static func name(of speed: Float) -> String {
        speed == 1 ? "1×" : "\(speed.formatted(.number.precision(.fractionLength(0...2))))×"
    }

    let post: Post
    let items: [StreamItem]
    /// What is decoding the current file. It changes when the queue moves from a file the
    /// system can read to one it cannot, or back.
    private(set) var playback: Playback = SystemPlayback()
    @ObservationIgnored private let app: AppModel

    private(set) var index: Int
    private(set) var quality: Quality?
    /// "Max" for the item playing: its download file as a quality, once 30nama has listed the files.
    private(set) var direct: Quality?
    /// The file playing is that download file itself, not the stream.
    private(set) var playingDirect = false
    /// The title's download files, asked for once when "Max" is chosen in Settings.
    @ObservationIgnored private var downloads: DownloadOptions?
    private(set) var mix: AudioMix
    private(set) var time = 0.0
    private(set) var duration = 0.0
    /// What the user asked for; the picture may still be waiting on the network.
    private(set) var playing = true
    private(set) var buffering = true
    /// Width over height of the picture, for the light around it.
    private(set) var aspect = 0.0
    /// The picture is moving: the clock has gone forward between two looks at it. Until then
    /// the player shows the title's own artwork instead of a black window.
    private(set) var started = false
    @ObservationIgnored private var lastSeen: Double?
    private(set) var speed: Float = 1
    private(set) var volume: Float = 1
    private(set) var muted = false
    private(set) var cue = ""
    private(set) var offer: Offer?
    /// A title resumed part-way offers to start over until ten seconds after where it resumed.
    private(set) var startOverBefore: Double?
    /// Next episode only: how far the credits have run, 0...1, when the next one starts by itself.
    private(set) var countdown: Double?
    private(set) var subtitleLanguage: String
    /// Seconds the subtitles are shown later (or, negative, earlier) than their file says.
    private(set) var subtitleOffset = 0.0
    private(set) var subtitleFileName: String?
    private(set) var pictureInPicture = false
    private(set) var pinned = false
    /// When playback stops by itself, for falling asleep to.
    private(set) var sleep: SleepTimer = .off
    @ObservationIgnored private var sleepTask: Task<Void, Never>?
    /// The stretch last jumped over by itself, so that one the user goes back into is left alone.
    @ObservationIgnored private var skippedOn: String?

    enum SleepTimer: Hashable {
        case off, endOfEpisode, minutes(Int)
        static let choices: [SleepTimer] = [.off, .endOfEpisode, .minutes(15), .minutes(30), .minutes(45), .minutes(60)]
    }
    /// The sound and subtitle tracks inside the file, when mpv is playing it. A stream has none to choose from here.
    private(set) var tracks: [MPVPlayback.Track] = []
    private(set) var audioTrack: Int?

    /// The subtitle choice for a track inside the file: this, followed by the track's id.
    static let inFile = "infile:"
    /// The subtitle choice for one found on SubSource: this, followed by the candidate's id.
    static let found = "found:"
    /// The English subtitle put into Persian (or Arabic) by Gemini, with the viewer's own key.
    static let machine = "machine"
    static let geminiKeyName = "geminiKey"

    /// Persian subtitles found on SubSource for an item 30nama gives none for, best first.
    private(set) var foundSubtitles: [SubSource.Candidate] = []
    private(set) var findingSubtitles = false
    @ObservationIgnored private var findTask: Task<Void, Never>?

    @ObservationIgnored private var resume: ContinueWatching?
    @ObservationIgnored private var track = SubtitleTrack()
    @ObservationIgnored private var fileTrack: SubtitleTrack?
    @ObservationIgnored private var subtitleCache: [URL: SubtitleTrack] = [:]
    @ObservationIgnored private var subtitleTask: Task<Void, Never>?
    @ObservationIgnored private var heartbeat: Timer?
    /// The last position reported for the current item, so the same one is not sent twice.
    @ObservationIgnored private var reportedTime = 0
    @ObservationIgnored private var closed = false
    @ObservationIgnored private var markedWatched = false
    /// Pictures for the timeline, from a second reader of the stream (`SeekPreview`).
    let preview = SeekPreview()
    /// Episodes begun one after another by autoplay with nobody touching the Mac in between.
    @ObservationIgnored private var unattended = 0
    private var system: SystemPlayback? { playback as? SystemPlayback }
    @ObservationIgnored private weak var window: NSWindow?
    @ObservationIgnored private var nowPlaying: NowPlaying?

    private var loc: Loc { app.loc }
    var current: StreamItem { items[index] }

    /// A stretch of the file with a name: the recap, the opening titles, the end credits.
    struct Mark: Hashable {
        enum Kind { case recap, intro, credits }
        let kind: Kind
        let from: Double
        let to: Double
    }

    /// The named stretches of the file being played, as far as the server knows them.
    var marks: [Mark] {
        guard duration > 0 else { return [] }
        var found: [Mark] = []
        let item = current
        if let end = item.recapEnd, end > 0 { found.append(Mark(kind: .recap, from: item.recapStart ?? 0, to: min(end, duration))) }
        if let end = item.introEnd, end > 0 { found.append(Mark(kind: .intro, from: item.introStart ?? 0, to: min(end, duration))) }
        if let start = item.creditsStart, start > 0, start < duration { found.append(Mark(kind: .credits, from: start, to: duration)) }
        return found.filter { $0.to > $0.from }
    }
    var hasNext: Bool { index + 1 < items.count }
    var hasPrevious: Bool { index > 0 }

    /// The previous key, as players have it: ten seconds into an episode it goes back to its
    /// start; before that, to the episode before. A film starts over.
    /// Someone touched the Mac: autoplay's count of unattended episodes starts again.
    func noticed() { unattended = 0 }

    /// Where the credits start: the server's mark, or, where it has none, the last 25 seconds, so
    /// the next episode is still counted down to.
    private var creditsStart: Double? {
        if let credits = current.creditsStart, credits > 0 { return credits }
        return duration > 120 ? duration - 25 : nil
    }

    func startOver() {
        seek(to: 0)
    }

    /// The clock time the item ends at, at the speed it plays.
    var endsAt: Date? {
        guard duration > 0, time < duration else { return nil }
        return Date().addingTimeInterval((duration - time) / Double(max(speed, 0.1)))
    }

    func previous() {
        if playback.position > 10 || !hasPrevious { seek(to: 0) } else { start(index - 1) }
    }

    init(request: PlaybackRequest, app: AppModel) {
        post = request.post
        items = request.items
        index = min(max(0, request.index), max(0, request.items.count - 1))
        resume = request.resume
        self.app = app
        mix = app.settings.audioMix
        subtitleLanguage = app.settings.defaultSubtitle
    }

    // MARK: Lifecycle

    func begin() {
        wire()
        nowPlaying = NowPlaying(.init(
            play: { [weak self] in if self?.playing == false { self?.toggle() } },
            pause: { [weak self] in if self?.playing == true { self?.toggle() } },
            toggle: { [weak self] in self?.toggle() },
            skip: { [weak self] in self?.skip($0) },
            seek: { [weak self] in self?.seek(to: $0) },
            next: { [weak self] in if let self, self.hasNext { self.start(self.index + 1) } },
            previous: { [weak self] in self?.previous() }
        ))
        heartbeat = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.report()
                self?.keepPoint()
            }
        }
        #if DEBUG
        if DebugLaunch.mute { toggleMute() }
        // `NAMA_PREVIEW=<seconds>`: asks the timeline's reader for that point and says what came.
        if let point = ProcessInfo.processInfo.environment["NAMA_PREVIEW"].flatMap(Double.init) {
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(4))
                _ = self?.preview.ask(point)
                try? await Task.sleep(for: .seconds(10))
                let frame = self?.preview.frames[SeekPreview.point(of: point)]
                print("preview \(point): \(frame.map { "\($0.width)x\($0.height)" } ?? "none")")
            }
        }
        #endif
        if let kept = UserDefaults.standard.dictionary(forKey: seriesKey) {
            if let value = kept["speed"] as? Double { speed = Float(value) }
            if let value = kept["subtitleOffset"] as? Double { subtitleOffset = value }
        }
        if items.isEmpty { close() } else { start(index) }
    }

    /// A title keeps its speed and subtitle timing from one episode, and one evening, to the next:
    /// a subtitle that runs late on one episode usually runs late on them all, and a film left half
    /// watched comes back to the same subtitle. The key keeps its old name, so what series kept stays.
    private var seriesKey: String { "series.\(post.id)" }

    /// A download file is cut differently from the stream, so it keeps its own subtitle timing beside the title's.
    private var fileKey: String? {
        guard playingDirect, let quality else { return nil }
        return "\(seriesKey)|\(quality.url.lastPathComponent)"
    }

    private func keepSeriesChoices() {
        let kept = ["speed": Double(speed), "subtitleOffset": subtitleOffset]
        UserDefaults.standard.set(kept, forKey: seriesKey)
        if let fileKey { UserDefaults.standard.set(kept, forKey: fileKey) }
    }

    /// This device's own record of where the title was left (`Resume`), to the second.
    private func keepPoint() {
        guard items.indices.contains(index), current.token != PlayerEngine.localToken, playback.position > 5 else { return }
        // Watched into the credits: what comes next is kept, not the last second of this one, which
        // would open, end and close at once when the stream does not say how long the file is.
        if duration > 0, playback.position >= duration * 0.97 {
            if items.indices.contains(index + 1) {
                app.keepPoint(post: post.id, fileID: items[index + 1].fileID, seconds: 1)
            } else {
                app.forgetPoint(post: post.id)
            }
            return
        }
        app.keepPoint(post: post.id, fileID: current.fileID, seconds: playback.position)
    }

    /// The window the player is in, for telling its keys from those of a panel or another window.
    var hostWindow: NSWindow? { window }

    func shutdown() {
        guard !closed else { return }
        closed = true
        preview.close()
        report()
        keepPoint()
        if items.indices.contains(index), current.token == PlayerEngine.localToken {
            UserDefaults.standard.set(time, forKey: "position." + current.fileID)
        }
        heartbeat?.invalidate()
        subtitleTask?.cancel()
        sleepTask?.cancel()
        dropPendingSkip()
        findTask?.cancel()
        if pinned { window?.level = .normal }
        playback.stop()
        nowPlaying?.end()
        nowPlaying = nil
    }

    /// Brings the system's media controls up to date with what the player is doing.
    private func announce() {
        guard !closed, items.indices.contains(index) else { return }
        nowPlaying?.update(
            title: post.title(loc.lang),
            subtitle: post.isSeries ? loc.episodeTag(season: current.season, episode: current.episodeRaw ?? "\(current.episodeNumber)") : nil,
            poster: post.poster.jpeg ?? post.poster.url(pixelWidth: 600),
            time: time,
            duration: duration,
            playing: playing,
            speed: speed,
            hasNext: hasNext
        )
    }

    /// Listens to whatever is decoding now.
    private func wire() {
        playback.onTick = { [weak self] seconds in self?.tick(seconds) }
        playback.onEnd = { [weak self] in self?.finished() }
        playback.onFail = { [weak self] in self?.failed() }
        (playback as? MPVPlayback)?.onTracks = { [weak self] found in
            self?.tracks = found
            self?.audioTrack = found.first { !$0.subtitle }?.id
        }
        // The stream opened with a mix this Mac cannot decode: fall back to the stereo one.
        system?.onSilent = { [weak self] in
            guard let self, self.mix != .stereo, let quality = self.quality, quality.stereo != nil else { return }
            self.selectMix(.stereo)
        }
    }

    /// Files the system's decoder reads. Anything else on disk goes to mpv.
    private static let systemTypes: Set<String> = ["mp4", "m4v", "mov", "m3u8", "mp3", "m4a"]

    /// Makes sure the right decoder is in place for `url`, swapping it if the last file needed the other.
    private func prepare(for url: URL) {
        // A download file played straight from 30nama is MKV or MP4 with any sound (DTS, TrueHD): mpv's.
        let needsOther = playingDirect || (url.isFileURL && !PlayerEngine.systemTypes.contains(url.pathExtension.lowercased()))
        guard needsOther != (playback is MPVPlayback) else { return }
        playback.stop()
        playback = needsOther ? MPVPlayback() : SystemPlayback()
        wire()
        playback.setVolume(volume)
        playback.setMuted(muted)
    }

    func close() {
        app.closePlayer()
    }

    /// The window the picture sits in, for full screen and for keeping it above the others.
    func attach(window: NSWindow?) {
        if let window { self.window = window }
    }

    // MARK: Items

    /// Starts item `i`, from `position` if given, else from where the user left off.
    func start(_ i: Int, at position: Double? = nil) {
        guard items.indices.contains(i) else { return close() }
        // Arrow presses still being added up were for the item that was playing.
        dropPendingSkip()
        report()
        index = i
        reportedTime = 0
        markedWatched = false
        guard let quality = current.quality(preferAdaptive: app.settings.preferAdaptive, screenLines: PlayerEngine.screenLines, cap: app.settings.qualityCap) else {
            app.showToast(loc(.noStream))
            return close()
        }
        // Settings chose another player for online titles; a download always plays here.
        if current.token != PlayerEngine.localToken, let other = PlayerEngine.chosenOther {
            let title = post.isSeries ? "\(post.title(loc.lang)) · \(loc.episodeTag(season: current.season, episode: current.episodeRaw ?? ""))" : post.title(loc.lang)
            hand(quality.url(for: mix), to: other, at: position ?? resumePosition(), title: title)
            app.showToast("\(loc(.playingIn)) \(other.name)")
            return close()
        }
        // From the lowest quality, unless Data saver is on.
        let lowest = current.qualities.filter { !$0.isAuto && $0.height > 0 }.min { $0.height < $1.height } ?? quality
        preview.open(StoredCaches.dataSaver ? nil : lowest.url)
        let from = position ?? resumePosition()
        startOverBefore = position == nil ? from.map { $0 + 10 } : nil
        // "Max": the download file itself, once 30nama has said which there are; the stream where
        // there is none, or where it would not play (`failed`).
        playingDirect = false
        direct = nil
        if app.settings.maxQuality, current.token != PlayerEngine.localToken {
            if downloads == nil { askForDownloads(then: quality, at: from) } else { playDirect(or: quality, at: from) }
        } else {
            load(quality, at: from)
        }
        resume = nil
        // A file belongs to the episode it was picked for.
        fileTrack = nil
        subtitleFileName = nil
        // A machine translation goes on to the next episode only when that one has no Persian of
        // its own and an English one to start from.
        let kept = subtitleLanguage == PlayerEngine.fileSubtitle || subtitleLanguage.hasPrefix(PlayerEngine.inFile) || subtitleLanguage.hasPrefix(PlayerEngine.found)
            || (subtitleLanguage == PlayerEngine.machine && (current.subtitles["fa"] != nil || current.subtitles["en"] == nil))
        selectSubtitle(kept ? app.settings.defaultSubtitle : subtitleLanguage, remember: false)
        lookForSubtitles()
    }

    /// Asks 30nama for the title's download files, then starts the item on one, or on the stream.
    /// Six seconds at most: the stream is there to fall back on.
    private func askForDownloads(then stream: Quality, at position: Double?) {
        buffering = true
        let item = index
        #if DEBUG
        if DemoStream.isEnabled {
            downloads = DemoStream.downloads(for: post)
            return playDirect(or: stream, at: position)
        }
        #endif
        let api = app.api, parameters = ["post_id": String(post.id), "free_download": app.isLoggedIn ? "0" : "1"]
        Task { [weak self] in
            let ask = Task { try? await api.call("download", parameters) }
            let patience = Task {
                try? await Task.sleep(for: .seconds(6))
                ask.cancel()
            }
            let answer = await ask.value
            patience.cancel()
            guard let self, !self.closed, self.index == item else { return }
            self.downloads = answer.flatMap { $0.success ? DownloadOptions($0.result) : nil } ?? DownloadOptions(.null)
            self.playDirect(or: stream, at: position)
        }
    }

    private func playDirect(or stream: Quality, at position: Double?) {
        direct = downloads.flatMap { Direct.pick($0, isSeries: post.isSeries, season: current.season, episode: current.episodeRaw) }.map(Direct.asQuality)
        guard let direct else { return load(stream, at: position) }
        playingDirect = true
        load(direct, at: position)
        // This file's own subtitle timing, where it has one.
        if let fileKey, let kept = UserDefaults.standard.dictionary(forKey: fileKey), let offset = kept["subtitleOffset"] as? Double {
            subtitleOffset = offset
        }
        // A file 30nama's server does not open (out of reach, or slow to answer) gives way to the stream.
        let item = index
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(20))
            guard let self, !self.closed, self.index == item, self.playingDirect, self.playback.duration == 0 else { return }
            self.fallBackToStream()
        }
    }

    /// The download file would not play: the stream takes over at the same moment, with its own subtitle timing.
    @discardableResult
    private func fallBackToStream() -> Bool {
        guard playingDirect, let stream = current.quality(preferAdaptive: app.settings.preferAdaptive, screenLines: PlayerEngine.screenLines, cap: app.settings.qualityCap) else { return false }
        let at = playback.position > 1 ? playback.position : time > 1 ? time : nil
        playingDirect = false
        if let kept = UserDefaults.standard.dictionary(forKey: seriesKey), let offset = kept["subtitleOffset"] as? Double { subtitleOffset = offset }
        load(stream, at: at)
        return true
    }

    private func resumePosition() -> Double? {
        guard let resume, resume.fileID == current.fileID, resume.time > 5 else { return nil }
        // Left in the closing credits: start over rather than resume into the end.
        if resume.duration > 0, resume.time > resume.duration * 0.97 { return nil }
        return resume.time
    }

    private func load(_ quality: Quality, at position: Double?) {
        self.quality = quality
        let url = quality.url(for: mix)
        prepare(for: url)
        tracks = []
        audioTrack = nil
        time = position ?? 0
        duration = 0
        buffering = true
        offer = nil
        countdown = nil
        guard url.isFileURL else { return begin(url, at: position) }
        // A downloaded file is read once first, away from the main thread. The first time, macOS
        // asks the user whether the app may use the folder and holds the read until they answer;
        // mpv, asked first, is left waiting for good. Asked here, the answer is in before it starts.
        let item = index
        Task { [weak self] in
            let readable = await Task.detached { () -> Bool in
                guard let handle = try? FileHandle(forReadingFrom: url) else { return false }
                try? handle.close()
                return true
            }.value
            guard let self, !self.closed, self.index == item else { return }
            guard readable else {
                self.app.showToast(self.loc(.folderDenied))
                return self.close()
            }
            self.begin(url, at: position)
            // A file on this Mac has no network to wait on: one that has not begun by now will not.
            try? await Task.sleep(for: .seconds(15))
            if !self.closed, self.index == item, !self.started, self.duration == 0 { self.failed() }
        }
    }

    private func begin(_ url: URL, at position: Double?) {
        playback.load(url, at: position)
        playback.setSpeed(speed, playing: false)
        playing = true
        playback.play()
        announce()
    }

    private func finished() {
        if sleep == .endOfEpisode {
            sleep = .off
            return close()
        }
        if hasNext, app.settings.autoplayNext {
            // Three in a row with no key, click or mouse move: someone has likely fallen asleep.
            // It stops, saying so, with no dialog to answer (from the Android and Windows apps).
            if unattended >= 3 {
                app.showToast(loc(.stillWatchingStopped))
                return close()
            }
            unattended += 1
            start(index + 1)
        } else {
            close()
        }
    }

    private func failed() {
        guard !closed else { return }
        if fallBackToStream() { return }
        app.showToast(loc(playback.refused ? .streamRefused : .playError))
        close()
    }

    // MARK: Transport

    func toggle() {
        playing.toggle()
        if playing { playback.play() } else { playback.pause() }
        announce()
    }

    func seek(to seconds: Double) {
        // A place chosen on the timeline wins over arrow presses still being added up.
        dropPendingSkip()
        place(at: seconds)
    }

    private func place(at seconds: Double) {
        startOverBefore = nil
        let target = max(0, duration > 0 ? min(seconds, duration - 1) : seconds)
        time = target
        playback.seek(to: target)
        announce()
    }

    /// Presses of the arrow keys in quick succession, added up: the picture jumps once, to where
    /// they lead, instead of stalling at every step on the way.
    private(set) var pendingSkip = 0.0
    @ObservationIgnored private var skipTarget: Double?
    @ObservationIgnored private var skipTask: Task<Void, Never>?

    private func dropPendingSkip() {
        skipTask?.cancel()
        skipTask = nil
        skipTarget = nil
        pendingSkip = 0
    }

    func skip(_ seconds: Double) {
        var target = (skipTarget ?? playback.position) + seconds
        target = max(0, duration > 0 ? min(target, duration - 1) : target)
        skipTarget = target
        pendingSkip += seconds
        time = target
        skipTask?.cancel()
        skipTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(450))
            guard let self, !Task.isCancelled, let target = self.skipTarget else { return }
            self.skipTarget = nil
            self.pendingSkip = 0
            self.place(at: target)
        }
    }

    func setSpeed(_ value: Float) {
        speed = value
        playback.setSpeed(value, playing: playing)
        announce()
        keepSeriesChoices()
    }

    func setSleep(_ timer: SleepTimer) {
        sleep = timer
        sleepTask?.cancel()
        guard case .minutes(let minutes) = timer else { return }
        sleepTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(minutes * 60))
            guard !Task.isCancelled, let self else { return }
            self.sleep = .off
            if self.playing { self.toggle() }
        }
    }

    func stepSpeed(_ direction: Int) {
        // Counted in hundredths, so that a step lands on exactly the speed the menu lists.
        let hundredths = Int((speed * 100).rounded()) + direction * Int((PlayerEngine.speedStep * 100).rounded())
        setSpeed(Float(min(200, max(50, hundredths))) / 100)
    }

    func setVolume(_ value: Float) {
        volume = min(1, max(0, value))
        playback.setVolume(volume)
        if volume > 0, muted { toggleMute() }
    }

    func toggleMute() {
        muted.toggle()
        playback.setMuted(muted)
    }

    /// The qualities the player's menu offers: the stream's, and "Max" when its file is known.
    var qualityChoices: [Quality] {
        current.qualities + (direct.map { [$0] } ?? [])
    }

    func selectQuality(_ option: Quality) {
        guard option != quality else { return }
        let at = currentPosition
        let wasDirect = playingDirect
        playingDirect = option == direct
        // Back on the stream: its own subtitle timing, not the file's.
        if wasDirect, !playingDirect, let kept = UserDefaults.standard.dictionary(forKey: seriesKey), let offset = kept["subtitleOffset"] as? Double {
            subtitleOffset = offset
        }
        load(option, at: at)
    }

    func selectMix(_ option: AudioMix) {
        guard option != mix, let quality else { return }
        mix = option
        app.update { $0.audioMix = option }
        load(quality, at: currentPosition)
    }

    private var currentPosition: Double? {
        let seconds = playback.position
        return seconds > 0 ? seconds : nil
    }

    // MARK: Window

    func togglePictureInPicture() {
        guard let pip = system?.pip else { return }
        if pip.isPictureInPictureActive { pip.stopPictureInPicture() } else { pip.startPictureInPicture() }
    }

    var canPictureInPicture: Bool { system?.pip != nil }

    /// Keeps the window above the others, for watching while working in something else.
    func togglePinned() {
        pinned.toggle()
        window?.level = pinned ? .floating : .normal
    }

    func toggleFullScreen() {
        window?.toggleFullScreen(nil)
    }

    // MARK: Other players

    /// Players on this Mac that take a stream address and a starting point (the owner: one choice
    /// in Settings, not a menu in the player).
    static let otherPlayers: [(name: String, id: String, app: URL)] = [("IINA", "com.colliderli.iina"), ("VLC", "org.videolan.vlc")].compactMap { name, id in
        NSWorkspace.shared.urlForApplication(withBundleIdentifier: id).map { (name, id, $0) }
    }

    static let onlinePlayerKey = "onlinePlayer"

    /// The tallest screen's height in pixels: "best" is no taller, as the window may go to it.
    static var screenLines: Int {
        NSScreen.screens.map { Int($0.frame.height * $0.backingScaleFactor) }.max() ?? 0
    }

    /// The player Settings chose for online titles, while it is still on this Mac.
    static var chosenOther: (name: String, id: String, app: URL)? {
        let id = UserDefaults.standard.string(forKey: onlinePlayerKey) ?? ""
        return otherPlayers.first { $0.id == id }
    }

    /// Hands a stream to another player, from `position` and with the title's name. Started
    /// through its own command line, as only that takes a starting point; opened plainly if that fails.
    private func hand(_ url: URL, to other: (name: String, id: String, app: URL), at position: Double?, title: String) {
        let start = Int(position ?? 0)
        let launch = Process()
        if other.id == "com.colliderli.iina" {
            launch.executableURL = other.app.appendingPathComponent("Contents/MacOS/iina-cli")
            launch.arguments = [url.absoluteString, "--mpv-start=\(start)", "--mpv-force-media-title=\(title)"]
        } else {
            launch.executableURL = other.app.appendingPathComponent("Contents/MacOS/VLC")
            launch.arguments = ["--start-time=\(start)", "--meta-title=\(title)", url.absoluteString]
        }
        do {
            try launch.run()
        } catch {
            NSWorkspace.shared.open([url], withApplicationAt: other.app, configuration: NSWorkspace.OpenConfiguration())
        }
    }

    // MARK: Offers

    func acceptOffer() {
        guard let offer else { return }
        switch offer {
        case .intro, .recap:
            guard let skip = current.skipTarget(at: playback.position) else { return }
            seek(to: skip.to)
        case .next:
            start(index + 1)
        }
    }

    private func tick(_ seconds: Double) {
        guard !closed, seconds.isFinite else { return }
        if let until = startOverBefore, seconds >= until { startOverBefore = nil }
        // While presses are being added up, the clock shows where they lead.
        if skipTarget == nil { time = seconds }
        let length = playback.duration
        if length > 0, length != duration {
            duration = length
            announce()
        }
        let waiting = playback.waiting
        if waiting != buffering { buffering = waiting }
        if !started {
            if let lastSeen, !waiting, seconds > lastSeen, seconds - lastSeen < 1.5 { started = true }
            lastSeen = seconds
        }
        let shape = playback.aspect
        if shape > 0, abs(shape - aspect) > 0.001 { aspect = shape }
        let active = system?.pip?.isPictureInPictureActive ?? false
        if active != pictureInPicture { pictureInPicture = active }

        // Into the last tenth: whatever is left is credits, so the file counts as watched.
        if !markedWatched, duration > 60, seconds / duration > 0.9 {
            markedWatched = true
            app.setWatched(current.fileID, true)
        }

        let text = track.text(at: seconds - subtitleOffset)
        if text != cue { cue = text }

        var due: Offer?
        var left: Double?
        if let skip = current.skipTarget(at: seconds) {
            due = skip.isIntro ? .intro : .recap
            // Once per stretch: going back into it on purpose is not undone.
            let stretch = "\(current.id)@\(skip.to)"
            if app.settings.autoSkip, started, skippedOn != stretch {
                skippedOn = stretch
                seek(to: skip.to)
                due = nil
            }
        } else if let credits = creditsStart, seconds >= credits, hasNext {
            due = .next
            if app.settings.autoplayNext, sleep != .endOfEpisode, duration > credits {
                // In steps, so the pill is not redrawn four times a second.
                left = (min(1, max(0, (seconds - credits) / (duration - credits))) * 50).rounded() / 50
            }
        }
        if due != offer { offer = due }
        if left != countdown { countdown = left }
    }

    // MARK: Progress

    /// Tells the server where playback is, which drives "continue watching" on every device.
    ///
    /// The position goes up in steps of thirty seconds, which is how the 30nama website reports
    /// it. The server accepts any other position just the same and then does not record it.
    private func report() {
        guard app.isLoggedIn, items.indices.contains(index), duration > 0, current.token != PlayerEngine.localToken else { return }
        let seconds = playback.position
        guard seconds.isFinite else { return }
        let position = Int(seconds) / 30 * 30
        guard position > 0, position != reportedTime else { return }
        reportedTime = position
        let item = current
        let params = [
            "post_id": item.postID ?? String(post.id),
            "file_id": item.fileID,
            "user_id": app.user?.id ?? "",
            "time": String(position),
            "duration": String(Int(duration)),
            "token": item.token,
        ]
        let api = app.api
        Task { _ = try? await api.call("observer", params) }
    }

    // MARK: Subtitles

    /// Languages the stream offers, in the order they are listed.
    var subtitleLanguages: [String] { ["fa", "en"].filter { current.subtitles[$0] != nil } }

    func selectAudioTrack(_ id: Int) {
        audioTrack = id
        (playback as? MPVPlayback)?.selectAudio(id)
    }

    /// 30nama has no Persian subtitle for this item: look on SubSource, and show the best one at once
    /// if Persian is what the user watches with.
    private func lookForSubtitles() {
        findTask?.cancel()
        foundSubtitles = []
        findingSubtitles = false
        var none = current.subtitles["fa"] == nil
        #if DEBUG
        if DebugLaunch.findSubtitles { none = true }
        #endif
        guard none, current.token != PlayerEngine.localToken else { return }
        let item = index
        let query = SubSource.Query(title: post.titleEnglish ?? post.title(.en), year: post.year, imdbID: post.imdbID,
                                    isSeries: post.isSeries, season: post.isSeries ? current.season : nil)
        let release = [current.title(.en), quality?.label].compactMap { $0 }.joined(separator: " ")
        let episode = post.isSeries ? current.episodeNumber : nil
        findingSubtitles = true
        findTask = Task { [weak self] in
            let list = await SubtitleFinder.shared.candidates(for: query, release: release, episode: episode)
            guard let self, !Task.isCancelled, !self.closed, self.index == item else { return }
            self.findingSubtitles = false
            self.foundSubtitles = Array(list.prefix(8))
            if let best = list.first, self.subtitleLanguage == "fa" {
                self.selectSubtitle(PlayerEngine.found + best.id, remember: false)
            }
        }
    }

    // MARK: Machine translation

    private var geminiKey: String? {
        let key = UserDefaults.standard.string(forKey: PlayerEngine.geminiKeyName)?.trimmingCharacters(in: .whitespacesAndNewlines)
        return key?.isEmpty == false ? key : nil
    }

    var translationTarget: Lang { Translation.target(loc.lang) }

    /// Offered only where there is nothing better: no Persian subtitle on 30nama or SubSource, an
    /// English one to start from, the viewer's own Gemini key, and not from Iran, where Google is
    /// out of reach (from the Android and Windows apps).
    var canTranslate: Bool {
        geminiKey != nil && !app.inIran && current.token != PlayerEngine.localToken
            && current.subtitles["fa"] == nil && current.subtitles["en"] != nil && !findingSubtitles && foundSubtitles.isEmpty
    }

    /// The English lines at once, then batch after batch put in as it arrives: from where the
    /// viewer is, onwards, then the part before. Kept on disk, so a film is translated once.
    private func translate() {
        guard let key = geminiKey, let url = current.subtitles["en"] else { return }
        let target = translationTarget, title = post.titleEnglish ?? post.title(.en), item = index
        let gemini = Gemini(transport: APIClient.urlSessionTransport(), folder: PlayerEngine.translations)
        subtitleTask = Task { [weak self] in
            guard let self else { return }
            var cues: [Cue]
            if let cached = self.subtitleCache[url] {
                cues = cached.cues
            } else {
                guard let (data, _) = try? await URLSession.shared.data(from: url) else {
                    if !Task.isCancelled { self.app.showToast(self.loc(.subFailed)) }
                    return
                }
                let parsed = await Task.detached { SubtitleTrack(vtt: SubtitleSite.text(data) ?? String(decoding: data, as: UTF8.self)) }.value
                self.subtitleCache[url] = parsed
                cues = parsed.cues
            }
            guard !Task.isCancelled, self.subtitleLanguage == PlayerEngine.machine, self.index == item else { return }
            self.track = SubtitleTrack(cues: cues)
            let start = Translation.batch(at: self.time, in: cues)
            for n in Translation.order(count: Translation.batchCount(cues), start: start) {
                let outcome = await gemini.translate(key: key, source: url.lastPathComponent + "|" + self.current.fileID, target: target,
                                                     title: title, batch: n, cues: Translation.batch(n, of: cues))
                guard !Task.isCancelled, self.subtitleLanguage == PlayerEngine.machine, self.index == item else { return }
                switch outcome {
                case .lines(let lines):
                    cues = Translation.apply(cues, batch: n, lines: lines)
                    self.track = SubtitleTrack(cues: cues)
                case .rejected:
                    return self.app.showToast(self.loc(.keyRejected))
                case .unavailable:
                    return self.app.showToast(self.loc(.translationStopped))
                }
            }
        }
    }

    static let translations = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("30nama-translations", isDirectory: true)

    func selectSubtitle(_ language: String, remember: Bool) {
        subtitleLanguage = language
        let inFile = language.hasPrefix(PlayerEngine.inFile)
        // A subtitle found for this title is remembered as Persian, which is what it is.
        let chosen = language.hasPrefix(PlayerEngine.found) ? "fa" : language
        if remember, language != PlayerEngine.fileSubtitle, language != PlayerEngine.machine, !inFile { app.update { $0.defaultSubtitle = chosen } }
        subtitleTask?.cancel()
        track = SubtitleTrack()
        cue = ""
        // A track inside the file is drawn by mpv, in its own styling; anything else by the app.
        (playback as? MPVPlayback)?.selectSubtitle(inFile ? Int(language.dropFirst(PlayerEngine.inFile.count)) : nil)
        if inFile { return }
        if language == PlayerEngine.machine { return translate() }
        if language.hasPrefix(PlayerEngine.found) {
            let id = String(language.dropFirst(PlayerEngine.found.count))
            guard let candidate = foundSubtitles.first(where: { $0.id == id }) else { return }
            let season = post.isSeries ? current.season : nil, episode = post.isSeries ? current.episodeNumber : nil
            subtitleTask = Task { [weak self] in
                let text = await SubtitleFinder.shared.text(of: candidate, season: season, episode: episode)
                guard !Task.isCancelled, let self, self.subtitleLanguage == language else { return }
                guard let text else { return self.app.showToast(self.loc(.subFailed)) }
                self.track = SubtitleTrack(vtt: text)
            }
            return
        }
        if language == PlayerEngine.fileSubtitle {
            track = fileTrack ?? SubtitleTrack()
            return
        }
        guard language != "off", let url = current.subtitles[language] else { return }
        if let cached = subtitleCache[url] {
            track = cached
            return
        }
        subtitleTask = Task { [weak self] in
            guard let (data, _) = try? await URLSession.shared.data(from: url) else {
                if !Task.isCancelled, let self { self.app.showToast(self.loc(.subFailed)) }
                return
            }
            let parsed = await Task.detached { SubtitleTrack(vtt: SubtitleSite.text(data) ?? String(decoding: data, as: UTF8.self)) }.value
            guard !Task.isCancelled, let self else { return }
            self.subtitleCache[url] = parsed
            if self.subtitleLanguage == language, self.current.subtitles[language] == url { self.track = parsed }
        }
    }

    func nudgeSubtitles(_ seconds: Double) {
        subtitleOffset = seconds == 0 ? 0 : ((subtitleOffset + seconds) * 4).rounded() / 4
        keepSeriesChoices()
    }

    /// Asks for a subtitle file on this Mac and shows it in place of the stream's own.
    func chooseSubtitleFile() {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = ["srt", "vtt"].compactMap { UTType(filenameExtension: $0) }
        panel.allowsMultipleSelection = false
        let wasPlaying = playing
        if wasPlaying { toggle() }
        if panel.runModal() == .OK, let url = panel.url { loadSubtitleFile(url) }
        if wasPlaying { toggle() }
    }

    /// SubRip or WebVTT. Persian files are often not UTF-8, so the Arabic Windows code page is tried next.
    func loadSubtitleFile(_ url: URL) {
        guard ["srt", "vtt"].contains(url.pathExtension.lowercased()), let data = try? Data(contentsOf: url) else { return }
        guard let text = SubtitleSite.text(data) else {
            app.showToast(loc(.subFailed))
            return
        }
        let parsed = SubtitleTrack(vtt: text)
        guard !parsed.cues.isEmpty else {
            app.showToast(loc(.subFailed))
            return
        }
        fileTrack = parsed
        subtitleFileName = url.deletingPathExtension().lastPathComponent
        selectSubtitle(PlayerEngine.fileSubtitle, remember: false)
    }
}
