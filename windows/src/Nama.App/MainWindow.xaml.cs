using Microsoft.UI.Xaml;
using Nama.App.Player;
using Nama.Core;

namespace Nama.App;

public sealed partial class MainWindow : Window
{
    private readonly MpvPlayback _playback;
    private readonly DispatcherTimer _clock = new() { Interval = TimeSpan.FromMilliseconds(250) };

    public MainWindow()
    {
        InitializeComponent();
        _playback = new MpvPlayback(DispatcherQueue);
        Surface.Playback = _playback;
        _playback.Failed += () => Clock.Text = "Playback failed";
        // The position is asked for a few times a second, so the controls are not redrawn every frame.
        _clock.Tick += (_, _) =>
        {
            Clock.Text = $"{Fmt.Clock(_playback.Position)} / {Fmt.Clock(_playback.Duration)}";
            PlayButton.Content = _playback.Paused ? "Play" : "Pause";
        };
        _clock.Start();
        Closed += (_, _) => { _clock.Stop(); _playback.Dispose(); };
        var url = Environment.GetEnvironmentVariable("NAMA_PLAY") ?? "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
        _playback.Load(url);
    }

    private void OnPlayPause(object sender, RoutedEventArgs e)
    {
        if (_playback.Paused) _playback.Play(); else _playback.Pause();
    }
}
