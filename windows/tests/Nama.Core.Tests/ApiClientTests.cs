using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class ApiClientTests
{
    private const string Ok = """{"success":true,"msg":null,"result":{"posts":[]}}""";
    private const string LoginRequired = """{"success":false,"msg":"ابتدا وارد حساب کاربری خود شوید","result":null}""";
    private static readonly Dictionary<string, string> NoHeaders = [];

    private static ApiConfig Config() => new()
    {
        Backoff = [TimeSpan.FromMilliseconds(20), TimeSpan.FromMilliseconds(20)],
        RequestSpacing = TimeSpan.Zero,
    };

    private static ApiClient Client(FakeNetwork net)
    {
        var api = new ApiClient(Config(), net.Transport);
        api.UseHost("https://h.example.com/api/v2");
        return api;
    }

    [Fact]
    public async Task BootstrapSkipsHostsThatDoNotAnswerTheApi()
    {
        var net = new FakeNetwork((action, host) =>
        {
            if (action == "discovery") return (0, "", NoHeaders);                        // discovery unreachable -> built-in hosts
            if (host.Contains("kazem")) return (403, "<html>blocked</html>", NoHeaders);  // geo-blocked host answers with HTML
            return (200, Ok, NoHeaders);
        });
        var api = new ApiClient(Config(), net.Transport);
        Assert.Equal("https://world.hana-api.com/api/v2", await api.BootstrapAsync());
    }

    [Fact]
    public async Task TheIranianHostSaysTheUserIsInIran()
    {
        // Only the Iranian host answers, as from inside Iran.
        var inside = new FakeNetwork((action, host) =>
        {
            if (action == "discovery") return (200, """[{"location":"WORLD","host":"https://w.example.com/api/v2"},{"location":"IR","host":"https://i.example.com/api/v2"}]""", NoHeaders);
            return host == "i.example.com" ? (200, Ok, NoHeaders) : (0, "", NoHeaders);
        });
        var there = new ApiClient(Config(), inside.Transport);
        await there.BootstrapAsync();
        Assert.True(there.InIran);
        // Abroad the Iranian host turns the request away.
        var outside = new FakeNetwork((action, host) =>
        {
            if (action == "discovery") return (0, "", NoHeaders);
            if (host.Contains("kazem")) return (403, "<html>blocked</html>", NoHeaders);
            return (200, Ok, NoHeaders);
        });
        var here = new ApiClient(Config(), outside.Transport);
        await here.BootstrapAsync();
        Assert.False(here.InIran);
    }

    [Fact]
    public async Task BootstrapPrefersPublishedEndpointList()
    {
        var net = new FakeNetwork((action, host) =>
        {
            if (action == "discovery") return (200, """[{"location":"X","host":"https://new.example.com/api/v2"}]""", NoHeaders);
            return host == "new.example.com" ? (200, Ok, NoHeaders) : (0, "", NoHeaders);
        });
        var api = new ApiClient(Config(), net.Transport);
        Assert.Equal("https://new.example.com/api/v2", await api.BootstrapAsync());
    }

    [Fact]
    public async Task BootstrapFailsWhenNothingAnswers()
    {
        var net = new FakeNetwork((_, _) => (0, "", NoHeaders));
        var e = await Assert.ThrowsAsync<ApiException>(() => new ApiClient(Config(), net.Transport).BootstrapAsync());
        Assert.Equal(ApiException.Kinds.Unreachable, e.Kind);
    }

    [Fact]
    public async Task RequestCarriesHeadersAndFormBody()
    {
        var net = new FakeNetwork((_, _) => (200, Ok, NoHeaders));
        var api = Client(net);
        api.SetToken("tok");
        api.SetLanguage(Lang.En);
        await api.GetAsync("search", new Dictionary<string, string> { ["query"] = "breaking bad & co", ["page"] = "1" });
        var last = net.Last!;
        Assert.Equal("search", last.Action);
        Assert.Equal("page=1&query=breaking%20bad%20%26%20co", last.Body);
        Assert.Equal("tok", last.Headers["c-token"]);
        Assert.Equal("en", last.Headers["c-language"]);
        Assert.Equal("1.0.0", last.Headers["c-version-number"]);
        Assert.Equal(new ApiConfig().ApiKeys[0], last.Headers["c-api-key"]);
        Assert.StartsWith("30nama", last.Headers["User-Agent"]); // without one, Cloudflare answers with a challenge page
    }

    [Fact]
    public async Task RateLimitIsRetriedThenSucceeds()
    {
        var remaining = new Counter(2);
        var net = new FakeNetwork((_, _) => remaining.Take()
            ? (429, "error code: 1015", new Dictionary<string, string> { ["Retry-After"] = "0" })
            : (200, Ok, NoHeaders));
        await Client(net).GetAsync("single", new Dictionary<string, string> { ["post_id"] = "1" });
        Assert.Equal(["single", "single", "single"], net.Calls);
    }

    [Fact]
    public async Task PersistentFailureGivesUpAfterRetries()
    {
        var net = new FakeNetwork((_, _) => (502, "<html>502</html>", NoHeaders));
        var e = await Assert.ThrowsAsync<ApiException>(() => Client(net).GetAsync("single"));
        Assert.Equal(ApiException.Kinds.Transient, e.Kind);
        Assert.Equal(3, net.Calls.Count); // first try plus two retries
    }

    [Fact]
    public async Task ProgressReportsAreNeverReplayed()
    {
        var net = new FakeNetwork((_, _) => (0, "", NoHeaders));
        await Assert.ThrowsAsync<ApiException>(() => Client(net).CallAsync("observer", new Dictionary<string, string> { ["time"] = "10" }));
        Assert.Equal(["observer"], net.Calls);
    }

    [Theory]
    [InlineData(200)]
    [InlineData(204)]
    public async Task AnEmptyAnswerToAProgressReportIsASuccess(int status)
    {
        var net = new FakeNetwork((_, _) => (status, "", NoHeaders));
        var answer = await Client(net).CallAsync("observer", new Dictionary<string, string> { ["time"] = "30" });
        Assert.True(answer.Success);
        Assert.Equal(["observer"], net.Calls);
    }

    [Fact]
    public async Task ServerErrorSurfacesItsMessage()
    {
        var net = new FakeNetwork((_, _) => (200, """{"success":false,"msg":"nope","result":null}""", NoHeaders));
        var e = await Assert.ThrowsAsync<ApiException>(() => Client(net).GetAsync("single"));
        Assert.Equal(ApiException.Kinds.Server, e.Kind);
        Assert.Equal("nope", e.Message);
        Assert.Equal(["single"], net.Calls); // a real API answer is not retried
    }

    [Fact]
    public async Task SessionExpiryIsConfirmedAgainstTheProfile()
    {
        var expired = new Counter(0);
        // The stream says "login required" but the profile still loads: a subscription problem, not a dead session.
        var net = new FakeNetwork((action, _) => action == "user" ? (200, Ok, NoHeaders) : (200, LoginRequired, NoHeaders));
        var api = Client(net);
        api.SetToken("tok");
        api.SetSessionExpiredHandler(() => expired.Add());
        var e = await api.CallAsync("stream", new Dictionary<string, string> { ["post_id"] = "1" });
        Assert.False(e.Success);
        Assert.Equal(["stream", "user"], net.Calls);
        Assert.Equal(0, expired.Value);

        // The profile refuses too: the session is gone.
        net = new FakeNetwork((_, _) => (200, LoginRequired, NoHeaders));
        api = Client(net);
        api.SetToken("tok");
        api.SetSessionExpiredHandler(() => expired.Add());
        await api.CallAsync("stream", new Dictionary<string, string> { ["post_id"] = "1" });
        Assert.Equal(1, expired.Value);

        // A guest is never "expired".
        net = new FakeNetwork((_, _) => (200, LoginRequired, NoHeaders));
        api = Client(net);
        api.SetSessionExpiredHandler(() => expired.Add());
        await api.CallAsync("continue_watching");
        Assert.Equal(["continue_watching"], net.Calls);
        Assert.Equal(1, expired.Value);
    }

    [Fact]
    public async Task GateLimitsConcurrencyAndServesScreenCallsFirst()
    {
        var gate = new RequestGate(limit: 2, spacing: TimeSpan.Zero);
        var order = new List<string>();
        var peak = new Counter(0);
        var now = new Counter(0);

        // Fill both slots, then queue background work followed by one screen call.
        await gate.AcquireAsync(background: false);
        await gate.AcquireAsync(background: false);
        async Task Work(string name, bool background)
        {
            await gate.AcquireAsync(background);
            lock (order) order.Add(name);
            peak.Raise(now.Add());
            await Task.Delay(20);
            now.Sub();
            gate.Release(background);
        }
        var tasks = Enumerable.Range(0, 4).Select(i => Work($"bg{i}", true)).ToList();
        await Task.Delay(50);
        tasks.Add(Work("core", false));
        await Task.Delay(50);
        gate.Release();
        await Task.Delay(10);
        gate.Release();
        await Task.WhenAll(tasks);

        Assert.Equal("core", order[0]); // screen call jumps the background queue
        Assert.Equal(5, order.Count);
        Assert.True(peak.Value <= 2);
    }

    // As in the Android app: background work never holds every slot, so a page is not kept waiting.
    [Fact]
    public async Task BackgroundCallsLeaveASlotForTheScreen()
    {
        var gate = new RequestGate(limit: 2, spacing: TimeSpan.Zero);
        await gate.AcquireAsync(background: true);
        var second = gate.AcquireAsync(background: true);
        await Task.Delay(20);
        Assert.False(second.IsCompleted); // a second background call waits
        var screen = gate.AcquireAsync(background: false);
        Assert.Same(screen, await Task.WhenAny(screen, Task.Delay(1000)));
        gate.Release(background: false);
        gate.Release(background: true);
        Assert.Same(second, await Task.WhenAny(second, Task.Delay(1000)));
        gate.Release(background: true);
    }

    // As in the Android app: a rate limit pauses the calls after it even when it is not retried.
    [Fact]
    public async Task ARateLimitPausesTheGateEvenWithoutARetry()
    {
        var net = new FakeNetwork((action, _) => action == "observer" ? (429, "error code: 1015", NoHeaders) : (200, Ok, NoHeaders));
        var api = new ApiClient(new ApiConfig { Backoff = [TimeSpan.FromMilliseconds(300), TimeSpan.FromMilliseconds(300)], RequestSpacing = TimeSpan.Zero }, net.Transport);
        api.UseHost("https://h.example.com/api/v2");
        // A progress report is never replayed, so it fails at once; the gate still waits before the next call.
        var e = await Assert.ThrowsAsync<ApiException>(() => api.CallAsync("observer", new Dictionary<string, string> { ["time"] = "10" }));
        Assert.True(e.RateLimited);
        var clock = System.Diagnostics.Stopwatch.StartNew();
        await api.CallAsync("profile");
        Assert.True(clock.Elapsed >= TimeSpan.FromMilliseconds(250), "the next call waited out the pause");
    }
}
