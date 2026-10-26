import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "app.nama.androidtv"
  compileSdk = 37

  // Kept only on this computer, never committed: the website's key and the release APK key.
  val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
  }

  defaultConfig {
    // "app.30nama.androidtv" was asked for, but no part of an Android package name may start with
    // a digit; the apps use Nama for 30nama wherever a name must start with a letter (AGENTS.md).
    applicationId = "app.nama.androidtv"
    // Android 6: the oldest TVs still in use (Sony's 2017 sets run 7, some boxes 6).
    minSdk = 23
    targetSdk = 36
    // The version, and a code Android compares that grows with it: 1.2.3 is 10203. A test of the
    // updater builds a higher one with -Pnama.version=0.1.1 (never published).
    val version = (project.findProperty("nama.version") as String?) ?: "0.1.0"
    versionName = version
    versionCode = version.split('.').map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }
    // The website's API key, from local.properties (never committed). Without it the app works in
    // full; only what comes from the website's own API stays hidden, as on the Mac and Windows.
    buildConfigField("String", "SITE_KEY", "\"${local.getProperty("nama.siteKey", "")}\"")
  }

  // The release APK key lives only on the computer that builds releases (AGENTS.md). Without it a
  // release build is left unsigned.
  val keystore = local.getProperty("nama.keystore")?.let(::file)?.takeIf { it.exists() }
  signingConfigs {
    if (keystore != null) create("release") {
      storeFile = keystore
      storePassword = local.getProperty("nama.keystorePassword")
      keyAlias = "nama"
      keyPassword = local.getProperty("nama.keystorePassword")
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.findByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      buildConfigField("String", "TEST_FEED", "\"\"")
    }
    // The updater tried as a viewer would meet it (AGENTS.md): a release build in all but the feed,
    // which comes from a server on the computer the emulator runs on, with a higher version that is
    // never published. Its own manifest lets that one address use plain http.
    create("userTest") {
      initWith(getByName("release"))
      matchingFallbacks += "release"
      buildConfigField("String", "TEST_FEED", "\"http://10.0.2.2:18765/releases.json\"")
    }
    debug { buildConfigField("String", "TEST_FEED", "\"\"") }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging { resources.excludes.add("META-INF/LICENSE*") }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  testOptions {
    unitTests {
      // Screen snapshots (Shots) render Compose on the JVM through Robolectric.
      isIncludeAndroidResources = true
      all {
        // The snapshots take minutes; they run only when asked for: -Pshots
        if (!project.hasProperty("shots")) it.exclude("**/Shots*")
        it.failOnNoDiscoveredTests = false
        it.systemProperty("roborazzi.test.record", "true")
        it.systemProperty("nama.shots", rootProject.file("build/shots").absolutePath)
        it.systemProperty("nama.fixtures", rootProject.file("../Tests/NamaCoreTests/Fixtures").absolutePath)
        (project.findProperty("shots.lang") as String?)?.let { lang -> it.systemProperty("nama.lang", lang) }
        (project.findProperty("shots.only") as String?)?.let { only -> it.systemProperty("nama.only", only) }
        it.maxHeapSize = "3g"
      }
    }
  }
}

dependencies {
  implementation(project(":core"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.foundation)
  implementation(libs.androidx.tv.material)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.datasource.okhttp)
  implementation(libs.media3.session)
  implementation(libs.media3.inspector.frame)
  implementation(libs.media3.ui.compose)
  implementation(libs.okhttp)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)
  implementation(libs.zxing.core)

  debugImplementation(libs.androidx.ui.test.manifest)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.ui.test.junit4)
}
