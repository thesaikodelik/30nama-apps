import Foundation

/// Fetches and keeps what TMDB says about titles. With the user's own TMDB key, through the API:
/// logo, trailer, crew and reviews. Without one, only the logo, read from the site's public pages.
@MainActor
@Observable
final class TMDBStore {
    static let shared = TMDBStore()

    static var key: String? {
        let value = UserDefaults.standard.string(forKey: "tmdbKey")?.trimmingCharacters(in: .whitespacesAndNewlines)
        return value?.isEmpty == false ? value : nil
    }

    /// What is known, by 30nama post id. Kept for the session; titles do not change their logo.
    private(set) var known: [Int: TMDB.Info] = [:]
    /// Looks under way, so that two parts of a page asking at once share one.
    @ObservationIgnored private var running: [Int: Task<TMDB.Info?, Never>] = [:]
    @ObservationIgnored private var usedKey: String?
    /// TMDB is filtered here (the app is in Iran, `AppModel.inIran`): it is not asked at all, and
    /// logos come from the repository's list of them instead.
    @ObservationIgnored var filtered = false
    @ObservationIgnored private var index: Task<LogoIndex?, Never>?

    /// What TMDB has for `post`, fetched the first time it is asked for. Without a key it returns
    /// once the logo is known; the trailer arrives in `known` a moment later.
    func info(for post: Post, lang: Lang) async -> TMDB.Info? {
        // A new key, or none: what was looked up with the old one may not hold.
        if TMDBStore.key != usedKey {
            usedKey = TMDBStore.key
            forget()
        }
        if let info = known[post.id] { return info }
        if let task = running[post.id] { return await task.value }
        trailerSettled.remove(post.id)
        let task = Task { await look(post, lang: lang) }
        running[post.id] = task
        let info = await task.value
        running[post.id] = nil
        // Failed: there will be no trailer this time, and no one should wait for one.
        if info == nil { trailerSettled.insert(post.id) }
        return info
    }

    /// The trailer for `post`, waiting up to `patience` and no longer. The wait starts now, not once
    /// TMDB has answered: where TMDB is filtered its requests are neither answered nor refused, and
    /// a title page holding its buttons for the answer showed none for a minute (issue #2).
    func trailer(for post: Post, lang: Lang, patience: Duration = .seconds(4)) async -> String? {
        let until = ContinuousClock.now + patience
        Task { _ = await info(for: post, lang: lang) }
        while ContinuousClock.now < until {
            if let trailer = known[post.id]?.trailer { return trailer }
            if trailerSettled.contains(post.id) { return nil }
            try? await Task.sleep(for: .milliseconds(250))
        }
        return known[post.id]?.trailer
    }

    @ObservationIgnored private var trailerSettled: Set<Int> = []

    private func look(_ post: Post, lang: Lang) async -> TMDB.Info? {
        #if DEBUG
        // `NAMA_TMDB_SAMPLE=1`: sample crew and reviews on top of what the public pages give, to
        // see those parts of a page without a key.
        if ProcessInfo.processInfo.environment["NAMA_TMDB_SAMPLE"] == "1" {
            var info = await publicLook(for: post, lang: lang) ?? TMDB.Info()
            info.crew = [.init(name: "Sample Director", job: "Director"), .init(name: "Sample Writer", job: "Screenplay"),
                         .init(name: "Sample Composer", job: "Original Music Composer"), .init(name: "Sample DoP", job: "Director of Photography")]
            info.reviews = [.init(author: "sample critic", text: String(repeating: "A sample review that runs on for a while, to see how a long text is cut short in its card. ", count: 4), rating: 8, link: nil),
                            .init(author: "another", text: "Short and to the point.", rating: nil, link: nil)]
            known[post.id] = info
            return info
        }
        #endif
        if filtered { return await indexedLook(for: post, lang: lang) }
        guard let key = TMDBStore.key else { return await publicLook(for: post, lang: lang) }
        switch TMDBStore.stored(post) {
        case .found(let details):
            let info = TMDB.info(in: details, lang: lang)
            known[post.id] = info
            trailerSettled.insert(post.id)
            return info
        case .missed:
            return nil
        case nil:
            break
        }
        var id: Int?
        if let imdb = post.imdbID, let found = await json(TMDB.findRequest(imdbID: imdb, key: key)) {
            id = TMDB.id(inFind: found, isSeries: post.isSeries)
        }
        let name = post.titleEnglish ?? post.title(.en)
        if id == nil, let found = await json(TMDB.searchRequest(title: name, year: post.year, isSeries: post.isSeries, key: key)) {
            id = TMDB.best(inSearch: found, title: name, year: post.year)
        }
        // 30nama's year is sometimes TMDB's off by one: once more without it, the nearest still first.
        if id == nil, post.year != nil, let found = await json(TMDB.searchRequest(title: name, year: nil, isSeries: post.isSeries, key: key)) {
            id = TMDB.best(inSearch: found, title: name, year: post.year)
        }
        // Asked and not found (TMDB answered): kept as a miss for a day.
        if id == nil, Date() >= quietUntil { TMDBStore.store(post, nil) }
        // A failed look is tried again next time the title is opened.
        guard let id, let details = await json(TMDB.detailsRequest(id: id, isSeries: post.isSeries, key: key)) else { return nil }
        TMDBStore.store(post, details)
        let info = TMDB.info(in: details, lang: lang)
        known[post.id] = info
        trailerSettled.insert(post.id)
        return info
    }

    /// Where TMDB is filtered: the logo from the list on GitHub, and no trailer (Aparat has those).
    private func indexedLook(for post: Post, lang: Lang) async -> TMDB.Info? {
        if index == nil { index = Task { await TMDBStore.loadIndex() } }
        let list = await index?.value
        // Not had this time: asked for again with the next title.
        if list == nil { index = nil }
        var info = TMDB.Info()
        info.logo = list?.logo(imdb: post.imdbID, lang: lang)
        known[post.id] = info
        trailerSettled.insert(post.id)
        return info
    }

    /// The list of logos, kept on disk and fetched again once a day; an old copy serves when GitHub does not answer.
    private static func loadIndex() async -> LogoIndex? {
        let file = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("30nama-logos.json")
        let saved = try? Data(contentsOf: file)
        let age = ((try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate).map { -$0.timeIntervalSinceNow }
        if let saved, let age, age < 24 * 3600, let index = (try? JSON.parse(saved)).flatMap(LogoIndex.init) { return index }
        var request = URLRequest(url: LogoIndex.address)
        request.timeoutInterval = 20
        if let (data, response) = try? await URLSession.shared.data(for: request), (response as? HTTPURLResponse)?.statusCode == 200,
           let index = (try? JSON.parse(data)).flatMap(LogoIndex.init) {
            try? data.write(to: file, options: .atomic)
            return index
        }
        return saved.flatMap { try? JSON.parse($0) }.flatMap(LogoIndex.init)
    }

    /// Without a key, the logo and the trailer, read from the site's public pages; the rest needs the API.
    private func publicLook(for post: Post, lang: Lang) async -> TMDB.Info? {
        guard let imdb = post.imdbID else { return nil }
        // The redirect lands on the title's own page; that address is all that is wanted of it.
        var request = URLRequest(url: TMDB.publicPage(imdbID: imdb))
        request.setValue(TMDBStore.agent, forHTTPHeaderField: "User-Agent")
        request.httpMethod = "HEAD"
        request.timeoutInterval = 15
        guard Date() >= quietUntil else { return nil }
        guard let (_, response) = try? await URLSession.shared.data(for: request), let page = response.url else {
            quietUntil = Date().addingTimeInterval(60)
            return nil
        }
        var info = TMDB.Info()
        for language in (lang == .fa ? ["fa", "en"] : ["en"]) {
            guard let list = TMDB.logosPage(title: page, language: language),
                  let html = await publicPage(list) else { break }
            if let logo = TMDB.logo(inPage: html) {
                info.logo = logo
                break
            }
        }
        // The logo is returned at once; the trailer follows into `known` without holding it up.
        known[post.id] = info
        Task {
            if let list = TMDB.trailersPage(title: page), let html = await publicPage(list), let trailer = TMDB.trailer(inPage: html) {
                known[post.id]?.trailer = trailer
            }
            trailerSettled.insert(post.id)
        }
        return info
    }

    private func publicPage(_ url: URL) async -> String? {
        var request = URLRequest(url: url)
        request.setValue(TMDBStore.agent, forHTTPHeaderField: "User-Agent")
        // As the API's requests: a page that does not come is let go, not waited on for a minute.
        request.timeoutInterval = 15
        guard let data = await answer(request) else { return nil }
        return String(decoding: data, as: UTF8.self)
    }

    /// Until when TMDB is not asked: after it failed to answer, a minute (as the Android and Windows
    /// apps and 30namaTV do), so a home page of forty titles does not wait out forty timeouts on a
    /// line where it is filtered.
    @ObservationIgnored private var quietUntil = Date.distantPast

    /// The body of a 200 answer, or nil. No answer at all, a refused key, too many requests or a
    /// server fault quiets TMDB for a minute.
    private func answer(_ request: URLRequest) async -> Data? {
        guard Date() >= quietUntil else { return nil }
        guard let (data, response) = try? await URLSession.shared.data(for: request), let http = response as? HTTPURLResponse else {
            quietUntil = Date().addingTimeInterval(60)
            return nil
        }
        if http.statusCode == 401 || http.statusCode == 429 || http.statusCode >= 500 { quietUntil = Date().addingTimeInterval(60) }
        return http.statusCode == 200 ? data : nil
    }

    // MARK: Answers kept on disk

    private enum Stored { case found(JSON), missed }

    private static let folder: URL = {
        let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("30nama-tmdb", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()

    /// The answer kept on disk while fresh: a week for one found, a day for a miss.
    private static func stored(_ post: Post) -> Stored? {
        let file = folder.appendingPathComponent("\(post.id).json")
        guard let data = try? Data(contentsOf: file),
              let written = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate else { return nil }
        let age = -written.timeIntervalSinceNow
        if data.isEmpty { return age < 24 * 3600 ? .missed : nil }
        guard age < 7 * 24 * 3600, let details = try? JSON.parse(data) else { return nil }
        return .found(details)
    }

    private static func store(_ post: Post, _ details: JSON?) {
        try? (details?.data() ?? Data()).write(to: folder.appendingPathComponent("\(post.id).json"), options: .atomic)
    }

    private static let agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

    private func forget() {
        known = [:]
        running = [:]
        trailerSettled = []
    }

    private func json(_ request: URLRequest) async -> JSON? {
        await answer(request).flatMap { try? JSON.parse($0) }
    }
}
