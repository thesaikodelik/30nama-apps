import XCTest
@testable import NamaCore

/// After the tests of Tachyon's segment.rs, by way of the Windows app.
final class SegmentsTests: XCTestCase {
    private let mb: Int64 = 1024 * 1024

    private func contiguous(_ states: [SegmentState], _ size: Int64, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(states.first?.start, 0, file: file, line: line)
        for i in 1..<states.count { XCTAssertEqual(states[i - 1].end, states[i].start, "no gap, no overlap", file: file, line: line) }
        XCTAssertEqual(states.last?.end, size, file: file, line: line)
    }

    func testSplitsTheLargestInHalf() throws {
        let map = SegmentMap.whole(100 * mb)
        let first = try XCTUnwrap(map.takeUnowned())
        let second = try XCTUnwrap(map.splitLargest())
        XCTAssertEqual(first.end, second.start)
        XCTAssertEqual(second.start, 50 * mb)
        XCTAssertEqual(map.splitLargest()?.start, 25 * mb, "both have 50 MB left; the earlier one is split")
        contiguous(map.snapshot(), 100 * mb)
    }

    func testASplitCountsWhatHasArrived() throws {
        let map = SegmentMap.whole(100 * mb)
        let segment = try XCTUnwrap(map.takeUnowned())
        let chunk = Data(count: Int(60 * mb))
        XCTAssertEqual(segment.write(chunk) { _, _ in }, chunk.count)
        XCTAssertEqual(map.splitLargest()?.start, 80 * mb, "40 MB were left after 60; the split lands halfway")
        XCTAssertEqual(segment.write(chunk) { _, _ in }, Int(20 * mb), "writing stops at the new boundary")
        XCTAssertTrue(segment.isComplete)
        contiguous(map.snapshot(), 100 * mb)
    }

    func testRefusesTinySplits() {
        let map = SegmentMap.whole(mb + mb / 2)
        _ = map.takeUnowned()
        XCTAssertNil(map.splitLargest())
        XCTAssertNil(SegmentMap([SegmentState(start: 0, pos: 0, end: 100 * mb)], splittable: false).splitLargest())
    }

    func testLeftoversAreTakenOnce() {
        let map = SegmentMap([SegmentState(start: 0, pos: 10, end: 10), SegmentState(start: 10, pos: 15, end: 20),
                              SegmentState(start: 20, pos: 22, end: 30)], splittable: true)
        XCTAssertEqual(map.takeUnowned()?.start, 10)
        XCTAssertEqual(map.takeUnowned()?.start, 20)
        XCTAssertNil(map.takeUnowned())
        XCTAssertEqual(map.downloaded, 10 + 5 + 2)
    }

    func testAReleasedSegmentIsTakenAgain() throws {
        let map = SegmentMap.whole(10 * mb, have: 3 * mb)
        let segment = try XCTUnwrap(map.takeUnowned())
        XCTAssertEqual(segment.pos, 3 * mb, "a single file from an older run goes on where it ended")
        XCTAssertNil(map.takeUnowned())
        map.release(segment)
        XCTAssertTrue(map.takeUnowned() === segment)
    }

    func testManyWritersFillTheFileExactly() {
        let size = 8 * mb + 12345
        let map = SegmentMap.whole(size)
        let file = NSMutableData(length: Int(size))!
        let fileLock = NSLock()
        let written = NSLock()
        var total: Int64 = 0
        let chunk = Data(repeating: 1, count: 64 * 1024)
        DispatchQueue.concurrentPerform(iterations: 4) { _ in
            while let segment = map.nextWork() {
                while true {
                    let n = segment.write(chunk) { offset, data in
                        fileLock.withLock { file.replaceBytes(in: NSRange(location: Int(offset), length: data.count), withBytes: (data as NSData).bytes) }
                    }
                    if n == 0 { break }
                    written.withLock { total += Int64(n) }
                }
            }
        }
        XCTAssertTrue(map.isComplete)
        XCTAssertEqual(total, size)
        XCTAssertFalse((file as Data).contains(0))
        contiguous(map.snapshot(), size)
    }
}
