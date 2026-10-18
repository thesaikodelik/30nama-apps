import Foundation

/// What a transfer remembers between runs, beside its partial file.
struct TransferRecord: Codable {
    var size: Int64
    var splittable: Bool
    var segments: [SegmentState]
}

/// One file fetched over up to four connections at once, into a partial file that is handed over
/// once every byte is in.
///
/// It asks first for the file's first byte, to learn its size and whether the server sends parts.
/// Then each connection takes a stretch of the file (`SegmentMap`): what an earlier run left, or
/// the second half of the stretch with the most left. A connection that drops is tried again
/// after a wait (`RetryPolicy`); a refusal ends the transfer. Every connection draws on one shared
/// speed limit. On 30nama's own servers one connection gave 11 to 18 MB/s and four gave 33; eight
/// gave no more.
///
/// Everything it does happens on its own serial queue, the one its session reports on.
final class Transfer: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    enum Outcome {
        /// The whole file, in its partial place, to be moved where it belongs.
        case done(URL)
        /// What went wrong. What arrived stays, for a later run to go on from.
        case failed(String)
    }

    /// The speed limit every transfer shares.
    static let limit = RateLimit()
    static let connections = 4

    let id: String
    private let address: URL
    private let part: URL
    private let recordFile: URL
    private let progress: @Sendable (_ received: Int64, _ expected: Int64) -> Void
    private let finished: @Sendable (Outcome) -> Void
    /// What the transfer is doing, in a line, for a test to read.
    var note: (@Sendable (String) -> Void)?

    private let queue: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .utility
        return queue
    }()
    private var session: URLSession!

    private final class Worker {
        let segment: SegmentMap.Segment
        var attempt = 0
        /// Bytes this connection has brought in, over all its tries.
        var received: Int64 = 0
        /// Held still by the speed limit until then (system uptime), or not held.
        var heldUntil: TimeInterval?
        /// Set when the connection is cut on purpose: its stretch is done, or was taken over.
        var ending = false
        init(_ segment: SegmentMap.Segment) { self.segment = segment }
    }

    private var map: SegmentMap?
    /// How many connections the server is taking; fewer than asked for when one goes unanswered.
    private var cap = Transfer.connections
    /// Connections waiting to try again: they still count against the cap.
    private var waiting = 0
    private var size: Int64 = 0
    private var file: FileHandle?
    private var workers: [Int: Worker] = [:]
    private var probe: URLSessionDataTask?
    private var probeAttempt = 0
    private var stopped = false
    private var over = false
    private var lastReport = Date.distantPast
    private var lastSave = Date.distantPast

    /// - Parameters:
    ///   - folder: Where the partial file and its record are kept.
    ///   - progress: Bytes in and bytes in all, now and then; on the transfer's own queue.
    ///   - finished: Called once, unless the transfer is stopped first; on the transfer's own queue.
    init(id: String, address: URL, folder: URL,
         progress: @escaping @Sendable (Int64, Int64) -> Void, finished: @escaping @Sendable (Outcome) -> Void) {
        self.id = id
        self.address = address
        part = Transfer.partFile(id, in: folder)
        recordFile = Transfer.recordFile(id, in: folder)
        self.progress = progress
        self.finished = finished
        super.init()
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 30
        configuration.httpMaximumConnectionsPerHost = Transfer.connections + 1
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        session = URLSession(configuration: configuration, delegate: self, delegateQueue: queue)
    }

    static func partFile(_ id: String, in folder: URL) -> URL { folder.appendingPathComponent(id + ".part") }
    static func recordFile(_ id: String, in folder: URL) -> URL { folder.appendingPathComponent(id + ".json") }

    /// Throws away what a transfer left: its partial file and its record.
    static func discard(_ id: String, in folder: URL) {
        try? FileManager.default.removeItem(at: partFile(id, in: folder))
        try? FileManager.default.removeItem(at: recordFile(id, in: folder))
    }

    /// Bytes an earlier run left, for the list to show before this one starts.
    static func saved(_ id: String, in folder: URL) -> (received: Int64, expected: Int64)? {
        guard let data = try? Data(contentsOf: recordFile(id, in: folder)),
              let record = try? JSONDecoder().decode(TransferRecord.self, from: data) else { return nil }
        return (record.segments.reduce(0) { $0 + $1.downloaded }, record.size)
    }

    func start() {
        queue.addOperation { [self] in
            guard !stopped else { return }
            try? FileManager.default.createDirectory(at: part.deletingLastPathComponent(), withIntermediateDirectories: true)
            // Going on from an earlier run, if its record and its file are both still here.
            if let data = try? Data(contentsOf: recordFile), let record = try? JSONDecoder().decode(TransferRecord.self, from: data),
               FileManager.default.fileExists(atPath: part.path), let handle = try? FileHandle(forWritingTo: part) {
                file = handle
                size = record.size
                map = SegmentMap(record.segments, splittable: record.splittable)
                report(force: true)
                fill()
            } else {
                Transfer.discard(id, in: part.deletingLastPathComponent())
                ask()
            }
        }
    }

    /// Cuts every connection and keeps what arrived, for `start` on a new transfer to go on from.
    func stop() {
        queue.addOperation { [self] in
            guard !stopped else { return }
            stopped = true
            probe?.cancel()
            for worker in workers.values { worker.ending = true }
            session.invalidateAndCancel()
            save()
            try? file?.close()
            file = nil
        }
    }

    // MARK: Learning the size

    private func ask() {
        var request = URLRequest(url: address)
        request.setValue("bytes=0-0", forHTTPHeaderField: "Range")
        let task = session.dataTask(with: request)
        probe = task
        task.resume()
    }

    private func learned(from response: HTTPURLResponse) {
        // "bytes 0-0/1234567": the server sends parts, and this is the size.
        let whole = (response.value(forHTTPHeaderField: "Content-Range")?.split(separator: "/").last).flatMap { Int64($0) }
        let splittable = response.statusCode == 206 && whole != nil
        size = whole ?? (response.expectedContentLength > 0 ? response.expectedContentLength : Int64.max)
        guard FileManager.default.createFile(atPath: part.path, contents: nil), let handle = try? FileHandle(forWritingTo: part) else {
            end(.failed("cannot write \(part.path)"))
            return
        }
        file = handle
        map = splittable ? .whole(size) : SegmentMap([SegmentState(start: 0, pos: 0, end: size)], splittable: false)
        save()
        report(force: true)
        if size == 0 { finish() } else { fill() }
    }

    // MARK: Connections

    /// Puts every free connection to work, up to the most allowed.
    private func fill() {
        guard !stopped, let map else { return }
        while workers.count + waiting < cap, let segment = map.nextWork() {
            launch(Worker(segment))
        }
        if workers.isEmpty, waiting == 0, map.isComplete { finish() }
    }

    private func launch(_ worker: Worker) {
        guard !stopped else { return }
        var request = URLRequest(url: address)
        if map?.splittable == true || worker.segment.pos > 0 {
            // Open-ended: the stretch's end can move closer while it is coming, when another
            // connection takes over its second half; this connection is cut when it gets there.
            request.setValue("bytes=\(worker.segment.pos)-", forHTTPHeaderField: "Range")
        }
        let task = session.dataTask(with: request)
        worker.ending = false
        workers[task.taskIdentifier] = worker
        task.resume()
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if dataTask === probe {
            completionHandler(.cancel)
            probe = nil
            if let http = response as? HTTPURLResponse, status < 400 {
                learned(from: http)
            } else {
                retryProbe(status: status, response: response)
            }
            return
        }
        guard let worker = workers[dataTask.taskIdentifier] else {
            completionHandler(.cancel)
            return
        }
        // A whole file sent for a request from the middle would be written in the wrong place.
        if status == 200, worker.segment.pos > 0 {
            completionHandler(.cancel)
            end(.failed("the server sent the whole file for a part of it"))
            return
        }
        completionHandler(status < 400 ? .allow : .cancel)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard !stopped, let worker = workers[dataTask.taskIdentifier], let file else { return }
        let written: Int
        do {
            written = try worker.segment.write(data) { offset, bytes in
                try file.seek(toOffset: UInt64(offset))
                try file.write(contentsOf: bytes)
            }
        } catch {
            end(.failed("writing: \(error.localizedDescription)"))
            return
        }
        worker.attempt = 0
        worker.received += Int64(written)
        if written < data.count || worker.segment.isComplete {
            worker.ending = true
            dataTask.cancel()
        } else {
            let wait = Transfer.limit.take(written)
            if wait > 0.02 {
                // Held still, the connection stops reading and the server slows to match. Pieces
                // already on their way still come, and each pushes the end of the hold further.
                let until = ProcessInfo.processInfo.systemUptime + wait
                if worker.heldUntil == nil {
                    worker.heldUntil = until
                    dataTask.suspend()
                    hold(worker, dataTask)
                } else {
                    worker.heldUntil = max(worker.heldUntil ?? 0, until)
                }
            }
        }
        report(force: false)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard !stopped else { return }
        // The first question went unanswered (an answer would have set the probe aside).
        if task === probe {
            probe = nil
            retryProbe(status: 0, response: nil)
            return
        }
        guard let map else { return }
        guard let worker = workers.removeValue(forKey: task.taskIdentifier) else { return }
        let status = (task.response as? HTTPURLResponse)?.statusCode
        let refused = status.map { $0 >= 400 } ?? false
        if worker.ending || (error == nil && !refused && worker.segment.isComplete) {
            fill()
            return
        }
        // A server that does not say the size ends the one connection at the end of the file.
        if error == nil, !refused, !map.splittable, size == Int64.max {
            size = worker.segment.pos
            finish()
            return
        }
        // Some servers take only so many connections from one address, and leave the rest
        // unanswered or say they are too many (429, 503). Such a connection, while others are
        // working, gives its stretch back to them, and the transfer goes on with fewer.
        let crowded = refused ? [429, 503].contains(status ?? 0) : worker.received == 0
        if crowded, !workers.isEmpty {
            cap = max(1, workers.count)
            map.release(worker.segment)
            note?("a connection went unanswered; going on with \(cap)")
            fill()
            return
        }
        worker.attempt += 1
        let retryAfter = (task.response as? HTTPURLResponse)?.value(forHTTPHeaderField: "Retry-After").flatMap(TimeInterval.init)
        guard let wait = RetryPolicy.next(attempt: worker.attempt, status: refused ? status : nil, retryAfter: retryAfter, jitter: .random(in: 0...1)) else {
            end(.failed(refused ? "HTTP \(status ?? 0)" : error?.localizedDescription ?? "the connection ended early"))
            return
        }
        note?("connection \(worker.segment.start / 1_048_576) MB: \(refused ? "HTTP \(status ?? 0)" : error?.localizedDescription ?? "ended early"); again in \(Int(wait)) s")
        // The stretch stays this connection's while it waits, so no other takes it up meanwhile.
        waiting += 1
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + wait) { [self] in
            queue.addOperation { [self] in
                waiting -= 1
                launch(worker)
            }
        }
    }

    /// Lets a held connection go once its hold is over, however far pieces still arriving pushed it.
    private func hold(_ worker: Worker, _ task: URLSessionDataTask) {
        let left = (worker.heldUntil ?? 0) - ProcessInfo.processInfo.systemUptime
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + max(0.01, left)) { [self] in
            queue.addOperation { [self] in
                guard !stopped, let until = worker.heldUntil else { return }
                if ProcessInfo.processInfo.systemUptime >= until {
                    worker.heldUntil = nil
                    task.resume()
                } else {
                    hold(worker, task)
                }
            }
        }
    }

    private func retryProbe(status: Int, response: URLResponse?) {
        probeAttempt += 1
        guard let wait = RetryPolicy.next(attempt: probeAttempt, status: status == 0 ? nil : status) else {
            end(.failed(status == 0 ? "no answer" : "HTTP \(status)"))
            return
        }
        DispatchQueue.global(qos: .utility).asyncAfter(deadline: .now() + wait) { [self] in
            queue.addOperation { [self] in if !stopped { ask() } }
        }
    }

    // MARK: Ending

    private func finish() {
        guard !over else { return }
        over = true
        stopped = true
        try? file?.synchronize()
        try? file?.close()
        file = nil
        try? FileManager.default.removeItem(at: recordFile)
        session.finishTasksAndInvalidate()
        report(force: true)
        finished(.done(part))
    }

    private func end(_ outcome: Outcome) {
        guard !over else { return }
        over = true
        stopped = true
        probe?.cancel()
        for worker in workers.values { worker.ending = true }
        session.invalidateAndCancel()
        save()
        try? file?.close()
        file = nil
        finished(outcome)
    }

    private func save() {
        guard let map else { return }
        let record = TransferRecord(size: size, splittable: map.splittable, segments: map.snapshot())
        if let data = try? JSONEncoder().encode(record) { try? data.write(to: recordFile, options: .atomic) }
        lastSave = Date()
    }

    /// A few times a second for the bar, and the record every few seconds in case the app is quit.
    private func report(force: Bool) {
        guard let map else { return }
        let now = Date()
        if force || now.timeIntervalSince(lastReport) > 0.5 {
            lastReport = now
            progress(map.downloaded, size == Int64.max ? 0 : size)
        }
        if now.timeIntervalSince(lastSave) > 3 { save() }
    }
}
