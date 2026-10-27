package app.nama.core

import java.time.Instant

/**
 * Interface strings. The language is an in-app setting (the catalogue itself is served in the
 * chosen language), not the system's, and English until the user picks another. The keys (`S`)
 * and the tables are generated from the Swift app's L10n.swift by kotlin-core/tools/gen-l10n.py.
 */
data class Loc(val lang: Lang) {
    operator fun invoke(key: S): String = when (lang) {
        Lang.fa -> LocTables.fa[key]
        Lang.ar -> LocTables.ar[key]
        Lang.en -> LocTables.en[key]
    } ?: LocTables.en[key] ?: key.name

    /** Digits in the interface language. */
    fun num(s: String): String = if (lang == Lang.fa) Fmt.persianDigits(s) else s
    fun num(n: Int): String = num(n.toString())

    /**
     * Text held to the interface's direction wherever it is drawn, so mixed words and figures
     * keep their order in a place laid out left to right.
     */
    fun isolated(text: String): String = (if (lang.isRTL) "⁧" else "⁦") + text + "⁩"

    /** A job as TMDB names it, in the interface's language. */
    fun job(name: String): String = when (name) {
        "Director" -> this(S.director)
        "Creator" -> this(S.creator)
        "Screenplay", "Writer" -> this(S.writer)
        "Novel" -> this(S.jobNovel)
        "Original Music Composer" -> this(S.jobComposer)
        "Director of Photography" -> this(S.jobPhotography)
        else -> name
    }

    /** A Home row's title by its key (`HomeSection.key`, plus the app's own "continue" and "for_you"). */
    fun title(forSection: String): String = when (forSection) {
        "continue" -> this(S.continueWatching)
        "for_you" -> this(S.forYou)
        "new_releases" -> this(S.newReleases)
        "suggested" -> this(S.suggested)
        "top_10" -> this(S.top10)
        "movies" -> this(S.movies)
        "series" -> this(S.series)
        "anime" -> this(S.anime)
        else -> forSection
    }

    fun kind(kind: Post.Kind): String = when (kind) {
        Post.Kind.movie -> this(S.movie)
        Post.Kind.series -> this(S.series)
        Post.Kind.anime -> this(S.anime)
    }

    /** "Series · 4 seasons" */
    fun kindLine(post: Post): String {
        var s = kind(post.kind)
        val n = post.seasons
        if (post.isSeries && n != null && n > 0) {
            s += " · " + if (lang == Lang.en) "$n ${if (n == 1) "season" else this(S.seasonsCount)}" else "${num(n)} ${this(S.seasonsCount)}"
        }
        return s
    }

    /** The account's plan as 30nama names it: "golden" -> "طلایی". */
    fun accountType(raw: String): String {
        val fa = mapOf("golden" to "طلایی", "gold" to "طلایی", "silver" to "نقره‌ای", "bronze" to "برنزی", "red" to "قرمز", "free" to "رایگان")
        val ar = mapOf("golden" to "ذهبي", "gold" to "ذهبي", "silver" to "فضي", "bronze" to "برونزي", "red" to "أحمر", "free" to "مجاني")
        return when (lang) {
            Lang.fa -> fa[raw.lowercase()] ?: raw
            Lang.ar -> ar[raw.lowercase()] ?: raw
            Lang.en -> raw.take(1).uppercase() + raw.drop(1)
        }
    }

    /** "Subscription until 2028-07-15 · 641 days left", in the calendar of the interface language. */
    fun subscriptionLine(user: User, now: Instant = Instant.now()): String? {
        val day = (if (lang == Lang.fa) user.expiryLocal?.replace("-", "/") else null) ?: user.expiry ?: return null
        var line = "${this(S.subscriptionUntil)} ${num(day)}"
        user.daysLeft(now)?.let { line += " · ${num(it)} ${this(S.daysLeft)}" }
        return line
    }

    /** "S2 · E5" / "فصل ۲ · قسمت ۵" */
    fun episodeTag(season: Int, episode: String): String = when (lang) {
        Lang.fa -> "فصل ${num(season)} · قسمت ${num(episode)}"
        Lang.ar -> "الموسم $season · الحلقة $episode"
        Lang.en -> "S$season · E$episode"
    }

    companion object {
        /** The tables themselves, for the test that every string is translated. */
        val en: Map<S, String> get() = LocTables.en
        val fa: Map<S, String> get() = LocTables.fa
        val ar: Map<S, String> get() = LocTables.ar
    }
}
