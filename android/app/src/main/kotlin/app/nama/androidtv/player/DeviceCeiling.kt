package app.nama.androidtv.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

/**
 * The most lines a picture is worth fetching in on this device: no more than its screen shows
 * (a 1080p TV, or a box wired to one) and no more than a hardware decoder here reads. A 4K file on
 * a 1080p screen costs four times the line for nothing seen, and a cheap box that cannot decode it
 * stutters (the owner). 0 where neither can be told.
 */
object DeviceCeiling {
  @Volatile private var known: Int? = null

  fun lines(context: Context): Int = known ?: (runCatching { shown(context).let { s -> decoded().let { d -> listOf(s, d).filter { it > 0 }.minOrNull() ?: 0 } } }.getOrDefault(0)).also { known = it }

  /**
   * The tallest mode the screen offers, as lines of a landscape picture. Some 4K TVs (Sony's,
   * some Philips and TCL) list only 1080p modes; Media3 knows their own ways of saying 4K.
   */
  @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
  private fun shown(context: Context): Int {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return 0
    val modes = display.supportedModes.maxOfOrNull { minOf(it.physicalWidth, it.physicalHeight) } ?: 0
    val media3 = runCatching { androidx.media3.common.util.Util.getCurrentDisplayModeSize(context, display).let { minOf(it.x, it.y) } }.getOrDefault(0)
    return maxOf(modes, media3)
  }

  /** The tallest 16:9 picture a hardware decoder for H.264 or HEVC takes; 0 where none says. */
  private fun decoded(): Int {
    var best = 0
    for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
      if (info.isEncoder || !hardware(info)) continue
      for (type in info.supportedTypes) {
        if (!type.equals("video/avc", true) && !type.equals("video/hevc", true)) continue
        val video = runCatching { info.getCapabilitiesForType(type).videoCapabilities }.getOrNull() ?: continue
        // Rounded up to the decoder's own alignment first, as Media3 does: 1080 is not a multiple
        // of 16, and a 1080p decoder that wants 16 said no to it, leaving 720.
        fun up(v: Int, by: Int) = if (by > 1) (v + by - 1) / by * by else v
        best = maxOf(best, listOf(3840 to 2160, 2560 to 1440, 1920 to 1080, 1280 to 720).firstOrNull { (w, h) ->
          video.isSizeSupported(up(w, video.widthAlignment), up(h, video.heightAlignment))
        }?.second ?: 0)
      }
    }
    return best
  }

  /** A decoder in the chip, not one in software: those claim large sizes and cannot keep up. */
  private fun hardware(info: MediaCodecInfo): Boolean =
      if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
      else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
}
