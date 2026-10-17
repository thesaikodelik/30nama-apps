using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class QrCodeTests
{
    public static readonly string[] Samples =
    [
        "https://30nama.com/auth/ABC123",
        "https://30nama.com/auth/a1b2c3d4e5f6g7h8i9j0?ref=tv&lang=fa&from=windows-app",
        "x",
        new string('w', 300),
    ];

    [Fact]
    public void PicksTheSmallestVersionThatHoldsTheText()
    {
        var code = QrCode.Encode(Samples[0]);
        Assert.Equal(3, code.Version); // 30 bytes: version 2 holds 26 at medium error correction, version 3 holds 42
        Assert.Equal(29, code.Size);
        Assert.Equal(1, QrCode.Encode("x").Version);
        Assert.True(QrCode.Encode(Samples[3]).Version >= 10);
        Assert.Throws<ArgumentException>(() => QrCode.Encode(new string('w', 3000)));
    }

    [Fact]
    public void HasItsFinderPatternsAndTimingLines()
    {
        var code = QrCode.Encode(Samples[1]);
        foreach (var (x, y) in new[] { (0, 0), (code.Size - 7, 0), (0, code.Size - 7) })
        {
            for (var i = 0; i < 7; i++)
            {
                Assert.True(code[x + i, y]);           // the outer ring
                Assert.True(code[x + i, y + 6]);
                Assert.True(code[x + 3, y + 3]);       // and the centre
            }
            Assert.False(code[x + 1, y + 1]);
        }
        for (var i = 8; i < code.Size - 8; i++) Assert.Equal(i % 2 == 0, code[i, 6]);
        Assert.True(code[8, code.Size - 8]); // the dark module
    }

    /// <summary>
    /// Writes the samples as text grids to the folder named by QR_DUMP, for a decoder outside the
    /// tests to read back. Does nothing otherwise.
    /// </summary>
    [Fact]
    public void DumpForDecoding()
    {
        if (Environment.GetEnvironmentVariable("QR_DUMP") is not { } folder) return;
        for (var n = 0; n < Samples.Length; n++)
        {
            var code = QrCode.Encode(Samples[n]);
            var lines = Enumerable.Range(0, code.Size).Select(y => new string(Enumerable.Range(0, code.Size).Select(x => code[x, y] ? '#' : '.').ToArray()));
            File.WriteAllText(Path.Combine(folder, $"qr{n}.txt"), Samples[n] + "\n" + string.Join("\n", lines));
        }
    }
}
