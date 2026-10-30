import Foundation
import CoreGraphics
import Observation

/// The picture at the point under the pointer on the timeline (the Android and Windows apps').
/// 30nama has no thumbnail strips, so frames come from a second mpv of its own (`FrameReader`)
/// reading the stream's lowest quality, on a thread of its own. Learnt from 30namaTV, by way of the
/// Android app:
///  - points in steps of five seconds, one request at a time, the newest point replacing any waiting;
///  - a file that fails is opened again on the next point; three misses in a row and previews are off for that file.
@MainActor
@Observable
final class SeekPreview {
    static let step: Double = 5
    private static let kept = 40

    /// The frames taken so far, by point. Read by the timeline.
    private(set) var frames: [Double: CGImage] = [:]

    @ObservationIgnored private let state = Shared()
    @ObservationIgnored private var thread: Thread?

    /// What the main thread and the reader's thread both touch, under one lock.
    private final class Shared: @unchecked Sendable {
        let lock = NSLock()
        let signal = DispatchSemaphore(value: 0)
        var url: URL?
        var generation = 0
        var wanted: Double?
        var misses = 0
        var closed = false
    }

    /// The point a time falls on: frames are taken every `step` seconds.
    static func point(of seconds: Double) -> Double { max(0, (seconds / step).rounded() * step) }

    /// The stream the frames are taken from; nil for none. What was taken from the one before is let go.
    func open(_ url: URL?) {
        let changed = state.lock.withLock { () -> Bool in
            guard url != state.url else { return false }
            state.url = url
            state.generation += 1
            state.wanted = nil
            state.misses = 0
            return true
        }
        guard changed else { return }
        frames = [:]
        order = []
        if url != nil, thread == nil { start() }
    }

    @ObservationIgnored private var order: [Double] = []

    /// The frame for `seconds` if it has been taken; otherwise asked for, and `frames` gets it when it comes.
    func ask(_ seconds: Double) -> CGImage? {
        let point = SeekPreview.point(of: seconds)
        if let frame = frames[point] { return frame }
        let asked = state.lock.withLock { () -> Bool in
            guard !state.closed, state.url != nil, state.misses < 3 else { return false }
            state.wanted = point
            return true
        }
        if asked { state.signal.signal() }
        return nil
    }

    func close() {
        state.lock.withLock { state.closed = true }
        state.signal.signal()
    }

    private func start() {
        let state = self.state
        let thread = Thread { [weak self] in
            let reader = FrameReader { state.lock.withLock { state.closed } }
            defer { reader.close() }
            while true {
                state.signal.wait()
                while true {
                    let job = state.lock.withLock { () -> (URL, Int, Double)?? in
                        if state.closed { return .some(nil) }
                        guard let wanted = state.wanted, let url = state.url else { return nil }
                        return (url, state.generation, wanted)
                    }
                    guard let job else { break }
                    guard let (url, generation, point) = job else { return }
                    let frame = reader.take(url, at: point)
                    let keep = state.lock.withLock { () -> Bool in
                        guard generation == state.generation else { return false }
                        if state.wanted == point { state.wanted = nil }
                        state.misses = frame == nil ? state.misses + 1 : 0
                        return frame != nil
                    }
                    guard keep, let frame else { continue }
                    DispatchQueue.main.async {
                        guard let self, generation == state.lock.withLock({ state.generation }) else { return }
                        self.frames[point] = frame
                        self.order.append(point)
                        while self.order.count > SeekPreview.kept { self.frames[self.order.removeFirst()] = nil }
                    }
                }
            }
        }
        thread.name = "seek preview"
        thread.qualityOfService = .utility
        self.thread = thread
        thread.start()
    }
}
