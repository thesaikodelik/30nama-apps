import SwiftUI

/// One download, as the list shows it and as it is remembered between launches.
struct DownloadItem: Identifiable, Codable, Equatable {
    enum State: String, Codable { case running, paused, done, failed }

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

    var fraction: Double { expected > 0 ? min(1, Double(received) / Double(expected)) : 0 }
}

/// Fetches files from 30nama into a folder on this Mac.
///
/// The work is given to the system's background transfer service, so a download goes on while
/// the app is closed and is picked up again when it is next opened.
@MainActor
@Observable
final class Downloads {
    private(set) var items: [DownloadItem] = []

    @ObservationIgnored private var session: URLSession!
    @ObservationIgnored private var tasks: [String: URLSessionDownloadTask] = [:]
    @ObservationIgnored private var resumeData: [String: Data] = [:]
    @ObservationIgnored private var lastShown: [String: Date] = [:]
    @ObservationIgnored private let relay = Relay()
    @ObservationIgnored private weak var app: AppModel?

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
            items = saved
        }
        relay.owner = self
        let configuration = URLSessionConfiguration.background(withIdentifier: (Bundle.main.bundleIdentifier ?? "30nama") + ".downloads")
        configuration.isDiscretionary = false
        configuration.sessionSendsLaunchEvents = false
        session = URLSession(configuration: configuration, delegate: relay, delegateQueue: .main)
        // Transfers the system kept going while the app was away.
        session.getAllTasks { [weak self] found in
            Task { @MainActor in self?.adopt(found.compactMap { $0 as? URLSessionDownloadTask }) }
        }
    }

    private func adopt(_ found: [URLSessionDownloadTask]) {
        for task in found {
            guard let id = task.taskDescription, items.contains(where: { $0.id == id }) else {
                task.cancel()
                continue
            }
            tasks[id] = task
            change(id) {
                $0.state = task.state == .suspended ? .paused : .running
                $0.received = task.countOfBytesReceived
                if task.countOfBytesExpectedToReceive > 0 { $0.expected = task.countOfBytesExpectedToReceive }
            }
        }
        // Marked as running but unknown to the system: it was lost, and can be started again.
        for item in items where item.state == .running && tasks[item.id] == nil {
            change(item.id) { $0.state = .failed }
        }
    }

    // MARK: Actions

    /// Starts fetching one file. Asking for a file that is already in the list does nothing.
    func start(_ file: DownloadFile, of post: Post) {
        guard let app, !items.contains(where: { $0.address.path == file.url.path && $0.state != .failed }) else { return }
        let loc = app.loc
        var parts: [String] = []
        if let season = file.season, let episode = file.episode { parts.append(loc.episodeTag(season: season, episode: episode)) }
        parts.append(file.summary)
        let item = DownloadItem(id: UUID().uuidString, title: post.title(loc.lang), detail: parts.joined(separator: " · "),
                                fileName: file.fileName, address: file.url, state: .running)
        items.removeAll { $0.address.path == file.url.path }
        items.insert(item, at: 0)
        begin(item)
        if let subtitle = file.subtitle { fetchSubtitle(subtitle, beside: file.fileName) }
        save()
    }

    private func begin(_ item: DownloadItem) {
        let task = resumeData[item.id].map { session.downloadTask(withResumeData: $0) } ?? session.downloadTask(with: item.address)
        resumeData[item.id] = nil
        task.taskDescription = item.id
        tasks[item.id] = task
        task.resume()
    }

    func pause(_ id: String) {
        tasks[id]?.suspend()
        change(id) { $0.state = .paused }
        save()
    }

    func resume(_ id: String) {
        guard let item = items.first(where: { $0.id == id }) else { return }
        if let task = tasks[id], task.state == .suspended {
            task.resume()
        } else {
            begin(item)
        }
        change(id) { $0.state = .running }
        save()
    }

    /// Stops a transfer and forgets it, or just forgets one that has ended. A finished file stays where it is.
    func remove(_ id: String) {
        tasks[id]?.cancel()
        tasks[id] = nil
        resumeData[id] = nil
        items.removeAll { $0.id == id }
        save()
    }

    func reveal(_ item: DownloadItem) {
        if let path = item.path, FileManager.default.fileExists(atPath: path) {
            NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: path)])
        } else {
            NSWorkspace.shared.open(folder)
        }
    }

    func open(_ item: DownloadItem) {
        guard let path = item.path else { return }
        NSWorkspace.shared.open(URL(fileURLWithPath: path))
    }

    // MARK: What the system reports

    fileprivate func progressed(_ id: String, received: Int64, expected: Int64) {
        // A few times a second is plenty for a bar; every packet would redraw the list all day.
        if let last = lastShown[id], Date().timeIntervalSince(last) < 0.5 { return }
        lastShown[id] = Date()
        change(id) {
            $0.received = received
            if expected > 0 { $0.expected = expected }
        }
    }

    /// The file has arrived in a temporary place that is cleared as soon as this returns.
    fileprivate func arrived(_ id: String, at temporary: URL, status: Int) {
        guard let item = items.first(where: { $0.id == id }) else { return }
        guard status < 400 else {
            change(id) { $0.state = .failed }
            return
        }
        let target = free(folder.appendingPathComponent(item.fileName))
        do {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try FileManager.default.moveItem(at: temporary, to: target)
            change(id) {
                $0.state = .done
                $0.path = target.path
                if $0.expected > 0 { $0.received = $0.expected }
            }
            if let app { app.showToast("\(app.loc(.downloadDone)): \(item.title)") }
        } catch {
            change(id) { $0.state = .failed }
        }
    }

    fileprivate func ended(_ id: String, error: Error?) {
        tasks[id] = nil
        defer { save() }
        guard let error else { return }
        let failure = error as NSError
        // Removing an item cancels its transfer; that is not a failure to report.
        guard failure.code != NSURLErrorCancelled, items.contains(where: { $0.id == id }) else { return }
        resumeData[id] = failure.userInfo[NSURLSessionDownloadTaskResumeData] as? Data
        change(id) { $0.state = .failed }
        if let app { app.showToast(app.loc(.downloadFailed)) }
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

    /// Persian subtitles are small, so they are simply fetched and put next to the film under its name.
    private func fetchSubtitle(_ address: URL, beside fileName: String) {
        let folder = folder
        Task.detached {
            guard let (data, response) = try? await URLSession.shared.data(from: address),
                  (response as? HTTPURLResponse)?.statusCode ?? 200 < 400, !data.isEmpty else { return }
            let suggested = response.suggestedFilename.map { URL(fileURLWithPath: $0).pathExtension } ?? ""
            let ext = ["srt", "vtt", "ass", "zip"].contains(suggested.lowercased()) ? suggested.lowercased() : "srt"
            let name = URL(fileURLWithPath: fileName).deletingPathExtension().lastPathComponent + ".fa." + ext
            try? data.write(to: folder.appendingPathComponent(name), options: .atomic)
        }
    }
}

/// The transfer service speaks to an Objective-C delegate; this passes what it says on.
private final class Relay: NSObject, URLSessionDownloadDelegate {
    @MainActor weak var owner: Downloads?

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64,
                    totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard let id = downloadTask.taskDescription else { return }
        MainActor.assumeIsolated { owner?.progressed(id, received: totalBytesWritten, expected: totalBytesExpectedToWrite) }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let id = downloadTask.taskDescription else { return }
        let status = (downloadTask.response as? HTTPURLResponse)?.statusCode ?? 200
        MainActor.assumeIsolated { owner?.arrived(id, at: location, status: status) }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let id = task.taskDescription else { return }
        MainActor.assumeIsolated { owner?.ended(id, error: error) }
    }
}

// MARK: - The list

/// Everything being fetched and everything fetched, newest first.
struct DownloadsView: View {
    @Environment(Downloads.self) private var downloads
    @Environment(\.loc) private var loc

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 22) {
                    HStack(alignment: .firstTextBaseline, spacing: 14) {
                        Text(loc(.downloads)).font(.app(34, .bold)).foregroundStyle(Theme.text1)
                        Spacer(minLength: 0)
                        Button { NSWorkspace.shared.open(downloads.folder) } label: {
                            Label(downloads.folder.lastPathComponent, systemImage: "folder")
                        }
                        .buttonStyle(GlassButtonStyle(height: 34))
                        .help(downloads.folder.path)
                    }
                    if downloads.items.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "arrow.down.circle").font(.system(size: 44, weight: .light)).foregroundStyle(Theme.text3)
                            Text(loc(.noDownloads)).font(.app(17, .medium)).foregroundStyle(Theme.text2)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, 120)
                    } else {
                        VStack(spacing: 10) {
                            ForEach(downloads.items) { item in DownloadRow(item: item) }
                        }
                    }
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 96)
                .padding(.bottom, 60)
                .frame(maxWidth: 980)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
        }
    }
}

private struct DownloadRow: View {
    let item: DownloadItem

    @Environment(Downloads.self) private var downloads
    @Environment(\.loc) private var loc

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: symbol)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(item.state == .done ? Theme.good : item.state == .failed ? Theme.accent : Theme.text2)
                .frame(width: 30)
            VStack(alignment: .leading, spacing: 5) {
                Text(item.title).font(.app(16, .bold)).foregroundStyle(Theme.text1).lineLimit(1)
                Text(item.detail).font(.app(13)).foregroundStyle(Theme.text3).lineLimit(1)
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
            Text(status).font(.app(13, .medium)).foregroundStyle(Theme.text2).lineLimit(1)
                .environment(\.layoutDirection, .leftToRight)
            HStack(spacing: 4) {
                switch item.state {
                case .running:
                    RowButton(symbol: "pause.fill", help: loc(.pause)) { downloads.pause(item.id) }
                case .paused, .failed:
                    RowButton(symbol: "arrow.clockwise", help: loc(.resume)) { downloads.resume(item.id) }
                case .done:
                    RowButton(symbol: "play.fill", help: loc(.open)) { downloads.open(item) }
                    RowButton(symbol: "folder", help: loc(.showInFinder)) { downloads.reveal(item) }
                }
                RowButton(symbol: "xmark", help: loc(.remove)) { downloads.remove(item.id) }
            }
        }
        .padding(.horizontal, 18)
        .frame(minHeight: 76)
        .glass(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var symbol: String {
        switch item.state {
        case .running: return "arrow.down.circle"
        case .paused: return "pause.circle"
        case .done: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.circle"
        }
    }

    private var status: String {
        let size = { (bytes: Int64) in ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file) }
        switch item.state {
        case .done: return item.expected > 0 ? size(item.expected) : ""
        case .failed: return loc(.downloadFailed)
        case .running, .paused:
            return item.expected > 0 ? "\(size(item.received)) / \(size(item.expected))" : size(item.received)
        }
    }
}

private struct RowButton: View {
    let symbol: String
    let help: String
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Theme.text1)
                .frame(width: 34, height: 34)
                .background(Circle().fill(Color.white.opacity(hovering ? 0.2 : 0.08)))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .help(help)
    }
}

// MARK: - Choosing what to download

/// The files 30nama offers for one title. A film lists its qualities; a series its seasons, the
/// qualities each comes in, and the episodes.
struct DownloadPicker: View {
    let post: Post

    @Environment(AppModel.self) private var app
    @Environment(Downloads.self) private var downloads
    @Environment(\.loc) private var loc
    @Environment(\.dismiss) private var dismiss

    @State private var options: DownloadOptions?
    @State private var message: String?
    @State private var season = 1
    @State private var pack: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(loc(.download)).font(.app(14, .semibold)).foregroundStyle(Theme.text3)
                    Text(post.title(loc.lang)).font(.app(22, .bold)).foregroundStyle(Theme.text1).lineLimit(1)
                }
                Spacer(minLength: 0)
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 12, weight: .bold)).foregroundStyle(Theme.text1)
                        .frame(width: 30, height: 30).background(Circle().fill(Color.white.opacity(0.12))).contentShape(Circle())
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.cancelAction)
            }
            if let options {
                if options.isEmpty {
                    note(loc(.noDownload))
                } else if options.packs.isEmpty {
                    list(options.files) { $0.summary }
                } else {
                    series(options)
                }
            } else if let message {
                note(message)
            } else {
                ProgressView().controlSize(.large).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .padding(22)
        .frame(width: 640, height: 580)
        .background(Theme.bg1)
        .environment(\.layoutDirection, loc.lang.isRTL ? .rightToLeft : .leftToRight)
        .preferredColorScheme(.dark)
        .task { await load() }
    }

    private func note(_ text: String) -> some View {
        Text(text).font(.app(16, .medium)).foregroundStyle(Theme.text2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func series(_ options: DownloadOptions) -> some View {
        let packs = options.packs.filter { $0.season == season }
        let chosen = packs.first { $0.id == pack } ?? packs.first
        return VStack(alignment: .leading, spacing: 12) {
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(options.seasons, id: \.self) { number in
                        PillChoice(title: "\(loc(.season)) \(loc.num(number))", selected: season == number) {
                            season = number
                            pack = nil
                        }
                    }
                }
            }
            .scrollIndicators(.hidden)
            ScrollView(.horizontal) {
                HStack(spacing: 8) {
                    ForEach(packs) { option in
                        PillChoice(title: option.summary, selected: option.id == chosen?.id) { pack = option.id }
                    }
                }
            }
            .scrollIndicators(.hidden)
            if let chosen {
                HStack {
                    Spacer(minLength: 0)
                    Button {
                        for file in chosen.files { downloads.start(file, of: post) }
                        app.showToast(loc(.downloadStarted))
                        dismiss()
                    } label: {
                        Label("\(loc(.allEpisodes)) (\(loc.num(chosen.files.count)))", systemImage: "arrow.down.to.line")
                    }
                    .buttonStyle(GlassButtonStyle(height: 34))
                }
                list(chosen.files) { file in
                    "\(loc(.episode)) \(loc.num(file.episode ?? ""))"
                }
            }
        }
    }

    private func list(_ files: [DownloadFile], label: @escaping (DownloadFile) -> String) -> some View {
        ScrollView(.vertical) {
            LazyVStack(spacing: 6) {
                ForEach(files) { file in
                    HStack(spacing: 10) {
                        Text(label(file)).font(.app(14, .semibold)).foregroundStyle(Theme.text1).lineLimit(1)
                        ForEach(file.tags.prefix(3), id: \.self) { tag in
                            Text(tag).font(.app(11, .bold)).foregroundStyle(Theme.text2)
                                .padding(.horizontal, 7).frame(height: 20)
                                .background(Capsule().fill(Color.white.opacity(0.1)))
                        }
                        if file.subtitle != nil {
                            Image(systemName: "captions.bubble.fill").font(.system(size: 11)).foregroundStyle(Theme.good).help(loc(.persianSub))
                        }
                        Spacer(minLength: 0)
                        let listed = downloads.items.contains { $0.address.path == file.url.path && $0.state != .failed }
                        Button {
                            downloads.start(file, of: post)
                            app.showToast(loc(.downloadStarted))
                        } label: {
                            Image(systemName: listed ? "checkmark" : "arrow.down.to.line")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(listed ? Theme.good : Theme.text1)
                                .frame(width: 34, height: 34)
                                .background(Circle().fill(Color.white.opacity(0.1)))
                                .contentShape(Circle())
                        }
                        .buttonStyle(.plain)
                        .disabled(listed)
                        .help(loc(.download))
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 46)
                    .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color.white.opacity(0.05)))
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
