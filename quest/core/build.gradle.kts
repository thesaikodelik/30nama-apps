plugins {
  alias(libs.plugins.jetbrains.kotlin.jvm)
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

// Tests read the same fixtures as the Swift tests, straight from Tests/NamaCoreTests/Fixtures.
sourceSets {
  test { resources.srcDir(rootProject.file("../Tests/NamaCoreTests/Fixtures")) }
}
