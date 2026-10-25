package app.nama.quest.ui.title

import app.nama.core.Lang

/**
 * Title-page strings only the headset needs (the Mac uses a right-click menu where the headset
 * asks for a held trigger). Same pattern as ui/Extra.kt: every entry in all three languages.
 */
enum class TX(val en: String, val fa: String, val ar: String) {
  holdToMarkWatched(
      "Hold to mark as watched",
      "نگه دارید تا دیده‌شده علامت بخورد",
      "اضغط مطولاً لتعليمها كمُشاهَدة",
  ),
  holdToMarkUnwatched(
      "Hold to mark as not watched",
      "نگه دارید تا علامت دیده‌شده برداشته شود",
      "اضغط مطولاً لإزالة علامة المشاهدة",
  ),
  ;

  operator fun invoke(lang: Lang): String =
      when (lang) {
        Lang.fa -> fa
        Lang.ar -> ar
        else -> en
      }
}
