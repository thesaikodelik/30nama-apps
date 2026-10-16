import Foundation
import CryptoKit

/// A Mac release on the project's GitHub page, as the releases API describes it.
struct MacRelease: Equatable {
    let version: String
    let page: URL
    let image: URL
    let signature: URL

    static let tagPrefix = "mac-v"
    /// Where the releases are listed. GitHub answers without a key, sixty times an hour per address.
    static let feed = URL(string: "https://api.github.com/repos/thesaikodelik/30nama-apps/releases?per_page=10")!

    /// The newest finished release in a `releases` answer that is newer than `current`. A release
    /// without its disk image and that image's signature is passed over, as if not out yet.
    static func newest(in j: JSON, newerThan current: String) -> MacRelease? {
        j.array.compactMap { r -> MacRelease? in
            guard !r["draft"].bool, !r["prerelease"].bool, let tag = r["tag_name"].text, tag.hasPrefix(tagPrefix) else { return nil }
            let assets = r["assets"].array
            func asset(_ suffix: String) -> URL? {
                assets.first { ($0["name"].text ?? "").hasSuffix(suffix) }?["browser_download_url"].url
            }
            guard let image = asset(".dmg"), let signature = asset(".dmg.sig"), let page = r["html_url"].url else { return nil }
            return MacRelease(version: String(tag.dropFirst(tagPrefix.count)), page: page, image: image, signature: signature)
        }
        .filter { isNewer($0.version, than: current) }
        .max { isNewer($1.version, than: $0.version) }
    }

    /// Compares numbers part by part, so 1.10 is newer than 1.9.
    static func isNewer(_ version: String, than other: String) -> Bool {
        version.compare(other, options: .numeric) == .orderedDescending
    }
}

/// Checks that a downloaded update was signed by the project. The app is not signed by an Apple
/// developer account, so macOS does not vouch for what it downloads; this signature does instead.
/// Only the public half of the key is here. The private half stays in the maintainer's keychain.
enum UpdateSignature {
    static let publicKey = "qZTK9RGsQiIYov3GDPYNvqJ1+yF6vVNL+wnxguk7LY0="

    /// `signature` is the text of the `.sig` file: the Ed25519 signature of `data`, in base64.
    static func isValid(_ signature: String, for data: Data, publicKey: String = publicKey) -> Bool {
        guard let raw = Data(base64Encoded: publicKey),
              let key = try? Curve25519.Signing.PublicKey(rawRepresentation: raw),
              let signature = Data(base64Encoded: signature.trimmingCharacters(in: .whitespacesAndNewlines)) else { return false }
        return key.isValidSignature(signature, for: data)
    }
}
