using System.Text;

namespace Nama.Core;

/// <summary>
/// A QR code for a short text, for the sign-in screen. The Mac app has one drawn by the system;
/// Windows has none, so this is the standard's encoder in its smallest form: byte mode, medium
/// error correction, version 1 to 40, the mask chosen by the standard's penalty rules. It
/// follows the structure of Project Nayuki's reference implementation.
/// </summary>
public sealed class QrCode
{
    public int Version { get; }
    public int Size { get; }
    private readonly bool[,] _modules;
    private readonly bool[,] _function;

    /// <summary>Whether the module at column <paramref name="x"/>, row <paramref name="y"/> is dark.</summary>
    public bool this[int x, int y] => _modules[y, x];

    // Error correction level M.
    private static readonly int[] EccPerBlock = [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28];
    private static readonly int[] Blocks = [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49];
    private const int FormatBitsM = 0;

    public static QrCode Encode(string text)
    {
        var data = Encoding.UTF8.GetBytes(text);
        for (var version = 1; version <= 40; version++)
        {
            var countBits = version <= 9 ? 8 : 16;
            var capacity = DataCodewords(version) * 8;
            var used = 4 + countBits + data.Length * 8;
            if (used > capacity || data.Length >= 1 << countBits) continue;

            var bits = new List<bool>();
            void Append(int value, int length)
            {
                for (var i = length - 1; i >= 0; i--) bits.Add(((value >> i) & 1) != 0);
            }
            Append(0b0100, 4);
            Append(data.Length, countBits);
            foreach (var b in data) Append(b, 8);
            Append(0, Math.Min(4, capacity - bits.Count));
            Append(0, (8 - bits.Count % 8) % 8);
            for (var pad = 0xEC; bits.Count < capacity; pad ^= 0xEC ^ 0x11) Append(pad, 8);

            var codewords = new byte[bits.Count / 8];
            for (var i = 0; i < bits.Count; i++) if (bits[i]) codewords[i >> 3] |= (byte)(1 << (7 - (i & 7)));
            return new QrCode(version, codewords);
        }
        throw new ArgumentException("Too long for a QR code.", nameof(text));
    }

    private QrCode(int version, byte[] data)
    {
        Version = version;
        Size = version * 4 + 17;
        _modules = new bool[Size, Size];
        _function = new bool[Size, Size];
        DrawFunctionPatterns();
        DrawCodewords(AddEccAndInterleave(data));

        var best = 0;
        var bestPenalty = int.MaxValue;
        for (var mask = 0; mask < 8; mask++)
        {
            ApplyMask(mask);
            DrawFormatBits(mask);
            var penalty = Penalty();
            if (penalty < bestPenalty) { best = mask; bestPenalty = penalty; }
            ApplyMask(mask); // masking twice undoes it
        }
        ApplyMask(best);
        DrawFormatBits(best);
    }

    private static int RawDataModules(int version)
    {
        var result = (16 * version + 128) * version + 64;
        if (version >= 2)
        {
            var align = version / 7 + 2;
            result -= (25 * align - 10) * align - 55;
            if (version >= 7) result -= 36;
        }
        return result;
    }

    private static int DataCodewords(int version) => RawDataModules(version) / 8 - EccPerBlock[version] * Blocks[version];

    // MARK: Function patterns

    private void SetFunction(int x, int y, bool dark)
    {
        _modules[y, x] = dark;
        _function[y, x] = true;
    }

    private void DrawFunctionPatterns()
    {
        for (var i = 0; i < Size; i++)
        {
            SetFunction(6, i, i % 2 == 0);
            SetFunction(i, 6, i % 2 == 0);
        }
        DrawFinder(3, 3);
        DrawFinder(Size - 4, 3);
        DrawFinder(3, Size - 4);
        var positions = AlignmentPositions();
        var n = positions.Count;
        for (var i = 0; i < n; i++)
        {
            for (var j = 0; j < n; j++)
            {
                if (i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0) continue;
                for (var dy = -2; dy <= 2; dy++)
                    for (var dx = -2; dx <= 2; dx++)
                        SetFunction(positions[i] + dx, positions[j] + dy, Math.Max(Math.Abs(dx), Math.Abs(dy)) != 1);
            }
        }
        DrawFormatBits(0); // reserves the area; drawn for real once the mask is chosen
        DrawVersion();
    }

    private void DrawFinder(int x, int y)
    {
        for (var dy = -4; dy <= 4; dy++)
        {
            for (var dx = -4; dx <= 4; dx++)
            {
                int xx = x + dx, yy = y + dy;
                var distance = Math.Max(Math.Abs(dx), Math.Abs(dy));
                if (xx >= 0 && xx < Size && yy >= 0 && yy < Size) SetFunction(xx, yy, distance != 2 && distance != 4);
            }
        }
    }

    private List<int> AlignmentPositions()
    {
        if (Version == 1) return [];
        var count = Version / 7 + 2;
        var step = Version == 32 ? 26 : (int)Math.Ceiling((Version * 4 + 4) / (double)(count * 2 - 2)) * 2;
        var result = new List<int> { 6 };
        for (var pos = Size - 7; result.Count < count; pos -= step) result.Insert(1, pos);
        return result;
    }

    private static bool Bit(int value, int i) => ((value >> i) & 1) != 0;

    private void DrawFormatBits(int mask)
    {
        var data = FormatBitsM << 3 | mask;
        var rem = data;
        for (var i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        var bits = (data << 10 | rem) ^ 0x5412;
        for (var i = 0; i <= 5; i++) SetFunction(8, i, Bit(bits, i));
        SetFunction(8, 7, Bit(bits, 6));
        SetFunction(8, 8, Bit(bits, 7));
        SetFunction(7, 8, Bit(bits, 8));
        for (var i = 9; i < 15; i++) SetFunction(14 - i, 8, Bit(bits, i));
        for (var i = 0; i < 8; i++) SetFunction(Size - 1 - i, 8, Bit(bits, i));
        for (var i = 8; i < 15; i++) SetFunction(8, Size - 15 + i, Bit(bits, i));
        SetFunction(8, Size - 8, true);
    }

    private void DrawVersion()
    {
        if (Version < 7) return;
        var rem = Version;
        for (var i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
        var bits = Version << 12 | rem;
        for (var i = 0; i < 18; i++)
        {
            int a = Size - 11 + i % 3, b = i / 3;
            SetFunction(a, b, Bit(bits, i));
            SetFunction(b, a, Bit(bits, i));
        }
    }

    // MARK: Data

    private byte[] AddEccAndInterleave(byte[] data)
    {
        int blocks = Blocks[Version], eccLength = EccPerBlock[Version];
        var raw = RawDataModules(Version) / 8;
        var shortBlocks = blocks - raw % blocks;
        var shortLength = raw / blocks;
        var divisor = ReedSolomonDivisor(eccLength);
        var all = new List<byte[]>();
        for (int i = 0, k = 0; i < blocks; i++)
        {
            var length = shortLength - eccLength + (i < shortBlocks ? 0 : 1);
            var chunk = data[k..(k + length)];
            k += length;
            var ecc = ReedSolomonRemainder(chunk, divisor);
            var block = new byte[shortLength + 1];
            chunk.CopyTo(block, 0);
            // Short blocks keep a hole where the long ones have their extra byte.
            ecc.CopyTo(block, i < shortBlocks ? length + 1 : length);
            all.Add(block);
        }
        var result = new List<byte>();
        for (var i = 0; i < shortLength + 1; i++)
        {
            for (var j = 0; j < blocks; j++)
            {
                if (i != shortLength - eccLength || j >= shortBlocks) result.Add(all[j][i]);
            }
        }
        return [.. result];
    }

    private static byte Multiply(int x, int y)
    {
        var z = 0;
        for (var i = 7; i >= 0; i--)
        {
            z = (z << 1) ^ ((z >> 7) * 0x11D);
            z ^= ((y >> i) & 1) * x;
        }
        return (byte)z;
    }

    private static byte[] ReedSolomonDivisor(int degree)
    {
        var result = new byte[degree];
        result[degree - 1] = 1;
        var root = 1;
        for (var i = 0; i < degree; i++)
        {
            for (var j = 0; j < degree; j++)
            {
                result[j] = Multiply(result[j], root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = Multiply(root, 0x02);
        }
        return result;
    }

    private static byte[] ReedSolomonRemainder(byte[] data, byte[] divisor)
    {
        var result = new byte[divisor.Length];
        foreach (var b in data)
        {
            var factor = b ^ result[0];
            Array.Copy(result, 1, result, 0, result.Length - 1);
            result[^1] = 0;
            for (var i = 0; i < result.Length; i++) result[i] ^= Multiply(divisor[i], factor);
        }
        return result;
    }

    private void DrawCodewords(byte[] data)
    {
        var i = 0;
        for (var right = Size - 1; right >= 1; right -= 2)
        {
            if (right == 6) right = 5;
            for (var vertical = 0; vertical < Size; vertical++)
            {
                for (var j = 0; j < 2; j++)
                {
                    var x = right - j;
                    var upward = ((right + 1) & 2) == 0;
                    var y = upward ? Size - 1 - vertical : vertical;
                    if (_function[y, x] || i >= data.Length * 8) continue;
                    _modules[y, x] = Bit(data[i >> 3], 7 - (i & 7));
                    i++;
                }
            }
        }
    }

    // MARK: Masks

    private void ApplyMask(int mask)
    {
        for (var y = 0; y < Size; y++)
        {
            for (var x = 0; x < Size; x++)
            {
                var invert = mask switch
                {
                    0 => (x + y) % 2 == 0,
                    1 => y % 2 == 0,
                    2 => x % 3 == 0,
                    3 => (x + y) % 3 == 0,
                    4 => (x / 3 + y / 2) % 2 == 0,
                    5 => x * y % 2 + x * y % 3 == 0,
                    6 => (x * y % 2 + x * y % 3) % 2 == 0,
                    _ => ((x + y) % 2 + x * y % 3) % 2 == 0,
                };
                if (invert && !_function[y, x]) _modules[y, x] = !_modules[y, x];
            }
        }
    }

    private int Penalty()
    {
        const int n1 = 3, n2 = 3, n3 = 40, n4 = 10;
        var result = 0;
        for (var pass = 0; pass < 2; pass++)
        {
            // Rows, then columns.
            for (var a = 0; a < Size; a++)
            {
                var color = false;
                var run = 0;
                var history = new int[7];
                for (var b = 0; b < Size; b++)
                {
                    var module = pass == 0 ? _modules[a, b] : _modules[b, a];
                    if (module == color)
                    {
                        run++;
                        if (run == 5) result += n1;
                        else if (run > 5) result++;
                    }
                    else
                    {
                        AddHistory(run, history);
                        if (!color) result += CountFinderLike(history) * n3;
                        color = module;
                        run = 1;
                    }
                }
                if (color)
                {
                    AddHistory(run, history);
                    run = 0;
                }
                run += Size;
                AddHistory(run, history);
                result += CountFinderLike(history) * n3;
            }
        }
        for (var y = 0; y < Size - 1; y++)
        {
            for (var x = 0; x < Size - 1; x++)
            {
                var c = _modules[y, x];
                if (c == _modules[y, x + 1] && c == _modules[y + 1, x] && c == _modules[y + 1, x + 1]) result += n2;
            }
        }
        var dark = 0;
        foreach (var m in _modules) if (m) dark++;
        var total = Size * Size;
        var k = (int)Math.Ceiling(Math.Abs(dark * 20 - total * 10) / (double)total) - 1;
        return result + k * n4;
    }

    private void AddHistory(int run, int[] history)
    {
        if (history[0] == 0) run += Size; // the light border before the first run
        Array.Copy(history, 0, history, 1, history.Length - 1);
        history[0] = run;
    }

    private static int CountFinderLike(int[] h)
    {
        var n = h[1];
        var core = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n;
        return (core && h[0] >= n * 4 && h[6] >= n ? 1 : 0) + (core && h[6] >= n * 4 && h[0] >= n ? 1 : 0);
    }
}
