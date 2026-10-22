import SwiftUI

/// 30nama's news, its reviews and its own announcements, as the website has them, newest first.
/// Only the site API has them, so this page is reached only when the build has its key.
struct NewsView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var kind = Article.Kind.news
    @State private var articles: [Article] = []
    @State private var page = 0
    @State private var pages = 1
    @State private var loading = false
    @State private var failed = false

    private let columns = [GridItem(.adaptive(minimum: 300, maximum: 420), spacing: 22)]

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 24) {
                    HStack(alignment: .firstTextBaseline, spacing: 14) {
                        Text(loc(.news)).font(.app(34, .bold)).foregroundStyle(Theme.text1)
                        Spacer(minLength: 0)
                        ForEach(Article.Kind.allCases, id: \.self) { option in
                            PillChoice(title: title(option), selected: kind == option) { kind = option }
                        }
                    }
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                        ForEach(articles) { article in
                            ArticleCard(article: article) { nav.open(.article(article)) }
                                .onAppear { if articles.suffix(6).contains(where: { $0.id == article.id }) { Task { await loadNext() } } }
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
        .task(id: kind) {
            articles = []
            page = 0
            pages = 1
            loading = false
            await loadNext()
        }
    }

    private func title(_ kind: Article.Kind) -> String {
        switch kind {
        case .news: return loc(.newsItems)
        case .review: return loc(.reviews)
        case .announcement: return loc(.announcements)
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
        // A change of kind starts the list again; an answer for the old one must not touch it.
        let asked = kind
        defer { if asked == kind { loading = false } }
        do {
            let next = ArticlePage(site: try await app.api.site(SiteQuery.articles(asked, page: page + 1)))
            guard asked == kind, !Task.isCancelled else { return }
            let known = Set(articles.map(\.id))
            articles += next.articles.filter { !known.contains($0.id) }
            page = next.page
            pages = next.pages
        } catch {
            if asked == kind, !Task.isCancelled { failed = true }
        }
    }
}

/// An article in the list: its picture, title, date and first lines.
private struct ArticleCard: View {
    let article: Article
    let open: () -> Void

    @State private var hovering = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        Button(action: open) {
            VStack(alignment: .leading, spacing: 0) {
                ZStack {
                    Theme.surface2
                    Image(systemName: "newspaper").font(.system(size: 34)).foregroundStyle(Theme.text3)
                    RemoteImage(url: article.thumbnail, fallback: article.picture, maxPixel: Int(420 * Display.scale))
                }
                .aspectRatio(16 / 9, contentMode: .fit)
                .clipped()
                VStack(alignment: .leading, spacing: 8) {
                    Text(article.title)
                        .font(.app(16, .bold))
                        .foregroundStyle(Theme.text1)
                        // Two lines' room always, so the cards of a row are one height.
                        .lineLimit(2, reservesSpace: true)
                    Text(article.summary ?? " ").font(.app(13)).foregroundStyle(Theme.text2).lineLimit(2, reservesSpace: true)
                    if let date = article.date {
                        Text(date).font(.app(12, .medium)).foregroundStyle(Theme.text3)
                    }
                }
                .multilineTextAlignment(.leading)
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            // Articles are written in Persian whatever the interface's language.
            .environment(\.layoutDirection, .rightToLeft)
            .background(Color.white.opacity(hovering ? 0.09 : 0.05), in: shape)
            .clipShape(shape)
            .overlay { shape.strokeBorder(Color.white.opacity(hovering ? 0.5 : 0.08), lineWidth: 1) }
            .scaleEffect(hovering ? 1.015 : 1)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(Theme.focusSpring, value: hovering)
    }
}

/// One article, whole: its picture, title and date, then its text as it was written, with its
/// headings and pictures, in a column narrow enough to read.
struct ArticleView: View {
    let article: Article

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var text: ArticleText?
    @State private var failed = false

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 18) {
                    if let picture = article.picture {
                        ZStack {
                            Theme.surface2
                            RemoteImage(url: picture, fallback: article.thumbnail, maxPixel: Int(760 * Display.scale))
                        }
                        .aspectRatio(16 / 9, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                    Text(article.title)
                        .font(.app(30, .bold))
                        .foregroundStyle(Theme.text1)
                        .fixedSize(horizontal: false, vertical: true)
                    if let date = text?.article.date ?? article.date {
                        Text(date).font(.app(14, .medium)).foregroundStyle(Theme.text3)
                    }
                    if let text {
                        ForEach(Array(text.blocks.enumerated()), id: \.offset) { _, block in
                            switch block {
                            case .heading(let line):
                                Text(line).font(.app(20, .bold)).foregroundStyle(Theme.text1).padding(.top, 8)
                                    .fixedSize(horizontal: false, vertical: true)
                            case .paragraph(let line):
                                Text(line).font(.app(16)).foregroundStyle(Theme.text2).lineSpacing(8)
                                    .fixedSize(horizontal: false, vertical: true)
                            case .picture(let url):
                                ArticlePicture(url: url)
                            }
                        }
                    } else if failed {
                        Button(loc(.retry)) { Task { await load() } }
                            .buttonStyle(GlassButtonStyle())
                            .frame(maxWidth: .infinity)
                    } else {
                        ProgressView().controlSize(.regular).frame(maxWidth: .infinity).padding(.vertical, 30)
                    }
                }
                .multilineTextAlignment(.leading)
                .frame(maxWidth: 760, alignment: .leading)
                .frame(maxWidth: .infinity)
                // Articles are written in Persian whatever the interface's language.
                .environment(\.layoutDirection, .rightToLeft)
                .padding(.horizontal, Mac.edge)
                .padding(.top, 96)
                .padding(.bottom, 60)
            }
            .scrollIndicators(.never)
        }
        .task { await load() }
    }

    private func load() async {
        failed = false
        do {
            text = ArticleText(site: try await app.api.site(SiteQuery.article(id: article.id)))
            if text == nil { failed = true }
        } catch {
            if !Task.isCancelled { failed = true }
        }
    }
}

/// A picture in an article, in its own proportions: they are not all the same shape.
private struct ArticlePicture: View {
    let url: URL

    @State private var image: NSImage?

    var body: some View {
        Group {
            if let image {
                Image(nsImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: min(image.size.width, 760))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .frame(maxWidth: .infinity)
                    .transition(.opacity)
            } else {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Theme.surface2).frame(height: 200)
            }
        }
        .task(id: url) {
            let loaded = await ImagePipeline.shared.image(url, maxPixel: Int(760 * Display.scale))
            withAnimation(.easeOut(duration: 0.2)) { image = loaded }
        }
    }
}
