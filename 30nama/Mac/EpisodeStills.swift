import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
import Observation

/// A still for each episode's tile, taken from the episode itself: 30nama has none. From the lowest
/// quality, with a second mpv of its own (`FrameReader`), one episode at a time on a thread of its
/// own, at the point `EpisodeStill` picks. Kept on the disk as small JPEGs, so each episode costs
/// this once. Only for episodes that stream: a file there is nothing to read. As EpisodeStills.kt
/// in the Android app and EpisodeStills.cs in the Windows app (the owner wants them on the Mac too).
@MainActor
@Observable
final class EpisodeStills {
    static let shared = EpisodeStills()

    /// Goes up as each still is saved, so the tiles look again.
    private(set) var version = 0

    private static let maxBytes = 20 * 1024 * 1024
    static let folder: URL = {
        let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("30nama-frames", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()

    @ObservationIgnored private let state = Queue()
    @ObservationIgnored private var thread: Thread?

    private final class Queue: @unchecked Sendable {
        let lock = NSLock()
        let signal = DispatchSemaphore(value: 0)
        var wanted: [(id: String, url: URL, credits: Double?)] = []
        var failed: Set<String> = []
    }

    /// The saved still for a file id, if there is one.
    nonisolated static func file(_ fileID: String) -> URL? {
        guard !fileID.isEmpty else { return nil }
        let url = folder.appendingPathComponent(fileID + ".jpg")
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    /// The episodes now in sight: those without a still are taken in this order, replacing what was
    /// waiting (another season, another title).
    func want(_ items: [StreamItem]) {
        let wanted = items.compactMap { item -> (String, URL, Double?)? in
            guard !item.fileID.isEmpty, EpisodeStills.file(item.fileID) == nil,
                  let lowest = item.qualities.filter({ !$0.isAuto }).min(by: { $0.height < $1.height }) ?? item.qualities.first else { return nil }
            return (item.fileID, lowest.url, item.creditsStart)
        }
        let any = state.lock.withLock { () -> Bool in
            state.wanted = wanted.filter { !state.failed.contains($0.0) }.map { (id: $0.0, url: $0.1, credits: $0.2) }
            return !state.wanted.isEmpty
        }
        guard any else { return }
        if thread == nil { start() }
        state.signal.signal()
    }

    private func start() {
        let state = self.state
        let thread = Thread { [weak self] in
            let reader = FrameReader { false }
            while true {
                state.signal.wait()
                while true {
                    let next = state.lock.withLock { state.wanted.isEmpty ? nil : state.wanted.removeFirst() }
                    guard let next else { break }
                    if EpisodeStills.file(next.id) != nil { continue }
                    guard let still = EpisodeStills.take(reader, next.url, next.credits), EpisodeStills.save(next.id, still) else {
                        state.lock.withLock { _ = state.failed.insert(next.id) }
                        continue
                    }
                    DispatchQueue.main.async { self?.version += 1 }
                }
            }
        }
        thread.name = "episode stills"
        thread.qualityOfService = .background
        self.thread = thread
        thread.start()
    }

    /// The frame at the first point, or at the second when the first is nearly black and the second is not darker.
    private nonisolated static func take(_ reader: FrameReader, _ url: URL, _ credits: Double?) -> CGImage? {
        let (first, second) = EpisodeStill.points(credits: credits)
        guard let a = reader.take(url, at: first) else { return nil }
        let light = luma(a)
        if light >= EpisodeStill.dark { return a }
        guard let b = reader.take(url, at: second) else { return a }
        return luma(b) > light ? b : a
    }

    private nonisolated static func luma(_ image: CGImage) -> Int {
        guard let data = image.dataProvider?.data as Data? else { return 0 }
        return EpisodeStill.luma([UInt8](data))
    }

    private nonisolated static func save(_ id: String, _ image: CGImage) -> Bool {
        let path = folder.appendingPathComponent(id + ".jpg")
        let part = folder.appendingPathComponent(id + ".jpg.part")
        guard let out = CGImageDestinationCreateWithURL(part as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else { return false }
        CGImageDestinationAddImage(out, image, [kCGImageDestinationLossyCompressionQuality: 0.8] as CFDictionary)
        guard CGImageDestinationFinalize(out) else { return false }
        try? FileManager.default.removeItem(at: path)
        guard (try? FileManager.default.moveItem(at: part, to: path)) != nil else { return false }
        trim()
        return true
    }

    /// The oldest go once the stills pass 20 MB.
    private nonisolated static func trim() {
        let keys: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey]
        guard let files = try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: keys) else { return }
        var entries = files.compactMap { url -> (URL, Int, Date)? in
            guard let values = try? url.resourceValues(forKeys: Set(keys)) else { return nil }
            return (url, values.fileSize ?? 0, values.contentModificationDate ?? .distantPast)
        }.sorted { $0.2 < $1.2 }
        var total = entries.reduce(0) { $0 + $1.1 }
        while total > maxBytes, !entries.isEmpty {
            let oldest = entries.removeFirst()
            try? FileManager.default.removeItem(at: oldest.0)
            total -= oldest.1
        }
    }
}
