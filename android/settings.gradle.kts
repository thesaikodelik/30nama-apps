pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "30nama-androidtv"

// The logic shared with the Quest app, read where it lives (nothing is written into quest/; the
// build output goes to android/build/core). It moves to kotlin-core/ later: then this line points there.
include(":core")
project(":core").projectDir = file("../quest/core")

include(":app")
