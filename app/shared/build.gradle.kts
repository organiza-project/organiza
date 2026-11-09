plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Every screen, drawn by Android and the Linux desktop alike (ADR-0023). Both targets are JVMs, so
// commonMain reads the :core modules and java.time directly.
kotlin {
    jvmToolchain(17)
    androidLibrary {
        namespace = "org.organiza.shared"
        compileSdk = 36
        minSdk = 26
        androidResources { enable = true }
    }
    jvm("desktop")

    sourceSets {
        // One copy of the Geist file: Android reads it as a font resource, the desktop from the classpath.
        named("desktopMain") { resources.srcDir("src/androidMain/res") }
        commonMain.dependencies {
            api(project(":core:arena"))
            api(project(":core:sync"))
            api(project(":core:presentation"))
            api(project(":core:scheduler"))
            api(project(":core:voice"))
            api(project(":core:calendar"))
            api(libs.compose.mp.foundation)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
        }
    }
}
