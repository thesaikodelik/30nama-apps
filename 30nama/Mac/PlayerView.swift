import SwiftUI
import AVKit

/// The picture, filling the window, with the app's own controls over it.
///
/// The controls fade when the pointer rests, as do the pointer and the window's buttons. The
/// keyboard does everything they do: space, the arrows, F, M, P, T, N, G and H, [ and ].
struct PlayerView: View {
    let request: PlaybackRequest

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc
    @Environment(\.layoutDirection) private var direction

    @State private var engine: PlayerEngine?
    /// The setting for light around the picture instead of black bars.
    @AppStorage("ambientLight") private var ambientLight = true
    @State private var controls = true
    @State private var episodes = false
    /// The list of keyboard shortcuts is open.
    @State private var keys = false
    @State private var hideTask: Task<Void, Never>?
    @State private var keyMonitor: Any?
    @FocusState private var focused: Bool

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black
                if let engine {
                    surface(engine)
                        .contentShape(Rectangle())
                        .onTapGesture(count: 2) { engine.toggleFullScreen() }
                        .onTapGesture {
                            if episodes { episodes = false } else { engine.toggle() }
                            wake()
                        }
                    if ambientLight, engine.playback is MPVPlayback {
                        AmbientLight(aspect: engine.aspect, size: geo.size)
                    }
                    // Until the picture moves, the title's own artwork stands in for it, so the
                    // page the user came from dissolves into the film rather than into black.
                    if !engine.started {
                        Curtain(post: request.post, line: curtainLine(engine))
                            .transition(.opacity.animation(.easeInOut(duration: 0.9)))
                    } else if engine.buffering {
                        ProgressView().controlSize(.large).tint(.white)
                    }
                    // No lines over the opening card: the film is not on screen to say them.
                    if engine.started { subtitle(engine, height: geo.size.height) }
                    // Out of the hierarchy while hidden, so that nothing is redrawn as the clock runs.
                    if controls {
                        chrome(engine).transition(.opacity)
                    }
                    if engine.pendingSkip != 0 {
                        SkipPill(seconds: engine.pendingSkip)
                            .transition(.scale(scale: 0.9).combined(with: .opacity))
                    }
                    // Above the controls' shading, so the offer stays bright and takes its own clicks.
                    OfferPill(engine: engine)
                        .padding(.bottom, controls ? 150 : 70)
                        .padding(.horizontal, Mac.edge)
                        .animation(Theme.glide, value: controls)
                    if keys {
                        ShortcutSheet { keys = false }
                            .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    }
                    if episodes {
                        EpisodePanel(engine: engine) { episodes = false }
                            // From the right in every language, like the button that opens it.
                            .transition(.move(edge: direction == .rightToLeft ? .leading : .trailing).combined(with: .opacity))
                    }
                }
            }
        }
        .ignoresSafeArea()
        .environment(\.hue, SceneTint.shared.known(for: request.post))
        .focusable()
        .focusEffectDisabled()
        .focused($focused)
        .onKeyPress(phases: .down) { press in handle(press) }
        .onExitCommand { leave() }
        .onContinuousHover { _ in wake() }
        .dropDestination(for: URL.self) { urls, _ in
            guard let url = urls.first else { return false }
            engine?.loadSubtitleFile(url)
            return true
        }
        .animation(.easeOut(duration: 0.25), value: controls)
        .animation(Theme.glide, value: episodes)
        .animation(Theme.glide, value: keys)
        .animation(.easeOut(duration: 0.18), value: engine?.pendingSkip != 0)
        .onAppear {
            let engine = PlayerEngine(request: request, app: app)
            self.engine = engine
            engine.begin()
            focused = true
            wake()
            // Letter keys are taken here, by where they sit on the keyboard: with a Persian or
            // Arabic layout the F key types ب, and SwiftUI only offers the letter typed.
            keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
                // The side arrows here too: SwiftUI hands on only some of a quick run of presses,
                // and a run is what is added up into one jump.
                if !episodes, !keys, event.modifierFlags.isDisjoint(with: [.command, .control, .option]),
                   event.keyCode == 123 || event.keyCode == 124 {
                    wake()
                    engine.skip(event.keyCode == 124 ? 10 : -10)
                    return nil
                }
                guard event.modifierFlags.isDisjoint(with: [.command, .control, .option]),
                      let letter = PlayerView.keyLetters[event.keyCode], press(letter) else { return event }
                return nil
            }
        }
        .onDisappear {
            if let keyMonitor { NSEvent.removeMonitor(keyMonitor) }
            hideTask?.cancel()
            engine?.shutdown()
            NSCursor.setHiddenUntilMouseMoves(false)
        }
    }

    /// The picture, drawn by whichever decoder the current file needs.
    @ViewBuilder
    private func surface(_ engine: PlayerEngine) -> some View {
        if let mpv = engine.playback as? MPVPlayback {
            MPVSurface(playback: mpv) { engine.attach(window: $0) }.id("mpv")
        } else if let system = engine.playback as? SystemPlayback {
            SystemSurface(playback: system, ambient: ambientLight) { engine.attach(window: $0) }.id(ObjectIdentifier(system))
        }
    }

    /// "Season 2 · Episode 5" under the title on the opening card, for a series.
    private func curtainLine(_ engine: PlayerEngine) -> String? {
        guard request.post.isSeries else { return nil }
        return loc.episodeTag(season: engine.current.season, episode: engine.current.episodeRaw ?? "\(engine.current.episodeNumber)")
    }

    // MARK: Showing and hiding

    /// Brings the controls up and lets them go again a few seconds after the pointer stops.
    private func wake() {
        if !controls { controls = true }
        hideTask?.cancel()
        #if DEBUG
        if DebugLaunch.holdControls { return }
        #endif
        hideTask = Task {
            try? await Task.sleep(for: .seconds(3))
            guard !Task.isCancelled, engine?.playing == true, !episodes, !keys else { return }
            controls = false
            NSCursor.setHiddenUntilMouseMoves(true)
        }
    }

    /// Esc leaves full screen first, and the player only after that.
    private func leave() {
        if keys {
            keys = false
        } else if episodes {
            episodes = false
        } else if NSApp.keyWindow?.styleMask.contains(.fullScreen) == true {
            engine?.toggleFullScreen()
        } else {
            engine?.close()
        }
    }

    private func handle(_ press: KeyPress) -> KeyPress.Result {
        guard let engine else { return .ignored }
        wake()
        switch press.key {
        case .space: engine.toggle()
        case .leftArrow: engine.skip(-10)
        case .rightArrow: engine.skip(10)
        case .upArrow: engine.setVolume(engine.volume + 0.1)
        case .downArrow: engine.setVolume(engine.volume - 0.1)
        // Takes the offer on screen; with none, full screen, as in other players.
        case .return: if engine.offer != nil { engine.acceptOffer() } else { engine.toggleFullScreen() }
        default: return .ignored
        }
        return .handled
    }

    /// A letter key's shortcut, by the key's place on an English keyboard. False when it has none.
    private func press(_ letter: String) -> Bool {
        guard let engine else { return false }
        wake()
        switch letter {
        case "/": keys.toggle()
        case "f": engine.toggleFullScreen()
        case "m": engine.toggleMute()
        case "p": engine.togglePictureInPicture()
        case "t": engine.togglePinned()
        case "n": if engine.hasNext { engine.start(engine.index + 1) }
        case "e": episodes.toggle()
        case "g": engine.nudgeSubtitles(-0.25)
        case "h": engine.nudgeSubtitles(0.25)
        case "[": engine.stepSpeed(-1)
        case "]": engine.stepSpeed(1)
        default: return false
        }
        return true
    }

    /// The keys with a letter shortcut, by their code: the same key whatever the keyboard layout.
    private static let keyLetters: [UInt16: String] = [3: "f", 46: "m", 35: "p", 17: "t", 45: "n", 14: "e", 5: "g", 4: "h", 33: "[", 30: "]", 44: "/"]

    private func sleepName(_ timer: PlayerEngine.SleepTimer, series: Bool) -> String {
        switch timer {
        case .off: return loc(.off)
        case .endOfEpisode: return loc(series ? .endOfEpisode : .endOfFilm)
        case .minutes(let minutes): return "\(loc.num(minutes)) \(loc(.min))"
        }
    }

    // MARK: Subtitles

    private func subtitle(_ engine: PlayerEngine, height: CGFloat) -> some View {
        // Sizes are set for a 1080-line picture; the text keeps its share of whatever the window is.
        let size = max(15, CGFloat(app.settings.subtitleSize) * height / 1080)
        let style = app.settings.subtitleBackground
        let shape = RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
        return VStack {
            Spacer(minLength: 0)
            if !engine.cue.isEmpty {
                Text(engine.cue)
                    .font(.app(size, .semibold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Color(nsColor: app.settings.subtitleColor.color))
                    .shadow(color: .black.opacity(style == .none ? 1 : 0), radius: 1.5)
                    .shadow(color: .black.opacity(style == .none ? 0.9 : 0), radius: 4, y: 1)
                    .padding(.horizontal, size * 0.5)
                    .padding(.vertical, size * 0.22)
                    .background {
                        if style.blurStrength > 0 {
                            // Never faded: a material that is made translucent stops blurring at all.
                            // The lighter plate is a thinner material instead.
                            shape.fill(style.blurStrength < 1 ? AnyShapeStyle(.ultraThinMaterial) : AnyShapeStyle(.regularMaterial))
                        } else if style.plateOpacity > 0 {
                            shape.fill(Color.black.opacity(style.plateOpacity))
                        }
                    }
                    .frame(maxWidth: 1100)
            }
        }
        .padding(.horizontal, Mac.edge)
        .padding(.bottom, controls ? 138 : 56)
        .animation(Theme.glide, value: controls)
        .allowsHitTesting(false)
    }

    // MARK: Controls

    private func chrome(_ engine: PlayerEngine) -> some View {
        VStack(spacing: 0) {
            topBar(engine)
            Spacer(minLength: 0)
            bottomBar(engine)
        }
        .background {
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.75), .clear], startPoint: .top, endPoint: .bottom).frame(height: 150)
                Spacer(minLength: 0)
                LinearGradient(colors: [.clear, .black.opacity(0.88)], startPoint: .top, endPoint: .bottom).frame(height: 230)
            }
            .allowsHitTesting(false)
        }
    }

    private func topBar(_ engine: PlayerEngine) -> some View {
        HStack(spacing: 14) {
            RoundButton(symbol: "chevron.backward", help: loc(.back), hintBelow: true) { engine.close() }
            VStack(alignment: .leading, spacing: 2) {
                Text(engine.post.title(loc.lang)).font(.app(20, .bold)).foregroundStyle(.white).lineLimit(1)
                if let line = episodeLine(engine) {
                    Text(line).font(.app(14, .medium)).foregroundStyle(.white.opacity(0.72)).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            RoundButton(symbol: engine.pinned ? "pin.fill" : "pin", help: loc(.alwaysOnTop), lit: engine.pinned, hintBelow: true) { engine.togglePinned() }
            if engine.canPictureInPicture {
                RoundButton(symbol: engine.pictureInPicture ? "pip.exit" : "pip.enter", help: loc(.pictureInPicture), hintBelow: true) { engine.togglePictureInPicture() }
            }
            RoundButton(symbol: "arrow.up.left.and.arrow.down.right", help: loc(.fullScreen), hintBelow: true) { engine.toggleFullScreen() }
        }
        .padding(.horizontal, Mac.edge - 12)
        // Clear of the window's own three buttons.
        .padding(.top, 44)
    }

    private func episodeLine(_ engine: PlayerEngine) -> String? {
        guard engine.post.isSeries else { return nil }
        let item = engine.current
        var parts = [loc.episodeTag(season: item.season, episode: item.episodeRaw ?? "\(item.episodeNumber)")]
        if let title = item.title(loc.lang) { parts.append(title) }
        return parts.joined(separator: " · ")
    }

    private func bottomBar(_ engine: PlayerEngine) -> some View {
        VStack(spacing: 6) {
            Scrubber(time: engine.time, duration: engine.duration, marks: engine.marks) { engine.seek(to: $0) }
            HStack(spacing: 6) {
                RoundButton(symbol: engine.playing ? "pause.fill" : "play.fill", help: loc(engine.playing ? .pause : .play), size: 46, glyph: 20) { engine.toggle() }
                RoundButton(symbol: "gobackward.10", help: loc(.back10)) { engine.skip(-10) }
                RoundButton(symbol: "goforward.10", help: loc(.forward10)) { engine.skip(10) }
                if engine.hasNext {
                    RoundButton(symbol: "forward.end.fill", help: loc(.nextEpisode)) { engine.start(engine.index + 1) }
                }
                RoundButton(symbol: engine.muted || engine.volume == 0 ? "speaker.slash.fill" : engine.volume < 0.5 ? "speaker.wave.1.fill" : "speaker.wave.2.fill", help: loc(.sound)) { engine.toggleMute() }
                Level(value: Double(engine.muted ? 0 : engine.volume)) { engine.setVolume(Float($0)) }
                    .frame(width: 84)
                Text("\(Fmt.clock(engine.time)) / \(Fmt.clock(engine.duration))")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(.white.opacity(0.85))
                    .padding(.leading, 10)
                Spacer(minLength: 0)
                menus(engine)
                if engine.items.count > 1 {
                    RoundButton(symbol: "list.bullet", help: loc(.episodes), lit: episodes) { episodes.toggle() }
                }
            }
        }
        // A timeline runs left to right in every language.
        .environment(\.layoutDirection, .leftToRight)
        .padding(.horizontal, Mac.edge - 12)
        .padding(.bottom, 22)
    }

    @ViewBuilder
    private func menus(_ engine: PlayerEngine) -> some View {
        // Shows the speed itself when it is not the usual one, so a step with [ or ] is seen.
        MenuButton(symbol: "gauge.with.dots.needle.67percent", help: loc(.speed), text: engine.speed == 1 ? nil : PlayerEngine.name(of: engine.speed)) {
            Picker(loc(.speed), selection: Binding(get: { engine.speed }, set: { engine.setSpeed($0) })) {
                // A speed reached with the keys is listed too, so the menu still marks it.
                ForEach(Set(PlayerEngine.speeds + [engine.speed]).sorted(), id: \.self) { speed in
                    Text(PlayerEngine.name(of: speed)).tag(speed)
                }
            }
            .pickerStyle(.inline)
        }
        MenuButton(symbol: engine.subtitleLanguage == "off" ? "captions.bubble" : "captions.bubble.fill", help: loc(.subtitles)) {
            Picker(loc(.subtitles), selection: Binding(get: { engine.subtitleLanguage }, set: { engine.selectSubtitle($0, remember: true) })) {
                Text(loc(.off)).tag("off")
                ForEach(engine.subtitleLanguages, id: \.self) { language in
                    Text(loc(language == "fa" ? .persian : .english)).tag(language)
                }
                if let name = engine.subtitleFileName {
                    Text(name).tag(PlayerEngine.fileSubtitle)
                }
                // The subtitle tracks the file itself carries.
                ForEach(engine.tracks.filter(\.subtitle)) { track in
                    Text(track.name).tag(PlayerEngine.inFile + String(track.id))
                }
            }
            .pickerStyle(.inline)
            // 30nama has no Persian subtitle for this one: those found on SubSource, best first.
            if engine.findingSubtitles {
                Text(loc(.findingSubtitles))
            } else if !engine.foundSubtitles.isEmpty {
                Picker(loc(.fromSubSource), selection: Binding(get: { engine.subtitleLanguage }, set: { engine.selectSubtitle($0, remember: true) })) {
                    ForEach(Array(engine.foundSubtitles.enumerated()), id: \.element.id) { place, candidate in
                        // The same release name twice gets a number, so the two can be told apart.
                        let earlier = engine.foundSubtitles.prefix(place).filter { $0.name == candidate.name }.count
                        Text(earlier > 0 ? "\(candidate.name) (\(loc.num(earlier + 1)))" : candidate.name).tag(PlayerEngine.found + candidate.id)
                    }
                }
                .pickerStyle(.inline)
            }
            Button(loc(.subtitleFile)) { engine.chooseSubtitleFile() }
            Divider()
            Section("\(loc(.subtitleTiming)): \(engine.subtitleOffset.formatted(.number.sign(strategy: .always(includingZero: false)).precision(.fractionLength(2))))s") {
                Button("+0.25s  (H)") { engine.nudgeSubtitles(0.25) }
                Button("−0.25s  (G)") { engine.nudgeSubtitles(-0.25) }
                Button(loc(.reset)) { engine.nudgeSubtitles(0) }
            }
        }
        // A stream can go to a TV or speaker on the network; a file mpv plays cannot.
        if let system = engine.playback as? SystemPlayback {
            AirPlayButton(player: system.player, help: loc(.airPlay))
        }
        // The rest, wanted now and then, behind one button. It shows the moon while a sleep
        // timer is running, so a timer is not forgotten behind it.
        MenuButton(symbol: engine.sleep == .off ? "gearshape" : "moon.zzz.fill", help: loc(.settings)) {
            if engine.current.qualities.count > 1 {
                SwiftUI.Menu(loc(.quality)) {
                    Picker(loc(.quality), selection: Binding(get: { engine.quality }, set: { if let quality = $0 { engine.selectQuality(quality) } })) {
                        ForEach(engine.current.qualities, id: \.self) { option in
                            Text(option.isAuto ? loc(.qualityAuto) : option.label).tag(Optional(option))
                        }
                    }
                    .pickerStyle(.inline)
                }
            }
            let sounds = engine.tracks.filter { !$0.subtitle }
            if sounds.count > 1 {
                SwiftUI.Menu(loc(.audio)) {
                    Picker(loc(.audio), selection: Binding(get: { engine.audioTrack ?? sounds[0].id }, set: { engine.selectAudioTrack($0) })) {
                        ForEach(sounds) { track in Text(track.name).tag(track.id) }
                    }
                    .pickerStyle(.inline)
                }
            } else if let quality = engine.quality, quality.mixes.count > 1 {
                SwiftUI.Menu(loc(.audio)) {
                    Picker(loc(.audio), selection: Binding(get: { engine.mix }, set: { engine.selectMix($0) })) {
                        ForEach(quality.mixes, id: \.self) { mix in
                            Text(loc(mix == .stereo ? .audioStereo : mix == .surround ? .audioSurround : .audioOriginal)).tag(mix)
                        }
                    }
                    .pickerStyle(.inline)
                }
            }
            SwiftUI.Menu(loc(.sleepTimer)) {
                Picker(loc(.sleepTimer), selection: Binding(get: { engine.sleep }, set: { engine.setSleep($0) })) {
                    ForEach(PlayerEngine.SleepTimer.choices, id: \.self) { choice in
                        Text(sleepName(choice, series: engine.post.isSeries)).tag(choice)
                    }
                }
                .pickerStyle(.inline)
            }
            if !PlayerEngine.otherPlayers.isEmpty {
                Divider()
                ForEach(PlayerEngine.otherPlayers, id: \.name) { other in
                    Button("\(loc(.openIn)) \(other.name)") { engine.open(in: other.app) }
                }
            }
            Divider()
            Button("\(loc(.shortcuts))  (?)") { keys = true }
        }
    }
}

// MARK: - Controls

private struct RoundButton: View {
    let symbol: String
    var help: String? = nil
    var size: CGFloat = 38
    var glyph: CGFloat = 15
    /// Drawn filled in: the thing it switches is on.
    var lit = false
    /// Its name shows below it, for a button at the top of the window.
    var hintBelow = false
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: glyph, weight: .semibold))
                .foregroundStyle(lit ? Theme.focusInk : .white)
                .frame(width: size, height: size)
                .background(Circle().fill(lit ? Color.white : Color.white.opacity(hovering ? 0.22 : 0)))
                .contentShape(Circle())
                .scaleEffect(hovering ? 1.08 : 1)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
        .modifier(HoverHint(text: help ?? "", hovering: hovering, below: hintBelow))
        // Read out by name, not by the picture on it.
        .accessibilityLabel(help ?? "")
        .accessibilityAddTraits(lit ? .isSelected : [])
    }
}

private struct MenuButton<Content: View>: View {
    let symbol: String
    let help: String
    /// Shown in place of the symbol.
    var text: String? = nil
    @ViewBuilder var content: Content

    @State private var hovering = false

    var body: some View {
        SwiftUI.Menu {
            content
        } label: {
            Group {
                if let text {
                    Text(text).font(.system(size: 13, weight: .bold).monospacedDigit()).padding(.horizontal, 8)
                } else {
                    Image(systemName: symbol).font(.system(size: 15, weight: .semibold)).frame(width: 38)
                }
            }
            .foregroundStyle(.white)
            .frame(minWidth: 38, minHeight: 38)
            .background(Capsule().fill(Color.white.opacity(hovering ? 0.22 : 0)))
            .contentShape(Capsule())
        }
        .menuStyle(.button)
        .buttonStyle(.plain)
        .menuIndicator(.hidden)
        .fixedSize()
        .onHover { hovering = $0 }
        .modifier(HoverHint(text: help, hovering: hovering, below: false))
        .accessibilityLabel(help)
    }
}

/// A button's name, shown after the pointer has rested on it a moment. The system's tooltip
/// is slow to come and, over a player that redraws its clock several times a second, often
/// never comes at all.
private struct HoverHint: ViewModifier {
    let text: String
    let hovering: Bool
    let below: Bool

    @State private var shown = false

    func body(content: Content) -> some View {
        content
            .overlay(alignment: below ? .bottom : .top) {
                if shown, !text.isEmpty {
                    Text(text)
                        .font(.app(12, .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.black.opacity(0.55), in: Capsule())
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().strokeBorder(Color.white.opacity(0.18), lineWidth: 1))
                        .fixedSize()
                        .offset(y: below ? 34 : -34)
                        .transition(.opacity)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
            .zIndex(shown ? 1 : 0)
            .animation(.easeOut(duration: 0.15), value: shown)
            .task(id: hovering) {
                guard hovering else { return shown = false }
                try? await Task.sleep(for: .milliseconds(450))
                if !Task.isCancelled { shown = true }
            }
    }
}

/// The light around the picture for a file mpv plays: mpv draws straight to the screen and
/// keeps no copy to fill the bars with, so they glow in the title's own colour instead,
/// brightest against the picture.
private struct AmbientLight: View {
    let aspect: Double
    let size: CGSize

    @Environment(\.hue) private var hue

    var body: some View {
        if let hue, aspect > 0, size.width > 0, size.height > 0 {
            let wide = aspect > size.width / size.height
            let bar = wide ? (size.height - size.width / aspect) / 2 : 0
            let flank = wide ? 0 : (size.width - size.height * aspect) / 2
            Group {
                if bar >= 10 {
                    VStack(spacing: 0) {
                        LinearGradient(colors: [hue.opacity(0.08), hue.opacity(0.4)], startPoint: .top, endPoint: .bottom).frame(height: bar)
                        Spacer(minLength: 0)
                        LinearGradient(colors: [hue.opacity(0.4), hue.opacity(0.08)], startPoint: .top, endPoint: .bottom).frame(height: bar)
                    }
                } else if flank >= 10 {
                    HStack(spacing: 0) {
                        LinearGradient(colors: [hue.opacity(0.08), hue.opacity(0.4)], startPoint: .leading, endPoint: .trailing).frame(width: flank)
                        Spacer(minLength: 0)
                        LinearGradient(colors: [hue.opacity(0.4), hue.opacity(0.08)], startPoint: .leading, endPoint: .trailing).frame(width: flank)
                    }
                }
            }
            .environment(\.layoutDirection, .leftToRight)
            .allowsHitTesting(false)
        }
    }
}

/// The timeline: click or drag anywhere on it, and the time under the pointer shows above.
private struct Scrubber: View {
    let time: Double
    let duration: Double
    /// The named stretches of the file, drawn on the line and named under the pointer.
    var marks: [PlayerEngine.Mark] = []
    let seek: (Double) -> Void

    @Environment(\.hue) private var hue
    @Environment(\.loc) private var loc
    @State private var drag: Double?
    @State private var hover: Double?

    var body: some View {
        GeometryReader { geo in
            let width = max(1, geo.size.width)
            let fraction = drag ?? (duration > 0 ? min(1, max(0, time / duration)) : 0)
            let active = drag != nil || hover != nil
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.24)).frame(height: active ? 6 : 4)
                Capsule().fill(hue ?? Theme.accent).frame(width: width * fraction, height: active ? 6 : 4)
                if duration > 0 {
                    ForEach(marks, id: \.self) { mark in
                        Capsule().fill(Color.white.opacity(0.55))
                            .frame(width: max(4, width * (mark.to - mark.from) / duration), height: active ? 6 : 4)
                            .offset(x: width * mark.from / duration)
                    }
                }
                Circle()
                    .fill(.white)
                    .frame(width: 14, height: 14)
                    .offset(x: width * fraction - 7)
                    .opacity(active ? 1 : 0)
                if let at = drag ?? hover, duration > 0 {
                    Text(label(at * duration))
                        .font(.system(size: 12, weight: .semibold).monospacedDigit())
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8)
                        .frame(height: 24)
                        .background(Capsule().fill(Color.black.opacity(0.8)))
                        .fixedSize()
                        .position(x: min(max(70, width * at), width - 70), y: -14)
                        .allowsHitTesting(false)
                }
            }
            .frame(height: 22)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { drag = min(1, max(0, $0.location.x / width)) }
                    .onEnded {
                        if duration > 0 { seek(min(1, max(0, $0.location.x / width)) * duration) }
                        drag = nil
                    }
            )
            .onContinuousHover { phase in
                switch phase {
                case .active(let point): hover = min(1, max(0, point.x / width))
                case .ended: hover = nil
                }
            }
            .animation(.easeOut(duration: 0.12), value: active)
        }
        .frame(height: 22)
    }

    /// The time under the pointer, with the name of the stretch it falls in.
    private func label(_ seconds: Double) -> String {
        let clock = Fmt.clock(seconds)
        guard let mark = marks.first(where: { seconds >= $0.from && seconds <= $0.to }) else { return clock }
        let name: String
        switch mark.kind {
        case .recap: name = loc(.recap)
        case .intro: name = loc(.intro)
        case .credits: name = loc(.credits)
        }
        return "\(name) · \(clock)"
    }
}

/// What the player shows before the picture moves: the title's own artwork, drawing slowly
/// nearer, with its name. It hands over to the film the moment the film is there.
private struct Curtain: View {
    let post: Post
    let line: String?

    @Environment(\.loc) private var loc
    @Environment(\.calm) private var calm
    @State private var near = false

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Color.black
            RemoteImage(url: post.cover ?? post.poster.big ?? post.poster.large, blurhash: post.poster.blurhash, maxPixel: 1920)
                .scaleEffect(near ? 1.07 : 1)
                .clipped()
            LinearGradient(colors: [.black.opacity(0.25), .black.opacity(0.8)], startPoint: .top, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 8) {
                Text(post.title(loc.lang))
                    .font(.app(44, .heavy))
                    .foregroundStyle(Theme.text1)
                    .lineLimit(2)
                    .shadow(color: .black.opacity(0.6), radius: 16, y: 6)
                HStack(spacing: 10) {
                    ProgressView().controlSize(.small).tint(.white)
                    if let line { Text(line).font(.app(17, .semibold)).foregroundStyle(Theme.text2) }
                }
            }
            .padding(.horizontal, Mac.edge)
            // Clear of the controls, which are up when the player opens.
            .padding(.bottom, 170)
        }
        .allowsHitTesting(false)
        // Once, and it ends: a push towards the screen while the film is fetched.
        .onAppear { if !calm { withAnimation(.easeOut(duration: 8)) { near = true } } }
    }
}

/// A small bar for the volume.
private struct Level: View {
    let value: Double
    let set: (Double) -> Void

    var body: some View {
        GeometryReader { geo in
            let width = max(1, geo.size.width)
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.24)).frame(height: 4)
                Capsule().fill(Color.white).frame(width: width * value, height: 4)
            }
            .frame(height: 22)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { set(min(1, max(0, $0.location.x / width))) })
        }
        .frame(height: 22)
    }
}

/// Skip the intro, skip the recap, or go on to the next episode: the red pill, as on the Apple TV.
/// Return accepts it, and so does a click.
private struct OfferPill: View {
    let engine: PlayerEngine
    @Environment(\.loc) private var loc

    var body: some View {
        VStack {
            Spacer(minLength: 0)
            HStack {
                Spacer(minLength: 0)
                if let offer = engine.offer {
                    Button { engine.acceptOffer() } label: {
                        HStack(spacing: 10) {
                            Image(systemName: offer == .next ? "play.fill" : "forward.fill").font(.system(size: 13, weight: .bold))
                            Text(loc(offer == .intro ? .skipIntro : offer == .recap ? .skipRecap : .nextEpisode)).font(.app(16, .bold))
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 22)
                        .frame(height: 46)
                        .background {
                            Capsule(style: .continuous)
                                .fill(LinearGradient(colors: [Color(hex: 0xFF6A4D), Theme.accent, Color(hex: 0xD92B58)], startPoint: .topLeading, endPoint: .bottomTrailing))
                        }
                        .overlay(alignment: .leading) {
                            // The next episode starts by itself when this has filled.
                            if let countdown = engine.countdown {
                                GeometryReader { geo in
                                    Rectangle().fill(Color.white.opacity(0.24)).frame(width: geo.size.width * countdown)
                                }
                                .clipShape(Capsule(style: .continuous))
                                .allowsHitTesting(false)
                            }
                        }
                        .shadow(color: Theme.accent.opacity(0.5), radius: 18, y: 8)
                        .contentShape(Capsule(style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .id(offer)
                    .transition(.opacity.combined(with: .offset(y: 14)))
                }
            }
        }
        .animation(Theme.focusSpring, value: engine.offer)
    }
}

/// The episodes of the series on a glass card at the side of the picture: the series' scene at
/// the head, then its seasons by name, one of them open at a time. The open season's name stays at
/// the top while its episodes scroll under it. The one playing is lit in the series' colour, the
/// ones seen ticked. However many seasons there are, only one season's episodes are ever listed.
private struct EpisodePanel: View {
    let engine: PlayerEngine
    let close: () -> Void

    @Environment(\.loc) private var loc
    @Environment(\.layoutDirection) private var direction
    /// The season whose episodes are listed; nil with all of them closed.
    @State private var open: Int?

    private var seasons: [Int] { Array(Set(engine.items.map(\.season))).sorted() }
    private func episodes(of season: Int) -> [(index: Int, item: StreamItem)] {
        engine.items.enumerated().filter { $0.element.season == season }.map { ($0.offset, $0.element) }
    }
    private static func mark(_ season: Int) -> String { "season:\(season)" }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        let titled = seasons.count > 1
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            VStack(spacing: 0) {
                header
                ScrollViewReader { reader in
                    ScrollView(.vertical) {
                        LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
                            ForEach(seasons, id: \.self) { season in
                                Section {
                                    if !titled || open == season {
                                        VStack(spacing: 4) {
                                            ForEach(episodes(of: season), id: \.item.id) { entry in
                                                EpisodeRow(item: entry.item, playing: entry.index == engine.index, progress: entry.index == engine.index && engine.duration > 0 ? engine.time / engine.duration : nil) {
                                                    engine.start(entry.index)
                                                    close()
                                                }
                                                .id(entry.item.id)
                                            }
                                        }
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .transition(.opacity.combined(with: .move(edge: .top)))
                                    }
                                } header: {
                                    if titled {
                                        SeasonHeader(season: season, count: episodes(of: season).count, open: open == season, playing: season == engine.current.season) {
                                            choose(season, reader)
                                        }
                                        .id(EpisodePanel.mark(season))
                                    }
                                }
                            }
                        }
                        .padding(.bottom, 24)
                    }
                    .scrollIndicators(.hidden)
                    // The list fades out at the foot of the card instead of being cut off there.
                    .mask {
                        LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .black, location: 0.9), .init(color: .clear, location: 1)], startPoint: .top, endPoint: .bottom)
                    }
                    .onAppear {
                        open = engine.current.season
                        // Opens on the episode playing, not on the first of its season.
                        DispatchQueue.main.async { reader.scrollTo(engine.current.id, anchor: .center) }
                    }
                }
            }
            .frame(width: 380)
            .background(Color.black.opacity(0.55), in: shape)
            .glass(shape)
            // Not clipped as a whole: a clip on the card is applied to a pinned season name on its
            // own, which turns the bar into a capsule. Only the picture at the top needs its corners.
            .shadow(color: .black.opacity(0.5), radius: 30, y: 12)
            .environment(\.layoutDirection, direction)
            .padding(.trailing, 18)
            // Clear of the window's buttons above and the controls below, which stay in reach.
            .padding(.top, 96)
            .padding(.bottom, 112)
        }
        // On the right, by the button that opens it, whatever the language; its contents keep theirs.
        .environment(\.layoutDirection, .leftToRight)
    }

    /// Closes the open season and opens this one; the open season's name closes it. Either way the
    /// list then goes to that season's name: left where it was, a list that has just grown shorter
    /// would show nothing but the space below its end.
    private func choose(_ season: Int, _ reader: ScrollViewProxy) {
        withAnimation(Theme.glide) { open = open == season ? nil : season }
        // Once the list has its new length; asked at once, it scrolls a list that is not there yet.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            withAnimation(Theme.glide) { reader.scrollTo(EpisodePanel.mark(season), anchor: .top) }
        }
    }

    /// The series' scene, fading into the card, with its name and how many seasons it has.
    private var header: some View {
        let post = engine.post
        return ZStack(alignment: .bottomLeading) {
            RemoteImage(url: post.cover ?? post.poster.big ?? post.poster.large, blurhash: post.poster.blurhash, maxPixel: 800)
                .frame(height: 118)
                .frame(maxWidth: .infinity)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24, style: .continuous))
                .overlay { LinearGradient(colors: [.black.opacity(0.1), .black.opacity(0.85)], startPoint: .top, endPoint: .bottom) }
            VStack(alignment: .leading, spacing: 3) {
                Text(post.title(loc.lang))
                    .font(.app(19, .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Text(seasons.count > 1 ? "\(loc.num(seasons.count)) \(loc(.seasonsCount)) · \(loc.num(engine.items.count)) \(loc(.episodeCount))" : "\(loc.num(engine.items.count)) \(loc(.episodeCount))")
                    .font(.app(12.5, .medium))
                    .foregroundStyle(.white.opacity(0.7))
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 12)
        }
        .frame(height: 118)
        .accessibilityElement(children: .combine)
    }
}

/// A season's name, which opens and closes its episodes. Held at the top of the list while they
/// scroll under it, on a ground solid enough that nothing shows through the words.
private struct SeasonHeader: View {
    let season: Int
    let count: Int
    let open: Bool
    /// The episode playing is in this season.
    let playing: Bool
    let action: () -> Void

    @Environment(\.loc) private var loc
    @Environment(\.hue) private var hue
    @State private var hovering = false

    var body: some View {
        Button(action: action) { bar }
            // A style of its own: the system's draws a button's ground as a capsule, and this is a full bar.
            .buttonStyle(BarButtonStyle())
            .onHover { hovering = $0 }
            .animation(Theme.glide, value: open)
            .animation(.easeOut(duration: 0.14), value: hovering)
            .accessibilityAddTraits(.isHeader)
            .accessibilityValue(open ? loc(.on) : loc(.off))
    }

    private var bar: some View {
        HStack(spacing: 8) {
            Text("\(loc(.season)) \(loc.num(season))")
                .font(.app(14.5, .bold))
                .foregroundStyle(Theme.text1)
            if playing {
                Circle().fill(hue ?? Theme.accent).frame(width: 6, height: 6)
            }
            Text("\(loc.num(count)) \(loc(.episodeCount))")
                .font(.app(12, .medium))
                .foregroundStyle(Theme.text3)
            Spacer(minLength: 0)
            Image(systemName: "chevron.down")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Theme.text2)
                .rotationEffect(.degrees(open ? 180 : 0))
        }
        .padding(.horizontal, 20)
        .frame(height: 46)
        .frame(maxWidth: .infinity)
        // A rectangle said outright: a bare colour takes the shape of what contains it.
        .background { Rectangle().fill(Color(white: open || hovering ? 0.13 : 0.1)) }
        .overlay(alignment: .bottom) { Rectangle().fill(Color.white.opacity(0.07)).frame(height: 1) }
        .contentShape(Rectangle())
    }
}

/// A button drawn as nothing but its label.
private struct BarButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.8 : 1)
    }
}

private struct EpisodeRow: View {
    let item: StreamItem
    let playing: Bool
    /// How far into it playback is, for the one playing.
    let progress: Double?
    let action: () -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc
    @Environment(\.hue) private var hue
    @State private var hovering = false

    var body: some View {
        let tint = hue ?? Theme.accent
        let seen = app.watched.contains(item.fileID)
        Button(action: action) {
            HStack(spacing: 12) {
                // The number on a tile of its own, filled in the series' colour for the one playing.
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(playing ? tint : Color.white.opacity(hovering ? 0.16 : 0.08))
                    if hovering && !playing {
                        Image(systemName: "play.fill").font(.system(size: 13, weight: .bold)).foregroundStyle(Theme.text1)
                    } else {
                        Text(loc.num(item.episodeRaw ?? "\(item.episodeNumber)"))
                            .font(.app(15, .heavy))
                            .foregroundStyle(playing ? .white : Theme.text2)
                            .minimumScaleFactor(0.6)
                    }
                }
                .frame(width: 42, height: 42)
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title(loc.lang) ?? "\(loc(.episode)) \(loc.num(item.episodeNumber))")
                        .font(.app(14, .semibold))
                        .foregroundStyle(Theme.text1)
                        .lineLimit(1)
                    HStack(spacing: 6) {
                        if playing {
                            Text(loc(.nowPlaying)).font(.app(11.5, .bold)).foregroundStyle(tint)
                        }
                        if let minutes = item.durationMinutes {
                            Text("\(loc.num(minutes)) \(loc(.min))").font(.app(11.5)).foregroundStyle(Theme.text3)
                        }
                    }
                    if let progress {
                        GeometryReader { geo in
                            Capsule().fill(Color.white.opacity(0.15))
                            Capsule().fill(tint).frame(width: max(4, geo.size.width * min(1, progress)))
                        }
                        .frame(height: 3)
                        .padding(.top, 2)
                    }
                }
                Spacer(minLength: 0)
                if seen && !playing {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(Theme.text3)
                        .accessibilityLabel(loc(.seen))
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.white.opacity(playing ? 0.1 : hovering ? 0.07 : 0)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
        .accessibilityAddTraits(playing ? .isSelected : [])
    }
}

private struct AirPlayButton: View {
    let player: AVPlayer
    let help: String

    @State private var hovering = false

    var body: some View {
        RoutePicker(player: player)
            .frame(width: 38, height: 38)
            .background(Circle().fill(Color.white.opacity(hovering ? 0.22 : 0)))
            .onHover { hovering = $0 }
            .modifier(HoverHint(text: help, hovering: hovering, below: false))
            .accessibilityLabel(help)
    }
}

/// The system's AirPlay button, drawn to sit among the player's own round ones.
private struct RoutePicker: NSViewRepresentable {
    let player: AVPlayer

    func makeNSView(context: Context) -> AVRoutePickerView {
        let view = AVRoutePickerView()
        view.player = player
        view.isRoutePickerButtonBordered = false
        view.setRoutePickerButtonColor(.white, for: .normal)
        view.setRoutePickerButtonColor(.white, for: .normalHighlighted)
        view.setRoutePickerButtonColor(NSColor(Theme.accent), for: .active)
        return view
    }

    func updateNSView(_ view: AVRoutePickerView, context: Context) {
        if view.player !== player { view.player = player }
    }
}

/// How far the arrow presses so far will jump, in the middle of the picture.
private struct SkipPill: View {
    let seconds: Double

    @Environment(\.loc) private var loc

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: seconds > 0 ? "goforward" : "gobackward")
                .font(.system(size: 22, weight: .semibold))
            // The sign stays by the number, before it, in every language.
            Text("\u{2066}\(seconds > 0 ? "+" : "−")\(loc.num(Int(abs(seconds))))\u{2069} \(loc(.secondsShort))")
                .font(.app(22, .bold))
                .monospacedDigit()
                .contentTransition(.numericText())
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 26)
        .frame(height: 64)
        .background(Color.black.opacity(0.45), in: Capsule(style: .continuous))
        .glass(Capsule(style: .continuous))
        .animation(.easeOut(duration: 0.15), value: seconds)
        .allowsHitTesting(false)
    }
}
