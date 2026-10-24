import SwiftUI
import UniformTypeIdentifiers

/// Account, language, playback and subtitle choices, with what the app is and is not beside them.
struct SettingsView: View {
    @Environment(AppModel.self) private var app
    @Environment(Updater.self) private var updater
    @Environment(\.loc) private var loc
    @State private var lookingForUpdate = false


    var body: some View {
        ZStack {
            AmbientBackground()
            ScrollView(.vertical) {
                HStack(alignment: .top, spacing: 56) {
                    identity
                    VStack(alignment: .leading, spacing: 22) {
                        // No account part: the account and signing out are on the profile, and
                        // iCloud is not open to a Mac build signed without an Apple developer account.
                        // For a member whose profile on the site is private: the site will not give
                        // their name, so it can be written here.
                        if app.user != nil {
                            group(loc(.profile)) {
                                VStack(alignment: .leading, spacing: 2) {
                                    row("\(loc(.yourName)) (\(loc(.optional)))") {
                                        TextField("", text: $typedName)
                                            .textFieldStyle(.plain)
                                            .font(.app(14))
                                            .padding(.horizontal, 12)
                                            .frame(width: 252, height: 32)
                                            .background(Capsule(style: .continuous).fill(Color.white.opacity(0.08)))
                                            .accessibilityLabel(loc(.yourName))
                                    }
                                    Text(loc(.yourNameHint))
                                        .font(.app(12))
                                        .foregroundStyle(Theme.text3)
                                        .padding(.horizontal, 16)
                                        .padding(.bottom, 8)
                                }
                            }
                        }
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
                        // Keys the user makes on other sites, kept on this Mac; everything works without them.
                        group(loc(.services)) {
                            keyField(loc(.subsourceKey), hint: loc(.subsourceKeyHint), text: $subsourceKey)
                            keyField(loc(.tmdbKey), hint: loc(.tmdbKeyHint), text: $tmdbKey)
                        }
                        group(loc(.about)) {
                            // The app looks by itself every hour; this is for when a release was
                            // just announced.
                            row(loc(.version)) {
                                Text(loc.num(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—"))
                                    .font(.app(15)).foregroundStyle(Theme.text3)
                                Button {
                                    Task { await lookForUpdate() }
                                } label: {
                                    if lookingForUpdate { ProgressView().controlSize(.small) } else { Text(loc(.checkForUpdate)) }
                                }
                                .buttonStyle(GlassButtonStyle(height: 34))
                                .disabled(lookingForUpdate)
                            }
                            fact(loc(.apiHost), hostName)
                            row(loc(.reportProblem)) {
                                Button(loc(.reportProblem)) { reportProblem() }.buttonStyle(GlassButtonStyle(height: 34))
                            }
                        }
                    }
                    .frame(maxWidth: 620)
                }
                .padding(.horizontal, Mac.edge)
                .padding(.top, 100)
                .padding(.bottom, 60)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.never)
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
            row(loc(.autoSkip)) {
                toggle(app.settings.autoSkip) { value in app.update { $0.autoSkip = value } }
            }
            row(loc(.aroundPicture)) {
                picker([false, true], selection: ambientLight, name: { loc($0 ? .sceneGlow : .blackBars) }) { ambientLight = $0 }
            }
            // Read when a stream is opened, so it counts from the next one.
            row(loc(.bufferTime)) {
                picker([0, 15, 30, 60, 120], selection: app.settings.bufferAhead,
                       name: { $0 == 0 ? loc(.qualityAuto) : "\(loc.num($0)) \(loc(.seconds))" }) { value in
                    app.update { $0.bufferAhead = value }
                }
            }
        }
    }

    /// A new issue on the project's GitHub page, with what a report needs to be acted on already
    /// filled in. Nothing about the account goes with it.
    private func reportProblem() {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let system = ProcessInfo.processInfo.operatingSystemVersionString
        let body = """
        **What happened / چه اتفاقی افتاد:**


        **What I expected / چه انتظاری داشتم:**


        ---
        30nama for Mac \(version) · macOS \(system) · \(app.settings.lang.rawValue)
        """
        var parts = URLComponents(string: "https://github.com/thesaikodelik/30nama-apps/issues/new")!
        parts.queryItems = [URLQueryItem(name: "body", value: body)]
        if let url = parts.url { NSWorkspace.shared.open(url) }
    }

    /// The user's own SubSource key, read by `SubtitleFinder`. Never leaves this Mac.
    @AppStorage("subsourceKey") private var subsourceKey = ""
    /// The user's own TMDB key or read access token, read by `TMDBStore`.
    @AppStorage("tmdbKey") private var tmdbKey = ""

    private func keyField(_ label: String, hint: String, text: Binding<String>) -> some View {
        KeyField(label: label, hint: hint, text: text) { row($0, control: $1) }
    }

    /// The member's own name, for when their profile on the site is private. Read by the profile.
    @AppStorage("displayName") private var typedName = ""

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
            row(loc(.downloadsAtOnce)) {
                picker([1, 2, 3, 5], selection: app.settings.downloadsAtOnce, name: { loc.num($0) }) { value in
                    app.update { $0.downloadsAtOnce = value }
                    downloads.applyRules()
                }
            }
            row(loc(.downloadSpeed)) {
                picker([0, 1, 2, 5, 10, 20], selection: app.settings.downloadLimit,
                       name: { $0 == 0 ? loc(.unlimited) : "\(loc.num($0)) MB/s" }) { value in
                    app.update { $0.downloadLimit = value }
                    downloads.applyRules()
                }
            }
            row(loc(.downloadHours)) {
                hourPicker(app.settings.downloadFrom) { value in app.update { $0.downloadFrom = value } }
                if app.settings.downloadFrom != nil || app.settings.downloadTo != nil {
                    Text(loc(.hoursTo)).font(.app(13)).foregroundStyle(Theme.text3)
                    hourPicker(app.settings.downloadTo) { value in app.update { $0.downloadTo = value } }
                }
            }
            .help(loc(.downloadHoursHint))
        }
    }

    /// An hour of the day, or none: any time.
    private func hourPicker(_ selection: Int?, set: @escaping (Int?) -> Void) -> some View {
        picker([-1] + Array(0..<24), selection: selection ?? -1,
               name: { $0 < 0 ? loc(.anyTime) : loc.num(String(format: "%02d:00", $0)) }) { value in
            set(value < 0 ? nil : value)
            downloads.applyRules()
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

    private func lookForUpdate() async {
        lookingForUpdate = true
        defer { lookingForUpdate = false }
        switch await updater.look(asked: true) {
        case .upToDate: app.showToast(loc(.upToDate))
        case .noAnswer: app.showToast(loc(.error))
        // Found: the update's own card says so. Nil: one is already on its way.
        case .found, nil: break
        }
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

/// A key the user makes on another site. It is kept as soon as it is typed or pasted, which a
/// hidden field did not show: a key that is there now says so, in the app's red, can be shown to check it,
/// and removed.
private struct KeyField<Row: View>: View {
    let label: String
    let hint: String
    @Binding var text: String
    let row: (String, () -> AnyView) -> Row

    @Environment(\.loc) private var loc
    @State private var shown = false

    init(label: String, hint: String, text: Binding<String>, row: @escaping (String, () -> AnyView) -> Row) {
        self.label = label
        self.hint = hint
        _text = text
        self.row = row
    }

    var body: some View {
        let saved = !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        VStack(alignment: .leading, spacing: 2) {
            row(label) {
                AnyView(HStack(spacing: 6) {
                    Group {
                        if shown { TextField("", text: $text) } else { SecureField("", text: $text) }
                    }
                    .textFieldStyle(.plain)
                    .font(.system(size: 13, design: .monospaced))
                    .accessibilityLabel(label)
                    if saved {
                        Button { shown.toggle() } label: {
                            Image(systemName: shown ? "eye.slash" : "eye").font(.system(size: 12, weight: .semibold))
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Theme.text3)
                        .help(shown ? loc(.hideKey) : loc(.showKey))
                        .accessibilityLabel(shown ? loc(.hideKey) : loc(.showKey))
                        Button { text = ""; shown = false } label: {
                            Image(systemName: "xmark.circle.fill").font(.system(size: 13))
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Theme.text3)
                        .help(loc(.removeKey))
                        .accessibilityLabel(loc(.removeKey))
                    }
                }
                .padding(.horizontal, 12)
                .frame(width: 252, height: 32)
                .background(Capsule(style: .continuous).fill(Color.white.opacity(0.08)))
                .overlay { Capsule(style: .continuous).strokeBorder(Theme.accent.opacity(saved ? 0.55 : 0), lineWidth: 1) }
                .environment(\.layoutDirection, .leftToRight))
            }
            Group {
                if saved {
                    // The app's own red, on the tick only: the words in the usual colour, so it does
                    // not read as an error.
                    Label {
                        Text(loc(.keySaved)).foregroundStyle(Theme.text2)
                    } icon: {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.accent)
                    }
                } else {
                    Text(hint).foregroundStyle(Theme.text3)
                }
            }
            .font(.app(12))
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
            .animation(.easeOut(duration: 0.2), value: saved)
        }
    }
}

