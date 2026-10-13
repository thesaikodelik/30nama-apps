import SwiftUI

enum MacSection: Hashable, CaseIterable {
    case profile, home, movies, series, anime, browse, search, downloads, settings

    var symbol: String {
        switch self {
        case .profile: return "person.crop.circle"
        case .home: return "house"
        case .movies: return "film"
        case .series: return "tv"
        case .anime: return "sparkles"
        case .browse: return "square.grid.2x2"
        case .search: return "magnifyingglass"
        case .downloads: return "arrow.down.circle"
        case .settings: return "gearshape"
        }
    }

    var title: S {
        switch self {
        case .profile: return .profile
        case .home: return .home
        case .movies: return .movies
        case .series: return .series
        case .anime: return .anime
        case .browse: return .browse
        case .search: return .search
        case .downloads: return .downloads
        case .settings: return .settings
        }
    }

    /// Search, downloads and settings are known by their glyphs alone; the rest carry their names.
    var iconOnly: Bool { self == .search || self == .downloads || self == .settings }
}

/// Something opened on top of a section.
enum MacRoute: Hashable {
    case title(Post)
    case catalog(slug: String, title: String)
}

/// Where the user is: a section, and what has been opened on top of it.
@MainActor
@Observable
final class MacNav {
    var section = MacSection.home
    private(set) var opened: [MacRoute] = []
    /// The title a download is being chosen for, when that pane is up.
    var picking: Post?

    func open(_ post: Post) {
        open(.title(post))
    }

    func open(_ route: MacRoute) {
        guard opened.last != route else { return }
        opened.append(route)
    }

    func back() {
        if picking != nil { picking = nil } else if !opened.isEmpty { opened.removeLast() }
    }

    func go(_ section: MacSection) {
        opened.removeAll()
        self.section = section
    }
}

struct MainView: View {
    @Environment(AppModel.self) private var app
    @State private var nav = MacNav()

    var body: some View {
        ZStack {
            page
            if let route = nav.opened.last {
                Group {
                    switch route {
                    case .title(let post): DetailsView(preview: post)
                    case .catalog(let slug, let title): CatalogView(slug: slug, title: title)
                    }
                }
                .id(route)
                .transition(.opacity.combined(with: .scale(scale: 1.02)))
                .zIndex(1)
            }
        }
        .background { shortcuts }
        .overlay(alignment: .top) {
            NavBar().padding(.top, 14).zIndex(2)
        }
        .overlay(alignment: .bottomTrailing) {
            if nav.section != .downloads || !nav.opened.isEmpty {
                DownloadsOrb().padding(24)
            }
        }
        .overlay {
            if let post = nav.picking {
                DownloadPicker(post: post) { nav.picking = nil }
                    .id(post.id)
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.22), value: nav.picking?.id)
        .animation(.easeOut(duration: 0.28), value: nav.opened.last)
        .environment(nav)
        // Esc steps back out of a title, as it closes anything else on a Mac.
        .onExitCommand { nav.back() }
        #if DEBUG
        .task { await debugLaunch() }
        #endif
    }

    #if DEBUG
    private func debugLaunch() async {
        if let name = DebugLaunch.section, let section = MacSection.allCases.first(where: { "\($0)" == name }) {
            nav.go(section)
        }
        if DebugLaunch.open == "continue" {
            if let post = await app.continueWatching().first { nav.open(post) }
            return
        }
        guard let target = DebugLaunch.open, let feed = try? await app.home() else { return }
        let pool = feed.heroes + feed.sections.flatMap(\.posts)
        if let post = target == "first" ? pool.first : pool.first(where: { String($0.id) == target }) { nav.open(post) }
    }
    #endif

    @ViewBuilder
    private var page: some View {
        switch nav.section {
        case .profile: ProfileView()
        case .home: HomeView()
        case .movies: CatalogView(slug: "movies", title: app.loc(.movies))
        case .series: CatalogView(slug: "series", title: app.loc(.series))
        case .anime: CatalogView(slug: "anime", title: app.loc(.anime))
        case .browse: BrowseView()
        case .search: SearchView()
        case .downloads: DownloadsView()
        case .settings: SettingsView()
        }
    }

    /// The keyboard's way around: ⌘1 to ⌘9 for the sections, ⌘F to search, ⌘[ to step back.
    private var shortcuts: some View {
        Group {
            ForEach(Array(MacSection.allCases.enumerated()), id: \.offset) { index, section in
                Button("") { nav.go(section) }
                    .keyboardShortcut(KeyEquivalent(Character("\(index + 1)")), modifiers: .command)
            }
            Button("") { nav.go(.search) }.keyboardShortcut("f", modifiers: .command)
            Button("") { nav.back() }.keyboardShortcut("[", modifiers: .command)
        }
        .opacity(0)
        .frame(width: 0, height: 0)
        .accessibilityHidden(true)
    }
}

/// The floating bar at the top of the window: the same glass capsule the Apple TV app has.
private struct NavBar: View {
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    var body: some View {
        // The back button has a place of its own beside the bar, kept even while it is hidden,
        // and an empty place of the same size balances it on the other side. So the bar stays
        // centred and its items do not shift when the button comes and goes.
        HStack(spacing: 10) {
            Button { nav.back() } label: {
                Image(systemName: "chevron.backward")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.text1)
                    .frame(width: 48, height: 48)
                    .glass(Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .opacity(nav.opened.isEmpty ? 0 : 1)
            .scaleEffect(nav.opened.isEmpty ? 0.8 : 1)
            .allowsHitTesting(!nav.opened.isEmpty)
            HStack(spacing: 2) {
                // Downloads are not a place in the catalogue; they have a marker of their own in the corner.
                ForEach(MacSection.allCases.filter { $0 != .downloads }, id: \.self) { section in
                    NavItem(section: section, selected: nav.section == section && nav.opened.isEmpty) {
                        nav.go(section)
                    }
                }
            }
            .padding(4)
            .glass(Capsule(style: .continuous))
            Color.clear.frame(width: 48, height: 48)
        }
        .animation(Theme.focusSpring, value: nav.opened.isEmpty)
    }
}

private struct NavItem: View {
    let section: MacSection
    let selected: Bool
    let action: () -> Void

    @Environment(\.loc) private var loc
    @Environment(Downloads.self) private var downloads
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                if section.iconOnly || section == .profile {
                    Image(systemName: section.symbol).font(.system(size: 16, weight: .semibold))
                        .overlay(alignment: .topTrailing) {
                            // Something is coming down: a dot on the downloads glyph says so from any screen.
                            if section == .downloads, downloads.running > 0 {
                                Circle().fill(Theme.accent).frame(width: 7, height: 7).offset(x: 3, y: -3)
                            }
                        }
                }
                if !section.iconOnly {
                    Text(loc(section.title)).font(.app(15.5, .semibold))
                }
            }
            .foregroundStyle(selected ? Theme.focusInk : Theme.text1.opacity(hovering ? 1 : 0.78))
            .padding(.horizontal, section.iconOnly ? 13 : 17)
            .frame(height: 40)
            .background {
                Capsule(style: .continuous).fill(selected ? Color.white : Color.white.opacity(hovering ? 0.12 : 0))
            }
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.16), value: hovering)
        .animation(Theme.focusSpring, value: selected)
        .help(loc(section.title))
    }
}
