using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class DownloadHoursTests
{
    private static DateTime At(int hour, int minute = 0) => new(2026, 10, 17, hour, minute, 0, DateTimeKind.Local);

    [Fact]
    public void AnyTimeWithoutHours()
    {
        Assert.True(DownloadHours.Open(null, null, At(15)));
        Assert.True(DownloadHours.Open(3, 3, At(15))); // the same hour twice is no limit
    }

    [Fact]
    public void WithinTheDay()
    {
        Assert.False(DownloadHours.Open(2, 8, At(1, 59)));
        Assert.True(DownloadHours.Open(2, 8, At(2)));
        Assert.True(DownloadHours.Open(2, 8, At(7, 59)));
        Assert.False(DownloadHours.Open(2, 8, At(8))); // up to the hour, not through it
    }

    [Fact]
    public void AcrossMidnight()
    {
        Assert.True(DownloadHours.Open(23, 7, At(23, 30)));
        Assert.True(DownloadHours.Open(23, 7, At(0)));
        Assert.True(DownloadHours.Open(23, 7, At(6, 59)));
        Assert.False(DownloadHours.Open(23, 7, At(7)));
        Assert.False(DownloadHours.Open(23, 7, At(12)));
    }
}
