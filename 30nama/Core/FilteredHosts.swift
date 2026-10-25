import Foundation

/// The hosts the app talks to that Iran filters. Seen on 25 October 2026 from probes on Iranian
/// networks: their names resolve to the filter's own 10.10.34.x. Nothing that comes from these
/// may hold up a page, since there a request to them is often neither answered nor refused.
public enum FilteredHosts {
    public static let suffixes = [
        "themoviedb.org", "tmdb.org", "fanart.tv",
        "youtube.com", "youtube-nocookie.com", "googlevideo.com", "ytimg.com",
        "subsource.net",
        // The website, its API and its image host; the app's own API and images are elsewhere.
        "30nama.com",
    ]

    public static func contains(_ host: String?) -> Bool {
        guard let host = host?.lowercased() else { return false }
        return suffixes.contains { host == $0 || host.hasSuffix("." + $0) }
    }
}

#if DEBUG
/// `NAMA_FILTERED=1`: the app as it is on an Iranian network that drops what it filters, as
/// Irancell does. A request to a filtered host gets no answer and fails once its time is up.
public final class FilteredNetwork: URLProtocol, @unchecked Sendable {
    public static let isOn = ProcessInfo.processInfo.environment["NAMA_FILTERED"] == "1"

    /// The shared session takes it from `start()`; a session with a configuration of its own needs it here.
    public static func install(in configuration: URLSessionConfiguration) {
        guard isOn else { return }
        configuration.protocolClasses = [FilteredNetwork.self] + (configuration.protocolClasses ?? [])
    }

    public static func start() {
        if isOn { URLProtocol.registerClass(FilteredNetwork.self) }
    }

    private var wait: DispatchWorkItem?

    public override class func canInit(with request: URLRequest) -> Bool { FilteredHosts.contains(request.url?.host) }
    public override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    public override func startLoading() {
        let wait = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.client?.urlProtocol(self, didFailWithError: URLError(.timedOut))
        }
        self.wait = wait
        DispatchQueue.global().asyncAfter(deadline: .now() + min(request.timeoutInterval, 60), execute: wait)
    }

    public override func stopLoading() { wait?.cancel() }
}
#endif
