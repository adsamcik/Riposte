import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.mememymood"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mememymood"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.mememymood.core.testing.HiltTestRunner"
        
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

    // Product flavors for embedding models and architectures
    // Two dimensions: embedding models (ML) and native ABIs (architecture)
    flavorDimensions += listOf("embedding", "abi")
    
    productFlavors {
        // ===== Embedding Model Dimension =====
        // Controls which EmbeddingGemma models are included in the APK
        // Generic + SOC-specific models = ~1.2 GB, reducing this saves significant space
        
        create("lite") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "false")
            // No EmbeddingGemma models - uses MediaPipe USE or simple embeddings only
            // APK impact: ~0 MB (smallest)
        }
        
        create("standard") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "true")
            buildConfigField("String", "INCLUDED_SOC_MODELS", "\"none\"")
            // Generic model only (~171 MB) - works on all devices
            // APK impact: ~175 MB
        }
        
        create("qualcomm") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "true")
            buildConfigField("String", "INCLUDED_SOC_MODELS", "\"qualcomm\"")
            // Generic + all Qualcomm models (~850 MB)
            // APK impact: ~860 MB
        }
        
        create("mediatek") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "true")
            buildConfigField("String", "INCLUDED_SOC_MODELS", "\"mediatek\"")
            // Generic + all MediaTek models (~525 MB)
            // APK impact: ~535 MB
        }
        
        create("full") {
            dimension = "embedding"
            buildConfigField("boolean", "INCLUDE_EMBEDDINGGEMMA", "true")
            buildConfigField("String", "INCLUDED_SOC_MODELS", "\"all\"")
            // All models (~1.2 GB) - for development/testing
            // APK impact: ~1.2 GB
        }
        
        // ===== ABI Dimension =====
        // Controls which native libraries are included
        
        create("universal") {
            dimension = "abi"
            // Include all ABIs - used for Play Store (App Bundle) and development
        }
        
        create("arm64") {
            dimension = "abi"
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        
        create("arm") {
            dimension = "abi"
            ndk {
                abiFilters += "armeabi-v7a"
            }
        }
        
        create("x86_64") {
            dimension = "abi"
            ndk {
                abiFilters += "x86_64"
            }
        }
        
        create("x86") {
            dimension = "abi"
            ndk {
                abiFilters += "x86"
            }
        }
    }
    
    // Configure source sets to include/exclude embedding models per flavor
    sourceSets {
        // Lite flavor: no EmbeddingGemma models at all
        // Only uses main assets (README.md, no embedding_models folder)
        getByName("lite") {
            assets.setSrcDirs(listOf("src/main/assets"))
        }
        
        // Standard flavor: generic model + tokenizer only
        getByName("standard") {
            assets.setSrcDirs(listOf(
                "src/main/assets",
                "src/main/assets_standard"
            ))
        }
        
        // Qualcomm flavor: generic + Qualcomm models
        getByName("qualcomm") {
            assets.setSrcDirs(listOf(
                "src/main/assets",
                "src/main/assets_standard",
                "src/main/assets_qualcomm"
            ))
        }
        
        // MediaTek flavor: generic + MediaTek models
        getByName("mediatek") {
            assets.setSrcDirs(listOf(
                "src/main/assets",
                "src/main/assets_standard",
                "src/main/assets_mediatek"
            ))
        }
        
        // Full flavor: all models
        getByName("full") {
            assets.setSrcDirs(listOf(
                "src/main/assets",
                "src/main/assets_full"
            ))
        }
    }

    // Release signing configuration
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("local.properties")
            if (keystorePropertiesFile.exists()) {
                val properties = Properties()
                keystorePropertiesFile.inputStream().use { stream -> properties.load(stream) }
                storeFile = properties.getProperty("RELEASE_STORE_FILE")?.let { path -> file(path) }
                storePassword = properties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = properties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    implementation(project(":feature:search"))
    implementation(project(":feature:share"))
    implementation(project(":feature:settings"))

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

    // Profile Installer for baseline profiles
    implementation(libs.profile.installer)

    // Baseline Profile
    baselineProfile(project(":baselineprofile"))

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

baselineProfile {
    // Automatically generate baseline profile during release builds
    automaticGenerationDuringBuild = false
    
    // Don't include in debug builds
    dexLayoutOptimization = true
}
