import Foundation

/// The TMDB logos of 30nama's titles, by IMDb id, as a daily workflow writes them to the
/// repository's `data` branch (Tools/logos/build_logos.py). For where TMDB is filtered: GitHub is
/// not, and the pictures themselves come through an image proxy that is not either.
struct LogoIndex: Sendable {
    static let address = URL(string: "https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/logos.json")!

    /// English (or with no text), then Persian; an empty name where there is none.
    private let logos: [String: [String]]

    init?(_ answer: JSON) {
        guard answer["version"].int == 1 else { return nil }
        var logos: [String: [String]] = [:]
        for (imdb, names) in answer["logos"].object ?? [:] {
            logos[imdb] = names.array.map { $0.text ?? "" }
        }
        self.logos = logos
    }

    var count: Int { logos.count }

    /// The title's logo in the interface's language if there is one, else English, through the proxy.
    func logo(imdb: String?, lang: Lang) -> URL? {
        guard let imdb, let names = logos[imdb] else { return nil }
        let english = names.first ?? "", persian = names.count > 1 ? names[1] : ""
        let name = lang == .fa && !persian.isEmpty ? persian : english
        return name.isEmpty ? nil : LogoIndex.proxied(name)
    }

    /// A TMDB picture by its file name, through wsrv.nl, which fetches it from outside.
    static func proxied(_ name: String) -> URL? {
        URL(string: "https://wsrv.nl/?url=image.tmdb.org/t/p/w500/" + name)
    }
}
