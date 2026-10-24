import SwiftUI

/// Sizes for a window seen from arm's length, not from a sofa.
enum Mac {
    static let edge: CGFloat = 48
    static let posterWidth: CGFloat = 156
    /// How many posters from the end of a wall the next page is asked for: about three rows.
    static let prefetch = 21
    static let radius: CGFloat = 12
}

struct Wordmark: View {
    var size: CGFloat = 28

    var body: some View {
        (Text("30").foregroundColor(Theme.accent) + Text("nama").foregroundColor(Theme.text1))
            .font(.app(size, .heavy))
            .environment(\.layoutDirection, .leftToRight)
    }
}

// MARK: - Buttons

/// The one bright button on a screen: play, sign in.
struct PrimaryButtonStyle: ButtonStyle {
    var tint: Color = .white

    func makeBody(configuration: Configuration) -> some View {
        Face(configuration: configuration, tint: tint)
    }

    private struct Face: View {
        let configuration: Configuration
        let tint: Color

        @Environment(\.hue) private var hue

        var body: some View {
            // The light the button throws is the colour of the title it is about.
            let glow = hue ?? tint
            Hovered { hovering in
                configuration.label
                    .font(.app(16, .bold))
                    .foregroundStyle(tint == .white ? Theme.focusInk : .white)
                    .padding(.horizontal, 26)
                    .frame(height: 46)
                    .background(Capsule(style: .continuous).fill(tint))
                    .shadow(color: glow.opacity(hue == nil ? (hovering ? 0.45 : 0.2) : (hovering ? 0.75 : 0.45)), radius: hovering ? 22 : 13, y: 6)
                    .scaleEffect(configuration.isPressed ? 0.96 : hovering ? 1.04 : 1)
                    .animation(Theme.focusSpring, value: hovering)
                    .animation(Theme.focusSpring, value: configuration.isPressed)
            }
        }
    }
}

// MARK: - The colour of the title on stage

private struct HueKey: EnvironmentKey {
    static let defaultValue: Color? = nil
}

extension EnvironmentValues {
    /// The colour of the title this part of the window is about, taken from its poster. The
    /// things that would be the app's red take it instead, and glass gets a rim of it, so each
    /// title's page is lit in its own colour. Nil where no one title is on stage.
    var hue: Color? {
        get { self[HueKey.self] }
        set { self[HueKey.self] = newValue }
    }
}

private struct HueRim<S: InsettableShape>: ViewModifier {
    let shape: S

    @Environment(\.hue) private var hue

    func body(content: Content) -> some View {
        content.overlay {
            if let hue {
                shape.strokeBorder(LinearGradient(colors: [hue.opacity(0.6), hue.opacity(0.1)], startPoint: .top, endPoint: .bottom), lineWidth: 1)
                    .allowsHitTesting(false)
            }
        }
    }
}

extension View {
    /// A rim in the colour of the title on stage, brightest at the top, for a pane of glass.
    func hueRim<S: InsettableShape>(_ shape: S) -> some View { modifier(HueRim(shape: shape)) }
}

/// Everything else: a pane of glass that brightens under the pointer.
struct GlassButtonStyle: ButtonStyle {
    var height: CGFloat = 46

    func makeBody(configuration: Configuration) -> some View {
        Hovered { hovering in
            configuration.label
                .font(.app(16, .semibold))
                .foregroundStyle(Theme.text1)
                .padding(.horizontal, height * 0.5)
                .frame(height: height)
                .background(Capsule(style: .continuous).fill(Color.white.opacity(hovering ? 0.2 : 0.1)))
                .glass(Capsule(style: .continuous))
                .hueRim(Capsule(style: .continuous))
                .scaleEffect(configuration.isPressed ? 0.96 : hovering ? 1.03 : 1)
                .animation(Theme.focusSpring, value: hovering)
                .animation(Theme.focusSpring, value: configuration.isPressed)
        }
    }
}

/// Tells its content whether the pointer is over it.
struct Hovered<Content: View>: View {
    @ViewBuilder var content: (Bool) -> Content
    @State private var hovering = false

    var body: some View {
        content(hovering).onHover { hovering = $0 }
    }
}

/// A small capsule that is one choice among a few: a season, a sort order.
struct PillChoice: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.app(13, .semibold))
                .foregroundStyle(selected ? Theme.focusInk : Theme.text1)
                .padding(.horizontal, 13)
                .frame(height: 30)
                .background(Capsule(style: .continuous).fill(selected ? Color.white : Color.white.opacity(hovering ? 0.2 : 0.1)))
                .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
    }
}

// MARK: - Facts about a title

struct Chip<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .font(.app(13, .semibold))
            .foregroundStyle(Theme.text1)
            .padding(.horizontal, 10)
            .frame(height: 26)
            .background(Capsule(style: .continuous).fill(Color.white.opacity(0.12)))
    }
}

/// Scores, year, length, age rating and genres on one line.
struct MetaRow: View {
    let post: Post
    @Environment(\.loc) private var loc

    var body: some View {
        HStack(spacing: 8) {
            if let score = post.imdbScore {
                Chip { label("IMDb", Fmt.score(score), Color(hex: 0xF5C518)) }
            }
            if let fresh = post.rottenTomatoes {
                Chip { label("RT", "\(fresh)%", Color(hex: 0xFA5A3C)) }
            }
            if let meta = post.metacritic {
                Chip { label("MC", "\(meta)", Color(hex: 0x66CC33)) }
            }
            if let fan = post.fanScore {
                Chip { label(fan.source, Fmt.score(fan.value), Color(hex: 0x6C8CFF)) }
            }
            if let site = post.siteScore {
                Chip {
                    HStack(spacing: 5) {
                        Image(systemName: "star.fill").font(.system(size: 10, weight: .bold)).foregroundStyle(Theme.good)
                        Text(loc.num(Fmt.score(site)))
                    }
                }
            }
            if let year = post.year {
                Chip { Text(loc.num(post.yearEnd.map { $0 == year ? "\(year)" : "\(year)–\($0)" } ?? "\(year)")) }
            }
            if let runtime = Fmt.runtime(minutes: post.runtimeMinutes, lang: loc.lang) {
                Chip { Text(runtime) }
            }
            if let age = post.age {
                Chip { Text(age).environment(\.layoutDirection, .leftToRight) }
            }
            if !post.genres.isEmpty {
                Text(post.genres.prefix(3).map(\.name).joined(separator: loc.lang == .en ? ", " : "، "))
                    .font(.app(14))
                    .foregroundStyle(Theme.text2)
                    .padding(.leading, 4)
            }
        }
        .lineLimit(1)
    }

    private func label(_ name: String, _ value: String, _ color: Color) -> some View {
        HStack(spacing: 6) {
            Text(name).font(.app(11, .heavy)).foregroundStyle(color)
            Text(value)
        }
        .environment(\.layoutDirection, .leftToRight)
    }
}

// MARK: - Cards

/// A poster that lifts toward the pointer and glows in its own colour.
struct PosterCard: View {
    let post: Post
    var width: CGFloat = Mac.posterWidth
    /// A star rating to pin on the corner, as on the profile's own shelf.
    var stars: Double? = nil
    /// A line that stays on the card whether or not the pointer is on it: the day an episode is due.
    var note: String? = nil
    let action: () -> Void

    @Environment(\.loc) private var loc
    @Environment(MacNav.self) private var nav: MacNav?
    @State private var hovering = false
    /// Where the card is in the window. Kept outside the view's state: it changes with every
    /// scroll and nothing is drawn from it.
    @State private var spot = Spot()

    private final class Spot {
        var frame = CGRect.zero
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Mac.radius, style: .continuous)
        Button {
            // The poster leaves from here for the title's page.
            if spot.frame.width > 0 { nav?.flight = Flight(post: post, from: spot.frame) }
            action()
        } label: {
            RemoteImage(url: post.poster.url(forWidth: width * 1.1), blurhash: post.poster.blurhash, maxPixel: Int(width * 1.5 * Display.scale))
                .frame(width: width, height: width * 1.5)
                // A poster that has not arrived yet is still a tile, not a hole in the row.
                .background(Theme.surface2)
                .overlay(alignment: .bottom) { caption }
                .overlay(alignment: .bottom) {
                    if let note, !hovering {
                        Text(note)
                            // In the direction of the words in it, so they come in the right order. Only
                            // Persian has its own; elsewhere the day is written in English.
                            .environment(\.layoutDirection, loc.lang == .fa ? .rightToLeft : .leftToRight)
                            .font(.app(11, .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .padding(.horizontal, 8)
                            .frame(maxWidth: .infinity, minHeight: 26)
                            .background(Color.black.opacity(0.72))
                    }
                }
                .overlay(alignment: .topLeading) { badge }
                .clipShape(shape)
                .overlay { shape.strokeBorder(Color.white.opacity(hovering ? 0.9 : 0.08), lineWidth: hovering ? 2 : 1) }
                .tilting(shape)
                // A glow only under the pointer: a resting one under every poster is the dearest
                // thing to draw while a wall scrolls, and at 120 frames a second it showed.
                .posterGlow(post, on: hovering, opacity: 0.55, radius: 24, y: 14)
                .scaleEffect(hovering ? 1.07 : 1)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        // The poster is a picture: say what it is a picture of.
        .accessibilityLabel(post.year.map { "\(post.title(loc.lang)), \(loc.num($0))" } ?? post.title(loc.lang))
        // Posters passing under a still pointer while the page scrolls do not light up one
        // after another; the one it rests on does once the scrolling stops and the pointer moves.
        .onHover { inside in
            if inside, let nav, Date().timeIntervalSince(nav.lastScroll) < 0.2 { return }
            hovering = inside
        }
        .background {
            // Only the card under the pointer keeps track of where it is: that is the one a card
            // opens over or a click flies from. Every card doing so ran on every frame of a scroll.
            if hovering {
                GeometryReader { geo in
                    Color.clear
                        .onAppear { spot.frame = geo.frame(in: .named(MainView.space)) }
                        .onChange(of: geo.frame(in: .named(MainView.space))) { _, new in spot.frame = new }
                }
            }
        }
        // The pointer has come to rest here: open the card that says more about the title. Not
        // at once: someone still looking along the row should not have cards opening under them.
        .task(id: hovering) {
            guard hovering, let nav else { return }
            try? await Task.sleep(for: .milliseconds(1300))
            // Not under a pane that has the window: the pointer there is not on this poster.
            guard !Task.isCancelled, spot.frame.width > 0, !nav.palette, nav.picking == nil,
                  Date().timeIntervalSince(nav.lastScroll) > 0.4 else { return }
            nav.peek = Peek(post: post, frame: spot.frame)
        }
        .animation(Theme.focusSpring, value: hovering)
        .zIndex(hovering ? 1 : 0)
    }

    /// The title, which the poster art does not always spell out, shown under the pointer.
    private var caption: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(post.title(loc.lang))
                .font(.app(13, .bold))
                .foregroundStyle(.white)
                .lineLimit(2)
            if let year = post.year {
                Text(loc.num(year)).font(.app(11, .medium)).foregroundStyle(.white.opacity(0.7))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .padding(.top, 26)
        .background(LinearGradient(colors: [.clear, .black.opacity(0.88)], startPoint: .top, endPoint: .bottom))
        .opacity(hovering ? 1 : 0)
    }

    @ViewBuilder
    private var badge: some View {
        if let stars {
            HStack(spacing: 4) {
                Image(systemName: "star.fill").font(.system(size: 10, weight: .bold)).foregroundStyle(Color(hex: 0xF5C518))
                Text(loc.num(Fmt.score(stars))).font(.app(12, .bold)).foregroundStyle(.white)
            }
            .padding(.horizontal, 8)
            .frame(height: 22)
            .background(Capsule(style: .continuous).fill(Color.black.opacity(0.72)))
            .padding(8)
        } else if post.comingSoon {
            Text(loc(.soon))
                .font(.app(11, .bold))
                .foregroundStyle(Theme.text1)
                .padding(.horizontal, 8)
                .frame(height: 22)
                .background(Capsule(style: .continuous).fill(Color.black.opacity(0.65)))
                .padding(8)
        }
    }
}

// MARK: - The card over a poster

/// What a poster opens into when the pointer rests on it: the scene, what the title is, and the
/// two things most often wanted of it, without leaving the shelf.
struct PeekCard: View {
    let peek: Peek
    /// The size of the window, to keep the card inside it.
    let bounds: CGSize
    /// The direction the card's own contents read in.
    let direction: LayoutDirection

    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    private static let size = CGSize(width: 344, height: 372)

    /// Over its poster, moved in from any edge of the window it would cross.
    private var rect: CGRect {
        let size = PeekCard.size
        let x = min(max(peek.frame.midX, size.width / 2 + 16), bounds.width - size.width / 2 - 16)
        let y = min(max(peek.frame.midY, size.height / 2 + 16), bounds.height - size.height / 2 - 16)
        return CGRect(x: x - size.width / 2, y: y - size.height / 2, width: size.width, height: size.height)
    }

    var body: some View {
        let post = peek.post
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            RemoteImage(url: post.cover ?? post.poster.big ?? post.poster.large, blurhash: post.poster.blurhash, fallback: post.poster.big ?? post.poster.large, maxPixel: Int(PeekCard.size.width * Display.scale))
                .frame(width: PeekCard.size.width, height: 194)
                .clipped()
                .overlay { LinearGradient(colors: [.clear, Theme.bg1.opacity(0.95)], startPoint: .center, endPoint: .bottom) }
                .overlay(alignment: .bottomLeading) {
                    Text(post.title(loc.lang))
                        .font(.app(20, .heavy))
                        .foregroundStyle(Theme.text1)
                        .lineLimit(2)
                        .minimumScaleFactor(0.8)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 6)
                }
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    if let score = post.imdbScore {
                        HStack(spacing: 4) {
                            Text("IMDb").font(.system(size: 10, weight: .heavy)).foregroundStyle(Color(hex: 0xF5C518))
                            Text(loc.num(Fmt.score(score))).font(.app(13, .bold)).foregroundStyle(Theme.text1)
                        }
                    }
                    Text(facts(post)).font(.app(13)).foregroundStyle(Theme.text2).lineLimit(1)
                }
                Text(post.plot(loc.lang))
                    .font(.app(13))
                    .foregroundStyle(Theme.text2)
                    .lineSpacing(4)
                    .lineLimit(3)
                    .frame(maxWidth: .infinity, minHeight: 62, alignment: .topLeading)
                HStack(spacing: 8) {
                    Button { nav.open(post, playing: true) } label: {
                        Label(loc(post.resume != nil ? .resume : .play), systemImage: "play.fill")
                            .font(.app(14, .bold))
                            .foregroundStyle(Theme.focusInk)
                            .padding(.horizontal, 18)
                            .frame(height: 38)
                            .background(Capsule(style: .continuous).fill(.white))
                            .shadow(color: post.tint.opacity(0.5), radius: 12, y: 4)
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    if app.isLoggedIn {
                        round(app.isListed(post, "watchlist") ? "bookmark.fill" : "bookmark", lit: app.isListed(post, "watchlist"), help: loc(.watchlist)) {
                            Task { await app.toggleList(post, "watchlist") }
                        }
                        round(app.isListed(post, "favorite") ? "heart.fill" : "heart", lit: app.isListed(post, "favorite"), help: loc(.favorites)) {
                            Task { await app.toggleList(post, "favorite") }
                        }
                    }
                    Spacer(minLength: 0)
                    round("chevron.forward", lit: false, help: loc(.details)) { nav.open(post, from: peek.frame) }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 16)
        }
        .frame(width: PeekCard.size.width, height: PeekCard.size.height, alignment: .top)
        .background(Theme.bg1.opacity(0.82))
        .background(.ultraThinMaterial)
        .clipShape(shape)
        .hueRim(shape)
        .posterGlow(post, on: true, opacity: 0.35, radius: 40, y: 20)
        .shadow(color: .black.opacity(0.5), radius: 24, y: 12)
        .contentShape(shape)
        .onTapGesture { nav.open(post, from: peek.frame) }
        .environment(\.hue, post.tint)
        .environment(\.layoutDirection, direction)
        .position(x: rect.midX, y: rect.midY)
        .onAppear { nav.peekRect = rect }
        .onChange(of: bounds) { nav.peekRect = rect }
    }

    /// The year, how long it runs and what kind of thing it is, in one line.
    private func facts(_ post: Post) -> String {
        var parts: [String] = []
        if let year = post.year { parts.append(loc.num(year)) }
        if let runtime = Fmt.runtime(minutes: post.runtimeMinutes, lang: loc.lang) { parts.append(runtime) }
        parts += post.genres.prefix(2).map(\.name)
        return parts.joined(separator: " · ")
    }

    private func round(_ symbol: String, lit: Bool, help: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(lit ? peek.post.tint : Theme.text1)
                .frame(width: 38, height: 38)
                .background(Circle().fill(Color.white.opacity(0.12)))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .help(help)
        .accessibilityLabel(help)
    }
}

// MARK: - The poster in flight

/// The poster of a title being opened, travelling from the card that was clicked to where the
/// page keeps it. The page holds its own copy back until this one has landed on that spot.
struct FlyingPoster: View {
    let flight: Flight
    let bounds: CGSize
    let rtl: Bool

    @Environment(MacNav.self) private var nav
    @State private var landed = false

    /// Where the title's page puts its poster.
    static func landing(in bounds: CGSize, rtl: Bool) -> CGRect {
        CGRect(x: rtl ? bounds.width - Mac.edge - DetailsLayout.poster.width : Mac.edge, y: DetailsLayout.top,
               width: DetailsLayout.poster.width, height: DetailsLayout.poster.height)
    }

    var body: some View {
        let rect = landed ? FlyingPoster.landing(in: bounds, rtl: rtl) : flight.from
        // The card's own picture: it is in memory already, so the flight never starts blank.
        RemoteImage(url: flight.post.poster.url(forWidth: Mac.posterWidth * 1.1), blurhash: flight.post.poster.blurhash, maxPixel: Int(Mac.posterWidth * 1.5 * Display.scale))
            .frame(width: rect.width, height: rect.height)
            .clipShape(RoundedRectangle(cornerRadius: landed ? 16 : Mac.radius, style: .continuous))
            .posterGlow(flight.post, on: true, opacity: 0.4, radius: 40, y: 18)
            .position(x: rect.midX, y: rect.midY)
            .allowsHitTesting(false)
            .task {
                withAnimation(.spring(response: 0.48, dampingFraction: 0.86)) { landed = true }
                try? await Task.sleep(for: .milliseconds(640))
                nav.flight = nil
            }
    }
}

/// Where the title's page lays its poster out, shared with the poster that flies there.
enum DetailsLayout {
    static let poster = CGSize(width: 240, height: 360)
    static let top: CGFloat = 130
}

// MARK: - A card that leans to the pointer

/// Leans a card under the pointer, as if pressed there, and runs a glint of light across it:
/// what a poster held in the hand does. It moves only while the pointer does, and comes back
/// flat when the pointer leaves.
private struct Tilt<S: Shape>: ViewModifier {
    let shape: S
    let degrees: Double

    @Environment(\.calm) private var calm
    @Environment(MacNav.self) private var nav: MacNav?
    /// Where the pointer is on the card, 0...1 each way; nil when it is elsewhere.
    @State private var at: UnitPoint?

    func body(content: Content) -> some View {
        let across = Double(at?.x ?? 0.5) - 0.5, down = Double(at?.y ?? 0.5) - 0.5
        return content
            .overlay {
                if let at {
                    RadialGradient(colors: [.white.opacity(0.30), .white.opacity(0.07), .clear], center: at, startRadius: 0, endRadius: 230)
                        .blendMode(.plusLighter)
                        .clipShape(shape)
                        .allowsHitTesting(false)
                        .transition(.opacity)
                }
            }
            .rotation3DEffect(.degrees(-down * 2 * degrees), axis: (x: 1, y: 0, z: 0), perspective: 0.4)
            .rotation3DEffect(.degrees(across * 2 * degrees), axis: (x: 0, y: 1, z: 0), perspective: 0.4)
            .onContinuousHover(coordinateSpace: .local) { phase in
                guard !calm else { return }
                switch phase {
                case .active(let point):
                    // Not while the page scrolls under a still pointer: see `PosterCard`.
                    if at == nil, let nav, Date().timeIntervalSince(nav.lastScroll) < 0.2 { return }
                    at = UnitPoint(x: point.x / max(1, size.width), y: point.y / max(1, size.height))
                case .ended: at = nil
                }
            }
            .background { GeometryReader { geo in Color.clear.onAppear { size = geo.size }.onChange(of: geo.size) { _, new in size = new } } }
            // Slow to follow, so the card leans after the pointer rather than snapping to it.
            .animation(.interactiveSpring(response: 0.45, dampingFraction: 0.85), value: at)
    }

    @State private var size = CGSize.zero
}

/// The glow behind a card, in the colour of the title's own poster. Worked out the first time
/// the card glows, from the poster picture the shelves already have, and kept for the title.
private struct PosterGlow: ViewModifier {
    let post: Post
    let on: Bool
    let opacity: Double
    let radius: CGFloat
    let y: CGFloat

    @State private var colour: Color?

    func body(content: Content) -> some View {
        let glow = colour ?? SceneTint.shared.knownPoster(for: post)
        content
            .shadow(color: on ? (glow ?? .clear).opacity(opacity) : .clear, radius: on ? radius : 0, y: on ? y : 0)
            .task(id: on) {
                guard on, glow == nil else { return }
                colour = await SceneTint.shared.posterColour(for: post, url: post.poster.url(forWidth: Mac.posterWidth * 1.1),
                                                             maxPixel: Int(Mac.posterWidth * 1.5 * Display.scale))
            }
    }
}

extension View {
    /// A glow behind a card in its poster's colour, while `on`. See `PosterGlow`.
    func posterGlow(_ post: Post, on: Bool, opacity: Double, radius: CGFloat, y: CGFloat) -> some View {
        modifier(PosterGlow(post: post, on: on, opacity: opacity, radius: radius, y: y))
    }

    /// Makes a card lean to the pointer and catch the light. See `Tilt`.
    func tilting<S: Shape>(_ shape: S, degrees: Double = 4) -> some View { modifier(Tilt(shape: shape, degrees: degrees)) }
}

/// A poster with its place in the top ten beside it, in figures as tall as the poster. The
/// figure is a quiet grey until the pointer is on the card, and then takes the title's colour.
struct RankedCard: View {
    let rank: Int
    let post: Post
    let action: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    var body: some View {
        HStack(alignment: .bottom, spacing: -24) {
            Text(loc.num(rank))
                .font(.app(170, .heavy))
                .foregroundStyle(LinearGradient(
                    colors: hovering ? [post.tint, post.tint.opacity(0.4)] : [Theme.text1.opacity(0.5), Theme.text1.opacity(0.07)],
                    startPoint: .top, endPoint: .bottom))
                .fixedSize()
                .frame(height: Mac.posterWidth * 1.5)
                .environment(\.layoutDirection, .leftToRight)
            PosterCard(post: post, action: action)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(loc.num(rank)). \(post.title(loc.lang))")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { action() }
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.2), value: hovering)
    }
}

/// A wide card for something half-watched: the scene, how far in, and what is left.
struct ContinueCard: View {
    let post: Post
    /// Takes the card off the row; nil where it cannot be taken off.
    var remove: (() -> Void)? = nil
    let action: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    static let size = CGSize(width: 288, height: 162)
    private var size: CGSize { ContinueCard.size }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Mac.radius + 2, style: .continuous)
        Button(action: action) {
            RemoteImage(url: post.cover ?? post.poster.big ?? post.poster.large, blurhash: post.poster.blurhash, fallback: post.poster.big ?? post.poster.large, maxPixel: Int(size.width * Display.scale))
                .frame(width: size.width, height: size.height)
                .overlay { LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .center, endPoint: .bottom) }
                .overlay(alignment: .bottomLeading) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(post.title(loc.lang)).font(.app(15, .bold)).foregroundStyle(.white).lineLimit(1)
                        if let line { Text(line).font(.app(12, .medium)).foregroundStyle(.white.opacity(0.72)).lineLimit(1) }
                        GeometryReader { geo in
                            Capsule().fill(Color.white.opacity(0.22))
                            Capsule().fill(Theme.accent).frame(width: max(6, geo.size.width * (post.resume?.progress ?? 0)))
                        }
                        .frame(height: 4)
                        .padding(.top, 4)
                    }
                    .padding(12)
                }
                .overlay {
                    Image(systemName: "play.fill")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Theme.focusInk)
                        .frame(width: 52, height: 52)
                        .background(Circle().fill(.white))
                        .opacity(hovering ? 1 : 0)
                        .scaleEffect(hovering ? 1 : 0.8)
                }
                .clipShape(shape)
                .overlay { shape.strokeBorder(Color.white.opacity(hovering ? 0.9 : 0.08), lineWidth: hovering ? 2 : 1) }
                .tilting(shape, degrees: 3)
                .posterGlow(post, on: hovering, opacity: 0.5, radius: 24, y: 14)
                .scaleEffect(hovering ? 1.04 : 1)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        // Off the card itself, so a click on it does not also open the title.
        .overlay(alignment: .topTrailing) {
            if let remove, hovering {
                Button(action: remove) {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 26, height: 26)
                        .background(Circle().fill(Color.black.opacity(0.6)))
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.25), lineWidth: 1))
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .help(loc(.removeFromContinue))
                .accessibilityLabel(loc(.removeFromContinue))
                .padding(10)
                .transition(.opacity)
            }
        }
        .contextMenu {
            if let remove { Button(loc(.removeFromContinue), action: remove) }
        }
        .onHover { hovering = $0 }
        .animation(Theme.focusSpring, value: hovering)
        .zIndex(hovering ? 1 : 0)
    }

    private var line: String? {
        guard let resume = post.resume else { return nil }
        var parts: [String] = []
        if let season = resume.season, let episode = resume.episode { parts.append(loc.episodeTag(season: season, episode: episode)) }
        if let left = resume.remainingMinutes { parts.append("\(loc.num(left)) \(loc(.minLeft))") }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

// MARK: - Shelf

/// A titled row that scrolls sideways, with arrows that appear under the pointer for a mouse without a wheel.
struct Shelf<Card: View>: View {
    let title: String
    let posts: [Post]
    /// How many cards one press of an arrow moves by.
    var step = 5
    /// Where the row's name leads, when there is a whole section of it: Movies, Series, Anime.
    var more: (() -> Void)? = nil
    @ViewBuilder var card: (Post) -> Card

    @State private var position: Post.ID?
    @State private var hovering = false
    @State private var titleHovered = false
    @Environment(\.loc) private var loc

    /// Each title once. A list drawn from several of the home feed's sections can name a title
    /// twice, and a row given the same id twice draws the second one as an empty space.
    private var shown: [Post] {
        var seen = Set<Int>()
        return posts.filter { seen.insert($0.id).inserted }
    }

    var body: some View {
        let posts = shown
        VStack(alignment: .leading, spacing: 4) {
            Group {
                if let more {
                    Button(action: more) {
                        HStack(spacing: 6) {
                            Text(title)
                            // The way on, shown under the pointer so the name reads as a name the rest of the time.
                            Image(systemName: "chevron.forward")
                                .font(.system(size: 14, weight: .bold))
                                .opacity(titleHovered ? 1 : 0)
                                .offset(x: titleHovered ? 0 : -4)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .onHover { titleHovered = $0 }
                    .animation(.easeOut(duration: 0.15), value: titleHovered)
                    .accessibilityHint(title)
                } else {
                    Text(title)
                }
            }
            .font(.app(20, .bold))
            .foregroundStyle(Theme.text1)
            .padding(.horizontal, Mac.edge)
            ScrollView(.horizontal) {
                LazyHStack(spacing: 16) {
                    ForEach(posts) { card($0) }
                }
                .scrollTargetLayout()
                .padding(.horizontal, Mac.edge)
                .padding(.vertical, 18)
            }
            .scrollIndicators(.never)
            .scrollClipDisabled()
            .scrollPosition(id: $position, anchor: .leading)
            .overlay(alignment: .leading) { arrow(-1, posts) }
            .overlay(alignment: .trailing) { arrow(1, posts) }
        }
        .onHover { hovering = $0 }
    }

    @ViewBuilder
    private func arrow(_ direction: Int, _ posts: [Post]) -> some View {
        let index = posts.firstIndex { $0.id == position } ?? 0
        let target = min(max(0, index + direction * step), max(0, posts.count - 1))
        if hovering, target != index {
            Button {
                withAnimation(Theme.glide) { position = posts[target].id }
            } label: {
                Image(systemName: direction < 0 ? "chevron.backward" : "chevron.forward")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.text1)
                    .frame(width: 38, height: 38)
                    .glass(Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(loc(direction < 0 ? .rowPrevious : .rowNext))
            .padding(.horizontal, 8)
            .transition(.opacity)
        }
    }
}

// MARK: - States

struct ErrorState: View {
    var message: String? = nil
    let retry: () -> Void
    @Environment(\.loc) private var loc

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "wifi.exclamationmark").font(.system(size: 44)).foregroundStyle(Theme.text3)
            Text(message ?? loc(.error)).font(.app(18, .medium)).foregroundStyle(Theme.text2)
            Button(loc(.retry), action: retry).buttonStyle(GlassButtonStyle())
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// The backdrop of a screen that has no title of its own to show.
struct AmbientBackground: View {
    var body: some View {
        ZStack {
            Theme.bg0
            RadialGradient(colors: [Theme.accent.opacity(0.16), .clear], center: .topTrailing, startRadius: 0, endRadius: 900)
            RadialGradient(colors: [Color(hex: 0x2B3A67).opacity(0.25), .clear], center: .bottomLeading, startRadius: 0, endRadius: 800)
        }
        .ignoresSafeArea()
    }
}

struct Toast: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.app(15, .semibold))
            .foregroundStyle(Theme.text1)
            .padding(.horizontal, 20)
            .frame(height: 44)
            .glass(Capsule(style: .continuous))
    }
}

// MARK: - The title as its own artwork

/// A title's name: its logo from TMDB when TMDB has one, otherwise the name in large type with its
/// other name under it. The logo is mostly in English; in Persian or Arabic the name in that
/// language follows it in smaller type, so it is still there to read.
struct TitleArt: View {
    static let arrival = AnyTransition.modifier(active: Arrival(shown: false), identity: Arrival(shown: true))
    static let settle = Animation.spring(response: 0.7, dampingFraction: 0.9)

    let post: Post
    /// Size of the name in type, and so of the space the logo is given.
    let size: CGFloat

    @Environment(\.loc) private var loc
    /// Nil while TMDB is being asked: the name is not shown yet, so it does not flash up and then
    /// give way to the logo.
    @State private var logo: PlatformImage??

    var body: some View {
        Group {
            if case .some(.some(let logo)) = logo {
                VStack(alignment: .leading, spacing: 10) {
                    Image(platformImage: logo)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: size * 8, maxHeight: size * 2.4, alignment: .leading)
                        .shadow(color: .black.opacity(0.55), radius: 16, y: 6)
                        .accessibilityLabel(post.title(loc.lang))
                    // Only a name in another script: a series with no Persian name would repeat the logo.
                    if loc.lang != .en, SubSource.simple(post.title(loc.lang)) != SubSource.simple(post.title(.en)) {
                        Text(post.title(loc.lang)).font(.app(size * 0.38, .semibold)).foregroundStyle(Theme.text1)
                    }
                }
                .transition(TitleArt.arrival)
            } else {
                VStack(alignment: .leading, spacing: 14) {
                    Text(post.title(loc.lang))
                        .font(.app(size, .heavy))
                        .foregroundStyle(LinearGradient(colors: [.white, Theme.text1.opacity(0.84)], startPoint: .top, endPoint: .bottom))
                        .lineLimit(2)
                        .minimumScaleFactor(0.6)
                        .shadow(color: .black.opacity(0.5), radius: 18, y: 6)
                    if let other = post.alternateTitle(loc.lang) {
                        Text(other).font(.app(18, .medium)).foregroundStyle(Theme.text2)
                    }
                }
                // Holding its place, unseen, until it is known there is no logo; then coming up out
                // of a blur, as the opening title does.
                .modifier(Arrival(shown: logo != nil))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .task(id: post.id) {
            // Known already, as on going back to a page: at once, with nothing to wait for.
            if let url = TMDBStore.shared.known[post.id]?.logo, let hit = ImagePipeline.shared.cached(url, maxPixel: 1000) {
                logo = .some(hit)
                return
            }
            logo = nil
            // A slow answer is not waited on for long; the name shows, and a logo that comes later still replaces it.
            let fallback = Task { @MainActor in
                try? await Task.sleep(for: .seconds(2))
                if !Task.isCancelled, logo == nil { withAnimation(TitleArt.settle) { logo = .some(nil) } }
            }
            defer { fallback.cancel() }
            guard let info = await TMDBStore.shared.info(for: post, lang: loc.lang), let url = info.logo,
                  let image = await ImagePipeline.shared.image(url, maxPixel: 1000) else {
                if !Task.isCancelled { withAnimation(TitleArt.settle) { logo = .some(nil) } }
                return
            }
            // In one motion with the page: what is below moves down to make room as the logo comes in,
            // instead of jumping there.
            if !Task.isCancelled { withAnimation(TitleArt.settle) { logo = .some(image) } }
        }
    }
}

/// Coming into view out of a blur, from a little below.
private struct Arrival: ViewModifier {
    let shown: Bool

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .blur(radius: shown ? 0 : 12)
            .offset(y: shown ? 0 : 14)
    }
}

/// A grey card of a real card's size, standing in its place while the row is on its way.
struct CardStandIn: View {
    let size: CGSize

    @Environment(\.lively) private var lively
    @State private var bright = false

    /// Titles that are no titles, for a row of stand-ins to be laid out like a real one.
    static let posts: [Post] = (1...5).compactMap { Post(JSON(["id": -$0] as [String: Any])) }

    var body: some View {
        RoundedRectangle(cornerRadius: Mac.radius + 2, style: .continuous)
            .fill(Color.white.opacity(bright ? 0.09 : 0.05))
            .frame(width: size.width, height: size.height)
            // A slow breath where motion is affordable; elsewhere it holds still.
            .onAppear {
                guard lively else { return }
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { bright = true }
            }
            .accessibilityHidden(true)
    }
}

// MARK: - Glass with a rim

/// The glass of the top bar and its discs, made as the liquid pill is: frosted, a glare from
/// above and a rim bright where the light strikes the top edge. A flat material with an even
/// hairline reads as grey plastic; the uneven rim is what reads as glass. `lit` is the open
/// look, a little brighter, as the pill under the open tab. macOS 26 has real glass of its own.
struct GlassLens<S: InsettableShape>: View {
    let shape: S
    var lit = false

    var body: some View {
        ZStack {
            shape.fill(.ultraThinMaterial)
            shape.fill(Color.white.opacity(GlassLight.body(lit)))
            shape.fill(GlassLight.glare(lit))
            shape.strokeBorder(GlassLight.rim(lit), lineWidth: GlassLight.rimWidth)
        }
        .shadow(color: .black.opacity(GlassLight.shadow(lit)), radius: 7, y: 3)
        .allowsHitTesting(false)
    }
}

/// How light falls on the glass, the same for a lens and for glass cut to a drawn shape.
enum GlassLight {
    static let rimWidth: CGFloat = 1.2
    static func body(_ lit: Bool) -> Double { lit ? 0.10 : 0.02 }
    static func shadow(_ lit: Bool) -> Double { lit ? 0.3 : 0.22 }
    /// A glare over the top half, and a little caught along the bottom edge.
    static func glare(_ lit: Bool) -> LinearGradient {
        LinearGradient(stops: [.init(color: .white.opacity(lit ? 0.30 : 0.12), location: 0),
                               .init(color: .white.opacity(lit ? 0.06 : 0.02), location: 0.5),
                               .init(color: .clear, location: 0.7),
                               .init(color: .white.opacity(lit ? 0.10 : 0.04), location: 1)],
                       startPoint: .top, endPoint: .bottom)
    }
    /// Bright where the light strikes the top edge, faint along the bottom.
    static func rim(_ lit: Bool) -> LinearGradient {
        LinearGradient(colors: [.white.opacity(lit ? 0.95 : 0.55), .white.opacity(lit ? 0.25 : 0.12),
                                .white.opacity(0.05), .white.opacity(lit ? 0.45 : 0.22)],
                       startPoint: .top, endPoint: .bottom)
    }
}

/// Glass cut to a shape that is drawn rather than a `Shape`: `outline` is the shape and `inner`
/// the same a hair narrower inside, and the band between the two is the rim.
private struct DrawnGlass<Outline: View, Inner: View>: View {
    let outline: Outline
    let inner: Inner
    var lit = false

    var body: some View {
        ZStack {
            Rectangle().fill(.ultraThinMaterial)
            Color.white.opacity(GlassLight.body(lit))
            GlassLight.glare(lit)
            GlassLight.rim(lit)
                .mask {
                    outline
                        .overlay { inner.blendMode(.destinationOut) }
                        .compositingGroup()
                }
        }
        .mask(outline)
        .shadow(color: .black.opacity(GlassLight.shadow(lit)), radius: 7, y: 3)
    }
}

extension View {
    /// `glass(_:)` with the rim of `GlassLens` where the system has no glass of its own.
    @ViewBuilder
    func lens<S: InsettableShape>(_ shape: S) -> some View {
        if #available(macOS 26.0, *) {
            glassEffect(.regular, in: shape)
        } else {
            background(GlassLens(shape: shape))
        }
    }
}

// MARK: - The liquid pill

/// Where each tab of a bar is, by its id, in the bar's own space ("liquidBar").
struct LiquidTabFrames: PreferenceKey {
    static var defaultValue: [AnyHashable: CGRect] = [:]
    static func reduce(value: inout [AnyHashable: CGRect], nextValue: () -> [AnyHashable: CGRect]) {
        value.merge(nextValue()) { $1 }
    }
}

/// Where the glass the pill flows over is, in the same space: a bar's capsule, a disc beside it.
struct LiquidGroundFrames: PreferenceKey {
    static var defaultValue: [CGRect] = []
    static func reduce(value: inout [CGRect], nextValue: () -> [CGRect]) {
        value.append(contentsOf: nextValue())
    }
}

extension View {
    /// Glass of a capsule's shape, drawn under the liquid pill rather than by this view, so the
    /// pill can leave one piece of glass and gather in another: the bar, and a disc beside it
    /// that is a tab of its own.
    func liquidGround() -> some View {
        modifier(LiquidGround())
    }

    /// Tells the bar's liquid pill where this tab is.
    func liquidTab(_ id: AnyHashable) -> some View {
        modifier(LiquidTab(id: id))
    }

    /// A bar whose selected tab sits on a white pill that flows from tab to tab like a drop of
    /// liquid. The tabs mark themselves with `liquidTab(_:)`; `selected` nil hides the pill.
    func liquidPill(selected: AnyHashable?) -> some View {
        modifier(LiquidPillBar(selected: selected))
    }
}

/// Asked again to measure where they are, and told whether the bar drew their glass. Measured
/// once at a bad moment (the app reopening itself after an update, its window not yet in place),
/// the pieces of glass and the tabs were somewhere else or nowhere, and the bar showed a plain
/// pill with no glass and no flow until something made it lay itself out again.
private struct LiquidPass: EnvironmentKey { static let defaultValue = 0 }
private struct LiquidGroundDrawn: EnvironmentKey { static let defaultValue = true }

private extension EnvironmentValues {
    var liquidPass: Int {
        get { self[LiquidPass.self] }
        set { self[LiquidPass.self] = newValue }
    }
    var liquidGroundDrawn: Bool {
        get { self[LiquidGroundDrawn.self] }
        set { self[LiquidGroundDrawn.self] = newValue }
    }
}

private struct LiquidGround: ViewModifier {
    @Environment(\.liquidPass) private var pass
    @Environment(\.liquidGroundDrawn) private var drawn

    func body(content: Content) -> some View {
        content
            .background {
                GeometryReader { geo in
                    Color.clear.preference(key: LiquidGroundFrames.self, value: [geo.frame(in: .named("liquidBar"))])
                }
                .id(pass)
            }
            // Until the bar has drawn the glass under the pill, this piece draws its own, so it
            // is never seen bare.
            .background {
                if !drawn { GlassLens(shape: Capsule(style: .continuous)) }
            }
    }
}

private struct LiquidTab: ViewModifier {
    let id: AnyHashable
    @Environment(\.liquidPass) private var pass

    func body(content: Content) -> some View {
        content.background {
            GeometryReader { geo in
                Color.clear.preference(key: LiquidTabFrames.self, value: [id: geo.frame(in: .named("liquidBar"))])
            }
            .id(pass)
        }
    }
}

private struct LiquidPillBar: ViewModifier {
    let selected: AnyHashable?
    @State private var frames: [AnyHashable: CGRect] = [:]
    @State private var grounds: [CGRect] = []
    @State private var size: CGSize = .zero
    @State private var pass = 0

    /// A place that is really in the bar: some size, and inside it.
    private func inBar(_ rect: CGRect) -> Bool {
        size.width > 0 && rect.width > 1 && rect.height > 1
            && CGRect(origin: .zero, size: size).insetBy(dx: -2, dy: -2).contains(rect)
    }

    func body(content: Content) -> some View {
        let groundsValid = !grounds.isEmpty && grounds.allSatisfy(inBar)
        let tabs = frames.filter { inBar($0.value) }
        content
            .environment(\.liquidPass, pass)
            .environment(\.liquidGroundDrawn, groundsValid)
            .onPreferenceChange(LiquidTabFrames.self) { frames = $0 }
            .onPreferenceChange(LiquidGroundFrames.self) { grounds = $0 }
            // Measured again whenever the window comes forward or changes size.
            .onReceive(NotificationCenter.default.publisher(for: NSWindow.didBecomeKeyNotification)) { _ in pass &+= 1 }
            .onReceive(NotificationCenter.default.publisher(for: NSWindow.didResizeNotification)) { _ in pass &+= 1 }
            .background {
                GeometryReader { geo in
                    Color.clear
                        .onAppear { size = geo.size }
                        .onChange(of: geo.size) { _, new in size = new }
                }
            }
            .background {
                ZStack {
                    if groundsValid {
                        DrawnGlass(outline: Capsules(frames: grounds), inner: Capsules(frames: grounds, inset: GlassLight.rimWidth))
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                    // Seen only within the glass: between two pieces of it the pill passes
                    // unseen, as if under the gap, and comes out in the next.
                    LiquidPill(selected: selected, frames: tabs)
                        .mask {
                            if groundsValid { Capsules(frames: grounds, inset: 3) } else { Rectangle() }
                        }
                }
            }
            .coordinateSpace(name: "liquidBar")
    }
}

/// Capsules at the given places, drawn rather than laid out, so they share the space the
/// frames were measured in whatever the direction of the interface.
private struct Capsules: View {
    let frames: [CGRect]
    var inset: CGFloat = 0

    var body: some View {
        Canvas { context, _ in
            for frame in frames where frame.width > 2 * inset && frame.height > 2 * inset {
                context.fill(Capsule(style: .continuous).path(in: frame.insetBy(dx: inset, dy: inset)), with: .color(.white))
            }
        }
    }
}

/// The pill itself, of glass. Moving to another tab, it stretches toward it, leading edge first, thinning
/// in the middle as it goes, while a drop of it stays behind joined by a neck that thins and
/// snaps; then it settles on the new tab.
///
/// After the "+" of the Takin app (CaptureMenu), and the classic metaball trick it uses: every
/// piece is drawn into one layer that is blurred and then cut back to a hard edge by its alpha,
/// so pieces close together merge into one shape with a bridge between them, and pieces apart
/// are clean shapes again. The words of the tabs are drawn above, so they never blur.
private struct LiquidPill: View {
    let selected: AnyHashable?
    let frames: [AnyHashable: CGRect]

    @Environment(\.calm) private var calm
    @State private var from: AnyHashable?
    @State private var progress = 1.0

    var body: some View {
        let target = selected.flatMap { frames[$0] }
        // Glass, cut to the liquid's shape: the goo needs a solid colour to find its edge, so it
        // is drawn as the mask, and what shows through it is frosted glass a little brighter than
        // the bar's, with light falling on it from above.
        let start = from.flatMap { frames[$0] }
        DrawnGlass(outline: Goo(from: start, to: target, progress: progress),
                   inner: Goo(from: start, to: target, progress: progress, inset: GlassLight.rimWidth),
                   lit: true)
            .opacity(target == nil ? 0 : 1)
            .animation(.easeOut(duration: 0.2), value: target == nil)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onChange(of: selected) { old, new in
                // From nothing (a page that is no tab was open), it only fades back in.
                guard let old, new != nil else {
                    from = nil
                    progress = 1
                    return
                }
                var still = Transaction()
                still.disablesAnimations = true
                withTransaction(still) {
                    from = old
                    progress = 0
                }
                withAnimation(calm ? .easeOut(duration: 0.18) : .spring(response: 0.55, dampingFraction: 0.78)) { progress = 1 }
            }
    }
}

private struct Goo: View, Animatable {
    var from: CGRect?
    var to: CGRect?
    var progress: Double
    /// Drawn this much inside the true shape; the rim is the difference between the two.
    var inset: CGFloat = 0

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        Canvas { context, _ in
            guard let to else { return }
            // Blurred, then cut back to an edge where the blur is about half there.
            context.addFilter(.alphaThreshold(min: 0.5, color: .white))
            context.addFilter(.blur(radius: 5))
            context.drawLayer { layer in
                for piece in pieces(to: to) where piece.width > 2 * inset && piece.height > 2 * inset {
                    layer.fill(Capsule(style: .continuous).path(in: piece.insetBy(dx: inset, dy: inset)), with: .color(.white))
                }
            }
        }
    }

    private func pieces(to: CGRect) -> [CGRect] {
        guard let from, from != to, progress < 1 else { return [to] }
        let p = max(0, progress)
        let settled = min(1, p)
        // The leading edge runs ahead, the trailing one follows late: the pill stretches.
        let lead = 1 - pow(1 - settled, 3) + (p - settled)
        let trail = settled * settled
        let right = to.midX > from.midX
        let minX = from.minX + (to.minX - from.minX) * (right ? trail : lead)
        let maxX = from.maxX + (to.maxX - from.maxX) * (right ? lead : trail)
        // Thinner in the middle of its run, as liquid is when it is pulled.
        let height = to.height * (1 - 0.32 * sin(.pi * settled))
        let body = CGRect(x: minX, y: to.midY - height / 2, width: max(height, maxX - minX), height: height)
        // What stays behind where it was, shrinking until the neck to it snaps.
        let left = pow(1 - settled, 1.6)
        let drop = CGRect(x: from.midX - from.width * left / 2, y: from.midY - from.height * left / 2,
                          width: from.width * left, height: from.height * left)
        return left > 0.02 ? [body, drop] : [body]
    }
}
