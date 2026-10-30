import Foundation

/// Where a still for an episode's tile is taken from the episode itself (30nama has none): a
/// quarter of the way to the credits, or five minutes in at most; and, when that frame is nearly
/// black (a fade, a title card), a second one further in. As EpisodeStills.kt in the Android app
/// and EpisodeStill.cs in the Windows app.
enum EpisodeStill {
    /// A frame darker than this, on average, is passed over for the second point.
    static let dark = 24

    /// The first point and the one tried when it is dark, in seconds.
    static func points(credits: Double?) -> (first: Double, second: Double) {
        let length = (credits ?? 0) > 0 ? credits! : 1200
        return (min(length * 0.25, 300), length * 0.4)
    }

    /// Mean brightness, 0 to 255, of BGRA (or BGR0) pixels, from every eighth pixel.
    static func luma(_ bgra: [UInt8]) -> Int {
        var sum = 0, count = 0
        var i = 0
        while i + 3 < bgra.count {
            sum += (114 * Int(bgra[i]) + 587 * Int(bgra[i + 1]) + 299 * Int(bgra[i + 2])) / 1000
            count += 1
            i += 32
        }
        return count == 0 ? 0 : sum / count
    }
}
