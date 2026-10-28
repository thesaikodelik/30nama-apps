package app.nama.core

// The actions the apps call, with the parameters the Apple TV and Mac apps send them. The Swift
// side calls `api.get("name", [...])` from its screens; here they are named once, so the Kotlin
// apps cannot send a parameter differently. Each is a thin layer over `call` and `get`.

/** The block's value, or nil when it threw; cancellation is let through, unlike `runCatching`. */
@PublishedApi
internal inline fun <T> attempt(block: () -> T): T? = try {
    block()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** A sign-in code to show, and the page that confirms it (for a QR code). */
data class LoginCode(val code: String, val url: String)

/** How the sign-in screen polls: every five seconds, for a minute, then a new code is offered. */
object QRLogin {
    const val POLL_SECONDS: Double = 5.0
    const val LIFETIME_SECONDS: Double = 60.0
}

/** A fresh sign-in code. Throws when the server could not be asked or gave none. */
suspend fun APIClient.qrLoginCode(): LoginCode {
    val result = get("qrlogin_code")
    val code = result["code"].text ?: throw APIError.Server("no code")
    return LoginCode(code, result["url"].text ?: "https://30nama.com/auth/$code")
}

/**
 * Whether the code has been confirmed on the phone: the session token when it has, nil while it
 * has not (unused codes answer `success: false`) or the server could not be asked. Never retried.
 */
suspend fun APIClient.qrLogin(code: String): String? {
    val e = attempt { call("qrlogin_login", mapOf("auth_code" to code)) } ?: return null
    return if (e.success) User.from(e.result)?.token else null
}

/**
 * Completes a sign-in, or refreshes a saved one: sets the token and reads the profile. The login
 * call returns only a token; when the profile does not come, a user with just the token is returned.
 */
suspend fun APIClient.signIn(token: String): User {
    setToken(token)
    return profile(token) ?: User(token)
}

/** The account's profile, or nil when it could not be read. */
suspend fun APIClient.profile(token: String): User? {
    val e = attempt { call("user") } ?: return null
    return if (e.success) User.from(e.result, fallbackToken = token) else null
}

/** Signs out on the server, then forgets the token here. */
suspend fun APIClient.signOut() {
    attempt { call("logout") }
    setToken(null)
}

suspend fun APIClient.home(): HomeFeed = HomeFeed.from(get("home_stream"))

/** A title's whole page: cast, crew, seasons, what changed, where the user left off. */
suspend fun APIClient.single(postID: Int, background: Boolean = false): Post? =
    Post.from(get("single", mapOf("post_id" to postID.toString()), background))

/** What `stream` said: the files to play, or why there are none (a refusal for a guest or a lapsed subscription). */
data class StreamAnswer(val result: StreamResult?, val refusal: String?)

/** The playable files of a title (one for a film, every episode for a series) with their qualities and subtitles. */
suspend fun APIClient.stream(postID: Int, signedIn: Boolean): StreamAnswer {
    val e = call("stream", mapOf("post_id" to postID.toString(), "free_stream" to if (signedIn) "0" else "1"))
    return if (e.success) StreamAnswer(StreamResult.from(e.result), null) else StreamAnswer(null, e.message)
}

suspend fun APIClient.downloads(postID: Int, signedIn: Boolean): DownloadOptions? {
    val e = call("download", mapOf("post_id" to postID.toString(), "free_download" to if (signedIn) "0" else "1"))
    return if (e.success) DownloadOptions.from(e.result) else null
}

/**
 * `streamonly` as the server reads it: only the word "true" filters. "1" was sent for years and
 * was ignored, so "streaming only" showed everything (tried on the server, 6 Aban 1405).
 */
fun streamOnly(on: Boolean): String = if (on) "true" else "false"

suspend fun APIClient.search(query: String, streamOnly: Boolean = false, page: Int = 1, background: Boolean = false): PostPage =
    PostPage.from(get("search", mapOf("query" to query, "streamonly" to streamOnly(streamOnly), "page" to page.toString()), background))

/**
 * A category or genre (`MenuEntry.slug`), a page at a time.
 * @param orderBy "update", "year", "imdb-rate", "favorite"…, as the catalogue screens sort.
 */
suspend fun APIClient.archive(slug: String, orderBy: String, page: Int = 1, streamOnly: Boolean = true, background: Boolean = false): PostPage =
    PostPage.from(get("archive", mapOf(
        "slugs" to slug, "streamonly" to streamOnly(streamOnly), "page" to page.toString(),
        "orderby" to orderBy, "order" to "desc",
    ), background))

suspend fun APIClient.menu(): Menu = Menu.from(get("menu"))

suspend fun APIClient.filterOptions(): FilterOptions = FilterOptions.from(get("advanced_search_parameters"))

suspend fun APIClient.searchChoices(): SearchChoices = SearchChoices.from(get("advanced_search_parameters"))

suspend fun APIClient.advancedSearch(query: AdvancedQuery, page: Int = 1): PostPage = PostPage.from(get("advanced_search", query.request(page)))

/** A catalogue narrowed by `filter`, inside one kind ("movie", "series", "anime"). */
suspend fun APIClient.filtered(filter: Filter, type: String?, orderBy: String, page: Int = 1): PostPage =
    PostPage.from(get("advanced_search", mapOf("parameters" to filter.parameters(type), "orderby" to orderBy, "order" to "DESC", "page" to page.toString())))

/** The account's "continue watching", each title with where it was left. Run it through `ContinueDismissals` before showing it. */
suspend fun APIClient.continueWatching(background: Boolean = false): List<Post> = Post.continueList(get("continue_watching", background = background))

/** Takes a title off "continue watching" on the server (and so on every device). */
suspend fun APIClient.dismissContinue(postID: Int): Boolean =
    attempt { call("continue_watching_dismiss", mapOf("id" to postID.toString()), background = true).success } ?: false

/** One of the user's lists ("watchlist", "favorite", "notify"), newest addition first. */
suspend fun APIClient.userList(group: String, page: Int = 1, background: Boolean = false): PostPage =
    PostPage.from(get("user_list", mapOf("action" to "list", "group" to group, "page" to page.toString(), "orderby" to "add_list", "order" to "DESC"), background))

/** Which titles are on each of the user's lists, by list name. */
suspend fun APIClient.myLists(): Map<String, Set<Int>> =
    (get("user_mylist").obj ?: emptyMap()).mapValues { (_, v) -> v["post"].array.mapNotNull { it.int }.toSet() }

/** Puts a title on a list, or takes it off: the server has one action for both. True when it did. */
suspend fun APIClient.toggleList(postID: Int, group: String): Boolean =
    attempt { call("user_list", mapOf("action" to "add", "group" to group, "post_id" to postID.toString())).success } ?: false

suspend fun APIClient.notifications(page: Int = 1): NotificationPage = NotificationPage.from(get("notification", mapOf("page" to page.toString()), background = true))

/** Marks one message read, or every one with "all". */
suspend fun APIClient.markNotificationRead(id: String) {
    attempt { call("notification_read", mapOf("notification_id" to id), background = true) }
}

/**
 * Where playback is, which drives "continue watching" on every device. The position goes up in
 * steps of thirty seconds, which is how the 30nama website reports it: the server accepts any other
 * position just the same and then does not record it.
 */
data class WatchProgress(val post: Post, val item: StreamItem, val userID: String?, val seconds: Double, val duration: Double) {
    /** The position to report, or nil when there is nothing worth reporting yet. */
    val reportedTime: Int? get() = if (seconds.isFinite()) (seconds.toInt() / 30 * 30).takeIf { it > 0 } else null

    fun params(): Map<String, String>? {
        val time = reportedTime ?: return null
        return mapOf(
            "post_id" to (item.postID ?: post.id.toString()),
            "file_id" to item.fileID,
            "user_id" to (userID ?: ""),
            "time" to time.toString(),
            "duration" to (if (duration.isFinite()) duration.toInt() else 0).toString(),
            "token" to item.token,
        )
    }
}

/**
 * Reports `progress` (`observer`), never retried. Call it as playback goes and send only when
 * `reportedTime` has changed since the last report. Only for a signed-in user.
 */
suspend fun APIClient.reportProgress(progress: WatchProgress): Boolean {
    val params = progress.params() ?: return false
    return attempt { call("observer", params).success } ?: false
}
