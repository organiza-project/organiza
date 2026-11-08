import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":app:shared"))
    implementation(compose.desktop.currentOs)
    testImplementation(libs.compose.mp.ui.test)
}

// The Linux desktop app (ADR-0023). `run` opens the window; `packageDeb` builds an installer.
compose.desktop {
    application {
        mainClass = "org.organiza.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "organiza"
            packageVersion = "0.2.0"
        }
    }
}
