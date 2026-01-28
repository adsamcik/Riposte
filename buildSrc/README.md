# Gradle Convention Plugins

This directory contains Gradle convention plugins that extract common build configuration into reusable plugins.

## Available Convention Plugins

### `meme-my-mood.android.library`
Base convention for Android library modules.

**Applies:**
- Android Library plugin
- Kotlin Android plugin

**Configures:**
- `compileSdk = 35`
- `minSdk = 26`
- Java 17 compatibility
- Kotlin compiler options
- Android Lint settings
- Resource packaging

**Usage:**
```kotlin
plugins {
    id("meme-my-mood.android.library")
}
```

### `meme-my-mood.android.compose`
Convention for Compose-enabled modules. Requires `android.library` plugin.

**Applies:**
- Kotlin Compose Compiler plugin

**Configures:**
- Compose build features
- Compose BOM dependency

**Usage:**
```kotlin
plugins {
    id("meme-my-mood.android.library")
    id("meme-my-mood.android.compose")
}
```

### `meme-my-mood.android.hilt`
Convention for Hilt dependency injection. Requires `android.library` plugin.

**Applies:**
- KSP plugin
- Hilt Android plugin

**Adds Dependencies:**
- Hilt Android
- Hilt Compiler (KSP)

**Usage:**
```kotlin
plugins {
    id("meme-my-mood.android.library")
    id("meme-my-mood.android.hilt")
}
```

### `meme-my-mood.android.testing`
Convention for testing dependencies. Requires `android.library` plugin.

**Adds Unit Test Dependencies:**
- JUnit
- Mockk
- Turbine (Flow testing)
- Truth (assertions)
- Coroutines Test

**Adds Android Test Dependencies:**
- AndroidX Test Extensions
- AndroidX Test Core
- AndroidX Test Runner
- Espresso Core
- Mockk Android

**Usage:**
```kotlin
plugins {
    id("meme-my-mood.android.library")
    id("meme-my-mood.android.testing")
}
```

## Example Module Configuration

Before convention plugins:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.feature.gallery"
    compileSdk = 35
    
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    // ... more test dependencies
}
```

After convention plugins:
```kotlin
plugins {
    id("meme-my-mood.android.library")
    id("meme-my-mood.android.compose")
    id("meme-my-mood.android.hilt")
    id("meme-my-mood.android.testing")
}

android {
    namespace = "com.example.feature.gallery"
}

dependencies {
    // Only module-specific dependencies
    implementation(projects.core.model)
    implementation(libs.paging.compose)
}
```

## Benefits

1. **Consistency**: All modules use the same configuration
2. **Maintainability**: Update configuration in one place
3. **Reduced Boilerplate**: Less repetitive code in module build files
4. **Type Safety**: Convention plugins are written in Kotlin
5. **Discoverability**: Clear naming makes plugins easy to understand

## Adding New Convention Plugins

1. Create a new file in `buildSrc/src/main/kotlin/`
2. Implement the `Plugin<Project>` interface
3. Register the plugin in `buildSrc/build.gradle.kts` under `gradlePlugin.plugins`
4. Document the plugin in this README

## How It Works

The `buildSrc` directory is a special Gradle directory that:
- Is compiled before the rest of the project
- Makes plugins available to all modules
- Allows sharing build logic without publishing plugins

When you add `id("meme-my-mood.android.library")` to a module's `build.gradle.kts`, Gradle:
1. Finds the plugin registration in `buildSrc/build.gradle.kts`
2. Instantiates the `AndroidLibraryConventionPlugin` class
3. Calls its `apply()` method on the target project
4. Applies all configured settings and dependencies
