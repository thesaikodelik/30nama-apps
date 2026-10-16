import Foundation
import Compression

/// Reads the files out of a zip archive held in memory. Subtitle sites hand out archives, and the
/// Apple TV has no unzip tool to call, so the few hundred lines of format a subtitle needs are read
/// here: stored and deflated entries, through the central directory.
enum Zip {
    /// Name and contents of every file in `data`. Folders are left out, and so is anything that
    /// cannot be read; names keep only their last part, so nothing points outside where it is put.
    static func files(in data: Data) -> [(name: String, data: Data)] {
        let bytes = [UInt8](data)
        guard let end = endRecord(bytes) else { return [] }
        let count = Int(u16(bytes, end + 10))
        var at = Int(u32(bytes, end + 16))
        var out: [(String, Data)] = []
        for _ in 0..<count {
            guard at + 46 <= bytes.count, u32(bytes, at) == 0x0201_4B50 else { break }
            let method = u16(bytes, at + 10)
            let packed = Int(u32(bytes, at + 20)), size = Int(u32(bytes, at + 24))
            let nameLength = Int(u16(bytes, at + 28)), extra = Int(u16(bytes, at + 30)), comment = Int(u16(bytes, at + 32))
            let local = Int(u32(bytes, at + 42))
            let nameEnd = at + 46 + nameLength
            guard nameEnd <= bytes.count else { break }
            let name = String(decoding: bytes[(at + 46)..<nameEnd], as: UTF8.self)
            at = nameEnd + extra + comment
            guard !name.hasSuffix("/"), local + 30 <= bytes.count, u32(bytes, local) == 0x0403_4B50 else { continue }
            let start = local + 30 + Int(u16(bytes, local + 26)) + Int(u16(bytes, local + 28))
            guard start + packed <= bytes.count else { continue }
            let body = Data(bytes[start..<(start + packed)])
            let file: Data?
            switch method {
            case 0: file = body
            case 8: file = inflate(body, size: size)
            default: file = nil
            }
            if let file { out.append(((name as NSString).lastPathComponent, file)) }
        }
        return out
    }

    private static func endRecord(_ b: [UInt8]) -> Int? {
        guard b.count >= 22 else { return nil }
        // The record ends the file, followed only by a comment of up to 64 KB.
        var i = b.count - 22
        let stop = max(0, b.count - 22 - 65_535)
        while i >= stop {
            if u32(b, i) == 0x0605_4B50 { return i }
            i -= 1
        }
        return nil
    }

    /// Raw deflate, which is what Compression's zlib setting reads.
    private static func inflate(_ data: Data, size: Int) -> Data? {
        guard size > 0 else { return Data() }
        var out = Data(count: size)
        let written = out.withUnsafeMutableBytes { target in
            data.withUnsafeBytes { source in
                compression_decode_buffer(target.bindMemory(to: UInt8.self).baseAddress!, size,
                                          source.bindMemory(to: UInt8.self).baseAddress!, data.count,
                                          nil, COMPRESSION_ZLIB)
            }
        }
        return written == size ? out : nil
    }

    private static func u16(_ b: [UInt8], _ i: Int) -> UInt16 { UInt16(b[i]) | UInt16(b[i + 1]) << 8 }
    private static func u32(_ b: [UInt8], _ i: Int) -> UInt32 {
        UInt32(b[i]) | UInt32(b[i + 1]) << 8 | UInt32(b[i + 2]) << 16 | UInt32(b[i + 3]) << 24
    }
}
