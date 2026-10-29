using System.Security.Cryptography;
using System.Text;

namespace Nama.Core;

/// <summary>
/// Subtitles translated by a language model (Google's Gemini, with the viewer's own key), for an
/// item that has no Persian subtitle anywhere. The film starts at once with the original lines:
/// batches are translated from where the viewer is, onwards, then the part before, and each
/// replaces its lines as it arrives. As Translation.kt of the Android app.
/// </summary>
public static class Translation
{
    /// <summary>Lines per request: enough context for names and tone, small enough to come back in seconds.</summary>
    public const int Batch = 80;

    /// <summary>The batch that holds the line on screen at <paramref name="t"/> seconds, or the next one.</summary>
    public static int BatchAt(IReadOnlyList<Cue> cues, double t)
    {
        var i = cues.ToList().FindIndex(c => c.End > t);
        if (i < 0) i = Math.Max(cues.Count - 1, 0);
        return i / Batch;
    }

    /// <summary>Batches in the order to translate them: from <paramref name="start"/> to the end, then the ones before it.</summary>
    public static List<int> Order(int count, int start)
    {
        var s = Math.Clamp(start, 0, Math.Max(count - 1, 0));
        return Enumerable.Range(s, Math.Max(count - s, 0)).Concat(Enumerable.Range(0, s)).ToList();
    }

    public static List<Cue> BatchOf(IReadOnlyList<Cue> cues, int n) => cues.Skip(n * Batch).Take(Batch).ToList();

    public static int BatchCount(IReadOnlyList<Cue> cues) => (cues.Count + Batch - 1) / Batch;

    /// <summary>A line break inside a cue, written so the model keeps it.</summary>
    private const string Break = " | ";

    /// <summary>
    /// The generateContent body: the lines as a JSON array, and an answer asked for as a JSON array
    /// of the same length, so each translation lands on its own line and a missing one shows.
    /// </summary>
    public static string Request(IReadOnlyList<string> lines, Lang target, string title)
    {
        var language = target == Lang.Ar ? "Arabic" : "Persian (Farsi), as spoken in Iran";
        var instruction = $"You translate film and TV subtitles into {language}. The title is \"{title}\". "
            + $"Translate each string of the JSON array the user sends, in order, into natural, spoken {language} that fits on screen. "
            + $"Keep names as they are said. \"{Break}\" marks a line break; keep it. "
            + $"Answer with a JSON array of exactly {lines.Count} strings, one per input string, and nothing else.";
        static Json Obj(params (string Key, Json Value)[] fields) => new(fields.ToDictionary(f => f.Key, f => f.Value));
        static Json Arr(params Json[] items) => new(items);
        var sent = new Json(lines.Select(l => new Json(l.Replace("\n", Break))).ToList()).Serialize();
        return Obj(
            ("systemInstruction", Obj(("parts", Arr(Obj(("text", new Json(instruction))))))),
            ("contents", Arr(Obj(("role", new Json("user")), ("parts", Arr(Obj(("text", new Json(sent)))))))),
            ("generationConfig", Obj(
                ("temperature", new Json(0.3)),
                ("responseMimeType", new Json("application/json")),
                ("responseSchema", Obj(("type", new Json("ARRAY")), ("items", Obj(("type", new Json("STRING"))))))))
        ).Serialize();
    }

    /// <summary>
    /// The translated lines from a generateContent answer, one per line sent; a line that did not
    /// come back is null and keeps its original. Null for an answer that is not a list at all.
    /// </summary>
    public static List<string?>? Read(Json answer, int count)
    {
        var text = string.Concat(answer["candidates"][0]["content"]["parts"].Array.Select(p => p["text"].String ?? "")).Trim();
        if (text.StartsWith("```json")) text = text["```json".Length..];
        else if (text.StartsWith("```")) text = text[3..];
        if (text.EndsWith("```")) text = text[..^3];
        IReadOnlyList<Json> list;
        try
        {
            var parsed = Json.Parse(text);
            if (parsed.Type != Json.Kind.Array) return null;
            list = parsed.Array;
        }
        catch (Exception) { return null; }
        if (list.Count == 0) return null;
        return Enumerable.Range(0, count).Select(i => i < list.Count && list[i].Text is { } line
            ? string.Join('\n', line.Replace(Break.Trim(), "\n").Split('\n').Select(l => l.Trim()))
            : null).ToList();
    }

    /// <summary><paramref name="cues"/> with the translated <paramref name="lines"/> of batch <paramref name="n"/> put in.</summary>
    public static List<Cue> Apply(IReadOnlyList<Cue> cues, int n, IReadOnlyList<string?> lines)
    {
        var output = cues.ToList();
        for (var i = 0; i < lines.Count; i++)
        {
            var at = n * Batch + i;
            if (lines[i] is { } line && at < output.Count) output[at] = output[at] with { Text = line };
        }
        return output;
    }

    /// <summary>The target language: Arabic for an Arabic interface, Persian otherwise.</summary>
    public static Lang Target(Lang lang) => lang == Lang.Ar ? Lang.Ar : Lang.Fa;
}

/// <summary>
/// Google's Gemini API, with the key the viewer made in their own Google AI Studio account; the app
/// carries none. Its free tier costs nothing, within a daily number of requests, and a film is about
/// twenty. The "-latest" names follow Google's newest model of each kind, so a retired model does
/// not break the feature. Translated batches are kept on disk, so a film is translated once. As
/// Gemini.kt of the Android app.
/// </summary>
public sealed class Gemini(Transport transport, string folder, Func<TimeSpan, Task>? wait = null)
{
    public abstract record Outcome
    {
        public sealed record Lines(IReadOnlyList<string?> Text) : Outcome;
        /// <summary>The key was refused.</summary>
        public sealed record Rejected : Outcome;
        /// <summary>The free requests for today are spent, or the service could not be reached.</summary>
        public sealed record Unavailable : Outcome;
    }

    /// <summary>The light model first: quick, and the most free requests a day.</summary>
    public static readonly string[] Models = ["gemini-flash-lite-latest", "gemini-flash-latest"];

    public const string ModelsAddress = "https://generativelanguage.googleapis.com/v1beta/models";

    private readonly Func<TimeSpan, Task> _wait = wait ?? (t => Task.Delay(t));

    /// <summary>One batch, from the disk if it was translated before. <paramref name="source"/> names the subtitle file.</summary>
    public async Task<Outcome> TranslateAsync(string key, string source, Lang target, string title, int n, IReadOnlyList<Cue> cues)
    {
        var file = Path.Combine(folder, $"{Digest($"{source}|{target.Code()}")}-{n}.json");
        try
        {
            if (File.Exists(file) && Json.Parse(await File.ReadAllTextAsync(file)).Array is { } saved && saved.Count == cues.Count)
                return new Outcome.Lines(saved.Select(s => s.String).ToList());
        }
        catch (Exception) { }
        var body = Translation.Request(cues.Select(c => c.Text).ToList(), target, title);
        foreach (var model in Models)
        {
            // A busy minute is waited out twice; a refusal or a spent day is not.
            for (var attempt = 0; attempt < 3; attempt++)
            {
                int code;
                Json? answer = null;
                try
                {
                    using var request = new HttpRequestMessage(HttpMethod.Post, $"{ModelsAddress}/{model}:generateContent")
                    {
                        Content = new StringContent(body, Encoding.UTF8, "application/json"),
                    };
                    request.Headers.TryAddWithoutValidation("x-goog-api-key", key);
                    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(90));
                    using var response = await transport(request, timeout.Token);
                    code = (int)response.StatusCode;
                    try { answer = Json.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token)); } catch (Exception) { }
                }
                catch (Exception) { code = 0; }
                var said = answer?.ToString() ?? "";
                if (code is >= 200 and < 300)
                {
                    if (answer is null || Translation.Read(answer, cues.Count) is not { } lines) continue;
                    try
                    {
                        Directory.CreateDirectory(folder);
                        await File.WriteAllTextAsync(file, new Json(lines.Select(l => l is null ? Json.Null : new Json(l)).ToList()).Serialize());
                    }
                    catch (Exception) { }
                    return new Outcome.Lines(lines);
                }
                if (code == 400 && said.Contains("API_KEY_INVALID") || code is 401 or 403) return new Outcome.Rejected();
                // This model is not offered to this key: the next one.
                if (code == 404) break;
                if (code == 429 && attempt < 2 && !said.Contains("PerDay")) await _wait(TimeSpan.FromSeconds(30));
                else if (code is 429 or 0) return new Outcome.Unavailable();
                else if (attempt < 2) await _wait(TimeSpan.FromSeconds(5));
            }
        }
        return new Outcome.Unavailable();
    }

    private static string Digest(string s) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(s))[..12]).ToLowerInvariant();
}
