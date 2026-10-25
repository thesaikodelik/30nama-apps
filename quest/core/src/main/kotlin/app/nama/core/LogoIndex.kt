package app.nama.core

import java.net.URI

/**
 * The TMDB logos of 30nama's titles, by IMDb id, as a daily workflow writes them to the
 * repository's `data` branch (Tools/logos/build_logos.py). For where TMDB is filtered: GitHub is
 * not, and the pictures themselves come through an image proxy that is not either.
 */
class LogoIndex private constructor(
    /** English (or with no text), then Persian; an empty name where there is none. */
    private val logos: Map<String, List<String>>,
) {
    val count: Int get() = logos.size

    /** The title's logo in the interface's language if there is one, else English, through the proxy. */
    fun logo(imdb: String?, lang: Lang): URI? {
        val names = logos[imdb ?: return null] ?: return null
        val english = names.firstOrNull() ?: ""
        val persian = names.getOrNull(1) ?: ""
        val name = if (lang == Lang.fa && persian.isNotEmpty()) persian else english
        return if (name.isEmpty()) null else proxied(name)
    }

    companion object {
        const val ADDRESS = "https://raw.githubusercontent.com/thesaikodelik/30nama-apps/data/logos.json"

        fun from(answer: Json): LogoIndex? {
            if (answer["version"].int != 1) return null
            return LogoIndex((answer["logos"].obj ?: emptyMap()).mapValues { (_, names) -> names.array.map { it.text ?: "" } })
        }

        /** A TMDB picture by its file name, through wsrv.nl, which fetches it from outside. */
        fun proxied(name: String): URI? = urlOf("https://wsrv.nl/?url=image.tmdb.org/t/p/w500/$name")
    }
}
