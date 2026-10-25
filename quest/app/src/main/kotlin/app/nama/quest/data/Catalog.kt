package app.nama.quest.data

/**
 * What the scene needs from the catalogue, kept small and UI-shaped. The real source wraps
 * app.nama.core; the demo source lets the room, the panels and the player be checked on a headset
 * without an account.
 */
interface Catalog {
  val isDemo: Boolean

  /** The catalogue is served in the interface's language. */
  var lang: app.nama.core.Lang

  val signedIn: Boolean

  /** Finds a host and restores a saved session. Throws when 30nama can't be reached. */
  suspend fun prepare() {}

  suspend fun home(): List<Row>

  suspend fun details(tile: Tile): Details

  /** Null when the title has nothing playable for this account. */
  suspend fun stream(details: Details, episode: Episode?): Stream?

  suspend fun reportProgress(stream: Stream, seconds: Double, duration: Double) {}

  suspend fun loginCode(): LoginCode?

  /** True once the code has been confirmed on the other device. */
  suspend fun pollLogin(code: LoginCode): Boolean
}

data class Tile(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val rating: String? = null,
    val isSeries: Boolean = false,
    /** 0..1 for rows like continue watching. */
    val progress: Float? = null,
    /** A short line under the title, e.g. "S2 · E4 · 23 min left". */
    val note: String? = null,
    /** Colours for drawn artwork when there is no image (demo titles). */
    val art: Art? = null,
)

data class Art(val deep: Long, val light: Long)

data class Row(val key: String, val title: String, val tiles: List<Tile>, val landscape: Boolean = false)

data class Person(val name: String, val role: String, val photo: String? = null)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val still: String? = null,
    val minutes: Int? = null,
    val watched: Boolean = false,
)

data class Season(val number: Int, val episodes: List<Episode>)

data class Details(
    val tile: Tile,
    val overview: String? = null,
    val runtimeMinutes: Int? = null,
    val logo: String? = null,
    val director: String? = null,
    val people: List<Person> = emptyList(),
    val seasons: List<Season> = emptyList(),
    /** Episode or film to resume, when the server remembers one. */
    val resume: Episode? = null,
)

data class SubtitleTrack(val url: String, val language: String, val label: String)

data class Stream(
    val url: String,
    val title: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val startAt: Double = 0.0,
    val introStart: Double? = null,
    val introEnd: Double? = null,
    /** Opaque handle the real catalogue needs to report progress. */
    val ref: Any? = null,
)

data class LoginCode(val code: String, val address: String, val ref: Any? = null)
