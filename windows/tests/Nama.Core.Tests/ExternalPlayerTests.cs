using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class ExternalPlayerTests
{
    [Fact]
    public void OnlyVideoPlayersCount()
    {
        Assert.Equal(ExternalPlayer.Kind.Vlc, ExternalPlayer.Of(@"C:\Program Files\VideoLAN\VLC\vlc.exe"));
        Assert.Equal(ExternalPlayer.Kind.Mpc, ExternalPlayer.Of(@"C:\Program Files (x86)\K-Lite Codec Pack\MPC-HC64\mpc-hc64.exe"));
        Assert.Equal(ExternalPlayer.Kind.Mpv, ExternalPlayer.Of("mpvnet.exe"));
        Assert.Equal(ExternalPlayer.Kind.Other, ExternalPlayer.Of(@"C:\Program Files\KMPlayer 64X\KMPlayer64.exe"));
        // Photos opens video files too, but is not a player.
        Assert.Null(ExternalPlayer.Of(@"C:\Windows\System32\mspaint.exe"));
        Assert.Null(ExternalPlayer.Of("Photos"));
    }

    [Fact]
    public void StoreAppsAreKnownByTheirPackage()
    {
        var package = ExternalPlayer.Package("@{Microsoft.ZuneVideo_10.26091.10011.0_x64__8wekyb3d8bbwe?ms-resource://Microsoft.ZuneVideo/Files/Assets/AppList.png}");
        Assert.Equal("Microsoft.ZuneVideo", package);
        Assert.Equal(ExternalPlayer.Kind.Other, ExternalPlayer.Of(package!));
        Assert.Null(ExternalPlayer.Package(@"C:\Program Files\VideoLAN\VLC\vlc.exe"));
        Assert.Null(ExternalPlayer.Package(null));
    }

    [Fact]
    public void StreamsGoOnlyToPlayersThatTakeThem()
    {
        Assert.True(ExternalPlayer.TakesStreams(ExternalPlayer.Kind.Vlc));
        Assert.True(ExternalPlayer.TakesStreams(ExternalPlayer.Kind.PotPlayer));
        Assert.False(ExternalPlayer.TakesStreams(ExternalPlayer.Kind.Other));
    }

    [Fact]
    public void EachIsToldWhereToStartInItsOwnWay()
    {
        const string url = "https://example.com/a.m3u8";
        Assert.Equal(["--start-time=754", "--meta-title=Dune", url], ExternalPlayer.Arguments(ExternalPlayer.Kind.Vlc, url, 754.6, "Dune"));
        Assert.Equal(["--start=754", url], ExternalPlayer.Arguments(ExternalPlayer.Kind.Mpv, url, 754.6));
        Assert.Equal([url, "/seek=01:02:03"], ExternalPlayer.Arguments(ExternalPlayer.Kind.PotPlayer, url, 3723));
        Assert.Equal([url, "/start", "60000"], ExternalPlayer.Arguments(ExternalPlayer.Kind.Mpc, url, 60));
        // From the start: nothing but the address.
        Assert.Equal([url], ExternalPlayer.Arguments(ExternalPlayer.Kind.Mpc, url, null));
        Assert.Equal([url], ExternalPlayer.Arguments(ExternalPlayer.Kind.Vlc, url, 0, " "));
    }
}
