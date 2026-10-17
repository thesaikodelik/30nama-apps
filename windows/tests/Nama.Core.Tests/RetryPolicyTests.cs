using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class RetryPolicyTests
{
    [Fact]
    public void WaitsLongerEachTime()
    {
        Assert.Equal(TimeSpan.FromSeconds(2), RetryPolicy.Next(1, null));
        Assert.Equal(TimeSpan.FromSeconds(4), RetryPolicy.Next(2, 503));
        Assert.Equal(TimeSpan.FromSeconds(16), RetryPolicy.Next(4, null));
        Assert.Equal(TimeSpan.FromSeconds(1.5), RetryPolicy.Next(1, null, jitter: 0)); // a little either way, so many downloads do not knock at once
        Assert.Null(RetryPolicy.Next(RetryPolicy.Attempts, null)); // and then gives up
    }

    [Fact]
    public void ARefusalIsNotTriedAgain()
    {
        Assert.Null(RetryPolicy.Next(1, 403)); // an expired link
        Assert.Null(RetryPolicy.Next(1, 404));
        Assert.NotNull(RetryPolicy.Next(1, 429));
        Assert.NotNull(RetryPolicy.Next(1, 408));
    }

    [Fact]
    public void HonoursTheServersOwnWait()
    {
        Assert.Equal(TimeSpan.FromSeconds(30), RetryPolicy.Next(1, 429, TimeSpan.FromSeconds(30)));
        Assert.Equal(TimeSpan.FromMinutes(2), RetryPolicy.Next(1, 503, TimeSpan.FromHours(1))); // within reason
    }
}
