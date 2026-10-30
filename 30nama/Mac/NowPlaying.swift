import AppKit
import MediaPlayer

/// The player's place in macOS's media controls: the play/pause key, headphone buttons, and the
/// Now Playing item in Control Center. Also keeps the screen awake while a film plays, which
/// the system's own player does only some of the time and mpv not at all.
@MainActor
final class NowPlaying {
    struct Handlers {
        var play: () -> Void
        var pause: () -> Void
        var toggle: () -> Void
        var skip: (Double) -> Void
        var seek: (Double) -> Void
        var next: () -> Void
        var previous: () -> Void
    }

    private var targets: [(MPRemoteCommand, Any)] = []
    private var awake: NSObjectProtocol?
    private var info: [String: Any] = [:]
    private var artworkFor: URL?

    init(_ handlers: Handlers) {
        let center = MPRemoteCommandCenter.shared()
        func on(_ command: MPRemoteCommand, _ act: @escaping (MPRemoteCommandEvent) -> Void) {
            command.isEnabled = true
            let token = command.addTarget { event in
                MainActor.assumeIsolated { act(event) }
                return .success
            }
            targets.append((command, token))
        }
        on(center.playCommand) { _ in handlers.play() }
        on(center.pauseCommand) { _ in handlers.pause() }
        on(center.togglePlayPauseCommand) { _ in handlers.toggle() }
        center.skipForwardCommand.preferredIntervals = [10]
        center.skipBackwardCommand.preferredIntervals = [10]
        on(center.skipForwardCommand) { _ in handlers.skip(10) }
        on(center.skipBackwardCommand) { _ in handlers.skip(-10) }
        on(center.nextTrackCommand) { _ in handlers.next() }
        on(center.previousTrackCommand) { _ in handlers.previous() }
        on(center.changePlaybackPositionCommand) { event in
            if let event = event as? MPChangePlaybackPositionCommandEvent { handlers.seek(event.positionTime) }
        }
    }

    /// What is on screen. Called when the item, its length, the play state, the speed, or the
    /// position by a jump changes; between those the system runs the clock on from `rate`.
    func update(title: String, subtitle: String?, poster: URL?, time: Double, duration: Double, playing: Bool, speed: Float, hasNext: Bool) {
        info[MPMediaItemPropertyTitle] = title
        info[MPMediaItemPropertyArtist] = subtitle
        info[MPMediaItemPropertyPlaybackDuration] = duration > 0 ? duration : nil
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = time
        info[MPNowPlayingInfoPropertyPlaybackRate] = playing ? Double(speed) : 0
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = 1.0
        info[MPNowPlayingInfoPropertyMediaType] = MPNowPlayingInfoMediaType.video.rawValue
        MPRemoteCommandCenter.shared().nextTrackCommand.isEnabled = hasNext
        let center = MPNowPlayingInfoCenter.default()
        center.nowPlayingInfo = info
        // On the Mac this, not the info, decides whether the play key comes to this app.
        center.playbackState = playing ? .playing : .paused
        keepAwake(playing)
        if let poster, poster != artworkFor {
            artworkFor = poster
            Task { [weak self] in
                guard let image = await ImagePipeline.shared.image(poster, maxPixel: 600), let self, self.artworkFor == poster else { return }
                self.info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                MPNowPlayingInfoCenter.default().nowPlayingInfo = self.info
            }
        }
    }

    private func keepAwake(_ on: Bool) {
        if on, awake == nil {
            awake = ProcessInfo.processInfo.beginActivity(options: [.idleDisplaySleepDisabled, .userInitiated], reason: "Playing a film")
        } else if !on, let activity = awake {
            ProcessInfo.processInfo.endActivity(activity)
            awake = nil
        }
    }

    /// The player has closed: the keys go back to whatever played before, and the screen may sleep.
    func end() {
        keepAwake(false)
        for (command, token) in targets { command.removeTarget(token) }
        targets = []
        let center = MPNowPlayingInfoCenter.default()
        center.nowPlayingInfo = nil
        center.playbackState = .stopped
    }
}
