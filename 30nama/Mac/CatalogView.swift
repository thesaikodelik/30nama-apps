import SwiftUI

/// Every title under one heading (movies, a genre), as a wall of posters that grows as it is scrolled.
struct CatalogView: View {
    let slug: String
    let title: String

    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var posts: [Post] = []
    @State private var page = 0
    @State private var pages = 1
    @State private var total: Int?
    @State private var sort = Sort.updated
    @State private var loading = false
    @State private var failed = false
    @State private var filter = Filter()
    @State private var options: FilterOptions?

    /// The catalogues that can be narrowed: the search behind the filters has to be told which
    /// kind of title to stay with, and a genre's own page has no such kind.
    private var type: String? { ["movies": "movie", "series": "series", "anime": "anime"][slug] }

    /// The orders 30nama's app API keeps to, and "newest". The app API has no newest: any order
    /// it does not know brings one fixed list, which was shown as "newest" until 1.2.4. The
    /// website's own API has it, by post id, so it is offered when the build has the site's key.
    /// Recently updated is the website's own first choice.
    enum Sort: String, CaseIterable {
        case updated = "update", newest = "id", popular = "favorite"

        var label: S {
            switch self {
            case .popular: return .popular
            case .updated: return .updated
            case .newest: return .newest
            }
        }
    }

    private var sorts: [Sort] { Sort.allCases.filter { $0 != .newest || (type != nil && app.api.hasSite) } }

    private let columns = [GridItem(.adaptive(minimum: Mac.posterWidth, maximum: Mac.posterWidth + 40), spacing: 20)]

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 26) {
                    header
                    if type != nil, let options { filters(options) }
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                        ForEach(posts) { post in
                            PosterCard(post: post) { nav.open(post) }
                                .onAppear {
                                    // Three rows or so before the end, the next page is asked for, so it
                                    // is in by the time the wall runs out.
                                    if posts.suffix(Mac.prefetch).contains(where: { $0.id == post.id }) { Task { await loadNext() } }
                                }
                        }
                    }
                    footer
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 96)
                .padding(.bottom, 60)
            }
            .scrollIndicators(.hidden)
        }
        .task(id: "\(sort.rawValue)|\(app.contentVersion)|\(filter.parameters(type: type))") {
            posts = []
            page = 0
            pages = 1
            total = nil
            loading = false
            await loadNext()
        }
        .task(id: app.contentVersion) {
            if type != nil { options = try? await app.filterOptions() }
        }
        // What the newest list cannot narrow by is let go, not kept and silently ignored.
        .onChange(of: sort) { _, sort in
            if sort == .newest { filter = Filter(genre: filter.genre) }
        }
    }

    /// Genre, country, lowest score and decade. With any of them set, the wall is filled by
    /// 30nama's advanced search instead of the plain archive.
    private func filters(_ options: FilterOptions) -> some View {
        HStack(spacing: 8) {
            FilterMenu(title: loc(.genres), choices: options.genres, selection: $filter.genre)
            // The site's newest list narrows by genre only.
            if sort != .newest {
                FilterMenu(title: loc(.country), choices: options.countries, selection: $filter.country)
                FilterMenu(title: loc(.minScore), choices: options.scores, selection: $filter.score)
                FilterMenu(title: loc(.decade),
                           choices: options.decades.map { FilterOptions.Choice(name: loc.num("\($0)–\($0 + 9)"), value: "\($0)") },
                           selection: Binding(get: { filter.decade.map(String.init) }, set: { filter.decade = $0.flatMap(Int.init) }))
            }
            if !filter.isEmpty {
                Button { filter = Filter() } label: {
                    Image(systemName: "xmark.circle.fill").font(.system(size: 16)).foregroundStyle(Theme.text3)
                }
                .buttonStyle(.plain)
                .help(loc(.reset))
                .accessibilityLabel(loc(.reset))
            }
            Spacer(minLength: 0)
        }
    }

    private var header: some View {
        HStack(alignment: .firstTextBaseline, spacing: 14) {
            Text(title).font(.app(34, .bold)).foregroundStyle(Theme.text1)
            if let total {
                Text("\(Fmt.count(total, lang: loc.lang)) \(loc(.results))")
                    .font(.app(15, .medium))
                    .foregroundStyle(Theme.text3)
            }
            Spacer(minLength: 0)
            HStack(spacing: 8) {
                ForEach(sorts, id: \.self) { option in
                    PillChoice(title: loc(option.label), selected: sort == option) { sort = option }
                }
            }
        }
    }

    @ViewBuilder
    private var footer: some View {
        if failed {
            Button(loc(.retry)) { Task { await loadNext() } }
                .buttonStyle(GlassButtonStyle())
                .frame(maxWidth: .infinity)
        } else if loading {
            ProgressView().controlSize(.regular).frame(maxWidth: .infinity).padding(.vertical, 20)
        }
    }

    private func loadNext() async {
        guard !loading, page < pages else { return }
        loading = true
        failed = false
        // A sort change restarts the list; a response for the old order must not touch it.
        let requested = sort
        defer { if requested == sort { loading = false } }
        let narrowed = filter
        do {
            if requested == .newest, let type {
                let path = SiteQuery.newest(kind: type, genre: narrowed.genre, streamOnly: true, page: page + 1)
                let next = PostPage(site: try await app.api.site(path))
                guard requested == sort, narrowed == filter, !Task.isCancelled else { return }
                let known = Set(posts.map(\.id))
                posts += next.posts.filter { !known.contains($0.id) }
                page = next.page
                pages = next.pages
                // The site's lists give pages, not a count.
                total = nil
                return
            }
            let result = narrowed.isEmpty
                ? try await app.api.get("archive", [
                    "slugs": slug, "streamonly": "1", "page": String(page + 1),
                    "orderby": requested.rawValue, "order": "desc",
                ])
                : try await app.api.get("advanced_search", [
                    "parameters": narrowed.parameters(type: type), "page": String(page + 1),
                    "orderby": requested.rawValue, "order": "DESC",
                ])
            guard requested == sort, narrowed == filter, !Task.isCancelled else { return }
            let next = PostPage(result)
            let known = Set(posts.map(\.id))
            posts += next.posts.filter { !known.contains($0.id) }
            page = next.page
            pages = next.pages
            total = next.total
        } catch {
            if requested == sort, !Task.isCancelled { failed = true }
        }
    }
}

/// One filter: a pill that names the choice made, and opens the list of choices.
struct FilterMenu: View {
    let title: String
    let choices: [FilterOptions.Choice]
    @Binding var selection: String?

    @Environment(\.loc) private var loc
    @State private var hovering = false

    private var chosen: FilterOptions.Choice? { choices.first { $0.value == selection } }

    var body: some View {
        SwiftUI.Menu {
            Picker(title, selection: $selection) {
                Text(loc(.allChoices)).tag(String?.none)
                ForEach(choices) { choice in
                    Text(choice.name).tag(Optional(choice.value))
                }
            }
            .pickerStyle(.inline)
        } label: {
            HStack(spacing: 6) {
                Text(chosen.map { "\(title): \($0.name)" } ?? title).font(.app(13, .semibold))
                Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold)).opacity(0.6)
            }
            .foregroundStyle(chosen != nil ? Theme.focusInk : Theme.text1)
            .padding(.horizontal, 13)
            .frame(height: 30)
            .background(Capsule(style: .continuous).fill(chosen != nil ? Color.white : Color.white.opacity(hovering ? 0.2 : 0.1)))
            .contentShape(Capsule(style: .continuous))
        }
        .menuStyle(.button)
        .buttonStyle(.plain)
        .menuIndicator(.hidden)
        .fixedSize()
        .onHover { hovering = $0 }
    }
}

/// Genres and categories as tiles; each opens its own wall of posters.
struct BrowseView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var menu: Menu?
    @State private var failed = false

    private let columns = [GridItem(.adaptive(minimum: 210, maximum: 300), spacing: 16)]

    var body: some View {
        ZStack {
            AmbientBackground()
            if let menu {
                ScrollView(.vertical) {
                    VStack(alignment: .leading, spacing: 34) {
                        // What only the 30nama website has, through its own API.
                        if app.api.hasSite {
                            HStack(spacing: 16) {
                                SiteTile(title: loc(.weeklySchedule), symbol: "calendar") { nav.open(.schedule) }
                                SiteTile(title: loc(.topLists), symbol: "trophy") { nav.open(.topLists) }
                            }
                        }
                        if !menu.genres.isEmpty { group(loc(.genres), menu.genres) }
                        if !menu.categories.isEmpty { group(loc(.categories), menu.categories) }
                    }
                    .padding(.horizontal, Mac.edge)
                    .padding(.top, 96)
                    .padding(.bottom, 60)
                }
                .scrollIndicators(.hidden)
            } else if failed {
                ErrorState { Task { await load() } }
            } else {
                ProgressView().controlSize(.large)
            }
        }
        .task(id: app.contentVersion) { await load() }
    }

    private func group(_ title: String, _ entries: [MenuEntry]) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title).font(.app(26, .bold)).foregroundStyle(Theme.text1)
            LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                ForEach(entries) { entry in
                    BrowseTile(entry: entry) { nav.open(.catalog(slug: entry.slug, title: entry.name)) }
                }
            }
        }
    }

    private func load() async {
        failed = false
        do {
            menu = try await app.menu()
        } catch {
            if menu == nil { failed = true }
        }
    }
}

private struct BrowseTile: View {
    let entry: MenuEntry
    let action: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        Button(action: action) {
            ZStack(alignment: .bottomLeading) {
                Theme.surface2
                RemoteImage(url: entry.thumb ?? entry.full, maxPixel: Int(300 * Display.scale))
                LinearGradient(colors: [.black.opacity(0.1), .black.opacity(0.8)], startPoint: .top, endPoint: .bottom)
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.name).font(.app(17, .bold)).foregroundStyle(.white).lineLimit(1)
                    if let count = entry.count, count > 0 {
                        Text("\(Fmt.count(count, lang: loc.lang)) \(loc(.results))").font(.app(12, .medium)).foregroundStyle(.white.opacity(0.7))
                    }
                }
                .padding(12)
            }
            .frame(height: 118)
            .clipShape(shape)
            .overlay { shape.strokeBorder(Color.white.opacity(hovering ? 0.9 : 0.08), lineWidth: hovering ? 2 : 1) }
            .scaleEffect(hovering ? 1.03 : 1)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(Theme.focusSpring, value: hovering)
    }
}

/// Search as you type, with what is popular standing in while there is nothing to show.
struct SearchView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var query = ""
    @State private var results: [Post] = []
    @State private var heading = ""
    @State private var note: String?
    @State private var searching = false
    @FocusState private var typing: Bool
    /// Only what can be watched here and now, as 30nama's own apps offer.
    @AppStorage("searchStreamOnly") private var streamOnly = false

    private let columns = [GridItem(.adaptive(minimum: Mac.posterWidth, maximum: Mac.posterWidth + 40), spacing: 20)]

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 26) {
                    VStack(spacing: 14) {
                        field
                        HStack(spacing: 8) {
                            PillChoice(title: loc(.streamOnly), selected: streamOnly) { streamOnly.toggle() }
                            Button { nav.open(.advancedSearch()) } label: {
                                Label(loc(.advancedSearch), systemImage: "slider.horizontal.3")
                            }
                            .buttonStyle(GlassButtonStyle(height: 30))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    if let note {
                        Text(note).font(.app(16, .medium)).foregroundStyle(Theme.text2)
                    }
                    if !results.isEmpty {
                        Text(heading).font(.app(22, .bold)).foregroundStyle(Theme.text1)
                        LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                            ForEach(results) { post in
                                PosterCard(post: post) { nav.open(post) }
                            }
                        }
                    }
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 96)
                .padding(.bottom, 60)
            }
            .scrollIndicators(.hidden)
        }
        .task(id: "\(query)|\(streamOnly)") { await run(query.trimmingCharacters(in: .whitespaces)) }
        .onAppear { typing = true }
    }

    private var field: some View {
        HStack(spacing: 12) {
            if searching {
                ProgressView().controlSize(.small).frame(width: 20)
            } else {
                Image(systemName: "magnifyingglass").font(.system(size: 17, weight: .semibold)).foregroundStyle(Theme.text3).frame(width: 20)
            }
            TextField(loc(.searchPrompt), text: $query)
                .textFieldStyle(.plain)
                .font(.app(19, .medium))
                .foregroundStyle(Theme.text1)
                .focused($typing)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").font(.system(size: 17)).foregroundStyle(Theme.text3)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(loc(.clearSearch))
            }
        }
        .padding(.horizontal, 20)
        // Left to right in every language: titles are searched by their English names.
        .environment(\.layoutDirection, .leftToRight)
        .frame(height: 54)
        .frame(maxWidth: 640)
        .glass(Capsule(style: .continuous))
    }

    private func run(_ text: String) async {
        guard text.count >= 2 else {
            await showPopular(note: nil)
            return
        }
        // Wait for typing to pause; a newer keystroke cancels this task.
        try? await Task.sleep(for: .milliseconds(450))
        guard !Task.isCancelled else { return }
        searching = true
        defer { searching = false }
        do {
            let page = PostPage(try await app.api.get("search", ["query": text, "streamonly": streamOnly ? "1" : "0", "page": "1"]))
            guard !Task.isCancelled else { return }
            if page.posts.isEmpty {
                await showPopular(note: "\(loc(.nothingFor)) «\(text)»")
            } else {
                note = nil
                heading = loc(.topResults)
                results = page.posts
            }
        } catch {
            guard !Task.isCancelled else { return }
            await showPopular(note: "\(loc(.error)). \(loc(.tryAgain))")
        }
    }

    /// With nothing to show for the query, offer what is popular instead of an empty screen.
    private func showPopular(note: String?) async {
        let feed = try? await app.home()
        guard !Task.isCancelled else { return }
        self.note = note
        heading = loc(.popularNow)
        let suggested = feed?.posts("suggested") ?? []
        results = Array((suggested.isEmpty ? feed?.posts("movies") ?? [] : suggested).prefix(24))
    }
}
