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

    /// A person as the site's biography page has them, with their titles newest first, a page at a
    /// time: by 30nama's own id for the person (`cast[].id` of `single`), not the IMDb one.
    static func person(id: Int, page: Int) -> String {
        page > 1 ? "single/id/\(id)/page/\(page)" : "single/id/\(id)"
    }

    /// The titles most recently added to 30nama, of one kind ("movie", "series", "anime"): what
    /// the site's own "newest" is, by post id. The app API has no such order.
    static func newest(kind: String, genre: String?, streamOnly: Bool, page: Int) -> String {
        "list/type/category/value/\(kind)/page/\(page)/orderby/id/order/desc/streamonly/\(streamOnly ? "yes" : "no")/genre/\(genre ?? "all")"
    }

    /// A member's public page on the site: it has the first and last name the app API leaves out.
    static func profile(username: String) -> String {
        "profile/user/" + (username.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))) ?? username)
    }

    /// The site's news, reviews and 30nama's own announcements, newest first, 24 a page.
    static func articles(_ kind: Article.Kind, page: Int) -> String { "article/type/\(kind.rawValue)/page/\(page)" }

    /// One article whole: the same action as a title's or a person's page.
    static func article(id: Int) -> String { "single/id/\(id)" }

    static func schedule(_ kind: Schedule.Kind) -> String { "schedule/type/\(kind.rawValue)" }

    static func topList(_ list: TopList) -> String { "toplist/list/\(list.rawValue)" }

    /// The site's poster sizes, flat (`medium`, `medium_webp`…), in the app API's nested shape.
    static func posterShape(_ v: JSON) -> JSON {
        var webp: [String: JSON] = [:], jpg: [String: JSON] = [:]
        for size in ["small", "medium", "large", "big"] {
            webp[size] = v[size + "_webp"]
            jpg[size] = v[size]
        }
        return .object(["webp": .object(webp), "jpg": .object(jpg)])
    }
}

/// What the site knows of a person: the facts beside their picture, the biography 30nama wrote,
/// and the first page of their titles. Many have no biography; then only the titles are there.
struct PersonDetails {
    var name: String?
    var fullName: String?
    var persianName: String?
    var profession: String?
    /// As the site writes them, in Persian ("۲۴ مرداد ۱۳۵۱").
    var birthdate: String?
    var deathdate: String?
    var age: String?
    var imdb: URL?
    var picture: Poster?
    /// The biography, a paragraph each, as plain text.
    var biography: [String]
    var titles: PostPage

    init?(site j: JSON) {
        guard j["id"].int != nil else { return nil }
        name = j["name"].text
        fullName = j["full_name"].text
        persianName = j["persian_name"].text
        profession = j["profession"].text
        birthdate = j["birthdate"].text
        deathdate = j["deathdate"].text
        age = j["age"].text
        imdb = j["imdb"].url
        let p = Poster(SiteQuery.posterShape(j["image"]["poster"]))
        picture = p.isEmpty ? nil : p
        biography = SiteText.paragraphs(j["content"].text ?? j["summary"].text ?? "").map(SiteText.tidyPersian)
        titles = PostPage(site: j["posts"])
    }
}

/// The site's texts are HTML written in its editor: paragraphs, bold, entities.
enum SiteText {
    static func latinDigits(_ s: String) -> String {
        String(s.map { c in
            if let v = "۰۱۲۳۴۵۶۷۸۹".firstIndex(of: c) { return Character(String("۰۱۲۳۴۵۶۷۸۹".distance(from: "۰۱۲۳۴۵۶۷۸۹".startIndex, to: v))) }
            if let v = "٠١٢٣٤٥٦٧٨٩".firstIndex(of: c) { return Character(String("٠١٢٣٤٥٦٧٨٩".distance(from: "٠١٢٣٤٥٦٧٨٩".startIndex, to: v))) }
            return c
        })
    }

    /// The site's Persian is typed loosely: Latin digits, a space before every comma, straight
    /// quotes that the right-to-left layout puts on the wrong side of what they quote, and the
    /// half-space left out. Put right for reading, not for meaning.
    static func tidyPersian(_ text: String) -> String {
        var s = text
        // A straight quote is neither left nor right, so in Persian it lands beside the wrong word;
        // Persian quotes have a side.
        s = s.replacingOccurrences(of: #""\s*([^"\n]+?)\s*""#, with: "«$1»", options: .regularExpression)
        // No space before a comma, a semicolon or a full stop; one after a comma.
        s = s.replacingOccurrences(of: #"\s+([،؛:.!؟])"#, with: "$1", options: .regularExpression)
        s = s.replacingOccurrences(of: #"،(?=\S)"#, with: "، ", options: .regularExpression)
        // The half-space after "می" and "نمی", and before the "ی" of "خانه ی".
        s = s.replacingOccurrences(of: #"(^|\s)(ن?می) (?=[آ-ی])"#, with: "$1$2\u{200C}", options: .regularExpression)
        s = s.replacingOccurrences(of: #"ه ی(?=\s|$)"#, with: "ه\u{200C}ی", options: .regularExpression)
        return Fmt.persianDigits(s)
    }

    /// A heading 30nama put in a biography: a short line that is not a sentence.
    static func isHeading(_ paragraph: String) -> Bool {
        paragraph.count < 48 && !paragraph.contains(where: { ".،؛!؟:".contains($0) })
    }

    /// The start of a long text, for showing it folded: whole paragraphs up to about `letters`,
    /// and never ending on a heading.
    static func opening(of paragraphs: [String], letters: Int = 800) -> [String] {
        var out: [String] = [], length = 0
        for p in paragraphs {
            out.append(p)
            length += p.count
            if length > letters, !isHeading(p) { break }
        }
        return out
    }

    /// An article's HTML as what the page is built of: headings, paragraphs and pictures, in
    /// order. A picture that links to a larger one is the larger one. Videos and the rest of the
    /// editor's markup are left out; links are kept as their words.
    static func blocks(_ html: String) -> [ArticleBlock] {
        let picture = "\u{1}", heading = "\u{2}"
        var s = html
        s = s.replacingOccurrences(of: #"<a[^>]*href="([^"]+\.(?:jpe?g|png|webp|gif))"[^>]*>\s*<img[^>]*>\s*</a>"#,
                                   with: "\n\(picture)$1\n", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: #"<img[^>]*\ssrc="([^"]+)"[^>]*>"#, with: "\n\(picture)$1\n", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: #"<video[\s\S]*?</video>|<iframe[\s\S]*?</iframe>"#, with: "\n", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: #"<h[1-6][^>]*>"#, with: "\n\(heading)", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: #"</h[1-6]>"#, with: "\n", options: [.regularExpression, .caseInsensitive])
        return paragraphs(s).compactMap { line in
            if line.hasPrefix(picture) {
                return URL(string: String(line.dropFirst()).trimmingCharacters(in: .whitespaces)).map(ArticleBlock.picture)
            }
            if line.hasPrefix(heading) {
                let text = tidyPersian(String(line.dropFirst()).trimmingCharacters(in: .whitespaces))
                return text.isEmpty ? nil : .heading(text)
            }
            return .paragraph(tidyPersian(line))
        }
    }

    static func paragraphs(_ html: String) -> [String] {
        var s = html
        for tag in ["</p>", "<br>", "<br/>", "<br />"] { s = s.replacingOccurrences(of: tag, with: "\n", options: .caseInsensitive) }
        s = s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        s = decodeEntities(s)
        return s.components(separatedBy: .newlines)
            .map { $0.replacingOccurrences(of: "[ \t\u{00A0}]+", with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    static func decodeEntities(_ s: String) -> String {
        let named = ["&nbsp;": " ", "&amp;": "&", "&quot;": "\"", "&apos;": "'", "&lt;": "<", "&gt;": ">", "&zwnj;": "\u{200C}"]
        var out = s
        for (k, v) in named { out = out.replacingOccurrences(of: k, with: v) }
        // Numbered ones: &#8217; and &#x2019;
        while let r = out.range(of: "&#x?[0-9A-Fa-f]+;", options: .regularExpression) {
            let body = out[r].dropFirst(2).dropLast()
            let value = body.hasPrefix("x") || body.hasPrefix("X") ? UInt32(body.dropFirst(), radix: 16) : UInt32(body)
            out.replaceSubrange(r, with: value.flatMap(Unicode.Scalar.init).map { String(Character($0)) } ?? "")
        }
        return out
    }
}

extension Post {
    /// A post as the site API lists it, put in the app API's shape so it is read by the one parser.
    init?(site j: JSON) {
        guard j["id"].int != nil else { return nil }
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
                "poster": SiteQuery.posterShape(j["image"]["poster"]),
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
    /// A page of the site API's lists: `{page, pages, posts}`, with no total. A title the site
    /// lists twice is kept once, in its first place: a grid shows it twice otherwise.
    init(site j: JSON) {
        var seen = Set<Int>()
        posts = j["posts"].array.compactMap(Post.init(site:)).filter { seen.insert($0.id).inserted }
        page = j["page"].int ?? 1
        pages = j["pages"].int ?? 1
        total = posts.count
    }
}

/// The site's lists of the best: IMDb's 250 films and series, and the titles 30nama's own users
/// rate highest. Each comes whole, in order, with its place.
enum TopList: String, CaseIterable {
    case imdbMovies = "top-movie-imdb", imdbSeries = "top-series-imdb"
    case siteMovies = "top-movie-30nama", siteSeries = "top-series-30nama"

    struct Entry: Identifiable {
        var rank: Int
        var post: Post
        var id: Int { post.id }
    }

    static func entries(site j: JSON) -> [Entry] {
        // A title listed twice keeps its first, higher place.
        var seen = Set<Int>()
        return j["posts"].array.enumerated().compactMap { index, item in
            guard let post = Post(site: item), seen.insert(post.id).inserted else { return nil }
            // The place comes in Persian digits ("۱۲"); the list is in order anyway.
            return Entry(rank: Int(SiteText.latinDigits(item["rank"].text ?? "")) ?? index + 1, post: post)
        }
    }
}

/// The week of a series or anime: on which day each one has a new episode. The site numbers the
/// days as the Persian week does, Saturday 0 to Friday 6.
struct Schedule {
    enum Kind: String, CaseIterable { case series, anime }

    struct Entry: Identifiable {
        var post: Post
        var day: Int
        var id: Int { post.id }
    }

    var entries: [Entry]

    init(site j: JSON) {
        var seen = Set<Int>()
        entries = j["posts"].array.compactMap { item in
            guard let post = Post(site: item["post"]), let day = item["day"].int, (0...6).contains(day),
                  seen.insert(post.id).inserted else { return nil }
            return Entry(post: post, day: day)
        }
    }

    func on(_ day: Int) -> [Post] { entries.filter { $0.day == day }.map(\.post) }

    /// The site's number for the day `date` falls on.
    static func day(of date: Date, calendar: Calendar = .current) -> Int {
        // Calendar weekdays run Sunday 1 to Saturday 7; Saturday is the site's 0.
        calendar.component(.weekday, from: date) % 7
    }

    /// The week from today on, as the site numbers its days.
    static func week(from date: Date, calendar: Calendar = .current) -> [Int] {
        let today = day(of: date, calendar: calendar)
        return (0..<7).map { (today + $0) % 7 }
    }

    /// Where a day of the site's falls in `Calendar.weekdaySymbols`, which start on Sunday.
    static func symbolIndex(of day: Int) -> Int { (day + 6) % 7 }
}

/// The name a member gave the site, which the app API does not send: only the username, which
/// cannot be changed and is often not a name at all.
struct SiteProfile: Equatable {
    var firstName: String?
    var lastName: String?

    init(site j: JSON) {
        firstName = j["first_name"].text
        lastName = j["last_name"].text
    }

    /// First and last name together, or nil when neither is filled in.
    var fullName: String? {
        let parts = [firstName, lastName].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }
}

/// A piece of an article's page.
enum ArticleBlock: Hashable {
    case heading(String)
    case paragraph(String)
    case picture(URL)
}

/// An article as the site lists it: news, a review, or one of 30nama's own announcements. Its
/// text is fetched apart, when it is opened.
struct Article: Identifiable, Hashable {
    enum Kind: String, CaseIterable { case news, review, announcement }

    let id: Int
    var kind: Kind
    var title: String
    /// The first lines of the text, as the list gives them.
    var summary: String?
    /// As the site writes it, in Persian ("۲۹ مهر ۱۴۰۵").
    var date: String?
    var picture: URL?
    var thumbnail: URL?

    init?(site j: JSON) {
        guard let id = j["id"].int, let title = j["title"].text else { return nil }
        self.id = id
        kind = j["article_type"].text.flatMap(Kind.init(rawValue:)) ?? .news
        self.title = SiteText.decodeEntities(title)
        summary = j["description"].text.map { SiteText.tidyPersian(SiteText.paragraphs($0).joined(separator: " ")) }.flatMap { $0.isEmpty ? nil : $0 }
        date = j["date"].text
        // The JPEG first: the WebP the site lists is missing from its server for most articles.
        picture = j["image"]["full"].url ?? j["poster"]["large"].url ?? j["image"]["full_webp"].url
        thumbnail = j["image"]["thumb"].url ?? picture
    }
}

struct ArticlePage {
    var articles: [Article]
    var page: Int
    var pages: Int

    init(site j: JSON) {
        var seen = Set<Int>()
        articles = j["posts"].array.compactMap(Article.init(site:)).filter { seen.insert($0.id).inserted }
        page = j["page"].int ?? 1
        pages = j["pages"].int ?? 1
    }
}

/// An article whole: what the list had, and its text in blocks.
struct ArticleText {
    var article: Article
    var blocks: [ArticleBlock]

    init?(site j: JSON) {
        guard let article = Article(site: j) else { return nil }
        self.article = article
        var blocks = SiteText.blocks(j["content"].text ?? "")
        // The article's own picture is shown above it; the text often opens with it again.
        if case .picture(let first) = blocks.first, first.deletingPathExtension().lastPathComponent.hasPrefix(article.picture?.deletingPathExtension().deletingPathExtension().lastPathComponent ?? "\u{0}") {
            blocks.removeFirst()
        }
        self.blocks = blocks
    }
}

