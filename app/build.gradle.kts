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
    versionCode = 126
    versionName = "1.2.6"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePropsFile = rootProject.file("keystore.properties")
      if (keystorePropsFile.exists()) {
        val props = Properties().apply {
          keystorePropsFile.inputStream().use { load(it) }
        }
        val storeFilePath = props.getProperty("storeFile") ?: "docsy-release-key.jks"
        val storeFileObj = if (storeFilePath.startsWith("/")) file(storeFilePath) else rootProject.file(storeFilePath)
        storeFile = storeFileObj
        storePassword = props.getProperty("storePassword")
        keyAlias = props.getProperty("keyAlias")
        keyPassword = props.getProperty("keyPassword")
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
      }
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
      isMinifyEnabled = false
    }
  }

  // Target distribution profiles: full, noSmsCalls, filesOnly
  flavorDimensions += "distribution"
  productFlavors {
    create("full") {
      dimension = "distribution"
    }
    create("noSmsCalls") {
      dimension = "distribution"
      applicationIdSuffix = ".nosmscalls"
    }
    create("filesOnly") {
      dimension = "distribution"
      applicationIdSuffix = ".filesonly"
    }
    create("offline") {
      dimension = "distribution"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

configurations.all {
  resolutionStrategy {
    force("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
    force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.10")
    force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.10")
    force("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
  }
}

gradle.taskGraph.whenReady {
  val hasReleaseTask = allTasks.any { it.name.contains("Release", ignoreCase = true) }
  if (hasReleaseTask && !rootProject.file("keystore.properties").exists()) {
    throw GradleException("keystore.properties is missing! Release builds require keystore.properties pointing to docsy-release-key.jks.")
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.sqlite.bundled)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.mlkit.text.recognition.devanagari)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Reads the text layer (with word positions) of digital PDFs: exact, and far faster than OCR.
  implementation("com.tom-roush:pdfbox-android:2.0.27.0")
  // On-device vision-language model runtime (GPU/NPU capable). Pin to a fixed version once it resolves.
  implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

  testImplementation(libs.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-Xskip-metadata-version-check")
  }
}
