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
  debugImplementation(libs.meta.spatial.sdk.castinputforward)
}

spatial { allowUsageDataCollection.set(false) }
