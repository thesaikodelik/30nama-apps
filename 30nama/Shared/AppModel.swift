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
    /// Stereo by default: the original mix is often silent on the Apple TV's own speakers.
    var audioMix: AudioMix = .stereo
    /// Mac only: how much the app moves when nobody is touching it.
    var effects = Effects.auto
    /// Use the adaptive stream instead of the highest fixed rendition.
    var preferAdaptive = false
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
        if let v = d.string(forKey: "audioMix").flatMap(AudioMix.init(rawValue:)) { s.audioMix = v }
        if let v = d.string(forKey: "effects").flatMap(Effects.init(rawValue:)) { s.effects = v }
        s.preferAdaptive = d.bool(forKey: "adaptive")
        s.reduceMotion = d.bool(forKey: "calm")
        return s
    }

    func save(_ d: UserDefaults = .standard) {
        d.set(lang.rawValue, forKey: "lang")
        d.set(guest, forKey: "guest")
        d.set(defaultSubtitle, forKey: "sub")
        d.set(subtitleSize, forKey: "subSize")
        d.set(subtitleColor.rawValue, forKey: "subColor")
        d.set(subtitleBackground.rawValue, forKey: "subBackground")
        d.set(autoplayNext, forKey: "autoplay")
        d.set(audioMix.rawValue, forKey: "audioMix")
        d.set(effects.rawValue, forKey: "effects")
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
    /// Bumped when the catalogue itself may differ: sign-in, sign-out, language.
    private(set) var contentVersion = 0
    /// Bumped when watch progress changed (the player closed).
    private(set) var watchVersion = 0
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
    @ObservationIgnored private var menuCache: (menu: Menu, lang: Lang)?
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
        } catch {
            phase = .failed
            return
        }
        if let saved = SessionStore.load() {
            user = saved
            await api.setToken(saved.token)
            Task { await refreshProfile() }
        }
        phase = (user != nil || settings.guest) ? .main : .login
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
        clearSession()
    }

    private func sessionExpired() {
        guard user != nil else { return }
        clearSession()
    }

    private func clearSession() {
        user = nil
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

    /// Home feed, cached for 90 seconds. If the network fails, the last good feed is served instead.
    func home() async throws -> HomeFeed {
        let lang = settings.lang
        if let c = homeCache, c.lang == lang, Date().timeIntervalSince(c.at) < 90 { return c.feed }
        let file = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("home-\(lang.rawValue).json")
        do {
            await api.setLanguage(lang)
            let result = try await api.get("home_stream")
            let feed = HomeFeed(result)
            guard !feed.isEmpty else { throw APIError.server("empty") }
            homeCache = (feed, Date(), lang)
            try? result.data()?.write(to: file, options: .atomic)
            return feed
        } catch {
            if let data = try? Data(contentsOf: file), let json = try? JSON.parse(data) {
                let feed = HomeFeed(json)
                if !feed.isEmpty { return feed }
            }
            throw error
        }
    }

    func continueWatching() async -> [Post] {
        guard isLoggedIn, let result = try? await api.get("continue_watching") else { return [] }
        return Post.continueList(result)
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
