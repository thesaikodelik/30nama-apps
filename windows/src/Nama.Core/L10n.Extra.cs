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
    // Windows' own, until L10n.swift has them.
    downloadWith, sentTo, anotherProgram,
    newTitlesAlert, newTitlesHint, newOn30nama, andMore,
    fillPicture, stretchPicture,
}
#pragma warning restore IDE1006

public sealed partial class Loc
{
    public string this[Extra key] =>
        ((Lang == Lang.Fa ? ExtraFa : Lang == Lang.Ar ? ExtraAr : ExtraEn).GetValueOrDefault(key) ?? ExtraEn.GetValueOrDefault(key)) ?? key.ToString();

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraEn = new Dictionary<Extra, string>
    {
        [Extra.fillPicture] = "Fill",
        [Extra.stretchPicture] = "Stretch",
        [Extra.newTitlesAlert] = "New title alerts",
        [Extra.newTitlesHint] = "A Windows notification when films or series are added to 30nama, looked for every few hours.",
        [Extra.newOn30nama] = "New on 30nama",
        [Extra.andMore] = "and {n} more",
        [Extra.downloadWith] = "Download with",
        [Extra.sentTo] = "Sent to",
        [Extra.anotherProgram] = "Another program…",
        [Extra.keepInTray] = "Keep running in the notification area",
        [Extra.keepInTrayHint] = "Closing the window leaves the app beside the clock, so downloads go on.",
        [Extra.openApp] = "Open 30nama",
        [Extra.quit] = "Quit",
        [Extra.showInFolder] = "Show in folder",
        [Extra.keySavedHere] = "Saved on this computer",
        
        
        
        
        
        
        
        
        
        
        
        
        [Extra.yourNameHintHere] = "Shown on your profile when your 30nama profile is private. Stays on this computer.",
    };

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraFa = new Dictionary<Extra, string>
    {
        [Extra.fillPicture] = "پر کردن صفحه",
        [Extra.stretchPicture] = "کشیدن",
        [Extra.newTitlesAlert] = "اعلان عنوان‌های تازه",
        [Extra.newTitlesHint] = "وقتی فیلم یا سریالی به ۳۰نما اضافه می‌شود، با اعلان ویندوز خبر می‌دهد. هر چند ساعت یک بار نگاه می‌کند.",
        [Extra.newOn30nama] = "تازه در ۳۰نما",
        [Extra.andMore] = "و {n} عنوان دیگر",
        [Extra.downloadWith] = "دانلود با",
        [Extra.sentTo] = "فرستاده شد به",
        [Extra.anotherProgram] = "برنامهٔ دیگر…",
        [Extra.keepInTray] = "ماندن در کنار ساعت ویندوز",
        [Extra.keepInTrayHint] = "با بستن پنجره، برنامه کنار ساعت می‌ماند تا دانلودها ادامه پیدا کنند.",
        [Extra.openApp] = "باز کردن ۳۰نما",
        [Extra.quit] = "خروج",
        [Extra.showInFolder] = "نمایش در پوشه",
        [Extra.keySavedHere] = "روی همین کامپیوتر ذخیره شد",
        
        
        
        
        
        
        
        
        
        
        
        
        [Extra.yourNameHintHere] = "وقتی پروفایل شما در ۳۰نما خصوصی است، این نام در پروفایل می‌آید. فقط روی همین کامپیوتر می‌ماند.",
    };

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraAr = new Dictionary<Extra, string>
    {
        [Extra.fillPicture] = "ملء الشاشة",
        [Extra.stretchPicture] = "تمديد",
        [Extra.newTitlesAlert] = "تنبيهات العناوين الجديدة",
        [Extra.newTitlesHint] = "إشعار من ويندوز عند إضافة أفلام أو مسلسلات إلى 30nama، يُتحقق منه كل بضع ساعات.",
        [Extra.newOn30nama] = "جديد على 30nama",
        [Extra.andMore] = "و{n} أخرى",
        [Extra.downloadWith] = "التنزيل عبر",
        [Extra.sentTo] = "أُرسل إلى",
        [Extra.anotherProgram] = "برنامج آخر…",
        [Extra.keepInTray] = "البقاء في منطقة الإشعارات",
        [Extra.keepInTrayHint] = "إغلاق النافذة يُبقي التطبيق بجانب الساعة لتستمر التنزيلات.",
        [Extra.openApp] = "فتح 30nama",
        [Extra.quit] = "إنهاء",
        [Extra.showInFolder] = "إظهار في المجلد",
        [Extra.keySavedHere] = "حُفظ على هذا الكمبيوتر",
        
        
        
        
        
        
        
        
        
        
        
        
        [Extra.yourNameHintHere] = "يظهر في ملفك الشخصي عندما يكون ملفك في 30nama خاصًا. يبقى على هذا الكمبيوتر فقط.",
    };
}
