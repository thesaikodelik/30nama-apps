import AppKit
import SwiftUI

/// The window opens where and as it was closed (Twitter feedback 4): its frame through AppKit's own
/// autosave, which keeps it on a screen that is still there, and filling the screen again if it
/// did when the app quit. Not when only the player filled it: that was for one film.
struct WindowPlace: NSViewRepresentable {
    let playerOpen: () -> Bool

    func makeNSView(context: Context) -> NSView {
        let view = Watcher()
        view.playerOpen = playerOpen
        return view
    }

    func updateNSView(_ view: NSView, context: Context) {
        (view as? Watcher)?.playerOpen = playerOpen
    }

    private final class Watcher: NSView {
        var playerOpen: () -> Bool = { false }
        private var placed = false
        private var watching: [NSObjectProtocol] = []
        private static let name = "main"
        private static let fullKey = "window.fullScreen"

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            guard let window, !placed else { return }
            placed = true
            window.setFrameUsingName(Self.name)
            window.setFrameAutosaveName(Self.name)
            if UserDefaults.standard.bool(forKey: Self.fullKey), !window.styleMask.contains(.fullScreen) {
                // Once the window is on screen; asked for before, AppKit lets it go.
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak window] in window?.toggleFullScreen(nil) }
            }
            let save: (Notification) -> Void = { [weak self, weak window] _ in
                guard let self, let window else { return }
                UserDefaults.standard.set(window.styleMask.contains(.fullScreen) && !self.playerOpen(), forKey: Self.fullKey)
            }
            let center = NotificationCenter.default
            watching = [
                center.addObserver(forName: NSApplication.willTerminateNotification, object: nil, queue: .main, using: save),
                center.addObserver(forName: NSWindow.willCloseNotification, object: window, queue: .main, using: save),
            ]
        }

        deinit { watching.forEach(NotificationCenter.default.removeObserver) }
    }
}
