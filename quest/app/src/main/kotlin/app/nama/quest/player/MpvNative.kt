package app.nama.quest.player

/**
 * The JNI surface of libnamampv (src/main/cpp/namampv.c). Every call here returns at once;
 * use [Mpv] instead of calling these directly.
 */
internal object MpvNative {
  init {
    // The linker pulls libmpv, FFmpeg and libc++_shared from the APK on its own.
    System.loadLibrary("namampv")
  }

  external fun create(): Long

  external fun initialize(handle: Long): Int

  external fun setOptionString(handle: Long, name: String, value: String): Int

  external fun setOptionLong(handle: Long, name: String, value: Long): Int

  external fun requestLogMessages(handle: Long, level: String): Int

  external fun commandAsync(handle: Long, userdata: Long, args: Array<String>): Int

  external fun setPropertyStringAsync(handle: Long, userdata: Long, name: String, value: String): Int

  external fun setPropertyLongAsync(handle: Long, userdata: Long, name: String, value: Long): Int

  external fun setPropertyDoubleAsync(handle: Long, userdata: Long, name: String, value: Double): Int

  external fun setPropertyFlagAsync(handle: Long, userdata: Long, name: String, value: Boolean): Int

  external fun observeProperty(handle: Long, userdata: Long, name: String, format: Int): Int

  external fun unobserveProperty(handle: Long, userdata: Long): Int

  external fun errorString(error: Int): String

  external fun newSurfaceRef(surface: Any): Long

  external fun deleteSurfaceRef(ref: Long)

  external fun wakeup(handle: Long)

  external fun terminateDestroy(handle: Long)

  /**
   * Blocks in mpv_wait_event and calls [sink]'s onNative* methods until a wakeup (returns 0) or
   * shutdown (returns 1). Only the event thread calls this.
   */
  external fun pumpEvents(handle: Long, sink: Mpv.EventSink): Int
}
