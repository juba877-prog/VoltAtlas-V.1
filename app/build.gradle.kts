import java.util.Properties
import java.io.FileInputStream
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kotlin.serialization)
  // alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.voltatlas.app.v1"

    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("String", "GEMINI_API_KEY", "\"\"")
    buildConfigField("boolean", "ENABLE_CLOUD_LLM", "false")
  }

  signingConfigs {
    create("release") {
      val ksPropsFile = rootProject.file("keystore.properties")
      val ksProps = Properties()
      if (ksPropsFile.exists()) {
        FileInputStream(ksPropsFile).use { ksProps.load(it) }
      }

      val propStoreFile = (ksProps.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH") ?: "my-upload-key.jks").trim()
      val propStorePass = (ksProps.getProperty("storePassword") ?: System.getenv("STORE_PASSWORD") ?: "").trim()
      val propKeyAlias = (ksProps.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS") ?: "upload").trim()
      val propKeyPass = (ksProps.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD") ?: propStorePass).trim()

      val resolvedKsFile = when {
        File(propStoreFile).isAbsolute -> File(propStoreFile)
        File(rootDir, propStoreFile).exists() -> File(rootDir, propStoreFile)
        else -> File(projectDir, propStoreFile)
      }

      val hasValidPassword = propStorePass.isNotEmpty() && propStorePass != "YOUR_STORE_PASSWORD_HERE"
      val fileExists = resolvedKsFile.exists()

      println("==================================================")
      println("[SIGNING DIAGNOSTIC] keystore.properties path : ${ksPropsFile.absolutePath} (exists: ${ksPropsFile.exists()})")
      println("[SIGNING DIAGNOSTIC] Resolved storeFile path  : ${resolvedKsFile.absolutePath} (exists: $fileExists)")
      println("[SIGNING DIAGNOSTIC] keyAlias                 : '$propKeyAlias'")
      println("[SIGNING DIAGNOSTIC] storePassword present    : ${propStorePass.isNotEmpty()}")

      if (fileExists && hasValidPassword) {
        println("[SIGNING RESOLUTION] ---> USING RELEASE UPLOAD KEY (${resolvedKsFile.name})")
        println("==================================================")
        storeFile = resolvedKsFile
        storePassword = propStorePass
        keyAlias = propKeyAlias
        keyPassword = propKeyPass
      } else {
        println("[SIGNING RESOLUTION] ---> NO VALID RELEASE KEYSTORE FOUND (fileExists=$fileExists, validPassword=$hasValidPassword)")
        println("==================================================")
        storeFile = null
      }
    }
    create("debugConfig") {
      storeFile = rootProject.file("debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
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
    debug { }
  }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
    checkDependencies = false
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }
}

// Hard fail if release packaging is attempted without valid upload keystore credentials
gradle.taskGraph.whenReady {
  val hasReleasePackagingTask = allTasks.any { task ->
    task.project == project && (
      task.name.startsWith("packageRelease") ||
      task.name.startsWith("bundleRelease") ||
      task.name.startsWith("assembleRelease")
    )
  }
  if (hasReleasePackagingTask) {
    val ksPropsFile = rootProject.file("keystore.properties")
    val ksProps = Properties()
    if (ksPropsFile.exists()) {
      FileInputStream(ksPropsFile).use { ksProps.load(it) }
    }
    val propStoreFile = (ksProps.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH") ?: "my-upload-key.jks").trim()
    val propStorePass = (ksProps.getProperty("storePassword") ?: System.getenv("STORE_PASSWORD") ?: "").trim()
    val resolvedKsFile = when {
      File(propStoreFile).isAbsolute -> File(propStoreFile)
      File(rootDir, propStoreFile).exists() -> File(rootDir, propStoreFile)
      else -> File(projectDir, propStoreFile)
    }
    val hasValidPassword = propStorePass.isNotEmpty() && propStorePass != "YOUR_STORE_PASSWORD_HERE"
    val fileExists = resolvedKsFile.exists()

    if (!fileExists || !hasValidPassword) {
      throw GradleException(
        "FATAL: Release packaging cannot proceed. Valid release upload keystore credentials are required (keystore.properties or environment variables KEYSTORE_PATH/STORE_PASSWORD). Reason: fileExists=$fileExists, validPassword=$hasValidPassword"
      )
    }
  }
}

composeCompiler {
  stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose_compiler_config.conf"))
  metricsDestination = layout.buildDirectory.dir("compose_metrics")
  reportsDestination = layout.buildDirectory.dir("compose_reports")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("GEMINI_API_KEY")
}

// googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.mlkit.text.recognition)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Immutable Collections & Baseline Profile Installer for Compose Stability
  implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
  implementation("androidx.profileinstaller:profileinstaller:1.4.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

  // Firebase Auth with Google Sign-In requires all of the following to be uncommented together.
  // If you are using Firebase Auth with other providers (e.g. Email/Password), you may only need
  // firebase-auth.
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation("com.airbnb.android:lottie-compose:6.5.2")
  implementation("ru.noties:jlatexmath-android:0.2.0")
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("org.json:json:20231013")
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// =============================================================================
// TEST EXECUTION PROFILES (AI FREEZE / FAST DEV LOOP)
// =============================================================================
tasks.withType<Test>().configureEach {
  if (name == "testDebugUnitTest") {
    dependsOn("generateReleaseBuildConfig")
  }

  val rawProfile = (project.findProperty("profile") ?: project.findProperty("testProfile") ?: "fast").toString().trim().lowercase()

  when (rawProfile) {
    "fast", "dev" -> {
      // Default Fast Profile: Core engineering & product tests only.
      // Excludes AI subsystem, heavy screenshot/preview, and stress/scale/fuzz suites.
      filter {
        excludeTestsMatching("com.example.ai.*")
        excludeTestsMatching("com.example.DashboardPreviewTest")
        excludeTestsMatching("com.example.sld.SldScreenScreenshotTest")
        excludeTestsMatching("com.example.ui.DashboardScreenshotTest")
        excludeTestsMatching("com.example.GreetingScreenshotTest")
        excludeTestsMatching("com.example.AllEnginesComprehensiveStressTest")
        excludeTestsMatching("com.example.MasterEngineeringStressTest")
        excludeTestsMatching("com.example.StressTestSuite")
        excludeTestsMatching("com.example.stress.*")
        excludeTestsMatching("com.example.ViewModelAndStateStressTest")
        // STAGE B0 diagnostic-only suites: the retained legacy Material-3 AlertDialog with
        // OutlinedTextField does not reach Compose idle under the current Robolectric harness.
        // Accepted as diagnostic evidence only; must not participate in default (fast/dev) runs.
        // They remain runnable under the "full" profile for intentional diagnostic use.
        excludeTestsMatching("com.example.ui.dialogs.circuiteditor.LegacyCircuitEditDialogSaveMappingCharacterizationTest")
        excludeTestsMatching("com.example.ui.dialogs.circuiteditor.LegacyCircuitEditDialogVisibilityCharacterizationTest")
        excludeTestsMatching("com.example.ui.dialogs.circuiteditor.diagnostics.CircuitEditDialogDiagnosticIsolationTest")
      }
    }
    "ai" -> {
      // AI-Only Profile: Run only AI subsystem tests under com.example.ai.*
      filter {
        includeTestsMatching("com.example.ai.*")
      }
    }
    "stress" -> {
      // Stress Profile: Run explicitly identified stress, scale, and fuzz suites
      filter {
        includeTestsMatching("com.example.AllEnginesComprehensiveStressTest")
        includeTestsMatching("com.example.MasterEngineeringStressTest")
        includeTestsMatching("com.example.StressTestSuite")
        includeTestsMatching("com.example.stress.*")
        includeTestsMatching("com.example.ViewModelAndStateStressTest")
      }
    }
    "gate" -> {
      // Release/CI GATE (AUDIT-5 P0 item 1). Runs every test EXCEPT the three STAGE B0
      // diagnostic-only suites, which are documented as unable to reach Compose idle under the
      // current Robolectric harness. Unlike 'fast', this profile INCLUDES com.example.ai.*, so the
      // mutation-approval security tests and the schema-contract tests (AUDIT-1 V-06) are always
      // exercised. A green dev-loop profile is not a release gate (AUDIT-1 V-13).
      filter {
        excludeTestsMatching("com.example.ui.dialogs.circuiteditor.LegacyCircuitEditDialogSaveMappingCharacterizationTest")
        excludeTestsMatching("com.example.ui.dialogs.circuiteditor.LegacyCircuitEditDialogVisibilityCharacterizationTest")
        excludeTestsMatching("com.example.ui.dialogs.circuiteditor.diagnostics.CircuitEditDialogDiagnosticIsolationTest")
      }
    }
    "diagnostic" -> {
      // Owns the quarantined STAGE B0 diagnostic-only suites explicitly, so that their exclusion
      // from 'fast'/'gate' is a documented relocation rather than a silent hiding.
      filter {
        includeTestsMatching("com.example.ui.dialogs.circuiteditor.LegacyCircuitEditDialogSaveMappingCharacterizationTest")
        includeTestsMatching("com.example.ui.dialogs.circuiteditor.LegacyCircuitEditDialogVisibilityCharacterizationTest")
        includeTestsMatching("com.example.ui.dialogs.circuiteditor.diagnostics.CircuitEditDialogDiagnosticIsolationTest")
      }
    }
    "full", "all" -> {
      // Full Profile: Execute all tests across the repository without exclusions
    }
    else -> {
      throw GradleException(
        "Unknown test execution profile: '$rawProfile'. " +
        "Supported profiles are: 'fast' (default), 'dev', 'ai', 'gate', 'diagnostic', 'full', 'all', 'stress'."
      )
    }
  }
}
