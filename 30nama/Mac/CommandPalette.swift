import SwiftUI

/// Search from anywhere: ⌘K brings a bar up in the middle of the window that finds titles as
/// they are typed and the app's own sections by name. The arrows choose, Return opens.
struct CommandPalette: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var text = ""
    @State private var posts: [Post] = []
    @State private var searching = false
    @State private var chosen = 0
    @FocusState private var typing: Bool

    private enum Row: Hashable {
        case place(MacSection)
        case title(Post)
    }

    /// Sections whose name holds what was typed; all of them while nothing has been.
    private var rows: [Row] {
        let query = text.trimmingCharacters(in: .whitespaces)
        let places = MacSection.allCases.filter { $0 != .search && (query.isEmpty || loc($0.title).localizedCaseInsensitiveContains(query)) }
        return places.map(Row.place) + posts.prefix(7).map(Row.title)
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        let rows = rows
        ZStack(alignment: .top) {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture { nav.palette = false }
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass").font(.system(size: 18, weight: .semibold)).foregroundStyle(Theme.text2)
                    TextField(loc(.searchPrompt), text: $text)
                        .textFieldStyle(.plain)
                        .font(.app(20, .medium))
                        .foregroundStyle(Theme.text1)
                        .focused($typing)
                        // Return is taken as a key: the field's own "submit" only selects what was typed.
                        .onKeyPress(.return) {
                            guard rows.indices.contains(chosen) else { return .ignored }
                            open(rows[chosen])
                            return .handled
                        }
                        .onKeyPress(.downArrow) { move(1, in: rows) }
                        .onKeyPress(.upArrow) { move(-1, in: rows) }
                    if searching { ProgressView().controlSize(.small) }
                    Text("esc").font(.system(size: 11, weight: .semibold)).foregroundStyle(Theme.text3)
                        .padding(.horizontal, 7).frame(height: 22)
                        .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Color.white.opacity(0.1)))
                }
                .padding(.horizontal, 20)
                // Left to right in every language: titles are searched by their English names.
                .environment(\.layoutDirection, .leftToRight)
                .frame(height: 62)
                if !rows.isEmpty {
                    Divider().overlay(Color.white.opacity(0.1))
                    VStack(spacing: 2) {
                        ForEach(Array(rows.enumerated()), id: \.element) { index, row in
                            line(row, lit: index == chosen)
                                .contentShape(Rectangle())
                                .onTapGesture { open(row) }
                                .onHover { if $0 { chosen = index } }
                        }
                    }
                    .padding(8)
                }
            }
            .frame(width: 640)
            .background(.ultraThinMaterial, in: shape)
            .background(shape.fill(Theme.bg1.opacity(0.6)))
            .overlay { shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.3), .white.opacity(0.06)], startPoint: .top, endPoint: .bottom), lineWidth: 1) }
            .clipShape(shape)
            .shadow(color: .black.opacity(0.55), radius: 50, y: 24)
            .padding(.top, 110)
        }
        .onAppear { typing = true }
        .onChange(of: rows.count) { _, count in chosen = min(chosen, max(0, count - 1)) }
        // Waits for a pause in the typing: each search is a request to a server that counts them.
        .task(id: text) {
            let query = text.trimmingCharacters(in: .whitespaces)
            guard query.count >= 2 else {
                posts = []
                searching = false
                return
            }
            try? await Task.sleep(for: .milliseconds(380))
            guard !Task.isCancelled else { return }
            searching = true
            let found = try? await app.api.get("search", ["query": query, "streamonly": "0", "page": "1"])
            guard !Task.isCancelled else { return }
            posts = found.map { PostPage($0).posts } ?? []
            searching = false
            chosen = min(chosen, max(0, rows.count - 1))
        }
    }

    @ViewBuilder
    private func line(_ row: Row, lit: Bool) -> some View {
        HStack(spacing: 14) {
            switch row {
            case .place(let section):
                Image(systemName: section.symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.text2)
                    .frame(width: 34, height: 34)
                    .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Color.white.opacity(0.08)))
                Text(loc(section.title)).font(.app(15, .semibold)).foregroundStyle(Theme.text1)
            case .title(let post):
                RemoteImage(url: post.poster.url(forWidth: 60), blurhash: post.poster.blurhash, maxPixel: Int(60 * Display.scale))
                    .frame(width: 34, height: 51)
                    .background(Theme.surface2)
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(post.title(loc.lang)).font(.app(15, .semibold)).foregroundStyle(Theme.text1).lineLimit(1)
                    Text(detail(post)).font(.app(12)).foregroundStyle(Theme.text3).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            if lit {
                Image(systemName: "return").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.text3)
            }
        }
        .padding(.horizontal, 12)
        .frame(minHeight: 46)
        .padding(.vertical, 3)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.white.opacity(lit ? 0.14 : 0)))
    }

    private func detail(_ post: Post) -> String {
        var parts = [loc.kindLine(post)]
        if let year = post.year { parts.append(loc.num(year)) }
        if let score = post.imdbScore { parts.append("IMDb " + loc.num(Fmt.score(score))) }
        return parts.joined(separator: " · ")
    }

    private func move(_ step: Int, in rows: [Row]) -> KeyPress.Result {
        guard !rows.isEmpty else { return .ignored }
        chosen = (chosen + step + rows.count) % rows.count
        return .handled
    }

    private func open(_ row: Row) {
        nav.palette = false
        switch row {
        case .place(let section): nav.go(section)
        case .title(let post): nav.open(post)
        }
    }
}
