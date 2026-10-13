#if DEBUG
import Foundation

/// Development aid: takes the app straight to a screen, so it can be looked at without clicking
/// the way there. Not compiled into release builds.
///
/// - `NAMA_SECTION=movies` opens that section (the names of `MacSection`).
/// - `NAMA_OPEN=first` opens the first featured title, `NAMA_OPEN=continue` the title last watched,
///   and `NAMA_OPEN=<post id>` one from the home feed.
/// - `NAMA_PLAY=1` also starts it playing. With `NAMA_DEMO=1` that needs no subscription.
/// - `NAMA_MUTE=1` starts the player silent, and `NAMA_CONTROLS=1` keeps its controls on screen.
enum DebugLaunch {
    private static let environment = ProcessInfo.processInfo.environment

    static var section: String? { environment["NAMA_SECTION"] }
    static var open: String? { environment["NAMA_OPEN"] }
    static var play: Bool { environment["NAMA_PLAY"] == "1" }
    static var mute: Bool { environment["NAMA_MUTE"] == "1" }
    static var holdControls: Bool { environment["NAMA_CONTROLS"] == "1" }
}
#endif
