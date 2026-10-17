using System.Numerics;
using System.Security.Cryptography;

namespace Nama.Core;

/// <summary>
/// Ed25519 signatures (RFC 8032), which .NET does not have. Only release files are signed and
/// checked with it, a few times a month, so it is written for clarity after the RFC's own
/// reference code rather than for speed. Keys are as CryptoKit's <c>rawRepresentation</c>: the
/// 32-byte seed and the 32-byte public key.
/// </summary>
public static class Ed25519
{
    private static readonly BigInteger P = BigInteger.Pow(2, 255) - 19;
    private static readonly BigInteger L = BigInteger.Pow(2, 252) + BigInteger.Parse("27742317777372353535851937790883648493");
    private static readonly BigInteger D = Mod(-121665 * Inverse(121666));
    private static readonly BigInteger SqrtMinusOne = BigInteger.ModPow(2, (P - 1) / 4, P);
    private static readonly Point Base = BasePoint();

    /// <summary>A point in extended coordinates: x = X/Z, y = Y/Z, x·y = T/Z.</summary>
    private readonly record struct Point(BigInteger X, BigInteger Y, BigInteger Z, BigInteger T);

    public static byte[] PublicKey(byte[] seed)
    {
        var (a, _) = Expand(seed);
        return Encode(Multiply(a, Base));
    }

    /// <summary>Signs a message read from a stream that can be read twice, as a file can.</summary>
    public static byte[] Sign(byte[] seed, Stream message)
    {
        var (a, prefix) = Expand(seed);
        var publicKey = Encode(Multiply(a, Base));
        var start = message.Position;
        var r = HashToScalar(message, prefix);
        var nonce = Encode(Multiply(r, Base));
        message.Position = start;
        var h = HashToScalar(message, nonce, publicKey);
        var s = Mod(r + h * a, L);
        return [.. nonce, .. Bytes(s)];
    }

    public static byte[] Sign(byte[] seed, byte[] message) => Sign(seed, new MemoryStream(message));

    public static bool Verify(byte[] publicKey, Stream message, byte[] signature)
    {
        if (publicKey.Length != 32 || signature.Length != 64) return false;
        if (Decode(publicKey) is not { } a || Decode(signature[..32]) is not { } r) return false;
        var s = Number(signature[32..]);
        if (s >= L) return false;
        var h = HashToScalar(message, signature[..32], publicKey);
        return SamePoint(Multiply(s, Base), Add(r, Multiply(h, a)));
    }

    public static bool Verify(byte[] publicKey, byte[] message, byte[] signature) => Verify(publicKey, new MemoryStream(message), signature);

    // MARK: Arithmetic

    private static BigInteger Mod(BigInteger x, BigInteger? m = null)
    {
        var n = m ?? P;
        var r = x % n;
        return r.Sign < 0 ? r + n : r;
    }

    private static BigInteger Inverse(BigInteger x) => BigInteger.ModPow(x, P - 2, P);

    private static Point Add(Point a, Point b)
    {
        var aa = Mod((a.Y - a.X) * (b.Y - b.X));
        var bb = Mod((a.Y + a.X) * (b.Y + b.X));
        var c = Mod(a.T * 2 * D * b.T);
        var d = Mod(a.Z * 2 * b.Z);
        BigInteger e = bb - aa, f = d - c, g = d + c, h = bb + aa;
        return new Point(Mod(e * f), Mod(g * h), Mod(f * g), Mod(e * h));
    }

    private static Point Multiply(BigInteger s, Point p)
    {
        var q = new Point(0, 1, 1, 0);
        while (s > 0)
        {
            if (!s.IsEven) q = Add(q, p);
            p = Add(p, p);
            s >>= 1;
        }
        return q;
    }

    private static bool SamePoint(Point a, Point b) =>
        Mod(a.X * b.Z - b.X * a.Z).IsZero && Mod(a.Y * b.Z - b.Y * a.Z).IsZero;

    private static BigInteger? RecoverX(BigInteger y, bool odd)
    {
        if (y >= P) return null;
        var x2 = Mod((y * y - 1) * Inverse(D * y * y + 1));
        if (x2.IsZero) return odd ? null : BigInteger.Zero;
        var x = BigInteger.ModPow(x2, (P + 3) / 8, P);
        if (!Mod(x * x - x2).IsZero) x = Mod(x * SqrtMinusOne);
        if (!Mod(x * x - x2).IsZero) return null;
        if (!x.IsEven != odd) x = P - x;
        return x;
    }

    private static Point BasePoint()
    {
        var y = Mod(4 * Inverse(5));
        var x = RecoverX(y, false)!.Value;
        return new Point(x, y, 1, Mod(x * y));
    }

    private static byte[] Encode(Point p)
    {
        var z = Inverse(p.Z);
        var x = Mod(p.X * z);
        var y = Mod(p.Y * z);
        return Bytes(y | (x.IsEven ? BigInteger.Zero : BigInteger.One << 255));
    }

    private static Point? Decode(byte[] bytes)
    {
        if (bytes.Length != 32) return null;
        var y = Number(bytes);
        var odd = !(y >> 255).IsZero;
        y &= (BigInteger.One << 255) - 1;
        if (RecoverX(y, odd) is not { } x) return null;
        return new Point(x, y, 1, Mod(x * y));
    }

    private static (BigInteger A, byte[] Prefix) Expand(byte[] seed)
    {
        if (seed.Length != 32) throw new ArgumentException("An Ed25519 seed is 32 bytes.", nameof(seed));
        var h = SHA512.HashData(seed);
        var a = Number(h[..32]);
        a &= (BigInteger.One << 254) - 8;
        a |= BigInteger.One << 254;
        return (a, h[32..]);
    }

    /// <summary>SHA-512 of the given bytes followed by the whole message, as a scalar.</summary>
    private static BigInteger HashToScalar(Stream message, params byte[][] first)
    {
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA512);
        foreach (var part in first) hash.AppendData(part);
        var buffer = new byte[1 << 16];
        int read;
        while ((read = message.Read(buffer, 0, buffer.Length)) > 0) hash.AppendData(buffer, 0, read);
        return Mod(Number(hash.GetHashAndReset()), L);
    }

    private static BigInteger Number(byte[] littleEndian) => new(littleEndian, isUnsigned: true, isBigEndian: false);

    private static byte[] Bytes(BigInteger n)
    {
        var raw = n.ToByteArray(isUnsigned: true, isBigEndian: false);
        var output = new byte[32];
        Array.Copy(raw, output, Math.Min(raw.Length, 32));
        return output;
    }
}
