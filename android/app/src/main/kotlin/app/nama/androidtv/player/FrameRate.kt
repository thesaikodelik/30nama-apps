package app.nama.androidtv.player

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Switches the TV to a refresh rate the film's frame rate divides into (24 frames on a 24, 48 or
 * 120 Hz mode), at the same resolution, so that motion is even instead of judder. Off unless asked
 * for in Settings: the switch blanks the screen for a moment on most TVs. Media3 itself already
 * asks for the rate where the TV can change it without a blank (Android 11 and later).
 */
object FrameRate {
  private var original: Int? = null

  /** The mode to use for [fps] among [modes], at the resolution of [current]; null when none fits better. */
  fun pick(fps: Float, current: Display.Mode, modes: List<Display.Mode>): Display.Mode? {
    if (fps <= 0) return null
    fun fits(rate: Float): Boolean {
      val ratio = rate / fps
      return ratio >= 0.99f && abs(ratio - ratio.roundToInt()) < 0.01f
    }
    if (fits(current.refreshRate)) return null
    return modes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight && fits(it.refreshRate) }
        .minByOrNull { it.refreshRate }
  }

  fun apply(activity: Activity, fps: Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val display = activity.windowManager.defaultDisplay
    val mode = pick(fps, display.mode, display.supportedModes.toList()) ?: return
    val attrs = activity.window.attributes
    if (original == null) original = attrs.preferredDisplayModeId
    attrs.preferredDisplayModeId = mode.modeId
    activity.window.attributes = attrs
  }

  /** Back to the mode the TV was in before the film. */
  fun restore(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val id = original ?: return
    original = null
    val attrs = activity.window.attributes
    attrs.preferredDisplayModeId = id
    activity.window.attributes = attrs
  }
}
