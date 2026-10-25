import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.meta.spatial.plugin)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "app.nama.quest"
  //noinspection GradleDependency
  compileSdk = 34
  // same NDK as tools/build-libmpv.sh, so libnamampv and libmpv share one libc++
  ndkVersion = "28.2.13676358"

  defaultConfig {
    applicationId = "app.nama.quest"
    // Horizon OS is Android 14 (API 34)
    minSdk = 34
    //noinspection OldTargetApi,ExpiredTargetSdkVersion
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0"
    ndk { abiFilters += "arm64-v8a" }
    // The website's API key, from local.properties (never committed). Without it the app works in
    // full; only what comes from the website's own API stays hidden, as on the Mac and Windows.
    val local = Properties().apply {
      rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    buildConfigField("String", "SITE_KEY", "\"${local.getProperty("nama.siteKey", "")}\"")
    // libnamampv is plain C; libc++_shared comes from jniLibs with libmpv
    externalNativeBuild { cmake { arguments += "-DANDROID_STL=none" } }
  }
  externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    resources.excludes.add("META-INF/LICENSE*")
    jniLibs.useLegacyPackaging = false
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
  testOptions {
    unitTests {
      // Panel snapshots (PanelShots) render Compose on the JVM through Robolectric.
      isIncludeAndroidResources = true
      all {
        // The snapshots take minutes; they run only when asked for: -Pshots
        if (!project.hasProperty("shots")) it.exclude("**/PanelShots*")
        it.failOnNoDiscoveredTests = false
        it.systemProperty("roborazzi.test.record", "true")
        it.systemProperty("nama.shots", rootProject.file("build/shots").absolutePath)
        it.systemProperty("nama.fixtures", rootProject.file("../Tests/NamaCoreTests/Fixtures").absolutePath)
        (project.findProperty("shots.lang") as String?)?.let { lang -> it.systemProperty("nama.lang", lang) }
        (project.findProperty("shots.trace") as String?)?.let { _ -> it.systemProperty("nama.trace", "1") }
        it.maxHeapSize = "3g"
      }
    }
  }
}

dependencies {
  implementation(project(":core"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.foundation)
  implementation(libs.androidx.material3)

  implementation(libs.meta.spatial.sdk.base)
  implementation(libs.meta.spatial.sdk.vr)
  implementation(libs.meta.spatial.sdk.toolkit)
  implementation(libs.meta.spatial.sdk.compose)
  implementation(libs.meta.spatial.sdk.mruk)
  implementation(libs.meta.spatial.sdk.isdk)
  debugImplementation(libs.meta.spatial.sdk.ovrmetrics)
  debugImplementation(libs.androidx.ui.test.manifest)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.meta.spatial.sdk.castinputforward)
}

spatial { allowUsageDataCollection.set(false) }
