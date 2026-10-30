import Foundation

/// What TMDB adds to a title page, with a key the user made on TMDB themselves: the title's own
/// logo, its trailer, the people who made it, and reviews. The app keeps no key of its own.
enum TMDB {
    static let api = URL(string: "https://api.themoviedb.org/3")!
    static let images = "https://image.tmdb.org/t/p/"

    /// A v4 read access token is sent as a bearer token; a v3 key goes in the address.
    static func request(_ path: String, query: [URLQueryItem] = [], key: String) -> URLRequest {
        var parts = URLComponents(url: api.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        var items = query
        let bearer = key.hasPrefix("eyJ")
        if !bearer { items.append(.init(name: "api_key", value: key)) }
        parts.queryItems = items.isEmpty ? nil : items
        var request = URLRequest(url: parts.url!)
        if bearer { request.setValue("Bearer " + key, forHTTPHeaderField: "Authorization") }
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 20
        return request
    }

    /// Finding the title: by its IMDb id when 30nama gives one, else by name and year.
    static func findRequest(imdbID: String, key: String) -> URLRequest {
        request("find/\(imdbID)", query: [.init(name: "external_source", value: "imdb_id")], key: key)
    }

    static func searchRequest(title: String, year: Int?, isSeries: Bool, key: String) -> URLRequest {
        var query = [URLQueryItem(name: "query", value: title)]
        if let year { query.append(.init(name: isSeries ? "first_air_date_year" : "year", value: String(year))) }
        return request("search/\(isSeries ? "tv" : "movie")", query: query, key: key)
    }

    static func id(inFind answer: JSON, isSeries: Bool) -> Int? {
        answer[isSeries ? "tv_results" : "movie_results"].array.first?["id"].int
    }

    static func id(inSearch answer: JSON) -> Int? {
        answer["results"].array.first?["id"].int
    }

    /// The result that is this title, not merely the first one: a name at least 0.8 alike, then
    /// the nearest year. Nil when none is alike enough, so a wrong title's logo and plot are not
    /// shown (the Tizen app's `bestCand`, as the Android and Windows apps have it).
    static func best(inSearch answer: JSON, title: String, year: Int?) -> Int? {
        let wanted = TitleMatch.stripYear(title)
        let scored = answer["results"].array.compactMap { r -> (id: Int, score: Double)? in
            guard let id = r["id"].int else { return nil }
            let names = [r["title"].text, r["name"].text, r["original_title"].text, r["original_name"].text].compactMap { $0 }
            let alike = names.map { TitleMatch.similarity(wanted, $0) }.max() ?? 0
            guard alike >= 0.8 else { return nil }
            let theirs = (r["release_date"].text ?? r["first_air_date"].text).flatMap { Int($0.prefix(4)) }
            let off = year.flatMap { y in theirs.map { abs(y - $0) } } ?? 0
            return (id, alike + (off == 0 ? 0.2 : off == 1 ? 0.1 : -0.05 * Double(off)))
        }
        return scored.max { $0.score < $1.score }?.id
    }

    /// Everything a title page shows, in one request.
    static func detailsRequest(id: Int, isSeries: Bool, key: String) -> URLRequest {
        let credits = isSeries ? "aggregate_credits" : "credits"
        return request("\(isSeries ? "tv" : "movie")/\(id)", query: [
            .init(name: "append_to_response", value: "images,videos,\(credits),reviews"),
            .init(name: "include_image_language", value: "fa,en,null"),
        ], key: key)
    }

    // MARK: Without a key: the public site

    static let site = URL(string: "https://www.themoviedb.org")!

    /// The site sends this to the title's own page.
    static func publicPage(imdbID: String) -> URL {
        URL(string: "https://www.themoviedb.org/redirect?external_source=imdb_id&external_id=\(imdbID)")!
    }

    /// The page that lists a title's logos in one language, given the title's own page.
    static func logosPage(title page: URL, language: String) -> URL? {
        guard page.path.hasPrefix("/movie/") || page.path.hasPrefix("/tv/") else { return nil }
        return URL(string: page.absoluteString.components(separatedBy: "?")[0] + "/images/logos?image_language=" + language)
    }

    /// The page that lists a title's trailers, given the title's own page.
    static func trailersPage(title page: URL) -> URL? {
        guard page.path.hasPrefix("/movie/") || page.path.hasPrefix("/tv/") else { return nil }
        return URL(string: page.absoluteString.components(separatedBy: "?")[0] + "/videos?active_nav_item=Trailers&language=en-US")
    }

    /// The YouTube id of the first trailer the page lists.
    static func trailer(inPage html: String) -> String? {
        guard let range = html.range(of: #"data-site="YouTube" data-id="[A-Za-z0-9_-]{11}""#, options: .regularExpression) else { return nil }
        return String(html[range].dropLast().suffix(11))
    }

    /// The first logo the page lists, which the site puts best voted first.
    static func logo(inPage html: String) -> URL? {
        guard let range = html.range(of: #"/t/p/[a-z0-9_]+/[A-Za-z0-9]+\.(png|svg)"#, options: .regularExpression) else { return nil }
        let file = html[range].split(separator: "/").last.map(String.init) ?? ""
        return URL(string: images + "w500/" + file.replacingOccurrences(of: ".svg", with: ".png"))
    }

    struct Person: Hashable {
        var name: String
        var job: String
        var photo: URL?
    }

    struct Review: Hashable {
        var author: String
        var text: String
        /// Out of ten, when the reviewer gave one.
        var rating: Double?
        var link: URL?
    }

    struct Info: Equatable {
        /// The title written as its own artwork, on a clear ground.
        var logo: URL?
        /// The YouTube id of the official trailer.
        var trailer: String?
        var crew: [Person] = []
        var reviews: [Review] = []

        var trailerPage: URL? { trailer.flatMap { URL(string: "https://www.youtube.com/watch?v=\($0)") } }
    }

    /// The jobs worth naming on a title page, in the order they are shown.
    static let jobs = ["Director", "Creator", "Screenplay", "Writer", "Novel", "Original Music Composer", "Director of Photography"]

    static func info(in details: JSON, lang: Lang) -> Info {
        var info = Info()

        // A logo in the interface's language if there is one, else English, else one with no text.
        let logos = details["images"]["logos"].array
        let order = (lang == .fa ? ["fa", "en"] : ["en"]) + [""]
        for code in order {
            let fitting = logos.filter { ($0["iso_639_1"].text ?? "") == code }
            if let best = fitting.max(by: { ($0["vote_average"].double ?? 0) < ($1["vote_average"].double ?? 0) }),
               let path = best["file_path"].text {
                // PNG at a size that stays sharp on a large screen; TMDB keeps SVGs as PNGs too.
                info.logo = URL(string: images + "w500" + path.replacingOccurrences(of: ".svg", with: ".png"))
                break
            }
        }

        let videos = details["videos"]["results"].array.filter { $0["site"].text == "YouTube" && $0["type"].text == "Trailer" }
        let trailer = videos.first { $0["official"].bool } ?? videos.first
        info.trailer = trailer?["key"].text

        // Films list one entry per job; series list each person once, with all their jobs.
        var people: [Person] = []
        for entry in details["credits"]["crew"].array + details["aggregate_credits"]["crew"].array {
            guard let name = entry["name"].text else { continue }
            let jobs = entry["job"].text.map { [$0] } ?? entry["jobs"].array.compactMap { $0["job"].text }
            let photo = entry["profile_path"].text.flatMap { URL(string: images + "w185" + $0) }
            for job in jobs where TMDB.jobs.contains(job) {
                people.append(Person(name: name, job: job, photo: photo))
            }
        }
        for creator in details["created_by"].array {
            guard let name = creator["name"].text else { continue }
            people.append(Person(name: name, job: "Creator", photo: creator["profile_path"].text.flatMap { URL(string: images + "w185" + $0) }))
        }
        var seen = Set<String>()
        info.crew = people
            .sorted { (jobs.firstIndex(of: $0.job) ?? 99) < (jobs.firstIndex(of: $1.job) ?? 99) }
            .filter { seen.insert($0.name).inserted }
            .prefix(8).map { $0 }

        info.reviews = details["reviews"]["results"].array.prefix(6).compactMap { r in
            guard let author = r["author"].text, let text = r["content"].text, !text.isEmpty else { return nil }
            return Review(author: author, text: text, rating: r["author_details"]["rating"].double, link: r["url"].url)
        }
        return info
    }
}
