// Signs a Windows installer for the in-app updater: writes <installer>.sig beside it, to be
// uploaded to the release with the installer. The app installs only what this key has signed.
//
// The Windows key is its own, apart from the Mac's: each stays on the computer that builds its
// releases. It is kept in %USERPROFILE%\.30nama, encrypted with Windows' DPAPI for this Windows
// account only, and never in the repository. Without it, the Windows app cannot update itself and
// people will have to download a release by hand; keep the text `export` prints somewhere safe,
// such as a password manager, and bring it back on another computer with `import`.
//
// Usage: dotnet run --project windows/tools/sign-update -- <command>
//   keygen           make the key, once
//   sign <file>      write <file>.sig
//   public           print the public key, for UpdateSignature.PublicKey
//   export           print the private key, for a backup
//   import           read a backed-up private key from the console
using System.Security.Cryptography;
using Nama.Core;

var folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".30nama");
var keyFile = Path.Combine(folder, "win-update-signing.key");
// Extra entropy for DPAPI, so the blob is only for this tool.
var purpose = "30nama-win-update-signing"u8.ToArray();

byte[]? Load()
{
    if (!File.Exists(keyFile)) return null;
    return ProtectedData.Unprotect(File.ReadAllBytes(keyFile), purpose, DataProtectionScope.CurrentUser);
}

void Store(byte[] seed)
{
    Directory.CreateDirectory(folder);
    File.WriteAllBytes(keyFile, ProtectedData.Protect(seed, purpose, DataProtectionScope.CurrentUser));
}

string PublicOf(byte[] seed) => Convert.ToBase64String(Ed25519.PublicKey(seed));

int Fail(string message)
{
    Console.Error.WriteLine(message);
    return 1;
}

switch (args.FirstOrDefault())
{
    case "keygen":
    {
        if (File.Exists(keyFile)) return Fail($"a key is already in {keyFile}; it is not replaced");
        var seed = RandomNumberGenerator.GetBytes(32);
        Store(seed);
        Console.WriteLine($"key made and kept in {keyFile}");
        Console.WriteLine($"public key: {PublicOf(seed)}");
        Console.WriteLine("back it up with `export`");
        return 0;
    }
    case "sign" when args.Length == 2:
    {
        if (Load() is not { } seed) return Fail("no signing key; make one with `keygen` or bring one back with `import`");
        if (!File.Exists(args[1])) return Fail($"no file at {args[1]}");
        string signature;
        using (var file = File.OpenRead(args[1])) signature = Convert.ToBase64String(Ed25519.Sign(seed, file));
        File.WriteAllText(args[1] + ".sig", signature + "\n");
        var publicKey = PublicOf(seed);
        // A release the app cannot check is worse than none: it would be offered and then refused.
        using (var file = File.OpenRead(args[1]))
        {
            if (!UpdateSignature.IsValid(signature, file, publicKey)) return Fail("the signature does not check out; nothing should be uploaded");
        }
        Console.WriteLine($"signed {args[1]} with key {publicKey}");
        if (publicKey != UpdateSignature.PublicKey) return Fail("this is not the key in UpdateSignature.PublicKey: the app would refuse the update");
        return 0;
    }
    case "public":
        return Load() is { } key ? Print(PublicOf(key)) : Fail("no signing key");
    case "export":
        return Load() is { } secret ? Print(Convert.ToBase64String(secret)) : Fail("no signing key");
    case "import":
    {
        if (File.Exists(keyFile)) return Fail($"a key is already in {keyFile}; it is not replaced");
        Console.Write("private key: ");
        var seed = Convert.FromBase64String(Console.ReadLine()?.Trim() ?? "");
        if (seed.Length != 32) return Fail("that is not a 32-byte key");
        Store(seed);
        Console.WriteLine($"public key: {PublicOf(seed)}");
        return 0;
    }
    default:
        return Fail("usage: sign-update keygen | sign <file> | public | export | import");
}

static int Print(string text)
{
    Console.WriteLine(text);
    return 0;
}
