// Signs a Mac disk image for the in-app updater: writes <image>.sig beside it, to be uploaded to
// the release with the image. The app installs only what this key has signed.
//
// The key lives in the login keychain of the maintainer's Mac (service "30nama-update-signing")
// and never in the repository. Without it, the Mac app cannot update itself and people will have
// to download a release by hand; keep a copy of it somewhere safe.
// Usage: swift Tools/sign-update.swift <path to .dmg>
import Foundation
import CryptoKit
import Security

let path = CommandLine.arguments.dropFirst().first ?? ""
guard let data = FileManager.default.contents(atPath: path) else {
    print("usage: swift Tools/sign-update.swift <path to .dmg>")
    exit(1)
}
let query: [String: Any] = [
    kSecClass as String: kSecClassGenericPassword,
    kSecAttrService as String: "30nama-update-signing",
    kSecAttrAccount as String: "ed25519",
    kSecReturnData as String: true,
]
var item: CFTypeRef?
guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
      let stored = (item as? Data).flatMap({ String(data: $0, encoding: .utf8) }),
      let raw = Data(base64Encoded: stored),
      let key = try? Curve25519.Signing.PrivateKey(rawRepresentation: raw) else {
    print("no signing key in the keychain")
    exit(1)
}
let signature = try key.signature(for: data).base64EncodedString()
try (signature + "\n").write(toFile: path + ".sig", atomically: true, encoding: .utf8)
print("signed \(path) with key \(key.publicKey.rawRepresentation.base64EncodedString())")
