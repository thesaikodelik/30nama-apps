using System.Globalization;

namespace Nama.Core;

/// <summary>
/// The video players on the computer the app hands a file or a stream to: which programs count,
/// and how each is told where to start. What is watched there is not reported to 30nama.
/// </summary>
public static class ExternalPlayer
{
    public enum Kind { Vlc, Mpv, PotPlayer, Mpc, Other }

    /// <summary>Video players by their program's name, without ".exe". Others that take video files are not offered, as on the Mac (photo viewers, editors, browsers).</summary>
    private static readonly Dictionary<string, Kind> Known = new(StringComparer.OrdinalIgnoreCase)
    {
        ["vlc"] = Kind.Vlc,
        ["mpv"] = Kind.Mpv, ["mpvnet"] = Kind.Mpv,
        ["potplayermini64"] = Kind.PotPlayer, ["potplayermini"] = Kind.PotPlayer, ["potplayer64"] = Kind.PotPlayer, ["potplayer"] = Kind.PotPlayer,
        ["mpc-hc64"] = Kind.Mpc, ["mpc-hc"] = Kind.Mpc, ["mpc-be64"] = Kind.Mpc, ["mpc-be"] = Kind.Mpc,
        ["mpc-qt"] = Kind.Other, ["smplayer"] = Kind.Other, ["kmplayer"] = Kind.Other, ["kmplayer64"] = Kind.Other, ["kmplayer64x"] = Kind.Other,
        ["gom"] = Kind.Other, ["wmplayer"] = Kind.Other,
        // Windows' own from the Store, by their package: Media Player and Films & TV.
        ["Microsoft.ZuneMusic"] = Kind.Other, ["Microsoft.ZuneVideo"] = Kind.Other,
    };

    /// <summary>What kind of player <paramref name="program"/> (a program's path, or a Store package's name) is, or null for a program that is not a video player.</summary>
    public static Kind? Of(string program)
    {
        var last = program.Replace('/', '\\').Split('\\')[^1];
        if (Known.TryGetValue(last, out var package)) return package;
        return Known.TryGetValue(Path.GetFileNameWithoutExtension(last), out var kind) ? kind : null;
    }

    /// <summary>The package's name in a Store app's icon reference ("@{Microsoft.ZuneVideo_10.0_x64__8wekyb3d8bbwe?ms-resource://…}").</summary>
    public static string? Package(string? iconReference) =>
        iconReference is { } text && text.StartsWith("@{") && text.IndexOf('_') is > 2 and var end ? text[2..end] : null;

    /// <summary>
    /// The players that are given a stream's address and play it as it comes (HLS among them).
    /// The others are offered for downloaded files only.
    /// </summary>
    public static bool TakesStreams(Kind kind) => kind != Kind.Other;

    /// <summary>The command line that opens <paramref name="address"/> in a player of <paramref name="kind"/>, from <paramref name="start"/> seconds where that player can be told.</summary>
    public static IReadOnlyList<string> Arguments(Kind kind, string address, double? start = null, string? title = null)
    {
        var from = start is > 0 ? (long)start.Value : 0;
        var seconds = from.ToString(CultureInfo.InvariantCulture);
        var list = new List<string>();
        switch (kind)
        {
            case Kind.Vlc:
                if (from > 0) list.Add("--start-time=" + seconds);
                if (!string.IsNullOrWhiteSpace(title)) list.Add("--meta-title=" + title);
                list.Add(address);
                break;
            case Kind.Mpv:
                if (from > 0) list.Add("--start=" + seconds);
                if (!string.IsNullOrWhiteSpace(title)) list.Add("--force-media-title=" + title);
                list.Add(address);
                break;
            case Kind.PotPlayer:
                list.Add(address);
                if (from > 0) list.Add("/seek=" + TimeSpan.FromSeconds(from).ToString(@"hh\:mm\:ss", CultureInfo.InvariantCulture));
                break;
            case Kind.Mpc:
                list.Add(address);
                if (from > 0) { list.Add("/start"); list.Add((from * 1000).ToString(CultureInfo.InvariantCulture)); }
                break;
            default:
                list.Add(address);
                break;
        }
        return list;
    }
}
