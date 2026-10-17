using System.Runtime.InteropServices;
using Nama.App.Model;
using Nama.Core;
using Windows.Media;
using Windows.Storage.Streams;

namespace Nama.App.Player;

/// <summary>
/// What the rest of Windows sees of playback: the screen is kept on while a title plays, and the
/// keyboard's and headphones' play, pause and next keys reach the player, with the title shown in
/// Windows' own media panel. As the Mac app's Now Playing and sleep assertion.
/// </summary>
public sealed partial class NowPlaying : IDisposable
{
    private readonly PlayerEngine _engine;
    private readonly Microsoft.UI.Dispatching.DispatcherQueue _ui;
    private readonly SystemMediaTransportControls? _controls;
    private bool _disposed;

    public NowPlaying(PlayerEngine engine, IntPtr window, Microsoft.UI.Dispatching.DispatcherQueue ui)
    {
        _engine = engine;
        _ui = ui;
        _controls = ForWindow(window);
        if (_controls is not null)
        {
            _controls.IsEnabled = true;
            _controls.IsPlayEnabled = true;
            _controls.IsPauseEnabled = true;
            _controls.ButtonPressed += OnButton;
        }
        engine.Changed += Update;
        Update();
    }

    private string? _shownItem;

    private void Update()
    {
        if (_disposed) return;
        // Kept on while a title plays; a paused one lets the screen and the computer sleep.
        Native.SetThreadExecutionState(_engine.Playing
            ? Native.Continuous | Native.DisplayRequired | Native.SystemRequired
            : Native.Continuous);
        if (_controls is null) return;
        _controls.PlaybackStatus = _engine.Playing ? MediaPlaybackStatus.Playing : MediaPlaybackStatus.Paused;
        _controls.IsNextEnabled = _engine.HasNext;
        _controls.IsPreviousEnabled = _engine.Index > 0;
        var item = _engine.Current;
        var key = $"{_engine.Post.Id}:{item.FileId}";
        if (_shownItem == key) return;
        _shownItem = key;
        var loc = AppModel.Shared.Loc;
        var updater = _controls.DisplayUpdater;
        updater.Type = MediaPlaybackType.Video;
        updater.VideoProperties.Title = _engine.Post.Title(loc.Lang);
        updater.VideoProperties.Subtitle = _engine.Post.IsSeries
            ? loc.EpisodeTag(item.Season, item.EpisodeRaw ?? item.EpisodeNumber.ToString()) + (item.Title(loc.Lang) is { } name ? $" · {name}" : "")
            : "";
        if (_engine.Post.Poster.Url(300) is { } poster) updater.Thumbnail = RandomAccessStreamReference.CreateFromUri(poster);
        updater.Update();
    }

    /// <summary>Comes on a thread of Windows' own; the player is only touched from its own.</summary>
    private void OnButton(SystemMediaTransportControls sender, SystemMediaTransportControlsButtonPressedEventArgs e)
    {
        var button = e.Button;
        _ui.TryEnqueue(() =>
        {
            if (_disposed) return;
            switch (button)
            {
                case SystemMediaTransportControlsButton.Play when !_engine.Playing:
                case SystemMediaTransportControlsButton.Pause when _engine.Playing:
                    _engine.Toggle();
                    break;
                case SystemMediaTransportControlsButton.Next when _engine.HasNext:
                    _engine.Start(_engine.Index + 1);
                    break;
                case SystemMediaTransportControlsButton.Previous when _engine.Index > 0:
                    _engine.Start(_engine.Index - 1);
                    break;
            }
        });
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _engine.Changed -= Update;
        Native.SetThreadExecutionState(Native.Continuous);
        if (_controls is null) return;
        _controls.ButtonPressed -= OnButton;
        _controls.DisplayUpdater.ClearAll();
        _controls.IsEnabled = false;
    }

    /// <summary>
    /// The media controls of a window. A desktop app has no view to ask them of, so they are asked
    /// of the window through ISystemMediaTransportControlsInterop on the class's activation factory.
    /// </summary>
    private static unsafe SystemMediaTransportControls? ForWindow(IntPtr window)
    {
        try
        {
            using var factory = WinRT.ActivationFactory.Get("Windows.Media.SystemMediaTransportControls");
            var interopId = new Guid("ddb0472d-c911-4a1f-86d9-dc3d71a95f5a"); // ISystemMediaTransportControlsInterop
            if (Marshal.QueryInterface(factory.ThisPtr, in interopId, out var interop) < 0) return null;
            try
            {
                var controlsId = new Guid("99fa3ff4-1742-42a6-902e-087d41f965ec"); // ISystemMediaTransportControls
                IntPtr controls;
                // GetForWindow, the first method after IInspectable's six.
                var getForWindow = (delegate* unmanaged[Stdcall]<IntPtr, IntPtr, Guid*, IntPtr*, int>)(*(*(void***)interop + 6));
                if (getForWindow(interop, window, &controlsId, &controls) < 0) return null;
                try { return SystemMediaTransportControls.FromAbi(controls); }
                finally { Marshal.Release(controls); }
            }
            finally { Marshal.Release(interop); }
        }
        catch (Exception) { return null; }
    }

    private static partial class Native
    {
        public const uint Continuous = 0x80000000, SystemRequired = 0x1, DisplayRequired = 0x2;

        [LibraryImport("kernel32.dll")]
        public static partial uint SetThreadExecutionState(uint flags);
    }
}
