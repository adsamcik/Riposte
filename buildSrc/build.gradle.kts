plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.58")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.0")
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "meme-my-mood.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "meme-my-mood.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "meme-my-mood.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidTesting") {
            id = "meme-my-mood.android.testing"
            implementationClass = "AndroidTestingConventionPlugin"
        }
        register("androidJacoco") {
            id = "meme-my-mood.android.jacoco"
            implementationClass = "AndroidJacocoConventionPlugin"
        }
    }
}
