package app.nama.core

import java.net.URI

/**
 * A message 30nama sends to the account: news, a title that arrived, an offer.
 *
 * The shape is read from what 30nama's own desktop app reads (`notification`, answered with
 * `{notifications, total_unread, page}`); no answer of the server's own has been seen yet, so
 * every field is optional and the date is taken in any of the forms the API uses elsewhere.
 */
data class AccountNotification(
    val id: String,
    val title: String?,
    /** The text, with the HTML it is sent with taken out. */
    val content: String?,
    val read: Boolean,
    val image: URI?,
    val kind: Kind,
    val link: URI?,
    val post: Post?,
    val date: DatePair,
) {
    @Suppress("EnumEntryName")
    enum class Kind { plain, link, post, pricing, stream }

    companion object {
        fun from(j: Json): AccountNotification? {
            val id = j["id"].text ?: return null
            val d = j["date"]
            return AccountNotification(
                id = id,
                title = j["title"].text,
                content = plainText(j["content"].text),
                read = j["read"].bool,
                image = j["image"].url ?: j["image"]["webp"].url ?: j["image"]["jpg"].url,
                kind = when (j["notification_type"].text) {
                    "link" -> Kind.link
                    "post" -> Kind.post
                    "pricing" -> Kind.pricing
                    "stream" -> Kind.stream
                    else -> Kind.plain
                },
                link = j["link"].url,
                post = if (j["post"].obj != null) Post.from(j["post"]) else null,
                date = if (d.obj != null) DatePair(d["g"].text, d["j"].text) else DatePair(d.text, null),
            )
        }

        private val lineBreak = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
        private val tag = Regex("<[^>]+>")

        fun plainText(html: String?): String? {
            var text = html ?: return null
            text = text.replace(lineBreak, "\n").replace(tag, "")
            for ((entity, character) in listOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&zwnj;" to "‌", "&amp;" to "&")) {
                text = text.replace(entity, character)
            }
            return text.trimWhitespaceAndNewlines().takeIf { it.isNotEmpty() }
        }
    }
}

/** One page of the account's messages, newest first. */
data class NotificationPage(val items: List<AccountNotification>, val unread: Int, val page: Int, val pages: Int) {
    /** The page with one message read, or every one with "all", as the server will once told. */
    fun markRead(id: String): NotificationPage {
        val items = items.map { if (id == "all" || it.id == id) it.copy(read = true) else it }
        return copy(items = items, unread = items.count { !it.read })
    }

    companion object {
        fun from(j: Json): NotificationPage {
            val list = if (j["notifications"].isNull) j else j["notifications"]
            val items = list.array.mapNotNull { AccountNotification.from(it) }
            val page = j["page"].int ?: 1
            return NotificationPage(items, j["total_unread"].int ?: items.count { !it.read }, page, j["pages"].int ?: page)
        }
    }
}
