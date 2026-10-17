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
    imdbTop250, siteTop100, votes, inWatchlists, inFavorites, budget, exclusiveSub, latestUpdate, otherLanguages,
    bufferTime, seconds,
}
#pragma warning restore IDE1006

public sealed partial class Loc
{
    public string this[Extra key] =>
        ((Lang == Lang.Fa ? ExtraFa : Lang == Lang.Ar ? ExtraAr : ExtraEn).GetValueOrDefault(key) ?? ExtraEn.GetValueOrDefault(key)) ?? key.ToString();

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraEn = new Dictionary<Extra, string>
    {
        [Extra.imdbTop250] = "IMDb Top 250",
        [Extra.siteTop100] = "30nama Top 100",
        [Extra.votes] = "votes",
        [Extra.inWatchlists] = "on watchlists",
        [Extra.inFavorites] = "among favourites",
        [Extra.budget] = "Budget",
        [Extra.exclusiveSub] = "Exclusive Persian subtitles",
        [Extra.latestUpdate] = "Latest update",
        [Extra.otherLanguages] = "Other languages",
        [Extra.bufferTime] = "Buffer ahead",
        [Extra.seconds] = "seconds",
    };

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraFa = new Dictionary<Extra, string>
    {
        [Extra.imdbTop250] = "۲۵۰ فیلم برتر IMDb",
        [Extra.siteTop100] = "۱۰۰ عنوان برتر ۳۰نما",
        [Extra.votes] = "رأی",
        [Extra.inWatchlists] = "در لیست تماشای کاربران",
        [Extra.inFavorites] = "در علاقه‌مندی‌های کاربران",
        [Extra.budget] = "بودجه",
        [Extra.exclusiveSub] = "زیرنویس فارسی اختصاصی",
        [Extra.latestUpdate] = "آخرین به‌روزرسانی",
        [Extra.otherLanguages] = "زبان‌های دیگر",
        [Extra.bufferTime] = "پیش‌بارگذاری",
        [Extra.seconds] = "ثانیه",
    };

    internal static readonly IReadOnlyDictionary<Extra, string> ExtraAr = new Dictionary<Extra, string>
    {
        [Extra.imdbTop250] = "أفضل 250 في IMDb",
        [Extra.siteTop100] = "أفضل 100 في 30nama",
        [Extra.votes] = "صوتًا",
        [Extra.inWatchlists] = "في قوائم المشاهدة",
        [Extra.inFavorites] = "في المفضّلة",
        [Extra.budget] = "الميزانية",
        [Extra.exclusiveSub] = "ترجمة فارسية حصرية",
        [Extra.latestUpdate] = "آخر تحديث",
        [Extra.otherLanguages] = "لغات أخرى",
        [Extra.bufferTime] = "التحميل المسبق",
        [Extra.seconds] = "ثانية",
    };
}
