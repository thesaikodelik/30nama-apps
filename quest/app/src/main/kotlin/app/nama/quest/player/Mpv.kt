package app.nama.quest.player

import android.util.Log
import android.view.Surface
import androidx.annotation.Keep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A thin, thread-safe wrapper over one libmpv instance.
 *
 * Nothing here waits for mpv: commands and property writes go through the async API, and values
 * come back as observed property changes on a dedicated event thread. The main thread must never
 * block on mpv (AGENTS.md: the core can be waiting for the very thread that asks it something).
 *
 * Lifecycle: construct, [setOption] as needed, [initialize], [attachSurface] whenever the panel's
 * Surface exists, then [destroy]. Listener callbacks run on the event thread.
 */
class Mpv(private val listener: Listener) {

  /** mpv_format values. */
  enum class Format(internal val id: Int) {
    NONE(0),
    STRING(1),
    OSD_STRING(2),
    FLAG(3),
    INT64(4),
    DOUBLE(5),
    /** Delivered as a JSON string (track-list, video-params, ...). */
    NODE(6),
  }

  enum class EndReason {
    EOF,
    STOP,
    QUIT,
    ERROR,
    REDIRECT,
    UNKNOWN;

    internal companion object {
      fun of(code: Long) =
          when (code) {
            0L -> EOF
            2L -> STOP
            3L -> QUIT
            4L -> ERROR
            5L -> REDIRECT
            else -> UNKNOWN
          }
    }
  }

  sealed interface Event {
    data class StartFile(val playlistEntryId: Long) : Event

    data object FileLoaded : Event

    /** [error] is an mpv error code (negative) when [reason] is ERROR; see [errorString]. */
    data class EndFile(val reason: EndReason, val error: Int, val playlistEntryId: Long) : Event

    data object PlaybackRestart : Event

    data object Seek : Event

    data object VideoReconfig : Event

    data object AudioReconfig : Event

    data object Shutdown : Event

    data class Other(val id: Int) : Event
  }

  interface Listener {
    /** [value] is Boolean, Long, Double, String (NODE as JSON) or null when unavailable. */
    fun onPropertyChange(name: String, value: Any?) {}

    fun onEvent(event: Event) {}

    /** [level] is mpv's: 10 fatal, 20 error, 30 warn, 40 info, 50 v, 60 debug, 70 trace. */
    fun onLogMessage(prefix: String, level: Int, text: String) {}
  }

  /** Called from native code by name; keep the signatures in step with namampv.c. */
  @Keep
  internal interface EventSink {
    @Keep fun onNativeEvent(id: Int, error: Int, userdata: Long, a: Long, b: Long)

    @Keep
    fun onNativeProperty(name: String, userdata: Long, format: Int, l: Long, d: Double, s: String?)

    @Keep fun onNativeLog(prefix: String, level: Int, text: String)
  }

  // Guards handle against destroy: calls take the read lock only to enqueue (never waits on mpv),
  // the event thread takes the write lock only to swap the handle out.
  private val lock = ReentrantReadWriteLock()
  private var handle: Long = MpvNative.create().also { check(it != 0L) { "mpv_create failed" } }
  @Volatile private var initialized = false
  @Volatile private var destroying = false
  private var eventThread: Thread? = null

  private val nextUserdata = AtomicLong(1)
  private val replies = ConcurrentHashMap<Long, (Int) -> Unit>()

  // Surface state. Guarded by surfaceLock; refs are freed only once mpv confirms it let go.
  private val surfaceLock = Any()
  private var surfaceRef = 0L
  private val retiredSurfaceRefs = HashSet<Long>()
  private var videoOutput = "gpu"

  /** Sets an option before [initialize]; afterwards the same as [setProperty]. */
  fun setOption(name: String, value: String): Int {
    if (initialized) return setProperty(name, value)
    return lock.read { if (handle == 0L) ERR_UNINITIALIZED else MpvNative.setOptionString(handle, name, value) }
  }

  /** Starts the core and the event thread. Log messages at [logLevel] and above reach the listener. */
  fun initialize(logLevel: String = "warn"): Int {
    check(!initialized) { "already initialized" }
    // lock order everywhere: surfaceLock, then the handle lock
    val r =
        synchronized(surfaceLock) {
          lock.read {
            if (handle == 0L) return ERR_UNINITIALIZED
            // no Surface yet: keep mpv from opening a window it has nowhere to draw
            if (surfaceRef == 0L) {
              MpvNative.setOptionString(handle, "vo", "null")
              MpvNative.setOptionString(handle, "force-window", "no")
            }
            MpvNative.requestLogMessages(handle, logLevel)
            // flipped under surfaceLock so attachSurface never sees a half-started core
            MpvNative.initialize(handle).also { if (it >= 0) initialized = true }
          }
        }
    if (r < 0) {
      Log.e(TAG, "mpv_initialize failed: ${errorString(r)}")
      return r
    }
    eventThread = Thread(::eventLoop, "mpv-events").apply { start() }
    return r
  }

  /**
   * Hands mpv the Surface to draw into (the panel's compositor layer). mpv receives a JNI global
   * reference as "wid"; the vo is switched from "null" to the chosen output, as mpv-android does.
   */
  fun attachSurface(surface: Surface) {
    detachSurface()
    synchronized(surfaceLock) {
      val ref = MpvNative.newSurfaceRef(surface)
      surfaceRef = ref
      if (!initialized) {
        lock.read {
          if (handle == 0L) return
          MpvNative.setOptionLong(handle, "wid", ref)
          MpvNative.setOptionString(handle, "force-window", "yes")
          MpvNative.setOptionString(handle, "vo", videoOutput)
        }
      } else {
        setProperty("wid", ref)
        setProperty("force-window", "yes")
        setProperty("vo", videoOutput)
      }
    }
  }

  /** Tells mpv the Surface's size in pixels; vo=gpu renders at this size. */
  fun setSurfaceSize(width: Int, height: Int) {
    if (width > 0 && height > 0) setProperty("android-surface-size", "${width}x$height")
  }

  /**
   * Takes the Surface away from mpv. The vo goes to "null" first; the global reference is freed
   * when mpv has acknowledged "wid=0", so it never draws into a freed object.
   */
  fun detachSurface() {
    synchronized(surfaceLock) {
      val ref = surfaceRef
      if (ref == 0L) return
      surfaceRef = 0L
      if (!initialized) {
        lock.read { if (handle != 0L) MpvNative.setOptionLong(handle, "wid", 0) }
        MpvNative.deleteSurfaceRef(ref)
        return
      }
      retiredSurfaceRefs += ref
      setProperty("vo", "null")
      setProperty("force-window", "no")
      val r = setProperty("wid", 0L) { releaseSurfaceRef(ref) }
      // not enqueued (mpv is going away): destroy() frees it after the core is gone
      if (r < 0) Log.w(TAG, "detachSurface: ${errorString(r)}")
    }
  }

  val hasSurface: Boolean
    get() = synchronized(surfaceLock) { surfaceRef != 0L }

  /**
   * The video output used while a Surface is attached ("mediacodec_embed" or "gpu"). Applied at
   * once if a Surface is attached, otherwise at the next [attachSurface].
   */
  fun setVideoOutput(vo: String) {
    synchronized(surfaceLock) {
      videoOutput = vo
      if (surfaceRef != 0L) setOption("vo", vo)
    }
  }

  /** Runs an mpv command asynchronously. [onReply] gets the mpv error code (0 or more is success). */
  fun command(vararg args: String, onReply: ((Int) -> Unit)? = null): Int =
      submit(onReply) { h, ud -> MpvNative.commandAsync(h, ud, arrayOf(*args)) }

  fun setProperty(name: String, value: String, onReply: ((Int) -> Unit)? = null): Int =
      submit(onReply) { h, ud -> MpvNative.setPropertyStringAsync(h, ud, name, value) }

  fun setProperty(name: String, value: Long, onReply: ((Int) -> Unit)? = null): Int =
      submit(onReply) { h, ud -> MpvNative.setPropertyLongAsync(h, ud, name, value) }

  fun setProperty(name: String, value: Double, onReply: ((Int) -> Unit)? = null): Int =
      submit(onReply) { h, ud -> MpvNative.setPropertyDoubleAsync(h, ud, name, value) }

  fun setProperty(name: String, value: Boolean, onReply: ((Int) -> Unit)? = null): Int =
      submit(onReply) { h, ud -> MpvNative.setPropertyFlagAsync(h, ud, name, value) }

  /** Values arrive through [Listener.onPropertyChange]. Returns an id for [unobserveProperty]. */
  fun observeProperty(name: String, format: Format): Long {
    val id = nextUserdata.getAndIncrement()
    lock.read { if (handle != 0L) MpvNative.observeProperty(handle, id, name, format.id) }
    return id
  }

  fun unobserveProperty(id: Long) {
    lock.read { if (handle != 0L) MpvNative.unobserveProperty(handle, id) }
  }

  /**
   * Shuts mpv down without blocking the caller: the event thread leaves its loop and runs
   * mpv_terminate_destroy itself. Safe to call more than once.
   */
  fun destroy() {
    if (destroying) return
    destroying = true
    detachSurface()
    if (eventThread != null) {
      lock.read { if (handle != 0L) MpvNative.wakeup(handle) }
    } else {
      // never initialized: no event thread, so tear down on a short-lived one
      Thread({ teardown() }, "mpv-destroy").start()
    }
  }

  fun errorString(error: Int): String = MpvNative.errorString(error)

  private inline fun submit(noinline onReply: ((Int) -> Unit)?, call: (Long, Long) -> Int): Int {
    val ud = if (onReply != null) nextUserdata.getAndIncrement().also { replies[it] = onReply } else 0L
    val r = lock.read { if (handle == 0L) ERR_UNINITIALIZED else call(handle, ud) }
    if (r < 0 && ud != 0L) replies.remove(ud)
    return r
  }

  private fun releaseSurfaceRef(ref: Long) {
    synchronized(surfaceLock) { if (!retiredSurfaceRefs.remove(ref)) return }
    MpvNative.deleteSurfaceRef(ref)
  }

  private fun eventLoop() {
    val h = handle
    try {
      while (!destroying) {
        if (MpvNative.pumpEvents(h, sink) != 0) break // shutdown or broken sink
      }
    } finally {
      teardown()
    }
  }

  private fun teardown() {
    val h = lock.write { handle.also { handle = 0L } }
    if (h == 0L) return
    MpvNative.terminateDestroy(h)
    synchronized(surfaceLock) {
      retiredSurfaceRefs.forEach(MpvNative::deleteSurfaceRef)
      retiredSurfaceRefs.clear()
      if (surfaceRef != 0L) MpvNative.deleteSurfaceRef(surfaceRef)
      surfaceRef = 0L
    }
    replies.clear()
  }

  private val sink =
      object : EventSink {
        override fun onNativeEvent(id: Int, error: Int, userdata: Long, a: Long, b: Long) {
          if (id == EVENT_SET_PROPERTY_REPLY || id == EVENT_COMMAND_REPLY) {
            replies.remove(userdata)?.invoke(error)
            return
          }
          val event =
              when (id) {
                EVENT_SHUTDOWN -> Event.Shutdown
                EVENT_START_FILE -> Event.StartFile(a)
                EVENT_END_FILE -> Event.EndFile(EndReason.of(a), error, b)
                EVENT_FILE_LOADED -> Event.FileLoaded
                EVENT_VIDEO_RECONFIG -> Event.VideoReconfig
                EVENT_AUDIO_RECONFIG -> Event.AudioReconfig
                EVENT_SEEK -> Event.Seek
                EVENT_PLAYBACK_RESTART -> Event.PlaybackRestart
                else -> Event.Other(id)
              }
          listener.onEvent(event)
        }

        override fun onNativeProperty(name: String, userdata: Long, format: Int, l: Long, d: Double, s: String?) {
          val value: Any? =
              when (format) {
                Format.FLAG.id -> l != 0L
                Format.INT64.id -> l
                Format.DOUBLE.id -> d
                Format.STRING.id, Format.OSD_STRING.id, Format.NODE.id -> s
                else -> null
              }
          listener.onPropertyChange(name, value)
        }

        override fun onNativeLog(prefix: String, level: Int, text: String) {
          listener.onLogMessage(prefix, level, text)
        }
      }

  private companion object {
    const val TAG = "Nama"
    const val ERR_UNINITIALIZED = -3 // MPV_ERROR_UNINITIALIZED

    const val EVENT_SHUTDOWN = 1
    const val EVENT_SET_PROPERTY_REPLY = 4
    const val EVENT_COMMAND_REPLY = 5
    const val EVENT_START_FILE = 6
    const val EVENT_END_FILE = 7
    const val EVENT_FILE_LOADED = 8
    const val EVENT_VIDEO_RECONFIG = 17
    const val EVENT_AUDIO_RECONFIG = 18
    const val EVENT_SEEK = 20
    const val EVENT_PLAYBACK_RESTART = 21
  }
}
