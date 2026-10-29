namespace Nama.Core;

/// <summary>What came of trying a key.</summary>
public enum KeyState { Unset, Checking, Works, Rejected, Unreachable }

/// <summary>
/// A key is saved first and tried after, with one light request: from Iran the service may be out of
/// reach while the key is fine, and that must not throw the key away. As the Android app's
/// KeyCheck, learnt from 30namaTV.
/// </summary>
public static class KeyCheck
{
    /// <summary>The light request that tells whether <paramref name="key"/> works for <paramref name="service"/>.</summary>
    public static HttpRequestMessage? Request(string service, string key)
    {
        switch (service)
        {
            case "tmdb":
                return TMDB.Request("configuration", null, key);
            case "subsource":
                var request = new HttpRequestMessage(HttpMethod.Get, SubSource.SearchUrl(new SubSource.Query("Inception", 2010, "tt1375666", false, null)));
                request.Headers.TryAddWithoutValidation("X-API-Key", key);
                return request;
            case "gemini":
                var models = new HttpRequestMessage(HttpMethod.Get, Gemini.ModelsAddress);
                models.Headers.TryAddWithoutValidation("x-goog-api-key", key);
                return models;
            default:
                return null;
        }
    }

    /// <summary>What an answer's status says of the key: a refusal is the key, anything else the service.</summary>
    public static KeyState Read(int status, string service = "") => status switch
    {
        >= 200 and < 300 => KeyState.Works,
        401 or 403 => KeyState.Rejected,
        // Gemini answers a wrong key with 400 API_KEY_INVALID.
        400 when service == "gemini" => KeyState.Rejected,
        _ => KeyState.Unreachable,
    };

    public static async Task<KeyState> CheckAsync(Transport transport, string service, string key)
    {
        if (Request(service, key) is not { } request) return KeyState.Unset;
        try
        {
            using (request)
            {
                using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(12));
                using var response = await transport(request, timeout.Token);
                return Read((int)response.StatusCode, service);
            }
        }
        catch (Exception) { return KeyState.Unreachable; }
    }
}
