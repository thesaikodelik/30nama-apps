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
    /// Network failure, timeout, rate limiting or a non-JSON body. Worth retrying.
    case transient(String, retryAfter: TimeInterval)
    /// The API answered `success: false`.
    case server(String)
    /// No host answered the connection test.
    case unreachable

    var message: String {
        switch self {
        case .transient(let m, _), .server(let m): return m
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
/// pause after a rate-limit response.
actor RequestGate {
    private let limit: Int
    private let spacing: TimeInterval
    private var inFlight = 0
    private var core: [CheckedContinuation<Void, Never>] = []
    private var background: [CheckedContinuation<Void, Never>] = []
    private var nextStart = Date.distantPast
    private var pausedUntil = Date.distantPast

    init(limit: Int = 2, spacing: TimeInterval = 0.1) {
        self.limit = limit
        self.spacing = spacing
    }

    func acquire(background isBackground: Bool) async {
        if inFlight < limit {
            inFlight += 1
        } else {
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

    /// Hands the slot to the next waiter, screen calls first.
    func release() {
        if !core.isEmpty {
            core.removeFirst().resume()
        } else if !background.isEmpty {
            background.removeFirst().resume()
        } else {
            inFlight -= 1
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
        self.transport = transport ?? APIClient.urlSessionTransport()
    }

    static func urlSessionTransport() -> Transport {
        let c = URLSessionConfiguration.ephemeral
        c.timeoutIntervalForRequest = 20
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        let session = URLSession(configuration: c)
        return { request in
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
            return (data, http)
        }
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
        return await withTaskGroup(of: [String].self) { group -> [String] in
            for url in urls {
                group.addTask {
                    var request = URLRequest(url: url)
                    request.timeoutInterval = 6
                    guard let (data, _) = try? await transport(request), let list = try? JSON.parse(data) else { return [] }
                    return list.array.compactMap { $0["host"].text }
                }
            }
            for await list in group where !list.isEmpty {
                group.cancelAll()
                return list
            }
            return []
        }
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
    func site(_ path: String, _ form: [String: String] = [:], background: Bool = false) async throws -> JSON {
        guard let key = config.site.key, let url = URL(string: config.site.host + "/action/" + path) else { throw APIError.unreachable }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(key, forHTTPHeaderField: "c-api-key")
        request.setValue(config.site.platform, forHTTPHeaderField: "c-platform")
        request.httpBody = APIClient.formEncode(form).data(using: .utf8)
        let e = try await retrying(background: background, retries: config.backoff.count) {
            try await self.exchange(request, action: path)
        }
        guard e.success else { throw APIError.server(e.message ?? "error") }
        return e.result
    }

    /// One request through the gate, tried again after a transient failure.
    private func retrying(background: Bool, retries: Int, _ send: () async throws -> Envelope) async throws -> Envelope {
        var attempt = 0
        while true {
            await gate.acquire(background: background)
            do {
                let envelope = try await send()
                await gate.release()
                return envelope
            } catch let APIError.transient(_, retryAfter) where attempt < retries {
                await gate.release()
                let delay = max(config.backoff[attempt], retryAfter)
                await gate.pause(for: delay)
                try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                attempt += 1
            } catch {
                await gate.release()
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
        // A progress report is answered with nothing at all: accepted, with nothing to say.
        if data.isEmpty, (200..<300).contains(response.statusCode) { return Envelope(success: true, message: nil, result: .null) }
        guard let json = try? JSON.parse(data), json.object != nil else {
            // Cloudflare answers rate limiting and outages with HTML or plain text.
            let retryAfter = min(Double(response.value(forHTTPHeaderField: "Retry-After") ?? "") ?? 0, 10)
            throw APIError.transient("\(action): HTTP \(response.statusCode), not JSON", retryAfter: retryAfter)
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
