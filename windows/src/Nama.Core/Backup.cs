namespace Nama.Core;

/// <summary>
/// What this computer keeps of the user's in one file, to carry to another (learnt from vispar):
/// ratings, what was watched, where each title was left, per-series choices, the cards taken off
/// "continue watching", and the settings. Not the user's own keys, which stay on the device they
/// were typed on, nor this computer's paths. A backup is read as untrusted text: only the files
/// known here, each of them JSON, are taken from it.
/// </summary>
public static class Backup
{
    public const int Version = 1;
    public const string Kind = "30nama-backup";
    /// <summary>Larger than any real backup by far; a bigger file is not one.</summary>
    public const int MaxChars = 20_000_000;

    public const string SettingsFile = "settings.json";

    public static readonly string[] Files =
        [SettingsFile, "ratings.json", "watched.json", "left.json", "local-positions.json", "series-choices.json", "continue-dismissals.json"];

    /// <summary>Settings that belong to this computer or are the user's secrets: left out, and kept as they are on restore.</summary>
    public static readonly string[] LocalSettings = ["subsourceKey", "geminiKey", "tmdbKey", "downloadFolder", "downloaderProgram"];

    /// <summary>The backup of <paramref name="files"/> (name to its text; missing or unreadable ones are left out).</summary>
    public static string Make(IReadOnlyDictionary<string, string?> files, DateTime made)
    {
        var kept = new Dictionary<string, Json>();
        foreach (var name in Files)
        {
            if (files.GetValueOrDefault(name) is not { Length: > 0 } text || Parse(text) is not { } json) continue;
            kept[name] = name == SettingsFile ? WithoutLocal(json) : json;
        }
        return new Json(new Dictionary<string, Json>
        {
            ["kind"] = new(Kind),
            ["version"] = new(Version),
            ["made"] = new(made.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", System.Globalization.CultureInfo.InvariantCulture)),
            ["files"] = new(kept),
        }).Serialize();
    }

    public enum Problem { NotABackup, TooLarge, NewerVersion, Empty }

    /// <summary>The files in a backup, name to text, or why it cannot be read.</summary>
    public static (Dictionary<string, string>? Files, Problem? Problem) Read(string text)
    {
        if (text.Length > MaxChars) return (null, Problem.TooLarge);
        if (Parse(text) is not { } json || json["kind"].Text != Kind) return (null, Problem.NotABackup);
        if (json["version"].Int is not { } version || version > Version) return (null, Problem.NewerVersion);
        var files = new Dictionary<string, string>();
        foreach (var name in Files)
        {
            var file = json["files"][name];
            if (file.IsNull || file.Type is not (Json.Kind.Object or Json.Kind.Array)) continue;
            files[name] = (name == SettingsFile ? WithoutLocal(file) : file).Serialize();
        }
        return files.Count == 0 ? (null, Problem.Empty) : (files, null);
    }

    /// <summary>Restored settings with this computer's own keys and paths put back in.</summary>
    public static string MergeSettings(string restored, string? current)
    {
        var fields = (Parse(restored)?.Object ?? new Dictionary<string, Json>()).ToDictionary(kv => kv.Key, kv => kv.Value);
        var here = current is null ? null : Parse(current);
        foreach (var key in LocalSettings)
        {
            fields.Remove(key);
            if (here?[key] is { IsNull: false } value) fields[key] = value;
        }
        return new Json(fields).Serialize();
    }

    private static Json WithoutLocal(Json settings) =>
        new((settings.Object ?? new Dictionary<string, Json>()).Where(kv => !LocalSettings.Contains(kv.Key)).ToDictionary(kv => kv.Key, kv => kv.Value));

    private static Json? Parse(string text)
    {
        try { return Json.Parse(text.TrimStart('﻿')); }
        catch (Exception) { return null; }
    }
}
