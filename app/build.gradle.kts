plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.retroplay.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.retroplay.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.9.1"
    }

    signingConfigs {
        create("localRelease") {
            // Release key lives outside the repo; its path/passwords come from ~/.gradle/gradle.properties
            // (RETROPLAY_STORE_FILE, RETROPLAY_STORE_PASSWORD, RETROPLAY_KEY_ALIAS, RETROPLAY_KEY_PASSWORD)
            // or the same names as environment variables (CI). Falls back to the debug key if unset.
            fun prop(n: String): String? = (project.findProperty(n) as String?) ?: System.getenv(n)
            val sf = prop("RETROPLAY_STORE_FILE")
            if (sf != null) {
                storeFile = file(sf)
                storePassword = prop("RETROPLAY_STORE_PASSWORD")
                keyAlias = prop("RETROPLAY_KEY_ALIAS")
                keyPassword = prop("RETROPLAY_KEY_PASSWORD")
            } else {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("localRelease")
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.media:media:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
