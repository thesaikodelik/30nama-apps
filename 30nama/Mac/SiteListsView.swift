import SwiftUI

/// The week of series and anime: which have a new episode on which day, from today on. From the
/// 30nama website's own API, as its schedule page has it.
struct ScheduleView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var kind = Schedule.Kind.series
    @State private var day = Schedule.day(of: Date())
    @State private var weeks: [Schedule.Kind: Schedule] = [:]
    @State private var failed = false

    private let columns = [GridItem(.adaptive(minimum: Mac.posterWidth, maximum: Mac.posterWidth + 40), spacing: 20)]

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 22) {
                    HStack(alignment: .firstTextBaseline, spacing: 14) {
                        Text(loc(.weeklySchedule)).font(.app(34, .bold)).foregroundStyle(Theme.text1)
                        Spacer(minLength: 0)
                        PillChoice(title: loc(.series), selected: kind == .series) { kind = .series }
                        PillChoice(title: loc(.anime), selected: kind == .anime) { kind = .anime }
                    }
                    days
                    if let week = weeks[kind] {
                        let posts = week.on(day)
                        if posts.isEmpty {
                            Text(loc(.noMatches)).font(.app(16, .medium)).foregroundStyle(Theme.text2)
                                .frame(maxWidth: .infinity).padding(.vertical, 30)
                        } else {
                            LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                                ForEach(posts) { post in
                                    PosterCard(post: post) { nav.open(post) }
                                }
                            }
                        }
                    } else if failed {
                        Button(loc(.retry)) { Task { await load() } }
                            .buttonStyle(GlassButtonStyle())
                            .frame(maxWidth: .infinity)
                    } else {
                        ProgressView().controlSize(.regular).frame(maxWidth: .infinity).padding(.vertical, 30)
                    }
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 96)
                .padding(.bottom, 60)
            }
            .scrollIndicators(.hidden)
        }
        .task(id: kind) { await load() }
    }

    /// Today first, then the days after it, named in the interface's language.
    private var days: some View {
        let names = Self.weekdayNames(loc.lang)
        let today = Schedule.day(of: Date())
        return HStack(spacing: 8) {
            ForEach(Schedule.week(from: Date()), id: \.self) { d in
                PillChoice(title: d == today ? loc(.today) : names[Schedule.symbolIndex(of: d)], selected: day == d) { day = d }
            }
            Spacer(minLength: 0)
        }
    }

    static func weekdayNames(_ lang: Lang) -> [String] {
        let f = DateFormatter()
        f.locale = Locale(identifier: lang == .fa ? "fa_IR" : lang == .ar ? "ar" : "en_US")
        return f.weekdaySymbols
    }

    private func load() async {
        guard weeks[kind] == nil else { return }
        failed = false
        let asked = kind
        do {
            let week = Schedule(site: try await app.api.site(SiteQuery.schedule(asked)))
            weeks[asked] = week
        } catch {
            if asked == kind, !Task.isCancelled { failed = true }
        }
    }
}

/// The site's lists of the best, each whole and in order, with the place on every poster.
struct TopListsView: View {
    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    @State private var list = TopList.imdbMovies
    @State private var lists: [TopList: [TopList.Entry]] = [:]
    @State private var failed = false

    private let columns = [GridItem(.adaptive(minimum: Mac.posterWidth, maximum: Mac.posterWidth + 40), spacing: 20)]

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 22) {
                    Text(loc(.topLists)).font(.app(34, .bold)).foregroundStyle(Theme.text1)
                    HStack(spacing: 8) {
                        ForEach(TopList.allCases, id: \.self) { option in
                            PillChoice(title: title(option), selected: list == option) { list = option }
                        }
                        Spacer(minLength: 0)
                    }
                    if let entries = lists[list] {
                        LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                            ForEach(entries) { entry in
                                PosterCard(post: entry.post) { nav.open(entry.post) }
                                    .overlay(alignment: .topLeading) { place(entry.rank) }
                            }
                        }
                    } else if failed {
                        Button(loc(.retry)) { Task { await load() } }
                            .buttonStyle(GlassButtonStyle())
                            .frame(maxWidth: .infinity)
                    } else {
                        ProgressView().controlSize(.regular).frame(maxWidth: .infinity).padding(.vertical, 30)
                    }
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 96)
                .padding(.bottom, 60)
            }
            .scrollIndicators(.hidden)
        }
        .task(id: list) { await load() }
    }

    private func place(_ rank: Int) -> some View {
        Text(loc.num(rank))
            .font(.app(14, .heavy))
            .foregroundStyle(.white)
            .frame(minWidth: 30, minHeight: 30)
            .padding(.horizontal, 4)
            .background(Capsule(style: .continuous).fill(.black.opacity(0.7)))
            .overlay(Capsule(style: .continuous).strokeBorder(Color.white.opacity(0.25), lineWidth: 1))
            .padding(8)
            .allowsHitTesting(false)
    }

    private func title(_ list: TopList) -> String {
        switch list {
        case .imdbMovies: return loc(.imdbTopMovies)
        case .imdbSeries: return loc(.imdbTopSeries)
        case .siteMovies: return loc(.siteTopMovies)
        case .siteSeries: return loc(.siteTopSeries)
        }
    }

    private func load() async {
        guard lists[list] == nil else { return }
        failed = false
        let asked = list
        do {
            lists[asked] = TopList.entries(site: try await app.api.site(SiteQuery.topList(asked)))
        } catch {
            if asked == list, !Task.isCancelled { failed = true }
        }
    }
}

/// A way into one of the site's own pages, at the top of Explore.
struct SiteTile: View {
    let title: String
    let symbol: String
    let action: () -> Void

    @Environment(\.hue) private var hue
    @State private var hovering = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: symbol).font(.system(size: 24, weight: .semibold)).foregroundStyle(hue ?? Theme.accent)
                Text(title).font(.app(18, .bold)).foregroundStyle(Theme.text1)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 20)
            .frame(height: 76)
            .background(Color.white.opacity(hovering ? 0.12 : 0.06), in: shape)
            .overlay { shape.strokeBorder(Color.white.opacity(hovering ? 0.9 : 0.08), lineWidth: hovering ? 2 : 1) }
            .scaleEffect(hovering ? 1.02 : 1)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(Theme.focusSpring, value: hovering)
    }
}
