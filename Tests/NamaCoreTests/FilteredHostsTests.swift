import XCTest
@testable import NamaCore

final class FilteredHostsTests: XCTestCase {
    func testWhatIranFilters() {
        for host in ["www.themoviedb.org", "api.tmdb.org", "image.tmdb.org", "www.youtube-nocookie.com", "i.ytimg.com",
                     "api.subsource.net", "interface.30nama.com", "cdn.30nama.com", "30nama.com"] {
            XCTAssertTrue(FilteredHosts.contains(host), host)
        }
        // The app's own API and images, the updater and Aparat get through.
        for host in ["ir.kazem-api.com", "world.hana-api.com", "cdn-world.hana-api.com", "api.github.com",
                     "www.aparat.com", "not30nama.com", nil] {
            XCTAssertFalse(FilteredHosts.contains(host), host ?? "nil")
        }
    }

    func testAFilteredRequestWaitsOutItsTimeAndFails() async {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FilteredNetwork.self]
        let session = URLSession(configuration: configuration)
        var request = URLRequest(url: URL(string: "https://www.themoviedb.org/")!)
        request.timeoutInterval = 0.3
        let start = Date()
        do {
            _ = try await session.data(for: request)
            XCTFail("a filtered host answered")
        } catch {
            XCTAssertEqual((error as? URLError)?.code, .timedOut)
            XCTAssertGreaterThanOrEqual(Date().timeIntervalSince(start), 0.25)
        }
    }
}
