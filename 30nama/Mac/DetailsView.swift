import SwiftUI

/// One title: its artwork behind, what it is, the user's own stars, and the people in it.
struct DetailsView: View {
    /// What the list already knew about the title, shown at once while the rest loads.
    let preview: Post

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var loaded: Post?
    @State private var stream: StreamResult?
    /// The stream is being asked for; the play button waits on it.
    @State private var streamPending = false
    /// Why 30nama would not hand over the stream, in its own words.
    @State private var refusal: String?
    @State private var season = 1
    @Environment(MacNav.self) private var nav

    private var post: Post { loaded ?? preview }

    var body: some View {
        ZStack {
            Theme.bg0
            StageView(post: post, dim: 0.3, settle: .zero)
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 40) {
                    header
                    if post.isSeries, let stream, stream.items.count > 1 { episodes(stream) }
                    if !post.cast.isEmpty { cast }
                    facts
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 130)
                .padding(.bottom, 60)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollIndicators(.hidden)
        }
        .task {
            loaded = (try? await app.api.get("single", ["post_id": String(preview.id)])).flatMap { Post($0) }
            await loadStream(quietly: false)
            #if DEBUG
            if DebugLaunch.play { play() }
            #endif
        }
        .task(id: app.watchVersion) {
            // Back from the player: where to resume has moved.
            if stream != nil { await loadStream(quietly: true) }
        }
    }

    // MARK: Stream

    /// Stream files need a subscription; guests only get them for free titles.
    private func loadStream(quietly: Bool) async {
        var result: StreamResult?
        #if DEBUG
        if DemoStream.isEnabled { result = DemoStream.result(for: post) }
        #endif
        if result == nil {
            guard app.isLoggedIn || post.freeStream else { return }
            if !quietly { streamPending = true }
            let envelope = try? await app.api.call("stream", ["post_id": String(post.id), "free_stream": app.isLoggedIn ? "0" : "1"])
            if let envelope, envelope.success { result = StreamResult(envelope.result) }
            refusal = envelope?.success == false ? envelope?.message : nil
        }
        if let result {
            stream = result
            if !quietly, result.items.indices.contains(result.startIndex) { season = result.items[result.startIndex].season }
        } else if !quietly {
            stream = nil
        }
        streamPending = false
    }

    /// Plays the item at `index`, or the one to resume.
    private func play(at index: Int? = nil) {
        guard let stream, !stream.items.isEmpty else {
            app.showToast(post.comingSoon ? loc(.comingSoonNote) : app.isLoggedIn ? (refusal ?? loc(.noStream)) : loc(.loginToWatch))
            return
        }
        app.play(PlaybackRequest(post: post, items: stream.items, index: index ?? stream.startIndex, resume: stream.resume))
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .top, spacing: 40) {
            let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
            VStack(spacing: 14) {
                RemoteImage(url: post.poster.big ?? post.poster.large, blurhash: post.poster.blurhash, maxPixel: Int(360 * Display.scale))
                    .frame(width: 240, height: 360)
                    .clipShape(shape)
                    .overlay { shape.strokeBorder(Color.white.opacity(0.14), lineWidth: 1) }
                    .shadow(color: post.tint.opacity(0.4), radius: 40, y: 18)
                // The user's own stars sit with the poster: they are about the title, not one of the things to do with it.
                HStack(spacing: 8) {
                    Text(loc(.yourRating)).font(.app(13, .semibold)).foregroundStyle(Theme.text2).lineLimit(1).fixedSize()
                    Spacer(minLength: 0)
                    StarRater(value: app.ratings.stars(for: post.id), size: 17) { app.rate(post, stars: $0) }
                }
                .padding(.horizontal, 16)
                .frame(width: 240, height: 46)
                .glass(Capsule(style: .continuous))
            }

            VStack(alignment: .leading, spacing: 14) {
                Text(loc.kindLine(post))
                    .font(.app(14, .semibold))
                    .foregroundStyle(Theme.text2)
                Text(post.title(loc.lang))
                    .font(.app(48, .heavy))
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
                    .frame(maxWidth: 700, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 14) {
                    Button { play() } label: {
                        HStack(spacing: 8) {
                            if streamPending {
                                ProgressView().controlSize(.small)
                            } else {
                                Image(systemName: "play.fill")
                            }
                            Text(playLabel)
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(streamPending)
                    if app.isLoggedIn {
                        Button { nav.picking = post } label: {
                            Label(loc(.download), systemImage: "arrow.down.to.line")
                        }
                        .buttonStyle(GlassButtonStyle())
                    }
                    if app.isLoggedIn {
                        ListToggle(symbol: "bookmark", on: app.isListed(post, "watchlist"), help: loc(.watchlist)) {
                            Task { await app.toggleList(post, "watchlist") }
                        }
                        ListToggle(symbol: "heart", on: app.isListed(post, "favorite"), help: loc(.favorites)) {
                            Task { await app.toggleList(post, "favorite") }
                        }
                    }
                }
                .padding(.top, 10)
            }
            Spacer(minLength: 0)
        }
    }

    /// "Resume · S2 · E5" for a series left part-way, plain "Play" otherwise.
    private var playLabel: String {
        guard let resume = stream?.resume ?? post.resume else { return loc(.play) }
        if post.isSeries, let stream, stream.items.indices.contains(stream.startIndex) {
            let item = stream.items[stream.startIndex]
            return "\(loc(.resume)) · \(loc.episodeTag(season: item.season, episode: item.episodeRaw ?? "\(item.episodeNumber)"))"
        }
        return resume.time > 5 ? loc(.resume) : loc(.play)
    }

    // MARK: Episodes

    private func episodes(_ stream: StreamResult) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Text(loc(.episodes)).font(.app(20, .bold)).foregroundStyle(Theme.text1)
                if stream.seasons.count > 1 {
                    ScrollView(.horizontal) {
                        HStack(spacing: 8) {
                            ForEach(stream.seasons, id: \.self) { number in
                                PillChoice(title: "\(loc(.season)) \(loc.num(number))", selected: season == number) { season = number }
                            }
                        }
                    }
                    .scrollIndicators(.hidden)
                }
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 250, maximum: 380), spacing: 12)], alignment: .leading, spacing: 12) {
                ForEach(Array(stream.items.enumerated()).filter { $0.element.season == season }, id: \.element.id) { index, item in
                    EpisodeTile(item: item, resume: stream.resume?.fileID == item.fileID ? stream.resume : nil,
                                watched: app.watched.contains(item.fileID)) { play(at: index) }
                        .contextMenu {
                            let done = app.watched.contains(item.fileID)
                            Button(loc(done ? .markUnwatched : .markWatched)) { app.setWatched(item.fileID, !done) }
                        }
                }
            }
        }
    }

    // MARK: People and facts

    private var cast: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(loc(.cast)).font(.app(20, .bold)).foregroundStyle(Theme.text1)
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 20) {
                    ForEach(post.cast) { person in
                        VStack(spacing: 8) {
                            ZStack {
                                Circle().fill(Theme.surface2)
                                Image(systemName: "person.fill").font(.system(size: 30)).foregroundStyle(Theme.text3)
                                if let face = person.poster {
                                    RemoteImage(url: face.url(forWidth: 96), blurhash: face.blurhash, maxPixel: Int(96 * Display.scale))
                                }
                            }
                            .frame(width: 96, height: 96)
                            .clipShape(Circle())
                            Text(person.name).font(.app(13, .semibold)).foregroundStyle(Theme.text1).lineLimit(1)
                            if let role = person.role {
                                Text(role).font(.app(12)).foregroundStyle(Theme.text3).lineLimit(1)
                            }
                        }
                        .frame(width: 112)
                    }
                }
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
        }
    }

    private var facts: some View {
        let rows: [(S, String)] = [
            (.director, post.directors), (.creator, post.creators), (.writer, post.writers),
            (.network, post.networks), (.country, post.countries),
        ].compactMap { key, names in names.isEmpty ? nil : (key, names.joined(separator: loc.lang == .en ? ", " : "، ")) }
        return VStack(alignment: .leading, spacing: 10) {
            ForEach(rows, id: \.0) { key, value in
                HStack(alignment: .firstTextBaseline, spacing: 16) {
                    Text(loc(key)).font(.app(14, .medium)).foregroundStyle(Theme.text3).frame(width: 110, alignment: .leading)
                    Text(value).font(.app(15)).foregroundStyle(Theme.text1)
                }
            }
        }
    }
}

/// Five stars that take half steps: the left half of a star is the half, the right half the whole.
struct StarRater: View {
    let value: Double?
    /// The size of one star's glyph.
    var size: CGFloat = 20
    /// Called with the new rating, or nil when the current one is clicked again to clear it.
    let set: (Double?) -> Void

    @State private var preview: Double?

    var body: some View {
        let shown = preview ?? value ?? 0
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { n in
                Image(systemName: shown >= Double(n) ? "star.fill" : shown >= Double(n) - 0.5 ? "star.leadinghalf.filled" : "star")
                    .font(.system(size: size, weight: .semibold))
                    .foregroundStyle(shown >= Double(n) - 0.5 ? Color(hex: 0xF5C518) : Theme.text3)
                    .frame(width: size * 1.35, height: 34)
                    .overlay {
                        HStack(spacing: 0) {
                            half(Double(n) - 0.5)
                            half(Double(n))
                        }
                    }
            }
        }
        .onHover { if !$0 { preview = nil } }
        .animation(.easeOut(duration: 0.12), value: shown)
    }

    private func half(_ stars: Double) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .onHover { if $0 { preview = stars } }
            .onTapGesture { set(value == stars ? nil : stars) }
    }
}

/// One episode on the title page: its number, name and length, and how far in the user is.
private struct EpisodeTile: View {
    let item: StreamItem
    let resume: ContinueWatching?
    var watched = false
    let action: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        Button(action: action) {
            HStack(spacing: 14) {
                ZStack {
                    Text(loc.num(item.episodeRaw ?? "\(item.episodeNumber)"))
                        .font(.app(20, .heavy))
                        .foregroundStyle(resume != nil ? Theme.accent : Theme.text2)
                        .opacity(hovering ? 0 : 1)
                    Image(systemName: "play.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                        .opacity(hovering ? 1 : 0)
                }
                .frame(width: 40)
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title(loc.lang) ?? "\(loc(.episode)) \(loc.num(item.episodeNumber))")
                        .font(.app(15, .semibold))
                        .foregroundStyle(Theme.text1)
                        .lineLimit(1)
                    if let minutes = item.durationMinutes {
                        Text("\(loc.num(minutes)) \(loc(.min))").font(.app(12)).foregroundStyle(Theme.text3)
                    }
                }
                Spacer(minLength: 0)
                if watched {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 16, weight: .semibold)).foregroundStyle(Theme.good)
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 62)
            .background(shape.fill(Color.white.opacity(hovering ? 0.16 : 0.07)))
            .overlay(alignment: .bottomLeading) {
                if let resume, resume.progress > 0 {
                    GeometryReader { geo in
                        Capsule().fill(Theme.accent).frame(width: max(8, (geo.size.width - 24) * resume.progress), height: 3)
                            .padding(.horizontal, 12)
                            .frame(maxHeight: .infinity, alignment: .bottom)
                            .padding(.bottom, 5)
                    }
                }
            }
            .clipShape(shape)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
    }
}

/// A round button that keeps a title on one of the user's 30nama lists: filled in when it is there.
private struct ListToggle: View {
    let symbol: String
    let on: Bool
    let help: String
    /// The glyph has a filled form to switch to. A plain action button has none.
    var fills = true
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: on && fills ? symbol + ".fill" : symbol)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(on ? Theme.accent : Theme.text1)
                .frame(width: 46, height: 46)
                .background(Circle().fill(Color.white.opacity(hovering ? 0.2 : 0.1)))
                .glass(Circle())
                .contentShape(Circle())
                .scaleEffect(hovering ? 1.06 : 1)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(Theme.focusSpring, value: hovering)
        .animation(Theme.focusSpring, value: on)
        .help(help)
    }
}
