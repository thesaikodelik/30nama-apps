#if DEBUG
import Foundation

/// Development aid: takes the app straight to a screen, so it can be looked at without clicking
/// the way there. Not compiled into release builds.
///
/// - `NAMA_SECTION=movies` opens that section (the names of `MacSection`).
/// - `NAMA_OPEN=first` opens the first featured title, `NAMA_OPEN=continue` the title last watched,
///   and `NAMA_OPEN=<post id>` one from the home feed.
/// - `NAMA_PLAY=1` also starts it playing. With `NAMA_DEMO=1` that needs no subscription.
/// - `NAMA_MUTE=1` starts the player silent, and `NAMA_CONTROLS=1` keeps its controls on screen.
/// - `NAMA_PROBE="action?key=value&key=value;another"` with `NAMA_PROBE_OUT=<file>` calls those API
///   actions with the signed-in session, writes the shape of each answer (values cut short) to
///   the file and quits without showing a window. For finding out what an action returns.
enum DebugLaunch {
    private static let environment = ProcessInfo.processInfo.environment

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
}
#endif
