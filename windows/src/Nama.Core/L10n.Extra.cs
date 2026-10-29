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
        [Extra.keyWorks] = "يعمل",
        [Extra.keyRejected] = "تقول الخدمة إن هذا المفتاح خاطئ",
        [Extra.keyUnreachable] = "تم الحفظ. تعذر الوصول إلى الخدمة لتجربته",
        [Extra.keyChecking] = "جارٍ تجربة المفتاح…",
        [Extra.yourNameHintHere] = "يظهر في ملفك الشخصي عندما يكون ملفك في 30nama خاصًا. يبقى على هذا الكمبيوتر فقط.",
    };
}
