pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local Maven for Mindlayer SDK (publish via `./gradlew :sdk:publishToMavenLocal` in Mindlayer repo)
        mavenLocal()
    }
}

rootProject.name = "riposte"

// App module
include(":app")

// Baseline Profile module
// TODO: Re-enable when baseline profile plugin supports AGP 9
// include(":baselineprofile")

// Core modules
include(":core:common")
include(":core:events")
include(":core:ui")
include(":core:database")
include(":core:datastore")
include(":core:ml")
include(":core:model")
include(":core:testing")
include(":core:search")

// Feature modules
include(":feature:gallery")
include(":feature:import")
include(":feature:share")
include(":feature:settings")

// Test fixture apps (debug-only; never shipped)
include(":testapps:share-receiver")
