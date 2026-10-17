using System.Net;
using System.Text;
using Nama.Core;

namespace Nama.Core.Tests;

internal static class Fixtures
{
    public static Json Load(string name) => Json.Parse(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "Fixtures", name + ".json")));

    /// <summary>A file of the Swift side of the repository, found by walking up from the test output.</summary>
    public static string RepoFile(string relative)
    {
        for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir is not null; dir = dir.Parent)
        {
            var path = Path.Combine(dir.FullName, relative);
            if (File.Exists(path)) return path;
        }
        throw new FileNotFoundException(relative);
    }
}

/// <summary>Scripted network: answers each request from a handler and records what was asked.</summary>
internal sealed class FakeNetwork(Func<string, string, (int Status, string Body, Dictionary<string, string> Headers)> handler)
{
    public sealed record Call(string Action, string Host, Dictionary<string, string> Headers, string Body);

    private readonly object _lock = new();
    private readonly List<Call> _log = [];

    public List<string> Calls { get { lock (_lock) return _log.Select(c => c.Action).ToList(); } }
    public Call? Last { get { lock (_lock) return _log.LastOrDefault(); } }

    public Transport Transport => async (request, cancel) =>
    {
        var url = request.RequestUri!;
        var action = url.AbsolutePath.Contains("/action/") ? url.Segments[^1] : "discovery";
        var body = request.Content is null ? "" : await request.Content.ReadAsStringAsync(cancel);
        var headers = request.Headers.ToDictionary(h => h.Key, h => string.Join(",", h.Value));
        (int Status, string Body, Dictionary<string, string> Headers) answer;
        lock (_lock)
        {
            _log.Add(new Call(action, url.Host, headers, body));
            answer = handler(action, url.Host);
        }
        if (answer.Status == 0) throw new HttpRequestException("timed out");
        var response = new HttpResponseMessage((HttpStatusCode)answer.Status) { Content = new ByteArrayContent(Encoding.UTF8.GetBytes(answer.Body)) };
        foreach (var (k, v) in answer.Headers) response.Headers.TryAddWithoutValidation(k, v);
        return response;
    };
}

internal sealed class Counter(int n)
{
    private readonly object _lock = new();
    private int _n = n;
    public int Value { get { lock (_lock) return _n; } }
    /// <summary>Decrements while positive; true if it did.</summary>
    public bool Take() { lock (_lock) { if (_n > 0) { _n--; return true; } return false; } }
    public int Add() { lock (_lock) return ++_n; }
    public void Sub() { lock (_lock) _n--; }
    public void Raise(int v) { lock (_lock) _n = Math.Max(_n, v); }
}
