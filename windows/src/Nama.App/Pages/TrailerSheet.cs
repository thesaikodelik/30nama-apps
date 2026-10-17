using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.Web.WebView2.Core;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// A title's trailer, playing over the window in YouTube's own player. YouTube cannot be reached
/// everywhere without a VPN; when it does not answer, the sheet says so rather than staying dark.
/// As TrailerSheet.swift.
/// </summary>
public sealed class TrailerSheet : Grid
{
    private static Loc Loc => AppModel.Shared.Loc;

    private readonly WebView2 _web = new() { DefaultBackgroundColor = Microsoft.UI.Colors.Black };
    private readonly Grid _screen = new() { Background = Theme.Black(1), CornerRadius = new CornerRadius(14) };
    private readonly Action _close;
    private bool _closed;

    public TrailerSheet(string title, string video, Action close)
    {
        _close = close;
        var dim = new Grid { Background = Theme.Black(0.6) };
        dim.Tapped += (_, e) => { if (ReferenceEquals(e.OriginalSource, dim)) Close(); };
        Children.Add(dim);

        var pane = new Grid { MaxWidth = 960, Margin = new Thickness(40, 70, 40, 40), VerticalAlignment = VerticalAlignment.Center, CornerRadius = new CornerRadius(22), Padding = new Thickness(20), RowSpacing = 14 };
        pane.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        pane.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        var glass = new Border { Background = Theme.GlassFill(), CornerRadius = new CornerRadius(22), BorderThickness = new Thickness(1), BorderBrush = Theme.White(0.12), Margin = new Thickness(-20) };
        SetRowSpan(glass, 2);
        pane.Children.Add(glass);

        var header = new Grid { ColumnSpacing = 12 };
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        header.Children.Add(Theme.Text($"{Loc[S.trailer]} · {title}", 17, FontWeights.Bold));
        var shut = Kit.Glass(null, Glyph.Close, Close, height: 30);
        Kit.AutomationName(shut, Loc[S.close]);
        SetColumn(shut, 1);
        header.Children.Add(shut);
        pane.Children.Add(header);

        _screen.Children.Add(_web);
        SetRow(_screen, 1);
        pane.Children.Add(_screen);
        // Sixteen by nine, whatever width the window leaves it.
        pane.SizeChanged += (_, e) => _screen.Height = Math.Max(0, e.NewSize.Width - 40) * 9 / 16;
        Children.Add(pane);

        KeyboardAccelerators.Add(Escape());
        Loaded += (_, _) => _ = StartAsync(video);
        Unloaded += (_, _) => Stop();
    }

    private Microsoft.UI.Xaml.Input.KeyboardAccelerator Escape()
    {
        var key = new Microsoft.UI.Xaml.Input.KeyboardAccelerator { Key = Windows.System.VirtualKey.Escape };
        key.Invoked += (_, e) => { e.Handled = true; Close(); };
        return key;
    }

    private void Close()
    {
        Stop();
        _close();
    }

    /// <summary>Stops the sound at once; a web view left to go away on its own can keep playing a while.</summary>
    private void Stop()
    {
        if (_closed) return;
        _closed = true;
        try { _web.Close(); } catch (Exception) { }
    }

    private async Task StartAsync(string video)
    {
        var reachable = ReachableAsync();
        try
        {
            // Its own folder under the app's, and leave to start with sound without a click.
            var environment = await CoreWebView2Environment.CreateWithOptionsAsync(null, Path.Combine(Storage.Folder, "WebView2"),
                new CoreWebView2EnvironmentOptions { AdditionalBrowserArguments = "--autoplay-policy=no-user-gesture-required" });
            await _web.EnsureCoreWebView2Async(environment);
            if (_closed) return;
            var core = _web.CoreWebView2;
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.AreDefaultContextMenusEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            // Links out of the player open in the browser, not in the sheet.
            core.NewWindowRequested += (sender, e) =>
            {
                e.Handled = true;
                if (Uri.TryCreate(e.Uri, UriKind.Absolute, out var link)) _ = Windows.System.Launcher.LaunchUriAsync(link);
            };
            // The player itself as the page; YouTube wants to know which page it is on, and is told.
            // A header given with the navigation itself is dropped, so it is put on the request as it leaves.
            core.AddWebResourceRequestedFilter("https://www.youtube-nocookie.com/embed/*", CoreWebView2WebResourceContext.Document);
            core.WebResourceRequested += (sender, e) => e.Request.Headers.SetHeader("Referer", "https://github.com/thesaikodelik/30nama-apps");
            core.Navigate($"https://www.youtube-nocookie.com/embed/{video}?autoplay=1&rel=0&playsinline=1&iv_load_policy=3");
        }
        catch (Exception)
        {
            Unreachable();
            return;
        }
        if (!await reachable && !_closed) Unreachable();
    }

    /// <summary>Whether YouTube answers a plain request at all from this connection.</summary>
    private static async Task<bool> ReachableAsync()
    {
        try
        {
            using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(8) };
            using var request = new HttpRequestMessage(HttpMethod.Head, "https://www.youtube-nocookie.com/");
            using var response = await client.SendAsync(request);
            return true;
        }
        catch (Exception) { return false; }
    }

    private void Unreachable()
    {
        Stop();
        _screen.Children.Clear();
        var note = new StackPanel { Spacing = 10, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center, Padding = new Thickness(30) };
        var icon = Glyph.Icon(Glyph.Wifi, 30, Theme.Text2);
        icon.HorizontalAlignment = HorizontalAlignment.Center;
        note.Children.Add(icon);
        var text = Theme.Text(Loc[S.trailerUnreachable], 15, FontWeights.Medium, Theme.Text2, lines: 0);
        text.TextAlignment = TextAlignment.Center;
        note.Children.Add(text);
        _screen.Children.Add(note);
    }
}
