import Foundation

/// When a download that failed is worth trying again, and how long to wait first. A dropped
/// connection, a busy server or a time-out are passing; a refusal (an expired link, a missing
/// file) is not. Waits double from two seconds, with a little jitter, and a server's own
/// Retry-After is honoured. The schedule is after the Tachyon download manager's (MIT).
enum RetryPolicy {
    static let attempts = 6
    private static let longest: TimeInterval = 120

    /// Whether an answer with this status may come right on a later try.
    static func passing(_ status: Int?) -> Bool {
        guard let status else { return true }
        return status == 408 || status == 429 || status >= 500
    }

    /// The wait before try number `attempt` + 1, or nil to give up.
    /// - Parameters:
    ///   - attempt: Tries made so far, from 1.
    ///   - status: The HTTP status that ended the last try; nil for a dropped connection or a time-out.
    static func next(attempt: Int, status: Int?, retryAfter: TimeInterval? = nil, jitter: Double = 0.5) -> TimeInterval? {
        guard attempt < attempts, passing(status) else { return nil }
        if let asked = retryAfter, asked > 0 { return min(asked, longest) }
        let seconds = min(longest, 2 * pow(2, Double(attempt - 1)))
        return seconds * (0.75 + 0.5 * min(1, max(0, jitter)))
    }
}

/// The hours of the day downloads may run in, for a line that is cheaper or freer at night. From
/// 30nama's own desktop app. `from` to `to` in whole hours of local time, and across midnight
/// when `from` is the later one: 2 to 8 is the early morning, 23 to 7 the night.
enum DownloadHours {
    /// Whether downloads may run at `date`. No hours set, or the same hour twice, is any time.
    static func open(from: Int?, to: Int?, at date: Date, calendar: Calendar = .current) -> Bool {
        guard let start = from, let end = to, start != end else { return true }
        let hour = calendar.component(.hour, from: date)
        return start < end ? hour >= start && hour < end : hour >= start || hour < end
    }
}

/// A cap on how fast downloads come in, shared by every connection: a bucket that fills at the
/// allowed rate and holds at most a second's worth. Each piece that arrives is taken from it, and
/// a connection that has overdrawn it waits until it is back to zero.
final class RateLimit: @unchecked Sendable {
    private let lock = NSLock()
    private var allowance: Double = 0
    private var last: TimeInterval?
    private var rate: Double = 0

    /// Bytes a second; zero or less is no limit.
    var bytesPerSecond: Double {
        get { lock.withLock { rate } }
        set { lock.withLock { rate = newValue; allowance = min(allowance, max(0, newValue)) } }
    }

    /// Takes `bytes` from the budget at time `now` and says how long to wait before reading more.
    func take(_ bytes: Int, now: TimeInterval = ProcessInfo.processInfo.systemUptime) -> TimeInterval {
        lock.withLock {
            guard rate > 0 else { return 0 }
            if let last { allowance = min(rate, allowance + (now - last) * rate) }
            last = now
            allowance -= Double(bytes)
            return allowance >= 0 ? 0 : -allowance / rate
        }
    }
}
