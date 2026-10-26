// Signs an Android TV APK for the in-app updater: writes <apk>.sig beside it, to be uploaded to the
// release with the APK. The app installs only what this key has signed.
//
// The Android key is its own, apart from the Mac's, Windows' and the Quest's: it stays on the
// computer that builds the Android releases. It is kept in %USERPROFILE%\.30nama, encrypted with
// Windows' DPAPI for this Windows account only, and never in the repository. Without it the app
// cannot update itself and people will have to download a release by hand; keep the text `export`
// prints somewhere safe, such as a password manager, and bring it back with `import`.
//
// Needs only a JDK (Ed25519 is part of Java since 15) and Windows PowerShell for DPAPI.
// Usage, from android/:  java tools/SignUpdate.java <command>
//   keygen           make the key, once
//   sign <file>      write <file>.sig
//   public           print the public key, for UpdateSignature.PUBLIC_KEY
//   export           print the private key, for a backup
//   import           read a backed-up private key from the console
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

public class SignUpdate {
  static final Path FOLDER = Path.of(System.getProperty("user.home"), ".30nama");
  static final Path KEY_FILE = FOLDER.resolve("android-update-signing.key");
  static final Path APP_KEY = Path.of("app/src/main/kotlin/app/nama/androidtv/data/Update.kt");
  // A PKCS#8 Ed25519 private key is this prefix and the 32-byte seed.
  static final byte[] PKCS8_PREFIX = hex("302e020100300506032b657004220420");

  public static void main(String[] args) throws Exception {
    String command = args.length > 0 ? args[0] : "";
    switch (command) {
      case "keygen" -> {
        if (Files.exists(KEY_FILE)) fail("a key is already in " + KEY_FILE + "; it is not replaced");
        byte[] seed = new byte[32];
        SecureRandom.getInstanceStrong().nextBytes(seed);
        store(seed);
        System.out.println("key made and kept in " + KEY_FILE);
        System.out.println("public key: " + publicOf(seed));
        System.out.println("back it up with `export`");
      }
      case "sign" -> {
        if (args.length != 2) usage();
        byte[] seed = load();
        Path file = Path.of(args[1]);
        if (!Files.exists(file)) fail("no file at " + file);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateOf(seed));
        feed(file, signer);
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        Files.writeString(Path.of(args[1] + ".sig"), signature + "\n");
        // A release the app cannot check is worse than none: it would be offered and then refused.
        Signature checker = Signature.getInstance("Ed25519");
        checker.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new java.security.spec.X509EncodedKeySpec(x509(seed))));
        feed(file, checker);
        if (!checker.verify(Base64.getDecoder().decode(signature))) fail("the signature does not check out; nothing should be uploaded");
        String publicKey = publicOf(seed);
        System.out.println("signed " + file + " with key " + publicKey);
        if (!publicKey.equals(appKey())) fail("this is not the key in UpdateSignature.PUBLIC_KEY: the app would refuse the update");
      }
      case "public" -> System.out.println(publicOf(load()));
      case "export" -> System.out.println(Base64.getEncoder().encodeToString(load()));
      case "import" -> {
        if (Files.exists(KEY_FILE)) fail("a key is already in " + KEY_FILE + "; it is not replaced");
        System.out.print("private key: ");
        String line = new BufferedReader(new InputStreamReader(System.in)).readLine();
        byte[] seed = Base64.getDecoder().decode(line == null ? "" : line.trim());
        if (seed.length != 32) fail("that is not a 32-byte key");
        store(seed);
        System.out.println("public key: " + publicOf(seed));
      }
      default -> usage();
    }
  }

  static void usage() {
    fail("usage: java tools/SignUpdate.java keygen | sign <file> | public | export | import");
  }

  static void fail(String message) {
    System.err.println(message);
    System.exit(1);
  }

  static void feed(Path file, Signature signature) throws Exception {
    try (InputStream in = Files.newInputStream(file)) {
      byte[] buffer = new byte[1 << 16];
      for (int n; (n = in.read(buffer)) > 0; ) signature.update(buffer, 0, n);
    }
  }

  static PrivateKey privateOf(byte[] seed) throws Exception {
    byte[] der = new byte[PKCS8_PREFIX.length + 32];
    System.arraycopy(PKCS8_PREFIX, 0, der, 0, PKCS8_PREFIX.length);
    System.arraycopy(seed, 0, der, PKCS8_PREFIX.length, 32);
    return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  /** The public key's X.509 form; the JDK will not derive it from a private key directly. */
  static byte[] x509(byte[] seed) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
    generator.initialize(255, new FixedRandom(seed));
    return generator.generateKeyPair().getPublic().getEncoded();
  }

  static String publicOf(byte[] seed) throws Exception {
    byte[] der = x509(seed);
    byte[] raw = new byte[32];
    System.arraycopy(der, der.length - 32, raw, 0, 32);
    return Base64.getEncoder().encodeToString(raw);
  }

  static String appKey() throws IOException {
    var m = Pattern.compile("PUBLIC_KEY = \"([^\"]*)\"").matcher(Files.readString(APP_KEY));
    return m.find() ? m.group(1) : "";
  }

  static byte[] load() throws Exception {
    if (!Files.exists(KEY_FILE)) fail("no signing key; make one with `keygen` or bring one back with `import`");
    return Base64.getDecoder().decode(dpapi("Unprotect", Files.readString(KEY_FILE).trim()));
  }

  static void store(byte[] seed) throws Exception {
    Files.createDirectories(FOLDER);
    Files.writeString(KEY_FILE, dpapi("Protect", Base64.getEncoder().encodeToString(seed)) + "\n");
  }

  /** Windows' DPAPI, through Windows PowerShell: the key opens only for this Windows account. */
  static String dpapi(String direction, String base64) throws Exception {
    String script = "Add-Type -AssemblyName System.Security; $d=[Convert]::FromBase64String([Console]::In.ReadLine()); "
        + "$e=[Text.Encoding]::UTF8.GetBytes('30nama-android-update-signing'); "
        + "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::" + direction + "($d,$e,'CurrentUser'))";
    Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(true).start();
    try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.US_ASCII)) {
      w.write(base64 + "\n");
    }
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
    if (p.waitFor() != 0 || out.isEmpty()) fail("DPAPI could not " + direction.toLowerCase() + " the key: " + out);
    return out;
  }

  static byte[] hex(String s) {
    byte[] out = new byte[s.length() / 2];
    for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
    return out;
  }

  /** Hands the generator the seed as its "random" bytes, so it rebuilds this key's public half. */
  static class FixedRandom extends SecureRandom {
    final byte[] seed;

    FixedRandom(byte[] seed) {
      this.seed = seed;
    }

    @Override
    public void nextBytes(byte[] bytes) {
      System.arraycopy(seed, 0, bytes, 0, bytes.length);
    }
  }
}
