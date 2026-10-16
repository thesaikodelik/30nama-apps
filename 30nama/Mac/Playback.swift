import SwiftUI
import AVKit
import Libmpv
import CoreImage

/// What decodes a file, draws its picture and plays its sound. The player above it (the queue of
/// episodes, subtitles, the skip offers, the controls) is the same whichever of these is at work.
///
/// There are two. The system's own decoder plays 30nama's streams, cheaply, and is what Picture
/// in Picture needs. mpv plays what the system cannot read, which is most downloaded files:
/// Matroska, and sound in formats Apple does not decode.
@MainActor
protocol Playback: AnyObject {
    /// Called a few times a second with the position, in seconds, while a file is open.
    var onTick: ((Double) -> Void)? { get set }
    var onEnd: (() -> Void)? { get set }
    var onFail: (() -> Void)? { get set }

    var position: Double { get }
    /// Zero until the file has said how long it is.
    var duration: Double { get }
    /// Playing was asked for but the picture is waiting on data.
    var waiting: Bool { get }
    /// Width over height of the picture as it is shown; zero until that is known.
    var aspect: Double { get }

    func load(_ url: URL, at position: Double?)
    func play()
    func pause()
    func seek(to seconds: Double)
    func setSpeed(_ speed: Float, playing: Bool)
    func setVolume(_ volume: Float)
    func setMuted(_ muted: Bool)
    /// Stops for good and lets go of everything.
    func stop()
}

// MARK: - The system's decoder

@MainActor
final class SystemPlayback: Playback {
    var onTick: ((Double) -> Void)?
    var onEnd: (() -> Void)?
    var onFail: (() -> Void)?
    /// Called when a file is open and has no sound this Mac can play.
    var onSilent: (() -> Void)?

    let player = AVPlayer()
    private var timeObserver: Any?
    private var statusObservation: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?
    private(set) var pip: AVPictureInPictureController?
    /// Hands out a thumbnail of the frame on screen, for the light around the picture.
    private var output: AVPlayerItemVideoOutput?

    init() {
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 4), queue: .main) { [weak self] time in
            MainActor.assumeIsolated {
                guard let self, time.seconds.isFinite else { return }
                self.onTick?(time.seconds)
            }
        }
    }

    var position: Double {
        let seconds = player.currentTime().seconds
        return seconds.isFinite ? seconds : 0
    }

    var duration: Double {
        guard let length = player.currentItem?.duration.seconds, length.isFinite, length > 0 else { return 0 }
        return length
    }

    var waiting: Bool { player.timeControlStatus == .waitingToPlayAtSpecifiedRate }

    var aspect: Double {
        guard let size = player.currentItem?.presentationSize, size.height > 0 else { return 0 }
        return size.width / size.height
    }

    /// The frame on screen now, 96 by 108, or nil when it has not changed since the last call.
    func glimpse() -> CVPixelBuffer? {
        guard let output else { return nil }
        let time = output.itemTime(forHostTime: CACurrentMediaTime())
        guard output.hasNewPixelBuffer(forItemTime: time) else { return nil }
        return output.copyPixelBuffer(forItemTime: time, itemTimeForDisplay: nil)
    }

    func load(_ url: URL, at position: Double?) {
        let item = AVPlayerItem(url: url)
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            // Taller than the picture's shape: the rows are what black bars are looked for in.
            kCVPixelBufferWidthKey as String: 96, kCVPixelBufferHeightKey as String: 108,
            kCVPixelBufferIOSurfacePropertiesKey as String: [String: Any](),
        ])
        item.add(output)
        self.output = output
        statusObservation = item.observe(\.status) { [weak self] item, _ in
            Task { @MainActor in
                if item.status == .failed { self?.onFail?() }
                if item.status == .readyToPlay { await self?.preferPlayableAudio(on: item) }
            }
        }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.onEnd?() }
        }
        player.replaceCurrentItem(with: item)
        if let position, position > 0 {
            player.seek(to: CMTime(seconds: position, preferredTimescale: 600))
        }
    }

    /// A playlist can default to a track this Mac cannot decode, and then stays silent. Pick a
    /// playable one; if there is none, say so.
    private func preferPlayableAudio(on item: AVPlayerItem) async {
        guard let group = try? await item.asset.loadMediaSelectionGroup(for: .audible),
              !group.options.isEmpty, player.currentItem === item else { return }
        if item.currentMediaSelection.selectedMediaOption(in: group)?.isPlayable == true { return }
        if let option = group.options.first(where: \.isPlayable) {
            item.select(option, in: group)
        } else {
            onSilent?()
        }
    }

    func play() { player.play() }
    func pause() { player.pause() }

    func seek(to seconds: Double) {
        player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    func setSpeed(_ speed: Float, playing: Bool) {
        player.defaultRate = speed
        if playing { player.rate = speed }
    }

    func setVolume(_ volume: Float) { player.volume = volume }
    func setMuted(_ muted: Bool) { player.isMuted = muted }

    /// Picture in Picture works on the layer the picture is drawn in.
    func attach(_ layer: AVPlayerLayer) {
        if pip == nil, AVPictureInPictureController.isPictureInPictureSupported() {
            pip = AVPictureInPictureController(playerLayer: layer)
            pip?.delegate = pipReturn
        }
    }

    private let pipReturn = PictureInPictureReturn()

    func stop() {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        statusObservation = nil
        if pip?.isPictureInPictureActive == true { pip?.stopPictureInPicture() }
        player.pause()
        player.replaceCurrentItem(with: nil)
    }
}

/// Back from Picture in Picture, the app's window is brought forward, on whatever desktop or
/// screen it is: left alone, the picture returns to a window the user is not looking at.
private final class PictureInPictureReturn: NSObject, AVPictureInPictureControllerDelegate {
    func pictureInPictureController(_ controller: AVPictureInPictureController,
                                    restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completion: @escaping (Bool) -> Void) {
        DispatchQueue.main.async {
            NSApp.activate(ignoringOtherApps: true)
            // The app has one window, and the picture plays in it.
            let window = NSApp.windows.first { $0.isVisible && $0.canBecomeMain }
            window?.makeKeyAndOrderFront(nil)
            completion(true)
        }
    }
}

/// The layer the system's decoder draws into. SwiftUI's own `VideoPlayer` brings the system's controls with it.
struct SystemSurface: NSViewRepresentable {
    let playback: SystemPlayback
    /// Fill the black around the picture with the picture itself, blurred.
    var ambient = true
    let onWindow: (NSWindow?) -> Void

    func makeNSView(context: Context) -> SurfaceView {
        let view = SurfaceView()
        view.playerLayer.player = playback.player
        playback.attach(view.playerLayer)
        view.onWindow = onWindow
        view.light(ambient ? { [weak playback] in playback?.glimpse() } : nil)
        return view
    }

    func updateNSView(_ view: SurfaceView, context: Context) {
        view.light(ambient ? { [weak playback] in playback?.glimpse() } : nil)
    }

    static func dismantleNSView(_ view: SurfaceView, coordinator: ()) {
        view.light(nil)
    }

    final class SurfaceView: NSView {
        let playerLayer = AVPlayerLayer()
        /// The frame on screen again, behind the picture and filling the window: a tiny copy,
        /// taken ten times a second, stretched up and blurred into light. Where the picture
        /// leaves bars, they glow with what is on screen instead of being black.
        private let glow = CALayer()
        /// Keeps black bars that are part of the file itself out of sight: the picture is
        /// enlarged until they fall outside this, and they are cut away.
        private let cut = CALayer()
        /// How much of the frame, top, bottom, left and right, is bars burned into the file.
        private var trim = Trim()
        private var watch = Watch()

        struct Trim: Equatable {
            var top = 0.0, bottom = 0.0, left = 0.0, right = 0.0
        }

        /// What the frames seen so far say about the bars: the fewest black rows and columns at
        /// each edge of any frame bright enough to tell. A dark scene proves nothing; a single
        /// bright one with picture at an edge is enough to keep that edge.
        private struct Watch {
            var top = Int.max, bottom = Int.max, left = Int.max, right = Int.max
            var frames = 0
        }
        private static let shrink: CGFloat = 24
        private var timer: Timer?
        /// The last two frames shown, kept so their memory is not handed back while on screen.
        private var held: [CVPixelBuffer] = []
        var onWindow: ((NSWindow?) -> Void)?

        override init(frame: NSRect) {
            super.init(frame: frame)
            wantsLayer = true
            layerUsesCoreImageFilters = true
            let root = CALayer()
            root.backgroundColor = NSColor.black.cgColor
            layer = root
            glow.contentsGravity = .resize
            glow.magnificationFilter = .linear
            glow.opacity = 0.55
            glow.filters = [CIFilter(name: "CIGaussianBlur", parameters: [kCIInputRadiusKey: 1.5])].compactMap { $0 }
            glow.isHidden = true
            root.addSublayer(glow)
            playerLayer.videoGravity = .resizeAspect
            cut.backgroundColor = NSColor.black.cgColor
            playerLayer.mask = cut
            root.addSublayer(playerLayer)
        }

        /// Turns the light around the picture on, with where to take frames from, or off.
        func light(_ source: (() -> CVPixelBuffer?)?) {
            guard let source else {
                timer?.invalidate()
                timer = nil
                held = []
                glow.contents = nil
                glow.isHidden = true
                watch = Watch()
                if trim != Trim() {
                    trim = Trim()
                    needsLayout = true
                }
                return
            }
            guard timer == nil else { return }
            glow.isHidden = false
            timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
                MainActor.assumeIsolated {
                    guard let self, let frame = source(), let surface = CVPixelBufferGetIOSurface(frame)?.takeUnretainedValue() else { return }
                    self.held = [frame] + self.held.prefix(1)
                    // Left to animate: each new frame fades over the last, which smooths the steps.
                    self.glow.contents = surface
                    self.look(at: frame)
                }
            }
        }

        /// Counts the black rows and columns at the edges of a frame, and once a second and a
        /// half of bright frames agree, cuts that much away.
        private func look(at frame: CVPixelBuffer) {
            CVPixelBufferLockBaseAddress(frame, .readOnly)
            defer { CVPixelBufferUnlockBaseAddress(frame, .readOnly) }
            let width = CVPixelBufferGetWidth(frame), height = CVPixelBufferGetHeight(frame), rowBytes = CVPixelBufferGetBytesPerRow(frame)
            guard width > 8, height > 8, let base = CVPixelBufferGetBaseAddress(frame)?.assumingMemoryBound(to: UInt8.self) else { return }
            func bright(_ x: Int, _ y: Int) -> Int {
                let pixel = base + y * rowBytes + x * 4
                return max(Int(pixel[0]), Int(pixel[1]), Int(pixel[2]))
            }
            // Too dark a frame cannot tell a bar from a night sky.
            var sum = 0
            for y in stride(from: height / 4, to: height * 3 / 4, by: 4) {
                for x in stride(from: width / 4, to: width * 3 / 4, by: 4) { sum += bright(x, y) }
            }
            let samples = max(1, ((height / 2) / 4) * ((width / 2) / 4))
            guard sum / samples > 45 else { return }
            func blackRow(_ y: Int) -> Bool { (0..<width).allSatisfy { bright($0, y) < 26 } }
            func blackColumn(_ x: Int) -> Bool { (0..<height).allSatisfy { bright(x, $0) < 26 } }
            let limit = 0.3
            var top = 0; while top < Int(Double(height) * limit), blackRow(top) { top += 1 }
            var bottom = 0; while bottom < Int(Double(height) * limit), blackRow(height - 1 - bottom) { bottom += 1 }
            var left = 0; while left < Int(Double(width) * limit), blackColumn(left) { left += 1 }
            var right = 0; while right < Int(Double(width) * limit), blackColumn(width - 1 - right) { right += 1 }
            watch.top = min(watch.top, top); watch.bottom = min(watch.bottom, bottom)
            watch.left = min(watch.left, left); watch.right = min(watch.right, right)
            watch.frames += 1
            guard watch.frames >= 15 else { return }
            let found = Trim(top: Double(watch.top) / Double(height), bottom: Double(watch.bottom) / Double(height),
                             left: Double(watch.left) / Double(width), right: Double(watch.right) / Double(width))
            guard found != trim else { return }
            trim = found
            // The bars slide away rather than vanish.
            NSAnimationContext.runAnimationGroup { context in
                context.duration = 0.6
                context.allowsImplicitAnimation = true
                self.place(animated: true)
            }
        }

        required init?(coder: NSCoder) { fatalError("not used") }

        override func layout() {
            super.layout()
            place(animated: false)
        }

        /// Lays the picture out, enlarged by whatever bars are burned into it so that only the
        /// picture proper is fitted to the window, and the bars are cut away.
        private func place(animated: Bool) {
            CATransaction.begin()
            // No implicit animation on a resize, or the picture lags behind the window.
            CATransaction.setDisableActions(!animated)
            if animated { CATransaction.setAnimationDuration(0.6) }
            let size = playerLayer.player?.currentItem?.presentationSize ?? .zero
            if size.width > 0, size.height > 0, trim != Trim() {
                let inner = CGSize(width: size.width * (1 - trim.left - trim.right), height: size.height * (1 - trim.top - trim.bottom))
                let scale = min(bounds.width / inner.width, bounds.height / inner.height)
                let shown = CGRect(x: bounds.midX - inner.width * scale / 2, y: bounds.midY - inner.height * scale / 2,
                                   width: inner.width * scale, height: inner.height * scale)
                // The layer's origin is its bottom-left corner, and the frame's first rows are its top.
                playerLayer.frame = CGRect(x: shown.minX - size.width * trim.left * scale, y: shown.minY - size.height * trim.bottom * scale,
                                           width: size.width * scale, height: size.height * scale)
                cut.frame = CGRect(x: size.width * trim.left * scale, y: size.height * trim.bottom * scale, width: shown.width, height: shown.height)
            } else {
                playerLayer.frame = bounds
                cut.frame = playerLayer.bounds
            }
            // Laid out small and scaled up, so the blur is worked out on a few pixels and the
            // stretch does the rest; a touch larger than the window, so its soft rim falls outside.
            let shrink = SurfaceView.shrink
            glow.bounds = CGRect(x: 0, y: 0, width: bounds.width / shrink, height: bounds.height / shrink)
            glow.position = CGPoint(x: bounds.midX, y: bounds.midY)
            glow.transform = CATransform3DMakeScale(shrink * 1.08, shrink * 1.08, 1)
            CATransaction.commit()
        }

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            onWindow?(window)
        }
    }
}

// MARK: - mpv

/// mpv, for files the system cannot read. It draws straight into a Metal layer.
@MainActor
final class MPVPlayback: Playback {
    var onTick: ((Double) -> Void)?
    var onEnd: (() -> Void)?
    var onFail: (() -> Void)?

    /// A sound or subtitle track inside the file.
    struct Track: Identifiable, Hashable {
        let id: Int
        let subtitle: Bool
        let name: String
    }

    /// Called when a file has opened, with the tracks it carries.
    var onTracks: (([Track]) -> Void)?

    let layer = MetalLayer()
    private var mpv: OpaquePointer?
    private var clock: Timer?
    private let events = DispatchQueue(label: "mpv-events", qos: .userInitiated)
    /// The box the wakeup callback reaches this object through; it outlives a stop safely.
    private let link = Link()

    /// What the event thread and the main thread share. mpv answers a question only when its
    /// own loop gets to it, and that loop can be waiting on the main thread (its video output
    /// sets the layer up there). So the main thread never asks mpv anything: mpv reports its
    /// state as it changes, the event thread files it here, and the main thread reads it here.
    private final class Link: @unchecked Sendable {
        private let lock = NSLock()
        private var stored: OpaquePointer?
        private var numbers: [String: Double] = [:]
        var wake: (() -> Void)?

        var handle: OpaquePointer? {
            get { lock.withLock { stored } }
            set { lock.withLock { stored = newValue } }
        }

        subscript(name: String) -> Double {
            get { lock.withLock { numbers[name] ?? 0 } }
            set { lock.withLock { numbers[name] = newValue } }
        }

        func forget() {
            lock.withLock { numbers = [:] }
        }
    }

    init() {
        layer.framebufferOnly = true
        layer.backgroundColor = NSColor.black.cgColor
        layer.contentsScale = NSScreen.main?.backingScaleFactor ?? 2

        // On a Mac with two graphics chips, draw with the frugal one: waking the other for a
        // sitcom is what turns the fans on.
        setenv("MVK_CONFIG_FORCE_LOW_POWER_GPU", "1", 1)
        guard let handle = mpv_create() else { return }
        mpv = handle
        // Lighter scaling on a Mac that runs hot; the picture is all but the same.
        if !Machine.isFast { mpv_set_option_string(handle, "profile", "fast") }
        var surface = layer
        mpv_set_option(handle, "wid", MPV_FORMAT_INT64, &surface)
        for (name, value) in [
            ("vo", "gpu-next"), ("gpu-api", "vulkan"), ("gpu-context", "moltenvk"), ("hwdec", "videotoolbox"),
            // The app draws its own controls and its own subtitles, and takes the keys itself.
            ("osc", "no"), ("osd-level", "0"), ("input-default-bindings", "no"), ("input-vo-keyboard", "no"),
            ("sid", "no"), ("sub-auto", "no"), ("ytdl", "no"),
            // Stay alive between files, and say so when one ends.
            ("idle", "yes"), ("keep-open", "no"), ("force-window", "no"),
        ] {
            mpv_set_option_string(handle, name, value)
        }
        #if DEBUG
        mpv_request_log_messages(handle, "warn")
        #else
        mpv_request_log_messages(handle, "no")
        #endif
        guard mpv_initialize(handle) >= 0 else {
            mpv_terminate_destroy(handle)
            mpv = nil
            return
        }

        for name in ["time-pos", "duration", "video-params/aspect"] { mpv_observe_property(handle, 0, name, MPV_FORMAT_DOUBLE) }
        for name in ["paused-for-cache", "seeking"] { mpv_observe_property(handle, 0, name, MPV_FORMAT_FLAG) }
        #if DEBUG
        // Says in the log whether the graphics chip is decoding; when it is not, the processor is, and runs hot.
        mpv_observe_property(handle, 0, "hwdec-current", MPV_FORMAT_STRING)
        #endif

        link.handle = handle
        link.wake = { [weak self, events, link] in
            events.async { [weak self] in self?.drain(link) }
        }
        mpv_set_wakeup_callback(handle, { context in
            guard let context else { return }
            Unmanaged<Link>.fromOpaque(context).takeUnretainedValue().wake?()
        }, Unmanaged.passUnretained(link).toOpaque())

        // The position is asked for a few times a second, not taken per frame, so the controls
        // are not redrawn twenty-four times a second for a clock that shows whole seconds.
        clock = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self, self.mpv != nil, self.duration > 0 else { return }
                self.onTick?(self.position)
            }
        }
    }

    /// Reads what mpv has to report: where it is, and when a file ends. Runs off the main thread.
    private nonisolated func drain(_ link: Link) {
        while true {
            guard let handle = link.handle, let event = mpv_wait_event(handle, 0)?.pointee, event.event_id != MPV_EVENT_NONE else { return }
            #if DEBUG
            if event.event_id == MPV_EVENT_LOG_MESSAGE, let data = event.data {
                let message = data.assumingMemoryBound(to: mpv_event_log_message.self).pointee
                print("mpv [\(String(cString: message.prefix))] \(String(cString: message.text))", terminator: "")
            }
            #endif
            if event.event_id == MPV_EVENT_PROPERTY_CHANGE, let data = event.data {
                let property = data.assumingMemoryBound(to: mpv_event_property.self).pointee
                let name = String(cString: property.name)
                #if DEBUG
                if property.format == MPV_FORMAT_STRING, let value = property.data?.assumingMemoryBound(to: UnsafePointer<CChar>?.self).pointee {
                    print("mpv \(name) = \(String(cString: value))")
                }
                #endif
                if property.format == MPV_FORMAT_DOUBLE, let value = property.data {
                    link[name] = value.assumingMemoryBound(to: Double.self).pointee
                } else if property.format == MPV_FORMAT_FLAG, let value = property.data {
                    link[name] = value.assumingMemoryBound(to: Int32.self).pointee != 0 ? 1 : 0
                }
                continue
            }
            if event.event_id == MPV_EVENT_FILE_LOADED {
                // Asked from here, not from the main thread: see `Link`.
                let tracks = MPVPlayback.tracks(of: handle)
                Task { @MainActor [weak self] in self?.onTracks?(tracks) }
                continue
            }
            guard event.event_id == MPV_EVENT_END_FILE, let data = event.data else { continue }
            let reason = data.assumingMemoryBound(to: mpv_event_end_file.self).pointee.reason
            Task { @MainActor [weak self] in
                if reason == MPV_END_FILE_REASON_EOF { self?.onEnd?() }
                if reason == MPV_END_FILE_REASON_ERROR { self?.onFail?() }
            }
        }
    }

    /// The sound and subtitle tracks of the open file, named for a menu.
    private nonisolated static func tracks(of handle: OpaquePointer) -> [Track] {
        func text(_ name: String) -> String? {
            guard let raw = mpv_get_property_string(handle, name) else { return nil }
            defer { mpv_free(raw) }
            let value = String(cString: raw)
            return value.isEmpty ? nil : value
        }
        var count: Int64 = 0
        mpv_get_property(handle, "track-list/count", MPV_FORMAT_INT64, &count)
        return (0..<Int(count)).compactMap { index in
            let base = "track-list/\(index)/"
            guard let kind = text(base + "type"), kind == "audio" || kind == "sub", let id = text(base + "id").flatMap(Int.init) else { return nil }
            let language = text(base + "lang").map { Locale.current.localizedString(forLanguageCode: $0) ?? $0 }
            let parts = [language, text(base + "title"), text(base + "codec")?.uppercased()].compactMap { $0 }
            return Track(id: id, subtitle: kind == "sub", name: parts.isEmpty ? "\(id)" : parts.joined(separator: " · "))
        }
    }

    /// Plays the sound track with this id.
    func selectAudio(_ id: Int) { set("aid", String(id)) }

    /// Has mpv draw one of the file's own subtitle tracks, or none when the app draws its own.
    func selectSubtitle(_ id: Int?) { set("sid", id.map(String.init) ?? "no") }

    private func set(_ name: String, _ value: String) {
        command(["set", name, value])
    }

    /// Hands mpv a command without waiting for it to be carried out.
    private func command(_ words: [String]) {
        guard let mpv else { return }
        var pointers: [UnsafePointer<CChar>?] = words.map { UnsafePointer(strdup($0)) }
        pointers.append(nil)
        mpv_command_async(mpv, 0, &pointers)
        for pointer in pointers { free(UnsafeMutablePointer(mutating: pointer)) }
    }

    var position: Double { link["time-pos"] }
    var duration: Double { link["duration"] }
    var waiting: Bool { link["paused-for-cache"] > 0 || link["seeking"] > 0 }
    var aspect: Double { link["video-params/aspect"] }


    func load(_ url: URL, at position: Double?) {
        guard mpv != nil else {
            onFail?()
            return
        }
        link.forget()
        var words = ["loadfile", url.isFileURL ? url.path : url.absoluteString, "replace", "-1"]
        if let position, position > 0 { words.append("start=\(Int(position))") }
        command(words)
    }

    func play() { set("pause", "no") }
    func pause() { set("pause", "yes") }
    func seek(to seconds: Double) { command(["seek", String(seconds), "absolute"]) }
    func setSpeed(_ speed: Float, playing: Bool) { set("speed", String(speed)) }
    func setVolume(_ volume: Float) { set("volume", String(Int(volume * 100))) }
    func setMuted(_ muted: Bool) { set("mute", muted ? "yes" : "no") }

    func stop() {
        clock?.invalidate()
        clock = nil
        link.wake = nil
        link.handle = nil
        guard let handle = mpv else { return }
        mpv = nil
        mpv_set_wakeup_callback(handle, nil, nil)
        // Shutting mpv down waits for its threads; that wait is not for the main thread. It is
        // queued behind any reading of events still under way.
        nonisolated(unsafe) let ending = handle
        events.async { mpv_terminate_destroy(ending) }
    }
}

/// A Metal layer with two workarounds mpv's Vulkan path needs on a Mac.
final class MetalLayer: CAMetalLayer {
    /// MoltenVK sets the size to 1×1 to force a presentation through, which flickers and can stick.
    override var drawableSize: CGSize {
        get { super.drawableSize }
        set {
            if Int(newValue.width) > 1, Int(newValue.height) > 1 { super.drawableSize = newValue }
        }
    }

    /// Extended range only takes effect when it is switched on from the main thread.
    override var wantsExtendedDynamicRangeContent: Bool {
        get { super.wantsExtendedDynamicRangeContent }
        set {
            if Thread.isMainThread {
                super.wantsExtendedDynamicRangeContent = newValue
            } else {
                DispatchQueue.main.sync { super.wantsExtendedDynamicRangeContent = newValue }
            }
        }
    }
}

/// The view mpv's layer lives in.
struct MPVSurface: NSViewRepresentable {
    let playback: MPVPlayback
    let onWindow: (NSWindow?) -> Void

    func makeNSView(context: Context) -> Host {
        let view = Host()
        view.metal = playback.layer
        view.layer = playback.layer
        view.wantsLayer = true
        view.onWindow = onWindow
        return view
    }

    func updateNSView(_ view: Host, context: Context) {}

    final class Host: NSView {
        var metal: MetalLayer?
        var onWindow: ((NSWindow?) -> Void)?

        override func layout() {
            super.layout()
            guard let metal else { return }
            let scale = window?.screen?.backingScaleFactor ?? NSScreen.main?.backingScaleFactor ?? 2
            metal.frame = bounds
            metal.contentsScale = scale
            metal.drawableSize = CGSize(width: bounds.width * scale, height: bounds.height * scale)
        }

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            onWindow?(window)
        }
    }
}
