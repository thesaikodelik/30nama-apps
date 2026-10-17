using System.Runtime.InteropServices;
using Microsoft.UI.Xaml.Controls;
using WinRT;

namespace Nama.App.Player;

/// <summary>
/// The panel mpv's picture appears in. XAML can be laid over it like over any other element,
/// which is why the player draws into a swap chain for composition instead of a window of its own.
/// </summary>
public sealed partial class VideoSurface : SwapChainPanel
{
    private MpvPlayback? _playback;

    public VideoSurface()
    {
        SizeChanged += (_, _) => Resize();
        CompositionScaleChanged += (_, _) => Resize();
    }

    public MpvPlayback? Playback
    {
        get => _playback;
        set
        {
            if (_playback is not null) _playback.SwapChainChanged -= Attach;
            _playback = value;
            if (_playback is not null) _playback.SwapChainChanged += Attach;
            Resize();
        }
    }

    private void Resize()
    {
        _playback?.SetSurfaceSize((int)Math.Round(ActualWidth * CompositionScaleX), (int)Math.Round(ActualHeight * CompositionScaleY));
        Rescale();
    }

    private IntPtr _swapChain;

    private unsafe void Attach(IntPtr swapChain)
    {
        _swapChain = swapChain;
        var unknown = ((IWinRTObject)this).NativeObject.ThisPtr;
        var iid = new Guid("63aad0b8-7c24-40ff-85a8-640d944cc325"); // ISwapChainPanelNative
        if (Marshal.QueryInterface(unknown, in iid, out var native) < 0) return;
        try
        {
            // ISwapChainPanelNative::SetSwapChain, the one method after IUnknown's three.
            var setSwapChain = (delegate* unmanaged[Stdcall]<IntPtr, IntPtr, int>)(*(*(void***)native + 3));
            setSwapChain(native, swapChain);
        }
        finally { Marshal.Release(native); }
        Rescale();
    }

    /// <summary>
    /// The panel stretches its swap chain by the display's scale; mpv already draws at full
    /// resolution, so the stretch is undone and every pixel lands on a pixel.
    /// </summary>
    private unsafe void Rescale()
    {
        if (_swapChain == IntPtr.Zero || CompositionScaleX <= 0 || CompositionScaleY <= 0) return;
        var iid = new Guid("a8be2ac4-199f-4946-b331-79599fb98de7"); // IDXGISwapChain2
        if (Marshal.QueryInterface(_swapChain, in iid, out var chain) < 0) return;
        try
        {
            var matrix = stackalloc float[6] { 1 / CompositionScaleX, 0, 0, 1 / CompositionScaleY, 0, 0 };
            // IDXGISwapChain2::SetMatrixTransform, slot 34 of its vtable.
            var setMatrix = (delegate* unmanaged[Stdcall]<IntPtr, float*, int>)(*(*(void***)chain + 34));
            setMatrix(chain, matrix);
        }
        finally { Marshal.Release(chain); }
    }
}
