import Foundation
import CoreGraphics
import Libmpv

/// A second mpv of its own, with no picture or sound out, that hands back small pictures from a
/// stream: it seeks to the keyframe nearest a point (no decoding up to the exact frame) and gives a
/// 320-pixel copy of it. Software decoding only, so the film's own decoder is left alone. Not
/// thread-safe: one thread asks it everything and waits for each answer there; nothing of it is
/// asked from the main thread (AGENTS.md). As FrameReader.cs of the Windows app. Used by
/// `SeekPreview` and `EpisodeStills`.
final class FrameReader {
    private var handle: OpaquePointer?
    private var loaded: URL?
    /// Asked while waiting: true lets a wait end early, as the owner is going away.
    private let stopped: () -> Bool

    init(stopped: @escaping () -> Bool) { self.stopped = stopped }

    deinit { close() }

    func close() {
        if let handle { mpv_terminate_destroy(handle) }
        handle = nil
        loaded = nil
    }

    /// The picture nearest `seconds` into `url`; nil when it could not be had.
    func take(_ url: URL, at seconds: Double) -> CGImage? {
        guard ready(), load(url) else { return nil }
        // What is left from before (the restart after loading, a seek given up on) is not this seek's answer.
        drain()
        guard run(["seek", String(format: "%.3f", seconds), "absolute+keyframes"]), wait(for: MPV_EVENT_PLAYBACK_RESTART, seconds: 8) else {
            // Perhaps the connection: the file is opened again next time.
            loaded = nil
            return nil
        }
        return picture()
    }

    private func ready() -> Bool {
        if handle != nil { return true }
        guard let created = mpv_create() else { return false }
        for (name, value) in [
            // No window, no sound: it only reads, and is asked for its picture.
            ("vo", "null"), ("ao", "null"), ("audio", "no"), ("sid", "no"), ("sub-auto", "no"),
            ("ytdl", "no"), ("cover-art-auto", "no"), ("audio-file-auto", "no"),
            ("input-default-bindings", "no"), ("terminal", "no"), ("idle", "yes"), ("pause", "yes"),
            // In software, and light: the film's own decoder is left alone, and a frame need not be exact.
            ("hwdec", "no"), ("vd-lavc-threads", "2"), ("vd-lavc-skiploopfilter", "all"), ("hr-seek", "no"),
            ("vf", "scale=w=320:h=-2"), ("hls-bitrate", "min"),
            ("demuxer-max-bytes", "8MiB"), ("demuxer-readahead-secs", "1"),
        ] {
            mpv_set_option_string(created, name, value)
        }
        guard mpv_initialize(created) >= 0 else {
            mpv_terminate_destroy(created)
            return false
        }
        handle = created
        return true
    }

    private func load(_ url: URL) -> Bool {
        if loaded == url { return true }
        loaded = nil
        // Loaded, and its first picture decoded: that first restart, come late, would otherwise be
        // taken for the answer to the first seek. The file before it ends first; that end is not
        // this one's, so it is waited past to the start.
        guard run(["loadfile", url.isFileURL ? url.path : url.absoluteString]),
              wait(for: MPV_EVENT_START_FILE, seconds: 15, endsWait: false),
              wait(for: MPV_EVENT_FILE_LOADED, seconds: 15),
              wait(for: MPV_EVENT_PLAYBACK_RESTART, seconds: 15) else { return false }
        loaded = url
        return true
    }

    private func drain() {
        guard let handle else { return }
        while let event = mpv_wait_event(handle, 0)?.pointee, event.event_id != MPV_EVENT_NONE {}
    }

    /// Waits on this thread for `wanted`; false at an end of file (when `endsWait`) or after `seconds`.
    private func wait(for wanted: mpv_event_id, seconds: Double, endsWait: Bool = true) -> Bool {
        guard let handle else { return false }
        let until = Date().addingTimeInterval(seconds)
        while Date() < until {
            guard let event = mpv_wait_event(handle, 0.5)?.pointee else { continue }
            if event.event_id == wanted { return true }
            if event.event_id == MPV_EVENT_SHUTDOWN || (event.event_id == MPV_EVENT_END_FILE && endsWait) { return false }
            if stopped() { return false }
        }
        return false
    }

    /// Runs a command and waits for it, on this thread.
    private func run(_ words: [String]) -> Bool {
        guard let handle else { return false }
        var pointers: [UnsafePointer<CChar>?] = words.map { UnsafePointer(strdup($0)) }
        pointers.append(nil)
        defer { for pointer in pointers { free(UnsafeMutablePointer(mutating: pointer)) } }
        return mpv_command(handle, &pointers) >= 0
    }

    private func picture() -> CGImage? {
        guard let handle else { return nil }
        var pointers: [UnsafePointer<CChar>?] = ["screenshot-raw", "video"].map { (word: String) in UnsafePointer(strdup(word)) }
        pointers.append(nil)
        defer { for pointer in pointers { free(UnsafeMutablePointer(mutating: pointer)) } }
        var result = mpv_node()
        guard mpv_command_ret(handle, &pointers, &result) >= 0 else { return nil }
        defer { mpv_free_node_contents(&result) }
        guard result.format == MPV_FORMAT_NODE_MAP, let list = result.u.list?.pointee else { return nil }
        var width = 0, height = 0, stride = 0
        var bytes: mpv_byte_array?
        for i in 0..<Int(list.num) {
            guard let keyPointer = list.keys?[i] else { continue }
            let node = list.values[i]
            switch String(cString: keyPointer) {
            case "w": width = Int(node.u.int64)
            case "h": height = Int(node.u.int64)
            case "stride": stride = Int(node.u.int64)
            case "data" where node.format == MPV_FORMAT_BYTE_ARRAY: bytes = node.u.ba?.pointee
            default: break
            }
        }
        guard let bytes, let data = bytes.data, width > 0, height > 0, stride >= width * 4, bytes.size >= stride * height else { return nil }
        // bgr0: the fourth byte is not alpha, so it is skipped.
        let copy = Data(bytes: data, count: stride * height)
        guard let provider = CGDataProvider(data: copy as CFData) else { return nil }
        let info = CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: stride,
                       space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: info, provider: provider, decode: nil,
                       shouldInterpolate: true, intent: .defaultIntent)
    }
}
