import Foundation
import WebKit

/// What the app keeps on disk to save asking again: pictures, the trailers' web pages, TMDB's
/// answers and translated subtitles. Its size is shown in Settings with a button that clears it (from the Android and
/// Windows apps). The saved home page stays, as it is what shows without a connection.
enum StoredCaches {
    private static var folders: [URL] {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return [caches.appendingPathComponent(Bundle.main.bundleIdentifier ?? "app.30nama.tv.mac", isDirectory: true),
                caches.appendingPathComponent("30nama-tmdb", isDirectory: true),
                caches.appendingPathComponent("30nama-translations", isDirectory: true)]
    }

    /// In bytes. Walks the folders, so it is asked for off the main thread.
    static func size() async -> Int64 {
        await Task.detached(priority: .utility) {
            var total: Int64 = 0
            for folder in folders {
                guard let walk = FileManager.default.enumerator(at: folder, includingPropertiesForKeys: [.totalFileAllocatedSizeKey]) else { continue }
                for case let file as URL in walk {
                    total += Int64((try? file.resourceValues(forKeys: [.totalFileAllocatedSizeKey]))?.totalFileAllocatedSize ?? 0)
                }
            }
            return total
        }.value
    }

    @MainActor
    static func clear() async {
        ImagePipeline.shared.clear()
        await WKWebsiteDataStore.default().removeData(ofTypes: [WKWebsiteDataTypeDiskCache, WKWebsiteDataTypeMemoryCache], modifiedSince: .distantPast)
        for folder in folders.dropFirst() {
            for file in (try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)) ?? [] {
                try? FileManager.default.removeItem(at: file)
            }
        }
    }

    /// Settings › Data saver: for metered internet or a capped VPN, nothing is fetched that is
    /// only there to look at (from the Android and Windows apps).
    static var dataSaver: Bool { UserDefaults.standard.bool(forKey: dataSaverKey) }
    static let dataSaverKey = "dataSaver"
}
