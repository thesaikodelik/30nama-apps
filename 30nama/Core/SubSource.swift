import Foundation

/// Finding a Persian subtitle on SubSource for a title 30nama has none for.
///
/// Two ways in. With the user's own SubSource key, its documented API. Without one, the requests
/// the site's own pages make, which need no key but may change when the site does. The app keeps
/// no key of its own: anything shipped inside it could be taken out of it.
enum SubSource {
    static let site = URL(string: "https://subsource.net")!
    static let api = URL(string: "https://api.subsource.net/api/v1")!
    static let persian = "farsi_persian"

    /// What is being looked for.
    struct Query: Equatable {
        var title: String
        var year: Int?
        var imdbID: String?
        var isSeries: Bool
        var season: Int?
    }

    /// A subtitle that may fit: how to fetch it, and the releases it was timed for.
    struct Candidate: Equatable, Hashable, Identifiable {
        enum Source: Hashable { case page(URL), api(Int) }
        var source: Source
        var releases: [String]
        var id: String {
            switch source {
            case .page(let url): return url.absoluteString
            case .api(let id): return "api:\(id)"
            }
        }
        /// The release it is named after, for a menu.
        var name: String { releases.first ?? "SubSource" }
    }

    // MARK: Without a key: the site's own requests

    /// The body of the site's search request.
    static func searchBody(_ query: Query) -> Data {
        let body: [String: Any] = ["query": query.title, "includeSeasons": query.isSeries, "limit": 15]
        return (try? JSONSerialization.data(withJSONObject: body)) ?? Data()
    }

    /// The listing of Persian subtitles for the result that is this title: the same kind (film or
    /// series), the same year when both say one, and the name closest to it.
    static func listing(in answer: JSON, for query: Query) -> URL? {
        let results = answer["results"].array.filter { r in
            let series = (r["type"].text ?? "").contains("series")
            return series == query.isSeries
        }
        let wanted = simple(query.title)
        let scored = results.compactMap { r -> (JSON, Int)? in
            var score = 0
            let name = simple(r["title"].text ?? "")
            if name == wanted { score += 10 } else if name.contains(wanted) || wanted.contains(name) { score += 3 } else { return nil }
            if let year = query.year, let theirs = r["releaseYear"].int {
                if theirs == year { score += 10 } else if abs(theirs - year) > 1 { return nil }
            }
            return (r, score)
        }
        guard let best = scored.max(by: { $0.1 < $1.1 })?.0 else { return nil }
        let base: String?
        if query.isSeries {
            let season = query.season ?? 1
            base = best["seasons"].array.first { $0["season"].int == season }?["link"].text
        } else {
            base = best["link"].text
        }
        guard let base else { return nil }
        return URL(string: current(base) + "/" + persian, relativeTo: site)?.absoluteURL
    }

    /// A listing address in the form the site uses now: "season-2", where it once had "season=2".
    static func current(_ path: String) -> String {
        path.replacingOccurrences(of: "season=", with: "season-")
    }

    /// The same, for a whole address, such as one 30nama links to.
    static func current(_ url: URL) -> URL {
        URL(string: current(url.absoluteString)) ?? url
    }

    /// Simplified for comparing names: lower case, letters and digits only.
    static func simple(_ name: String) -> String {
        String(name.lowercased().filter { $0.isLetter || $0.isNumber })
    }

    // MARK: With the user's key: the documented API

    static func searchRequest(_ query: Query, key: String) -> URLRequest {
        var parts = URLComponents(url: api.appendingPathComponent("movies/search"), resolvingAgainstBaseURL: false)!
        if let imdb = query.imdbID {
            parts.queryItems = [.init(name: "searchType", value: "imdb"), .init(name: "imdb", value: imdb)]
        } else {
            parts.queryItems = [.init(name: "searchType", value: "text"), .init(name: "q", value: query.title),
                                .init(name: "type", value: query.isSeries ? "series" : "movie")]
            if let year = query.year { parts.queryItems?.append(.init(name: "year", value: String(year))) }
        }
        if query.isSeries, let season = query.season { parts.queryItems?.append(.init(name: "season", value: String(season))) }
        return keyed(parts.url!, key)
    }

    /// The id of the title the search found: for a series, the entry for the season wanted.
    static func movieID(in answer: JSON, for query: Query) -> Int? {
        let found = answer["data"].array
        if query.isSeries, let season = query.season, let entry = found.first(where: { $0["season"].int == season }) {
            return entry["movieId"].int
        }
        return found.first?["movieId"].int
    }

    static func subtitlesRequest(movieID: Int, key: String) -> URLRequest {
        var parts = URLComponents(url: api.appendingPathComponent("subtitles"), resolvingAgainstBaseURL: false)!
        parts.queryItems = [.init(name: "movieId", value: String(movieID)), .init(name: "language", value: persian), .init(name: "sort", value: "rating")]
        return keyed(parts.url!, key)
    }

    static func candidates(inAPI answer: JSON) -> [Candidate] {
        answer["data"].array.compactMap { s in
            guard let id = s["subtitleId"].int else { return nil }
            return Candidate(source: .api(id), releases: s["releaseInfo"].array.compactMap(\.text))
        }
    }

    static func downloadRequest(id: Int, key: String) -> URLRequest {
        keyed(api.appendingPathComponent("subtitles/\(id)/download"), key)
    }

    private static func keyed(_ url: URL, _ key: String) -> URLRequest {
        var request = URLRequest(url: url)
        request.setValue(key, forHTTPHeaderField: "X-API-Key")
        request.timeoutInterval = 20
        return request
    }

    // MARK: Choosing

    /// The candidates in the order they are likely to be in time with what is playing: for an
    /// episode, only those for it or for its whole season; then those that share the copy's marks.
    static func ranked(_ candidates: [Candidate], release: String, season: Int?, episode: Int?) -> [Candidate] {
        let entries = candidates.map { SubtitleSite.Entry(path: $0.id, releases: $0.releases) }
        var left = entries, order: [SubtitleSite.Entry] = []
        while let next = SubtitleSite.best(for: release, season: season, episode: episode, among: left) {
            // `best` passes over subtitles for other episodes only when something better is there.
            if let season, let episode, !fits(next.releases, season: season, episode: episode) { break }
            order.append(next)
            left.removeAll { $0 == next }
        }
        return order.compactMap { entry in candidates.first { $0.id == entry.path } }
    }

    private static func fits(_ releases: [String], season: Int, episode: Int) -> Bool {
        let tag = String(format: "s%02de%02d", season, episode)
        let anyEpisode = releases.contains { SubtitleSite.words($0).contains { $0.range(of: #"^s\d{1,2}e\d{1,3}$"#, options: .regularExpression) != nil } }
        return !anyEpisode || releases.contains { SubtitleSite.words($0).contains(tag) }
    }

    /// The subtitle text in what was downloaded: an archive, or the file itself.
    static func text(from data: Data, season: Int?, episode: Int?) -> String? {
        guard data.starts(with: [0x50, 0x4B]) else { return SubtitleSite.text(data).flatMap { $0.contains("-->") ? $0 : nil } }
        let files = Zip.files(in: data)
        guard let name = SubtitleSite.pick(from: files.map(\.name), season: season, episode: episode),
              let file = files.first(where: { $0.name == name }) else { return nil }
        return SubtitleSite.text(file.data).flatMap { $0.contains("-->") ? $0 : nil }
    }
}
