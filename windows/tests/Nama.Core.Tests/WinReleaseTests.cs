using System.Text;
using Nama.Core;
using Xunit;

namespace Nama.Core.Tests;

public class Ed25519Tests
{
    // RFC 8032, section 7.1, tests 1 to 3.
    [Theory]
    [InlineData("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60", "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", "",
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")]
    [InlineData("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb", "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c", "72",
        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00")]
    [InlineData("c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7", "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025", "af82",
        "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a")]
    public void MatchesTheRfc(string seed, string publicKey, string message, string signature)
    {
        var s = Convert.FromHexString(seed);
        var m = Convert.FromHexString(message);
        Assert.Equal(publicKey, Convert.ToHexString(Ed25519.PublicKey(s)).ToLowerInvariant());
        Assert.Equal(signature, Convert.ToHexString(Ed25519.Sign(s, m)).ToLowerInvariant());
        Assert.True(Ed25519.Verify(Convert.FromHexString(publicKey), m, Convert.FromHexString(signature)));
    }

    [Fact]
    public void RefusesWhatWasNotSigned()
    {
        var seed = Convert.FromHexString("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
        var key = Ed25519.PublicKey(seed);
        var signature = Ed25519.Sign(seed, "installer"u8.ToArray());
        Assert.True(Ed25519.Verify(key, "installer"u8.ToArray(), signature));
        Assert.False(Ed25519.Verify(key, "installes"u8.ToArray(), signature)); // a changed file
        var bent = (byte[])signature.Clone();
        bent[40] ^= 1;
        Assert.False(Ed25519.Verify(key, "installer"u8.ToArray(), bent));
        var other = Ed25519.PublicKey(Convert.FromHexString("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb"));
        Assert.False(Ed25519.Verify(other, "installer"u8.ToArray(), signature)); // signed with another key
        Assert.False(Ed25519.Verify(key, "installer"u8.ToArray(), signature[..63]));
    }
}

public class WinReleaseTests
{
    private static Dictionary<string, object?> Release(string tag, string[]? assets = null, bool draft = false) => new()
    {
        ["tag_name"] = tag, ["draft"] = draft, ["prerelease"] = false,
        ["html_url"] = $"https://example.com/releases/{tag}",
        ["assets"] = (assets ?? ["30nama-setup.exe", "30nama-setup.exe.sig"])
            .Select(a => new Dictionary<string, object?> { ["name"] = a, ["browser_download_url"] = $"https://example.com/{tag}/{a}" }).ToArray(),
    };

    [Fact]
    public void PicksTheNewestCompleteWindowsRelease()
    {
        var feed = Json.From(new[]
        {
            Release("win-v1.10.0", ["30nama-setup.exe"]),
            Release("win-v1.9.0"),
            Release("win-v2.0.0", draft: true),
            Release("mac-v3.0.0", ["30nama-mac.dmg", "30nama-mac.dmg.sig"]),
            Release("win-v1.2.0"),
        });
        var newest = WinRelease.Newest(feed, "1.1.0");
        Assert.NotNull(newest);
        Assert.Equal("1.9.0", newest.Version); // 1.10 has no signature yet, 2.0 is a draft, 3.0 is for the Mac
        Assert.Equal("30nama-setup.exe", newest.Installer.Segments[^1]);
        Assert.Equal("30nama-setup.exe.sig", newest.Signature.Segments[^1]);
        Assert.Null(WinRelease.Newest(feed, "1.9.0"));
    }

    [Fact]
    public void VersionsCompareByNumber()
    {
        Assert.True(WinRelease.IsNewer("1.10.0", "1.9.2"));
        Assert.True(WinRelease.IsNewer("1.2", "1.1.9"));
        Assert.False(WinRelease.IsNewer("1.1.0", "1.1.0"));
    }

    [Fact]
    public void Signature()
    {
        var seed = Convert.FromHexString("c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7");
        var publicKey = Convert.ToBase64String(Ed25519.PublicKey(seed));
        var data = "installer"u8.ToArray();
        var signature = Convert.ToBase64String(Ed25519.Sign(seed, data)) + "\n";
        Assert.True(UpdateSignature.IsValid(signature, new MemoryStream(data), publicKey));
        Assert.False(UpdateSignature.IsValid(signature, new MemoryStream("installes"u8.ToArray()), publicKey)); // a changed file
        Assert.False(UpdateSignature.IsValid(signature, new MemoryStream(data))); // signed with another key than the app's
        Assert.False(UpdateSignature.IsValid("not base64", new MemoryStream(data), publicKey));
    }

    [Fact]
    public void TheAppHasAKey() => Assert.Equal(32, Convert.FromBase64String(UpdateSignature.PublicKey).Length);
}
