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
  dataSaver("Data saver", "صرفه‌جویی در اینترنت", "توفير البيانات"),
  dataSaverHint(
      "For metered internet or a VPN with a data cap. The next titles' pictures and logos are not fetched before you reach them, episodes take no frames from their own video, no preview shows while seeking, and no clip plays behind a title's page. Read ahead under Playback is set on its own.",
      "برای اینترنت حجمی یا VPN با حجم محدود. تصویر و لوگوی عنوان‌های بعدی پیش از رسیدن به آن‌ها گرفته نمی‌شود، قسمت‌ها از ویدئوی خودشان عکس نمی‌گیرند، هنگام جلو و عقب بردن پیش‌نمایش نمی‌آید و پشت صفحهٔ عنوان فیلمی پخش نمی‌شود. «خواندن جلوتر» در بخش پخش جدا تنظیم می‌شود.",
      "للإنترنت المحدود أو VPN بحجم محدود. لا تُجلب صور العناوين التالية وشعاراتها قبل الوصول إليها، ولا تأخذ الحلقات صوراً من الفيديو نفسه، ولا تظهر معاينة أثناء التقديم والترجيع، ولا يُعرض مقطع خلف صفحة العنوان. يُضبط «التحميل المسبق» في قسم التشغيل بشكل منفصل.",
  ),
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
  qualityMax("Max, the original file", "Max، فایل اصلی", "Max، الملف الأصلي"),
  qualityMaxHint(
      "Max plays the download file itself, often 4K and HDR, where the TV can play it: files with DTS or TrueHD sound are passed over. It needs a fast line. If a file will not play, or plays without sound, the stream takes over at the same moment.",
      "«Max» خود فایل دانلود را پخش می‌کند، که اغلب 4K و HDR است، اگر تلویزیون بتواند پخشش کند: فایل‌های با صدای DTS یا TrueHD کنار گذاشته می‌شوند. اینترنت پرسرعت می‌خواهد. اگر فایلی پخش نشد یا بی‌صدا بود، همان لحظه استریم معمولی جایش را می‌گیرد.",
      "يشغّل «Max» ملف التنزيل نفسه، وغالباً بدقة 4K وHDR، إن استطاع التلفاز تشغيله: تُستبعد الملفات ذات صوت DTS أو TrueHD. يحتاج إلى اتصال سريع. إن لم يعمل ملف أو عمل بلا صوت، يحل البث العادي محله في اللحظة نفسها.",
  ),
  subtitlePosition("Subtitle position", "جای زیرنویس", "موضع الترجمة"),
  positionBottom("Bottom", "پایین", "أسفل"),
  positionMiddle("Middle", "وسط", "الوسط"),
  positionTop("Top", "بالا", "أعلى"),
  subtitlePositionHint(
      "Where subtitles sit on the picture. Bottom is usual; Top or Middle keep them off a film's own text at its foot.",
      "زیرنویس کجای تصویر بنشیند. معمولاً پایین؛ بالا یا وسط آن را از روی نوشته‌های خود فیلم در پایین تصویر دور نگه می‌دارد.",
      "أين تظهر الترجمة على الصورة. عادةً في الأسفل؛ الأعلى أو الوسط يبعدانها عن نصوص الفيلم نفسه في أسفل الصورة.",
  ),
  audioAndSubtitles("Audio and subtitles", "صدا و زیرنویس", "الصوت والترجمة"),
  qualityCapHint(
      "1080p, 720p and 480p are for a slow line: the film starts in the highest quality up to that, and never climbs above it on its own.",
      "کیفیت‌های 1080p و 720p و 480p برای اینترنت کند است: فیلم با بالاترین کیفیت تا همان اندازه شروع می‌شود و خودش از آن بالاتر نمی‌رود.",
      "الجودات 1080p و720p و480p للاتصال البطيء: يبدأ الفيلم بأعلى جودة حتى ذلك الحد، ولا يتجاوزه من تلقاء نفسه.",
  ),
  freshSeries("New and returning series", "سریال‌های تازه و برگشته", "مسلسلات جديدة وعائدة"),
  popularFilms("Popular films now", "فیلم‌های پرطرفدار این روزها", "أفلام رائجة الآن"),
  moreLikeThis("More like this", "شبیه این", "أعمال مشابهة"),
  partOf("Part {at} of {of}", "قسمت {at} از {of}", "الجزء {at} من {of}"),
  aroundHint(
      "Scene glow: where the film leaves room on the screen, the scene's own colours light it softly, and black bars burned into the file itself are cut away. Taken from a small copy of the film every few seconds, only with motion effects on. Black bars: the room stays black, as in a cinema.",
      "نور صحنه: جایی که فیلم روی صفحه خالی می‌گذارد، رنگ‌های خود صحنه آرام روشنش می‌کنند، و نوار سیاهی که داخل خود فایل است بریده می‌شود. هر چند ثانیه از یک نسخهٔ کوچک فیلم برداشته می‌شود، فقط وقتی جلوه‌های حرکتی روشن‌اند. نوار سیاه: جای خالی سیاه می‌ماند، مثل سینما.",
      "ضوء المشهد: حيث يترك الفيلم مساحة على الشاشة، تضيئها ألوان المشهد نفسه بهدوء، وتُقص الأشرطة السوداء المحروقة في الملف نفسه. يؤخذ من نسخة صغيرة من الفيلم كل بضع ثوانٍ، فقط مع تشغيل تأثيرات الحركة. الأشرطة السوداء: تبقى المساحة سوداء كما في السينما.",
  ),
  effectsAuto("Automatic, for this TV", "خودکار، برای این تلویزیون", "تلقائي، لهذا التلفاز"),
  effectsLight("Light, easier on the TV", "سبک، راحت‌تر برای تلویزیون", "خفيف، أسهل على التلفاز"),
  effectsHint(
      "The motion that never stops: the backdrop drifting behind a page, the billboard turning by itself, cards leaning, the ring and light turning round what is in focus. Automatic gives it to a TV with the memory for it (2.5 GB or more) and spares a weaker one. Full if a smaller TV handles it well; Light if a TV slows down.",
      "حرکت‌هایی که تمام نمی‌شوند: پس‌زمینه‌ای که پشت صفحه آرام جابه‌جا می‌شود، بیلبوردی که خودش ورق می‌خورد، کارت‌هایی که کج می‌شوند، و حلقه و نوری که دور چیزِ در فوکوس می‌چرخد. «خودکار» آن‌ها را به تلویزیونی می‌دهد که حافظه‌اش را دارد (۲٫۵ گیگ یا بیشتر) و تلویزیون ضعیف‌تر را معاف می‌کند. اگر تلویزیون کوچک‌تری خوب از پسش برمی‌آید «کامل»، و اگر تلویزیونی کند می‌شود «سبک».",
      "الحركة التي لا تتوقف: الخلفية التي تنساب خلف الصفحة، واللوحة التي تتقلب وحدها، والبطاقات المائلة، والحلقة والضوء حول ما عليه التركيز. «تلقائي» يمنحها لتلفاز لديه الذاكرة الكافية (2.5 غيغابايت أو أكثر) ويعفي الأضعف. «كامل» إن كان تلفاز أصغر يتحملها جيداً، و«خفيف» إن تباطأ التلفاز.",
  ),
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
  installsNotAllowed(
      "Android did not let 30nama install it. Turn 30nama on under Unknown sources, in the TV's Settings › Apps › Security & restrictions (on Google TV: Settings › Privacy › Security & restrictions), then try again. Or scan this with a phone to download the new version by hand.",
      "اندروید اجازهٔ نصب به ۳۰نما نداد. در تنظیمات تلویزیون › برنامه‌ها › امنیت و محدودیت‌ها › منابع ناشناس (در Google TV: تنظیمات › حریم خصوصی › امنیت و محدودیت‌ها)، ۳۰نما را روشن کنید و دوباره بزنید. یا این را با گوشی اسکن کنید و نسخهٔ تازه را دستی دانلود کنید.",
      "لم يسمح أندرويد لـ۳۰نما بتثبيته. فعّل ۳۰نما ضمن المصادر غير المعروفة، في إعدادات التلفاز › التطبيقات › الأمان والقيود (في Google TV: الإعدادات › الخصوصية › الأمان والقيود)، ثم أعد المحاولة. أو امسح هذا بهاتف لتنزيل الإصدار الجديد يدوياً.",
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
