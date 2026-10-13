import SwiftUI

// The two platforms name their image, colour and screen types differently. Shared code uses these.
#if os(macOS)
import AppKit

typealias PlatformImage = NSImage
typealias PlatformColor = NSColor

extension NSImage {
    convenience init(cgImage: CGImage) {
        self.init(cgImage: cgImage, size: NSSize(width: cgImage.width, height: cgImage.height))
    }
}

extension Image {
    init(platformImage: PlatformImage) { self.init(nsImage: platformImage) }
}

enum Display {
    @MainActor static var scale: CGFloat { NSScreen.main?.backingScaleFactor ?? 2 }
}
#else
import UIKit

typealias PlatformImage = UIImage
typealias PlatformColor = UIColor

extension Image {
    init(platformImage: PlatformImage) { self.init(uiImage: platformImage) }
}

enum Display {
    @MainActor static var scale: CGFloat { UIScreen.main.scale }
}
#endif
