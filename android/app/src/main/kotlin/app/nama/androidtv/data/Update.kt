package app.nama.androidtv.data

import app.nama.core.Json
import java.io.InputStream
import kotlin.io.encoding.Base64

/** An Android TV release on the project's GitHub page, as the releases API describes it. As MacRelease. */
data class AndroidRelease(val version: String, val page: String, val apk: String, val signature: String) {
  companion object {
    const val TAG_PREFIX = "android-v"
    /** Where the releases are listed. GitHub answers without a key, sixty times an hour per address. */
    const val FEED = "https://api.github.com/repos/thesaikodelik/30nama-apps/releases?per_page=10"
    /**
     * Where a person downloads by hand when updating from inside the app went wrong: the page with
     * every device's file. Not `releases/latest`: Android releases are made with `--latest=false`
     * (AGENTS.md), so that page is the Mac's or Windows'.
     */
    const val DOWNLOADS = "https://github.com/thesaikodelik/30nama-apps/releases/tag/downloads"

    /**
     * The newest finished release in a `releases` answer that is newer than [current]. A release
     * without its APK and that APK's signature is passed over, as if not out yet.
     */
    fun newest(j: Json, current: String): AndroidRelease? =
        j.array.mapNotNull { r ->
          val tag = r["tag_name"].text ?: return@mapNotNull null
          if (r["draft"].bool || r["prerelease"].bool || !tag.startsWith(TAG_PREFIX)) return@mapNotNull null
          fun asset(suffix: String) = r["assets"].array.firstOrNull { (it["name"].text ?: "").endsWith(suffix) }?.get("browser_download_url")?.text
          val apk = asset(".apk") ?: return@mapNotNull null
          val sig = asset(".apk.sig") ?: return@mapNotNull null
          val page = r["html_url"].text ?: return@mapNotNull null
          AndroidRelease(tag.removePrefix(TAG_PREFIX), page, apk, sig)
        }
            .filter { isNewer(it.version, current) }
            .reduceOrNull { a, b -> if (isNewer(b.version, a.version)) b else a }

    /** Compares numbers part by part, so 1.10 is newer than 1.9. */
    fun isNewer(version: String, than: String): Boolean {
      val a = version.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
      val b = than.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
      for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
      }
      return false
    }
  }
}

/**
 * Checks that a downloaded update was signed by the project. Android checks only that an update
 * comes from the same APK key as the app already there; this signature says the project made it.
 * Only the public half of the key is here. The private half stays on the computer that builds the
 * Android releases (android/tools/SignUpdate.java).
 */
object UpdateSignature {
  const val PUBLIC_KEY = "lzC65K2hrh/zLyg0l5m/OQvom1XTJpVLDe5m4cpKWeE="

  /** [signature] is the text of the `.sig` file: the Ed25519 signature of the file, in base64. */
  fun isValid(signature: String, file: InputStream, publicKey: String = PUBLIC_KEY): Boolean {
    val key = runCatching { Base64.Default.decode(publicKey) }.getOrNull() ?: return false
    val sig = runCatching { Base64.Default.decode(signature.trim()) }.getOrNull() ?: return false
    return Ed25519.verify(key, file, sig)
  }
}

/**
 * An update handed to Android's installer, written down first. The version that opens next can then
 * tell whether it arrived: if that is still the old one, the update went wrong after the app let go
 * of it (the viewer said no, or the installer failed), with nothing open to say so.
 */
data class PendingUpdate(val version: String, val page: String) {
  /** The app now running is the update, or a later one. */
  fun arrived(current: String) = !AndroidRelease.isNewer(version, current)

  fun serialize(): String = Json.Obj(mapOf("version" to Json.Str(version), "page" to Json.Str(page))).serialize()

  companion object {
    fun parse(text: String?): PendingUpdate? {
      val j = runCatching { Json.parse(text ?: return null) }.getOrNull() ?: return null
      return PendingUpdate(j["version"].text ?: return null, j["page"].text ?: return null)
    }
  }
}
