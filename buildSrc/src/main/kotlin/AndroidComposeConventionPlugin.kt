import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for Compose-enabled modules.
 * 
 * Configures Jetpack Compose:
 * - Enables Compose
 * - Adds Compose BOM
 * - Configures Compose compiler options
 * 
 * Usage:
 * ```kotlin
 * plugins {
 *     id("meme-my-mood.android.library")
 *     id("meme-my-mood.android.compose")
 * }
 * ```
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                val bom = libs.findLibrary("compose-bom").get()
                add("implementation", platform(bom.get().toString()))
                add("androidTestImplementation", platform(bom.get().toString()))
            }
        }
    }
}
