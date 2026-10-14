import SwiftUI

/// Sizes for a window seen from arm's length, not from a sofa.
enum Mac {
    static let edge: CGFloat = 48
    static let posterWidth: CGFloat = 156
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
    @Environment(\.lively) private var lively
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
                // A row of resting shadows is the dearest thing to draw while a shelf scrolls, so a
                // slow Mac gets a shadow only under the pointer.
                .shadow(color: hovering ? post.tint.opacity(0.55) : .black.opacity(lively ? 0.4 : 0), radius: hovering ? 24 : lively ? 8 : 0, y: hovering ? 14 : lively ? 4 : 0)
                .scaleEffect(hovering ? 1.07 : 1)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .background {
            GeometryReader { geo in
                Color.clear
                    .onAppear { spot.frame = geo.frame(in: .named(MainView.space)) }
                    .onChange(of: geo.frame(in: .named(MainView.space))) { _, new in spot.frame = new }
            }
        }
        // The pointer has come to rest here: open the card that says more about the title.
        .task(id: hovering) {
            guard hovering, let nav else { return }
            try? await Task.sleep(for: .milliseconds(700))
            guard !Task.isCancelled, spot.frame.width > 0, Date().timeIntervalSince(nav.lastScroll) > 0.4 else { return }
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
            RemoteImage(url: post.cover ?? post.poster.big ?? post.poster.large, blurhash: post.poster.blurhash, maxPixel: Int(PeekCard.size.width * Display.scale))
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
        .shadow(color: post.tint.opacity(0.35), radius: 40, y: 20)
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
            .shadow(color: flight.post.tint.opacity(0.4), radius: 40, y: 18)
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
            .rotation3DEffect(.degrees(-down * 2 * degrees), axis: (x: 1, y: 0, z: 0), perspective: 0.6)
            .rotation3DEffect(.degrees(across * 2 * degrees), axis: (x: 0, y: 1, z: 0), perspective: 0.6)
            .onContinuousHover(coordinateSpace: .local) { phase in
                guard !calm else { return }
                switch phase {
                case .active(let point): at = UnitPoint(x: point.x / max(1, size.width), y: point.y / max(1, size.height))
                case .ended: at = nil
                }
            }
            .background { GeometryReader { geo in Color.clear.onAppear { size = geo.size }.onChange(of: geo.size) { _, new in size = new } } }
            .animation(.interactiveSpring(response: 0.28, dampingFraction: 0.78), value: at)
    }

    @State private var size = CGSize.zero
}

extension View {
    /// Makes a card lean to the pointer and catch the light. See `Tilt`.
    func tilting<S: Shape>(_ shape: S, degrees: Double = 9) -> some View { modifier(Tilt(shape: shape, degrees: degrees)) }
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
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.2), value: hovering)
    }
}

/// A wide card for something half-watched: the scene, how far in, and what is left.
struct ContinueCard: View {
    let post: Post
    let action: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    private let size = CGSize(width: 288, height: 162)

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Mac.radius + 2, style: .continuous)
        Button(action: action) {
            RemoteImage(url: post.cover ?? post.poster.big ?? post.poster.large, blurhash: post.poster.blurhash, maxPixel: Int(size.width * Display.scale))
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
                .tilting(shape, degrees: 6)
                .shadow(color: post.tint.opacity(hovering ? 0.5 : 0), radius: hovering ? 24 : 0, y: hovering ? 14 : 0)
                .scaleEffect(hovering ? 1.04 : 1)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
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
    @ViewBuilder var card: (Post) -> Card

    @State private var position: Post.ID?
    @State private var hovering = false

    /// Each title once. A list drawn from several of the home feed's sections can name a title
    /// twice, and a row given the same id twice draws the second one as an empty space.
    private var shown: [Post] {
        var seen = Set<Int>()
        return posts.filter { seen.insert($0.id).inserted }
    }

    var body: some View {
        let posts = shown
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
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
            .scrollIndicators(.hidden)
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
