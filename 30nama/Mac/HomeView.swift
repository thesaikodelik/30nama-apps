import SwiftUI

/// Home: a billboard of featured titles over their own artwork, with the shelves scrolling up over it.
struct HomeView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc
    @Environment(\.calm) private var calm
    @Environment(\.lively) private var lively
    @Environment(\.controlActiveState) private var windowState

    @State private var feed: HomeFeed?
    /// Nil while the row is on its way.
    @State private var continuing: [Post]?
    /// TMDB's week, as far as 30nama has it; filled in after the page is up.
    /// Nil while the row is on its way.
    @State private var trending: [Post]?
    /// The ten best-loved films and series. The home feed no longer names a top ten of its own,
    /// so these are the first ten of the catalogue's "most popular" order.
    @State private var top: [String: [Post]] = HomeView.topCache
    private static var topCache: [String: [Post]] = [:]
    /// The week's trending row came the last time it was asked for.
    private static var trendingCame: Bool {
        get { UserDefaults.standard.bool(forKey: "trendingCame") }
        set { UserDefaults.standard.set(newValue, forKey: "trendingCame") }
    }
    @State private var failed = false
    @State private var hero = 0
    /// The pointer is on the billboard: someone is reading it, so it should not move on.
    @State private var paused = false
    /// The billboard is on screen, not scrolled away above the shelves.
    @State private var inSight = true

    /// Each featured title's colour, taken from its scene.
    @State private var tints: [Int: Color] = [:]

    /// The colour the billboard and everything about it is lit in.
    private var light: Color? { featured.map { tints[$0.id] ?? $0.tint } }

    private var featured: Post? {
        guard let heroes = feed?.heroes, !heroes.isEmpty else { return nil }
        return heroes[min(hero, heroes.count - 1)]
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                StageView(post: featured, tint: light)
                if let feed {
                    ScrollView(.vertical) {
                        // Lazy, so the billboard is told when it scrolls out of sight and back.
                        LazyVStack(alignment: .leading, spacing: 0) {
                            billboard(feed).frame(height: max(500, geo.size.height * 0.72))
                                .onAppear { inSight = true }
                                .onDisappear { inSight = false }
                            shelves(feed)
                        }
                    }
                    .scrollIndicators(.never)
                } else if failed {
                    ErrorState { Task { await load() } }
                } else {
                    ProgressView().controlSize(.large)
                }
            }
        }
        .task(id: app.contentVersion) { await load() }
        .task(id: "\(app.watchVersion)|\(app.continueVersion)") {
            // Once the row is up; the first time is `load`'s, with its stand-ins.
            if feed != nil, continuing != nil { continuing = await app.continueWatching() }
        }
        .environment(\.hue, light)
        .task(id: featured?.id) {
            // This title's colour, and the next one's ahead of its turn: that fetches its picture
            // early too, so the billboard has it ready when it moves on.
            guard let heroes = feed?.heroes, !heroes.isEmpty else { return }
            for post in [heroes[min(hero, heroes.count - 1)], heroes[(hero + 1) % heroes.count]] where tints[post.id] == nil {
                tints[post.id] = await SceneTint.shared.colour(for: post)
            }
        }
    }

    // MARK: Billboard

    private func billboard(_ feed: HomeFeed) -> some View {
        VStack(alignment: .leading, spacing: 22) {
            Spacer(minLength: 0)
            if let post = featured {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 9) {
                        Capsule().fill(light ?? post.tint).frame(width: 4, height: 16)
                        Text("\(loc(.featured)) · \(loc.kindLine(post))")
                            .font(.app(14, .semibold))
                            .foregroundStyle(Theme.text2)
                    }
                    TitleArt(post: post, size: 56)
                    MetaRow(post: post).padding(.top, 2)
                    Text(post.plot(loc.lang))
                        .font(.app(16))
                        .foregroundStyle(Theme.text2)
                        .lineSpacing(6)
                        .lineLimit(3)
                        .frame(maxWidth: 580, alignment: .leading)
                    HStack(spacing: 12) {
                        Button { nav.open(post) } label: {
                            Label(loc(post.resume != nil ? .resume : .play), systemImage: "play.fill")
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        Button { nav.open(post) } label: {
                            Label(loc(.details), systemImage: "info.circle")
                        }
                        .buttonStyle(GlassButtonStyle())
                    }
                    .padding(.top, 8)
                }
                .frame(maxWidth: 760, alignment: .leading)
                .id(post.id)
                .transition(.opacity.combined(with: .offset(y: 10)))
                .onHover { paused = $0 }
            }
            Pager(count: feed.heroes.count, hero: $hero, resting: resting, light: light)
        }
        .padding(.horizontal, Mac.edge)
        .padding(.bottom, 30)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The billboard has no audience: something covers it, the window is not the one in use, or
    /// the user is reading it. It costs nothing while it rests.
    private var resting: Bool {
        calm || paused || !inSight || !nav.opened.isEmpty || app.playback != nil || windowState != .key
    }

    /// The section a row of the home feed stands for, when it is a whole one.
    static func section(for key: String) -> MacSection? {
        switch key {
        case "movies": return .movies
        case "series": return .series
        case "anime": return .anime
        default: return nil
        }
    }

    // MARK: Shelves

    private func shelves(_ feed: HomeFeed) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            if app.isLoggedIn {
                ArrivingShelf(title: loc(.continueWatching), posts: continuing, step: 3, card: .wide) { post in
                    ContinueCard(post: post, remove: {
                        app.dismissContinue(post)
                        withAnimation(Theme.glide) { continuing?.removeAll { $0.id == post.id } }
                    }) { nav.open(post) }
                }
            }
            // Its stand-ins only where it came last time: from where TMDB cannot be reached it
            // never comes, and stand-ins that then went would move every row under them.
            if trending != nil || HomeView.trendingCame {
                ArrivingShelf(title: loc(.trendingWeek), posts: trending, card: .poster) { post in
                    PosterCard(post: post) { nav.open(post) }
                }
            }
            ForEach(feed.sections) { section in
                ArrivingShelf(title: loc.title(forSection: section.key), posts: section.posts, more: HomeView.section(for: section.key).map { target in { nav.go(target) } },
                              card: section.key == "top_10" ? .ranked : .poster) { post in
                    if section.key == "top_10" {
                        RankedCard(rank: (section.posts.firstIndex { $0.id == post.id } ?? 0) + 1, post: post) { nav.open(post) }
                    } else {
                        PosterCard(post: post) { nav.open(post) }
                    }
                }
                // The two top tens sit under the first shelf, before the long lists begin.
                if section.id == feed.sections.first?.id, feed.posts("top_10").isEmpty {
                    ranked(loc(.topFilms), top["movies"])
                    ranked(loc(.topSeries), top["series"])
                }
            }
        }
        .padding(.top, 14)
        .padding(.bottom, 60)
        .background {
            // The shelves rise out of the artwork: clear where they begin, solid a little further down.
            LinearGradient(stops: [
                .init(color: .clear, location: 0),
                .init(color: Theme.bg0.opacity(0.9), location: 0.1),
                .init(color: Theme.bg0, location: 0.24),
            ], startPoint: .top, endPoint: .bottom)
        }
    }

    @ViewBuilder
    private func ranked(_ title: String, _ posts: [Post]?) -> some View {
        ArrivingShelf(title: title, posts: posts, step: 4, card: .ranked) { post in
            RankedCard(rank: ((posts ?? []).firstIndex { $0.id == post.id } ?? 0) + 1, post: post) { nav.open(post) }
        }
    }

    // MARK: Data

    private func load() async {
        // Last time's page at once, so there is something to see from the first frame; the fresh
        // one replaces it when it comes.
        if feed == nil, let saved = app.cachedHome() { feed = saved }
        do {
            let fresh = try await app.home()
            feed = fresh
            failed = false
            hero = min(hero, max(0, fresh.heroes.count - 1))
            continuing = await app.continueWatching()
        } catch {
            if feed == nil { failed = true }
        }
        await loadTop()
        if trending == nil {
            let found = await app.trendingThisWeek()
            guard !Task.isCancelled else { return }
            trending = found
            HomeView.trendingCame = !found.isEmpty
        }
    }

    /// Asked for once and kept for as long as the app runs: the order changes slowly, and the
    /// page is not waiting on it.
    private func loadTop() async {
        guard top.isEmpty else { return }
        for slug in ["movies", "series"] {
            // Empty, not missing, when it could not be had: a missing row keeps its stand-ins.
            guard let result = try? await app.api.get("archive", ["slugs": slug, "streamonly": "1", "page": "1", "orderby": "favorite", "order": "desc"], background: true) else {
                top[slug] = []
                continue
            }
            top[slug] = Array(PostPage(result).posts.prefix(10))
        }
        HomeView.topCache = top
    }
}

/// Story-style pager under the billboard, as on the Apple TV: the current segment fills as its
/// turn runs out, and then the billboard moves on. The turn is its own state, so the bar filling
/// redraws the bar and not the whole home page with every row on it.
private struct Pager: View {
    let count: Int
    @Binding var hero: Int
    /// Nobody is watching the billboard: the turn holds where it is.
    let resting: Bool
    let light: Color?

    @Environment(\.loc) private var loc
    @Environment(\.calm) private var calm
    @Environment(\.lively) private var lively
    /// How much of the featured title's turn has run, 0...1.
    @State private var turn = 0.0

    /// How long each featured title is shown.
    private static let turnLength = 12.0

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<count, id: \.self) { index in
                Button {
                    withAnimation(.easeInOut(duration: 0.45)) {
                        hero = index
                        turn = 0
                    }
                } label: {
                    Capsule()
                        .fill(Color.white.opacity(0.28))
                        .frame(width: index == hero ? 44 : 12, height: 4)
                        .overlay(alignment: .leading) {
                            // With motion reduced nothing moves on, so the segment is simply lit.
                            Capsule().fill(light ?? .white)
                                .frame(width: index == hero ? (calm ? 44 : max(4, 44 * turn)) : 0, height: 4)
                        }
                        .padding(.vertical, 8)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(loc(.featured)) \(loc.num(index + 1))")
                .accessibilityAddTraits(index == hero ? .isSelected : [])
            }
        }
        .task(id: "\(hero)|\(resting)") { await rotate() }
    }

    /// Runs the featured title's turn and moves the billboard on when it is over. The turn is
    /// counted in steps, so it can stop where it is when the billboard rests and go on from
    /// there. A Mac that takes motion in its stride gets steps fine enough to read as a smooth
    /// fill; any other gets one a second, so the window is not redrawn all the time for a bar.
    private func rotate() async {
        guard !resting, count > 1 else { return }
        let step = lively ? 0.1 : 1.0
        while turn < 1 {
            try? await Task.sleep(for: .seconds(step))
            guard !Task.isCancelled else { return }
            withAnimation(.linear(duration: lively ? step : 0.3)) { turn = min(1, turn + step / Pager.turnLength) }
        }
        withAnimation(.easeInOut(duration: 0.55)) {
            hero = (hero + 1) % count
            turn = 0
        }
    }
}

/// A home row that arrives whole. Until its list and the pictures of the cards in sight are in, it
/// stands as grey cards of its own cards' size in its own place, so nothing on the page moves when
/// it comes; then its cards come one after another. A row with nothing in it leaves.
private struct ArrivingShelf<Card: View>: View {
    enum Kind {
        case poster, ranked, wide

        var size: CGSize {
            switch self {
            case .poster: return CGSize(width: Mac.posterWidth, height: Mac.posterWidth * 1.5)
            case .ranked: return CGSize(width: Mac.posterWidth + 60, height: Mac.posterWidth * 1.5)
            case .wide: return ContinueCard.size
            }
        }

        /// The picture each kind of card shows, at the size it asks for it.
        @MainActor
        func picture(of post: Post) -> (url: URL?, pixels: Int) {
            switch self {
            case .poster, .ranked:
                return (post.poster.url(forWidth: Mac.posterWidth * 1.1), Int(Mac.posterWidth * 1.5 * Display.scale))
            case .wide:
                return (post.cover ?? post.poster.big ?? post.poster.large, Int(ContinueCard.size.width * Display.scale))
            }
        }
    }

    let title: String
    /// Nil while it is on its way.
    let posts: [Post]?
    var step = 5
    var more: (() -> Void)? = nil
    let card: Kind
    @ViewBuilder let content: (Post) -> Card

    @Environment(\.calm) private var calm
    @State private var ready = false
    /// How many of its cards are in, counted from the first.
    @State private var shown = 0

    init(title: String, posts: [Post]?, step: Int = 5, more: (() -> Void)? = nil, card: Kind, @ViewBuilder content: @escaping (Post) -> Card) {
        self.title = title
        self.posts = posts
        self.step = step
        self.more = more
        self.card = card
        self.content = content
    }

    var body: some View {
        Group {
            if !ready {
                Shelf(title: title, posts: CardStandIn.posts, step: step) { _ in CardStandIn(size: card.size) }
                    .allowsHitTesting(false)
            } else if let posts, !posts.isEmpty {
                Shelf(title: title, posts: posts, step: step, more: more) { post in
                    let order = posts.firstIndex { $0.id == post.id } ?? 0
                    content(post)
                        .opacity(order < shown ? 1 : 0)
                        .offset(y: order < shown ? 0 : 10)
                }
            }
        }
        .task(id: posts?.map(\.id)) { await arrive() }
    }

    private func arrive() async {
        guard let posts else { return }
        // Already up: a fresh list changes the cards where they are.
        if ready {
            shown = posts.count
            return
        }
        let kind = card
        let pictures = Task { @MainActor in
            await withTaskGroup(of: Void.self) { group in
                for post in posts.prefix(7) {
                    let picture = kind.picture(of: post)
                    guard let url = picture.url else { continue }
                    group.addTask { @MainActor in _ = await ImagePipeline.shared.image(url, maxPixel: picture.pixels) }
                }
            }
        }
        // A slow picture holds the row up for three seconds at most.
        await withTaskGroup(of: Void.self) { race in
            race.addTask { await pictures.value }
            race.addTask { try? await Task.sleep(for: .seconds(3)) }
            _ = await race.next()
            race.cancelAll()
        }
        guard !Task.isCancelled else { return }
        withAnimation(.easeOut(duration: 0.2)) { ready = true }
        for count in 1...max(1, posts.count) {
            withAnimation(calm ? nil : .spring(response: 0.42, dampingFraction: 0.84)) { shown = count }
            if !calm, count < 9 { try? await Task.sleep(for: .milliseconds(60)) }
        }
    }
}
