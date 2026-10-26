import SwiftUI

// MARK: - Focus styles

/// Focus treatment for artwork: the card springs forward, catches a highlight along its top edge
/// and throws a glow in its own colour. No hard outline; the lift does the work.
struct CardButtonStyle: ButtonStyle {
    var radius: CGFloat = Theme.cardRadius
    var scale: CGFloat = 1.1
    var anchor: UnitPoint = .center
    /// Colour of the glow under the focused card, normally the title's own tint.
    var glow: Color = .white

    func makeBody(configuration: Configuration) -> some View {
        StyledLabel(configuration: configuration, style: self)
    }

    private struct StyledLabel: View {
        let configuration: Configuration
        let style: CardButtonStyle
        @Environment(\.isFocused) private var focused

        var body: some View {
            let shape = RoundedRectangle(cornerRadius: style.radius, style: .continuous)
            configuration.label
                .overlay {
                    // Specular sheen, as if the card tilted into the light.
                    LinearGradient(colors: [.white.opacity(0.26), .white.opacity(0.04), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)
                        .clipShape(shape)
                        .opacity(focused ? 1 : 0)
                }
                .overlay {
                    shape.strokeBorder(
                        LinearGradient(colors: [.white, .white.opacity(0.45)], startPoint: .top, endPoint: .bottom),
                        lineWidth: 3)
                        .opacity(focused ? 1 : 0)
                }
                .shadow(color: style.glow.opacity(focused ? 0.6 : 0), radius: 36, y: 10)
                .shadow(color: .black.opacity(focused ? 0.55 : 0), radius: 18, y: 22)
                .scaleEffect(focused ? (configuration.isPressed ? style.scale - 0.04 : style.scale) : 1, anchor: style.anchor)
                .animation(Theme.focusSpring, value: focused)
                .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
        }
    }
}

/// Buttons and chips: glass at rest, solid white in focus. The primary action rests on a lit
/// accent gradient, so "Play" is findable at a glance and focus still looks the same everywhere.
struct PillButtonStyle: ButtonStyle {
    enum Kind { case primary, secondary, chip }
    var kind: Kind = .secondary
    /// Chips can show a persistent "selected" state (current season, current sort).
    var selected = false

    func makeBody(configuration: Configuration) -> some View {
        StyledLabel(configuration: configuration, style: self)
    }

    private struct StyledLabel: View {
        let configuration: Configuration
        let style: PillButtonStyle
        @Environment(\.isFocused) private var focused

        var body: some View {
            let chip = style.kind == .chip
            let primary = style.kind == .primary
            let shape = Capsule(style: .continuous)
            configuration.label
                .font(.app(chip ? 24 : 27, .semibold))
                .lineLimit(1)
                .foregroundStyle(focused ? Theme.focusInk : (chip && !style.selected ? Theme.text2 : Color.white))
                .padding(.horizontal, chip ? 30 : 40)
                .frame(height: chip ? 58 : 76)
                .background {
                    ZStack {
                        if !primary {
                            Color.clear.glass(shape).opacity(focused ? 0 : 1)
                            shape.fill(Color.white.opacity(style.selected ? 0.16 : 0))
                        }
                        shape.fill(LinearGradient(colors: [Color(hex: 0xFF6A4D), Theme.accent, Color(hex: 0xD92B58)], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .opacity(primary && !focused ? 1 : 0)
                        shape.fill(Color.white).opacity(focused ? 1 : 0)
                    }
                }
                .overlay(alignment: .bottom) {
                    if chip && style.selected && !focused {
                        Capsule().fill(Theme.accent).frame(width: 26, height: 4).padding(.bottom, 6)
                    }
                }
                .shadow(color: (primary && !focused ? Theme.accent : Color.white).opacity(focused ? 0.35 : (primary ? 0.45 : 0)), radius: 24, y: 8)
                .scaleEffect(focused ? (configuration.isPressed ? 1.0 : 1.07) : 1)
                .animation(Theme.focusSpring, value: focused)
                .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
        }
    }
}

/// Full-width rows in Settings and the information panel: glass that turns white in focus.
struct RowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        StyledLabel(configuration: configuration)
    }

    private struct StyledLabel: View {
        let configuration: Configuration
        @Environment(\.isFocused) private var focused

        var body: some View {
            let shape = RoundedRectangle(cornerRadius: Theme.tileRadius, style: .continuous)
            configuration.label
                .foregroundStyle(focused ? Theme.focusInk : Theme.text1)
                .padding(.horizontal, 34)
                .frame(maxWidth: .infinity, minHeight: 86, alignment: .leading)
                .background {
                    ZStack {
                        Color.clear.glass(shape).opacity(focused ? 0 : 1)
                        shape.fill(Color.white).opacity(focused ? 1 : 0)
                    }
                }
                .scaleEffect(focused ? 1.025 : 1)
                .shadow(color: .black.opacity(focused ? 0.45 : 0), radius: 22, y: 14)
                .animation(Theme.focusSpring, value: focused)
        }
    }
}

// MARK: - Artwork

struct PosterArt: View {
    let post: Post
    var width: CGFloat = Theme.posterWidth
    /// False keeps to the placeholder for now, for cards that are far out of sight.
    var loads = true

    @Environment(\.loc) private var loc

    var body: some View {
        ZStack {
            Theme.surface
            // Still identifies the title if the image never arrives.
            Text(post.title(loc.lang))
                .font(.app(22, .medium))
                .foregroundStyle(Theme.text3)
                .multilineTextAlignment(.center)
                .lineLimit(3)
                .padding(14)
            RemoteImage(url: loads ? post.poster.url(forWidth: width * 1.1) : nil, blurhash: post.poster.blurhash, maxPixel: Int(width * 1.5 * UIScreen.main.scale))
        }
        .frame(width: width, height: width * 1.5)
        .overlay(alignment: .topLeading) {
            if post.comingSoon {
                Badge(text: loc(.soon), background: Color.black.opacity(0.6), foreground: Theme.text1)
            } else if post.freeStream {
                Badge(text: loc(.free), background: Theme.good, foreground: Theme.focusInk)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
    }
}

private struct Badge: View {
    let text: String
    let background: Color
    let foreground: Color

    var body: some View {
        Text(text)
            .font(.app(19, .bold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
            .background(background, in: Capsule())
            .padding(10)
    }
}

/// A poster that opens its title.
struct PosterLink: View {
    let post: Post
    var width: CGFloat = Theme.posterWidth
    /// Shows the title under the poster (grids). Rows leave it to the billboard.
    var titled = false
    var loads = true

    @Environment(\.loc) private var loc

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            NavigationLink(value: Route.details(PostRef(post))) {
                PosterArt(post: post, width: width, loads: loads)
            }
            .buttonStyle(CardButtonStyle(glow: post.tint))
            // Outside the button so the caption does not scale with the poster.
            if titled {
                Text(post.title(loc.lang))
                    .font(.app(22, .medium))
                    .foregroundStyle(Theme.text2)
                    .lineLimit(1)
                    .frame(width: width, alignment: .leading)
            }
        }
    }
}

// MARK: - Text

/// The facts about a title as glass chips: its scores (IMDb, Rotten Tomatoes, Metacritic,
/// MyAnimeList, 30nama), then year, runtime and age rating, then genres.
struct MetaLine: View {
    let post: Post
    var genres = 2
    /// Details pages have room for every score on a row of its own; the billboard keeps to one line.
    var expanded = false

    @Environment(\.loc) private var loc

    var body: some View {
        if expanded {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) { scores(all: true) }
                HStack(spacing: 10) { facts }
            }
            .lineLimit(1)
        } else {
            HStack(spacing: 10) {
                scores(all: false)
                facts
            }
            .lineLimit(1)
        }
    }

    @ViewBuilder
    private func scores(all: Bool) -> some View {
        if let imdb = post.imdbScore {
            ScoreChip(source: "IMDb", color: Color(hex: 0xF5C518), value: Fmt.score(imdb))
        }
        if let tomato = post.rottenTomatoes {
            // Fresh at 60% and above, as on the site.
            ScoreChip(source: "RT", color: tomato >= 60 ? Color(hex: 0xFA4B2A) : Color(hex: 0x7BB661), value: "\(tomato)%")
        }
        if all, let meta = post.metacritic {
            ScoreChip(source: "MC", color: meta >= 61 ? Color(hex: 0x66CC33) : (meta >= 40 ? Color(hex: 0xFFCC33) : Color(hex: 0xFF5A4D)), value: "\(meta)")
        }
        if all || post.rottenTomatoes == nil, let fan = post.fanScore {
            ScoreChip(source: fan.source, color: Color(hex: 0x6C8CFF), value: Fmt.score(fan.value))
        }
        if let score = post.siteScore {
            Chip {
                HStack(spacing: 6) {
                    Image(systemName: "star.fill").font(.system(size: 16, weight: .bold)).foregroundStyle(Theme.good)
                    Text(loc.num(Fmt.score(score)))
                }
            }
        }
    }

    @ViewBuilder
    private var facts: some View {
        if let year = post.year {
            Chip { Text(yearText(year)) }
        }
        if let runtime = Fmt.runtime(minutes: post.runtimeMinutes, lang: loc.lang) {
            Chip { Text(runtime) }
        }
        if let age = post.age {
            Chip { Text(age).environment(\.layoutDirection, .leftToRight) }
        }
        if !post.genres.isEmpty {
            Text(post.genres.prefix(genres).map(\.name).joined(separator: loc.lang == .fa ? "، " : ", "))
                .font(.app(24, .medium))
                .foregroundStyle(Theme.text2)
                .padding(.leading, 8)
        }
    }

    private func yearText(_ year: Int) -> String {
        guard post.isSeries, let end = post.yearEnd, end != year else { return loc.num(year) }
        return loc.num(year) + "–" + loc.num(end)
    }
}

/// A score with the short name of its source in that source's colour.
private struct ScoreChip: View {
    let source: String
    let color: Color
    let value: String

    var body: some View {
        Chip {
            HStack(spacing: 8) {
                Text(source).font(.app(19, .heavy)).foregroundStyle(color)
                Text(value)
            }
            .environment(\.layoutDirection, .leftToRight)
        }
    }
}

struct Chip<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .font(.app(22, .semibold))
            .foregroundStyle(Theme.text1)
            .padding(.horizontal, 16)
            .frame(height: 42)
            .glass(Capsule(style: .continuous))
    }
}

/// Small coloured label above a title: what kind of thing it is.
struct Eyebrow: View {
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Capsule().fill(Theme.accent).frame(width: 6, height: 24)
            Text(text)
                .font(.app(24, .semibold))
                .foregroundStyle(Theme.text1.opacity(0.9))
                .lineLimit(1)
        }
    }
}

/// Text that swaps by dissolving through a blur instead of a plain crossfade.
struct BlurFade: ViewModifier {
    let radius: CGFloat
    let opacity: Double
    func body(content: Content) -> some View { content.blur(radius: radius).opacity(opacity) }
}

extension AnyTransition {
    static var blurFade: AnyTransition {
        .modifier(active: BlurFade(radius: 14, opacity: 0), identity: BlurFade(radius: 0, opacity: 1))
    }
}

// MARK: - States

struct ErrorState: View {
    var message: String? = nil
    let retry: () -> Void

    @Environment(\.loc) private var loc

    var body: some View {
        VStack(spacing: 28) {
            Image(systemName: "wifi.exclamationmark")
                .font(.system(size: 64, weight: .light))
                .foregroundStyle(Theme.text3)
            Text(message ?? "\(loc(.error)). \(loc(.tryAgain))")
                .font(.app(28))
                .foregroundStyle(Theme.text2)
                .multilineTextAlignment(.center)
            Button(loc(.retry), action: retry)
                .buttonStyle(PillButtonStyle(kind: .primary))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// `ErrorState` for what comes from the website: says why when the site gave no answer at all
/// (it is filtered in Iran), and asks it again at once on retry.
struct SiteErrorState: View {
    let retry: () -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc
    @State private var outOfReach = false

    var body: some View {
        ErrorState(message: outOfReach ? loc(.siteOutOfReach) : nil) {
            Task {
                await app.api.siteAgain()
                retry()
            }
        }
        // Only in Iran, where the site is filtered and a VPN is the answer; elsewhere as before.
        .task { outOfReach = app.inIran ? !(await app.api.siteInReach) : false }
    }
}

/// Placeholder block with a light sweeping across it.
struct Skeleton: View {
    var width: CGFloat? = nil
    var height: CGFloat
    var radius: CGFloat = 12

    @State private var swept = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        shape
            .fill(Color.white.opacity(0.07))
            .frame(width: width, height: height)
            .overlay {
                GeometryReader { geo in
                    LinearGradient(colors: [.clear, .white.opacity(0.12), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: geo.size.width * 0.7)
                        .offset(x: swept ? geo.size.width * 1.2 : -geo.size.width * 0.9)
                }
                .clipShape(shape)
            }
            .onAppear {
                withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: false)) { swept = true }
            }
    }
}

struct Toast: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.app(26, .medium))
            .foregroundStyle(Theme.text1)
            .padding(.horizontal, 34)
            .padding(.vertical, 18)
            .glass(Capsule(style: .continuous))
            .shadow(color: .black.opacity(0.5), radius: 24, y: 12)
    }
}

/// The "30nama" wordmark.
struct Wordmark: View {
    var size: CGFloat = 40

    var body: some View {
        (Text("30").foregroundColor(Theme.accent) + Text("nama").foregroundColor(Theme.text1))
            .font(.app(size, .heavy))
            .environment(\.layoutDirection, .leftToRight)
    }
}
