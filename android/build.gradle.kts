plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
}

// core's own directory belongs to the Quest app; this build keeps what it makes of it here.
project(":core") { layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("core")) }
