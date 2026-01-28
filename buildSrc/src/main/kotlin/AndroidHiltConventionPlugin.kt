import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin for Hilt dependency injection.
 * 
 * Applies Hilt plugins and adds common dependencies:
 * - Hilt Android
 * - Hilt compiler
 * 
 * Usage:
 * ```kotlin
 * plugins {
 *     id("meme-my-mood.android.library")
 *     id("meme-my-mood.android.hilt")
 * }
 * ```
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("dagger.hilt.android.plugin")
            }

            val libs = extensions.getByType<org.gradle.accessors.dm.LibrariesForLibs>()

            dependencies {
                add("implementation", libs.hilt.android)
                add("ksp", libs.hilt.compiler)
            }
        }
    }
}
