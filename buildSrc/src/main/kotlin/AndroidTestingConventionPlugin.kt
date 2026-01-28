import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for testing configuration.
 * 
 * Adds common test dependencies:
 * - JUnit
 * - Mockk
 * - Turbine (for Flow testing)
 * - Truth (assertions)
 * - Coroutines Test
 * - AndroidX Test (for instrumented tests)
 * 
 * Usage:
 * ```kotlin
 * plugins {
 *     id("meme-my-mood.android.library")
 *     id("meme-my-mood.android.testing")
 * }
 * ```
 */
class AndroidTestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libs = extensions.getByType<org.gradle.accessors.dm.LibrariesForLibs>()

            dependencies {
                // Unit test dependencies
                add("testImplementation", libs.junit)
                add("testImplementation", libs.mockk)
                add("testImplementation", libs.turbine)
                add("testImplementation", libs.truth)
                add("testImplementation", libs.coroutines.test)

                // Android instrumented test dependencies
                add("androidTestImplementation", libs.androidx.test.ext)
                add("androidTestImplementation", libs.androidx.test.core)
                add("androidTestImplementation", libs.androidx.test.runner)
                add("androidTestImplementation", libs.espresso.core)
                add("androidTestImplementation", libs.mockk.android)
            }
        }
    }
}
