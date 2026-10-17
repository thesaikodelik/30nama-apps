namespace Nama.Core;

/// <summary>A Windows release on the project's GitHub page, as the releases API describes it. As MacRelease.swift.</summary>
public sealed record WinRelease(string Version, Uri Page, Uri Installer, Uri Signature)
{
    public const string TagPrefix = "win-v";
    /// <summary>Where the releases are listed. GitHub answers without a key, sixty times an hour per address.</summary>
    public static readonly Uri Feed = new("https://api.github.com/repos/thesaikodelik/30nama-apps/releases?per_page=10");

    /// <summary>
    /// The newest finished release in a <c>releases</c> answer that is newer than <paramref name="current"/>.
    /// A release without its installer and that installer's signature is passed over, as if not out yet.
    /// </summary>
    public static WinRelease? Newest(Json j, string current) =>
        j.Array.Select(r =>
            {
                if (r["draft"].Bool || r["prerelease"].Bool || r["tag_name"].Text is not { } tag || !tag.StartsWith(TagPrefix, StringComparison.Ordinal)) return null;
                var assets = r["assets"].Array;
                Uri? Asset(string name) => assets.FirstOrDefault(a => a["name"].Text == name)?["browser_download_url"].Url;
                var installer = assets.FirstOrDefault(a => a["name"].Text is { } n && n.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) && Asset(n + ".sig") is not null);
                if (installer?["name"].Text is not { } name || installer["browser_download_url"].Url is not { } file || r["html_url"].Url is not { } page) return null;
                return new WinRelease(tag[TagPrefix.Length..], page, file, Asset(name + ".sig")!);
            })
            .OfType<WinRelease>()
            .Where(r => IsNewer(r.Version, current))
            .Aggregate((WinRelease?)null, (best, r) => best is null || IsNewer(r.Version, best.Version) ? r : best);

    /// <summary>Compares numbers part by part, so 1.10 is newer than 1.9.</summary>
    public static bool IsNewer(string version, string other)
    {
        var a = Parts(version);
        var b = Parts(other);
        for (var i = 0; i < Math.Max(a.Length, b.Length); i++)
        {
            var x = i < a.Length ? a[i] : -1;
            var y = i < b.Length ? b[i] : -1;
            if (x != y) return x > y;
        }
        return false;
    }

    private static long[] Parts(string version) =>
        version.Split('.', '-', '+').Select(p => long.TryParse(new string(p.TakeWhile(char.IsAsciiDigit).ToArray()), out var n) ? n : 0).ToArray();
}

/// <summary>
/// Checks that a downloaded update was signed by the project. The Windows app has no code-signing
/// certificate, so Windows does not vouch for what it downloads; this signature does instead. Only
/// the public half of the key is here. The private half stays on the maintainer's Windows computer,
/// apart from the Mac's key (see windows/tools/sign-update).
/// </summary>
public static class UpdateSignature
{
    public const string PublicKey = "xRjuyDIiqPcFUhhBurXtGWMmrrwMewTZaBXAGzhEKdQ=";

    /// <summary><paramref name="signature"/> is the text of the <c>.sig</c> file: the Ed25519 signature of the file, in base64.</summary>
    public static bool IsValid(string signature, Stream file, string publicKey = PublicKey)
    {
        try
        {
            return Ed25519.Verify(Convert.FromBase64String(publicKey), file, Convert.FromBase64String(signature.Trim()));
        }
        catch (FormatException) { return false; }
    }
}
