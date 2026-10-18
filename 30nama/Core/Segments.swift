import Foundation

/// A stretch of a file: bytes `start` to `end`, of which those before `pos` have arrived.
struct SegmentState: Codable, Equatable {
    var start: Int64
    var pos: Int64
    var end: Int64

    var remaining: Int64 { max(0, end - pos) }
    var downloaded: Int64 { pos - start }
}

/// One file fetched over several connections at once. It starts as one segment covering the
/// whole file; whenever a connection is free it halves the segment with the most bytes left and
/// takes the second half, and the connection that had it stops at the new boundary. No
/// connection idles while another still has a long tail, and a slow one loses work to fast ones.
/// Ported from the Tachyon download manager's segment.rs (MIT), by way of the Windows app.
/// Safe to use from several threads.
final class SegmentMap: @unchecked Sendable {
    /// Split points are aligned to this many bytes when possible.
    private static let align: Int64 = 64 * 1024

    final class Segment: @unchecked Sendable {
        private let lock = NSLock()
        private var _pos: Int64
        private var _end: Int64
        fileprivate var owned = false
        let start: Int64

        fileprivate init(_ state: SegmentState, owned: Bool = false) {
            start = state.start
            _pos = state.pos
            _end = state.end
            self.owned = owned
        }

        var state: SegmentState { lock.withLock { SegmentState(start: start, pos: _pos, end: _end) } }
        var pos: Int64 { lock.withLock { _pos } }
        var end: Int64 { lock.withLock { _end } }
        var isComplete: Bool { lock.withLock { _pos >= _end } }

        /// Writes as much of `data` as fits before the segment's end, at its position, and returns
        /// how much that was. Held under the lock, so a split can never move the end below bytes
        /// being written.
        func write(_ data: Data, with put: (Int64, Data) throws -> Void) rethrows -> Int {
            try lock.withLock {
                let n = Int(min(Int64(data.count), max(0, _end - _pos)))
                if n > 0 {
                    try put(_pos, n == data.count ? data : data.prefix(n))
                    _pos += Int64(n)
                }
                return n
            }
        }

        fileprivate func splitOff(minimum: Int64) -> Segment? {
            lock.withLock {
                let remaining = _end - _pos
                guard remaining >= 2 * minimum else { return nil }
                var middle = _pos + remaining / 2
                let aligned = middle - middle % SegmentMap.align
                if aligned >= _pos + minimum { middle = aligned }
                let second = Segment(SegmentState(start: middle, pos: middle, end: _end), owned: true)
                _end = middle
                return second
            }
        }
    }

    private let lock = NSLock()
    private var segments: [Segment]
    private let minimum: Int64

    /// Whether the file can be fetched in parts: the server answers ranges and its size is known.
    let splittable: Bool

    init(_ states: [SegmentState], splittable: Bool, minimum: Int64 = 1024 * 1024) {
        segments = states.sorted { $0.start < $1.start }.map { Segment($0) }
        self.splittable = splittable
        self.minimum = max(1, minimum)
    }

    /// One segment for a whole file of `size` bytes, of which the first `have` are here.
    static func whole(_ size: Int64, have: Int64 = 0) -> SegmentMap {
        SegmentMap([SegmentState(start: 0, pos: min(have, size), end: size)], splittable: true)
    }

    func snapshot() -> [SegmentState] { lock.withLock { segments.map(\.state) } }

    var downloaded: Int64 { lock.withLock { segments.reduce(0) { $0 + $1.state.downloaded } } }

    var isComplete: Bool { lock.withLock { segments.allSatisfy(\.isComplete) } }

    /// The first unfinished segment no connection has: left from a last run, or one a connection gave up.
    func takeUnowned() -> Segment? {
        lock.withLock {
            guard let free = segments.first(where: { !$0.owned && !$0.isComplete }) else { return nil }
            free.owned = true
            return free
        }
    }

    /// Lets go of a segment, for another connection to take up.
    func release(_ segment: Segment) {
        lock.withLock { segment.owned = false }
    }

    /// Halves the segment with the most left and returns the second half, already taken.
    func splitLargest() -> Segment? {
        guard splittable else { return nil }
        return lock.withLock {
            // On a tie the earliest, so the file fills from the front.
            var victim: Segment?
            var most: Int64 = 2 * minimum - 1
            for segment in segments {
                let left = segment.state.remaining
                if left > most {
                    most = left
                    victim = segment
                }
            }
            guard let second = victim?.splitOff(minimum: minimum) else { return nil }
            let at = segments.firstIndex { $0.start > second.start } ?? segments.count
            segments.insert(second, at: at)
            return second
        }
    }

    /// Work for a free connection: what is left over first, then a fresh split.
    func nextWork() -> Segment? { takeUnowned() ?? splitLargest() }
}
