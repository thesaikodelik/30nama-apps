// Draws the background of the Mac app's disk image: the window people see when they open the
// download, with the logo, an arrow from the app to Applications, and what to do in two languages.
// Writes background.png and background@2x.png into the folder given.
// Run from the repository root: swift Tools/dmg-background.swift <folder>
import AppKit
import CoreText

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let out = URL(fileURLWithPath: CommandLine.arguments[1])
for name in ["Regular", "Medium", "Bold"] {
    CTFontManagerRegisterFontsForURL(root.appendingPathComponent("30nama/Shared/Fonts/Vazirmatn-\(name).ttf") as CFURL, .process, nil)
}
let logo = NSBitmapImageRep(data: try! Data(contentsOf: root.appendingPathComponent("Tools/30nama-logo.png")))!.cgImage!

/// The window's size in points; the icons sit at the points make-dmg.sh gives Finder.
let size = CGSize(width: 660, height: 420)
let appAt = CGPoint(x: 170, y: 200), applicationsAt = CGPoint(x: 490, y: 200)

func colour(_ hex: UInt32, _ alpha: CGFloat = 1) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
}

func draw(scale: CGFloat) -> NSBitmapImageRep {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(size.width * scale), pixelsHigh: Int(size.height * scale),
                               bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                               colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    rep.size = size
    let context = NSGraphicsContext(bitmapImageRep: rep)!
    NSGraphicsContext.current = context
    let cg = context.cgContext
    // Points with the origin at the top left, as Finder places icons.
    cg.translateBy(x: 0, y: size.height)
    cg.scaleBy(x: 1, y: -1)

    // A warm, light ground, with the app's two lights low behind the icons.
    let ground = CGGradient(colorsSpace: nil, colors: [colour(0xFAF8F5), colour(0xEFEBE6)] as CFArray, locations: [0, 1])!
    cg.drawLinearGradient(ground, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
    for (centre, hex, alpha, radius) in [(appAt, UInt32(0xF0454A), CGFloat(0.16), CGFloat(170)), (applicationsAt, 0x5B4BFF, 0.11, 170)] {
        let glow = CGGradient(colorsSpace: nil, colors: [colour(hex, alpha), colour(hex, 0)] as CFArray, locations: [0, 1])!
        cg.drawRadialGradient(glow, startCenter: centre, startRadius: 0, endCenter: centre, endRadius: radius, options: [])
    }

    // The logo across the top. Its name is drawn in white on 30nama's own dark pages; here it is
    // inked, and the red cube is kept as it is.
    let logoHeight: CGFloat = 40
    let logoWidth = logoHeight * CGFloat(logo.width) / CGFloat(logo.height)
    let logoRect = CGRect(x: (size.width - logoWidth) / 2, y: 44, width: logoWidth, height: logoHeight)
    cg.saveGState()
    cg.translateBy(x: 0, y: logoRect.minY * 2 + logoRect.height)
    cg.scaleBy(x: 1, y: -1)
    let split = logoRect.minX + logoRect.width * 0.29
    cg.saveGState()
    cg.clip(to: CGRect(x: logoRect.minX, y: logoRect.minY, width: split - logoRect.minX, height: logoRect.height))
    cg.draw(logo, in: logoRect)
    cg.restoreGState()
    cg.saveGState()
    cg.clip(to: CGRect(x: split, y: logoRect.minY, width: logoRect.maxX - split, height: logoRect.height))
    cg.clip(to: logoRect, mask: logo)
    cg.setFillColor(colour(0x1B1D24))
    cg.fill(logoRect)
    cg.restoreGState()
    cg.restoreGState()

    // The arrow from the app to Applications: a soft line that firms up towards its head.
    let from = CGPoint(x: appAt.x + 82, y: appAt.y), to = CGPoint(x: applicationsAt.x - 82, y: applicationsAt.y)
    cg.saveGState()
    cg.setLineCap(.round)
    cg.setLineJoin(.round)
    cg.setLineWidth(3)
    let path = CGMutablePath()
    path.move(to: from)
    path.addQuadCurve(to: to, control: CGPoint(x: (from.x + to.x) / 2, y: from.y - 26))
    path.move(to: CGPoint(x: to.x - 13, y: to.y - 11))
    path.addLine(to: to)
    path.addLine(to: CGPoint(x: to.x - 15, y: to.y + 7))
    cg.addPath(path)
    cg.replacePathWithStrokedPath()
    cg.clip()
    let ink = CGGradient(colorsSpace: nil, colors: [colour(0xF0454A, 0.25), colour(0xF0454A, 0.95)] as CFArray, locations: [0, 1])!
    cg.drawLinearGradient(ink, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    cg.restoreGState()

    // What to do: Persian first, English under it, centred below the icons and their names.
    func line(_ text: String, font: String, points: CGFloat, hex: UInt32, y: CGFloat, rightToLeft: Bool) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        paragraph.baseWritingDirection = rightToLeft ? .rightToLeft : .leftToRight
        let string = NSAttributedString(string: text, attributes: [
            .font: NSFont(name: font, size: points)!,
            .foregroundColor: NSColor(cgColor: colour(hex))!,
            .paragraphStyle: paragraph,
        ])
        // AppKit draws text the right way up in a flipped context only if told the context is flipped.
        NSGraphicsContext.current = NSGraphicsContext(cgContext: cg, flipped: true)
        string.draw(in: CGRect(x: 40, y: y, width: size.width - 80, height: points * 2))
        NSGraphicsContext.current = context
    }
    line("برای نصب، ۳۰نما را روی پوشهٔ Applications بکشید", font: "Vazirmatn-Medium", points: 15, hex: 0x30333B, y: 318, rightToLeft: true)
    line("Drag 30nama onto Applications to install it", font: "Vazirmatn-Regular", points: 12, hex: 0x8A8D96, y: 348, rightToLeft: false)
    return rep
}

for (scale, name) in [(CGFloat(1), "background.png"), (2, "background@2x.png")] {
    try! draw(scale: scale).representation(using: .png, properties: [:])!.write(to: out.appendingPathComponent(name))
}
print("written")
