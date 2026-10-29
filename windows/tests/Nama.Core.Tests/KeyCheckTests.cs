using System.Net;
using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class KeyCheckTests
{
    private static Transport Answer(HttpStatusCode status, List<HttpRequestMessage>? asked = null) => (request, _) =>
    {
        asked?.Add(request);
        return Task.FromResult(new HttpResponseMessage(status));
    };

    [Fact]
    public async Task ARefusalIsTheKeyAnythingElseTheService()
    {
        var asked = new List<HttpRequestMessage>();
        Assert.Equal(KeyState.Works, await KeyCheck.CheckAsync(Answer(HttpStatusCode.OK, asked), "tmdb", "abc"));
        Assert.Contains("/3/configuration", asked[0].RequestUri!.AbsolutePath);
        Assert.Equal(KeyState.Rejected, await KeyCheck.CheckAsync(Answer(HttpStatusCode.Unauthorized), "tmdb", "abc"));
        Assert.Equal(KeyState.Rejected, await KeyCheck.CheckAsync(Answer(HttpStatusCode.Forbidden), "subsource", "abc"));
        Assert.Equal(KeyState.Unreachable, await KeyCheck.CheckAsync(Answer(HttpStatusCode.BadGateway), "tmdb", "abc"));
        Assert.Equal(KeyState.Unset, await KeyCheck.CheckAsync(Answer(HttpStatusCode.OK), "other", "abc"));
    }

    [Fact]
    public async Task NoAnswerKeepsTheKey()
    {
        // From Iran the service may be filtered while the key is fine: unreachable, not wrong.
        Transport down = (_, _) => throw new HttpRequestException("no route");
        Assert.Equal(KeyState.Unreachable, await KeyCheck.CheckAsync(down, "subsource", "abc"));
    }

    [Fact]
    public void SubSourceIsAskedWithItsOwnHeader()
    {
        using var request = KeyCheck.Request("subsource", "k1")!;
        Assert.Equal("k1", request.Headers.GetValues("X-API-Key").Single());
    }
}
