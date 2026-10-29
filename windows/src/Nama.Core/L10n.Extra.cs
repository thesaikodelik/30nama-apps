namespace Nama.Core;

/// <summary>
/// Strings the Windows app has before the Mac app does. The shared tables are written in
/// L10n.swift, which the Windows side does not edit; these wait here, in all three languages,
/// until the Mac side takes them over (TASKS.md has the item), and then move into
/// <see cref="S"/> with the same names.
/// </summary>
#pragma warning disable IDE1006
public enum Extra
{
    showInFolder, keepInTray, keepInTrayHint, openApp, quit,
    // The Mac's keySaved and yourNameHint say "this Mac"; on Windows it is this computer.
    keySavedHere, yourNameHintHere,
    // From the Android app: a key is saved, then tried.
    keyWorks, keyRejected, keyUnreachable, keyChecking,
    // From the Android app: the download file itself, played straight.
    qualityMax,
    stillWatchingStopped, startOver, endsAt, offline,
    freshSeries, popularFilms, partOf,
    geminiKey, geminiKeyHint, machineTranslation, translationStopped,
    dataSaver, dataSaverHint, clearCaches, clear, megabytes, cleared,
}
#pragma warning restore IDE1006

public sealed partial class Loc
{
    public string this[Extra key] =>
        ((Lang == Lang.Fa ? ExtraFa : Lang == Lang.Ar ? ExtraAr : ExtraEn).GetValueOrDefault(key) ?? ExtraEn.GetValueOrDefault(key)) ?? key.ToString();

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraEn = new Dictionary<Extra, string>
    {
        [Extra.keepInTray] = "Keep running in the notification area",
        [Extra.keepInTrayHint] = "Closing the window leaves the app beside the clock, so downloads go on.",
        [Extra.openApp] = "Open 30nama",
        [Extra.quit] = "Quit",
        [Extra.showInFolder] = "Show in folder",
        [Extra.keySavedHere] = "Saved on this computer",
        [Extra.qualityMax] = "Max, the original file",
        [Extra.stillWatchingStopped] = "Autoplay paused after three episodes",
        [Extra.startOver] = "Start over",
        [Extra.endsAt] = "Ends at",
        [Extra.offline] = "No internet. Showing what was saved.",
        [Extra.freshSeries] = "New and returning series",
        [Extra.popularFilms] = "Popular films now",
        [Extra.partOf] = "Part {at} of {of}",
        [Extra.geminiKey] = "Gemini key",
        [Extra.geminiKeyHint] = "Optional. Made free in Google AI Studio with your Google account. Translates the English subtitle into Persian when a title has no Persian one anywhere. Google's free tier may use what is sent to improve its models.",
        [Extra.machineTranslation] = "machine translation",
        [Extra.translationStopped] = "Translation paused: Gemini's free requests for today are used up, or it could not be reached. The rest stays in English.",
        [Extra.dataSaver] = "Data saver",
        [Extra.dataSaverHint] = "For metered internet or a VPN with a data cap: no preview shows while seeking, and no trailer plays behind a title's page.",
        [Extra.clearCaches] = "Saved trailers, TMDB answers and translations",
        [Extra.clear] = "Clear",
        [Extra.megabytes] = "MB",
        [Extra.cleared] = "Cleared",
        [Extra.keyWorks] = "Works",
        [Extra.keyRejected] = "The service says this key is wrong",
        [Extra.keyUnreachable] = "Saved. The service could not be reached to try it",
        [Extra.keyChecking] = "Trying the key…",
        [Extra.yourNameHintHere] = "Shown on your profile when your 30nama profile is private. Stays on this computer.",
    };

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraFa = new Dictionary<Extra, string>
    {
        [Extra.keepInTray] = "ماندن در کنار ساعت ویندوز",
        [Extra.keepInTrayHint] = "با بستن پنجره، برنامه کنار ساعت می‌ماند تا دانلودها ادامه پیدا کنند.",
        [Extra.openApp] = "باز کردن ۳۰نما",
        [Extra.quit] = "خروج",
        [Extra.showInFolder] = "نمایش در پوشه",
        [Extra.keySavedHere] = "روی همین کامپیوتر ذخیره شد",
        [Extra.qualityMax] = "Max، فایل اصلی",
        [Extra.stillWatchingStopped] = "پخش خودکار بعد از سه قسمت ایستاد",
        [Extra.startOver] = "از اول",
        [Extra.endsAt] = "تا ساعت",
        [Extra.offline] = "اینترنت وصل نیست. آخرین نسخهٔ ذخیره‌شده نشان داده می‌شود.",
        [Extra.freshSeries] = "سریال‌های تازه و برگشته",
        [Extra.popularFilms] = "فیلم‌های پرطرفدار این روزها",
        [Extra.partOf] = "قسمت {at} از {of}",
        [Extra.geminiKey] = "کلید Gemini",
        [Extra.geminiKeyHint] = "اختیاری. رایگان در Google AI Studio با حساب گوگل خودتان ساخته می‌شود. وقتی عنوانی هیچ‌جا زیرنویس فارسی ندارد، زیرنویس انگلیسی را به فارسی ترجمه می‌کند. گوگل در نسخهٔ رایگان ممکن است متن فرستاده‌شده را برای بهتر کردن مدل‌هایش به کار ببرد.",
        [Extra.machineTranslation] = "ترجمهٔ ماشینی",
        [Extra.translationStopped] = "ترجمه ایستاد: درخواست‌های رایگان امروز Gemini تمام شده یا در دسترس نیست. بقیه انگلیسی می‌ماند.",
        [Extra.dataSaver] = "صرفه‌جویی در اینترنت",
        [Extra.dataSaverHint] = "برای اینترنت حجمی یا VPN با حجم محدود: هنگام جلو و عقب بردن پیش‌نمایش نمی‌آید و پشت صفحهٔ عنوان تریلری پخش نمی‌شود.",
        [Extra.clearCaches] = "تریلرها، پاسخ‌های TMDB و ترجمه‌های ذخیره‌شده",
        [Extra.clear] = "پاک کردن",
        [Extra.megabytes] = "مگابایت",
        [Extra.cleared] = "پاک شد",
        [Extra.keyWorks] = "کار می‌کند",
        [Extra.keyRejected] = "سرویس می‌گوید این کلید درست نیست",
        [Extra.keyUnreachable] = "ذخیره شد. سرویس برای امتحان در دسترس نبود",
        [Extra.keyChecking] = "در حال امتحان کلید…",
        [Extra.yourNameHintHere] = "وقتی پروفایل شما در ۳۰نما خصوصی است، این نام در پروفایل می‌آید. فقط روی همین کامپیوتر می‌ماند.",
    };

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraAr = new Dictionary<Extra, string>
    {
        [Extra.keepInTray] = "البقاء في منطقة الإشعارات",
        [Extra.keepInTrayHint] = "إغلاق النافذة يُبقي التطبيق بجانب الساعة لتستمر التنزيلات.",
        [Extra.openApp] = "فتح 30nama",
        [Extra.quit] = "إنهاء",
        [Extra.showInFolder] = "إظهار في المجلد",
        [Extra.keySavedHere] = "حُفظ على هذا الكمبيوتر",
        [Extra.qualityMax] = "Max، الملف الأصلي",
        [Extra.stillWatchingStopped] = "توقف التشغيل التلقائي بعد ثلاث حلقات",
        [Extra.startOver] = "من البداية",
        [Extra.endsAt] = "ينتهي في",
        [Extra.offline] = "لا يوجد اتصال. يُعرض آخر ما حُفظ.",
        [Extra.freshSeries] = "مسلسلات جديدة وعائدة",
        [Extra.popularFilms] = "أفلام رائجة الآن",
        [Extra.partOf] = "الجزء {at} من {of}",
        [Extra.geminiKey] = "مفتاح Gemini",
        [Extra.geminiKeyHint] = "اختياري. يُنشأ مجاناً في Google AI Studio بحساب Google الخاص بك. يترجم الترجمة الإنجليزية إلى الفارسية حين لا توجد ترجمة فارسية لعنوان ما. قد تستخدم Google في الفئة المجانية ما يُرسل لتحسين نماذجها.",
        [Extra.machineTranslation] = "ترجمة آلية",
        [Extra.translationStopped] = "توقفت الترجمة: نفدت طلبات Gemini المجانية لليوم أو تعذر الوصول إليه. يبقى الباقي بالإنجليزية.",
        [Extra.dataSaver] = "توفير البيانات",
        [Extra.dataSaverHint] = "للإنترنت المحدود أو VPN بحجم محدود: لا تظهر معاينة أثناء التقديم والترجيع، ولا يُعرض إعلان خلف صفحة العنوان.",
        [Extra.clearCaches] = "الإعلانات وردود TMDB والترجمات المحفوظة",
        [Extra.clear] = "مسح",
        [Extra.megabytes] = "ميغابايت",
        [Extra.cleared] = "تم المسح",
        [Extra.keyWorks] = "يعمل",
        [Extra.keyRejected] = "تقول الخدمة إن هذا المفتاح خاطئ",
        [Extra.keyUnreachable] = "تم الحفظ. تعذر الوصول إلى الخدمة لتجربته",
        [Extra.keyChecking] = "جارٍ تجربة المفتاح…",
        [Extra.yourNameHintHere] = "يظهر في ملفك الشخصي عندما يكون ملفك في 30nama خاصًا. يبقى على هذا الكمبيوتر فقط.",
    };
}
