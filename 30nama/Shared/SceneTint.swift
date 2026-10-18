import SwiftUI

/// The colour a title's page is lit in, taken from its wide scene picture when that has a lively
/// colour in it, and from its poster otherwise. A poster is often dark or grey where the scene
/// behind the page is full of colour, and the page should glow with what is on screen.
@MainActor
final class SceneTint {
    static let shared = SceneTint()

    /// What each title's scene gave, nil where it had no colour worth taking.
    private var known: [Int: Color?] = [:]

    /// The colour for this title if it has been worked out already, else the poster's.
    func known(for post: Post) -> Color {
        (known[post.id] ?? nil) ?? post.tint
    }

    /// The colour for this title, worked out once and remembered. Asks for the same picture, at
    /// the same size, as the backdrop does, so it is usually already in memory.
    func colour(for post: Post) async -> Color {
        if let found = known[post.id] { return found ?? post.tint }
        var found: Color?
        if let url = post.cover, let image = await ImagePipeline.shared.image(url, maxPixel: 1920) {
            found = SceneTint.lively(image)
        }
        known[post.id] = found
        return found ?? post.tint
    }

    /// What each title's poster gave, nil where it had no colour worth taking.
    private var posters: [Int: Color?] = [:]

    /// The poster's colour, if it has been worked out already.
    func knownPoster(for post: Post) -> Color? { posters[post.id] ?? nil }

    /// The colour of the title's own poster, for the glow behind a card that shows it: taken
    /// from the picture the card has in memory. The blur code's colour will not do, as the
    /// server sends the same few codes for many posters.
    func posterColour(for post: Post, url: URL?, maxPixel: Int) async -> Color {
        if let found = posters[post.id] { return found ?? post.tint }
        var found: Color?
        if let url, let image = await ImagePipeline.shared.image(url, maxPixel: maxPixel) {
            found = SceneTint.lively(image)
        }
        posters[post.id] = found
        return found ?? post.tint
    }

    /// The liveliest colour in a picture: the hue that most of its bright, saturated parts share,
    /// made as vivid as the app's other lights. Nil for a picture that is all greys and shadow.
    private static func lively(_ image: PlatformImage) -> Color? {
        guard let picture = image.picture else { return nil }
        let width = 32, height = 18
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8,
                                          bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.interpolationQuality = .medium
            context.draw(picture, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return nil }
        // Twelve bins of hue, each weighted by how strong and bright its pixels are.
        var weight = [Double](repeating: 0, count: 12)
        var sum = [(Double, Double, Double)](repeating: (0, 0, 0), count: 12)
        for index in stride(from: 0, to: pixels.count, by: 4) {
            let r = Double(pixels[index]) / 255, g = Double(pixels[index + 1]) / 255, b = Double(pixels[index + 2]) / 255
            let high = max(r, g, b), low = min(r, g, b), span = high - low
            guard high > 0.25, span / high > 0.3 else { continue }
            var hue: Double
            if high == r { hue = ((g - b) / span).truncatingRemainder(dividingBy: 6) }
            else if high == g { hue = (b - r) / span + 2 }
            else { hue = (r - g) / span + 4 }
            hue /= 6
            if hue < 0 { hue += 1 }
            let bin = min(11, Int(hue * 12))
            let strength = (span / high) * high
            weight[bin] += strength
            sum[bin].0 += r * strength; sum[bin].1 += g * strength; sum[bin].2 += b * strength
        }
        guard let best = weight.indices.max(by: { weight[$0] < weight[$1] }), weight[best] > 1.2 else { return nil }
        let (r, g, b) = (sum[best].0 / weight[best], sum[best].1 / weight[best], sum[best].2 / weight[best])
        let high = max(r, g, b), low = min(r, g, b), span = high - low
        var hue: Double
        if high == r { hue = ((g - b) / max(span, 0.0001)).truncatingRemainder(dividingBy: 6) }
        else if high == g { hue = (b - r) / max(span, 0.0001) + 2 }
        else { hue = (r - g) / max(span, 0.0001) + 4 }
        hue /= 6
        if hue < 0 { hue += 1 }
        let saturation = high > 0 ? span / high : 0
        return Color(hue: hue, saturation: min(0.95, max(0.6, saturation * 1.4)), brightness: min(1, max(0.85, high * 1.5)))
    }
}
