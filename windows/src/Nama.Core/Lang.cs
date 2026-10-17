namespace Nama.Core;

/// <summary>The interface languages, in the order the language setting goes through them.</summary>
public enum Lang { En, Ar, Fa }

public static class LangExtensions
{
    public static IReadOnlyList<Lang> All { get; } = [Lang.En, Lang.Ar, Lang.Fa];

    public static bool IsRtl(this Lang lang) => lang != Lang.En;

    /// <summary>What the language calls itself.</summary>
    public static string Name(this Lang lang) => lang switch
    {
        Lang.Ar => "العربية",
        Lang.Fa => "فارسی",
        _ => "English",
    };

    /// <summary>The language to ask 30nama for titles and plots in. It has them in Persian and English only.</summary>
    public static string Catalogue(this Lang lang) => lang == Lang.Fa ? "fa" : "en";

    /// <summary>The code used to store the setting, as the Mac app's raw values.</summary>
    public static string Code(this Lang lang) => lang switch { Lang.Ar => "ar", Lang.Fa => "fa", _ => "en" };

    public static Lang? FromCode(string? code) => code switch { "en" => Lang.En, "ar" => Lang.Ar, "fa" => Lang.Fa, _ => null };
}
