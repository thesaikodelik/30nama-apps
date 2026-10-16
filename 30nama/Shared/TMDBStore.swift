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
        let task = Task { await look(post, lang: lang) }
        running[post.id] = task
        let info = await task.value
        running[post.id] = nil
        return info
    }

    /// The trailer for `post`, waiting up to `patience` for a look that is still under way.
    func trailer(for post: Post, lang: Lang, patience: Duration = .seconds(4)) async -> String? {
        _ = await info(for: post, lang: lang)
        let until = ContinuousClock.now + patience
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
        guard let key = TMDBStore.key else { return await publicLook(for: post, lang: lang) }
        var id: Int?
        if let imdb = post.imdbID, let found = await json(TMDB.findRequest(imdbID: imdb, key: key)) {
            id = TMDB.id(inFind: found, isSeries: post.isSeries)
        }
        if id == nil, let found = await json(TMDB.searchRequest(title: post.titleEnglish ?? post.title(.en), year: post.year, isSeries: post.isSeries, key: key)) {
            id = TMDB.id(inSearch: found)
        }
        // A failed look is tried again next time the title is opened.
        guard let id, let details = await json(TMDB.detailsRequest(id: id, isSeries: post.isSeries, key: key)) else { return nil }
        let info = TMDB.info(in: details, lang: lang)
        known[post.id] = info
        trailerSettled.insert(post.id)
        return info
    }

    /// Without a key, the logo and the trailer, read from the site's public pages; the rest needs the API.
    private func publicLook(for post: Post, lang: Lang) async -> TMDB.Info? {
        guard let imdb = post.imdbID else { return nil }
        // The redirect lands on the title's own page; that address is all that is wanted of it.
        var request = URLRequest(url: TMDB.publicPage(imdbID: imdb))
        request.setValue(TMDBStore.agent, forHTTPHeaderField: "User-Agent")
        request.httpMethod = "HEAD"
        guard let (_, response) = try? await URLSession.shared.data(for: request), let page = response.url else { return nil }
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
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return String(decoding: data, as: UTF8.self)
    }

    private static let agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

    private func forget() {
        known = [:]
        running = [:]
        trailerSettled = []
    }

    private func json(_ request: URLRequest) async -> JSON? {
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try? JSON.parse(data)
    }
}
