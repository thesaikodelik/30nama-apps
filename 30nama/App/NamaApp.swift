import SwiftUI

@main
struct NamaApp: App {
    @State private var app = AppModel()

    init() {
        AppFont.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(app)
        }
    }
}

/// Where a card or button leads.
enum Route: Hashable {
    case details(PostRef)
    case catalog(slug: String, title: String)
    /// An actor's biography and titles, from the 30nama website's own API.
    case person(Person)
    case schedule
    case topLists
}

/// A title to open. Carries the list entry so the page has something to show while it loads.
struct PostRef: Hashable {
    let id: Int
    var preview: Post?
    /// Start playback as soon as the stream is known (the hero "Play" button).
    var autoplay = false

    init(_ post: Post, autoplay: Bool = false) {
        id = post.id
        preview = post
        self.autoplay = autoplay
    }
}

extension View {
    func withRoutes() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .details(let ref): DetailsView(ref: ref)
            case .catalog(let slug, let title): CatalogView(slug: slug, title: title)
            case .person(let person): PersonView(person: person)
            case .schedule: ScheduleView()
            case .topLists: TopListsView()
            }
        }
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.scenePhase) private var scenePhase
    /// The opening title has run its course.
    @State private var introPlayed = false
    /// The opening title has finished leaving and is out of the hierarchy.
    @State private var splashGone = false

    /// The title leaves once it has played and there is something to show behind it.
    private var splashLeaving: Bool { introPlayed && app.phase != .booting }

    var body: some View {
        @Bindable var app = app
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
                MainView()
            }
            }
            // Seen through the opening title's glass: softened, but still itself, and coming
            // into focus as the glass parts.
            .blur(radius: splashLeaving || splashGone ? 0 : 6)
            // And it settles into place as it comes into focus.
            .scaleEffect(splashLeaving || splashGone ? 1 : 1.03)
            .animation(.easeOut(duration: 0.9), value: splashLeaving)
            if !splashGone {
                SplashView(leaving: splashLeaving, ready: app.homeLoaded || (app.phase != .main && app.phase != .booting))
                    .zIndex(10)
            }
        }
        .overlay(alignment: .bottom) {
            if let toast = app.toast {
                Toast(text: toast)
                    .padding(.bottom, 70)
                    .transition(.opacity.combined(with: .offset(y: 16)))
            }
        }
        .animation(.easeOut(duration: 0.4), value: app.phase)
        .onChange(of: splashLeaving) { _, leaving in
            guard leaving else { return }
            Task {
                try? await Task.sleep(for: .seconds(SplashView.exitDuration))
                splashGone = true
            }
        }
        .animation(.easeOut(duration: 0.24), value: app.toast)
        .environment(\.layoutDirection, app.settings.lang.isRTL ? .rightToLeft : .leftToRight)
        .environment(\.loc, app.loc)
        .environment(\.calm, app.settings.reduceMotion)
        .preferredColorScheme(.dark)
        .fullScreenCover(item: $app.playback, onDismiss: { app.playerDidClose() }) { request in
            PlayerScreen(request: request)
                .ignoresSafeArea()
                .environment(app)
        }
        .task { await app.boot() }
        .onChange(of: scenePhase) { _, phase in
            // Coming back to the app: pick up what was rated on another Apple TV meanwhile.
            if phase == .active, app.phase == .main { app.syncRatings(unlessFresherThan: 60) }
        }
        .task {
            // The opening title runs its course once; after that it only waits on the network.
            try? await Task.sleep(for: .seconds(2.8))
            // Then a little longer, at most, for the home page to arrive behind the glass.
            let deadline = Date().addingTimeInterval(2.5)
            while !app.homeLoaded, app.phase == .main || app.phase == .booting, Date() < deadline {
                try? await Task.sleep(for: .milliseconds(100))
            }
            introPlayed = true
        }
    }
}

struct MainView: View {
    // Search first, as most apps have it; the user's own page last, by settings.
    enum Tab: Hashable { case search, home, movies, series, anime, browse, profile, settings }

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc
    @State private var tab = Tab.home

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { SearchView().withRoutes() }
                .tabItem { iconTab(loc(.search), "magnifyingglass", titled: roomy) }
                .tag(Tab.search)
            NavigationStack { HomeView().withRoutes() }
                .tabItem { Text(loc(.home)) }
                .tag(Tab.home)
            NavigationStack { CatalogView(slug: "movies", title: loc(.movies)).withRoutes() }
                .tabItem { Text(loc(.movies)) }
                .tag(Tab.movies)
            NavigationStack { CatalogView(slug: "series", title: loc(.series)).withRoutes() }
                .tabItem { Text(loc(.series)) }
                .tag(Tab.series)
            NavigationStack { CatalogView(slug: "anime", title: loc(.anime)).withRoutes() }
                .tabItem { Text(loc(.anime)) }
                .tag(Tab.anime)
            NavigationStack { BrowseView().withRoutes() }
                .tabItem { Text(loc(.browse)) }
                .tag(Tab.browse)
            NavigationStack { ProfileView().withRoutes() }
                .tabItem { iconTab(loc(.profile), "person.crop.circle") }
                .tag(Tab.profile)
            NavigationStack { SettingsView() }
                .tabItem { iconTab(loc(.settings), "gearshape", titled: roomy) }
                .tag(Tab.settings)
        }
        // A new language or account means a different catalogue: rebuild every tab.
        .id(app.contentVersion)
        // Home's rows of films, series and anime end in a card that leads to the whole tab.
        .environment(\.openTab) { key in
            switch key {
            case "movies": tab = .movies
            case "series": tab = .series
            case "anime": tab = .anime
            default: break
            }
        }
    }

    /// Persian titles are short enough for all eight tabs to fit the bar. English and Arabic ones
    /// are not, so there search and settings go by their icons alone, as is usual on tvOS.
    private var roomy: Bool { loc.lang == .fa }

    /// A tab with an icon. The tab bar always puts the icon to the left of the title, whatever the
    /// reading direction, so in a right-to-left language the two are drawn as one image with the
    /// icon leading.
    @ViewBuilder
    private func iconTab(_ title: String, _ symbol: String, titled: Bool = true) -> some View {
        if !titled {
            Image(systemName: symbol).accessibilityLabel(title)
        } else if loc.lang.isRTL, let image = MainView.tabImage(title, symbol) {
            Image(uiImage: image)
        } else {
            Label(title, systemImage: symbol)
        }
    }

    /// Title and icon as the tab bar itself would draw them, in right-to-left order.
    private static func tabImage(_ title: String, _ symbol: String) -> UIImage? {
        let font = UIFont.systemFont(ofSize: 25, weight: .bold)
        guard let icon = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(font: font, scale: .large)) else { return nil }
        let text = NSAttributedString(string: title, attributes: [.font: font, .foregroundColor: UIColor.white])
        let textSize = text.size()
        let size = CGSize(width: ceil(textSize.width) + 8 + icon.size.width, height: max(ceil(textSize.height), icon.size.height))
        let image = UIGraphicsImageRenderer(size: size).image { _ in
            text.draw(at: CGPoint(x: 0, y: (size.height - textSize.height) / 2))
            icon.withTintColor(.white).draw(at: CGPoint(x: size.width - icon.size.width, y: (size.height - icon.size.height) / 2))
        }.withRenderingMode(.alwaysTemplate)
        image.accessibilityLabel = title
        return image
    }
}

private struct OpenTabKey: EnvironmentKey {
    static let defaultValue: (String) -> Void = { _ in }
}

extension EnvironmentValues {
    /// Moves to the tab for a section of the catalogue: "movies", "series" or "anime".
    var openTab: (String) -> Void {
        get { self[OpenTabKey.self] }
        set { self[OpenTabKey.self] = newValue }
    }
}
