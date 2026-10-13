import Foundation

struct Cue: Equatable {
    var start: Double
    var end: Double
    var text: String
}

/// WebVTT (and SRT, which differs only in details this parser already tolerates) subtitle track.
struct SubtitleTrack: Equatable {
    private(set) var cues: [Cue]

    init(cues: [Cue] = []) {
        self.cues = cues.sorted { $0.start < $1.start }
    }

    init(vtt text: String) {
        let normalized = text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .replacingOccurrences(of: "\u{FEFF}", with: "")
        var cues: [Cue] = []
        for block in normalized.components(separatedBy: "\n\n") {
            let lines = block.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
            guard let timing = lines.firstIndex(where: { $0.contains("-->") }) else { continue }
            let parts = lines[timing].components(separatedBy: "-->")
            guard parts.count == 2,
                  let start = SubtitleTrack.timestamp(parts[0]),
                  let end = SubtitleTrack.timestamp(parts[1].trimmingCharacters(in: .whitespaces).components(separatedBy: .whitespaces)[0])
            else { continue }
            let body = SubtitleTrack.clean(lines[(timing + 1)...].joined(separator: "\n"))
            if !body.isEmpty { cues.append(Cue(start: start, end: end, text: body)) }
        }
        self.init(cues: SubtitleTrack.directed(cues))
    }

    /// Fixes the direction each line is laid out in, for a track in a right-to-left script.
    ///
    /// Persian subtitles come in two kinds. Properly written ones end a sentence with its full
    /// stop. Most of the files in circulation, though, were prepared for players that lay every
    /// line out left to right: there the full stop is typed first, so that it lands at the left
    /// end, where a right-to-left sentence finishes. Each kind reads wrongly when drawn the
    /// other's way (the full stop jumps to the start of the line), so this works out which kind
    /// the file is and wraps every line in the direction it was written for.
    static func directed(_ cues: [Cue]) -> [Cue] {
        func rightToLeft(_ line: Substring) -> Bool {
            line.unicodeScalars.contains { (0x0590...0x08FF).contains($0.value) || (0xFB1D...0xFDFF).contains($0.value) || (0xFE70...0xFEFC).contains($0.value) }
        }
        let lines = cues.flatMap { $0.text.split(separator: "\n") }.filter(rightToLeft)
        guard !lines.isEmpty else { return cues }
        // Only marks that take their direction from the line. The Arabic question mark belongs
        // to the script, stays with its sentence either way, and so says nothing here.
        let closers: Set<Character> = [".", "!", "?", "…", ":"]
        var first = 0, last = 0
        for line in lines {
            let text = line.trimmingCharacters(in: .whitespaces)
            if let c = text.first, closers.contains(c) { first += 1 }
            if let c = text.last, closers.contains(c) { last += 1 }
        }
        // An isolate holds its direction whatever the direction of the view that draws it.
        let open = first > last ? "\u{2066}" : "\u{2067}", close = "\u{2069}"
        return cues.map { cue in
            // A line with nothing right-to-left in it needs no help and is left as it is.
            let wrapped = cue.text.split(separator: "\n", omittingEmptySubsequences: false).map { rightToLeft($0) ? open + $0 + close : String($0) }
            return Cue(start: cue.start, end: cue.end, text: wrapped.joined(separator: "\n"))
        }
    }

    /// "MM:SS.mmm" or "HH:MM:SS.mmm"; SRT uses a comma before the milliseconds.
    static func timestamp(_ raw: String) -> Double? {
        let s = raw.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        let fields = s.split(separator: ":")
        guard (2...3).contains(fields.count) else { return nil }
        var total = 0.0
        for f in fields {
            guard let v = Double(f), v >= 0 else { return nil }
            total = total * 60 + v
        }
        return total
    }

    /// Strips markup (`<i>`, `<c.yellow>`), ASS position overrides (`{\an8}`) and decodes the few entities that occur in practice.
    static func clean(_ raw: String) -> String {
        var s = raw.replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
        s = s.replacingOccurrences(of: #"\{\\an?\d\}"#, with: "", options: .regularExpression)
        for (entity, char) in [("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&nbsp;", " "), ("&lrm;", ""), ("&rlm;", "")] {
            s = s.replacingOccurrences(of: entity, with: char)
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Text to show at `t`, or an empty string.
    func text(at t: Double) -> String {
        guard !cues.isEmpty else { return "" }
        // Last cue starting at or before t.
        var lo = 0, hi = cues.count - 1, idx = -1
        while lo <= hi {
            let mid = (lo + hi) / 2
            if cues[mid].start <= t { idx = mid; lo = mid + 1 } else { hi = mid - 1 }
        }
        // Cues can overlap, so look a few back for one that has not ended yet.
        var j = idx
        while j >= 0 && j > idx - 4 {
            if cues[j].end > t { return cues[j].text }
            j -= 1
        }
        return ""
    }
}
