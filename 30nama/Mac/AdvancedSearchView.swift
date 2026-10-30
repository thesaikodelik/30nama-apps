import SwiftUI

/// 30nama's catalogue narrowed by everything its advanced search acts on: several genres at once,
/// type, country, language, network, lowest score, age rating, years, director, actor, x265 and 3D.
struct AdvancedSearchView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var choices: SearchChoices?
    @State private var query: AdvancedQuery
    @State private var director: String
    @State private var actor: String
    @State private var posts: [Post] = []
    @State private var page = 0
    @State private var pages = 1
    @State private var total: Int?
    @State private var loading = false
    @State private var failed = false

    private let columns = [GridItem(.adaptive(minimum: Mac.posterWidth, maximum: Mac.posterWidth + 40), spacing: 20)]

    /// - Parameter start: What the search opens with: nothing chosen, or a director whose name
    ///   was clicked. Set before the page is first drawn, so only that search is sent.
    init(start: AdvancedQuery = AdvancedQuery()) {
        _query = State(initialValue: start)
        _director = State(initialValue: start.director ?? "")
        _actor = State(initialValue: start.actor ?? "")
    }

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 22) {
                    header
                    if let choices { filters(choices) }
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                        ForEach(posts) { post in
                            PosterCard(post: post) { nav.open(post) }
                                .onAppear { if posts.suffix(Mac.prefetch).contains(where: { $0.id == post.id }) { Task { await loadNext() } } }
                        }
                    }
                    footer
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 96)
                .padding(.bottom, 60)
            }
            .scrollIndicators(.never)
        }
        .task(id: app.contentVersion) { choices = try? await app.searchChoices() }
        .task(id: query) {
            posts = []
            page = 0
            pages = 1
            total = nil
            loading = false
            await loadNext()
        }
        // The name is sent once typing pauses, not with every letter.
        .task(id: director) {
            try? await Task.sleep(for: .milliseconds(600))
            guard !Task.isCancelled else { return }
            let name = director.trimmingCharacters(in: .whitespaces)
            query.director = name.isEmpty ? nil : name
        }
        .task(id: actor) {
            try? await Task.sleep(for: .milliseconds(600))
            guard !Task.isCancelled else { return }
            let name = actor.trimmingCharacters(in: .whitespaces)
            query.actor = name.isEmpty ? nil : name
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 14) {
                Text(loc(.advancedSearch)).font(.app(34, .bold)).foregroundStyle(Theme.text1)
                if let total {
                    Text("\(Fmt.count(total, lang: loc.lang)) \(loc(.results))").font(.app(15, .medium)).foregroundStyle(Theme.text3)
                }
                Spacer(minLength: 0)
            }
            Text(loc(.advancedHint)).font(.app(14)).foregroundStyle(Theme.text3)
        }
    }

    /// Two lines, each with a side at the start and one at the end (the owner, after Twitter
    /// feedback 3): the menus, and across from them the names to search by; then the years and
    /// switches, and across from them the sorts.
    private func filters(_ c: SearchChoices) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 16) {
                // Wraps onto a second line in a narrow window rather than running off the side.
                FlowLayout(spacing: 8) {
                    GenreMenu(choices: c.genres, selection: $query.genres)
                    FilterMenu(title: loc(.kind), choices: c.types, selection: $query.type)
                    FilterMenu(title: loc(.country), choices: c.countries, selection: $query.country)
                    FilterMenu(title: loc(.originalLanguage), choices: c.languages, selection: $query.language)
                    FilterMenu(title: loc(.network), choices: c.networks, selection: $query.network)
                    FilterMenu(title: loc(.minScore), choices: c.scores, selection: $query.score)
                    FilterMenu(title: loc(.ageRating), choices: c.ages, selection: $query.age)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 8) {
                    nameField(loc(.directorName), symbol: "person.crop.rectangle", text: $director)
                    nameField(loc(.actorName), symbol: "theatermasks", text: $actor)
                }
            }
            HStack(spacing: 16) {
                FlowLayout(spacing: 8) {
                    FilterMenu(title: loc(.years), choices: years(c), selection: yearBinding(\.fromYear))
                    FilterMenu(title: loc(.hoursTo), choices: years(c), selection: yearBinding(\.toYear))
                    PillChoice(title: "x265", selected: query.x265) { query.x265.toggle() }
                    PillChoice(title: "3D", selected: query.threeD) { query.threeD.toggle() }
                    PillChoice(title: loc(.streamOnly), selected: query.streamOnly) { query.streamOnly.toggle() }
                    if query.count > 0 {
                        Button {
                            director = ""
                            actor = ""
                            query = AdvancedQuery(streamOnly: query.streamOnly, sort: query.sort)
                        } label: {
                            Label(loc(.reset), systemImage: "xmark.circle.fill").font(.app(13, .semibold)).foregroundStyle(Theme.text3)
                                .frame(height: 30)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 8) {
                    ForEach(AdvancedQuery.sorts, id: \.self) { sort in
                        PillChoice(title: sortName(sort), selected: query.sort == sort) { query.sort = sort }
                    }
                }
            }
        }
    }

    /// A name to search by. Left to right in every language, as the search box is: the names are
    /// written in English (Twitter feedback 2).
    private func nameField(_ placeholder: String, symbol: String, text: Binding<String>) -> some View {
        HStack(spacing: 8) {
            Image(systemName: symbol).font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.text3)
            TextField(placeholder, text: text)
                .textFieldStyle(.plain)
                .font(.app(13, .medium))
                .foregroundStyle(Theme.text1)
                .frame(width: 150)
        }
        .padding(.horizontal, 13)
        .frame(height: 30)
        .background(Capsule(style: .continuous).fill(Color.white.opacity(0.1)))
        .environment(\.layoutDirection, .leftToRight)
    }

    private func sortName(_ sort: String) -> String {
        switch sort {
        case "update": return loc(.updated)
        case "year": return loc(.byYear)
        case "imdb-rate": return "IMDb"
        case "favorite": return loc(.popular)
        default: return loc(.relevance)
        }
    }

    private func years(_ c: SearchChoices) -> [FilterOptions.Choice] {
        c.years.reversed().map { FilterOptions.Choice(name: loc.num(String($0)), value: String($0)) }
    }

    private func yearBinding(_ key: WritableKeyPath<AdvancedQuery, Int?>) -> Binding<String?> {
        Binding(get: { query[keyPath: key].map(String.init) }, set: { query[keyPath: key] = $0.flatMap(Int.init) })
    }

    @ViewBuilder
    private var footer: some View {
        if failed {
            Button(loc(.retry)) { Task { await loadNext() } }
                .buttonStyle(GlassButtonStyle())
                .frame(maxWidth: .infinity)
        } else if loading {
            ProgressView().controlSize(.regular).frame(maxWidth: .infinity).padding(.vertical, 20)
        } else if posts.isEmpty, total != nil {
            Text(loc(.noMatches)).font(.app(16, .medium)).foregroundStyle(Theme.text2).frame(maxWidth: .infinity).padding(.vertical, 30)
        }
    }

    private func loadNext() async {
        guard !loading, page < pages else { return }
        loading = true
        failed = false
        let asked = query
        defer { if asked == query { loading = false } }
        do {
            let result = try await app.api.get("advanced_search", asked.request(page: page + 1))
            guard asked == query, !Task.isCancelled else { return }
            let next = PostPage(result)
            let known = Set(posts.map(\.id))
            posts += next.posts.filter { !known.contains($0.id) }
            page = next.page
            pages = next.pages
            total = next.total
        } catch {
            if asked == query, !Task.isCancelled { failed = true }
        }
    }
}

/// Several genres at once: each one picked stays ticked, and the pill says how many.
private struct GenreMenu: View {
    let choices: [FilterOptions.Choice]
    @Binding var selection: [String]

    @Environment(\.loc) private var loc
    @State private var hovering = false

    var body: some View {
        let picked = choices.filter { selection.contains($0.value) }
        SwiftUI.Menu {
            if !selection.isEmpty {
                Button(loc(.allChoices)) { selection = [] }
                Divider()
            }
            ForEach(choices) { choice in
                Toggle(choice.name, isOn: Binding(
                    get: { selection.contains(choice.value) },
                    set: { on in
                        if on { selection.append(choice.value) } else { selection.removeAll { $0 == choice.value } }
                    }))
            }
        } label: {
            HStack(spacing: 6) {
                Text(picked.isEmpty ? loc(.genres) : picked.count == 1 ? "\(loc(.genres)): \(picked[0].name)" : "\(loc(.genres)): \(loc.num(picked.count))")
                    .font(.app(13, .semibold))
                Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold)).opacity(0.6)
            }
            .foregroundStyle(picked.isEmpty ? Theme.text1 : Theme.focusInk)
            .padding(.horizontal, 13)
            .frame(height: 30)
            .background(Capsule(style: .continuous).fill(picked.isEmpty ? Color.white.opacity(hovering ? 0.2 : 0.1) : Color.white))
            .contentShape(Capsule(style: .continuous))
        }
        .menuStyle(.button)
        .buttonStyle(.plain)
        .menuIndicator(.hidden)
        .fixedSize()
        .onHover { hovering = $0 }
    }
}

/// Lays its children in rows, starting a new row where the next would not fit.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(subviews, width: proposal.width ?? .infinity)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(0, rows.count - 1))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.items {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [(items: [Int], width: CGFloat, height: CGFloat)] {
        var rows: [(items: [Int], width: CGFloat, height: CGFloat)] = []
        var current: (items: [Int], width: CGFloat, height: CGFloat) = ([], 0, 0)
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = current.items.isEmpty ? size.width : current.width + spacing + size.width
            if needed > width, !current.items.isEmpty {
                rows.append(current)
                current = ([index], size.width, size.height)
            } else {
                current = (current.items + [index], needed, max(current.height, size.height))
            }
        }
        if !current.items.isEmpty { rows.append(current) }
        return rows
    }
}
