import AppKit

/// Keeps the app up to date from the project's GitHub releases: looks for a newer one now and
/// then, downloads its disk image on request, checks the project's signature on it, and puts the
/// new app in place of this one when the user relaunches.
///
/// Every step goes to update.log in the app's support folder, so a failure on someone's Mac can be
/// read rather than guessed. A file update-feed.txt there, holding an address, points even a
/// released copy at a test feed: an installed copy can then be tried end to end the way a user
/// runs it, which a copy started from a terminal is not (it borrows the terminal's permissions).
/// That is safe, since only an image signed with the project's key and newer than this app is
/// ever put in place.
@MainActor
@Observable
final class Updater {
    enum Phase: Equatable {
        case idle
        case available(MacRelease)
        case downloading(MacRelease, progress: Double)
        case ready(MacRelease)
        case failed(MacRelease)
    }

    private(set) var phase: Phase = .idle
    /// Put off until the app is next opened.
    private(set) var postponed = false

    @ObservationIgnored private var staged: URL?
    @ObservationIgnored private var lastLook = Date.distantPast

    init() { checkPending() }

    static var current: String {
        #if DEBUG
        if let version = DebugLaunch.version { return version }
        #endif
        return Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
    }

    // MARK: Files beside the app's other data

    nonisolated private static var folder: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(Bundle.main.bundleIdentifier ?? "30nama", isDirectory: true)
    }
    nonisolated private static var logFile: URL { folder.appendingPathComponent("update.log") }
    nonisolated private static var pendingFile: URL { folder.appendingPathComponent("update-pending.json") }
    nonisolated private static var feedFile: URL { folder.appendingPathComponent("update-feed.txt") }

    /// One line in update.log, which starts again past a hundred kilobytes.
    nonisolated private static func log(_ line: String) {
        let manager = FileManager.default
        try? manager.createDirectory(at: folder, withIntermediateDirectories: true)
        if let size = (try? manager.attributesOfItem(atPath: logFile.path))?[.size] as? Int, size > 100_000 {
            try? manager.removeItem(at: logFile)
        }
        let stamp = ISO8601DateFormatter.string(from: Date(), timeZone: .current, formatOptions: [.withFullDate, .withFullTime, .withSpaceBetweenDateAndTime])
        let entry = Data("\(stamp) [\(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?")] \(line)\n".utf8)
        if let handle = try? FileHandle(forWritingTo: logFile) {
            defer { try? handle.close() }
            _ = try? handle.seekToEnd()
            try? handle.write(contentsOf: entry)
        } else {
            try? entry.write(to: logFile)
        }
    }

    /// A test feed's address, from update-feed.txt (or, in a build being worked on, NAMA_UPDATE_FEED).
    private static var testFeed: URL? {
        #if DEBUG
        if let feed = DebugLaunch.updateFeed { return feed }
        #endif
        guard let text = try? String(contentsOf: feedFile, encoding: .utf8),
              let url = URL(string: text.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme == "https" || url.scheme == "http" else { return nil }
        return url
    }

    /// An update swapped in last time: arrived if this is that version. If this is still the old
    /// one, it went wrong after the app quit, and this is the first chance to say so.
    private func checkPending() {
        guard let pending = PendingUpdate(data: try? Data(contentsOf: Updater.pendingFile)) else { return }
        try? FileManager.default.removeItem(at: Updater.pendingFile)
        if pending.arrived(in: Updater.current) {
            Updater.log("opened as \(Updater.current): the update to \(pending.version) arrived")
        } else {
            Updater.log("opened as \(Updater.current), not \(pending.version): the update did not arrive")
            phase = .failed(MacRelease(version: pending.version, page: pending.page, image: pending.page, signature: pending.page))
        }
    }

    /// Looks for a newer release, at most every few hours: GitHub answers sixty times an hour per address.
    func look() async {
        guard phase == .idle, Date().timeIntervalSince(lastLook) > 4 * 3600 else { return }
        lastLook = Date()
        let test = Updater.testFeed
        if let test { Updater.log("looking at the test feed \(test.absoluteString)") }
        var request = URLRequest(url: test ?? MacRelease.feed)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 20
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let feed = try? JSON.parse(data) else {
            if test != nil { Updater.log("the test feed did not answer") }
            return
        }
        guard let release = MacRelease.newest(in: feed, newerThan: Updater.current) else { return }
        Updater.log("found \(release.version)")
        phase = .available(release)
    }

    func postpone() { postponed = true }

    /// The app can replace itself only where it may write: not from the disk image, nor from a
    /// folder this user cannot change. Anywhere else the download page is opened instead.
    private var canReplace: Bool {
        let folder = Bundle.main.bundleURL.deletingLastPathComponent().path
        let readOnlyVolume = (try? Bundle.main.bundleURL.resourceValues(forKeys: [.volumeIsReadOnlyKey]).volumeIsReadOnly) ?? true
        return !readOnlyVolume && FileManager.default.isWritableFile(atPath: folder)
    }

    func install() {
        guard case .available(let release) = phase else { return }
        guard canReplace else {
            Updater.log("cannot write where the app is (\(Bundle.main.bundleURL.deletingLastPathComponent().path)): opened the release page")
            NSWorkspace.shared.open(release.page)
            return
        }
        phase = .downloading(release, progress: 0)
        Task {
            do {
                staged = try await fetch(release)
                Updater.log("ready: \(release.version) waits beside the app")
                phase = .ready(release)
            } catch {
                Updater.log("failed: \(error)")
                phase = .failed(release)
            }
        }
    }

    /// Download, signature, and a copy of the app out of the image, next to this one.
    private func fetch(_ release: MacRelease) async throws -> URL {
        Updater.log("downloading \(release.image.absoluteString)")
        let image: URL
        do {
            image = try await Download.file(release.image) { [weak self] fraction in
                Task { @MainActor in
                    guard let self, case .downloading = self.phase else { return }
                    // In steps, so the bar is not redrawn for every packet.
                    self.phase = .downloading(release, progress: (fraction * 100).rounded() / 100)
                }
            }
        } catch {
            throw UpdateError.step("download", error)
        }
        let signature: Data
        do {
            (signature, _) = try await URLSession.shared.data(from: release.signature)
        } catch {
            // The image is no use without it; it is not left behind in the temporary folder.
            try? FileManager.default.removeItem(at: image)
            throw UpdateError.step("signature download", error)
        }
        let expectedID = Bundle.main.bundleIdentifier
        let place = Bundle.main.bundleURL
        return try await Task.detached {
            defer { try? FileManager.default.removeItem(at: image) }
            let data = try Data(contentsOf: image, options: .mappedIfSafe)
            Updater.log("downloaded \(data.count) bytes")
            guard UpdateSignature.isValid(String(decoding: signature, as: UTF8.self), for: data) else { throw UpdateError.signature }
            Updater.log("signature checked")
            // Opened where macOS opens any disk image, out of sight. Mounted in a folder of the
            // app's own instead, its contents could not be read by an app opened from the Finder:
            // macOS refused without a word (1.2.0 to 1.2.3).
            let output = try Updater.run("/usr/bin/hdiutil", ["attach", "-plist", "-nobrowse", "-readonly", "-noautoopen", image.path])
            guard let mount = DiskImage.mountPoint(inAttachOutput: output) else { throw UpdateError.contents("no volume in hdiutil's answer") }
            Updater.log("opened the image at \(mount.path)")
            defer { _ = try? Updater.run("/usr/bin/hdiutil", ["detach", mount.path, "-force"]) }
            let items: [URL]
            do {
                items = try FileManager.default.contentsOfDirectory(at: mount, includingPropertiesForKeys: nil)
            } catch {
                throw UpdateError.step("reading the image", error)
            }
            guard let app = items.first(where: { $0.pathExtension == "app" }) else { throw UpdateError.contents("no app in the image") }
            // Read from the file itself: Bundle(url:) may answer from its cache for an app with this app's identifier.
            let plist = try? PropertyListSerialization.propertyList(from: Data(contentsOf: app.appendingPathComponent("Contents/Info.plist")), format: nil)
            let info = plist as? [String: Any] ?? [:]
            guard info["CFBundleIdentifier"] as? String == expectedID,
                  info["CFBundleShortVersionString"] as? String == release.version else {
                throw UpdateError.contents("\(info["CFBundleIdentifier"] ?? "?") \(info["CFBundleShortVersionString"] ?? "?") is not \(expectedID ?? "?") \(release.version)")
            }
            // Beside the app, on the same disk, so it can be swapped in at once.
            let folder = try FileManager.default.url(for: .itemReplacementDirectory, in: .userDomainMask, appropriateFor: place, create: true)
            let copy = folder.appendingPathComponent(place.lastPathComponent)
            _ = try Updater.run("/usr/bin/ditto", [app.path, copy.path])
            Updater.log("copied \(release.version) to \(folder.path)")
            return copy
        }.value
    }

    /// Puts the new app where this one is and opens it once this one has quit.
    func relaunch() {
        guard case .ready(let release) = phase, let staged else { return }
        let place = Bundle.main.bundleURL
        // Written first: if what follows goes wrong once this app is gone, the next one opened knows.
        try? PendingUpdate(version: release.version, page: release.page).data.write(to: Updater.pendingFile)
        do {
            // One swap on one disk: the old app stays whole until the new one is whole in its place.
            _ = try FileManager.default.replaceItemAt(place, withItemAt: staged)
        } catch {
            try? FileManager.default.removeItem(at: Updater.pendingFile)
            Updater.log("failed: could not put \(release.version) in place of the app: \(error)")
            phase = .failed(release)
            return
        }
        Updater.log("put \(release.version) in place; relaunching")
        let opener = Process()
        opener.executableURL = URL(fileURLWithPath: "/bin/sh")
        opener.arguments = ["-c", "while kill -0 \(ProcessInfo.processInfo.processIdentifier) 2>/dev/null; do sleep 0.2; done; /usr/bin/open \"$0\"", place.path]
        try? opener.run()
        NSApp.terminate(nil)
    }

    func openPage() {
        if case .failed(let release) = phase { NSWorkspace.shared.open(release.page) }
    }

    /// What the tool wrote. Its errors go to update.log with the step that failed.
    @discardableResult
    nonisolated private static func run(_ tool: String, _ arguments: [String]) throws -> Data {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: tool)
        process.arguments = arguments
        let output = Pipe(), errors = Pipe()
        process.standardOutput = output
        process.standardError = errors
        try process.run()
        // Read before waiting: a tool that fills the pipe would otherwise wait for us forever.
        let data = output.fileHandleForReading.readDataToEndOfFile()
        let complaint = errors.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            let said = String(decoding: complaint.suffix(400), as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
            throw UpdateError.tool("\((tool as NSString).lastPathComponent) \(arguments.first ?? "") exited \(process.terminationStatus): \(said)")
        }
        return data
    }

    enum UpdateError: Error, CustomStringConvertible {
        case signature, contents(String), tool(String), step(String, Error)

        var description: String {
            switch self {
            case .signature: return "the signature does not match"
            case .contents(let what): return "image contents: \(what)"
            case .tool(let what): return what
            case .step(let step, let error): return "\(step): \(error.localizedDescription)"
            }
        }
    }
}

/// One file to disk, telling how far it has got.
private final class Download: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let progress: @Sendable (Double) -> Void
    private var continuation: CheckedContinuation<URL, Error>?

    private init(progress: @escaping @Sendable (Double) -> Void) {
        self.progress = progress
    }

    static func file(_ url: URL, progress: @escaping @Sendable (Double) -> Void) async throws -> URL {
        let download = Download(progress: progress)
        let session = URLSession(configuration: .ephemeral, delegate: download, delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        return try await withCheckedThrowingContinuation { continuation in
            download.continuation = continuation
            session.downloadTask(with: url).resume()
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData _: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard totalBytesExpectedToWrite > 0 else { return }
        progress(Double(totalBytesWritten) / Double(totalBytesExpectedToWrite))
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        // The system removes `location` as soon as this returns.
        let kept = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".dmg")
        let status = (downloadTask.response as? HTTPURLResponse)?.statusCode ?? 0
        if (200..<300).contains(status), (try? FileManager.default.moveItem(at: location, to: kept)) != nil {
            continuation?.resume(returning: kept)
        } else {
            continuation?.resume(throwing: URLError(.badServerResponse))
        }
        continuation = nil
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error {
            continuation?.resume(throwing: error)
            continuation = nil
        }
    }
}
