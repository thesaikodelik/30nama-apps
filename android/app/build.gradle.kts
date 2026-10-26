import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "app.nama.androidtv"
  compileSdk = 37

  defaultConfig {
    // "app.30nama.androidtv" was asked for, but no part of an Android package name may start with
    // a digit; the apps use Nama for 30nama wherever a name must start with a letter (AGENTS.md).
    applicationId = "app.nama.androidtv"
    // Android 6: the oldest TVs still in use (Sony's 2017 sets run 7, some boxes 6).
    minSdk = 23
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    // The website's API key, from local.properties (never committed). Without it the app works in
    // full; only what comes from the website's own API stays hidden, as on the Mac and Windows.
    val local = Properties().apply {
      rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    buildConfigField("String", "SITE_KEY", "\"${local.getProperty("nama.siteKey", "")}\"")
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
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
  implementation(libs.media3.inspector)
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
