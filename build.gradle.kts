// Top-level build file for Riposte
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint) apply false
}

// Detekt configuration for static analysis
detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    baseline = file("detekt-baseline.xml")
    buildUponDefaultConfig = true
    parallel = true
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/main/kotlin/**", "**/src/main/java/**")
            exclude("**/build/**")
        }
    )
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

// Common configuration for all subprojects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        android.set(true)
        verbose.set(true)
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            // Set JVM target to match Java 17 (Kotlin 2.3.0 defaults to 21)
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            
            // Treat all warnings as errors for stricter code quality
            allWarningsAsErrors.set(true)
        }
    }

    // Add coroutines opt-in only for modules that declare coroutines as a dependency,
    // to avoid "unresolved opt-in marker" warnings in modules without coroutines.
    afterEvaluate {
        val hasCoroutines = configurations.findByName("implementation")
            ?.dependencies
            ?.any { it.group == "org.jetbrains.kotlinx" && it.name.startsWith("kotlinx-coroutines") }
            ?: false
        if (hasCoroutines) {
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                        "-opt-in=kotlinx.coroutines.FlowPreview",
                    )
                }
            }
        }
    }

    // Configure Compose compiler for all modules that use it
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            // Generate function key meta classes for improved debugging
            includeSourceInformation.set(true)

            // Stability configuration file for external types
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability_config.conf"))
        }
    }
}
