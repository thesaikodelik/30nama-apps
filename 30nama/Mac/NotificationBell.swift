import SwiftUI

/// The account's messages from 30nama: a bell beside the downloads disc, with how many are new,
/// opening a panel of them. A guest has none, and the bell keeps its place without showing.
struct NotificationBell: View {
    @Environment(AppModel.self) private var app
    @Environment(\.loc) private var loc
    @State private var hovering = false
    @State private var open = false

    var body: some View {
        let unread = app.notifications?.unread ?? 0
        Button { open.toggle() } label: {
            ZStack {
                Circle().fill(Color.white.opacity(open ? 0.18 : hovering ? 0.12 : 0)).padding(4)
                Image(systemName: unread > 0 ? "bell.badge" : "bell")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Theme.text1.opacity(hovering || open ? 1 : 0.78))
            }
            .frame(width: 48, height: 48)
            .glass(Circle())
            .overlay(alignment: .topTrailing) {
                if unread > 0 {
                    Text(loc.num(min(unread, 99)))
                        .font(.app(11, .heavy))
                        .foregroundStyle(.white)
                        .frame(minWidth: 19, minHeight: 19)
                        .background(Circle().fill(Theme.accent))
                        .offset(x: 3, y: -3)
                }
            }
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .onHover { hovering = $0 }
        .help(loc(.notifications))
        .accessibilityLabel(unread > 0 ? "\(loc(.notifications)), \(loc.num(unread)) \(loc(.unread))" : loc(.notifications))
        .popover(isPresented: $open, arrowEdge: .bottom) {
            NotificationPanel { open = false }
                .environment(app)
                .environment(\.loc, loc)
                .environment(\.layoutDirection, loc.lang == .en ? .leftToRight : .rightToLeft)
        }
        .opacity(app.user == nil ? 0 : 1)
        .allowsHitTesting(app.user != nil)
        .accessibilityHidden(app.user == nil)
    }
}

private struct NotificationPanel: View {
    let close: () -> Void

    @Environment(AppModel.self) private var app
    @Environment(MacNav.self) private var nav
    @Environment(\.loc) private var loc

    var body: some View {
        let page = app.notifications
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(loc(.notifications)).font(.app(17, .bold)).foregroundStyle(Theme.text1)
                Spacer()
                if (page?.unread ?? 0) > 0 {
                    Button(loc(.markAllRead)) { app.markNotificationRead("all") }
                        .buttonStyle(.plain)
                        .font(.app(13, .semibold))
                        .foregroundStyle(Theme.accent)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            Divider().opacity(0.4)
            if let items = page?.items, !items.isEmpty {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(items) { item in
                            Row(item: item) { act(on: item) }
                            if item.id != items.last?.id { Divider().opacity(0.25).padding(.horizontal, 18) }
                        }
                    }
                }
                .frame(maxHeight: 460)
            } else {
                Text(loc(.noNotifications))
                    .font(.app(14))
                    .foregroundStyle(Theme.text3)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
            }
        }
        .frame(width: 380)
    }

    private func act(on item: AccountNotification) {
        if !item.read { app.markNotificationRead(item.id) }
        if let post = item.post {
            close()
            nav.open(post)
        } else if let link = item.link {
            close()
            NSWorkspace.shared.open(link)
        }
    }

    private struct Row: View {
        let item: AccountNotification
        let action: () -> Void

        @Environment(\.loc) private var loc
        @State private var hovering = false

        var body: some View {
            Button(action: action) {
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(item.read ? Color.clear : Theme.accent)
                        .frame(width: 8, height: 8)
                        .padding(.top, 6)
                    VStack(alignment: .leading, spacing: 4) {
                        if let title = item.title {
                            Text(title).font(.app(14, item.read ? .medium : .bold)).foregroundStyle(Theme.text1)
                        }
                        if let content = item.content {
                            Text(content).font(.app(13)).foregroundStyle(Theme.text2).lineLimit(4)
                        }
                        if let date = loc.lang == .fa ? item.date.jalali ?? item.date.gregorian : item.date.gregorian ?? item.date.jalali {
                            Text(loc.num(date)).font(.app(11)).foregroundStyle(Theme.text3)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .multilineTextAlignment(.leading)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(Color.white.opacity(hovering ? 0.06 : 0))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .onHover { hovering = $0 }
        }
    }
}
