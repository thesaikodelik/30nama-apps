package app.nama.quest.ui

import app.nama.core.Lang

/**
 * Strings only the headset needs, beside the shared table in app.nama.core.L10n (the Windows app
 * keeps its own extras the same way). Every entry carries all three languages, so none can be
 * left untranslated.
 */
enum class X(val en: String, val fa: String, val ar: String) {
  openOnOtherDevice(
      "On your phone or computer, open",
      "روی گوشی یا کامپیوتر باز کنید:",
      "على هاتفك أو حاسوبك افتح",
  ),
  enterThisCode("Enter this code", "این کد را وارد کنید", "أدخل هذا الرمز"),
  waitingForConfirm("Waiting for you to confirm…", "منتظر تأیید شما…", "في انتظار تأكيدك…"),
  codeRefreshes("A new code appears every few minutes.", "کد هر چند دقیقه عوض می‌شود.", "يتغير الرمز كل بضع دقائق."),
  noTypingNeeded("Nothing to type in the headset.", "در هدست چیزی تایپ نمی‌شود.", "لا حاجة للكتابة في النظارة."),
  demoBadge("Demo", "نمایشی", "عرض تجريبي"),
  pointToBrowse("Point at a poster to see it on the screen", "روی یک پوستر بروید تا روی پرده ببینیدش", "أشر إلى ملصق لتراه على الشاشة"),
  nothingLoaded("The catalogue didn't load.", "فهرست بارگذاری نشد.", "لم يتم تحميل القائمة."),
  storylineAndCrew("Story", "داستان", "القصة"),
  runtime("Runtime", "مدت", "المدة"),
  ;

  operator fun invoke(lang: Lang): String =
      when (lang) {
        Lang.fa -> fa
        Lang.ar -> ar
        else -> en
      }
}
