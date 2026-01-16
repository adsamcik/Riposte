plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.mememymood.core.testing"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "com.mememymood.core.testing.HiltTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    // Project dependencies
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:model"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Hilt Testing
    api(libs.hilt.android.testing)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    api(libs.coroutines.test)

    // Testing libraries (exposed as API for consumers)
    api(libs.junit)
    api(libs.truth)
    api(libs.mockk)
    api(libs.mockk.android)
    api(libs.turbine)
    api(libs.robolectric)

    // AndroidX Test
    api(libs.androidx.test.core)
    api(libs.androidx.test.core.ktx)
    api(libs.androidx.test.runner)
    api(libs.androidx.test.ext)

    // Compose Testing
    api(platform(libs.compose.bom))
    api(libs.compose.ui.test.junit4)
    debugApi(libs.compose.ui.test.manifest)
}
