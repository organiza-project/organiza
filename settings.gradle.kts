pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "organiza"

include(":core:domain", ":core:testing", ":core:storage", ":core:enforcement", ":core:calendar", ":core:scheduler", ":core:ai", ":core:voice", ":core:arena", ":app:android")
