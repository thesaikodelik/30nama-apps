import Foundation

/// A title's page on the 30nama website, for sharing (a user on issue #2). The site's own links
/// read `/movie/4705/X-Men-2000` and `/series/…`: the number is the app's own post id (checked
/// against the app API on 30 October 2026), and the rest the English name's words joined by
/// hyphens, then the year. As the Windows and Android apps make it.
enum SiteLink {
    static let host = "https://30nama.com"

    static func url(for post: Post) -> URL {
        var words = Self.words(post.titleEnglish ?? post.title(.en))
        // 30nama's English titles sometimes end in the year already.
        if let year = post.year, !words.hasSuffix("-\(year)"), words != String(year) {
            words = words.isEmpty ? String(year) : "\(words)-\(year)"
        }
        let kind = post.isSeries ? "series" : "movie"
        return URL(string: words.isEmpty ? "\(host)/\(kind)/\(post.id)" : "\(host)/\(kind)/\(post.id)/\(words)")!
    }

    /// Letters and digits only, each run of anything else one hyphen.
    private static func words(_ title: String) -> String {
        var text = ""
        for c in title.unicodeScalars {
            if c.isASCII, CharacterSet.alphanumerics.contains(c) { text.unicodeScalars.append(c) }
            else if !text.isEmpty, text.last != "-" { text.append("-") }
        }
        return text.trimmingCharacters(in: CharacterSet(charactersIn: "-"))
    }
}
