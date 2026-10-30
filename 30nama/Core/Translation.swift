import Foundation
import CryptoKit

/// Subtitles translated by a language model (Google's Gemini, with the viewer's own key), for an
/// item that has no Persian subtitle anywhere. The film starts at once with the original lines:
/// batches are translated from where the viewer is, onwards, then the part before, and each
/// replaces its lines as it arrives. As Translation.kt of the Android app and Translation.cs of
/// the Windows app.
enum Translation {
    /// Lines per request: enough context for names and tone, small enough to come back in seconds.
    static let batch = 80

    /// The batch that holds the line on screen at `t` seconds, or the next one.
    static func batch(at t: Double, in cues: [Cue]) -> Int {
        let i = cues.firstIndex { $0.end > t } ?? max(cues.count - 1, 0)
        return i / batch
    }

    /// Batches in the order to translate them: from `start` to the end, then the ones before it.
    static func order(count: Int, start: Int) -> [Int] {
        let s = min(max(start, 0), max(count - 1, 0))
        return Array(s..<max(count, s)) + Array(0..<s)
    }

    static func batch(_ n: Int, of cues: [Cue]) -> [Cue] { Array(cues.dropFirst(n * batch).prefix(batch)) }

    static func batchCount(_ cues: [Cue]) -> Int { (cues.count + batch - 1) / batch }

    /// A line break inside a cue, written so the model keeps it.
    private static let lineBreak = " | "

    /// The generateContent body: the lines as a JSON array, and an answer asked for as a JSON array
    /// of the same length, so each translation lands on its own line and a missing one shows.
    static func request(_ lines: [String], target: Lang, title: String) -> Data {
        let language = target == .ar ? "Arabic" : "Persian (Farsi), as spoken in Iran"
        let instruction = "You translate film and TV subtitles into \(language). The title is \"\(title)\". "
            + "Translate each string of the JSON array the user sends, in order, into natural, spoken \(language) that fits on screen. "
            + "Keep names as they are said. \"\(lineBreak)\" marks a line break; keep it. "
            + "Answer with a JSON array of exactly \(lines.count) strings, one per input string, and nothing else."
        let sent = JSON.array(lines.map { .string($0.replacingOccurrences(of: "\n", with: lineBreak)) }).data().map { String(decoding: $0, as: UTF8.self) } ?? "[]"
        let body: [String: Any] = [
            "systemInstruction": ["parts": [["text": instruction]]],
            "contents": [["role": "user", "parts": [["text": sent]]]],
            "generationConfig": [
                "temperature": 0.3,
                "responseMimeType": "application/json",
                "responseSchema": ["type": "ARRAY", "items": ["type": "STRING"]],
            ],
        ]
        return (try? JSONSerialization.data(withJSONObject: body)) ?? Data()
    }

    /// The translated lines from a generateContent answer, one per line sent; a line that did not
    /// come back is nil and keeps its original. Nil for an answer that is not a list at all.
    static func read(_ answer: JSON, count: Int) -> [String?]? {
        var text = answer["candidates"][0]["content"]["parts"].array.map { $0["text"].string ?? "" }.joined()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("```json") { text = String(text.dropFirst(7)) } else if text.hasPrefix("```") { text = String(text.dropFirst(3)) }
        if text.hasSuffix("```") { text = String(text.dropLast(3)) }
        guard let parsed = try? JSON.parse(Data(text.utf8)), case .array(let list) = parsed, !list.isEmpty else { return nil }
        return (0..<count).map { i in
            guard i < list.count, let line = list[i].text else { return nil }
            return line.replacingOccurrences(of: lineBreak.trimmingCharacters(in: .whitespaces), with: "\n")
                .split(separator: "\n", omittingEmptySubsequences: false)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .joined(separator: "\n")
        }
    }

    /// `cues` with the translated `lines` of batch `n` put in.
    static func apply(_ cues: [Cue], batch n: Int, lines: [String?]) -> [Cue] {
        var output = cues
        for (i, line) in lines.enumerated() {
            let at = n * batch + i
            if let line, at < output.count { output[at].text = line }
        }
        return output
    }

    /// The target language: Arabic for an Arabic interface, Persian otherwise.
    static func target(_ lang: Lang) -> Lang { lang == .ar ? .ar : .fa }
}

/// Google's Gemini API, with the key the viewer made in their own Google AI Studio account; the app
/// carries none. Its free tier costs nothing, within a daily number of requests, and a film is about
/// twenty. The "-latest" names follow Google's newest model of each kind, so a retired model does
/// not break the feature. Translated batches are kept on disk, so a film is translated once. As
/// Gemini.kt of the Android app and Gemini of the Windows app.
struct Gemini {
    enum Outcome: Equatable {
        case lines([String?])
        /// The key was refused.
        case rejected
        /// The free requests for today are spent, or the service could not be reached.
        case unavailable
    }

    /// The light model first: quick, and the most free requests a day.
    static let models = ["gemini-flash-lite-latest", "gemini-flash-latest"]
    static let modelsAddress = "https://generativelanguage.googleapis.com/v1beta/models"

    let transport: Transport
    let folder: URL
    var wait: @Sendable (TimeInterval) async -> Void = { try? await Task.sleep(nanoseconds: UInt64($0 * 1_000_000_000)) }

    /// One batch, from the disk if it was translated before. `source` names the subtitle file.
    func translate(key: String, source: String, target: Lang, title: String, batch n: Int, cues: [Cue]) async -> Outcome {
        let file = folder.appendingPathComponent("\(Gemini.digest("\(source)|\(target.rawValue)"))-\(n).json")
        if let data = try? Data(contentsOf: file), case .array(let saved)? = try? JSON.parse(data), saved.count == cues.count {
            return .lines(saved.map(\.string))
        }
        let body = Translation.request(cues.map(\.text), target: target, title: title)
        for model in Gemini.models {
            // A busy minute is waited out twice; a refusal or a spent day is not.
            for attempt in 0..<3 {
                var request = URLRequest(url: URL(string: "\(Gemini.modelsAddress)/\(model):generateContent")!)
                request.httpMethod = "POST"
                request.httpBody = body
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
                request.timeoutInterval = 90
                var code = 0
                var answer: JSON?
                if let (data, response) = try? await transport(request) {
                    code = response.statusCode
                    answer = try? JSON.parse(data)
                }
                let said = answer?.data().map { String(decoding: $0, as: UTF8.self) } ?? ""
                if (200..<300).contains(code) {
                    guard let answer, let lines = Translation.read(answer, count: cues.count) else { continue }
                    try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                    try? JSON.array(lines.map { $0.map(JSON.string) ?? .null }).data()?.write(to: file, options: .atomic)
                    return .lines(lines)
                }
                if (code == 400 && said.contains("API_KEY_INVALID")) || code == 401 || code == 403 { return .rejected }
                // This model is not offered to this key: the next one.
                if code == 404 { break }
                if code == 429, attempt < 2, !said.contains("PerDay") {
                    await wait(30)
                } else if code == 429 || code == 0 {
                    return .unavailable
                } else if attempt < 2 {
                    await wait(5)
                }
            }
        }
        return .unavailable
    }

    private static func digest(_ s: String) -> String {
        SHA256.hash(data: Data(s.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
    }
}
