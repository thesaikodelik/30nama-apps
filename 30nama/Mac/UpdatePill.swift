import SwiftUI

/// A newer version, told in a glass capsule that drops from under the top bar. While it
/// downloads, the capsule fills with the app's red like a glass being poured; full, it offers to
/// relaunch into the new version.
struct UpdatePill: View {
    /// Where its top sits below the top of the bar: just clear of the bar's lower edge.
    static let drop: CGFloat = 48 + 6

    @Environment(Updater.self) private var updater
    @Environment(\.loc) private var loc
    @Environment(\.lively) private var lively

    var body: some View {
        Group {
            if !updater.postponed, let release = shown {
                pill(release)
                    // Out from behind the bar, and back behind it when it goes.
                    .transition(.offset(y: -UpdatePill.drop).combined(with: .scale(scale: 0.94, anchor: .top)))
            }
        }
        // Slower than the app's usual glide: news that has just come should be seen coming.
        .animation(.spring(response: 0.8, dampingFraction: 0.82), value: updater.phase)
        .animation(.spring(response: 0.6, dampingFraction: 0.9), value: updater.postponed)
    }

    private var shown: MacRelease? {
        switch updater.phase {
        case .idle: return nil
        case .available(let r), .downloading(let r, _), .ready(let r), .failed(let r): return r
        }
    }

    private var fill: Double {
        switch updater.phase {
        case .downloading(_, let progress): return progress
        case .ready: return 1
        default: return 0
        }
    }

    private func pill(_ release: MacRelease) -> some View {
        let shape = Capsule(style: .continuous)
        return HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .bold))
                // White once the red has started to rise: it fills from this end first.
                .foregroundStyle(fill > 0 ? .white : Theme.accent)
                .contentTransition(.symbolEffect(.replace))
            Text(message(release))
                .font(.app(14, .semibold))
                .foregroundStyle(Theme.text1)
                .monospacedDigit()
                .contentTransition(.numericText())
                .lineLimit(1)
            action
        }
        .padding(.leading, 16)
        .padding(.trailing, 6)
        .frame(height: 44)
        .background { Liquid(level: fill, moving: lively && fill > 0 && fill < 1).clipShape(shape) }
        .glass(shape)
        .shadow(color: Theme.accent.opacity(fill * 0.45), radius: 18, y: 6)
        .fixedSize()
    }

    private var icon: String {
        switch updater.phase {
        case .ready: return "checkmark.circle.fill"
        case .failed: return "exclamationmark.triangle.fill"
        default: return "arrow.down.circle.fill"
        }
    }

    private func message(_ release: MacRelease) -> String {
        switch updater.phase {
        case .downloading(_, let progress): return "\(loc(.updating)) \(loc.num(Int(progress * 100)))\(loc.lang == .en ? "%" : "٪")"
        case .ready: return loc(.updateReady)
        case .failed: return loc(.updateFailed)
        default: return "\(loc(.newVersion)) \(loc.num(release.version))"
        }
    }

    @ViewBuilder
    private var action: some View {
        switch updater.phase {
        case .available:
            HStack(spacing: 4) {
                PillButton(title: loc(.installUpdate), prominent: true) { updater.install() }
                PillButton(symbol: "xmark", title: loc(.later)) { updater.postpone() }
            }
        case .downloading:
            Color.clear.frame(width: 6, height: 32)
        case .ready:
            PillButton(title: loc(.relaunch), prominent: true) { updater.relaunch() }
        case .failed:
            HStack(spacing: 4) {
                PillButton(title: loc(.downloadPage), prominent: true) { updater.openPage() }
                PillButton(symbol: "xmark", title: loc(.later)) { updater.postpone() }
            }
        case .idle:
            EmptyView()
        }
    }
}

private struct PillButton: View {
    var symbol: String? = nil
    let title: String
    var prominent = false
    let action: () -> Void

    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            Group {
                if let symbol {
                    Image(systemName: symbol).font(.system(size: 11, weight: .bold)).frame(width: 32)
                } else {
                    Text(title).font(.app(13.5, .bold)).padding(.horizontal, 14)
                }
            }
            .foregroundStyle(prominent ? Theme.focusInk : Theme.text1.opacity(hovering ? 1 : 0.7))
            .frame(height: 32)
            .background(Capsule(style: .continuous).fill(prominent ? Color.white.opacity(hovering ? 1 : 0.92) : Color.white.opacity(hovering ? 0.14 : 0)))
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .animation(.easeOut(duration: 0.14), value: hovering)
        .help(symbol == nil ? "" : title)
        .accessibilityLabel(title)
    }
}

/// Red rising in the capsule from the side a line starts on, its surface swaying while it fills.
private struct Liquid: View {
    let level: Double
    /// The surface moves. Off on a Mac that should not redraw without end; the level still rises.
    let moving: Bool

    var body: some View {
        if moving {
            TimelineView(.animation) { context in
                surface(phase: context.date.timeIntervalSinceReferenceDate)
            }
        } else {
            surface(phase: 0)
        }
    }

    private func surface(phase: Double) -> some View {
        GeometryReader { geo in
            Wave(level: level, phase: phase * 3, depth: level > 0 && level < 1 ? 5 : 0)
                .fill(LinearGradient(colors: [Theme.accent.opacity(0.95), Color(hex: 0xFF7A5C).opacity(0.9)], startPoint: .leading, endPoint: .trailing))
                // Rises from the right in a right-to-left language, where its line starts.
                .flipsForRightToLeftLayoutDirection(true)
                .frame(width: geo.size.width, height: geo.size.height)
        }
        .animation(.easeOut(duration: 0.35), value: level)
    }
}

/// The filled part of the capsule: up to `level` of its width, with a wavy front edge.
private struct Wave: Shape {
    var level: Double
    var phase: Double
    var depth: Double

    var animatableData: Double {
        get { level }
        set { level = newValue }
    }

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let front = rect.width * level
        guard front > 0 else { return path }
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: front, y: rect.minY))
        let steps = 24
        for i in 0...steps {
            let y = rect.height * Double(i) / Double(steps)
            let x = front + depth * sin(Double(i) / Double(steps) * .pi * 2 + phase)
            path.addLine(to: CGPoint(x: min(rect.width, x), y: y))
        }
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
