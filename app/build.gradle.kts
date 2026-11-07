plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// CI passes its run number, so every build has a higher versionCode than the one before.
val buildNumber = providers.environmentVariable("SNAPSTASH_BUILD_NUMBER").orNull?.toInt()
val baseVersion = providers.gradleProperty("snapstash.version").get()

android {
    namespace = "io.github.murrayb52.snapstash"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.murrayb52.snapstash"
        // Android 10+ (scoped storage era); folders are reached via the system folder picker.
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber ?: 1
        versionName = if (buildNumber != null) "$baseVersion.$buildNumber" else "$baseVersion-local"
    }

    signingConfigs {
        // A fixed debug key checked into the repo, so a new build installs over the previous one.
        getByName("debug") {
            storeFile = file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Screenshot tests render the real UI under Robolectric.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)

    // Screenshot tests (Roborazzi + Robolectric). CI records them with -Proborazzi.test.record=true.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
