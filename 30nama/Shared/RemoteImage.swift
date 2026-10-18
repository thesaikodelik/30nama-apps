import SwiftUI
import ImageIO

/// Downloads, downsamples and caches artwork.
///
/// Images are decoded off the main thread straight to the size they are shown at, which keeps
/// memory flat on the 1080p Apple TV while rows of posters scroll past.
final class ImagePipeline: @unchecked Sendable {
    static let shared = ImagePipeline()

    private let cache = NSCache<NSString, PlatformImage>()
    private let session: URLSession
    private let lock = NSLock()
    private var inFlight: [String: Task<PlatformImage?, Never>] = [:]

    init() {
        cache.totalCostLimit = 192 * 1024 * 1024
        let c = URLSessionConfiguration.default
        c.urlCache = URLCache(memoryCapacity: 8 * 1024 * 1024, diskCapacity: 400 * 1024 * 1024)
        c.requestCachePolicy = .returnCacheDataElseLoad
        c.timeoutIntervalForRequest = 30
        session = URLSession(configuration: c)
    }

    private func key(_ url: URL, _ maxPixel: Int) -> String { "\(maxPixel)|\(url.absoluteString)" }

    func cached(_ url: URL, maxPixel: Int) -> PlatformImage? {
        cache.object(forKey: key(url, maxPixel) as NSString)
    }

    func image(_ url: URL, maxPixel: Int) async -> PlatformImage? {
        let key = key(url, maxPixel)
        if let hit = cache.object(forKey: key as NSString) { return hit }
        let task = lock.withLock { () -> Task<PlatformImage?, Never> in
            if let running = inFlight[key] { return running }
            let task = Task.detached(priority: .userInitiated) { [session, cache] () -> PlatformImage? in
                guard let (data, response) = try? await session.data(from: url),
                      (response as? HTTPURLResponse)?.statusCode ?? 200 < 400,
                      let image = ImagePipeline.decode(data, maxPixel: maxPixel) else { return nil }
                cache.setObject(image, forKey: key as NSString, cost: Int(image.size.width * image.size.height * 4))
                return image
            }
            inFlight[key] = task
            return task
        }
        let image = await task.value
        lock.withLock { inFlight[key] = nil }
        return image
    }

    static func decode(_ data: Data, maxPixel: Int) -> PlatformImage? {
        let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions) else { return nil }
        let options = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
        ] as CFDictionary
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options).map { PlatformImage(cgImage: $0) }
    }
}

/// BlurHash placeholders, decoded once per hash.
enum BlurPlaceholder {
    private static let cache = NSCache<NSString, PlatformImage>()

    static func image(_ hash: String?) -> PlatformImage? {
        guard let hash else { return nil }
        if let hit = cache.object(forKey: hash as NSString) { return hit }
        let w = 12, h = 18
        guard let pixels = BlurHash.pixels(hash, width: w, height: h),
              let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cg = CGImage(
                  width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: w * 4,
                  space: CGColorSpaceCreateDeviceRGB(),
                  bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.noneSkipLast.rawValue),
                  provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)
        else { return nil }
        let image = PlatformImage(cgImage: cg)
        cache.setObject(image, forKey: hash as NSString)
        return image
    }
}

/// Artwork that fills its frame: BlurHash first, then the real image faded in over it.
struct RemoteImage: View {
    let url: URL?
    var blurhash: String? = nil
    /// Longest side, in pixels, the image is decoded to.
    var maxPixel: Int

    @State private var image: PlatformImage?
    /// The picture is fully in: the blur under it goes. The server sends the same few blur codes
    /// for many posters, so its colour is often not the poster's, and left underneath it showed
    /// through the soft edge of a rounded corner.
    @State private var settled = false

    var body: some View {
        Color.clear
            .overlay {
                if !settled, let placeholder = BlurPlaceholder.image(blurhash) {
                    Image(platformImage: placeholder).resizable().scaledToFill()
                }
            }
            .overlay {
                if let image {
                    Image(platformImage: image).resizable().scaledToFill().transition(.opacity)
                }
            }
            .clipped()
            .task(id: url) {
                guard let url else {
                    image = nil
                    settled = false
                    return
                }
                if let hit = ImagePipeline.shared.cached(url, maxPixel: maxPixel) {
                    image = hit
                    settled = true
                    return
                }
                image = nil
                settled = false
                let loaded = await ImagePipeline.shared.image(url, maxPixel: maxPixel)
                guard !Task.isCancelled else { return }
                withAnimation(.easeOut(duration: 0.24)) { image = loaded }
                guard loaded != nil else { return }
                // After the fade, so the blur is never seen leaving.
                try? await Task.sleep(for: .milliseconds(300))
                guard !Task.isCancelled else { return }
                settled = true
            }
    }
}

extension Poster {
    /// Variant for a poster drawn `width` points wide on this screen (with headroom for the focus scale).
    @MainActor
    func url(forWidth width: CGFloat) -> URL? {
        url(pixelWidth: Double(width * Display.scale))
    }
}
