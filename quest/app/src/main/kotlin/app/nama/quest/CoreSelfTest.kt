package app.nama.quest

import android.util.Log
import app.nama.core.Aparat
import app.nama.core.Episode
import app.nama.core.SiteText
import app.nama.core.TitleMatch

/**
 * Debug builds run app.nama.core's text handling once on the headset itself. Its unit tests run
 * on the desktop JVM, whose regex engine is not Android's ICU: a pattern can pass every test and
 * still fail here (the "(?U)" flag did, on opening any title).
 */
object CoreSelfTest {
  fun run() {
    val checks =
        listOf<Pair<String, () -> Any?>>(
            "Aparat.trailer" to { Aparat.trailer(emptyList(), "Northern Shadows (2023)", 2023) },
            "Aparat.words" to { Aparat.words("تریلر فیلم Northern Shadows 2023") },
            "Episode.label" to { Episode.label("11,12", 1) },
            "SiteText.tidyPersian" to { SiteText.tidyPersian("او می رود ، \"خانه ی ما\" .") },
            "TitleMatch.stripYear" to { TitleMatch.stripYear("Animals (2026)") },
            "TitleMatch.normalize" to { TitleMatch.normalize("Amélie & Co.") },
        )
    var failed = 0
    for ((name, check) in checks) {
      try {
        check()
      } catch (t: Throwable) {
        failed++
        Log.e("Nama", "self-test $name failed", t)
      }
    }
    Log.i("Nama", "self-test: ${checks.size - failed}/${checks.size} passed")
  }
}
