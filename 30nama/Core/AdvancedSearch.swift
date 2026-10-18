import Foundation

/// What 30nama's advanced search can be narrowed by, as `advanced_search_parameters` lists it.
/// Only the fields the server was seen to act on are kept: it also lists quality, cast, creator
/// and release group, and ignores them.
struct SearchChoices {
    typealias Choice = FilterOptions.Choice

    var genres: [Choice]
    var types: [Choice]
    var countries: [Choice]
    var languages: [Choice]
    var networks: [Choice]
    /// Lowest IMDb score to accept.
    var scores: [Choice]
    var ages: [Choice]
    /// The years there are titles for, oldest and newest.
    var years: ClosedRange<Int>

    init(_ j: JSON) {
        // Entries are {name, value}, or for some fields a bare value that is its own name.
        func choices(_ key: String) -> [Choice] {
            j[key].array.compactMap { entry in
                if entry.object == nil {
                    return entry.text.map { Choice(name: $0, value: $0) }
                }
                guard let name = entry["name"].text, let value = entry["value"].string, !value.isEmpty else { return nil }
                return Choice(name: name, value: value)
            }
        }
        genres = choices("genre")
        // Anime films are refused outright; anime as a whole stands for them.
        types = choices("type").filter { $0.value != "movie_anime" }
        countries = choices("country")
        languages = choices("language")
        networks = choices("channel")
        // "Under 5.0" is sent as a negative number; only the floors are offered.
        scores = choices("imdb_rate").filter { (Double($0.value) ?? 0) > 0 }
        ages = choices("age")
        let newest = j["max_year"].int ?? Calendar(identifier: .gregorian).component(.year, from: Date())
        let oldest = min(newest, j["min_year"].int ?? 1920)
        years = oldest...newest
    }
}

/// An advanced search: the fields chosen, and the order the titles come in.
struct AdvancedQuery: Equatable {
    static let sorts = ["relevant", "update", "year", "imdb-rate", "favorite"]

    var genres: [String] = []
    var type: String?
    var country: String?
    var language: String?
    var network: String?
    var score: String?
    var age: String?
    var director: String?
    var fromYear: Int?
    var toYear: Int?
    var x265 = false
    var threeD = false
    var streamOnly = true
    var sort = "relevant"

    /// How many fields narrow the search, for the count on the filters button.
    var count: Int {
        let set: [Any?] = [type, country, language, network, score, age, director, fromYear, toYear]
        return set.compactMap { $0 }.count + genres.count + (x265 ? 1 : 0) + (threeD ? 1 : 0)
    }

    /// The `parameters` value `advanced_search` takes: a JSON object of the chosen fields.
    func parameters() -> String {
        var fields: [String: Any] = [:]
        if !genres.isEmpty { fields["genre"] = genres }
        // A list, as Filter sends it: a single value is ignored.
        if let type { fields["type"] = [type] }
        if let country { fields["country"] = country }
        if let language { fields["language"] = language }
        if let network { fields["channel"] = network }
        if let score, let floor = Double(score) { fields["imdb_rate"] = floor }
        if let age { fields["age"] = age }
        if let name = director?.trimmingCharacters(in: .whitespaces), !name.isEmpty { fields["director"] = name }
        // The years either way round: someone writing 2010 to 2000 means the same decade.
        var (from, to) = (fromYear ?? 0, toYear ?? 0)
        if fromYear != nil, toYear != nil, from > to { (from, to) = (to, from) }
        if from > 0 { fields["min_year"] = String(from) }
        if to > 0 { fields["max_year"] = String(to) }
        if x265 { fields["x265"] = "true" }
        if threeD { fields["3D"] = "true" }
        if streamOnly { fields["stream"] = "true" }
        let data = try? JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys, .withoutEscapingSlashes])
        return data.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
    }

    func request(page: Int) -> [String: String] {
        ["parameters": parameters(), "orderby": sort, "order": "DESC", "page": String(page)]
    }
}
