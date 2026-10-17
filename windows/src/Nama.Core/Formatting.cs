using System.Globalization;

namespace Nama.Core;

public static class Fmt
{
    private const string PersianDigitChars = "۰۱۲۳۴۵۶۷۸۹";

    /// <summary>Replaces ASCII digits with Persian ones.</summary>
    public static string PersianDigits(string s) =>
        string.Create(s.Length, s, (span, src) =>
        {
            for (var i = 0; i < src.Length; i++) span[i] = char.IsAsciiDigit(src[i]) ? PersianDigitChars[src[i] - '0'] : src[i];
        });

    /// <summary>Player clock: "2:03" or "1:02:03".</summary>
    public static string Clock(double seconds)
    {
        var s = Math.Max(0, double.IsFinite(seconds) ? (int)seconds : 0);
        int h = s / 3600, m = s % 3600 / 60, x = s % 60;
        return h > 0 ? $"{h}:{m:00}:{x:00}" : $"{m}:{x:00}";
    }

    /// <summary>Runtime in minutes as "1h 35m" / "۱ ساعت و ۳۵ دقیقه".</summary>
    public static string? Runtime(int? minutes, Lang lang)
    {
        if (minutes is not { } total || total <= 0) return null;
        int h = total / 60, m = total % 60;
        string Join(string separator, string? a, string? b) => string.Join(separator, new[] { a, b }.OfType<string>());
        return lang switch
        {
            Lang.Ar => Join(" ", h > 0 ? $"{h} س" : null, m > 0 ? $"{m} د" : null),
            Lang.Fa => PersianDigits(Join(" و ", h > 0 ? $"{h} ساعت" : null, m > 0 ? $"{m} دقیقه" : null)),
            _ => Join(" ", h > 0 ? $"{h}h" : null, m > 0 ? $"{m}m" : null),
        };
    }

    public static string Score(double value) =>
        value.ToString(value >= 10 ? "0" : "0.0", CultureInfo.InvariantCulture);

    /// <summary>84827 -> "84,827" / "۸۴٬۸۲۷".</summary>
    public static string Count(int value, Lang lang)
    {
        if (lang != Lang.Fa) return value.ToString("#,0", CultureInfo.InvariantCulture);
        var persian = (NumberFormatInfo)CultureInfo.InvariantCulture.NumberFormat.Clone();
        persian.NumberGroupSeparator = "٬";
        return PersianDigits(value.ToString("#,0", persian));
    }
}
