import SwiftUI
import AVKit

/// Full-screen playback in the system player.
///
/// `AVPlayerViewController` brings the transport bar, Siri Remote scrubbing, audio-track selection
/// and the info panel. On top of that this adds what 30nama's streams need: side-loaded WebVTT
/// subtitles, skip-intro and next-episode prompts, a quality menu and progress reporting.
struct PlayerScreen: UIViewControllerRepresentable {
    let request: PlaybackRequest

    @Environment(AppModel.self) private var app

    func makeCoordinator() -> PlayerEngine {
        PlayerEngine(request: request, app: app)
    }

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        context.coordinator.makeController()
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {}

    static func dismantleUIViewController(_ controller: AVPlayerViewController, coordinator: PlayerEngine) {
        coordinator.shutdown()
    }
}

@MainActor
final class PlayerEngine: NSObject, AVPlayerViewControllerDelegate, UIGestureRecognizerDelegate {
    private let post: Post
    private let items: [StreamItem]
    private let app: AppModel
    private let player = AVPlayer()

    private var index: Int
    private var resume: ContinueWatching?
    private var quality: Quality?
    /// Which of the server's mixes is open. Stereo is what the TV speakers can play.
    private var mix: AudioMix
    /// True once this item's audio track has been checked, so the time observer stops asking.
    private var audioSettled = false
    private weak var controller: AVPlayerViewController?

    private var timeObserver: Any?
    private var statusObservation: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?
    private var heartbeat: Timer?
    /// The last position reported for the current item, so the same one is not sent twice.
    private var reportedTime = 0
    private var closed = false

    private var subtitleLanguage: String
    /// Seconds the subtitles are shown later (or, negative, earlier) than their file says.
    private var subtitleOffset = 0.0
    /// Persian subtitles found on SubSource for an item 30nama gives none for, best first.
    private var foundSubtitles: [SubSource.Candidate] = []
    private var findTask: Task<Void, Never>?
    private static let found = "found:"
    private var track = SubtitleTrack()
    private var subtitleCache: [URL: SubtitleTrack] = [:]
    private var subtitleTask: Task<Void, Never>?
    private let subtitleLabel = UILabel()
    /// Plate behind the subtitle text; clear, black or blurred depending on the setting.
    private let subtitlePlate = UIView()
    private let subtitleBlur = UIVisualEffectView(effect: nil)
    /// Holds the blur part-way in; see `applySubtitleBlur`.
    private var blurAnimator: UIViewPropertyAnimator?
    private var activeObserver: NSObjectProtocol?
    private var subtitleBottom: NSLayoutConstraint?
    /// What is covering the bottom of the picture at the moment.
    private var barShowing = false
    private var episodesShowing = false
    private var shownCue = ""
    /// The skip or next-episode offer the picture calls for at this moment, shown or not.
    private var due: PlayerPrompt.Kind?
    private let prompt = PlayerPrompt()
    private var promptHost: UIHostingController<AnyView>?
    private var focusObserver: NSObjectProtocol?
    /// Focus is on one of the player's own controls (a tab of the info panel, a menu button)
    /// rather than on the picture.
    private var controlsFocused = false

    private var loc: Loc { app.loc }
    private var current: StreamItem { items[index] }

    /// When playback stops by itself, for falling asleep to.
    private enum SleepTimer: Equatable { case off, endOfEpisode, minutes(Int) }
    private var sleep: SleepTimer = .off
    private var sleepTask: Task<Void, Never>?
    /// The stretch last jumped over by itself, so that one the user goes back into is left alone.
    private var skippedOn: String?
    private var rateObservation: NSKeyValueObservation?

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

    func makeController() -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.delegate = self
        self.controller = controller
        installSubtitleLabel(in: controller)
        installPrompt(in: controller)
        // The category alone does not open the route, so a playing picture stays silent.
        // External playback is for sending video off this TV; left on, it can take the audio with it.
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .moviePlayback)
        try? session.setActive(true)
        player.allowsExternalPlayback = false
        player.isMuted = false
        player.volume = 1

        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 4), queue: .main) { [weak self] time in
            MainActor.assumeIsolated { self?.tick(time.seconds) }
        }
        heartbeat = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.report() }
        }
        // A series keeps its subtitle timing from one episode to the next, as on the Mac.
        if post.isSeries, let kept = UserDefaults.standard.dictionary(forKey: speedKey)?["subtitleOffset"] as? Double { subtitleOffset = kept }
        // The speed picked in the system's menu, kept for the series' next episodes.
        rateObservation = player.observe(\.rate) { [weak self] player, _ in
            let rate = player.rate
            Task { @MainActor in if rate > 0 { self?.keepSpeed(rate) } }
        }
        if items.isEmpty { close() } else { start(index) }
        return controller
    }

    func shutdown() {
        guard !closed else { return }
        closed = true
        report()
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        if let activeObserver { NotificationCenter.default.removeObserver(activeObserver) }
        if let focusObserver { NotificationCenter.default.removeObserver(focusObserver) }
        blurAnimator?.stopAnimation(true)
        statusObservation = nil
        heartbeat?.invalidate()
        subtitleTask?.cancel()
        sleepTask?.cancel()
        findTask?.cancel()
        rateObservation = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    private func close() {
        app.closePlayer()
    }

    // MARK: Items

    /// Starts item `i`, from `position` if given, else from where the user left off.
    private func start(_ i: Int, at position: Double? = nil) {
        guard items.indices.contains(i) else { return close() }
        report()
        index = i
        reportedTime = 0
        guard let quality = current.quality(preferAdaptive: app.settings.preferAdaptive) else {
            app.showToast(loc(.noStream))
            return close()
        }
        load(quality, at: position ?? resumePosition())
        resume = nil
        if subtitleLanguage.hasPrefix(PlayerEngine.found) { subtitleLanguage = "fa" }
        selectSubtitle(subtitleLanguage, remember: false)
        lookForSubtitles()
    }

    /// 30nama has no Persian subtitle for this item: look on SubSource, and show the best one at once
    /// if Persian is what the user watches with.
    private func lookForSubtitles() {
        findTask?.cancel()
        foundSubtitles = []
        guard current.subtitles["fa"] == nil else { return }
        let item = index
        let query = SubSource.Query(title: post.titleEnglish ?? post.title(.en), year: post.year, imdbID: post.imdbID,
                                    isSeries: post.isSeries, season: post.isSeries ? current.season : nil)
        let release = [current.title(.en), quality?.label].compactMap { $0 }.joined(separator: " ")
        let episode = post.isSeries ? current.episodeNumber : nil
        findTask = Task { [weak self] in
            let list = await SubtitleFinder.shared.candidates(for: query, release: release, episode: episode)
            guard let self, !Task.isCancelled, !self.closed, self.index == item else { return }
            self.foundSubtitles = Array(list.prefix(6))
            if let best = list.first, self.subtitleLanguage == "fa" {
                self.selectSubtitle(PlayerEngine.found + best.id, remember: false)
            }
            self.refreshMenus()
        }
    }

    private func resumePosition() -> Double? {
        guard let resume, resume.fileID == current.fileID, resume.time > 5 else { return nil }
        // Left in the closing credits: start over rather than resume into the end.
        if resume.duration > 0, resume.time > resume.duration * 0.97 { return nil }
        return resume.time
    }

    private func load(_ quality: Quality, at position: Double?) {
        self.quality = quality
        audioSettled = false
        let item = AVPlayerItem(url: quality.url(for: mix))
        item.externalMetadata = metadata()

        statusObservation = item.observe(\.status) { [weak self] item, _ in
            Task { @MainActor in
                if item.status == .failed { self?.failed() }
                if item.status == .readyToPlay { self?.preferPlayableAudio(on: item) }
            }
        }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.finished() }
        }

        player.replaceCurrentItem(with: item)
        if let position, position > 0 {
            player.seek(to: CMTime(seconds: position, preferredTimescale: 600))
        }
        // Play goes at the default rate: the speed this series was last watched at.
        let speed = keptSpeed
        player.defaultRate = speed
        if let choice = controller?.speeds.first(where: { abs($0.rate - speed) < 0.01 }) { controller?.selectSpeed(choice) }
        player.play()

        due = nil
        prompt.offer = nil
        refreshMenus()
        attachArtwork(to: item)
    }

    private func finished() {
        if sleep == .endOfEpisode {
            sleep = .off
            return close()
        }
        if index + 1 < items.count, app.settings.autoplayNext {
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

    // MARK: Subtitle timing

    private func nudgeSubtitles(_ seconds: Double) {
        subtitleOffset = seconds == 0 ? 0 : ((subtitleOffset + seconds) * 4).rounded() / 4
        if post.isSeries {
            var kept = UserDefaults.standard.dictionary(forKey: speedKey) ?? [:]
            kept["subtitleOffset"] = subtitleOffset
            UserDefaults.standard.set(kept, forKey: speedKey)
        }
        showCue(track.text(at: player.currentTime().seconds - subtitleOffset))
        refreshMenus()
    }

    // MARK: Speed and sleep

    /// A series keeps its speed from one episode, and one evening, to the next.
    private var speedKey: String { "series.\(post.id)" }

    private var keptSpeed: Float {
        guard post.isSeries, let kept = UserDefaults.standard.dictionary(forKey: speedKey)?["speed"] as? Double else { return 1 }
        return Float(kept)
    }

    private func keepSpeed(_ rate: Float) {
        guard post.isSeries, abs(rate - keptSpeed) > 0.01 else { return }
        var kept = UserDefaults.standard.dictionary(forKey: speedKey) ?? [:]
        kept["speed"] = Double(rate)
        UserDefaults.standard.set(kept, forKey: speedKey)
    }

    private func setSleep(_ timer: SleepTimer) {
        sleep = timer
        sleepTask?.cancel()
        refreshMenus()
        guard case .minutes(let minutes) = timer else { return }
        sleepTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(minutes * 60))
            guard !Task.isCancelled, let self else { return }
            self.sleep = .off
            self.player.pause()
            self.refreshMenus()
        }
    }

    private func sleepMenu() -> UIMenu {
        let choices: [(SleepTimer, String)] = [
            (.off, loc(.off)),
            (.endOfEpisode, loc(post.isSeries ? .endOfEpisode : .endOfFilm)),
        ] + [15, 30, 45, 60].map { (.minutes($0), "\(loc.num($0)) \(loc(.min))") }
        return UIMenu(title: loc(.sleepTimer), image: UIImage(systemName: sleep == .off ? "moon.zzz" : "moon.zzz.fill"), children: choices.map { timer, name in
            UIAction(title: name, state: timer == sleep ? .on : .off) { [weak self] _ in self?.setSleep(timer) }
        })
    }

    // MARK: Progress

    /// Tells the server where playback is, which drives "continue watching" on every device.
    ///
    /// The position goes up in steps of thirty seconds, which is how the 30nama website reports
    /// it. The server accepts any other position just the same and then does not record it.
    private func report() {
        guard app.isLoggedIn, items.indices.contains(index), player.currentItem != nil else { return }
        let seconds = player.currentTime().seconds
        guard seconds.isFinite else { return }
        let time = Int(seconds) / 30 * 30
        guard time > 0, time != reportedTime else { return }
        reportedTime = time
        let duration = player.currentItem?.duration.seconds ?? 0
        let item = current
        let params = [
            "post_id": item.postID ?? String(post.id),
            "file_id": item.fileID,
            "user_id": app.user?.id ?? "",
            "time": String(time),
            "duration": String(duration.isFinite ? Int(duration) : 0),
            "token": item.token,
        ]
        let api = app.api
        Task { _ = try? await api.call("observer", params) }
    }

    /// The original playlist often defaults to a track this TV cannot decode, and then stays silent
    /// instead of moving to a playable one. Pick a playable track once the playlist has listed them.
    private func preferPlayableAudio(on item: AVPlayerItem) {
        guard player.currentItem === item, !audioSettled else { return }
        guard let group = item.asset.mediaSelectionGroup(forMediaCharacteristic: .audible) else { return }
        guard !group.options.isEmpty else { return }
        let selected = item.currentMediaSelection.selectedMediaOption(in: group)
        if selected?.isPlayable == true {
            audioSettled = true
            return
        }
        if let option = group.options.first(where: \.isPlayable) {
            item.select(option, in: group)
            audioSettled = true
            return
        }
        audioSettled = true
        guard mix != .stereo, let quality, let stereo = quality.stereo, stereo != quality.url(for: mix) else { return }
        mix = .stereo
        app.update { $0.audioMix = .stereo }
        let position = player.currentTime().seconds
        load(quality, at: position.isFinite ? position : nil)
    }

    private func tick(_ time: Double) {
        guard !closed, time.isFinite else { return }
        if let item = player.currentItem { preferPlayableAudio(on: item) }
        let cue = track.text(at: time - subtitleOffset)
        if cue != shownCue { showCue(cue) }

        if let skip = current.skipTarget(at: time) {
            due = skip.isIntro ? .intro : .recap
            // Once per stretch: going back into it on purpose is not undone.
            let stretch = "\(current.id)@\(skip.to)"
            if app.settings.autoSkip, skippedOn != stretch, player.timeControlStatus == .playing {
                skippedOn = stretch
                player.seek(to: CMTime(seconds: skip.to, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
                due = nil
            }
        } else if let credits = current.creditsStart, credits > 0, time >= credits, index + 1 < items.count {
            due = .next
        } else {
            due = nil
        }
        showPrompt(at: time)
    }

    // MARK: Skip and next-episode prompt

    /// The prompt is not the system's contextual button, whose look cannot be changed: it is a
    /// pill of the app's own, drawn over the picture, and the select button is taken over while
    /// it is up. It steps aside whenever the transport bar or the info panel is in use, so that
    /// select goes to them as usual.
    private func installPrompt(in controller: AVPlayerViewController) {
        guard let overlay = controller.contentOverlayView else { return }
        let host = UIHostingController(rootView: AnyView(PlayerPromptView(prompt: prompt)
            .environment(\.layoutDirection, loc.lang.isRTL ? .rightToLeft : .leftToRight)))
        host.view.backgroundColor = .clear
        host.view.isUserInteractionEnabled = false
        host.view.frame = overlay.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        overlay.addSubview(host.view)
        promptHost = host

        let select = UITapGestureRecognizer(target: self, action: #selector(acceptPrompt))
        select.allowedPressTypes = [NSNumber(value: UIPress.PressType.select.rawValue)]
        select.delegate = self
        controller.view.addGestureRecognizer(select)

        // While nothing of the player's is on screen, focus rests on a view the size of the
        // picture. Anything smaller holding focus is one of its controls.
        focusObserver = NotificationCenter.default.addObserver(forName: UIFocusSystem.didUpdateNotification, object: nil, queue: .main) { [weak self] note in
            let context = note.userInfo?[UIFocusSystem.focusUpdateContextUserInfoKey] as? UIFocusUpdateContext
            MainActor.assumeIsolated {
                // The info panel is not inside the player's own view, so this goes by the window.
                guard let self, let view = context?.nextFocusedItem as? UIView, let window = view.window, window == self.controller?.view.window else { return }
                let frame = view.convert(view.bounds, to: window)
                self.controlsFocused = frame.width < window.bounds.width * 0.9 || frame.height < window.bounds.height * 0.9
                self.showPrompt(at: self.player.currentTime().seconds)
            }
        }
    }

    private func showPrompt(at time: Double) {
        guard let due, !barShowing, !controlsFocused else {
            if prompt.offer != nil { prompt.offer = nil }
            return
        }
        var offer = PlayerPrompt.Offer(kind: due, title: loc(due == .intro ? .skipIntro : due == .recap ? .skipRecap : .nextEpisode))
        if due == .next, app.settings.autoplayNext, sleep != .endOfEpisode, let credits = current.creditsStart,
           let length = player.currentItem?.duration.seconds, length.isFinite, length > credits {
            // In steps, so the pill is not redrawn four times a second.
            offer.countdown = (min(1, max(0, (time - credits) / (length - credits))) * 50).rounded() / 50
        }
        if prompt.offer != offer { prompt.offer = offer }
    }

    @objc private func acceptPrompt() {
        guard let kind = prompt.offer?.kind else { return }
        switch kind {
        case .intro, .recap:
            guard let skip = current.skipTarget(at: player.currentTime().seconds) else { return }
            player.seek(to: CMTime(seconds: skip.to, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        case .next:
            start(index + 1)
        }
    }

    /// Select belongs to the prompt only while it is on screen.
    nonisolated func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        MainActor.assumeIsolated { prompt.offer != nil }
    }

    /// The player's own handling of select waits for the prompt to pass on it.
    nonisolated func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldBeRequiredToFailBy other: UIGestureRecognizer) -> Bool {
        true
    }

    // MARK: Menus and info panel

    private func refreshMenus() {
        guard let controller else { return }
        var menus: [UIMenuElement] = []

        let languages = ["fa", "en"].filter { current.subtitles[$0] != nil }
        if !languages.isEmpty || !foundSubtitles.isEmpty {
            let codes = languages + foundSubtitles.map { PlayerEngine.found + $0.id }
            var options = [("off", loc(.off))] + languages.map { ($0, loc($0 == "fa" ? .persian : .english)) }
            // Found ones are named by their release; the same name twice gets a number.
            var seen: [String: Int] = [:]
            for candidate in foundSubtitles {
                seen[candidate.name, default: 0] += 1
                let n = seen[candidate.name]!
                options.append((PlayerEngine.found + candidate.id, "\(loc(.persian)) · \(candidate.name)" + (n > 1 ? " (\(loc.num(n)))" : "")))
            }
            let active = codes.contains(subtitleLanguage) ? subtitleLanguage : "off"
            let choices = UIMenu(options: .displayInline, children: options.map { code, name in
                UIAction(title: name, state: code == active ? .on : .off) { [weak self] _ in
                    self?.selectSubtitle(code, remember: true)
                    self?.refreshMenus()
                }
            })
            let shown = subtitleOffset == 0 ? "0" : subtitleOffset.formatted(.number.sign(strategy: .always()).precision(.fractionLength(2)))
            let quarter = loc.num("0.25")
            let timing = UIMenu(title: loc.isolated("\(loc(.subtitleTiming)): \(loc.num(shown)) \(loc(.secondsShort))"), children: [
                UIAction(title: loc.isolated("\(loc(.subtitleEarlier)) (−\(quarter) \(loc(.secondsShort)))")) { [weak self] _ in self?.nudgeSubtitles(-0.25) },
                UIAction(title: loc.isolated("\(loc(.subtitleLater)) (+\(quarter) \(loc(.secondsShort)))")) { [weak self] _ in self?.nudgeSubtitles(0.25) },
                UIAction(title: loc(.reset), attributes: subtitleOffset == 0 ? .disabled : []) { [weak self] _ in self?.nudgeSubtitles(0) },
            ])
            menus.append(UIMenu(title: loc(.subtitles), image: UIImage(systemName: "text.bubble"), children: [choices, timing]))
        }

        let mixes = quality?.mixes ?? []
        if mixes.count > 1, let quality {
            menus.append(UIMenu(title: loc(.audio), image: UIImage(systemName: "speaker.wave.2"), children: mixes.map { option in
                let title = loc(option == .stereo ? .audioStereo : option == .surround ? .audioSurround : .audioOriginal)
                return UIAction(title: title, state: option == mix ? .on : .off) { [weak self] _ in
                    guard let self, option != self.mix else { return }
                    self.mix = option
                    self.app.update { $0.audioMix = option }
                    let position = self.player.currentTime().seconds
                    self.load(quality, at: position.isFinite ? position : nil)
                }
            }))
        }

        if current.qualities.count > 1 {
            menus.append(UIMenu(title: loc(.quality), image: UIImage(systemName: "sparkles.tv"), children: current.qualities.map { option in
                UIAction(title: option.isAuto ? loc(.qualityAuto) : option.label, state: option == quality ? .on : .off) { [weak self] _ in
                    guard let self, option != self.quality else { return }
                    let position = self.player.currentTime().seconds
                    self.load(option, at: position.isFinite ? position : nil)
                }
            }))
        }
        menus.append(sleepMenu())
        controller.transportBarCustomMenuItems = menus

        if items.count > 1 {
            let strip = EpisodeStrip(
                entries: Array(items.enumerated()).map { .init(index: $0.offset, item: $0.element) },
                playing: index
            ) { [weak self] chosen in
                self?.start(chosen)
            } shown: { [weak self] showing in
                guard let self, episodesShowing != showing else { return }
                episodesShowing = showing
                subtitleBottom?.constant = subtitleLift
                UIView.animate(withDuration: 0.3) { self.subtitlePlate.superview?.layoutIfNeeded() }
            }
            let host = UIHostingController(rootView: strip
                .environment(\.loc, loc)
                .environment(\.layoutDirection, loc.lang.isRTL ? .rightToLeft : .leftToRight))
            host.title = loc(.episodes)
            host.view.backgroundColor = .clear
            // Room for the row of season names over the episodes, when there is more than one season.
            host.preferredContentSize = CGSize(width: 0, height: Set(items.map(\.season)).count > 1 ? 390 : 290)
            controller.customInfoViewControllers = [host]
        } else {
            controller.customInfoViewControllers = []
        }
    }

    private func metadata() -> [AVMetadataItem] {
        func make(_ identifier: AVMetadataIdentifier, _ value: String) -> AVMetadataItem {
            let m = AVMutableMetadataItem()
            m.identifier = identifier
            m.value = value as NSString
            m.extendedLanguageTag = "und"
            return m
        }
        var out = [make(.commonIdentifierTitle, post.title(loc.lang))]
        if post.isSeries || items.count > 1 {
            var line = loc.episodeTag(season: current.season, episode: Episode.label(current.episodeRaw, fallback: index + 1))
            if let name = current.title(loc.lang) { line += " — " + name }
            out.append(make(.iTunesMetadataTrackSubTitle, line))
        }
        let plot = post.plot(loc.lang)
        if !plot.isEmpty { out.append(make(.commonIdentifierDescription, plot)) }
        if let genre = post.genres.first?.name { out.append(make(.quickTimeMetadataGenre, genre)) }
        return out
    }

    /// Poster for the info panel and Now Playing. Fetched separately so it never delays playback.
    private func attachArtwork(to item: AVPlayerItem) {
        guard let url = post.poster.jpeg else { return }
        Task { [weak self, weak item] in
            guard let (data, _) = try? await URLSession.shared.data(from: url), let item,
                  self?.player.currentItem === item else { return }
            let artwork = AVMutableMetadataItem()
            artwork.identifier = .commonIdentifierArtwork
            artwork.value = data as NSData
            artwork.dataType = kCMMetadataBaseDataType_JPEG as String
            artwork.extendedLanguageTag = "und"
            item.externalMetadata.append(artwork)
        }
    }

    // MARK: Subtitles

    /// Subtitles are separate WebVTT files, not tracks of the stream, so they are fetched here and
    /// drawn over the video.
    private func selectSubtitle(_ language: String, remember: Bool) {
        subtitleLanguage = language
        let found = language.hasPrefix(PlayerEngine.found)
        if remember { app.update { $0.defaultSubtitle = found ? "fa" : language } }
        subtitleTask?.cancel()
        track = SubtitleTrack()
        showCue("")
        if found {
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
            let parsed = await Task.detached { SubtitleTrack(vtt: String(decoding: data, as: UTF8.self)) }.value
            guard !Task.isCancelled, let self else { return }
            self.subtitleCache[url] = parsed
            if self.subtitleLanguage == language, self.current.subtitles[language] == url { self.track = parsed }
        }
    }

    private func installSubtitleLabel(in controller: AVPlayerViewController) {
        guard let overlay = controller.contentOverlayView else { return }
        subtitleLabel.numberOfLines = 0
        subtitleLabel.textAlignment = .center
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        subtitlePlate.translatesAutoresizingMaskIntoConstraints = false
        subtitlePlate.layer.cornerRadius = 14
        subtitlePlate.layer.cornerCurve = .continuous
        subtitlePlate.clipsToBounds = true
        subtitlePlate.isHidden = true
        subtitleBlur.frame = subtitlePlate.bounds
        subtitleBlur.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        subtitlePlate.addSubview(subtitleBlur)
        subtitlePlate.addSubview(subtitleLabel)
        overlay.addSubview(subtitlePlate)

        subtitlePlate.backgroundColor = UIColor.black.withAlphaComponent(app.settings.subtitleBackground.plateOpacity)
        applySubtitleBlur()
        // A paused animator is finished off by the system when the app leaves the foreground,
        // which would snap a half blur to a full one; set it up again on return.
        activeObserver = NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.applySubtitleBlur() }
        }

        let bottom = subtitlePlate.bottomAnchor.constraint(equalTo: overlay.bottomAnchor, constant: -70)
        subtitleBottom = bottom
        NSLayoutConstraint.activate([
            subtitlePlate.centerXAnchor.constraint(equalTo: overlay.centerXAnchor),
            subtitlePlate.widthAnchor.constraint(lessThanOrEqualTo: overlay.widthAnchor, multiplier: 0.82),
            bottom,
            subtitleLabel.leadingAnchor.constraint(equalTo: subtitlePlate.leadingAnchor, constant: 26),
            subtitleLabel.trailingAnchor.constraint(equalTo: subtitlePlate.trailingAnchor, constant: -26),
            subtitleLabel.topAnchor.constraint(equalTo: subtitlePlate.topAnchor, constant: 8),
            subtitleLabel.bottomAnchor.constraint(equalTo: subtitlePlate.bottomAnchor, constant: -10),
        ])
    }

    /// A visual effect view has no "amount" and must not be made translucent, so a partial blur is
    /// made by animating the effect in and stopping the animation part-way.
    private func applySubtitleBlur() {
        let strength = app.settings.subtitleBackground.blurStrength
        blurAnimator?.stopAnimation(true)
        blurAnimator = nil
        subtitleBlur.isHidden = strength == 0
        subtitleBlur.effect = nil
        guard strength > 0 else { return }
        guard strength < 1 else {
            subtitleBlur.effect = UIBlurEffect(style: .dark)
            return
        }
        let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [subtitleBlur] in
            subtitleBlur.effect = UIBlurEffect(style: .dark)
        }
        animator.pausesOnCompletion = true
        animator.fractionComplete = strength
        blurAnimator = animator
    }

    private func showCue(_ text: String) {
        shownCue = text
        subtitlePlate.isHidden = text.isEmpty
        guard !text.isEmpty else {
            subtitleLabel.attributedText = nil
            return
        }
        // On a plate the text needs no help; on bare video it gets an outline and a shadow.
        let bare = app.settings.subtitleBackground == .none
        let size = CGFloat(app.settings.subtitleSize)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.lineHeightMultiple = 1.15
        let shadow = NSShadow()
        shadow.shadowColor = UIColor.black.withAlphaComponent(bare ? 0.9 : 0)
        shadow.shadowBlurRadius = 6
        shadow.shadowOffset = CGSize(width: 0, height: 2)
        subtitleLabel.attributedText = NSAttributedString(string: text, attributes: [
            .font: UIFont(name: AppFont.name(.medium), size: size) ?? .systemFont(ofSize: size, weight: .medium),
            .foregroundColor: app.settings.subtitleColor.color,
            // Negative width strokes and fills: a thin black outline keeps text readable over bright scenes.
            .strokeColor: UIColor.black,
            .strokeWidth: bare ? -2.5 : 0,
            .shadow: shadow,
            .paragraphStyle: paragraph,
        ])
    }

    /// How far subtitles sit above the bottom edge: clear of the episodes panel or the transport
    /// bar while either is showing.
    private var subtitleLift: CGFloat {
        // The panel is taller with the row of season names in it.
        let panel: CGFloat = Set(items.map(\.season)).count > 1 ? -570 : -470
        return episodesShowing ? panel : (barShowing ? -250 : -70)
    }

    /// Lifts subtitles clear of the transport bar while it is showing.
    nonisolated func playerViewController(
        _ playerViewController: AVPlayerViewController,
        willTransitionToVisibilityOfTransportBar visible: Bool,
        with coordinator: AVPlayerViewControllerAnimationCoordinator
    ) {
        MainActor.assumeIsolated {
            barShowing = visible
            showPrompt(at: player.currentTime().seconds)
            // The panels live in the bar, so they are gone when it is.
            if !visible { episodesShowing = false }
            subtitleBottom?.constant = subtitleLift
            let overlay = playerViewController.contentOverlayView
            coordinator.addCoordinatedAnimations({ overlay?.layoutIfNeeded() }, completion: nil)
        }
    }
}

/// The series' episodes, shown as a tab of the player's info panel: the season names in a row, and
/// under them the episodes of the one chosen, which is the season playing when the panel opens.
private struct EpisodeStrip: View {
    struct Entry: Identifiable {
        let index: Int
        let item: StreamItem
        var id: Int { index }
    }

    let entries: [Entry]
    let playing: Int
    let select: (Int) -> Void
    /// Told when the strip comes on screen and when it leaves.
    let shown: (Bool) -> Void

    @Environment(\.loc) private var loc
    @State private var season: Int?

    private var seasons: [Int] { Array(Set(entries.map(\.item.season))).sorted() }
    private var playingSeason: Int { entries.first { $0.index == playing }?.item.season ?? seasons.first ?? 1 }

    var body: some View {
        let open = season ?? playingSeason
        VStack(alignment: .leading, spacing: 0) {
            if seasons.count > 1 {
                ScrollView(.horizontal) {
                    HStack(spacing: 16) {
                        ForEach(seasons, id: \.self) { number in
                            SeasonChip(title: "\(loc(.season)) \(loc.num(number))", selected: number == open, playing: number == playingSeason) {
                                withAnimation(Theme.glide) { season = number }
                            }
                        }
                    }
                    .padding(.horizontal, Theme.edge)
                    .padding(.top, 18)
                }
                .scrollIndicators(.hidden)
                .scrollClipDisabled()
                .focusSection()
            }
            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 24) {
                        ForEach(entries.filter { $0.item.season == open }) { entry in
                            Button { select(entry.index) } label: {
                                EpisodeTile(item: entry.item, position: entry.index + 1, nowPlaying: entry.index == playing)
                            }
                            .buttonStyle(CardButtonStyle(radius: Theme.tileRadius, scale: 1.06))
                            .id(entry.index)
                        }
                    }
                    .padding(.horizontal, Theme.edge)
                    .padding(.vertical, 26)
                    // A new season starts at its first episode, not where the last one was left.
                    .id(open)
                }
                .scrollIndicators(.hidden)
                .scrollClipDisabled()
                .focusSection()
                .onAppear {
                    proxy.scrollTo(playing, anchor: .center)
                    shown(true)
                }
                .onDisappear { shown(false) }
            }
        }
    }
}

/// A season's name over the episodes; pressed, it shows that season's. The app's chip, as on the
/// title page: the system's plain button lays a white plate behind it when focused.
private struct SeasonChip: View {
    let title: String
    let selected: Bool
    /// The episode playing is in this season.
    let playing: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if playing { Circle().fill(Theme.accent).frame(width: 10, height: 10) }
                Text(title)
            }
        }
        .buttonStyle(PillButtonStyle(kind: .chip, selected: selected))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
