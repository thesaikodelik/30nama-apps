import Foundation

/// A message 30nama sends to the account: news, a title that arrived, an offer.
///
/// The shape is read from what 30nama's own desktop app reads (`notification`, answered with
/// `{notifications, total_unread, page}`); no answer of the server's own has been seen yet, so
/// every field is optional and the date is taken in any of the forms the API uses elsewhere.
struct AccountNotification: Identifiable {
    enum Kind { case plain, link, post, pricing, stream }

    let id: String
    var title: String?
    /// The text, with the HTML it is sent with taken out.
    var content: String?
    var read: Bool
    var image: URL?
    var kind: Kind
    var link: URL?
    var post: Post?
    var date: (gregorian: String?, jalali: String?)

    init?(_ j: JSON) {
        guard let id = j["id"].text else { return nil }
        self.id = id
        title = j["title"].text
        content = AccountNotification.plainText(j["content"].text)
        read = j["read"].bool
        image = j["image"].url ?? j["image"]["webp"].url ?? j["image"]["jpg"].url
        switch j["notification_type"].text {
        case "link": kind = .link
        case "post": kind = .post
        case "pricing": kind = .pricing
        case "stream": kind = .stream
        default: kind = .plain
        }
        link = j["link"].url
        post = j["post"].object != nil ? Post(j["post"]) : nil
        let d = j["date"]
        date = d.object != nil ? (d["g"].text, d["j"].text) : (d.text, nil)
    }

    static func plainText(_ html: String?) -> String? {
        guard var text = html else { return nil }
        text = text.replacingOccurrences(of: #"<br\s*/?>"#, with: "\n", options: [.regularExpression, .caseInsensitive])
        text = text.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        for (entity, character) in [("&nbsp;", " "), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&#39;", "'"), ("&zwnj;", "\u{200C}"), ("&amp;", "&")] {
            text = text.replacingOccurrences(of: entity, with: character)
        }
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? nil : text
    }
}

/// One page of the account's messages, newest first.
struct NotificationPage {
    var items: [AccountNotification]
    var unread: Int
    var page: Int
    var pages: Int

    init(_ j: JSON) {
        let list = j["notifications"].isNull ? j : j["notifications"]
        items = list.array.compactMap(AccountNotification.init)
        unread = j["total_unread"].int ?? items.filter { !$0.read }.count
        page = j["page"].int ?? 1
        pages = j["pages"].int ?? page
    }

    /// Marks one message read, or every one with "all", as the server will once told.
    mutating func markRead(_ id: String) {
        for i in items.indices where id == "all" || items[i].id == id { items[i].read = true }
        unread = items.filter { !$0.read }.count
    }
}
