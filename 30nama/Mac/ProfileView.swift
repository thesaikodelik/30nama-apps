import SwiftUI
import UniformTypeIdentifiers

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

    @State private var confirmingSignOut = false
    @State private var showingNotifications = false
    /// The name the member gave the site; the app API sends only the username.
    @State private var siteProfile: SiteProfile?
    /// The site has answered (or there was no one to ask): the line can be picked.
    @State private var profileAsked = false

    /// A line from a film, picked once for this time the app is open, and again only when the name
    /// it is said to arrives (the site answers after the page has opened).
    @State private var line: MovieLine?
    @State private var filmOnSite: Post?
    @AppStorage("displayName") private var typedName = ""

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
            .scrollIndicators(.never)
        }
        .task(id: app.ratings) { await refresh() }
        .alert(loc(.signOutTitle), isPresented: $confirmingSignOut) {
            Button(loc(.signOut), role: .destructive) { app.signOut() }
            Button(loc(.cancel), role: .cancel) {}
        } message: {
            Text(loc(.signOutBody))
        }
        .task(id: "\(app.user?.id ?? "")|\(app.listVersion)") { await loadLists() }
        .task(id: app.user?.name) {
            siteProfile = nil
            profileAsked = false
            defer { profileAsked = true }
            guard let username = app.user?.name, app.api.hasSite else { return }
            siteProfile = (try? await app.api.site(SiteQuery.profile(username: username), background: true)).map(SiteProfile.init(site:))
        }
    }

    /// The user's own ratings as a spreadsheet, to keep or take elsewhere.
    private func exportRatings() {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "30nama-ratings.csv"
        panel.allowedContentTypes = [.commaSeparatedText]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try app.ratings.csv().write(to: url, atomically: true, encoding: .utf8)
            app.showToast(loc(.exportDone))
        } catch {
            app.showToast(loc(.error))
        }
    }

    // MARK: Header

    /// One card instead of a stack of lines: the picture, and beside it the name, the username and
    /// the line from a film; across from them the counts as figures, with the buttons under them.
    private var header: some View {
        HStack(alignment: .center, spacing: 22) {
                avatar
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .center, spacing: 12) {
                        Text(displayName ?? loc(.guestAccount))
                            .font(.app(34, .heavy))
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
                    if app.user == nil {
                        // A guest otherwise had no way in from here but the settings.
                        Text(loc(.signInHint))
                            .font(.app(14))
                            .foregroundStyle(Theme.text2)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: 460, alignment: .leading)
                    } else if displayName != app.user?.name, let username = app.user?.name {
                        // The username, under a name given instead of it.
                        Text(username).font(.app(13, .medium)).foregroundStyle(Theme.text3)
                    }
                    greeting.padding(.top, 10)
                }
                Spacer(minLength: 16)
                VStack(alignment: .trailing, spacing: 18) {
                    figures
                    buttons
                }
        }
        // A card of its own, of the top bar's glass, so who it is stands apart from the rows below.
        .padding(.horizontal, 28)
        .padding(.vertical, 24)
        .lens(RoundedRectangle(cornerRadius: 28, style: .continuous))
    }

    /// The name to show: the one the member gave the site, or failing that (a private profile)
    /// the one they gave this Mac, or failing that the username.
    private var displayName: String? {
        siteProfile?.fullName ?? ownName.map { $0 } ?? app.user?.name
    }

    /// The name written in Settings, when there is one.
    private var ownName: String? {
        let name = typedName.trimmingCharacters(in: .whitespaces)
        return name.isEmpty || app.user == nil ? nil : name
    }

    /// First and family name for the line from a film: the site's, or the one written here.
    private var names: (first: String?, family: String?) {
        if let site = siteProfile, site.fullName != nil { return (site.firstName, site.lastName) }
        guard let own = ownName else { return (nil, nil) }
        let words = own.split(separator: " ").map(String.init)
        return (words.first, words.count > 1 ? words.dropFirst().joined(separator: " ") : nil)
    }

    /// The counts as figures with a word under each: the lists kept on 30nama, what was rated,
    /// and the days of the plan left (its last day under the pointer).
    private var figures: some View {
        HStack(alignment: .top, spacing: 28) {
            ForEach(stats.indices, id: \.self) { i in
                VStack(spacing: 2) {
                    HStack(spacing: 5) {
                        if let symbol = stats[i].symbol {
                            Image(systemName: symbol).font(.system(size: 13, weight: .bold)).foregroundStyle(ProfileView.gold)
                        }
                        Text(stats[i].value).font(.app(26, .heavy)).foregroundStyle(Theme.text1)
                    }
                    Text(stats[i].label).font(.app(12, .medium)).foregroundStyle(Theme.text3).lineLimit(1)
                }
                .help(stats[i].hint ?? "")
            }
        }
    }

    @ViewBuilder
    private var buttons: some View {
        HStack(spacing: 10) {
            if app.user == nil {
                Button { app.requestSignIn() } label: {
                    Label(loc(.signIn), systemImage: "person.crop.circle.badge.checkmark")
                }
                .buttonStyle(PrimaryButtonStyle())
            } else {
                if !app.ratings.isEmpty {
                    Button { exportRatings() } label: {
                        Label(loc(.exportRatings), systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(GlassButtonStyle(height: 34))
                }
                // The bar's bell shows only while something is new; the older ones are here.
                if !(app.notifications?.items.isEmpty ?? true) {
                    Button { showingNotifications = true } label: {
                        Label(loc(.notifications), systemImage: "bell")
                    }
                    .buttonStyle(GlassButtonStyle(height: 34))
                    .popover(isPresented: $showingNotifications, arrowEdge: .bottom) {
                        NotificationPanel { showingNotifications = false }
                            .environment(app)
                            .environment(\.loc, loc)
                            .environment(\.layoutDirection, loc.lang == .en ? .leftToRight : .rightToLeft)
                    }
                }
                Button { NSWorkspace.shared.open(ProfileView.pricing) } label: {
                    Label(loc(.renewSubscription), systemImage: "creditcard")
                }
                .buttonStyle(GlassButtonStyle(height: 34))
                Button { confirmingSignOut = true } label: {
                    Label(loc(.signOut), systemImage: "rectangle.portrait.and.arrow.right")
                }
                .buttonStyle(GlassButtonStyle(height: 34))
            }
        }
    }

    /// 30nama's own page for buying or renewing a plan.
    private static let pricing = URL(string: "https://30nama.com/pricing")!

    /// A line from a film, in the film's own English, with their first name where a name or the
    /// word for them was; under it the film, its year and its director, which open the film's page
    /// when 30nama has it. Never the username: a line said to an account name means nothing.
    private var greeting: some View {
        Group {
            if let line {
                Quote(line: line, post: filmOnSite) { nav.open($0) }
            } else {
                Color.clear.frame(height: 1)
            }
        }
        // English inside a Persian or Arabic page: written left to right, kept to the same edge as
        // the name above it.
        .environment(\.layoutDirection, .leftToRight)
        // Outside the left-to-right part, so "leading" is the page's own: right in Persian.
        .frame(maxWidth: 620, alignment: .leading)
        .task(id: "\(profileAsked)|\(names.first ?? "")") {
            guard profileAsked else { return }
            // Once per opening of the app, unless the name has only now come.
            if let kept = ProfileView.kept, kept.name == names.first {
                line = kept.line
                filmOnSite = kept.post
                return
            }
            var generator = SystemRandomNumberGenerator()
            let picked = MovieLines.pick(name: names.first, familyName: names.family, using: &generator)
            line = picked
            filmOnSite = nil
            ProfileView.kept = (names.first, picked, nil)
            // Quietly, after the page's own requests: whether 30nama has the film.
            for query in picked.searchQueries {
                guard let found = try? await app.api.get("search", ["query": query, "streamonly": "0", "page": "1"], background: true),
                      !Task.isCancelled else { continue }
                if let post = picked.match(in: PostPage(found).posts) {
                    filmOnSite = post
                    ProfileView.kept = (names.first, picked, post)
                    break
                }
            }
        }
    }

    /// The line of this opening of the app, the name it was picked for, and its film on 30nama.
    @MainActor private static var kept: (name: String?, line: MovieLine, post: Post?)?

    /// The counts: the lists kept on 30nama, what was rated and how generously, and the plan's days.
    private var stats: [(value: String, label: String, symbol: String?, hint: String?)] {
        var out: [(value: String, label: String, symbol: String?, hint: String?)] = []
        if !watchlist.isEmpty { out.append((loc.num(listSizes["watchlist"] ?? watchlist.count), loc(.watchlist), nil, nil)) }
        if !favorites.isEmpty { out.append((loc.num(listSizes["favorite"] ?? favorites.count), loc(.favorites), nil, nil)) }
        if !app.ratings.isEmpty {
            out.append((loc.num(app.ratings.count), loc(.ratedCount), nil, nil))
            if let average = app.ratings.average { out.append((loc.num(Fmt.score(average)), loc(.averageRating), "star.fill", nil)) }
        }
        if let user = app.user, let days = user.daysLeft() {
            out.append((loc.num(days), loc(.subscriptionDays), nil, loc.subscriptionLine(user)))
        }
        return out
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

/// The line from a film as a quotation: a large quotation mark in the scene's colour, the words in
/// a serif, and under them a signature, the film, its year and who directed it. When 30nama has the
/// film the signature opens its page.
private struct Quote: View {
    let line: MovieLine
    let post: Post?
    let open: (Post) -> Void

    @Environment(\.hue) private var hue
    @State private var hovering = false

    var body: some View {
        let tint = hue ?? Theme.accent
        let lit = hovering && post != nil
        HStack(alignment: .center, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text(line.text)
                    .font(.system(size: 19, weight: .regular, design: .serif))
                    .italic()
                    .foregroundStyle(Theme.text1.opacity(0.92))
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 26)
                    .overlay(alignment: .topLeading) {
                        Text("\u{201C}")
                            .font(.system(size: 50, weight: .bold, design: .serif))
                            .foregroundStyle(tint.opacity(0.75))
                            .offset(x: -2, y: -12)
                            .accessibilityHidden(true)
                    }
                Button { if let post { open(post) } } label: {
                    HStack(spacing: 8) {
                        Capsule().fill(tint).frame(width: 16, height: 2)
                        Text("\(line.film.uppercased()) · \(String(line.year)) · \(line.directors.joined(separator: ", "))")
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(0.8)
                            .foregroundStyle(lit ? tint : Theme.text3)
                            .underline(lit)
                            .lineLimit(1)
                    }
                    .padding(.leading, 26)
                }
                .buttonStyle(.plain)
                .disabled(post == nil)
                .onHover { hover($0) }
            }
        }
        .animation(.easeOut(duration: 0.15), value: lit)
    }

    private func hover(_ inside: Bool) {
        guard post != nil, inside != hovering else { return }
        hovering = inside
        if inside { NSCursor.pointingHand.push() } else { NSCursor.pop() }
    }
}
