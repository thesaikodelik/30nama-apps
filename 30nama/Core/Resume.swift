import Foundation

/// Where to start a title. 30nama's own record lags behind: it keeps positions in steps of thirty
/// seconds, and an episode left part-way can come back as nowhere at all. So this device keeps its
/// own point for each title too, and playback starts from whichever of the two is further on (later
/// in the list of files, then later in the file). As the Android and Windows apps, learnt from 30namaTV.
enum Resume {
    struct Point: Equatable {
        var index: Int
        var seconds: Double?
    }

    /// A point this device kept: the file, and the second in it.
    struct Kept: Equatable {
        var fileID: String
        var seconds: Double
    }

    /// Where to start `items`: `chosen` is the file the user picked (or the server's), `server` what
    /// 30nama remembers and `kept` what this device does. A position inside the last 3% is the
    /// credits: that file starts over. A position in the first five seconds is no position at all.
    static func start(_ items: [StreamItem], chosen: Int, server: ContinueWatching?, kept: Kept?, pickedByUser: Bool) -> Point {
        func position(_ seconds: Double, _ duration: Double) -> Double? {
            seconds > 5 && (duration <= 0 || seconds < duration * 0.97) ? seconds : nil
        }
        func duration(_ item: StreamItem) -> Double { Double(item.durationMinutes ?? 0) * 60 }
        func index(_ fileID: String?) -> Int? { fileID.flatMap { id in items.firstIndex { $0.fileID == id } } }

        // An episode chosen from the list starts there; only its own saved point can apply.
        if pickedByUser {
            guard items.indices.contains(chosen) else { return Point(index: chosen, seconds: nil) }
            let id = items[chosen].fileID
            let fromServer = server.flatMap { $0.fileID == id ? position($0.time, $0.duration) : nil }
            let fromHere = kept.flatMap { $0.fileID == id ? position($0.seconds, duration(items[chosen])) : nil }
            return Point(index: chosen, seconds: [fromServer, fromHere].compactMap { $0 }.max())
        }
        var best: (index: Int, seconds: Double, duration: Double)?
        if let server, let s = index(server.fileID) { best = (s, server.time, server.duration) }
        if let kept, let k = index(kept.fileID), best.map({ k > $0.index || (k == $0.index && kept.seconds > $0.seconds) }) ?? true {
            // The same file as 30nama's point: its length is known from there when the stream's is not.
            let length = server?.fileID == kept.fileID ? max(duration(items[k]), server?.duration ?? 0) : duration(items[k])
            best = (k, kept.seconds, length)
        }
        guard let found = best else { return Point(index: chosen, seconds: nil) }
        // Left in its credits, and another follows: that one is next, not this one again from the start.
        if found.duration > 0, found.seconds >= found.duration * 0.97, found.index < items.count - 1 {
            return Point(index: found.index + 1, seconds: nil)
        }
        return Point(index: found.index, seconds: position(found.seconds, found.duration))
    }
}
