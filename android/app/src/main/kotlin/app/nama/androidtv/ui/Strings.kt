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
  matchFrameRate("Match frame rate", "هماهنگی نرخ تصویر", "مطابقة معدل الإطارات"),
  matchFrameRateHint(
      "Switch the TV to the film's own frame rate, so motion is smooth. Most TVs go black for a moment when it switches.",
      "تلویزیون به نرخ تصویر خود فیلم برود تا حرکت نرم باشد. بیشتر تلویزیون‌ها هنگام این کار یک لحظه سیاه می‌شوند.",
      "يتحول التلفاز إلى معدل إطارات الفيلم نفسه لتكون الحركة سلسة. تصبح معظم أجهزة التلفاز سوداء للحظة عند التبديل.",
  ),
  qualityHint(
      "The highest fixed quality, or the adaptive stream that follows the connection's speed.",
      "بالاترین کیفیت ثابت، یا جریان تطبیقی که با سرعت اینترنت بالا و پایین می‌رود.",
      "أعلى جودة ثابتة، أو البث التكيفي الذي يتبع سرعة الاتصال.",
  ),
  audioHint(
      "Stereo plays on the TV's own speakers. 5.1 and the original mix are for a sound system.",
      "استریو روی بلندگوی خود تلویزیون پخش می‌شود. ۵٫۱ و صدای اصلی برای سیستم صوتی است.",
      "الستيريو يعمل على سماعات التلفاز. 5.1 والصوت الأصلي لنظام صوتي.",
  ),
  autoplayHint("The next episode starts by itself when one ends.", "بعد از تمام شدن هر قسمت، قسمت بعد خودش شروع شود.", "تبدأ الحلقة التالية تلقائياً عند انتهاء الحلقة."),
  autoSkipHint("Jump over the opening titles and the recap without being asked.", "تیتراژ اول و خلاصهٔ قسمت قبل بدون پرسیدن رد شود.", "تخطي الشارة والملخص دون سؤال."),
  motionHint(
      "No drifting backgrounds or turning banners. On a TV with little memory this is always so.",
      "بدون پس‌زمینهٔ متحرک و بنرهای چرخان. روی تلویزیون‌های با حافظهٔ کم همیشه همین‌طور است.",
      "بلا خلفيات متحركة أو لافتات دوارة. على أجهزة التلفاز ذات الذاكرة القليلة يكون الأمر دائماً كذلك.",
  ),
  languageHint(
      "The app's language. Titles and stories come in it too, where 30nama has them.",
      "زبان برنامه. نام و داستان عنوان‌ها هم، هر جا ۳۰نما داشته باشد، به همین زبان می‌آید.",
      "لغة التطبيق. تأتي العناوين والقصص بها أيضاً حيث تتوفر في ۳۰نما.",
  ),
  subtitlesHint(
      "How subtitles look. The sample below is drawn at its real size.",
      "ظاهر زیرنویس. نمونهٔ پایین در اندازهٔ واقعی کشیده شده است.",
      "مظهر الترجمة. العينة أدناه مرسومة بحجمها الحقيقي.",
  ),
  keyFromPhone(
      "Or scan this with a phone on the same Wi-Fi, and paste the key there.",
      "یا این را با گوشی‌ای که به همین وای‌فای وصل است اسکن کنید و کلید را آن‌جا بچسبانید.",
      "أو امسح هذا بهاتف على نفس شبكة Wi-Fi والصق المفتاح هناك.",
  ),
  keyFromSite("Scan this to make a key on your phone.", "برای ساختن کلید، این را با گوشی اسکن کنید.", "امسح هذا لإنشاء مفتاح على هاتفك."),
  keyWorks("Works", "کار می‌کند", "يعمل"),
  keyRejected("The service says this key is wrong", "سرویس می‌گوید این کلید درست نیست", "تقول الخدمة إن هذا المفتاح خاطئ"),
  keyUnreachable("Saved. The service could not be reached to try it", "ذخیره شد. سرویس برای امتحان در دسترس نبود", "تم الحفظ. تعذر الوصول إلى الخدمة لتجربته"),
  keyChecking("Trying the key…", "در حال امتحان کلید…", "جارٍ تجربة المفتاح…"),
  keyNotSet("Not set", "تنظیم نشده", "غير مضبوط"),
  sendToTv("Send to TV", "بفرست به تلویزیون", "أرسل إلى التلفاز"),
  keySent("Sent. You can close this page.", "فرستاده شد. این صفحه را می‌توانید ببندید.", "تم الإرسال. يمكنك إغلاق هذه الصفحة."),
  keyLooksWrong("That doesn't look like a key.", "این شبیه کلید نیست.", "هذا لا يبدو مفتاحاً."),
  pasteKey("Paste the key here", "کلید را این‌جا بچسبانید", "الصق المفتاح هنا"),
  clearCaches("Clear saved pictures and video", "پاک کردن تصویرها و ویدئوهای ذخیره‌شده", "مسح الصور والفيديو المحفوظة"),
  cleared("Cleared", "پاک شد", "تم المسح"),
  licenses("Licences", "مجوزها", "التراخيص"),
  sectionApp("App", "برنامه", "التطبيق"),
  save("Save", "ذخیره", "حفظ"),
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
