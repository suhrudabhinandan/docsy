import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.suhrud.docsy"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.suhrud.docsy"
    minSdk = 26
    targetSdk = 36
    versionCode = 7
    versionName = "1.0.6"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions += "version"
  productFlavors {
    create("full") {
      dimension = "version"
    }
    create("noSmsCalls") {
      dimension = "version"
      applicationIdSuffix = ".nosmscalls"
    }
    create("filesOnly") {
      dimension = "version"
      applicationIdSuffix = ".filesonly"
    }
  }

  signingConfigs {
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (!keystorePropsFile.exists()) {
      throw GradleException("FATAL: keystore.properties is missing! Release builds require keystore.properties for official signing.")
    }
    create("release") {
      val props = Properties().apply {
        keystorePropsFile.inputStream().use { stream ->
          load(stream)
        }
      }
      val storeFilePath = props.getProperty("storeFile") ?: "docsy-release-key.jks"
      storeFile = rootProject.file(storeFilePath)
      storePassword = props.getProperty("storePassword") ?: throw GradleException("storePassword missing in keystore.properties")
      keyAlias = props.getProperty("keyAlias") ?: "upload"
      keyPassword = props.getProperty("keyPassword") ?: throw GradleException("keyPassword missing in keystore.properties")
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Default debug keystore managed by Android Gradle Plugin
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.health.connect)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.google.mlkit.text)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)
}
