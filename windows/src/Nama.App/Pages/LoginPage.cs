using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Nama.App.Model;
using Nama.App.UI;
using Nama.Core;

namespace Nama.App.Pages;

/// <summary>
/// Sign-in with a code. On a computer the browser is one click away, so the code can be
/// confirmed on this machine as well as scanned with a phone.
/// </summary>
public sealed class LoginPage : Grid
{
    private static AppModel App => AppModel.Shared;

    private enum Status { Waiting, Expired, Failed, Done }

    private static readonly TimeSpan PollInterval = TimeSpan.FromSeconds(5);
    private static readonly TimeSpan Lifetime = TimeSpan.FromSeconds(60);

    private readonly Stage _stage = new(dim: 0.55);
    private readonly StackPanel _instructions = new() { Spacing = 18, MaxWidth = 520, VerticalAlignment = VerticalAlignment.Center };
    private readonly Grid _tile = new() { Width = 300, Height = 300 };
    private readonly StackPanel _waiting = new() { Orientation = Orientation.Horizontal, Spacing = 10, HorizontalAlignment = HorizontalAlignment.Center, Height = 20 };
    private CancellationTokenSource? _run;
    private Status _status = Status.Waiting;
    private string? _code;
    private Uri? _link;
    private ImageSource? _qr;

    public LoginPage()
    {
        Children.Add(_stage);
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 72, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Padding = new Thickness(80, 0, 80, 0) };
        row.Children.Add(_instructions);
        var tileColumn = new StackPanel { Spacing = 18, VerticalAlignment = VerticalAlignment.Center };
        var card = new Border { CornerRadius = new CornerRadius(28), Background = Theme.Brush(Colors.White), Child = _tile, Translation = new System.Numerics.Vector3(0, 0, 40), Shadow = new ThemeShadow() };
        tileColumn.Children.Add(card);
        var dot = new Microsoft.UI.Xaml.Shapes.Ellipse { Width = 9, Height = 9, Fill = Theme.Brush(Theme.Accent), VerticalAlignment = VerticalAlignment.Center };
        _waiting.Children.Add(dot);
        _waiting.Children.Add(Theme.Text(App.Loc[S.waiting], 15, FontWeights.Medium, Theme.Text2));
        tileColumn.Children.Add(_waiting);
        row.Children.Add(tileColumn);
        Children.Add(row);
        Loaded += async (_, _) =>
        {
            Start();
            // A different one of the best-regarded titles each time the screen comes up.
            try
            {
                var backdrops = (await App.HomeAsync()).Backdrops();
                if (backdrops.Count > 0) _stage.Show(backdrops[Random.Shared.Next(backdrops.Count)]);
            }
            catch (Exception) { }
        };
        Unloaded += (_, _) => _run?.Cancel();
    }

    private void Start()
    {
        _run?.Cancel();
        _run = new CancellationTokenSource();
        _ = RunCodeAsync(_run.Token);
    }

    private void Render()
    {
        var loc = App.Loc;
        _instructions.Children.Clear();
        var logo = new Image { Source = new BitmapImage(new Uri("ms-appx:///Assets/Logo.png")), Height = 54, HorizontalAlignment = HorizontalAlignment.Left, FlowDirection = FlowDirection.LeftToRight };
        _instructions.Children.Add(logo);
        var title = Theme.Text(loc[S.scanTitle], 40, FontWeights.Bold, lines: 2);
        title.Margin = new Thickness(0, 10, 0, 0);
        _instructions.Children.Add(title);
        var steps = new StackPanel { Spacing = 14 };
        var keys = new[] { S.step1, S.step2, S.step3 };
        for (var i = 0; i < keys.Length; i++)
        {
            var step = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 14 };
            var number = Theme.Text(loc.Num(i + 1), 15, FontWeights.Bold, Colors.White);
            number.HorizontalAlignment = HorizontalAlignment.Center;
            number.VerticalAlignment = VerticalAlignment.Center;
            step.Children.Add(new Border { Width = 30, Height = 30, CornerRadius = new CornerRadius(15), Background = Theme.Brush(Theme.Accent), Child = number });
            var text = Theme.Text(loc[keys[i]], 18, color: Theme.Text2);
            text.VerticalAlignment = VerticalAlignment.Center;
            step.Children.Add(text);
            steps.Children.Add(step);
        }
        _instructions.Children.Add(steps);
        if (_code is not null)
        {
            var block = new StackPanel { Spacing = 8, Margin = new Thickness(0, 8, 0, 0) };
            block.Children.Add(Theme.Text(loc[S.orEnterCode], 15, color: Theme.Text3));
            var code = new TextBlock
            {
                Text = _code, FontSize = 34, FontWeight = FontWeights.ExtraBold, CharacterSpacing = 300,
                FontFamily = new FontFamily("Cascadia Mono,Consolas"), Foreground = Theme.Brush(Theme.Text1),
                IsTextSelectionEnabled = true, FlowDirection = FlowDirection.LeftToRight, HorizontalAlignment = HorizontalAlignment.Left,
            };
            block.Children.Add(code);
            _instructions.Children.Add(block);
        }
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12, Margin = new Thickness(0, 14, 0, 0) };
        if (_link is { } link && _status == Status.Waiting)
            buttons.Children.Add(Kit.Primary("30nama.com", Glyph.Globe, () => _ = Windows.System.Launcher.LaunchUriAsync(link)));
        if (_status is Status.Expired or Status.Failed)
            buttons.Children.Add(Kit.Primary(loc[S.newCode], null, Start));
        buttons.Children.Add(Kit.Glass(loc[S.guest], null, App.ContinueAsGuest));
        _instructions.Children.Add(buttons);

        _tile.Children.Clear();
        switch (_status)
        {
            case Status.Waiting when _qr is not null:
                _tile.Children.Add(new Image { Source = _qr, Margin = new Thickness(22), Stretch = Stretch.Uniform, FlowDirection = FlowDirection.LeftToRight });
                break;
            case Status.Waiting:
                var spinner = Kit.Spinner();
                spinner.Foreground = Theme.Brush(Theme.FocusInk);
                _tile.Children.Add(spinner);
                break;
            case Status.Expired or Status.Failed:
                var message = Theme.Text(loc[_status == Status.Expired ? S.expired : S.codeFailed], 20, FontWeights.Bold, Theme.FocusInk);
                message.HorizontalAlignment = HorizontalAlignment.Center;
                message.VerticalAlignment = VerticalAlignment.Center;
                _tile.Children.Add(message);
                break;
            case Status.Done:
                var check = Glyph.Icon(Glyph.Check, 96, Theme.Accent);
                check.HorizontalAlignment = HorizontalAlignment.Center;
                _tile.Children.Add(check);
                break;
        }
        _waiting.Opacity = _status == Status.Waiting && _qr is not null ? 1 : 0;
    }

    // MARK: Flow

    /// <summary>Fetches a code, shows it, and polls until it is used or it expires.</summary>
    private async Task RunCodeAsync(CancellationToken cancel)
    {
        _status = Status.Waiting;
        _qr = null;
        _code = null;
        _link = null;
        Render();
        string fresh;
        try
        {
            var result = await App.Api.GetAsync("qrlogin_code");
            if (result["code"].Text is not { } code) throw new ApiException(ApiException.Kinds.Server, "no code");
            fresh = code;
            var address = result["url"].Text ?? $"https://30nama.com/auth/{fresh}";
            _code = fresh;
            _link = Uri.TryCreate(address, UriKind.Absolute, out var u) ? u : null;
            _qr = QrImage(address);
        }
        catch (Exception)
        {
            if (!cancel.IsCancellationRequested) { _status = Status.Failed; Render(); }
            return;
        }
        if (cancel.IsCancellationRequested) return;
        Render();
        var started = DateTime.UtcNow;
        while (!cancel.IsCancellationRequested)
        {
            try { await Task.Delay(PollInterval, cancel); } catch (TaskCanceledException) { return; }
            if (DateTime.UtcNow - started > Lifetime)
            {
                _status = Status.Expired;
                Render();
                return;
            }
            // Unused codes answer success: false; keep polling.
            try
            {
                var e = await App.Api.CallAsync("qrlogin_login", new Dictionary<string, string> { ["auth_code"] = fresh });
                if (cancel.IsCancellationRequested) return;
                if (!e.Success || Core.User.Parse(e.Result)?.Token is not { } token) continue;
                _status = Status.Done;
                Render();
                await Task.Delay(500);
                await App.SignInAsync(token);
                return;
            }
            catch (Exception) { }
        }
    }

    /// <summary>The code as a picture, ten pixels a module, so it stays sharp when scaled.</summary>
    private static ImageSource QrImage(string text)
    {
        var code = QrCode.Encode(text);
        const int scale = 10;
        var side = code.Size * scale;
        var pixels = new byte[side * side * 4];
        for (var y = 0; y < side; y++)
        {
            for (var x = 0; x < side; x++)
            {
                var value = code[x / scale, y / scale] ? (byte)0 : (byte)255;
                var o = (y * side + x) * 4;
                pixels[o] = pixels[o + 1] = pixels[o + 2] = value;
                pixels[o + 3] = 255;
            }
        }
        var bitmap = new WriteableBitmap(side, side);
        using (var stream = bitmap.PixelBuffer.AsStream()) stream.Write(pixels, 0, pixels.Length);
        bitmap.Invalidate();
        return bitmap;
    }
}
