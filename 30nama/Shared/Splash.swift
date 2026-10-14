import SwiftUI

/// Opening title: light gathers behind the 30nama logo, the cube and then the name resolve out of
/// a blur and a glint runs across them. To leave, the red line under the logo runs out to both
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
            Theme.bg0
            // Two soft lights drifting apart: the projector's red and a cool counter-light.
            Circle().fill(Theme.accent)
                .frame(width: 760, height: 760)
                .blur(radius: 190)
                .opacity(lit ? 0.5 : 0)
                .offset(x: lit ? -210 : 0, y: lit ? 40 : 0)
            Circle().fill(Color(hex: 0x5B4BFF))
                .frame(width: 640, height: 640)
                .blur(radius: 190)
                .opacity(lit ? 0.4 : 0)
                .offset(x: lit ? 260 : 0, y: lit ? -70 : 0)
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
