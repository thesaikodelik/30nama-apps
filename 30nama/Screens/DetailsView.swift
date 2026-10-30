import SwiftUI

/// A title's page: the pitch and the play button over its backdrop, then episodes, storyline and cast.
struct DetailsView: View {
    let ref: PostRef

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var loaded: Post?
    @State private var stream: StreamResult?
    @State private var streamPending = true
    @State private var failed = false
    @State private var season = 1
    /// "Play" was pressed (or requested by the hero) before the stream was known.
    @State private var playWhenReady = false
    /// Why the server gave no stream, in its own words.
    @State private var streamRefusal: String?
    @FocusState private var focus: Target?

    enum Target: Hashable {
        case play, episodes, story
        case star(Int)
        case season(Int)
        case episode(String)
        case person(String)
    }

    private var post: Post? { loaded ?? ref.preview }
    private var items: [StreamItem] { stream?.items ?? [] }
    private var inHeader: Bool {
        switch focus {
        case nil, .play, .episodes, .star: return true
        default: return false
        }
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            StageView(post: post, dim: inHeader ? 0.12 : 0.62, settle: .zero)
            if let post {
                ScrollView(.vertical) {
                    VStack(alignment: .leading, spacing: 56) {
                        header(post)
                        if post.isSeries && !items.isEmpty { episodes }
                        if post.plot(loc.lang).count > 260 || !facts(post).isEmpty { story(post) }
                        if !post.cast.isEmpty { cast(post) }
                        Color.clear.frame(height: 40)
                    }
                }
                .scrollIndicators(.hidden)
            } else if failed {
                ErrorState { Task { await load() } }
            }
        }
        .ignoresSafeArea()
        .toolbar(.hidden, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .task { await load() }
        .onChange(of: app.watchVersion) {
            // Back from the player: pick up the new resume point.
            Task { await loadStream(quietly: true) }
        }
        .onChange(of: focus) { _, target in
            if case .season(let s) = target { season = s }
        }
    }

    // MARK: Data

    private func load() async {
        failed = false
        playWhenReady = ref.autoplay
        do {
            let result = try await app.api.get("single", ["post_id": String(ref.id)])
            guard let post = Post(result) else { throw APIError.server("bad post") }
            loaded = post
            if focus == nil { focus = .play }
        } catch {
            if ref.preview == nil { failed = true }
        }
        await loadStream(quietly: false)
    }

    /// Stream files need a subscription; guests only get them for free titles.
    private func loadStream(quietly: Bool) async {
        guard let post else { return }
        var result: StreamResult?
        #if DEBUG
        if DemoStream.isEnabled { result = DemoStream.result(for: post) }
        #endif
        if result == nil {
            guard app.isLoggedIn || post.freeStream else {
                streamPending = false
                if playWhenReady { playWhenReady = false; explainNoStream() }
                return
            }
            if !quietly { streamPending = true }
            let envelope = try? await app.api.call("stream", ["post_id": String(post.id), "free_stream": app.isLoggedIn ? "0" : "1"])
            #if DEBUG
            print("STREAM success=\(envelope?.success ?? false) msg=\(envelope?.message ?? "-") result=\(envelope.map { DebugShape.describe($0.result) } ?? "no response")")
            #endif
            if let envelope, envelope.success { result = StreamResult(envelope.result) }
            streamRefusal = envelope?.success == false ? envelope?.message : nil
        }
        if let result {
            stream = result
            if !quietly, let first = result.items[safe: result.startIndex] { season = first.season }
        } else if !quietly {
            stream = nil
        }
        streamPending = false
        if playWhenReady {
            playWhenReady = false
            play()
        }
    }

    private func play(at index: Int? = nil) {
        guard let post else { return }
        if streamPending {
            playWhenReady = true
            return
        }
        guard let stream, !stream.items.isEmpty else { return explainNoStream() }
        app.play(post, stream, index: index)
    }

    private func explainNoStream() {
        app.showToast(app.isLoggedIn ? (streamRefusal ?? loc(.noStream)) : loc(.loginToWatch))
    }

    // MARK: Header

    private func header(_ post: Post) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                Eyebrow(text: loc.kindLine(post))
                if post.persianSubtitle {
                    Chip { Label(loc(.persianSub), systemImage: "captions.bubble.fill") }
                }
                if post.isSeries, let date = loc.lang == .fa ? post.nextEpisodeDate.jalali : post.nextEpisodeDate.gregorian {
                    Text("\(post.nextEpisodeCaption ?? loc(.nextEpisode)) · \(date)")
                        .font(.app(22, .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .frame(height: 42)
                        .background(Theme.accent, in: Capsule())
                }
            }
            .lineLimit(1)

            Text(post.title(loc.lang))
                .font(.app(92, .heavy))
                .foregroundStyle(
                    LinearGradient(colors: [.white, Theme.text1.opacity(0.82)], startPoint: .top, endPoint: .bottom))
                .lineLimit(2)
                .minimumScaleFactor(0.55)
                .shadow(color: .black.opacity(0.55), radius: 18, y: 6)
            if let alternate = post.alternateTitle(loc.lang) {
                Text(alternate)
                    .font(.app(30, .medium))
                    .foregroundStyle(Theme.text2)
                    .lineLimit(1)
            }
            MetaLine(post: post, genres: 3, expanded: true)
                .padding(.top, 4)
            Text(post.plot(loc.lang))
                .font(.app(28))
                .foregroundStyle(Theme.text2)
                .lineSpacing(6)
                .lineLimit(4)
                .frame(maxWidth: 880, alignment: .leading)

            HStack(spacing: 16) {
                Button { play() } label: {
                    HStack(spacing: 12) {
                        if playWhenReady && streamPending {
                            ProgressView().tint(Theme.focusInk)
                        } else {
                            Image(systemName: "play.fill")
                        }
                        Text(playLabel)
                    }
                }
                .buttonStyle(PillButtonStyle(kind: .primary))
                .focused($focus, equals: .play)

                if post.isSeries && !items.isEmpty {
                    Button {
                        focus = .episode(currentEpisodeID)
                    } label: {
                        Label(loc(.episodes), systemImage: "list.bullet")
                    }
                    .buttonStyle(PillButtonStyle())
                    .focused($focus, equals: .episodes)
                }
            }
            .padding(.top, 18)

            HStack(spacing: 18) {
                Text(loc(.yourRating))
                    .font(.app(24, .medium))
                    .foregroundStyle(Theme.text2)
                StarRating(value: app.ratings.stars(for: post.id), focus: $focus) { app.rate(post, stars: $0) }
            }
            .padding(.top, 10)
            .focusSection()

            if let progress = stream?.resume?.progress, progress > 0, !post.isSeries {
                ProgressBar(progress: progress).frame(width: 300)
            }
        }
        .multilineTextAlignment(.leading)
        .frame(maxWidth: 1000, alignment: .leading)
        // Bottom-aligned so the buttons sit at the same height whatever the title length, with the
        // next section's heading peeking below as a cue to scroll.
        .frame(height: 880, alignment: .bottomLeading)
        .padding(.leading, Theme.edge)
        .focusSection()
    }

    private var playLabel: String {
        guard stream?.resume != nil, let item = items[safe: stream?.startIndex ?? 0] else {
            return post?.comingSoon == true ? loc(.comingSoonNote) : loc(.play)
        }
        guard post?.isSeries == true else { return loc(.resume) }
        let episode = Episode.label(item.episodeRaw, fallback: 1)
        return "\(loc(.resume)) · \(loc.episodeTag(season: item.season, episode: episode))"
    }

    /// The episode to land on when jumping to the list: the one to resume, else the first.
    private var currentEpisodeID: String {
        let index = stream?.startIndex ?? 0
        let inSeason = items.filter { $0.season == season }
        if let item = items[safe: index], item.season == season { return item.id }
        return inSeason.first?.id ?? ""
    }

    // MARK: Episodes

    private var episodes: some View {
        let seasons = stream?.seasons ?? []
        let inSeason = Array(items.enumerated()).filter { $0.element.season == season }
        return VStack(alignment: .leading, spacing: 22) {
            HStack(alignment: .firstTextBaseline, spacing: 18) {
                SectionTitle(loc(.episodes))
                Text("\(loc.num(inSeason.count)) \(loc(.episodeCount))")
                    .font(.app(24))
                    .foregroundStyle(Theme.text3)
            }
            .padding(.leading, Theme.edge)

            if seasons.count > 1 {
                ScrollView(.horizontal) {
                    HStack(spacing: 12) {
                        ForEach(seasons, id: \.self) { s in
                            Button("\(loc(.season)) \(loc.num(s))") { season = s }
                                .buttonStyle(PillButtonStyle(kind: .chip, selected: s == season))
                                .focused($focus, equals: .season(s))
                        }
                    }
                    .padding(.horizontal, Theme.edge)
                    .padding(.vertical, 10)
                }
                .scrollIndicators(.hidden)
                .scrollClipDisabled()
                .focusSection()
                .defaultFocus($focus, .season(season), priority: .userInitiated)
            }

            ScrollView(.horizontal) {
                LazyHStack(spacing: 24) {
                    ForEach(inSeason, id: \.element.id) { index, item in
                        Button { play(at: index) } label: {
                            EpisodeTile(item: item, position: index + 1, progress: progress(of: item))
                        }
                        .buttonStyle(CardButtonStyle(radius: Theme.tileRadius, scale: 1.07, glow: post?.tint ?? .white))
                        .focused($focus, equals: .episode(item.id))
                    }
                }
                .padding(.horizontal, Theme.edge)
                .padding(.vertical, 18)
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
            .focusSection()
            // Coming down from the header lands on the episode to resume, not on whichever tile is nearest.
            .defaultFocus($focus, .episode(currentEpisodeID), priority: .userInitiated)
            .id(season)
        }
    }

    private func progress(of item: StreamItem) -> Double {
        guard let resume = stream?.resume, resume.fileID == item.fileID else { return 0 }
        return resume.progress
    }

    // MARK: Storyline and cast

    private func story(_ post: Post) -> some View {
        // The header shows four lines of plot; anything longer is given in full here.
        let longPlot = post.plot(loc.lang).count > 260
        let rows = facts(post)
        return VStack(alignment: .leading, spacing: 22) {
            SectionTitle(loc(longPlot ? .storyline : .information))
            Button {} label: {
                HStack(alignment: .top, spacing: 56) {
                    if longPlot {
                        Text(post.plot(loc.lang))
                            .font(.app(27))
                            .lineSpacing(8)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 40, alignment: .topLeading), count: longPlot ? 1 : 2), alignment: .leading, spacing: 14) {
                        ForEach(rows, id: \.0) { label, value in
                            HStack(alignment: .firstTextBaseline, spacing: 14) {
                                Text(label).opacity(0.55).frame(width: 150, alignment: .leading)
                                Text(value).lineLimit(2)
                                Spacer(minLength: 0)
                            }
                            .font(.app(24, .medium))
                        }
                    }
                    .frame(maxWidth: longPlot ? 560 : .infinity, alignment: .leading)
                }
                .padding(.vertical, 28)
            }
            .buttonStyle(RowButtonStyle())
            .focused($focus, equals: .story)
        }
        .padding(.horizontal, Theme.edge)
    }

    private func facts(_ post: Post) -> [(String, String)] {
        func row(_ key: S, _ values: [String]) -> (String, String)? {
            guard !values.isEmpty else { return nil }
            // Names mostly arrive in Latin script; a Persian comma between them reads as a typo.
            let latin = values.joined().unicodeScalars.allSatisfy { $0.value < 0x0600 }
            return (loc(key), values.prefix(3).joined(separator: latin || loc.lang == .en ? ", " : "، "))
        }
        return [
            row(.creator, post.creators),
            row(.director, post.directors),
            row(.writer, post.writers),
            row(.network, post.networks),
            row(.country, post.countries),
            row(.originalLanguage, post.language.map { [$0] } ?? []),
            row(.status, post.airStatus.map { [$0] } ?? []),
        ].compactMap { $0 }
    }

    private func cast(_ post: Post) -> some View {
        VStack(alignment: .leading, spacing: 22) {
            SectionTitle(loc(.cast)).padding(.leading, Theme.edge)
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 28) {
                    ForEach(post.cast) { person in
                        // Who they are and their titles come from the site API, by 30nama's id or
                        // the IMDb one: without either, the face only takes the focus.
                        if app.api.hasSite, person.siteID != nil || person.imdb != nil {
                            NavigationLink(value: Route.person(person)) { Face(person: person) }
                                .buttonStyle(FaceButtonStyle())
                                .focused($focus, equals: .person(person.id))
                        } else {
                            Button {} label: { Face(person: person) }
                                .buttonStyle(FaceButtonStyle())
                                .focused($focus, equals: .person(person.id))
                        }
                    }
                }
                .padding(.horizontal, Theme.edge)
                .padding(.vertical, 18)
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
            .focusSection()
            .defaultFocus($focus, post.cast.first.map { .person($0.id) }, priority: .userInitiated)
        }
    }
}

// MARK: - Pieces

/// Five stars. Pressing a star gives that many; pressing it again takes half a star off, and a
/// third press clears the rating.
private struct StarRating: View {
    let value: Double?
    var focus: FocusState<DetailsView.Target?>.Binding
    let set: (Double?) -> Void

    @Environment(\.loc) private var loc

    var body: some View {
        HStack(spacing: 6) {
            ForEach(1...5, id: \.self) { n in
                Button { press(n) } label: {
                    Image(systemName: symbol(for: n))
                        .font(.system(size: 34, weight: .semibold))
                        .frame(width: 52, height: 52)
                }
                .buttonStyle(StarButtonStyle(lit: (value ?? 0) > Double(n) - 1))
                .focused(focus, equals: .star(n))
            }
            if let value {
                Text(loc.num(Fmt.score(value)))
                    .font(.app(26, .bold))
                    .foregroundStyle(Theme.text1)
                    .padding(.leading, 10)
            }
        }
    }

    private func symbol(for n: Int) -> String {
        let v = value ?? 0
        if v >= Double(n) { return "star.fill" }
        return v >= Double(n) - 0.5 ? "star.leadinghalf.filled" : "star"
    }

    private func press(_ n: Int) {
        switch value {
        case Double(n): set(Double(n) - 0.5)
        case Double(n) - 0.5: set(nil)
        default: set(Double(n))
        }
    }
}

private struct StarButtonStyle: ButtonStyle {
    let lit: Bool

    func makeBody(configuration: Configuration) -> some View {
        StyledLabel(configuration: configuration, lit: lit)
    }

    private struct StyledLabel: View {
        let configuration: Configuration
        let lit: Bool
        @Environment(\.isFocused) private var focused

        var body: some View {
            configuration.label
                .foregroundStyle(focused ? Color.white : (lit ? Color(hex: 0xF5C518) : Theme.text3))
                .shadow(color: Color(hex: 0xF5C518).opacity(focused ? 0.8 : 0), radius: 14)
                .scaleEffect(focused ? (configuration.isPressed ? 1.15 : 1.35) : 1)
                .animation(Theme.focusSpring, value: focused)
        }
    }
}

private struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(.app(34, .bold))
            .foregroundStyle(Theme.text1)
    }
}

struct ProgressBar: View {
    let progress: Double

    var body: some View {
        Capsule()
            .fill(Color.white.opacity(0.22))
            .frame(height: 6)
            .overlay(alignment: .leading) {
                GeometryReader { geo in
                    Capsule().fill(Theme.accent).frame(width: max(6, geo.size.width * min(1, progress)))
                }
            }
    }
}

/// Episode tile. The API has no stills, so the tile is typographic: a big number and the title.
struct EpisodeTile: View {
    let item: StreamItem
    /// 1-based position in the whole list, used when the episode has no number.
    let position: Int
    var progress: Double = 0
    var nowPlaying = false

    @Environment(\.loc) private var loc

    var body: some View {
        let number = Episode.label(item.episodeRaw, fallback: position)
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                Text(loc.num(number.count == 1 ? "0" + number : number))
                    .font(.app(number.count > 3 ? 44 : 64, .heavy))
                    .foregroundStyle(nowPlaying ? Theme.accent : Theme.text3)
                    .environment(\.layoutDirection, .leftToRight)
                Spacer()
                if nowPlaying {
                    Text(loc(.nowPlaying)).font(.app(20, .semibold)).foregroundStyle(Theme.accent)
                } else if let minutes = item.durationMinutes {
                    Text("\(loc.num(minutes)) \(loc(.min))").font(.app(22, .medium)).foregroundStyle(Theme.text2)
                }
            }
            Spacer(minLength: 0)
            Text(item.title(loc.lang) ?? "\(loc(.episode)) \(loc.num(number))")
                .font(.app(26, .semibold))
                .foregroundStyle(Theme.text1)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        }
        .padding(24)
        .frame(width: 360, height: 200, alignment: .topLeading)
        .background {
            ZStack {
                Theme.surface.opacity(0.72)
                LinearGradient(colors: [.white.opacity(0.10), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)
            }
        }
        .overlay(RoundedRectangle(cornerRadius: Theme.tileRadius, style: .continuous).strokeBorder(Color.white.opacity(0.10), lineWidth: 1))
        .overlay(alignment: .bottom) {
            if progress > 0 { ProgressBar(progress: progress).padding(.horizontal, 22).padding(.bottom, 10) }
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.tileRadius, style: .continuous))
    }
}

private struct Face: View {
    let person: Person

    var body: some View {
        VStack(spacing: 14) {
            ZStack {
                Theme.surface2
                Text(initials)
                    .font(.app(44, .bold))
                    .foregroundStyle(Theme.text3)
                if let poster = person.poster {
                    RemoteImage(url: poster.url(forWidth: 170), blurhash: poster.blurhash, maxPixel: Int(255 * UIScreen.main.scale))
                }
            }
            .frame(width: 170, height: 170)
            .clipShape(Circle())
            Text(person.name)
                .font(.app(22, .semibold))
                .foregroundStyle(Theme.text1)
                .lineLimit(1)
            Text(person.role ?? " ")
                .font(.app(20))
                .foregroundStyle(Theme.text3)
                .lineLimit(1)
        }
        .frame(width: 190)
        .environment(\.layoutDirection, .leftToRight)
    }

    private var initials: String {
        let words = person.name.split(separator: " ")
        return String([words.first?.first, words.count > 1 ? words.last?.first : nil].compactMap { $0 }).uppercased()
    }
}

private struct FaceButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        StyledLabel(configuration: configuration)
    }

    private struct StyledLabel: View {
        let configuration: Configuration
        @Environment(\.isFocused) private var focused

        var body: some View {
            configuration.label
                .overlay(alignment: .top) {
                    Circle().strokeBorder(Color.white, lineWidth: 4)
                        .frame(width: 184, height: 184)
                        .offset(y: -7)
                        .opacity(focused ? 1 : 0)
                }
                .scaleEffect(focused ? 1.1 : 1)
                .animation(Theme.focusSpring, value: focused)
        }
    }
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
