import SwiftUI

/// One download, as the list shows it and as it is remembered between launches.
struct DownloadItem: Identifiable, Codable, Equatable {
    enum State: String, Codable { case running, paused, done, failed }

    /// The three parts of the downloads section.
    enum Shelf: String, CaseIterable { case movies, series, anime }

    var id: String
    /// The title it belongs to.
    var title: String
    /// Which file of that title: "S3 · E16 · x265 BluRay 1080p · 340 MB".
    var detail: String
    var fileName: String
    var address: URL
    var state: State
    var received: Int64 = 0
    var expected: Int64 = 0
    /// Where the finished file is.
    var path: String?
    /// Paused by the download hours, to go on by itself when they come round.
    var held: Bool?
    /// Waiting for its turn: only so many files come in at once (Settings), the oldest first.
    var queued: Bool?

    // What it is a file of. Missing in items saved by the first version, which knew less.
    var postID: Int?
    /// "movie", "series" or "anime".
    var kind: String?
    var isSeries: Bool?
    var season: Int?
    var episode: String?
    /// "x265 BluRay 1080p · 30nama · 340 MB", without the episode.
    var quality: String?
    var cover: URL?
    var poster: URL?
    var blurhash: String?
    /// Persian subtitles saved beside the file.
    var subtitlePath: String?
    /// The page of the subtitle site that lists Persian subtitles for this film or season.
    var subtitlePage: URL?
    var added: Date?

    var fraction: Double { expected > 0 ? min(1, Double(received) / Double(expected)) : 0 }

    var shelf: Shelf {
        if kind == "anime" { return .anime }
        return isSeries == true || season != nil ? .series : .movies
    }

    /// Episodes of one series stand together under this.
    var group: String { postID.map(String.init) ?? title }

    /// Enough of the title to draw its artwork and to hand to the player.
    var post: Post? {
        var poster: [String: Any] = [:]
        if let blurhash { poster["preview"] = blurhash }
        if let art = self.poster?.absoluteString { poster["webp"] = ["large": art, "big": art] }
        var image: [String: Any] = ["poster": poster]
        if let cover { image["cover"] = ["webp": cover.absoluteString] }
        var options: [String: Any] = ["is_series": shelf != .movies && (isSeries ?? true)]
        if let kind { options["title_type"] = kind }
        return Post(JSON(["id": postID ?? abs(title.hashValue % 1_000_000), "title": ["english": title, "local": title],
                          "options": options, "image": image] as [String: Any]))
    }

    /// An item saved before the app recorded what a file belonged to: the name says whether it is an episode.
    mutating func inferFromName() {
        guard kind == nil, season == nil,
              let match = fileName.range(of: #"[Ss](\d{1,2})[Ee](\d{1,3})"#, options: .regularExpression) else { return }
        let numbers = fileName[match].dropFirst().split(whereSeparator: { $0 == "E" || $0 == "e" }).compactMap { Int($0) }
        guard numbers.count == 2 else { return }
        season = numbers[0]
        episode = String(numbers[1])
        isSeries = true
        kind = "series"
    }
}

/// Fetches files from 30nama into a folder on this Mac.
///
/// Each file comes over several connections at once (`Transfer`), within the speed limit and the
/// download hours set in Settings. A download stops when the app is quit and goes on from where
/// it was when the app is next opened.
@MainActor
@Observable
final class Downloads {
    /// A player on this Mac that can open a downloaded file.
    struct Player: Identifiable {
        let name: String
        let app: URL
        let icon: NSImage
        var id: URL { app }
    }

    private(set) var items: [DownloadItem] = []
    @ObservationIgnored private var transfers: [String: Transfer] = [:]
    @ObservationIgnored private var playersByType: [String: [Player]] = [:]
    @ObservationIgnored private weak var app: AppModel?

    /// Where files are kept while they come in, with a record of which parts are here.
    nonisolated static var partsFolder: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(Bundle.main.bundleIdentifier ?? "30nama", isDirectory: true)
            .appendingPathComponent("Partial downloads", isDirectory: true)
    }

    /// Room left on the disk the downloads go to, for the list to show.
    var freeSpace: Int64? {
        (try? folder.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]))?.volumeAvailableCapacityForImportantUsage
    }

    var running: Int { items.filter { $0.state == .running }.count }

    /// Where finished files go: the folder chosen in Settings, or the user's Downloads.
    var folder: URL {
        if let chosen = app?.settings.downloadFolder, FileManager.default.fileExists(atPath: chosen) {
            return URL(fileURLWithPath: chosen, isDirectory: true)
        }
        return FileManager.default.urls(for: .downloadsDirectory, in: .userDomainMask)[0]
    }

    init(app: AppModel) {
        self.app = app
        if let data = UserDefaults.standard.data(forKey: "downloads"), let saved = try? JSONDecoder().decode([DownloadItem].self, from: data) {
            items = saved.map { item in
                var item = item
                item.inferFromName()
                return item
            }
        }
        // Versions before 1.2.5 gave downloads to the system's background service. Anything it
        // still has would come in twice; it is stopped, and fetched here from the start.
        let legacy = URLSession(configuration: .background(withIdentifier: (Bundle.main.bundleIdentifier ?? "30nama") + ".downloads"))
        legacy.getAllTasks { found in
            found.forEach { $0.cancel() }
            legacy.invalidateAndCancel()
        }
        for item in items where item.state == .running || item.state == .paused {
            if let saved = Transfer.saved(item.id, in: Downloads.partsFolder) {
                change(item.id) {
                    $0.received = saved.received
                    if saved.expected > 0 { $0.expected = saved.expected }
                }
            }
        }
        // What was coming in when the app was quit goes on now, within the rules and in its turn.
        for item in items where item.state == .running {
            change(item.id) {
                $0.state = .paused
                $0.queued = true
            }
        }
        applyRules()
        Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(30))
                self?.applyRules()
            }
        }
    }

    /// The speed limit and the download hours, as Settings has them now: called every half
    /// minute, and at once when they are changed.
    func applyRules() {
        guard let settings = app?.settings else { return }
        Transfer.limit.bytesPerSecond = Double(settings.downloadLimit) * 1_048_576
        let open = DownloadHours.open(from: settings.downloadFrom, to: settings.downloadTo, at: Date())
        for item in items {
            if !open, item.state == .running {
                transfers.removeValue(forKey: item.id)?.stop()
                change(item.id) {
                    $0.state = .paused
                    $0.held = true
                }
            } else if open, item.held == true {
                change(item.id) {
                    $0.held = nil
                    $0.queued = true
                }
            }
        }
        pump()
    }

    /// Starts the files waiting their turn while there is room, the one asked for first going
    /// first: so a whole season comes in an episode at a time, in order. Stops the newest ones
    /// again when there are more coming in than Settings now allows.
    private func pump() {
        defer { save() }
        guard hoursOpen else { return }
        let allowed = max(1, app?.settings.downloadsAtOnce ?? 1)
        let running = items.filter { $0.state == .running }.sorted { ($0.added ?? .distantPast) < ($1.added ?? .distantPast) }
        for extra in running.dropFirst(allowed) {
            transfers.removeValue(forKey: extra.id)?.stop()
            change(extra.id) {
                $0.state = .paused
                $0.queued = true
            }
        }
        var room = allowed - min(running.count, allowed)
        let waiting = items.filter { $0.queued == true && $0.state == .paused }.sorted { ($0.added ?? .distantPast) < ($1.added ?? .distantPast) }
        for item in waiting where room > 0 {
            change(item.id) {
                $0.queued = nil
                $0.state = .running
            }
            begin(item)
            room -= 1
        }
    }

    var hoursOpen: Bool {
        guard let settings = app?.settings else { return true }
        return DownloadHours.open(from: settings.downloadFrom, to: settings.downloadTo, at: Date())
    }

    // MARK: Actions

    /// Starts fetching one file. Asking for a file that is already in the list does nothing.
    func start(_ file: DownloadFile, of post: Post) {
        guard let app, !has(file) else { return }
        let loc = app.loc
        var parts: [String] = []
        if let season = file.season, let episode = file.episode { parts.append(loc.episodeTag(season: season, episode: episode)) }
        parts.append(file.summary)
        var item = DownloadItem(id: UUID().uuidString, title: post.title(loc.lang), detail: parts.joined(separator: " · "),
                                fileName: file.fileName, address: file.url, state: .running)
        item.postID = post.id
        item.kind = post.kind.rawValue
        item.isSeries = post.isSeries
        item.season = file.season
        item.episode = file.episode
        item.quality = file.summary
        item.cover = post.cover
        item.poster = post.poster.big ?? post.poster.large ?? post.poster.medium
        item.blurhash = post.poster.blurhash
        item.added = Date()
        item.subtitlePage = post.subtitlePages.first { $0.persian && ($0.season == nil || $0.season == file.season) }?.link
        items.removeAll { $0.fileName == file.fileName }
        item.state = .paused
        item.queued = true
        items.insert(item, at: 0)
        pump()
        // 30nama's own subtitle when it has one; otherwise the one on the subtitle site that fits this copy.
        if let subtitle = file.subtitle { fetchSubtitle(subtitle, for: item) } else { findSubtitle(item.id, quietly: true) }
        save()
    }

    /// Whether this file is already in the list. Its address carries a token that changes with
    /// every answer from the server, so a file is known by its name.
    func has(_ file: DownloadFile) -> Bool {
        items.contains { $0.fileName == file.fileName && $0.state != .failed }
    }

    private func begin(_ item: DownloadItem) {
        transfers.removeValue(forKey: item.id)?.stop()
        let id = item.id
        let transfer = Transfer(id: id, address: item.address, folder: Downloads.partsFolder,
                                progress: { received, expected in
                                    Task { @MainActor [weak self] in self?.progressed(id, received: received, expected: expected) }
                                },
                                finished: { outcome in
                                    Task { @MainActor [weak self] in self?.ended(id, outcome) }
                                })
        transfers[id] = transfer
        transfer.start()
    }

    func pause(_ id: String) {
        transfers.removeValue(forKey: id)?.stop()
        change(id) {
            $0.state = .paused
            $0.held = nil
            $0.queued = nil
        }
        // Its turn goes to the next one waiting.
        pump()
    }

    /// Back in line: it starts when there is room, and outside the download hours when they come round.
    func resume(_ id: String) {
        guard items.contains(where: { $0.id == id }) else { return }
        change(id) {
            $0.state = .paused
            $0.held = nil
            $0.queued = true
        }
        pump()
    }

    /// Stops a transfer and forgets it, or just forgets one that has ended. A finished file stays where it is.
    /// Takes a download off the list. A finished file stays where it is unless `trashing` says
    /// it should go too; then it and its subtitles are moved to the Trash, where they can be had back.
    func remove(_ id: String, trashing: Bool = false) {
        transfers.removeValue(forKey: id)?.stop()
        // Stopping writes its record once more; the folder is cleared after that.
        let folder = Downloads.partsFolder
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + 1) { Transfer.discard(id, in: folder) }
        if let item = items.first(where: { $0.id == id }) {
            if item.state != .done, let subtitle = item.subtitlePath {
                // Subtitles fetched for a film that never arrived would only be litter in the folder.
                try? FileManager.default.removeItem(atPath: subtitle)
            } else if item.state == .done, trashing {
                for path in [item.path, item.subtitlePath].compactMap({ $0 }) where FileManager.default.fileExists(atPath: path) {
                    try? FileManager.default.trashItem(at: URL(fileURLWithPath: path), resultingItemURL: nil)
                }
            }
        }
        items.removeAll { $0.id == id }
        pump()
    }

    func reveal(_ item: DownloadItem) {
        if let path = item.path, FileManager.default.fileExists(atPath: path) {
            NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: path)])
        } else {
            NSWorkspace.shared.open(folder)
        }
    }

    // MARK: Playing what was downloaded

    /// Plays a finished file in the app's own player, with the other downloaded episodes of the
    /// same series queued after it. The player picks the decoder: the system's for what it reads,
    /// mpv for the rest (Matroska, which is most of what 30nama offers).
    func play(_ item: DownloadItem) {
        guard let app, let path = item.path, FileManager.default.fileExists(atPath: path) else { return }
        do {
            let queue = item.shelf == .movies ? [item] : items
                .filter { $0.group == item.group && $0.state == .done && $0.path != nil }
                .sorted { ($0.season ?? 0, Episode.number($0.episode) ?? 0) < ($1.season ?? 0, Episode.number($1.episode) ?? 0) }
            let entries = queue.compactMap(streamItem)
            guard let post = item.post, let index = queue.firstIndex(where: { $0.id == item.id }), entries.count == queue.count else { return }
            let left = UserDefaults.standard.double(forKey: "position.local:" + item.id)
            let resume = left > 5 ? ContinueWatching(JSON(["file_id": "local:" + item.id, "time": left] as [String: Any])) : nil
            app.play(PlaybackRequest(post: post, items: entries, index: index, resume: resume))
        }
    }

    /// A downloaded file in the shape the player takes a stream in.
    private func streamItem(_ item: DownloadItem) -> StreamItem? {
        guard let path = item.path else { return nil }
        var file: [String: Any] = ["m3u8": URL(fileURLWithPath: path).absoluteString]
        if let subtitle = item.subtitlePath, FileManager.default.fileExists(atPath: subtitle) {
            file["subtitle"] = ["fa": URL(fileURLWithPath: subtitle).absoluteString]
        }
        var info: [String: Any] = ["file_id": "local:" + item.id, "season": item.season ?? 1]
        if let episode = item.episode { info["episode"] = episode }
        return StreamItem(JSON(["info": info, "options": ["token": PlayerEngine.localToken], "file": file] as [String: Any]))
    }

    /// The apps on this Mac that say they open this kind of file, the user's usual one first.
    func players(for item: DownloadItem) -> [Player] {
        guard let path = item.path else { return [] }
        let type = URL(fileURLWithPath: path).pathExtension.lowercased()
        if let known = playersByType[type] { return known }
        // Anything may claim a video file, a chat app to send it for one. Only players are wanted.
        let found = NSWorkspace.shared.urlsForApplications(toOpen: URL(fileURLWithPath: path))
            .filter { $0 != Bundle.main.bundleURL && Downloads.isPlayer($0) }
            .prefix(4)
            .map { Player(name: FileManager.default.displayName(atPath: $0.path).replacingOccurrences(of: ".app", with: ""),
                          app: $0, icon: NSWorkspace.shared.icon(forFile: $0.path)) }
        playersByType[type] = Array(found)
        return Array(found)
    }

    private static func isPlayer(_ app: URL) -> Bool {
        guard let bundle = Bundle(url: app) else { return false }
        let known = ["com.colliderli.iina", "org.videolan.vlc", "com.apple.QuickTimePlayerX", "io.mpv", "com.firecore.infuse", "com.movist.MovistPro", "com.elmedia.player"]
        if let id = bundle.bundleIdentifier, known.contains(where: { id.hasPrefix($0) }) { return true }
        let category = bundle.object(forInfoDictionaryKey: "LSApplicationCategoryType") as? String ?? ""
        return category.hasSuffix(".video") || category.hasSuffix(".entertainment")
    }

    func open(_ item: DownloadItem, with player: Player) {
        guard let path = item.path else { return }
        NSWorkspace.shared.open([URL(fileURLWithPath: path)], withApplicationAt: player.app, configuration: NSWorkspace.OpenConfiguration())
    }

    // MARK: What the system reports

    private func progressed(_ id: String, received: Int64, expected: Int64) {
        // Only from a transfer still wanted: one stopped a moment ago may still have a word in flight.
        guard transfers[id] != nil else { return }
        change(id) {
            $0.received = received
            if expected > 0 { $0.expected = expected }
        }
    }

    private func ended(_ id: String, _ outcome: Transfer.Outcome) {
        guard transfers.removeValue(forKey: id) != nil, let item = items.first(where: { $0.id == id }) else { return }
        defer { pump() }
        switch outcome {
        case .done(let partial):
            let target = free(folder.appendingPathComponent(item.fileName))
            do {
                try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                try FileManager.default.moveItem(at: partial, to: target)
                change(id) {
                    $0.state = .done
                    $0.path = target.path
                    if $0.expected > 0 { $0.received = $0.expected }
                }
                if let app { app.showToast("\(app.loc(.downloadDone)): \(item.title)") }
            } catch {
                change(id) { $0.state = .failed }
            }
        case .failed:
            change(id) { $0.state = .failed }
            if let app { app.showToast(app.loc(.downloadFailed)) }
        }
    }

    // MARK: Helpers

    private func change(_ id: String, _ edit: (inout DownloadItem) -> Void) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        edit(&items[index])
    }

    private func save() {
        UserDefaults.standard.set(try? JSONEncoder().encode(items), forKey: "downloads")
    }

    /// The address itself if nothing is there yet, else the same name with a number after it.
    private func free(_ wanted: URL) -> URL {
        var candidate = wanted, count = 2
        let base = wanted.deletingPathExtension().lastPathComponent, ext = wanted.pathExtension
        while FileManager.default.fileExists(atPath: candidate.path) {
            candidate = wanted.deletingLastPathComponent().appendingPathComponent(ext.isEmpty ? "\(base) \(count)" : "\(base) \(count).\(ext)")
            count += 1
        }
        return candidate
    }

    /// The downloads a subtitle is being looked for right now.
    private(set) var seeking: Set<String> = []

    /// Looks on the subtitle site for a subtitle in time with this copy and saves it beside the
    /// file under the file's name, so that any player picks it up by itself.
    func findSubtitle(_ id: String, quietly: Bool = false) {
        guard let item = items.first(where: { $0.id == id }), let page = item.subtitlePage, !seeking.contains(id) else {
            if !quietly, let app { app.showToast(app.loc(.subtitleMissing)) }
            return
        }
        seeking.insert(id)
        let target = (item.path.map { URL(fileURLWithPath: $0).deletingLastPathComponent() } ?? folder)
            .appendingPathComponent(URL(fileURLWithPath: item.fileName).deletingPathExtension().lastPathComponent + ".fa.srt")
        Task {
            let saved = await SubtitleFetcher.fetch(page: page, fileName: item.fileName, season: item.season,
                                                    episode: Episode.number(item.episode), to: target)
            seeking.remove(id)
            if saved {
                change(id) { $0.subtitlePath = target.path }
                save()
            }
            if !quietly, let app { app.showToast(app.loc(saved ? .subtitleSaved : .subtitleMissing)) }
        }
    }

    /// Fetches only the Persian subtitles of these files, for someone who already has the films:
    /// 30nama's own where it has them, else the ones on the subtitle site that fit each copy.
    /// They are saved in the download folder under the films' names. Returns how many were found.
    func subtitlesOnly(_ files: [DownloadFile], of post: Post) async -> Int {
        var saved = 0
        for file in files {
            let target = subtitleTarget(file)
            if let own = file.subtitle, let (data, _) = try? await URLSession.shared.data(from: own),
               let text = SubtitleSite.text(data), text.contains("-->"),
               (try? text.write(to: target, atomically: true, encoding: .utf8)) != nil {
                saved += 1
            } else if let page = post.subtitlePages.first(where: { $0.persian && ($0.season == nil || $0.season == file.season) })?.link,
                      await SubtitleFetcher.fetch(page: page, fileName: file.fileName, season: file.season,
                                                  episode: Episode.number(file.episode), to: target) {
                saved += 1
            }
        }
        if let app { app.showToast(saved > 0 ? app.loc(.subtitleSaved) : app.loc(.subtitleMissing)) }
        return saved
    }

    /// Whether this file's Persian subtitles are already in the download folder.
    func hasSubtitle(_ file: DownloadFile) -> Bool {
        FileManager.default.fileExists(atPath: subtitleTarget(file).path)
    }

    private func subtitleTarget(_ file: DownloadFile) -> URL {
        folder.appendingPathComponent(URL(fileURLWithPath: file.fileName).deletingPathExtension().lastPathComponent + ".fa.srt")
    }

    /// Persian subtitles are small, so they are simply fetched and put next to the film under its name.
    private func fetchSubtitle(_ address: URL, for item: DownloadItem) {
        let folder = folder, id = item.id, fileName = item.fileName
        Task {
            let saved: String? = await Task.detached {
                guard let (data, response) = try? await URLSession.shared.data(from: address),
                      (response as? HTTPURLResponse)?.statusCode ?? 200 < 400, !data.isEmpty else { return nil }
                let suggested = response.suggestedFilename.map { URL(fileURLWithPath: $0).pathExtension.lowercased() } ?? ""
                let ext = ["srt", "vtt", "ass", "zip"].contains(suggested) ? suggested : "srt"
                let target = folder.appendingPathComponent(URL(fileURLWithPath: fileName).deletingPathExtension().lastPathComponent + ".fa." + ext)
                return (try? data.write(to: target, options: .atomic)) != nil ? target.path : nil
            }.value
            guard let saved else { return }
            change(id) { $0.subtitlePath = saved }
            save()
        }
    }
}

// MARK: - The list

/// Everything being fetched and everything fetched, in three parts: films, series and anime.
/// Each part wears the artwork of the title that was downloaded into it last.
struct DownloadsView: View {
    @Environment(Downloads.self) private var downloads
    @Environment(\.loc) private var loc

    /// The part on show. Until the user picks one, it is the part the newest download is in.
    @State private var chosen: DownloadItem.Shelf?
    /// The series whose episodes are folded out.
    @State private var unfolded: Set<String> = []
    @State private var settled = false

    private var shelf: DownloadItem.Shelf { chosen ?? downloads.items.first?.shelf ?? .movies }
    private var shown: [DownloadItem] { downloads.items.filter { $0.shelf == shelf } }

    var body: some View {
        ZStack {
            AmbientBackground()
            StageView(post: shown.first?.post, dim: 0.5, settle: .zero, glow: false)
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 22) {
                    header
                    if shown.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "arrow.down.circle").font(.system(size: 44, weight: .light)).foregroundStyle(Theme.text3)
                            Text(loc(.noDownloads)).font(.app(17, .medium)).foregroundStyle(Theme.text2)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 120)
                    } else {
                        VStack(spacing: 10) {
                            ForEach(sections, id: \.key) { section in
                                if section.folds {
                                    SeriesGroup(items: section.items, open: unfolded.contains(section.key)) {
                                        withAnimation(Theme.glide) {
                                            if unfolded.contains(section.key) { unfolded.remove(section.key) } else { unfolded.insert(section.key) }
                                        }
                                    }
                                } else if let item = section.items.first {
                                    DownloadRow(item: item, style: .title)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 96)
                .padding(.bottom, 60)
                .frame(maxWidth: 1020)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.never)
        }
        .onAppear {
            // The series downloaded into most recently starts folded out.
            guard !settled else { return }
            settled = true
            if let latest = downloads.items.first(where: { $0.shelf != .movies && $0.season != nil }) { unfolded.insert(latest.group) }
        }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 14) {
            Text(loc(.downloads)).font(.app(34, .bold)).foregroundStyle(Theme.text1)
            HStack(spacing: 2) {
                ForEach(DownloadItem.Shelf.allCases, id: \.self) { part in
                    let count = downloads.items.filter { $0.shelf == part }.count
                    Button { chosen = part } label: {
                        HStack(spacing: 7) {
                            Text(loc(title(part))).font(.app(15.5, .semibold))
                            if count > 0 {
                                Text(loc.num(count))
                                    .font(.app(12, .bold))
                                    .padding(.horizontal, 7)
                                    .frame(height: 20)
                                    .background(Capsule().fill(Color.white.opacity(shelf == part ? 0.22 : 0.14)))
                            }
                        }
                        .foregroundStyle(shelf == part ? Theme.text1 : Theme.text1.opacity(0.7))
                        // Dark words on the dark glass until the pill is under them.
                        .animation(.easeOut(duration: 0.18).delay(shelf == part ? 0.14 : 0), value: shelf)
                        .padding(.horizontal, 18)
                        .frame(height: 40)
                        .contentShape(Capsule(style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .liquidTab(part)
                }
            }
            // Sized before the glass: see UpdatePill.
            .fixedSize()
            // The open part's white pill flows to the next like a drop of liquid, as in the top bar.
            .liquidPill(selected: AnyHashable(shelf))
            .padding(4)
            .glass(Capsule(style: .continuous))
            .fixedSize()
            .padding(.leading, 10)
            Spacer(minLength: 0)
            if let free = downloads.freeSpace {
                HStack(spacing: 4) {
                    Text(ByteCountFormatter.string(fromByteCount: free, countStyle: .file)).environment(\.layoutDirection, .leftToRight)
                    Text(loc(.freeSpace))
                }
                .font(.app(13, .medium))
                .foregroundStyle(Theme.text3)
                .lineLimit(1)
                .fixedSize()
            }
            Button { NSWorkspace.shared.open(downloads.folder) } label: {
                Label(downloads.folder.lastPathComponent, systemImage: "folder")
            }
            .buttonStyle(GlassButtonStyle(height: 34))
            .help(downloads.folder.path)
        }
    }

    private func title(_ part: DownloadItem.Shelf) -> S {
        switch part {
        case .movies: return .movies
        case .series: return .series
        case .anime: return .anime
        }
    }

    /// A film stands alone; the episodes of a series stand together, in the order they are watched.
    private var sections: [(key: String, folds: Bool, items: [DownloadItem])] {
        var order: [String] = []
        var groups: [String: [DownloadItem]] = [:]
        for item in shown {
            let key = item.season != nil ? item.group : item.id
            if groups[key] == nil { order.append(key) }
            groups[key, default: []].append(item)
        }
        return order.map { key in
            let items = groups[key] ?? []
            let folds = items.first?.season != nil
            let sorted = folds ? items.sorted { ($0.season ?? 0, Episode.number($0.episode) ?? 0) < ($1.season ?? 0, Episode.number($1.episode) ?? 0) } : items
            return (key, folds, sorted)
        }
    }
}

/// A series in the list: one bar that folds out into its downloaded episodes.
private struct SeriesGroup: View {
    let items: [DownloadItem]
    let open: Bool
    let toggle: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        VStack(spacing: 0) {
            Button(action: toggle) {
                HStack(spacing: 16) {
                    Thumb(item: items[0])
                    VStack(alignment: .leading, spacing: 4) {
                        Text(items[0].title).font(.app(17, .bold)).foregroundStyle(Theme.text1).lineLimit(1)
                        Text(summary).font(.app(13)).foregroundStyle(Theme.text3).lineLimit(1)
                    }
                    Spacer(minLength: 8)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.text2)
                        .rotationEffect(.degrees(open ? 180 : 0))
                        .frame(width: 34, height: 34)
                        .background(Circle().fill(Color.white.opacity(hovering ? 0.2 : 0.08)))
                }
                .padding(.horizontal, 18)
                .frame(minHeight: 84)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .onHover { hovering = $0 }
            if open {
                VStack(spacing: 0) {
                    ForEach(items) { item in
                        Divider().overlay(Color.white.opacity(0.06)).padding(.horizontal, 18)
                        DownloadRow(item: item, style: .episode)
                    }
                }
                .transition(.opacity)
            }
        }
        .glass(shape)
        .clipShape(shape)
    }

    private var summary: String {
        var parts = ["\(loc.num(items.count)) \(loc(.episode))"]
        let bytes = items.reduce(Int64(0)) { $0 + max($1.expected, $1.received) }
        // The size is Latin text inside a right-to-left line: held together so its parts do not trade places.
        if bytes > 0 { parts.append("\u{2068}" + ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file) + "\u{2069}") }
        return parts.joined(separator: " · ")
    }
}

/// The poster of what a download belongs to, small.
private struct Thumb: View {
    let item: DownloadItem

    var body: some View {
        ZStack {
            Theme.surface2
            RemoteImage(url: item.poster, blurhash: item.blurhash, maxPixel: Int(60 * Display.scale))
        }
        .frame(width: 40, height: 60)
        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
    }
}

private struct DownloadRow: View {
    enum Style { case title, episode }

    let item: DownloadItem
    let style: Style

    @Environment(Downloads.self) private var downloads
    @Environment(\.loc) private var loc
    /// The row is asking what removing should mean: off the list only, or the file as well.
    @State private var asking = false

    var body: some View {
        HStack(spacing: 16) {
            if style == .title {
                Thumb(item: item)
            } else {
                Image(systemName: symbol)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 40)
            }
            VStack(alignment: .leading, spacing: 5) {
                Text(heading).font(.app(style == .title ? 17 : 15, .bold)).foregroundStyle(Theme.text1).lineLimit(1)
                Text(style == .title ? item.detail : (item.quality ?? item.detail)).font(.app(13)).foregroundStyle(Theme.text3).lineLimit(1)
                if item.state == .running || item.state == .paused {
                    GeometryReader { geo in
                        Capsule().fill(Color.white.opacity(0.14))
                        Capsule().fill(item.state == .paused ? Theme.text3 : Theme.accent).frame(width: max(4, geo.size.width * item.fraction))
                    }
                    .frame(height: 5)
                    .padding(.top, 2)
                }
            }
            Spacer(minLength: 8)
            Text(status).font(.app(13, .medium)).foregroundStyle(item.state == .failed ? Theme.accent : Theme.text2).lineLimit(1)
                .environment(\.layoutDirection, item.held == true || item.queued == true ? (loc.lang == .en ? .leftToRight : .rightToLeft) : .leftToRight)
            if asking {
                HStack(spacing: 6) {
                    RowChoice(title: loc(.removeFromList)) { downloads.remove(item.id) }
                    RowChoice(title: loc(.moveToTrash), symbol: "trash") { downloads.remove(item.id, trashing: true) }
                    RowButton(symbol: "arrow.uturn.backward", help: loc(.cancel)) { asking = false }
                }
                .transition(.opacity)
            } else {
            HStack(spacing: 4) {
                switch item.state {
                case .running:
                    RowButton(symbol: "pause.fill", help: loc(.pause)) { downloads.pause(item.id) }
                case .paused where item.queued == true || item.held == true:
                    // Waiting already: the button takes it out of the line.
                    RowButton(symbol: "pause.fill", help: loc(.pause)) { downloads.pause(item.id) }
                case .paused, .failed:
                    RowButton(symbol: "arrow.clockwise", help: loc(.resume)) { downloads.resume(item.id) }
                case .done:
                    // The players on this Mac that take the file, by their own icons, as round as the buttons beside them.
                    ForEach(downloads.players(for: item)) { player in
                        Button { downloads.open(item, with: player) } label: {
                            Image(nsImage: player.icon)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 40, height: 40)
                                .frame(width: 34, height: 34)
                                .clipShape(Circle())
                                .overlay { Circle().strokeBorder(Color.white.opacity(0.14), lineWidth: 1) }
                                .contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .help("\(loc(.openIn)) \(player.name)")
                        .accessibilityLabel("\(loc(.openIn)) \(player.name)")
                    }
                    if item.subtitlePath != nil {
                        RowButton(symbol: "captions.bubble.fill", help: loc(.withSubtitle), tint: Theme.good) {}
                    } else if downloads.seeking.contains(item.id) {
                        ProgressView().controlSize(.small).frame(width: 34, height: 34)
                    } else if item.subtitlePage != nil {
                        RowButton(symbol: "captions.bubble", help: loc(.getSubtitle)) { downloads.findSubtitle(item.id) }
                    }
                    RowButton(symbol: "play.fill", help: loc(.play), bright: true) { downloads.play(item) }
                    RowButton(symbol: "folder", help: loc(.showInFinder)) { downloads.reveal(item) }
                }
                RowButton(symbol: "xmark", help: loc(.remove)) {
                    // Only a file that is there raises the question; anything else just leaves the list.
                    if item.state == .done, let path = item.path, FileManager.default.fileExists(atPath: path) {
                        asking = true
                    } else {
                        downloads.remove(item.id)
                    }
                }
            }
            .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.16), value: asking)
        .padding(.horizontal, 18)
        .frame(minHeight: style == .title ? 84 : 62)
        .background {
            if style == .title { Color.clear.glass(RoundedRectangle(cornerRadius: 16, style: .continuous)) }
        }
    }

    private var heading: String {
        guard style == .episode, let season = item.season, let episode = item.episode else { return item.title }
        return loc.episodeTag(season: season, episode: episode)
    }

    private var symbol: String {
        switch item.state {
        case .running: return "arrow.down.circle"
        case .paused: return "pause.circle"
        case .done: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.circle"
        }
    }

    private var tint: Color {
        item.state == .done ? Theme.good : item.state == .failed ? Theme.accent : Theme.text2
    }

    private var status: String {
        let size = { (bytes: Int64) in ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file) }
        switch item.state {
        case .done: return item.expected > 0 ? size(item.expected) : ""
        case .failed: return loc(.downloadFailed)
        case .paused where item.held == true || (item.queued == true && !downloads.hoursOpen): return loc(.waitingForHours)
        case .paused where item.queued == true: return loc(.queued)
        case .running, .paused:
            return item.expected > 0 ? "\(size(item.received)) / \(size(item.expected))" : size(item.received)
        }
    }
}

/// One of the answers a row offers when it asks, spelled out.
private struct RowChoice: View {
    let title: String
    var symbol: String? = nil
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let symbol { Image(systemName: symbol).font(.system(size: 12, weight: .bold)) }
                Text(title).font(.app(13, .semibold)).lineLimit(1)
            }
            .foregroundStyle(Theme.text1)
            .padding(.horizontal, 14)
            .frame(height: 34)
            .background(Capsule().fill(Color.white.opacity(hovering ? 0.22 : 0.1)))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
    }
}

private struct RowButton: View {
    let symbol: String
    let help: String
    /// The row's main action: white, like the play button elsewhere.
    var bright = false
    var tint: Color? = nil
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(bright ? Theme.focusInk : tint ?? Theme.text1)
                .frame(width: 34, height: 34)
                .background(Circle().fill(bright ? Color.white.opacity(hovering ? 1 : 0.92) : Color.white.opacity(hovering ? 0.2 : 0.08)))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .help(help)
        .accessibilityLabel(help)
    }
}


// MARK: - The disc beside the bar

/// Downloads from any screen: a glass disc beside the bar at the top of the window. A ring around
/// it fills as the running downloads come in, and a click opens the list.
struct DownloadsDisc: View {
    let selected: Bool
    let action: () -> Void

    @Environment(Downloads.self) private var downloads
    @Environment(\.loc) private var loc
    @State private var hovering = false

    private var busy: [DownloadItem] { downloads.items.filter { $0.state == .running || $0.state == .paused } }

    /// How far along everything still coming in is, as one figure.
    private var fraction: Double {
        let expected = busy.reduce(Int64(0)) { $0 + $1.expected }
        let received = busy.reduce(Int64(0)) { $0 + $1.received }
        return expected > 0 ? min(1, Double(received) / Double(expected)) : 0
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                // A tab of the bar beside it: its glass is drawn under the bar's liquid pill, and
                // the pill flows into it when its page is the open one.
                Circle().fill(Color.white.opacity(!selected && hovering ? 0.12 : 0))
                    .liquidTab(MacSection.downloads)
                    .padding(4)
                if !busy.isEmpty {
                    Circle().strokeBorder(Color.white.opacity(0.14), lineWidth: 3)
                    Circle()
                        .trim(from: 0, to: max(0.02, fraction))
                        .stroke(Theme.accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .padding(1.5)
                        .animation(.easeOut(duration: 0.5), value: fraction)
                }
                Image(systemName: busy.isEmpty ? "arrow.down.to.line" : "arrow.down")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Theme.text1.opacity(selected || hovering ? 1 : 0.78))
            }
            .frame(width: 48, height: 48)
            .overlay(alignment: .topTrailing) {
                if !busy.isEmpty {
                    Text(loc.num(busy.count))
                        .font(.app(11, .heavy))
                        .foregroundStyle(.white)
                        .frame(minWidth: 19, minHeight: 19)
                        .background(Circle().fill(Theme.accent))
                        .offset(x: 3, y: -3)
                }
            }
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.16), value: hovering)
        .animation(Theme.focusSpring, value: selected)
        .help(loc(.downloads))
        .accessibilityLabel(loc(.downloads))
        .accessibilityValue(busy.isEmpty ? "" : loc.num("\(Int(fraction * 100))٪"))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
