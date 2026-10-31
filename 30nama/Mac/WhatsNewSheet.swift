import SwiftUI

/// What each version brought, over the window: the version sections of the Mac release's text on
/// GitHub. That text is written in Persian, so it reads right to left whatever the app's language.
struct WhatsNewSheet: View {
    let close: () -> Void

    @Environment(Updater.self) private var updater
    @Environment(\.loc) private var loc
    @State private var blocks: [ReleaseNotes.Block]?
    @State private var failed = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        ZStack {
            Color.black.opacity(0.6)
                .ignoresSafeArea()
                .onTapGesture(perform: close)
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(loc(.whatsNew)).font(.app(17, .bold)).foregroundStyle(Theme.text1)
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
                if loc.lang != .fa {
                    Text(loc(.notesInPersian)).font(.app(13)).foregroundStyle(Theme.text3)
                }
                Group {
                    if let blocks {
                        ScrollView(.vertical) {
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(Array(blocks.enumerated()), id: \.offset) { _, block in
                                    line(block)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .environment(\.layoutDirection, .rightToLeft)
                        }
                        .scrollIndicators(.never)
                        // Arriving after the spinner, the list otherwise opened a little way down.
                        .defaultScrollAnchor(.top)
                    } else if failed {
                        Text(loc(.error)).font(.app(15, .medium)).foregroundStyle(Theme.text2)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        ProgressView().controlSize(.regular)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                .frame(height: 520)
            }
            .padding(24)
            .frame(width: 640)
            // A click on the card stays on it; only one on the dark around it closes the sheet.
            .contentShape(shape)
            .onTapGesture {}
            .background(.ultraThinMaterial, in: shape)
            .background(shape.fill(Theme.bg1.opacity(0.55)))
            .overlay(shape.strokeBorder(Color.white.opacity(0.12), lineWidth: 1))
            .shadow(color: .black.opacity(0.5), radius: 40, y: 20)
        }
        .task {
            blocks = await updater.whatsNew()
            failed = blocks == nil
        }
    }

    @ViewBuilder private func line(_ block: ReleaseNotes.Block) -> some View {
        switch block {
        case .version(let text):
            Text(text).font(.app(18, .bold)).foregroundStyle(Theme.text1)
                .padding(.top, 14)
        case .group(let text):
            Text(text).font(.app(14, .semibold)).foregroundStyle(Theme.text2)
                .padding(.top, 6)
        case .item(let text):
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("•").foregroundStyle(Theme.text3)
                Text(Self.inline(text)).fixedSize(horizontal: false, vertical: true)
            }
            .font(.app(14)).foregroundStyle(Theme.text1)
        case .text(let text):
            Text(Self.inline(text)).font(.app(14)).foregroundStyle(Theme.text1)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// Bold and code in a line, as GitHub shows them.
    private static func inline(_ text: String) -> AttributedString {
        (try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(text)
    }
}
