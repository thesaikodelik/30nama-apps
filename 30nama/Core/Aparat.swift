import Foundation

/// A title's trailer from Aparat, the Iranian video site: open in Iran, where TMDB and YouTube are
/// filtered, searchable without a key, and its videos are plain files the app can play itself.
/// What is there is uploaded by anyone, so a video is taken only when its title says plainly that
/// it is this title's trailer.
enum Aparat {
    struct Video: Equatable, Sendable {
        let uid: String
        let title: String
        let duration: Int
        let visits: Int
    }

    static func searchRequest(title: String) -> URLRequest {
        let query = "تریلر " + bareTitle(title)
        let text = query.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))) ?? query
        return request("https://www.aparat.com/etc/api/videoBySearch/text/\(text)/perpage/20")
    }

    static func videoRequest(uid: String) -> URLRequest {
        request("https://www.aparat.com/etc/api/video/videohash/\(uid)")
    }

    private static func request(_ address: String) -> URLRequest {
        var request = URLRequest(url: URL(string: address)!)
        request.timeoutInterval = 15
        return request
    }

    static func videos(inSearch answer: JSON) -> [Video] {
        answer["videobysearch"].array.compactMap { v in
            guard let uid = v["uid"].text, let title = v["title"].text else { return nil }
            return Video(uid: uid, title: title, duration: v["duration"].int ?? 0, visits: v["visit_cnt"].int ?? 0)
        }
    }

    /// The trailer for a title among what a search found: the most watched of those that name
    /// the title as a whole, call themselves a trailer of a film, a series or an animation, and are
    /// neither a review nor a reaction nor a game's.
    static func trailer(in videos: [Video], title: String, year: Int?) -> Video? {
        let name = words(bareTitle(title))
        guard !name.isEmpty else { return nil }
        // A name this short is in many other titles; it must come with its year.
        let short = name.count < 4
        return videos.filter { video in
            let text = words(video.title)
            guard (30...300).contains(video.duration),
                  " \(text) ".contains(" \(name) "),
                  trailerWords.contains(where: text.contains),
                  kindWords.contains(where: text.contains),
                  !otherWords.contains(where: text.contains) else { return false }
            let years = years(in: text)
            if let year, !years.isEmpty, !years.contains(where: { abs($0 - year) <= 1 }) { return false }
            if short { return year.map { years.contains($0) } ?? false }
            return true
        }
        .max { $0.visits < $1.visits }
    }

    /// The video's file to play: 720p where there is one, else the nearest to it.
    static func file(inVideo answer: JSON) -> URL? {
        let files = answer["video"]["file_link_all"].array
        for profile in ["720p", "1080p", "480p", "360p", "240p", "144p"] {
            if let file = files.first(where: { $0["profile"].text == profile }), let url = file["urls"].array.first?.url { return url }
        }
        return answer["video"]["file_link"].url
    }

    private static let trailerWords = ["تریلر", "تیزر", "trailer", "teaser"]
    private static let kindWords = ["فیلم", "سریال", "انیمیشن", "انیمه", "کارتون", "movie", "film", "series", "anime"]
    private static let otherWords = ["نقد", "بررسی", "واکنش", "ری اکشن", "ریاکشن", "تحلیل", "خلاصه", "مقایسه", "پشت صحنه",
                                     // Not "بازی" alone: "با بازی ..." is "starring".
                                     "تریلر بازی", "بازی ویدیویی", "گیمر", "هوش مصنوعی", "هواداری",
                                     "reaction", "review", "game", "fan made", "fanmade"]

    /// The title without a year at its end, as 30nama's English titles sometimes have.
    private static func bareTitle(_ title: String) -> String {
        title.replacingOccurrences(of: #"\s*\(?(19|20)\d\d\)?\s*$"#, with: "", options: .regularExpression)
    }

    /// Lowercase words, digits in Latin, everything else a single space.
    static func words(_ text: String) -> String {
        let latin = text.lowercased().map { c -> Character in
            if let digit = "۰۱۲۳۴۵۶۷۸۹".firstIndex(of: c) { return Character(String("۰۱۲۳۴۵۶۷۸۹".distance(from: "۰۱۲۳۴۵۶۷۸۹".startIndex, to: digit))) }
            return c.isLetter || c.isNumber ? c : " "
        }
        return String(latin).split(separator: " ").joined(separator: " ")
    }

    private static func years(in text: String) -> [Int] {
        text.split(separator: " ").compactMap { word in
            guard word.count == 4, let year = Int(word), (1900...2100).contains(year) else { return nil }
            return year
        }
    }
}

/// Where a title's trailer is: on YouTube (as TMDB knows it) or on Aparat.
enum TrailerVideo: Hashable, Sendable {
    case youtube(String)
    case aparat(String)
}
