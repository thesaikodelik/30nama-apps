import SwiftUI

/// Opening title: a sheet of liquid glass over the app itself, so that the home page is seen
/// softly through it once it has loaded; the 30nama logo's cube and then its name resolve out of
/// a blur on the glass, and a glint runs across them and the glass. To leave, the red line under the logo runs out to both
/// edges of the screen and the card parts along it like a curtain, uncovering the app.
struct SplashView: View {
    /// Start the exit.
    var leaving = false

    /// How long the exit takes, from `leaving` turning true to nothing being left on screen.
    static let exitDuration = 1.15

    @State private var lit = false
    @State private var glint = false

    /// Where the cube ends and the name begins, as a fraction of the logo's width.
    private let logoSplit = 0.29
    private let logoHeight: CGFloat = 190

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            // The seam sits where the line is: just under the logo.
            let seam = size.height / 2 + 84
            let parting = Animation.timingCurve(0.7, 0, 0.2, 1, duration: 0.75).delay(0.38)
            ZStack(alignment: .top) {
                card(size)
                    .mask(alignment: .top) { Rectangle().frame(height: seam) }
                    .offset(y: leaving ? -seam : 0)
                    .animation(parting, value: leaving)
                card(size)
                    .mask(alignment: .bottom) { Rectangle().frame(height: size.height - seam) }
                    .offset(y: leaving ? size.height - seam : 0)
                    .animation(parting, value: leaving)
                Capsule()
                    .fill(LinearGradient(colors: [.clear, Theme.accent, Color(hex: 0xFF8A70), Theme.accent, .clear], startPoint: .leading, endPoint: .trailing))
                    .frame(width: lit ? (leaving ? size.width * 1.1 : 320) : 0, height: 3)
                    .shadow(color: Theme.accent, radius: leaving ? 22 : 12)
                    .opacity(leaving ? 0 : 1)
                    .frame(width: size.width)
                    .offset(y: seam - 1.5)
                    .animation(.spring(response: 0.9, dampingFraction: 0.8).delay(leaving ? 0 : 0.75), value: lit)
                    .animation(.easeInOut(duration: 0.42), value: leaving)
            }
        }
        .ignoresSafeArea()
        .onAppear {
            lit = true
            withAnimation(.easeInOut(duration: 0.9).delay(1.15)) { glint = true }
        }
    }

    /// Background, lights and wordmark. Drawn twice, once for each half of the curtain.
    private func card(_ size: CGSize) -> some View {
        ZStack {
            glassPane
            wordmark
                .overlay {
                    // The glint: a white silhouette of the logo seen through a moving slit.
                    logo.renderingMode(.template).resizable().scaledToFit().frame(height: logoHeight).foregroundStyle(.white).mask {
                        LinearGradient(colors: [.clear, .white, .clear], startPoint: .leading, endPoint: .trailing)
                            .frame(width: 150)
                            .rotationEffect(.degrees(18))
                            .offset(x: glint ? 460 : -460)
                    }
                }
                .offset(y: -22)
        }
        .frame(width: size.width, height: size.height)
        .animation(.easeOut(duration: 1.6), value: lit)
    }

    /// A sheet of liquid glass over the whole screen, through which the app behind is seen. Where
    /// the system has Liquid Glass it is made of that; elsewhere it is light on the glass's face
    /// and rim, and the app behind is softened by the root view (a material here would blur it
    /// past recognising).
    @ViewBuilder
    private var glassPane: some View {
        if #available(tvOS 26.0, macOS 26.0, *) {
            Color.clear.glassEffect(.clear, in: Rectangle())
                .overlay { sheen }
        } else {
            ZStack {
                // Clear glass, with a shade only where the logo sits, so it holds its own over
                // whatever the app shows behind.
                RadialGradient(colors: [.black.opacity(0.5), .black.opacity(0.15)], center: .center, startRadius: 0, endRadius: 650)
                sheen
                // The thick edge of the glass: a bright rim, and light glowing in from it.
                Rectangle().strokeBorder(LinearGradient(colors: [.white.opacity(0.7), .white.opacity(0.15), .white.opacity(0.35)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 2)
                Rectangle().strokeBorder(Color.white.opacity(0.18), lineWidth: 26).blur(radius: 18)
            }
        }
    }

    /// Light on the face of the glass: bright from the upper left, a band where the surface bends,
    /// and the glint crossing it with the one on the logo.
    private var sheen: some View {
        ZStack {
            LinearGradient(stops: [
                .init(color: .white.opacity(0.22), location: 0),
                .init(color: .white.opacity(0.04), location: 0.35),
                .init(color: .clear, location: 0.65),
                .init(color: .white.opacity(0.08), location: 1),
            ], startPoint: .topLeading, endPoint: .bottomTrailing)
            // A broad reflection across the upper half, as glass catches a window.
            LinearGradient(colors: [.clear, .white.opacity(0.12), .white.opacity(0.03), .clear], startPoint: .leading, endPoint: .trailing)
                .frame(width: 900)
                .rotationEffect(.degrees(28))
                .offset(x: -420, y: -260)
                .blur(radius: 8)
            Ellipse()
                .stroke(Color.white.opacity(0.16), lineWidth: 40)
                .frame(width: 1600, height: 700)
                .blur(radius: 30)
                .offset(y: -380)
            LinearGradient(colors: [.clear, .white.opacity(0.22), .clear], startPoint: .leading, endPoint: .trailing)
                .frame(width: 420)
                .rotationEffect(.degrees(18))
                .offset(x: glint ? 1100 : -1100)
                .blendMode(.plusLighter)
        }
        .opacity(lit ? 1 : 0)
        .allowsHitTesting(false)
    }

    private var logo: Image { Image("Logo") }

    /// The logo in its two parts, so the cube can arrive a beat before the name.
    private var wordmark: some View {
        ZStack {
            logoPart(from: 0, to: logoSplit, delay: 0.2)
            logoPart(from: logoSplit, to: 1, delay: 0.42)
        }
        .environment(\.layoutDirection, .leftToRight)
    }

    private func logoPart(from start: Double, to end: Double, delay: Double) -> some View {
        logo.resizable().scaledToFit().frame(height: logoHeight)
            .mask {
                GeometryReader { geo in
                    Rectangle().frame(width: geo.size.width * (end - start)).offset(x: geo.size.width * start)
                }
            }
            .blur(radius: lit ? 0 : 18)
            .opacity(lit ? 1 : 0)
            .offset(y: lit ? 0 : 44)
            .scaleEffect(lit ? 1 : 1.3)
            .animation(.spring(response: 0.62, dampingFraction: 0.68).delay(delay), value: lit)
    }
}
