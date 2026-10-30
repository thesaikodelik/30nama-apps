import Foundation

struct APIConfig {
    var discoveryURLs = [
        "https://pub-ac69cf865ce145f8b4c1ed87dc2a9fb7.r2.dev/sdk-endpoint.json",
        "https://30update.s3.ir-thr-at1.arvanstorage.ir/sdk-endpoint.json",
    ]
    var fallbackHosts = [
        "https://world.hana-api.com/api/v2",
        "https://ir.kazem-api.com/api/v2",
    ]
    /// The hosts that answer only from inside Iran; the endpoint list says the same with "IR".
    var iranHosts: Set<String> = ["https://ir.kazem-api.com/api/v2"]
    /// WebTV client key first, Android TV key as a fallback.
    var apiKeys = [
        "83hRzBabjDnuv9SmV2GFaftpxjhZSHXYe7UnBuX5DdANdpzPvXu46jvKZHPb97As",
        "VdapnkLG9EHsSmF8yeKcb4253RAuN6YX",
    ]
    var clientVersion = "1.0.0"
    /// Waits between retries of a transient failure. Its count is the number of retries.
    var backoff: [TimeInterval] = [1, 3]
    var requestSpacing: TimeInterval = 0.1
    /// The website's own API, for what only the site has.
    var site = SiteConfig()
}

enum APIError: Error, Equatable {
    /// Network failure, timeout, rate limiting or a non-JSON body. Worth retrying. `rateLimited`:
    /// Cloudflare's rate limit (429, or its "error code: 1015" page), which pauses the whole gate.
    case transient(String, retryAfter: TimeInterval, rateLimited: Bool = false)
    /// The API answered `success: false`.
    case server(String)
    /// No host answered the connection test.
    case unreachable

    var message: String {
        switch self {
        case .transient(let m, _, _), .server(let m): return m
        case .unreachable: return "unreachable"
        }
    }
}

struct Envelope {
    var success: Bool
    var message: String?
    var result: JSON
}

typealias Transport = @Sendable (URLRequest) async throws -> (Data, HTTPURLResponse)

/// Limits concurrency and spacing of API calls.
///
/// 30nama sits behind Cloudflare rate limiting, so every call goes through one gate: at most two
/// in flight, a short gap between starts, screen calls ahead of background ones, and a shared
/// pause after a rate-limit response. Background work never holds every slot, so a page that
/// opens while posters' extras are being fetched is not kept waiting behind them.
actor RequestGate {
    private let limit: Int
    private let backgroundLimit: Int
    private let spacing: TimeInterval
    private var inFlight = 0
    private var backgroundInFlight = 0
    private var core: [CheckedContinuation<Void, Never>] = []
    private var background: [CheckedContinuation<Void, Never>] = []
    private var nextStart = Date.distantPast
    private var pausedUntil = Date.distantPast

    init(limit: Int = 2, spacing: TimeInterval = 0.1, backgroundLimit: Int? = nil) {
        self.limit = limit
        self.spacing = spacing
        self.backgroundLimit = backgroundLimit ?? max(1, limit - 1)
    }

    func acquire(background isBackground: Bool) async {
        if inFlight < limit, !isBackground || backgroundInFlight < backgroundLimit {
            inFlight += 1
            if isBackground { backgroundInFlight += 1 }
        } else {
            // The slot is counted for us by whoever hands it over (`release`).
            await withCheckedContinuation { c in
                if isBackground { background.append(c) } else { core.append(c) }
            }
        }
        let now = Date()
        let start = max(now, nextStart, pausedUntil)
        nextStart = start.addingTimeInterval(spacing)
        let wait = start.timeIntervalSince(now)
        if wait > 0 { try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000)) }
    }

    /// Gives back a slot taken with the same `background`, and hands it on: screen calls first.
    func release(background isBackground: Bool = false) {
        inFlight -= 1
        if isBackground { backgroundInFlight -= 1 }
        if !core.isEmpty {
            inFlight += 1
            core.removeFirst().resume()
        } else if !background.isEmpty, backgroundInFlight < backgroundLimit {
            inFlight += 1
            backgroundInFlight += 1
            background.removeFirst().resume()
        }
    }

    func pause(for seconds: TimeInterval) {
        pausedUntil = max(pausedUntil, Date().addingTimeInterval(seconds))
    }
}

/// RPC client for the 30nama API: `POST {host}/action/{name}` with a form body, answered by a
/// `{success, msg, result}` envelope.
actor APIClient {
    let config: APIConfig
    private let transport: Transport
    private let gate: RequestGate
    /// The website's own gate: its host is not the app API's, and a request of its that hangs, as
    /// they do where it is filtered, must not hold a place the app's own requests are waiting for.
    private let siteGate: RequestGate
    private var discoveredIran: Set<String> = []

    /// The host that answered is one that answers only inside Iran: the user is there, without a
    /// VPN, and TMDB, YouTube and the website are filtered. Asked for no longer than is needed.
    var inIran: Bool {
        guard let host else { return false }
        return config.iranHosts.contains(host) || discoveredIran.contains(host)
    }

    /// Until when the website is taken to be out of reach (see `site`).
    private var siteOutUntil = Date.distantPast
    /// How long the website is not asked again after it gave no answer at all.
    static let siteOutFor: TimeInterval = 180

    private(set) var host: String?
    private var key: String
    private var token = ""
    private var language = Lang.en.catalogue
    private var onSessionExpired: (@Sendable () -> Void)?
    private var checkingSession = false

    /// Actions that must not be replayed.
    private static let noRetry: Set<String> = ["observer", "logout", "qrlogin_login"]

    init(config: APIConfig = APIConfig(), transport: Transport? = nil) {
        self.config = config
        self.key = config.apiKeys[0]
        self.gate = RequestGate(spacing: config.requestSpacing)
        self.siteGate = RequestGate(spacing: config.requestSpacing)
        self.transport = transport ?? APIClient.urlSessionTransport()
    }

    static func urlSessionTransport() -> Transport {
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = 20
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        #if DEBUG
        FilteredNetwork.install(in: c)
        #endif
        let session = URLSession(configuration: c)
        let transport: Transport = { request in
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
            return (data, http)
        }
        return ReadOnly.isOn ? ReadOnly.wrap(transport) : transport
    }

    func setToken(_ token: String?) { self.token = token ?? "" }
    func setLanguage(_ lang: Lang) { language = lang.catalogue }
    func setSessionExpiredHandler(_ handler: (@Sendable () -> Void)?) { onSessionExpired = handler }
    func useHost(_ host: String) { self.host = host }

    // MARK: Endpoint discovery

    /// Finds a working host and key: fetch the published endpoint list (falling back to the
    /// built-in one), then run the `test` action against every host and take the first that answers.
    @discardableResult
    func bootstrap(cachedHost: String? = nil) async throws -> String {
        var hosts: [String] = []
        for h in await discoverHosts() + config.fallbackHosts where !hosts.contains(h) { hosts.append(h) }
        if let cachedHost, !hosts.contains(cachedHost) { hosts.insert(cachedHost, at: 0) }

        for candidate in config.apiKeys {
            let winner = await withTaskGroup(of: String?.self) { group -> String? in
                for h in hosts {
                    group.addTask {
                        let e = try? await self.send(host: h, key: candidate, action: "test", params: [:], token: "", timeout: 8)
                        return e?.success == true ? h : nil
                    }
                }
                for await h in group {
                    if let h { group.cancelAll(); return h }
                }
                return nil
            }
            if let winner {
                host = winner
                key = candidate
                return winner
            }
        }
        throw APIError.unreachable
    }

    private func discoverHosts() async -> [String] {
        let transport = self.transport
        let urls = config.discoveryURLs.compactMap { URL(string: "\($0)?t=\(Int(Date().timeIntervalSince1970))") }
        let found = await withTaskGroup(of: [JSON].self) { group -> [JSON] in
            for url in urls {
                group.addTask {
                    var request = URLRequest(url: url)
                    request.timeoutInterval = 6
                    guard let (data, _) = try? await transport(request), let list = try? JSON.parse(data) else { return [] }
                    return list.array
                }
            }
            for await list in group where !list.isEmpty {
                group.cancelAll()
                return list
            }
            return []
        }
        for entry in found where entry["location"].text == "IR" {
            if let host = entry["host"].text { discoveredIran.insert(host) }
        }
        return found.compactMap { $0["host"].text }
    }

    // MARK: Calls

    /// Runs an action and returns its envelope, including unsuccessful ones. Throws only when the
    /// server could not be reached or kept answering with something that is not the API.
    func call(_ action: String, _ params: [String: String] = [:], background: Bool = false) async throws -> Envelope {
        guard let host else { throw APIError.unreachable }
        let envelope = try await retrying(background: background, retries: APIClient.noRetry.contains(action) ? 0 : config.backoff.count) {
            try await self.send(host: host, key: self.key, action: action, params: params, token: self.token, timeout: 20)
        }
        if !envelope.success, action != "user", APIClient.isLoginRequired(envelope.message) {
            await confirmSessionExpired()
        }
        return envelope
    }

    /// Whether the website's API can be asked at all: a build without its key leaves out what only
    /// the site has.
    nonisolated var hasSite: Bool { config.site.key != nil }

    /// Runs an action of the website's API (`SiteConfig`) and returns its `result`, throwing
    /// `APIError.server` when it did not succeed. Through the same gate, with the same retries, as
    /// the app API: both are behind the same Cloudflare. Its parameters are in `path`, the rest in `form`.
    ///
    /// Iran filters the website (its API with it), and on some networks there a request to it is
    /// neither answered nor refused. One that gets no answer at all is not tried again, and for
    /// `siteOutFor` the site is not asked: what needs it fails at once with `APIError.unreachable`
    /// instead of every page waiting out the same silence. `siteAgain()` lifts that.
    func site(_ path: String, _ form: [String: String] = [:], background: Bool = false) async throws -> JSON {
        guard let key = config.site.key, let url = URL(string: config.site.host + "/action/" + path) else { throw APIError.unreachable }
        guard siteInReach else { throw APIError.unreachable }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 12
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(key, forHTTPHeaderField: "c-api-key")
        request.setValue(config.site.platform, forHTTPHeaderField: "c-platform")
        request.httpBody = APIClient.formEncode(form).data(using: .utf8)
        let e = try await retrying(through: siteGate, background: background, retries: config.backoff.count) {
            let data: Data, response: HTTPURLResponse
            do {
                (data, response) = try await self.transport(request)
            } catch {
                // Given up by whoever asked (a page closed) says nothing about the site.
                if error is CancellationError || (error as? URLError)?.code == .cancelled { throw error }
                self.siteOutUntil = Date().addingTimeInterval(APIClient.siteOutFor)
                throw APIError.unreachable
            }
            return try self.envelope(data, response, action: path)
        }
        guard e.success else { throw APIError.server(e.message ?? "error") }
        return e.result
    }

    /// Whether the website answered the last time it was asked, or has not been asked for a while.
    var siteInReach: Bool { Date() >= siteOutUntil }

    /// Asked for again by the user (a retry button, perhaps with a VPN now on): ask the site again at once.
    func siteAgain() { siteOutUntil = .distantPast }

    /// One request through the gate, tried again after a transient failure.
    private func retrying(through gate: RequestGate? = nil, background: Bool, retries: Int, _ send: () async throws -> Envelope) async throws -> Envelope {
        let gate = gate ?? self.gate
        var attempt = 0
        while true {
            await gate.acquire(background: background)
            do {
                let envelope = try await send()
                await gate.release(background: background)
                return envelope
            } catch let APIError.transient(message, retryAfter, rateLimited) {
                await gate.release(background: background)
                // A rate limit pauses every call behind it, retried or not (a write is never
                // replayed), so the next one does not walk into the same wall.
                if rateLimited { await gate.pause(for: max(config.backoff.isEmpty ? 1 : config.backoff[min(attempt, config.backoff.count - 1)], retryAfter)) }
                guard attempt < retries else { throw APIError.transient(message, retryAfter: retryAfter, rateLimited: rateLimited) }
                let delay = max(config.backoff[attempt], retryAfter)
                await gate.pause(for: delay)
                try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                attempt += 1
            } catch {
                await gate.release(background: background)
                throw error
            }
        }
    }

    /// Runs an action and returns its `result`, throwing `APIError.server` when it did not succeed.
    func get(_ action: String, _ params: [String: String] = [:], background: Bool = false) async throws -> JSON {
        let e = try await call(action, params, background: background)
        guard e.success else { throw APIError.server(e.message ?? "error") }
        return e.result
    }

    /// `streamonly` as the server reads it: only the word "true" filters. "1" was sent for years and
    /// was ignored, so "streaming only" showed everything (found by the Android app, 6 Aban 1405).
    static func streamOnly(_ on: Bool) -> String { on ? "true" : "false" }

    static func isLoginRequired(_ message: String?) -> Bool {
        guard let m = message?.lowercased() else { return false }
        return m.contains("login required") || m.contains("وارد حساب کاربری")
    }

    /// "Login required" on an action can also mean "this needs a subscription". Only treat the
    /// session as gone when the profile call says so too.
    private func confirmSessionExpired() async {
        guard !token.isEmpty, !checkingSession, let host else { return }
        checkingSession = true
        defer { checkingSession = false }
        guard let e = try? await send(host: host, key: key, action: "user", params: [:], token: token, timeout: 20),
              !e.success, APIClient.isLoginRequired(e.message) else { return }
        token = ""
        onSessionExpired?()
    }

    private func send(host: String, key: String, action: String, params: [String: String], token: String, timeout: TimeInterval) async throws -> Envelope {
        guard let url = URL(string: host + "/action/" + action) else { throw APIError.unreachable }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = timeout
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(key, forHTTPHeaderField: "c-api-key")
        request.setValue(token, forHTTPHeaderField: "c-token")
        request.setValue(config.clientVersion, forHTTPHeaderField: "c-version-number")
        request.setValue(language, forHTTPHeaderField: "c-language")
        request.httpBody = APIClient.formEncode(params).data(using: .utf8)
        return try await exchange(request, action: action)
    }

    private func exchange(_ request: URLRequest, action: String) async throws -> Envelope {
        let data: Data, response: HTTPURLResponse
        do {
            (data, response) = try await transport(request)
        } catch {
            throw APIError.transient("\(action): \(error.localizedDescription)", retryAfter: 0)
        }
        return try envelope(data, response, action: action)
    }

    private func envelope(_ data: Data, _ response: HTTPURLResponse, action: String) throws -> Envelope {
        // A progress report is answered with nothing at all: accepted, with nothing to say.
        if data.isEmpty, (200..<300).contains(response.statusCode) { return Envelope(success: true, message: nil, result: .null) }
        guard let json = try? JSON.parse(data), json.object != nil else {
            // Cloudflare answers rate limiting and outages with HTML or plain text.
            let retryAfter = min(Double(response.value(forHTTPHeaderField: "Retry-After") ?? "") ?? 0, 10)
            let limited = response.statusCode == 429 || String(decoding: data, as: UTF8.self).contains("error code: 1015")
            throw APIError.transient("\(action): HTTP \(response.statusCode), not JSON", retryAfter: retryAfter, rateLimited: limited)
        }
        return Envelope(success: json["success"].bool, message: json["msg"].text, result: json["result"])
    }

    static func formEncode(_ params: [String: String]) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        func enc(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: allowed) ?? s }
        return params.sorted { $0.key < $1.key }.map { enc($0.key) + "=" + enc($0.value) }.joined(separator: "&")
    }
}
