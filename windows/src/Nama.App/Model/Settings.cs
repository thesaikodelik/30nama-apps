using System.Text.Json;
using System.Text.Json.Serialization;
using Nama.Core;
using Windows.Security.Credentials;

namespace Nama.App.Model;

public enum SubtitleColor { White, Yellow, Cyan, Green }

/// <summary>What sits behind subtitle text: nothing (outlined text), or a black plate at half or full strength.</summary>
public enum SubtitleBackground { None, Half, Solid }

/// <summary>User preferences, kept in a file in the app's folder under LocalAppData.</summary>
public sealed record Settings
{
    /// <summary>English until the user picks another.</summary>
    public Lang Lang { get; init; } = Lang.En;
    /// <summary>The user chose to browse without signing in.</summary>
    public bool Guest { get; init; }
    /// <summary>"off", "fa" or "en".</summary>
    public string DefaultSubtitle { get; init; } = "fa";
    public int SubtitleSize { get; init; } = 46;
    public SubtitleColor SubtitleColor { get; init; } = SubtitleColor.White;
    public SubtitleBackground SubtitleBackground { get; init; } = SubtitleBackground.None;
    public bool AutoplayNext { get; init; } = true;
    /// <summary>Jump over the opening titles and the recap without being asked.</summary>
    public bool AutoSkip { get; init; }
    public AudioMix AudioMix { get; init; } = AudioMix.Stereo;
    /// <summary>Use the adaptive stream instead of the highest fixed rendition.</summary>
    public bool PreferAdaptive { get; init; }
    public bool ReduceMotion { get; init; }
    /// <summary>
    /// How far ahead the player reads, in seconds; zero leaves it to mpv. From 30nama's own
    /// desktop app: a short buffer on a metered line, a long one on a shaky line.
    /// </summary>
    public int BufferSeconds { get; init; }
    public static readonly int[] BufferChoices = [0, 30, 60, 120, 300];
    /// <summary>Where downloads are put. Null is the user's Downloads folder.</summary>
    public string? DownloadFolder { get; init; }
    /// <summary>
    /// The most all downloads together may take, in kilobytes a second; zero for no limit. From
    /// 30nama's own desktop app, for a line that is shared or metered.
    /// </summary>
    public int DownloadLimit { get; init; }
    public static readonly int[] LimitChoices = [0, 256, 512, 1024, 2048, 5120, 10240];
    /// <summary>Closing the window leaves the app beside the clock, as 30nama's own desktop app does by default.</summary>
    public bool KeepInTray { get; init; } = true;
    public double Volume { get; init; } = 1;

    public static readonly int[] SubtitleSizes = [38, 46, 54];

    private static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.CamelCase) },
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    private static string File => Path.Combine(Storage.Folder, "settings.json");

    public static Settings Load()
    {
        try { return JsonSerializer.Deserialize<Settings>(System.IO.File.ReadAllText(File), Options) ?? new Settings(); }
        catch (Exception) { return new Settings(); }
    }

    public void Save() => Storage.Write(File, JsonSerializer.Serialize(this, Options));
}

/// <summary>The app's own folder, under LocalAppData.</summary>
public static class Storage
{
    public static string Folder { get; } = Directory.CreateDirectory(
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "30nama")).FullName;

    public static string? Read(string path)
    {
        try { return System.IO.File.ReadAllText(path); }
        catch (Exception) { return null; }
    }

    /// <summary>Written beside and moved into place, so a crash halfway leaves the old file whole.</summary>
    public static void Write(string path, string text)
    {
        try
        {
            var temporary = path + ".tmp";
            System.IO.File.WriteAllText(temporary, text);
            System.IO.File.Move(temporary, path, overwrite: true);
        }
        catch (Exception) { }
    }
}

/// <summary>
/// Keeps the session across launches: the token in the Windows Credential Locker, where only this
/// user can read it, and the profile next to the settings.
/// </summary>
public static class SessionStore
{
    private const string Resource = "30nama";
    private const string Account = "session-token";
    private static string ProfileFile => Path.Combine(Storage.Folder, "profile.json");

    public static void Save(User user)
    {
        try
        {
            var vault = new PasswordVault();
            Remove(vault);
            vault.Add(new PasswordCredential(Resource, Account, user.Token));
        }
        catch (Exception) { }
        var profile = new Dictionary<string, string?>
        {
            ["user_id"] = user.Id, ["user_name"] = user.Name, ["user_type"] = user.Type, ["user_expire"] = user.Expiry,
            ["user_expire_local"] = user.ExpiryLocal, ["user_avatar"] = user.Avatar?.OriginalString,
        };
        Storage.Write(ProfileFile, JsonSerializer.Serialize(profile.Where(p => p.Value is not null).ToDictionary()));
    }

    public static User? Load()
    {
        string? token = null;
        try
        {
            var credential = new PasswordVault().Retrieve(Resource, Account);
            credential.RetrievePassword();
            token = credential.Password;
        }
        catch (Exception) { }
        if (string.IsNullOrEmpty(token)) return null;
        var profile = Storage.Read(ProfileFile) is { } text ? Json.Parse(text) : Json.Null;
        var fields = (profile.Object ?? new Dictionary<string, Json>()).ToDictionary(p => p.Key, p => (object?)p.Value);
        fields["user_session"] = token;
        return User.Parse(Json.From(fields));
    }

    public static void Clear()
    {
        try { Remove(new PasswordVault()); } catch (Exception) { }
        try { System.IO.File.Delete(ProfileFile); } catch (Exception) { }
    }

    private static void Remove(PasswordVault vault)
    {
        try { foreach (var c in vault.FindAllByResource(Resource)) vault.Remove(c); }
        catch (Exception) { } // nothing stored yet
    }
}
