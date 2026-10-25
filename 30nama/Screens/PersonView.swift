import SwiftUI

/// An actor chosen on a title's page: who they are, as the 30nama website has it (picture, names,
/// birth, the biography 30nama wrote), and their titles newest first. Only the site API has
/// this. It finds a person by 30nama's own id; a cast member without one is found by the IMDb
/// id instead, and then there are only the titles.
struct PersonView: View {
    let person: Person

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var details: PersonDetails?
    @State private var posts: [Post] = []
    @State private var page = 0
    @State private var pages = 1
    @State private var loading = false
    @State private var failed = false
    @State private var done = false
    @State private var wholeBiography = false

    private let columns = Array(repeating: GridItem(.fixed(Theme.posterWidth), spacing: 32), count: 7)

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 40) {
                header
                if done || !posts.isEmpty {
                    Text(loc(.filmography)).font(.app(38, .bold)).foregroundStyle(Theme.text1)
                }
                if failed && posts.isEmpty {
                    SiteErrorState { Task { await loadNext() } }.frame(height: 400)
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 44) {
                        ForEach(posts) { post in
                            PosterLink(post: post, titled: true)
                                .onAppear { if post == posts[safe: posts.count - 15] || post == posts.last { Task { await loadNext() } } }
                        }
                        if loading {
                            ForEach(0..<7, id: \.self) { _ in
                                Skeleton(width: Theme.posterWidth, height: Theme.posterWidth * 1.5, radius: Theme.cardRadius)
                                    .padding(.bottom, 52)
                            }
                        }
                    }
                    .focusSection()
                    if !loading, done, posts.isEmpty {
                        Text(loc(.noMatches)).font(.app(28, .medium)).foregroundStyle(Theme.text2)
                    }
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
        .task { await loadNext() }
    }

    // MARK: Who they are

    /// As the site lays it out: a card at the side with the picture and the facts, and the
    /// biography beside it.
    private var header: some View {
        HStack(alignment: .top, spacing: 56) {
            // Something has to take the focus as soon as the page opens: the titles come later,
            // and on a page with nothing focused the remote's Back closes the whole app.
            Button {} label: { card }
                .buttonStyle(CardButtonStyle(radius: Theme.tileRadius, scale: 1.03))
            VStack(alignment: .leading, spacing: 14) {
                Text(details?.name ?? person.name).font(.app(56, .bold)).foregroundStyle(Theme.text1)
                if let persian = details?.persianName, persian != details?.name {
                    Text(persian).font(.app(32, .medium)).foregroundStyle(Theme.text2)
                }
                if let bio = details?.biography, !bio.isEmpty {
                    biography(bio).padding(.top, 18)
                }
            }
            .frame(maxWidth: 1100, alignment: .leading)
            Spacer(minLength: 0)
        }
    }

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: Theme.tileRadius, style: .continuous)
        let face = details?.picture ?? person.poster
        return VStack(alignment: .leading, spacing: 0) {
            ZStack {
                Theme.surface2
                Image(systemName: "person.fill").font(.system(size: 80)).foregroundStyle(Theme.text3)
                if let face {
                    RemoteImage(url: face.url(forWidth: 360), blurhash: face.blurhash, maxPixel: Int(360 * UIScreen.main.scale))
                }
            }
            .frame(width: 360, height: 500)
            .clipped()
            if !facts.isEmpty {
                VStack(alignment: .leading, spacing: 18) {
                    ForEach(facts, id: \.self) { fact in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(fact.label).font(.app(20, .semibold)).foregroundStyle(Theme.text3)
                            Text(fact.value).font(.app(26, .medium)).foregroundStyle(Theme.text1)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(26)
                .frame(width: 360, alignment: .leading)
            }
        }
        .background(Color.white.opacity(0.05), in: shape)
        .clipShape(shape)
    }

    private struct Fact: Hashable {
        let label: String
        let value: String
    }

    /// The facts the site keeps, labelled. The site writes the values in Persian whatever the
    /// interface's language. The IMDb profile is a web page, which a television cannot open.
    private var facts: [Fact] {
        guard let d = details else { return [] }
        return [
            d.fullName.flatMap { $0 == d.name ? nil : Fact(label: loc(.fullName), value: $0) },
            d.birthdate.map { Fact(label: loc(.born), value: $0) },
            d.deathdate.map { Fact(label: loc(.died), value: $0) },
            d.age.map { Fact(label: loc(.personAge), value: $0) },
            d.profession.map { Fact(label: loc(.profession), value: $0) },
        ].compactMap { $0 }
    }

    /// The biography is written in Persian, so it reads right to left whatever the interface's
    /// language. Folded, its start is one row that opens the rest when pressed. Open, every
    /// paragraph is a row of its own, so the remote moves down it a paragraph at a time; text
    /// that cannot take the focus could not be scrolled to on a television.
    @ViewBuilder
    private func biography(_ paragraphs: [String]) -> some View {
        let long = paragraphs.joined().count > 1000
        VStack(alignment: .leading, spacing: 16) {
            if wholeBiography || !long {
                ForEach(Array(paragraphs.enumerated()), id: \.offset) { _, paragraph in
                    Button { if long { wholeBiography = false } } label: { line(paragraph) }
                        .buttonStyle(RowButtonStyle())
                }
            } else {
                Button { wholeBiography = true } label: {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(Array(SiteText.opening(of: paragraphs, letters: 500).enumerated()), id: \.offset) { _, paragraph in
                            line(paragraph)
                        }
                        Text(loc(.readMore)).font(.app(24, .bold)).opacity(0.7)
                    }
                    .padding(.vertical, 22)
                }
                .buttonStyle(RowButtonStyle())
            }
        }
        .environment(\.layoutDirection, .rightToLeft)
        .focusSection()
    }

    private func line(_ paragraph: String) -> some View {
        Text(paragraph)
            .font(.app(SiteText.isHeading(paragraph) ? 28 : 26, SiteText.isHeading(paragraph) ? .bold : .regular))
            .lineSpacing(8)
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Their titles

    private func loadNext() async {
        guard !loading, page < pages else { return }
        loading = true
        failed = false
        defer { loading = false }
        do {
            let next: PostPage
            if let id = person.siteID {
                let result = try await app.api.site(SiteQuery.person(id: id, page: page + 1))
                guard let found = PersonDetails(site: result) else { throw APIError.server("person") }
                if details == nil { details = found }
                next = found.titles
            } else if let imdb = person.imdb {
                let q = SiteQuery.filmography(imdb: imdb, page: page + 1)
                next = PostPage(site: try await app.api.site(q.path, q.form))
            } else {
                return
            }
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
