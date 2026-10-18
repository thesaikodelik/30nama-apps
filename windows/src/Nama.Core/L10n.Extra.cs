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
    };

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraFa = new Dictionary<Extra, string>
    {
        [Extra.keepInTray] = "ماندن در کنار ساعت ویندوز",
        [Extra.keepInTrayHint] = "با بستن پنجره، برنامه کنار ساعت می‌ماند تا دانلودها ادامه پیدا کنند.",
        [Extra.openApp] = "باز کردن ۳۰نما",
        [Extra.quit] = "خروج",
        [Extra.showInFolder] = "نمایش در پوشه",
    };

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraAr = new Dictionary<Extra, string>
    {
        [Extra.keepInTray] = "البقاء في منطقة الإشعارات",
        [Extra.keepInTrayHint] = "إغلاق النافذة يُبقي التطبيق بجانب الساعة لتستمر التنزيلات.",
        [Extra.openApp] = "فتح 30nama",
        [Extra.quit] = "إنهاء",
        [Extra.showInFolder] = "إظهار في المجلد",
    };
}
