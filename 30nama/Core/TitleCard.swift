import Foundation

/// What the back of a title's poster shows (the poster turns over when clicked): another of its
/// posters, its name in its own language and script, the collection it belongs to and its place
/// there, and the logo of the company that made it. For every viewer from `cards/NN.json` on the
/// `data` branch, which a daily job builds from TMDB's API (Tools/logos/build_cards.py); a viewer
/// with a TMDB key of their own reads TMDB's answer itself, in the same shape. TMDB pictures are
/// kept as file names. As TitleCard.kt of the Android app and TitleCard.cs of the Windows app.
struct TitleCard: Equatable {
    /// A collection: its names (English, and Persian or Arabic where TMDB has them), and the title's place in it.
    struct Part: Equatable {
        var names: [String: String]
        var at: Int
        var of: Int

        func name(_ lang: Lang) -> String? { names[lang.rawValue] ?? names["en"] }
    }

    var poster: String?
    var ownName: String?
    var collection: Part?
    var companyLogo: String?
    var companyName: String?

    /// Nothing to put on the back: the poster does not turn.
    var isEmpty: Bool { poster == nil && ownName == nil && collection == nil && companyLogo == nil }

    static let base = "https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/cards/"
    private static let wanted: Set<String> = ["fa", "ar"]

    /// The file a title is kept in: the last two digits of its IMDb number.
    static func shard(_ imdb: String) -> String {
        let digits = imdb.filter(\.isASCII).filter(\.isNumber)
        let last = digits.count > 2 ? String(digits.suffix(2)) : digits
        return String(repeating: "0", count: max(0, 2 - last.count)) + last
    }

    static func address(_ imdb: String) -> URL { URL(string: "\(base)\(shard(imdb)).json")! }

    /// A TMDB picture by its file name.
    static func picture(_ name: String) -> URL { URL(string: "\(TMDB.images)w500/\(name)")! }

    /// The title's card in one of the daily files; nil where it is not there.
    static func fromShard(_ answer: JSON, imdb: String) -> TitleCard? {
        guard answer["version"].int == 1 else { return nil }
        let c = answer["cards"][imdb]
        guard c.object != nil else { return nil }
        var collection: Part?
        let col = c["c"]
        if let raw = col["names"].object {
            let names = raw.compactMapValues(\.text)
            if !names.isEmpty, let at = col["at"].int, let of = col["of"].int, of >= 2 { collection = Part(names: names, at: at, of: of) }
        }
        let card = TitleCard(poster: c["p"].text, ownName: c["o"].text, collection: collection, companyLogo: c["l"].text, companyName: c["n"].text)
        return card.isEmpty ? nil : card
    }

    /// The same card from TMDB's own answer, for a viewer with a key: `details` asked with
    /// `append_to_response=images`, `collection` with `append_to_response=translations`. As
    /// build_cards.py makes it.
    static func fromTMDB(_ details: JSON, collection: JSON? = nil) -> TitleCard? {
        let main = details["poster_path"].text
        let poster = details["images"]["posters"].array
            .filter { p in p["file_path"].text.map { $0 != main } == true && ["en", ""].contains(p["iso_639_1"].text ?? "") }
            .sorted { a, b in
                let (va, vb) = (a["vote_average"].double ?? 0, b["vote_average"].double ?? 0)
                return va != vb ? va > vb : (a["vote_count"].int ?? 0) > (b["vote_count"].int ?? 0)
            }
            .first?["file_path"].text.map { String($0.drop { $0 == "/" }) }
        let original = details["original_title"].text ?? details["original_name"].text
        let shown = details["title"].text ?? details["name"].text
        let language = details["original_language"].text
        let ownName = original.flatMap { o in
            language != nil && language != "en" && o.lowercased() != shown?.lowercased() ? o : nil
        }
        let company = (details["production_companies"].array + details["networks"].array).first { $0["logo_path"].text != nil }
        let card = TitleCard(
            poster: poster,
            ownName: ownName,
            collection: collection.flatMap { part(of: $0, id: details["id"].int) },
            companyLogo: company?["logo_path"].text.map { String($0.drop { $0 == "/" }).replacingOccurrences(of: ".svg", with: ".png") },
            companyName: company?["name"].text)
        return card.isEmpty ? nil : card
    }

    private static func part(of collection: JSON, id: Int?) -> Part? {
        let parts = collection["parts"].array.filter { $0["id"].int != nil }
            .sorted { a, b in
                let (da, db) = (a["release_date"].text ?? "9999", b["release_date"].text ?? "9999")
                return da != db ? da < db : (a["id"].int ?? 0) < (b["id"].int ?? 0)
            }
            .compactMap { $0["id"].int }
        guard let id, let index = parts.firstIndex(of: id), parts.count >= 2 else { return nil }
        var names: [String: String] = [:]
        if let english = collection["name"].text { names["en"] = english }
        for t in collection["translations"]["translations"].array {
            guard let code = t["iso_639_1"].text, wanted.contains(code) else { continue }
            if let name = t["data"]["name"].text ?? t["data"]["title"].text { names[code] = name }
        }
        return names.isEmpty ? nil : Part(names: names, at: index + 1, of: parts.count)
    }
}
