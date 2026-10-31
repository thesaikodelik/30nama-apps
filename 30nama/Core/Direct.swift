import Foundation

/// The "Max" quality, as the Tizen, Android and Windows apps have it: the download file itself (MKV
/// or MP4, often 4K and HDR) played straight from 30nama for the item playing, instead of
/// downloading it first. mpv plays every sound these files carry (DTS and TrueHD too), so none is
/// passed over: the best by its lines, then its size. As Direct.cs of the Windows app.
enum Direct {
    /// The file for a film, or for `season` and `episode` of a series; nil when there is no fit.
    static func pick(_ options: DownloadOptions, isSeries: Bool, season: Int?, episode: String?) -> DownloadFile? {
        let wanted = Episode.number(episode)
        let files = isSeries
            ? options.packs.filter { $0.season == (season ?? 1) }.flatMap(\.files).filter { Episode.number($0.episode) == wanted }
            : options.files
        return files.max { (lines($0), megabytes($0.size)) < (lines($1), megabytes($1.size)) }
    }

    private static func words(_ file: DownloadFile) -> String {
        ([file.quality, file.encoder ?? "", file.url.lastPathComponent] + file.tags).joined(separator: " ").lowercased()
    }

    /// The picture's lines, from what the file says: 2160 for 4K.
    static func lines(_ file: DownloadFile) -> Int {
        let w = words(file)
        if w.contains("2160") || w.contains("4k") || w.contains("uhd") { return 2160 }
        return [1440, 1080, 720, 576, 480].first { w.contains(String($0)) } ?? 0
    }

    /// "781 MB", "2.4 GB" as megabytes; 0 when not said.
    static func megabytes(_ size: String?) -> Double {
        guard let size, let match = size.firstMatch(of: #/([0-9.]+)\s*([GMKgmk])[Bb]/#), let n = Double(match.1) else { return 0 }
        switch match.2.uppercased() {
        case "G": return n * 1024
        case "K": return n / 1024
        default: return n
        }
    }

    /// A short name for the quality menu: "2160p · HDR · x265".
    static func label(_ file: DownloadFile) -> String {
        let w = words(file)
        var parts: [String] = []
        let height = lines(file)
        if height > 0 { parts.append("\(height)p") }
        if w.contains("dolby vision") || w.contains("dovi") || w.firstMatch(of: #/\bdv\b/#) != nil {
            parts.append("Dolby Vision")
        } else if w.contains("hdr") {
            parts.append("HDR")
        }
        if w.contains("x265") || w.contains("hevc") || w.contains("h265") { parts.append("x265") }
        return parts.isEmpty ? "Max" : parts.joined(separator: " · ")
    }

    /// The file as a quality the player can load: one address for every mix, as the file carries its own sound.
    static func asQuality(_ file: DownloadFile) -> Quality {
        Quality(label: label(file), url: file.url, height: lines(file), isAuto: false)
    }
}
