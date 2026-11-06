// Code shared by the Android app and, later, the iPhone app. See
// docs/superpowers/specs/2026-11-04-iphone-groundwork.md. Only code with no Android or iPhone
// parts goes in commonMain. The iPhone targets are added once the Mac builds them.
plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.ksp)
  alias(libs.plugins.androidx.room)
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
      // The on-device database (tables, queries). Each app opens it its own way.
      api(libs.androidx.room.runtime)
      // Dates and time zones that work on Android and iPhone alike (replaces java.time).
      api(libs.kotlinx.datetime)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

// Room writes each schema version here; commit the JSON so schema changes show up in review.
room {
  schemaDirectory("$projectDir/schemas")
}

// Room writes the database code for each platform at build time. KSP makes one configuration per
// platform (kspAndroid, kspJvm, later the iPhone ones), some only once that platform is set up,
// so add Room's compiler to each as it appears. Shared code and tests have nothing for Room.
configurations.configureEach {
  if (name.startsWith("ksp") && name != "ksp" && !name.contains("Test") && !name.contains("Metadata")) {
    project.dependencies.add(name, libs.androidx.room.compiler)
  }
}
