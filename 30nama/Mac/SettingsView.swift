import SwiftUI
import UniformTypeIdentifiers

/// Account, language, playback and subtitle choices, with what the app is and is not beside them.
struct SettingsView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var confirmingSignOut = false

    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                HStack(alignment: .top, spacing: 56) {
                    identity
                    VStack(alignment: .leading, spacing: 22) {
                        account
                        group(loc(.language)) {
                            row(loc(.language)) {
                                picker(Lang.allCases, selection: app.settings.lang, name: \.name) { value in app.update { $0.lang = value } }
                            }
                        }
                        playback
                        subtitles
                        storage
                        group(loc(.performance)) {
                            row(loc(.effects)) {
                                picker(Effects.allCases, selection: app.settings.effects, name: { loc($0.label) }) { value in
                                    app.update { $0.effects = value }
                                }
                            }
                        }
                        group(loc(.about)) {
                            fact(loc(.version), loc.num(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"))
                            fact(loc(.apiHost), hostName)
                        }
                    }
                    .frame(maxWidth: 620)
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 100)
                .padding(.bottom, 60)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
        }
        .alert(loc(.signOutTitle), isPresented: $confirmingSignOut) {
            Button(loc(.signOut), role: .destructive) { app.signOut() }
            Button(loc(.cancel), role: .cancel) {}
        } message: {
            Text(loc(.signOutBody))
        }
    }

    // MARK: Panes

    private var identity: some View {
        VStack(alignment: .leading, spacing: 16) {
            Image("Logo").resizable().scaledToFit().frame(height: 64)
            Text(loc(.unofficial))
                .font(.app(15))
                .foregroundStyle(Theme.text3)
                .lineSpacing(5)
                .multilineTextAlignment(.leading)
        }
        .frame(width: 320, alignment: .leading)
        .padding(.top, 30)
    }

    private var account: some View {
        group(loc(.account)) {
            if let user = app.user {
                fact(loc(.account), user.name ?? "—")
                if let type = user.type { fact(loc(.accountType), loc.accountType(type)) }
                if let expiry = user.expiry { fact(loc(.expires), loc.num(expiry)) }
            } else {
                fact(loc(.account), loc(.guestAccount))
            }
            fact(loc(.cloudRatings), loc(cloudLabel))
            if !app.ratings.isEmpty {
                row(loc(.yourRating)) {
                    Button(loc(.exportRatings)) { exportRatings() }.buttonStyle(GlassButtonStyle(height: 34))
                }
            }
            row("") {
                if app.user != nil {
                    Button(loc(.signOut)) { confirmingSignOut = true }.buttonStyle(GlassButtonStyle(height: 34))
                } else {
                    Button { app.requestSignIn() } label: {
                        Label(loc(.signInQR), systemImage: "qrcode")
                    }
                    .buttonStyle(GlassButtonStyle(height: 34))
                }
            }
        }
    }

    private var playback: some View {
        group(loc(.playback)) {
            row(loc(.quality)) {
                picker([false, true], selection: app.settings.preferAdaptive, name: { $0 ? loc(.qualityAuto) : loc(.qualityBest) }) { value in
                    app.update { $0.preferAdaptive = value }
                }
            }
            row(loc(.audio)) {
                picker([AudioMix.stereo, .surround, .original], selection: app.settings.audioMix,
                       name: { loc($0 == .stereo ? .audioStereo : $0 == .surround ? .audioSurround : .audioOriginal) }) { value in
                    app.update { $0.audioMix = value }
                }
            }
            row(loc(.autoplay)) {
                toggle(app.settings.autoplayNext) { value in app.update { $0.autoplayNext = value } }
            }
            row(loc(.aroundPicture)) {
                picker([false, true], selection: ambientLight, name: { loc($0 ? .sceneGlow : .blackBars) }) { ambientLight = $0 }
            }
        }
    }

    /// Read by the player; kept on this Mac only, like the other things that are about the screen.
    @AppStorage("ambientLight") private var ambientLight = true

    @Environment(Downloads.self) private var downloads

    private var storage: some View {
        group(loc(.downloads)) {
            row(loc(.downloadFolder)) {
                Text("\u{200E}" + (downloads.folder.path as NSString).abbreviatingWithTildeInPath)
                    .font(.app(13)).foregroundStyle(Theme.text3).lineLimit(1).truncationMode(.middle)
                    .environment(\.layoutDirection, .leftToRight)
                Button(loc(.choose)) { chooseFolder() }.buttonStyle(GlassButtonStyle(height: 30))
                if app.settings.downloadFolder != nil {
                    Button(loc(.reset)) { app.update { $0.downloadFolder = nil } }.buttonStyle(GlassButtonStyle(height: 30))
                }
            }
        }
    }

    private func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.canCreateDirectories = true
        panel.directoryURL = downloads.folder
        guard panel.runModal() == .OK, let url = panel.url else { return }
        app.update { $0.downloadFolder = url.path }
    }

    private var subtitles: some View {
        group(loc(.subtitles)) {
            row(loc(.defaultSubs)) {
                picker(["fa", "en", "off"], selection: app.settings.defaultSubtitle,
                       name: { $0 == "fa" ? loc(.persian) : $0 == "en" ? loc(.english) : loc(.off) }) { value in
                    app.update { $0.defaultSubtitle = value }
                }
            }
            row(loc(.subSize)) {
                picker(Settings.subtitleSizes, selection: app.settings.subtitleSize,
                       name: { $0 < 42 ? loc(.small) : $0 >= 50 ? loc(.large) : loc(.medium) }) { value in
                    app.update { $0.subtitleSize = value }
                }
            }
            row(loc(.subColor)) {
                picker(SubtitleColor.allCases, selection: app.settings.subtitleColor, name: { loc($0.label) }) { value in
                    app.update { $0.subtitleColor = value }
                }
            }
            row(loc(.subBackground)) {
                picker(SubtitleBackground.allCases, selection: app.settings.subtitleBackground, name: { loc($0.label) }) { value in
                    app.update { $0.subtitleBackground = value }
                }
            }
            sample
        }
    }

    /// A live preview over a bright, busy picture, where the choice of background actually matters.
    private var sample: some View {
        let style = app.settings.subtitleBackground
        let plate = RoundedRectangle(cornerRadius: 8, style: .continuous)
        return Text(loc(.sample))
            .font(.app(CGFloat(app.settings.subtitleSize) * 0.5, .semibold))
            .foregroundStyle(Color(nsColor: app.settings.subtitleColor.color))
            .shadow(color: .black.opacity(style == .none ? 1 : 0), radius: 2, y: 1)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .padding(.horizontal, 14)
            .padding(.vertical, 5)
            .background {
                if style.blurStrength > 0 {
                    plate.fill(style.blurStrength < 1 ? AnyShapeStyle(.ultraThinMaterial) : AnyShapeStyle(.regularMaterial))
                } else {
                    plate.fill(Color.black.opacity(style.plateOpacity))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 96)
            .background {
                LinearGradient(colors: [Color(hex: 0xF2A65A), Color(hex: 0xD9567B), Color(hex: 0x5B6CFF), Color(hex: 0x3FC1C9)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
    }

    /// Saves the ratings as a CSV wherever the user says. Letterboxd's importer reads the file as it is.
    private func exportRatings() {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "30nama-ratings.csv"
        panel.allowedContentTypes = [.commaSeparatedText]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            try app.ratings.csv().write(to: url, atomically: true, encoding: .utf8)
            app.showToast(loc(.exportDone))
        } catch {
            app.showToast(loc(.error))
        }
    }

    private var cloudLabel: S {
        switch app.cloud {
        case .idle, .syncing: return .cloudSyncing
        case .synced: return .cloudSynced
        case .signedOut: return .cloudSignedOut
        case .failed: return .cloudFailed
        }
    }

    private var hostName: String {
        guard let host = app.apiHost, let name = URL(string: host)?.host else { return "—" }
        return name
    }

    // MARK: Building blocks

    private func group<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.app(14, .semibold)).foregroundStyle(Theme.text3).padding(.leading, 6)
            VStack(spacing: 0) { content() }
                .padding(.vertical, 4)
                .glass(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

    private func row<Control: View>(_ label: String, @ViewBuilder control: () -> Control) -> some View {
        HStack(spacing: 12) {
            Text(label).font(.app(15, .medium)).foregroundStyle(Theme.text1)
            Spacer(minLength: 0)
            control()
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 46)
    }

    private func fact(_ label: String, _ value: String) -> some View {
        row(label) {
            Text(value).font(.app(15)).foregroundStyle(Theme.text3).lineLimit(1)
        }
    }

    /// A choice from a short list: a glass capsule showing what is chosen, all of them the same
    /// width so that they line up down the page, opening the system's menu with a tick by the
    /// current one.
    private func picker<Value: Hashable>(_ values: [Value], selection: Value, name: @escaping (Value) -> String, set: @escaping (Value) -> Void) -> some View {
        SwiftUI.Menu {
            Picker("", selection: Binding(get: { selection }, set: set)) {
                ForEach(values, id: \.self) { Text(name($0)).tag($0) }
            }
            .pickerStyle(.inline)
            .labelsHidden()
        } label: {
            ChoiceLabel(text: name(selection))
        }
        .menuStyle(.button)
        .buttonStyle(.plain)
        .menuIndicator(.hidden)
        .fixedSize()
    }

    private func picker<Value: Hashable>(_ values: [Value], selection: Value, name: KeyPath<Value, String>, set: @escaping (Value) -> Void) -> some View {
        picker(values, selection: selection, name: { $0[keyPath: name] }, set: set)
    }

    private func toggle(_ value: Bool, set: @escaping (Bool) -> Void) -> some View {
        Toggle("", isOn: Binding(get: { value }, set: set))
            .toggleStyle(.switch)
            .labelsHidden()
            .tint(Theme.accent)
    }
}

/// The face of a settings dropdown.
private struct ChoiceLabel: View {
    let text: String

    @State private var hovering = false

    var body: some View {
        HStack(spacing: 8) {
            Text(text)
                .font(.app(14, .semibold))
                .foregroundStyle(Theme.text1)
                .lineLimit(1)
            Spacer(minLength: 6)
            Image(systemName: "chevron.up.chevron.down")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Theme.text3)
        }
        .padding(.horizontal, 16)
        .frame(width: 252, height: 36)
        .background(Capsule(style: .continuous).fill(Color.white.opacity(hovering ? 0.16 : 0.08)))
        .overlay { Capsule(style: .continuous).strokeBorder(Color.white.opacity(hovering ? 0.22 : 0.1), lineWidth: 1) }
        .contentShape(Capsule(style: .continuous))
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
    }
}
