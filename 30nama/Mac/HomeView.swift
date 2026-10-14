import SwiftUI

/// Home: a billboard of featured titles over their own artwork, with the shelves scrolling up over it.
struct HomeView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc
    @Environment(\.calm) private var calm
    @Environment(\.lively) private var lively
    @Environment(\.controlActiveState) private var windowState

    @State private var feed: HomeFeed?
    @State private var continuing: [Post] = []
    @State private var failed = false
    @State private var hero = 0
    /// The pointer is on the billboard: someone is reading it, so it should not move on.
    @State private var paused = false
    /// How much of the featured title's turn has run, 0...1. It holds while the billboard rests.
    @State private var turn = 0.0

    /// How long each featured title is shown.
    private static let turnLength = 12.0

    private var featured: Post? {
        guard let heroes = feed?.heroes, !heroes.isEmpty else { return nil }
        return heroes[min(hero, heroes.count - 1)]
    }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                StageView(post: featured)
                if let feed {
                    ScrollView(.vertical) {
                        VStack(alignment: .leading, spacing: 0) {
                            billboard(feed).frame(height: max(500, geo.size.height * 0.72))
                            shelves(feed)
                        }
                    }
                    .scrollIndicators(.hidden)
                } else if failed {
                    ErrorState { Task { await load() } }
                } else {
                    ProgressView().controlSize(.large)
                }
            }
        }
        .task(id: app.contentVersion) { await load() }
        .task(id: app.watchVersion) {
            if feed != nil { continuing = await app.continueWatching() }
        }
        .task(id: "\(hero)|\(resting)|\(feed == nil)") { await rotate() }
        .environment(\.hue, featured?.tint)
    }

    // MARK: Billboard

    private func billboard(_ feed: HomeFeed) -> some View {
        VStack(alignment: .leading, spacing: 22) {
            Spacer(minLength: 0)
            if let post = featured {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 9) {
                        Capsule().fill(post.tint).frame(width: 4, height: 16)
                        Text("\(loc(.featured)) · \(loc.kindLine(post))")
                            .font(.app(14, .semibold))
                            .foregroundStyle(Theme.text2)
                    }
                    Text(post.title(loc.lang))
                        .font(.app(56, .heavy))
                        .foregroundStyle(LinearGradient(colors: [.white, Theme.text1.opacity(0.84)], startPoint: .top, endPoint: .bottom))
                        .lineLimit(2)
                        .minimumScaleFactor(0.6)
                        .shadow(color: .black.opacity(0.5), radius: 18, y: 6)
                    if let other = post.alternateTitle(loc.lang) {
                        Text(other).font(.app(18, .medium)).foregroundStyle(Theme.text2)
                    }
                    MetaRow(post: post).padding(.top, 2)
                    Text(post.plot(loc.lang))
                        .font(.app(16))
                        .foregroundStyle(Theme.text2)
                        .lineSpacing(6)
                        .lineLimit(3)
                        .frame(maxWidth: 580, alignment: .leading)
                    HStack(spacing: 12) {
                        Button { nav.open(post) } label: {
                            Label(loc(post.resume != nil ? .resume : .play), systemImage: "play.fill")
                        }
                        .buttonStyle(PrimaryButtonStyle())
                        Button { nav.open(post) } label: {
                            Label(loc(.details), systemImage: "info.circle")
                        }
                        .buttonStyle(GlassButtonStyle())
                    }
                    .padding(.top, 8)
                }
                .frame(maxWidth: 760, alignment: .leading)
                .id(post.id)
                .transition(.opacity.combined(with: .offset(y: 10)))
                .onHover { paused = $0 }
            }
            dots(feed.heroes.count)
        }
        .padding(.horizontal, Mac.edge)
        .padding(.bottom, 30)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Story-style pager, as on the Apple TV: the current segment fills as its turn runs out.
    private func dots(_ count: Int) -> some View {
        HStack(spacing: 6) {
            ForEach(0..<count, id: \.self) { index in
                Button {
                    withAnimation(.easeInOut(duration: 0.45)) {
                        hero = index
                        turn = 0
                    }
                } label: {
                    Capsule()
                        .fill(Color.white.opacity(0.28))
                        .frame(width: index == hero ? 44 : 12, height: 4)
                        .overlay(alignment: .leading) {
                            // With motion reduced nothing moves on, so the segment is simply lit.
                            Capsule().fill(featured?.tint ?? .white)
                                .frame(width: index == hero ? (calm ? 44 : max(4, 44 * turn)) : 0, height: 4)
                        }
                        .padding(.vertical, 8)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .animation(Theme.focusSpring, value: hero)
    }

    /// The billboard has no audience: something covers it, the window is not the one in use, or
    /// the user is reading it. It costs nothing while it rests.
    private var resting: Bool {
        calm || paused || !nav.opened.isEmpty || app.playback != nil || windowState != .key
    }

    /// Runs the featured title's turn and moves the billboard on when it is over. The turn is
    /// counted in steps, so it can stop where it is when the billboard rests and go on from
    /// there. A Mac that takes motion in its stride gets steps fine enough to read as a smooth
    /// fill; any other gets one a second, so the window is not redrawn all the time for a bar.
    private func rotate() async {
        guard !resting, let count = feed?.heroes.count, count > 1 else { return }
        let step = lively ? 0.1 : 1.0
        while turn < 1 {
            try? await Task.sleep(for: .seconds(step))
            guard !Task.isCancelled else { return }
            withAnimation(.linear(duration: lively ? step : 0.3)) { turn = min(1, turn + step / HomeView.turnLength) }
        }
        withAnimation(.easeInOut(duration: 0.55)) {
            hero = (hero + 1) % count
            turn = 0
        }
    }

    // MARK: Shelves

    private func shelves(_ feed: HomeFeed) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            if !continuing.isEmpty {
                Shelf(title: loc(.continueWatching), posts: continuing, step: 3) { post in
                    ContinueCard(post: post) { nav.open(post) }
                }
            }
            ForEach(feed.sections) { section in
                Shelf(title: loc.title(forSection: section.key), posts: section.posts) { post in
                    PosterCard(post: post) { nav.open(post) }
                }
            }
        }
        .padding(.top, 14)
        .padding(.bottom, 60)
        .background {
            // The shelves rise out of the artwork: clear where they begin, solid a little further down.
            LinearGradient(stops: [
                .init(color: .clear, location: 0),
                .init(color: Theme.bg0.opacity(0.9), location: 0.1),
                .init(color: Theme.bg0, location: 0.24),
            ], startPoint: .top, endPoint: .bottom)
        }
    }

    // MARK: Data

    private func load() async {
        do {
            let fresh = try await app.home()
            feed = fresh
            failed = false
            hero = min(hero, max(0, fresh.heroes.count - 1))
            continuing = await app.continueWatching()
        } catch {
            if feed == nil { failed = true }
        }
    }
}
