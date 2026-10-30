import SwiftUI
import Security

/// User preferences, persisted in `UserDefaults`.
struct Settings: Equatable {
    /// English until the user picks another.
    var lang: Lang = .en
    /// The user chose to browse without signing in.
    var guest = false
    /// "off", "fa" or "en".
    var defaultSubtitle = "fa"
    var subtitleSize = 46
    var subtitleColor = SubtitleColor.white
    var subtitleBackground = SubtitleBackground.none
    var autoplayNext = true
    /// Jump over the opening titles and the recap without being asked.
    var autoSkip = false
    /// Stereo by default: the original mix is often silent on the Apple TV's own speakers.
    var audioMix: AudioMix = .stereo
    /// Mac only: how much the app moves when nobody is touching it.
    var effects = Effects.auto
    /// Mac only: where downloads are put. Nil is the user's Downloads folder.
    var downloadFolder: String?
    /// Mac only: a cap on download speed in megabytes a second; zero is none.
    var downloadLimit = 0
    /// Mac only: how many files come in at once; the rest wait their turn, oldest first.
    var downloadsAtOnce = 1
    /// Mac only: the hours downloads may run in (see `DownloadHours`); nil is any time.
    var downloadFrom: Int?
    var downloadTo: Int?
    /// Mac only: how many seconds of a stream to hold ahead; zero leaves it to the player.
    var bufferAhead = 0
    /// Use the adaptive stream instead of the highest fixed rendition.
    var preferAdaptive = false
    /// Mac only: a slow line's ceiling, 1080, 720 or 480 lines; zero is none.
    var qualityCap = 0
    var reduceMotion = false

    static let subtitleSizes = [38, 46, 54]

    static func load(_ d: UserDefaults = .standard) -> Settings {
        var s = Settings()
        if let v = d.string(forKey: "lang").flatMap(Lang.init(rawValue:)) { s.lang = v }
        s.guest = d.bool(forKey: "guest")
        if let v = d.string(forKey: "sub") { s.defaultSubtitle = v }
        if let v = d.object(forKey: "subSize") as? Int, subtitleSizes.contains(v) { s.subtitleSize = v }
        if let v = d.string(forKey: "subColor").flatMap(SubtitleColor.init(rawValue:)) { s.subtitleColor = v }
        if let v = d.string(forKey: "subBackground").flatMap(SubtitleBackground.init(rawValue:)) { s.subtitleBackground = v }
        if let v = d.object(forKey: "autoplay") as? Bool { s.autoplayNext = v }
        s.autoSkip = d.bool(forKey: "autoSkip")
        if let v = d.string(forKey: "audioMix").flatMap(AudioMix.init(rawValue:)) { s.audioMix = v }
        if let v = d.string(forKey: "effects").flatMap(Effects.init(rawValue:)) { s.effects = v }
        s.downloadFolder = d.string(forKey: "downloadFolder")
        s.downloadLimit = max(0, d.integer(forKey: "downloadLimit"))
        if let v = d.object(forKey: "downloadsAtOnce") as? Int, v > 0 { s.downloadsAtOnce = v }
        s.downloadFrom = (d.object(forKey: "downloadFrom") as? Int).flatMap { (0..<24).contains($0) ? $0 : nil }
        s.downloadTo = (d.object(forKey: "downloadTo") as? Int).flatMap { (0..<24).contains($0) ? $0 : nil }
        s.bufferAhead = max(0, d.integer(forKey: "bufferAhead"))
        s.preferAdaptive = d.bool(forKey: "adaptive")
        s.qualityCap = max(0, d.integer(forKey: "qualityCap"))
        s.reduceMotion = d.bool(forKey: "calm")
        return s
    }

    func save(_ d: UserDefaults = .standard) {
        d.set(qualityCap, forKey: "qualityCap")
        d.set(lang.rawValue, forKey: "lang")
        d.set(guest, forKey: "guest")
        d.set(defaultSubtitle, forKey: "sub")
        d.set(subtitleSize, forKey: "subSize")
        d.set(subtitleColor.rawValue, forKey: "subColor")
        d.set(subtitleBackground.rawValue, forKey: "subBackground")
        d.set(autoplayNext, forKey: "autoplay")
        d.set(autoSkip, forKey: "autoSkip")
        d.set(audioMix.rawValue, forKey: "audioMix")
        d.set(effects.rawValue, forKey: "effects")
        d.set(downloadFolder, forKey: "downloadFolder")
        d.set(downloadLimit, forKey: "downloadLimit")
        d.set(downloadsAtOnce, forKey: "downloadsAtOnce")
        d.set(downloadFrom, forKey: "downloadFrom")
        d.set(downloadTo, forKey: "downloadTo")
        d.set(bufferAhead, forKey: "bufferAhead")
        d.set(preferAdaptive, forKey: "adaptive")
        d.set(reduceMotion, forKey: "calm")
    }
}

enum SubtitleColor: String, CaseIterable {
    case white, yellow, cyan, green

    var label: S {
        switch self {
        case .white: return .white
        case .yellow: return .yellow
        case .cyan: return .cyan
        case .green: return .green
        }
    }

    var color: PlatformColor {
        switch self {
        case .white: return .white
        case .yellow: return PlatformColor(red: 1.0, green: 0.90, blue: 0.30, alpha: 1)
        case .cyan: return PlatformColor(red: 0.45, green: 0.93, blue: 1.0, alpha: 1)
        case .green: return PlatformColor(red: 0.55, green: 0.95, blue: 0.55, alpha: 1)
        }
    }
}

/// How much ambient motion the Mac app shows: the drifting backdrop and the like. Automatic gives
/// a fast Mac all of it and spares a slow one, where the same motion means heat and fan noise.
enum Effects: String, CaseIterable {
    case auto, full, light

    var label: S {
        switch self {
        case .auto: return .effectsAuto
        case .full: return .effectsFull
        case .light: return .effectsLight
        }
    }
}

/// What sits behind subtitle text: nothing (outlined text), a black plate at half or full
/// strength, or a plate that blurs the picture behind it, lightly or fully.
enum SubtitleBackground: String, CaseIterable {
    case none, half, solid, blurHalf, blur

    var label: S {
        switch self {
        case .none: return .bgNone
        case .half: return .bgHalf
        case .solid: return .bgSolid
        case .blurHalf: return .bgBlurHalf
        case .blur: return .bgBlur
        }
    }

    /// Opacity of the black plate; 0 for the styles that have none.
    var plateOpacity: Double {
        switch self {
        case .half: return 0.5
        case .solid: return 1
        case .none, .blurHalf, .blur: return 0
        }
    }

    /// Strength of the blur plate, 0...1; 0 for the styles that have none.
    var blurStrength: Double {
        switch self {
        case .blurHalf: return 0.5
        case .blur: return 1
        case .none, .half, .solid: return 0
        }
    }
}

/// Keeps the session across launches: the token in the Keychain, the profile next to the settings.
enum SessionStore {
    private static let account = "session-token"
    private static var service: String { Bundle.main.bundleIdentifier ?? "30nama" }
    private static var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }

    #if os(macOS)
    /// A Mac build signed to run locally is a different app, as far as the keychain can tell,
    /// every time it is built, and the keychain would ask for the login password after each
    /// update. Only a build signed by a team uses it; the others keep the token in a file that
    /// nobody but the user can read.
    private static let keychainFits: Bool = {
        var code: SecCode?
        var fixed: SecStaticCode?
        var info: CFDictionary?
        guard SecCodeCopySelf([], &code) == errSecSuccess, let code,
              SecCodeCopyStaticCode(code, [], &fixed) == errSecSuccess, let fixed,
              SecCodeCopySigningInformation(fixed, SecCSFlags(rawValue: kSecCSSigningInformation), &info) == errSecSuccess,
              let signing = info as? [String: Any] else { return false }
        return signing[kSecCodeInfoTeamIdentifier as String] != nil
    }()

    private static var file: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(service, isDirectory: true).appendingPathComponent("session")
    }

    private static func writeFile(_ token: String) -> Bool {
        let manager = FileManager.default
        try? manager.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        return manager.createFile(atPath: file.path, contents: Data(token.utf8), attributes: [.posixPermissions: 0o600])
    }
    #else
    private static let keychainFits = true
    #endif

    private static func keep(_ token: String) -> Bool {
        #if os(macOS)
        if !keychainFits { return writeFile(token) }
        #endif
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = Data(token.utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        return SecItemAdd(add as CFDictionary, nil) == errSecSuccess
    }

    private static func kept() -> String? {
        #if os(macOS)
        if !keychainFits { return (try? Data(contentsOf: file)).flatMap { String(data: $0, encoding: .utf8) } }
        #endif
        var read = query
        read[kSecReturnData as String] = true
        read[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(read as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func save(_ user: User) {
        let stored = keep(user.token)
        let d = UserDefaults.standard
        // Without Keychain access (unsigned development builds) fall back to the defaults store.
        d.set(stored ? nil : user.token, forKey: "session.token")
        d.set(["id": user.id, "name": user.name, "type": user.type, "expiry": user.expiry, "expiryLocal": user.expiryLocal,
               "avatar": user.avatar?.absoluteString].compactMapValues { $0 }, forKey: "session.profile")
    }

    static func load() -> User? {
        let d = UserDefaults.standard
        guard let token = kept() ?? d.string(forKey: "session.token"), !token.isEmpty else { return nil }
        let p = d.dictionary(forKey: "session.profile") as? [String: String] ?? [:]
        return User(JSON(["user_session": token, "user_id": p["id"], "user_name": p["name"], "user_type": p["type"], "user_expire": p["expiry"],
                          "user_expire_local": p["expiryLocal"], "user_avatar": p["avatar"]].compactMapValues { $0 }))
    }

    static func clear() {
        #if os(macOS)
        try? FileManager.default.removeItem(at: file)
        if keychainFits { SecItemDelete(query as CFDictionary) }
        #else
        SecItemDelete(query as CFDictionary)
        #endif
        UserDefaults.standard.removeObject(forKey: "session.token")
        UserDefaults.standard.removeObject(forKey: "session.profile")
    }
}

/// Everything the player needs to start.
struct PlaybackRequest: Identifiable {
    let id = UUID()
    let post: Post
    let items: [StreamItem]
    let index: Int
    /// Position to resume from, when it belongs to the item being started.
    let resume: ContinueWatching?
}

/// App-wide state: boot, session, settings and the shared API client.
@MainActor
@Observable
final class AppModel {
    enum Phase: Equatable { case booting, failed, login, main }

    private(set) var phase: Phase = .booting
    private(set) var settings = Settings.load()
    private(set) var user: User?
    private(set) var apiHost: String?
    /// Connected through the host that answers only inside Iran: TMDB, YouTube and the website are
    /// filtered, so what comes from them is not waited for (see `APIClient.inIran`).
    private(set) var inIran = false
    /// Bumped when the catalogue itself may differ: sign-in, sign-out, language.
    private(set) var contentVersion = 0
    /// Bumped when watch progress changed (the player closed).
    private(set) var watchVersion = 0
    /// Goes up when "continue watching" should be asked for again, after a title was taken off it.
    private(set) var continueVersion = 0
    var playback: PlaybackRequest?
    private(set) var toast: String?
    /// The user's own ratings. The copy on the device is what the app works from; iCloud keeps
    /// another, so they follow the user to other Apple TVs and survive tvOS clearing the app's storage.
    private(set) var ratings = UserDefaults.standard.data(forKey: "ratings").flatMap(RatingBook.init(data:)) ?? RatingBook()
    /// How the last exchange with iCloud went.
    private(set) var cloud = CloudState.idle

    enum CloudState: Equatable { case idle, syncing, synced, signedOut, failed }

    let api = APIClient()

    @ObservationIgnored private var homeCache: (feed: HomeFeed, at: Date, lang: Lang)?
    /// The home page has arrived at least once since launch. The opening title waits a little for
    /// it, so the page is there behind its glass.
    private(set) var homeLoaded = false
    /// The home page shown is the one saved last time: the fresh one could not be had.
    private(set) var homeIsStale = false
    @ObservationIgnored private var menuCache: (menu: Menu, lang: Lang)?
    @ObservationIgnored private var filterCache: (options: FilterOptions, lang: Lang)?
    @ObservationIgnored private var choicesCache: (choices: SearchChoices, lang: Lang)?
    /// The ids of the titles in each of the user's 30nama lists ("watchlist", "favorite", "notify").
    private(set) var lists: [String: Set<Int>] = [:]
    /// Bumped when a title was put on a list or taken off one.
    private(set) var listVersion = 0
    /// Files watched to the end on this device. The server keeps where each title was left, but
    /// not which episodes are done.
    private(set) var watched = Set(UserDefaults.standard.stringArray(forKey: "watched") ?? [])
    @ObservationIgnored private var toastTask: Task<Void, Never>?
    @ObservationIgnored private let cloudRatings = CloudRatings()
    @ObservationIgnored private var cloudTask: Task<Void, Never>?
    @ObservationIgnored private var lastCloudSync = Date.distantPast

    var loc: Loc { Loc(lang: settings.lang) }
    var isLoggedIn: Bool { user != nil }

    // MARK: Boot and session

    func boot() async {
        phase = .booting
        await api.setLanguage(settings.lang)
        await api.setSessionExpiredHandler { [weak self] in
            Task { @MainActor in self?.sessionExpired() }
        }
        do {
            let host = try await api.bootstrap(cachedHost: UserDefaults.standard.string(forKey: "host"))
            UserDefaults.standard.set(host, forKey: "host")
            apiHost = host
            inIran = await api.inIran
            #if DEBUG
            if FilteredNetwork.isOn { inIran = true }
            #endif
            TMDBStore.shared.filtered = inIran
        } catch {
            phase = .failed
            return
        }
        var asGuest = false
        #if DEBUG
        // `NAMA_GUEST=1`: opens as a guest, to see what one sees, without signing the account out.
        asGuest = ProcessInfo.processInfo.environment["NAMA_GUEST"] == "1"
        #endif
        if !asGuest, let saved = SessionStore.load() {
            user = saved
            await api.setToken(saved.token)
            Task { await refreshProfile() }
            Task { await refreshLists() }
        }
        phase = (user != nil || settings.guest || asGuest) ? .main : .login
        syncRatings()
    }

    /// The saved profile can be stale (renewed subscription), so refresh it once per launch.
    private func refreshProfile() async {
        guard let token = user?.token, let e = try? await api.call("user"), e.success,
              let fresh = User(e.result, fallbackToken: token) else { return }
        user = fresh
        SessionStore.save(fresh)
    }

    /// Completes a QR sign-in. The login call returns only a token; the profile follows.
    func signIn(token: String) async {
        await api.setToken(token)
        var signedIn = User(JSON(["user_session": token]))
        if let e = try? await api.call("user"), e.success, let full = User(e.result, fallbackToken: token) {
            signedIn = full
        }
        guard let signedIn else { return }
        user = signedIn
        SessionStore.save(signedIn)
        update { $0.guest = false }
        homeCache = nil
        contentVersion += 1
        showToast([loc(.welcome), signedIn.name].compactMap { $0 }.joined(separator: " "))
        phase = .main
        await refreshLists()
    }

    func continueAsGuest() {
        update { $0.guest = true }
        phase = .main
    }

    /// Leaves guest browsing for the sign-in screen.
    func requestSignIn() {
        phase = .login
    }

    func signOut() {
        let api = self.api
        Task {
            _ = try? await api.call("logout")
            await api.setToken(nil)
        }
        forgetAccount()
        clearSession()
    }

    /// What was this account's alone goes with it, as another member may sign in next: where each
    /// title was left on this device, the episodes marked seen, the titles taken off Continue
    /// watching, the name written for it and its series' news (as the Android and Windows apps).
    /// Not when a session simply runs out: that is the same member, signing in again.
    private func forgetAccount() {
        let defaults = UserDefaults.standard
        for key in defaults.dictionaryRepresentation().keys where key.hasPrefix("left.") { defaults.removeObject(forKey: key) }
        for key in ["watched", "continueDismissals", "displayName", "episodeWatch.expected", "episodeWatch.new", "episodeWatch.last"] {
            defaults.removeObject(forKey: key)
        }
        watched = []
        dismissals = ContinueDismissals()
    }

    private func sessionExpired() {
        guard user != nil else { return }
        clearSession()
    }

    private func clearSession() {
        user = nil
        lists = [:]
        SessionStore.clear()
        update { $0.guest = false }
        homeCache = nil
        playback = nil
        contentVersion += 1
        phase = .login
    }

    // MARK: Settings

    func update(_ change: (inout Settings) -> Void) {
        var next = settings
        change(&next)
        guard next != settings else { return }
        let languageChanged = next.lang != settings.lang
        settings = next
        next.save()
        if languageChanged {
            homeCache = nil
            menuCache = nil
            contentVersion += 1
            Task { await api.setLanguage(next.lang) }
        }
    }

    // MARK: Shared data

    private func homeFile(_ lang: Lang) -> URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("home-\(lang.rawValue).json")
    }

    /// The home page as it was last fetched, read from disk: shown at once on launch, under the
    /// opening title's glass, while the fresh one is on its way.
    func cachedHome() -> HomeFeed? {
        guard let data = try? Data(contentsOf: homeFile(settings.lang)), let json = try? JSON.parse(data) else { return nil }
        let feed = HomeFeed(json)
        guard !feed.isEmpty else { return nil }
        homeLoaded = true
        return feed
    }

    /// Home feed, cached for 90 seconds. If the network fails, the last good feed is served instead.
    func home() async throws -> HomeFeed {
        let lang = settings.lang
        if let c = homeCache, c.lang == lang, Date().timeIntervalSince(c.at) < 90 { return c.feed }
        let file = homeFile(lang)
        do {
            await api.setLanguage(lang)
            let result = try await api.get("home_stream")
            let feed = HomeFeed(result)
            guard !feed.isEmpty else { throw APIError.server("empty") }
            homeCache = (feed, Date(), lang)
            try? result.data()?.write(to: file, options: .atomic)
            homeLoaded = true
            homeIsStale = false
            return feed
        } catch {
            if let data = try? Data(contentsOf: file), let json = try? JSON.parse(data) {
                let feed = HomeFeed(json)
                if !feed.isEmpty {
                    homeLoaded = true
                    homeIsStale = true
                    return feed
                }
            }
            throw error
        }
    }

    /// What is trending on TMDB this week and can be watched on 30nama, in TMDB's order. Through
    /// the user's TMDB key when there is one, else TMDB's public page. Each title costs a 30nama
    /// search the first time, so the answers are kept on disk (see `Trending`).
    func trendingThisWeek() async -> [Post] {
        await trendingRow(.trend)
    }

    /// One of the rows TMDB is asked for. "New and returning series" and "Popular films now" need
    /// the user's own key; without one they come empty (from the Tizen, Android and Windows apps).
    func trendingRow(_ row: TrendingRow) async -> [Post] {
        // The rows are TMDB's.
        guard !inIran else { return [] }
        let key = TMDBStore.key
        if trending == nil || trending?.key != key {
            let api = api
            let file = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
                .appendingPathComponent(key == nil ? "trending-public.json" : "trending.json")
            trending = (key, Trending(key: key, cacheFile: file) { query in
                try await api.get("search", ["query": query, "streamonly": APIClient.streamOnly(false), "page": "1"], background: true)
            })
        }
        return await trending?.service.row(row) ?? []
    }

    @ObservationIgnored private var trending: (key: String?, service: Trending)?

    func continueWatching() async -> [Post] {
        await continueWatchingIfReachable() ?? []
    }

    /// Nil when 30nama could not be asked, as against an empty list.
    /// - Parameter background: no page is waiting on it, so it gives way to those that are.
    func continueWatchingIfReachable(background: Bool = false) async -> [Post]? {
        guard isLoggedIn, let result = try? await api.get("continue_watching", background: background) else { return nil }
        let list = dismissals.filter(Post.continueList(result))
        UserDefaults.standard.set(dismissals.plist, forKey: "continueDismissals")
        return list
    }

    @ObservationIgnored private var dismissals = ContinueDismissals(plist: UserDefaults.standard.dictionary(forKey: "continueDismissals") as? [String: Double] ?? [:])

    /// Takes a title off "continue watching", on 30nama and so on every device. The server's list
    /// is cached for a while after that, so the app also keeps the title off itself until it is
    /// watched again.
    func dismissContinue(_ post: Post) {
        dismissals.dismiss(post)
        UserDefaults.standard.set(dismissals.plist, forKey: "continueDismissals")
        let api = api
        Task {
            _ = try? await api.call("continue_watching_dismiss", ["id": String(post.id)], background: true)
            // The server sends at most twelve, and its list lags behind for a while: asked again
            // shortly and once more later, the titles after the ones taken off move up into the row.
            for wait in [2.0, 45.0] {
                try? await Task.sleep(for: .seconds(wait))
                continueVersion += 1
            }
        }
    }

    /// One of the user's own lists on 30nama ("watchlist" or "favorite"), newest addition first.
    func myList(_ group: String) async -> [Post] {
        guard isLoggedIn, let result = try? await api.get("user_list", ["action": "list", "group": group, "page": "1", "orderby": "add_list", "order": "DESC"]) else { return [] }
        return Post.list(result)
    }

    /// How many titles the user keeps in each of their 30nama lists.
    func myListSizes() async -> [String: Int] {
        guard isLoggedIn, let result = try? await api.get("user_mylist") else { return [:] }
        return (result.object ?? [:]).mapValues { $0["post"].array.count }
    }

    // MARK: Notifications

    /// The messages 30nama has for the account, newest first: nil for a guest, and until asked.
    private(set) var notifications: NotificationPage?

    /// Asked for on opening and every quarter of an hour after, as 30nama's own desktop app does.
    func refreshNotifications() async {
        guard user != nil else {
            notifications = nil
            return
        }
        guard let result = try? await api.get("notification", ["page": "1"], background: true) else { return }
        notifications = NotificationPage(result)
    }

    /// Marks one message read, or every one with "all": here at once, on the server after.
    func markNotificationRead(_ id: String) {
        notifications?.markRead(id)
        Task { _ = try? await api.call("notification_read", ["notification_id": id], background: true) }
    }

    // MARK: Lists

    /// Reads which titles are on the user's lists, so a title page can show where it stands.
    func refreshLists() async {
        guard isLoggedIn, let result = try? await api.get("user_mylist") else { return }
        lists = (result.object ?? [:]).mapValues { Set($0["post"].array.compactMap(\.int)) }
    }

    func isListed(_ post: Post, _ group: String) -> Bool {
        lists[group]?.contains(post.id) ?? false
    }

    /// Puts a title on one of the user's lists, or takes it off. The server has one action for
    /// both: it adds what is missing and removes what is there.
    func toggleList(_ post: Post, _ group: String) async {
        guard isLoggedIn else { return }
        let was = isListed(post, group)
        func set(_ on: Bool) {
            if on { lists[group, default: []].insert(post.id) } else { lists[group]?.remove(post.id) }
        }
        // Answer at once; put it back if the server says no.
        set(!was)
        let done = (try? await api.call("user_list", ["action": "add", "group": group, "post_id": String(post.id)]))?.success ?? false
        if done {
            listVersion += 1
        } else {
            set(was)
            showToast(loc(.error))
        }
    }

    /// Series on the user's watchlist or notify list with an episode still to come, soonest first.
    func upcoming() async -> [Post] {
        await upcomingIfReachable() ?? []
    }

    /// Nil when one of the lists could not be read, as against lists with nothing coming.
    func upcomingIfReachable() async -> [Post]? {
        guard isLoggedIn else { return nil }
        var posts: [Post] = []
        for group in ["watchlist", "notify"] {
            var page = 1, pages = 1
            // A handful of pages is every list a person keeps; more would only hammer the server.
            while page <= pages, page <= 4 {
                guard let result = try? await api.get("user_list", ["action": "list", "group": group, "page": String(page), "orderby": "add_list", "order": "DESC"], background: true) else { return nil }
                let next = PostPage(result)
                posts += next.posts
                pages = next.pages
                page += 1
            }
        }
        return Post.upcoming(posts)
    }

    /// The choices for narrowing a catalogue, as the server offers them in the current language.
    func filterOptions() async throws -> FilterOptions {
        if let c = filterCache, c.lang == settings.lang { return c.options }
        let options = FilterOptions(try await api.get("advanced_search_parameters"))
        filterCache = (options, settings.lang)
        return options
    }

    /// Everything the advanced search can be narrowed by, in the current language.
    func searchChoices() async throws -> SearchChoices {
        if let c = choicesCache, c.lang == settings.lang { return c.choices }
        let choices = SearchChoices(try await api.get("advanced_search_parameters"))
        choicesCache = (choices, settings.lang)
        return choices
    }

    // MARK: Watched

    func setWatched(_ fileID: String, _ on: Bool) {
        guard !fileID.isEmpty, watched.contains(fileID) != on else { return }
        if on { watched.insert(fileID) } else { watched.remove(fileID) }
        UserDefaults.standard.set(Array(watched), forKey: "watched")
    }

    func menu() async throws -> Menu {
        if let c = menuCache, c.lang == settings.lang { return c.menu }
        let menu = Menu(try await api.get("menu"))
        menuCache = (menu, settings.lang)
        return menu
    }

    // MARK: Ratings

    /// Rates a title, or clears its rating when `stars` is nil.
    func rate(_ post: Post, stars: Double?) {
        ratings.rate(post, stars: stars)
        UserDefaults.standard.set(ratings.encoded(), forKey: "ratings")
        // Stars are often nudged a few times in a row: wait for the hand to rest before sending.
        syncRatings(after: 2)
    }

    /// Exchanges ratings with iCloud: what was rated here goes up, what was rated elsewhere comes down.
    /// - Parameter fresh: skip the exchange if one finished less than this many seconds ago.
    func syncRatings(after delay: TimeInterval = 0, unlessFresherThan fresh: TimeInterval = 0) {
        guard Date().timeIntervalSince(lastCloudSync) >= fresh else { return }
        cloudTask?.cancel()
        cloudTask = Task { [weak self, cloudRatings] in
            if delay > 0 { try? await Task.sleep(for: .seconds(delay)) }
            guard let self, !Task.isCancelled else { return }
            if cloud != .synced { cloud = .syncing }
            do {
                let merged = try await RatingSync.run(local: ratings, cloud: cloudRatings)
                // Anything rated while that was under way is in `ratings` but not in `merged`.
                let combined = ratings.merged(with: merged)
                if combined != ratings {
                    ratings = combined
                    UserDefaults.standard.set(combined.encoded(), forKey: "ratings")
                }
                lastCloudSync = Date()
                cloud = .synced
                if combined != merged { syncRatings(after: 2) }
            } catch RatingCloudError.unavailable {
                cloud = .signedOut
            } catch {
                if !Task.isCancelled { cloud = .failed }
            }
        }
    }

    // MARK: Playback and messages

    func play(_ request: PlaybackRequest) {
        playback = request
    }

    // MARK: Where this device left each title

    /// Kept every ten seconds while a title plays: 30nama's own record moves in steps of thirty
    /// seconds and sometimes loses an episode altogether (`Resume`).
    func keepPoint(post: Int, fileID: String, seconds: Double) {
        guard !fileID.isEmpty, seconds > 0 else { return }
        UserDefaults.standard.set(["file": fileID, "seconds": seconds], forKey: "left.\(post)")
    }

    func forgetPoint(post: Int) {
        UserDefaults.standard.removeObject(forKey: "left.\(post)")
    }

    func keptPoint(post: Int) -> Resume.Kept? {
        guard let kept = UserDefaults.standard.dictionary(forKey: "left.\(post)"),
              let file = kept["file"] as? String, let seconds = kept["seconds"] as? Double else { return nil }
        return Resume.Kept(fileID: file, seconds: seconds)
    }

    /// Plays a title's stream: the episode picked, or the one to go on with, from the further of
    /// 30nama's record and this device's.
    func play(_ post: Post, _ stream: StreamResult, index picked: Int? = nil) {
        let start = Resume.start(stream.items, chosen: picked ?? stream.startIndex, server: stream.resume,
                                 kept: keptPoint(post: post.id), pickedByUser: picked != nil)
        let item = stream.items.indices.contains(start.index) ? stream.items[start.index] : nil
        let resume = start.seconds.flatMap { s in item.flatMap { ContinueWatching(JSON(["file_id": $0.fileID, "time": s] as [String: Any])) } }
        play(PlaybackRequest(post: post, items: stream.items, index: start.index, resume: resume))
    }

    func closePlayer() {
        playback = nil
    }

    /// Called once the player is off screen, however it was closed.
    func playerDidClose() {
        watchVersion += 1
    }

    func showToast(_ text: String) {
        toastTask?.cancel()
        toast = text
        toastTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(3.5))
            if !Task.isCancelled { self?.toast = nil }
        }
    }
}
