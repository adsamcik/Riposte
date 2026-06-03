import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
    // TODO: Re-enable when baseline profile plugin supports AGP 9
    // alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.adsamcik.riposte"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.adsamcik.riposte"
        minSdk = 31
        targetSdk = 37
        versionCode = 12
        versionName = "0.5.0"

        testInstrumentationRunner = "com.adsamcik.riposte.core.testing.HiltTestRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Automatic per-app language support (AGP 8.1+)
    androidResources {
        generateLocaleConfig = true
        // Only include supported locales in APK
        localeFilters += setOf("en", "cs", "de", "es", "pt")
    }

    // Release signing configuration
    signingConfigs {
        // Stable debug signing key shared with the Mindlayer service
        // (com.adsamcik.mindlayer.service). Riposte's debug variant uses
        // this so its signing cert matches Mindlayer's debug cert, which
        // grants signature|knownSigner permission to BIND_ML_SERVICE
        // automatically and lets Mindlayer's DebugAllowlistSeeder
        // auto-approve Riposte without any UI dialog. See
        // app/keystores/README.md for details.
        getByName("debug") {
            storeFile = file("keystores/knowncerts-owner.jks")
            storePassword = "knowncertstest"
            keyAlias = "knowncerts-owner"
            keyPassword = "knowncertstest"
        }
        create("release") {
            val keystorePropertiesFile = rootProject.file("local.properties")
            if (keystorePropertiesFile.exists()) {
                val properties = Properties()
                keystorePropertiesFile.inputStream().use { stream -> properties.load(stream) }
                val storeFilePath = properties.getProperty("RELEASE_STORE_FILE")
                if (storeFilePath != null && file(storeFilePath).exists()) {
                    storeFile = file(storeFilePath)
                    storePassword = properties.getProperty("RELEASE_STORE_PASSWORD")
                    keyAlias = properties.getProperty("RELEASE_KEY_ALIAS")
                    keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use release signing if configured, otherwise fall back to debug
            val releaseConfig = signingConfigs.getByName("release")
            signingConfig =
                if (releaseConfig.storeFile != null) {
                    releaseConfig
                } else {
                    signingConfigs.getByName("debug")
                }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        disable += "PropertyEscape"
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
    }
}

dependencies {
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ml"))

    // Feature modules
    implementation(project(":feature:gallery"))
    implementation(project(":feature:import"))
    implementation(project(":feature:share"))
    implementation(project(":feature:settings"))

    // Core search (logic-only, no UI)
    implementation(project(":core:search"))

    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.core.splashscreen)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.navigation.compose)
    debugImplementation(libs.bundles.compose.debug)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // WorkManager
    implementation(libs.workmanager.runtime)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Google Play In-App Review
    implementation(libs.play.review.ktx)

    // Profile Installer for baseline profiles
    implementation(libs.profile.installer)

    // TODO: Re-enable when baseline profile plugin supports AGP 9
    // baselineProfile(project(":baselineprofile"))

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.bundles.android.testing)
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:database"))
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

// Ensure the test-receiver fixture APK is installed on the device before any
// connected instrumentation test runs. Without this, ShareIntegrationTest
// can't fire intents at the receiver Activities.
afterEvaluate {
    tasks
        .matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }
        .configureEach {
            dependsOn(":testapps:share-receiver:installDebug")
        }
}

// TODO: Re-enable when baseline profile plugin supports AGP 9
// baselineProfile {
//     automaticGenerationDuringBuild = false
//     dexLayoutOptimization = true
// }
