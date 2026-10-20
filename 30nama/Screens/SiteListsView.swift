import SwiftUI

/// The week of series and anime: which have a new episode on which day, from today on. From the
/// 30nama website's own API, as its schedule page has it.
struct ScheduleView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var kind = Schedule.Kind.series
    @State private var day = Schedule.day(of: Date())
    @State private var weeks: [Schedule.Kind: Schedule] = [:]
    @State private var failed = false

    private let columns = Array(repeating: GridItem(.fixed(Theme.posterWidth), spacing: 32), count: 7)

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 30) {
                HStack(alignment: .firstTextBaseline, spacing: 20) {
                    Text(loc(.weeklySchedule)).font(.app(56, .bold)).foregroundStyle(Theme.text1)
                    Spacer()
                    HStack(spacing: 12) {
                        Button(loc(.series)) { kind = .series }
                            .buttonStyle(PillButtonStyle(kind: .chip, selected: kind == .series))
                        Button(loc(.anime)) { kind = .anime }
                            .buttonStyle(PillButtonStyle(kind: .chip, selected: kind == .anime))
                    }
                    .focusSection()
                }
                days
                if let week = weeks[kind] {
                    let posts = week.on(day)
                    if posts.isEmpty {
                        Text(loc(.noMatches)).font(.app(28, .medium)).foregroundStyle(Theme.text2).padding(.vertical, 40)
                    } else {
                        LazyVGrid(columns: columns, alignment: .leading, spacing: 44) {
                            ForEach(posts) { post in PosterLink(post: post, titled: true) }
                        }
                        .focusSection()
                    }
                } else if failed {
                    ErrorState { Task { await load() } }.frame(height: 500)
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 44) {
                        ForEach(0..<7, id: \.self) { _ in
                            Skeleton(width: Theme.posterWidth, height: Theme.posterWidth * 1.5, radius: Theme.cardRadius)
                        }
                    }
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
        .task(id: kind) { await load() }
    }

    /// Today first, then the days after it, named in the interface's language.
    private var days: some View {
        let names = Self.weekdayNames(loc.lang)
        let today = Schedule.day(of: Date())
        return HStack(spacing: 12) {
            ForEach(Schedule.week(from: Date()), id: \.self) { d in
                Button(d == today ? loc(.today) : names[Schedule.symbolIndex(of: d)]) { day = d }
                    .buttonStyle(PillButtonStyle(kind: .chip, selected: day == d))
            }
        }
        .padding(.vertical, 8)
        .focusSection()
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
            weeks[asked] = Schedule(site: try await app.api.site(SiteQuery.schedule(asked)))
        } catch {
            if asked == kind, !Task.isCancelled { failed = true }
        }
    }
}

/// The site's lists of the best, each whole and in order, with the place on every poster.
struct TopListsView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var list = TopList.imdbMovies
    @State private var lists: [TopList: [TopList.Entry]] = [:]
    @State private var failed = false

    private let columns = Array(repeating: GridItem(.fixed(Theme.posterWidth), spacing: 32), count: 7)

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 30) {
                Text(loc(.topLists)).font(.app(56, .bold)).foregroundStyle(Theme.text1)
                HStack(spacing: 12) {
                    ForEach(TopList.allCases, id: \.self) { option in
                        Button(title(option)) { list = option }
                            .buttonStyle(PillButtonStyle(kind: .chip, selected: list == option))
                    }
                }
                .padding(.vertical, 8)
                .focusSection()
                if let entries = lists[list] {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 44) {
                        ForEach(entries) { entry in
                            PosterLink(post: entry.post, titled: true)
                                .overlay(alignment: .topLeading) { place(entry.rank) }
                        }
                    }
                    .focusSection()
                } else if failed {
                    ErrorState { Task { await load() } }.frame(height: 500)
                } else {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 44) {
                        ForEach(0..<7, id: \.self) { _ in
                            Skeleton(width: Theme.posterWidth, height: Theme.posterWidth * 1.5, radius: Theme.cardRadius)
                        }
                    }
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
        .task(id: list) { await load() }
    }

    private func place(_ rank: Int) -> some View {
        Text(loc.num(rank))
            .font(.app(24, .heavy))
            .foregroundStyle(.white)
            .frame(minWidth: 48, minHeight: 48)
            .padding(.horizontal, 6)
            .background(Capsule(style: .continuous).fill(.black.opacity(0.7)))
            .padding(12)
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
