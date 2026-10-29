package app.nama.androidtv

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nama.androidtv.data.AndroidRelease
import app.nama.androidtv.data.PendingUpdate
import app.nama.androidtv.data.UpdateSignature
import app.nama.core.Json
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Updates from the project's GitHub releases, as on the Mac and Windows: the newest `android-v`
 * release is found, its APK downloaded and checked twice (the project's Ed25519 signature, and that
 * Android's own APK key is the one this app was installed with, since Android refuses anything else
 * over it), then handed to Android's installer. Each step is written to update.log, and a failure
 * sends the viewer to the download page, so the cause is found without guessing.
 */
class Updater(private val context: Context, private val http: OkHttpClient, private val scope: CoroutineScope) {
  sealed interface State {
    data object Idle : State
    data object Checking : State
    data object UpToDate : State
    data class Available(val release: AndroidRelease) : State
    /** [progress] 0 to 1, or below 0 while the size is not known. */
    data class Downloading(val release: AndroidRelease, val progress: Float) : State
    /** Android's installer has it; it asks the viewer, and then the app is replaced. */
    data class Installing(val release: AndroidRelease) : State
    /** Android first asks to let this app install apps; it comes back here once allowed. */
    data class NeedsPermission(val release: AndroidRelease) : State
    /**
     * [step] is the step that failed, in the log's words, shown small on the card for a photo;
     * [blocked]: Android had not let this app install apps, and the card says where to allow it.
     */
    data class Failed(val page: String, val step: String = "", val blocked: Boolean = false) : State
  }

  var state: State by mutableStateOf(State.Idle)
    internal set

  /** The failure card is up; it goes when closed, and the row in Settings brings it back. */
  var showFailure: Boolean by mutableStateOf(false)

  /** "Later" on the capsule under the bar: it goes until the app opens again (the Mac's). */
  var postponed: Boolean by mutableStateOf(false)
    private set

  fun postpone() {
    postponed = true
  }

  /** Set once, when the app opens as the update it installed; for a word to the viewer. */
  var arrived: String? by mutableStateOf(null)
    private set

  val current: String = BuildConfig.VERSION_NAME
  private val dir = File(context.cacheDir, "update")
  private val pendingFile = File(context.filesDir, "update-pending.json")
  private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

  /** The `userTest` build's local feed, or the real one. */
  private val feed: String = BuildConfig.TEST_FEED.ifEmpty { AndroidRelease.FEED }

  /** At launch: whether the last update arrived, and a quiet look for a new one once a day. */
  /**
   * Once per process: the activity can be made again while the app lives on (Back out, open from
   * the launcher), and a second start would delete a download under way, or call an install still
   * waiting at the installer a failure.
   */
  private var started = false

  fun start() {
    if (started) return
    started = true
    PendingUpdate.parse(pendingFile.takeIf { it.exists() }?.readText())?.let { pending ->
      pendingFile.delete()
      if (pending.arrived(current)) {
        log("opened as ${current}: the update arrived")
        arrived = current
      } else {
        log("opened as $current, not ${pending.version}: the installer did not finish")
        state = State.Failed(pending.page, "installer did not finish (${pending.version})", blocked = !allowedToInstall())
        showFailure = true
      }
    }
    dir.deleteRecursively()
    val last = prefs.getLong("checked", 0)
    if (state == State.Idle && System.currentTimeMillis() - last > 20 * 3600_000L) check(quiet = true)
  }

  fun check(quiet: Boolean = false) {
    if (state is State.Checking || state is State.Downloading || state is State.Installing) return
    if (!quiet) state = State.Checking
    scope.launch {
      val found = withContext(Dispatchers.IO) {
        runCatching {
          http.newCall(Request.Builder().url(feed).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            AndroidRelease.newest(Json.parse(r.body!!.bytes()), current)
          }
        }
      }
      found.onSuccess { prefs.edit().putLong("checked", System.currentTimeMillis()).apply() }
      found.exceptionOrNull()?.let { Log.w(AppModel.TAG, "update check: $it") }
      val release = found.getOrNull()
      // An update under way since this look began (the viewer pressed Install) is not overwritten.
      if (state is State.Downloading || state is State.Installing || state is State.NeedsPermission) return@launch
      state = when {
        release != null -> State.Available(release)
        // A failed look in the background says nothing; one the viewer asked for says it is current
        // only when GitHub answered.
        quiet -> state
        found.isSuccess -> State.UpToDate
        else -> State.Idle
      }
    }
  }

  fun dismiss() {
    state = State.Idle
  }

  fun install(release: AndroidRelease) {
    if (state is State.Downloading || state is State.Installing) return
    state = State.Downloading(release, -1f)
    scope.launch {
      val apk = File(dir, "30nama-androidtv.apk")
      val step = withContext(Dispatchers.IO) { prepare(release, apk) }
      if (step != null) return@launch fail(release, step)
      if (!allowedToInstall()) {
        log("asking to be allowed to install apps")
        state = State.NeedsPermission(release)
        val asked = runCatching {
          context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        // Some TVs have no such screen: the installer then asks by itself, or says no.
        if (asked.isSuccess) return@launch
      }
      commit(release, apk)
    }
  }

  /** Back from the system's "install unknown apps" screen. */
  fun resumed() {
    val s = state as? State.NeedsPermission ?: return
    // Some TVs (a Sony on Android 12) open the screen but keep the switch elsewhere: the card says where.
    if (!allowedToInstall()) return fail(s.release, "not allowed to install apps")
    scope.launch { commit(s.release, File(dir, "30nama-androidtv.apk")) }
  }

  /** Downloads and checks; the step that failed, or null. */
  private fun prepare(release: AndroidRelease, apk: File): String? {
    dir.mkdirs()
    log("update ${release.version} from $current: downloading ${release.apk}")
    val sig = runCatching { http.newCall(Request.Builder().url(release.signature).build()).execute().use { if (it.isSuccessful) it.body!!.string() else error("HTTP ${it.code}") } }
        .getOrElse { return "download signature: $it" }
    // A second try before giving up: a TV's connection drops more often than a computer's.
    fun download() = runCatching {
      http.newCall(Request.Builder().url(release.apk).build()).execute().use { r ->
        if (!r.isSuccessful) error("HTTP ${r.code}")
        val body = r.body!!
        val total = body.contentLength()
        var done = 0L
        var shown = 0f
        body.byteStream().use { input ->
          apk.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
              val n = input.read(buffer)
              if (n < 0) break
              out.write(buffer, 0, n)
              done += n
              val p = if (total > 0) done.toFloat() / total else -1f
              if (p - shown >= 0.01f) {
                shown = p
                state = State.Downloading(release, p)
              }
            }
          }
        }
      }
    }
    download().recoverCatching {
      log("download broke off ($it); once more")
      download().getOrThrow()
    }.onFailure { return "download apk: $it" }
    log("downloaded ${apk.length()} bytes; checking the signature")
    val signed = runCatching { apk.inputStream().buffered().use { UpdateSignature.isValid(sig, it) } }.getOrElse { return "signature: could not read the apk ($it)" }
    if (!signed) return "signature: not the project's"
    val archive = archiveInfo(apk) ?: return "apk: Android cannot read it"
    if (archive.packageName != context.packageName) return "apk: another app (${archive.packageName})"
    val mine = runCatching { packageInfo() }.getOrNull() ?: return "apk: this app's own key unknown"
    val theirs = signers(archive)
    // Two empty sets are equal: no key read is no check, and fails.
    if (theirs.isEmpty() || theirs != signers(mine)) return "apk: signed with another APK key than this app"
    log("signature and APK key check out")
    return null
  }

  private suspend fun commit(release: AndroidRelease, apk: File) {
    state = State.Installing(release)
    val step = withContext(Dispatchers.IO) {
      runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
          setAppPackageName(context.packageName)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
          session.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
          // Written down before the app is replaced: the version that opens next says whether it came.
          pendingFile.writeText(PendingUpdate(release.version, release.page).serialize())
          val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
          val status = PendingIntent.getBroadcast(context, id, Intent(context, InstallStatus::class.java).setPackage(context.packageName), flags)
          session.commit(status.intentSender)
        }
        log("handed to the installer")
        null
      }.getOrElse { "installer: $it" }
    }
    if (step != null) fail(release, step)
  }

  /** What the installer answered, through [InstallStatus]. */
  internal fun installerSaid(intent: Intent) {
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    val release = (state as? State.Installing)?.release
    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        log("the installer asks the viewer")
        @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
      }
      PackageInstaller.STATUS_SUCCESS -> log("installed")
      else -> {
        pendingFile.delete()
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
          log("the viewer said no")
          state = release?.let { State.Available(it) } ?: State.Idle
        } else {
          fail(release, "installer status $status: $message")
        }
      }
    }
  }

  private fun fail(release: AndroidRelease?, step: String) {
    log("failed at $step")
    Log.w(AppModel.TAG, "update: $step")
    state = State.Failed(release?.page ?: AndroidRelease.DOWNLOADS, step, blocked = !allowedToInstall())
    showFailure = true
  }

  private fun allowedToInstall(): Boolean =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

  private fun archiveInfo(apk: File): PackageInfo? =
      // Android 9 reads a downloaded APK's key only when asked for both (AGENTS.md).
      @Suppress("DEPRECATION")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)
      else @Suppress("DEPRECATION") context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)

  private fun packageInfo(): PackageInfo =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
      else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)

  /** The APK keys' fingerprints. Android 9 reports them as signing info, older ones as signatures. */
  @SuppressLint("PackageManagerGetSignatures")
  private fun signers(info: PackageInfo): Set<String> {
    @Suppress("DEPRECATION")
    val sigs = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory } else null) ?: info.signatures
    return sigs.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
  }

  /** One line per step, in the app's files; the newest 200 lines are kept. */
  private fun log(line: String) {
    val file = File(context.filesDir, "update.log")
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
    runCatching {
      val lines = (if (file.exists()) file.readLines() else emptyList()) + "$stamp  $line"
      file.writeText(lines.takeLast(200).joinToString("\n", postfix = "\n"))
    }
    Log.i(AppModel.TAG, "update: $line")
  }
}

/** Receives the installer's answers for [Updater]. */
class InstallStatus : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    (context.applicationContext as NamaApp).model.updater.installerSaid(intent)
  }
}

/**
 * After the app was replaced by its update, opens it again, so the viewer is not left on the home
 * screen. Only up to Android 9: later ones refuse to let an app open itself from the background, and
 * the viewer is told before the installer to open it from the home screen (TV.installHint).
 */
class Replaced : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
  }
}
