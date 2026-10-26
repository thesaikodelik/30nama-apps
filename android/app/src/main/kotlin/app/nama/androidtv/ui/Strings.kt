package app.nama.androidtv.ui

import androidx.compose.runtime.Composable
import app.nama.core.Lang

/**
 * Strings only the Android TV app needs, beside core's `Loc` (generated from L10n.swift, which this
 * app does not edit). Every entry in all three languages; StringsTest checks none is left empty.
 * One that another app comes to need moves to L10n.swift (TASKS.md).
 */
@Suppress("EnumEntryName")
enum class TV(val en: String, val fa: String, val ar: String) {
  pressBackAgain("Press Back again to leave", "برای خروج دوباره «برگشت» را بزنید", "اضغط رجوع مرة أخرى للخروج"),
  persianKeyboard("Persian keyboard", "صفحه‌کلید فارسی", "لوحة مفاتيح فارسية"),
  space("Space", "فاصله", "مسافة"),
  zwnj("Half space", "نیم‌فاصله", "نصف مسافة"),
  delete("Delete", "پاک کردن", "حذف"),
  recentSearches("Recent searches", "جستجوهای اخیر", "عمليات البحث الأخيرة"),
  clear("Clear", "پاک کردن", "مسح"),
  startOver("Start over", "از اول", "من البداية"),
  endsAt("Ends at", "تا ساعت", "ينتهي في"),
  stillWatchingStopped("Autoplay paused after three episodes", "پخش خودکار بعد از سه قسمت ایستاد", "توقف التشغيل التلقائي بعد ثلاث حلقات"),
  readAhead("Read ahead", "خواندن جلوتر", "التحميل المسبق"),
  readAheadHint(
      "How much of what is left of a title is saved ahead on the TV, for a slow or shaky connection.",
      "چقدر از باقی‌ماندهٔ عنوان پیشاپیش روی تلویزیون ذخیره شود، برای اینترنت کند یا ناپایدار.",
      "كم مما تبقى من العنوان يُحفظ مسبقاً على التلفاز، للاتصال البطيء أو غير المستقر.",
  ),
  offline("No internet. Showing what was saved.", "اینترنت وصل نیست. آخرین نسخهٔ ذخیره‌شده نشان داده می‌شود.", "لا يوجد اتصال. يُعرض آخر ما حُفظ."),
  ;

  operator fun invoke(lang: Lang): String =
      when (lang) {
        Lang.fa -> fa
        Lang.ar -> ar
        else -> en
      }
}

/** The string in the interface's language, inside a composable. */
@Composable fun tv(key: TV): String = key(LocalLoc.current.lang)
