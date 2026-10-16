import Foundation

/// Titles the user took off "continue watching", and where they had got to when they did.
///
/// The server is asked to forget them too, but goes on listing them for a while from its cache,
/// so the app hides them itself. A title comes back once it is watched again: its position then
/// moves on from the one it was dismissed at.
struct ContinueDismissals: Equatable {
    private(set) var positions: [Int: Double] = [:]

    init() {}

    /// From what `plist` gave back. Keys are post ids written as text, as property lists need.
    init(plist: [String: Double]) {
        for (key, time) in plist { if let id = Int(key) { positions[id] = time } }
    }

    var plist: [String: Double] {
        Dictionary(uniqueKeysWithValues: positions.map { (String($0.key), $0.value) })
    }

    mutating func dismiss(_ post: Post) {
        positions[post.id] = post.resume?.time ?? 0
    }

    /// The list without what was dismissed and not watched since. Entries for titles no longer on
    /// the list are dropped, so the record stays as long as the list at most.
    mutating func filter(_ posts: [Post]) -> [Post] {
        let shown = posts.filter { post in
            guard let at = positions[post.id] else { return true }
            return abs((post.resume?.time ?? 0) - at) > 1
        }
        let listed = Set(posts.map(\.id))
        let shownIDs = Set(shown.map(\.id))
        positions = positions.filter { listed.contains($0.key) && !shownIDs.contains($0.key) }
        return shown
    }
}
