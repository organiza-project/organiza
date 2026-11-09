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
        // Tesseract for Android is published only here, so nothing else may come from it (ADR-0018).
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.adaptech-cz(\\..+)?") }
        }
    }
}

rootProject.name = "organiza"

include(":core:domain", ":core:testing", ":core:storage", ":core:enforcement", ":core:calendar", ":core:scheduler", ":core:ai", ":core:voice", ":core:arena", ":core:presentation", ":core:sync", ":app:shared", ":app:android", ":app:desktop")
