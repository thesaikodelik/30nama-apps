import SwiftUI
import WebKit

/// A title's trailer, playing over the window in YouTube's own player. YouTube cannot be reached
/// everywhere without a VPN; when the player has not started after a while, the sheet says so
/// rather than staying dark.
struct TrailerSheet: View {
    let title: String
    let video: String
    let close: () -> Void

    @Environment(\.loc) private var loc
    @State private var unreachable = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        ZStack {
            Color.black.opacity(0.6)
                .ignoresSafeArea()
                .onTapGesture(perform: close)
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("\(loc(.trailer)) · \(title)").font(.app(17, .bold)).foregroundStyle(Theme.text1).lineLimit(1)
                    Spacer()
                    Button(action: close) {
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Theme.text1)
                            .frame(width: 30, height: 30)
                            .background(Circle().fill(Color.white.opacity(0.12)))
                            .contentShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(loc(.close))
                }
                ZStack {
                    YouTubePlayer(video: video) { unreachable = !$0 }
                    if unreachable {
                        VStack(spacing: 10) {
                            Image(systemName: "wifi.exclamationmark").font(.system(size: 30))
                            Text(loc(.trailerUnreachable)).font(.app(15, .medium)).multilineTextAlignment(.center)
                        }
                        .foregroundStyle(Theme.text2)
                        .padding(30)
                    }
                }
                .aspectRatio(16 / 9, contentMode: .fit)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .padding(20)
            .frame(width: 960)
            // A click on the card stays on it; only one on the dark around it closes the sheet.
            .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .onTapGesture {}
            .background(.ultraThinMaterial, in: shape)
            .background(shape.fill(Theme.bg1.opacity(0.55)))
            .overlay(shape.strokeBorder(Color.white.opacity(0.12), lineWidth: 1))
            .shadow(color: .black.opacity(0.5), radius: 40, y: 20)
        }
    }
}

/// YouTube's embedded player, loaded as the page itself.
private struct YouTubePlayer: NSViewRepresentable {
    let video: String
    /// Whether the player came up.
    let reached: (Bool) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(reached: reached) }

    func makeNSView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.mediaTypesRequiringUserActionForPlayback = []
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = context.coordinator
        view.setValue(false, forKey: "drawsBackground")
        // The player itself as the page, so that this app's leave to play sound without a click
        // applies to it; YouTube wants to know which page it is on, and is told.
        // YouTube starts on its own only without sound; it starts muted and the sound is turned on
        // the moment it is playing.
        var request = URLRequest(url: URL(string: "https://www.youtube-nocookie.com/embed/\(video)?autoplay=1&mute=1&rel=0&playsinline=1&iv_load_policy=3")!)
        request.setValue("https://github.com/thesaikodelik/30nama-apps", forHTTPHeaderField: "Referer")
        view.load(request)
        context.coordinator.watch()
        context.coordinator.unmute(view)
        return view
    }

    func updateNSView(_ view: WKWebView, context: Context) {}

    static func dismantleNSView(_ view: WKWebView, coordinator: Coordinator) {
        coordinator.stop()
        // Stops the sound at once; a web view left to go away on its own can keep playing a while.
        view.loadHTMLString("", baseURL: nil)
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        let reached: (Bool) -> Void
        private var check: Task<Void, Never>?

        init(reached: @escaping (Bool) -> Void) { self.reached = reached }

        /// The embed page itself is local; what tells whether YouTube answers is a plain request to it.
        func watch() {
            check = Task { @MainActor [reached] in
                var request = URLRequest(url: URL(string: "https://www.youtube-nocookie.com/")!)
                request.httpMethod = "HEAD"
                request.timeoutInterval = 8
                let answered = (try? await URLSession.shared.data(for: request)) != nil
                if !Task.isCancelled { reached(answered) }
            }
        }

        private var sound: Task<Void, Never>?

        @MainActor func unmute(_ view: WKWebView) {
            sound = Task { @MainActor [weak view] in
                for _ in 0..<40 {
                    try? await Task.sleep(for: .milliseconds(300))
                    guard !Task.isCancelled, let view else { return }
                    // Told to play if it is waiting for a click; once it is moving, given its sound.
                    let script = "(function(){var v=document.querySelector('video');var p=document.getElementById('movie_player');if(!v)return false;if(v.currentTime<0.2){if(p&&p.playVideo)p.playVideo();else v.play();return false}if(p&&p.unMute){p.unMute();p.setVolume(100)}v.muted=false;return true})()"
                    if (try? await view.evaluateJavaScript(script)) as? Bool == true { return }
                }
            }
        }

        func stop() {
            check?.cancel()
            sound?.cancel()
        }
    }
}

/// A trailer playing silently behind a title page, in place of a stretch of the film. YouTube's
/// player draws its own title and buttons at first; the picture is shown only once the video is
/// really moving and those have gone. If it has not started in time (YouTube is out of reach on
/// some connections), `failed` is called and the page can show something else.
struct BackdropTrailer: View {
    let video: String
    let started: () -> Void
    let failed: () -> Void

    var body: some View {
        GeometryReader { geo in
            // Filling the page like the still behind it: wide enough to cover, cropped at the edges.
            let width = max(geo.size.width, geo.size.height * 16 / 9) * 1.04
            YouTubeBackdrop(video: video, started: started, failed: failed)
                .frame(width: width, height: width * 9 / 16)
                .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
        .clipped()
        .allowsHitTesting(false)
    }
}

private struct YouTubeBackdrop: NSViewRepresentable {
    let video: String
    let started: () -> Void
    let failed: () -> Void

    /// Where the trailer starts: past studio cards and the first fade from black.
    static let skip = 8

    func makeCoordinator() -> Coordinator { Coordinator(started: started, failed: failed) }

    func makeNSView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.mediaTypesRequiringUserActionForPlayback = []
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.setValue(false, forKey: "drawsBackground")
        let parameters = "autoplay=1&mute=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1&iv_load_policy=3&loop=1&playlist=\(video)&start=\(YouTubeBackdrop.skip)"
        var request = URLRequest(url: URL(string: "https://www.youtube-nocookie.com/embed/\(video)?\(parameters)")!)
        request.setValue("https://github.com/thesaikodelik/30nama-apps", forHTTPHeaderField: "Referer")
        view.load(request)
        context.coordinator.watch(view)
        return view
    }

    func updateNSView(_ view: WKWebView, context: Context) {}

    static func dismantleNSView(_ view: WKWebView, coordinator: Coordinator) {
        coordinator.stop()
        view.loadHTMLString("", baseURL: nil)
    }

    final class Coordinator {
        let started: () -> Void
        let failed: () -> Void
        private var check: Task<Void, Never>?

        init(started: @escaping () -> Void, failed: @escaping () -> Void) {
            self.started = started
            self.failed = failed
        }

        /// The page is YouTube's own, so its video element can be asked how far it has played.
        @MainActor func watch(_ view: WKWebView) {
            check = Task { @MainActor [weak view, started, failed] in
                let skip = Double(YouTubeBackdrop.skip)
                for _ in 0..<40 {
                    try? await Task.sleep(for: .milliseconds(300))
                    guard !Task.isCancelled, let view else { return }
                    let at = (try? await view.evaluateJavaScript("(function(){var v=document.querySelector('video');return v?v.currentTime:0})()")) as? Double ?? 0
                    // A couple of seconds in: playing for real, and YouTube's title has faded.
                    if at > skip + 2.5 {
                        started()
                        return
                    }
                }
                if !Task.isCancelled { failed() }
            }
        }

        func stop() { check?.cancel() }
    }
}
