package app.nama.quest

import android.content.Context
import android.os.Build
import android.util.Log
import app.nama.quest.player.MpvPlayer
import app.nama.quest.player.VideoEngine
import java.io.File

fun createVideoEngine(context: Context): VideoEngine =
    MpvPlayer(MpvPlayer.Config(userAgent = "30nama-quest/${BuildConfig.VERSION_NAME}", caFile = trustBundle(context)))

/**
 * Android's trusted roots gathered into one PEM file for mpv's TLS (mbedTLS has no access to the
 * system store). Rebuilt when the OS is updated, since an update can change the roots.
 */
private fun trustBundle(context: Context): String? =
    runCatching {
          val out = File(context.filesDir, "ca-roots.pem")
          val stamp = File(context.filesDir, "ca-roots.stamp")
          if (out.exists() && stamp.exists() && stamp.readText() == Build.FINGERPRINT) return out.path
          val pem = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]+?-----END CERTIFICATE-----")
          val roots =
              File("/system/etc/security/cacerts").listFiles().orEmpty().flatMap { f ->
                pem.findAll(runCatching { f.readText() }.getOrDefault("")).map { it.value }.toList()
              }
          if (roots.isEmpty()) return null
          out.writeText(roots.joinToString("\n", postfix = "\n"))
          stamp.writeText(Build.FINGERPRINT)
          out.path
        }
        .onFailure { Log.w("Nama", "trust bundle: $it") }
        .getOrNull()
