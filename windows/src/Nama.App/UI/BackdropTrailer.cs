using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace Nama.App.UI;

/// <summary>
/// A title's trailer playing behind its page without sound, filling it like the still it covers,
/// under the same shading so the page stays readable. It shows only once it is really playing, and
/// tells when YouTube does not answer. As BackdropTrailer in TrailerSheet.swift.
/// </summary>
public sealed class BackdropTrailer : Grid
{
    /// <summary>Where the trailer starts: past studio cards and the first fade from black.</summary>
    private const int Skip = 8;

    private readonly WebView2 _web = new() { DefaultBackgroundColor = Colors.Transparent };
    private readonly CancellationTokenSource _stop = new();
    private bool _stopped;

    /// <summary>YouTube did not start the trailer.</summary>
    public event Action? Failed;

    public BackdropTrailer(string video)
    {
        IsHitTestVisible = false;
        Opacity = 0;
        OpacityTransition = new ScalarTransition { Duration = TimeSpan.FromSeconds(1.4) };
        // Held left to right, so the picture is not mirrored in a right-to-left window.
        var picture = new Grid { FlowDirection = FlowDirection.LeftToRight };
        picture.Children.Add(_web);
        Children.Add(picture);
        Children.Add(new Border { Background = Theme.Black(0.3) });
        Children.Add(new Border { Background = Scrim(horizontal: true) });
        Children.Add(new Border { Background = Scrim(horizontal: false) });
        // Wide enough to cover the page, cropped at its edges.
        SizeChanged += (_, e) =>
        {
            var width = Math.Max(e.NewSize.Width, e.NewSize.Height * 16 / 9) * 1.04;
            _web.Width = width;
            _web.Height = width * 9 / 16;
            Clip = new RectangleGeometry { Rect = new Windows.Foundation.Rect(0, 0, e.NewSize.Width, e.NewSize.Height) };
        };
        Loaded += (_, _) => _ = StartAsync(video);
        Unloaded += (_, _) => Stop();
    }

    /// <summary>Dark from the side the text is on and from the bottom, as the still is shaded.</summary>
    private static LinearGradientBrush Scrim(bool horizontal) => new()
    {
        StartPoint = horizontal ? new Windows.Foundation.Point(0, 0.5) : new Windows.Foundation.Point(0.5, 1),
        EndPoint = horizontal ? new Windows.Foundation.Point(1, 0.5) : new Windows.Foundation.Point(0.5, 0),
        GradientStops = horizontal
            ? [new GradientStop { Color = Theme.Bg0.WithAlpha(0.96), Offset = 0 }, new GradientStop { Color = Theme.Bg0.WithAlpha(0.78), Offset = 0.30 }, new GradientStop { Color = Theme.Bg0.WithAlpha(0), Offset = 0.68 }]
            : [new GradientStop { Color = Theme.Bg0, Offset = 0 }, new GradientStop { Color = Theme.Bg0.WithAlpha(0.85), Offset = 0.32 }, new GradientStop { Color = Theme.Bg0.WithAlpha(0), Offset = 0.62 }],
    };

    private async Task StartAsync(string video)
    {
        try
        {
            await YouTube.LoadAsync(_web, video, $"autoplay=1&mute=1&controls=0&disablekb=1&fs=0&rel=0&playsinline=1&iv_load_policy=3&loop=1&playlist={video}&start={Skip}");
            // The page is YouTube's own, so its video can be asked how far it has played. A couple
            // of seconds in, it is playing for real and YouTube's title has faded.
            for (var i = 0; i < 40; i++)
            {
                await Task.Delay(300, _stop.Token);
                if (await YouTube.PlayedAsync(_web) > Skip + 2.5)
                {
                    Opacity = 1;
                    return;
                }
            }
        }
        catch (OperationCanceledException) { return; }
        catch (Exception) { }
        if (!_stopped) Failed?.Invoke();
    }

    /// <summary>Fades out and then lets go of the player.</summary>
    public async Task EndAsync()
    {
        Opacity = 0;
        await Task.Delay(1400);
        Stop();
    }

    private void Stop()
    {
        if (_stopped) return;
        _stopped = true;
        _stop.Cancel();
        YouTube.Stop(_web);
    }
}
