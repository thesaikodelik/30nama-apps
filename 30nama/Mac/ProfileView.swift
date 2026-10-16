import SwiftUI

/// The user's own page: who they are, what their stars say about their taste, the lists they keep
/// on 30nama, and what all that suggests they watch next.
struct ProfileView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var picks: [Post] = []
    @State private var starters: [Post] = []
    @State private var watchlist: [Post] = []
    @State private var favorites: [Post] = []
    /// The full size of each list; the shelves only show the latest additions.
    @State private var listSizes: [String: Int] = [:]
    /// Series the user follows that have an episode on the way, soonest first.
    @State private var upcoming: [Post] = []

    private static let gold = Color(hex: 0xF5C518)
    private let columns = [GridItem(.adaptive(minimum: Mac.posterWidth, maximum: Mac.posterWidth + 40), spacing: 20)]

    /// The title whose artwork washes the page: something the user chose, else something they rated.
    private var backdrop: Post? {
        favorites.first ?? watchlist.first ?? app.ratings.newestFirst.first?.post
    }

    var body: some View {
        ZStack {
            AmbientBackground()
            StageView(post: backdrop, dim: 0.5, glow: false)
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 34) {
                    VStack(alignment: .leading, spacing: 30) {
                        header
                        if !app.ratings.isEmpty { insights }
                        if app.ratings.isEmpty { emptyState }
                    }
                    .padding(.horizontal, Mac.edge)
                    if !upcoming.isEmpty {
                        Shelf(title: loc(.upcoming), posts: upcoming) { post in
                            PosterCard(post: post, note: post.nextEpisodeLabel(loc.lang)) { nav.open(post) }
                        }
                    }
                    if !picks.isEmpty { shelf(loc(.forYou), picks) }
                    if !watchlist.isEmpty { shelf(loc(.watchlist), watchlist) }
                    if !favorites.isEmpty { shelf(loc(.favorites), favorites) }
                    if app.ratings.isEmpty {
                        if !starters.isEmpty { shelf(loc(.startWithThese), starters) }
                    } else {
                        rated.padding(.horizontal, Mac.edge)
                    }
                }
                .padding(.top, 100)
                .padding(.bottom, 60)
            }
            .scrollIndicators(.hidden)
        }
        .task(id: app.ratings) { await refresh() }
        .task(id: "\(app.user?.id ?? "")|\(app.listVersion)") { await loadLists() }
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .center, spacing: 26) {
            avatar
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .center, spacing: 12) {
                    Text(app.user?.name ?? loc(.guestAccount))
                        .font(.app(38, .heavy))
                        .foregroundStyle(Theme.text1)
                        .lineLimit(1)
                        .shadow(color: .black.opacity(0.5), radius: 14, y: 5)
                    if let type = app.user?.type {
                        Text(loc.accountType(type))
                            .font(.app(13, .bold))
                            .foregroundStyle(Theme.focusInk)
                            .padding(.horizontal, 12)
                            .frame(height: 26)
                            .background(Capsule(style: .continuous).fill(LinearGradient(colors: [Color(hex: 0xFFE27A), ProfileView.gold], startPoint: .top, endPoint: .bottom)))
                    }
                }
                HStack(spacing: 10) {
                    if !app.ratings.isEmpty {
                        Stat(value: loc.num(app.ratings.count), label: loc(.ratedCount))
                        if let average = app.ratings.average {
                            Stat(value: loc.num(Fmt.score(average)), label: loc(.averageRating), symbol: "star.fill")
                        }
                    }
                    if !watchlist.isEmpty { Stat(value: loc.num(listSizes["watchlist"] ?? watchlist.count), label: loc(.watchlist)) }
                    if !favorites.isEmpty { Stat(value: loc.num(listSizes["favorite"] ?? favorites.count), label: loc(.favorites)) }
                }
                // For the user's own eyes, and not the point of the page: kept small and quiet.
                if let user = app.user, let line = loc.subscriptionLine(user) {
                    Text(line).font(.app(13)).foregroundStyle(Theme.text3)
                }
            }
            Spacer(minLength: 0)
        }
    }

    private var avatar: some View {
        ZStack {
            Circle().fill(LinearGradient(colors: [Color(hex: 0xFF6A4D), Theme.accent, Color(hex: 0xD92B58)], startPoint: .topLeading, endPoint: .bottomTrailing))
            if let initials {
                Text(initials).font(.app(42, .heavy)).foregroundStyle(.white).environment(\.layoutDirection, .leftToRight)
            } else {
                Image(systemName: "person.fill").font(.system(size: 46)).foregroundStyle(.white)
            }
            if let url = app.user?.avatar {
                RemoteImage(url: url, maxPixel: Int(120 * Display.scale))
            }
        }
        .frame(width: 120, height: 120)
        .clipShape(Circle())
        .overlay { Circle().strokeBorder(LinearGradient(colors: [.white.opacity(0.7), .white.opacity(0.1)], startPoint: .top, endPoint: .bottom), lineWidth: 2) }
        .shadow(color: Theme.accent.opacity(0.4), radius: 26, y: 8)
    }

    /// Initials of the signed-in name, or nil for a guest (who gets a person glyph instead).
    private var initials: String? {
        guard let name = app.user?.name else { return nil }
        let words = name.split(separator: " ")
        let letters = [words.first?.first, words.count > 1 ? words.last?.first : nil].compactMap { $0 }
        return letters.isEmpty ? nil : String(letters).uppercased()
    }

    private struct Stat: View {
        let value: String
        let label: String
        var symbol: String?

        var body: some View {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 6) {
                    if let symbol {
                        Image(systemName: symbol).font(.system(size: 14, weight: .bold)).foregroundStyle(ProfileView.gold)
                    }
                    Text(value).font(.app(22, .heavy)).foregroundStyle(Theme.text1)
                }
                Text(label).font(.app(12, .medium)).foregroundStyle(Theme.text3)
            }
            .padding(.horizontal, 16)
            .frame(minWidth: 96, minHeight: 60, alignment: .leading)
            .glass(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    // MARK: Taste

    /// What the stars add up to: the genres that do best, and how generous the user is with them.
    private var insights: some View {
        HStack(alignment: .top, spacing: 18) {
            panel(loc(.yourTaste)) {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(app.ratings.favoriteGenreNames().enumerated()), id: \.offset) { _, genre in
                        HStack(spacing: 14) {
                            Text(genre.name)
                                .font(.app(15, .semibold))
                                .foregroundStyle(Theme.text1)
                                .lineLimit(1)
                                .frame(width: 120, alignment: .leading)
                            GeometryReader { geo in
                                Capsule().fill(Color.white.opacity(0.1))
                                Capsule()
                                    .fill(LinearGradient(colors: [Color(hex: 0xFF6A4D), Theme.accent], startPoint: .leading, endPoint: .trailing))
                                    .frame(width: max(8, geo.size.width * genre.strength))
                            }
                            .frame(height: 8)
                        }
                    }
                }
            }
            panel(loc(.howYouRate)) {
                let spread = app.ratings.spread()
                let most = max(1, spread.max() ?? 1)
                HStack(alignment: .bottom, spacing: 16) {
                    ForEach(Array(spread.enumerated()), id: \.offset) { index, count in
                        VStack(spacing: 6) {
                            Text(count > 0 ? loc.num(count) : " ").font(.app(13, .semibold)).foregroundStyle(Theme.text2)
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .fill(count > 0 ? AnyShapeStyle(LinearGradient(colors: [Color(hex: 0xFFE27A), ProfileView.gold], startPoint: .top, endPoint: .bottom)) : AnyShapeStyle(Color.white.opacity(0.1)))
                                .frame(width: 38, height: max(6, 70 * CGFloat(count) / CGFloat(most)))
                            HStack(spacing: 3) {
                                Text(loc.num(index + 1)).font(.app(13, .semibold))
                                Image(systemName: "star.fill").font(.system(size: 9, weight: .bold))
                            }
                            .foregroundStyle(Theme.text3)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func panel<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(.app(15, .semibold)).foregroundStyle(Theme.text3)
            content()
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, minHeight: 190, maxHeight: 190, alignment: .topLeading)
        .glass(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // MARK: Shelves

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(loc(.noRatingsYet)).font(.app(22, .bold)).foregroundStyle(Theme.text1)
            Text(loc(.rateHint)).font(.app(16)).foregroundStyle(Theme.text2)
        }
    }

    private func shelf(_ title: String, _ posts: [Post]) -> some View {
        Shelf(title: title, posts: posts) { post in
            PosterCard(post: post) { nav.open(post) }
        }
    }

    private var rated: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(loc(.yourRating)).font(.app(20, .bold)).foregroundStyle(Theme.text1)
            LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                ForEach(app.ratings.newestFirst) { rating in
                    if let post = rating.post {
                        PosterCard(post: post, stars: rating.stars) { nav.open(post) }
                    }
                }
            }
        }
    }

    // MARK: Data

    private func refresh() async {
        guard let feed = try? await app.home() else { return }
        // The same title can sit in two sections of the feed; it is wanted once.
        var seen = Set<Int>()
        let pool = feed.sections.flatMap(\.posts).filter { seen.insert($0.id).inserted }
        if app.ratings.isEmpty {
            // Nothing to go on yet: offer the best-regarded titles to kick things off.
            starters = Array(pool.filter { !$0.comingSoon && $0.imdbScore != nil }
                .sorted { ($0.imdbScore ?? 0) > ($1.imdbScore ?? 0) }
                .prefix(14))
        } else {
            picks = app.ratings.recommend(from: pool, limit: 16)
        }
    }

    /// The lists the user keeps on 30nama itself. A guest has none.
    private func loadLists() async {
        async let watch = app.myList("watchlist")
        async let liked = app.myList("favorite")
        async let sizes = app.myListSizes()
        (watchlist, favorites, listSizes) = await (watch, liked, sizes)
        upcoming = await app.upcoming()
    }
}
