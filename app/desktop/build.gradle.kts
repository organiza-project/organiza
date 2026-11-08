import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(project(":app:shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.sqlite.jdbc.crypt)
    testImplementation(libs.compose.mp.ui.test)
    // The fakes come with the plain xerial driver, which would clash with the encrypting one.
    testImplementation(project(":core:testing")) {
        exclude(group = "org.xerial")
    }
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
