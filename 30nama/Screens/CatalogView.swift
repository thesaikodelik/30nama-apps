import SwiftUI

/// A paged poster grid for one category or genre, with sorting.
struct CatalogView: View {
    let slug: String
    let title: String

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var posts: [Post] = []
    @State private var page = 0
    @State private var pages = 1
    @State private var total: Int?
    @State private var sort = Sort.updated
    @State private var loading = false
    @State private var failed = false

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

    /// "movie", "series" or "anime" for the three catalogue tabs: the site's newest list is by kind.
    private var kind: String? { ["movies": "movie", "series": "series", "anime": "anime"][slug] }
    private var sorts: [Sort] { Sort.allCases.filter { $0 != .newest || (kind != nil && app.api.hasSite) } }

    private let columns = Array(repeating: GridItem(.fixed(Theme.posterWidth), spacing: 32), count: 7)

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 30) {
                HStack(alignment: .firstTextBaseline, spacing: 20) {
                    Text(title)
                        .font(.app(56, .bold))
                        .foregroundStyle(Theme.text1)
                    if let total {
                        Text("\(Fmt.count(total, lang: loc.lang)) \(loc(.results))")
                            .font(.app(24))
                            .foregroundStyle(Theme.text3)
                    }
                    Spacer()
                    HStack(spacing: 12) {
                        ForEach(sorts, id: \.self) { option in
                            Button(loc(option.label)) { sort = option }
                                .buttonStyle(PillButtonStyle(kind: .chip, selected: option == sort))
                        }
                    }
                    .focusSection()
                }

                if failed && posts.isEmpty {
                    ErrorState { Task { await loadNext() } }.frame(height: 600)
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 44) {
                        ForEach(posts) { post in
                            PosterLink(post: post, titled: true)
                                .onAppear { if post == posts[safe: posts.count - 15] || post == posts.last { Task { await loadNext() } } }
                        }
                        if loading {
                            ForEach(0..<(posts.isEmpty ? 14 : 7), id: \.self) { _ in
                                Skeleton(width: Theme.posterWidth, height: Theme.posterWidth * 1.5, radius: Theme.cardRadius)
                                    .padding(.bottom, 52)
                            }
                        }
                    }
                    .focusSection()
                }
            }
            .padding(.horizontal, Theme.edge + 4)
            .padding(.top, 20)
            .padding(.bottom, 80)
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
        .background(AmbientBackground())
        .overlay(alignment: .top) { TopShade() }
        .task(id: sort) {
            posts = []
            page = 0
            pages = 1
            total = nil
            loading = false
            await loadNext()
        }
    }

    private func loadNext() async {
        guard !loading, page < pages else { return }
        loading = true
        failed = false
        // A sort change restarts the list; a response for the old order must not touch it.
        let requested = sort
        defer { if requested == sort { loading = false } }
        do {
            if requested == .newest, let kind {
                let next = PostPage(site: try await app.api.site(SiteQuery.newest(kind: kind, genre: nil, streamOnly: true, page: page + 1)))
                guard requested == sort, !Task.isCancelled else { return }
                let known = Set(posts.map(\.id))
                posts += next.posts.filter { !known.contains($0.id) }
                page = next.page
                pages = next.pages
                // The site's lists give pages, not a count.
                total = nil
                return
            }
            let result = try await app.api.get("archive", [
                "slugs": slug, "streamonly": "1", "page": String(page + 1),
                "orderby": requested.rawValue, "order": "desc",
            ])
            guard requested == sort, !Task.isCancelled else { return }
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

/// Shade under the tab bar on scrolling screens. The bar is translucent, so without this, rows
/// passing beneath it show through and tangle with its labels.
struct TopShade: View {
    var body: some View {
        LinearGradient(
            stops: [
                .init(color: Theme.bg0, location: 0),
                .init(color: Theme.bg0, location: 0.6),
                .init(color: Theme.bg0.opacity(0), location: 1),
            ],
            startPoint: .top, endPoint: .bottom)
            .frame(height: 250)
            .ignoresSafeArea()
            .allowsHitTesting(false)
    }
}

/// Backdrop for list screens: the base colour with a faint warm glow from the top corner.
struct AmbientBackground: View {
    var body: some View {
        ZStack {
            Theme.bg0
            RadialGradient(colors: [Theme.accent.opacity(0.10), .clear], center: .topTrailing, startRadius: 0, endRadius: 1100)
            LinearGradient(colors: [Theme.bg1.opacity(0.9), Theme.bg0], startPoint: .top, endPoint: .bottom).opacity(0.5)
        }
        .ignoresSafeArea()
    }
}
