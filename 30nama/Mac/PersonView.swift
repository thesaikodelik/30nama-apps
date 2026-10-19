import SwiftUI

/// An actor clicked on a title page: who they are, as the 30nama website has it (picture, names,
/// birth, the biography 30nama wrote), and their titles newest first. Only the site API has this.
/// It finds a person by 30nama's own id; a cast member without one is found by the IMDb id
/// instead, and then there are only the titles.
struct PersonView: View {
    let person: Person

    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var details: PersonDetails?
    @State private var posts: [Post] = []
    @State private var page = 0
    @State private var pages = 1
    @State private var loading = false
    @State private var failed = false
    @State private var done = false
    @State private var wholeBiography = false

    private let columns = [GridItem(.adaptive(minimum: Mac.posterWidth, maximum: Mac.posterWidth + 40), spacing: 20)]

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 28) {
                    header
                    if done || !posts.isEmpty {
                        Text(loc(.filmography)).font(.app(22, .bold)).foregroundStyle(Theme.text1)
                    }
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

    // MARK: Who they are

    /// As the site lays it out: a card at the side with the picture and the facts, and the
    /// biography beside it in a column narrow enough to read.
    private var header: some View {
        HStack(alignment: .top, spacing: 36) {
            card
            VStack(alignment: .leading, spacing: 10) {
                Text(details?.name ?? person.name).font(.app(36, .bold)).foregroundStyle(Theme.text1)
                if let persian = details?.persianName, persian != details?.name {
                    Text(persian).font(.app(20, .medium)).foregroundStyle(Theme.text2)
                }
                if let bio = details?.biography, !bio.isEmpty {
                    biography(bio).padding(.top, 14)
                }
            }
            .frame(maxWidth: 760, alignment: .leading)
            Spacer(minLength: 0)
        }
        .animation(.easeOut(duration: 0.25), value: details == nil)
    }

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        let face = details?.picture ?? person.poster
        return VStack(alignment: .leading, spacing: 0) {
            ZStack {
                Theme.surface2
                Image(systemName: "person.fill").font(.system(size: 54)).foregroundStyle(Theme.text3)
                if let face {
                    RemoteImage(url: face.url(forWidth: 260), blurhash: face.blurhash, maxPixel: Int(260 * Display.scale))
                }
            }
            .frame(width: 260, height: 360)
            .clipped()
            if !facts.isEmpty || details?.imdb != nil {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(facts, id: \.self) { fact in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(fact.label).font(.app(12, .semibold)).foregroundStyle(Theme.text3)
                            Text(fact.value).font(.app(15, .medium)).foregroundStyle(Theme.text1)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    if let link = details?.imdb {
                        Button { NSWorkspace.shared.open(link) } label: {
                            Label(loc(.imdbProfile), systemImage: "arrow.up.right.square")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(GlassButtonStyle(height: 38))
                        .padding(.top, 4)
                    }
                }
                .padding(18)
                .frame(width: 260, alignment: .leading)
            }
        }
        .background(Color.white.opacity(0.05), in: shape)
        .clipShape(shape)
        .overlay { shape.strokeBorder(Color.white.opacity(0.08), lineWidth: 1) }
    }

    /// The facts the site keeps, labelled. The site writes the values in Persian whatever the
    /// interface's language.
    private struct Fact: Hashable {
        let label: String
        let value: String
    }

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
    /// language. Its short lines with no full stop are the headings 30nama put in it. Folded, it
    /// shows its start and fades out above "More".
    private func biography(_ paragraphs: [String]) -> some View {
        let long = paragraphs.joined().count > 1000
        let shown = wholeBiography || !long ? paragraphs : Self.opening(of: paragraphs)
        return VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array(shown.enumerated()), id: \.offset) { _, paragraph in
                    if Self.isHeading(paragraph) {
                        Text(paragraph).font(.app(17, .bold)).foregroundStyle(Theme.text1)
                    } else {
                        Text(paragraph).font(.app(16)).foregroundStyle(Theme.text2).lineSpacing(8)
                    }
                }
            }
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
            .mask {
                LinearGradient(stops: [.init(color: .black, location: 0),
                                       .init(color: .black, location: wholeBiography || !long ? 1 : 0.6),
                                       .init(color: .black.opacity(wholeBiography || !long ? 1 : 0), location: 1)],
                               startPoint: .top, endPoint: .bottom)
            }
            if long {
                Button(wholeBiography ? loc(.readLess) : loc(.readMore)) {
                    withAnimation(.easeOut(duration: 0.25)) { wholeBiography.toggle() }
                }
                .buttonStyle(GlassButtonStyle(height: 34))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .environment(\.layoutDirection, .rightToLeft)
    }

    /// A heading 30nama put in the text: a short line that is not a sentence.
    static func isHeading(_ paragraph: String) -> Bool {
        paragraph.count < 48 && !paragraph.contains(where: { ".،؛!؟:".contains($0) })
    }

    /// The start of the biography, about as tall as the card beside it: whole paragraphs up to
    /// about eight hundred letters.
    static func opening(of paragraphs: [String]) -> [String] {
        var out: [String] = [], length = 0
        for p in paragraphs {
            out.append(p)
            length += p.count
            if length > 800, !isHeading(p) { break }
        }
        return out
    }

    // MARK: Their titles

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
