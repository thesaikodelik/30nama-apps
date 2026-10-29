using System.Net;
using System.Text;
using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class TranslationTests
{
    private static readonly List<Cue> Cues = Enumerable.Range(0, 200).Select(i => new Cue(i * 3.0, i * 3.0 + 2, $"line {i}")).ToList();

    [Fact]
    public void StartsWhereTheViewerIsThenGoesBack()
    {
        Assert.Equal(3, Translation.BatchCount(Cues));
        var at = Translation.BatchAt(Cues, 90 * 3.0);
        Assert.Equal(1, at);
        Assert.Equal([1, 2, 0], Translation.Order(3, at));
        Assert.Equal([0, 1, 2], Translation.Order(3, 0));
        Assert.Equal(2, Translation.BatchAt(Cues, 10_000)); // past the last line, the last batch
        Assert.Equal(40, Translation.BatchOf(Cues, 2).Count);
    }

    [Fact]
    public void TheRequestAsksForOneStringPerLine()
    {
        var body = Json.Parse(Translation.Request(["Hello.", "Two\nlines"], Lang.Fa, "Heat"));
        var sent = Json.Parse(body["contents"][0]["parts"][0]["text"].String!).Array.Select(s => s.String).ToList();
        Assert.Equal(["Hello.", "Two | lines"], sent);
        Assert.Equal("ARRAY", body["generationConfig"]["responseSchema"]["type"].String);
        Assert.Contains("Persian", body["systemInstruction"]["parts"][0]["text"].String);
    }

    private static Json Answer(string text) => new(new Dictionary<string, Json>
    {
        ["candidates"] = new(new[] { new Json(new Dictionary<string, Json> { ["content"] = new(new Dictionary<string, Json> { ["parts"] = new(new[] { new Json(new Dictionary<string, Json> { ["text"] = new(text) }) }) }) }) }),
    });

    [Fact]
    public void ReadsTheAnswerLineByLine()
    {
        Assert.Equal(["سلام.", "دو\nخط"], Translation.Read(Answer("""["سلام.", "دو | خط"]"""), 2)!);
        // A line that did not come back keeps its original.
        Assert.Equal(["سلام.", null], Translation.Read(Answer("""["سلام."]"""), 2)!);
        Assert.Equal(["سلام."], Translation.Read(Answer("```json\n[\"سلام.\"]\n```"), 1)!);
        Assert.Null(Translation.Read(Answer("Sorry, I can't."), 1));
        Assert.Null(Translation.Read(Json.Parse("{}"), 1));
    }

    [Fact]
    public void ABatchReplacesItsOwnLinesOnly()
    {
        var output = Translation.Apply(Cues, 1, ["یک", null]);
        Assert.Equal("یک", output[80].Text);
        Assert.Equal("line 81", output[81].Text);
        Assert.Equal("line 79", output[79].Text);
        Assert.Equal(Cues[80].Start, output[80].Start);
    }

    [Fact]
    public void ArabicForAnArabicInterfacePersianOtherwise()
    {
        Assert.Equal(Lang.Ar, Translation.Target(Lang.Ar));
        Assert.Equal(Lang.Fa, Translation.Target(Lang.En));
    }

    private static Transport Server(Func<HttpRequestMessage, (HttpStatusCode, string)> answer, List<string>? asked = null) => (request, _) =>
    {
        asked?.Add(request.RequestUri!.AbsolutePath);
        var (status, body) = answer(request);
        return Task.FromResult(new HttpResponseMessage(status) { Content = new StringContent(body, Encoding.UTF8, "application/json") });
    };

    private static string Folder() => Path.Combine(Path.GetTempPath(), "nama-gemini-" + Guid.NewGuid().ToString("N"));

    [Fact]
    public async Task ABatchIsTranslatedOnceAndKept()
    {
        var asked = new List<string>();
        var folder = Folder();
        var gemini = new Gemini(Server(_ => (HttpStatusCode.OK, Answer("""["یک", "دو"]""").Serialize()), asked), folder, _ => Task.CompletedTask);
        var two = Cues.Take(2).ToList();
        var first = await gemini.TranslateAsync("key", "file.vtt", Lang.Fa, "Heat", 0, two);
        Assert.Equal(["یک", "دو"], Assert.IsType<Gemini.Outcome.Lines>(first).Text);
        var again = await gemini.TranslateAsync("key", "file.vtt", Lang.Fa, "Heat", 0, two);
        Assert.Equal(["یک", "دو"], Assert.IsType<Gemini.Outcome.Lines>(again).Text);
        Assert.Single(asked); // the second came from the disk
        Assert.EndsWith("gemini-flash-lite-latest:generateContent", asked[0]);
        Directory.Delete(folder, true);
    }

    [Fact]
    public async Task ARefusedKeyAndASpentDayStop()
    {
        var two = Cues.Take(2).ToList();
        var refused = new Gemini(Server(_ => (HttpStatusCode.BadRequest, """{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}""")), Folder(), _ => Task.CompletedTask);
        Assert.IsType<Gemini.Outcome.Rejected>(await refused.TranslateAsync("bad", "f", Lang.Fa, "Heat", 0, two));
        var asked = new List<string>();
        var spent = new Gemini(Server(_ => ((HttpStatusCode)429, """{"error":{"message":"GenerateRequestsPerDay quota"}}"""), asked), Folder(), _ => Task.CompletedTask);
        Assert.IsType<Gemini.Outcome.Unavailable>(await spent.TranslateAsync("key", "f", Lang.Fa, "Heat", 0, two));
        Assert.Single(asked); // a spent day is not asked again
    }

    [Fact]
    public async Task AModelNotOfferedGivesWayToTheNext()
    {
        var asked = new List<string>();
        var gemini = new Gemini(Server(r => r.RequestUri!.AbsolutePath.Contains("lite") ? (HttpStatusCode.NotFound, "{}") : (HttpStatusCode.OK, Answer("""["یک"]""").Serialize()), asked), Folder(), _ => Task.CompletedTask);
        Assert.IsType<Gemini.Outcome.Lines>(await gemini.TranslateAsync("key", "f", Lang.Fa, "Heat", 0, Cues.Take(1).ToList()));
        Assert.Equal(2, asked.Count);
    }

    [Fact]
    public async Task GeminiKeysAreCheckedToo()
    {
        Assert.Equal(KeyState.Rejected, await KeyCheck.CheckAsync(Server(_ => (HttpStatusCode.BadRequest, "{}")), "gemini", "bad"));
        Assert.Equal(KeyState.Works, await KeyCheck.CheckAsync(Server(_ => (HttpStatusCode.OK, "{}")), "gemini", "good"));
        // A 400 from another service is not the key.
        Assert.Equal(KeyState.Unreachable, KeyCheck.Read(400, "tmdb"));
    }
}
