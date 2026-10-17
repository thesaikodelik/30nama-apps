#if DEBUG
using Nama.Core;

namespace Nama.App.Player;

/// <summary>
/// Debug builds only, with NAMA_DEMO=1: a stream for any title, from public test streams, so the
/// player can be tried without an account. Two episodes, with an intro, end credits and a Persian
/// subtitle typed the way most files in circulation are.
/// </summary>
public static class DemoStream
{
    public static bool IsEnabled => Environment.GetEnvironmentVariable("NAMA_DEMO") == "1";

    public static StreamResult Result(Post post)
    {
        var subtitle = Path.Combine(Path.GetTempPath(), "nama-demo-fa.vtt");
        File.WriteAllText(subtitle, """
            WEBVTT

            00:00:01.000 --> 00:00:04.000
            !سلام.این زیرنویس نمونه است

            00:00:04.500 --> 00:00:08.000
            .هی.بيا ببينيم
            جهتش درسته؟

            00:00:09.000 --> 00:00:14.000
            زیرنویس فارسی برای آزمایش پخش‌کننده
            """);
        var file = new Uri(subtitle).AbsoluteUri;
        Dictionary<string, object?> Item(int episode, string id, string url) => new()
        {
            ["info"] = new Dictionary<string, object?> { ["file_id"] = id, ["post_id"] = post.Id.ToString(), ["season"] = "1", ["episode"] = episode.ToString(), ["title"] = $"Demo {episode}", ["duration"] = 600 },
            ["options"] = new Dictionary<string, object?> { ["token"] = "demo", ["intro_start"] = "00:00:05", ["intro_end"] = "00:00:40", ["end"] = 560 },
            ["file"] = new Dictionary<string, object?>
            {
                ["m3u8"] = new[] { new Dictionary<string, object?> { ["label"] = "Auto", ["default"] = url } },
                ["subtitle"] = new Dictionary<string, object?> { ["fa"] = file },
            },
        };
        var json = Json.From(new Dictionary<string, object?>
        {
            ["hls"] = new[]
            {
                Item(1, "demo-1", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                Item(2, "demo-2", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"),
            },
        });
        return new StreamResult(json);
    }
}
#endif
