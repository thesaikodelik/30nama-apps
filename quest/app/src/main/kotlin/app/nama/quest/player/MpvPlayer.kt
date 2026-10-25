package app.nama.quest.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import app.nama.quest.data.Stream
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * mpv with defaults for streaming HLS and MP4 over https on the headset.
 *
 * Decoding starts on the hardware pair (MediaCodec straight into the Surface, zero copy) and
 * falls back to the software pair (FFmpeg decode, GL render) when MediaCodec cannot play the
 * file. With the hardware pair mpv cannot draw subtitles onto the Surface, so text subtitles are
 * published in [MpvState.subText] for a panel in front of the screen to show, in both modes.
 *
 * All calls are non-blocking and safe from the main thread.
 */
class MpvPlayer(private val config: Config = Config()) : VideoEngine, Mpv.Listener {

  data class Config(
      /** Start every file on MediaCodec; false forces the software pair. */
      val hardwareDecoding: Boolean = true,
      val userAgent: String? = null,
      /** Forward cache. HLS segments of a 4K movie run ~2-4 MB/s, so this is about a minute. */
      val demuxerMaxBytes: String = "192MiB",
      /** Kept behind the play head so short seeks back don't hit the network. */
      val demuxerMaxBackBytes: String = "64MiB",
      val cacheSecs: Int = 120,
      /** Seconds of cache needed before resuming after a stall; short so stalls stay short. */
      val cachePauseWait: Double = 2.0,
      /** The headset plays stereo; mpv's own downmix is better than leaving it to Android. */
      val audioChannels: String = "stereo",
      /** Faster but plainer scaling for the software pair. */
      val gpuDumbMode: Boolean = false,
      val logLevel: String = "warn",
      /** PEM bundle of trusted roots. mbedTLS can't read Android's store, so without one mpv skips verification. */
      val caFile: String? = null,
  )

  enum class DecodeMode {
    HARDWARE,
    SOFTWARE,
  }

  data class Track(
      val id: Int,
      /** "video", "audio" or "sub". */
      val type: String,
      val title: String?,
      val lang: String?,
      val codec: String?,
      val selected: Boolean,
      val default: Boolean,
      val forced: Boolean,
      val external: Boolean,
      val albumArt: Boolean,
      val channels: Int?,
  ) {
    /** Picture subtitles (Blu-ray PGS, DVD) cannot become sub-text. */
    val isBitmapSub: Boolean
      get() = type == "sub" && codec in BITMAP_SUB_CODECS
  }

  data class MpvState(
      val timePos: Double = 0.0,
      val duration: Double = 0.0,
      val paused: Boolean = true,
      val pausedForCache: Boolean = false,
      val cacheDuration: Double = 0.0,
      val videoWidth: Int = 0,
      val videoHeight: Int = 0,
      val videoAspect: Double = 0.0,
      val tracks: List<Track> = emptyList(),
      val subText: String = "",
      val hwdecCurrent: String = "",
      val eofReached: Boolean = false,
      val mode: DecodeMode = DecodeMode.HARDWARE,
      val loading: Boolean = false,
      val fileLoaded: Boolean = false,
      val error: String? = null,
  ) {
    val selectedSub: Track?
      get() = tracks.firstOrNull { it.type == "sub" && it.selected }

    val selectedAudio: Track?
      get() = tracks.firstOrNull { it.type == "audio" && it.selected }

    val hasVideo: Boolean
      get() = tracks.any { it.type == "video" && !it.albumArt }
  }

  private val mpv = Mpv(this)
  private val main = Handler(Looper.getMainLooper())

  private val _details = MutableStateFlow(MpvState())
  /** Everything mpv reports, for screens that need more than [state]. */
  val details: StateFlow<MpvState> = _details.asStateFlow()

  private val _state = MutableStateFlow(VideoState())
  override val state: StateFlow<VideoState> = _state.asStateFlow()

  private val stateLock = Any()
  @Volatile private var current: Stream? = null
  @Volatile private var fellBack = false
  @Volatile private var wantedSubLanguage: String? = null
  @Volatile private var subsAdded = false
  /**
   * Set when a new file is asked for and cleared by its StartFile. Until then, file events and
   * file properties still in flight belong to the previous file (a trailer replaced by the film)
   * and are dropped, so they can't add subtitles, trigger a fallback or show a stale frame size.
   */
  @Volatile private var awaitingStart = false
  @Volatile private var released = false

  init {
    applyDefaults()
    applyMode(if (config.hardwareDecoding) DecodeMode.HARDWARE else DecodeMode.SOFTWARE)
    val r = mpv.initialize(config.logLevel)
    if (r < 0) update { it.copy(error = "mpv: ${mpv.errorString(r)}") }
    observeAll()
  }

  private fun applyDefaults() {
    val o = mutableListOf(
        // a library, not a player: no config files, scripts or key bindings of its own
        "config" to "no",
        "input-default-bindings" to "no",
        "input-vo-keyboard" to "no",
        "idle" to "yes",
        // stay on the last frame at the end so the UI can seek back
        "keep-open" to "yes",
        "save-position-on-quit" to "no",

        "gpu-context" to "android",
        "gpu-api" to "opengl",
        "opengl-es" to "yes",
        "ao" to "audiotrack,aaudio,opensles",
        "audio-channels" to config.audioChannels,

        // streaming
        "cache" to "yes",
        "cache-secs" to config.cacheSecs.toString(),
        "demuxer-max-bytes" to config.demuxerMaxBytes,
        "demuxer-max-back-bytes" to config.demuxerMaxBackBytes,
        "demuxer-seekable-cache" to "yes",
        "cache-pause-wait" to fmt(config.cachePauseWait),
        "force-seekable" to "yes",
        "network-timeout" to "30",
        "stream-buffer-size" to "512KiB",
        "hls-bitrate" to "max",

        // decoding and output
        "hwdec-codecs" to "all",
        "framedrop" to "decoder",
        "vd-lavc-dr" to "yes",
        // Quest's compositor reads untagged buffers as sRGB, BT.709 primaries; HDR is tone-mapped
        "target-prim" to "bt.709",
        "target-trc" to "srgb",
        "gpu-dumb-mode" to if (config.gpuDumbMode) "yes" else "auto",

        // subtitles: the app draws text subs on its own panel (see class doc)
        "sub-auto" to "fuzzy",
        "sub-visibility" to "no",
    )
    config.userAgent?.let { o += "user-agent" to it }
    config.caFile?.let {
      o += "tls-ca-file" to it
      o += "tls-verify" to "yes"
    }
    for ((k, v) in o) {
      val r = mpv.setOption(k, v)
      if (r < 0) Log.w(TAG, "mpv option $k=$v: ${mpv.errorString(r)}")
    }
  }

  private fun observeAll() {
    for ((name, format) in OBSERVED) mpv.observeProperty(name, format)
  }

  // VideoEngine ----------------------------------------------------------------

  override fun attach(surface: Surface) = mpv.attachSurface(surface)

  /** Also passes the Surface's size, which the software pair renders at. */
  fun attach(surface: Surface, width: Int, height: Int) {
    mpv.attachSurface(surface)
    mpv.setSurfaceSize(width, height)
  }

  fun setSurfaceSize(width: Int, height: Int) = mpv.setSurfaceSize(width, height)

  override fun detach() = mpv.detachSurface()

  override fun setReadAhead(seconds: Int) {
    val secs = if (seconds > 0) seconds else config.cacheSecs
    mpv.setProperty("cache-secs", secs.toLong())
    mpv.setProperty("demuxer-readahead-secs", secs.toLong())
  }

  override fun load(stream: Stream) {
    current = stream
    fellBack = false
    applyMode(if (config.hardwareDecoding) DecodeMode.HARDWARE else DecodeMode.SOFTWARE)
    open(stream, stream.startAt)
  }

  override fun setPaused(paused: Boolean) {
    mpv.setProperty("pause", paused)
  }

  override fun seekBy(seconds: Double) {
    mpv.command("seek", fmt(seconds), "relative")
  }

  override fun seekTo(seconds: Double) = seekTo(seconds, exact = false)

  override fun seekExact(seconds: Double) = seekTo(seconds, exact = true)

  /** [exact] decodes up to the exact frame; slower, for precise jumps like "skip intro". */
  fun seekTo(seconds: Double, exact: Boolean) {
    mpv.command("seek", fmt(seconds), if (exact) "absolute+exact" else "absolute+keyframes")
  }

  /** Picks a subtitle by language: an external track of the stream first, then an embedded one. */
  override fun selectSubtitle(language: String?) {
    wantedSubLanguage = language
    applyWantedSubtitle(_details.value.tracks)
  }

  override fun stop() {
    current = null
    main.removeCallbacksAndMessages(null)
    mpv.command("stop")
    update { fresh(it) }
  }

  /** A state for a new file: what belongs to the file is reset, what belongs to mpv is kept. */
  private fun fresh(old: MpvState, loading: Boolean = false) =
      MpvState(mode = old.mode, paused = old.paused, hwdecCurrent = old.hwdecCurrent, loading = loading)

  override fun release() {
    if (released) return
    released = true
    main.removeCallbacksAndMessages(null)
    mpv.destroy()
  }

  // Extras beyond VideoEngine ------------------------------------------------

  fun togglePause() = mpv.command("cycle", "pause")

  /** A track id from [MpvState.tracks]; null turns audio off. */
  fun selectAudio(id: Int?) {
    mpv.setProperty("aid", id?.toString() ?: "no")
  }

  /** A track id from [MpvState.tracks]; null turns subtitles off. */
  fun selectSubtitleTrack(id: Int?) {
    wantedSubLanguage = null
    mpv.setProperty("sid", id?.toString() ?: "no")
  }

  fun addSubtitle(url: String, title: String? = null, language: String? = null, select: Boolean = true) {
    val args = mutableListOf("sub-add", url, if (select) "select" else "auto")
    if (title != null || language != null) args += title ?: ""
    if (language != null) args += language
    mpv.command(*args.toTypedArray())
  }

  fun setSubtitleDelay(seconds: Double) = mpv.setProperty("sub-delay", seconds)

  fun setAudioDelay(seconds: Double) = mpv.setProperty("audio-delay", seconds)

  fun setVolume(percent: Int) = mpv.setProperty("volume", percent.toLong())

  override fun setMuted(muted: Boolean) {
    mpv.setProperty("mute", muted)
  }

  fun setSpeed(speed: Double) = mpv.setProperty("speed", speed)

  /** Raw access for anything else; commands and writes are async like everything here. */
  val engine: Mpv
    get() = mpv

  // Loading and fallback -----------------------------------------------------

  private fun open(stream: Stream, startAt: Double) {
    subsAdded = false
    awaitingStart = true
    // Retries and checks queued for the previous file must not act on this one.
    main.removeCallbacksAndMessages(null)
    update { fresh(it, loading = true) }

    // Headers go one by one: a plain string list would split on the commas inside values.
    mpv.command("change-list", "http-header-fields", "clr", "")
    var userAgent = config.userAgent
    for ((k, v) in stream.headers) {
      if (k.equals("User-Agent", ignoreCase = true)) userAgent = v
      else mpv.command("change-list", "http-header-fields", "append", "$k: $v")
    }
    userAgent?.let { mpv.setProperty("user-agent", it) }

    // A trailer behind the title page is quiet and plays round; a film is neither.
    mpv.setProperty("mute", stream.preview)
    mpv.setProperty("loop-file", if (stream.preview) "inf" else "no")

    val args = mutableListOf("loadfile", stream.url, "replace")
    if (startAt > 0) {
      args += "-1" // playlist index, unused with "replace"
      args += "start=${fmt(startAt)}"
    }
    mpv.command(*args.toTypedArray()) { r ->
      if (r < 0) update { it.copy(loading = false, error = "loadfile: ${mpv.errorString(r)}") }
    }
  }

  private fun applyMode(mode: DecodeMode) {
    if (mode == DecodeMode.HARDWARE) {
      mpv.setOption("hwdec", "mediacodec")
      mpv.setVideoOutput("mediacodec_embed")
    } else {
      mpv.setOption("hwdec", "no")
      mpv.setVideoOutput("gpu")
    }
    update { it.copy(mode = mode) }
  }

  /** MediaCodec failed for this file: reopen it on the software pair where it was. */
  private fun fallBackToSoftware(why: String) {
    val stream = current ?: return
    if (fellBack || _details.value.mode != DecodeMode.HARDWARE) return
    fellBack = true
    val at = _details.value.timePos.takeIf { it > 0 } ?: stream.startAt
    Log.w(TAG, "MediaCodec failed ($why); software decoding from ${fmt(at)}s")
    applyMode(DecodeMode.SOFTWARE)
    open(stream, at)
  }

  private fun checkHardwareDecoding() {
    val d = _details.value
    if (d.mode != DecodeMode.HARDWARE || !d.fileLoaded || !d.hasVideo) return
    if (!d.hwdecCurrent.startsWith("mediacodec")) fallBackToSoftware("hwdec-current=${d.hwdecCurrent.ifEmpty { "none" }}")
  }

  private fun applyWantedSubtitle(tracks: List<Track>) {
    val lang = wantedSubLanguage
    val subs = tracks.filter { it.type == "sub" }
    if (lang == null) {
      if (subs.any { it.selected }) mpv.setProperty("sid", "no")
      return
    }
    val pick =
        subs.firstOrNull { it.external && it.lang.equals(lang, ignoreCase = true) }
            ?: subs.firstOrNull { it.lang.equals(lang, ignoreCase = true) }
            ?: return
    if (!pick.selected) mpv.setProperty("sid", pick.id.toLong())
  }

  /** The software pair can draw picture subtitles itself; text ones stay with the app's panel. */
  private fun updateSubVisibility(d: MpvState) {
    val visible = d.mode == DecodeMode.SOFTWARE && d.selectedSub?.isBitmapSub == true
    mpv.setProperty("sub-visibility", visible)
  }

  // Mpv.Listener (event thread) ---------------------------------------------

  override fun onEvent(event: Mpv.Event) {
    if (event is Mpv.Event.StartFile) awaitingStart = false
    else if (awaitingStart && (event == Mpv.Event.FileLoaded || event == Mpv.Event.PlaybackRestart || event is Mpv.Event.EndFile)) return
    when (event) {
      is Mpv.Event.StartFile -> update { it.copy(loading = true, eofReached = false, error = null) }
      Mpv.Event.FileLoaded -> {
        update { it.copy(loading = false, fileLoaded = true) }
        // external subtitles of the stream; added here because sub-add needs a loaded file
        val stream = current
        if (stream != null && !subsAdded) {
          subsAdded = true
          for (s in stream.subtitles) mpv.command("sub-add", s.url, "auto", s.label, s.language)
        }
      }
      Mpv.Event.PlaybackRestart -> {
        update { it.copy(loading = false) }
        // hwdec-current may land just after this event; look again shortly
        main.postDelayed(::checkHardwareDecoding, 1500)
      }
      is Mpv.Event.EndFile -> {
        if (event.reason == Mpv.EndReason.ERROR) {
          val msg = mpv.errorString(event.error)
          if (_details.value.mode == DecodeMode.HARDWARE && !fellBack && current != null) {
            main.post { fallBackToSoftware("end-file: $msg") }
          } else {
            update { it.copy(loading = false, fileLoaded = false, error = msg) }
          }
        } else if (event.reason == Mpv.EndReason.STOP || event.reason == Mpv.EndReason.QUIT) {
          update { it.copy(fileLoaded = false) }
        }
      }
      else -> {}
    }
  }

  override fun onPropertyChange(name: String, value: Any?) {
    if (awaitingStart && name in FILE_PROPERTIES) return
    when (name) {
      "time-pos" -> {
        val t = value as? Double ?: 0.0
        val last = _details.value.timePos
        // per-frame updates would recompose the panels 60 times a second
        if (abs(t - last) >= 0.25 || t < last) update { it.copy(timePos = t) }
      }
      "duration" -> update { it.copy(duration = value as? Double ?: 0.0) }
      "pause" -> update { it.copy(paused = value as? Boolean ?: false) }
      "paused-for-cache" -> update { it.copy(pausedForCache = value as? Boolean ?: false) }
      "demuxer-cache-duration" -> update { it.copy(cacheDuration = value as? Double ?: 0.0) }
      "video-params/w" -> update { it.copy(videoWidth = (value as? Long ?: 0L).toInt()) }
      "video-params/h" -> update { it.copy(videoHeight = (value as? Long ?: 0L).toInt()) }
      "video-params/aspect" -> update { it.copy(videoAspect = value as? Double ?: 0.0) }
      "sub-text" -> update { it.copy(subText = value as? String ?: "") }
      "eof-reached" -> update { it.copy(eofReached = value as? Boolean ?: false) }
      "hwdec-current" -> {
        val hw = value as? String ?: ""
        val was = _details.value.hwdecCurrent
        update { it.copy(hwdecCurrent = hw) }
        // decoding started on MediaCodec and later dropped to software mid-file
        if (was.startsWith("mediacodec") && !hw.startsWith("mediacodec")) main.post { checkHardwareDecoding() }
      }
      "track-list" -> {
        val tracks = parseTracks(value as? String)
        val d = update { it.copy(tracks = tracks) }
        applyWantedSubtitle(tracks)
        updateSubVisibility(d)
      }
    }
  }

  override fun onLogMessage(prefix: String, level: Int, text: String) {
    val line = "[$prefix] ${text.trimEnd()}"
    when {
      level <= 20 -> Log.e(TAG, line)
      level <= 30 -> Log.w(TAG, line)
      else -> Log.i(TAG, line)
    }
    // the hardware pair failing to set up the video chain shows only in the log
    if (level <= 20 && _details.value.mode == DecodeMode.HARDWARE &&
        (text.contains("video chain", ignoreCase = true) || text.contains("mediacodec", ignoreCase = true))) {
      main.post { fallBackToSoftware(text.trim()) }
    }
  }

  // State --------------------------------------------------------------------

  private fun update(f: (MpvState) -> MpvState): MpvState =
      synchronized(stateLock) {
        val d = f(_details.value)
        _details.value = d
        _state.value =
            VideoState(
                position = d.timePos,
                duration = d.duration,
                paused = d.paused,
                buffering = d.pausedForCache || d.loading,
                subtitle = d.subText,
                subtitleLanguage = d.selectedSub?.lang,
                videoWidth = d.videoWidth,
                videoHeight = d.videoHeight,
                ended = d.eofReached,
                error = d.error,
            )
        d
      }

  private companion object {
    const val TAG = "Nama"

    val BITMAP_SUB_CODECS = setOf("hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle", "xsub", "pgssub", "vobsub")

    /** Properties that describe the current file rather than the player. */
    val FILE_PROPERTIES =
        setOf(
            "time-pos", "duration", "demuxer-cache-duration", "video-params/w", "video-params/h",
            "video-params/aspect", "sub-text", "eof-reached", "track-list", "paused-for-cache",
        )

    val OBSERVED =
        listOf(
            "time-pos" to Mpv.Format.DOUBLE,
            "duration" to Mpv.Format.DOUBLE,
            "pause" to Mpv.Format.FLAG,
            "paused-for-cache" to Mpv.Format.FLAG,
            "demuxer-cache-duration" to Mpv.Format.DOUBLE,
            "video-params/w" to Mpv.Format.INT64,
            "video-params/h" to Mpv.Format.INT64,
            "video-params/aspect" to Mpv.Format.DOUBLE,
            "track-list" to Mpv.Format.NODE,
            "sub-text" to Mpv.Format.STRING,
            "hwdec-current" to Mpv.Format.STRING,
            "eof-reached" to Mpv.Format.FLAG,
        )

    /** mpv parses numbers with a dot whatever the device locale is. */
    fun fmt(v: Double): String = String.format(Locale.ROOT, "%.3f", v)

    fun parseTracks(json: String?): List<Track> {
      if (json.isNullOrEmpty()) return emptyList()
      return try {
        val a = JSONArray(json)
        List(a.length()) { i ->
          val t = a.getJSONObject(i)
          Track(
              id = t.optInt("id"),
              type = t.optString("type"),
              title = t.optString("title").ifEmpty { null },
              lang = t.optString("lang").ifEmpty { null },
              codec = t.optString("codec").ifEmpty { null },
              selected = t.optBoolean("selected"),
              default = t.optBoolean("default"),
              forced = t.optBoolean("forced"),
              external = t.optBoolean("external"),
              albumArt = t.optBoolean("albumart"),
              channels = if (t.has("demux-channel-count")) t.optInt("demux-channel-count") else null,
          )
        }
      } catch (e: org.json.JSONException) {
        Log.w(TAG, "track-list: ${e.message}")
        emptyList()
      }
    }
  }
}
