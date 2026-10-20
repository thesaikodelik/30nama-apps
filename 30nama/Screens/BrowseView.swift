import SwiftUI

/// Entry point to the whole catalogue: categories as chips, genres as picture tiles.
struct BrowseView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var menu: Menu?
    @State private var failed = false

    private let columns = Array(repeating: GridItem(.fixed(416), spacing: 32), count: 4)

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 34) {
                Text(loc(.browse))
                    .font(.app(56, .bold))
                    .foregroundStyle(Theme.text1)

                if let menu {
                    ScrollView(.horizontal) {
                        HStack(spacing: 12) {
                            // What only the 30nama website has, through its own API.
                            if app.api.hasSite {
                                NavigationLink(value: Route.schedule) { Label(loc(.weeklySchedule), systemImage: "calendar") }
                                    .buttonStyle(PillButtonStyle(kind: .chip))
                                NavigationLink(value: Route.topLists) { Label(loc(.topLists), systemImage: "trophy") }
                                    .buttonStyle(PillButtonStyle(kind: .chip))
                            }
                            ForEach(menu.categories) { category in
                                NavigationLink(value: Route.catalog(slug: category.slug, title: category.name)) {
                                    Text(category.name)
                                }
                                .buttonStyle(PillButtonStyle(kind: .chip))
                            }
                        }
                        .padding(.vertical, 12)
                    }
                    .scrollIndicators(.hidden)
                    .scrollClipDisabled()
                    .focusSection()

                    Text(loc(.genres))
                        .font(.app(34, .bold))
                        .foregroundStyle(Theme.text1)
                        .padding(.top, 6)

                    LazyVGrid(columns: columns, alignment: .leading, spacing: 32) {
                        ForEach(menu.genres) { genre in
                            NavigationLink(value: Route.catalog(slug: genre.slug, title: genre.name)) {
                                GenreTile(genre: genre)
                            }
                            .buttonStyle(CardButtonStyle(radius: Theme.tileRadius, scale: 1.06))
                        }
                    }
                    .focusSection()
                } else if failed {
                    ErrorState { Task { await load() } }.frame(height: 600)
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 32) {
                        ForEach(0..<8, id: \.self) { _ in Skeleton(width: 416, height: 234, radius: Theme.tileRadius) }
                    }
                    .padding(.top, 110)
                }
            }
            .padding(.horizontal, Theme.edge + 4)
            .padding(.top, 20)
            .padding(.bottom, 80)
        }
        .scrollIndicators(.hidden)
        .scrollClipDisabled()
        .background(AmbientBackground())
        .overlay(alignment: .top) { TopShade() }
        .task { await load() }
    }

    private func load() async {
        failed = false
        do {
            menu = try await app.menu()
        } catch {
            failed = true
        }
    }
}

private struct GenreTile: View {
    let genre: MenuEntry

    @Environment(\.loc) private var loc

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Theme.surface
            RemoteImage(url: genre.full ?? genre.thumb, maxPixel: Int(416 * UIScreen.main.scale))
            LinearGradient(colors: [.black.opacity(0.88), .black.opacity(0.1)], startPoint: .bottom, endPoint: .top)
            VStack(alignment: .leading, spacing: 2) {
                Text(genre.name)
                    .font(.app(34, .bold))
                    .foregroundStyle(Theme.text1)
                if let count = genre.count {
                    Text(Fmt.count(count, lang: loc.lang))
                        .font(.app(21, .medium))
                        .foregroundStyle(Theme.text2)
                }
            }
            .padding(20)
        }
        .frame(width: 416, height: 234)
        .clipShape(RoundedRectangle(cornerRadius: Theme.tileRadius, style: .continuous))
    }
}
