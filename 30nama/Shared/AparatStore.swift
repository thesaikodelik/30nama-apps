import Foundation

/// Trailers found on Aparat, for titles TMDB gave none for: in Iran, where TMDB and YouTube are
/// filtered, that is every title. Each title is looked up once a session.
@MainActor
@Observable
final class AparatStore {
    static let shared = AparatStore()

    /// What was found, by 30nama post id.
    private(set) var found: [Int: Aparat.Video] = [:]
    @ObservationIgnored private var asked: Set<Int> = []

    /// Looks for the title's trailer, if it has not been looked for already.
    func look(for post: Post) async {
        guard !asked.contains(post.id) else { return }
        asked.insert(post.id)
        let title = post.titleEnglish ?? post.title(.en)
        guard let answer = await json(Aparat.searchRequest(title: title)) else {
            // No answer: tried again next time the title is opened.
            asked.remove(post.id)
            return
        }
        found[post.id] = Aparat.trailer(in: Aparat.videos(inSearch: answer), title: title, year: post.year)
    }

    /// The file to play. Asked for when it is played: Aparat's addresses are signed and do not last.
    func file(uid: String) async -> URL? {
        await json(Aparat.videoRequest(uid: uid)).flatMap(Aparat.file(inVideo:))
    }

    private func json(_ request: URLRequest) async -> JSON? {
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return try? JSON.parse(data)
    }
}
