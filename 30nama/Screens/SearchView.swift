import SwiftUI

/// Search with the system keyboard (which also brings dictation and typing from an iPhone).
struct SearchView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var query = ""
    @State private var results: [Post] = []
    @State private var heading = ""
    @State private var note: String?
    @State private var searching = false

    private let columns = Array(repeating: GridItem(.fixed(Theme.posterWidth), spacing: 32), count: 7)

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 26) {
                if let note {
                    Text(note)
                        .font(.app(28))
                        .foregroundStyle(Theme.text2)
                }
                HStack(spacing: 16) {
                    Text(heading)
                        .font(.app(34, .bold))
                        .foregroundStyle(Theme.text1)
                    if searching { ProgressView() }
                }
                LazyVGrid(columns: columns, alignment: .leading, spacing: 44) {
                    ForEach(results) { post in
                        PosterLink(post: post, titled: true)
                    }
                }
                .focusSection()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Theme.edge + 4)
            .padding(.top, 20)
            .padding(.bottom, 80)
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
        .background(AmbientBackground())
        .searchable(text: $query, prompt: loc(.searchPrompt))
        .task(id: query) { await run() }
    }

    private func run() async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            await showPopular(note: nil)
            return
        }
        // Wait for typing to pause; a newer keystroke cancels this task.
        try? await Task.sleep(for: .milliseconds(500))
        guard !Task.isCancelled else { return }
        searching = true
        defer { searching = false }
        do {
            let page = PostPage(try await app.api.get("search", ["query": text, "streamonly": APIClient.streamOnly(false), "page": "1"]))
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
        results = Array((suggested.isEmpty ? feed?.posts("movies") ?? [] : suggested).prefix(21))
    }
}
