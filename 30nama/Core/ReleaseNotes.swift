import Foundation

/// What each Mac version brought, as the app shows it under "What's new": the version sections of
/// the release's own text on GitHub, newest first. The release page goes on with the link to the
/// full changelog, the first-install steps and the checksum; none of that is shown in the app.
enum ReleaseNotes {
    enum Block: Equatable {
        /// "۲.۰.۶ (۸ آبان ۱۴۰۵)"
        case version(String)
        /// A bold line on its own: the part of the app the lines below are about.
        case group(String)
        case item(String)
        case text(String)
    }

    static func blocks(in body: String) -> [Block] {
        var blocks: [Block] = []
        for raw in body.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.hasPrefix("### ") {
                blocks.append(.version(String(line.dropFirst(4))))
                continue
            }
            // Nothing before the first version: that is how to install, for whoever downloads it.
            guard !blocks.isEmpty, !line.isEmpty else { continue }
            // The versions end where the page links to the full changelog, or goes back to HTML and code.
            if line.contains("](") || line.hasPrefix("<") || line.hasPrefix("```") { break }
            if line.hasPrefix("- ") {
                blocks.append(.item(String(line.dropFirst(2))))
            } else if line.count > 4, line.hasPrefix("**"), line.hasSuffix("**"), !line.dropFirst(2).dropLast(2).contains("**") {
                blocks.append(.group(String(line.dropFirst(2).dropLast(2))))
            } else {
                blocks.append(.text(line))
            }
        }
        return blocks
    }

    /// The notes in a `releases` answer (`MacRelease.feed`): this version's own release, or else
    /// the newest Mac one, whose text carries every version before it (older releases are taken
    /// down). Nil when there is none.
    static func blocks(inFeed j: JSON, version: String) -> [Block]? {
        func number(_ r: JSON) -> String? {
            guard !r["draft"].bool, let tag = r["tag_name"].text, tag.hasPrefix(MacRelease.tagPrefix) else { return nil }
            return String(tag.dropFirst(MacRelease.tagPrefix.count))
        }
        let mac = j.array.filter { number($0) != nil }
        guard let release = mac.first(where: { number($0) == version })
                ?? mac.max(by: { MacRelease.isNewer(number($1)!, than: number($0)!) }),
              let body = release["body"].text else { return nil }
        let blocks = blocks(in: body)
        return blocks.isEmpty ? nil : blocks
    }
}
