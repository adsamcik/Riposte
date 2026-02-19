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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adsamcik.riposte"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"

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

    // Product flavors for embedding models
    // Single dimension: controls which EmbeddingGemma models are included
    // Always builds universal (all architectures) for maximum compatibility
    flavorDimensions += "embedding"

    productFlavors {
        create("lite") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "false")
            // No EmbeddingGemma models - uses MediaPipe USE or simple embeddings only
            // APK size: ~177 MB (smallest)
        }

        create("standard") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "true")
            buildConfigField("String", "INCLUDED_SOC_MODELS", "\"none\"")
            isDefault = true
            // Generic model only (~171 MB) - works on all devices
            // APK size: ~350 MB (RECOMMENDED)
        }

        create("qualcomm") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "true")
            buildConfigField("String", "INCLUDED_SOC_MODELS", "\"qualcomm\"")
            // Generic + all Qualcomm models (~850 MB)
            // APK size: ~880 MB
        }

        create("mediatek") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "true")
            buildConfigField("String", "INCLUDED_SOC_MODELS", "\"mediatek\"")
            // Generic + all MediaTek models (~525 MB)
            // APK size: ~555 MB
        }

        create("full") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "true")
            buildConfigField("String", "INCLUDED_SOC_MODELS", "\"all\"")
            // All models (~1.2 GB) - for development/testing
            // APK size: ~1.3 GB
        }
    }

    // Configure source sets to include/exclude embedding models per flavor
    sourceSets {
        // Lite: no EmbeddingGemma models
        getByName("lite") {
            assets.setSrcDirs(listOf("src/main/assets"))
        }

        // Standard: generic model + tokenizer only (default)
        getByName("standard") {
            assets.setSrcDirs(
                listOf(
                    "src/main/assets",
                    "src/main/assets_standard",
                ),
            )
        }

        // Qualcomm: generic + Qualcomm models
        getByName("qualcomm") {
            assets.setSrcDirs(
                listOf(
                    "src/main/assets",
                    "src/main/assets_standard",
                    "src/main/assets_qualcomm",
                ),
            )
        }

        // MediaTek: generic + MediaTek models
        getByName("mediatek") {
            assets.setSrcDirs(
                listOf(
                    "src/main/assets",
                    "src/main/assets_standard",
                    "src/main/assets_mediatek",
                ),
            )
        }

        // Full: all models
        getByName("full") {
            assets.setSrcDirs(
                listOf(
                    "src/main/assets",
                    "src/main/assets_full",
                ),
            )
        }
    }

    // Release signing configuration
    signingConfigs {
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

// TODO: Re-enable when baseline profile plugin supports AGP 9
// baselineProfile {
//     automaticGenerationDuringBuild = false
//     dexLayoutOptimization = true
// }
