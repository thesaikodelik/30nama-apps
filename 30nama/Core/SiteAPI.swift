import Foundation

/// 30nama's website talks to an API of its own beside the one the apps use, and some of the site
/// is only there: a person's titles, the weekly schedule, the top lists, the newest titles. Same
/// account system and same `{success, msg, result}` envelope, but its own host and key, the
/// action's parameters in the path, and posts that are flat where the app API's are nested.
///
/// The key is the one the site gives every browser. It is not kept in the repository: a build
/// takes it from `NAMA_SITE_KEY` in `Config/Local.xcconfig`, through the Info.plist. Without it,
/// or if 30nama changes it, these parts of the apps stay hidden and the rest carries on:
/// everything else is on the app API.
struct SiteConfig {
    var host = "https://interface.30nama.com/api/v1"
    var key: String? = (Bundle.main.object(forInfoDictionaryKey: "NamaSiteKey") as? String)
        .flatMap { $0.isEmpty || $0.hasPrefix("$(") ? nil : $0 }
    var platform = "Website"
}

enum SiteQuery {
    /// A person's titles, newest first: what the site's own "explore?cast=nm…" asks. Only the site
    /// API acts on `cast`, and only with the IMDb id; the app API ignores it.
    static func filmography(imdb: String, page: Int) -> (path: String, form: [String: String]) {
        let fields = (try? JSONSerialization.data(withJSONObject: ["cast": imdb], options: [.sortedKeys]))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        return ("advanced_search/page/\(page)/orderby/year/order/desc", ["parameters": fields])
    }
}

extension Post {
    /// A post as the site API lists it, put in the app API's shape so it is read by the one parser.
    init?(site j: JSON) {
        guard j["id"].int != nil else { return nil }
        func images(_ v: JSON) -> JSON {
            var webp: [String: JSON] = [:], jpg: [String: JSON] = [:]
            for size in ["small", "medium", "large", "big"] {
                webp[size] = v[size + "_webp"]
                jpg[size] = v[size]
            }
            return .object(["webp": .object(webp), "jpg": .object(jpg)])
        }
        let title = j["title"].text ?? ""
        // The site has no year of its own on a list entry: it is the one at the end of the title.
        let year = title.range(of: #"(?<=\s)\d{4}$"#, options: .regularExpression).map { JSON.string(String(title[$0])) } ?? .null
        let imdb = j["imdb_id"].text.map { JSON.string("https://www.imdb.com/title/\($0)/") } ?? .null
        let shape: JSON = .object([
            "id": j["id"],
            "options": .object([
                "title_type": j["title_type"],
                "is_series": j["is_series"],
                "stream": j["stream"],
                "coming_soon": j["coming_soon"],
                "free_stream": j["free_stream"],
                "persian_subtitle": j["persian_subtitle"],
            ]),
            "title": .object(["english": j["title"], "local": j["persian_title"]]),
            "plot": .object(["english": j["english_plot"], "local": j["persian_plot"]]),
            "info": .object(["year": year, "budget": j["budget"]]),
            "genre": j["genre"],
            "image": .object([
                "poster": images(j["image"]["poster"]),
                "cover": .object(["webp": j["image"]["cover_webp"], "jpg": j["image"]["cover"]]),
            ]),
            "score": .object([
                "imdb": .object(["score": j["imdb_score"], "votes": j["imdb_votes"], "link": imdb, "top_250": j["imdb_250"]]),
                "30nama": .object(["score": j["30nama_score"]]),
                "myanimelist": .object(["score": j["myanimelist_score"]]),
                "mydramalist": .object(["score": j["mydramalist_score"]]),
            ]),
        ])
        self.init(shape)
    }
}

extension PostPage {
    /// A page of the site API's lists: `{page, pages, posts}`, with no total.
    init(site j: JSON) {
        posts = j["posts"].array.compactMap(Post.init(site:))
        page = j["page"].int ?? 1
        pages = j["pages"].int ?? 1
        total = posts.count
    }
}
