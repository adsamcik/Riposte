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
    }
}

rootProject.name = "meme-my-mood"

// App module
include(":app")

// Baseline Profile module
include(":baselineprofile")

// Core modules
include(":core:common")
include(":core:ui")
include(":core:database")
include(":core:datastore")
include(":core:ml")
include(":core:model")
include(":core:testing")

// Feature modules
include(":feature:gallery")
include(":feature:import")
include(":feature:search")
include(":feature:share")
include(":feature:settings")
