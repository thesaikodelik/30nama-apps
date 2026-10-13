import Foundation

/// One title the user has rated, with just enough of the title kept to show it again without the network.
struct Rating: Codable, Equatable, Identifiable {
    var postID: Int
    /// Half-star steps from 0.5 to 5.
    var stars: Double
    var date: Date
    var titleEnglish: String?
    var titleLocal: String?
    var year: Int?
    var isSeries: Bool
    var isAnime: Bool
    var genres: [Genre]
    var posterLarge: URL?
    var posterBig: URL?
    var blurhash: String?

    struct Genre: Codable, Equatable {
        var name: String
        var slug: String
    }

    var id: Int { postID }

    init(post: Post, stars: Double, date: Date = Date()) {
        postID = post.id
        self.stars = Rating.snap(stars)
        self.date = date
        titleEnglish = post.titleEnglish
        titleLocal = post.titleLocal
        year = post.year
        isSeries = post.isSeries
        isAnime = post.kind == .anime
        genres = post.genres.map { Genre(name: $0.name, slug: $0.slug) }
        posterLarge = post.poster.large
        posterBig = post.poster.big
        blurhash = post.poster.blurhash
    }

    /// Nearest half star within 0.5...5.
    static func snap(_ stars: Double) -> Double {
        min(5, max(0.5, (stars * 2).rounded() / 2))
    }

    /// The title as a `Post`, so it can be drawn by the same cards and opened like any other.
    var post: Post? {
        var poster: [String: Any] = [:]
        if let blurhash { poster["preview"] = blurhash }
        var webp: [String: Any] = [:]
        if let posterLarge { webp["large"] = posterLarge.absoluteString }
        if let posterBig { webp["big"] = posterBig.absoluteString }
        poster["webp"] = webp
        var title: [String: Any] = [:]
        if let titleEnglish { title["english"] = titleEnglish }
        if let titleLocal { title["local"] = titleLocal }
        var info: [String: Any] = [:]
        if let year { info["year"] = year }
        var options: [String: Any] = ["is_series": isSeries]
        if isAnime { options["title_type"] = "anime" }
        return Post(JSON([
            "id": postID, "title": title, "info": info, "options": options,
            "genre": genres.map { ["name": $0.name, "slug": $0.slug] },
            "image": ["poster": poster],
        ] as [String: Any]))
    }
}

/// Everything the user has rated, and what that says about their taste.
struct RatingBook: Equatable {
    private(set) var ratings: [Int: Rating] = [:]
    /// Titles whose rating was taken away, and when. Kept so that another device, which still has
    /// the rating, learns that it is gone instead of bringing it back.
    private(set) var removed: [Int: Date] = [:]

    init(_ ratings: [Rating] = []) {
        for r in ratings { self.ratings[r.postID] = r }
    }

    private struct Stored: Codable {
        var ratings: [Rating]
        var removed: [String: Date]
    }

    /// Reads a stored book. Books saved before removals were recorded are a bare list of ratings.
    init?(data: Data) {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        if let stored = try? decoder.decode(Stored.self, from: data) {
            self.init(stored.ratings)
            for (id, date) in stored.removed { if let id = Int(id) { removed[id] = date } }
        } else if let list = try? decoder.decode([Rating].self, from: data) {
            self.init(list)
        } else {
            return nil
        }
    }

    func encoded() -> Data {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .secondsSince1970
        encoder.outputFormatting = .sortedKeys
        let stored = Stored(ratings: newestFirst, removed: Dictionary(uniqueKeysWithValues: removed.map { (String($0.key), $0.value) }))
        return (try? encoder.encode(stored)) ?? Data()
    }

    var count: Int { ratings.count }
    var isEmpty: Bool { ratings.isEmpty }
    var newestFirst: [Rating] { ratings.values.sorted { ($0.date, $0.postID) > ($1.date, $1.postID) } }
    var average: Double? { isEmpty ? nil : ratings.values.map(\.stars).reduce(0, +) / Double(count) }

    func stars(for postID: Int) -> Double? { ratings[postID]?.stars }

    /// Sets the rating for a title, or removes it when `stars` is nil.
    mutating func rate(_ post: Post, stars: Double?, date: Date = Date()) {
        if let stars {
            ratings[post.id] = Rating(post: post, stars: stars, date: date)
            removed[post.id] = nil
        } else if ratings.removeValue(forKey: post.id) != nil {
            removed[post.id] = date
        }
    }

    /// This book and another device's, brought together: for every title, whatever happened last
    /// stands, be it a rating or its removal. Either book can be the receiver; the result is the same.
    func merged(with other: RatingBook) -> RatingBook {
        var out = RatingBook()
        for id in Set(ratings.keys).union(other.ratings.keys).union(removed.keys).union(other.removed.keys) {
            let rating = [ratings[id], other.ratings[id]].compactMap { $0 }.max { ($0.date, $0.stars) < ($1.date, $1.stars) }
            let removal = [removed[id], other.removed[id]].compactMap { $0 }.max()
            if let rating, rating.date >= removal ?? .distantPast {
                out.ratings[id] = rating
            } else if let removal {
                out.removed[id] = removal
            }
        }
        return out
    }

    // MARK: Taste

    /// A rating above this counts for a genre, below it against.
    static let neutral = 3.0

    /// How much the user likes each genre: the average of (stars - neutral) over the titles rated
    /// in it, shrunk toward zero when there are only a few, so one five-star western does not
    /// outweigh ten four-star dramas.
    func taste() -> [String: Double] {
        var sum: [String: Double] = [:], n: [String: Double] = [:]
        for r in ratings.values {
            for g in r.genres {
                sum[g.slug, default: 0] += r.stars - RatingBook.neutral
                n[g.slug, default: 0] += 1
            }
        }
        return sum.reduce(into: [:]) { out, entry in
            let count = n[entry.key] ?? 1
            out[entry.key] = entry.value / (count + 2)
        }
    }

    /// Genres the user rates highest, best first.
    func favoriteGenres(limit: Int = 3) -> [String] {
        taste().filter { $0.value > 0 }.sorted { ($0.value, $0.key) > ($1.value, $1.key) }.prefix(limit).map(\.key)
    }

    /// The genres the user rates highest, by name, with how strongly each is liked next to the
    /// strongest (0...1).
    func favoriteGenreNames(limit: Int = 4) -> [(name: String, strength: Double)] {
        let taste = taste()
        var names: [String: String] = [:]
        for r in newestFirst.reversed() { for g in r.genres { names[g.slug] = g.name } }
        let top = favoriteGenres(limit: limit)
        guard let best = top.first.flatMap({ taste[$0] }), best > 0 else { return [] }
        return top.map { (names[$0] ?? $0, (taste[$0] ?? 0) / best) }
    }

    /// How many titles got one star, two, and so on up to five. Half stars count with the star above.
    func spread() -> [Int] {
        var counts = [Int](repeating: 0, count: 5)
        for r in ratings.values { counts[min(4, max(0, Int(r.stars.rounded(.up)) - 1))] += 1 }
        return counts
    }

    /// How well a title fits the user's taste. Positive means "probably yes".
    func affinity(for post: Post, taste: [String: Double]) -> Double {
        let known = post.genres.compactMap { taste[$0.slug] }
        guard !known.isEmpty else { return 0 }
        let fit = known.reduce(0, +) / Double(known.count)
        // Between two titles that fit equally, prefer the better-regarded one.
        let acclaim = ((post.imdbScore ?? 6.5) - 6.5) * 0.08
        return fit + acclaim
    }

    /// Titles from `candidates` the user has not rated, best fit first, leaving out poor fits.
    func recommend(from candidates: [Post], limit: Int = 20) -> [Post] {
        let taste = taste()
        guard !taste.isEmpty else { return [] }
        var seen = Set(ratings.keys)
        return candidates
            .filter { seen.insert($0.id).inserted && !$0.comingSoon }
            .map { ($0, affinity(for: $0, taste: taste)) }
            .filter { $0.1 > 0 }
            .sorted { ($0.1, $0.0.id) > ($1.1, $1.0.id) }
            .prefix(limit)
            .map(\.0)
    }
}

extension RatingBook {
    /// The ratings as a CSV that Letterboxd's importer reads: title, year and stars, newest first.
    /// The app cannot send ratings to 30nama, so this is how they leave it.
    func csv() -> String {
        func field(_ text: String) -> String {
            guard text.contains(where: { ",\"\n".contains($0) }) else { return text }
            return "\"" + text.replacingOccurrences(of: "\"", with: "\"\"") + "\""
        }
        var lines = ["Title,Year,Rating"]
        for rating in newestFirst {
            guard let title = rating.post?.cleanEnglishTitle ?? rating.titleEnglish ?? rating.titleLocal else { continue }
            lines.append([field(title), rating.year.map(String.init) ?? "", String(rating.stars)].joined(separator: ","))
        }
        return lines.joined(separator: "\n") + "\n"
    }
}
