import AppKit

/// Keeps the app up to date from the project's GitHub releases: looks for a newer one now and
/// then, downloads its disk image on request, checks the project's signature on it, and puts the
/// new app in place of this one when the user relaunches.
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

    static var current: String {
        #if DEBUG
        if let version = DebugLaunch.version { return version }
        #endif
        return Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
    }

    private static var feed: URL {
        #if DEBUG
        if let feed = DebugLaunch.updateFeed { return feed }
        #endif
        return MacRelease.feed
    }

    /// Looks for a newer release, at most every few hours: GitHub answers sixty times an hour per address.
    func look() async {
        guard phase == .idle, Date().timeIntervalSince(lastLook) > 4 * 3600 else { return }
        lastLook = Date()
        var request = URLRequest(url: Updater.feed)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 20
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let feed = try? JSON.parse(data),
              let release = MacRelease.newest(in: feed, newerThan: Updater.current) else { return }
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
            NSWorkspace.shared.open(release.page)
            return
        }
        phase = .downloading(release, progress: 0)
        Task {
            do {
                staged = try await fetch(release)
                phase = .ready(release)
            } catch {
                phase = .failed(release)
            }
        }
    }

    /// Download, signature, and a copy of the app out of the image, next to this one.
    private func fetch(_ release: MacRelease) async throws -> URL {
        let image = try await Download.file(release.image) { [weak self] fraction in
            Task { @MainActor in
                guard let self, case .downloading = self.phase else { return }
                // In steps, so the bar is not redrawn for every packet.
                self.phase = .downloading(release, progress: (fraction * 100).rounded() / 100)
            }
        }
        let signature: Data
        do {
            (signature, _) = try await URLSession.shared.data(from: release.signature)
        } catch {
            // The image is no use without it; it is not left behind in the temporary folder.
            try? FileManager.default.removeItem(at: image)
            throw error
        }
        let expectedID = Bundle.main.bundleIdentifier
        let place = Bundle.main.bundleURL
        return try await Task.detached {
            defer { try? FileManager.default.removeItem(at: image) }
            let data = try Data(contentsOf: image, options: .mappedIfSafe)
            guard UpdateSignature.isValid(String(decoding: signature, as: UTF8.self), for: data) else { throw UpdateError.signature }
            // Beside the app, on the same disk, so it can be swapped in at once.
            let folder = try FileManager.default.url(for: .itemReplacementDirectory, in: .userDomainMask, appropriateFor: place, create: true)
            let mount = folder.appendingPathComponent("image", isDirectory: true)
            try Updater.run("/usr/bin/hdiutil", ["attach", "-nobrowse", "-readonly", "-noautoopen", "-mountpoint", mount.path, image.path])
            defer { try? Updater.run("/usr/bin/hdiutil", ["detach", mount.path, "-force"]) }
            guard let app = try FileManager.default.contentsOfDirectory(at: mount, includingPropertiesForKeys: nil).first(where: { $0.pathExtension == "app" }),
                  let info = Bundle(url: app)?.infoDictionary,
                  info["CFBundleIdentifier"] as? String == expectedID,
                  info["CFBundleShortVersionString"] as? String == release.version else { throw UpdateError.contents }
            let copy = folder.appendingPathComponent(place.lastPathComponent)
            try Updater.run("/usr/bin/ditto", [app.path, copy.path])
            return copy
        }.value
    }

    /// Puts the new app where this one is and opens it once this one has quit.
    func relaunch() {
        guard case .ready(let release) = phase, let staged else { return }
        let place = Bundle.main.bundleURL
        do {
            _ = try FileManager.default.replaceItemAt(place, withItemAt: staged)
        } catch {
            phase = .failed(release)
            return
        }
        let opener = Process()
        opener.executableURL = URL(fileURLWithPath: "/bin/sh")
        opener.arguments = ["-c", "while kill -0 \(ProcessInfo.processInfo.processIdentifier) 2>/dev/null; do sleep 0.2; done; /usr/bin/open \"$0\"", place.path]
        try? opener.run()
        NSApp.terminate(nil)
    }

    func openPage() {
        if case .failed(let release) = phase { NSWorkspace.shared.open(release.page) }
    }

    nonisolated private static func run(_ tool: String, _ arguments: [String]) throws {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: tool)
        process.arguments = arguments
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else { throw UpdateError.tool(tool) }
    }

    enum UpdateError: Error { case signature, contents, tool(String) }
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
