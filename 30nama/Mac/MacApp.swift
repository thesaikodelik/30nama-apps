import SwiftUI

@main
struct NamaMacApp: App {
    @State private var app: AppModel
    @State private var downloads: Downloads
    @State private var updater = Updater()

    init() {
        let model = AppModel()
        _app = State(initialValue: model)
        _downloads = State(initialValue: Downloads(app: model))
        AppFont.register()
        #if DEBUG
        DebugLaunch.probeIfAsked()
        DebugLaunch.fetchIfAsked()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .environment(downloads)
                .environment(updater)
                .frame(minWidth: 1120, minHeight: 720)
                // Every hour while open, and when the app comes to the front after an hour or more:
                // a release could otherwise wait half a day to be seen. The first look waits for the
                // opening title to be over, so the news is seen arriving.
                .task {
                    try? await Task.sleep(for: .seconds(8))
                    while !Task.isCancelled {
                        await updater.look()
                        try? await Task.sleep(for: .seconds(3600))
                    }
                }
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.didBecomeActiveNotification)) { _ in
                    Task { await updater.look() }
                }
                // The account's messages, a little after the home page's requests, then every quarter
                // of an hour; asked again at once when someone signs in or out.
                .task(id: app.user?.id) {
                    try? await Task.sleep(for: .seconds(10))
                    while !Task.isCancelled {
                        await app.refreshNotifications()
                        try? await Task.sleep(for: .seconds(900))
                    }
                }
                // A minute in, once the app is signed in and the home page has had its requests.
                .task {
                    try? await Task.sleep(for: .seconds(60))
                    while !Task.isCancelled {
                        await EpisodeWatch.shared.look(app)
                        try? await Task.sleep(for: .seconds(3600))
                    }
                }
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 1440, height: 900)
        .commands {
            // One window is the whole app; a second would only share its state confusingly.
            CommandGroup(replacing: .newItem) {}
        }
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The opening title has run its course.
    @State private var introPlayed = false
    /// The window is on screen, so the opening title can begin.
    @State private var onScreen = false
    /// The opening title has finished leaving and is out of the hierarchy.
    @State private var splashGone = false

    /// The title leaves once it has played and there is something to show behind it.
    private var splashLeaving: Bool { introPlayed && app.phase != .booting }

    /// Whether ambient motion may run without end. The user can say; left to itself the app gives
    /// it to a Mac that can afford it, and never while the Mac is saving power.
    private var lively: Bool {
        guard !reduceMotion else { return false }
        switch app.settings.effects {
        case .full: return true
        case .light: return false
        case .auto: return Machine.isFast && !ProcessInfo.processInfo.isLowPowerModeEnabled
        }
    }

    var body: some View {
        ZStack {
            Theme.bg0.ignoresSafeArea()
            Group {
            switch app.phase {
            case .booting:
                // Only when retrying after a failed start: the first start is under the opening title,
                // whose glass would show a spinner through it.
                if splashGone { ProgressView() }
            case .failed:
                ErrorState(message: "\(app.loc(.noConnection)). \(app.loc(.tryAgain))") {
                    Task { await app.boot() }
                }
            case .login:
                LoginView()
            case .main:
                // Going to the film, the page draws nearer and goes dark, like a room's lights
                // going down; it comes back the same way.
                MainView()
                    .scaleEffect(app.playback != nil ? 1.08 : 1)
                    .opacity(app.playback != nil ? 0 : 1)
            }
            // The same opening title as on the Apple TV.
            }
            // Seen through the opening title's glass: softened, but still itself, and coming
            // into focus as the glass parts.
            .blur(radius: splashLeaving || splashGone ? 0 : 6)
            // And it settles into place as it comes into focus.
            .scaleEffect(splashLeaving || splashGone ? 1 : 1.03)
            .animation(.easeOut(duration: 0.9), value: splashLeaving)
            if !splashGone {
                SplashView(leaving: splashLeaving, ready: app.homeLoaded || (app.phase != .main && app.phase != .booting), start: onScreen)
                    .zIndex(10)
            }
            if let request = app.playback {
                PlayerView(request: request)
                    .id(request.id)
                    .transition(.asymmetric(insertion: .opacity.animation(.easeIn(duration: 0.45).delay(0.2)), removal: .opacity))
                    .zIndex(5)
            }
        }
        .overlay(alignment: .bottom) {
            if let toast = app.toast {
                Toast(text: toast)
                    .padding(.bottom, 36)
                    .transition(.opacity.combined(with: .offset(y: 12)))
            }
        }
        .animation(.easeOut(duration: 0.4), value: app.phase)
        .animation(.easeOut(duration: 0.24), value: app.toast)
        .animation(.easeInOut(duration: 0.55), value: app.playback?.id)
        .onChange(of: app.playback?.id) { old, new in
            // The player has gone: what was watched may have changed "continue watching".
            if old != nil, new == nil { app.playerDidClose() }
        }
        .environment(\.layoutDirection, app.settings.lang.isRTL ? .rightToLeft : .leftToRight)
        .environment(\.loc, app.loc)
        .environment(\.calm, reduceMotion)
        .environment(\.lively, lively)
        .preferredColorScheme(.dark)
        .ignoresSafeArea()
        .task { await app.boot() }
        .onChange(of: scenePhase) { _, phase in
            // Back in front: pick up what was rated on another device meanwhile.
            if phase == .active, app.phase == .main { app.syncRatings(unlessFresherThan: 60) }
        }
        .task {
            // The title is timed from when the window is really on screen, not from when the app
            // started: the window can take a second or more to appear.
            while !NSApp.windows.contains(where: { $0.isVisible && $0.occlusionState.contains(.visible) }) {
                try? await Task.sleep(for: .milliseconds(30))
            }
            onScreen = true
            // The opening title runs its course once; after that it only waits on the network.
            try? await Task.sleep(for: .seconds(2.8))
            // Then a little longer, at most, for the home page to arrive behind the glass.
            let deadline = Date().addingTimeInterval(2.5)
            while !app.homeLoaded, app.phase == .main || app.phase == .booting, Date() < deadline {
                try? await Task.sleep(for: .milliseconds(100))
            }
            introPlayed = true
        }
        .onChange(of: splashLeaving) { _, leaving in
            guard leaving else { return }
            Task {
                try? await Task.sleep(for: .seconds(SplashView.exitDuration))
                splashGone = true
            }
        }
    }
}

