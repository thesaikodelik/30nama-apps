namespace Nama.Core;

/// <summary>
/// The download managers the app hands a file to instead of downloading it itself, as many in Iran
/// download everything with one (learnt from vispar). IDM and FDM are found by their program; any
/// other the user picks in Settings. Each is told what to fetch on its command line.
/// </summary>
public static class ExternalDownloader
{
    /// <summary>Other: a program the user chose themselves.</summary>
    public enum Kind { Idm, Fdm, Other }

    /// <summary>The setting's value for each, as kept in settings.json.</summary>
    public static string Code(Kind kind) => kind switch { Kind.Idm => "idm", Kind.Fdm => "fdm", _ => "other" };

    public static Kind? FromCode(string? code) => code switch { "idm" => Kind.Idm, "fdm" => Kind.Fdm, "other" => Kind.Other, _ => null };

    /// <summary>Its name in Settings; a program of the user's own goes by its file's.</summary>
    public static string Name(Kind kind, string? program = null) => kind switch
    {
        Kind.Idm => "IDM",
        Kind.Fdm => "FDM",
        _ => Path.GetFileNameWithoutExtension(program ?? "") is { Length: > 0 } name ? name : "?",
    };

    /// <summary>The program's file name, as Windows' App Paths and the installers' folders know it; a program of the user's own has none.</summary>
    public static string Program(Kind kind) => kind == Kind.Idm ? "IDMan.exe" : "fdm.exe";

    /// <summary>What a chosen program is: IDM or FDM by their program's name, so each gets its own command line.</summary>
    public static Kind Of(string program)
    {
        var name = program.Replace('/', '\\').Split('\\')[^1];
        return name.Equals("IDMan.exe", StringComparison.OrdinalIgnoreCase) ? Kind.Idm
            : name.Equals("fdm.exe", StringComparison.OrdinalIgnoreCase) ? Kind.Fdm : Kind.Other;
    }

    /// <summary>
    /// The command line that has <paramref name="kind"/> fetch <paramref name="address"/> into
    /// <paramref name="folder"/> as <paramref name="fileName"/>. IDM starts it at once without its
    /// dialog (/n); FDM and any other take the address alone, which download managers generally do,
    /// and ask where to keep it in their own window.
    /// </summary>
    public static IReadOnlyList<string> Arguments(Kind kind, Uri address, string folder, string fileName) => kind switch
    {
        Kind.Idm => ["/d", address.AbsoluteUri, "/p", folder.TrimEnd('\\'), "/f", fileName, "/n"],
        _ => [address.AbsoluteUri],
    };
}
