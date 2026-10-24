"""Writes windows/src/Nama.Core/MovieLines.cs from 30nama/Core/MovieLines.swift, text for text.

The lines are written on the Mac side; this copies them, so not a word drifts. Run it after
MovieLines.swift changes; a test fails until then.

    python windows/tools/gen-movie-lines.py
"""
import pathlib
import re

root = str(pathlib.Path(__file__).resolve().parents[2])
swift = open(root + '/30nama/Core/MovieLines.swift', encoding='utf-8').read()

def cs(s):
    assert '\\' not in s, s
    return '"' + s.replace('"', '\\"') + '"'

lines = re.findall(r'MovieLine\(text: "((?:[^"\\]|\\.)*)", film: "((?:[^"\\]|\\.)*)", year: (\d+), directors: \[([^\]]*)\]\)', swift)
assert len(lines) == 100, len(lines)
addressed = re.findall(r'^\s*\((\d+), "((?:[^"\\]|\\.)*)"\),\s*$', swift, re.M)
assert len(addressed) == 43, len(addressed)

out = []
w = out.append
w('namespace Nama.Core;')
w('')
w('/// <summary>A line from a film, with the film it is from.</summary>')
w('/// <param name="Directors">As the film\'s credits name them.</param>')
w('public sealed record MovieLine(string Text, string Film, int Year, IReadOnlyList<string> Directors)')
w('{')
w('    /// <summary>What to search 30nama for: the film\'s name, then with its year, the way 30nama writes titles.</summary>')
w('    public IReadOnlyList<string> SearchQueries => [Film, $"{Film} {Year}"];')
w('')
w('    /// <summary>')
w('    /// The film on 30nama among search results: a film, not a series, with a close enough name and')
w('    /// the same year give or take one. One that can be streamed wins.')
w('    /// </summary>')
w('    public Post? Match(IEnumerable<Post> posts)')
w('    {')
w('        (Post Post, double Score)? best = null;')
w('        foreach (var post in posts.Where(p => !p.IsSeries))')
w('        {')
w('            var raw = post.TitleEnglish ?? "";')
w('            var name = Math.Max(TitleMatch.Similarity(TitleMatch.StripYear(raw), Film), TitleMatch.Similarity(raw, Film));')
w('            if (name < 0.8 || post.Year is not { } found || Math.Abs(found - Year) > 1) continue;')
w('            var score = name + (found == Year ? 0.25 : 0.1) + (post.HasStream ? 0.1 : 0);')
w('            if (score > (best?.Score ?? 0)) best = (post, score);')
w('        }')
w('        return best?.Post;')
w('    }')
w('')
w('    public bool Equals(MovieLine? other) => other is not null && Text == other.Text && Film == other.Film && Year == other.Year && Directors.SequenceEqual(other.Directors);')
w('    public override int GetHashCode() => HashCode.Combine(Text, Film, Year);')
w('}')
w('')
w('/// <summary>')
w('/// A line from the screen, said to whoever opens the app: the American Film Institute\'s hundred')
w('/// most memorable American movie quotations (2005), as Wikipedia\'s list of them gives them, each')
w('/// with its film and year from the same list and the directors the film\'s own article credits.')
w('/// One is picked each time the app opens. Kept in the original English, which is how they are known.')
w('/// Written from MovieLines.swift, text for text.')
w('/// </summary>')
w('public static class MovieLines')
w('{')
w('    public static IReadOnlyList<MovieLine> All { get; } =')
w('    [')
for text, film, year, directors in lines:
    ds = ', '.join(cs(d) for d in re.findall(r'"((?:[^"\\]|\\.)*)"', directors))
    w(f'        new({cs(text)}, {cs(film)}, {year}, [{ds}]),')
w('    ];')
w('')
w('    /// <summary>')
w('    /// The lines from <see cref="All"/> (by their place in it, from 1) that call someone by name or')
w('    /// speak to them, with <c>{name}</c> where the name or the word for them was: a line is only said')
w('    /// to someone when their name fits in it.')
w('    /// </summary>')
w('    public static IReadOnlyList<(int Rank, string Text)> Addressed { get; } =')
w('    [')
for rank, text in addressed:
    w(f'        ({rank}, {cs(text)}),')
w('    ];')
w('')
w('    /// <summary>Bond\'s own line goes by the family name, so it is said only when that is known.</summary>')
w('    private const int Bond = 22;')
w('')
w('    /// <summary>')
w('    /// The line for this time the app is open: one said to them by their first name when it is')
w('    /// known, otherwise one of the hundred as it is.')
w('    /// </summary>')
w('    public static MovieLine Pick(string? name, string? familyName, Random random)')
w('    {')
w('        name = name?.Trim();')
w('        if (string.IsNullOrEmpty(name)) return All[random.Next(All.Count)];')
w('        var lines = Addressed.ToList();')
w('        if (familyName?.Trim() is { Length: > 0 } family) lines.Add((Bond, $"{family}. {name} {family}."));')
w('        var (rank, text) = lines[random.Next(lines.Count)];')
w('        return All[rank - 1] with { Text = text.Replace("{name}", name) };')
w('    }')
w('}')
open(root + '/windows/src/Nama.Core/MovieLines.cs', 'w', encoding='utf-8', newline='\n').write('\n'.join(out) + '\n')
print('wrote', len(lines), 'lines and', len(addressed), 'addressed')
