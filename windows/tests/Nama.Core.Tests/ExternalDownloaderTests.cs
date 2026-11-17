using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class ExternalDownloaderTests
{
    private static readonly Uri File = new("https://dl.example.com/films/Heat%201995%201080p.mkv?token=abc");

    [Fact]
    public void IdmIsToldWhereAndUnderWhatName()
    {
        Assert.Equal(["/d", File.AbsoluteUri, "/p", @"C:\Users\x\Downloads", "/f", "Heat 1995 1080p.mkv", "/n"],
            ExternalDownloader.Arguments(ExternalDownloader.Kind.Idm, File, @"C:\Users\x\Downloads\", "Heat 1995 1080p.mkv"));
    }

    [Fact]
    public void FdmTakesTheAddressAlone()
    {
        Assert.Equal([File.AbsoluteUri], ExternalDownloader.Arguments(ExternalDownloader.Kind.Fdm, File, @"C:\x", "a.mkv"));
    }

    [Fact]
    public void AProgramOfTheUsersOwnIsGivenTheAddress()
    {
        Assert.Equal(ExternalDownloader.Kind.Other, ExternalDownloader.Of(@"C:\Program Files\JDownloader\JDownloader2.exe"));
        Assert.Equal([File.AbsoluteUri], ExternalDownloader.Arguments(ExternalDownloader.Kind.Other, File, @"C:\x", "a.mkv"));
        Assert.Equal("JDownloader2", ExternalDownloader.Name(ExternalDownloader.Kind.Other, @"C:\Program Files\JDownloader\JDownloader2.exe"));
        // IDM or FDM picked by hand still get their own command line.
        Assert.Equal(ExternalDownloader.Kind.Idm, ExternalDownloader.Of(@"D:\Tools\IDM\idman.EXE"));
        Assert.Equal(ExternalDownloader.Kind.Fdm, ExternalDownloader.Of(@"C:\Program Files\Softdeluxe\Free Download Manager\fdm.exe"));
    }

    [Fact]
    public void TheSettingRoundTrips()
    {
        foreach (var kind in Enum.GetValues<ExternalDownloader.Kind>())
            Assert.Equal(kind, ExternalDownloader.FromCode(ExternalDownloader.Code(kind)));
        Assert.Null(ExternalDownloader.FromCode(null)); // the app's own downloader
        Assert.Null(ExternalDownloader.FromCode("vlc"));
    }
}
