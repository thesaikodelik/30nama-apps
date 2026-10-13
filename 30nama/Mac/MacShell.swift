import SwiftUI

enum MacSection: Hashable, CaseIterable {
    case profile, home, movies, series, anime, browse, search, settings

    var symbol: String {
        switch self {
        case .profile: return "person.crop.circle"
        case .home: return "house"
        case .movies: return "film"
        case .series: return "tv"
        case .anime: return "sparkles"
        case .browse: return "square.grid.2x2"
        case .search: return "magnifyingglass"
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
        case .settings: return .settings
        }
    }

    /// Search and settings are known by their glyphs alone; the rest carry their names.
    var iconOnly: Bool { self == .search || self == .settings }
}

/// Where the user is: a section, and the titles opened on top of it.
@MainActor
@Observable
final class MacNav {
    var section = MacSection.home
    private(set) var opened: [Post] = []

    func open(_ post: Post) {
        guard opened.last?.id != post.id else { return }
        opened.append(post)
    }

    func back() {
        if !opened.isEmpty { opened.removeLast() }
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
            if let post = nav.opened.last {
                DetailsView(preview: post)
                    .id(post.id)
                    .transition(.opacity.combined(with: .scale(scale: 1.02)))
                    .zIndex(1)
            }
        }
        .overlay(alignment: .top) {
            NavBar().padding(.top, 14).zIndex(2)
        }
        .animation(.easeOut(duration: 0.28), value: nav.opened.last?.id)
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
        guard let target = DebugLaunch.open, let feed = try? await app.home() else { return }
        let pool = feed.heroes + feed.sections.flatMap(\.posts)
        if let post = target == "first" ? pool.first : pool.first(where: { String($0.id) == target }) { nav.open(post) }
    }
    #endif

    @ViewBuilder
    private var page: some View {
        switch nav.section {
        case .home: HomeView()
        default: ComingSoon(section: nav.section)
        }
    }
}

/// The floating bar at the top of the window: the same glass capsule the Apple TV app has.
private struct NavBar: View {
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    var body: some View {
        HStack(spacing: 10) {
            if !nav.opened.isEmpty {
                Button { nav.back() } label: {
                    Image(systemName: "chevron.backward")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.text1)
                        .frame(width: 44, height: 44)
                        .glass(Circle())
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .transition(.opacity.combined(with: .scale(scale: 0.8)))
            }
            HStack(spacing: 2) {
                ForEach(MacSection.allCases, id: \.self) { section in
                    NavItem(section: section, selected: nav.section == section && nav.opened.isEmpty) {
                        nav.go(section)
                    }
                }
            }
            .padding(4)
            .glass(Capsule(style: .continuous))
        }
        .animation(Theme.focusSpring, value: nav.opened.isEmpty)
    }
}

private struct NavItem: View {
    let section: MacSection
    let selected: Bool
    let action: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 7) {
                if section.iconOnly || section == .profile {
                    Image(systemName: section.symbol).font(.system(size: 14, weight: .semibold))
                }
                if !section.iconOnly {
                    Text(loc(section.title)).font(.app(14, .semibold))
                }
            }
            .foregroundStyle(selected ? Theme.focusInk : Theme.text1.opacity(hovering ? 1 : 0.78))
            .padding(.horizontal, section.iconOnly ? 11 : 15)
            .frame(height: 36)
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

/// Stands in for a section that has not been built for the Mac yet.
private struct ComingSoon: View {
    let section: MacSection
    @Environment(\.loc) private var loc

    var body: some View {
        ZStack {
            AmbientBackground()
            VStack(spacing: 14) {
                Image(systemName: section.symbol).font(.system(size: 46, weight: .light)).foregroundStyle(Theme.text3)
                Text(loc(section.title)).font(.app(30, .bold)).foregroundStyle(Theme.text1)
                Text(loc(.soon)).font(.app(15, .medium)).foregroundStyle(Theme.text3)
            }
        }
    }
}
