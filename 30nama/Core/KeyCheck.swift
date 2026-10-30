import Foundation

/// A key the user brought for another service is saved first and tried after, with one light
/// request: from Iran the service may be out of reach while the key is fine, and that must not
/// throw the key away. As the Android app's KeyCheck (learnt from 30namaTV) and the Windows app's.
enum KeyCheck {
    enum State: Equatable { case unset, checking, works, rejected, unreachable }

    enum Service: String { case tmdb, subsource, gemini }

    /// The light request that tells whether `key` works for `service`.
    static func request(_ service: Service, key: String) -> URLRequest {
        switch service {
        case .tmdb:
            return TMDB.request("configuration", key: key)
        case .subsource:
            return SubSource.searchRequest(.init(title: "Inception", year: 2010, imdbID: "tt1375666", isSeries: false), key: key)
        case .gemini:
            var request = URLRequest(url: URL(string: "https://generativelanguage.googleapis.com/v1beta/models")!)
            request.setValue(key, forHTTPHeaderField: "x-goog-api-key")
            return request
        }
    }

    /// What an answer's status says of the key: a refusal is the key, anything else the service.
    static func read(status: Int, service: Service) -> State {
        switch status {
        case 200..<300: return .works
        case 401, 403: return .rejected
        // Gemini answers a wrong key with 400 API_KEY_INVALID.
        case 400 where service == .gemini: return .rejected
        default: return .unreachable
        }
    }

    static func check(_ service: Service, key: String, transport: Transport) async -> State {
        var request = request(service, key: key)
        request.timeoutInterval = 12
        guard let (_, response) = try? await transport(request) else { return .unreachable }
        return read(status: response.statusCode, service: service)
    }
}
