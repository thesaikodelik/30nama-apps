using System.Text.RegularExpressions;
using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

/// <summary>Both apps carry the same keys and translations; the Mac app's file is where they are written.</summary>
public class L10nParityTests
{
    [Fact]
    public void StringsMatchTheMacApp()
    {
        var swift = File.ReadAllText(Fixtures.RepoFile(Path.Combine("30nama", "Core", "L10n.swift")));
        var hint = "run: python windows/tools/gen-l10n.py";

        var cases = Regex.Match(swift, @"enum S: String, CaseIterable \{(.*?)\n\}", RegexOptions.Singleline).Groups[1].Value;
        var keys = Regex.Matches(cases, "case (.+)").SelectMany(m => m.Groups[1].Value.Split(',')).Select(k => k.Trim()).Where(k => k.Length > 0).ToList();
        Assert.True(keys.SequenceEqual(Enum.GetNames<S>()), "the keys differ from L10n.swift; " + hint);

        foreach (var (name, table) in new[] { ("en", Loc.En), ("fa", Loc.Fa), ("ar", Loc.Ar) })
        {
            var body = Regex.Match(swift, $@"static let {name}: \[S: String\] = \[(.*?)\n    \]", RegexOptions.Singleline).Groups[1].Value;
            var entries = Regex.Matches(body, @"\.(\w+): ""((?:[^""\\]|\\.)*)""")
                .ToDictionary(m => m.Groups[1].Value, m => Regex.Unescape(Regex.Replace(m.Groups[2].Value, @"\\u\{([0-9A-Fa-f]+)\}", x => "\\u" + x.Groups[1].Value.PadLeft(4, '0'))));
            Assert.True(entries.Count == table.Count, $"{name}: {entries.Count} strings in L10n.swift, {table.Count} here; " + hint);
            foreach (var (key, value) in entries)
                Assert.True(table.TryGetValue(Enum.Parse<S>(key), out var ours) && ours == value, $"{name}.{key} differs from L10n.swift; " + hint);
        }
    }

    [Fact]
    public void StringsTheMacAppHasNotYetAreInAllThreeLanguages()
    {
        foreach (var key in Enum.GetValues<Extra>())
        {
            Assert.True(Loc.ExtraEn.ContainsKey(key), $"missing English: {key}");
            Assert.True(Loc.ExtraFa.ContainsKey(key), $"missing Persian: {key}");
            Assert.True(Loc.ExtraAr.ContainsKey(key), $"missing Arabic: {key}");
            // Once the Mac app has a key, it belongs in the shared tables and leaves this one.
            Assert.False(Enum.IsDefined(typeof(S), key.ToString()), $"{key} is in L10n.swift now; move it out of L10n.Extra.cs");
        }
    }
}
