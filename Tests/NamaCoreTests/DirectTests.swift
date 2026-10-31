import XCTest
@testable import NamaCore

/// As the Windows app's DirectTests; mpv plays a TrueHD file, so none is passed over here.
final class DirectTests: XCTestCase {
    private func file(_ name: String, _ quality: String, _ size: String) -> [String: Any] {
        ["dl": "https://example.com/\(name)", "quality": quality, "size": size]
    }

    func testAFilmTakesTheSharpestThenLargestFile() {
        let options = DownloadOptions(JSON(["download": [
            file("a.1080p.mkv", "1080p BluRay", "2.1 GB"),
            file("a.2160p.TrueHD.mkv", "2160p REMUX", "40 GB"),
            file("a.2160p.mkv", "2160p WEB-DL HDR", "12 GB"),
            file("a.720p.mkv", "720p", "900 MB"),
        ]] as [String: Any]))
        XCTAssertEqual(Direct.pick(options, isSeries: false, season: nil, episode: nil)?.url.lastPathComponent, "a.2160p.TrueHD.mkv")
    }

    func testAnEpisodeTakesItsOwnFile() {
        func pack(_ season: Int, _ links: [(String, String)]) -> [String: Any] {
            ["season": season, "quality": "1080p", "size": "1 GB",
             "link": links.map { ["dl": "https://example.com/\($0.0)", "episode": $0.1] }]
        }
        let options = DownloadOptions(JSON(["download": ["s": [
            pack(1, [("s1e1.mkv", "1"), ("s1e2.mkv", "2")]), pack(2, [("s2e2.mkv", "2")]),
        ]]] as [String: Any]))
        XCTAssertEqual(Direct.pick(options, isSeries: true, season: 1, episode: "02")?.url.lastPathComponent, "s1e2.mkv")
        XCTAssertNil(Direct.pick(options, isSeries: true, season: 3, episode: "1"))
    }

    func testSizesAndLabels() {
        XCTAssertEqual(Direct.megabytes("2.4 GB"), 2457.6, accuracy: 0.01)
        XCTAssertEqual(Direct.megabytes("781 MB"), 781, accuracy: 0.01)
        let options = DownloadOptions(JSON(["download": [file("b.mkv", "2160p HDR x265", "10 GB")]] as [String: Any]))
        let picked = Direct.pick(options, isSeries: false, season: nil, episode: nil)!
        XCTAssertEqual(Direct.label(picked), "2160p · HDR · x265")
        XCTAssertEqual(Direct.asQuality(picked).height, 2160)
    }
}
