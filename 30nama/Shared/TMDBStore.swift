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
    @ObservationIgnored private var asked: Set<Int> = []
    @ObservationIgnored private var usedKey: String?

    /// What TMDB has for `post`, fetched the first time it is asked for.
    func info(for post: Post, lang: Lang) async -> TMDB.Info? {
        // A new key, or none: what was looked up with the old one may not hold.
        if TMDBStore.key != usedKey {
            usedKey = TMDBStore.key
            forget()
        }
        if let info = known[post.id] { return info }
        guard !asked.contains(post.id) else { return nil }
        guard let key = TMDBStore.key else { return await publicLogo(for: post, lang: lang) }
        asked.insert(post.id)
        var id: Int?
        if let imdb = post.imdbID, let found = await json(TMDB.findRequest(imdbID: imdb, key: key)) {
            id = TMDB.id(inFind: found, isSeries: post.isSeries)
        }
        if id == nil, let found = await json(TMDB.searchRequest(title: post.titleEnglish ?? post.title(.en), year: post.year, isSeries: post.isSeries, key: key)) {
            id = TMDB.id(inSearch: found)
        }
        guard let id, let details = await json(TMDB.detailsRequest(id: id, isSeries: post.isSeries, key: key)) else {
            // A failed look is tried again next time the title is opened.
            asked.remove(post.id)
            return nil
        }
        let info = TMDB.info(in: details, lang: lang)
        known[post.id] = info
        return info
    }

    /// Without a key, the logo alone, read from the site's public pages; the rest needs the API.
    private func publicLogo(for post: Post, lang: Lang) async -> TMDB.Info? {
        guard let imdb = post.imdbID else { return nil }
        asked.insert(post.id)
        // The redirect lands on the title's own page; that address is all that is wanted of it.
        var request = URLRequest(url: TMDB.publicPage(imdbID: imdb))
        request.setValue(TMDBStore.agent, forHTTPHeaderField: "User-Agent")
        request.httpMethod = "HEAD"
        guard let (_, response) = try? await URLSession.shared.data(for: request), let page = response.url else {
            asked.remove(post.id)
            return nil
        }
        for language in (lang == .fa ? ["fa", "en"] : ["en"]) {
            guard let list = TMDB.logosPage(title: page, language: language) else { break }
            var get = URLRequest(url: list)
            get.setValue(TMDBStore.agent, forHTTPHeaderField: "User-Agent")
            if let (data, _) = try? await URLSession.shared.data(for: get),
               let logo = TMDB.logo(inPage: String(decoding: data, as: UTF8.self)) {
                let info = TMDB.Info(logo: logo)
                known[post.id] = info
                return info
            }
        }
        known[post.id] = TMDB.Info()
        return nil
    }

    private static let agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

    private func forget() {
        known = [:]
        asked = []
    }

    private func json(_ request: URLRequest) async -> JSON? {
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try? JSON.parse(data)
    }
}
