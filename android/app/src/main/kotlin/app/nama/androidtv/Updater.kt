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
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nama.androidtv.data.AndroidRelease
import app.nama.androidtv.data.PendingUpdate
import app.nama.androidtv.data.UpdateSignature
import app.nama.androidtv.ui.clearCaches
import app.nama.core.Json
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    /**
     * Android's installer has it; it asks the viewer, and then the app is replaced. [asksPermission]:
     * this app may not install apps yet, so Android's window first asks for the switch.
     */
    data class Installing(val release: AndroidRelease, val asksPermission: Boolean = false) : State
    /**
     * Android's installer refused for want of the switch: a card walks the viewer to it, and the
     * update goes on by itself once they come back with it on (the APK is already downloaded).
     */
    data class NeedsPermission(val release: AndroidRelease) : State
    /**
     * [step] is the step that failed, in the log's words, shown small on the card for a photo;
     * [blocked]: Android had not let this app install apps, and the card says where to allow it.
     * [full]: the TV had no room for the new version, and the card says how to make some.
     */
    data class Failed(val page: String, val step: String = "", val blocked: Boolean = false, val full: Boolean = false) : State
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

  /** The `userTest` build's local feed, or the real one. */
  private val feed: String = BuildConfig.TEST_FEED.ifEmpty { AndroidRelease.FEED }

  /**
   * Once per process: the activity can be made again while the app lives on (Back out, open from
   * the launcher), and a second start would delete a download under way, or call an install still
   * waiting at the installer a failure.
   */
  private var started = false

  /**
   * When the last look began. Looks come at launch and on every return to the app, at most one in
   * [LOOK_GAP]. Until 1.1.4 a look came once a day, and only when the process started: a TV keeps
   * the app alive for days, and a look just before a release kept it from being seen for 20 hours.
   */
  private var lastLook = 0L

  private companion object {
    /** Fifteen minutes: a TV is opened and left many times an evening; GitHub allows sixty looks an hour per address. */
    const val LOOK_GAP = 15 * 60_000L

    /** Beyond the APK itself: the installer's own files and Android's optimised code. */
    const val ROOM_MARGIN = 32L shl 20

    /** A full disk, as Android and Java word it, wherever in the steps it showed. */
    val NO_ROOM = Regex("ENOSPC|No space left|Failed to allocate|INSUFFICIENT_STORAGE", RegexOption.IGNORE_CASE)
  }

  /** At launch: whether the last update arrived, and a quiet look for a new one. */
  fun start() {
    if (started) return
    started = true
    PendingUpdate.parse(pendingFile.takeIf { it.exists() }?.readText())?.let { pending ->
      pendingFile.delete()
      if (pending.arrived(current)) {
        log("opened as ${current}: the update arrived")
        arrived = current
      } else {
        // Android's confirmation was left (Home, or the TV slept) and said nothing back. Not a failure:
        // the update is offered again under the bar, not a card sending the viewer to download it.
        log("opened as $current, not ${pending.version}: the installer was left; offered again")
      }
    }
    dir.deleteRecursively()
    check(quiet = true)
  }

  /**
   * Asks GitHub and the R2 mirror at once and takes the newest either has; one that does not answer
   * does not hold up the other. The `userTest` build asks only its local feed.
   */
  private suspend fun look(): Result<AndroidRelease?> = coroutineScope {
    fun ask(url: String, read: (Json) -> AndroidRelease?) = async(Dispatchers.IO) {
      runCatching {
        http.newCall(Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
          if (!r.isSuccessful) error("HTTP ${r.code}")
          read(Json.parse(r.body!!.bytes()))
        }
      }.onFailure { Log.w(AppModel.TAG, "update check, $url: $it") }
    }
    val asked = buildList {
      add(ask(feed) { AndroidRelease.newest(it, current) })
      if (BuildConfig.TEST_FEED.isEmpty()) add(ask(AndroidRelease.MIRROR) { AndroidRelease.fromMirror(it, current) })
    }.awaitAll()
    if (asked.none { it.isSuccess }) asked.first()
    else Result.success(AndroidRelease.newestOf(asked.map { it.getOrNull() }))
  }

  fun check(quiet: Boolean = false) {
    if (state is State.Checking || state is State.Downloading || state is State.Installing) return
    if (!quiet) state = State.Checking
    lastLook = System.currentTimeMillis()
    scope.launch {
      val found = look()
      val release = found.getOrNull()
      // An update under way since this look began (the viewer pressed Install) is not overwritten.
      if (state is State.Downloading || state is State.Installing || state is State.NeedsPermission) return@launch
      state = when {
        release != null -> State.Available(release)
        // A failed look in the background says nothing; one the viewer asked for says it is current
        // only when GitHub or the mirror answered.
        quiet -> state
        found.isSuccess -> State.UpToDate
        else -> State.Idle
      }
    }
  }

  /** The release last downloaded, for Try again on the failure card to install again, not only look. */
  private var last: AndroidRelease? = null

  fun retry() {
    showFailure = false
    last?.let(::install) ?: check()
  }

  /** "Later" on the permission card: the update waits in the capsule. */
  fun notNow() {
    (state as? State.NeedsPermission)?.let { state = State.Available(it.release) }
  }

  /**
   * Android's switch for this app, or the nearest screen a TV has: some open the per-app screen,
   * some (Sony, Google TV) only their security list, some nothing but Settings.
   */
  fun openPermission() {
    val tries = buildList {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
      }
      add(Intent(Settings.ACTION_SECURITY_SETTINGS))
      add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in tries) {
      if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) {
        log("opened ${intent.action}${intent.data?.let { " for this app" } ?: ""}")
        return
      }
    }
    log("no settings screen opened")
  }

  /**
   * What each version brought (Settings › What's new): this version's own release, or the newest
   * Android one, whose text carries the sections before it. Null when GitHub did not answer.
   */
  suspend fun notes(): List<app.nama.core.ReleaseNotes.Block>? = withContext(Dispatchers.IO) {
    runCatching {
      http.newCall(Request.Builder().url(feed).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
        if (!r.isSuccessful) error("HTTP ${r.code}")
        app.nama.core.ReleaseNotes.blocks(Json.parse(r.body!!.bytes()), current, AndroidRelease.TAG_PREFIX)
      }
    }.onFailure { Log.w(AppModel.TAG, "release notes: $it") }.getOrNull()
  }

  fun dismiss() {
    state = State.Idle
  }

  fun install(release: AndroidRelease) {
    if (state is State.Downloading || state is State.Installing) return
    state = State.Downloading(release, -1f)
    last = release
    scope.launch {
      val apk = File(dir, "30nama-androidtv.apk")
      val step = withContext(Dispatchers.IO) { prepare(release, apk) }
      if (step != null) return@launch fail(release, step)
      // Without the switch the install still goes to Android, as the official 30tv app does: its
      // window asks for the switch and goes on by itself after Back. Until 1.1.5 this app sent the
      // viewer to Settings itself and went on only if it was still alive on return; a TV short of
      // memory had often closed it, and the update had to be found and chosen again (the owner).
      if (!allowedToInstall()) log("not yet allowed to install apps; Android's window asks")
      commit(release, apk)
    }
  }

  /**
   * Back in the app. From the system's settings: with the switch on, the update goes on; else the
   * card stays. From anywhere else: a quiet look, if the last was [LOOK_GAP] ago.
   */
  fun resumed() {
    val s = state as? State.NeedsPermission
    if (s == null) {
      if (started && System.currentTimeMillis() - lastLook > LOOK_GAP) check(quiet = true)
      return
    }
    if (!allowedToInstall()) return
    log("allowed to install apps")
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
    state = State.Installing(release, asksPermission = !allowedToInstall())
    val step = withContext(Dispatchers.IO) {
      makeRoom(apk.length() + ROOM_MARGIN)?.let { return@withContext it }
      runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
          setAppPackageName(context.packageName)
          // With UPDATE_PACKAGES_WITHOUT_USER_ACTION in the manifest (without it this was ignored until
          // 1.1.5), Android 12 and later replace the app with no window, once it installed itself before.
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
    if (step != null) return fail(release, step)
    // Android's window comes back to Update by itself once the switch is on, with this app never
    // resumed in between: the capsule watches for the switch to say the next step.
    while ((state as? State.Installing)?.asksPermission == true) {
      kotlinx.coroutines.delay(1000)
      if (allowedToInstall()) {
        log("allowed to install apps")
        (state as? State.Installing)?.let { state = it.copy(asksPermission = false) }
      }
    }
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
        if (release != null && (status == PackageInstaller.STATUS_FAILURE_BLOCKED || !allowedToInstall())) {
          // Refused for want of the switch: back to the card that shows where it is, not a failure.
          log("installer status $status ($message): not allowed to install apps")
          state = State.NeedsPermission(release)
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
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
    // Only the installer's own refusal is about the permission; a download or a key that does not
    // match says its own step, not "Android did not allow it".
    state = State.Failed(
        release?.page ?: AndroidRelease.DOWNLOADS, step,
        blocked = step.startsWith("installer") && !allowedToInstall(),
        full = step.startsWith("storage") || NO_ROOM.containsMatchIn(step))
    showFailure = true
  }

  /**
   * Room for Android to take the APK in: its installer copies it once more, and holds back a few
   * percent of the disk besides. The owner's Sony (4 GB, 4% free) had 380 KB it would give, and
   * 1.1.2's update failed there on every try. This app's own pictures and video go first, then
   * Android is asked to empty other apps' caches. The step that failed, or null.
   */
  private fun makeRoom(bytes: Long): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val storage = context.getSystemService(StorageManager::class.java) ?: return null
    val volume = runCatching { storage.getUuidForPath(context.filesDir) }.getOrElse { return null }
    fun room() = runCatching { storage.getAllocatableBytes(volume) }.getOrDefault(Long.MAX_VALUE)
    if (room() >= bytes) return null
    log("room for ${room()} bytes, ${bytes} needed; emptying this app's pictures and video")
    runCatching { clearCaches(context) }
    if (room() >= bytes) return null
    runCatching { storage.allocateBytes(volume, bytes) }
        .onSuccess { log("Android made room"); return null }
        .onFailure { log("Android could not make room: $it") }
    return "storage: room for ${room()} bytes, ${bytes} needed"
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
