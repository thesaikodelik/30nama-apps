import Foundation

/// The interface languages, in the order the language setting goes through them.
enum Lang: String, CaseIterable {
    case en, ar, fa

    var isRTL: Bool { self != .en }

    /// What the language calls itself.
    var name: String {
        switch self {
        case .en: return "English"
        case .ar: return "العربية"
        case .fa: return "فارسی"
        }
    }

    /// The language to ask 30nama for titles and plots in. It has them in Persian and English only.
    var catalogue: String { self == .fa ? "fa" : "en" }
}

// MARK: - Images

/// Poster variants as served by the CDN (widths 50 / 172 / 214 / 428 px, all 2:3).
struct Poster: Hashable {
    var blurhash: String?
    var small: URL?
    var medium: URL?
    var large: URL?
    var big: URL?

    init(_ j: JSON) {
        blurhash = j["preview"].text
        func pick(_ size: String) -> URL? { j["webp"][size].url ?? j["jpg"][size].url }
        small = pick("small"); medium = pick("medium"); large = pick("large"); big = pick("big")
        jpeg = j["jpg"]["large"].url ?? j["jpg"]["medium"].url
    }

    /// JPEG variant, for places that cannot take WebP (player artwork metadata).
    var jpeg: URL?

    var isEmpty: Bool { small == nil && medium == nil && large == nil && big == nil }

    /// Smallest variant that is at least `pixelWidth` wide, falling back to the largest available.
    func url(pixelWidth: Double) -> URL? {
        let ladder: [(Double, URL?)] = [(50, small), (172, medium), (214, large), (428, big)]
        return ladder.first { $0.0 >= pixelWidth && $0.1 != nil }?.1 ?? big ?? large ?? medium ?? small
    }
}

// MARK: - Post

struct Genre: Hashable {
    var name: String
    var slug: String
}

struct Person: Hashable, Identifiable {
    var name: String
    var role: String?
    var poster: Poster?
    var id: String { name + "|" + (role ?? "") }
}

/// A title (movie, series or anime) as it appears in lists and on the details page.
struct Post: Identifiable, Hashable {
    enum Kind: String { case movie, series, anime }

    let id: Int
    var kind: Kind = .movie
    var isSeries = false
    var hasStream = false
    var comingSoon = false
    var freeStream = false
    var persianSubtitle = false

    var titleEnglish: String?
    var titleLocal: String?
    var plotEnglish: String?
    var plotLocal: String?

    var year: Int?
    var yearEnd: Int?
    var seasons: Int?
    var age: String?
    var runtimeMinutes: Int?

    var genres: [Genre] = []
    var poster = Poster(.null)
    var cover: URL?

    var imdbScore: Double?
    var imdbID: String?
    var siteScore: Double?
    /// Tomatometer, 0...100.
    var rottenTomatoes: Int?
    /// Metascore, 0...100.
    var metacritic: Int?
    /// MyAnimeList (anime) or MyDramaList (Asian drama) score out of 10, whichever the title has.
    var fanScore: (source: String, value: Double)?
    // Shown by 30nama's own apps beside the scores: places on the top lists, votes, list counts.
    var imdbTop250: Int?
    var siteTop100: Int?
    var imdbVotes: Int?
    /// How many of 30nama's users keep it on their watchlist, and among their favourites.
    var inWatchlists: Int?
    var inFavorites: Int?
    var budget: String?
    var exclusiveSubtitle = false
    /// What 30nama has changed on the title ("new episode", "subtitle fixed"), newest first.
    var updates: [TitleUpdate] = []
    /// The last of them, with what it was.
    var latestUpdate: (title: String, content: String?)? { updates.first.map { ($0.title, $0.content) } }

    var cast: [Person] = []
    var directors: [String] = []
    var writers: [String] = []
    var creators: [String] = []
    var networks: [String] = []
    var countries: [String] = []
    var language: String?
    /// Spoken besides `language`, without repeating it.
    var otherLanguages: [String] = []
    var airStatus: String?
    var nextEpisodeCaption: String?
    var nextEpisodeDate: (gregorian: String?, jalali: String?) = (nil, nil)

    /// Only present on list entries of the "continue watching" feed and on `single`.
    var resume: ContinueWatching?
    /// The pages of the subtitle site 30nama sends people to: one per language, and per season of a series.
    var subtitlePages: [SubtitlePage] = []

    static func == (a: Post, b: Post) -> Bool { a.id == b.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }

    init?(_ j: JSON) {
        guard let id = j["id"].int else { return nil }
        self.id = id
        let o = j["options"], info = j["info"]
        isSeries = o["is_series"].bool
        kind = o["title_type"].text == "anime" ? .anime : (isSeries ? .series : .movie)
        hasStream = o["stream"].bool
        comingSoon = o["coming_soon"].bool
        freeStream = o["free_stream"].bool
        persianSubtitle = o["persian_subtitle"].bool

        titleEnglish = j["title"]["english"].text
        titleLocal = j["title"]["local"].text
        plotEnglish = j["plot"]["english"].text
        plotLocal = j["plot"]["local"].text

        year = info["year"].int
        yearEnd = info["year_end"].int
        seasons = info["seasons"].int
        age = info["age"].text
        runtimeMinutes = info["time"]["default"].int.flatMap { $0 > 0 ? $0 : nil }

        genres = j["genre"].array.compactMap { g in
            g["name"].text.map { Genre(name: $0, slug: g["slug"].text ?? $0) }
        }
        poster = Poster(j["image"]["poster"])
        cover = j["image"]["cover"]["webp"].url ?? j["image"]["cover"]["jpg"].url

        imdbScore = j["score"]["imdb"]["score"].double.flatMap { $0 > 0 ? $0 : nil }
        imdbID = j["score"]["imdb"]["link"].text.flatMap { Post.imdbID(in: $0) }
        siteScore = j["score"]["30nama"]["score"].double.flatMap { $0 > 0 ? $0 : nil }
        rottenTomatoes = j["score"]["rottentomatoes"]["score"].int.flatMap { $0 > 0 ? $0 : nil }
        metacritic = j["score"]["metacritic"]["score"].int.flatMap { $0 > 0 ? $0 : nil }
        for (key, label) in [("myanimelist", "MAL"), ("mydramalist", "MDL")] {
            if let v = j["score"][key]["score"].double, v > 0 { fanScore = (label, v); break }
        }
        func positive(_ v: JSON) -> Int? { v.int.flatMap { $0 > 0 ? $0 : nil } }
        imdbTop250 = positive(j["score"]["imdb"]["top_250"])
        siteTop100 = positive(j["score"]["30nama"]["top_100"])
        imdbVotes = positive(j["score"]["imdb"]["votes"])
        inWatchlists = positive(j["list"]["watchlist"])
        inFavorites = positive(j["list"]["favorite"])
        budget = info["budget"].text
        exclusiveSubtitle = o["exclusive_subtitle"].bool
        updates = j["note"]["update"].array.compactMap(TitleUpdate.init)

        cast = j["cast"].array.compactMap { c in
            guard let name = c["name"].text else { return nil }
            let p = Poster(c["image"]["poster"])
            return Person(name: name, role: c["as"].text, poster: p.isEmpty ? nil : p)
        }
        func names(_ v: JSON) -> [String] { v.array.compactMap { $0["name"].text ?? $0.text } }
        directors = names(j["crew"]["director"])
        writers = names(j["crew"]["writer"])
        creators = names(j["crew"]["creator"])
        networks = j["network"].array.compactMap(\.text)
        countries = names(j["country"])
        language = j["language"]["primary"]["name"].text
        var spoken: [String] = []
        for name in j["language"]["others"].array.compactMap({ $0["name"].text }) where name != language && !spoken.contains(name) {
            spoken.append(name)
        }
        otherLanguages = spoken
        airStatus = j["air"]["air_status"].text
        nextEpisodeCaption = j["air"]["next_episode_caption"].text
        nextEpisodeDate = (j["air"]["next_episode_date"]["g"].text, j["air"]["next_episode_date"]["j"].text)
        subtitlePages = j["subtitle"].array.compactMap { entry in
            guard let link = entry["link"].url else { return nil }
            // The language is written out in Persian; the address says it in a form that does not change.
            return SubtitlePage(link: link, persian: link.path.contains("farsi") || link.path.contains("persian"), season: entry["season"].int)
        }
        resume = ContinueWatching(j["continue_watching"])
    }

    /// Posts from any of the list shapes the API uses (`posts`, `items`, `list`, a bare array,
    /// and entries wrapped as `{post: {...}}`). Duplicates are dropped, order is kept.
    static func list(_ j: JSON) -> [Post] {
        var seen = Set<Int>()
        return rawList(j).compactMap { x in
            guard let p = Post(x["post"].isNull ? x : x["post"]), seen.insert(p.id).inserted else { return nil }
            return p
        }
    }

    /// The "continue watching" feed: like `list`, but each post carries its progress, which sits
    /// inside the post (as `continue_watching`, or as `data`, which is what the server sends a
    /// subscriber), next to it in a wrapper, or flat on the entry itself.
    static func continueList(_ j: JSON) -> [Post] {
        var seen = Set<Int>()
        return rawList(j).compactMap { x in
            let inner = x["post"].isNull ? x : x["post"]
            guard var p = Post(inner), seen.insert(p.id).inserted else { return nil }
            p.resume = ContinueWatching(x["continue_watching"]) ?? p.resume ?? ContinueWatching(inner["data"]) ?? ContinueWatching(x)
            // The feed gives a position and a percentage to four places, but no length: work it out.
            if var resume = p.resume, resume.duration <= 0, resume.time > 0, let percent = resume.reportedPercent, percent > 0 {
                resume.duration = resume.time * 100 / percent
                p.resume = resume
            }
            return p
        }
    }

    static func rawList(_ j: JSON) -> [JSON] {
        if case .array(let a) = j { return a }
        for key in ["posts", "items", "list"] {
            if case .array(let a) = j[key] { return a }
        }
        return []
    }

    static func imdbID(in link: String) -> String? {
        link.range(of: #"tt\d{6,10}"#, options: .regularExpression).map { String(link[$0]) }
    }

    // MARK: Display

    /// Titles arrive with the release year appended ("Animals 2026"). The year has its own place in
    /// the meta line, so drop it, but only when it really is this title's year ("Blade Runner 2049"
    /// keeps its number).
    private func withoutYear(_ title: String?) -> String? {
        guard let t = title else { return nil }
        for y in [year, yearEnd].compactMap({ $0 }) {
            for suffix in [" \(y)", " (\(y))"] where t.hasSuffix(suffix) && t.count > suffix.count {
                return String(t.dropLast(suffix.count)).trimmingCharacters(in: .whitespaces)
            }
        }
        return t
    }

    var cleanEnglishTitle: String? { withoutYear(titleEnglish) }
    var cleanLocalTitle: String? { withoutYear(titleLocal) }

    func title(_ lang: Lang) -> String {
        if lang == .fa, let l = cleanLocalTitle { return l }
        return cleanEnglishTitle ?? cleanLocalTitle ?? ""
    }

    /// The title in the other language, when there is one and it differs.
    func alternateTitle(_ lang: Lang) -> String? {
        let other = lang == .fa ? cleanEnglishTitle : cleanLocalTitle
        guard let other, other != title(lang) else { return nil }
        return other
    }

    func plot(_ lang: Lang) -> String {
        if lang == .fa, let l = plotLocal { return l }
        return plotEnglish ?? plotLocal ?? ""
    }
}

// MARK: - Playback

struct ContinueWatching: Hashable {
    var fileID: String?
    var time: Double
    var duration: Double
    var season: Int?
    var episode: String?
    /// Percentage watched as the server reports it, for responses that carry no duration.
    var reportedPercent: Double?

    init?(_ j: JSON) {
        guard j.object != nil else { return nil }
        reportedPercent = j["progress"].double
        let info = j["info"]
        fileID = j["file_id"].text ?? info["file_id"].text
        time = j["time"].double ?? j["position"].double ?? 0
        duration = j["duration"].double ?? 0
        season = Episode.number(j["season"].string ?? info["season"].string)
        episode = j["episode"].text ?? info["episode"].text
        if fileID == nil && time <= 0 { return nil }
    }

    var progress: Double {
        if duration > 0 { return min(1, max(0, time / duration)) }
        return min(1, max(0, (reportedPercent ?? 0) / 100))
    }
    var remainingMinutes: Int? { duration > 0 && time > 0 ? max(1, Int(((duration - time) / 60).rounded())) : nil }
}

/// Which audio mix to open. The original file is often a codec the Apple TV cannot play through
/// its own speakers (DTS, TrueHD); the server publishes stereo and 5.1 playlists beside it.
enum AudioMix: String {
    case original, stereo, surround
}

/// One selectable rendition of a stream.
struct Quality: Hashable {
    var label: String
    /// The original playlist (`default`), or the only URL the entry has.
    var url: URL
    /// Stereo downmix, when the server offers one apart from `url`.
    var stereo: URL? = nil
    /// 5.1 downmix, when the server offers one apart from `url`.
    var surround: URL? = nil
    /// Vertical resolution parsed from the label ("1080p" -> 1080); 0 for the adaptive entry.
    var height: Int
    var isAuto: Bool

    /// The playlist for `mix`, falling back to the original when that mix was not published.
    func url(for mix: AudioMix) -> URL {
        switch mix {
        case .stereo: return stereo ?? url
        case .surround: return surround ?? url
        case .original: return url
        }
    }

    /// Mixes worth showing. Original is always there; the others only when they are a different file.
    var mixes: [AudioMix] {
        var list: [AudioMix] = []
        if stereo != nil { list.append(.stereo) }
        if surround != nil { list.append(.surround) }
        list.append(.original)
        return list
    }

    /// `m3u8` is either one URL or a list of `{label, default, 2ch, 6ch}` entries where "Auto" is the adaptive master.
    static func parse(_ m: JSON) -> [Quality] {
        if case .array(let entries) = m {
            return entries.compactMap { e in
                guard let url = entryURL(e) else { return nil }
                let label = e["label"].text ?? "Auto"
                let isAuto = ["auto", "خودکار"].contains(label.lowercased())
                let height = Int(label.filter(\.isNumber)) ?? 0
                return Quality(
                    label: label, url: url,
                    stereo: channelURL(e, "2ch"), surround: channelURL(e, "6ch"),
                    height: isAuto ? 0 : height, isAuto: isAuto || height == 0
                )
            }
        }
        return entryURL(m).map { [Quality(label: "Auto", url: $0, height: 0, isAuto: true)] } ?? []
    }

    /// A sibling playlist (`2ch`, `6ch`). Missing keys stay nil so they are not mistaken for the original.
    private static func channelURL(_ e: JSON, _ key: String) -> URL? {
        guard case .object = e else { return nil }
        return entryURL(e[key])
    }

    private static func entryURL(_ e: JSON) -> URL? {
        switch e {
        case .string: return e.url
        case .array(let a): return a.first.flatMap(entryURL)
        case .object:
            // Stereo before 5.1: a device that cannot decode the original still gets sound.
            for key in ["default", "file", "url", "src", "link", "m3u8", "path", "source", "2ch", "6ch"] {
                if let u = entryURL(e[key]) { return u }
            }
            return nil
        default: return nil
        }
    }
}

enum Episode {
    /// Leading integer of values like "3", "11,12" or "S02".
    static func number(_ raw: String?) -> Int? {
        guard let raw else { return nil }
        let digits = raw.drop { !$0.isNumber }.prefix { $0.isNumber }
        return Int(digits)
    }

    /// Combined episodes arrive as "11,12"; show them as "11–12".
    static func label(_ raw: String?, fallback: Int) -> String {
        guard let raw, !raw.trimmingCharacters(in: .whitespaces).isEmpty else { return String(fallback) }
        return raw.replacingOccurrences(of: #"\s*[,&+\-–]\s*"#, with: "–", options: .regularExpression)
    }
}

/// One playable file: a movie, or an episode of a series.
struct StreamItem: Identifiable, Hashable {
    var fileID: String
    var postID: String?
    var season: Int
    var episodeRaw: String?
    var titleEnglish: String?
    var titleLocal: String?
    var durationMinutes: Int?
    var token: String
    var introStart: Double?
    var introEnd: Double?
    var recapStart: Double?
    var recapEnd: Double?
    var creditsStart: Double?
    var qualities: [Quality]
    var subtitles: [String: URL]

    var id: String { fileID.isEmpty ? "\(season)-\(episodeRaw ?? "")" : fileID }
    var episodeNumber: Int { Episode.number(episodeRaw) ?? 0 }

    init?(_ j: JSON) {
        let info = j["info"], opt = j["options"], file = j["file"]
        qualities = Quality.parse(file["m3u8"])
        fileID = info["file_id"].text ?? ""
        postID = info["post_id"].text
        season = Episode.number(info["season"].string) ?? 1
        episodeRaw = info["episode"].text
        let t = [info["title"], info["name"], j["title"]].first { !$0.isNull } ?? .null
        if t.object != nil {
            titleEnglish = t["english"].text; titleLocal = t["local"].text
        } else {
            titleEnglish = t.text
        }
        if let d = info["duration"].double ?? info["time"].double, d > 0 {
            durationMinutes = Int((d > 600 ? d / 60 : d).rounded())
        }
        token = opt["token"].text ?? ""
        introStart = StreamItem.seconds(opt["intro_start"])
        introEnd = StreamItem.seconds(opt["intro_end"])
        recapStart = StreamItem.seconds(opt["previously_start"])
        recapEnd = StreamItem.seconds(opt["previously_end"])
        creditsStart = StreamItem.seconds(opt["end"])
        subtitles = [:]
        for lang in ["fa", "en"] {
            if let u = StreamItem.subtitleURL(file["subtitle"][lang]) { subtitles[lang] = u }
        }
        if qualities.isEmpty && fileID.isEmpty { return nil }
    }

    /// Times arrive as seconds or as "HH:MM:SS".
    static func seconds(_ v: JSON) -> Double? {
        if case .string(let s) = v, s.contains(":") {
            return s.split(separator: ":").reduce(0) { $0 * 60 + (Double($1) ?? 0) }
        }
        return v.double
    }

    private static func subtitleURL(_ v: JSON) -> URL? {
        switch v {
        case .string: return v.url
        case .array(let a): return a.first.flatMap(subtitleURL)
        case .object:
            for key in ["url", "file", "src", "link", "vtt"] {
                if let u = v[key].url { return u }
            }
            return nil
        default: return nil
        }
    }

    func title(_ lang: Lang) -> String? {
        if lang == .fa, let l = titleLocal { return l }
        return titleEnglish ?? titleLocal
    }

    /// Highest fixed rendition by default; the adaptive master when asked for or when it is all there is.
    func quality(preferAdaptive: Bool) -> Quality? {
        let fixed = qualities.filter { !$0.isAuto }.max { $0.height < $1.height }
        let auto = qualities.first(where: \.isAuto)
        return preferAdaptive ? (auto ?? fixed) : (fixed ?? auto)
    }

    /// What to skip at `t`, if anything. One second of slack at the end so the button does not flash.
    func skipTarget(at t: Double) -> (isIntro: Bool, to: Double)? {
        if let end = introEnd, end > 0, t >= (introStart ?? 0), t < end - 1 { return (true, end) }
        if let end = recapEnd, end > 0, t >= (recapStart ?? 0), t < end - 1 { return (false, end) }
        return nil
    }
}

struct StreamResult {
    var items: [StreamItem]
    var resume: ContinueWatching?

    init(_ j: JSON) {
        items = j["hls"].array.compactMap(StreamItem.init).sorted {
            ($0.season, $0.episodeNumber) < ($1.season, $1.episodeNumber)
        }
        resume = ContinueWatching(j["continue_watching"])
        if resume?.fileID == nil { resume = nil }
    }

    var seasons: [Int] { Array(Set(items.map(\.season))).sorted() }

    /// Index of the file to resume, or the first one.
    var startIndex: Int {
        guard let id = resume?.fileID else { return 0 }
        return items.firstIndex { $0.fileID == id } ?? 0
    }
}

// MARK: - Feeds

struct HomeSection: Identifiable {
    let key: String
    var posts: [Post]
    var id: String { key }
}

struct HomeFeed {
    var heroes: [Post]
    var sections: [HomeSection]

    static let sectionKeys = ["new_releases", "suggested", "top_10", "movies", "series", "anime"]

    init(_ j: JSON) {
        heroes = Array(Post.list(j["hero_header"]).prefix(8))
        sections = HomeFeed.sectionKeys.compactMap { key in
            var posts = Post.list(j[key])
            if key == "top_10" { posts = Array(posts.prefix(10)) }
            return posts.isEmpty ? nil : HomeSection(key: key, posts: posts)
        }
    }

    var isEmpty: Bool { heroes.isEmpty && sections.isEmpty }
    func posts(_ key: String) -> [Post] { sections.first { $0.key == key }?.posts ?? [] }

    /// Titles fit to stand behind a screen that has none of its own, such as sign-in: the
    /// best-regarded live-action films and series that have a wide picture. Animation is left
    /// out, since a cartoon still sets the wrong tone for the whole app. When nothing qualifies,
    /// anything with a wide picture will do.
    func backdrops(limit: Int = 10) -> [Post] {
        var seen = Set<Int>()
        let wide = (sections.flatMap(\.posts) + heroes).filter { $0.cover != nil && seen.insert($0.id).inserted }
        let fit = wide.filter { $0.kind != .anime && !$0.genres.contains { $0.slug == "animation" } }
        let ranked = (fit.isEmpty ? wide : fit).sorted { ($0.imdbScore ?? 0) > ($1.imdbScore ?? 0) }
        return Array(ranked.prefix(limit))
    }
}

struct PostPage {
    var posts: [Post]
    var page: Int
    var pages: Int
    var total: Int

    init(_ j: JSON) {
        posts = Post.list(j)
        page = j["page"].int ?? 1
        pages = j["pages"].int ?? 1
        total = j["total"].int ?? posts.count
    }
}

/// A category or genre from the `menu` action.
struct MenuEntry: Identifiable, Hashable {
    let id: Int
    var name: String
    var slug: String
    var count: Int?
    var thumb: URL?
    var full: URL?

    init?(_ j: JSON) {
        guard let slug = j["slug"].text, let name = j["name"].text else { return nil }
        self.slug = slug; self.name = name
        id = j["id"].int ?? slug.hashValue
        count = j["count"].int
        thumb = j["image"]["webp"]["thumb"].url ?? j["image"]["jpg"]["thumb"].url
        full = j["image"]["webp"]["full"].url ?? j["image"]["jpg"]["full"].url
    }
}

struct Menu {
    var categories: [MenuEntry]
    var genres: [MenuEntry]

    init(_ j: JSON) {
        categories = j["categories"].array.compactMap(MenuEntry.init)
        genres = j["genres"].array.compactMap(MenuEntry.init)
    }
}

// MARK: - Subtitles from the subtitle site

/// A page on the subtitle site that lists the subtitles for one film, or one season, in one language.
struct SubtitlePage: Hashable, Codable {
    var link: URL
    var persian: Bool
    var season: Int?
}

/// Reads the subtitle site 30nama links to, so a subtitle can be fetched without visiting it.
/// The site has no interface for programs; this reads its pages, and will need mending if they change.
enum SubtitleSite {
    /// One subtitle on a listing: where its own page is, and the releases it was timed for.
    struct Entry: Equatable {
        var path: String
        var releases: [String]
    }

    /// The subtitles a listing page offers. One subtitle is listed once for every release it fits.
    static func entries(in html: String) -> [Entry] {
        guard let pattern = try? NSRegularExpression(pattern: #"href="(/subtitle/[^"]+/\d+)"[^>]*>([^<]*)</a>"#) else { return [] }
        var order: [String] = []
        var found: [String: [String]] = [:]
        let whole = NSRange(html.startIndex..., in: html)
        for match in pattern.matches(in: html, range: whole) {
            guard let path = Range(match.range(at: 1), in: html), let label = Range(match.range(at: 2), in: html) else { continue }
            // "Farsi/Persian — Some.Film.2020.1080p.WEB-DL": the release is what follows the dash.
            let text = String(html[label])
            let release = (text.range(of: "— ").map { String(text[$0.upperBound...]) } ?? text).trimmingCharacters(in: .whitespaces)
            let key = String(html[path])
            if found[key] == nil { order.append(key) }
            if !release.isEmpty, found[key]?.contains(release) != true { found[key, default: []].append(release) }
        }
        return order.map { Entry(path: $0, releases: found[$0] ?? []) }
    }

    /// The address a subtitle's own page offers its file at.
    static func download(in html: String) -> URL? {
        guard let range = html.range(of: #"https://api\.subsource\.net/v1/subtitle/download/[A-Za-z0-9]+"#, options: .regularExpression) else { return nil }
        return URL(string: String(html[range]))
    }

    /// The words of a file or release name that say what it is a copy of: "bluray", "1080p", "s01e02".
    static func words(_ name: String) -> Set<String> {
        let lowered = name.lowercased().replacingOccurrences(of: "web-dl", with: "webdl").replacingOccurrences(of: "blu-ray", with: "bluray")
        return Set(lowered.split { !$0.isLetter && !$0.isNumber }.map(String.init))
    }

    /// The subtitle most likely to be in time with `fileName`: for an episode it must be for that
    /// episode or for its whole season, and beyond that the more of the copy's own marks it
    /// shares (source, resolution, who released it), the better.
    static func best(for fileName: String, season: Int?, episode: Int?, among entries: [Entry]) -> Entry? {
        let wanted = words(fileName)
        let sources: Set<String> = ["bluray", "brrip", "bdrip", "webdl", "web", "webrip", "hdtv", "dvdrip", "hdrip"]
        var best: (entry: Entry, score: Int)?
        for entry in entries {
            var top = Int.min
            for release in entry.releases {
                let has = words(release)
                var score = 0
                if let season, let episode {
                    let tag = String(format: "s%02de%02d", season, episode)
                    let other = has.contains { $0.range(of: #"^s\d{1,2}e\d{1,3}$"#, options: .regularExpression) != nil }
                    if has.contains(tag) { score += 100 } else if other { continue } else { score += 20 }
                }
                // The source matters most: a subtitle timed for a broadcast runs off against a disc.
                score += wanted.intersection(has).intersection(sources).count * 30
                score += wanted.intersection(has).subtracting(sources).count
                top = max(top, score)
            }
            if entry.releases.isEmpty { top = 0 }
            if top > (best?.score ?? Int.min) { best = (entry, top) }
        }
        return best?.entry
    }

    /// Of the subtitle files in an archive, the one for this episode; for a film, the only or first one.
    static func pick(from names: [String], season: Int?, episode: Int?) -> String? {
        let subtitles = names.filter { $0.lowercased().hasSuffix(".srt") }.sorted()
        guard let season, let episode else { return subtitles.first }
        let tags = [String(format: "s%02de%02d", season, episode), String(format: "%dx%02d", season, episode), String(format: "e%02d", episode)]
        for tag in tags {
            if let match = subtitles.first(where: { $0.lowercased().contains(tag) }) { return match }
        }
        return subtitles.count == 1 ? subtitles.first : nil
    }

    /// Subtitle text whatever it was saved as. Persian files are often in the Arabic Windows code page.
    static func text(_ data: Data) -> String? {
        let arabic = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(CFStringEncodings.windowsArabic.rawValue)))
        if let text = String(data: data, encoding: .utf8) { return text }
        // Any even run of bytes passes for UTF-16, so that is only believed when the file says so itself.
        if data.starts(with: [0xFF, 0xFE]) || data.starts(with: [0xFE, 0xFF]), let text = String(data: data, encoding: .utf16) { return text }
        return String(data: data, encoding: arabic)
    }
}

// MARK: - Downloads

/// One file 30nama offers for download: a film in one quality, or one episode of a series.
struct DownloadFile: Identifiable, Hashable {
    var id: String
    var url: URL
    var quality: String
    /// The size as the server words it, "781 MB".
    var size: String?
    var encoder: String?
    var tags: [String] = []
    var season: Int?
    var episode: String?
    /// Persian subtitles for this file, when 30nama has them.
    var subtitle: URL?

    /// The name to save under: the one in the address, which carries the right extension.
    var fileName: String {
        let name = url.lastPathComponent.removingPercentEncoding ?? url.lastPathComponent
        return name.isEmpty || name == "/" ? "\(id).mkv" : name
    }

    /// "x265 BluRay 1080p · 30nama · 781 MB"
    var summary: String {
        [quality, encoder, size].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }
}

/// What can be downloaded for a title. A film is a flat list of files, one per quality. A series
/// comes as packs, a season in one quality, each holding its episodes.
struct DownloadOptions {
    struct Pack: Identifiable, Hashable {
        var id: String
        var season: Int
        var quality: String
        /// The size of one episode, as the server words it.
        var size: String?
        var encoder: String?
        var files: [DownloadFile]

        var summary: String {
            [quality, encoder, size].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
        }
    }

    var files: [DownloadFile] = []
    var packs: [Pack] = []

    var seasons: [Int] { Array(Set(packs.map(\.season))).sorted() }
    var isEmpty: Bool { files.isEmpty && packs.isEmpty }

    /// The server numbers its files with eighteen digits, more than a number here keeps apart:
    /// several episodes came out with one id and all but one vanished from the list. What a
    /// file is of, and the name in its address, tell every file from every other.
    static func identity(_ url: URL, _ season: Int?, _ episode: String?) -> String {
        "\(season ?? 0)|\(episode ?? "")|\(url.lastPathComponent)"
    }

    /// Every episode any pack of the season has, in order. A pack that lacks some of them is incomplete.
    func episodes(inSeason season: Int) -> [String] {
        var seen = Set<String>()
        return packs.filter { $0.season == season }.flatMap(\.files).compactMap(\.episode)
            .filter { seen.insert($0).inserted }
            .sorted { (Episode.number($0) ?? Int.max, $0) < (Episode.number($1) ?? Int.max, $1) }
    }

    init(_ j: JSON) {
        let root = j["download"]
        func tags(_ entry: JSON) -> [String] { entry["tags"].array.compactMap(\.text) }
        if case .array(let entries) = root {
            files = entries.compactMap { entry in
                guard let url = entry["dl"].url else { return nil }
                return DownloadFile(id: DownloadOptions.identity(url, nil, nil), url: url, quality: entry["quality"].text ?? "",
                                    size: entry["size"].text, encoder: entry["encoder"].text, tags: tags(entry),
                                    subtitle: entry["persian_subtitle"].url)
            }
        } else {
            for (_, group) in root.object ?? [:] {
                for entry in group.array {
                    let season = entry["season_int"].int ?? entry["season"].int ?? 1
                    let quality = entry["quality"].text ?? ""
                    let links: [DownloadFile] = entry["link"].array.compactMap { link in
                        guard let url = link["dl"].url else { return nil }
                        return DownloadFile(id: DownloadOptions.identity(url, season, link["episode"].string), url: url, quality: quality,
                                            size: entry["size"].text, encoder: entry["encoder"].text, tags: tags(entry),
                                            season: season, episode: link["episode"].string, subtitle: link["persian_subtitle"].url)
                    }
                    guard !links.isEmpty else { continue }
                    packs.append(Pack(id: "\(season)-\(quality)-\(entry["encoder"].text ?? "")-\(packs.count)", season: season, quality: quality,
                                      size: entry["size"].text, encoder: entry["encoder"].text, files: links))
                }
            }
            packs.sort { ($0.season, $0.quality) < ($1.season, $1.quality) }
        }
    }
}

// MARK: - Narrowing a catalogue

/// The choices 30nama's advanced search offers, as the server lists them.
struct FilterOptions {
    struct Choice: Hashable, Identifiable {
        var name: String
        var value: String
        var id: String { value }
    }

    var genres: [Choice]
    var countries: [Choice]
    /// Lowest IMDb score to accept.
    var scores: [Choice]
    /// First year of each decade there are titles for, newest first.
    var decades: [Int]

    init(_ j: JSON) {
        func choices(_ key: String) -> [Choice] {
            j[key].array.compactMap { entry in
                guard let name = entry["name"].text, let value = entry["value"].string, !value.isEmpty else { return nil }
                return Choice(name: name, value: value)
            }
        }
        genres = choices("genre")
        countries = choices("country")
        // "Under 5.0" is sent as a negative number; only the floors are offered.
        scores = choices("imdb_rate").filter { (Double($0.value) ?? 0) > 0 }
        let newest = (j["max_year"].int ?? 2020) / 10 * 10
        let oldest = max(1950, (j["min_year"].int ?? 1950) / 10 * 10)
        decades = Array(stride(from: newest, through: oldest, by: -10))
    }
}

/// What the user has narrowed a catalogue down to.
struct Filter: Equatable {
    var genre: String?
    var country: String?
    var score: String?
    /// First year of the decade: 2010 stands for 2010 to 2019.
    var decade: Int?

    var isEmpty: Bool { genre == nil && country == nil && score == nil && decade == nil }

    /// The `parameters` value `advanced_search` takes: a JSON object of the chosen fields.
    /// - Parameter type: "movie", "series" or "anime", to stay inside one catalogue.
    func parameters(type: String?) -> String {
        var fields: [String: Any] = ["stream": "true"]
        // As a list: the server ignores a type given as a single value, and the catalogue of films
        // filled up with series.
        if let type { fields["type"] = [type] }
        if let genre { fields["genre"] = genre }
        if let country { fields["country"] = country }
        if let score, let floor = Double(score) { fields["imdb_rate"] = floor }
        if let decade {
            fields["min_year"] = decade
            fields["max_year"] = decade + 9
        }
        let data = try? JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys])
        return data.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
    }
}

extension Post {
    /// The day the next episode is due, when the server names one. It writes the day out,
    /// "16 October 2026 Friday"; a plain "2026-10-16" is understood too.
    var nextEpisodeDay: Date? {
        guard let raw = nextEpisodeDate.gregorian else { return nil }
        let format = DateFormatter()
        format.calendar = Calendar(identifier: .gregorian)
        format.locale = Locale(identifier: "en_US_POSIX")
        format.timeZone = TimeZone(identifier: "UTC")
        format.dateFormat = "d MMMM yyyy"
        if let day = format.date(from: raw.split(separator: " ").prefix(3).joined(separator: " ")) { return day }
        format.dateFormat = "yyyy-MM-dd"
        return format.date(from: String(raw.prefix(10)))
    }

    /// The day of the next episode as people say it, weekday first and no year: "پنجشنبه ۲۳ مهر",
    /// "Thursday 15 October". The server writes it the other way round ("۲۳ مهر ۱۴۰۵ پنجشنبه").
    func nextEpisodeLabel(_ lang: Lang) -> String? {
        guard let raw = lang == .fa ? nextEpisodeDate.jalali : nextEpisodeDate.gregorian else { return nil }
        let parts = raw.split(separator: " ").map(String.init)
        // Day, month, year, then the weekday, which in Persian can be two words ("سه شنبه").
        guard parts.count >= 4 else { return raw }
        return ([parts[3...].joined(separator: " "), parts[0], parts[1]]).joined(separator: " ")
    }

    /// The series among `posts` with an episode still to come, soonest first.
    static func upcoming(_ posts: [Post], now: Date = Date()) -> [Post] {
        let today = now.addingTimeInterval(-86400)
        var seen = Set<Int>()
        return posts
            .filter { seen.insert($0.id).inserted }
            .compactMap { post in post.nextEpisodeDay.map { (post, $0) } }
            .filter { $0.1 > today }
            .sorted { $0.1 < $1.1 }
            .map(\.0)
    }
}

// MARK: - Account

struct User: Equatable {
    var token: String
    var id: String?
    var name: String?
    var type: String?
    /// The day the subscription ends, as "2028-07-15".
    var expiry: String?
    /// The same day in the Persian calendar ("۱۴۰۷-۰۴-۲۵"), when the server gives it.
    var expiryLocal: String?
    var avatar: URL?

    /// The profile is returned bare, or wrapped as `{user: {...}}`, with or without the session token.
    init?(_ j: JSON, fallbackToken: String? = nil) {
        let u = j["user_session"].isNull && j["user"].object != nil ? j["user"] : j
        guard let token = u["user_session"].text ?? u["token"].text ?? fallbackToken else { return nil }
        self.token = token
        id = u["user_id"].text ?? u["id"].text
        name = u["user_name"].text ?? u["user_email"].text
        type = u["user_type"].text
        // A subscriber's expiry arrives as `{days, g, j}`: the date and time in both calendars.
        let expire = u["user_expire"]
        if expire.object != nil {
            expiry = expire["g"].text.map { String($0.prefix(10)) }
            expiryLocal = expire["j"].text.map { String($0.prefix(10)) }
        } else {
            expiry = User.formatExpiry(expire)
            expiryLocal = u["user_expire_local"].text
        }
        avatar = u["user_avatar"].url
    }

    /// Whole days until the subscription ends, counting today; nil when the end is unknown.
    func daysLeft(now: Date = Date()) -> Int? {
        guard let expiry else { return nil }
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyy-MM-dd"
        guard let end = f.date(from: expiry) else { return nil }
        return max(0, Int((end.timeIntervalSince(now) / 86400).rounded(.up)))
    }

    /// Expiry arrives as a unix timestamp (seconds or milliseconds) or as preformatted text.
    static func formatExpiry(_ v: JSON) -> String? {
        guard let raw = v.text else { return nil }
        guard raw.range(of: #"^\d{9,13}$"#, options: .regularExpression) != nil, var n = Double(raw) else { return raw }
        if n > 1e11 { n /= 1000 }
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date(timeIntervalSince1970: n))
    }
}

/// One change 30nama made to a title, as its notes list them.
struct TitleUpdate: Equatable {
    let title: String
    let content: String?
    /// "2026-10-09 22:41:36" and its Persian-calendar twin, as sent.
    let gregorian: String?
    let jalali: String?

    init?(_ j: JSON) {
        guard let title = j["title"].text else { return nil }
        self.title = title
        content = j["content"].text
        gregorian = j["date"]["g"].text
        jalali = j["date"]["j"].text
    }

    /// The day it was made, without the hour: in the Persian calendar for Persian.
    func day(persian: Bool) -> String? {
        let full = persian ? jalali ?? gregorian : gregorian ?? jalali
        return full?.split(separator: " ").first.map(String.init)
    }
}
