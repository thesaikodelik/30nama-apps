import SwiftUI
import CoreImage.CIFilterBuiltins

/// Sign-in by scanning a QR code with a phone that is logged in to 30nama.
struct LoginView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var code: String?
    @State private var qr: UIImage?
    @State private var status = Status.waiting
    /// Bumped to request a fresh code.
    @State private var attempt = 0
    @State private var backdrop: Post?
    @FocusState private var focus: Target?

    enum Status { case waiting, expired, failed, done }
    enum Target: Hashable { case guest, newCode }

    private static let pollInterval: Duration = .seconds(5)
    private static let lifetime: TimeInterval = 60

    var body: some View {
        ZStack {
            StageView(post: backdrop, dim: 0.55)
            HStack(alignment: .center, spacing: 0) {
                instructions
                Spacer(minLength: 60)
                tile
            }
            .padding(.horizontal, 110)
        }
        .ignoresSafeArea()
        .task {
            // A different one of the best-regarded titles each time the screen comes up.
            backdrop = (try? await app.home())?.backdrops().randomElement()
        }
        .task(id: attempt) { await runCode() }
        .onAppear { focus = .guest }
    }

    private var instructions: some View {
        VStack(alignment: .leading, spacing: 26) {
            Wordmark(size: 44)
            Text(loc(.scanTitle))
                .font(.app(60, .bold))
                .foregroundStyle(Theme.text1)
                .padding(.top, 8)
            VStack(alignment: .leading, spacing: 20) {
                ForEach(Array([S.step1, .step2, .step3].enumerated()), id: \.offset) { index, key in
                    HStack(spacing: 18) {
                        Text(loc.num(index + 1))
                            .font(.app(24, .bold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(Theme.accent, in: Circle())
                        Text(loc(key))
                            .font(.app(28))
                            .foregroundStyle(Theme.text2)
                    }
                }
            }
            if let code {
                HStack(alignment: .firstTextBaseline, spacing: 18) {
                    Text(loc(.orEnterCode))
                        .font(.app(24))
                        .foregroundStyle(Theme.text3)
                    Text(code)
                        .font(.system(size: 44, weight: .bold, design: .monospaced))
                        .tracking(5)
                        .foregroundStyle(Theme.text1)
                        .environment(\.layoutDirection, .leftToRight)
                }
                .padding(.top, 6)
            }
            HStack(spacing: 16) {
                Button(loc(.guest)) { app.continueAsGuest() }
                    .buttonStyle(PillButtonStyle(kind: .primary))
                    .focused($focus, equals: .guest)
                Button(loc(.newCode)) { attempt += 1 }
                    .buttonStyle(PillButtonStyle())
                    .focused($focus, equals: .newCode)
            }
            .padding(.top, 22)
        }
        .frame(maxWidth: 900, alignment: .leading)
    }

    private var tile: some View {
        VStack(spacing: 26) {
            ZStack {
                RoundedRectangle(cornerRadius: 28, style: .continuous).fill(.white)
                if let qr {
                    Image(uiImage: qr)
                        .interpolation(.none)
                        .resizable()
                        .padding(30)
                        .opacity(status == .waiting ? 1 : 0.12)
                }
                switch status {
                case .waiting:
                    if qr == nil { ProgressView().tint(.black) }
                case .expired, .failed:
                    Text(loc(status == .expired ? .expired : .codeFailed))
                        .font(.app(30, .bold))
                        .foregroundStyle(Theme.focusInk)
                case .done:
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 150))
                        .foregroundStyle(Theme.accent)
                        .transition(.scale(scale: 0.9).combined(with: .opacity))
                }
            }
            .frame(width: 460, height: 460)
            .shadow(color: .black.opacity(0.5), radius: 40, y: 20)
            .animation(.easeOut(duration: 0.26), value: status)

            HStack(spacing: 12) {
                PulsingDot()
                Text(loc(.waiting))
                    .font(.app(24, .medium))
                    .foregroundStyle(Theme.text2)
            }
            .opacity(status == .waiting && qr != nil ? 1 : 0)
        }
    }

    // MARK: Flow

    /// Fetches a code, shows it, and polls until it is used or it expires.
    private func runCode() async {
        status = .waiting
        qr = nil
        code = nil
        guard let result = try? await app.api.get("qrlogin_code"), let fresh = result["code"].text else {
            if !Task.isCancelled { status = .failed; focus = .newCode }
            return
        }
        code = fresh
        qr = LoginView.qrImage(result["url"].text ?? "https://30nama.com/auth/\(fresh)")
        let started = Date()
        while !Task.isCancelled {
            try? await Task.sleep(for: LoginView.pollInterval)
            guard !Task.isCancelled else { return }
            if Date().timeIntervalSince(started) > LoginView.lifetime {
                status = .expired
                focus = .newCode
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

    static func qrImage(_ text: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 16, y: 16))
        return CIContext().createCGImage(scaled, from: scaled.extent).map { UIImage(cgImage: $0) }
    }
}

private struct PulsingDot: View {
    @State private var on = false

    var body: some View {
        Circle()
            .fill(Theme.accent)
            .frame(width: 12, height: 12)
            .opacity(on ? 1 : 0.3)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { on = true }
            }
    }
}
