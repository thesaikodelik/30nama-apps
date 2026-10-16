import XCTest
import CryptoKit
@testable import NamaCore

final class MacReleaseTests: XCTestCase {
    private func release(_ tag: String, assets: [String] = ["30nama-mac.dmg", "30nama-mac.dmg.sig"], draft: Bool = false) -> [String: Any] {
        [
            "tag_name": tag, "draft": draft, "prerelease": false,
            "html_url": "https://example.com/releases/\(tag)",
            "assets": assets.map { ["name": $0, "browser_download_url": "https://example.com/\(tag)/\($0)"] },
        ]
    }

    func testPicksTheNewestCompleteMacRelease() throws {
        let feed = JSON([
            release("mac-v1.10.0", assets: ["30nama-mac.dmg"]),
            release("mac-v1.9.0"),
            release("mac-v2.0.0", draft: true),
            release("tv-v3.0.0"),
            release("mac-v1.2.0"),
        ] as [Any])
        let newest = try XCTUnwrap(MacRelease.newest(in: feed, newerThan: "1.1.0"))
        XCTAssertEqual(newest.version, "1.9.0", "1.10 has no signature yet, 2.0 is a draft, 3.0 is not for the Mac")
        XCTAssertEqual(newest.image.lastPathComponent, "30nama-mac.dmg")
        XCTAssertEqual(newest.signature.lastPathComponent, "30nama-mac.dmg.sig")
        XCTAssertNil(MacRelease.newest(in: feed, newerThan: "1.9.0"))
    }

    func testVersionsCompareByNumber() {
        XCTAssertTrue(MacRelease.isNewer("1.10.0", than: "1.9.2"))
        XCTAssertTrue(MacRelease.isNewer("1.2", than: "1.1.9"))
        XCTAssertFalse(MacRelease.isNewer("1.1.0", than: "1.1.0"))
    }

    func testSignature() {
        let key = Curve25519.Signing.PrivateKey()
        let publicKey = key.publicKey.rawRepresentation.base64EncodedString()
        let data = Data("disk image".utf8)
        let signature = try! key.signature(for: data).base64EncodedString() + "\n"
        XCTAssertTrue(UpdateSignature.isValid(signature, for: data, publicKey: publicKey))
        XCTAssertFalse(UpdateSignature.isValid(signature, for: Data("disk imagf".utf8), publicKey: publicKey), "a changed file")
        XCTAssertFalse(UpdateSignature.isValid(signature, for: data), "signed with another key than the app's")
        XCTAssertFalse(UpdateSignature.isValid("not base64", for: data, publicKey: publicKey))
    }
}
