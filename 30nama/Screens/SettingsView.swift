import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc

    @State private var confirmingSignOut = false

    var body: some View {
        HStack(alignment: .top, spacing: 80) {
            identity
            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 14) {
                    account
                    group(loc(.language)) {
                        choice(loc(.language), value: app.settings.lang.name) {
                            app.update { $0.lang = next(after: $0.lang, in: Lang.allCases) }
                        }
                    }
                    group(loc(.playback)) {
                        choice(loc(.quality), value: loc(app.settings.preferAdaptive ? .qualityAuto : .qualityBest)) {
                            app.update { $0.preferAdaptive.toggle() }
                        }
                        choice(loc(.defaultSubs), value: subtitleName(app.settings.defaultSubtitle)) {
                            app.update { $0.defaultSubtitle = next(after: $0.defaultSubtitle, in: ["fa", "en", "off"]) }
                        }
                        choice(loc(.subSize), value: sizeName(app.settings.subtitleSize)) {
                            app.update { $0.subtitleSize = next(after: $0.subtitleSize, in: Settings.subtitleSizes) }
                        }
                        choice(loc(.subColor), value: loc(app.settings.subtitleColor.label)) {
                            app.update { $0.subtitleColor = next(after: $0.subtitleColor, in: SubtitleColor.allCases) }
                        }
                        choice(loc(.subBackground), value: loc(app.settings.subtitleBackground.label)) {
                            app.update { $0.subtitleBackground = next(after: $0.subtitleBackground, in: SubtitleBackground.allCases) }
                        }
                        subtitleSample
                        choice(loc(.autoplay), value: loc(app.settings.autoplayNext ? .on : .off)) {
                            app.update { $0.autoplayNext.toggle() }
                        }
                        choice(loc(.autoSkip), value: loc(app.settings.autoSkip ? .on : .off)) {
                            app.update { $0.autoSkip.toggle() }
                        }
                    }
                    group(loc(.display)) {
                        choice(loc(.reduceMotion), value: loc(app.settings.reduceMotion ? .on : .off)) {
                            app.update { $0.reduceMotion.toggle() }
                        }
                    }
                    group(loc(.about)) {
                        fact(loc(.version), Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                        fact(loc(.apiHost), hostName)
                    }
                }
                .padding(.horizontal, 30)
                .padding(.vertical, 30)
            }
            .scrollIndicators(.hidden)
            .scrollClipDisabled()
            .frame(width: 980)
        }
        .padding(.horizontal, Theme.edge)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(AmbientBackground())
        .overlay(alignment: .top) { TopShade() }
        .alert(loc(.signOutTitle), isPresented: $confirmingSignOut) {
            Button(loc(.signOut), role: .destructive) { app.signOut() }
            Button(loc(.cancel), role: .cancel) {}
        } message: {
            Text(loc(.signOutBody))
        }
    }

    // MARK: Panes

    private var identity: some View {
        VStack(alignment: .leading, spacing: 22) {
            Wordmark(size: 84)
            Text(loc(.unofficial))
                .font(.app(24))
                .foregroundStyle(Theme.text3)
                .lineSpacing(6)
                .multilineTextAlignment(.leading)
        }
        .frame(width: 560, alignment: .leading)
        .padding(.top, 150)
    }

    @ViewBuilder
    private var account: some View {
        group(loc(.account)) {
            if let user = app.user {
                fact(loc(.account), user.name ?? "—")
                if let type = user.type { fact(loc(.accountType), loc.accountType(type)) }
                if let expiry = user.expiry { fact(loc(.expires), loc.num(expiry)) }
                Button { confirmingSignOut = true } label: {
                    Text(loc(.signOut)).font(.app(28, .medium))
                }
                .buttonStyle(RowButtonStyle())
            } else {
                fact(loc(.account), loc(.guestAccount))
                Button { app.requestSignIn() } label: {
                    Label(loc(.signInQR), systemImage: "qrcode").font(.app(28, .medium))
                }
                .buttonStyle(RowButtonStyle())
            }
            fact(loc(.cloudRatings), loc(cloudLabel))
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

    /// Live preview over a bright, busy picture, where the choice of background actually matters.
    private var subtitleSample: some View {
        let style = app.settings.subtitleBackground
        let plate = RoundedRectangle(cornerRadius: 14, style: .continuous)
        return Text(loc(.sample))
            .font(.app(CGFloat(app.settings.subtitleSize), .medium))
            .foregroundStyle(Color(uiColor: app.settings.subtitleColor.color))
            .shadow(color: .black.opacity(style == .none ? 1 : 0), radius: 3, y: 2)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .padding(.horizontal, 24)
            .padding(.vertical, 8)
            .background {
                if style.blurStrength > 0 {
                    plate.fill(.ultraThinMaterial).environment(\.colorScheme, .dark).opacity(style.blurStrength)
                } else {
                    plate.fill(Color.black.opacity(style.plateOpacity))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 150)
            .background {
                LinearGradient(colors: [Color(hex: 0xF2A65A), Color(hex: 0xD9567B), Color(hex: 0x5B6CFF), Color(hex: 0x3FC1C9)],
                               startPoint: .topLeading, endPoint: .bottomTrailing)
            }
            .clipShape(RoundedRectangle(cornerRadius: Theme.tileRadius, style: .continuous))
    }

    // MARK: Building blocks

    private func group<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.app(24, .semibold))
                .foregroundStyle(Theme.text3)
                .padding(.top, 22)
                .padding(.leading, 6)
            content()
        }
    }

    /// A setting that cycles through its values when pressed.
    private func choice(_ label: String, value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).font(.app(28, .medium))
                Spacer()
                Text(value).font(.app(26)).opacity(0.7)
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 20, weight: .semibold)).opacity(0.45)
            }
        }
        .buttonStyle(RowButtonStyle())
    }

    private func fact(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.app(28, .medium)).foregroundStyle(Theme.text2)
            Spacer()
            Text(value).font(.app(26)).foregroundStyle(Theme.text3).lineLimit(1)
        }
        .padding(.horizontal, 32)
        .frame(minHeight: 72)
    }

    private func next<T: Equatable>(after value: T, in values: [T]) -> T {
        let i = values.firstIndex(of: value) ?? -1
        return values[(i + 1) % values.count]
    }

    private func subtitleName(_ code: String) -> String {
        switch code {
        case "fa": return loc(.persian)
        case "en": return loc(.english)
        default: return loc(.off)
        }
    }

    private func sizeName(_ size: Int) -> String {
        switch size {
        case ..<42: return loc(.small)
        case 50...: return loc(.large)
        default: return loc(.medium)
        }
    }

    private var hostName: String {
        guard let host = app.apiHost, let name = URL(string: host)?.host else { return "—" }
        return name
    }
}
