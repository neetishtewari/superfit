// Code shared by the Android app and, later, the iPhone app. See
// docs/superpowers/specs/2026-11-04-iphone-groundwork.md. Only code with no Android or iPhone
// parts goes in commonMain. The iPhone targets are added once the Mac builds them.
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
  jvmToolchain(17)

  androidLibrary {
    namespace = "com.superfit.shared"
    compileSdk = 36
    minSdk = 26
  }

  // Runs the shared tests quickly on the computer, without Android.
  jvm()

  sourceSets {
    commonMain.dependencies {
      // Room's annotations only (no database code), so the entities can live here.
      api(libs.androidx.room.common)
      // Dates and time zones that work on Android and iPhone alike (replaces java.time).
      api(libs.kotlinx.datetime)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}
