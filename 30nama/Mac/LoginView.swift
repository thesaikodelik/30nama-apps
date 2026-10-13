import SwiftUI
import CoreImage.CIFilterBuiltins

/// Sign-in with a code. On a Mac the browser is one click away, so the code can be confirmed on
/// this machine as well as scanned with a phone.
struct LoginView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var code: String?
    @State private var link: URL?
    @State private var qr: PlatformImage?
    @State private var status = Status.waiting
    @State private var attempt = 0
    @State private var backdrop: Post?

    enum Status { case waiting, expired, failed, done }

    private static let pollInterval: Duration = .seconds(5)
    private static let lifetime: TimeInterval = 60

    var body: some View {
        ZStack {
            StageView(post: backdrop, dim: 0.55)
            HStack(alignment: .center, spacing: 72) {
                instructions
                tile
            }
            .padding(.horizontal, 80)
        }
        .task {
            backdrop = (try? await app.home())?.heroes.first { $0.cover != nil }
        }
        .task(id: attempt) { await runCode() }
    }

    private var instructions: some View {
        VStack(alignment: .leading, spacing: 18) {
            Image("Logo").resizable().scaledToFit().frame(height: 54)
            Text(loc(.scanTitle))
                .font(.app(40, .bold))
                .foregroundStyle(Theme.text1)
                .padding(.top, 10)
            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array([S.step1, .step2, .step3].enumerated()), id: \.offset) { index, key in
                    HStack(spacing: 14) {
                        Text(loc.num(index + 1))
                            .font(.app(15, .bold))
                            .foregroundStyle(.white)
                            .frame(width: 30, height: 30)
                            .background(Theme.accent, in: Circle())
                        Text(loc(key)).font(.app(18)).foregroundStyle(Theme.text2)
                    }
                }
            }
            if let code {
                VStack(alignment: .leading, spacing: 8) {
                    Text(loc(.orEnterCode)).font(.app(15)).foregroundStyle(Theme.text3)
                    Text(code)
                        .font(.system(size: 34, weight: .heavy, design: .monospaced))
                        .tracking(6)
                        .foregroundStyle(Theme.text1)
                        .textSelection(.enabled)
                        .environment(\.layoutDirection, .leftToRight)
                }
                .padding(.top, 8)
            }
            HStack(spacing: 12) {
                if let link, status == .waiting {
                    Button {
                        NSWorkspace.shared.open(link)
                    } label: {
                        Label("30nama.com", systemImage: "safari")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                }
                if status == .expired || status == .failed {
                    Button(loc(.newCode)) { attempt += 1 }.buttonStyle(PrimaryButtonStyle())
                }
                Button(loc(.guest)) { app.continueAsGuest() }.buttonStyle(GlassButtonStyle())
            }
            .padding(.top, 14)
        }
        .frame(maxWidth: 520, alignment: .leading)
    }

    private var tile: some View {
        VStack(spacing: 18) {
            ZStack {
                RoundedRectangle(cornerRadius: 28, style: .continuous).fill(.white)
                if let qr, status == .waiting {
                    Image(platformImage: qr)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .padding(22)
                        .transition(.opacity)
                }
                switch status {
                case .waiting:
                    if qr == nil { ProgressView().controlSize(.large) }
                case .expired, .failed:
                    Text(loc(status == .expired ? .expired : .codeFailed))
                        .font(.app(20, .bold))
                        .foregroundStyle(Theme.focusInk)
                case .done:
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 96))
                        .foregroundStyle(Theme.accent)
                        .transition(.scale(scale: 0.9).combined(with: .opacity))
                }
            }
            .frame(width: 300, height: 300)
            .shadow(color: .black.opacity(0.5), radius: 34, y: 18)
            .animation(.easeOut(duration: 0.26), value: status)

            HStack(spacing: 10) {
                // Present only while it has something to say: a dot that pulses unseen still costs.
                if status == .waiting, qr != nil { PulsingDot() }
                Text(loc(.waiting)).font(.app(15, .medium)).foregroundStyle(Theme.text2)
            }
            .frame(height: 20)
            .opacity(status == .waiting && qr != nil ? 1 : 0)
        }
    }

    // MARK: Flow

    /// Fetches a code, shows it, and polls until it is used or it expires.
    private func runCode() async {
        status = .waiting
        qr = nil
        code = nil
        link = nil
        guard let result = try? await app.api.get("qrlogin_code"), let fresh = result["code"].text else {
            if !Task.isCancelled { status = .failed }
            return
        }
        let address = result["url"].text ?? "https://30nama.com/auth/\(fresh)"
        code = fresh
        link = URL(string: address)
        qr = LoginView.qrImage(address)
        let started = Date()
        while !Task.isCancelled {
            try? await Task.sleep(for: LoginView.pollInterval)
            guard !Task.isCancelled else { return }
            if Date().timeIntervalSince(started) > LoginView.lifetime {
                status = .expired
                return
            }
            // Unused codes answer `success: false`; keep polling.
            guard let e = try? await app.api.call("qrlogin_login", ["auth_code": fresh]), e.success,
                  let token = User(e.result)?.token else { continue }
            status = .done
            try? await Task.sleep(for: .milliseconds(500))
            await app.signIn(token: token)
            return
        }
    }

    static func qrImage(_ text: String) -> PlatformImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 16, y: 16))
        return CIContext().createCGImage(scaled, from: scaled.extent).map { PlatformImage(cgImage: $0) }
    }
}

private struct PulsingDot: View {
    @Environment(\.lively) private var lively
    @State private var on = false

    var body: some View {
        Circle()
            .fill(Theme.accent)
            .frame(width: 9, height: 9)
            .opacity(on || !lively ? 1 : 0.3)
            .onAppear {
                // A pulse never ends, so a Mac that is sparing itself gets a steady dot.
                guard lively else { return }
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { on = true }
            }
    }
}
