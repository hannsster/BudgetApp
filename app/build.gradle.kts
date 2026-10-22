import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Secrets are read from local.properties (git-ignored) or the environment (used by CI).
 * An empty value is a valid state: the app shows a setup card instead of crashing.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun buildSecret(name: String): String =
    localProperties.getProperty(name) ?: System.getenv(name) ?: ""

/**
 * Release signing is optional so a plain checkout still produces an installable APK.
 * Set these four (local.properties or environment) to ship updates with a stable signature:
 * RELEASE_KEYSTORE_PATH, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD.
 */
val releaseKeyStorePath = buildSecret("RELEASE_KEYSTORE_PATH")
val releaseKeyStorePassword = buildSecret("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = buildSecret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = buildSecret("RELEASE_KEY_PASSWORD")
val releaseSigningRequested = releaseKeyStorePath.isNotBlank() &&
    releaseKeyStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()
val releaseKeystoreFile = releaseKeyStorePath.takeIf { it.isNotBlank() }?.let { file(it) }
val hasReleaseKeystore = releaseSigningRequested && releaseKeystoreFile?.exists() == true

// Shipping a debug-signed APK by accident is how update-in-place breaks, so a half-configured
// keystore is a hard failure instead of a silent fallback.
if (releaseSigningRequested && !hasReleaseKeystore) {
    throw GradleException(
        "RELEASE_KEYSTORE_PATH points at a keystore that does not exist: $releaseKeyStorePath",
    )
}

android {
    namespace = "com.mipresupuestocr.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mipresupuestocr.app"
        minSdk = 26
        targetSdk = 35
        // Overridable from the release workflow: -PappVersionName=1.2.0 -PappVersionCode=42
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 4
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField(
            "String",
            "TWELVE_DATA_API_KEY",
            "\"${buildSecret("TWELVE_DATA_API_KEY")}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeyStorePath)
                storePassword = releaseKeyStorePassword
                keyAlias = releaseKeyAlias
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The deliverable is an installable APK. Without a release keystore the build falls
            // back to the debug key so the artifact still installs; add the four RELEASE_*
            // properties for updates that keep the same signature.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
