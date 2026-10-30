package app.nama.androidtv.player

import app.nama.core.downloads
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import app.nama.androidtv.AppModel
import app.nama.androidtv.PlaybackRequest
import app.nama.androidtv.attempt
import app.nama.androidtv.data.Gemini
import app.nama.androidtv.data.Resume
import app.nama.androidtv.data.Translation
import app.nama.androidtv.ui.TV
import app.nama.core.AudioMix
import app.nama.core.Episode
import app.nama.core.Lang
import app.nama.core.Post
import app.nama.core.Quality
import app.nama.core.S
import app.nama.core.StreamItem
import app.nama.core.SubSource
import app.nama.core.SubtitleTrack
import app.nama.core.WatchProgress
import app.nama.core.reportProgress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/** When playback stops by itself, for falling asleep to. */
sealed interface Sleep {
  data object Off : Sleep
  data object EndOfEpisode : Sleep
  data class Minutes(val n: Int) : Sleep
}

/** What the player offers to do right now, as the pill over the picture. */
data class Offer(val kind: Kind, val countdown: Float? = null) {
  enum class Kind { Intro, Recap, Next, StartOver }
}

/**
 * One film or series playing (the Apple TV's PlayerEngine), on Media3. 30nama's streams are plain
 * HLS. Subtitles are separate files, drawn by the app itself rather than by Media3, so that their
 * look, their timing nudge and the Persian fixes in core are the same as in the other apps.
 */
@OptIn(UnstableApi::class)
class PlayerEngine(private val context: Context, private val app: AppModel, request: PlaybackRequest, http: OkHttpClient, private val pickedByUser: Boolean) {
  val post: Post = request.post
  val items: List<StreamItem> = request.items
  private val serverResume = request.resume
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)
  private val loc get() = app.loc

  // MARK: State the screen reads

  var index by mutableIntStateOf(request.index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    private set
  val current: StreamItem get() = items[index]
  var position by mutableDoubleStateOf(0.0)
    private set
  var duration by mutableDoubleStateOf(0.0)
    private set
  var buffered by mutableDoubleStateOf(0.0)
    private set
  var playing by mutableStateOf(false)
    private set
  var buffering by mutableStateOf(true)
    private set

  /** The download files, asked for once when "Max" is chosen; null until they have come. */
  private var downloads: app.nama.core.DownloadOptions? = null
  private var downloadsAsked = false
  /** Files that would not play, or played without sound: never tried again in this player. */
  private val directFailed = HashSet<String>()
  /** The download file playing, when "Max" plays one. */
  var direct by mutableStateOf<app.nama.core.DownloadFile?>(null)
    private set

  /** Whether the film's first picture has been shown; until then the player shows its curtain. */
  var pictured by mutableStateOf(false)
    private set
  var quality by mutableStateOf<Quality?>(null)
    private set
  /** Which of the server's mixes is open. Stereo is what a TV's own speakers can play. */
  var mix by mutableStateOf(app.settings.audioMix)
    private set
  /** "off", "fa", "en", [FOUND] and a SubSource candidate's id, or [TRANSLATED]. */
  var subtitleLanguage by mutableStateOf(app.settings.defaultSubtitle)
    private set
  /** Seconds the subtitles are shown later (or, negative, earlier) than their file says. */
  var subtitleOffset by mutableDoubleStateOf(0.0)
    private set
  var cue by mutableStateOf("")
    private set
  /** Persian subtitles found on SubSource for an item 30nama gives none for, best first. */
  var found by mutableStateOf<List<SubSource.Candidate>>(emptyList())
    private set
  /** SubSource has been asked about this item (and [found] is its answer). */
  private var searched by mutableStateOf(false)
  /** How much of the translation has come, 0 to 1, while there is one. */
  var translated by mutableStateOf<Float?>(null)
    private set

  /**
   * A translation can be offered: no Persian subtitle anywhere, an English one to start from, the
   * viewer's own Gemini key, and not on the Iranian server, where Google is out of reach.
   */
  val canTranslate: Boolean
    get() = current.subtitles["fa"] == null && searched && found.isEmpty() && current.subtitles["en"] != null &&
        app.key("gemini") != null && !app.inIran
  var sleep by mutableStateOf<Sleep>(Sleep.Off)
    private set
  var speed by mutableFloatStateOf(1f)
    private set
  var offer by mutableStateOf<Offer?>(null)
    private set

  /** Back during the countdown to the next episode: no countdown, and the end goes back to the page. */
  private var nextCancelled = false

  /** Stops the countdown to the next episode (Back, as the aliir74 app has it). */
  fun cancelNext() {
    nextCancelled = true
    if (offer?.kind == Offer.Kind.Next) offer = null
  }
  /** Width over height of the picture, as it should be shown. */
  var aspect by mutableStateOf<Float?>(null)
    private set
  /** The audio languages of the stream, when there is more than one, and the one playing. */
  var audioTracks by mutableStateOf<List<String>>(emptyList())
    private set
  var audioTrack by mutableStateOf<String?>(null)
    private set
  /** The film's frame rate, once the stream says it. */
  var frameRate by mutableFloatStateOf(0f)
    private set

  // MARK: The player

  /**
   * The CDN signs every segment's address for the address that asked for the stream. Behind a VPN
   * or a carrier with several exits, some connections then get 403 for good. The player has its
   * own pool of plain HTTP/1.1 connections, so that one bad connection does not carry every segment,
   * and on a 403 the pool is emptied and the request tried again on a new one. Learnt from 30namaTV.
   */
  private val cdn = http.newBuilder().protocols(listOf(Protocol.HTTP_1_1)).connectionPool(ConnectionPool()).readTimeout(20, TimeUnit.SECONDS).build()

  private val retries = object : DefaultLoadErrorHandlingPolicy(MIN_RETRIES) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
      val e = info.exception
      if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode == 403 && info.errorCount <= MIN_RETRIES) {
        cdn.connectionPool.evictAll()
        return 200
      }
      return super.getRetryDelayMsFor(info)
    }

    // Never to another quality on a failure: the user chose this one.
    override fun getFallbackSelectionFor(options: LoadErrorHandlingPolicy.FallbackOptions, info: LoadErrorHandlingPolicy.LoadErrorInfo): LoadErrorHandlingPolicy.FallbackSelection? = null
  }

  private val bandwidth = DefaultBandwidthMeter.Builder(context)
      // The last speed this TV measured, else 6 Mb/s: Media3's own guess of 1 Mb/s held a fast line
      // at 480p for half a minute.
      .setInitialBitrateEstimate(prefs.getLong("bitrate", 6_000_000L))
      .build()

  /** The player always comes first; fetching ahead onto the disk waits while it loads. */
  private val priority = androidx.media3.common.PriorityTaskManager()
  private val network = OkHttpDataSource.Factory(cdn).setUserAgent(USER_AGENT)
  private val disk = Disk.cache(context)
  private val readAhead = disk?.let { ReadAhead(it, network, priority) }
  /** Frames for the seek bar. */
  val preview = SeekPreview(context, network)

  val player: ExoPlayer = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
      .setMediaSourceFactory(
          DefaultMediaSourceFactory(
              disk?.let {
                Routing(network, androidx.media3.datasource.cache.CacheDataSource.Factory().setCache(it).setUpstreamDataSourceFactory(network).setCacheKeyFactory { s -> Disk.key(s.uri.toString()) })
              } ?: network)
              .setLoadErrorHandlingPolicy(retries))
      .setPriorityTaskManager(priority)
      .setLoadControl(
          DefaultLoadControl.Builder()
              .setBufferDurationsMs(50_000, 120_000, 2_500, 5_000)
              // A third of the heap and no more: a paused film and a subtitle search once ran a 2 GB TV out of memory.
              .setTargetBufferBytes(minOf(Runtime.getRuntime().maxMemory() / 3, 96L * 1024 * 1024).toInt())
              .build())
      .setBandwidthMeter(bandwidth)
      .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
      .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
      .setSeekBackIncrementMs(10_000)
      .setSeekForwardIncrementMs(10_000)
      .build().also { p ->
        // The adaptive stream no taller than this screen shows and its decoder reads (DeviceCeiling).
        DeviceCeiling.lines(context).takeIf { it > 0 }?.let { lines ->
          // By height alone: a 2048x858 scope picture is well within a 1080p screen.
          p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, lines + 16).build()
        }
      }

  /**
   * The system's view of playback, for the remote's media keys, HDMI-CEC and the Assistant: next
   * and previous are episodes, and stop is a pause.
   */
  private val session: MediaSession? = runCatching {
    MediaSession.Builder(context, object : ForwardingPlayer(player) {
      override fun getAvailableCommands(): Player.Commands =
          super.getAvailableCommands().buildUpon().add(COMMAND_SEEK_TO_NEXT).add(COMMAND_SEEK_TO_PREVIOUS).build()

      override fun isCommandAvailable(command: Int): Boolean = command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_PREVIOUS || super.isCommandAvailable(command)

      override fun seekToNext() {
        next()
      }

      override fun seekToPrevious() {
        previous()
      }

      override fun stop() {
        player.pause()
      }
    }).setId("nama-${System.nanoTime()}").build()
  }.onFailure { Log.w(AppModel.TAG, "media session: $it") }.getOrNull()

  // MARK: Bookkeeping

  private var track = SubtitleTrack()
  private val subtitleCache = HashMap<String, SubtitleTrack>()
  private var subtitleJob: Job? = null
  private var findJob: Job? = null
  private var sleepJob: Job? = null
  /** The last position reported for the current item, so the same one is not sent twice. */
  private var reportedTime = 0
  /** The stretch last jumped over by itself, so that one the user goes back into is left alone. */
  private var skippedOn: String? = null
  /** Episodes that started by themselves with no key pressed in between. */
  private var unattended = 0
  /** Where the current file was resumed, for "Start over" in its first ten seconds. */
  private var resumedAt: Double? = null
  private var startedAt = 0L
  private var closed = false
  private var onClose: () -> Unit = {}

  init {
    // A series keeps its subtitle timing and its speed from one episode, and one evening, to the
    // next; a film its timing.
    restoreOffset()
    if (post.isSeries) speed = prefs.getFloat("series.${post.id}.speed", 1f)
    player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
      override fun onVideoDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        preview.filmDecoder(decoderName)
      }
    })
    player.addListener(object : Player.Listener {
      override fun onRenderedFirstFrame() {
        pictured = true
      }

      override fun onPlaybackStateChanged(state: Int) {
        buffering = state == Player.STATE_BUFFERING
        if (state == Player.STATE_ENDED) finished()
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        playing = isPlaying
        if (!isPlaying) report()
      }

      override fun onPlayerError(error: PlaybackException) {
        Log.w(AppModel.TAG, "playback: ${error.errorCodeName} ${error.cause}")
        if (closed) return
        if (direct != null) return leaveDirect("would not play (${error.errorCodeName})")
        app.showToast(loc(S.playError))
        close()
      }

      override fun onVideoSizeChanged(size: VideoSize) {
        val f = player.videoFormat
        val decoder = if (size.width > 0) size.width * size.pixelWidthHeightRatio to size.height.toFloat() else null
        val stream = f?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width * it.pixelWidthHeightRatio to it.height.toFloat() }
        aspect = filmShape(decoder, stream)
        f?.frameRate?.takeIf { it > 0 }?.let { frameRate = it }
      }

      override fun onTracksChanged(tracks: Tracks) {
        val langs = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.mapNotNull { g -> g.mediaTrackGroup.getFormat(0).language }.distinct()
        audioTracks = if (langs.size > 1) langs else emptyList()
        audioTrack = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.mediaTrackGroup?.getFormat(0)?.language
        // A file whose sound this TV cannot play would go on silent: the stream instead.
        if (direct != null && !tracks.isEmpty && tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO } && tracks.groups.none { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }) {
          val sounds = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.joinToString { it.mediaTrackGroup.getFormat(0).sampleMimeType ?: "?" }
          leaveDirect("has no sound this TV plays ($sounds)")
        }
        // Nor a picture: Media3 leaves a video track it has no decoder for unselected and plays the
        // sound over black (Dolby Vision profile 5, say), with no error to fall back on.
        if (direct != null && !tracks.isEmpty && tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO } && tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }) {
          val pictures = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.joinToString { it.mediaTrackGroup.getFormat(0).codecs ?: it.mediaTrackGroup.getFormat(0).sampleMimeType ?: "?" }
          leaveDirect("has no picture this TV plays ($pictures)")
        }
      }
    })
  }

  /** Starts playback; [onClose] is called once, however it ends. */
  fun begin(onClose: () -> Unit) {
    this.onClose = onClose
    if (items.isEmpty()) return close()
    val start = Resume.start(items, index, serverResume, app.kept.get(post.id), pickedByUser)
    start(start.index, start.seconds)
    scope.launch { ticker() }
    readAhead?.follow(scope, player) { app.settings.readAhead }
    // Every ten seconds, where playback is, as on the Apple TV.
    scope.launch {
      while (isActive) {
        delay(10_000)
        report()
      }
    }
  }

  // MARK: Items

  /** The "Max" file for the item playing, if any fits and has not failed. */
  fun directFor(): app.nama.core.DownloadFile? =
      downloads?.let { Direct.pick(it, post.isSeries, current.season, current.episodeRaw) }?.takeIf { it.id !in directFailed }

  /** Starts item [i], from [at] if given. */
  fun start(i: Int, at: Double? = null) {
    if (i !in items.indices) return close()
    report()
    keep()
    index = i
    // Each file's own first frame brings the controls up (PlayerScreen), not the change of index.
    pictured = false
    reportedTime = 0
    // Not the last file's length for this one, as the switch reports it.
    duration = 0.0
    val q = StartQuality.pick(current, app.settings.preferAdaptive, app.settings.startCap, DeviceCeiling.lines(context))
    if (q == null) {
      app.showToast(loc(S.noStream))
      return close()
    }
    // Another app chosen in Settings: the stream goes there, from where it was, and this player closes.
    if (app.settings.otherPlayer && playElsewhere(q.url(mix).toString(), at)) return close()
    resumedAt = at?.takeIf { it > 60 }
    startedAt = System.currentTimeMillis()
    nextCancelled = false
    quality = q
    if (app.settings.maxQuality && !downloadsAsked) {
      // Asked once; the stream starts at once, and the file takes over as soon as it is known.
      downloadsAsked = true
      load(q, at)
      scope.launch {
        downloads = runCatching { app.api.downloads(post.id, app.isLoggedIn) }.getOrNull()
        directFor()?.let { f -> if (!closed) playDirect(f, position.takeIf { it > 0 } ?: at) }
      }
    } else {
      val f = if (app.settings.maxQuality) directFor() else null
      if (f != null) playDirect(f, at) else load(q, at)
    }
    // Previews come from the smallest rendition: a tiny picture, at the least cost to the line.
    val lowest = current.qualities.filter { !it.isAuto }.minByOrNull { it.height } ?: current.qualities.firstOrNull()
    // None with Data saver: a preview is a stream of its own.
    preview.use(if (app.settings.dataSaver) null else lowest?.url(AudioMix.stereo)?.toString())
    if (subtitleLanguage.startsWith(FOUND)) subtitleLanguage = "fa"
    // A translation goes on into the next episode, if that has nothing Persian either.
    if (subtitleLanguage == TRANSLATED && current.subtitles["fa"] != null) subtitleLanguage = "fa"
    selectSubtitle(subtitleLanguage, remember = false)
    lookForSubtitles()
  }

  /**
   * The stream in the player the viewer picks, through Android's own chooser (its Always is theirs to
   * press). "position" in milliseconds is what VLC, MX Player and Just Player read; "title" names it.
   * False when no app on the TV plays video: then it plays here.
   */
  private fun playElsewhere(url: String, at: Double?): Boolean {
    val title = post.title(loc.lang)
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        .setDataAndType(android.net.Uri.parse(url), if (url.contains(".m3u8")) "application/x-mpegURL" else "video/*")
        .putExtra("title", title)
        .putExtra("position", ((at ?: 0.0) * 1000).toInt())
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }
        .onSuccess { Log.i(AppModel.TAG, "playing $title in another app") }
        .onFailure { app.showToast(TV.noOtherPlayer(loc.lang)) }
        .isSuccess
  }

  /** Plays a download file in place of the stream, from [at]. */
  fun playDirect(f: app.nama.core.DownloadFile, at: Double?) {
    direct = f
    restoreOffset()
    val url = f.url.toString()
    val name = f.fileName.lowercase()
    val item = MediaItem.Builder()
        .setUri(url)
        .setMimeType(if (name.endsWith(".mp4") || name.endsWith(".m4v")) MimeTypes.VIDEO_MP4 else MimeTypes.VIDEO_MATROSKA)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(post.title(loc.lang)).setDisplayTitle(post.title(loc.lang)).build())
        .build()
    Log.i(AppModel.TAG, "max: playing ${f.fileName}")
    player.setMediaItem(item, ((at ?: 0.0) * 1000).toLong())
    player.setPlaybackSpeed(speed)
    player.prepare()
    player.play()
    offer = null
  }

  /** The file failed: the stream takes over where the film was, and the file is not tried again. */
  private fun leaveDirect(why: String) {
    val f = direct ?: return
    Log.w(AppModel.TAG, "max: ${f.fileName} $why; back to the stream")
    directFailed += f.id
    direct = null
    quality?.let { load(it, position) }
  }

  private fun load(q: Quality, at: Double?) {
    direct = null
    restoreOffset()
    quality = q
    val url = q.url(mix).toString()
    val title = post.title(loc.lang)
    val line = if (post.isSeries || items.size > 1) loc.episodeTag(current.season, Episode.label(current.episodeRaw, index + 1)) + (current.title(loc.lang)?.let { " — $it" } ?: "") else null
    val item = MediaItem.Builder()
        .setUri(url)
        .apply { if (url.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setSubtitle(line).setDisplayTitle(title)
                .apply { post.poster.url(428.0)?.let { setArtworkUri(android.net.Uri.parse(it.toString())) } }
                .build())
        .build()
    player.setMediaItem(item, ((at ?: 0.0) * 1000).toLong())
    player.setPlaybackSpeed(speed)
    player.prepare()
    player.play()
    offer = null
  }

  fun next() {
    if (index + 1 < items.size) start(index + 1)
  }

  /** Back to the start of this episode if it has run more than ten seconds, else the one before. */
  fun previous() {
    if (position > 10 || index == 0) seekTo(0.0) else start(index - 1)
  }

  private fun finished() {
    if (closed) return
    if (sleep == Sleep.EndOfEpisode) {
      sleep = Sleep.Off
      return close()
    }
    if (index + 1 < items.size && app.settings.autoplayNext && !nextCancelled) {
      // After three episodes in a row with no key pressed, nobody may be watching: stop there,
      // without a dialog asking.
      if (unattended >= 3) {
        app.showToast(TV.stillWatchingStopped(loc.lang))
        return close()
      }
      unattended++
      start(index + 1)
    } else close()
  }

  /** A key was pressed: someone is watching. */
  fun attended() {
    unattended = 0
  }

  fun togglePlay() {
    if (player.isPlaying) player.pause()
    else {
      if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
      player.play()
    }
  }

  fun seekTo(seconds: Double) {
    // Never past the end, once it is known: there the film counts as finished and the next one starts.
    val to = seconds.coerceAtLeast(0.0).let { if (duration > 1) it.coerceAtMost(duration - 1) else it }
    player.seekTo((to * 1000).toLong())
    position = to
  }

  fun close() {
    if (closed) return
    closed = true
    report()
    keep()
    prefs.edit().putLong("bitrate", bandwidth.bitrateEstimate).apply()
    scope.cancel()
    preview.release()
    session?.release()
    player.release()
    onClose()
  }

  // MARK: Menus

  fun chooseQuality(q: Quality) {
    if (q == quality && direct == null) return
    load(q, position)
  }

  fun chooseMix(m: AudioMix) {
    if (m == mix) return
    mix = m
    app.update { it.copy(audioMix = m) }
    quality?.let { load(it, position) }
  }

  fun chooseAudioTrack(language: String) {
    val group = player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.mediaTrackGroup.getFormat(0).language == language } ?: return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0)).build()
  }

  fun chooseSpeed(s: Float) {
    speed = s
    player.setPlaybackSpeed(s)
    if (post.isSeries) prefs.edit().putFloat("series.${post.id}.speed", s).apply()
  }

  fun chooseSleep(s: Sleep) {
    sleep = s
    sleepJob?.cancel()
    if (s !is Sleep.Minutes) return
    sleepJob = scope.launch {
      delay(s.n * 60_000L)
      sleep = Sleep.Off
      // A paused film is left as it is; one playing, or waiting on the line to play, stops.
      if (player.playWhenReady) player.pause()
    }
  }

  /** Where the subtitle timing is kept: a series' for all its episodes, a film's for itself (films too, the Tizen app's). */
  private fun offsetKey() = (if (post.isSeries) "series.${post.id}.offset" else "film.${post.id}.offset") +
      // Each file its own: one release is timed apart from another (the Tizen app's).
      (direct?.let { ".${it.fileName}" } ?: "")

  /** The timing kept for what is about to play: the series', the film's, or that file's. */
  private fun restoreOffset() {
    subtitleOffset = prefs.getFloat(offsetKey(), 0f).toDouble()
  }

  /** Moves the subtitles by [seconds], in quarter seconds; zero puts them back. */
  fun nudgeSubtitles(seconds: Double) {
    subtitleOffset = if (seconds == 0.0) 0.0 else (Math.round((subtitleOffset + seconds) * 4) / 4.0).coerceIn(-10.0, 10.0)
    prefs.edit().putFloat(offsetKey(), subtitleOffset.toFloat()).apply()
    cue = track.text(position - subtitleOffset)
  }

  // MARK: Subtitles

  /**
   * 30nama has no Persian subtitle for this item: look on SubSource, and show the best one at once
   * if Persian is what the user watches with.
   */
  private fun lookForSubtitles() {
    findJob?.cancel()
    found = emptyList()
    searched = false
    if (current.subtitles["fa"] != null) return
    val item = index
    val query = SubSource.Query(post.titleEnglish ?: post.title(Lang.en), post.year, post.imdbID, post.isSeries, if (post.isSeries) current.season else null)
    val release = listOfNotNull(current.title(Lang.en), quality?.label).joinToString(" ")
    val episode = if (post.isSeries) current.episodeNumber else null
    findJob = scope.launch {
      val list = attempt { app.finder.candidates(query, release, episode) }.orEmpty()
      if (index != item) return@launch
      found = list.take(6)
      searched = true
      if (subtitleLanguage == "fa") list.firstOrNull()?.let { selectSubtitle(FOUND + it.id, remember = false) }
    }
  }

  /** Subtitles are separate WebVTT files, not tracks of the stream: fetched here and drawn over the video. */
  fun selectSubtitle(language: String, remember: Boolean) {
    subtitleLanguage = language
    if (remember) app.update { it.copy(defaultSubtitle = if (language.startsWith(FOUND) || language == TRANSLATED) "fa" else language) }
    subtitleJob?.cancel()
    track = SubtitleTrack()
    cue = ""
    translated = null
    if (language == TRANSLATED) {
      translate()
      return
    }
    if (language.startsWith(FOUND)) {
      val id = language.removePrefix(FOUND)
      val candidate = found.firstOrNull { it.id == id } ?: return
      val season = if (post.isSeries) current.season else null
      val episode = if (post.isSeries) current.episodeNumber else null
      subtitleJob = scope.launch {
        val text = attempt { app.finder.text(candidate, season, episode) }
        if (subtitleLanguage != language) return@launch
        if (text == null) app.showToast(loc(S.subFailed)) else track = withContext(Dispatchers.Default) { SubtitleTrack.vtt(text) }
      }
      return
    }
    if (language == "off") return
    val url = current.subtitles[language]?.toString() ?: return
    subtitleCache[url]?.let {
      track = it
      return
    }
    subtitleJob = scope.launch {
      val parsed = load(url) ?: return@launch app.showToast(loc(S.subFailed))
      if (subtitleLanguage == language) track = parsed
    }
  }

  /** A subtitle file, fetched and read, or from the ones already read. */
  private suspend fun load(url: String): SubtitleTrack? {
    subtitleCache[url]?.let { return it }
    val text = withContext(Dispatchers.IO) {
      attempt {
        // The debug build's test subtitles come inline.
        if (url.startsWith("data:")) String(android.util.Base64.decode(url.substringAfter("base64,"), android.util.Base64.DEFAULT))
        else cdn.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
      }
    } ?: return null
    return withContext(Dispatchers.Default) { SubtitleTrack.vtt(text) }.also { subtitleCache[url] = it }
  }

  /**
   * The English subtitle translated by Gemini (Translation.kt). The film goes on with the English
   * lines; each batch replaces its lines as it arrives, starting from where the viewer is.
   */
  private fun translate() {
    val url = current.subtitles["en"]?.toString() ?: return
    val key = app.key("gemini") ?: return
    val item = index
    subtitleJob = scope.launch {
      val source = load(url) ?: return@launch app.showToast(loc(S.subFailed))
      if (subtitleLanguage != TRANSLATED || index != item) return@launch
      var cues = source.cues
      track = source
      val target = Translation.target(app.settings.lang)
      val title = post.titleEnglish ?: post.title(Lang.en)
      val count = Translation.batchCount(cues)
      // A signed address changes from one evening to the next; the file it names does not.
      val name = url.substringBefore('?')
      var done = 0
      translated = 0f
      for (n in Translation.order(count, Translation.batchAt(cues, position - subtitleOffset))) {
        when (val o = app.gemini.translate(key, name, target, title, n, Translation.batch(cues, n))) {
          is Gemini.Outcome.Lines -> {
            cues = Translation.apply(cues, n, o.lines)
            track = SubtitleTrack(SubtitleTrack.directed(cues))
            cue = track.text(position - subtitleOffset)
          }
          Gemini.Outcome.Rejected -> return@launch app.showToast(TV.keyRejected(app.settings.lang))
          Gemini.Outcome.Unavailable -> return@launch app.showToast(TV.translationStopped(app.settings.lang))
        }
        done++
        translated = done.toFloat() / count
      }
    }
  }

  // MARK: Every quarter second

  private suspend fun ticker() {
    while (scope.isActive) {
      delay(250)
      if (closed) return
      val t = player.currentPosition / 1000.0
      position = t
      duration = player.duration.takeIf { it != C.TIME_UNSET }?.div(1000.0) ?: 0.0
      buffered = player.bufferedPosition / 1000.0
      val text = track.text(t - subtitleOffset)
      if (text != cue) cue = text
      tick(t)
    }
  }

  private fun tick(t: Double) {
    val item = current
    val skip = item.skipTarget(t)
    val credits = item.creditsStart?.takeIf { it > 0 }
    // Without a mark for the credits, the last 25 seconds stand in for it.
    val creditsAt = credits ?: (duration - 25).takeIf { duration > 120 }
    val hasNext = index + 1 < items.size
    // Watched once the credits begin, or most of the file has played.
    if (duration > 60 && (credits != null && t >= credits || t / duration > 0.95)) app.setWatched(item.fileID, true)
    offer = when {
      skip != null -> {
        // Once per stretch: going back into it on purpose is not undone.
        val stretch = "${item.id}@${skip.to}"
        if (app.settings.autoSkip && skippedOn != stretch && player.isPlaying) {
          skippedOn = stretch
          seekTo(skip.to)
          null
        } else Offer(if (skip.isIntro) Offer.Kind.Intro else Offer.Kind.Recap)
      }
      hasNext && creditsAt != null && t >= creditsAt && !nextCancelled -> {
        warmNext()
        val counting = app.settings.autoplayNext && sleep != Sleep.EndOfEpisode && unattended < 3 && duration > creditsAt
        Offer(Offer.Kind.Next, if (counting) (((t - creditsAt) / (duration - creditsAt)).toFloat().coerceIn(0f, 1f) * 50).toInt() / 50f else null)
      }
      // A title picked up part-way offers to start over for its first ten seconds.
      resumedAt != null && System.currentTimeMillis() - startedAt < 10_000 -> Offer(Offer.Kind.StartOver)
      else -> null
    }
  }

  private var warmed = -1

  /** Once per episode, as its credits begin: the next one's opening onto the disk. */
  private fun warmNext() {
    if (warmed == index) return
    warmed = index
    val next = items.getOrNull(index + 1) ?: return
    val url = StartQuality.pick(next, app.settings.preferAdaptive, app.settings.startCap, DeviceCeiling.lines(context))?.url(mix)?.toString() ?: return
    if (!url.contains(".m3u8")) return
    readAhead?.warm(scope, url, quality?.height?.takeIf { it > 0 })
  }

  /** Acts on the pill: skips the stretch, starts the next episode, or goes back to the start. */
  fun acceptOffer() {
    when (offer?.kind) {
      Offer.Kind.Intro, Offer.Kind.Recap -> current.skipTarget(position)?.let { seekTo(it.to) }
      Offer.Kind.Next -> next()
      Offer.Kind.StartOver -> {
        resumedAt = null
        seekTo(0.0)
      }
      null -> Unit
    }
    offer = null
  }

  // MARK: Progress

  /**
   * Tells the server where playback is, which drives "continue watching" on every device. The
   * position goes up in steps of thirty seconds, which is how the 30nama website reports it.
   */
  private fun report() {
    if (!app.isLoggedIn) return
    val seconds = player.currentPosition / 1000.0
    val time = (seconds.toInt() / 30) * 30
    // Not before the file's length is known: a switch would send the last file's.
    if (time <= 0 || time == reportedTime || duration <= 0) return
    reportedTime = time
    val progress = WatchProgress(post, current, app.user?.id, seconds, duration)
    val api = app.api
    app.background { api.reportProgress(progress) }
  }

  /** This TV's own record of where the title was left. */
  private fun keep() {
    val seconds = player.currentPosition / 1000.0
    if (seconds > 5) app.kept.put(post.id, current.fileID, seconds)
  }

  companion object {
    const val FOUND = "found:"
    const val TRANSLATED = "translated"
    private const val MIN_RETRIES = 10
    private const val USER_AGENT = "30nama-androidtv"
  }
}
