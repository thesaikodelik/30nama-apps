using System.Globalization;
using System.Net.Http.Headers;
using System.Text;

namespace Nama.Core;

public sealed record ApiConfig
{
    public IReadOnlyList<string> DiscoveryUrls { get; init; } =
    [
        "https://pub-ac69cf865ce145f8b4c1ed87dc2a9fb7.r2.dev/sdk-endpoint.json",
        "https://30update.s3.ir-thr-at1.arvanstorage.ir/sdk-endpoint.json",
    ];
    public IReadOnlyList<string> FallbackHosts { get; init; } =
    [
        "https://world.hana-api.com/api/v2",
        "https://ir.kazem-api.com/api/v2",
    ];
    /// <summary>WebTV client key first, Android TV key as a fallback.</summary>
    public IReadOnlyList<string> ApiKeys { get; init; } =
    [
        "83hRzBabjDnuv9SmV2GFaftpxjhZSHXYe7UnBuX5DdANdpzPvXu46jvKZHPb97As",
        "VdapnkLG9EHsSmF8yeKcb4253RAuN6YX",
    ];
    public string ClientVersion { get; init; } = "1.0.0";
    /// <summary>
    /// Cloudflare turns away a request that names no program with a challenge page; the Mac app's
    /// requests carry one by themselves, these have to be given one.
    /// </summary>
    public string UserAgent { get; init; } = "30nama (Windows NT 10.0; Win64; x64)";
    /// <summary>Waits between retries of a transient failure. Its count is the number of retries.</summary>
    public IReadOnlyList<TimeSpan> Backoff { get; init; } = [TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(3)];
    public TimeSpan RequestSpacing { get; init; } = TimeSpan.FromSeconds(0.1);
}

public sealed class ApiException : Exception
{
    public enum Kinds
    {
        /// <summary>Network failure, timeout, rate limiting or a non-JSON body. Worth retrying.</summary>
        Transient,
        /// <summary>The API answered <c>success: false</c>.</summary>
        Server,
        /// <summary>No host answered the connection test.</summary>
        Unreachable,
    }

    public Kinds Kind { get; }
    public TimeSpan RetryAfter { get; }

    public ApiException(Kinds kind, string message, TimeSpan retryAfter = default) : base(message)
    {
        Kind = kind;
        RetryAfter = retryAfter;
    }

    public static ApiException Unreachable() => new(Kinds.Unreachable, "unreachable");
}

public sealed record Envelope(bool Success, string? Message, Json Result);

/// <summary>Sends one request. Tests replace it with a scripted network.</summary>
public delegate Task<HttpResponseMessage> Transport(HttpRequestMessage request, CancellationToken cancel);

/// <summary>
/// Limits concurrency and spacing of API calls.
///
/// 30nama sits behind Cloudflare rate limiting, so every call goes through one gate: at most two
/// in flight, a short gap between starts, screen calls ahead of background ones, and a shared
/// pause after a rate-limit response.
/// </summary>
public sealed class RequestGate(int limit = 2, TimeSpan spacing = default)
{
    private readonly object _lock = new();
    private int _inFlight;
    private readonly Queue<TaskCompletionSource> _core = new();
    private readonly Queue<TaskCompletionSource> _background = new();
    private DateTime _nextStart = DateTime.MinValue;
    private DateTime _pausedUntil = DateTime.MinValue;

    public async Task AcquireAsync(bool background)
    {
        TaskCompletionSource? wait = null;
        lock (_lock)
        {
            if (_inFlight < limit) _inFlight++;
            else
            {
                wait = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
                (background ? _background : _core).Enqueue(wait);
            }
        }
        if (wait is not null) await wait.Task;
        TimeSpan delay;
        lock (_lock)
        {
            var now = DateTime.UtcNow;
            var start = new[] { now, _nextStart, _pausedUntil }.Max();
            _nextStart = start + spacing;
            delay = start - now;
        }
        if (delay > TimeSpan.Zero) await Task.Delay(delay);
    }

    /// <summary>Hands the slot to the next waiter, screen calls first.</summary>
    public void Release()
    {
        lock (_lock)
        {
            if (_core.TryDequeue(out var next) || _background.TryDequeue(out next)) next.SetResult();
            else _inFlight--;
        }
    }

    public void Pause(TimeSpan duration)
    {
        lock (_lock)
        {
            var until = DateTime.UtcNow + duration;
            if (until > _pausedUntil) _pausedUntil = until;
        }
    }
}

/// <summary>
/// RPC client for the 30nama API: <c>POST {host}/action/{name}</c> with a form body, answered by a
/// <c>{success, msg, result}</c> envelope.
/// </summary>
public sealed class ApiClient
{
    public ApiConfig Config { get; }
    private readonly Transport _transport;
    private readonly RequestGate _gate;
    private readonly object _lock = new();

    private string? _host;
    private string _key;
    private string _token = "";
    private string _language = Lang.En.Catalogue();
    private Action? _onSessionExpired;
    private int _checkingSession;

    /// <summary>Actions that must not be replayed.</summary>
    private static readonly HashSet<string> NoRetry = ["observer", "logout", "qrlogin_login"];

    public ApiClient(ApiConfig? config = null, Transport? transport = null)
    {
        Config = config ?? new ApiConfig();
        _key = Config.ApiKeys[0];
        _gate = new RequestGate(spacing: Config.RequestSpacing);
        _transport = transport ?? HttpTransport();
    }

    /// <summary>
    /// The network, through HttpClient, with the TLS handshake made by the app's own SslStream.
    ///
    /// Cloudflare in front of the API answers HttpClient's own HTTPS connections with a challenge
    /// page (seen on Windows 11 with .NET 8, every header and HTTP version alike), while the same
    /// request over an SslStream set up directly goes through. So each connection is opened and
    /// encrypted here, and HttpClient is handed it as a plain one: the request goes out as
    /// <c>http://host:443</c> with the Host header put back.
    /// </summary>
    public static Transport HttpTransport()
    {
        var handler = new SocketsHttpHandler
        {
            AutomaticDecompression = System.Net.DecompressionMethods.All,
            PooledConnectionIdleTimeout = TimeSpan.FromSeconds(60),
            ConnectCallback = async (context, cancel) =>
            {
                var socket = new System.Net.Sockets.Socket(System.Net.Sockets.SocketType.Stream, System.Net.Sockets.ProtocolType.Tcp) { NoDelay = true };
                try
                {
                    await socket.ConnectAsync(context.DnsEndPoint, cancel);
                    var stream = new System.Net.Sockets.NetworkStream(socket, ownsSocket: true);
                    if (context.InitialRequestMessage.Options.TryGetValue(Secure, out var secure) && secure)
                    {
                        var tls = new System.Net.Security.SslStream(stream);
                        await tls.AuthenticateAsClientAsync(new System.Net.Security.SslClientAuthenticationOptions
                        {
                            TargetHost = context.DnsEndPoint.Host,
                            ApplicationProtocols = [System.Net.Security.SslApplicationProtocol.Http11],
                        }, cancel);
                        return tls;
                    }
                    return stream;
                }
                catch
                {
                    socket.Dispose();
                    throw;
                }
            },
        };
        var client = new HttpClient(handler) { Timeout = Timeout.InfiniteTimeSpan };
        return (request, cancel) =>
        {
            if (request.RequestUri is { Scheme: "https" } url)
            {
                request.RequestUri = new UriBuilder(url) { Scheme = "http", Port = url.Port }.Uri;
                request.Headers.Host = url.IsDefaultPort ? url.Host : url.Authority;
                request.Options.Set(Secure, true);
            }
            return client.SendAsync(request, cancel);
        };
    }

    /// <summary>Marks a request whose connection the transport has to encrypt itself.</summary>
    private static readonly HttpRequestOptionsKey<bool> Secure = new("nama.secure");

    public string? Host { get { lock (_lock) return _host; } }

    public void SetToken(string? token) { lock (_lock) _token = token ?? ""; }
    public void SetLanguage(Lang lang) { lock (_lock) _language = lang.Catalogue(); }
    public void SetSessionExpiredHandler(Action? handler) { lock (_lock) _onSessionExpired = handler; }
    public void UseHost(string host) { lock (_lock) _host = host; }

    // MARK: Endpoint discovery

    /// <summary>
    /// Finds a working host and key: fetch the published endpoint list (falling back to the
    /// built-in one), then run the <c>test</c> action against every host and take the first that answers.
    /// </summary>
    public async Task<string> BootstrapAsync(string? cachedHost = null)
    {
        var hosts = new List<string>();
        foreach (var h in (await DiscoverHostsAsync()).Concat(Config.FallbackHosts))
        {
            if (!hosts.Contains(h)) hosts.Add(h);
        }
        if (cachedHost is not null && !hosts.Contains(cachedHost)) hosts.Insert(0, cachedHost);

        foreach (var candidate in Config.ApiKeys)
        {
            using var stop = new CancellationTokenSource();
            var tests = hosts.Select(async h =>
            {
                try
                {
                    var e = await SendAsync(h, candidate, "test", new Dictionary<string, string>(), "", TimeSpan.FromSeconds(8), stop.Token);
                    return e.Success ? h : null;
                }
                catch (Exception) { return null; }
            }).ToList();
            while (tests.Count > 0)
            {
                var done = await Task.WhenAny(tests);
                tests.Remove(done);
                if (await done is not { } winner) continue;
                stop.Cancel();
                lock (_lock) { _host = winner; _key = candidate; }
                return winner;
            }
        }
        throw ApiException.Unreachable();
    }

    private async Task<List<string>> DiscoverHostsAsync()
    {
        var stamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
        using var stop = new CancellationTokenSource();
        var lookups = Config.DiscoveryUrls.Select(async url =>
        {
            try
            {
                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(stop.Token);
                timeout.CancelAfter(TimeSpan.FromSeconds(6));
                using var request = new HttpRequestMessage(HttpMethod.Get, $"{url}?t={stamp}");
                request.Headers.TryAddWithoutValidation("User-Agent", Config.UserAgent);
                using var response = await _transport(request, timeout.Token);
                var list = Json.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token));
                return list.Array.Select(x => x["host"].Text).OfType<string>().ToList();
            }
            catch (Exception) { return []; }
        }).ToList();
        while (lookups.Count > 0)
        {
            var done = await Task.WhenAny(lookups);
            lookups.Remove(done);
            var list = await done;
            if (list.Count == 0) continue;
            stop.Cancel();
            return list;
        }
        return [];
    }

    // MARK: Calls

    /// <summary>
    /// Runs an action and returns its envelope, including unsuccessful ones. Throws only when the
    /// server could not be reached or kept answering with something that is not the API.
    /// </summary>
    public async Task<Envelope> CallAsync(string action, IReadOnlyDictionary<string, string>? parameters = null, bool background = false)
    {
        string host, key, token;
        lock (_lock)
        {
            host = _host ?? throw ApiException.Unreachable();
            key = _key;
            token = _token;
        }
        var retries = NoRetry.Contains(action) ? 0 : Config.Backoff.Count;
        for (var attempt = 0; ; attempt++)
        {
            await _gate.AcquireAsync(background);
            try
            {
                var envelope = await SendAsync(host, key, action, parameters ?? new Dictionary<string, string>(), token, TimeSpan.FromSeconds(20), CancellationToken.None);
                _gate.Release();
                if (!envelope.Success && action != "user" && IsLoginRequired(envelope.Message)) await ConfirmSessionExpiredAsync();
                return envelope;
            }
            catch (ApiException e) when (e.Kind == ApiException.Kinds.Transient && attempt < retries)
            {
                _gate.Release();
                var delay = new[] { Config.Backoff[attempt], e.RetryAfter }.Max();
                _gate.Pause(delay);
                await Task.Delay(delay);
            }
            catch
            {
                _gate.Release();
                throw;
            }
        }
    }

    /// <summary>Runs an action and returns its <c>result</c>, throwing a server error when it did not succeed.</summary>
    public async Task<Json> GetAsync(string action, IReadOnlyDictionary<string, string>? parameters = null, bool background = false)
    {
        var e = await CallAsync(action, parameters, background);
        if (!e.Success) throw new ApiException(ApiException.Kinds.Server, e.Message ?? "error");
        return e.Result;
    }

    public static bool IsLoginRequired(string? message)
    {
        if (message is null) return false;
        var m = message.ToLowerInvariant();
        return m.Contains("login required") || m.Contains("وارد حساب کاربری");
    }

    /// <summary>
    /// "Login required" on an action can also mean "this needs a subscription". Only treat the
    /// session as gone when the profile call says so too.
    /// </summary>
    private async Task ConfirmSessionExpiredAsync()
    {
        string? host, key, token;
        lock (_lock) { host = _host; key = _key; token = _token; }
        if (token.Length == 0 || host is null || Interlocked.Exchange(ref _checkingSession, 1) == 1) return;
        try
        {
            Envelope e;
            try { e = await SendAsync(host, key, "user", new Dictionary<string, string>(), token, TimeSpan.FromSeconds(20), CancellationToken.None); }
            catch (Exception) { return; }
            if (e.Success || !IsLoginRequired(e.Message)) return;
            Action? handler;
            lock (_lock) { _token = ""; handler = _onSessionExpired; }
            handler?.Invoke();
        }
        finally { Interlocked.Exchange(ref _checkingSession, 0); }
    }

    private async Task<Envelope> SendAsync(string host, string key, string action, IReadOnlyDictionary<string, string> parameters, string token, TimeSpan timeout, CancellationToken cancel)
    {
        if (!Uri.TryCreate(host + "/action/" + action, UriKind.Absolute, out var url)) throw ApiException.Unreachable();
        string language;
        lock (_lock) language = _language;
        using var request = new HttpRequestMessage(HttpMethod.Post, url);
        request.Headers.TryAddWithoutValidation("User-Agent", Config.UserAgent);
        request.Headers.TryAddWithoutValidation("c-api-key", key);
        request.Headers.TryAddWithoutValidation("c-token", token);
        request.Headers.TryAddWithoutValidation("c-version-number", Config.ClientVersion);
        request.Headers.TryAddWithoutValidation("c-language", language);
        request.Content = new ByteArrayContent(Encoding.UTF8.GetBytes(FormEncode(parameters)));
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/x-www-form-urlencoded");

        byte[] data;
        int status;
        string? retryAfterHeader;
        try
        {
            using var limit = CancellationTokenSource.CreateLinkedTokenSource(cancel);
            limit.CancelAfter(timeout);
            using var response = await _transport(request, limit.Token);
            data = await response.Content.ReadAsByteArrayAsync(limit.Token);
            status = (int)response.StatusCode;
            retryAfterHeader = response.Headers.TryGetValues("Retry-After", out var values) ? values.FirstOrDefault() : null;
        }
        catch (Exception e) when (e is not ApiException)
        {
            throw new ApiException(ApiException.Kinds.Transient, $"{action}: {e.Message}");
        }
        // A progress report is answered with nothing at all: accepted, with nothing to say.
        if (data.Length == 0 && status is >= 200 and < 300) return new Envelope(true, null, Json.Null);
        Json? json = null;
        try { json = Json.Parse(data); } catch (Exception) { }
        if (json?.Object is null)
        {
            // Cloudflare answers rate limiting and outages with HTML or plain text.
            var seconds = double.TryParse(retryAfterHeader, NumberStyles.Float, CultureInfo.InvariantCulture, out var s) ? Math.Min(s, 10) : 0;
            throw new ApiException(ApiException.Kinds.Transient, $"{action}: HTTP {status}, not JSON", TimeSpan.FromSeconds(seconds));
        }
        return new Envelope(json["success"].Bool, json["msg"].Text, json["result"]);
    }

    /// <summary>Form fields in key order, escaped as RFC 3986 asks.</summary>
    public static string FormEncode(IReadOnlyDictionary<string, string> parameters) =>
        string.Join('&', parameters.OrderBy(p => p.Key, StringComparer.Ordinal).Select(p => Uri.EscapeDataString(p.Key) + "=" + Uri.EscapeDataString(p.Value)));
}
