import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Convention plugin for JaCoCo code coverage.
 * 
 * Configures JaCoCo for Android modules:
 * - Enables test coverage
 * - Creates unified coverage reports
 * - Sets coverage thresholds
 * - Excludes generated code from coverage
 * 
 * Usage:
 * ```kotlin
 * plugins {
 *     id("meme-my-mood.android.library")
 *     id("meme-my-mood.android.jacoco")
 * }
 * ```
 * 
 * Run coverage: `./gradlew testDebugUnitTestCoverage`
 */
class AndroidJacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("jacoco")
            }

            // Configure JaCoCo version
            extensions.configure<JacocoPluginExtension> {
                toolVersion = "0.8.12"
            }

            // Enable test coverage for Android
            extensions.configure<LibraryExtension> {
                buildTypes {
                    debug {
                        enableUnitTestCoverage = true
                        enableAndroidTestCoverage = true
                    }
                }
            }

            // Configure JaCoCo for test tasks
            tasks.withType<Test>().configureEach {
                configure<JacocoTaskExtension> {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }

            // Create unified coverage report task
            tasks.register<JacocoReport>("testDebugUnitTestCoverage") {
                dependsOn("testDebugUnitTest")
                
                group = "verification"
                description = "Generate JaCoCo coverage reports for Debug build"

                reports {
                    xml.required.set(true)
                    html.required.set(true)
                    csv.required.set(false)
                }

                val fileFilter = listOf(
                    // Android framework
                    "**/R.class",
                    "**/R$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*",
                    "**/*Test*.*",
                    "android/**/*.*",
                    
                    // Hilt/Dagger generated
                    "**/*_HiltModules*.*",
                    "**/*_Factory*.*",
                    "**/*_MembersInjector*.*",
                    "**/Hilt_*.*",
                    "**/*_Impl*.*",
                    "**/DaggerAppComponent*.*",
                    
                    // Room generated
                    "**/*_Impl.class",
                    "**/*Dao_Impl.class",
                    
                    // Data Binding
                    "**/databinding/*.*",
                    "**/DataBinderMapperImpl.class",
                    "**/DataBinderMapperImpl\$*.class",
                    "**/BR.class",
                    
                    // Navigation
                    "**/*Directions.class",
                    "**/*Directions\$*.class",
                    "**/*Args.class",
                    
                    // Compose generated
                    "**/*\$\$serializer.class",
                    
                    // Other generated
                    "**/*\$Companion.class",
                )

                val javaTree = fileTree("${buildDir}/intermediates/javac/debug") {
                    exclude(fileFilter)
                }
                val kotlinTree = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
                    exclude(fileFilter)
                }

                classDirectories.setFrom(files(listOf(javaTree, kotlinTree)))
                sourceDirectories.setFrom(files(
                    "${projectDir}/src/main/java",
                    "${projectDir}/src/main/kotlin"
                ))
                executionData.setFrom(fileTree(buildDir) {
                    include("**/*.exec", "**/*.ec")
                })
            }

            // Create coverage verification task with thresholds
            tasks.register<JacocoCoverageVerification>("verifyCoverage") {
                dependsOn("testDebugUnitTestCoverage")
                
                group = "verification"
                description = "Verify code coverage meets minimum thresholds"

                violationRules {
                    rule {
                        limit {
                            minimum = "0.60".toBigDecimal() // 60% minimum coverage
                        }
                    }
                    
                    rule {
                        element = "CLASS"
                        limit {
                            counter = "BRANCH"
                            minimum = "0.50".toBigDecimal() // 50% branch coverage
                        }
                    }
                }

                val fileFilter = listOf(
                    "**/R.class",
                    "**/R$*.class",
                    "**/BuildConfig.*",
                    "**/Manifest*.*",
                    "**/*Test*.*",
                    "**/*_HiltModules*.*",
                    "**/*_Factory*.*",
                    "**/*_MembersInjector*.*",
                    "**/*_Impl.class",
                )

                val javaTree = fileTree("${buildDir}/intermediates/javac/debug") {
                    exclude(fileFilter)
                }
                val kotlinTree = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
                    exclude(fileFilter)
                }

                classDirectories.setFrom(files(listOf(javaTree, kotlinTree)))
                sourceDirectories.setFrom(files(
                    "${projectDir}/src/main/java",
                    "${projectDir}/src/main/kotlin"
                ))
                executionData.setFrom(fileTree(buildDir) {
                    include("**/*.exec", "**/*.ec")
                })
            }
        }
    }
}
