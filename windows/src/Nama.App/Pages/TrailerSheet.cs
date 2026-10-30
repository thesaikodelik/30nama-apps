using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// A title's trailer, playing over the window in YouTube's own player. YouTube cannot be reached
/// everywhere without a VPN; when it does not answer, the sheet says so rather than staying dark.
/// A trailer from Aparat (<see cref="AparatStore.Prefix"/>) is a plain file, played by Windows'
/// own player. As TrailerSheet.swift.
/// </summary>
public sealed class TrailerSheet : Grid
{
    private static Loc Loc => AppModel.Shared.Loc;

    private readonly WebView2 _web = new() { DefaultBackgroundColor = Microsoft.UI.Colors.Black };
    private readonly Grid _screen = new() { Background = Theme.Black(1), CornerRadius = new CornerRadius(14) };
    private readonly Action _close;
    private bool _closed;
    private MediaPlayerElement? _file;

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

        if (!video.StartsWith(AparatStore.Prefix, StringComparison.Ordinal)) _screen.Children.Add(_web);
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
        YouTube.Stop(_web);
        if (_file?.MediaPlayer is { } player)
        {
            player.Pause();
            _file.SetMediaPlayer(null);
            player.Dispose();
        }
    }

    private async Task StartAsync(string video)
    {
        if (video.StartsWith(AparatStore.Prefix, StringComparison.Ordinal))
        {
            await PlayFileAsync(video[AparatStore.Prefix.Length..]);
            return;
        }
        var reachable = ReachableAsync();
        try
        {
            // The player itself as the page, with sound.
            await YouTube.LoadAsync(_web, video, "autoplay=1&rel=0&playsinline=1&iv_load_policy=3");
        }
        catch (Exception)
        {
            Unreachable();
            return;
        }
        if (!await reachable && !_closed) Unreachable();
    }

    /// <summary>Aparat's file, asked for now (its address is signed and does not last), with Windows' own controls.</summary>
    private async Task PlayFileAsync(string uid)
    {
        var busy = Kit.Spinner();
        _screen.Children.Add(busy);
        var file = await AparatStore.Shared.FileAsync(uid);
        _screen.Children.Remove(busy);
        if (_closed) return;
        if (file is null)
        {
            Note(S.trailerNotLoaded, Glyph.Video);
            return;
        }
        var player = new Windows.Media.Playback.MediaPlayer { AutoPlay = true, Source = Windows.Media.Core.MediaSource.CreateFromUri(file) };
        player.MediaFailed += (_, _) => DispatcherQueue.TryEnqueue(() => { if (!_closed) Note(S.trailerNotLoaded, Glyph.Video); });
        // Left to right in every language: in a mirrored sheet the picture itself came out mirrored.
        _file = new MediaPlayerElement { AreTransportControlsEnabled = true, Stretch = Microsoft.UI.Xaml.Media.Stretch.Uniform, FlowDirection = FlowDirection.LeftToRight };
        _file.SetMediaPlayer(player);
        _screen.Children.Add(_file);
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

    private void Unreachable() => Note(S.trailerUnreachable, Glyph.Wifi);

    /// <summary>Says in the screen's place why there is no trailer, rather than leaving it dark.</summary>
    private void Note(S what, string glyph)
    {
        Stop();
        _screen.Children.Clear();
        var note = new StackPanel { Spacing = 10, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center, Padding = new Thickness(30) };
        var icon = Glyph.Icon(glyph, 30, Theme.Text2);
        icon.HorizontalAlignment = HorizontalAlignment.Center;
        note.Children.Add(icon);
        var text = Theme.Text(Loc[what], 15, FontWeights.Medium, Theme.Text2, lines: 0);
        text.TextAlignment = TextAlignment.Center;
        note.Children.Add(text);
        _screen.Children.Add(note);
    }
}
