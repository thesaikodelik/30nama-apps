#if DEBUG
import Foundation

/// Development aid: takes the app straight to a screen, so it can be looked at without clicking
/// the way there. Not compiled into release builds.
///
/// - `NAMA_SECTION=movies` opens that section (the names of `MacSection`).
/// - `NAMA_OPEN=first` opens the first featured title, `NAMA_OPEN=continue` the title last watched,
///   and `NAMA_OPEN=<post id>` any title by its number.
/// - `NAMA_PLAY=1` also starts it playing. With `NAMA_DEMO=1` that needs no subscription.
/// - `NAMA_MPV_LOG=<file>` has mpv write its full log there.
/// - `NAMA_GUEST=1` opens as a guest, leaving the saved session as it is.
/// - `NAMA_MUTE=1` starts the player silent, and `NAMA_CONTROLS=1` keeps its controls on screen.
/// - `NAMA_UPDATE_FEED=<url>` reads releases from there instead of GitHub, and `NAMA_VERSION=1.1.0`
///   makes the app take itself for that version, to walk through an update before one is published.
/// - `NAMA_PROBE="action?key=value&key=value;another"` with `NAMA_PROBE_OUT=<file>` calls those API
///   actions with the signed-in session, writes the shape of each answer (values cut short) to
///   the file and quits without showing a window. For finding out what an action returns.
enum DebugLaunch {
    private static let environment = ProcessInfo.processInfo.environment

    /// `NAMA_FETCH=<url>` with `NAMA_FETCH_OUT=<folder>`: fetches that one file with the download
    /// engine into the folder, writes how it went (speed every second, the end) to fetch.log
    /// there, and ends the process. `NAMA_FETCH_LIMIT=<MB/s>` sets the speed limit.
    static func fetchIfAsked() {
        guard let address = environment["NAMA_FETCH"].flatMap(URL.init(string:)),
              let out = environment["NAMA_FETCH_OUT"].map({ URL(fileURLWithPath: $0, isDirectory: true) }) else { return }
        Transfer.limit.bytesPerSecond = (environment["NAMA_FETCH_LIMIT"].flatMap(Double.init) ?? 0) * 1_048_576
        let log = out.appendingPathComponent("fetch.log")
        try? FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
        FileManager.default.createFile(atPath: log.path, contents: nil)
        let handle = try? FileHandle(forWritingTo: log)
        let started = Date()
        let lock = NSLock()
        var last: (time: Date, bytes: Int64) = (started, 0)
        func write(_ line: String) {
            lock.withLock {
                _ = try? handle?.seekToEnd()
                try? handle?.write(contentsOf: Data((String(format: "%7.1fs ", Date().timeIntervalSince(started)) + line + "\n").utf8))
            }
        }
        let transfer = Transfer(id: "fetch", address: address, folder: out, progress: { received, expected in
            let now = Date()
            let (since, bytes) = lock.withLock { last }
            guard now.timeIntervalSince(since) >= 1 else { return }
            lock.withLock { last = (now, received) }
            let speed = Double(received - bytes) / now.timeIntervalSince(since) / 1_048_576
            write(String(format: "%.1f / %.1f MB  %.1f MB/s", Double(received) / 1_048_576, Double(expected) / 1_048_576, speed))
        }, finished: { outcome in
            switch outcome {
            case .done(let file):
                let size = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size] as? Int64) ?? -1
                let seconds = Date().timeIntervalSince(started)
                write(String(format: "done: %lld bytes in %.1f s, %.1f MB/s on average", size, seconds, Double(size) / seconds / 1_048_576))
            case .failed(let why):
                write("failed: \(why)")
            }
            exit(0)
        })
        write("fetching \(address.host ?? "?")")
        transfer.note = { write($0) }
        transfer.start()
        if let stopAfter = environment["NAMA_FETCH_STOP"].flatMap(Double.init) {
            // Stops part way, as quitting the app would, to try going on from the record.
            DispatchQueue.main.asyncAfter(deadline: .now() + stopAfter) {
                transfer.stop()
                write("stopped")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1) { exit(0) }
            }
        }
    }

    /// Runs the probe, if one was asked for, and ends the process when it is done.
    static func probeIfAsked() {
        guard let spec = environment["NAMA_PROBE"], let out = environment["NAMA_PROBE_OUT"] else { return }
        Task {
            let api = APIClient()
            var text = ""
            do {
                _ = try await api.bootstrap(cachedHost: UserDefaults.standard.string(forKey: "host"))
                await api.setToken(SessionStore.load()?.token)
                for call in spec.split(separator: ";") {
                    let parts = call.split(separator: "?", maxSplits: 1)
                    var params: [String: String] = [:]
                    for pair in (parts.count > 1 ? parts[1] : "").split(separator: "&") {
                        let sides = pair.split(separator: "=", maxSplits: 1)
                        if sides.count == 2 { params[String(sides[0])] = String(sides[1]) }
                    }
                    let answer = try await api.call(String(parts[0]), params)
                    text += "\(call) -> success=\(answer.success) message=\(answer.message ?? "-")\n\(DebugShape.describe(answer.result))\n\n"
                    // `NAMA_PROBE_KEYS=type,genre` also writes those parts of the answer in full.
                    for key in (environment["NAMA_PROBE_KEYS"] ?? "").split(separator: ",") {
                        let part = answer.result[String(key)]
                        if let data = part.data(), let whole = String(data: data, encoding: .utf8) { text += "\(key): \(whole)\n\n" }
                    }
                }
            } catch {
                text += "error: \(error)\n"
            }
            try? text.write(toFile: out, atomically: true, encoding: .utf8)
            exit(0)
        }
    }

    static var section: String? { environment["NAMA_SECTION"] }
    static var open: String? { environment["NAMA_OPEN"] }
    static var play: Bool { environment["NAMA_PLAY"] == "1" }
    static var mute: Bool { environment["NAMA_MUTE"] == "1" }
    static var holdControls: Bool { environment["NAMA_CONTROLS"] == "1" }
    static var updateFeed: URL? { environment["NAMA_UPDATE_FEED"].flatMap(URL.init(string:)) }
    static var version: String? { environment["NAMA_VERSION"] }
    /// `NAMA_FINDSUBS=1`: look on SubSource as if 30nama had no Persian subtitle for what plays.
    static var findSubtitles: Bool { environment["NAMA_FINDSUBS"] == "1" }
}
#endif
