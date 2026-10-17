using Microsoft.UI.Xaml.Controls;
using Microsoft.Web.WebView2.Core;
using Nama.App.Model;

namespace Nama.App.UI;

/// <summary>
/// YouTube's own player in a web view, for trailers: in the trailer sheet with sound, and behind a
/// title's page without. Both share one browser environment, which may only be made one way.
/// </summary>
public static class YouTube
{
    private static Task<CoreWebView2Environment>? _environment;

    /// <summary>Its own folder under the app's, and leave to start playing without a click.</summary>
    private static Task<CoreWebView2Environment> EnvironmentAsync() =>
        _environment ??= CoreWebView2Environment.CreateWithOptionsAsync(null, Path.Combine(Storage.Folder, "WebView2"),
            new CoreWebView2EnvironmentOptions { AdditionalBrowserArguments = "--autoplay-policy=no-user-gesture-required" }).AsTask();

    /// <summary>Opens <paramref name="video"/> in <paramref name="web"/>, as YouTube's embedded player with the given options.</summary>
    public static async Task LoadAsync(WebView2 web, string video, string options)
    {
        await web.EnsureCoreWebView2Async(await EnvironmentAsync());
        var core = web.CoreWebView2;
        core.Settings.AreDevToolsEnabled = false;
        core.Settings.AreDefaultContextMenusEnabled = false;
        core.Settings.IsStatusBarEnabled = false;
        // Links out of the player open in the browser, not in the app.
        core.NewWindowRequested += (sender, e) =>
        {
            e.Handled = true;
            if (Uri.TryCreate(e.Uri, UriKind.Absolute, out var link)) _ = Windows.System.Launcher.LaunchUriAsync(link);
        };
        // YouTube wants to know which page it is on, and is told. A header given with the
        // navigation itself is dropped, so it is put on the request as it leaves.
        core.AddWebResourceRequestedFilter("https://www.youtube-nocookie.com/embed/*", CoreWebView2WebResourceContext.Document);
        core.WebResourceRequested += (sender, e) => e.Request.Headers.SetHeader("Referer", "https://github.com/thesaikodelik/30nama-apps");
        core.Navigate($"https://www.youtube-nocookie.com/embed/{video}?{options}");
    }

    /// <summary>How far the page's video has played, in seconds; 0 before it has started or when it cannot be asked.</summary>
    public static async Task<double> PlayedAsync(WebView2 web)
    {
        try
        {
            if (web.CoreWebView2 is not { } core) return 0;
            var answer = await core.ExecuteScriptAsync("(function(){var v=document.querySelector('video');return v?v.currentTime:0})()");
            return double.TryParse(answer, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var at) ? at : 0;
        }
        catch (Exception) { return 0; }
    }

    /// <summary>Stops the sound at once; a web view left to go away on its own can keep playing a while.</summary>
    public static void Stop(WebView2 web)
    {
        try { web.Close(); } catch (Exception) { }
    }
}
