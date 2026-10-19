import SwiftUI

/// The titles of an actor clicked on a title page, newest first. Only the site API finds them
/// (by the person's IMDb id), so this page is reached only when the build has its key.
struct PersonView: View {
    let person: Person

    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var posts: [Post] = []
    @State private var page = 0
    @State private var pages = 1
    @State private var loading = false
    @State private var failed = false
    @State private var done = false

    private let columns = [GridItem(.adaptive(minimum: Mac.posterWidth, maximum: Mac.posterWidth + 40), spacing: 20)]

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 28) {
                    header
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
            .scrollIndicators(.hidden)
        }
        .task { await loadNext() }
    }

    private var header: some View {
        HStack(spacing: 20) {
            ZStack {
                Circle().fill(Theme.surface2)
                Image(systemName: "person.fill").font(.system(size: 40)).foregroundStyle(Theme.text3)
                if let face = person.poster {
                    RemoteImage(url: face.url(forWidth: 120), blurhash: face.blurhash, maxPixel: Int(120 * Display.scale))
                }
            }
            .frame(width: 120, height: 120)
            .clipShape(Circle())
            VStack(alignment: .leading, spacing: 6) {
                Text(person.name).font(.app(34, .bold)).foregroundStyle(Theme.text1)
                Text(loc(.filmography)).font(.app(16, .medium)).foregroundStyle(Theme.text3)
            }
            Spacer(minLength: 0)
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
        } else if posts.isEmpty, done {
            Text(loc(.noMatches)).font(.app(16, .medium)).foregroundStyle(Theme.text2).frame(maxWidth: .infinity).padding(.vertical, 30)
        }
    }

    private func loadNext() async {
        guard let imdb = person.imdb, !loading, page < pages else { return }
        loading = true
        failed = false
        defer { loading = false }
        do {
            let q = SiteQuery.filmography(imdb: imdb, page: page + 1)
            let next = PostPage(site: try await app.api.site(q.path, q.form))
            guard !Task.isCancelled else { return }
            let known = Set(posts.map(\.id))
            posts += next.posts.filter { !known.contains($0.id) }
            page = next.page
            pages = next.pages
            done = true
        } catch {
            if !Task.isCancelled { failed = true }
        }
    }
}
