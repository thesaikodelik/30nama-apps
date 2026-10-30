import Foundation

// TMDB's trending lists, turned into titles 30nama carries. With the user's own TMDB key the
// documented API is asked; without one, the week's trending list is read from TMDB's public page,
// and the rows that need the API stay empty. Ported from the Tizen client's tmdb.js.

/// A title as TMDB lists it, cut down to what matching needs.
struct TMDBItem: Hashable {
    enum Kind: String { case movie, tv }

    var id: Int
    var kind: Kind
    var title: String
    var originalTitle: String?
    var year: Int?
    var hasPoster: Bool
    var popularity: Double
    var voteCount: Int

    var key: String { kind.rawValue + String(id) }

    /// The fields of a list entry worth caching.
    static let keptKeys = [
        "id", "media_type", "title", "name", "original_title", "original_name",
        "release_date", "first_air_date", "poster_path", "popularity", "vote_count",
    ]

    /// `defaultKind` is for lists that hold one kind and so do not say which.
    init?(_ j: JSON, defaultKind: Kind? = nil) {
        guard let id = j["id"].int, j["media_type"].text != "person" else { return nil }
        self.id = id
        switch j["media_type"].text {
        case "tv": kind = .tv
        case "movie": kind = .movie
        default: kind = defaultKind ?? (j["first_air_date"].isNull ? .movie : .tv)
        }
        title = j["title"].text ?? j["name"].text ?? ""
        originalTitle = j["original_title"].text ?? j["original_name"].text
        year = (j["release_date"].text ?? j["first_air_date"].text).flatMap { Int($0.prefix(4)) }
        hasPoster = j["poster_path"].text != nil
        popularity = j["popularity"].double ?? 0
        voteCount = j["vote_count"].int ?? 0
    }

    static func list(_ results: JSON, defaultKind: Kind? = nil) -> [TMDBItem] {
        results.array.compactMap { TMDBItem($0, defaultKind: defaultKind) }
    }

    /// TMDB's public "trending this week" panel, written as the list entries the API would give,
    /// so the rest of the way is the same.
    static let publicTrending = URL(string: "https://www.themoviedb.org/remote/panel?panel=trending_scroller&group=this-week&language=en-US")!

    static func publicList(_ html: String) -> JSON {
        guard let pattern = try? NSRegularExpression(pattern: #"href="/(movie|tv)/(\d+)[^"]*"><h2[^>]*><span>([^<]+)</span></h2></a></div><span class="release_date[^"]*">([^<]*)</span>"#) else { return .array([]) }
        var seen = Set<String>()
        let whole = NSRange(html.startIndex..., in: html)
        let entries: [JSON] = pattern.matches(in: html, range: whole).compactMap { m in
            guard let kind = Range(m.range(at: 1), in: html).map({ String(html[$0]) }),
                  let id = Range(m.range(at: 2), in: html).flatMap({ Int(html[$0]) }),
                  let title = Range(m.range(at: 3), in: html).map({ String(html[$0]) }),
                  seen.insert(kind + String(id)).inserted else { return nil }
            let date = Range(m.range(at: 4), in: html).map { String(html[$0]) } ?? ""
            var entry: [String: JSON] = [
                "id": .number(Double(id)), "media_type": .string(kind),
                kind == "tv" ? "name" : "title": .string(unescape(title)),
                "poster_path": .string("/public"),
            ]
            if let year = date.range(of: #"\d{4}"#, options: .regularExpression).map({ String(date[$0]) }) {
                entry[kind == "tv" ? "first_air_date" : "release_date"] = .string(year + "-01-01")
            }
            return .object(entry)
        }
        return .array(entries)
    }

    private static func unescape(_ s: String) -> String {
        s.replacingOccurrences(of: "&amp;", with: "&").replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&quot;", with: "\"").replacingOccurrences(of: "&#x27;", with: "'")
    }
}

/// The rows TMDB adds to Home.
enum TrendingRow: String, CaseIterable {
    /// Trending this week, movies and series together.
    case trend
    /// New and returning series.
    case fresh
    /// Movies in cinemas and trending today.
    case pop

    struct Source {
        var path: String
        var params: [String: String]
        var kind: TMDBItem.Kind?
    }

    var sectionKey: String { "tm_" + rawValue }

    /// Most titles a row shows.
    var cap: Int { self == .trend ? 16 : 14 }

    /// A row with fewer titles than this is not worth showing.
    static let minimum = 5

    func sources(now: Date = Date()) -> [Source] {
        switch self {
        case .trend:
            return [Source(path: "/trending/all/week", params: ["language": "en-US"], kind: nil)]
        case .fresh:
            let f = DateFormatter()
            f.calendar = Calendar(identifier: .gregorian)
            f.locale = Locale(identifier: "en_US_POSIX")
            f.timeZone = TimeZone(identifier: "UTC")
            f.dateFormat = "yyyy-MM-dd"
            let since = f.string(from: now.addingTimeInterval(-30 * 86400))
            return (1...3).map { page in
                Source(path: "/discover/tv", params: [
                    "language": "en-US", "air_date.gte": since, "sort_by": "popularity.desc", "vote_count.gte": "200",
                    // No talk shows, news, reality or soaps.
                    "without_genres": "10767,10763,10764,10766", "page": String(page),
                ], kind: .tv)
            }
        case .pop:
            return [
                Source(path: "/movie/now_playing", params: ["language": "en-US", "region": "US"], kind: .movie),
                Source(path: "/trending/movie/day", params: ["language": "en-US"], kind: .movie),
            ]
        }
    }

    /// The titles to look for on 30nama, in the order the row should show them.
    func candidates(from lists: [[TMDBItem]]) -> [TMDBItem] {
        var seen = Set<String>()
        var out = lists.joined().filter { !$0.title.isEmpty && $0.hasPoster && seen.insert($0.key).inserted }
        if self == .fresh {
            // Best-known series first, obscure ones dropped.
            func weight(_ item: TMDBItem) -> Double { item.popularity + Double(item.voteCount).squareRoot() / 5 }
            out = out.filter { $0.voteCount >= 200 }.enumerated()
                .sorted { (weight($0.element), -$0.offset) > (weight($1.element), -$1.offset) }
                .map(\.element)
        }
        return out
    }
}

/// Decides whether a 30nama title and a TMDB title are the same one.
enum TitleMatch {
    /// Lowercase, without accents or punctuation: "Amélie & Co." -> "amelie and co".
    static func normalize(_ s: String) -> String {
        var s = s.lowercased().decomposedStringWithCanonicalMapping
        s.unicodeScalars.removeAll { (0x0300...0x036F).contains($0.value) }
        return s.replacingOccurrences(of: "&", with: " and ")
            .replacingOccurrences(of: "[^a-z0-9\u{0600}-\u{06FF}]+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }

    /// 30nama appends the release year to its titles ("Animals 2026").
    static func stripYear(_ s: String) -> String {
        let stripped = s.replacingOccurrences(of: #"\s*[\(\[]?\b(19|20)\d\d[\)\]]?\s*$"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
        return stripped.isEmpty ? s : stripped
    }

    /// 0...1: how many words two titles share. A title that only adds a subtitle to the other still counts as close.
    static func similarity(_ a: String, _ b: String) -> Double {
        let a = normalize(a), b = normalize(b)
        if a.isEmpty || b.isEmpty { return 0 }
        if a == b { return 1 }
        let wordsA = a.split(separator: " "), wordsB = b.split(separator: " ")
        let shared = wordsA.filter(Set(wordsB).contains).count
        let dice = 2 * Double(shared) / Double(wordsA.count + wordsB.count)
        return a.hasPrefix(b + " ") || b.hasPrefix(a + " ") ? max(dice, 0.82) : dice
    }

    /// What to search 30nama for: the title, then the original one when it differs and is in Latin
    /// script, then the title with its year, the way 30nama itself writes it. That last one finds
    /// titles with a common name ("Animals") that the plain search buries under their namesakes.
    static func queries(for item: TMDBItem) -> [String] {
        var out = [item.title]
        if let original = item.originalTitle, normalize(original) != normalize(item.title),
           original.range(of: "[a-zA-Z]", options: .regularExpression) != nil {
            out.append(original)
        }
        if let year = item.year { out.append("\(item.title) \(year)") }
        return out
    }

    /// The search result that is this TMDB title: same kind, a close enough name, the same year
    /// give or take one. Titles that can be streamed win over ones that cannot; upcoming ones are skipped.
    static func bestPost(in posts: [Post], for item: TMDBItem) -> Post? {
        var best: (post: Post, score: Double)?
        for post in posts where post.isSeries == (item.kind == .tv) && !post.comingSoon {
            let raw = post.titleEnglish ?? ""
            let name = [item.title, item.originalTitle ?? ""].filter { !$0.isEmpty }
                .map { max(similarity(stripYear(raw), $0), similarity(raw, $0)) }.max() ?? 0
            guard name >= 0.8 else { continue }
            var score = name
            if let wanted = item.year, let year = post.year {
                switch abs(wanted - year) {
                case 0: score += 0.25
                case 1: score += 0.1
                default: continue
                }
            }
            if post.hasStream { score += 0.1 }
            if score > (best?.score ?? 0) { best = (post, score) }
        }
        return best?.post
    }
}

/// Builds the trending rows: asks TMDB what is popular, then finds each title on 30nama.
///
/// Both halves are cached on disk, because finding one title costs a 30nama search and 30nama is
/// rate limited: TMDB's lists for six hours, a title that was found for a week, one that was not for a day.
actor Trending {
    typealias Search = @Sendable (_ query: String) async throws -> JSON

    private struct Entry {
        var at: Date
        var value: JSON
    }

    /// The user's own TMDB key; nil for the public page.
    private let key: String?
    private let transport: Transport
    private let search: Search
    private let cacheFile: URL?
    private let now: @Sendable () -> Date

    private var cache: [String: Entry] = [:]
    private var cacheLoaded = false
    private var running: [TrendingRow: Task<[Post], Never>] = [:]

    /// The fields of a 30nama post that lists and the billboard draw from.
    private static let keptPostKeys = ["id", "post_type", "options", "info", "title", "plot", "genre", "image", "score"]

    /// - Parameter key: the user's TMDB key; nil or blank reads the public page instead.
    /// - Parameter search: runs a 30nama title search and returns its result.
    init(key: String?, cacheFile: URL? = nil, transport: Transport? = nil, now: @escaping @Sendable () -> Date = { Date() }, search: @escaping Search) {
        let key = key?.trimmingCharacters(in: .whitespacesAndNewlines)
        self.key = key?.isEmpty == false ? key : nil
        self.cacheFile = cacheFile
        self.transport = transport ?? APIClient.urlSessionTransport()
        self.now = now
        self.search = search
    }

    /// The titles of a row, in TMDB's order. Empty when too few of them are on 30nama, or TMDB could not be reached.
    func row(_ row: TrendingRow) async -> [Post] {
        if let task = running[row] { return await task.value }
        let task = Task { await build(row) }
        running[row] = task
        let posts = await task.value
        running[row] = nil
        return posts
    }

    private func build(_ row: TrendingRow) async -> [Post] {
        loadCache()
        var lists: [[TMDBItem]] = []
        for source in row.sources(now: now()) { lists.append(await list(source)) }
        let candidates = row.candidates(from: lists)

        // Up to four lookups at a time, and no new ones once the row is full.
        let found = await withTaskGroup(of: (Int, Post?).self) { group -> [(Int, Post)] in
            var found: [(Int, Post)] = [], next = 0, active = 0
            while true {
                while active < 4, next < candidates.count, found.count < row.cap {
                    let index = next
                    group.addTask { (index, await self.resolve(candidates[index])) }
                    next += 1
                    active += 1
                }
                guard let (index, post) = await group.next() else { break }
                active -= 1
                if let post { found.append((index, post)) }
            }
            return found
        }
        saveCache()

        var seen = Set<Int>()
        let posts = found.sorted { $0.0 < $1.0 }.map(\.1).filter { seen.insert($0.id).inserted }.prefix(row.cap)
        return posts.count >= TrendingRow.minimum ? Array(posts) : []
    }

    private func list(_ source: TrendingRow.Source) async -> [TMDBItem] {
        let query = source.params.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: "&")
        let key = "l:\(source.path)?\(query)"
        if let hit = cached(key) { return TMDBItem.list(hit, defaultKind: source.kind) }
        if self.key == nil {
            // Without a key, the lists written for everyone each day on the repository's data branch
            // (Tools/trending), as the Android and Windows apps read them; TMDB's public page only
            // where GitHub does not answer.
            if let shared = try? await fetchShared(), case let list = shared["lists"]["\(source.path)?\(query)"], !list.array.isEmpty {
                cache[key] = Entry(at: now(), value: list)
                return TMDBItem.list(list, defaultKind: source.kind)
            }
            guard source.path == "/trending/all/week", let html = try? await fetchPublic() else { return [] }
            let list = TMDBItem.publicList(html)
            guard !list.array.isEmpty else { return [] }
            cache[key] = Entry(at: now(), value: list)
            return TMDBItem.list(list)
        }
        guard let fresh = try? await fetch(source) else { return [] }
        let slim = JSON.array(fresh["results"].array.map { entry in
            .object((entry.object ?? [:]).filter { TMDBItem.keptKeys.contains($0.key) })
        })
        cache[key] = Entry(at: now(), value: slim)
        return TMDBItem.list(slim, defaultKind: source.kind)
    }

    private func fetch(_ source: TrendingRow.Source) async throws -> JSON {
        guard let key else { throw URLError(.userAuthenticationRequired) }
        let items = source.params.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        let request = TMDB.request(String(source.path.dropFirst()), query: items, key: key)
        let (data, response) = try await transport(request)
        guard response.statusCode == 200 else { throw URLError(.badServerResponse) }
        return try JSON.parse(data)
    }

    /// The trending lists written each day for viewers without a key.
    static let sharedTrending = URL(string: "https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/trending.json")!

    /// Fetched once per build of the rows, not once per list.
    private var shared: Task<JSON?, Never>?

    private func fetchShared() async throws -> JSON? {
        if shared == nil {
            let transport = transport
            shared = Task {
                var request = URLRequest(url: Trending.sharedTrending)
                request.timeoutInterval = 15
                guard let (data, response) = try? await transport(request), response.statusCode == 200 else { return nil }
                return try? JSON.parse(data)
            }
        }
        let value = await shared?.value
        if value == nil { shared = nil }
        return value
    }

    private func fetchPublic() async throws -> String {
        var request = URLRequest(url: TMDBItem.publicTrending)
        request.timeoutInterval = 15
        request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await transport(request)
        guard response.statusCode == 200 else { throw URLError(.badServerResponse) }
        return String(decoding: data, as: UTF8.self)
    }

    /// The 30nama title for a TMDB one, or nil when 30nama does not have it.
    private func resolve(_ item: TMDBItem) async -> Post? {
        let key = "p:" + item.key
        if let hit = cached(key) { return Post(hit) }
        var match = JSON.null
        do {
            for query in TitleMatch.queries(for: item) {
                let entries = Post.rawList(try await search(query)).map { $0["post"].isNull ? $0 : $0["post"] }
                if let post = TitleMatch.bestPost(in: entries.compactMap { Post($0) }, for: item),
                   let raw = entries.first(where: { $0["id"].int == post.id }) {
                    match = .object((raw.object ?? [:]).filter { Trending.keptPostKeys.contains($0.key) })
                    break
                }
            }
        } catch {
            // The search itself failed, which says nothing about the title: ask again next time.
            return nil
        }
        cache[key] = Entry(at: now(), value: match)
        return Post(match)
    }

    // MARK: Cache

    private static func life(of key: String, _ value: JSON) -> TimeInterval {
        if key.hasPrefix("l:") { return 6 * 3600 }
        return value.isNull ? 24 * 3600 : 7 * 24 * 3600
    }

    private func cached(_ key: String) -> JSON? {
        guard let entry = cache[key], now().timeIntervalSince(entry.at) < Trending.life(of: key, entry.value) else { return nil }
        return entry.value
    }

    private func loadCache() {
        guard !cacheLoaded else { return }
        cacheLoaded = true
        guard let cacheFile, let data = try? Data(contentsOf: cacheFile), let stored = try? JSON.parse(data).object else { return }
        for (key, entry) in stored {
            guard let at = entry["t"].double else { continue }
            cache[key] = Entry(at: Date(timeIntervalSince1970: at), value: entry["v"])
        }
    }

    private func saveCache() {
        guard let cacheFile else { return }
        let time = now()
        cache = cache.filter { time.timeIntervalSince($0.value.at) < Trending.life(of: $0.key, $0.value.value) }
        let stored = JSON.object(cache.mapValues { .object(["t": .number($0.at.timeIntervalSince1970), "v": $0.value]) })
        try? stored.data()?.write(to: cacheFile, options: .atomic)
    }
}
