import Foundation

/// The days the user's series have their next episode, as last seen, and which of them have come.
///
/// The server tells only the day the next episode airs, and moves on to the one after once it has.
/// So each look remembers the days still ahead, and a remembered day that has passed by the next
/// look is an episode out since. Half a day is allowed after it for the episode to reach 30nama.
struct EpisodeAlerts: Equatable {
    private(set) var expected: [Int: Date] = [:]
    static let grace: TimeInterval = 12 * 3600

    init() {}

    init(plist: [String: Double]) {
        for (key, time) in plist { if let id = Int(key) { expected[id] = Date(timeIntervalSince1970: time) } }
    }

    var plist: [String: Double] {
        Dictionary(uniqueKeysWithValues: expected.map { (String($0.key), $0.value.timeIntervalSince1970) })
    }

    /// Takes in the series followed now and returns those with an episode out since the last look.
    /// A series no longer followed is forgotten, and says nothing.
    mutating func update(with followed: [Post], now: Date = Date()) -> [Post] {
        var byID: [Int: Post] = [:]
        for post in followed where byID[post.id] == nil { byID[post.id] = post }
        var out: [Post] = []
        for (id, day) in expected where day.addingTimeInterval(EpisodeAlerts.grace) <= now {
            if let post = byID[id] { out.append(post) }
            expected[id] = nil
        }
        expected = expected.filter { byID[$0.key] != nil }
        for post in byID.values {
            if let day = post.nextEpisodeDay, day.addingTimeInterval(EpisodeAlerts.grace) > now { expected[post.id] = day }
        }
        return out.sorted { $0.id < $1.id }
    }
}
