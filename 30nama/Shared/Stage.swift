import SwiftUI

/// The full-bleed backdrop behind a screen.
///
/// It crossfades to whatever title is in focus and drifts slowly, so the page never feels like a
/// static grid. Static scrims keep the text column and the rows legible over any image.
struct StageView: View {
    /// Title whose cover to show. `nil` keeps the current image.
    let post: Post?
    /// Extra black over the image, 0...1, for screens where the backdrop is only ambience.
    var dim: Double = 0
    /// How long focus must rest on a title before the backdrop follows it.
    var settle: Duration = .milliseconds(350)
    /// Paints the dark corner under the text with the title's own colour.
    var glow = true

    @Environment(\.layoutDirection) private var direction
    @Environment(\.calm) private var calm
    @State private var shown: Backdrop?

    private struct Backdrop: Equatable {
        let id: Int
        let image: PlatformImage
        let blurred: Bool
        static func == (a: Backdrop, b: Backdrop) -> Bool { a.id == b.id }
    }

    var body: some View {
        let rtl = direction == .rightToLeft
        ZStack {
            Theme.bg0
            if let shown {
                DriftingImage(image: shown.image, variant: shown.id, blurred: shown.blurred, still: calm)
                    .id(shown.id)
                    .transition(.opacity)
            }
            // Text side: near-opaque at the start edge, clear by two thirds across.
            LinearGradient(
                stops: [
                    .init(color: Theme.bg0.opacity(0.96), location: 0),
                    .init(color: Theme.bg0.opacity(0.78), location: 0.30),
                    .init(color: Theme.bg0.opacity(0), location: 0.68),
                ],
                startPoint: rtl ? .trailing : .leading, endPoint: rtl ? .leading : .trailing)
            // Rows side: solid at the bottom edge.
            LinearGradient(
                stops: [
                    .init(color: Theme.bg0, location: 0),
                    .init(color: Theme.bg0.opacity(0.85), location: 0.32),
                    .init(color: Theme.bg0.opacity(0), location: 0.62),
                ],
                startPoint: .bottom, endPoint: .top)
            LinearGradient(colors: [Theme.bg0.opacity(0.55), .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: 220)
                .frame(maxHeight: .infinity, alignment: .top)
            // Ambient light: the scrimmed corner is not plain black but glows in the title's colour.
            if glow, let post {
                RadialGradient(colors: [post.tint.opacity(0.34), post.tint.opacity(0.10), .clear],
                               center: rtl ? .bottomTrailing : .bottomLeading, startRadius: 0, endRadius: 1150)
                    .blendMode(.plusLighter)
                    .animation(.easeInOut(duration: 1.0), value: post.id)
            }
            Color.black.opacity(dim)
        }
        .animation(.easeInOut(duration: 0.7), value: shown)
        .animation(.easeInOut(duration: 0.3), value: dim)
        .ignoresSafeArea()
        .task(id: post?.id) {
            guard let post else { return }
            // The first image appears as soon as it is decoded; later ones wait for focus to settle.
            if shown != nil { try? await Task.sleep(for: settle) }
            guard !Task.isCancelled, shown?.id != post.id else { return }
            let cover = post.cover
            guard let url = cover ?? post.poster.big ?? post.poster.large,
                  let image = await ImagePipeline.shared.image(url, maxPixel: cover == nil ? 640 : 1920),
                  !Task.isCancelled else { return }
            shown = Backdrop(id: post.id, image: image, blurred: cover == nil)
        }
    }
}

/// One backdrop layer with a slow Ken Burns drift.
private struct DriftingImage: View {
    let image: PlatformImage
    let variant: Int
    /// Titles without a wide cover fall back to their poster, blurred into a wash of colour.
    let blurred: Bool
    let still: Bool

    @State private var drifted = false

    var body: some View {
        // Four drift directions, picked by title so the same title always moves the same way.
        let directions: [CGSize] = [.init(width: -28, height: -10), .init(width: 20, height: -10),
                                    .init(width: -20, height: 8), .init(width: 0, height: 12)]
        let d = directions[abs(variant) % directions.count]
        Color.clear
            .overlay {
                Image(platformImage: image)
                    .resizable()
                    .scaledToFill()
                    .blur(radius: blurred ? 60 : 0)
                    .scaleEffect(still ? 1.0 : (drifted ? 1.12 : 1.04))
                    .offset(still ? .zero : (drifted ? d : .zero))
            }
            .clipped()
            .onAppear {
                guard !still else { return }
                withAnimation(.linear(duration: 24).repeatForever(autoreverses: true)) { drifted = true }
            }
    }
}
