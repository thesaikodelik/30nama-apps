using System.Diagnostics;
using Microsoft.Win32;
using Nama.Core;

namespace Nama.App.Shell;

/// <summary>
/// IDM and FDM on this computer: where their program is, from Windows' App Paths or the folders
/// their installers use, and handing them a file to download.
/// </summary>
public static class OtherDownloaders
{
    /// <summary>The program of <paramref name="kind"/>, or null when it is not installed.</summary>
    public static string? Find(ExternalDownloader.Kind kind)
    {
        var program = ExternalDownloader.Program(kind);
        foreach (var hive in new[] { Registry.CurrentUser, Registry.LocalMachine })
        {
            try
            {
                using var key = hive.OpenSubKey($@"Software\Microsoft\Windows\CurrentVersion\App Paths\{program}");
                if (key?.GetValue(null) is string path && File.Exists(path.Trim('"'))) return path.Trim('"');
            }
            catch (Exception) { }
        }
        // IDM keeps its path under its own key.
        if (kind == ExternalDownloader.Kind.Idm)
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(@"Software\DownloadManager");
                if (key?.GetValue("ExePath") is string path && File.Exists(path)) return path;
            }
            catch (Exception) { }
        }
        var folders = kind == ExternalDownloader.Kind.Idm ? new[] { "Internet Download Manager" } : [@"Softdeluxe\Free Download Manager", "Free Download Manager"];
        var roots = new[] { Environment.SpecialFolder.ProgramFilesX86, Environment.SpecialFolder.ProgramFiles }.Select(Environment.GetFolderPath)
            .Append(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs"));
        return roots.SelectMany(root => folders.Select(folder => Path.Combine(root, folder, program))).FirstOrDefault(File.Exists);
    }

    /// <summary>Those installed, for the choice in Settings.</summary>
    public static IReadOnlyList<ExternalDownloader.Kind> Installed() =>
        new[] { ExternalDownloader.Kind.Idm, ExternalDownloader.Kind.Fdm }.Where(k => Find(k) is not null).ToList();

    /// <summary>The program the settings name, when it is still there: IDM or FDM where installed, or the one the user chose.</summary>
    public static string? Chosen(Model.Settings settings) => ExternalDownloader.FromCode(settings.Downloader) switch
    {
        ExternalDownloader.Kind.Other => settings.DownloaderProgram is { } program && File.Exists(program) ? program : null,
        { } kind => Find(kind),
        null => null,
    };

    /// <summary>Has <paramref name="program"/> download <paramref name="address"/>; false when it would not start.</summary>
    public static bool Send(string program, Uri address, string folder, string fileName)
    {
        try
        {
            var info = new ProcessStartInfo(program) { UseShellExecute = false };
            foreach (var argument in ExternalDownloader.Arguments(ExternalDownloader.Of(program), address, folder, fileName)) info.ArgumentList.Add(argument);
            Process.Start(info);
            return true;
        }
        catch (Exception) { return false; }
    }
}
