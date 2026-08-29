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

rootProject.name = "FarmOS"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:network",
    ":core:sync",
    ":domain:goat",
    ":feature:goat",
)
