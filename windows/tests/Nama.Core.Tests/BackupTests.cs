using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class BackupTests
{
    private const string Settings = """{"lang":"fa","volume":0.8,"tmdbKey":"secret-tmdb","geminiKey":"secret-gemini","subsourceKey":"secret-sub","downloadFolder":"C:\\Users\\a\\Films","downloaderProgram":"C:\\Tools\\dm.exe","downloader":"idm"}""";

    private static Dictionary<string, string?> Here() => new()
    {
        ["settings.json"] = Settings,
        ["ratings.json"] = """{"604":8}""",
        ["watched.json"] = """["f1","f2"]""",
        ["left.json"] = "not json",
        ["downloads.json"] = """[{"path":"C:\\x.mkv"}]""",
    };

    [Fact]
    public void KeysAndPathsStayOnThisComputer()
    {
        var backup = Backup.Make(Here(), new DateTime(2026, 11, 17, 12, 0, 0, DateTimeKind.Utc));
        foreach (var secret in new[] { "secret-tmdb", "secret-gemini", "secret-sub", "Films", "dm.exe" }) Assert.DoesNotContain(secret, backup);
        Assert.DoesNotContain("downloads.json", backup); // not a file a backup carries
        var (files, problem) = Backup.Read(backup);
        Assert.Null(problem);
        Assert.Equal(["ratings.json", "settings.json", "watched.json"], files!.Keys.Order());
        Assert.Contains("\"lang\":\"fa\"", files["settings.json"]);
        Assert.Contains("\"downloader\":\"idm\"", files["settings.json"]);
    }

    [Fact]
    public void RestoringKeepsThisComputersOwn()
    {
        var restored = """{"lang":"ar","tmdbKey":"from-elsewhere"}""";
        var merged = Json.Parse(Backup.MergeSettings(restored, Settings));
        Assert.Equal("ar", merged["lang"].Text);
        Assert.Equal("secret-tmdb", merged["tmdbKey"].Text); // this computer's, not the file's
        Assert.Equal(@"C:\Users\a\Films", merged["downloadFolder"].Text);
        Assert.True(Json.Parse(Backup.MergeSettings(restored, null))["tmdbKey"].IsNull); // none here: none taken from the file
    }

    [Fact]
    public void WhatIsNotABackupIsTurnedDown()
    {
        Assert.Equal(Backup.Problem.NotABackup, Backup.Read("hello").Problem);
        Assert.Equal(Backup.Problem.NotABackup, Backup.Read("""{"kind":"other","version":1}""").Problem);
        Assert.Equal(Backup.Problem.NewerVersion, Backup.Read("""{"kind":"30nama-backup","version":9,"files":{}}""").Problem);
        Assert.Equal(Backup.Problem.Empty, Backup.Read("""{"kind":"30nama-backup","version":1,"files":{"evil.dll":{"a":1},"ratings.json":"text"}}""").Problem);
        Assert.Equal(Backup.Problem.TooLarge, Backup.Read(new string(' ', Backup.MaxChars + 1)).Problem);
    }
}
