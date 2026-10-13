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
        Hovered { hovering in
            configuration.label
                .font(.app(16, .bold))
                .foregroundStyle(tint == .white ? Theme.focusInk : .white)
                .padding(.horizontal, 26)
                .frame(height: 46)
                .background(Capsule(style: .continuous).fill(tint))
                .shadow(color: tint.opacity(hovering ? 0.45 : 0.2), radius: hovering ? 20 : 10, y: 6)
                .scaleEffect(configuration.isPressed ? 0.96 : hovering ? 1.04 : 1)
                .animation(Theme.focusSpring, value: hovering)
                .animation(Theme.focusSpring, value: configuration.isPressed)
        }
    }
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
    let action: () -> Void

    @Environment(\.loc) private var loc
    @State private var hovering = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: Mac.radius, style: .continuous)
        Button(action: action) {
            RemoteImage(url: post.poster.url(forWidth: width * 1.1), blurhash: post.poster.blurhash, maxPixel: Int(width * 1.5 * Display.scale))
                .frame(width: width, height: width * 1.5)
                .overlay(alignment: .bottom) { caption }
                .overlay(alignment: .topLeading) { badge }
                .clipShape(shape)
                .overlay { shape.strokeBorder(Color.white.opacity(hovering ? 0.9 : 0.08), lineWidth: hovering ? 2 : 1) }
                .shadow(color: hovering ? post.tint.opacity(0.55) : .black.opacity(0.4), radius: hovering ? 24 : 8, y: hovering ? 14 : 4)
                .scaleEffect(hovering ? 1.07 : 1)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
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
                .shadow(color: hovering ? post.tint.opacity(0.5) : .black.opacity(0.4), radius: hovering ? 24 : 8, y: hovering ? 14 : 4)
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

    var body: some View {
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
            .overlay(alignment: .leading) { arrow(-1) }
            .overlay(alignment: .trailing) { arrow(1) }
        }
        .onHover { hovering = $0 }
    }

    private var index: Int { posts.firstIndex { $0.id == position } ?? 0 }

    @ViewBuilder
    private func arrow(_ direction: Int) -> some View {
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
