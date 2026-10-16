import SwiftUI

/// Every key the player answers to, on a glass card over the picture. Opened with ? or the
/// keyboard button; most people never find shortcuts that nothing on screen mentions.
struct ShortcutSheet: View {
    let close: () -> Void

    @Environment(\.loc) private var loc

    private var rows: [(keys: [String], what: String)] {
        [
            (["Space"], "\(loc(.play)) / \(loc(.pause))"),
            (["←", "→"], "\(loc(.back10)) / \(loc(.forward10))"),
            (["↑", "↓"], loc(.volume)),
            (["M"], loc(.mute)),
            (["F"], loc(.fullScreen)),
            (["Return"], "\(loc(.fullScreen)) · \(loc(.skipIntro))"),
            (["P"], loc(.pictureInPicture)),
            (["T"], loc(.alwaysOnTop)),
            (["N"], loc(.nextEpisode)),
            (["E"], loc(.episodes)),
            (["[", "]"], "\(loc(.slower)) / \(loc(.faster))"),
            (["G", "H"], "\(loc(.subtitleEarlier)) / \(loc(.subtitleLater))"),
            (["Esc"], loc(.back)),
        ]
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        let half = (rows.count + 1) / 2
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text(loc(.shortcuts)).font(.app(20, .bold)).foregroundStyle(Theme.text1)
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
            HStack(alignment: .top, spacing: 36) {
                column(rows.prefix(half))
                column(rows.dropFirst(half))
            }
        }
        .padding(26)
        .frame(width: 720)
        .background(Color.black.opacity(0.5), in: shape)
        .glass(shape)
        .shadow(color: .black.opacity(0.5), radius: 40, y: 20)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }

    private func column(_ part: ArraySlice<(keys: [String], what: String)>) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(part.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 12) {
                    HStack(spacing: 4) {
                        ForEach(row.keys, id: \.self) { KeyCap(label: $0) }
                    }
                    // Keys are named as printed on the keyboard, left to right, in any language.
                    .environment(\.layoutDirection, .leftToRight)
                    .frame(width: 92, alignment: .leading)
                    Text(row.what)
                        .font(.app(13.5, .medium))
                        .foregroundStyle(Theme.text2)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct KeyCap: View {
    let label: String

    var body: some View {
        Text(label)
            .font(.system(size: 12, weight: .semibold, design: .rounded))
            .foregroundStyle(Theme.text1)
            .padding(.horizontal, 7)
            .frame(minWidth: 26, minHeight: 24)
            .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Color.white.opacity(0.12)))
            .overlay(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.22), lineWidth: 1)
            }
    }
}
