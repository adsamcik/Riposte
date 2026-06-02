plugins {
    alias(libs.plugins.android.application)
    // Kotlin Android support is auto-applied by AGP 9 — no separate plugin
    // declaration needed (matches the pattern used by core/* and feature/*).
}

/**
 * Riposte Share Receiver Test App.
 *
 * Standalone Android app that pretends to be every kind of share receiver we
 * want to integration-test against (well-behaved, Discord-style buggy, slow,
 * paranoid, multi-process, etc.). NEVER shipped to users — debug-only fixture
 * for Riposte's `connectedAndroidTest`.
 *
 * Each Activity simulates one misbehavior pattern. After processing the share,
 * it writes the outcome (URI read, bytes count, any exception thrown) to its
 * [ShareTelemetryProvider] so Riposte's integration test can assert against it
 * via ContentResolver — no logcat scraping, no flakiness.
 *
 * Adding a new bug pattern: drop a new Activity class, declare it in the
 * manifest with the appropriate intent-filter, write outcome via
 * [TelemetryRecorder]. That's it.
 */
android {
    namespace = "com.adsamcik.riposte.testreceiver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adsamcik.riposte.testreceiver"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Debug-only fixture — no release variant. Even if someone runs
    // `assembleRelease`, the resulting APK is just for sideload testing.
    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // No Compose, no Hilt — keep this fixture absolutely minimal so failures
    // here can never be confused with framework issues.
    buildFeatures {
        compose = false
        buildConfig = false
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
