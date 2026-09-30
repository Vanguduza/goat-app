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
    ":core:design",
    ":domain:goat",
    ":domain:rabbit",
    ":domain:ops",
    ":domain:replication",
    ":data:goat",
    ":data:herd",
    ":feature:goat",
    ":feature:rabbit",
    ":feature:ops",
)
