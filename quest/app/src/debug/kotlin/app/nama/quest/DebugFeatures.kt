package app.nama.quest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.PanelRegistration
import java.io.File

/**
 * Debug only: a model sent by tools/hot-reload.sh replaces the apk's own on the headset a second
 * later, without a rebuild. The release build has an empty twin.
 *
 * Not the SDK's HotReloadFeature: it reloads Spatial Editor scenes, and for meshes made in code it
 * either never matches an apk:/// mesh or doubles the path of a file:/// one (0.14.0).
 */
fun debugFeatures(activity: AppSystemActivity): List<SpatialFeature> {
  HotAssets.start(activity)
  return emptyList()
}

/** Debug panels: the note for Claude (Notes). */
fun debugPanels(): List<PanelRegistration> = listOf(Notes.panel())

fun debugTick(activity: AppSystemActivity, model: AppModel, shelf: Shelf) = Notes.tick(activity, model, shelf)

/** Where the app reads [path] (relative to the apk's assets) from. */
fun assetUri(path: String): String = HotAssets.uri(path) ?: "apk:///$path"

/** Goes up each time models arrive, so the stage knows to load them again. */
fun assetVersion(): Int = HotAssets.version

private object HotAssets {
  const val ACTION = "app.nama.quest.HOT_RELOAD"
  @Volatile var version = 0
  private var inbox: File? = null
  private var loaded: File? = null

  fun start(ctx: Context) {
    val files = ctx.getExternalFilesDir(null) ?: return
    inbox = files.resolve("hot")
    loaded = files.resolve("hot-loaded")
    // What was sent before the last install is older than the apk's own models: drop it.
    val installed = ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
    inbox!!.walkBottomUp().filter { it.isFile && it.lastModified() < installed }.forEach { it.delete() }
    loaded!!.deleteRecursively()
    take()
    ctx.registerReceiver(object : BroadcastReceiver() {
      override fun onReceive(c: Context, i: Intent) = take()
    }, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
  }

  /** Copies the inbox under a new name: the SDK keeps meshes by their address, so a new one loads afresh. */
  private fun take() {
    val from = inbox ?: return
    if (from.walk().none { it.isFile }) return
    val next = version + 1
    val to = loaded!!.resolve("v$next")
    from.copyRecursively(to, overwrite = true)
    // Keep the one on screen until the new one has replaced it.
    loaded!!.listFiles()?.filter { it.name != "v$next" && it.name != "v$version" }?.forEach { it.deleteRecursively() }
    version = next
    Log.i("Nama", "hot reload v$next: ${from.walk().filter { it.isFile }.map { it.relativeTo(from).path }.toList()}")
  }

  fun uri(path: String): String? =
      loaded?.resolve("v$version/$path")?.takeIf { version > 0 && it.exists() }?.let { "file://${it.path}" }
}
