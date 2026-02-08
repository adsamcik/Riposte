// Top-level build file for Riposte
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// Common configuration for all subprojects
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            // Set JVM target to match Java 17 (Kotlin 2.3.0 defaults to 21)
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            
            // Treat all warnings as errors for stricter code quality
            allWarningsAsErrors.set(false)
            
            // Enable experimental coroutines APIs
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview"
            )
        }
    }

    // Configure Compose compiler for all modules that use it
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            // Enable strong skipping mode for better recomposition performance
            enableStrongSkippingMode.set(true)
            
            // Enable intrinsic remember for optimized remember calls
            enableIntrinsicRemember.set(true)
            
            // Generate function key meta classes for improved debugging
            includeSourceInformation.set(true)
            
            // Stability configuration file for external types
            stabilityConfigurationFile.set(rootProject.file("compose_stability_config.conf"))
        }
    }
}
