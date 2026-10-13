import SwiftUI

/// One title: its artwork behind, what it is, the user's own stars, and the people in it.
struct DetailsView: View {
    /// What the list already knew about the title, shown at once while the rest loads.
    let preview: Post

    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var loaded: Post?

    private var post: Post { loaded ?? preview }

    var body: some View {
        ZStack {
            Theme.bg0
            StageView(post: post, dim: 0.3, settle: .zero)
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 40) {
                    header
                    if !post.cast.isEmpty { cast }
                    facts
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 130)
                .padding(.bottom, 60)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollIndicators(.hidden)
        }
        .task {
            loaded = (try? await app.api.get("single", ["post_id": String(preview.id)])).flatMap { Post($0) }
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(alignment: .top, spacing: 40) {
            let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
            RemoteImage(url: post.poster.big ?? post.poster.large, blurhash: post.poster.blurhash, maxPixel: Int(360 * Display.scale))
                .frame(width: 240, height: 360)
                .clipShape(shape)
                .overlay { shape.strokeBorder(Color.white.opacity(0.14), lineWidth: 1) }
                .shadow(color: post.tint.opacity(0.4), radius: 40, y: 18)

            VStack(alignment: .leading, spacing: 14) {
                Text(loc.kindLine(post))
                    .font(.app(14, .semibold))
                    .foregroundStyle(Theme.text2)
                Text(post.title(loc.lang))
                    .font(.app(48, .heavy))
                    .foregroundStyle(LinearGradient(colors: [.white, Theme.text1.opacity(0.84)], startPoint: .top, endPoint: .bottom))
                    .lineLimit(2)
                    .minimumScaleFactor(0.6)
                    .shadow(color: .black.opacity(0.5), radius: 18, y: 6)
                if let other = post.alternateTitle(loc.lang) {
                    Text(other).font(.app(18, .medium)).foregroundStyle(Theme.text2)
                }
                MetaRow(post: post).padding(.top, 2)
                Text(post.plot(loc.lang))
                    .font(.app(16))
                    .foregroundStyle(Theme.text2)
                    .lineSpacing(6)
                    .frame(maxWidth: 700, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 14) {
                    Button { app.showToast(loc(.soon)) } label: {
                        Label(loc(post.resume != nil ? .resume : .play), systemImage: "play.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    HStack(spacing: 12) {
                        Text(loc(.yourRating)).font(.app(15, .medium)).foregroundStyle(Theme.text2)
                        StarRater(value: app.ratings.stars(for: post.id)) { app.rate(post, stars: $0) }
                    }
                    .padding(.horizontal, 18)
                    .frame(height: 46)
                    .glass(Capsule(style: .continuous))
                }
                .padding(.top, 10)
            }
            Spacer(minLength: 0)
        }
    }

    // MARK: People and facts

    private var cast: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(loc(.cast)).font(.app(20, .bold)).foregroundStyle(Theme.text1)
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 20) {
                    ForEach(post.cast) { person in
                        VStack(spacing: 8) {
                            ZStack {
                                Circle().fill(Theme.surface2)
                                Image(systemName: "person.fill").font(.system(size: 30)).foregroundStyle(Theme.text3)
                                if let face = person.poster {
                                    RemoteImage(url: face.url(forWidth: 96), blurhash: face.blurhash, maxPixel: Int(96 * Display.scale))
                                }
                            }
                            .frame(width: 96, height: 96)
                            .clipShape(Circle())
                            Text(person.name).font(.app(13, .semibold)).foregroundStyle(Theme.text1).lineLimit(1)
                            if let role = person.role {
                                Text(role).font(.app(12)).foregroundStyle(Theme.text3).lineLimit(1)
                            }
                        }
                        .frame(width: 112)
                    }
                }
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
        }
    }

    private var facts: some View {
        let rows: [(S, String)] = [
            (.director, post.directors), (.creator, post.creators), (.writer, post.writers),
            (.network, post.networks), (.country, post.countries),
        ].compactMap { key, names in names.isEmpty ? nil : (key, names.joined(separator: loc.lang == .en ? ", " : "، ")) }
        return VStack(alignment: .leading, spacing: 10) {
            ForEach(rows, id: \.0) { key, value in
                HStack(alignment: .firstTextBaseline, spacing: 16) {
                    Text(loc(key)).font(.app(14, .medium)).foregroundStyle(Theme.text3).frame(width: 110, alignment: .leading)
                    Text(value).font(.app(15)).foregroundStyle(Theme.text1)
                }
            }
        }
    }
}

/// Five stars that take half steps: the left half of a star is the half, the right half the whole.
struct StarRater: View {
    let value: Double?
    /// Called with the new rating, or nil when the current one is clicked again to clear it.
    let set: (Double?) -> Void

    @State private var preview: Double?

    var body: some View {
        let shown = preview ?? value ?? 0
        HStack(spacing: 2) {
            ForEach(1...5, id: \.self) { n in
                Image(systemName: shown >= Double(n) ? "star.fill" : shown >= Double(n) - 0.5 ? "star.leadinghalf.filled" : "star")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(shown >= Double(n) - 0.5 ? Color(hex: 0xF5C518) : Theme.text3)
                    .frame(width: 28, height: 34)
                    .overlay {
                        HStack(spacing: 0) {
                            half(Double(n) - 0.5)
                            half(Double(n))
                        }
                    }
            }
        }
        .onHover { if !$0 { preview = nil } }
        .animation(.easeOut(duration: 0.12), value: shown)
    }

    private func half(_ stars: Double) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .onHover { if $0 { preview = stars } }
            .onTapGesture { set(value == stars ? nil : stars) }
    }
}
