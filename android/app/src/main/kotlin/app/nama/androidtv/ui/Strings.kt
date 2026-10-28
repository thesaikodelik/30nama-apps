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
  motionOffBySystem(
      "Animations are off for the whole TV in its own settings, so the app keeps still whatever this says.",
      "انیمیشن‌ها در تنظیمات خود تلویزیون خاموش‌اند، پس برنامه هر چه این‌جا باشد بی‌حرکت می‌ماند.",
      "الرسوم المتحركة متوقفة للتلفاز كله في إعداداته، لذا يبقى التطبيق ثابتاً مهما كان هذا الخيار.",
  ),
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
  megabytes("MB", "مگابایت", "ميغابايت"),
  cleared("Cleared", "پاک شد", "تم المسح"),
  licenses("Licences", "مجوزها", "التراخيص"),
  sectionApp("App", "برنامه", "التطبيق"),
  save("Save", "ذخیره", "حفظ"),
  machineTranslation("machine translation", "ترجمهٔ ماشینی", "ترجمة آلية"),
  translationStopped(
      "Translation paused: Gemini's free requests for today are used up, or it could not be reached. The rest stays in English.",
      "ترجمه ایستاد: درخواست‌های رایگان امروز Gemini تمام شده یا در دسترس نیست. بقیه انگلیسی می‌ماند.",
      "توقفت الترجمة: نفدت طلبات Gemini المجانية لليوم أو تعذر الوصول إليه. يبقى الباقي بالإنجليزية.",
  ),
  geminiKey("Gemini key", "کلید Gemini", "مفتاح Gemini"),
  renewHint(
      "Scan the code with your phone to renew on 30nama's website.",
      "کد را با گوشی اسکن کنید تا اشتراک را در سایت ۳۰نما تمدید کنید.",
      "امسح الرمز بهاتفك للتجديد على موقع 30nama.",
  ),
  reportHint(
      "Scan the code with your phone to write to us on the project's GitHub page. This app's version, the TV's Android and the language are filled in; nothing about your account goes with it.",
      "کد را با گوشی اسکن کنید تا در صفحهٔ گیت‌هاب پروژه برایمان بنویسید. نسخهٔ برنامه، اندروید تلویزیون و زبان خودشان نوشته می‌شوند؛ چیزی از حساب شما فرستاده نمی‌شود.",
      "امسح الرمز بهاتفك لتكتب لنا في صفحة المشروع على GitHub. يُملأ إصدار التطبيق وأندرويد التلفاز واللغة؛ ولا يُرسل شيء عن حسابك.",
  ),
  imdbHint("Scan the code with your phone to open this page on IMDb.", "کد را با گوشی اسکن کنید تا این صفحه در IMDb باز شود.", "امسح الرمز بهاتفك لفتح هذه الصفحة على IMDb."),
  yourNameHint(
      "Shown on your profile, and said by the line from a film, when your 30nama profile is private. In English letters. Stays on this TV.",
      "وقتی پروفایل شما در ۳۰نما خصوصی است، این نام در پروفایل می‌آید و جملهٔ فیلم هم شما را با آن صدا می‌زند. با حروف انگلیسی. فقط روی همین تلویزیون می‌ماند.",
      "يظهر في ملفك الشخصي، وتناديك به جملة الفيلم، عندما يكون ملفك في 30nama خاصًا. بالأحرف الإنجليزية. يبقى على هذا التلفاز فقط.",
  ),
  typeName("Your name, in English letters", "نام شما، با حروف انگلیسی", "اسمك، بالأحرف الإنجليزية"),
  geminiKeyHint(
      "Optional. Made free in Google AI Studio with your Google account. Translates the English subtitle into Persian when a title has no Persian one anywhere. Google's free tier may use what is sent to improve its models.",
      "اختیاری. رایگان در Google AI Studio با حساب گوگل خودتان ساخته می‌شود. وقتی عنوانی هیچ‌جا زیرنویس فارسی ندارد، زیرنویس انگلیسی را به فارسی ترجمه می‌کند. گوگل در نسخهٔ رایگان ممکن است متن فرستاده‌شده را برای بهتر کردن مدل‌هایش به کار ببرد.",
      "اختياري. يُنشأ مجاناً في Google AI Studio بحساب Google الخاص بك. يترجم الترجمة الإنجليزية إلى الفارسية حين لا توجد ترجمة فارسية لعنوان ما. قد تستخدم Google في الفئة المجانية ما يُرسل لتحسين نماذجها.",
  ),
  retry("Try again", "دوباره", "إعادة المحاولة"),
  allowInstalls("Allow 30nama to install apps", "اجازهٔ نصب برنامه به ۳۰نما", "اسمح لـ۳۰نما بتثبيت التطبيقات"),
  installing("Installing", "در حال نصب", "جارٍ التثبيت"),
  updatedTo("Updated to", "به‌روز شد به نسخهٔ", "تم التحديث إلى"),
  newVersionInSettings("is out. Settings › About", "آمده است. تنظیمات › درباره", "متوفر. الإعدادات › حول"),
  updateHint(
      "New versions come from the project's GitHub page. Each is checked against the project's signature before it is installed.",
      "نسخه‌های تازه از صفحهٔ گیت‌هاب پروژه می‌آیند. هر کدام پیش از نصب با امضای پروژه سنجیده می‌شود.",
      "تأتي الإصدارات الجديدة من صفحة المشروع على GitHub. يُتحقق من كل منها بتوقيع المشروع قبل تثبيته.",
  ),
  installHint(
      "Android asks to confirm. 30nama then closes for the update; if it does not open again by itself, open it from the TV's home screen.",
      "اندروید تأیید می‌خواهد. بعد ۳۰نما برای به‌روزرسانی بسته می‌شود؛ اگر خودش دوباره باز نشد، از صفحهٔ اصلی تلویزیون بازش کنید.",
      "يطلب أندرويد التأكيد. ثم يُغلق ۳۰نما للتحديث؛ إن لم يُفتح من تلقاء نفسه، افتحه من الشاشة الرئيسية للتلفاز.",
  ),
  updateFailedHint(
      "Scan this with a phone to download the new version by hand. Each step of the update is written in update.log, in the app's files.",
      "این را با گوشی اسکن کنید و نسخهٔ تازه را دستی دانلود کنید. هر قدم به‌روزرسانی در فایل update.log، میان فایل‌های برنامه، نوشته شده است.",
      "امسح هذا بهاتف لتنزيل الإصدار الجديد يدوياً. كل خطوة من التحديث مكتوبة في update.log ضمن ملفات التطبيق.",
  ),
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
