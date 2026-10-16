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

/// The card that opens over a poster the pointer has rested on: the title, and where its poster
/// is in the window.
struct Peek: Equatable {
    let post: Post
    let frame: CGRect
}

/// A poster on its way from the card that was clicked to its place on the title's page.
struct Flight: Equatable {
    let post: Post
    let from: CGRect
}

/// Where the user is: a section, and what has been opened on top of it.
@MainActor
@Observable
final class MacNav {
    var section = MacSection.home
    private(set) var opened: [MacRoute] = []
    /// The title a download is being chosen for, when that pane is up.
    var picking: Post?
    /// The quick-search bar is up.
    var palette = false
    /// The poster the pointer has rested on, while its card is up.
    var peek: Peek?
    /// The poster in flight to the page being opened, until it lands.
    var flight: Flight?
    /// A title opened by a play button somewhere else: its page starts it without a second click.
    var autoplay: Int?
    /// Where the card that is up lies, and when the window was last scrolled. Read by the code
    /// that puts the card up and takes it away; nothing is drawn from them.
    @ObservationIgnored var peekRect = CGRect.zero
    @ObservationIgnored var lastScroll = Date.distantPast

    /// Opens a title's page. Given where its poster is in the window, the poster flies to its
    /// place on the page rather than the page simply fading in.
    func open(_ post: Post, playing: Bool = false, from frame: CGRect? = nil) {
        if playing { autoplay = post.id }
        if let frame, frame.width > 0, opened.last != .title(post) { flight = Flight(post: post, from: frame) }
        open(.title(post))
    }

    func open(_ route: MacRoute) {
        peek = nil
        guard opened.last != route else { return }
        opened.append(route)
    }

    func back() {
        peek = nil
        if palette { palette = false } else if picking != nil { picking = nil } else if !opened.isEmpty { opened.removeLast() }
    }

    func go(_ section: MacSection) {
        peek = nil
        opened.removeAll()
        self.section = section
    }
}

struct MainView: View {
    @Environment(AppModel.self) private var app
    @State private var nav = MacNav()
    @State private var scrollWatch: Any?
    @Environment(\.layoutDirection) private var direction

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
            // Clear of the window's edge, with the same air above it as the window's own buttons have.
            // The update capsule sits behind the bar and slides out from under it.
            ZStack(alignment: .top) {
                UpdatePill().padding(.top, UpdatePill.drop)
                // At its full width: sharing the stack, it was offered a little less and cut a title short.
                NavBar().fixedSize()
            }
            .padding(.top, 22)
            .zIndex(2)
        }
        .overlay {
            // Placed by the window's own left and top, whatever way the language reads; the card
            // itself goes back to the language's direction.
            GeometryReader { geo in
                if let peek = nav.peek {
                    PeekCard(peek: peek, bounds: geo.size, direction: direction)
                        .id(peek.post.id)
                        .transition(.scale(scale: 0.86).combined(with: .opacity))
                }
                if let flight = nav.flight {
                    FlyingPoster(flight: flight, bounds: geo.size, rtl: direction == .rightToLeft)
                        .id(flight.post.id)
                }
            }
            .environment(\.layoutDirection, .leftToRight)
        }
        // The card goes when the pointer leaves it. Asked of the window and not of the card: a
        // card that comes up under a pointer at rest is never told the pointer came in, and so
        // would never hear that it left.
        .onContinuousHover(coordinateSpace: .named(MainView.space)) { phase in
            guard nav.peek != nil else { return }
            if case .active(let point) = phase, nav.peekRect.insetBy(dx: -10, dy: -10).contains(point) { return }
            nav.peek = nil
        }
        .coordinateSpace(name: MainView.space)
        .animation(Theme.focusSpring, value: nav.peek)
        .onAppear {
            // Scrolling is not resting: the card steps aside, and none comes up mid-scroll.
            scrollWatch = NSEvent.addLocalMonitorForEvents(matching: .scrollWheel) { event in
                nav.lastScroll = Date()
                if nav.peek != nil { nav.peek = nil }
                return event
            }
        }
        .onDisappear {
            if let scrollWatch { NSEvent.removeMonitor(scrollWatch) }
            scrollWatch = nil
        }
        .overlay {
            if let post = nav.picking {
                DownloadPicker(post: post) { nav.picking = nil }
                    .id(post.id)
                    .transition(.opacity)
            }
        }
        .overlay {
            if nav.palette {
                CommandPalette().transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
        }
        .animation(.easeOut(duration: 0.18), value: nav.palette)
        .animation(.easeOut(duration: 0.22), value: nav.picking?.id)
        .animation(.easeOut(duration: 0.28), value: nav.opened.last)
        .environment(nav)
        // Esc steps back out of a title, as it closes anything else on a Mac.
        .onExitCommand { nav.back() }
        #if DEBUG
        .task { await debugLaunch() }
        #endif
    }

    /// The coordinate space cards and the pointer are placed in: the whole window.
    static let space = "window"

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

    /// The keyboard's way around: ⌘1 to ⌘9 for the sections, ⌘K to find anything, ⌘F to search, ⌘[ to step back.
    private var shortcuts: some View {
        Group {
            ForEach(Array(MacSection.allCases.enumerated()), id: \.offset) { index, section in
                Button("") { nav.go(section) }
                    .keyboardShortcut(KeyEquivalent(Character("\(index + 1)")), modifiers: .command)
                    .accessibilityHidden(true)
            }
            Button("") {
                nav.peek = nil
                nav.palette.toggle()
            }
            .keyboardShortcut("k", modifiers: .command)
            .accessibilityHidden(true)
            Button("") { nav.go(.search) }.keyboardShortcut("f", modifiers: .command).accessibilityHidden(true)
            Button("") { nav.back() }.keyboardShortcut("[", modifiers: .command).accessibilityHidden(true)
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
        // and the downloads disc of the same size balances it on the other side. So the bar stays
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
            .accessibilityLabel(loc(.back))
            .accessibilityHidden(nav.opened.isEmpty)
            .opacity(nav.opened.isEmpty ? 0 : 1)
            .scaleEffect(nav.opened.isEmpty ? 0.8 : 1)
            .allowsHitTesting(!nav.opened.isEmpty)
            HStack(spacing: 2) {
                // Downloads are not a place in the catalogue; they have a disc of their own beside the bar.
                ForEach(MacSection.allCases.filter { $0 != .downloads }, id: \.self) { section in
                    NavItem(section: section, selected: nav.section == section && nav.opened.isEmpty) {
                        nav.go(section)
                    }
                }
            }
            .padding(4)
            .glass(Capsule(style: .continuous))
            DownloadsDisc(selected: nav.section == .downloads && nav.opened.isEmpty) { nav.go(.downloads) }
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
                    Image(systemName: section.symbol).font(.system(size: 16, weight: .semibold))
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
        .accessibilityLabel(loc(section.title))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
