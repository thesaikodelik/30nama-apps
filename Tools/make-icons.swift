// Draws the layered tvOS app icon, the Top Shelf images, the in-app logo and the Mac app icon into the asset catalogs.
// Run from the project root:  swift Tools/make-icons.swift
import AppKit

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let catalog = root.appendingPathComponent("30nama/Resources/Assets.xcassets/App Icon & Top Shelf Image.brandassets")

// 30nama's own logo, cube and wordmark, enlarged from the files on 30nama.com (see THIRD-PARTY-NOTICES.md).
let logo = NSBitmapImageRep(data: try! Data(contentsOf: root.appendingPathComponent("Tools/30nama-logo.png")))!
/// Where the cube ends and the wordmark begins, as a fraction of the logo's width.
let logoSplit: CGFloat = 0.29

let accent = NSColor(srgbRed: 0xF0 / 255, green: 0x45 / 255, blue: 0x4A / 255, alpha: 1)
let bgTop = NSColor(srgbRed: 0x16 / 255, green: 0x19 / 255, blue: 0x22 / 255, alpha: 1)
let bgBottom = NSColor(srgbRed: 0x07 / 255, green: 0x08 / 255, blue: 0x0B / 255, alpha: 1)

enum Layer { case back, middle, front, flat }

func render(_ width: Int, _ height: Int, _ layer: Layer) -> Data {
    let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height, bitsPerSample: 8, samplesPerPixel: 4,
        hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    let ctx = NSGraphicsContext.current!.cgContext
    let w = CGFloat(width), h = CGFloat(height)
    let rect = CGRect(x: 0, y: 0, width: w, height: h)

    if layer == .back || layer == .flat {
        NSGradient(starting: bgBottom, ending: bgTop)!.draw(in: rect, angle: 90)
        // Projector-lamp bloom behind the logo.
        let bloom = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [accent.withAlphaComponent(0.42).cgColor, accent.withAlphaComponent(0).cgColor] as CFArray, locations: [0, 1])!
        ctx.drawRadialGradient(bloom, startCenter: CGPoint(x: w / 2, y: h * 0.5), startRadius: 0, endCenter: CGPoint(x: w / 2, y: h * 0.5), endRadius: h * 0.78, options: [])
    }
    // The logo sits in the middle. In the layered icon the cube floats above the wordmark.
    NSGraphicsContext.current!.imageInterpolation = .high
    let logoHeight = h * 0.36
    let logoWidth = logoHeight * CGFloat(logo.pixelsWide) / CGFloat(logo.pixelsHigh)
    let box = CGRect(x: (w - logoWidth) / 2, y: (h - logoHeight) / 2, width: logoWidth, height: logoHeight)
    let split = box.minX + logoWidth * logoSplit
    if layer == .front || layer == .flat {
        ctx.saveGState()
        ctx.clip(to: CGRect(x: 0, y: 0, width: split, height: h))
        logo.draw(in: box, from: .zero, operation: .sourceOver, fraction: 1, respectFlipped: false, hints: nil)
        ctx.restoreGState()
    }
    if layer == .middle || layer == .flat {
        ctx.saveGState()
        ctx.clip(to: CGRect(x: split, y: 0, width: w - split, height: h))
        logo.draw(in: box, from: .zero, operation: .sourceOver, fraction: 1, respectFlipped: false, hints: nil)
        ctx.restoreGState()
    }
    NSGraphicsContext.restoreGraphicsState()
    return rep.representation(using: .png, properties: [:])!
}

func write(_ data: Data, _ path: String) {
    let url = catalog.appendingPathComponent(path)
    try! FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! data.write(to: url)
}

func json(_ object: Any, _ path: String) {
    let url = catalog.appendingPathComponent(path)
    try! FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys]).write(to: url)
}

let info: [String: Any] = ["author": "xcode", "version": 1]

/// A layered icon: `scales` maps a scale ("1x") to pixel dimensions.
func imageStack(_ name: String, scales: [(String, Int, Int)]) {
    let layers: [(String, Layer)] = [("Front", .front), ("Middle", .middle), ("Back", .back)]
    json(["info": info, "layers": layers.map { ["filename": "\($0.0).imagestacklayer"] }], "\(name).imagestack/Contents.json")
    for (layerName, layer) in layers {
        let base = "\(name).imagestack/\(layerName).imagestacklayer"
        json(["info": info], "\(base)/Contents.json")
        var images: [[String: String]] = []
        for (scale, w, h) in scales {
            let file = "\(layerName.lowercased())@\(scale).png"
            write(render(w, h, layer), "\(base)/Content.imageset/\(file)")
            images.append(["filename": file, "idiom": "tv", "scale": scale])
        }
        json(["images": images, "info": info], "\(base)/Content.imageset/Contents.json")
    }
}

func imageSet(_ name: String, scales: [(String, Int, Int)]) {
    var images: [[String: String]] = []
    for (scale, w, h) in scales {
        let file = "image@\(scale).png"
        write(render(w, h, .flat), "\(name).imageset/\(file)")
        images.append(["filename": file, "idiom": "tv", "scale": scale])
    }
    json(["images": images, "info": info], "\(name).imageset/Contents.json")
}

imageStack("App Icon", scales: [("1x", 400, 240), ("2x", 800, 480)])
imageStack("App Icon - App Store", scales: [("1x", 1280, 768)])
imageSet("Top Shelf Image", scales: [("1x", 1920, 720), ("2x", 3840, 1440)])
imageSet("Top Shelf Image Wide", scales: [("1x", 2320, 720), ("2x", 4640, 1440)])
json([
    "assets": [
        ["filename": "App Icon - App Store.imagestack", "idiom": "tv", "role": "primary-app-icon", "size": "1280x768"],
        ["filename": "App Icon.imagestack", "idiom": "tv", "role": "primary-app-icon", "size": "400x240"],
        ["filename": "Top Shelf Image Wide.imageset", "idiom": "tv", "role": "top-shelf-image-wide", "size": "2320x720"],
        ["filename": "Top Shelf Image.imageset", "idiom": "tv", "role": "top-shelf-image", "size": "1920x720"],
    ],
    "info": info,
], "Contents.json")
// The logo on its own, for the opening title.
func logoImage(height: Int) -> Data {
    let width = height * logo.pixelsWide / logo.pixelsHigh
    let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height, bitsPerSample: 8, samplesPerPixel: 4,
        hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    NSGraphicsContext.current!.imageInterpolation = .high
    logo.draw(in: CGRect(x: 0, y: 0, width: width, height: height), from: .zero, operation: .sourceOver, fraction: 1, respectFlipped: false, hints: nil)
    NSGraphicsContext.restoreGraphicsState()
    return rep.representation(using: .png, properties: [:])!
}

let logoSet = catalog.deletingLastPathComponent().appendingPathComponent("Logo.imageset")
try! FileManager.default.createDirectory(at: logoSet, withIntermediateDirectories: true)
var logoImages: [[String: String]] = []
for (scale, height) in [("1x", 208), ("2x", 416)] {
    try! logoImage(height: height).write(to: logoSet.appendingPathComponent("logo@\(scale).png"))
    logoImages.append(["filename": "logo@\(scale).png", "idiom": "universal", "scale": scale])
}
try! JSONSerialization.data(withJSONObject: ["images": logoImages, "info": info], options: [.prettyPrinted, .sortedKeys])
    .write(to: logoSet.appendingPathComponent("Contents.json"))

// MARK: Mac

// The Mac icon: the cube alone on the same dark plate, in the rounded square macOS expects.
func macIcon(_ side: Int) -> Data {
    let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: side, pixelsHigh: side, bitsPerSample: 8, samplesPerPixel: 4,
        hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    let ctx = NSGraphicsContext.current!.cgContext
    let s = CGFloat(side)
    // Apple's grid: the plate is 824 of 1024 points wide, with the rest left for its shadow.
    let plate = CGRect(x: s * 0.0977, y: s * 0.0977, width: s * 0.8046, height: s * 0.8046)
    let shape = CGPath(roundedRect: plate, cornerWidth: s * 0.181, cornerHeight: s * 0.181, transform: nil)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -s * 0.012), blur: s * 0.03, color: NSColor.black.withAlphaComponent(0.45).cgColor)
    ctx.addPath(shape)
    ctx.setFillColor(bgBottom.cgColor)
    ctx.fillPath()
    ctx.restoreGState()
    ctx.saveGState()
    ctx.addPath(shape)
    ctx.clip()
    NSGradient(starting: bgBottom, ending: bgTop)!.draw(in: plate, angle: 90)
    let bloom = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [accent.withAlphaComponent(0.5).cgColor, accent.withAlphaComponent(0).cgColor] as CFArray, locations: [0, 1])!
    ctx.drawRadialGradient(bloom, startCenter: CGPoint(x: plate.midX, y: plate.midY), startRadius: 0, endCenter: CGPoint(x: plate.midX, y: plate.midY), endRadius: plate.width * 0.62, options: [])
    // Only the cube: the left part of the logo, scaled so it fills the plate comfortably.
    NSGraphicsContext.current!.imageInterpolation = .high
    let cubeHeight = plate.height * 0.5
    let fullWidth = cubeHeight * CGFloat(logo.pixelsWide) / CGFloat(logo.pixelsHigh)
    let cubeWidth = fullWidth * logoSplit
    let box = CGRect(x: plate.midX - cubeWidth / 2, y: plate.midY - cubeHeight / 2, width: fullWidth, height: cubeHeight)
    ctx.clip(to: CGRect(x: box.minX, y: 0, width: cubeWidth, height: s))
    logo.draw(in: box, from: .zero, operation: .sourceOver, fraction: 1, respectFlipped: false, hints: nil)
    ctx.restoreGState()
    NSGraphicsContext.restoreGraphicsState()
    return rep.representation(using: .png, properties: [:])!
}

let macCatalog = root.appendingPathComponent("30nama/Mac/Assets.xcassets")
let macIconSet = macCatalog.appendingPathComponent("AppIcon.appiconset")
try! FileManager.default.createDirectory(at: macIconSet, withIntermediateDirectories: true)
var macImages: [[String: String]] = []
for points in [16, 32, 128, 256, 512] {
    for scale in [1, 2] {
        let file = "icon_\(points)@\(scale)x.png"
        try! macIcon(points * scale).write(to: macIconSet.appendingPathComponent(file))
        macImages.append(["filename": file, "idiom": "mac", "scale": "\(scale)x", "size": "\(points)x\(points)"])
    }
}
try! JSONSerialization.data(withJSONObject: ["images": macImages, "info": info], options: [.prettyPrinted, .sortedKeys])
    .write(to: macIconSet.appendingPathComponent("Contents.json"))
try! JSONSerialization.data(withJSONObject: ["info": info], options: [.prettyPrinted, .sortedKeys])
    .write(to: macCatalog.appendingPathComponent("Contents.json"))
// The Mac app shows the same logo and accent colour; its catalog carries copies of both.
for shared in ["Logo.imageset", "AccentColor.colorset"] {
    let to = macCatalog.appendingPathComponent(shared)
    try? FileManager.default.removeItem(at: to)
    try! FileManager.default.copyItem(at: catalog.deletingLastPathComponent().appendingPathComponent(shared), to: to)
}
print("Wrote \(catalog.path), \(logoSet.lastPathComponent) and \(macCatalog.path)")
