#if DEBUG
import Foundation

/// Development aid: stream files need a subscription, so this stands in for the `stream` action
/// and lets the player, subtitles and episode list be exercised without one.
/// Enable by launching with the environment variable `NAMA_DEMO=1`. Not compiled into release builds.
enum DemoStream {
    static var isEnabled: Bool { ProcessInfo.processInfo.environment["NAMA_DEMO"] == "1" }

    /// Apple's public HLS test stream.
    private static let video = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

    static func result(for post: Post) -> StreamResult {
        func subtitle(_ lines: [String]) -> String {
            var vtt = "WEBVTT\n\n"
            for (i, line) in lines.enumerated() {
                vtt += String(format: "00:00:%02d.000 --> 00:00:%02d.500\n", i * 4 + 1, i * 4 + 4) + line + "\n\n"
            }
            return "data:text/vtt;base64," + Data(vtt.utf8).base64EncodedString()
        }
        let subtitles: [String: Any] = [
            "fa": subtitle((1...40).map { "این خط شمارهٔ \($0) زیرنویس آزمایشی است\nو خط دوم آن" }),
            "en": subtitle((1...40).map { "Sample subtitle line \($0)" }),
        ]
        func item(season: Int, episode: Int) -> [String: Any] {
            [
                "info": ["file_id": "\(season)\(String(format: "%02d", episode))", "post_id": post.id, "season": season, "episode": episode,
                         "title": ["english": "Sample episode \(episode)"], "duration": 2640],
                "options": ["token": "demo", "intro_start": 8, "intro_end": 40, "end": 90],
                "file": ["m3u8": [["label": "Auto", "default": video], ["label": "1080p", "default": video]], "subtitle": subtitles],
            ]
        }
        let items = post.isSeries
            ? [1, 2].flatMap { s in (1...6).map { item(season: s, episode: $0) } }
            : [["info": ["file_id": "1", "post_id": post.id], "options": ["token": "demo"], "file": ["m3u8": video, "subtitle": subtitles]]]
        return StreamResult(JSON([
            "hls": items,
            "continue_watching": post.isSeries ? ["file_id": "103", "time": 20, "duration": 600] : NSNull(),
        ] as [String: Any]))
    }
}
/// Prints the structure of a response with values cut short: enough to see its shape in the
/// console without dumping stream addresses or tokens.
enum DebugShape {
    static func describe(_ j: JSON, depth: Int = 0) -> String {
        switch j {
        case .null: return "null"
        case .bool(let b): return "\(b)"
        case .number(let n): return "\(n)"
        case .string(let s): return "\"\(s.prefix(28))\(s.count > 28 ? "…" : "")\""
        case .array(let a):
            guard let first = a.first, depth < 6 else { return "[×\(a.count)]" }
            return "[×\(a.count) " + describe(first, depth: depth + 1) + "]"
        case .object(let o):
            guard depth < 6 else { return "{…}" }
            return "{" + o.keys.sorted().map { "\($0): " + describe(o[$0] ?? .null, depth: depth + 1) }.joined(separator: ", ") + "}"
        }
    }
}
#endif
