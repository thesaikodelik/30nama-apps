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

// The Apple TV app's logic (30nama/Core) in Kotlin, read where it lives (its build output goes to
// android/build/core). Not quest/core: the Quest app is unfinished and its copy is its own.
include(":core")
project(":core").projectDir = file("../kotlin-core")

include(":app")
