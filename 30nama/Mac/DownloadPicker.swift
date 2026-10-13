import SwiftUI

/// Choosing what to download for one title, as a pane of glass over the window.
///
/// A film: pick a quality, and it starts. A series: pick a season, then the quality the season
/// comes in, then single episodes or the whole season at once. Each step is laid out under its
/// own heading, top to bottom, so there is never a doubt about what the next click will do.
struct DownloadPicker: View {
    let post: Post
    let close: () -> Void

    @Environment(AppModel.self) private var app
    @Environment(Downloads.self) private var downloads
    @Environment(\.loc) private var loc

    @State private var options: DownloadOptions?
    @State private var message: String?
    @State private var season = 1
    @State private var pack: String?

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        ZStack {
            Color.black.opacity(0.55)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture(perform: close)
            VStack(spacing: 0) {
                header
                Divider().overlay(Color.white.opacity(0.1))
                content
                Divider().overlay(Color.white.opacity(0.1))
                footer
            }
            .frame(width: 900)
            .frame(maxHeight: 680)
            .background(.ultraThinMaterial, in: shape)
            .background(shape.fill(Theme.bg1.opacity(0.55)))
            .overlay { shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.3), .white.opacity(0.06)], startPoint: .top, endPoint: .bottom), lineWidth: 1) }
            .clipShape(shape)
            .shadow(color: .black.opacity(0.55), radius: 50, y: 24)
            .padding(.top, 60)
            .padding(.bottom, 30)
        }
        .task { await load() }
    }

    // MARK: Frame

    private var header: some View {
        HStack(spacing: 16) {
            RemoteImage(url: post.poster.url(forWidth: 60), blurhash: post.poster.blurhash, maxPixel: Int(90 * Display.scale))
                .frame(width: 54, height: 81)
                .background(Theme.surface2)
                .clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(loc(.download)).font(.app(13, .semibold)).foregroundStyle(Theme.accent)
                Text(post.title(loc.lang)).font(.app(24, .bold)).foregroundStyle(Theme.text1).lineLimit(1)
                Text(loc.kindLine(post)).font(.app(13)).foregroundStyle(Theme.text3)
            }
            // Room for the close button, which sits in the corner and not in this row.
            Spacer(minLength: 40)
        }
        .padding(.horizontal, 26)
        .padding(.vertical, 20)
        // In the corner, as far from the top as from the side: centred on the row's height it
        // floated half-way down a tall header.
        .overlay(alignment: .topTrailing) {
            Button(action: close) {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.text1)
                    .frame(width: 36, height: 36)
                    .background(Circle().fill(Color.white.opacity(0.12)))
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .padding(18)
        }
    }

    @ViewBuilder
    private var content: some View {
        if let options {
            if options.isEmpty {
                note(loc(.noDownload))
            } else if options.packs.isEmpty {
                ScrollView(.vertical) {
                    film(options.files)
                        .padding(26)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                // Two columns, so the whole choice is in view at once: what to take on one side,
                // the episodes that choice holds on the other.
                HStack(alignment: .top, spacing: 0) {
                    ScrollView(.vertical) {
                        VStack(alignment: .leading, spacing: 22) { packs(options) }
                            .padding(26)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(width: 520)
                    Divider().overlay(Color.white.opacity(0.1))
                    ScrollView(.vertical) {
                        episodes
                            .padding(26)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .frame(height: 440)
            }
        } else if let message {
            note(message)
        } else {
            ProgressView().controlSize(.large).frame(maxWidth: .infinity).frame(height: 260)
        }
    }

    private var footer: some View {
        HStack(spacing: 10) {
            Image(systemName: "folder").font(.system(size: 13, weight: .semibold)).foregroundStyle(Theme.text3)
            Text(loc(.saveTo)).font(.app(13)).foregroundStyle(Theme.text3)
            // A path reads left to right in any language; on its own, or its slashes change sides.
            Text("\u{200E}" + (downloads.folder.path as NSString).abbreviatingWithTildeInPath)
                .font(.app(13, .semibold))
                .foregroundStyle(Theme.text2)
                .lineLimit(1)
                .truncationMode(.middle)
                .environment(\.layoutDirection, .leftToRight)
            Spacer(minLength: 0)
            if let chosen = chosenPack {
                let left = chosen.files.filter { !downloads.has($0) }
                SubtitleButton(wide: true, had: false) { await downloads.subtitlesOnly(chosen.files, of: post) > 0 }
                    .id(chosen.id)
                Button {
                    for file in left { downloads.start(file, of: post) }
                    app.showToast(loc(.downloadStarted))
                    close()
                } label: {
                    Label("\(loc(.wholeSeason)) · \(loc.num(left.count)) \(loc(.episode))", systemImage: "arrow.down.to.line")
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(left.isEmpty)
                .opacity(left.isEmpty ? 0.4 : 1)
            }
        }
        .padding(.horizontal, 26)
        .frame(height: 76)
    }

    private func note(_ text: String) -> some View {
        Text(text).font(.app(16, .medium)).foregroundStyle(Theme.text2)
            .frame(maxWidth: .infinity).frame(height: 260)
    }

    private func heading(_ text: String, _ aside: String? = nil) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(text).font(.app(15, .bold)).foregroundStyle(Theme.text1)
            if let aside { Text(aside).font(.app(13)).foregroundStyle(Theme.text3) }
        }
    }

    // MARK: A film

    /// The qualities a film comes in, the sharpest first, each one a row to click.
    private func film(_ files: [DownloadFile]) -> some View {
        let groups = Dictionary(grouping: files) { resolution($0.quality) }
        return VStack(alignment: .leading, spacing: 20) {
            ForEach(groups.keys.sorted(by: >), id: \.self) { lines in
                VStack(alignment: .leading, spacing: 8) {
                    heading(lines > 0 ? "\(lines)p" : loc(.fileQuality), lines >= 2160 ? "4K" : nil)
                    ForEach(groups[lines] ?? []) { file in
                        FileRow(title: file.quality, detail: [file.encoder].compactMap { $0 }, tags: file.tags, size: file.size,
                                subtitled: file.subtitle != nil, shortfall: nil, state: downloads.has(file) ? .listed : .ready,
                                hasSubtitle: downloads.hasSubtitle(file),
                                subtitleOnly: { await downloads.subtitlesOnly([file], of: post) > 0 }) {
                            downloads.start(file, of: post)
                            app.showToast(loc(.downloadStarted))
                            close()
                        }
                    }
                }
            }
        }
    }

    private func resolution(_ quality: String) -> Int {
        for lines in [2160, 1080, 720, 480] where quality.contains("\(lines)") { return lines }
        return 0
    }

    // MARK: A series

    private var chosenPack: DownloadOptions.Pack? {
        guard let options, !options.packs.isEmpty else { return nil }
        let packs = options.packs.filter { $0.season == season }
        return packs.first { $0.id == pack } ?? packs.first
    }

    @ViewBuilder
    private func packs(_ options: DownloadOptions) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            heading(loc(.season))
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 92), spacing: 8)], alignment: .leading, spacing: 8) {
                ForEach(options.seasons, id: \.self) { number in
                    Choice(title: "\(loc(.season)) \(loc.num(number))", selected: season == number) {
                        season = number
                        pack = nil
                    }
                }
            }
        }
        VStack(alignment: .leading, spacing: 8) {
            heading(loc(.fileQuality), loc(.perEpisode))
            let whole = options.episodes(inSeason: season).count
            ForEach(options.packs.filter { $0.season == season }) { option in
                FileRow(title: option.quality, detail: [option.encoder].compactMap { $0 }, tags: option.files.first?.tags ?? [], size: option.size,
                        subtitled: option.files.contains { $0.subtitle != nil },
                        // A pack that lacks episodes says so, and how many it does have.
                        shortfall: option.files.count < whole ? "\(loc(.incomplete)) · \(loc.num(option.files.count)) \(loc(.ofTotal)) \(loc.num(whole)) \(loc(.episode))" : nil,
                        state: option.id == chosenPack?.id ? .chosen : .choice) {
                    pack = option.id
                }
            }
        }
    }

    /// The episodes of the chosen season in the chosen quality: click one to fetch just that one.
    @ViewBuilder
    private var episodes: some View {
        if let chosen = chosenPack, let options {
            let all = options.episodes(inSeason: chosen.season)
            VStack(alignment: .leading, spacing: 10) {
                heading(loc(.episodes), chosen.files.count < all.count
                    ? "\(loc.num(chosen.files.count)) \(loc(.ofTotal)) \(loc.num(all.count))" : loc.num(all.count))
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 8)], alignment: .leading, spacing: 8) {
                    // Every episode of the season keeps its place. One this quality does not have
                    // is shown switched off, not left out: a gap reads as a fault in the list.
                    ForEach(all, id: \.self) { episode in
                        let file = chosen.files.first { $0.episode == episode }
                        EpisodeChip(title: "\(loc(.episode)) \(loc.num(episode))", available: file != nil,
                                    listed: file.map(downloads.has) ?? false) {
                            guard let file else { return }
                            downloads.start(file, of: post)
                            app.showToast(loc(.downloadStarted))
                        }
                        .help(file == nil ? loc(.notInPack) : "")
                    }
                }
            }
        }
    }

    private func load() async {
        guard let answer = try? await app.api.call("download", ["post_id": String(post.id), "free_download": app.isLoggedIn ? "0" : "1"]) else {
            message = "\(loc(.error)). \(loc(.tryAgain))"
            return
        }
        guard answer.success else {
            message = answer.message ?? loc(.noDownload)
            return
        }
        let found = DownloadOptions(answer.result)
        season = found.seasons.first ?? 1
        options = found
    }
}

/// One of a few: a season. Large enough to hit without aiming.
private struct Choice: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.app(14, .semibold))
                .foregroundStyle(selected ? Theme.focusInk : Theme.text1)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(Capsule(style: .continuous).fill(selected ? Color.white : Color.white.opacity(hovering ? 0.2 : 0.1)))
                .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
    }
}

/// A file or a pack of files as one wide row: what it is on one side, how big on the other.
/// For a film the row is the download button; for a series it chooses the quality.
/// Fetches Persian subtitles without the film, and says on its own face how that went.
private struct SubtitleButton: View {
    enum Phase { case idle, working, saved, missing }

    /// The long wording, for the foot of the pane; a row has room only for the short one.
    let wide: Bool
    /// The subtitles are in the folder already.
    let had: Bool
    let fetch: () async -> Bool

    @Environment(\.loc) private var loc
    @State private var phase = Phase.idle
    @State private var hovering = false

    private var done: Bool { phase == .saved || (phase == .idle && had) }

    var body: some View {
        Button {
            guard phase != .working else { return }
            phase = .working
            Task { phase = await fetch() ? .saved : .missing }
        } label: {
            HStack(spacing: wide ? 8 : 5) {
                ZStack {
                    if phase == .working {
                        ProgressView().controlSize(.small).scaleEffect(wide ? 0.8 : 0.6)
                    } else {
                        Image(systemName: done ? "checkmark" : phase == .missing ? "xmark" : "captions.bubble")
                            .font(.system(size: wide ? 14 : 12, weight: .bold))
                            .foregroundStyle(done ? Theme.good : Theme.text1)
                    }
                }
                .frame(width: wide ? 18 : 15, height: wide ? 18 : 15)
                Text(phase == .missing ? loc(.subtitleMissing) : wide ? loc(.getSubtitle) : loc(.subtitles))
                    .font(.app(wide ? 16 : 12, wide ? .semibold : .bold))
                    .lineLimit(1)
            }
            .foregroundStyle(Theme.text1)
            .padding(.horizontal, wide ? 22 : 12)
            .frame(height: wide ? 46 : 32)
            .background(Capsule().fill(Color.white.opacity(hovering ? 0.2 : 0.12)))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .help(loc(.getSubtitle))
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
        .animation(.easeOut(duration: 0.14), value: phase)
    }
}

private struct FileRow: View {
    enum Mark { case ready, listed, choice, chosen }

    let title: String
    let detail: [String]
    let tags: [String]
    let size: String?
    let subtitled: Bool
    /// "Incomplete · 2 of 10 episodes", for a pack that lacks some.
    let shortfall: String?
    let state: Mark
    var hasSubtitle = false
    /// Fetches just the subtitles for this file. A film's row offers it; a pack's does not.
    var subtitleOnly: (() async -> Bool)? = nil
    let action: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        HStack(spacing: 0) {
          Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: symbol)
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(state == .listed ? Theme.good : state == .chosen ? Theme.accent : Theme.text2)
                    .frame(width: 26)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.app(15, .bold)).foregroundStyle(Theme.text1).lineLimit(1)
                    HStack(spacing: 6) {
                        ForEach(detail, id: \.self) { Text($0).font(.app(12, .medium)).foregroundStyle(Theme.text3) }
                        ForEach(tags.prefix(4), id: \.self) { tag in
                            Text(tag).font(.app(11, .bold)).foregroundStyle(Theme.text2)
                                .padding(.horizontal, 7).frame(height: 19)
                                .background(Capsule().fill(Color.white.opacity(0.1)))
                        }
                        if subtitled {
                            HStack(spacing: 4) {
                                Image(systemName: "captions.bubble").font(.system(size: 9, weight: .bold))
                                Text(loc(.persianSub)).font(.app(11, .bold))
                            }
                            .foregroundStyle(Theme.text2)
                            .padding(.horizontal, 7).frame(height: 19)
                            .background(Capsule().fill(Color.white.opacity(0.1)))
                        }
                        if let shortfall {
                            // In the app's own greys, like the labels beside it; the mark in front sets it apart.
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.circle").font(.system(size: 9, weight: .bold))
                                Text(shortfall).font(.app(11, .bold))
                            }
                            .foregroundStyle(Theme.text2)
                            .padding(.horizontal, 7).frame(height: 19)
                            .background(Capsule().fill(Color.white.opacity(0.1)))
                        }
                    }
                }
                Spacer(minLength: 8)
                if let size {
                    Text(size).font(.system(size: 14, weight: .semibold).monospacedDigit()).foregroundStyle(Theme.text1)
                        .environment(\.layoutDirection, .leftToRight)
                }
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 58)
            .contentShape(Rectangle())
          }
          .buttonStyle(.plain)
          .disabled(state == .listed)
          if let subtitleOnly {
              // Its own button, apart from the row's: the film is not wanted, only its subtitles.
              SubtitleButton(wide: false, had: hasSubtitle, fetch: subtitleOnly).padding(.trailing, 14)
          }
        }
        .background(shape.fill(Color.white.opacity(state == .chosen ? 0.16 : hovering ? 0.12 : 0.06)))
        .overlay { shape.strokeBorder(state == .chosen ? Color.white.opacity(0.55) : Color.white.opacity(0.06), lineWidth: state == .chosen ? 1.5 : 1) }
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
    }

    private var symbol: String {
        switch state {
        case .ready: return "arrow.down.circle"
        case .listed: return "checkmark.circle.fill"
        case .choice: return "circle"
        case .chosen: return "largecircle.fill.circle"
        }
    }
}

/// One episode: click to fetch it. Once it is in the list it wears a tick and takes no more clicks.
private struct EpisodeChip: View {
    let title: String
    /// The chosen quality has this episode.
    var available = true
    let listed: Bool
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 12, style: .continuous)
        Button(action: action) {
            HStack(spacing: 7) {
                Image(systemName: !available ? "minus" : listed ? "checkmark" : "arrow.down.to.line")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(listed ? Theme.good : Theme.text2)
                Text(title).font(.app(13, .semibold)).foregroundStyle(listed ? Theme.text3 : Theme.text1).lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            .background(shape.fill(Color.white.opacity(!available ? 0.03 : listed ? 0.04 : hovering ? 0.2 : 0.09)))
            .overlay { if !available { shape.strokeBorder(Color.white.opacity(0.08), style: StrokeStyle(lineWidth: 1, dash: [4, 3])) } }
            .opacity(available ? 1 : 0.38)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .disabled(listed || !available)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
    }
}

// MARK: - Subtitles

/// Fetches a Persian subtitle from the subtitle site 30nama links to and saves it under the
/// film's own name, so nobody has to visit the site, pick a file and rename it by hand.
enum SubtitleFetcher {
    private static let agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

    private static func get(_ url: URL) async -> Data? {
        var request = URLRequest(url: url)
        request.setValue(agent, forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 25
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode ?? 0 < 400 else { return nil }
        return data
    }

    /// - Parameter page: the site's listing for this film or season.
    /// - Returns: whether a subtitle in time with this copy was found and written to `target`.
    static func fetch(page: URL, fileName: String, season: Int?, episode: Int?, to target: URL) async -> Bool {
        guard let listing = await get(page).flatMap({ String(data: $0, encoding: .utf8) }),
              let entry = SubtitleSite.best(for: fileName, season: season, episode: episode, among: SubtitleSite.entries(in: listing)),
              let own = URL(string: entry.path, relativeTo: page)?.absoluteURL,
              let detail = await get(own).flatMap({ String(data: $0, encoding: .utf8) }),
              let address = SubtitleSite.download(in: detail),
              let archive = await get(address) else { return false }
        return await Task.detached { unpack(archive, season: season, episode: episode, to: target) }.value
    }

    /// The site hands out an archive. The right file is taken out of it and rewritten as UTF-8,
    /// which every player reads.
    private static func unpack(_ archive: Data, season: Int?, episode: Int?, to target: URL) -> Bool {
        let manager = FileManager.default
        func write(_ data: Data) -> Bool {
            guard let text = SubtitleSite.text(data), text.contains("-->") else { return false }
            try? manager.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
            return (try? text.write(to: target, atomically: true, encoding: .utf8)) != nil
        }
        // Not an archive: the subtitle itself.
        guard archive.starts(with: [0x50, 0x4B]) else { return write(archive) }
        let work = manager.temporaryDirectory.appendingPathComponent("30nama-subtitle-" + UUID().uuidString, isDirectory: true)
        defer { try? manager.removeItem(at: work) }
        let packed = work.appendingPathComponent("subtitle.zip"), open = work.appendingPathComponent("open", isDirectory: true)
        do {
            try manager.createDirectory(at: open, withIntermediateDirectories: true)
            try archive.write(to: packed)
            let unzip = Process()
            unzip.executableURL = URL(fileURLWithPath: "/usr/bin/unzip")
            // Paths inside the archive are dropped, so nothing can be written outside the work folder.
            unzip.arguments = ["-o", "-qq", "-j", packed.path, "-d", open.path]
            unzip.standardOutput = FileHandle.nullDevice
            unzip.standardError = FileHandle.nullDevice
            try unzip.run()
            unzip.waitUntilExit()
        } catch {
            return false
        }
        let names = (try? manager.contentsOfDirectory(atPath: open.path)) ?? []
        guard let name = SubtitleSite.pick(from: names, season: season, episode: episode),
              let data = try? Data(contentsOf: open.appendingPathComponent(name)) else { return false }
        return write(data)
    }
}
