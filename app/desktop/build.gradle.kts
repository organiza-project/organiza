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

tasks.test {
    // gsettings writes from tests land in a file under build/, never in the person's dconf.
    environment("GSETTINGS_BACKEND", "keyfile")
    environment("XDG_CONFIG_HOME", layout.buildDirectory.dir("test-config").get().asFile.path)
}

// The Linux desktop app (ADR-0023). `run` opens the window; `packageDeb` builds an installer.
compose.desktop {
    application {
        mainClass = "org.organiza.desktop.MainKt"
        // Lets Main name its windows after the launcher instead of the main class.
        jvmArgs += listOf("--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            // The encrypted SQLite driver needs java.sql; TLS to Groq and Supabase needs the EC ciphers.
            modules("java.sql", "jdk.unsupported", "jdk.crypto.ec")
            // The name people see in the app grid; the Debian package itself stays lowercase.
            packageName = "Organiza"
            packageVersion = "0.3.1"
            description = "Focus sessions, habits and app blocking that stay on your computer"
            vendor = "Organiza Project"
            licenseFile.set(rootProject.file("LICENSE"))
            linux {
                iconFile.set(file("packaging/organiza.png"))
                packageName = "organiza"
                debMaintainer = "radwandoufan@gmail.com"
                appCategory = "Utility"
                menuGroup = "Utility"
                shortcut = true
            }
        }
    }
}

// jpackage has no option for the homepage, AppStream metadata or a themed icon, so they are added to the finished .deb.
tasks.matching { it.name == "packageDeb" }.configureEach {
    doLast {
        val deb = outputs.files.asFileTree.matching { include("*.deb") }.singleFile
        val finish = ProcessBuilder(file("packaging/finish-deb.sh").path, deb.path).inheritIO().start()
        check(finish.waitFor() == 0) { "finish-deb.sh failed" }
    }
}
