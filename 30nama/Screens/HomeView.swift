import SwiftUI

/// Home: a billboard over a living backdrop, with rows of posters below.
///
/// On arrival it is a hero carousel. Moving down turns the billboard into a preview of whatever
/// card is in focus, while the backdrop crossfades to that title.
struct HomeView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc
    @Environment(\.calm) private var calm

    @State private var feed: HomeFeed?
    @State private var continuing: [Post] = []
    @State private var forYou: [Post] = []
    @State private var failed = false
    @State private var heroIndex = 0
    /// The card in focus, when focus is in the rows.
    @State private var browsing: Selection?
    /// `browsing`, once focus has rested on it for a moment.
    @State private var settled: Selection?
    @State private var lastInput = Date()
    /// 0...1: how far through its turn the current hero is.
    @State private var heroProgress = 0.0
    @State private var visible = false
    /// Rows that have had focus, whose cards beyond the first screenful may now load their posters.
    @State private var opened: Set<String> = []
    @FocusState private var focus: Target?

    enum Target: Hashable {
        case play, details, signIn
        case card(row: String, id: Int)
    }

    struct Selection: Equatable {
        var row: String
        var post: Post
        var rank: Int
    }

    private enum Layout {
        static let screen: CGFloat = 1080
        /// Bottom edge of the billboard text while the hero is showing.
        static let heroTextBottom: CGFloat = 596
        /// While browsing the text ends higher, leaving a band where the row above shows through.
        static let browseTextBottom: CGFloat = 548
        static let buttonsTop: CGFloat = 628
        /// Where the first row rests while the hero is showing: just its header and a strip of posters.
        static let restingRowTop: CGFloat = 852
        /// Where the focused row's header sits while browsing.
        static let focusLine: CGFloat = 632
        static let headerHeight: CGFloat = 40
        static let headerGap: CGFloat = 26
        static let rowHeight: CGFloat = headerHeight + headerGap + 300
        static let rowSpacing: CGFloat = 36
    }

    private var heroes: [Post] { feed?.heroes ?? [] }

    private var rows: [HomeSection] {
        guard let feed else { return [] }
        let lead = [
            continuing.isEmpty ? nil : HomeSection(key: "continue", posts: continuing),
            forYou.isEmpty ? nil : HomeSection(key: "for_you", posts: forYou),
        ].compactMap { $0 }
        return lead + feed.sections
    }

    /// What the rows are scrolled to: the hero ("top"), a row, or the sign-in note below the last row.
    private var scrollTarget: String {
        if focus == .signIn { return "signin" }
        return browsing?.row ?? "top"
    }

    private var hero: Post? { heroes.indices.contains(heroIndex) ? heroes[heroIndex] : heroes.first }
    /// What the billboard and the backdrop show.
    private var featured: Post? { browsing == nil ? hero : (settled ?? browsing)?.post }

    var body: some View {
        ZStack(alignment: .topLeading) {
            StageView(post: featured)
            if feed != nil {
                content
                billboard
            } else if failed {
                ErrorState { Task { await load() } }
            } else {
                loadingSkeleton
            }
        }
        .ignoresSafeArea()
        .toolbar(.hidden, for: .navigationBar)
        .task { await load() }
        .task(id: app.watchVersion) {
            if feed != nil { continuing = await app.continueWatching() }
        }
        .task(id: browsing) {
            // Billboard text follows focus after a beat, so a held remote does not strobe it.
            try? await Task.sleep(for: .milliseconds(140))
            if !Task.isCancelled { settled = browsing }
        }
        .task(id: visible) { await rotateHero() }
        .onAppear { visible = true }
        .onDisappear { visible = false }
        .onChange(of: focus) { _, target in
            lastInput = Date()
            switch target {
            case .card(let row, let id):
                opened.insert(row)
                if let section = rows.first(where: { $0.key == row }),
                   let index = section.posts.firstIndex(where: { $0.id == id }) {
                    browsing = Selection(row: row, post: section.posts[index], rank: index + 1)
                }
            case .play, .details:
                browsing = nil
            case .signIn, nil:
                break
            }
        }
    }

    // MARK: Data

    private func load() async {
        failed = false
        // Last time's page at once, so there is something to see from the first frame; the fresh
        // one replaces it when it comes.
        if feed == nil, let saved = app.cachedHome() { feed = saved }
        do {
            async let feed = app.home()
            async let continuing = app.continueWatching()
            let (loaded, resumed) = try await (feed, continuing)
            self.continuing = resumed
            self.feed = loaded
            self.forYou = app.ratings.recommend(from: loaded.sections.flatMap(\.posts), limit: 14)
            if focus == nil { focus = loaded.heroes.isEmpty ? nil : .play }
        } catch {
            failed = true
        }
    }

    /// Advances the hero every nine seconds while it is on screen and the remote is idle.
    private func rotateHero() async {
        let turn = 9.0
        while visible, !Task.isCancelled {
            try? await Task.sleep(for: .milliseconds(250))
            guard visible, !calm, browsing == nil, heroes.count > 1, app.playback == nil else { continue }
            let idle = Date().timeIntervalSince(lastInput)
            if idle >= turn {
                lastInput = Date()
                heroProgress = 0
                withAnimation(Theme.glide) { heroIndex = (heroIndex + 1) % heroes.count }
            } else {
                withAnimation(.linear(duration: 0.25)) { heroProgress = idle / turn }
            }
        }
    }

    // MARK: Billboard

    private var billboard: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let post = featured {
                Billboard(post: post, eyebrow: eyebrow(for: post), plotLines: browsing == nil ? 3 : 2)
                    .id(post.id)
                    .transition(calm ? .opacity : .blurFade.combined(with: .offset(y: 14)))
            }
        }
        .frame(width: 900, height: browsing == nil ? Layout.heroTextBottom : Layout.browseTextBottom, alignment: .bottomLeading)
        .padding(.leading, Theme.edge)
        .animation(.easeOut(duration: 0.34), value: featured?.id)
        .animation(Theme.glide, value: browsing == nil)
        .allowsHitTesting(false)
    }

    private func eyebrow(for post: Post) -> String {
        guard let selection = settled ?? browsing, browsing != nil else {
            return "\(loc(.featured)) · \(loc.kindLine(post))"
        }
        switch selection.row {
        case "top_10": return "\(loc(.top10)) · #\(loc.num(selection.rank))"
        case "continue": return loc(.continueWatching)
        default: return loc.kindLine(post)
        }
    }

    // MARK: Rows

    private var content: some View {
        ScrollViewReader { proxy in
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 0) {
                    Color.clear.frame(height: Layout.buttonsTop).id("top")
                    heroControls
                        .frame(height: Layout.restingRowTop - Layout.buttonsTop, alignment: .top)
                    VStack(alignment: .leading, spacing: Layout.rowSpacing) {
                        ForEach(rows) { section in
                            row(section)
                                .frame(height: Layout.rowHeight, alignment: .top)
                                .id(section.key)
                        }
                        if !app.isLoggedIn { guestNote.id("signin") }
                    }
                    Color.clear.frame(height: 200)
                }
            }
            .scrollIndicators(.hidden)
            // The focus engine's own scrolling races with ours and lands rows at a different height,
            // so it is switched off here: rows only ever move to the positions set below.
            .scrollDisabled(true)
            // Everything above the billboard's bottom edge belongs to the billboard. Just below it the
            // row above fades in as a thin strip: a hint that there is more up there, never under the text.
            .mask {
                LinearGradient(
                    stops: [
                        .init(color: .clear, location: (Layout.browseTextBottom + 10) / Layout.screen),
                        .init(color: .black, location: (Layout.focusLine - Layout.rowSpacing + 4) / Layout.screen),
                    ],
                    startPoint: .top, endPoint: .bottom)
            }
            .onChange(of: scrollTarget) { _, target in
                withAnimation(Theme.glide) { scroll(proxy, to: target) }
            }
        }
    }

    private func scroll(_ proxy: ScrollViewProxy, to target: String) {
        switch target {
        case "top": proxy.scrollTo("top", anchor: .top)
        case "signin": proxy.scrollTo("signin", anchor: .bottom)
        default:
            // Put the focused row's header on the focus line.
            let y = Layout.focusLine / (Layout.screen - Layout.rowHeight)
            proxy.scrollTo(target, anchor: UnitPoint(x: 0.5, y: y))
        }
    }

    private var heroControls: some View {
        VStack(alignment: .leading, spacing: 26) {
            HStack(spacing: 16) {
                if let hero {
                    NavigationLink(value: Route.details(PostRef(hero, autoplay: true))) {
                        Label(continuing.contains(hero) ? loc(.resume) : loc(.play), systemImage: "play.fill")
                    }
                    .buttonStyle(PillButtonStyle(kind: .primary))
                    .focused($focus, equals: .play)

                    NavigationLink(value: Route.details(PostRef(hero))) {
                        Label(loc(.details), systemImage: "info.circle")
                    }
                    .buttonStyle(PillButtonStyle())
                    .focused($focus, equals: .details)
                }
            }
            if heroes.count > 1 {
                // Story-style pager: the current segment fills as its turn runs out.
                HStack(spacing: 8) {
                    ForEach(heroes.indices, id: \.self) { i in
                        Capsule()
                            .fill(Color.white.opacity(0.22))
                            .frame(width: i == heroIndex ? 64 : 22, height: 5)
                            .overlay(alignment: .leading) {
                                Capsule().fill(Color.white)
                                    .frame(width: i == heroIndex ? max(5, 64 * heroProgress) : 0, height: 5)
                            }
                    }
                }
                .animation(Theme.glide, value: heroIndex)
            }
        }
        .padding(.leading, Theme.edge)
        .focusSection()
    }

    private func row(_ section: HomeSection) -> some View {
        let current = browsing?.row == section.key
        let keys = rows.map(\.key)
        // The row above the focused one stays visible only as a quiet strip under the billboard.
        let above = (keys.firstIndex(of: section.key) ?? 0) < (browsing.flatMap { keys.firstIndex(of: $0.row) } ?? 0)
        return VStack(alignment: .leading, spacing: Layout.headerGap) {
            Text(loc.title(forSection: section.key))
                .font(.app(32, .bold))
                .foregroundStyle(current ? Theme.text1 : Theme.text2)
                .scaleEffect(current ? 1.06 : 1, anchor: .leading)
                .animation(Theme.focusSpring, value: current)
                .frame(height: Layout.headerHeight)
                .padding(.leading, Theme.edge)
            ScrollView(.horizontal) {
                // Not a lazy stack: a row that is off screen makes only its first few cards, and
                // moving down onto it from further along lands focus on the gap where a card should
                // be, with nothing to select. Every card exists from the start; only the posters
                // past the first screenful wait until the row has had focus.
                HStack(alignment: .top, spacing: section.key == "top_10" ? 8 : 24) {
                    ForEach(Array(section.posts.enumerated()), id: \.element.id) { index, post in
                        card(post, in: section, rank: index + 1, loads: index < 10 || opened.contains(section.key))
                            .focused($focus, equals: .card(row: section.key, id: post.id))
                    }
                }
                .padding(.horizontal, Theme.edge)
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
        }
        .opacity(above ? 0.45 : 1)
        .animation(.easeOut(duration: 0.3), value: above)
        .focusSection()
    }

    @ViewBuilder
    private func card(_ post: Post, in section: HomeSection, rank: Int, loads: Bool) -> some View {
        switch section.key {
        case "continue":
            NavigationLink(value: Route.details(PostRef(post))) {
                ContinueCard(post: post)
            }
            .buttonStyle(CardButtonStyle(glow: post.tint))
            // Held select: off the row, on 30nama too.
            .contextMenu {
                Button(role: .destructive) {
                    app.dismissContinue(post)
                    withAnimation(Theme.glide) { continuing.removeAll { $0.id == post.id } }
                } label: {
                    Label(loc(.removeFromContinue), systemImage: "xmark.circle")
                }
            }
        case "top_10":
            HStack(alignment: .bottom, spacing: -34) {
                Text(loc.num(rank))
                    .font(.app(250, .heavy))
                    .foregroundStyle(Theme.text1.opacity(browsing?.post == post && browsing?.row == "top_10" ? 0.9 : 0.22))
                    .frame(height: 300)
                    .environment(\.layoutDirection, .leftToRight)
                PosterLink(post: post, loads: loads)
            }
        default:
            PosterLink(post: post, loads: loads)
        }
    }

    private var guestNote: some View {
        HStack(spacing: 28) {
            Text(loc(.loginToStream))
                .font(.app(26))
                .foregroundStyle(Theme.text2)
            Button(loc(.signIn)) { app.requestSignIn() }
                .buttonStyle(PillButtonStyle())
                .focused($focus, equals: .signIn)
        }
        .padding(.leading, Theme.edge)
        .padding(.top, 8)
        .padding(.bottom, 90)
    }

    private var loadingSkeleton: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 20) {
                Skeleton(width: 600, height: 64)
                Skeleton(width: 420, height: 28)
                Skeleton(width: 760, height: 28)
                Skeleton(width: 760, height: 28)
            }
            .padding(.leading, Theme.edge)
            .padding(.top, 330)
            HStack(spacing: 24) {
                ForEach(0..<9, id: \.self) { _ in
                    Skeleton(width: Theme.posterWidth, height: Theme.posterWidth * 1.5, radius: Theme.cardRadius)
                }
            }
            .padding(.leading, Theme.edge)
            .padding(.top, Layout.restingRowTop + Layout.headerHeight + Layout.headerGap)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

/// Title block for the featured title: what it is, its name, the facts, the pitch.
private struct Billboard: View {
    let post: Post
    let eyebrow: String
    var plotLines = 3

    @Environment(\.loc) private var loc

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Eyebrow(text: eyebrow)
            Text(post.title(loc.lang))
                .font(.app(80, .heavy))
                .foregroundStyle(
                    LinearGradient(colors: [.white, Theme.text1.opacity(0.82)], startPoint: .top, endPoint: .bottom))
                .lineLimit(2)
                .minimumScaleFactor(0.55)
                .shadow(color: .black.opacity(0.55), radius: 18, y: 6)
            if let alternate = post.alternateTitle(loc.lang) {
                Text(alternate)
                    .font(.app(28, .medium))
                    .foregroundStyle(Theme.text2)
                    .lineLimit(1)
            }
            MetaLine(post: post)
                .padding(.top, 6)
            Text(post.plot(loc.lang))
                .font(.app(26))
                .foregroundStyle(Theme.text2)
                .lineSpacing(6)
                .lineLimit(plotLines)
                .frame(maxWidth: 820, alignment: .leading)
                .padding(.top, 2)
        }
        .multilineTextAlignment(.leading)
    }
}

/// Landscape card for the "continue watching" row: cover, progress, and where you left off.
private struct ContinueCard: View {
    let post: Post

    @Environment(\.loc) private var loc

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Theme.surface
            RemoteImage(url: post.cover ?? post.poster.big, blurhash: post.poster.blurhash, maxPixel: Int(400 * UIScreen.main.scale))
            LinearGradient(colors: [.black.opacity(0.85), .clear], startPoint: .bottom, endPoint: .center)
            VStack(alignment: .leading, spacing: 10) {
                Text(post.title(loc.lang))
                    .font(.app(24, .bold))
                    .foregroundStyle(Theme.text1)
                    .lineLimit(1)
                if let label {
                    Text(label)
                        .font(.app(20, .medium))
                        .foregroundStyle(Theme.text2)
                        .lineLimit(1)
                }
                if let progress = post.resume?.progress, progress > 0 {
                    Capsule()
                        .fill(Color.white.opacity(0.25))
                        .frame(height: 6)
                        .overlay(alignment: .leading) {
                            GeometryReader { geo in
                                Capsule().fill(Theme.accent).frame(width: max(6, geo.size.width * progress))
                            }
                        }
                }
            }
            .padding(16)
        }
        .frame(width: 400, height: 300)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
    }

    private var label: String? {
        guard let resume = post.resume else { return nil }
        var parts: [String] = []
        if let season = resume.season, let episode = resume.episode {
            parts.append(loc.episodeTag(season: season, episode: Episode.label(episode, fallback: 1)))
        }
        if let left = resume.remainingMinutes {
            parts.append("\(loc.num(left)) \(loc(.minLeft))")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}
