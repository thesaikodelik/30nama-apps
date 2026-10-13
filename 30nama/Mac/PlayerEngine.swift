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
    static let speeds: [Float] = [0.5, 0.75, 1, 1.25, 1.5, 2]

    let post: Post
    let items: [StreamItem]
    @ObservationIgnored let player = AVPlayer()
    @ObservationIgnored private let app: AppModel

    private(set) var index: Int
    private(set) var quality: Quality?
    private(set) var mix: AudioMix
    private(set) var time = 0.0
    private(set) var duration = 0.0
    /// What the user asked for; the picture may still be waiting on the network.
    private(set) var playing = true
    private(set) var buffering = true
    private(set) var speed: Float = 1
    private(set) var volume: Float = 1
    private(set) var muted = false
    private(set) var cue = ""
    private(set) var offer: Offer?
    /// Next episode only: how far the credits have run, 0...1, when the next one starts by itself.
    private(set) var countdown: Double?
    private(set) var subtitleLanguage: String
    /// Seconds the subtitles are shown later (or, negative, earlier) than their file says.
    private(set) var subtitleOffset = 0.0
    private(set) var subtitleFileName: String?
    private(set) var pictureInPicture = false
    private(set) var pinned = false

    @ObservationIgnored private var resume: ContinueWatching?
    @ObservationIgnored private var track = SubtitleTrack()
    @ObservationIgnored private var fileTrack: SubtitleTrack?
    @ObservationIgnored private var subtitleCache: [URL: SubtitleTrack] = [:]
    @ObservationIgnored private var subtitleTask: Task<Void, Never>?
    @ObservationIgnored private var timeObserver: Any?
    @ObservationIgnored private var statusObservation: NSKeyValueObservation?
    @ObservationIgnored private var endObserver: NSObjectProtocol?
    @ObservationIgnored private var heartbeat: Timer?
    /// The last position reported for the current item, so the same one is not sent twice.
    @ObservationIgnored private var reportedTime = 0
    @ObservationIgnored private var closed = false
    @ObservationIgnored private var markedWatched = false
    @ObservationIgnored private var pip: AVPictureInPictureController?
    @ObservationIgnored private weak var window: NSWindow?

    private var loc: Loc { app.loc }
    var current: StreamItem { items[index] }
    var hasNext: Bool { index + 1 < items.count }
    var hasPrevious: Bool { index > 0 }

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
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 4), queue: .main) { [weak self] time in
            MainActor.assumeIsolated { self?.tick(time.seconds) }
        }
        heartbeat = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.report() }
        }
        #if DEBUG
        if DebugLaunch.mute { toggleMute() }
        #endif
        if items.isEmpty { close() } else { start(index) }
    }

    func shutdown() {
        guard !closed else { return }
        closed = true
        report()
        if items.indices.contains(index), current.token == PlayerEngine.localToken {
            UserDefaults.standard.set(time, forKey: "position." + current.fileID)
        }
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        statusObservation = nil
        heartbeat?.invalidate()
        subtitleTask?.cancel()
        if pictureInPicture { pip?.stopPictureInPicture() }
        if pinned { window?.level = .normal }
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    func close() {
        app.closePlayer()
    }

    /// The layer the picture is drawn in, which Picture in Picture needs, and the window it sits in.
    func attach(layer: AVPlayerLayer, window: NSWindow?) {
        self.window = window
        if pip == nil, AVPictureInPictureController.isPictureInPictureSupported() {
            pip = AVPictureInPictureController(playerLayer: layer)
        }
    }

    // MARK: Items

    /// Starts item `i`, from `position` if given, else from where the user left off.
    func start(_ i: Int, at position: Double? = nil) {
        guard items.indices.contains(i) else { return close() }
        report()
        index = i
        reportedTime = 0
        markedWatched = false
        guard let quality = current.quality(preferAdaptive: app.settings.preferAdaptive) else {
            app.showToast(loc(.noStream))
            return close()
        }
        load(quality, at: position ?? resumePosition())
        resume = nil
        // A file belongs to the episode it was picked for.
        fileTrack = nil
        subtitleFileName = nil
        selectSubtitle(subtitleLanguage == PlayerEngine.fileSubtitle ? app.settings.defaultSubtitle : subtitleLanguage, remember: false)
    }

    private func resumePosition() -> Double? {
        guard let resume, resume.fileID == current.fileID, resume.time > 5 else { return nil }
        // Left in the closing credits: start over rather than resume into the end.
        if resume.duration > 0, resume.time > resume.duration * 0.97 { return nil }
        return resume.time
    }

    private func load(_ quality: Quality, at position: Double?) {
        self.quality = quality
        let item = AVPlayerItem(url: quality.url(for: mix))
        statusObservation = item.observe(\.status) { [weak self] item, _ in
            Task { @MainActor in
                if item.status == .failed { self?.failed() }
                if item.status == .readyToPlay { await self?.preferPlayableAudio(on: item) }
            }
        }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.finished() }
        }
        player.replaceCurrentItem(with: item)
        time = position ?? 0
        duration = 0
        buffering = true
        if let position, position > 0 {
            player.seek(to: CMTime(seconds: position, preferredTimescale: 600))
        }
        player.defaultRate = speed
        playing = true
        player.play()
        offer = nil
        countdown = nil
    }

    private func finished() {
        if hasNext, app.settings.autoplayNext {
            start(index + 1)
        } else {
            close()
        }
    }

    private func failed() {
        guard !closed else { return }
        app.showToast(loc(.playError))
        close()
    }

    /// The original playlist can default to a track this Mac cannot decode, and then stays silent.
    /// Pick a playable one, or fall back to the stereo mix.
    private func preferPlayableAudio(on item: AVPlayerItem) async {
        guard let group = try? await item.asset.loadMediaSelectionGroup(for: .audible),
              !group.options.isEmpty, player.currentItem === item else { return }
        if item.currentMediaSelection.selectedMediaOption(in: group)?.isPlayable == true { return }
        if let option = group.options.first(where: \.isPlayable) {
            item.select(option, in: group)
            return
        }
        guard mix != .stereo, let quality, quality.stereo != nil else { return }
        selectMix(.stereo)
    }

    // MARK: Transport

    func toggle() {
        playing.toggle()
        if playing { player.play() } else { player.pause() }
    }

    func seek(to seconds: Double) {
        let target = max(0, duration > 0 ? min(seconds, duration - 1) : seconds)
        time = target
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func skip(_ seconds: Double) {
        seek(to: player.currentTime().seconds + seconds)
    }

    func setSpeed(_ value: Float) {
        speed = value
        player.defaultRate = value
        if playing { player.rate = value }
    }

    func stepSpeed(_ direction: Int) {
        let at = PlayerEngine.speeds.firstIndex(of: speed) ?? 2
        setSpeed(PlayerEngine.speeds[min(max(0, at + direction), PlayerEngine.speeds.count - 1)])
    }

    func setVolume(_ value: Float) {
        volume = min(1, max(0, value))
        player.volume = volume
        if volume > 0, muted { toggleMute() }
    }

    func toggleMute() {
        muted.toggle()
        player.isMuted = muted
    }

    func selectQuality(_ option: Quality) {
        guard option != quality else { return }
        load(option, at: currentPosition)
    }

    func selectMix(_ option: AudioMix) {
        guard option != mix, let quality else { return }
        mix = option
        app.update { $0.audioMix = option }
        load(quality, at: currentPosition)
    }

    private var currentPosition: Double? {
        let seconds = player.currentTime().seconds
        return seconds.isFinite ? seconds : nil
    }

    // MARK: Window

    func togglePictureInPicture() {
        guard let pip else { return }
        if pip.isPictureInPictureActive { pip.stopPictureInPicture() } else { pip.startPictureInPicture() }
    }

    var canPictureInPicture: Bool { pip != nil }

    /// Keeps the window above the others, for watching while working in something else.
    func togglePinned() {
        pinned.toggle()
        window?.level = pinned ? .floating : .normal
    }

    func toggleFullScreen() {
        window?.toggleFullScreen(nil)
    }

    // MARK: Other players

    /// Players on this Mac that take a stream address, for a file whose sound the system's own
    /// decoder cannot play.
    static let otherPlayers: [(name: String, app: URL)] = [("IINA", "com.colliderli.iina"), ("VLC", "org.videolan.vlc")].compactMap { name, id in
        NSWorkspace.shared.urlForApplication(withBundleIdentifier: id).map { (name, $0) }
    }

    /// Hands the stream over and pauses here. What is watched there is not reported to 30nama.
    func open(in other: URL) {
        guard let quality else { return }
        if playing { toggle() }
        NSWorkspace.shared.open([quality.url(for: mix)], withApplicationAt: other, configuration: NSWorkspace.OpenConfiguration())
    }

    // MARK: Offers

    func acceptOffer() {
        guard let offer else { return }
        switch offer {
        case .intro, .recap:
            guard let skip = current.skipTarget(at: player.currentTime().seconds) else { return }
            seek(to: skip.to)
        case .next:
            start(index + 1)
        }
    }

    private func tick(_ seconds: Double) {
        guard !closed, seconds.isFinite else { return }
        time = seconds
        if let length = player.currentItem?.duration.seconds, length.isFinite, length > 0, length != duration { duration = length }
        let waiting = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
        if waiting != buffering { buffering = waiting }
        let active = pip?.isPictureInPictureActive ?? false
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
        } else if let credits = current.creditsStart, credits > 0, seconds >= credits, hasNext {
            due = .next
            if app.settings.autoplayNext, duration > credits {
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
        guard app.isLoggedIn, items.indices.contains(index), player.currentItem != nil, current.token != PlayerEngine.localToken else { return }
        let seconds = player.currentTime().seconds
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

    func selectSubtitle(_ language: String, remember: Bool) {
        subtitleLanguage = language
        if remember, language != PlayerEngine.fileSubtitle { app.update { $0.defaultSubtitle = language } }
        subtitleTask?.cancel()
        track = SubtitleTrack()
        cue = ""
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
