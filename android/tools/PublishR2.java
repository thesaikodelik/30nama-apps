// Puts an Android TV release on the project's R2 bucket, beside GitHub, for the in-app updater:
// the APK, its .sig, and last android/latest.json naming them, so the updater never sees a version
// whose files are not there yet. A TV that cannot reach GitHub (often slow or blocked in Iran) then
// still finds and downloads the update. The app checks the .sig exactly as for GitHub, so the
// bucket cannot pass off a file of its own.
//
// The bucket's upload key stays on the computers that build the Android releases, never in the
// repository: %USERPROFILE%\.30nama\r2.properties on Windows, ~/.30nama/r2.properties on a Mac,
// readable only by this user. It is an R2 API token limited to Object Read & Write on this one
// bucket. Its lines:
//   endpoint=https://<account id>.r2.cloudflarestorage.com
//   bucket=nama-updates
//   accessKeyId=...
//   secretAccessKey=...
// The account id names the Cloudflare account; it is never written anywhere public, and the app
// knows only the bucket's public r2.dev address (AndroidRelease.MIRROR in Update.kt).
//
// Needs only a JDK.
// Usage, from android/:  java tools/PublishR2.java <command>
//   publish <version> <apk>   upload <apk> and <apk>.sig as that version, then latest.json
//   check                     print what latest.json says now, and check its files are there
//   key                       check the upload key works (lists the bucket, changes nothing)
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class PublishR2 {
  static final Path SETTINGS = Path.of(System.getProperty("user.home"), ".30nama", "r2.properties");
  static final Path APP = Path.of("app/src/main/kotlin/app/nama/androidtv/data/Update.kt");
  static final String PAGE = "https://github.com/thesaikodelik/30nama-apps/releases/tag/android-v";
  static final HttpClient HTTP = HttpClient.newHttpClient();

  public static void main(String[] args) throws Exception {
    String command = args.length > 0 ? args[0] : "";
    String mirror = mirror();
    String base = mirror.substring(0, mirror.lastIndexOf("/latest.json"));
    switch (command) {
      case "publish" -> {
        if (args.length != 3) usage();
        String version = args[1];
        if (!version.matches("\\d+\\.\\d+\\.\\d+")) fail("version must look like 1.2.3");
        Path apk = Path.of(args[2]), sig = Path.of(args[2] + ".sig");
        if (!Files.exists(apk)) fail("no file at " + apk);
        if (!Files.exists(sig)) fail("no " + sig + "; sign first: java tools/SignUpdate.java sign " + apk);
        // A slip of the version must not send every TV back, or offer a lower number as new.
        String now = fetchText(mirror);
        if (now != null) {
          String was = field(now, "version");
          if (was != null && !newer(version, was)) fail("latest.json already says " + was + "; " + version + " is not newer");
        }
        Properties s = settings();
        String name = "30nama-android-" + version + ".apk";
        put(s, "android/" + name, Files.readAllBytes(apk), "application/vnd.android.package-archive", "public, max-age=31536000, immutable");
        put(s, "android/" + name + ".sig", Files.readAllBytes(sig), "text/plain; charset=utf-8", "public, max-age=31536000, immutable");
        String json = "{\n"
            + "  \"version\": \"" + version + "\",\n"
            + "  \"page\": \"" + PAGE + version + "\",\n"
            + "  \"apk\": \"" + base + "/" + name + "\",\n"
            + "  \"signature\": \"" + base + "/" + name + ".sig\"\n"
            + "}\n";
        // Last, and never cached: the updater must see the new files the moment this names them.
        put(s, "android/latest.json", json.getBytes(StandardCharsets.UTF_8), "application/json", "no-cache");
        System.out.println("uploaded " + name + ", its .sig and latest.json");
        verify(mirror, apk, sig);
      }
      case "check" -> verify(mirror, null, null);
      case "key" -> key();
      default -> usage();
    }
  }

  /** Reads latest.json from the public address and fetches what it names, as a TV would. */
  static void verify(String mirror, Path apk, Path sig) throws Exception {
    String json = fetchText(mirror);
    if (json == null) fail("no latest.json at " + mirror);
    System.out.println(mirror + ":\n" + json.strip());
    byte[] gotApk = fetch(field(json, "apk")), gotSig = fetch(field(json, "signature"));
    if (gotApk == null || gotSig == null) fail("a file latest.json names does not download");
    System.out.println("apk " + gotApk.length + " bytes, SHA-256 " + sha256Hex(gotApk));
    if (apk != null && !Arrays.equals(gotApk, Files.readAllBytes(apk))) fail("the downloaded apk is not the uploaded one");
    if (sig != null && !Arrays.equals(gotSig, Files.readAllBytes(sig))) fail("the downloaded .sig is not the uploaded one");
    System.out.println(apk != null ? "the public files are the uploaded ones" : "both files download");
  }

  /** One object, signed with AWS Signature Version 4, which R2 speaks (region "auto"). */
  static void put(Properties s, String key, byte[] body, String type, String cache) throws Exception {
    HttpResponse<String> answer = signed(s, "PUT", "/" + s.getProperty("bucket") + "/" + key, "", body, Map.of("Content-Type", type, "Cache-Control", cache));
    if (answer.statusCode() / 100 != 2) fail("upload of " + key + ": HTTP " + answer.statusCode() + " " + code(answer.body()));
    System.out.println("  " + key + " (" + body.length + " bytes)");
  }

  /** Lists one object: proves the key and the signing work, and changes nothing. */
  static void key() throws Exception {
    Properties s = settings();
    HttpResponse<String> answer = signed(s, "GET", "/" + s.getProperty("bucket"), "list-type=2&max-keys=1", new byte[0], Map.of());
    if (answer.statusCode() != 200) fail("the key was refused: HTTP " + answer.statusCode() + " " + code(answer.body()));
    Matcher m = Pattern.compile("<KeyCount>(\\d+)</KeyCount>").matcher(answer.body());
    System.out.println("the key works for bucket " + s.getProperty("bucket") + (m.find() ? " (" + (m.group(1).equals("0") ? "empty" : "has objects") + ")" : ""));
  }

  /**
   * Only R2's error code. Its whole answer repeats the request it signed, account id included,
   * which then sits in a terminal, a log or a screenshot.
   */
  static String code(String body) {
    Matcher m = Pattern.compile("<Code>([^<]*)</Code>").matcher(body);
    return m.find() ? m.group(1) : "(no error code)";
  }

  /** [query] is already canonical: its names sorted, its values needing no escaping. */
  static HttpResponse<String> signed(Properties s, String method, String path, String query, byte[] body, Map<String, String> extra) throws Exception {
    String endpoint = endpoint(s);
    String host = URI.create(endpoint).getHost();
    ZonedDateTime t = ZonedDateTime.now(ZoneOffset.UTC);
    String date = t.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
    String day = date.substring(0, 8);
    String hash = sha256Hex(body);
    String headers = "host:" + host + "\nx-amz-content-sha256:" + hash + "\nx-amz-date:" + date + "\n";
    String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
    String request = method + "\n" + path + "\n" + query + "\n" + headers + "\n" + signedHeaders + "\n" + hash;
    String scope = day + "/auto/s3/aws4_request";
    String toSign = "AWS4-HMAC-SHA256\n" + date + "\n" + scope + "\n" + sha256Hex(request.getBytes(StandardCharsets.UTF_8));
    byte[] k = hmac(("AWS4" + s.getProperty("secretAccessKey")).getBytes(StandardCharsets.UTF_8), day);
    k = hmac(hmac(hmac(k, "auto"), "s3"), "aws4_request");
    String signature = hex(hmac(k, toSign));
    HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(endpoint + path + (query.isEmpty() ? "" : "?" + query)))
        .header("x-amz-content-sha256", hash)
        .header("x-amz-date", date)
        .header("Authorization", "AWS4-HMAC-SHA256 Credential=" + s.getProperty("accessKeyId") + "/" + scope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature)
        .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
    extra.forEach(r::header);
    return HTTP.send(r.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** The account's S3 address. Cloudflare's own page shows it with the bucket on the end; that is taken off. */
  static String endpoint(Properties s) {
    String e = s.getProperty("endpoint").trim().replaceAll("/+$", "");
    String bucket = "/" + s.getProperty("bucket").trim();
    return e.endsWith(bucket) ? e.substring(0, e.length() - bucket.length()) : e;
  }

  static Properties settings() throws IOException {
    if (!Files.exists(SETTINGS)) fail("no " + SETTINGS + "; see the top of this file");
    Properties s = new Properties();
    try (Reader in = Files.newBufferedReader(SETTINGS)) { s.load(in); }
    for (String k : List.of("endpoint", "bucket", "accessKeyId", "secretAccessKey"))
      if (s.getProperty(k, "").isBlank()) fail(SETTINGS + " has no " + k);
    return s;
  }

  /** The app's own mirror address, so the tool and the app cannot disagree. */
  static String mirror() throws IOException {
    Matcher m = Pattern.compile("const val MIRROR = \"([^\"]+)\"").matcher(Files.readString(APP));
    if (!m.find()) fail("no MIRROR in " + APP + "; run from android/");
    return m.group(1);
  }

  static byte[] fetch(String url) throws Exception {
    if (url == null) return null;
    HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("Cache-Control", "no-cache").build(), HttpResponse.BodyHandlers.ofByteArray());
    return r.statusCode() == 200 ? r.body() : null;
  }

  static String fetchText(String url) throws Exception {
    byte[] b = fetch(url);
    return b == null ? null : new String(b, StandardCharsets.UTF_8);
  }

  static String field(String json, String name) {
    Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
    return m.find() ? m.group(1) : null;
  }

  /** As AndroidRelease.isNewer: number by number, so 1.10 is newer than 1.9. */
  static boolean newer(String a, String b) {
    String[] x = a.split("\\."), y = b.split("\\.");
    for (int i = 0; i < Math.max(x.length, y.length); i++) {
      int p = i < x.length ? Integer.parseInt(x[i].replaceAll("\\D.*", "0")) : 0;
      int q = i < y.length ? Integer.parseInt(y[i].replaceAll("\\D.*", "0")) : 0;
      if (p != q) return p > q;
    }
    return false;
  }

  static byte[] hmac(byte[] key, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
  }

  static String sha256Hex(byte[] data) throws Exception {
    return hex(MessageDigest.getInstance("SHA-256").digest(data));
  }

  static String hex(byte[] b) {
    StringBuilder s = new StringBuilder();
    for (byte x : b) s.append(String.format("%02x", x));
    return s.toString();
  }

  static void usage() {
    fail("usage: java tools/PublishR2.java publish <version> <apk> | check | key");
  }

  static void fail(String message) {
    System.err.println("PublishR2: " + message);
    System.exit(1);
  }
}
