import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  id("kotlin-kapt")
  alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSecret(property: String, envVar: String): String? =
    localProperties.getProperty(property) ?: System.getenv(envVar)

android {
    namespace = "com.superfit.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.superfit.aifitness"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.0.14"
    }

    // Release signing secrets come from local.properties (gitignored) or environment variables,
    // never from this file. Without them, release builds are produced unsigned.
    val releaseStorePassword = signingSecret("superfit.storePassword", "SUPERFIT_STORE_PASSWORD")
    val releaseKeyPassword = signingSecret("superfit.keyPassword", "SUPERFIT_KEY_PASSWORD")
    signingConfigs {
        if (releaseStorePassword != null && releaseKeyPassword != null) {
            create("release") {
                storeFile = rootProject.file(
                    signingSecret("superfit.storeFile", "SUPERFIT_STORE_FILE") ?: "superfit-release.jks"
                )
                storePassword = releaseStorePassword
                keyAlias = signingSecret("superfit.keyAlias", "SUPERFIT_KEY_ALIAS") ?: "superfit-key"
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

kapt {
    arguments {
        // Room writes each schema version here; commit the JSON so schema changes show up in review.
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.google.material)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room Database
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  kapt(libs.androidx.room.compiler)

  // WorkManager
  implementation(libs.androidx.work.runtime)

  // Google Health Connect
  implementation(libs.androidx.health.connect)

  // Google AI (Gemini) SDK
  implementation(libs.google.generativeai)

  // Firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.play.services.auth)
  implementation(libs.firebase.firestore)
}

