import SwiftUI

@main
struct NamaMacApp: App {
    @State private var app = AppModel()

    init() {
        AppFont.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
                .frame(minWidth: 1120, minHeight: 720)
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
            switch app.phase {
            case .booting:
                Opening()
            case .failed:
                ErrorState(message: "\(app.loc(.noConnection)). \(app.loc(.tryAgain))") {
                    Task { await app.boot() }
                }
            case .login:
                LoginView()
            case .main:
                MainView()
            }
            if let request = app.playback {
                PlayerView(request: request)
                    .id(request.id)
                    .transition(.opacity)
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
        .animation(.easeOut(duration: 0.3), value: app.playback?.id)
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
    }
}

/// What is on screen while the app finds its server: the logo, with light gathering behind it.
private struct Opening: View {
    @State private var lit = false

    var body: some View {
        ZStack {
            RadialGradient(colors: [Theme.accent.opacity(lit ? 0.32 : 0), .clear], center: .center, startRadius: 0, endRadius: 520)
            Image("Logo")
                .resizable()
                .scaledToFit()
                .frame(height: 96)
                .opacity(lit ? 1 : 0)
                .blur(radius: lit ? 0 : 14)
                .scaleEffect(lit ? 1 : 0.94)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.9)) { lit = true }
        }
    }
}
