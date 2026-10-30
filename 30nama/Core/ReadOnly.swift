import Foundation

/// Debug builds only, with `NAMA_READONLY=1`: nothing the app sends changes the account. Where
/// playback was, lists, "continue watching", messages read and sign-out are answered here with a
/// plain success, so clicks made during a test cannot move a real person's progress. As ReadOnly.kt
/// in the Android app and ReadOnly.cs in the Windows app.
enum ReadOnly {
    private static let writes: Set<String> = ["observer", "user_list", "continue_watching_dismiss", "logout", "notification_read"]

    static var isOn: Bool {
        #if DEBUG
        ProcessInfo.processInfo.environment["NAMA_READONLY"] == "1"
        #else
        false
        #endif
    }

    /// Whether a request to the app API would change the account. Lists are read with the same
    /// action that changes them; only a change is held back.
    static func isWrite(_ request: URLRequest) -> Bool {
        guard let path = request.url?.path, let range = path.range(of: "/action/") else { return false }
        let action = String(path[range.upperBound...])
        let body = request.httpBody.map { String(decoding: $0, as: UTF8.self) } ?? ""
        return writes.contains(action) && !(action == "user_list" && body.contains("action=list"))
    }

    /// The network minus the account's writes.
    static func wrap(_ inner: @escaping Transport) -> Transport {
        { request in
            guard isWrite(request) else { return try await inner(request) }
            let body = Data(#"{"success":true,"msg":"","result":{}}"#.utf8)
            return (body, HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
        }
    }
}
