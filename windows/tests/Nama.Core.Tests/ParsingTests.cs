using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

// Ported from Tests/NamaCoreTests/ParsingTests.swift.
public class ParsingTests
{
    [Fact]
    public void SubtitleSiteListingAndChoice()
    {
        const string listing = """
        <nav><ul>
        <li><a href="/subtitle/some-show/farsi_persian/101">Farsi/Persian — Some.Show.S01E02.1080p.WEB-DL.x264-GRP</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/102">Farsi/Persian — Some.Show.S01E02.1080p.BluRay.x265-ABC</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/102">Farsi/Persian — Some.Show.S01E02.720p.BluRay.x264-XYZ</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/103">Farsi/Persian — Some.Show.S01E03.1080p.BluRay.x265-ABC</a></li>
        <li><a href="/subtitle/some-show/farsi_persian/104">Farsi/Persian — Some.Show.Season.1.Complete.BluRay</a></li>
        </ul></nav>
        """;
        var entries = SubtitleSite.Entries(listing);
        Assert.Equal(["101", "102", "103", "104"], entries.Select(e => e.Path[^3..]));
        Assert.Equal(2, entries[1].Releases.Count); // a subtitle listed for two releases is one entry
        // The right episode, and of those the one cut from the same source.
        Assert.Equal("102", SubtitleSite.Best("Some_Show_S01E02_1080p_BluRay_30nama.mkv", 1, 2, entries)?.Path[^3..]);
        Assert.Equal("101", SubtitleSite.Best("Some.Show.S01E02.720p.WEB-DL.mkv", 1, 2, entries)?.Path[^3..]);
        // No subtitle for the episode itself: the season's pack is the next best.
        Assert.Equal("104", SubtitleSite.Best("Some.Show.S01E09.BluRay.mkv", 1, 9, entries)?.Path[^3..]);
        Assert.Null(SubtitleSite.Best("x.mkv", null, null, []));

        Assert.Equal("abc123DEF", SubtitleSite.Download("""x "download_url":"https://api.subsource.net/v1/subtitle/download/abc123DEF" y""")?.LastPathComponent());
        Assert.Null(SubtitleSite.Download("nothing here"));

        string[] pack = ["readme.txt", "Some.Show.S01E01.srt", "Some.Show.S01E02.srt", "Some.Show.S01E10.srt"];
        Assert.Equal("Some.Show.S01E02.srt", SubtitleSite.Pick(pack, 1, 2));
        Assert.Null(SubtitleSite.Pick(pack, 1, 7)); // better none than the wrong episode
        Assert.Equal("Film.2020.srt", SubtitleSite.Pick(["Film.2020.srt", "notes.nfo"], null, null));
        // The Arabic Windows code page is read when the bytes are not UTF-8.
        Assert.Equal("سلام", SubtitleSite.Text([0xD3, 0xE1, 0xC7, 0xE3]));
    }

    private static string Vtt(params string[] lines) =>
        "WEBVTT\n\n" + string.Join("\n\n", lines.Select((l, i) => $"00:00:0{i}.000 --> 00:00:0{i}.900\n{l}"));

    [Fact]
    public void PersianSubtitlesKeepTheDirectionTheyWereWrittenFor()
    {
        const string ltr = "⁦", rtl = "⁧", end = "⁩";
        // Typed for a left-to-right player: the full stop comes first. The question mark is no evidence.
        var old = SubtitleTrack.FromVtt(Vtt("!باشه.فهمیدم", ".هی.بیا برقص\nچیه.ازدواج کردی؟", "خوبه"));
        Assert.Equal(ltr + "!باشه.فهمیدم" + end, old.TextAt(0.5));
        Assert.Equal(ltr + ".هی.بیا برقص" + end + "\n" + ltr + "چیه.ازدواج کردی؟" + end, old.TextAt(1.5)); // each line on its own
        // Written properly: the full stop comes last.
        var proper = SubtitleTrack.FromVtt(Vtt("باشه. فهمیدم!", "خوبه."));
        Assert.Equal(rtl + "باشه. فهمیدم!" + end, proper.TextAt(0.5));
        // Typed on an Arabic keyboard: the yeh with dots and the Arabic kaf become the Persian letters.
        var typed = SubtitleTrack.FromVtt(Vtt("چي اومدي", "كتاب"));
        Assert.Equal(rtl + "چی اومدی" + end, typed.TextAt(0.5));
        Assert.Equal(rtl + "کتاب" + end, typed.TextAt(1.5));
        // The same before a zero-width non-joiner, which clings to the letter in front of it.
        var joined = SubtitleTrack.FromVtt(Vtt("مي‌دوني چي"));
        Assert.Equal(rtl + "می‌دونی چی" + end, joined.TextAt(0.5));
        // Arabic itself keeps its own letters.
        Assert.Equal(rtl + "في كتاب" + end, SubtitleTrack.FromVtt(Vtt("في كتاب")).TextAt(0.5));
        // Nothing right-to-left in it: left alone.
        Assert.Equal("Fine. I get it!", SubtitleTrack.FromVtt(Vtt("Fine. I get it!")).TextAt(0.5));
    }

    [Fact]
    public void JsonAccessorsAreLenient()
    {
        var j = Json.Parse("""{"n":"42","f":7.5,"t":true,"s":" hi ","z":null,"a":[1,"2"],"big":1234567}""");
        Assert.Equal(42, j["n"].Int);
        Assert.Equal(7.5, j["f"].Double);
        Assert.Equal("7.5", j["f"].String);
        Assert.Equal("1234567", j["big"].String);
        Assert.True(j["t"].Bool);
        Assert.False(j["t"].IsNull);
        Assert.Null(j["t"].Int); // a boolean is not a number
        Assert.Equal("hi", j["s"].Text);
        Assert.Null(j["z"].Text);
        Assert.True(j["missing"]["deeper"][3].IsNull);
        Assert.Equal(2, j["a"][1].Int);
        Assert.Equal(j, Json.Parse(j.Serialize())); // round-trips
    }

    [Fact]
    public void VttParsing()
    {
        const string vtt = "﻿WEBVTT\r\n\r\nNOTE made by a tool\r\n\r\n1\r\n00:00:01.000 --> 00:00:03.500 line:90%\r\n<i>Hello</i> &amp; welcome\r\nsecond line\r\n\r\n00:01:02,250 --> 00:01:04,000\r\n{\\an8}سلام دنیا\r\n\r\n01:00:00.000 --> 01:00:02.000\r\nLate\r\n";
        var track = SubtitleTrack.FromVtt(vtt);
        Assert.Equal(3, track.Cues.Count);
        Assert.Equal(new Cue(1, 3.5, "Hello & welcome\nsecond line"), track.Cues[0]);
        Assert.Equal(62.25, track.Cues[1].Start);
        Assert.Equal("⁧سلام دنیا⁩", track.Cues[1].Text); // a right-to-left line is held in its direction; the others are untouched
        Assert.Equal(3600, track.Cues[2].Start);
    }

    [Fact]
    public void CueLookup()
    {
        var track = new SubtitleTrack([new Cue(10, 12, "b"), new Cue(1, 3, "a"), new Cue(10.5, 11, "overlap")]);
        Assert.Equal("", track.TextAt(0.5));
        Assert.Equal("a", track.TextAt(1));
        Assert.Equal("", track.TextAt(3)); // end is exclusive
        Assert.Equal("overlap", track.TextAt(10.7));
        Assert.Equal("b", track.TextAt(11.5)); // falls back to an earlier cue that is still running
        Assert.Equal("", track.TextAt(99));
        Assert.Equal("", new SubtitleTrack().TextAt(5));
    }

    [Fact]
    public void TrackWritesSrtForMpv()
    {
        var track = new SubtitleTrack([new Cue(62.25, 3723.5, "⁧سلام⁩\nدنیا")]);
        Assert.Equal("1\n00:01:02,250 --> 01:02:03,500\n⁧سلام⁩\nدنیا\n\n", track.ToSrt());
    }

    [Fact]
    public void BlurHashDecodes()
    {
        // Hash served by the API for a real poster.
        var px = BlurHash.Pixels("bRIr1_%M~qxtyXtR%hRjIUxu", 8, 12)!;
        Assert.Equal(8 * 12 * 4, px.Length);
        Assert.True(Enumerable.Range(0, px.Length / 4).All(i => px[i * 4 + 3] == 255)); // opaque
        Assert.True(px.Distinct().Count() > 8); // not a flat colour

        // A single-component hash is exactly its average colour.
        var flat = BlurHash.Pixels("00OZZy", 2, 2)!;
        Assert.Equal(flat[..4], flat[^4..]);

        // ...and that colour is what Average reports.
        var average = BlurHash.Average("00OZZy")!.Value;
        Assert.Equal(flat[0], (byte)Math.Round(average.Red * 255));
        Assert.Equal(flat[2], (byte)Math.Round(average.Blue * 255));
        Assert.Null(BlurHash.Average("0"));

        Assert.Null(BlurHash.Pixels("bRIr1_%M", 4, 4)); // wrong length
        Assert.Null(BlurHash.Pixels("", 4, 4));
    }

    [Fact]
    public void Formatting()
    {
        Assert.Equal("2:05", Fmt.Clock(125));
        Assert.Equal("1:02:03", Fmt.Clock(3723));
        Assert.Equal("0:00", Fmt.Clock(-4));
        Assert.Equal("1h 35m", Fmt.Runtime(95, Lang.En));
        Assert.Equal("50m", Fmt.Runtime(50, Lang.En));
        Assert.Equal("۲ ساعت", Fmt.Runtime(120, Lang.Fa));
        Assert.Equal("۱ ساعت و ۳۵ دقیقه", Fmt.Runtime(95, Lang.Fa));
        Assert.Equal("1 س 35 د", Fmt.Runtime(95, Lang.Ar));
        Assert.Null(Fmt.Runtime(0, Lang.En));
        Assert.Equal("S۲ · E۱۵", Fmt.PersianDigits("S2 · E15"));
        Assert.Equal("8.5", Fmt.Score(8.5));
        Assert.Equal("84,827", Fmt.Count(84827, Lang.En));
        Assert.Equal("۸۴٬۸۲۷", Fmt.Count(84827, Lang.Fa));
        Assert.Equal(11, Episode.Number("11,12"));
        Assert.Equal("7", Episode.Label(null, 7));
    }

    [Fact]
    public void EveryStringIsTranslated()
    {
        foreach (var key in Enum.GetValues<S>())
        {
            Assert.True(Loc.En.ContainsKey(key), $"missing English: {key}");
            Assert.True(Loc.Fa.ContainsKey(key), $"missing Persian: {key}");
            Assert.True(Loc.Ar.ContainsKey(key), $"missing Arabic: {key}");
        }
        Assert.Equal("فصل ۲ · قسمت ۵", new Loc(Lang.Fa).EpisodeTag(2, "5"));
        Assert.Equal("S2 · E5", new Loc(Lang.En).EpisodeTag(2, "5"));
        Assert.Equal("الموسم 2 · الحلقة 5", new Loc(Lang.Ar).EpisodeTag(2, "5"));
        Assert.Equal("تشغيل", new Loc(Lang.Ar)[S.play]);
        Assert.Equal([Lang.En, Lang.Ar, Lang.Fa], LangExtensions.All); // the order the language setting steps through
        Assert.Equal([false, true, true], LangExtensions.All.Select(l => l.IsRtl()));
        Assert.Equal(["en", "en", "fa"], LangExtensions.All.Select(l => l.Catalogue())); // 30nama has no Arabic catalogue
    }
}
