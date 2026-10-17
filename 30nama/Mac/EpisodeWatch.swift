import Foundation
import UserNotifications

/// Tells the user, with a system notification, when a series they follow has a new episode: one
/// on their watchlist or notify list, or one they are part way through.
@MainActor
final class EpisodeWatch {
    static let shared = EpisodeWatch()

    private let defaults = UserDefaults.standard
    /// Looks every few hours: each look is several list requests to a rate-limited server.
    private let spacing: TimeInterval = 6 * 3600

    func look(_ app: AppModel) async {
        guard app.isLoggedIn else { return }
        let last = defaults.double(forKey: "episodeWatch.last")
        guard Date().timeIntervalSince1970 - last > spacing else { return }

        // Only when both lists came: with one missing, the days remembered for its series would be
        // dropped as if they were no longer followed, and their episodes never told of.
        guard let upcoming = await app.upcomingIfReachable(),
              let watching = await app.continueWatchingIfReachable(background: true) else { return }
        // Counted from a look that worked; one that failed is tried again within the hour.
        defaults.set(Date().timeIntervalSince1970, forKey: "episodeWatch.last")
        let followed = upcoming + watching.filter(\.isSeries)
        guard !followed.isEmpty else { return }
        var alerts = EpisodeAlerts(plist: defaults.dictionary(forKey: "episodeWatch.expected") as? [String: Double] ?? [:])
        let out = alerts.update(with: followed)
        defaults.set(alerts.plist, forKey: "episodeWatch.expected")
        guard !out.isEmpty else { return }

        // Asked the first time there is something to say, not when the app opens.
        let center = UNUserNotificationCenter.current()
        guard (try? await center.requestAuthorization(options: [.alert, .sound])) == true else { return }
        for post in out {
            let content = UNMutableNotificationContent()
            content.title = app.loc(.newEpisode)
            content.body = post.title(app.loc.lang)
            content.sound = .default
            try? await center.add(UNNotificationRequest(identifier: "episode.\(post.id)", content: content, trigger: nil))
        }
    }
}
