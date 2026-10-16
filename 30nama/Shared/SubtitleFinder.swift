import Foundation

/// Looks on SubSource for Persian subtitles to a title 30nama gives none for, and fetches them.
/// With a key the user made on SubSource it goes through the site's documented API; without one,
/// or when that fails, through the requests the site's own pages make.
@MainActor
final class SubtitleFinder {
    static let shared = SubtitleFinder()

    /// The user's own key, if they made one. Kept on this device only.
    static var key: String? {
        let value = UserDefaults.standard.string(forKey: "subsourceKey")?.trimmingCharacters(in: .whitespacesAndNewlines)
        return value?.isEmpty == false ? value : nil
    }

    private static let agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

    /// What was found for a title and season, so the next episode does not search again.
    private var found: [String: [SubSource.Candidate]] = [:]
    private var texts: [String: String] = [:]

    /// The subtitles that may fit, best first; empty when there are none.
    func candidates(for query: SubSource.Query, release: String, episode: Int?) async -> [SubSource.Candidate] {
        let cacheKey = "\(SubSource.simple(query.title))|\(query.year ?? 0)|\(query.season ?? 0)"
        let all: [SubSource.Candidate]
        if let known = found[cacheKey] {
            all = known
        } else {
            var list: [SubSource.Candidate] = []
            if let key = SubtitleFinder.key { list = await viaAPI(query, key: key) }
            if list.isEmpty { list = await viaPages(query) }
            found[cacheKey] = list
            all = list
        }
        return SubSource.ranked(all, release: release, season: query.isSeries ? query.season : nil, episode: episode)
    }

    /// The text of one subtitle, taken out of its archive and made readable.
    func text(of candidate: SubSource.Candidate, season: Int?, episode: Int?) async -> String? {
        let cacheKey = "\(candidate.id)|\(season ?? 0)|\(episode ?? 0)"
        if let known = texts[cacheKey] { return known }
        let data: Data?
        switch candidate.source {
        case .api(let id):
            guard let key = SubtitleFinder.key else { return nil }
            data = await fetch(SubSource.downloadRequest(id: id, key: key))
        case .page(let page):
            guard let html = await fetch(page).flatMap({ String(data: $0, encoding: .utf8) }),
                  let address = SubtitleSite.download(in: html) else { return nil }
            data = await fetch(address)
        }
        guard let data else { return nil }
        let text = await Task.detached { SubSource.text(from: data, season: season, episode: episode) }.value
        if let text { texts[cacheKey] = text }
        return text
    }

    // MARK: Two ways in

    private func viaAPI(_ query: SubSource.Query, key: String) async -> [SubSource.Candidate] {
        guard let answer = await json(SubSource.searchRequest(query, key: key)),
              let movie = SubSource.movieID(in: answer, for: query),
              let list = await json(SubSource.subtitlesRequest(movieID: movie, key: key)) else { return [] }
        return SubSource.candidates(inAPI: list)
    }

    private func viaPages(_ query: SubSource.Query) async -> [SubSource.Candidate] {
        var search = URLRequest(url: URL(string: "https://api.subsource.net/v1/movie/search")!)
        search.httpMethod = "POST"
        search.httpBody = SubSource.searchBody(query)
        search.setValue("application/json", forHTTPHeaderField: "Content-Type")
        search.setValue("https://subsource.net", forHTTPHeaderField: "Origin")
        guard let answer = await json(search), let listing = SubSource.listing(in: answer, for: query),
              let html = await fetch(listing).flatMap({ String(data: $0, encoding: .utf8) }) else { return [] }
        return SubtitleSite.entries(in: html).compactMap { entry in
            URL(string: entry.path, relativeTo: SubSource.site).map { .init(source: .page($0.absoluteURL), releases: entry.releases) }
        }
    }

    // MARK: Network

    private func fetch(_ url: URL) async -> Data? {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        return await fetch(request)
    }

    private func fetch(_ request: URLRequest) async -> Data? {
        var request = request
        request.setValue(SubtitleFinder.agent, forHTTPHeaderField: "User-Agent")
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode ?? 0 < 400 else { return nil }
        return data
    }

    private func json(_ request: URLRequest) async -> JSON? {
        guard let data = await fetch(request) else { return nil }
        return try? JSON.parse(data)
    }
}
